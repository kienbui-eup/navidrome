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
  Grid,
} from '@material-ui/core'
import GetAppIcon from '@material-ui/icons/GetApp'
import SearchIcon from '@material-ui/icons/Search'
import FilterListIcon from '@material-ui/icons/FilterList'
import AlbumIcon from '@material-ui/icons/Album'
import ClearIcon from '@material-ui/icons/Clear'
import StarIcon from '@material-ui/icons/Star'
import LanguageIcon from '@material-ui/icons/Language'
import SortIcon from '@material-ui/icons/Sort'
import MusicNoteIcon from '@material-ui/icons/MusicNote'
import GraphicEqIcon from '@material-ui/icons/GraphicEq'
import CloudQueueIcon from '@material-ui/icons/CloudQueue'
import { makeStyles } from '@material-ui/core/styles'
import { httpClient } from '../dataProvider'
import { APP_NAME } from '../consts'
import { formatBytes } from '../utils'
import config from '../config'
import UnifiedImport from './UnifiedImport'


const foldSearch = (s) => {
  if (!s) return ''
  return s
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
}

const formatRegion = (region) => {
  if (!region) return 'Không xác định'
  const r = region.toLowerCase().trim()
  if (r === 'vietnamese' || r === 'vi' || r === 'vie') return 'Việt Nam 🇻🇳'
  if (r === 'english' || r === 'eng' || r === 'en' || r === 'us' || r === 'gb') return 'Âu Mỹ 🇺🇸🇬🇧'
  if (r === 'french' || r === 'fr' || r === 'fre') return 'Pháp 🇫🇷'
  if (r === 'japanese' || r === 'ja' || r === 'jp' || r === 'jpn') return 'Nhật Bản 🇯🇵'
  if (r === 'chinese' || r === 'zh' || r === 'cn' || r === 'chi') return 'Trung Quốc 🇨🇳'
  if (r === 'korean' || r === 'ko' || r === 'kor') return 'Hàn Quốc 🇰🇷'
  return r.charAt(0).toUpperCase() + r.slice(1)
}

const detectRegion = (song) => {
  const g = (song.genre || '').toLowerCase()
  const a = (song.artist || '').toLowerCase()
  const t = (song.title || '').toLowerCase()
  const al = (song.album || '').toLowerCase()
  if (g.includes('viet') || g.includes('vi_') || g.includes('v-pop') || g.includes('nhac tre') || g.includes('tru tinh') || g.includes('que huong') || g.includes('cai luong') ||
      a.includes('trịnh công sơn') || a.includes('lệ quyên') || a.includes('bằng kiều') || a.includes('đàm vĩnh hưng') || a.includes('tuấn hưng') || a.includes('hà anh tuấn') || a.includes('khánh ly')) {
    return 'vietnamese'
  }
  if (g.includes('french') || g.includes('fr_')) return 'french'
  if (g.includes('japan') || g.includes('ja_') || g.includes('j-pop')) return 'japanese'
  if (g.includes('china') || g.includes('zh_') || g.includes('c-pop')) return 'chinese'
  if (g.includes('korea') || g.includes('ko_') || g.includes('k-pop')) return 'korean'
  if (g.includes('english') || g.includes('eng') || g.includes('rock') || g.includes('pop') || g.includes('jazz') || g.includes('blues') || g.includes('metal')) return 'english'
  return g || 'Không xác định'
}

const getFormatRank = (suffix) => {
  const f = (suffix || '').toLowerCase()
  if (f.includes('dsd') || f.includes('dsf') || f.includes('dff')) return 100
  if (f.includes('flac')) return 80
  if (f.includes('alac') || f.includes('m4a')) return 78
  if (f.includes('ape') || f.includes('wavpack')) return 75
  if (f.includes('wav') || f.includes('aiff')) return 70
  if (f.includes('mp3')) return 35
  if (f.includes('ogg') || f.includes('opus')) return 35
  return 20
}

const parseDriveFilename = (file) => {
  const name = file.name || ''
  const id = file.id

  // 1. Get file extension / suffix
  const dotIdx = name.lastIndexOf('.')
  const ext = dotIdx > 0 ? name.slice(dotIdx + 1).toLowerCase() : 'mp3'
  let rawName = dotIdx > 0 ? name.slice(0, dotIdx) : name

  // 2. Try to extract Year (4 consecutive digits like 19xx or 20xx in brackets or standalone)
  let year = null
  const yearMatch = rawName.match(/(?:^|\D)(19\d{2}|20\d{2})(?:\D|$)/)
  if (yearMatch) {
    year = parseInt(yearMatch[1], 10)
    rawName = rawName.replace(/[([\s]*\b(19\d{2}|20\d{2})\b[)\]\s]*/g, ' ').trim()
  }

  // 3. Try to extract Album from bracketed or parenthesized parts
  let album = 'Không rõ Album'
  const bracketMatch = rawName.match(/[([{]([^\])}]+)[\])}]/)
  if (bracketMatch) {
    const candidate = bracketMatch[1].trim()
    if (!['flac', 'dsd', 'dsf', 'wav', 'mp3', 'lossless', '24bit', '192khz'].includes(candidate.toLowerCase())) {
      album = candidate
      rawName = rawName.replace(/[([{][^\])}]+[\])}]/g, ' ').trim()
    }
  }

  // 4. Try to extract Artist and Title from hyphen ( - ) separator
  let artist = 'Không rõ Ca sĩ'
  let title = rawName

  const splitMatch = rawName.split(/\s*(?:[-_—])\s*/)
  if (splitMatch.length >= 2) {
    artist = splitMatch[0].trim()
    title = splitMatch.slice(1).join(' - ').trim()
  }

  title = title.replace(/\s+/g, ' ').trim() || 'Bài hát không tên'
  artist = artist.replace(/\s+/g, ' ').trim() || 'Không rõ Ca sĩ'

  return {
    id,
    title,
    artist,
    album,
    year,
    suffix: ext,
    size: file.size || 0,
    starred: false,
    bitRate: ext === 'flac' ? 1411 : (ext === 'mp3' ? 320 : 0),
  }
}

