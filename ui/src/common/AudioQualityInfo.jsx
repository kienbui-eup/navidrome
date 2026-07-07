import React from 'react'
import PropTypes from 'prop-types'
import Chip from '@material-ui/core/Chip'
import Typography from '@material-ui/core/Typography'
import { makeStyles } from '@material-ui/core/styles'
import { useTranslate } from 'react-admin'
import config from '../config'
import {
  analyzeAudioQuality,
  TIER_COLORS,
  formatDb,
  peakToDb,
} from '../utils/audioQuality'

const useStyles = makeStyles(
  (theme) => ({
    root: {
      border: `1px solid ${theme.palette.divider}`,
      borderRadius: theme.shape.borderRadius,
      padding: theme.spacing(1.5, 2),
      margin: theme.spacing(1, 2, 2),
    },
    header: {
      display: 'flex',
      alignItems: 'center',
      flexWrap: 'wrap',
      gap: theme.spacing(1),
      marginBottom: theme.spacing(1.5),
    },
    badge: {
      fontWeight: 600,
    },
    tierName: {
      color: theme.palette.text.secondary,
    },
    grid: {
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))',
      gap: theme.spacing(1.5),
    },
    label: {
      display: 'block',
      color: theme.palette.text.secondary,
      textTransform: 'uppercase',
      letterSpacing: '0.05em',
    },
    value: {
      fontWeight: 500,
      fontVariantNumeric: 'tabular-nums',
    },
    subValue: {
      display: 'block',
      color: theme.palette.text.secondary,
      fontVariantNumeric: 'tabular-nums',
    },
    warning: {
      color: theme.palette.warning?.main || '#E3B341',
    },
    gainSection: {
      marginTop: theme.spacing(1.5),
      paddingTop: theme.spacing(1),
      borderTop: `1px dashed ${theme.palette.divider}`,
    },
  }),
  { name: 'NDAudioQualityInfo' },
)

const SpecItem = ({ classes, label, value, subValue, subValueClass }) =>
  value == null ? null : (
    <div>
      <Typography variant="caption" className={classes.label}>
        {label}
      </Typography>
      <Typography variant="body2" component="span" className={classes.value}>
        {value}
      </Typography>
      {subValue && (
        <Typography
          variant="caption"
          className={`${classes.subValue}${subValueClass ? ` ${subValueClass}` : ''}`}
        >
          {subValue}
        </Typography>
      )}
    </div>
  )

export const AudioQualityInfo = ({ record }) => {
  const classes = useStyles()
  const translate = useTranslate()
  const quality = analyzeAudioQuality(record)
  const t = (key, defaultValue, options) =>
    translate(`resources.song.audioQuality.${key}`, {
      _: defaultValue,
      ...options,
    })

  const tierColor = TIER_COLORS[quality.tier]
  const tierNames = {
    dsd: t('tierDsd', '1-bit Direct Stream Digital'),
    hiRes: t('tierHiRes', 'High-resolution lossless'),
    cd: t('tierCd', 'CD-quality lossless'),
    lossyHigh: t('tierLossyHigh', 'Lossy, high bitrate'),
    lossy: t('tierLossy', 'Lossy'),
  }

  const formatLabel =
    quality.suffix &&
    quality.codec &&
    quality.codec !== quality.suffix.toLowerCase()
      ? `${quality.suffix} · ${quality.codec.toUpperCase()}`
      : quality.suffix || null

  const compressionNote =
    quality.compressionPct != null
      ? t('compression', '%{pct}% of %{pcm} kbps uncompressed', {
          pct: quality.compressionPct,
          pcm: quality.uncompressedRate,
        })
      : null

  const trackGain = record.rgTrackGain
  const albumGain = record.rgAlbumGain
  const trackPeakDb = peakToDb(record.rgTrackPeak)
  const albumPeakDb = peakToDb(record.rgAlbumPeak)
  const hasGainData =
    trackGain != null ||
    albumGain != null ||
    trackPeakDb != null ||
    albumPeakDb != null
  const clippingRisk =
    trackGain != null && trackPeakDb != null && trackGain + trackPeakDb > 0

  return (
    <div className={classes.root} data-testid="audio-quality-info">
      <div className={classes.header}>
        <Chip
          size="small"
          variant="outlined"
          className={classes.badge}
          label={quality.badge}
          style={
            tierColor ? { borderColor: tierColor, color: tierColor } : undefined
          }
        />
        {tierNames[quality.tier] && (
          <Typography variant="caption" className={classes.tierName}>
            {tierNames[quality.tier]}
          </Typography>
        )}
      </div>
      <div className={classes.grid}>
        <SpecItem
          classes={classes}
          label={t('format', 'Format')}
          value={formatLabel}
        />
        <SpecItem
          classes={classes}
          label={translate('resources.song.fields.sampleRate', {
            _: 'Sample rate',
          })}
          value={quality.sampleRateLabel}
        />
        <SpecItem
          classes={classes}
          label={translate('resources.song.fields.bitDepth', {
            _: 'Bit depth',
          })}
          value={quality.bitDepthLabel}
        />
        <SpecItem
          classes={classes}
          label={translate('resources.song.fields.channels', { _: 'Channels' })}
          value={quality.channelsLabel}
        />
        <SpecItem
          classes={classes}
          label={translate('resources.song.fields.bitRate', { _: 'Bit rate' })}
          value={quality.bitRateLabel}
          subValue={compressionNote}
        />
      </div>
      {config.enableReplayGain && hasGainData && (
        <div className={`${classes.grid} ${classes.gainSection}`}>
          <SpecItem
            classes={classes}
            label={translate('resources.song.fields.trackGain', {
              _: 'Track gain',
            })}
            value={formatDb(trackGain)}
          />
          <SpecItem
            classes={classes}
            label={t('trackPeak', 'Track peak')}
            value={formatDb(trackPeakDb, 'dBFS')}
            subValue={
              clippingRisk
                ? t('clippingRisk', 'May clip with ReplayGain applied')
                : null
            }
            subValueClass={classes.warning}
          />
          <SpecItem
            classes={classes}
            label={translate('resources.song.fields.albumGain', {
              _: 'Album gain',
            })}
            value={formatDb(albumGain)}
          />
          <SpecItem
            classes={classes}
            label={t('albumPeak', 'Album peak')}
            value={formatDb(albumPeakDb, 'dBFS')}
          />
        </div>
      )}
    </div>
  )
}

AudioQualityInfo.propTypes = {
  record: PropTypes.object.isRequired,
}

AudioQualityInfo.defaultProps = {
  record: {},
}
