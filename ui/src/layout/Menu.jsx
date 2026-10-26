import React, { useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { Button, Divider, makeStyles } from '@material-ui/core'
import clsx from 'clsx'
import {
  useTranslate,
  MenuItemLink,
  getResources,
  toggleSidebar,
} from 'react-admin'
import ChevronLeft from '@material-ui/icons/ChevronLeft'
import ChevronRight from '@material-ui/icons/ChevronRight'
import ViewListIcon from '@material-ui/icons/ViewList'
import AlbumIcon from '@material-ui/icons/Album'
import SubMenu from './SubMenu'
import { humanize, pluralize } from 'inflection'
import albumLists from '../album/albumLists'
import PlaylistsSubMenu from './PlaylistsSubMenu'
import LibrarySelector from '../common/LibrarySelector'
import config from '../config'

const useStyles = makeStyles((theme) => ({
  root: {
    display: 'flex',
    flexDirection: 'column',
    height: '100%',
    minHeight: 0,
    transition: theme.transitions.create('width', {
      easing: theme.transitions.easing.sharp,
      duration: theme.transitions.duration.leavingScreen,
    }),
    [theme.breakpoints.down('xs')]: {
      height: 'calc(100dvh - var(--troly-player-height, 0px))',
    },
  },
  open: {
    width: 240,
  },
  closed: {
    width: 55,
  },
  active: {
    color: theme.palette.text.primary,
    fontWeight: 'bold',
  },
  navigation: {
    flex: 1,
    minHeight: 0,
    overflowY: 'auto',
    overflowX: 'hidden',
    paddingTop: theme.spacing(1),
  },
  footer: {
    flexShrink: 0,
    padding: theme.spacing(1, 0.5),
    borderTop: `1px solid ${theme.palette.divider}`,
  },
  toggle: {
    '&&': {
      backgroundColor: 'transparent',
      borderRadius: 10,
      fontWeight: 400,
      fontSize: '0.8125rem',
      '&:hover, &:focus-visible': {
        backgroundColor: theme.palette.action.hover,
      },
    },
    width: '100%',
    minWidth: 44,
    minHeight: 44,
    justifyContent: 'flex-start',
    padding: theme.spacing(0, 1.5),
    textTransform: 'none',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    color: theme.palette.text.secondary,
    '& .MuiButton-label': {
      justifyContent: 'inherit',
      color: 'inherit',
      padding: 0,
    },
    '& svg': {
      width: 20,
      height: 20,
      flexShrink: 0,
      marginRight: theme.spacing(1),
    },
    '&:focus-visible': {
      outline: `2px solid ${theme.palette.primary.main}`,
      outlineOffset: -2,
    },
  },
  toggleCompact: {
    justifyContent: 'center',
    '& svg': { marginRight: 0 },
  },
}))

const translatedResourceName = (resource, translate) =>
  translate(`resources.${resource.name}.name`, {
    smart_count: 2,
    _:
      resource.options && resource.options.label
        ? translate(resource.options.label, {
            smart_count: 2,
            _: resource.options.label,
          })
        : humanize(pluralize(resource.name)),
  })

const Menu = ({ dense = false }) => {
  const open = useSelector((state) => state.admin.ui.sidebarOpen)
  const translate = useTranslate()
  const dispatch = useDispatch()
  const classes = useStyles()
  const resources = useSelector(getResources)
  const toggleLabel = translate(
    open ? 'ra.action.close_menu' : 'ra.action.open_menu',
  )

  // TODO State is not persisted in mobile when you close the sidebar menu. Move to redux?
  const [state, setState] = useState({
    menuAlbumList: true,
    menuPlaylists: true,
    menuSharedPlaylists: true,
  })

  const handleToggle = (menu) => {
    setState((state) => ({ ...state, [menu]: !state[menu] }))
  }

  const renderResourceMenuItemLink = (resource) => (
    <MenuItemLink
      key={resource.name}
      to={`/${resource.name}`}
      activeClassName={classes.active}
      primaryText={translatedResourceName(resource, translate)}
      leftIcon={resource.icon || <ViewListIcon />}
      sidebarIsOpen={open}
      dense={dense}
    />
  )

  const renderAlbumMenuItemLink = (type, al) => {
    const resource = resources.find((r) => r.name === 'album')
    if (!resource) {
      return null
    }

    const albumListAddress = `/album/${type}`

    const name = translate(`resources.album.lists.${type || 'default'}`, {
      _: translatedResourceName(resource, translate),
    })

    return (
      <MenuItemLink
        key={albumListAddress}
        to={albumListAddress}
        activeClassName={classes.active}
        primaryText={name}
        leftIcon={al.icon || <ViewListIcon />}
        sidebarIsOpen={open}
        dense={dense}
        exact
      />
    )
  }

  const subItems = (subMenu) => (resource) =>
    resource.hasList && resource.options && resource.options.subMenu === subMenu

  return (
    <div
      className={clsx(classes.root, {
        [classes.open]: open,
        [classes.closed]: !open,
      })}
    >
      <div className={classes.navigation}>
        {open && <LibrarySelector />}
        <SubMenu
          handleToggle={() => handleToggle('menuAlbumList')}
          isOpen={state.menuAlbumList}
          sidebarIsOpen={open}
          name="menu.albumList"
          icon={<AlbumIcon />}
          dense={dense}
        >
          {Object.keys(albumLists).map((type) =>
            renderAlbumMenuItemLink(type, albumLists[type]),
          )}
        </SubMenu>
        {resources.filter(subItems(undefined)).map(renderResourceMenuItemLink)}
        {config.devSidebarPlaylists && open ? (
          <>
            <Divider />
            <PlaylistsSubMenu
              state={state}
              setState={setState}
              sidebarIsOpen={open}
              dense={dense}
            />
          </>
        ) : (
          resources.filter(subItems('playlist')).map(renderResourceMenuItemLink)
        )}
      </div>
      <div className={classes.footer}>
        <Button
          className={clsx(classes.toggle, !open && classes.toggleCompact)}
          onClick={() => dispatch(toggleSidebar())}
          aria-label={toggleLabel}
          aria-expanded={open}
          title={toggleLabel}
        >
          {open ? <ChevronLeft /> : <ChevronRight />}
          {open && toggleLabel}
        </Button>
      </div>
    </div>
  )
}

export default Menu
