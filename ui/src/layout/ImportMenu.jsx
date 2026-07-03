import React, { forwardRef } from 'react'
import { MenuItemLink } from 'react-admin'
import { MdLibraryMusic } from 'react-icons/md'
import { makeStyles } from '@material-ui/core'

const useStyles = makeStyles((theme) => ({
  menuItem: {
    color: theme.palette.text.secondary,
  },
}))

// Admin-only entry point to the music import page. The backend endpoints are
// admin-gated, so this link is rendered only for admins.
const ImportMenu = forwardRef(({ onClick, sidebarIsOpen, dense }, ref) => {
  const classes = useStyles()
  return (
    <MenuItemLink
      ref={ref}
      to="/import"
      primaryText="Import nhạc"
      leftIcon={<MdLibraryMusic size={24} />}
      onClick={onClick}
      className={classes.menuItem}
      sidebarIsOpen={sidebarIsOpen}
      dense={dense}
    />
  )
})

ImportMenu.displayName = 'ImportMenu'

export default ImportMenu
