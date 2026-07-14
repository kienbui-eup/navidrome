import React from 'react'
import { Route } from 'react-router-dom'
import Personal from './personal/Personal'
import ImportMusic from './import/ImportMusic'
import UpgradeQuality from './upgrade/UpgradeQuality'
import ServerStatus from './server/ServerStatus'
import MetadataDashboard from './import/MetadataDashboard'
import MusicTrends from './import/MusicTrends'
import AlbumList from './album/AlbumList'

const routes = [
  <Route exact path="/personal" render={() => <Personal />} key={'personal'} />,
  <Route exact path="/import" render={() => <ImportMusic />} key={'import'} />,
  <Route
    exact
    path="/upgrade"
    render={() => <UpgradeQuality />}
    key={'upgrade'}
  />,
  <Route exact path="/server" render={() => <ServerStatus />} key={'server'} />,
  <Route
    exact
    path="/metadata-dashboard"
    render={() => <MetadataDashboard />}
    key={'metadata-dashboard'}
  />,
  <Route exact path="/trends" render={() => <MusicTrends />} key={'trends'} />,
  <Route exact path="/album/all" render={(props) => <AlbumList {...props} />} key={'album-all'} />,
  <Route exact path="/album/random" render={(props) => <AlbumList {...props} />} key={'album-random'} />,
  <Route exact path="/album/starred" render={(props) => <AlbumList {...props} />} key={'album-starred'} />,
  <Route exact path="/album/topRated" render={(props) => <AlbumList {...props} />} key={'album-topRated'} />,
  <Route exact path="/album/recentlyAdded" render={(props) => <AlbumList {...props} />} key={'album-recentlyAdded'} />,
  <Route exact path="/album/recentlyPlayed" render={(props) => <AlbumList {...props} />} key={'album-recentlyPlayed'} />,
  <Route exact path="/album/mostPlayed" render={(props) => <AlbumList {...props} />} key={'album-mostPlayed'} />,
]

export default routes

