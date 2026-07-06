import React, { useState, useEffect, useCallback } from 'react'
import { Title, useNotify, usePermissions } from 'react-admin'
import {
  Card,
  CardContent,
  Tabs,
  Tab,
  TextField,
  Button,
  Box,
  Typography,
  List,
  ListItem,
  ListItemText,
  ListItemSecondaryAction,
  IconButton,
  CircularProgress,
  LinearProgress,
  Divider,
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
} from '@material-ui/core'
import GetAppIcon from '@material-ui/icons/GetApp'
import SearchIcon from '@material-ui/icons/Search'
import { makeStyles } from '@material-ui/core/styles'
import { httpClient } from '../dataProvider'
import { APP_NAME } from '../consts'
import { formatBytes } from '../utils'
import config from '../config'

const useStyles = makeStyles((theme) => ({
  root: { marginTop: '1em' },
  field: { marginRight: theme.spacing(1), minWidth: 320 },
  libSelect: { minWidth: 220, marginBottom: theme.spacing(2) },
  actions: {
    marginTop: theme.spacing(2),
    display: 'flex',
    gap: theme.spacing(1),
    flexWrap: 'wrap',
  },
  section: { marginTop: theme.spacing(3) },
  hint: { color: theme.palette.text.secondary, marginTop: theme.spacing(1) },
  itemMeta: { color: theme.palette.text.secondary, fontSize: '0.8rem' },
  progress: {
    marginTop: theme.spacing(2),
    padding: theme.spacing(2),
    border: `1px solid ${theme.palette.divider}`,
    borderRadius: theme.shape.borderRadius,
  },
  errBox: {
    marginTop: theme.spacing(1),
    maxHeight: 120,
    overflow: 'auto',
    fontSize: '0.75rem',
    color: theme.palette.error.main,
  },
}))

// Extract a readable error message from a react-admin HttpError.
const errMsg = (e) => (e && (e.body || e.message)) || 'Lỗi không xác định'

