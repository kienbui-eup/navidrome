import React, { useEffect, useRef, useState } from 'react'
import { useNotify } from 'react-admin'
import {
  Box,
  Button,
  Chip,
  CircularProgress,
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
  Switch,
  TextField,
  Typography,
  Divider,
} from '@material-ui/core'
import { makeStyles } from '@material-ui/core/styles'
import SearchIcon from '@material-ui/icons/Search'
import GetAppIcon from '@material-ui/icons/GetApp'
import PlayArrowIcon from '@material-ui/icons/PlayArrow'
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

// Archive preview URLs are absolute (public). Drive preview URLs are relative
// to our API and need the JWT appended, because <audio> cannot send the
// Authorization header the way httpClient does.
const previewSrc = (hit) => {
  if (!hit.previewUrl.startsWith('/')) return hit.previewUrl
  const token = localStorage.getItem('token')
  return `${hit.previewUrl}&jwt=${encodeURIComponent(token || '')}`
}

const hitKey = (hit) =>
  hit.source === 'archive' ? `${hit.identifier}/${hit.filename}` : hit.fileId

// Format a length that may be seconds ("391.05") or already "mm:ss".
const formatLength = (len) => {
  if (!len) return ''
  const secs = Number(len)
  if (Number.isNaN(secs)) return len
  const m = Math.floor(secs / 60)
  const s = Math.round(secs % 60)
  return `${m}:${String(s).padStart(2, '0')}`
}

const foldSearch = (s) => {
  if (!s) return ''
  return s
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
}

// Convert region names to premium display names with flags
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

