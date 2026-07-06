import React, { useMemo } from 'react'
import {
  Datagrid,
  DateField,
  EditButton,
  Filter,
  NumberField,
  ReferenceInput,
  SearchInput,
  SelectInput,
  TextField,
  useUpdate,
  useNotify,
  useRecordContext,
  useTranslate,
  BulkDeleteButton,
  usePermissions,
} from 'react-admin'
import Switch from '@material-ui/core/Switch'
import Chip from '@material-ui/core/Chip'
import { makeStyles } from '@material-ui/core/styles'
import { useMediaQuery } from '@material-ui/core'
import {
  CoverArtAvatar,
  DurationField,
  List,
  Writable,
  isWritable,
  isSmartPlaylist,
  useSelectedFields,
  useResourceRefresh,
} from '../common'
import PlaylistListActions from './PlaylistListActions'
import ChangePublicStatusButton from './ChangePublicStatusButton'

const useStyles = makeStyles((theme) => ({
  button: {
    color: theme.palette.type === 'dark' ? 'white' : undefined,
  },
  badges: {
    display: 'flex',
    gap: theme.spacing(0.5),
    flexWrap: 'wrap',
  },
}))

// Playlists created by the auto-playlist scheduler (Phase 2) are marked by
// convention with a "auto:"+templateID prefix on the Comment field, since no
// DB migration was added for this feature. See core/playlists/auto.go.
const AUTO_COMMENT_PREFIX = 'auto:'

const PlaylistFilter = (props) => {
  const { permissions } = usePermissions()
  return (
    <Filter {...props} variant={'outlined'}>
      <SearchInput source="q" alwaysOn />
      {permissions === 'admin' && (
        <ReferenceInput
          source="owner_id"
          label={'resources.playlist.fields.ownerName'}
          reference="user"
          perPage={0}
          sort={{ field: 'name', order: 'ASC' }}
          alwaysOn
        >
          <SelectInput optionText="name" />
        </ReferenceInput>
      )}
    </Filter>
  )
}

const TogglePublicInput = ({ resource, source }) => {
  const record = useRecordContext()
  const notify = useNotify()
  const [togglePublic] = useUpdate(
    resource,
    record.id,
    {
      ...record,
      public: !record.public,
    },
    {
      undoable: false,
      onFailure: (error) => {
        notify('ra.page.error', 'warning')
      },
    },
  )

  const handleClick = (e) => {
    togglePublic()
    e.stopPropagation()
  }

  return (
    <Switch
      checked={record[source]}
      onClick={handleClick}
      disabled={!isWritable(record.ownerId)}
    />
  )
}

const ToggleAutoImport = ({ resource, source }) => {
  const record = useRecordContext()
  const notify = useNotify()
  const [ToggleAutoImport] = useUpdate(
    resource,
    record.id,
    {
      ...record,
      sync: !record.sync,
    },
    {
      undoable: false,
      onFailure: (error) => {
        notify('ra.page.error', 'warning')
      },
    },
  )
  const handleClick = (e) => {
    ToggleAutoImport()
    e.stopPropagation()
  }

  return record.path ? (
    <Switch
      checked={record[source]}
      onClick={handleClick}
      disabled={!isWritable(record.ownerId)}
    />
  ) : null
}

// Chip(s) distinguishing smart playlists (record.rules != null, see
// model/playlist.go IsSmartPlaylist) from regular ones, and further calling
// out auto-generated ones (Comment prefixed with "auto:", see Phase 2
// scheduler convention in core/playlists/auto.go).
const PlaylistBadges = () => {
  const classes = useStyles()
  const translate = useTranslate()
  const record = useRecordContext()
  if (!record) {
    return null
  }
  const isSmart = isSmartPlaylist(record)
  const isAuto =
    typeof record.comment === 'string' &&
    record.comment.startsWith(AUTO_COMMENT_PREFIX)
  if (!isSmart && !isAuto) {
    return null
  }
  return (
    <span className={classes.badges}>
      {isSmart && (
        <Chip
          size="small"
          variant="outlined"
          label={translate('resources.playlist.fields.smart')}
        />
      )}
      {isAuto && (
        <Chip
          size="small"
          color="primary"
          label={translate('resources.playlist.fields.auto')}
        />
      )}
    </span>
  )
}

const PlaylistListBulkActions = (props) => {
  const classes = useStyles()
  return (
    <>
      <ChangePublicStatusButton
        public={true}
        {...props}
        className={classes.button}
      />
      <ChangePublicStatusButton
        public={false}
        {...props}
        className={classes.button}
      />
      <BulkDeleteButton {...props} className={classes.button} />
    </>
  )
}

const PlaylistList = (props) => {
  const isXsmall = useMediaQuery((theme) => theme.breakpoints.down('xs'))
  const isDesktop = useMediaQuery((theme) => theme.breakpoints.up('md'))
  useResourceRefresh('playlist')

  const toggleableFields = useMemo(
    () => ({
      badges: <PlaylistBadges label={'resources.playlist.fields.type'} />,
      ownerName: isDesktop && <TextField source="ownerName" />,
      songCount: !isXsmall && <NumberField source="songCount" />,
      duration: <DurationField source="duration" />,
      updatedAt: isDesktop && (
        <DateField source="updatedAt" sortByOrder={'DESC'} />
      ),
      public: !isXsmall && (
        <TogglePublicInput source="public" sortByOrder={'DESC'} />
      ),
      comment: <TextField source="comment" />,
      sync: !isXsmall && (
        <ToggleAutoImport source="sync" sortByOrder={'DESC'} />
      ),
    }),
    [isDesktop, isXsmall],
  )

  const columns = useSelectedFields({
    resource: 'playlist',
    columns: toggleableFields,
    defaultOff: ['comment'],
  })

  return (
    <List
      {...props}
      exporter={false}
      sort={{ field: 'name', order: 'ASC' }}
      filters={<PlaylistFilter />}
      actions={<PlaylistListActions />}
      bulkActionButtons={!isXsmall && <PlaylistListBulkActions />}
    >
      <Datagrid rowClick="show" isRowSelectable={(r) => isWritable(r?.ownerId)}>
        <CoverArtAvatar source="id" variant="square" />
        <TextField source="name" />
        {columns}
        <Writable>
          <EditButton />
        </Writable>
      </Datagrid>
    </List>
  )
}

export default PlaylistList
