import React, { useState, useEffect, useCallback } from 'react'
import { useDispatch } from 'react-redux'
import {
  Title,
  useNotify,
  usePermissions,
  useTranslate,
  useVersion,
} from 'react-admin'
import {
  Card,
  CardContent,
  Tabs,
  Tab,
  Button,
  Box,
  Typography,
  CircularProgress,
  LinearProgress,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  Chip,
  Checkbox,
  IconButton,
  Tooltip,
} from '@material-ui/core'
import SearchIcon from '@material-ui/icons/Search'
import CheckIcon from '@material-ui/icons/Check'
import CloseIcon from '@material-ui/icons/Close'
import DeleteIcon from '@material-ui/icons/Delete'
import WarningIcon from '@material-ui/icons/Warning'
import { alpha, makeStyles } from '@material-ui/core/styles'
import { httpClient } from '../dataProvider'
import { APP_NAME } from '../consts'
import { openDeleteMediaDialog } from '../actions'

const PAGE_SIZE = 50
const PENDING_STATUSES = ['pending', 'needs_review']
const HISTORY_STATUSES = ['replaced', 'rejected', 'failed']

const useStyles = makeStyles((theme) => ({
  root: { marginTop: '1em' },
  libSelect: { minWidth: 220, marginBottom: theme.spacing(2) },
  actions: {
    marginTop: theme.spacing(2),
    marginBottom: theme.spacing(2),
    display: 'flex',
    gap: theme.spacing(1),
    flexWrap: 'wrap',
    alignItems: 'center',
  },
  section: { marginTop: theme.spacing(3) },
  hint: { color: theme.palette.text.secondary },
  progress: {
    marginTop: theme.spacing(2),
    padding: theme.spacing(2),
    border: `1px solid ${theme.palette.divider}`,
    borderRadius: theme.shape.borderRadius,
  },
  tableWrap: {
    marginTop: theme.spacing(1),
    overflowX: 'auto',
  },
  tagBox: {
    marginTop: theme.spacing(0.5),
    display: 'flex',
    gap: theme.spacing(0.5),
    flexWrap: 'wrap',
    alignItems: 'center',
  },
  tagChip: { height: 20 },
  errText: { color: theme.palette.error.main, fontSize: '0.8rem' },
  deleteButton: {
    color: theme.palette.error.main,
    '&:hover': {
      backgroundColor: alpha(theme.palette.error.main, 0.12),
      '@media (hover: none)': {
        backgroundColor: 'transparent',
      },
    },
  },
}))

// verifyInfo may arrive as a JSON-encoded string (the backend model stores it
// as a raw string column) or, depending on how the handler serializes the
// response DTO, as an already-parsed object. Handle both defensively.
const parseVerifyInfo = (v) => {
  if (!v) return null
  if (typeof v === 'object') return v
  try {
    return JSON.parse(v)
  } catch (e) {
    return null
  }
}

const VerifyInfoSummary = ({ info, translate, classes }) => {
  const v = parseVerifyInfo(info)
  if (!v) {
    return (
      <Typography variant="body2" className={classes.hint}>
        {translate('upgrade.verify.noVerifyInfo')}
      </Typography>
    )
  }
  const probe = [
    v.actualBitRate ? `${v.actualBitRate} kbps` : null,
    v.actualSampleRate ? `${v.actualSampleRate} Hz` : null,
    v.actualBitDepth ? `${v.actualBitDepth}-bit` : null,
  ].filter(Boolean)
  return (
    <Box>
      {probe.length > 0 && (
        <Typography variant="body2">{probe.join(' • ')}</Typography>
      )}
      {(v.lraOld != null || v.lraNew != null) && (
        <Typography variant="body2" className={classes.hint}>
          {translate('upgrade.verify.lra')}:{' '}
          {translate('upgrade.verify.lraOld')}{' '}
          {v.lraOld != null ? v.lraOld : '?'} LU →{' '}
          {translate('upgrade.verify.lraNew')}{' '}
          {v.lraNew != null ? v.lraNew : '?'} LU
        </Typography>
      )}
      {v.missingTags && v.missingTags.length > 0 && (
        <Box className={classes.tagBox}>
          <Typography variant="body2" className={classes.hint}>
            {translate('upgrade.verify.missingTags')}:
          </Typography>
          {v.missingTags.map((tag) => (
            <Chip
              key={tag}
              size="small"
              label={tag}
              color="secondary"
              className={classes.tagChip}
            />
          ))}
        </Box>
      )}
    </Box>
  )
}

