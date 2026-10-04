import blue from '@material-ui/core/colors/blue'
import stylesheet from './dark.css.js'

export default {
  themeName: 'Dark',
  palette: {
    primary: {
      main: '#A5B4FF',
    },
    secondary: {
      main: '#FF6B4A',
    },
    background: {
      default: '#0A0A0A',
      paper: '#121212',
    },
    type: 'dark',
  },
  overrides: {
    MuiFormGroup: {
      root: {
        color: 'white',
      },
    },
    MuiButton: {
      textPrimary: {
        color: '#A5B4FF',
      },
    },
    NDLogin: {
      systemNameLink: {
        color: '#A5B4FF',
        fontWeight: 'bold',
        letterSpacing: '1px',
      },
      icon: {},
      welcome: {
        color: '#A5B4FF',
      },
      card: {
        minWidth: 340,
        backgroundColor: '#121212f0',
        borderRadius: '16px',
        border: '1px solid rgba(165, 180, 255, 0.15)',
        boxShadow: '0 12px 40px 0 rgba(0, 0, 0, 0.8)',
        backdropFilter: 'blur(10px)',
      },
      avatar: {
        display: 'flex',
        justifyContent: 'center',
        marginTop: '-2.5em',
      },
      button: {
        boxShadow: '0 4px 15px rgba(165, 180, 255, 0.2)',
        background: '#A5B4FF !important',
        color: '#0A0A0A !important',
        fontWeight: 'bold',
        '&:hover': {
          boxShadow: '0 6px 20px rgba(165, 180, 255, 0.4)',
        },
      },
    },
    NDMobileArtistDetails: {
      bgContainer: {
        background:
          'linear-gradient(to bottom, rgba(10 10 10 / 80%), rgb(10 10 10))!important',
      },
    },
  },
  player: {
    theme: 'dark',
    stylesheet,
  },
}
