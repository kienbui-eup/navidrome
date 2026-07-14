import ReactGA from 'react-ga'
import { Provider } from 'react-redux'
import { createHashHistory } from 'history'
import {
  Admin as RAAdmin,
  Resource,
  useSetLocale,
  useRefresh,
} from 'react-admin'
import { HotKeys } from 'react-hotkeys'
import dataProvider from './dataProvider'
import authProvider from './authProvider'
import { Layout, Login, Logout } from './layout'
import transcoding from './transcoding'
import player from './player'
import device from './device'
import user from './user'
import song from './song'
import album from './album'
import artist from './artist'
import playlist from './playlist'
import radio from './radio'
import { Player } from './audioplayer'
import share from './share'
import library from './library'
import plugin from './plugin'
import customRoutes from './routes'

// Import Material UI Icons for admin navigation sidebar
import LibraryMusicIcon from '@material-ui/icons/LibraryMusic'
import PeopleIcon from '@material-ui/icons/People'
import DevicesIcon from '@material-ui/icons/Devices'
import HeadsetIcon from '@material-ui/icons/Headset'
import TransformIcon from '@material-ui/icons/Transform'
import ErrorOutlineIcon from '@material-ui/icons/ErrorOutline'
import ExtensionIcon from '@material-ui/icons/Extension'
import ShareIcon from '@material-ui/icons/Share'
import {
  libraryReducer,
  themeReducer,
  addToPlaylistDialogReducer,
  expandInfoDialogReducer,
  listenBrainzTokenDialogReducer,
  saveQueueDialogReducer,
  deleteMediaDialogReducer,
  playerReducer,
  albumViewReducer,
  activityReducer,
  settingsReducer,
  replayGainReducer,
  downloadMenuDialogReducer,
  shareDialogReducer,
  transcodingReducer,
} from './reducers'
import createAdminStore from './store/createAdminStore'
import { i18nProvider, retrieveTranslation } from './i18n'
import config, { shareInfo } from './config'
import { keyMap } from './hotkeys'
import useChangeThemeColor from './useChangeThemeColor'
import SharePlayer from './share/SharePlayer'
import { HTML5Backend } from 'react-dnd-html5-backend'
import { DndProvider } from 'react-dnd'
import missing from './missing/index.js'
import { useEffect } from 'react'

const history = createHashHistory()

if (config.gaTrackingId) {
  ReactGA.initialize(config.gaTrackingId)
  history.listen((location) => {
    ReactGA.pageview(location.pathname)
  })
  ReactGA.pageview(window.location.pathname)
}

const adminStore = createAdminStore({
  authProvider,
  dataProvider,
  history,
  customReducers: {
    library: libraryReducer,
    player: playerReducer,
    albumView: albumViewReducer,
    theme: themeReducer,
    addToPlaylistDialog: addToPlaylistDialogReducer,
    downloadMenuDialog: downloadMenuDialogReducer,
    expandInfoDialog: expandInfoDialogReducer,
    listenBrainzTokenDialog: listenBrainzTokenDialogReducer,
    saveQueueDialog: saveQueueDialogReducer,
    shareDialog: shareDialogReducer,
    deleteMediaDialog: deleteMediaDialogReducer,
    activity: activityReducer,
    settings: settingsReducer,
    replayGain: replayGainReducer,
    transcoding: transcodingReducer,
  },
})

const App = () => (
  <Provider store={adminStore}>
    <Admin />
  </Provider>
)

const Admin = (props) => {
  const setLocale = useSetLocale()
  const refresh = useRefresh()
  useEffect(() => {
    if (config.defaultLanguage !== '' && !localStorage.getItem('locale')) {
      retrieveTranslation(config.defaultLanguage)
        .then(() => setLocale(config.defaultLanguage))
        .then(() => {
          localStorage.setItem('locale', config.defaultLanguage)
          refresh(true)
        })
        .catch((e) => {
          // eslint-disable-next-line no-console
          console.error(
            'Cannot load language "' + config.defaultLanguage + '": ' + e,
          )
        })
    }
  }, [setLocale, refresh])
  useChangeThemeColor()
  /* eslint-disable react/jsx-key */
  return (
    <RAAdmin
      disableTelemetry
      dataProvider={dataProvider}
      authProvider={authProvider}
      i18nProvider={i18nProvider}
      customRoutes={customRoutes}
      history={history}
      layout={Layout}
      loginPage={Login}
      logoutButton={Logout}
      {...props}
    >
      {(permissions) => [
        <Resource name="album" {...album} options={{ subMenu: 'albumList' }} />,
        <Resource name="artist" {...artist} />,
        <Resource name="song" {...song} />,
        <Resource
          name="radio"
          {...(permissions === 'admin' ? radio.admin : radio.all)}
        />,
        <Resource
          name="playlist"
          {...playlist}
          options={{ subMenu: 'playlist' }}
        />,
        permissions === 'admin' ? (
          <Resource
            name="library"
            icon={LibraryMusicIcon}
            {...library}
          />
        ) : null,
        <Resource name="user" icon={PeopleIcon} {...user} />,
        <Resource
          name="player"
          icon={HeadsetIcon}
          {...player}
        />,
        <Resource
          name="device"
          icon={DevicesIcon}
          {...device}
        />,
        permissions === 'admin' ? (
          <Resource
            name="transcoding"
            icon={TransformIcon}
            {...transcoding}
          />
        ) : (
          <Resource name="transcoding" />
        ),
        permissions === 'admin' ? (
          <Resource
            name="missing"
            icon={ErrorOutlineIcon}
            {...missing}
          />
        ) : null,
        permissions === 'admin' && config.pluginsEnabled ? (
          <Resource
            name="plugin"
            icon={ExtensionIcon}
            {...plugin}
          />
        ) : null,
        config.enableSharing && <Resource name="share" icon={ShareIcon} {...share} />,

        <Resource name="translation" />,
        <Resource name="genre" />,
        <Resource name="tag" />,
        <Resource name="playlistTrack" />,
        <Resource name="keepalive" />,
        <Resource name="insights" />,
        <Resource name="config" />,
        <Player />,
      ]}
    </RAAdmin>
  )
  /* eslint-enable react/jsx-key */
}

const AppWithHotkeys = () => {
  let language = localStorage.getItem('locale') || 'en'
  document.documentElement.lang = language
  if (config.enableSharing && shareInfo) {
    return <SharePlayer />
  }
  return (
    <HotKeys keyMap={keyMap}>
      <DndProvider backend={HTML5Backend}>
        <App />
      </DndProvider>
    </HotKeys>
  )
}

export default AppWithHotkeys
