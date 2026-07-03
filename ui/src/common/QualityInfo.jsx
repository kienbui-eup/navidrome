import React, { useMemo } from 'react'
import PropTypes from 'prop-types'
import Chip from '@material-ui/core/Chip'
import { useTranslate } from 'react-admin'
import config from '../config'
import { makeStyles } from '@material-ui/core'
import clsx from 'clsx'
import { calculateGain } from '../utils/calculateReplayGain'

const llFormats = new Set(config.losslessFormats.split(','))
const placeholder = 'N/A'

// Quality tiers -> chip accent color (req: green lossless/hi-res, blue high
// lossy >=256kbps, yellow low lossy <256kbps, undefined/grey when unknown).
const QUALITY_COLORS = {
  lossless: '#3FB950',
  highLossy: '#58A6FF',
  lowLossy: '#E3B341',
}

const getQualityColor = ({ suffix, bitRate, bitDepth, isLossless }) => {
  if (isLossless || bitDepth > 16) return QUALITY_COLORS.lossless
  if (suffix && llFormats.has(suffix)) return QUALITY_COLORS.lossless
  if (bitRate > 0) {
    return bitRate >= 256 ? QUALITY_COLORS.highLossy : QUALITY_COLORS.lowLossy
  }
  return undefined
}

const useStyle = makeStyles(
  (theme) => ({
    chip: {
      transform: 'scale(0.8)',
    },
  }),
  {
    name: 'NDQualityInfo',
  },
)

export const QualityInfo = ({
  record,
  size,
  gainMode,
  preAmp,
  className,
  transcodeStream,
  isDirectPlay,
}) => {
  const classes = useStyle()
  const translate = useTranslate()
  let {
    suffix,
    bitRate,
    bitDepth,
    sampleRate,
    channels,
    isRadio,
    rgAlbumGain,
    rgAlbumPeak,
    rgTrackGain,
    rgTrackPeak,
  } = record
  let info = placeholder

  if (suffix) {
    suffix = suffix.toUpperCase()
    info = suffix
    if (!llFormats.has(suffix) && bitRate > 0) {
      info += ' ' + bitRate
    }
  }

  // Color reflects the quality actually delivered: source by default...
  let qualityColor = getQualityColor({ suffix, bitRate, bitDepth })

  // Show transcode target when transcoding (not direct play)
  if (transcodeStream && !isDirectPlay) {
    const targetCodec = (transcodeStream.codec || '').toUpperCase()
    const targetBitrate = transcodeStream.audioBitrate
      ? Math.round(transcodeStream.audioBitrate / 1000)
      : 0
    let targetInfo = targetCodec
    if (targetBitrate > 0) {
      targetInfo += ' ' + targetBitrate
    }
    const sourceSuffix = suffix || placeholder
    info = `${sourceSuffix} → ${targetInfo}`
    // ...but when transcoding, the target format/bitrate is what the user hears.
    qualityColor = getQualityColor({
      suffix: targetCodec,
      bitRate: targetBitrate,
      isLossless: llFormats.has(targetCodec),
    })
  }

  const extra = useMemo(() => {
    if (gainMode !== 'none') {
      const gainValue = calculateGain(
        { gainMode, preAmp },
        { rgAlbumGain, rgAlbumPeak, rgTrackGain, rgTrackPeak },
      )
      // convert normalized gain (after peak) back to dB
      const toDb = (Math.log10(gainValue) * 20).toFixed(2)
      return ` (${toDb} dB)`
    }

    return ''
  }, [gainMode, preAmp, rgAlbumGain, rgAlbumPeak, rgTrackGain, rgTrackPeak])

  // Radio streams have no reliable quality metadata: don't show a badge.
  if (isRadio) {
    return null
  }

  // Full technical breakdown shown on hover (req: sample rate, bit depth,
  // bitrate, channels, and direct-play vs transcoded status).
  const details = []
  if (suffix) details.push(suffix)
  if (sampleRate > 0) details.push(`${(sampleRate / 1000).toFixed(1)} kHz`)
  if (bitDepth > 0) details.push(`${bitDepth}-bit`)
  if (bitRate > 0) details.push(`${bitRate} kbps`)
  if (channels > 0) {
    details.push(
      channels === 1 ? 'Mono' : channels === 2 ? 'Stereo' : `${channels}ch`,
    )
  }
  if (transcodeStream && !isDirectPlay) {
    details.push(translate('player.transcoded', { _: 'Transcoded' }))
  } else if (isDirectPlay) {
    details.push(translate('player.directPlay', { _: 'Direct Play' }))
  }
  const title = details.join(' • ') || undefined

  return (
    <Chip
      className={clsx(classes.chip, className)}
      variant="outlined"
      size={size}
      label={`${info}${extra}`}
      title={title}
      style={
        qualityColor
          ? { borderColor: qualityColor, color: qualityColor }
          : undefined
      }
    />
  )
}

QualityInfo.propTypes = {
  record: PropTypes.object.isRequired,
  size: PropTypes.string,
  className: PropTypes.string,
  gainMode: PropTypes.string,
  transcodeStream: PropTypes.object,
  isDirectPlay: PropTypes.bool,
}

QualityInfo.defaultProps = {
  record: {},
  size: 'small',
  gainMode: 'none',
}
