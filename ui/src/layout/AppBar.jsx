import React, { createElement, forwardRef, Fragment } from 'react'
import {
  AppBar as RAAppBar,
  MenuItemLink,
  useTranslate,
  usePermissions,
  getResources,
} from 'react-admin'
import { MdInfo, MdPerson, MdSupervisorAccount } from 'react-icons/md'
import { useSelector } from 'react-redux'
import {
  makeStyles,
  MenuItem,
  ListItemIcon,
  Divider,
  Typography,
} from '@material-ui/core'
import ViewListIcon from '@material-ui/icons/ViewList'
import { Dialogs } from '../dialogs/Dialogs'
import { AboutDialog } from '../dialogs'
import PersonalMenu from './PersonalMenu'
import ImportMenu from './ImportMenu'
import ActivityPanel from './ActivityPanel'
import NowPlayingPanel from './NowPlayingPanel'
import UserMenu from './UserMenu'
import config from '../config'
import { PageHeaderFrame, PageHeaderSlot } from './PageHeader'
import BrandLogo from '../common/BrandLogo'

const useStyles = makeStyles(
  (theme) => ({
    root: {
      color: theme.palette.text.secondary,
    },
    active: {
      color: theme.palette.text.primary,
    },
    icon: { minWidth: theme.spacing(5) },
    appBar: {
      '&&': {
        backgroundColor: theme.palette.background.paper,
        color: theme.palette.text.primary,
        boxShadow: 'none',
        borderBottom: `1px solid ${theme.palette.divider}`,
      },
    },
    toolbar: {
      minHeight: 64,
      boxSizing: 'border-box',
      padding: theme.spacing(0, 2, 0, 0),
      gap: theme.spacing(1),
      flexWrap: 'wrap',
      '& > button': {
        width: 44,
        height: 44,
        minWidth: 44,
        minHeight: 44,
        padding: 10,
        margin: 0,
      },
      [theme.breakpoints.down('xs')]: {
        padding: theme.spacing(1),
        gap: theme.spacing(0.5),
        '& > .MuiIconButton-root, & > .app-loader': { order: 0 },
        '& > .MuiIconButton-root:first-child': { order: 0 },
      },
    },
    menuButton: {
      [theme.breakpoints.up('sm')]: { display: 'none' },
    },
    brand: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      flex: '0 0 240px',
      height: 64,
      padding: 0,
      boxSizing: 'border-box',
      '&:focus-visible': {
        outline: `2px solid ${theme.palette.primary.main}`,
        outlineOffset: -3,
      },
    },
    logo: {
      display: 'block',
      width: '100%',
      height: '100%',
      objectFit: 'contain',
    },
    utilities: {
      display: 'flex',
      alignItems: 'center',
      flexShrink: 0,
      marginLeft: 'auto',
      '& button': {
        width: 44,
        height: 44,
        minWidth: 44,
        minHeight: 44,
        padding: 10,
      },
      [theme.breakpoints.down('xs')]: { order: 0 },
    },
  }),
  {
    name: 'NDAppBar',
  },
)

const AboutMenuItem = forwardRef(({ onClick, ...rest }, ref) => {
  const classes = useStyles(rest)
  const translate = useTranslate()
  const [open, setOpen] = React.useState(false)

  const handleOpen = () => {
    setOpen(true)
  }
  const handleClose = () => {
    onClick && onClick()
    setOpen(false)
  }
  const label = translate('menu.about')
  return (
    <>
      <MenuItem ref={ref} onClick={handleOpen} className={classes.root}>
        <ListItemIcon className={classes.icon}>
          <MdInfo title={label} size={24} />
        </ListItemIcon>
        {label}
      </MenuItem>
      <AboutDialog onClose={handleClose} open={open} />
    </>
  )
})

AboutMenuItem.displayName = 'AboutMenuItem'

const settingsResources = (resource) =>
  resource.name !== 'user' &&
  resource.hasList &&
  resource.options &&
  resource.options.subMenu === 'settings'

const CustomUserMenu = ({ onClick, ...rest }) => {
  const translate = useTranslate()
  const resources = useSelector(getResources)
  const classes = useStyles(rest)
  const { permissions } = usePermissions()

  const resourceDefinition = (resourceName) =>
    resources.find((r) => r?.name === resourceName)

  const renderUserMenuItemLink = () => {
    const userResource = resourceDefinition('user')
    if (!userResource) {
      return null
    }
    if (permissions !== 'admin') {
      if (!config.enableUserEditing) {
        return null
      }
      userResource.icon = MdPerson
    } else {
      userResource.icon = MdSupervisorAccount
    }
    return renderSettingsMenuItemLink(
      userResource,
      permissions !== 'admin' ? localStorage.getItem('userId') : null,
    )
  }

  const renderSettingsMenuItemLink = (resource, id) => {
    const label = translate(`resources.${resource.name}.name`, {
      smart_count: id ? 1 : 2,
    })
    const link = id ? `/${resource.name}/${id}` : `/${resource.name}`
    return (
      <MenuItemLink
        className={classes.root}
        activeClassName={classes.active}
        key={resource.name}
        to={link}
        primaryText={label}
        leftIcon={
          (resource.icon && createElement(resource.icon, { size: 24 })) || (
            <ViewListIcon />
          )
        }
        onClick={onClick}
        sidebarIsOpen={true}
      />
    )
  }

  return (
    <>
      {config.devActivityPanel &&
        permissions === 'admin' &&
        config.enableNowPlaying && <NowPlayingPanel />}
      {config.devActivityPanel && permissions === 'admin' && <ActivityPanel />}
      <UserMenu {...rest}>
        <PersonalMenu sidebarIsOpen={true} onClick={onClick} />
        {permissions === 'admin' && (
          <ImportMenu sidebarIsOpen={true} onClick={onClick} />
        )}
        <Divider />
        {renderUserMenuItemLink()}
        {resources
          .filter(settingsResources)
          .map((r) => renderSettingsMenuItemLink(r))}
        <Divider />
        <AboutMenuItem />
      </UserMenu>
      <Dialogs />
    </>
  )
}

const AppBarUserMenu = ({ logout }) => {
  const classes = useStyles()
  return (
    <div className={classes.utilities}>
      <CustomUserMenu logout={logout} />
    </div>
  )
}

const HeaderBrand = () => {
  const classes = useStyles()
  return (
    <a href="#/" className={classes.brand}>
      <BrandLogo className={classes.logo} />
    </a>
  )
}

const AppBar = (props) => {
  const classes = useStyles()
  return (
    <PageHeaderFrame>
      <RAAppBar
        {...props}
        position="static"
        color="default"
        elevation={0}
        className={classes.appBar}
        classes={{ toolbar: classes.toolbar, menuButton: classes.menuButton }}
        container={Fragment}
        userMenu={<AppBarUserMenu />}
      >
        <HeaderBrand />
        <PageHeaderSlot
          fallback={<Typography variant="h6" id="react-admin-title" />}
        />
      </RAAppBar>
    </PageHeaderFrame>
  )
}

export default AppBar
