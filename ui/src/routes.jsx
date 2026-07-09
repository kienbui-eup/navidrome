import React from 'react'
import { Route } from 'react-router-dom'
import Personal from './personal/Personal'
import ImportMusic from './import/ImportMusic'
import UpgradeQuality from './upgrade/UpgradeQuality'
import ServerStatus from './server/ServerStatus'

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
]

export default routes
