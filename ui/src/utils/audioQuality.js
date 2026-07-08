import config from '../config'

// Codecs that are always lossless, regardless of container/suffix
// (e.g. ALAC lives in .m4a, which is not in config.losslessFormats)
const LOSSLESS_CODECS = new Set([
  'flac',
  'alac',
  'pcm',
  'dsd',
  'ape',
  'wv',
  'wavpack',
  'tta',
  'tak',
  'shn',
  'mqa',
])

const DSD_SUFFIXES = new Set(['DSF', 'DFF'])
const DSD_BASE_RATE = 44100
const VALID_DSD_MULTIPLES = new Set([64, 128, 256, 512, 1024])

export const QUALITY_TIERS = {
  DSD: 'dsd',
  HI_RES: 'hiRes',
  CD: 'cd',
  LOSSY_HIGH: 'lossyHigh',
  LOSSY: 'lossy',
  UNKNOWN: 'unknown',
}

// Accent color per tier. Green lossless/hi-res, blue high lossy,
// yellow low lossy (same convention as QualityInfo), violet for DSD.
export const TIER_COLORS = {
  [QUALITY_TIERS.DSD]: '#A371F7',
  [QUALITY_TIERS.HI_RES]: '#3FB950',
  [QUALITY_TIERS.CD]: '#3FB950',
  [QUALITY_TIERS.LOSSY_HIGH]: '#58A6FF',
  [QUALITY_TIERS.LOSSY]: '#E3B341',
}

const trimNumber = (value, decimals) =>
  String(parseFloat(value.toFixed(decimals)))

// 44100 -> "44.1", 96000 -> "96", 176400 -> "176.4"
export const formatKHz = (sampleRate) => trimNumber(sampleRate / 1000, 1)

// 2822400 -> "2.8224", 5644800 -> "5.6448"
export const formatMHz = (sampleRate) => trimNumber(sampleRate / 1000000, 4)

// Linear ReplayGain peak (1.0 == full scale) -> dBFS
export const peakToDb = (peak) =>
  typeof peak === 'number' && peak > 0 ? 20 * Math.log10(peak) : null

export const formatDb = (value, unit = 'dB') => {
  if (typeof value !== 'number' || isNaN(value)) {
    return null
  }
  const sign = value > 0 ? '+' : ''
  return `${sign}${value.toFixed(2)} ${unit}`
}

const channelsLabel = (channels) => {
  switch (channels) {
    case 1:
      return 'Mono'
    case 2:
      return 'Stereo'
    case 6:
      return '5.1 Surround'
    case 8:
      return '7.1 Surround'
    default:
      return channels > 0 ? `${channels} ch` : null
  }
}

// Derives an audiophile-oriented quality description from a MediaFile record.
export const analyzeAudioQuality = (record = {}) => {
  const llFormats = new Set((config.losslessFormats || '').split(','))
  const suffix = (record.suffix || '').toUpperCase()
  const codec = (record.codec || '').toLowerCase()
  const bitRate = record.bitRate || 0
  const bitDepth = record.bitDepth || 0
  const channels = record.channels || 0
  let sampleRate = record.sampleRate || 0

  const isDSD = codec === 'dsd' || DSD_SUFFIXES.has(suffix)
  // ffprobe-shaped DSD rates are stored as the PCM-equivalent byte rate
  // (raw DSD rate ÷ 8). Real DSD rates start at 2.8224 MHz, so anything
  // below 1 MHz on a DSD file is the ÷8 convention.
  if (isDSD && sampleRate > 0 && sampleRate < 1000000) {
    sampleRate *= 8
  }

  const isLossless =
    isDSD ||
    LOSSLESS_CODECS.has(codec) ||
    (suffix !== '' && llFormats.has(suffix))

  let dsdMultiple = null
  if (isDSD && sampleRate > 0) {
    const multiple = Math.round(sampleRate / DSD_BASE_RATE)
    dsdMultiple = VALID_DSD_MULTIPLES.has(multiple) ? multiple : null
  }

  let tier = QUALITY_TIERS.UNKNOWN
  if (isDSD) {
    tier = QUALITY_TIERS.DSD
  } else if (isLossless && (bitDepth > 16 || sampleRate > 48000)) {
    tier = QUALITY_TIERS.HI_RES
  } else if (isLossless) {
    tier = QUALITY_TIERS.CD
  } else if (bitRate >= 256) {
    tier = QUALITY_TIERS.LOSSY_HIGH
  } else if (bitRate > 0) {
    tier = QUALITY_TIERS.LOSSY
  }

  // "24/96"-style audiophile notation for PCM lossless
  let notation = null
  if (isDSD) {
    notation = dsdMultiple ? `DSD${dsdMultiple}` : 'DSD'
  } else if (isLossless && sampleRate > 0) {
    notation =
      bitDepth > 0
        ? `${bitDepth}/${formatKHz(sampleRate)}`
        : `${formatKHz(sampleRate)} kHz`
  }

  let badge
  switch (tier) {
    case QUALITY_TIERS.DSD:
      badge = notation
      break
    case QUALITY_TIERS.HI_RES:
      badge = `Hi-Res ${notation}`
      break
    case QUALITY_TIERS.CD:
      badge = notation ? `CD ${notation}` : 'CD'
      break
    case QUALITY_TIERS.LOSSY_HIGH:
    case QUALITY_TIERS.LOSSY:
      badge = suffix ? `${suffix} ${bitRate} kbps` : `${bitRate} kbps`
      break
    default:
      badge = suffix || 'N/A'
  }

  const sampleRateLabel =
    sampleRate > 0
      ? isDSD
        ? `${formatMHz(sampleRate)} MHz`
        : `${formatKHz(sampleRate)} kHz`
      : null

  const bitDepthLabel = isDSD
    ? '1-bit (DSD)'
    : bitDepth > 0
      ? `${bitDepth}-bit`
      : null

  // Uncompressed data rate (kbps): PCM = rate × depth × channels;
  // DSD is 1 bit per sample
  let uncompressedRate = null
  if (isLossless && sampleRate > 0 && channels > 0) {
    const effectiveDepth = isDSD ? 1 : bitDepth
    if (effectiveDepth > 0) {
      uncompressedRate = Math.round(
        (sampleRate * effectiveDepth * channels) / 1000,
      )
    }
  }
  const compressionPct =
    uncompressedRate && bitRate > 0
      ? Math.min(100, Math.round((bitRate / uncompressedRate) * 100))
      : null

  return {
    tier,
    isLossless,
    isDSD,
    dsdMultiple,
    notation,
    badge,
    suffix,
    codec,
    sampleRateLabel,
    bitDepthLabel,
    channelsLabel: channelsLabel(channels),
    bitRateLabel: bitRate > 0 ? `${bitRate} kbps` : null,
    uncompressedRate,
    compressionPct,
  }
}
