import stylesheet from './vibeND.css.js'

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
      backgroundColor: '#3FB950 !important',
      color: '#fff',
      borderRadius: 500,
      border: 0,
      '&:hover': {
        transform: 'scale(2.1)',
        backgroundColor: '#3FB950 !important',
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
  themeName: 'VibeND',
  palette: {
    primary: {
      main: '#58A6FF',
    },
    secondary: {
      main: '#3FB950',
    },
    background: {
      default: '#0D1117',
      paper: '#161B22',
    },
    text: {
      primary: '#E6EDF3',
      secondary: '#8B949E',
    },
    type: 'dark',
  },
  overrides: {
    MuiFormGroup: {
      root: {
        color: '#E6EDF3',
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
        backgroundColor: '#21262D !important',
        border: '1px solid transparent',
        borderRadius: 500,
        '&:hover': {
          backgroundColor: `${'#30363D !important'}`,
        },
      },
      label: {
        color: '#E6EDF3',
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
        color: '#E6EDF3',
      },
    },
    MuiDrawer: {
      root: {
        background: '#0D1117',
        paddingTop: '10px',
      },
    },

    MuiList: {
      root: {
        color: '#E6EDF3',
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
        backgroundColor: '#161B22',
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
        color: '#8B949E',
        transition: 'background-color .3s ease',
        '&:hover': {
          backgroundColor: '#1C2128 !important',
        },
        '&:last-child': {
          borderBottom: '1px solid #30363D !important',
        },
      },
      head: {
        color: '#8B949E',
      },
    },
    MuiToolbar: {
      root: {
        backgroundColor: '#161B22 !important',
      },
    },
    MuiTableCell: {
      root: {
        borderBottom: 'none',
        color: '#8B949E !important',
        padding: '10px !important',
      },
      head: {
        borderBottom: '1px solid #0D1117',
        fontSize: '0.75rem',
        textTransform: 'uppercase',
        letterSpacing: 1.2,
        backgroundColor: '#21262D !important',
        color: '#E6EDF3 !important',
      },
      body: {
        color: '#E6EDF3 !important',
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
      positionFixed: {
        backgroundColor: '#161B22 !important',
        boxShadow:
          'rgba(1, 4, 9, 0.25) 0px 4px 6px, rgba(1, 4, 9, 0.1) 0px 5px 7px',
      },
    },
    MuiOutlinedInput: {
      root: {
        borderRadius: '8px',
        '&:hover': {
          borderColor: '#E6EDF3',
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
        backgroundColor: '#21262D',
      },
    },
    NDAlbumGridView: {
      albumName: {
        marginTop: '0.5rem',
        fontWeight: 700,
        textTransform: 'none',
        color: '#E6EDF3',
      },
      albumSubtitle: {
        color: '#8B949E',
      },
      albumContainer: {
        backgroundColor: '#161B22',
        borderRadius: '8px',
        padding: '.75rem',
        transition: 'background-color .3s ease, transform .3s ease',
        '&:hover': {
          backgroundColor: '#1C2128',
          transform: 'translateY(-2px)',
        },
      },
      albumPlayButton: {
        backgroundColor: '#58A6FF',
        borderRadius: '50%',
        boxShadow: '0 8px 8px rgb(0 0 0 / 30%)',
        padding: '0.35rem',
        transition: 'padding .3s ease',
        '&:hover': {
          background: `${'#58A6FF'} !important`,
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
        background: '#161B22',
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
        color: '#E6EDF3',
        fontSize: '0.875rem',
      },
      songTitle: {
        fontWeight: 600,
      },
      songInfo: {
        fontSize: '0.675rem',
        color: '#8B949E',
      },
      player: {
        border: '10px solid #30363D',
        backgroundColor: '#161B22 !important',
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
        border: '1px solid #30363D',
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
        background: '#0D1117',
        backgroundColor: 'rgb(13, 17, 23)',
      },
      root: {
        backgroundColor: '#0D1117',
      },
    },
    RaList: {
      content: {
        backgroundColor: '#0D1117',
        borderRadius: '0px',
      },
    },
    RaListToolbar: {
      toolbar: {
        backgroundColor: '#0D1117',
        padding: '0 .55rem !important',
      },
    },
    RaSidebar: {
      fixed: {
        backgroundColor: '#0D1117',
      },
      drawerPaper: {
        backgroundColor: '#0D1117 !important',
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
        color: '#58A6FF',
      },
    },
    RaPaginationActions: {
      currentPageButton: {
        border: '2px solid rgba(255,255,255,0.25)',
      },
      button: {
        backgroundColor: '#21262D',
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
