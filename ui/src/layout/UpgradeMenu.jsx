import React, { forwardRef } from 'react'
import { MenuItemLink, useTranslate } from 'react-admin'
import { MdHighQuality } from 'react-icons/md'
import { makeStyles } from '@material-ui/core'

const useStyles = makeStyles((theme) => ({
  menuItem: {
    color: theme.palette.text.secondary,
  },
}))

// Admin-only entry point to the quality upgrader page. The backend endpoints
// are admin-gated, so this link is rendered only for admins (see AppBar.jsx).
const UpgradeMenu = forwardRef(({ onClick, sidebarIsOpen, dense }, ref) => {
  const classes = useStyles()
  const translate = useTranslate()
  return (
    <MenuItemLink
      ref={ref}
      to="/upgrade"
      primaryText={translate('menu.upgradeQuality')}
      leftIcon={<MdHighQuality size={24} />}
      onClick={onClick}
      className={classes.menuItem}
      sidebarIsOpen={sidebarIsOpen}
      dense={dense}
    />
  )
})

UpgradeMenu.displayName = 'UpgradeMenu'

export default UpgradeMenu
