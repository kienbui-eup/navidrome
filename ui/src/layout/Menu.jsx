import React, { useState } from 'react'
import { useSelector } from 'react-redux'
import { Divider, makeStyles, Typography } from '@material-ui/core'
import clsx from 'clsx'
import { useTranslate, MenuItemLink, getResources, usePermissions } from 'react-admin'
import ViewListIcon from '@material-ui/icons/ViewList'
import AlbumIcon from '@material-ui/icons/Album'
import DnsIcon from '@material-ui/icons/Dns'
import SubMenu from './SubMenu'
import { humanize, pluralize } from 'inflection'
import albumLists from '../album/albumLists'
import PlaylistsSubMenu from './PlaylistsSubMenu'
import LibrarySelector from '../common/LibrarySelector'
import config from '../config'
import { MdCloudDownload, MdHighQuality } from 'react-icons/md'

const useStyles = makeStyles((theme) => ({
  root: {
    marginTop: theme.spacing(1),
    marginBottom: theme.spacing(1),
    transition: theme.transitions.create('width', {
      easing: theme.transitions.easing.sharp,
      duration: theme.transitions.duration.leavingScreen,
    }),
    paddingBottom: (props) => (props.addPadding ? '80px' : '20px'),
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
  sectionHeader: {
    padding: theme.spacing(2, 2, 0.5, 2),
    color: theme.palette.text.secondary,
    fontWeight: 'bold',
    fontSize: '0.72rem',
    textTransform: 'uppercase',
    letterSpacing: '1.2px',
    display: 'block',
    opacity: 0.8,
  },
  divider: {
    margin: theme.spacing(1.5, 0),
    backgroundColor: theme.palette.divider,
    opacity: 0.5,
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
  const queue = useSelector((state) => state.player?.queue)
  const classes = useStyles({ addPadding: queue.length > 0 })
  const resources = useSelector(getResources)
  const { permissions } = usePermissions()

  // TODO State is not persisted in mobile when you close the sidebar menu. Move to redux?
  const [state, setState] = useState({
    menuAlbumList: true,
    menuPlaylists: true,
    menuSharedPlaylists: true,
  })

  const handleToggle = (menu) => {
    setState((state) => ({ ...state, [menu]: !state[menu] }))
  }

  const renderResourceMenuItemLink = (resource) => {
    // The artist resource's plain list entry shares the /artist pathname
    // with the composer/conductor role links below. Without a custom
    // isActive it would render as active on those role-filtered views too.
    const isArtist = resource.name === 'artist'

    return (
      <MenuItemLink
        key={resource.name}
        to={`/${resource.name}`}
        activeClassName={classes.active}
        primaryText={translatedResourceName(resource, translate)}
        leftIcon={
          resource.icon
            ? React.isValidElement(resource.icon)
              ? resource.icon
              : React.createElement(resource.icon)
            : <ViewListIcon />
        }
        sidebarIsOpen={open}
        dense={dense}
        {...(isArtist && {
          isActive: (match, location) =>
            location.pathname === '/artist' &&
            !location.search.includes('"role":"composer"') &&
            !location.search.includes('"role":"conductor"'),
        })}
      />
    )
  }

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

  // Renders a menu entry that links to the artist list pre-filtered by
  // participant role (e.g. composer, conductor). Reuses the artist resource
  // (icon) and the artist list's existing `role` filter — no new resource.
  const renderArtistRoleMenuItemLink = (role) => {
    const resource = resources.find((r) => r.name === 'artist')
    if (!resource) {
      return null
    }

    const roleAddress = `/artist?filter={"role":"${role}"}`
    const roleFilterFragment = `"role":"${role}"`

    const name = translate(`resources.artist.roles.${role}`, {
      smart_count: 2,
    })

    // NavLink (via MenuItemLink's ...rest passthrough) only matches on
    // pathname by default, so the plain Artists entry and both role links
    // would all be active at once on /artist. Restrict activation to an
    // exact pathname match plus the role's own filter fragment in the
    // query string.
    const isRoleActive = (match, location) =>
      location.pathname === '/artist' &&
      location.search.includes(roleFilterFragment)

    return (
      <MenuItemLink
        key={roleAddress}
        to={roleAddress}
        isActive={isRoleActive}
        activeClassName={classes.active}
        primaryText={name}
        leftIcon={
          resource.icon
            ? React.isValidElement(resource.icon)
              ? resource.icon
              : React.createElement(resource.icon)
            : <ViewListIcon />
        }
        sidebarIsOpen={open}
        dense={dense}
      />
    )
  }

  const libraryResourceNames = ['library', 'missing', 'share']
  const systemResourceNames = ['user', 'player', 'transcoding', 'plugin']

  const libraryResources = resources.filter(
    (resource) =>
      libraryResourceNames.includes(resource.name) &&
      (resource.hasList || resource.name === 'share'),
  )

  const systemResources = resources.filter(
    (resource) =>
      systemResourceNames.includes(resource.name) &&
      (resource.hasList || resource.name === 'plugin'),
  )

  return (
    <div
      className={clsx(classes.root, {
        [classes.open]: open,
        [classes.closed]: !open,
      })}
    >
      {/* Group 1: Cài đặt Thư viện */}
      {libraryResources.length > 0 && (
        <>
          {open && (
            <Typography variant="caption" className={classes.sectionHeader}>
              {translate('menu.librarySettings', { _: 'Cài đặt Thư viện' })}
            </Typography>
          )}
          {libraryResources.map((resource) => (
            <React.Fragment key={resource.name}>
              {renderResourceMenuItemLink(resource)}
            </React.Fragment>
          ))}
          {permissions === 'admin' && (
            <>
              <MenuItemLink
                key="import"
                to="/import"
                activeClassName={classes.active}
                primaryText={translate('menu.import', { _: 'Import Nhạc' })}
                leftIcon={<MdCloudDownload size={24} />}
                sidebarIsOpen={open}
                dense={dense}
              />
              <MenuItemLink
                key="upgrade"
                to="/upgrade"
                activeClassName={classes.active}
                primaryText={translate('menu.upgradeQuality', { _: 'Nâng cấp Chất lượng' })}
                leftIcon={<MdHighQuality size={24} />}
                sidebarIsOpen={open}
                dense={dense}
              />
            </>
          )}
        </>
      )}

      {/* Phân cách giữa hai nhóm */}
      {libraryResources.length > 0 && systemResources.length > 0 && (
        <Divider className={classes.divider} />
      )}

      {/* Group 2: Quản trị Hệ thống */}
      {systemResources.length > 0 && (
        <>
          {open && (
            <Typography variant="caption" className={classes.sectionHeader}>
              {translate('menu.systemAdmin', { _: 'Quản trị Hệ thống' })}
            </Typography>
          )}
          {systemResources.map((resource) => (
            <React.Fragment key={resource.name}>
              {renderResourceMenuItemLink(resource)}
            </React.Fragment>
          ))}
          {permissions === 'admin' && (
            <MenuItemLink
              key="server"
              to="/server"
              activeClassName={classes.active}
              primaryText={translate('menu.serverStatus', { _: 'Theo dõi Server VM' })}
              leftIcon={<DnsIcon />}
              sidebarIsOpen={open}
              dense={dense}
            />
          )}
        </>
      )}
    </div>
  )
}

export default Menu
