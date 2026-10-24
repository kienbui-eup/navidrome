import React from 'react'
import { useTheme } from '@material-ui/core/styles'
import WordmarkLight from '../icons/trolyplay-wordmark.svg'
import WordmarkInk from '../icons/trolyplay-wordmark-ink.svg'
import Symbol from '../icons/android-icon-192x192.png'
import { APP_NAME } from '../consts'

const BrandLogo = ({ compact = false, className }) => {
  const theme = useTheme()
  const wordmark = theme.palette.type === 'dark' ? WordmarkLight : WordmarkInk
  return (
    <img
      src={compact ? Symbol : wordmark}
      alt={APP_NAME}
      className={className}
    />
  )
}

export default BrandLogo
