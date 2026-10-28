import { makeStyles } from '@material-ui/core/styles'
import { COMPACT_PLAYER_QUERY, PLAYER_HEIGHT } from './layout'

const useStyle = makeStyles(
  (theme) => ({
    audioTitle: {
      textDecoration: 'none',
      color: theme.palette.primary.dark,
    },
    songTitle: {
      fontWeight: 'bold',
      '&:hover + $qualityInfo': {
        opacity: 1,
      },
    },
    songInfo: {
      display: 'block',
      marginTop: '2px',
    },
    songAlbum: {
      fontStyle: 'italic',
      fontSize: 'smaller',
    },
    qualityInfo: {
      marginTop: '-4px',
      opacity: 0,
      transition: 'all 500ms ease-out',
    },
    player: {
      display: (props) => (props.visible ? 'block' : 'none'),
      '&&': { border: 0, backgroundColor: 'transparent !important' },
      '& .music-player-panel': {
        paddingBottom: 'env(safe-area-inset-bottom, 0px)',
        boxSizing: 'border-box',
        minHeight: PLAYER_HEIGHT,
      },
      [`@media ${COMPACT_PLAYER_QUERY}`]: {
        touchAction: 'auto',
        '& .sound-operation': {
          display: 'none',
        },
        '& .react-jinke-music-player': {
          top: 'auto !important',
          left: '0 !important',
          right: 0,
          bottom: 0,
          width: '100%',
          height: PLAYER_HEIGHT,
          transform: 'none !important',
          paddingBottom: 'env(safe-area-inset-bottom, 0px)',
          boxSizing: 'border-box',
          backgroundColor: theme.palette.background.paper,
          borderTop: `1px solid ${theme.palette.divider}`,
          '& .music-player': { width: '100%', height: 80 },
          '& .music-player-controller': {
            position: 'absolute',
            inset: '0 60px 0 0',
            width: 'auto',
            height: 80,
            border: 0,
            borderRadius: 0,
            boxShadow: 'none',
            transform: 'none',
            animation: 'none',
            backgroundSize: '48px 48px',
            backgroundPosition: '16px center',
            backgroundRepeat: 'no-repeat',
            backgroundColor: 'transparent',
            '&::before': { display: 'none' },
          },
          '& .controller-title': {
            display: 'block',
            position: 'absolute',
            left: 80,
            right: 64,
            top: 28,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            textAlign: 'left',
            color: theme.palette.text.primary,
            fontSize: 14,
            pointerEvents: 'none',
          },
          '& .music-player-controller-setting, & .audio-circle-process-bar': {
            display: 'none',
          },
          '& .destroy-btn': {
            position: 'absolute',
            right: 8,
            top: 18,
            width: 44,
            height: 44,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: theme.palette.text.secondary,
            '& svg': { color: 'inherit' },
          },
        },
        '& .react-jinke-music-player-mobile': {
          height: '100dvh',
          boxSizing: 'border-box',
          padding:
            'max(12px, env(safe-area-inset-top, 0px)) max(16px, env(safe-area-inset-right, 0px)) max(12px, env(safe-area-inset-bottom, 0px)) max(16px, env(safe-area-inset-left, 0px))',
          overflowY: 'auto',
          overscrollBehavior: 'contain',
          backgroundColor: theme.palette.background.paper,
          color: theme.palette.text.primary,
          gap: 8,
          '& > .group': { flex: '0 0 auto' },
          '& .react-jinke-music-player-mobile-header': { minHeight: 80 },
          '& .react-jinke-music-player-mobile-header-title': {
            padding: '0 44px',
            minWidth: 0,
          },
          '& .react-jinke-music-player-mobile-header-right': {
            width: 44,
            height: 44,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          },
          '& .react-jinke-music-player-mobile-cover': {
            width: 'min(72vw, max(96px, calc(100dvh - 340px)), 360px)',
            height: 'auto',
            aspectRatio: '1 / 1',
            flexShrink: 0,
            margin: '8px auto',
          },
          '& .react-jinke-music-player-mobile-progress': { minHeight: 44 },
          '& .react-jinke-music-player-mobile-toggle': {
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: 0,
            minHeight: 56,
            gap: 16,
            '& > .group': {
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              minWidth: 48,
              minHeight: 48,
              padding: 0,
            },
          },
          '& .react-jinke-music-player-mobile-operation .item': {
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            minWidth: 44,
            minHeight: 44,
          },
          '@media (max-height:500px) and (min-width:480px)': {
            display: 'grid',
            gridTemplateColumns: 'minmax(120px, 34%) minmax(0, 1fr)',
            gridTemplateRows: 'auto auto auto auto',
            alignContent: 'center',
            '& .react-jinke-music-player-mobile-header': {
              gridColumn: 2,
              gridRow: 1,
            },
            '& .react-jinke-music-player-mobile-cover': {
              gridColumn: 1,
              gridRow: '1 / 5',
              width: 'min(100%, 50dvh)',
            },
            '& .react-jinke-music-player-mobile-progress': {
              gridColumn: 2,
              gridRow: 2,
            },
            '& .react-jinke-music-player-mobile-toggle': {
              gridColumn: 2,
              gridRow: 3,
            },
            '& .react-jinke-music-player-mobile-operation': {
              gridColumn: 2,
              gridRow: 4,
            },
          },
        },
      },
      '@media (prefers-reduced-motion)': {
        '& .music-player-panel .panel-content div.img-rotate': {
          animation: 'none',
        },
      },
      '& .progress-bar-content': {
        display: 'flex',
        flexDirection: 'column',
      },
      '& .play-mode-title': {
        'pointer-events': 'none',
      },
      '& .music-player-panel .panel-content div.img-rotate': {
        // Customize desktop player when cover animation is disabled
        animationDuration: (props) => !props.enableCoverAnimation && '0s',
        borderRadius: (props) => !props.enableCoverAnimation && '0',
        // Fix cover display when image is not square
        backgroundSize: 'contain',
        backgroundPosition: 'center',
      },
      '& .react-jinke-music-player-mobile .react-jinke-music-player-mobile-cover':
        {
          // Customize mobile player when cover animation is disabled
          borderRadius: (props) => !props.enableCoverAnimation && '0',
          // Fix cover display when image is not square
          aspectRatio: '1/1',
          display: 'flex',
        },
      '& .react-jinke-music-player-mobile .react-jinke-music-player-mobile-cover img.cover':
        {
          animationDuration: (props) => !props.enableCoverAnimation && '0s',
          objectFit: 'contain', // Fix cover display when image is not square
        },
      // Hide old singer display
      '& .react-jinke-music-player-mobile .react-jinke-music-player-mobile-singer':
        {
          display: 'none',
        },
      // Hide extra whitespace from switch div
      '& .react-jinke-music-player-mobile .react-jinke-music-player-mobile-switch':
        {
          display: 'none',
        },
      '& .music-player-panel .panel-content .progress-bar-content section.audio-main':
        {
          display: (props) => (props.isRadio ? 'none' : 'inline-flex'),
        },
      '& .react-jinke-music-player-mobile-progress': {
        display: (props) => (props.isRadio ? 'none' : 'flex'),
      },
    },
  }),
  { name: 'NDAudioPlayer' },
)

export default useStyle