// Format counts nicely (e.g. 1.2k, 1.2M)
const formatCount = (num) => {
  if (!num || num <= 0) return ''
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}k`
  return `${num}`
}

// Map formats to hierarchy score for sorting
const getFormatRank = (format) => {
  const f = (format || '').toLowerCase()
  if (f.includes('dsd') || f.includes('dsf') || f.includes('dff')) return 100
  if (f.includes('24bit') || f.includes('24-bit')) return 95
  if (f.includes('flac')) return 80
  if (f.includes('alac') || f.includes('apple lossless')) return 78
  if (f.includes('ape') || f.includes('wavpack')) return 75
  if (f.includes('wav') || f.includes('aiff')) return 70
  if (f.includes('320') || f.includes('vbr')) return 40
  if (f.includes('mp3')) return 35
  if (f.includes('ogg') || f.includes('opus')) return 35
  if (f.includes('aac') || f.includes('m4a')) return 30
  return 20
}

const SongSearch = ({ libraryId, onImported, onJobStarted, classes }) => {
  const localClasses = useLocalStyles()
  const notify = useNotify()
  const [query, setQuery] = useState('')
  const [driveFolder, setDriveFolder] = useState('')
  const [losslessOnly, setLosslessOnly] = useState(true)
  const [result, setResult] = useState(null)
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
  const [sortBy, setSortBy] = useState('relevance') // 'relevance' | 'year' | 'downloads' | 'likes' | 'quality' | 'format'

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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [notify])

  const stopPreview = () => {
    if (audioRef.current) {
      audioRef.current.pause()
    }
    setPlaying(null)
  }

  const togglePreview = (hit) => {
    const key = hitKey(hit)
    if (playing === key) {
      stopPreview()
      return
    }
    const audio = audioRef.current
    audio.pause()
    audio.src = previewSrc(hit)
    setPlaying(key)
    audio.play().catch(() => {
      notify('Không phát được bản nghe thử', 'warning')
      setPlaying(null)
    })
  }

  const search = async () => {
    setBusy('search')
    setResult(null)
    stopPreview()
    try {
      const params = new URLSearchParams({
        q: query,
        lossless: losslessOnly ? 'true' : 'false',
      })
      if (driveFolder.trim()) params.set('drive', driveFolder.trim())
      const { json } = await httpClient(
        `/api/import/search/songs?${params.toString()}`,
      )
      setResult(json || { hits: [] })
      if (!json || !json.hits || json.hits.length === 0) {
        notify('Không tìm thấy bài nào', 'info')
      }
    } catch (e) {
      notify(`Tìm kiếm lỗi: ${errMsg(e)}`, 'warning')
    } finally {
      setBusy(null)
    }
  }

  const importHit = async (hit) => {
    const key = hitKey(hit)
    setBusy(key)
    try {
      const { json } =
        hit.source === 'archive'
          ? await httpClient('/api/import/archive', {
              method: 'POST',
              body: JSON.stringify({
                identifier: hit.identifier,
                filename: hit.filename,
                libraryId,
              }),
            })
          : await httpClient('/api/import/drive/file', {
              method: 'POST',
              body: JSON.stringify({
                id: hit.fileId,
                name: hit.filename,
                libraryId,
              }),
            })
      onImported(json.savedName)
    } catch (e) {
      notify(`Không tải được "${hit.title}": ${errMsg(e)}`, 'warning')
    } finally {
      setBusy(null)
    }
  }

  const importGroup = async (group) => {
    if (!group || !group.hits || group.hits.length === 0) return
    const key = 'group-' + group.key
    setBusy(key)
    try {
      const items = group.hits.map((hit) => {
        if (hit.source === 'archive') {
          return {
            type: 'archive',
            identifier: hit.identifier,
            filename: hit.filename,
          }
        } else {
          return {
            type: 'drive',
            id: hit.fileId,
            name: hit.filename,
          }
        }
      })
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

  const hits = (result && result.hits) || []

  // Dynamic filter values from the loaded dataset
  const uniqueYears = Array.from(
    new Set(hits.map((h) => h.year).filter(Boolean)),
  )
    .sort()
    .reverse()
  const uniqueFormats = Array.from(
    new Set(hits.map((h) => h.format).filter(Boolean)),
  ).sort()

  // 1. Filter local hits first
  const filteredHits = hits.filter((hit) => {
    if (filterSinger.trim()) {
      const s = foldSearch(filterSinger.trim())
      const artist = foldSearch(hit.artist || '')
      if (!artist.includes(s)) return false
    }
    if (filterComposer.trim()) {
      const c = foldSearch(filterComposer.trim())
      const artist = foldSearch(hit.artist || '')
      const title = foldSearch(hit.title || '')
      if (!artist.includes(c) && !title.includes(c)) return false
    }
    if (filterYear !== 'all') {
      if (hit.year !== filterYear) return false
    }
    if (filterFormat !== 'all') {
      if (hit.format !== filterFormat) return false
    }
    if (filterSize !== 'all') {
      const sizeMB = (hit.size || 0) / (1024 * 1024)
      if (filterSize === 'small' && sizeMB >= 10) return false
      if (
        filterSize === 'medium' &&
        (sizeMB < 10 || sizeMB >= 50)
      )
        return false
      if (
        filterSize === 'large' &&
        (sizeMB < 50 || sizeMB >= 100)
      )
        return false
      if (filterSize === 'xlarge' && sizeMB < 100) return false
    }
    return true
  })

  // 2. Sort filtered hits
  const sortedHits = [...filteredHits].sort((a, b) => {
    if (sortBy === 'year') {
      const yA = parseInt(a.year) || 0
      const yB = parseInt(b.year) || 0
      if (yB !== yA) return yB - yA // Newer first
    } else if (sortBy === 'downloads') {
      const dA = a.downloads || 0
      const dB = b.downloads || 0
      if (dB !== dA) return dB - dA // More listens first
    } else if (sortBy === 'likes') {
      const lA = a.likes || 0
      const lB = b.likes || 0
      if (lB !== lA) return lB - lA // More likes first
    } else if (sortBy === 'quality') {
      const qA = a.quality || 0
      const qB = b.quality || 0
      if (qB !== qA) return qB - qA // Higher quality first
    } else if (sortBy === 'format') {
      const rA = getFormatRank(a.format)
      const rB = getFormatRank(b.format)
      if (rB !== rA) return rB - rA // Better format rank first
    }
    return 0 // Keep original/relevance rank from backend
  })

  // 3. Group sorted hits (preserving their sorted order inside groups)
  const groupHits = (hitsList, groupByOption) => {
    const groups = {}
    hitsList.forEach((hit) => {
      let key = ''
      let title = ''
      let subtitle = ''
      let icon = <AlbumIcon color="primary" />

      if (groupByOption === 'album') {
        key = hit.album || 'Không rõ Album'
        title = key
        subtitle = [
          hit.artist,
          hit.year,
          hit.region ? formatRegion(hit.region) : null,
        ].filter(Boolean).join(' • ')
        icon = <AlbumIcon color="primary" />
      } else if (groupByOption === 'artist') {
        key = hit.artist || 'Không rõ Ca sĩ'
        title = key
        subtitle = 'Ca sĩ / Nghệ sĩ trình bày'
        icon = <MusicNoteIcon color="primary" />
      } else if (groupByOption === 'region') {
        key = hit.region || ''
        title = formatRegion(hit.region)
        subtitle = 'Khu vực / Ngôn ngữ phát hành'
        icon = <LanguageIcon color="primary" />
      } else {
        key = 'all'
        title = 'Tất cả bài hát'
        subtitle = `${hitsList.length} bài hát`
        icon = <GraphicEqIcon color="primary" />
      }

      if (!groups[key]) {
        groups[key] = {
          key: key,
          title: title,
          subtitle: subtitle,
          icon: icon,
          hits: [],
        }
      }
      groups[key].hits.push(hit)
    })

    // Sort groups alphabetically by title, but put "unknown/khác" at the very bottom
    return Object.values(groups).sort((a, b) => {
      if (groupByOption === 'none') return 0
      const isUnkA = a.title.includes('Không rõ') || a.title.includes('Không xác định')
      const isUnkB = b.title.includes('Không rõ') || b.title.includes('Không xác định')
      if (isUnkA && !isUnkB) return 1
      if (!isUnkA && isUnkB) return -1
      return a.title.localeCompare(b.title, 'vi')
    })
  }

  const renderFormatChip = (hit) => {
    const f = (hit.format || '').toLowerCase()
    let style = {}
    let label = hit.format
    if (f.includes('dsd')) {
      style = {
        background: 'linear-gradient(45deg, #FFD700, #FFA500)',
        color: '#000',
        fontWeight: 'bold',
      }
    } else if (hit.lossless) {
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
        color={!style.backgroundColor && hit.lossless ? 'primary' : 'default'}
      />
    )
  }

  const renderLikes = (likes) => {
    if (!likes || likes <= 0) return null
    return (
      <Box display="inline-flex" alignItems="center" style={{ color: '#ffb300', fontSize: '0.8rem', gap: 2 }}>
        <StarIcon style={{ fontSize: '0.9rem' }} />
        <span>{likes.toFixed(1)}</span>
      </Box>
    )
  }

  const renderRegion = (region) => {
    if (!region) return null
    return (
      <span style={{ fontSize: '0.8rem', opacity: 0.8 }}>
        🌎 {formatRegion(region)}
      </span>
    )
  }

  const getButtonText = (group) => {
    if (groupBy === 'album') return `Tải cả album (${group.hits.length})`
    if (groupBy === 'artist') return `Tải tất cả bài của ca sĩ (${group.hits.length})`
    if (groupBy === 'region') return `Tải tất cả thuộc khu vực (${group.hits.length})`
    return `Tải toàn bộ nhóm (${group.hits.length})`
  }

  return (
    <Box className={classes.section}>
      <TextField
        className={classes.field}
        label="Tìm nhạc (độ chính xác cao từ Archive.org & Drive)"
        placeholder="Nhập tên album, bài hát, ca sĩ..."
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && search()}
        variant="outlined"
        size="small"
        fullWidth
      />
      <TextField
        className={classes.field}
        style={{ marginTop: 8 }}
        label="Thư mục Google Drive (tùy chọn)"
        placeholder="https://drive.google.com/drive/folders/..."
        value={driveFolder}
        onChange={(e) => setDriveFolder(e.target.value)}
        variant="outlined"
        size="small"
        fullWidth
      />
      <Box className={classes.actions}>
        <Button
          variant="contained"
          color="primary"
          startIcon={
            busy === 'search' ? <CircularProgress size={16} /> : <SearchIcon />
          }
          disabled={busy === 'search'}
          onClick={search}
        >
          Tìm
        </Button>
        <FormControlLabel
          control={
            <Switch
              checked={losslessOnly}
              onChange={(e) => setLosslessOnly(e.target.checked)}
              color="primary"
              size="small"
            />
          }
          label="Chỉ lossless / hi-res"
        />
      </Box>

      {result && result.warnings && result.warnings.length > 0 && (
        <Typography className={classes.hint} color="error">
          {result.warnings.join(' — ')}
        </Typography>
      )}

      {/* Interactive Filters, Grouping & Sorting Panel */}
      {hits.length > 0 && (
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
              Bộ lọc & Sắp xếp ({filteredHits.length} / {hits.length} bài)
            </Typography>
            {(filterSinger ||
              filterComposer ||
              filterYear !== 'all' ||
              filterSize !== 'all' ||
              filterFormat !== 'all' ||
              groupBy !== 'album' ||
              sortBy !== 'relevance') && (
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
                  setSortBy('relevance')
                }}
              >
                Đặt lại mặc định
              </Button>
            )}
          </Box>
          <Grid container spacing={2}>
            {/* Row 1: Advanced Filters */}
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
                label="Tác giả"
                placeholder="Nhập tên tác giả..."
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
                      {f}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>

            {/* Row 2: Premium Grouping & Sorting Controls */}
            <Grid item xs={12}>
              <Divider style={{ margin: '8px 0 16px 0', opacity: 0.5 }} />
            </Grid>
            
            <Grid item xs={12} sm={6}>
              <FormControl variant="outlined" size="small" fullWidth>
                <InputLabel id="group-by-label" style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                  Nhóm danh sách theo
                </InputLabel>
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
                  <MenuItem value="relevance">🔍 Độ phù hợp / Tìm kiếm</MenuItem>
                  <MenuItem value="year">📅 Năm phát hành (Mới nhất)</MenuItem>
                  <MenuItem value="downloads">📥 Lượt tải / Lượt nghe nhiều nhất</MenuItem>
                  <MenuItem value="likes">⭐ Lượt thích / Điểm đánh giá cao nhất</MenuItem>
                  <MenuItem value="quality">🎧 Chất lượng âm thanh tốt nhất</MenuItem>
                  <MenuItem value="format">🎵 Định dạng cao cấp nhất (DSD, FLAC 24bit...)</MenuItem>
                </Select>
              </FormControl>
            </Grid>
          </Grid>
        </Box>
      )}

      {busy === 'search' && !result && (
        <Box display="flex" justifyContent="center" my={4}>
          <CircularProgress />
        </Box>
      )}

      {sortedHits.length > 0 && (
        <>
          {groupBy !== 'none' ? (
            groupHits(sortedHits, groupBy).map((group) => {
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
                      color="primary"
                      size="small"
                      startIcon={
                        busy === 'group-' + group.key ? (
                          <CircularProgress size={16} />
                        ) : (
                          <GetAppIcon />
                        )
                      }
                      disabled={busy === 'group-' + group.key}
                      onClick={() => importGroup(group)}
                    >
                      {getButtonText(group)}
                    </Button>
                  </Box>
                  <List className={localClasses.songList}>
                    {group.hits.map((hit) => {
                      const sKey = hitKey(hit)
                      const meta = [
                        formatLength(hit.length),
                        hit.size ? formatBytes(hit.size) : null,
                        groupBy !== 'album' && hit.album ? `Album: ${hit.album}` : null,
                        groupBy !== 'artist' && hit.artist ? `Artist: ${hit.artist}` : null,
                        hit.year ? `Năm: ${hit.year}` : null,
                      ]
                        .filter(Boolean)
                        .join(' • ')
                      return (
                        <ListItem key={sKey} className={localClasses.songItem} divider>
                          <IconButton
                            aria-label="Nghe thử"
                            onClick={() => togglePreview(hit)}
                          >
                            {playing === sKey ? <StopIcon /> : <PlayArrowIcon />}
                          </IconButton>
                          <ListItemText
                            primary={
                              <Box display="flex" alignItems="center" flexWrap="wrap" gap={1}>
                                <span style={{ fontWeight: 500, marginRight: 8 }}>{hit.title}</span>
                                {renderFormatChip(hit)}
                                {hit.source === 'drive' && (
                                  <Chip size="small" variant="outlined" label="Drive" style={{ height: 20, fontSize: '0.7rem' }} />
                                )}
                              </Box>
                            }
                            secondary={
                              <Box display="flex" alignItems="center" flexWrap="wrap" gap={1.5} mt={0.5} style={{ color: 'rgba(255, 255, 255, 0.6)', fontSize: '0.8rem' }}>
                                <span>{meta}</span>
                                {hit.downloads > 0 && (
                                  <Box display="inline-flex" alignItems="center" gap={0.5}>
                                    <CloudQueueIcon style={{ fontSize: '0.9rem', opacity: 0.7 }} />
                                    <span>{formatCount(hit.downloads)} lượt tải</span>
                                  </Box>
                                )}
                                {renderLikes(hit.likes)}
                                {groupBy !== 'region' && hit.region && renderRegion(hit.region)}
                              </Box>
                            }
                          />
                          <ListItemSecondaryAction>
                            <IconButton
                              edge="end"
                              aria-label="Tải về"
                              disabled={busy === sKey}
                              onClick={() => importHit(hit)}
                            >
                              {busy === sKey ? (
                                <CircularProgress size={20} />
                              ) : (
                                <GetAppIcon />
                              )}
                            </IconButton>
                          </ListItemSecondaryAction>
                        </ListItem>
                      )
                    })}
                  </List>
                </Box>
              )
            })
          ) : (
            <List className={classes.section}>
              {sortedHits.map((hit) => {
                const sKey = hitKey(hit)
                const meta = [
                  hit.album !== hit.title ? `Album: ${hit.album}` : null,
                  hit.artist ? `Artist: ${hit.artist}` : null,
                  hit.year ? `Năm: ${hit.year}` : null,
                  formatLength(hit.length),
                  hit.size ? formatBytes(hit.size) : null,
                ]
                  .filter(Boolean)
                  .join(' • ')
                return (
                  <ListItem key={sKey} className={localClasses.songItem} divider>
                    <IconButton
                      aria-label="Nghe thử"
                      onClick={() => togglePreview(hit)}
                    >
                      {playing === sKey ? <StopIcon /> : <PlayArrowIcon />}
                    </IconButton>
                    <ListItemText
                      primary={
                        <Box display="flex" alignItems="center" flexWrap="wrap" gap={1}>
                          <span style={{ fontWeight: 500, marginRight: 8 }}>{hit.title}</span>
                          {renderFormatChip(hit)}
                          <Chip
                            size="small"
                            variant="outlined"
                            label={hit.source === 'archive' ? 'Archive' : 'Drive'}
                            style={{ height: 20, fontSize: '0.7rem' }}
                          />
                        </Box>
                      }
                      secondary={
                        <Box display="flex" alignItems="center" flexWrap="wrap" gap={1.5} mt={0.5} style={{ color: 'rgba(255, 255, 255, 0.6)', fontSize: '0.8rem' }}>
                          <span>{meta}</span>
                          {hit.downloads > 0 && (
                            <Box display="inline-flex" alignItems="center" gap={0.5}>
                              <CloudQueueIcon style={{ fontSize: '0.9rem', opacity: 0.7 }} />
                              <span>{formatCount(hit.downloads)} lượt tải</span>
                            </Box>
                          )}
                          {renderLikes(hit.likes)}
                          {hit.region && renderRegion(hit.region)}
                        </Box>
                      }
                    />
                    <ListItemSecondaryAction>
                      <IconButton
                        edge="end"
                        aria-label="Tải về"
                        disabled={busy === sKey}
                        onClick={() => importHit(hit)}
                      >
                        {busy === sKey ? (
                          <CircularProgress size={20} />
                        ) : (
                          <GetAppIcon />
                        )}
                      </IconButton>
                    </ListItemSecondaryAction>
                  </ListItem>
                )
              })}
            </List>
          )}
        </>
      )}
    </Box>
  )
}

export default SongSearch
