import React, { useCallback, useEffect, useRef, useState } from 'react'
import { useNotify } from 'react-admin'
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  Grid,
  IconButton,
  InputLabel,
  List,
  ListItem,
  ListItemSecondaryAction,
  ListItemText,
  MenuItem,
  Select,
  TextField,
  Typography,
  Divider,
} from '@material-ui/core'
import { makeStyles } from '@material-ui/core/styles'
import AddIcon from '@material-ui/icons/Add'
import ArrowBackIcon from '@material-ui/icons/ArrowBack'
import DeleteIcon from '@material-ui/icons/Delete'
import EditIcon from '@material-ui/icons/Edit'
import GetAppIcon from '@material-ui/icons/GetApp'
import PlayArrowIcon from '@material-ui/icons/PlayArrow'
import SearchIcon from '@material-ui/icons/Search'
import StopIcon from '@material-ui/icons/Stop'
import FilterListIcon from '@material-ui/icons/FilterList'
import AlbumIcon from '@material-ui/icons/Album'
import ClearIcon from '@material-ui/icons/Clear'
import StarIcon from '@material-ui/icons/Star'
import LanguageIcon from '@material-ui/icons/Language'
import SortIcon from '@material-ui/icons/Sort'
import MusicNoteIcon from '@material-ui/icons/MusicNote'
import GraphicEqIcon from '@material-ui/icons/GraphicEq'
import CloudQueueIcon from '@material-ui/icons/CloudQueue'
import { httpClient } from '../dataProvider'
import { formatBytes } from '../utils'

const useLocalStyles = makeStyles((theme) => ({
  filterContainer: {
    marginTop: theme.spacing(2),
    marginBottom: theme.spacing(2),
    padding: theme.spacing(2.5),
    backgroundColor: theme.palette.background.paper,
    borderRadius: theme.shape.borderRadius,
    border: `1px solid ${theme.palette.divider}`,
    boxShadow: '0 4px 20px rgba(0,0,0,0.15)',
  },
  albumHeader: {
    backgroundColor: theme.palette.action.hover,
    padding: theme.spacing(1.5, 2.5),
    marginTop: theme.spacing(3),
    borderRadius: theme.shape.borderRadius,
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: theme.spacing(1.5),
    borderLeft: `4px solid ${theme.palette.primary.main}`,
  },
  albumTitle: {
    fontWeight: 'bold',
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(1),
  },
  songList: {
    paddingLeft: theme.spacing(1),
  },
  metaSeparator: {
    margin: theme.spacing(0, 1),
    opacity: 0.5,
  },
  interactiveSelect: {
    transition: 'all 0.2s ease-in-out',
    '&:hover': {
      borderColor: theme.palette.primary.main,
    },
  },
  songItem: {
    transition: 'background-color 0.2s ease',
    '&:hover': {
      backgroundColor: 'rgba(255, 255, 255, 0.03)',
    },
  },
}))

const errMsg = (e) => (e && (e.body || e.message)) || 'Lỗi không xác định'

const formatDuration = (secs) => {
  if (!secs) return ''
  const m = Math.floor(secs / 60)
  const s = Math.round(secs % 60)
  return `${m}:${String(s).padStart(2, '0')}`
}

const previewSrc = (serverId, songId) => {
  const token = localStorage.getItem('token')
  return `/api/import/remote/preview?server=${encodeURIComponent(
    serverId,
  )}&id=${encodeURIComponent(songId)}&jwt=${encodeURIComponent(token || '')}`
}

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

const emptyForm = { id: '', name: '', url: '', username: '', password: '' }

