import React, { useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import {
  useDataProvider,
  useNotify,
  usePermissions,
  useRefresh,
  useTranslate,
  useUnselectAll,
} from 'react-admin'
import {
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
} from '@material-ui/core'
import { alpha, makeStyles } from '@material-ui/core/styles'
import { closeDeleteMediaDialog } from '../actions'
import { httpClient } from '../dataProvider'

// Extensions the quality upgrader treats as lossless (mirrors the
// losslessExtensions set in core/upgrader_match.go). Only used here to decide
// whether to offer "find higher quality instead" for a single song; the
// server-side scan is the authority on what actually qualifies.
const LOSSLESS_SUFFIXES = new Set([
  'flac',
  'alac',
  'wav',
  'aiff',
  'aif',
  'ape',
  'shn',
  'dsf',
  'dff',
  'wv',
  'wvp',
  'tak',
  'tta',
])

const isLossySuffix = (suffix) =>
  !!suffix && !LOSSLESS_SUFFIXES.has(String(suffix).toLowerCase())

const useStyles = makeStyles(
  (theme) => ({
    deleteButton: {
      color: theme.palette.error.main,
      '&:hover': {
        backgroundColor: alpha(theme.palette.error.main, 0.12),
        '@media (hover: none)': {
          backgroundColor: 'transparent',
        },
      },
    },
  }),
  { name: 'NDDeleteMediaDialog' },
)

const songId = (record) => record.mediaFileId || record.id

const deletePath = (mode, record) => {
  if (mode === 'album') {
    return `/api/album/${encodeURIComponent(record.id)}`
  }
  if (mode === 'songs') {
    const query = (record.ids || [])
      .map((id) => `id=${encodeURIComponent(id)}`)
      .join('&')
    return `/api/song?${query}`
  }
  return `/api/song/${encodeURIComponent(songId(record))}`
}

// react-admin's fetchJson sets HttpError.message from the JSON body's
// "message" field (falling back to the HTTP status text). The deletion API
// returns { "error": "..." } instead, so the real message lives in the parsed
// body, not e.message.
const errorMessage = (e) =>
  (e && e.body && e.body.error) || (e && e.message) || ''

export const DeleteMediaDialog = () => {
  const classes = useStyles()
  const dispatch = useDispatch()
  const translate = useTranslate()
  const notify = useNotify()
  const refresh = useRefresh()
  const dataProvider = useDataProvider()
  const { permissions } = usePermissions()
  const unselectAll = useUnselectAll()
  const { open, mode, record, hideUpgradeAction } = useSelector(
    (state) => state.deleteMediaDialog,
  )
  const [busy, setBusy] = useState(false)

  const handleClose = () => {
    if (busy) {
      return
    }
    dispatch(closeDeleteMediaDialog())
  }

  const count =
    mode === 'songs'
      ? (record && (record.count ?? record.ids?.length)) || 0
      : mode === 'album'
        ? (record && record.songCount) || 0
        : 1

  // The dialog itself is only reachable from admin-gated entry points (menu
  // items, bulk actions), same as the Upgrade page/menu (see UpgradeMenu.jsx
  // and AppBar.jsx, which gate purely on permissions === 'admin' - there is no
  // separate client-facing "upgrade enabled" config flag). We repeat the
  // admin check here for clarity and so this component stays correct if ever
  // reused from a non-admin-gated call site.
  const upgradeUiAvailable = !hideUpgradeAction && permissions === 'admin'
  const canOfferUpgrade =
    upgradeUiAvailable &&
    (mode === 'song' ? isLossySuffix(record && record.suffix) : true)

  const handleDelete = () => {
    if (!record) {
      return
    }
    setBusy(true)
    httpClient(deletePath(mode, record), { method: 'DELETE' })
      .then(() => {
        notify(translate('deleteMedia.notify.deleted', { count }), 'info')
        refresh()
        if (mode === 'songs') {
          unselectAll('song')
        }
        dispatch(closeDeleteMediaDialog())
      })
      .catch((e) => {
        if (e && e.status === 409) {
          notify(
            translate('deleteMedia.notify.conflict409', {
              error: errorMessage(e),
            }),
            'warning',
          )
        } else {
          notify(
            translate('deleteMedia.notify.failed', { error: errorMessage(e) }),
            'warning',
          )
        }
      })
      .finally(() => setBusy(false))
  }

  const handleUpgradeInstead = () => {
    if (!record) {
      return
    }
    setBusy(true)
    const idsPromise =
      mode === 'album'
        ? dataProvider
            .getList('song', {
              pagination: { page: 1, perPage: -1 },
              sort: { field: 'album', order: 'ASC' },
              filter: { album_id: record.id },
            })
            .then((response) => response.data.map((s) => s.id))
        : mode === 'songs'
          ? Promise.resolve(record.ids || [])
          : Promise.resolve([songId(record)])

    idsPromise
      .then((mediaFileIds) =>
        httpClient('/api/upgrade/scan', {
          method: 'POST',
          body: JSON.stringify({ mediaFileIds }),
        }),
      )
      .then(() => {
        notify(translate('deleteMedia.notify.upgradeQueued'), 'info')
        dispatch(closeDeleteMediaDialog())
      })
      .catch((e) => {
        notify(
          translate('deleteMedia.notify.failed', { error: errorMessage(e) }),
          'warning',
        )
      })
      .finally(() => setBusy(false))
  }

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      aria-labelledby="delete-media-dialog-title"
      fullWidth
      maxWidth="sm"
    >
      {record && (
        <>
          <DialogTitle id="delete-media-dialog-title">
            {translate(`deleteMedia.dialogTitle.${mode}`, { count })}
          </DialogTitle>
          <DialogContent>
            {mode === 'song' && (
              <DialogContentText>
                {translate('deleteMedia.info.song', {
                  title: record.title,
                  artist: record.artist,
                })}
                {record.suffix &&
                  ` — ${translate('deleteMedia.info.quality', {
                    suffix: String(record.suffix).toUpperCase(),
                    bitRate: record.bitRate || 0,
                  })}`}
              </DialogContentText>
            )}
            {mode === 'album' && (
              <DialogContentText>
                {translate('deleteMedia.info.album', {
                  name: record.name,
                  count,
                })}
              </DialogContentText>
            )}
            {mode === 'songs' && (
              <DialogContentText>
                {translate('deleteMedia.info.songs', { count })}
              </DialogContentText>
            )}
            <DialogContentText color="error">
              {translate('deleteMedia.warning')}
            </DialogContentText>
          </DialogContent>
          <DialogActions>
            {canOfferUpgrade && (
              <Button
                onClick={handleUpgradeInstead}
                disabled={busy}
                color="primary"
              >
                {translate('deleteMedia.buttons.upgradeInstead')}
              </Button>
            )}
            <Button onClick={handleClose} disabled={busy}>
              {translate('deleteMedia.buttons.cancel')}
            </Button>
            <Button
              onClick={handleDelete}
              disabled={busy}
              className={classes.deleteButton}
              startIcon={busy ? <CircularProgress size={16} /> : null}
            >
              {translate('deleteMedia.buttons.confirm')}
            </Button>
          </DialogActions>
        </>
      )}
    </Dialog>
  )
}

export default DeleteMediaDialog
