import React from 'react'
import { Route } from 'react-router-dom'
import Personal from './personal/Personal'
import ImportMusic from './import/ImportMusic'
import UpgradeQuality from './upgrade/UpgradeQuality'
import ServerStatus from './server/ServerStatus'
import MetadataDashboard from './import/MetadataDashboard'
import MusicTrends from './import/MusicTrends'

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
]

export default routes
