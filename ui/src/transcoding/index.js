import { MdTransform } from 'react-icons/md'
import TranscodingEdit from './TranscodingEdit'
import TranscodingCreate from './TranscodingCreate'
import TranscodingShow from './TranscodingShow'
import { lazyPage } from '../common/lazyPage'
import config from '../config'

export default {
  // Admin-only, rarely visited at startup: code-split to shrink initial bundle.
  list: lazyPage(() => import('./TranscodingList')),
  edit: config.enableTranscodingConfig && TranscodingEdit,
  create: config.enableTranscodingConfig && TranscodingCreate,
  show: !config.enableTranscodingConfig && TranscodingShow,
  icon: MdTransform,
}
