import { GrDocumentMissing } from 'react-icons/gr'
import { lazyPage } from '../common/lazyPage'
export default {
  // Admin-only page: code-split to shrink initial bundle.
  list: lazyPage(() => import('./MissingFilesList')),
  icon: GrDocumentMissing,
}
