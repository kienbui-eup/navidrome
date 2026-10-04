// Troly Play — theme tối (mặc định). Sinh từ brand/tokens.json (Troly v2.2 + mở rộng giải trí):
// canvas #111322, surface #1B1E32, raised #252A43, line #363C56, text #F3F4FC, muted #AEB5CC,
// action (Mist) #A5B4FF, play (Coral) #FF6B4A. Không đổi giá trị ở đây; đổi trong brand/ rồi sinh lại.
import stylesheet from './trolyPlay.css.js'

// For Album, Playlist
const musicListActions = {
  alignItems: 'center',
  '@global': {
    'button:first-child:not(:only-child)': {
      '@media screen and (max-width: 720px)': {
        transform: 'scale(1.5)',
        margin: '1rem',
        '&:hover': {
          transform: 'scale(1.6) !important',
        },
      },
      transform: 'scale(2)',
      margin: '1.5rem',
      minWidth: 0,
      padding: 5,
      transition: 'transform .3s ease',
      backgroundColor: '#FF6B4A !important',
      color: '#fff',
      borderRadius: 500,
      border: 0,
      '&:hover': {
        transform: 'scale(2.1)',
        backgroundColor: '#FF6B4A !important',
        border: 0,
      },
    },
    'button:only-child': {
      margin: '1.5rem',
    },
    'button:first-child>span:first-child': {
      padding: 0,
    },
    'button:first-child>span:first-child>span': {
      display: 'none',
    },
    'button>span:first-child>span, button:not(:first-child)>span:first-child>svg':
      {
        color: 'rgba(255, 255, 255, 0.8)',
      },
  },
}