const useStyles = makeStyles((theme) => ({
  root: {
    marginTop: '0.5em',
    [theme.breakpoints.down('xs')]: {
      marginTop: 0,
      boxShadow: 'none',
      background: 'transparent',
    }
  },
  cardContent: {
    [theme.breakpoints.down('xs')]: {
      padding: '8px !important',
    }
  },
  title: {
    [theme.breakpoints.down('xs')]: {
      fontSize: '1.1rem',
      fontWeight: 'bold',
      marginBottom: theme.spacing(0.5),
    }
  },
  field: { marginRight: theme.spacing(1), minWidth: 320 },
  libSelect: { minWidth: 220, marginBottom: theme.spacing(1) },
  actions: {
    marginTop: theme.spacing(2),
    display: 'flex',
    gap: theme.spacing(1),
    flexWrap: 'wrap',
  },
  section: {
    marginTop: theme.spacing(2),
    [theme.breakpoints.down('xs')]: {
      marginTop: theme.spacing(1),
    }
  },
  hint: {
    color: theme.palette.text.secondary,
    marginTop: theme.spacing(0.5),
    fontSize: '0.85rem',
    [theme.breakpoints.down('xs')]: {
      display: 'none',
    }
  },
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

  // Recovery active import job on mount (prevent tab-switching state loss)
  useEffect(() => {
    const activeJobId = localStorage.getItem('activeImportJobId')
    if (activeJobId) {
      httpClient(`/api/import/job/${activeJobId}`)
        .then(({ json }) => {
          if (json && json.status === 'running') {
            setJob(json)
          } else {
            localStorage.removeItem('activeImportJobId')
          }
        })
        .catch(() => {
          localStorage.removeItem('activeImportJobId')
        })
    }
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
            localStorage.removeItem('activeImportJobId')
          }
        })
        .catch(() => {
          setJob((j) => (j ? { ...j, status: 'error' } : j))
          localStorage.removeItem('activeImportJobId')
        })
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
      localStorage.setItem('activeImportJobId', json.jobId)
    } catch (e) {
      notify(`Không bắt đầu được: ${errMsg(e)}`, 'warning')
    } finally {
      setBusy(null)
    }
  }

  const cancelJob = () => {
    if (job) {
      httpClient(`/api/import/job/${job.id}/cancel`, { method: 'POST' })
      localStorage.removeItem('activeImportJobId')
    }
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

  useEffect(() => {
    if (isDrive && url.trim()) {
      const t = setTimeout(() => {
        listDrive()
      }, 500)
      return () => clearTimeout(t)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [url, isDrive])

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

  const onRemoteJobStarted = (jobId, count) => {
    setJob({
      id: jobId,
      status: 'running',
      total: count,
      completed: 0,
      failed: 0,
      skipped: 0,
      errors: [],
    })
    localStorage.setItem('activeImportJobId', jobId)
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
      <CardContent className={classes.cardContent}>

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
            {job.items && job.items.length > 0 && (
              <Box style={{ marginTop: 12, maxHeight: 220, overflow: 'auto', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 4, padding: 8, background: 'rgba(0,0,0,0.15)' }}>
                {job.items.map((it, idx) => (
                  <Box key={idx} display="flex" flexDirection="column" style={{ marginBottom: 6, paddingBottom: 6, borderBottom: idx < job.items.length - 1 ? '1px solid rgba(255,255,255,0.05)' : 'none' }}>
                    <Box display="flex" justifyContent="space-between" alignItems="center">
                      <Typography variant="body2" style={{ fontSize: '0.82rem', fontWeight: it.status === 'downloading' ? 'bold' : 'normal', color: it.status === 'completed' ? '#4caf50' : (it.status === 'failed' ? '#f44336' : (it.status === 'downloading' ? '#2196f3' : (it.status === 'skipped' ? '#ff9800' : '#888'))) }}>
                        {it.status === 'downloading' ? '📥 ' : (it.status === 'completed' ? '✅ ' : (it.status === 'failed' ? '❌ ' : (it.status === 'skipped' ? '⏭️ ' : '⏳ ')))}
                        {it.label}
                      </Typography>
                      <Typography variant="caption" style={{ color: '#aaa', marginLeft: 8, flexShrink: 0 }}>
                        {it.status === 'downloading' ? `${it.progress}% (${formatBytes(it.size || 0)})` : (it.status === 'completed' ? 'Xong' : (it.status === 'failed' ? 'Lỗi' : (it.status === 'skipped' ? 'Trùng' : 'Đang chờ')))}
                      </Typography>
                    </Box>
                    {it.status === 'downloading' && (
                      <LinearProgress variant="determinate" value={it.progress} style={{ marginTop: 4, height: 3, borderRadius: 1 }} />
                    )}
                  </Box>
                ))}
              </Box>
            )}
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
            onChange={(e, v) => {
              setTab(v)
              if (v === 1) loadHistory()
            }}
            indicatorColor="primary"
            textColor="primary"
          >
            <Tab label="Tìm & Nhập Nhạc" />
            <Tab label="Lịch sử Import" />
          </Tabs>
        </Box>

        {tab === 0 && (
          <UnifiedImport
            libraryId={libraryId}
            onImported={afterImport}
            onJobStarted={onRemoteJobStarted}
          />
        )}

        {tab === 1 && (
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
