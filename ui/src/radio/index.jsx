import RadioCreate from './RadioCreate'
import RadioEdit from './RadioEdit'
import DynamicMenuIcon from '../layout/DynamicMenuIcon'
import RadioIcon from '@material-ui/icons/Radio'
import RadioOutlinedIcon from '@material-ui/icons/RadioOutlined'
import { lazyPage } from '../common/lazyPage'
import React from 'react'

const all = {
  // Code-split the list to shrink the initial bundle.
  list: lazyPage(() => import('./RadioList')),
  icon: (
    <DynamicMenuIcon
      path={'radio'}
      icon={RadioOutlinedIcon}
      activeIcon={RadioIcon}
    />
  ),
}

const admin = {
  ...all,
  create: RadioCreate,
  edit: RadioEdit,
}

export default { all, admin }
