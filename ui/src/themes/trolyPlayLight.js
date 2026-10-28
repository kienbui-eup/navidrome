// Troly Play — theme sáng. Sinh từ brand/tokens.json (Troly v2.2 + mở rộng giải trí):
// canvas #FAF8F2, surface #FFFFFF, raised #F0F2FA, line #DDE0EC, text #20223A, muted #60657B,
// action (Cobalt) #4055E8, play (Coral 700) #B93C1E. Không đổi giá trị ở đây; đổi trong brand/ rồi sinh lại.
import stylesheet from './trolyPlayLight.css.js'

// For Album, Playlist
const musicListActions = {
  alignItems: 'center',
  '& > div > div:first-child > button:first-child': {
    width: 56,
    height: 56,
    minWidth: 56,
    padding: 12,
    margin: 0,
    backgroundColor: '#B93C1E !important',
    color: '#FFFFFF',
    borderRadius: '50%',
    '& .MuiButton-label, & .MuiIconButton-label': { color: '#FFFFFF' },
    '& .MuiButton-label > span': { display: 'none' },
    '@media (max-width:959px)': {
      width: 48,
      height: 48,
      minWidth: 48,
    },
  },
  '& button:not(:first-child)': { color: '#20223A' },
}

export default {
  themeName: 'Troly Play Light',
  typography: {
    fontFamily:
      "'Be Vietnam Pro', 'Inter', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif",
  },
  palette: {
    primary: {
      main: '#4055E8',
    },
    secondary: {
      main: '#B93C1E',
    },
    background: {
      default: '#FAF8F2',
      paper: '#FFFFFF',
    },
    text: {
      primary: '#20223A',
      secondary: '#60657B',
    },
    type: 'light',
    // Troly Play semantic tokens (brand/tokens.json)
    play: { main: '#B93C1E', text: '#B93C1E', soft: '#FFE6DE' },
    quality: {
      hires: '#85500D',
      lossless: '#3345C8',
      highLossy: '#60657B',
      lowLossy: '#60657B',
    },
  },
  overrides: {
    MuiFormGroup: {
      root: {
        color: '#20223A',
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
        backgroundColor: '#F0F2FA',
        border: '1px solid transparent',
        borderRadius: 500,
        textTransform: 'none',
        fontWeight: 500,
        '&:hover': {
          backgroundColor: '#DDE0EC',
        },
      },
      // Troly: one primary action per screen, in `action` (Cobalt / Mist)
      containedPrimary: {
        backgroundColor: '#4055E8',
        '& $label': { color: '#FFFFFF' },
        '&:hover': { backgroundColor: '#3345C8' },
      },
      label: {
        color: '#20223A',
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
      root: { color: '#20223A' },
      label: {
        color: 'inherit',
      },
    },
    MuiDrawer: {
      root: {
        background: '#FAF8F2',
        paddingTop: '10px',
      },
    },

    MuiList: {
      root: {
        color: '#20223A',
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
        backgroundColor: '#FFFFFF',
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
        color: '#60657B',
        transition: 'background-color .3s ease',
        '&:hover': {
          backgroundColor: '#F0F2FA !important',
        },
        '&:last-child': {
          borderBottom: '1px solid #DDE0EC !important',
        },
      },
      head: {
        color: '#60657B',
      },
    },
    MuiToolbar: {
      root: {
        backgroundColor: '#FFFFFF !important',
      },
    },
    MuiTableCell: {
      root: {
        borderBottom: 'none',
        color: '#60657B !important',
        padding: '10px !important',
      },
      head: {
        borderBottom: '1px solid #FAF8F2',
        fontSize: '0.75rem',
        textTransform: 'uppercase',
        letterSpacing: 1.2,
        backgroundColor: '#F0F2FA !important',
        color: '#20223A !important',
      },
      body: {
        color: '#20223A !important',
      },
    },
    MuiSwitch: {
      track: {
        width: '89%',
        transform: 'translateX(.1rem) scale(140%)',
        opacity: '0.7 !important',
        backgroundColor: 'rgba(32,34,58,0.25)',
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
        color: '#20223A',
      },
      positionFixed: {
        backgroundColor: '#FFFFFF !important',
        color: '#20223A',
        boxShadow:
          '0 1px 2px rgba(32, 34, 58, 0.06), 0 4px 12px rgba(32, 34, 58, 0.08)',
      },
    },
    MuiOutlinedInput: {
      root: {
        borderRadius: '8px',
        '&:hover': {
          borderColor: '#20223A',
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
        backgroundColor: '#F0F2FA',
      },
    },
    NDAlbumGridView: {
      albumName: {
        marginTop: '0.5rem',
        fontWeight: 700,
        textTransform: 'none',
        color: '#20223A',
      },
      albumSubtitle: {
        color: '#60657B',
      },
      albumContainer: {
        backgroundColor: '#FFFFFF',
        borderRadius: '8px',
        padding: '.75rem',
        transition: 'background-color .3s ease, transform .3s ease',
        '&:hover': {
          backgroundColor: '#F0F2FA',
          transform: 'translateY(-2px)',
        },
      },
      albumPlayButton: {
        backgroundColor: '#B93C1E',
        color: '#FFFFFF',
        borderRadius: '50%',
        boxShadow: '0 8px 16px rgba(32, 34, 58, 0.12)',
        padding: '0.35rem',
        transition: 'padding .3s ease',
        '&:hover': {
          background: '#B93C1E !important',
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
        color: '#20223A',
      },
      details: {
        fontSize: '.875rem',
        color: '#60657B',
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
        background: '#FFFFFF',
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
        color: '#60657B',
      },
    },
    NDCollapsibleComment: {
      commentBlock: {
        fontSize: '.875rem',
        color: '#60657B',
      },
    },
    NDAudioPlayer: {
      audioTitle: {
        color: '#20223A',
        fontSize: '0.875rem',
      },
      songTitle: {
        fontWeight: 600,
      },
      songInfo: {
        fontSize: '0.675rem',
        color: '#60657B',
      },
      player: {
        border: '10px solid #DDE0EC',
        backgroundColor: '#FFFFFF !important',
      },
    },
    NDLogin: {
      systemNameLink: {
        color: '#20223A',
      },
      card: {
        border: '1px solid #DDE0EC',
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
        background: '#FAF8F2',
        backgroundColor: '#FAF8F2',
      },
      root: {
        backgroundColor: '#FAF8F2',
      },
    },
    RaList: {
      content: {
        backgroundColor: '#FAF8F2',
        borderRadius: '0px',
      },
    },
    RaListToolbar: {
      toolbar: {
        backgroundColor: '#FAF8F2',
        padding: '0 .55rem !important',
      },
    },
    RaSidebar: {
      fixed: {
        backgroundColor: '#FAF8F2',
      },
      drawerPaper: {
        backgroundColor: '#FAF8F2 !important',
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
        color: '#4055E8',
      },
    },
    RaPaginationActions: {
      currentPageButton: {
        border: '2px solid rgba(32,34,58,0.25)',
      },
      button: {
        backgroundColor: '#F0F2FA',
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
    theme: 'light',
    stylesheet,
  },
}
