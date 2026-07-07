import {
  analyzeAudioQuality,
  formatDb,
  formatKHz,
  formatMHz,
  peakToDb,
  QUALITY_TIERS,
} from './audioQuality'

describe('analyzeAudioQuality', () => {
  it('detects DSD64 from a TagLib-convention DSF record', () => {
    const q = analyzeAudioQuality({
      suffix: 'dsf',
      codec: 'dsd',
      bitRate: 5644,
      sampleRate: 2822400,
      bitDepth: 1,
      channels: 2,
    })
    expect(q.tier).toEqual(QUALITY_TIERS.DSD)
    expect(q.dsdMultiple).toEqual(64)
    expect(q.badge).toEqual('DSD64')
    expect(q.sampleRateLabel).toEqual('2.8224 MHz')
    expect(q.bitDepthLabel).toEqual('1-bit (DSD)')
    expect(q.channelsLabel).toEqual('Stereo')
    expect(q.isLossless).toBe(true)
  })

  it('normalizes ffprobe-convention DSD rates (rate ÷ 8)', () => {
    const q = analyzeAudioQuality({
      suffix: 'dff',
      bitRate: 5644,
      sampleRate: 352800,
      bitDepth: 8,
      channels: 2,
    })
    expect(q.tier).toEqual(QUALITY_TIERS.DSD)
    expect(q.dsdMultiple).toEqual(64)
    expect(q.sampleRateLabel).toEqual('2.8224 MHz')
    expect(q.bitDepthLabel).toEqual('1-bit (DSD)')
  })

  it('detects hi-res lossless with 24/96 notation', () => {
    const q = analyzeAudioQuality({
      suffix: 'flac',
      codec: 'flac',
      bitRate: 2304,
      sampleRate: 96000,
      bitDepth: 24,
      channels: 2,
    })
    expect(q.tier).toEqual(QUALITY_TIERS.HI_RES)
    expect(q.badge).toEqual('Hi-Res 24/96')
    expect(q.sampleRateLabel).toEqual('96 kHz')
    expect(q.uncompressedRate).toEqual(4608)
    expect(q.compressionPct).toEqual(50)
  })

  it('treats 16/44.1 lossless as CD tier', () => {
    const q = analyzeAudioQuality({
      suffix: 'flac',
      codec: 'flac',
      bitRate: 1008,
      sampleRate: 44100,
      bitDepth: 16,
      channels: 2,
    })
    expect(q.tier).toEqual(QUALITY_TIERS.CD)
    expect(q.badge).toEqual('CD 16/44.1')
    expect(q.sampleRateLabel).toEqual('44.1 kHz')
  })

  it('detects ALAC in an m4a container via codec', () => {
    const q = analyzeAudioQuality({
      suffix: 'm4a',
      codec: 'alac',
      bitRate: 890,
      sampleRate: 44100,
      bitDepth: 16,
      channels: 2,
    })
    expect(q.tier).toEqual(QUALITY_TIERS.CD)
    expect(q.isLossless).toBe(true)
  })

  it('classifies high-bitrate lossy at 256 kbps and above', () => {
    const q = analyzeAudioQuality({
      suffix: 'mp3',
      codec: 'mp3',
      bitRate: 320,
      sampleRate: 44100,
      channels: 2,
    })
    expect(q.tier).toEqual(QUALITY_TIERS.LOSSY_HIGH)
    expect(q.badge).toEqual('MP3 320 kbps')
    expect(q.bitDepthLabel).toBeNull()
    expect(q.compressionPct).toBeNull()
  })

  it('classifies low-bitrate lossy below 256 kbps', () => {
    const q = analyzeAudioQuality({
      suffix: 'opus',
      codec: 'opus',
      bitRate: 160,
      sampleRate: 48000,
      channels: 2,
    })
    expect(q.tier).toEqual(QUALITY_TIERS.LOSSY)
  })

  it('handles multichannel layouts', () => {
    const q = analyzeAudioQuality({
      suffix: 'flac',
      codec: 'flac',
      bitRate: 4000,
      sampleRate: 48000,
      bitDepth: 24,
      channels: 6,
    })
    expect(q.channelsLabel).toEqual('5.1 Surround')
  })

  it('returns unknown tier for an empty record', () => {
    const q = analyzeAudioQuality({})
    expect(q.tier).toEqual(QUALITY_TIERS.UNKNOWN)
    expect(q.badge).toEqual('N/A')
    expect(q.sampleRateLabel).toBeNull()
  })

  it('does not break with no record', () => {
    expect(analyzeAudioQuality().tier).toEqual(QUALITY_TIERS.UNKNOWN)
  })
})

describe('formatters', () => {
  it('formats kHz trimming trailing zeros', () => {
    expect(formatKHz(44100)).toEqual('44.1')
    expect(formatKHz(48000)).toEqual('48')
    expect(formatKHz(176400)).toEqual('176.4')
  })

  it('formats MHz for DSD rates', () => {
    expect(formatMHz(2822400)).toEqual('2.8224')
    expect(formatMHz(5644800)).toEqual('5.6448')
  })

  it('converts linear peak to dBFS', () => {
    expect(peakToDb(1)).toEqual(0)
    expect(peakToDb(0.5)).toBeCloseTo(-6.02, 2)
    expect(peakToDb(0)).toBeNull()
    expect(peakToDb(undefined)).toBeNull()
  })

  it('formats dB values with explicit sign', () => {
    expect(formatDb(-5.5)).toEqual('-5.50 dB')
    expect(formatDb(2.3)).toEqual('+2.30 dB')
    expect(formatDb(0, 'dBFS')).toEqual('0.00 dBFS')
    expect(formatDb(null)).toBeNull()
  })
})
