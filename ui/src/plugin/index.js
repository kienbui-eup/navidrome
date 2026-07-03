import { VscExtensions } from 'react-icons/vsc'
import { lazyPage } from '../common/lazyPage'

export default {
  icon: VscExtensions,
  // Admin/plugins-only pages: code-split to shrink initial bundle.
  list: lazyPage(() => import('./PluginList')),
  show: lazyPage(() => import('./PluginShow')),
}