const RemoteImport = ({ libraryId, onJobStarted, classes }) => {
  const localClasses = useLocalStyles()
  const notify = useNotify()
  const [servers, setServers] = useState([])
  const [serverId, setServerId] = useState('')
  const [dialog, setDialog] = useState(null)
  const [testState, setTestState] = useState(null)
  const [query, setQuery] = useState('')
  const [result, setResult] = useState(null)
  const [view, setView] = useState({ type: 'search' })
  const [busy, setBusy] = useState(null)
  const [playing, setPlaying] = useState(null)
  const audioRef = useRef(null)

  // Filters State
  const [filterSinger, setFilterSinger] = useState('')
  const [filterComposer, setFilterComposer] = useState('')
  const [filterYear, setFilterYear] = useState('all')
  const [filterSize, setFilterSize] = useState('all')
  const [filterFormat, setFilterFormat] = useState('all')
  
  // Grouping & Sorting State
  const [groupBy, setGroupBy] = useState('album') // 'album' | 'artist' | 'region' | 'none'
  const [sortBy, setSortBy] = useState('format') // 'format' (default best first) | 'year' | 'playcount' | 'likes' | 'quality'

  useEffect(() => {
    const audio = new Audio()
    audio.preload = 'none'
    audio.onended = () => setPlaying(null)
    audio.onerror = () =>
      setPlaying((p) => {
        if (p) notify('Không phát được bản nghe thử', 'warning')
        return null
      })
    audioRef.current = audio
    return () => {
      audio.pause()
      audio.removeAttribute('src')
    }
  }, [notify])

  const stopPreview = () => {
    if (audioRef.current) {
      audioRef.current.pause()
    }
    setPlaying(null)
  }

  const togglePreview = (song) => {
    if (playing === song.id) {
      stopPreview()
      return
    }
    const audio = audioRef.current
    audio.pause()
    audio.src = previewSrc(serverId, song.id)
    setPlaying(song.id)
    audio.play().catch(() => {
      notify('Không phát được bản nghe thử', 'warning')
      setPlaying(null)
    })
  }

  const loadServers = useCallback(() => {
    httpClient('/api/import/remote/servers')
      .then(({ json }) => {
        const list = json || []
        setServers(list)
        setServerId((cur) =>
          list.some((s) => s.id === cur) ? cur : list[0] ? list[0].id : '',
        )
      })
      .catch(() => {})
  }, [])

  useEffect(() => {
    loadServers()
  }, [loadServers])

  useEffect(() => {
    if (serverId) {
      search()
    } else {
      setResult(null)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [serverId])

  const openDialog = (server) => {
    setTestState(null)
    setDialog(server ? { ...server, password: '' } : { ...emptyForm })
  }

  const testConnection = async () => {
    setTestState('busy')
    try {
      await httpClient('/api/import/remote/servers/test', {
        method: 'POST',
        body: JSON.stringify(dialog),
      })
      setTestState('ok')
    } catch (e) {
      setTestState(errMsg(e))
    }
  }

  const saveServer = async () => {
    setBusy('save')
    try {
      await httpClient(
        dialog.id
          ? `/api/import/remote/servers/${dialog.id}`
          : '/api/import/remote/servers',
        { method: dialog.id ? 'PUT' : 'POST', body: JSON.stringify(dialog) },
      )
      setDialog(null)
      loadServers()
      notify('Đã lưu server nguồn', 'info')
    } catch (e) {
      notify(`Không lưu được: ${errMsg(e)}`, 'warning')
    } finally {
      setBusy(null)
    }
  }

  const deleteServer = async () => {
    const server = servers.find((s) => s.id === serverId)
    if (!server) return
    if (!window.confirm(`Xóa server nguồn "${server.name}"?`)) return
    try {
      await httpClient(`/api/import/remote/servers/${server.id}`, {
        method: 'DELETE',
      })
      loadServers()
    } catch (e) {
      notify(`Không xóa được: ${errMsg(e)}`, 'warning')
    }
  }

  const search = async () => {
    setBusy('search')
    setResult(null)
    setView({ type: 'search' })
    stopPreview()
    try {
      const params = new URLSearchParams({ server: serverId, q: query })
      const { json } = await httpClient(
        `/api/import/remote/search?${params.toString()}`,
      )
      setResult(json || { songs: [], albums: [], artists: [] })
    } catch (e) {
      notify(`Tìm kiếm lỗi: ${errMsg(e)}`, 'warning')
    } finally {
      setBusy(null)
    }
  }

  const openArtist = async (artist) => {
    setBusy(`open-${artist.id}`)
    try {
      const params = new URLSearchParams({ server: serverId, id: artist.id })
      const { json } = await httpClient(
        `/api/import/remote/artist?${params.toString()}`,
      )
      setView({ type: 'artist', artist, albums: json || [] })
    } catch (e) {
      notify(`Không mở được ca sĩ: ${errMsg(e)}`, 'warning')
    } finally {
      setBusy(null)
    }
  }

  const openAlbum = async (album) => {
    setBusy(`open-${album.id}`)
    try {
      const params = new URLSearchParams({ server: serverId, id: album.id })
      const { json } = await httpClient(
        `/api/import/remote/album?${params.toString()}`,
      )
      setView({ type: 'album', album, songs: json || [] })
    } catch (e) {
      notify(`Không mở được album: ${errMsg(e)}`, 'warning')
    } finally {
      setBusy(null)
    }
  }

  const startImport = async (type, id, label, confirmText) => {
    if (confirmText && !window.confirm(confirmText)) return
    setBusy(`import-${id}`)
    try {
      const { json } = await httpClient('/api/import/remote/import', {
        method: 'POST',
        body: JSON.stringify({ serverId, type, id, libraryId }),
      })
      notify(`Đã xếp hàng ${json.count} bài từ "${label}"`, 'info')
      onJobStarted(json.jobId, json.count)
    } catch (e) {
      notify(`Không import được "${label}": ${errMsg(e)}`, 'warning')
    } finally {
      setBusy(null)
    }
  }

  const importGroup = async (group) => {
    if (!group || !group.songs || group.songs.length === 0) return
    const key = 'group-' + group.key
    setBusy(key)
    try {
      const items = group.songs.map((song) => ({
        type: 'remote',
        serverId: serverId,
        id: song.id,
        name: song.title,
      }))
      const { json } = await httpClient('/api/import/job', {
        method: 'POST',
        body: JSON.stringify({ items, libraryId }),
      })
      if (onJobStarted) {
        onJobStarted(json.jobId, items.length)
        notify(
          `Đã bắt đầu import nhóm "${group.title}" (${items.length} bài)...`,
          'info',
        )
      } else {
        notify(`Đã thêm nhóm "${group.title}" vào hàng đợi tải.`, 'info')
      }
    } catch (e) {
      notify(`Không tải được nhóm: ${errMsg(e)}`, 'warning')
    } finally {
      setBusy(null)
    }
  }

  const importButton = (type, id, label, confirmText) => (
    <IconButton
      edge="end"
      aria-label="Import"
      disabled={busy === `import-${id}`}
      onClick={() => startImport(type, id, label, confirmText)}
    >
      {busy === `import-${id}` ? (
        <CircularProgress size={20} />
      ) : (
        <GetAppIcon />
      )}
    </IconButton>
  )

  const renderFormatChip = (song) => {
    const f = (song.suffix || '').toLowerCase()
    let style = {}
    let label = song.suffix ? song.suffix.toUpperCase() : ''
    
    const isLossless = getFormatRank(song.suffix) >= 70 || song.bitRate > 320
    
    if (f.includes('dsd') || f.includes('dsf') || f.includes('dff')) {
      style = {
        background: 'linear-gradient(45deg, #FFD700, #FFA500)',
        color: '#000',
        fontWeight: 'bold',
      }
    } else if (isLossless) {
      style = {
        backgroundColor: '#00e5ff',
        color: '#000',
        fontWeight: '500',
      }
    }
    return (
      <Chip
        size="small"
        label={label}
        style={style}
        color={!style.backgroundColor && isLossless ? 'primary' : 'default'}
      />
    )
  }

  const renderLikes = (song) => {
    if (!song.starred) return null
    return (
      <Box display="inline-flex" alignItems="center" style={{ color: '#ffb300', fontSize: '0.8rem', gap: 2, marginRight: 8 }}>
        <StarIcon style={{ fontSize: '0.9rem' }} />
        <span>Yêu thích</span>
      </Box>
    )
  }

  const songs = (result && result.songs) || []

  // Dynamic filter lists
  const uniqueYears = Array.from(
    new Set(songs.map((s) => s.year).filter(Boolean)),
  )
    .sort()
    .reverse()
  const uniqueFormats = Array.from(
    new Set(songs.map((s) => s.suffix).filter(Boolean)),
  ).sort()

  // 1. Filter local songs
  const filteredSongs = songs.filter((song) => {
    if (filterSinger.trim()) {
      const s = foldSearch(filterSinger.trim())
      const artist = foldSearch(song.artist || '')
      if (!artist.includes(s)) return false
    }
    if (filterComposer.trim()) {
      const c = foldSearch(filterComposer.trim())
      const title = foldSearch(song.title || '')
      const artist = foldSearch(song.artist || '')
      if (!title.includes(c) && !artist.includes(c)) return false
    }
    if (filterYear !== 'all') {
      if (song.year !== parseInt(filterYear)) return false
    }
    if (filterFormat !== 'all') {
      if (song.suffix !== filterFormat) return false
    }
    if (filterSize !== 'all') {
      const sizeMB = (song.size || 0) / (1024 * 1024)
      if (filterSize === 'small' && sizeMB >= 10) return false
      if (filterSize === 'medium' && (sizeMB < 10 || sizeMB >= 50)) return false
      if (filterSize === 'large' && (sizeMB < 50 || sizeMB >= 100)) return false
      if (filterSize === 'xlarge' && sizeMB < 100) return false
    }
    return true
  })

  // 2. Sort filtered songs
  const sortedSongs = [...filteredSongs].sort((a, b) => {
    if (sortBy === 'year') {
      const yA = a.year || 0
      const yB = b.year || 0
      if (yB !== yA) return yB - yA
    } else if (sortBy === 'playcount') {
      const pA = a.playCount || 0
      const pB = b.playCount || 0
      if (pB !== pA) return pB - pA
    } else if (sortBy === 'likes') {
      const sA = a.starred ? 1 : 0
      const sB = b.starred ? 1 : 0
      if (sB !== sA) return sB - sA
    } else if (sortBy === 'quality') {
      const qA = a.bitRate || 0
      const qB = b.bitRate || 0
      if (qB !== qA) return qB - qA
    } else if (sortBy === 'format') {
      const rA = getFormatRank(a.suffix)
      const rB = getFormatRank(b.suffix)
      if (rB !== rA) return rB - rA
    }
    return 0
  })

  // 3. Group songs
  const groupSongs = (songsList, groupByOption) => {
    const groups = {}
    songsList.forEach((song) => {
      let key = ''
      let title = ''
      let subtitle = ''
      let icon = <AlbumIcon color="primary" />

      if (groupByOption === 'album') {
        key = song.album || 'Không rõ Album'
        title = key
        subtitle = [
          song.artist,
          song.year,
        ].filter(Boolean).join(' • ')
        icon = <AlbumIcon color="primary" />
      } else if (groupByOption === 'artist') {
        key = song.artist || 'Không rõ Ca sĩ'
        title = key
        subtitle = 'Ca sĩ trình bày'
        icon = <MusicNoteIcon color="primary" />
      } else if (groupByOption === 'region') {
        const reg = detectRegion(song)
        key = reg
        title = formatRegion(reg)
        subtitle = 'Khu vực / Ngôn ngữ phát hành'
        icon = <LanguageIcon color="primary" />
      } else {
        key = 'all'
        title = 'Tất cả bài hát'
        subtitle = `${songsList.length} bài hát`
        icon = <GraphicEqIcon color="primary" />
      }

      if (!groups[key]) {
        groups[key] = {
          key: key,
          title: title,
          subtitle: subtitle,
          icon: icon,
          songs: [],
        }
      }
      groups[key].songs.push(song)
    })

    return Object.values(groups).sort((a, b) => {
      if (groupByOption === 'none') return 0
      const isUnkA = a.title.includes('Không rõ') || a.title.includes('Không xác định')
      const isUnkB = b.title.includes('Không rõ') || b.title.includes('Không xác định')
      if (isUnkA && !isUnkB) return 1
      if (!isUnkA && isUnkB) return -1
      return a.title.localeCompare(b.title, 'vi')
    })
  }

  const getButtonText = (group) => {
    if (groupBy === 'album') return `Tải cả album (${group.songs.length})`
    if (groupBy === 'artist') return `Tải tất cả bài của ca sĩ (${group.songs.length})`
    if (groupBy === 'region') return `Tải tất cả thuộc khu vực (${group.songs.length})`
    return `Tải toàn bộ nhóm (${group.songs.length})`
  }

  const songItem = (song) => {
    const meta = [
      song.artist,
      song.album,
      formatDuration(song.duration),
      song.size ? formatBytes(song.size) : null,
    ]
      .filter(Boolean)
      .join(' • ')
    return (
      <ListItem key={song.id} divider className={localClasses.songItem}>
        <IconButton aria-label="Nghe thử" onClick={() => togglePreview(song)}>
          {playing === song.id ? <StopIcon /> : <PlayArrowIcon />}
        </IconButton>
        <ListItemText
          primary={
            <Box display="flex" alignItems="center" flexWrap="wrap" style={{ gap: 8 }}>
              <Typography variant="body1" style={{ fontWeight: 500 }}>{song.title}</Typography>
              {renderFormatChip(song)}
              {song.bitRate > 0 && (
                <Chip
                  size="small"
                  variant="outlined"
                  label={`${song.bitRate} kbps`}
                />
              )}
              {song.playCount > 0 && (
                <Chip
                  size="small"
                  variant="outlined"
                  label={`🎧 ${song.playCount} lượt nghe`}
                />
              )}
              {renderLikes(song)}
            </Box>
          }
          secondary={<span className={classes.itemMeta}>{meta}</span>}
        />
        <ListItemSecondaryAction>
          {importButton('song', song.id, song.title)}
        </ListItemSecondaryAction>
      </ListItem>
    )
  }

  const albumItem = (album) => (
    <ListItem
      key={album.id}
      divider
      button
      onClick={() => openAlbum(album)}
      disabled={busy === `open-${album.id}`}
    >
      <ListItemText
        primary={album.name}
        secondary={
          <span className={classes.itemMeta}>
            {[
              album.artist,
              album.songCount ? `${album.songCount} bài` : null,
              album.year || null,
            ]
              .filter(Boolean)
              .join(' • ')}
          </span>
        }
      />
      <ListItemSecondaryAction>
        {importButton('album', album.id, album.name)}
      </ListItemSecondaryAction>
    </ListItem>
  )

  const artistItem = (artist) => (
    <ListItem
      key={artist.id}
      divider
      button
      onClick={() => openArtist(artist)}
      disabled={busy === `open-${artist.id}`}
    >
      <ListItemText
        primary={artist.name}
        secondary={
          <span className={classes.itemMeta}>
            {artist.albumCount ? `${artist.albumCount} album` : ''}
          </span>
        }
      />
      <ListItemSecondaryAction>
        {importButton(
          'artist',
          artist.id,
          artist.name,
          `Import toàn bộ ${artist.albumCount || '?'} album của "${artist.name}"?`,
        )}
      </ListItemSecondaryAction>
    </ListItem>
  )

  const selectedServer = servers.find((s) => s.id === serverId)

  return (
    <Box className={classes.section}>
      <Box className={classes.actions} style={{ marginTop: 0 }}>
        {servers.length > 0 && (
          <FormControl style={{ minWidth: 220 }}>
            <InputLabel>Server nguồn</InputLabel>
            <Select
              value={serverId}
              onChange={(e) => setServerId(e.target.value)}
            >
              {servers.map((s) => (
                <MenuItem key={s.id} value={s.id}>
                  {s.name}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        )}
        <Button startIcon={<AddIcon />} onClick={() => openDialog(null)}>
          Thêm server
        </Button>
        {selectedServer && (
          <>
            <Button
              startIcon={<EditIcon />}
              onClick={() => openDialog(selectedServer)}
            >
              Sửa
            </Button>
            <Button startIcon={<DeleteIcon />} onClick={deleteServer}>
              Xóa
            </Button>
          </>
        )}
      </Box>

      {servers.length === 0 && (
        <Typography className={classes.hint}>
          Chưa có server nguồn nào. Thêm một server vi2play/Subsonic khác
          (địa chỉ + tài khoản) để tìm và tải nhạc từ đó.
        </Typography>
      )}

      {selectedServer && (
        <>
          <Box className={classes.actions}>
            <TextField
              className={classes.field}
              label="Tìm bài hát, album, ca sĩ"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && search()}
              variant="outlined"
              size="small"
            />
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
              disabled={busy === 'search'}
              onClick={search}
            >
              Tìm
            </Button>
          </Box>

          {view.type !== 'search' && (
            <Box className={classes.actions}>
              <Button
                startIcon={<ArrowBackIcon />}
                onClick={() => setView({ type: 'search' })}
              >
                Quay lại kết quả
              </Button>
              <Typography style={{ alignSelf: 'center' }}>
                {view.type === 'artist'
                  ? `Album của ${view.artist.name}`
                  : `${view.album.name}${view.album.artist ? ` — ${view.album.artist}` : ''}`}
              </Typography>
            </Box>
          )}

          {view.type === 'search' && (
            <>
              {busy === 'search' && !result && (
                <Box display="flex" justifyContent="center" my={4}>
                  <CircularProgress />
                </Box>
              )}

              {result && (
                <>
                  {/* Premium Filters, Grouping & Sorting Panel */}
                  {songs.length > 0 && (
                    <Box className={localClasses.filterContainer}>
                      <Box
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 8,
                          marginBottom: 16,
                          flexWrap: 'wrap',
                        }}
                      >
                        <FilterListIcon color="primary" />
                        <Typography variant="subtitle1" style={{ fontWeight: 'bold' }}>
                          Bộ lọc & Sắp xếp ({sortedSongs.length} / {songs.length} bài)
                        </Typography>
                        {(filterSinger ||
                          filterComposer ||
                          filterYear !== 'all' ||
                          filterSize !== 'all' ||
                          filterFormat !== 'all' ||
                          groupBy !== 'album' ||
                          sortBy !== 'format') && (
                          <Button
                            size="small"
                            variant="text"
                            color="secondary"
                            startIcon={<ClearIcon />}
                            onClick={() => {
                              setFilterSinger('')
                              setFilterComposer('')
                              setFilterYear('all')
                              setFilterSize('all')
                              setFilterFormat('all')
                              setGroupBy('album')
                              setSortBy('format')
                            }}
                          >
                            Đặt lại mặc định
                          </Button>
                        )}
                      </Box>
                      <Grid container spacing={2}>
                        <Grid item xs={12} sm={6} md={3}>
                          <TextField
                            label="Ca sĩ"
                            placeholder="Nhập tên ca sĩ..."
                            value={filterSinger}
                            onChange={(e) => setFilterSinger(e.target.value)}
                            variant="outlined"
                            size="small"
                            fullWidth
                          />
                        </Grid>
                        <Grid item xs={12} sm={6} md={3}>
                          <TextField
                            label="Tên bài / Tác giả"
                            placeholder="Nhập tên bài..."
                            value={filterComposer}
                            onChange={(e) => setFilterComposer(e.target.value)}
                            variant="outlined"
                            size="small"
                            fullWidth
                          />
                        </Grid>
                        <Grid item xs={12} sm={4} md={2}>
                          <FormControl variant="outlined" size="small" fullWidth>
                            <InputLabel id="filter-year-label">Năm</InputLabel>
                            <Select
                              labelId="filter-year-label"
                              label="Năm"
                              value={filterYear}
                              onChange={(e) => setFilterYear(e.target.value)}
                            >
                              <MenuItem value="all">Tất cả năm</MenuItem>
                              {uniqueYears.map((y) => (
                                <MenuItem key={y} value={y}>
                                  {y}
                                </MenuItem>
                              ))}
                            </Select>
                          </FormControl>
                        </Grid>
                        <Grid item xs={12} sm={4} md={2}>
                          <FormControl variant="outlined" size="small" fullWidth>
                            <InputLabel id="filter-size-label">Dung lượng</InputLabel>
                            <Select
                              labelId="filter-size-label"
                              label="Dung lượng"
                              value={filterSize}
                              onChange={(e) => setFilterSize(e.target.value)}
                            >
                              <MenuItem value="all">Mọi dung lượng</MenuItem>
                              <MenuItem value="small">Nhỏ (&lt; 10MB)</MenuItem>
                              <MenuItem value="medium">Vừa (10MB - 50MB)</MenuItem>
                              <MenuItem value="large">Lớn (50MB - 100MB)</MenuItem>
                              <MenuItem value="xlarge">Cực lớn (&gt; 100MB)</MenuItem>
                            </Select>
                          </FormControl>
                        </Grid>
                        <Grid item xs={12} sm={4} md={2}>
                          <FormControl variant="outlined" size="small" fullWidth>
                            <InputLabel id="filter-format-label">Định dạng</InputLabel>
                            <Select
                              labelId="filter-format-label"
                              label="Định dạng"
                              value={filterFormat}
                              onChange={(e) => setFilterFormat(e.target.value)}
                            >
                              <MenuItem value="all">Mọi định dạng</MenuItem>
                              {uniqueFormats.map((f) => (
                                <MenuItem key={f} value={f}>
                                  {f.toUpperCase()}
                                </MenuItem>
                              ))}
                            </Select>
                          </FormControl>
                        </Grid>

                        <Grid item xs={12}>
                          <Divider style={{ margin: '8px 0 16px 0', opacity: 0.5 }} />
                        </Grid>
                        
                        <Grid item xs={12} sm={6}>
                          <FormControl variant="outlined" size="small" fullWidth>
                            <InputLabel id="group-by-label">Nhóm danh sách theo</InputLabel>
                            <Select
                              labelId="group-by-label"
                              label="Nhóm danh sách theo"
                              value={groupBy}
                              onChange={(e) => setGroupBy(e.target.value)}
                              className={localClasses.interactiveSelect}
                              startAdornment={<AlbumIcon style={{ marginRight: 8, opacity: 0.7 }} />}
                            >
                              <MenuItem value="album">📀 Nhóm theo Album (Mặc định)</MenuItem>
                              <MenuItem value="artist">🎙️ Nhóm theo Ca sĩ / Nghệ sĩ</MenuItem>
                              <MenuItem value="region">🌏 Nhóm theo Region / Quốc gia</MenuItem>
                              <MenuItem value="none">📄 Không nhóm (Danh sách phẳng)</MenuItem>
                            </Select>
                          </FormControl>
                        </Grid>
                        
                        <Grid item xs={12} sm={6}>
                          <FormControl variant="outlined" size="small" fullWidth>
                            <InputLabel id="sort-by-label">Sắp xếp theo (từ cao đến thấp)</InputLabel>
                            <Select
                              labelId="sort-by-label"
                              label="Sắp xếp theo (từ cao đến thấp)"
                              value={sortBy}
                              onChange={(e) => setSortBy(e.target.value)}
                              className={localClasses.interactiveSelect}
                              startAdornment={<SortIcon style={{ marginRight: 8, opacity: 0.7 }} />}
                            >
                              <MenuItem value="format">🎵 Định dạng cao cấp nhất (DSD, FLAC 24bit...)</MenuItem>
                              <MenuItem value="year">📅 Năm phát hành (Mới nhất)</MenuItem>
                              <MenuItem value="playcount">🎧 Lượt nghe (PlayCount) nhiều nhất</MenuItem>
                              <MenuItem value="likes">⭐ Lượt thích (Starred) ưu tiên</MenuItem>
                              <MenuItem value="quality">🎧 BitRate chất lượng cao nhất</MenuItem>
                            </Select>
                          </FormControl>
                        </Grid>
                      </Grid>
                    </Box>
                  )}

                  {/* Render Results */}
                  {groupBy !== 'none' && sortedSongs.length > 0 ? (
                    groupSongs(sortedSongs, groupBy).map((group) => {
                      const key = 'group-container-' + group.key
                      return (
                        <Box key={key} className={classes.section}>
                          <Box className={localClasses.albumHeader}>
                            <Box style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                              {group.icon}
                              <Box>
                                <Typography
                                  variant="subtitle1"
                                  className={localClasses.albumTitle}
                                >
                                  {group.title}
                                </Typography>
                                <Typography variant="body2" color="textSecondary">
                                  {group.subtitle}
                                </Typography>
                              </Box>
                            </Box>
                            <Button
                              variant="outlined"
                              size="small"
                              startIcon={
                                busy === 'group-' + group.key ? (
                                  <CircularProgress size={14} />
                                ) : (
                                  <CloudQueueIcon />
                                )
                              }
                              disabled={busy === 'group-' + group.key}
                              onClick={() => importGroup(group)}
                            >
                              {getButtonText(group)}
                            </Button>
                          </Box>
                          <List dense className={localClasses.songList}>
                            {group.songs.map(songItem)}
                          </List>
                        </Box>
                      )
                    })
                  ) : (
                    sortedSongs.length > 0 && (
                      <Box className={classes.section}>
                        <Typography variant="subtitle1">Bài hát</Typography>
                        <List dense>{sortedSongs.map(songItem)}</List>
                      </Box>
                    )
                  )}

                  {result.albums.length > 0 && view.type === 'search' && (
                    <Box className={classes.section}>
                      <Typography variant="subtitle1" style={{ fontWeight: 'bold', marginTop: 16 }}>Album</Typography>
                      <List dense>{result.albums.map(albumItem)}</List>
                    </Box>
                  )}
                  {result.artists.length > 0 && view.type === 'search' && (
                    <Box className={classes.section}>
                      <Typography variant="subtitle1" style={{ fontWeight: 'bold', marginTop: 16 }}>Ca sĩ / Nghệ sĩ</Typography>
                      <List dense>{result.artists.map(artistItem)}</List>
                    </Box>
                  )}

                  {sortedSongs.length === 0 && result.albums.length === 0 && result.artists.length === 0 && (
                    <Typography className={classes.hint}>
                      Không tìm thấy kết quả nào.
                    </Typography>
                  )}
                </>
              )}
            </>
          )}

          {view.type === 'artist' && (
            <List dense className={classes.section}>
              {view.albums.length === 0 ? (
                <Typography className={classes.hint}>
                  Không có album nào.
                </Typography>
              ) : (
                view.albums.map(albumItem)
              )}
            </List>
          )}

          {view.type === 'album' && (
            <List dense className={classes.section}>
              {view.songs.length === 0 ? (
                <Typography className={classes.hint}>
                  Không có bài nào.
                </Typography>
              ) : (
                view.songs.map(songItem)
              )}
            </List>
          )}
        </>
      )}

      <Dialog open={!!dialog} onClose={() => setDialog(null)} fullWidth>
        <DialogTitle>
          {dialog && dialog.id ? 'Sửa server nguồn' : 'Thêm server nguồn'}
        </DialogTitle>
        {dialog && (
          <DialogContent>
            <TextField
              label="Tên hiển thị (tùy chọn)"
              value={dialog.name}
              onChange={(e) => setDialog({ ...dialog, name: e.target.value })}
              fullWidth
              margin="dense"
            />
            <TextField
              label="Địa chỉ server"
              placeholder="http://192.168.1.10:4533"
              value={dialog.url}
              onChange={(e) => setDialog({ ...dialog, url: e.target.value })}
              fullWidth
              margin="dense"
              required
            />
            <TextField
              label="Tài khoản"
              value={dialog.username}
              onChange={(e) =>
                setDialog({ ...dialog, username: e.target.value })
              }
              fullWidth
              margin="dense"
              required
            />
            <TextField
              label="Mật khẩu"
              type="password"
              placeholder={dialog.id ? 'Để trống = giữ mật khẩu cũ' : ''}
              value={dialog.password}
              onChange={(e) =>
                setDialog({ ...dialog, password: e.target.value })
              }
              fullWidth
              margin="dense"
              required={!dialog.id}
            />
            {testState && testState !== 'busy' && (
              <Typography
                className={classes.hint}
                style={testState === 'ok' ? { color: 'green' } : undefined}
                color={testState === 'ok' ? undefined : 'error'}
              >
                {testState === 'ok' ? 'Kết nối thành công' : testState}
              </Typography>
            )}
          </DialogContent>
        )}
        <DialogActions>
          <Button
            onClick={testConnection}
            disabled={!dialog || !dialog.url || testState === 'busy'}
            startIcon={testState === 'busy' ? <CircularProgress size={16} /> : null}
          >
            Kiểm tra kết nối
          </Button>
          <Button onClick={() => setDialog(null)}>Hủy</Button>
          <Button
            color="primary"
            variant="contained"
            disabled={
              !dialog ||
              !dialog.url ||
              !dialog.username ||
              (!dialog.id && !dialog.password) ||
              busy === 'save'
            }
            onClick={saveServer}
          >
            Lưu
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}

export default RemoteImport