export default {
  themeName: 'Troly Play',
  typography: {
    fontFamily:
      "'Be Vietnam Pro', 'Inter', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif",
  },
  palette: {
    primary: {
      main: '#A5B4FF',
    },
    secondary: {
      main: '#FF6B4A',
    },
    background: {
      default: '#111322',
      paper: '#1B1E32',
    },
    text: {
      primary: '#F3F4FC',
      secondary: '#AEB5CC',
    },
    type: 'dark',
    // Troly Play semantic tokens (brand/tokens.json)
    play: { main: '#FF6B4A', text: '#FFB49E', soft: '#2A1712' },
    stage: { main: '#0B0C18' },
    quality: {
      hires: '#F2B59B',
      lossless: '#A5B4FF',
      highLossy: '#AEB5CC',
      lowLossy: '#AEB5CC',
    },
  },
  overrides: {
    MuiFormGroup: {
      root: {
        color: '#F3F4FC',
      },
    },
    MuiMenuItem: {
      root: {
        fontSize: '0.875rem',
        paddingTop: '4px',
        paddingBottom: '4px',
        paddingLeft: '10px',
        margin: '5px',
        borderRadius: '8px',
      },
    },
    MuiDivider: {
      root: {
        margin: '.75rem 0',
      },
    },
    MuiButton: {
      root: {
        backgroundColor: '#252A43',
        border: '1px solid transparent',
        borderRadius: 500,
        textTransform: 'none',
        fontWeight: 500,
        '&:hover': {
          backgroundColor: '#363C56',
        },
      },
      // Troly: one primary action per screen, in `action` (Cobalt / Mist)
      containedPrimary: {
        backgroundColor: '#A5B4FF',
        '& $label': { color: '#111322' },
        '&:hover': { backgroundColor: '#B7C3FF' },
      },
      label: {
        color: '#F3F4FC',
        paddingRight: '1rem',
        paddingLeft: '0.7rem',
      },
      contained: {
        boxShadow: 'none',
        '&:hover': {
          boxShadow: 'none',
        },
      },
    },
    MuiIconButton: {
      label: {
        color: '#F3F4FC',
      },
    },
    MuiDrawer: {
      root: {
        background: '#111322',
        paddingTop: '10px',
      },
    },

    MuiList: {
      root: {
        color: '#F3F4FC',
        background: 'none',
      },
    },
    MuiListItem: {
      button: {
        transition: 'background-color .1s ease !important',
      },
    },
    MuiPaper: {
      root: {
        backgroundColor: '#1B1E32',
      },
      rounded: {
        borderRadius: '8px',
      },
      elevation1: {
        boxShadow: 'none',
      },
    },
    MuiTableRow: {
      root: {
        color: '#AEB5CC',
        transition: 'background-color .3s ease',
        '&:hover': {
          backgroundColor: '#252A43 !important',
        },
        '&:last-child': {
          borderBottom: '1px solid #363C56 !important',
        },
      },
      head: {
        color: '#AEB5CC',
      },
    },
    MuiToolbar: {
      root: {
        backgroundColor: '#1B1E32 !important',
      },
    },
    MuiTableCell: {
      root: {
        borderBottom: 'none',
        color: '#AEB5CC !important',
        padding: '10px !important',
      },
      head: {
        borderBottom: '1px solid #111322',
        fontSize: '0.75rem',
        textTransform: 'uppercase',
        letterSpacing: 1.2,
        backgroundColor: '#252A43 !important',
        color: '#F3F4FC !important',
      },
      body: {
        color: '#F3F4FC !important',
      },
    },
    MuiSwitch: {
      track: {
        width: '89%',
        transform: 'translateX(.1rem) scale(140%)',
        opacity: '0.7 !important',
        backgroundColor: 'rgba(255,255,255,0.25)',
      },
      thumb: {
        transform: 'scale(60%)',
        boxShadow: 'none',
      },
    },
    RaToolBar: {
      regular: {
        backgroundColor: 'none !important',
      },
    },
    MuiAppBar: {
      colorSecondary: {
        color: '#F3F4FC',
      },
      positionFixed: {
        backgroundColor: '#1B1E32 !important',
        color: '#F3F4FC',
        boxShadow: 'none',
      },
    },
    MuiOutlinedInput: {
      root: {
        borderRadius: '8px',
        '&:hover': {
          borderColor: '#F3F4FC',
        },
      },
      notchedOutline: {
        transition: 'border-color .1s',
      },
    },
    MuiSelect: {
      select: {
        '&:focus': {
          borderRadius: '8px',
        },
      },
    },
    MuiChip: {
      root: {
        backgroundColor: '#252A43',
      },
    },
    NDAlbumGridView: {
      albumName: {
        marginTop: '0.5rem',
        fontWeight: 700,
        textTransform: 'none',
        color: '#F3F4FC',
      },
      albumSubtitle: {
        color: '#AEB5CC',
      },
      albumContainer: {
        backgroundColor: '#1B1E32',
        borderRadius: '8px',
        padding: '.75rem',
        transition: 'background-color .3s ease, transform .3s ease',
        '&:hover': {
          backgroundColor: '#252A43',
          transform: 'translateY(-2px)',
        },
      },
      albumPlayButton: {
        backgroundColor: '#A5B4FF',
        borderRadius: '50%',
        boxShadow: '0 8px 8px rgb(0 0 0 / 30%)',
        padding: '0.35rem',
        transition: 'padding .3s ease',
        '&:hover': {
          background: `${'#A5B4FF'} !important`,
          padding: '0.45rem',
        },
      },
    },
    NDPlaylistDetails: {
      container: {
        borderRadius: 0,
        paddingTop: '2.5rem !important',
        boxShadow: 'none',
      },
      title: {
        fontSize: 'calc(1.5rem + 1.5vw);',
        fontWeight: 700,
        color: '#fff',
      },
      details: {
        fontSize: '.875rem',
        color: 'rgba(255,255,255, 0.8)',
      },
    },
    NDAlbumShow: {
      albumActions: musicListActions,
    },
    NDPlaylistShow: {
      playlistActions: musicListActions,
    },
    NDAlbumDetails: {
      root: {
        background: '#1B1E32',
        borderRadius: 0,
        boxShadow: '0 8px 8px rgb(0 0 0 / 20%)',
      },
      cardContents: {
        alignItems: 'center',
        paddingTop: '1.5rem',
      },
      recordName: {
        fontSize: 'calc(1rem + 1.5vw);',
        fontWeight: 700,
      },
      recordArtist: {
        fontSize: '.875rem',
        fontWeight: 700,
      },
      recordMeta: {
        fontSize: '.875rem',
        color: 'rgba(255,255,255, 0.8)',
      },
    },
    NDCollapsibleComment: {
      commentBlock: {
        fontSize: '.875rem',
        color: 'rgba(255,255,255, 0.8)',
      },
    },
    NDAudioPlayer: {
      audioTitle: {
        color: '#F3F4FC',
        fontSize: '0.875rem',
      },
      songTitle: {
        fontWeight: 600,
      },
      songInfo: {
        fontSize: '0.675rem',
        color: '#AEB5CC',
      },
      player: {
        border: '10px solid #363C56',
        backgroundColor: '#1B1E32 !important',
      },
    },
    NDLogin: {
      main: {
        boxShadow: 'inset 0 0 0 2000px rgba(0, 0, 0, .75)',
      },
      systemNameLink: {
        color: '#fff',
      },
      card: {
        border: '1px solid #363C56',
      },
      avatar: {
        marginBottom: 0,
      },
    },
    NDSubMenu: {
      sidebarIsClosed: {
        '& a': {
          paddingLeft: '10px',
        },
      },
    },
    RaLayout: {
      content: {
        padding: '0 !important',
        background: '#111322',
        backgroundColor: '#111322',
      },
      root: {
        backgroundColor: '#111322',
      },
    },
    RaList: {
      content: {
        backgroundColor: '#111322',
        borderRadius: '0px',
      },
    },
    RaListToolbar: {
      toolbar: {
        backgroundColor: '#111322',
        padding: '0 .55rem !important',
      },
    },
    RaSidebar: {
      fixed: {
        backgroundColor: '#111322',
      },
      drawerPaper: {
        backgroundColor: '#111322 !important',
      },
    },
    RaSearchInput: {
      input: {
        paddingLeft: '.9rem',
        marginTop: '36px',
        border: 0,
      },
    },
    RaDatagrid: {
      headerCell: {
        '&:first-child': {
          borderTopLeftRadius: '0px !important',
        },
        '&:last-child': {
          borderTopRightRadius: '0px !important',
        },
      },
    },
    RaButton: {
      button: {
        margin: '0 5px 0 5px',
      },
    },
    RaLink: {
      link: {
        color: '#A5B4FF',
      },
    },
    RaPaginationActions: {
      currentPageButton: {
        border: '2px solid rgba(255,255,255,0.25)',
      },
      button: {
        backgroundColor: '#252A43',
        minWidth: 48,
        margin: '0 4px',
        '@global': {
          '> .MuiButton-label': {
            padding: 0,
          },
        },
      },
      actions: {
        '@global': {
          '.next-page': {
            marginLeft: 8,
            marginRight: 8,
          },
          '.previous-page': {
            marginRight: 8,
          },
        },
      },
    },
  },
  player: {
    theme: 'dark',
    stylesheet,
  },
}
