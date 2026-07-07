import React, { useCallback } from 'react'
import PropTypes from 'prop-types'
import PlaylistAddIcon from '@material-ui/icons/PlaylistAdd'
import IconButton from '@material-ui/core/IconButton'
import { makeStyles } from '@material-ui/core/styles'
import clsx from 'clsx'
import { useDispatch } from 'react-redux'
import { useDataProvider, useNotify, useTranslate } from 'react-admin'
import { addTracks } from '../actions'

const useStyles = makeStyles(
  {
    button: {
      color: (props) => props.color,
      visibility: (props) => (props.visible === false ? 'hidden' : 'inherit'),
    },
  },
  { name: 'NDQuickAddToQueueButton' },
)

// One-click "add to the current play queue" button. When songQueryParams is
// given (album/artist), all matching songs are fetched and enqueued;
// otherwise the record itself is enqueued.
export const QuickAddToQueueButton = ({
  record,
  resource,
  visible,
  color,
  size,
  className,
  disabled,
  component: Button,
  songQueryParams,
  addLabel,
  ...rest
}) => {
  const classes = useStyles({ color, visible })
  const dispatch = useDispatch()
  const dataProvider = useDataProvider()
  const notify = useNotify()
  const translate = useTranslate()

  const label = translate(
    resource === 'song'
      ? 'resources.song.actions.addToQueue'
      : 'resources.album.actions.addToQueue',
    { _: 'Play Later' },
  )

  const notifyAdded = useCallback(
    (count) =>
      notify('message.songsAddedToQueue', {
        type: 'info',
        messageArgs: { smart_count: count },
      }),
    [notify],
  )

  const handleClick = useCallback(
    (e) => {
      e.preventDefault()
      e.stopPropagation()
      if (songQueryParams) {
        dataProvider
          .getList('song', songQueryParams)
          .then((res) => {
            const data = res.data.reduce(
              (acc, cur) => ({ ...acc, [cur.id]: cur }),
              {},
            )
            const ids = res.data.map((r) => r.id)
            dispatch(addTracks(data, ids))
            notifyAdded(ids.length)
          })
          .catch(() => notify('ra.page.error', { type: 'warning' }))
      } else {
        dispatch(addTracks({ [record.id]: record }))
        notifyAdded(1)
      }
    },
    [songQueryParams, dataProvider, dispatch, notify, notifyAdded, record],
  )

  return (
    <Button
      onClick={handleClick}
      size={'small'}
      aria-label={label}
      title={label}
      disabled={disabled || record.missing}
      className={clsx(classes.button, className)}
      {...rest}
    >
      <PlaylistAddIcon fontSize={size} />
    </Button>
  )
}

QuickAddToQueueButton.propTypes = {
  record: PropTypes.object,
  resource: PropTypes.string,
  songQueryParams: PropTypes.object,
  visible: PropTypes.bool,
  color: PropTypes.string,
  size: PropTypes.string,
  component: PropTypes.object,
  disabled: PropTypes.bool,
}

QuickAddToQueueButton.defaultProps = {
  record: {},
  resource: 'song',
  addLabel: true,
  visible: true,
  size: 'small',
  color: 'inherit',
  component: IconButton,
  disabled: false,
}