const ImportMusic = () => {
  const classes = useStyles()
  const notify = useNotify()
  const { permissions, loaded: permsLoaded } = usePermissions()
  const [tab, setTab] = useState(0)

  // shared busy flag keyed by an action id so individual buttons can spin
  const [busy, setBusy] = useState(null)

  // target library
  const [libraries, setLibraries] = useState([])
  const [libraryId, setLibraryId] = useState(0)

  // background job + history
  const [job, setJob] = useState(null)
  const [history, setHistory] = useState(null)

  // URL / RSS tab state
  const [url, setUrl] = useState('')
  const [feedItems, setFeedItems] = useState(null)
  const [driveFiles, setDriveFiles] = useState(null)
  const isDrive = /drive\.google\.com/.test(url)

  // Internet Archive tab state
  const [query, setQuery] = useState('')
  const [results, setResults] = useState(null)
  const [openItem, setOpenItem] = useState(null)
  const [files, setFiles] = useState(null)

  const loadHistory = useCallback(() => {
    httpClient('/api/import/history')
      .then(({ json }) => setHistory(json || []))
      .catch(() => {})
  }, [])

  // Load the list of libraries to pick an import destination.
  useEffect(() => {
    httpClient('/api/library')
      .then(({ json }) => {
        const libs = json || []
        setLibraries(libs)
        const def = libs.find((l) => l.id === 1) || libs[0]
        if (def) setLibraryId(def.id)
      })
      .catch(() => {})
  }, [])

  // Poll the running job for progress.
  useEffect(() => {
    if (!job || job.status !== 'running') return undefined
    const t = setTimeout(() => {
      httpClient(`/api/import/job/${job.id}`)
        .then(({ json }) => {
          setJob(json)
          if (json.status !== 'running') {
            notify(
              `Hoàn tất: ${json.completed} tải, ${json.skipped} trùng, ${json.failed} lỗi`,
              json.failed > 0 ? 'warning' : 'info',
            )
            loadHistory()
          }
        })
        .catch(() => setJob((j) => (j ? { ...j, status: 'error' } : j)))
    }, 1200)
    return () => clearTimeout(t)
  }, [job, notify, loadHistory])

  const triggerScan = () =>
    httpClient('/api/import/scan', { method: 'POST' }).catch(() => {})

  const afterImport = (name) => {
    notify(`Đã tải "${name}". Đang quét thư viện...`, 'info')
    triggerScan()
    loadHistory()
  }

  const startJob = async (items) => {
    if (!items || items.length === 0) return
    setBusy('job')
    try {
      const { json } = await httpClient('/api/import/job', {
        method: 'POST',
        body: JSON.stringify({ items, libraryId }),
      })
      setJob({
        id: json.jobId,
        status: 'running',
        total: items.length,
        completed: 0,
        failed: 0,
        skipped: 0,
        errors: [],
      })
    } catch (e) {
      notify(`Không bắt đầu được: ${errMsg(e)}`, 'warning')
    } finally {
      setBusy(null)
    }
  }

  const cancelJob = () => {
    if (job) httpClient(`/api/import/job/${job.id}/cancel`, { method: 'POST' })
  }

  const importFromUrl = async (target, id) => {
    setBusy(id)
    try {
      const { json } = await httpClient('/api/import/url', {
        method: 'POST',
        body: JSON.stringify({ url: target, libraryId }),
      })
      afterImport(json.savedName)
    } catch (e) {
      notify(`Không import được: ${errMsg(e)}`, 'warning')
    } finally {
      setBusy(null)
    }
  }

  const readFeed = async () => {
    setBusy('feed')
    setFeedItems(null)
    try {
      const { json } = await httpClient('/api/import/feed', {
        method: 'POST',
        body: JSON.stringify({ url }),
      })
      setFeedItems(json || [])
      if (!json || json.length === 0) {
        notify('Không tìm thấy file audio nào trong feed', 'info')
      }
    } catch (e) {
      notify(`Không đọc được feed: ${errMsg(e)}`, 'warning')
    } finally {
      setBusy(null)
    }
  }

  const listDrive = async () => {
    setBusy('drive')
    setDriveFiles(null)
    try {
      const { json } = await httpClient('/api/import/drive/list', {
        method: 'POST',
        body: JSON.stringify({ url }),
      })
      setDriveFiles(json || [])
      if (!json || json.length === 0) {
        notify('Không tìm thấy file nhạc trong thư mục Drive', 'info')
      }
    } catch (e) {
      notify(`Không đọc được Google Drive: ${errMsg(e)}`, 'warning')
    } finally {
      setBusy(null)
    }
  }

  const importDriveFile = async (file) => {
    setBusy(file.id)
    try {
      const { json } = await httpClient('/api/import/drive/file', {
        method: 'POST',
        body: JSON.stringify({ id: file.id, name: file.name, libraryId }),
      })
      afterImport(json.savedName)
    } catch (e) {
      notify(`Không tải được "${file.name || file.id}": ${errMsg(e)}`, 'warning')
    } finally {
      setBusy(null)
    }
  }

  const search = async () => {
    setBusy('search')
    setResults(null)
    setOpenItem(null)
    setFiles(null)
    try {
      const { json } = await httpClient(
        `/api/import/archive/search?q=${encodeURIComponent(query)}&rows=25`,
      )
      setResults(json || [])
      if (!json || json.length === 0) {
        notify('Không có kết quả', 'info')
      }
    } catch (e) {
      notify(`Tìm kiếm lỗi: ${errMsg(e)}`, 'warning')
    } finally {
      setBusy(null)
    }
  }

  const openArchiveItem = async (item) => {
    if (openItem === item.identifier) {
      setOpenItem(null)
      setFiles(null)
      return
    }
    setBusy(item.identifier)
    setOpenItem(item.identifier)
    setFiles(null)
    try {
      const { json } = await httpClient(
        `/api/import/archive/files?id=${encodeURIComponent(item.identifier)}`,
      )
      setFiles(json || [])
    } catch (e) {
      notify(`Không lấy được danh sách file: ${errMsg(e)}`, 'warning')
      setOpenItem(null)
    } finally {
      setBusy(null)
    }
  }

  const importArchiveFile = async (identifier, file) => {
    const id = identifier + '/' + file.name
    setBusy(id)
    try {
      const { json } = await httpClient('/api/import/archive', {
        method: 'POST',
        body: JSON.stringify({ identifier, filename: file.name, libraryId }),
      })
      afterImport(json.savedName)
    } catch (e) {
      notify(`Không import được: ${errMsg(e)}`, 'warning')
    } finally {
      setBusy(null)
    }
  }

  const handleTab = (e, v) => {
    setTab(v)
    if (v === 2) loadHistory()
  }

  if (permsLoaded && permissions !== 'admin') {
    return (
      <Card className={classes.root}>
        <Title title={`${APP_NAME} - Import nhạc`} />
        <CardContent>
          <Typography>
            Chỉ quản trị viên mới sử dụng được tính năng import nhạc.
          </Typography>
        </CardContent>
      </Card>
    )
  }

  const jobRunning = job && job.status === 'running'
  const jobDone = job ? job.completed + job.skipped + job.failed : 0
  const jobPct = job && job.total ? Math.round((jobDone / job.total) * 100) : 0

  return (
    <Card className={classes.root}>
      <Title title={`${APP_NAME} - Import nhạc`} />
      <CardContent>
        <Typography variant="h6">Import nhạc từ nguồn công khai</Typography>
        <Typography className={classes.hint}>
          Tải nhạc từ URL/podcast trực tiếp, thư mục Google Drive công khai, hoặc
          kho mở Internet Archive vào thư viện. Chỉ dùng cho nội dung bạn có
          quyền tải.
        </Typography>

        {libraries.length > 1 && (
          <FormControl className={classes.libSelect} margin="normal">
            <InputLabel>Thư viện đích</InputLabel>
            <Select
              value={libraryId}
              onChange={(e) => setLibraryId(e.target.value)}
            >
              {libraries.map((l) => (
                <MenuItem key={l.id} value={l.id}>
                  {l.name}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        )}

        {job && (
          <Box className={classes.progress}>
            <Typography>
              {jobRunning ? 'Đang import' : 'Import xong'}: {jobDone}/{job.total}
            </Typography>
            <LinearProgress
              variant="determinate"
              value={jobPct}
              style={{ marginTop: 8, marginBottom: 8 }}
            />
            <Typography className={classes.hint}>
              Xong {job.completed} • Trùng {job.skipped} • Lỗi {job.failed}
              {job.current ? ` • Đang tải: ${job.current}` : ''}
            </Typography>
            {jobRunning && (
              <Button size="small" onClick={cancelJob} style={{ marginTop: 8 }}>
                Hủy
              </Button>
            )}
            {job.errors && job.errors.length > 0 && (
              <div className={classes.errBox}>
                {job.errors.map((err, i) => (
                  <div key={i}>{err}</div>
                ))}
              </div>
            )}
          </Box>
        )}

        <Box className={classes.section}>
          <Tabs
            value={tab}
            onChange={handleTab}
            indicatorColor="primary"
            textColor="primary"
          >
            <Tab label="URL / RSS" />
            <Tab label="Internet Archive" />
            <Tab label="Lịch sử" />
          </Tabs>
        </Box>

        {tab === 0 && (
          <Box className={classes.section}>
            <TextField
              className={classes.field}
              label="URL file nhạc hoặc RSS/Podcast"
              placeholder="https://.../track.flac"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              variant="outlined"
              size="small"
              fullWidth
            />
            <Box className={classes.actions}>
              <Button
                variant="contained"
                color="primary"
                startIcon={
                  busy === 'url' ? <CircularProgress size={16} /> : <GetAppIcon />
                }
                disabled={!url || busy === 'url'}
                onClick={() => importFromUrl(url, 'url')}
              >
                Tải file nhạc
              </Button>
              <Button
                variant="outlined"
                startIcon={
                  busy === 'feed' ? <CircularProgress size={16} /> : null
                }
                disabled={!url || busy === 'feed'}
                onClick={readFeed}
              >
                Đọc RSS / Podcast
              </Button>
              <Button
                variant="outlined"
                color={isDrive ? 'primary' : 'default'}
                startIcon={
                  busy === 'drive' ? <CircularProgress size={16} /> : null
                }
                disabled={!isDrive || busy === 'drive'}
                onClick={listDrive}
              >
                Liệt kê Google Drive
              </Button>
            </Box>
            {isDrive && (
              <Typography className={classes.hint}>
                {config.googleDriveEnabled
                  ? 'Đang dùng Google Drive API (ổn định, phân trang đầy đủ). '
                  : 'Chưa cấu hình Drive API key — đang dùng chế độ không cần key (có thể giới hạn với thư mục lớn). Đặt ND_GOOGLEDRIVEAPIKEY để bật API. '}
                Nhấn &quot;Liệt kê Google Drive&quot; để xem các file nhạc trong
                thư mục công khai, rồi tải từng file hoặc tất cả.
              </Typography>
            )}

            {driveFiles && driveFiles.length > 0 && (
              <>
                <Box className={classes.actions}>
                  <Button
                    variant="contained"
                    color="primary"
                    startIcon={<GetAppIcon />}
                    disabled={jobRunning || busy === 'job'}
                    onClick={() =>
                      startJob(
                        driveFiles.map((f) => ({
                          type: 'drive',
                          id: f.id,
                          name: f.name,
                        })),
                      )
                    }
                  >
                    Import tất cả ({driveFiles.length})
                  </Button>
                </Box>
                <List>
                  {driveFiles.map((file) => (
                    <ListItem key={file.id} divider>
                      <ListItemText primary={file.name || file.id} />
                      <ListItemSecondaryAction>
                        <IconButton
                          edge="end"
                          aria-label="Tải"
                          disabled={busy === file.id}
                          onClick={() => importDriveFile(file)}
                        >
                          {busy === file.id ? (
                            <CircularProgress size={20} />
                          ) : (
                            <GetAppIcon />
                          )}
                        </IconButton>
                      </ListItemSecondaryAction>
                    </ListItem>
                  ))}
                </List>
              </>
            )}

            {feedItems && feedItems.length > 0 && (
              <>
                <Box className={classes.actions}>
                  <Button
                    variant="contained"
                    color="primary"
                    startIcon={<GetAppIcon />}
                    disabled={jobRunning || busy === 'job'}
                    onClick={() =>
                      startJob(
                        feedItems.map((it) => ({
                          type: 'url',
                          url: it.url,
                          name: it.title,
                        })),
                      )
                    }
                  >
                    Import tất cả ({feedItems.length})
                  </Button>
                </Box>
                <List>
                  {feedItems.map((item, i) => (
                    <ListItem key={i} divider>
                      <ListItemText
                        primary={item.title || item.url}
                        secondary={item.type}
                      />
                      <ListItemSecondaryAction>
                        <IconButton
                          edge="end"
                          aria-label="Tải"
                          disabled={busy === item.url}
                          onClick={() => importFromUrl(item.url, item.url)}
                        >
                          {busy === item.url ? (
                            <CircularProgress size={20} />
                          ) : (
                            <GetAppIcon />
                          )}
                        </IconButton>
                      </ListItemSecondaryAction>
                    </ListItem>
                  ))}
                </List>
              </>
            )}
          </Box>
        )}

        {tab === 1 && (
          <Box className={classes.section}>
            <TextField
              className={classes.field}
              label="Tìm trên Internet Archive"
              placeholder="tên album, nghệ sĩ, netlabel..."
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && query && search()}
              variant="outlined"
              size="small"
              fullWidth
            />
            <Box className={classes.actions}>
              <Button
                variant="contained"
                color="primary"
                startIcon={
                  busy === 'search' ? (
                    <CircularProgress size={16} />
                  ) : (
                    <SearchIcon />
                  )
                }
                disabled={!query || busy === 'search'}
                onClick={search}
              >
                Tìm
              </Button>
            </Box>

            {results && (
              <List className={classes.section}>
                {results.map((item) => (
                  <React.Fragment key={item.identifier}>
                    <ListItem button onClick={() => openArchiveItem(item)}>
                      <ListItemText
                        primary={item.title || item.identifier}
                        secondary={
                          <span className={classes.itemMeta}>
                            {[item.creator, item.year]
                              .filter(Boolean)
                              .join(' • ')}
                          </span>
                        }
                      />
                      {busy === item.identifier && <CircularProgress size={20} />}
                    </ListItem>
                    {openItem === item.identifier && files && (
                      <>
                        {files.length === 0 && (
                          <ListItem>
                            <ListItemText secondary="Không có file audio" />
                          </ListItem>
                        )}
                        {files.length > 0 && (
                          <ListItem style={{ paddingLeft: 32 }}>
                            <Button
                              size="small"
                              variant="outlined"
                              startIcon={<GetAppIcon />}
                              disabled={jobRunning || busy === 'job'}
                              onClick={() =>
                                startJob(
                                  files.map((f) => ({
                                    type: 'archive',
                                    identifier: item.identifier,
                                    filename: f.name,
                                  })),
                                )
                              }
                            >
                              Import tất cả ({files.length})
                            </Button>
                          </ListItem>
                        )}
                        {files.map((f) => {
                          const bid = item.identifier + '/' + f.name
                          return (
                            <ListItem key={f.name} style={{ paddingLeft: 32 }}>
                              <ListItemText
                                primary={f.name}
                                secondary={f.format}
                              />
                              <ListItemSecondaryAction>
                                <IconButton
                                  edge="end"
                                  aria-label="Tải"
                                  disabled={busy === bid}
                                  onClick={() =>
                                    importArchiveFile(item.identifier, f)
                                  }
                                >
                                  {busy === bid ? (
                                    <CircularProgress size={20} />
                                  ) : (
                                    <GetAppIcon />
                                  )}
                                </IconButton>
                              </ListItemSecondaryAction>
                            </ListItem>
                          )
                        })}
                        <Divider />
                      </>
                    )}
                  </React.Fragment>
                ))}
              </List>
            )}
          </Box>
        )}

        {tab === 2 && (
          <Box className={classes.section}>
            {(!history || history.length === 0) && (
              <Typography className={classes.hint}>
                Chưa có lịch sử import.
              </Typography>
            )}
            {history && history.length > 0 && (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Thời gian</TableCell>
                    <TableCell>Nguồn</TableCell>
                    <TableCell>Tên file</TableCell>
                    <TableCell>Dung lượng</TableCell>
                    <TableCell>Người</TableCell>
                    <TableCell>Trạng thái</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {history.map((rec, i) => (
                    <TableRow key={i}>
                      <TableCell>
                        {new Date(rec.time).toLocaleString()}
                      </TableCell>
                      <TableCell>{rec.source}</TableCell>
                      <TableCell>{rec.savedName}</TableCell>
                      <TableCell>{formatBytes(rec.bytes || 0)}</TableCell>
                      <TableCell>{rec.user}</TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          label={
                            rec.status === 'duplicate' ? 'Trùng' : 'Đã import'
                          }
                          color={
                            rec.status === 'duplicate' ? 'default' : 'primary'
                          }
                        />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Box>
        )}
      </CardContent>
    </Card>
  )
}

export default ImportMusic
