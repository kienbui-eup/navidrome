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
      padding: theme.spacing(0, 2),
      gap: theme.spacing(1),
      flexWrap: 'nowrap',
      '& > button': {
        width: 44,
        height: 44,
        minWidth: 44,
        minHeight: 44,
        padding: 10,
        margin: 0,
        flexShrink: 0,
        borderRadius: 12,
        backgroundColor: theme.palette.action.hover,
      },
      '& > .app-loader': {
        width: '44px !important',
        height: '44px !important',
        padding: 12,
        margin: 0,
        boxSizing: 'border-box',
        flexShrink: 0,
      },
      [theme.breakpoints.down('md')]: {
        display: 'grid',
        gridTemplateColumns: 'auto minmax(0, 1fr) auto',
        padding: theme.spacing(0, 2, 1),
        '& > .MuiIconButton-root:not($menuButton), & > .app-loader': {
          gridColumn: 2,
          gridRow: 1,
          justifySelf: 'end',
        },
      },
      [theme.breakpoints.down('xs')]: {
        gridTemplateColumns: '44px minmax(0, 1fr) auto',
        padding: theme.spacing(0, 1, 1),
        '& > .MuiIconButton-root:not($menuButton), & > .app-loader': {
          gridColumn: 1,
          gridRow: 3,
          justifySelf: 'start',
        },
      },
    },
    menuButton: {
      [theme.breakpoints.up('sm')]: { display: 'none' },
      [theme.breakpoints.down('xs')]: { gridColumn: 1, gridRow: 1 },
    },
    brand: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      flex: '0 0 240px',
      width: 240,
      height: 64,
      padding: 0,
      boxSizing: 'border-box',
      [theme.breakpoints.down('md')]: { gridColumn: 1, gridRow: 1 },
      [theme.breakpoints.down('xs')]: {
        gridColumn: '2 / -1',
        justifySelf: 'start',
      },
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
      gap: theme.spacing(1),
      paddingLeft: theme.spacing(1.5),
      borderLeft: `1px solid ${theme.palette.divider}`,
      '& button': {
        width: 44,
        height: 44,
        minWidth: 44,
        minHeight: 44,
        padding: 10,
        margin: 0,
        borderRadius: 12,
        backgroundColor: theme.palette.action.hover,
      },
      [theme.breakpoints.down('md')]: { gridColumn: 3, gridRow: 1 },
      [theme.breakpoints.down('xs')]: {
        gridColumn: '2 / -1',
        gridRow: 3,
        justifySelf: 'end',
        paddingLeft: 0,
        borderLeft: 0,
      },
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