// Extract a readable error message from a react-admin HttpError.
const errMsg = (e, translate) =>
  (e && (e.body || e.message)) || translate('upgrade.notify.unknownError')

const UpgradeQuality = () => {
  const classes = useStyles()
  const notify = useNotify()
  const translate = useTranslate()
  const dispatch = useDispatch()
  // Bumped by react-admin's refresh() (e.g. called by DeleteMediaDialog after
  // a successful delete). This page fetches its own state via httpClient
  // rather than the react-admin store, so it doesn't otherwise notice.
  const version = useVersion()
  const { permissions, loaded: permsLoaded } = usePermissions()
  const [tab, setTab] = useState(0)

  // shared busy flag keyed by an action id so individual buttons can spin
  const [busy, setBusy] = useState(null)

  // scan scope + progress
  const [libraries, setLibraries] = useState([])
  const [scanLibraryId, setScanLibraryId] = useState(0)
  const [scanStatus, setScanStatus] = useState(null)

  // pending / needs_review queue
  const [pending, setPending] = useState([])
  const [loadingPending, setLoadingPending] = useState(false)
  const [pendingEnd, setPendingEnd] = useState(PAGE_SIZE)
  const [selected, setSelected] = useState(new Set())

  // history (replaced / rejected / failed)
  const [history, setHistory] = useState([])
  const [loadingHistory, setLoadingHistory] = useState(false)
  const [historyEnd, setHistoryEnd] = useState(PAGE_SIZE)

  const fetchByStatuses = useCallback(
    (statuses, end) =>
      Promise.all(
        statuses.map((s) =>
          httpClient(
            `/api/upgrade/candidates?status=${encodeURIComponent(s)}&_start=0&_end=${end}`,
          )
            .then(({ json }) => json || [])
            .catch(() => []),
        ),
      ).then((lists) => lists.flat()),
    [],
  )

  const loadPending = useCallback(() => {
    setLoadingPending(true)
    fetchByStatuses(PENDING_STATUSES, pendingEnd)
      .then((list) => {
        list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
        setPending(list)
        setSelected(new Set())
      })
      .finally(() => setLoadingPending(false))
  }, [fetchByStatuses, pendingEnd])

  const loadHistory = useCallback(() => {
    setLoadingHistory(true)
    fetchByStatuses(HISTORY_STATUSES, historyEnd)
      .then((list) => {
        list.sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt))
        setHistory(list)
      })
      .finally(() => setLoadingHistory(false))
  }, [fetchByStatuses, historyEnd])

  // Load target libraries for scan scope selection.
  useEffect(() => {
    httpClient('/api/library')
      .then(({ json }) => setLibraries(json || []))
      .catch(() => {})
  }, [])

  // Load the current scan status once on mount (in case a scan is already
  // running, e.g. after a page reload or started by another admin).
  useEffect(() => {
    httpClient('/api/upgrade/status')
      .then(({ json }) => setScanStatus(json))
      .catch(() => {})
  }, [])

  // Load the pending queue on mount, again whenever pendingEnd grows ("load
  // more"), and again whenever `version` changes - i.e. whenever something
  // outside this page called react-admin's refresh() (the delete-original
  // action below opens DeleteMediaDialog, which does exactly that on a
  // successful delete).
  useEffect(() => {
    loadPending()
  }, [loadPending, version])

  // Load history whenever its tab is active, again whenever historyEnd grows
  // ("load more"), and again on `version` bumps (see above).
  useEffect(() => {
    if (tab === 1) loadHistory()
  }, [tab, loadHistory, version])

  // Poll scan status while a scan is running.
  useEffect(() => {
    if (!scanStatus || !scanStatus.running) return undefined
    const t = setTimeout(() => {
      httpClient('/api/upgrade/status')
        .then(({ json }) => {
          setScanStatus(json)
          if (!json.running) {
            notify(
              translate('upgrade.notify.scanDone', { found: json.found }),
              'info',
            )
            loadPending()
          }
        })
        .catch(() => setScanStatus((s) => (s ? { ...s, running: false } : s)))
    }, 1500)
    return () => clearTimeout(t)
  }, [scanStatus, notify, translate, loadPending])

  const startScan = async () => {
    setBusy('scan')
    try {
      const body = {}
      if (scanLibraryId) body.libraryId = scanLibraryId
      await httpClient('/api/upgrade/scan', {
        method: 'POST',
        body: JSON.stringify(body),
      })
      notify(translate('upgrade.notify.scanStarted'), 'info')
      setScanStatus({ running: true, scanned: 0, total: 0, found: 0 })
    } catch (e) {
      if (e && e.status === 409) {
        notify(translate('upgrade.scan.alreadyRunning'), 'warning')
      } else {
        notify(
          translate('upgrade.notify.startFailed', {
            error: errMsg(e, translate),
          }),
          'warning',
        )
      }
    } finally {
      setBusy(null)
    }
  }

  const approve = async (id, force) => {
    setBusy(id)
    try {
      await httpClient(`/api/upgrade/candidates/${id}/approve`, {
        method: 'POST',
        body: JSON.stringify({ force: !!force }),
      })
      notify(translate('upgrade.notify.approved'), 'info')
      loadPending()
    } catch (e) {
      notify(
        translate('upgrade.notify.approveFailed', {
          error: errMsg(e, translate),
        }),
        'warning',
      )
    } finally {
      setBusy(null)
    }
  }

  const reject = async (id) => {
    setBusy(id)
    try {
      await httpClient(`/api/upgrade/candidates/${id}/reject`, {
        method: 'POST',
      })
      notify(translate('upgrade.notify.rejected'), 'info')
      loadPending()
    } catch (e) {
      notify(
        translate('upgrade.notify.rejectFailed', {
          error: errMsg(e, translate),
        }),
        'warning',
      )
    } finally {
      setBusy(null)
    }
  }

  const approveBatch = async () => {
    const ids = Array.from(selected)
    if (ids.length === 0) return
    setBusy('batch')
    try {
      await httpClient('/api/upgrade/candidates/approve-batch', {
        method: 'POST',
        body: JSON.stringify({ ids }),
      })
      notify(
        translate('upgrade.notify.batchApproved', { count: ids.length }),
        'info',
      )
      loadPending()
    } catch (e) {
      notify(
        translate('upgrade.notify.batchApproveFailed', {
          error: errMsg(e, translate),
        }),
        'warning',
      )
    } finally {
      setBusy(null)
    }
  }

  // Opens the shared delete-confirmation dialog for a candidate's original
  // (current) track. hideUpgradeAction is set because we're already on the
  // Upgrade page - offering to queue another upgrade scan from here would be
  // redundant. The dialog itself performs the DELETE and calls refresh() on
  // success, which this page picks up via the `version` effect above.
  const deleteOriginal = (c) => {
    dispatch(
      openDeleteMediaDialog({
        mode: 'song',
        hideUpgradeAction: true,
        record: {
          id: c.mediaFileId,
          title: c.currentTitle,
          artist: c.currentArtist,
          suffix: c.currentFormat,
          bitRate: c.currentBitRate,
        },
      }),
    )
  }

  const toggleSelect = (id) => {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })
  }

  const selectableIds = pending
    .filter((c) => c.status === 'pending')
    .map((c) => c.id)
  const allSelected =
    selectableIds.length > 0 && selectableIds.every((id) => selected.has(id))
  const someSelected = selectableIds.some((id) => selected.has(id))

  const toggleSelectAll = () => {
    if (allSelected) {
      setSelected(new Set())
    } else {
      setSelected(new Set(selectableIds))
    }
  }

  if (permsLoaded && permissions !== 'admin') {
    return (
      <Card className={classes.root}>
        <Title title={`${APP_NAME} - ${translate('upgrade.pageTitle')}`} />
        <CardContent>
          <Typography>{translate('upgrade.adminOnly')}</Typography>
        </CardContent>
      </Card>
    )
  }

  const scanPct =
    scanStatus && scanStatus.total
      ? Math.round((scanStatus.scanned / scanStatus.total) * 100)
      : 0
  const showScanProgress =
    scanStatus &&
    (scanStatus.running || scanStatus.scanned > 0 || scanStatus.found > 0)
  const pendingHasMore = pending.length >= pendingEnd
  const historyHasMore = history.length >= historyEnd

  return (
    <Card className={classes.root}>
      <Title title={`${APP_NAME} - ${translate('upgrade.pageTitle')}`} />
      <CardContent>
        <Typography variant="h6">{translate('upgrade.heading')}</Typography>
        <Typography className={classes.hint}>
          {translate('upgrade.hint')}
        </Typography>

        <FormControl className={classes.libSelect} margin="normal">
          <InputLabel>{translate('upgrade.librarySelect.label')}</InputLabel>
          <Select
            value={scanLibraryId}
            onChange={(e) => setScanLibraryId(e.target.value)}
          >
            <MenuItem value={0}>
              {translate('upgrade.librarySelect.all')}
            </MenuItem>
            {libraries.map((l) => (
              <MenuItem key={l.id} value={l.id}>
                {l.name}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <Box className={classes.actions}>
          <Button
            variant="contained"
            color="primary"
            startIcon={
              busy === 'scan' ? <CircularProgress size={16} /> : <SearchIcon />
            }
            disabled={busy === 'scan' || (scanStatus && scanStatus.running)}
            onClick={startScan}
          >
            {translate('upgrade.scan.button')}
          </Button>
        </Box>

        {showScanProgress && (
          <Box className={classes.progress}>
            <Typography>
              {scanStatus.running
                ? translate('upgrade.scan.running')
                : translate('upgrade.scan.done')}
              : {scanStatus.scanned}/{scanStatus.total}
            </Typography>
            <LinearProgress
              variant={scanStatus.total ? 'determinate' : 'indeterminate'}
              value={scanPct}
              style={{ marginTop: 8, marginBottom: 8 }}
            />
            <Typography className={classes.hint}>
              {translate('upgrade.scan.progress', {
                scanned: scanStatus.scanned,
                total: scanStatus.total,
                found: scanStatus.found,
              })}
            </Typography>
          </Box>
        )}

        <Box className={classes.section}>
          <Tabs
            value={tab}
            onChange={(e, v) => setTab(v)}
            indicatorColor="primary"
            textColor="primary"
          >
            <Tab label={translate('upgrade.tabs.pending')} />
            <Tab label={translate('upgrade.tabs.history')} />
          </Tabs>
        </Box>

        {tab === 0 && (
          <Box className={classes.section}>
            {selected.size > 0 && (
              <Box className={classes.actions}>
                <Button
                  variant="contained"
                  color="primary"
                  startIcon={
                    busy === 'batch' ? (
                      <CircularProgress size={16} />
                    ) : (
                      <CheckIcon />
                    )
                  }
                  disabled={busy === 'batch'}
                  onClick={approveBatch}
                >
                  {translate('upgrade.actions.approveSelected', {
                    count: selected.size,
                  })}
                </Button>
              </Box>
            )}

            {loadingPending && <CircularProgress size={24} />}
            {!loadingPending && pending.length === 0 && (
              <Typography className={classes.hint}>
                {translate('upgrade.empty.pending')}
              </Typography>
            )}
            {!loadingPending && pending.length > 0 && (
              <Box className={classes.tableWrap}>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell padding="checkbox">
                        <Checkbox
                          indeterminate={someSelected && !allSelected}
                          checked={allSelected}
                          disabled={selectableIds.length === 0}
                          onChange={toggleSelectAll}
                          inputProps={{
                            'aria-label': translate(
                              'upgrade.actions.selectAll',
                            ),
                          }}
                        />
                      </TableCell>
                      <TableCell>
                        {translate('upgrade.table.currentTrack')}
                      </TableCell>
                      <TableCell>
                        {translate('upgrade.table.candidate')}
                      </TableCell>
                      <TableCell>{translate('upgrade.table.source')}</TableCell>
                      <TableCell>
                        {translate('upgrade.table.matchScore')}
                      </TableCell>
                      <TableCell>{translate('upgrade.table.status')}</TableCell>
                      <TableCell align="right">
                        {translate('upgrade.table.actions')}
                      </TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {pending.map((c) => (
                      <TableRow key={c.id} hover>
                        <TableCell padding="checkbox">
                          {c.status === 'pending' && (
                            <Checkbox
                              checked={selected.has(c.id)}
                              onChange={() => toggleSelect(c.id)}
                            />
                          )}
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2">
                            {[c.currentArtist, c.currentTitle]
                              .filter(Boolean)
                              .join(' - ')}
                          </Typography>
                          <Typography variant="body2" className={classes.hint}>
                            {[
                              c.currentFormat,
                              c.currentBitRate
                                ? `${c.currentBitRate} kbps`
                                : null,
                            ]
                              .filter(Boolean)
                              .join(' • ')}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2">{c.title}</Typography>
                          <Typography variant="body2" className={classes.hint}>
                            {[
                              c.format,
                              c.estBitRate ? `${c.estBitRate} kbps` : null,
                            ]
                              .filter(Boolean)
                              .join(' • ')}
                          </Typography>
                          {c.status === 'needs_review' && (
                            <VerifyInfoSummary
                              info={c.verifyInfo}
                              translate={translate}
                              classes={classes}
                            />
                          )}
                        </TableCell>
                        <TableCell>
                          {translate(`upgrade.source.${c.source}`, {
                            _: c.source,
                          })}
                        </TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            label={c.matchScore}
                            color={c.matchScore >= 90 ? 'primary' : 'default'}
                          />
                        </TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            label={translate(`upgrade.status.${c.status}`, {
                              _: c.status,
                            })}
                            color={
                              c.status === 'needs_review'
                                ? 'secondary'
                                : 'default'
                            }
                          />
                        </TableCell>
                        <TableCell align="right">
                          {c.status === 'needs_review' && (
                            <Tooltip
                              title={translate('upgrade.actions.forceApprove')}
                            >
                              <span>
                                <IconButton
                                  size="small"
                                  color="secondary"
                                  disabled={busy === c.id}
                                  onClick={() => approve(c.id, true)}
                                  aria-label={translate(
                                    'upgrade.actions.forceApprove',
                                  )}
                                >
                                  <WarningIcon fontSize="small" />
                                </IconButton>
                              </span>
                            </Tooltip>
                          )}
                          <Tooltip title={translate('upgrade.actions.approve')}>
                            <span>
                              <IconButton
                                size="small"
                                color="primary"
                                disabled={busy === c.id}
                                onClick={() => approve(c.id, false)}
                                aria-label={translate(
                                  'upgrade.actions.approve',
                                )}
                              >
                                {busy === c.id ? (
                                  <CircularProgress size={18} />
                                ) : (
                                  <CheckIcon fontSize="small" />
                                )}
                              </IconButton>
                            </span>
                          </Tooltip>
                          <Tooltip title={translate('upgrade.actions.reject')}>
                            <span>
                              <IconButton
                                size="small"
                                disabled={busy === c.id}
                                onClick={() => reject(c.id)}
                                aria-label={translate('upgrade.actions.reject')}
                              >
                                <CloseIcon fontSize="small" />
                              </IconButton>
                            </span>
                          </Tooltip>
                          <Tooltip
                            title={translate('upgrade.actions.deleteOriginal')}
                          >
                            <span>
                              <IconButton
                                size="small"
                                className={classes.deleteButton}
                                disabled={busy === c.id}
                                onClick={() => deleteOriginal(c)}
                                aria-label={translate(
                                  'upgrade.actions.deleteOriginal',
                                )}
                              >
                                <DeleteIcon fontSize="small" />
                              </IconButton>
                            </span>
                          </Tooltip>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Box>
            )}
            {!loadingPending && pendingHasMore && (
              <Button
                size="small"
                style={{ marginTop: 8 }}
                onClick={() => setPendingEnd((e) => e + PAGE_SIZE)}
              >
                {translate('upgrade.actions.loadMore')}
              </Button>
            )}
          </Box>
        )}

        {tab === 1 && (
          <Box className={classes.section}>
            {loadingHistory && <CircularProgress size={24} />}
            {!loadingHistory && history.length === 0 && (
              <Typography className={classes.hint}>
                {translate('upgrade.empty.history')}
              </Typography>
            )}
            {!loadingHistory && history.length > 0 && (
              <Box className={classes.tableWrap}>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>
                        {translate('upgrade.table.currentTrack')}
                      </TableCell>
                      <TableCell>
                        {translate('upgrade.table.candidate')}
                      </TableCell>
                      <TableCell>{translate('upgrade.table.source')}</TableCell>
                      <TableCell>{translate('upgrade.table.status')}</TableCell>
                      <TableCell>{translate('upgrade.verify.title')}</TableCell>
                      <TableCell>
                        {translate('upgrade.table.reviewedBy')}
                      </TableCell>
                      <TableCell>
                        {translate('upgrade.table.updatedAt')}
                      </TableCell>
                      <TableCell align="right">
                        {translate('upgrade.table.actions')}
                      </TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {history.map((c) => (
                      <TableRow key={c.id}>
                        <TableCell>
                          <Typography variant="body2">
                            {[c.currentArtist, c.currentTitle]
                              .filter(Boolean)
                              .join(' - ')}
                          </Typography>
                          <Typography variant="body2" className={classes.hint}>
                            {[
                              c.currentFormat,
                              c.currentBitRate
                                ? `${c.currentBitRate} kbps`
                                : null,
                            ]
                              .filter(Boolean)
                              .join(' • ')}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2">{c.title}</Typography>
                          {c.status === 'failed' && c.error && (
                            <Typography className={classes.errText}>
                              {c.error}
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell>
                          {translate(`upgrade.source.${c.source}`, {
                            _: c.source,
                          })}
                        </TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            label={translate(`upgrade.status.${c.status}`, {
                              _: c.status,
                            })}
                            color={
                              c.status === 'replaced' ? 'primary' : 'default'
                            }
                          />
                        </TableCell>
                        <TableCell>
                          <VerifyInfoSummary
                            info={c.verifyInfo}
                            translate={translate}
                            classes={classes}
                          />
                        </TableCell>
                        <TableCell>{c.reviewedBy || '—'}</TableCell>
                        <TableCell>
                          {c.updatedAt
                            ? new Date(c.updatedAt).toLocaleString()
                            : '—'}
                        </TableCell>
                        <TableCell align="right">
                          {(c.status === 'failed' ||
                            c.status === 'rejected') && (
                            <Tooltip
                              title={translate(
                                'upgrade.actions.deleteOriginal',
                              )}
                            >
                              <span>
                                <IconButton
                                  size="small"
                                  className={classes.deleteButton}
                                  onClick={() => deleteOriginal(c)}
                                  aria-label={translate(
                                    'upgrade.actions.deleteOriginal',
                                  )}
                                >
                                  <DeleteIcon fontSize="small" />
                                </IconButton>
                              </span>
                            </Tooltip>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Box>
            )}
            {!loadingHistory && historyHasMore && (
              <Button
                size="small"
                style={{ marginTop: 8 }}
                onClick={() => setHistoryEnd((e) => e + PAGE_SIZE)}
              >
                {translate('upgrade.actions.loadMore')}
              </Button>
            )}
          </Box>
        )}
      </CardContent>
    </Card>
  )
}

export default UpgradeQuality
