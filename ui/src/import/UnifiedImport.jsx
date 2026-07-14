import React, { useState, useEffect, useRef, useCallback } from 'react'
import { useNotify } from 'react-admin'
import {
  Box,
  Button,
  ButtonGroup,
  Chip,
  CircularProgress,
  FormControl,
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
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Avatar,
  Card,
  CardContent,
  FormControlLabel,
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
import AddIcon from '@material-ui/icons/Add'
import EditIcon from '@material-ui/icons/Edit'
import DeleteIcon from '@material-ui/icons/Delete'
import ArrowBackIcon from '@material-ui/icons/ArrowBack'
import ChevronRightIcon from '@material-ui/icons/ChevronRight'
import ExpandMoreIcon from '@material-ui/icons/ExpandMore'
import ExpandLessIcon from '@material-ui/icons/ExpandLess'
import HomeIcon from '@material-ui/icons/Home'
import StorageIcon from '@material-ui/icons/Storage'
import FolderIcon from '@material-ui/icons/Folder'
import AccountTreeIcon from '@material-ui/icons/AccountTree'
import FormatListBulletedIcon from '@material-ui/icons/FormatListBulleted'
import CheckIcon from '@material-ui/icons/Check'

import { httpClient } from '../dataProvider'
import { formatBytes } from '../utils'

const useStyles = makeStyles((theme) => ({
  root: {
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(3),
    [theme.breakpoints.down('xs')]: {
      gap: theme.spacing(2),
    },
  },
  sourceContainer: {
    display: 'flex',
    gap: theme.spacing(2),
    flexWrap: 'wrap',
    marginBottom: theme.spacing(1),
    [theme.breakpoints.down('xs')]: {
      gap: theme.spacing(1),
    },
  },
  sourceCard: {
    flex: '1 1 200px',
    cursor: 'pointer',
    borderRadius: theme.shape.borderRadius * 1.5,
    border: `2px solid ${theme.palette.divider}`,
    transition: 'all 0.25s cubic-bezier(0.4, 0, 0.2, 1)',
    backgroundColor: 'rgba(255, 255, 255, 0.02)',
    boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
    '&:hover': {
      borderColor: theme.palette.primary.main,
      transform: 'translateY(-2px)',
      boxShadow: '0 8px 24px rgba(0,0,0,0.15)',
      backgroundColor: 'rgba(255, 255, 255, 0.04)',
    },
  },
  sourceCardSelected: {
    borderColor: theme.palette.primary.main,
    backgroundColor: 'rgba(0, 229, 255, 0.05)',
    boxShadow: `0 0 0 1px ${theme.palette.primary.main}, 0 8px 24px rgba(0, 229, 255, 0.1)`,
  },
  cardTitle: {
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(1),
    fontWeight: 'bold',
  },
  searchPanel: {
    padding: theme.spacing(2),
    borderRadius: theme.shape.borderRadius * 1.2,
    backgroundColor: theme.palette.background.paper,
    border: `1px solid ${theme.palette.divider}`,
    boxShadow: '0 4px 16px rgba(0,0,0,0.15)',
    [theme.breakpoints.down('xs')]: {
      padding: theme.spacing(1.2),
    },
  },
  filterPanel: {
    marginTop: theme.spacing(1.5),
    padding: theme.spacing(2),
    backgroundColor: 'rgba(255, 255, 255, 0.01)',
    borderRadius: theme.shape.borderRadius,
    border: `1px solid ${theme.palette.divider}`,
    [theme.breakpoints.down('xs')]: {
      padding: theme.spacing(1.2),
    },
  },
  sectionTitle: {
    fontWeight: 'bold',
    [theme.breakpoints.down('xs')]: {
      fontSize: '1rem',
    }
  },
  displayModeText: {
    [theme.breakpoints.down('xs')]: {
      display: 'none',
    }
  },
  treeArtistBlock: {
    marginBottom: theme.spacing(1.5),
    backgroundColor: 'rgba(255, 255, 255, 0.015)',
    borderRadius: theme.shape.borderRadius * 1.2,
    border: '1px solid rgba(255, 255, 255, 0.05)',
    overflow: 'hidden',
    transition: 'all 0.2s ease',
    '&:hover': {
      borderColor: 'rgba(197, 168, 128, 0.3)',
      boxShadow: '0 4px 20px rgba(0,0,0,0.2)',
    },
  },
  treeArtistHeader: {
    padding: '12px 16px',
    cursor: 'pointer',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: 'rgba(255, 255, 255, 0.005)',
    '&:hover': {
      backgroundColor: 'rgba(255, 255, 255, 0.03)',
    },
    [theme.breakpoints.down('xs')]: {
      padding: '8px 12px',
    },
  },
  treeArtistName: {
    fontWeight: 'bold',
    color: '#FFF',
    fontSize: '0.95rem',
    [theme.breakpoints.down('xs')]: {
      fontSize: '0.88rem',
    },
  },
  treeAlbumBlock: {
    marginTop: theme.spacing(1),
    marginBottom: theme.spacing(1),
    backgroundColor: 'rgba(255, 255, 255, 0.01)',
    borderRadius: theme.shape.borderRadius,
    border: '1px solid rgba(255, 255, 255, 0.03)',
    overflow: 'hidden',
  },
  treeAlbumHeader: {
    padding: '8px 12px',
    cursor: 'pointer',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    '&:hover': {
      backgroundColor: 'rgba(255, 255, 255, 0.03)',
    },
    [theme.breakpoints.down('xs')]: {
      padding: '6px 10px',
    },
  },
  treeAlbumName: {
    fontWeight: 'bold',
    color: '#DDD',
    fontSize: '0.88rem',
    [theme.breakpoints.down('xs')]: {
      fontSize: '0.82rem',
    },
  },
  breadcrumbContainer: {
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(1),
    flexWrap: 'wrap',
    padding: theme.spacing(1.5, 2),
    borderRadius: theme.shape.borderRadius,
    backgroundColor: 'rgba(255, 255, 255, 0.02)',
    border: `1px solid ${theme.palette.divider}`,
    marginBottom: theme.spacing(2),
    [theme.breakpoints.down('xs')]: {
      padding: theme.spacing(1, 1.5),
      gap: theme.spacing(0.5),
    },
  },
  breadcrumbItem: {
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(0.5),
    cursor: 'pointer',
    fontSize: '0.9rem',
    color: theme.palette.text.secondary,
    '&:hover': {
      color: theme.palette.primary.main,
    },
    [theme.breakpoints.down('xs')]: {
      fontSize: '0.8rem',
    },
  },
  breadcrumbActive: {
    color: theme.palette.text.primary,
    fontWeight: 'bold',
    pointerEvents: 'none',
  },
  gridContainer: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
    gap: theme.spacing(2.5),
    marginTop: theme.spacing(2),
    [theme.breakpoints.down('xs')]: {
      gridTemplateColumns: 'repeat(auto-fill, minmax(135px, 1fr))',
      gap: theme.spacing(1.5),
    },
  },
  gridCard: {
    cursor: 'pointer',
    borderRadius: theme.shape.borderRadius * 1.2,
    border: `1px solid ${theme.palette.divider}`,
    backgroundColor: 'rgba(255, 255, 255, 0.02)',
    transition: 'all 0.2s ease',
    '&:hover': {
      transform: 'translateY(-3px)',
      boxShadow: '0 8px 24px rgba(0,0,0,0.2)',
      borderColor: theme.palette.primary.main,
      backgroundColor: 'rgba(255, 255, 255, 0.04)',
    },
  },
  artistAvatar: {
    width: 64,
    height: 64,
    backgroundColor: theme.palette.primary.dark,
    color: theme.palette.primary.contrastText,
    fontSize: '1.8rem',
    fontWeight: 'bold',
    marginBottom: theme.spacing(1.5),
    [theme.breakpoints.down('xs')]: {
      width: 44,
      height: 48,
      fontSize: '1.3rem',
      marginBottom: theme.spacing(1),
    },
  },
  albumHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: theme.spacing(2, 2.5),
    borderRadius: theme.shape.borderRadius,
    backgroundColor: 'rgba(255,255,255,0.03)',
    borderLeft: `4px solid ${theme.palette.primary.main}`,
    marginBottom: theme.spacing(2),
    [theme.breakpoints.down('xs')]: {
      flexDirection: 'column',
      alignItems: 'stretch',
      gap: theme.spacing(1.5),
      padding: theme.spacing(1.5),
    },
  },
  songItem: {
    borderRadius: theme.shape.borderRadius,
    marginBottom: theme.spacing(0.5),
    border: '1px solid transparent',
    transition: 'all 0.15s ease',
    paddingRight: theme.spacing(1),
    '&:hover': {
      backgroundColor: 'rgba(255, 255, 255, 0.03)',
      borderColor: theme.palette.divider,
    },
    [theme.breakpoints.down('xs')]: {
      paddingLeft: theme.spacing(0.5),
      paddingRight: theme.spacing(0.5),
      paddingTop: '4px',
      paddingBottom: '4px',
    },
  },
  formatChipDSD: {
    background: 'linear-gradient(45deg, #FFD700, #FFA500)',
    color: '#000',
    fontWeight: 'bold',
    height: 20,
  },
  formatChipLossless: {
    backgroundColor: '#00e5ff',
    color: '#000',
    fontWeight: 'bold',
    height: 20,
  },
  metaText: {
    fontSize: '0.8rem',
    color: theme.palette.text.secondary,
  },
  actions: {
    display: 'flex',
    gap: theme.spacing(1),
    alignItems: 'center',
  },
}))

// Constants & Helper parsers
const getFormatRank = (format) => {
  const f = (format || '').toLowerCase()
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

  const dotIdx = name.lastIndexOf('.')
  const ext = dotIdx > 0 ? name.slice(dotIdx + 1).toLowerCase() : 'mp3'
  let rawName = dotIdx > 0 ? name.slice(0, dotIdx) : name

  let year = null
  const yearMatch = rawName.match(/(?:^|\D)(19\d{2}|20\d{2})(?:\D|$)/)
  if (yearMatch) {
    year = parseInt(yearMatch[1], 10)
    rawName = rawName.replace(/[([\s]*\b(19\d{2}|20\d{2})\b[)\]\s]*/g, ' ').trim()
  }

  let album = 'Không rõ Album'
  const bracketMatch = rawName.match(/[([{]([^\])}]+)[\])}]/)
  if (bracketMatch) {
    const candidate = bracketMatch[1].trim()
    if (!['flac', 'dsd', 'dsf', 'wav', 'mp3', 'lossless', '24bit', '192khz'].includes(candidate.toLowerCase())) {
      album = candidate
      rawName = rawName.replace(/[([{][^\])}]+[\])}]/g, ' ').trim()
    }
  }

  let artist = 'Không rõ Ca sĩ'
  let title = rawName

  const splitMatch = rawName.split(/\s*(?:[-_—])\s*/)
  if (splitMatch.length >= 2) {
    artist = splitMatch[0].trim()
    title = splitMatch.slice(1).join(' - ').trim()
  }

  title = title.replace(/\s+/g, ' ').trim() || 'Bài hát không tên'
  artist = artist.replace(/\s+/g, ' ').trim() || 'Không rõ Ca sĩ'

  let sampleRate = 44100
  let bitDepth = 16
  let channelCount = 2
  let bitRate = ext === 'flac' ? 1411 : (ext === 'mp3' ? 320 : 0)

  if (ext === 'dsf' || ext === 'dff') {
    sampleRate = 2822400
    bitDepth = 1
    channelCount = 2
    bitRate = 5644
  } else {
    const lName = name.toLowerCase()
    if (lName.includes('24bit') || lName.includes('24-bit')) {
      bitDepth = 24
    } else if (lName.includes('32bit') || lName.includes('32-bit')) {
      bitDepth = 32
    }
    if (lName.includes('192khz') || lName.includes('192-khz')) {
      sampleRate = 192000
    } else if (lName.includes('96khz') || lName.includes('96-khz')) {
      sampleRate = 96000
    } else if (lName.includes('88khz') || lName.includes('88-khz')) {
      sampleRate = 88200
    } else if (lName.includes('48khz') || lName.includes('48-khz')) {
      sampleRate = 48000
    }
    // Calculate FLAC bitrate based on sample rate & bit depth (approximate)
    if (ext === 'flac') {
      bitRate = Math.round((sampleRate * bitDepth * channelCount * 0.6) / 1000)
    }
  }

  return {
    id,
    title,
    artist,
    album,
    year,
    suffix: ext,
    size: file.size || 0,
    starred: false,
    bitRate,
    sampleRate,
    bitDepth,
    channelCount,
  }
}

const foldSearch = (s) => {
  if (!s) return ''
  return s
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
}

const formatSampleRate = (hz) => {
  if (!hz) return null
  if (hz >= 1000000) {
    return `${(hz / 1000000).toFixed(1)} MHz`
  }
  return `${(hz / 1000).toFixed(1)} kHz`
}

const formatChannels = (ch) => {
  if (!ch) return null
  if (ch === 1) return 'Mono'
  if (ch === 2) return 'Stereo'
  return `${ch} Ch`
}

const formatDuration = (secs) => {
  if (!secs) return ''
  const secsNum = Number(secs)
  if (Number.isNaN(secsNum)) return secs
  const m = Math.floor(secsNum / 60)
  const s = Math.round(secsNum % 60)
  return `${m}:${String(s).padStart(2, '0')}`
}

const UnifiedImport = ({ libraryId, onImported, onJobStarted }) => {
  const classes = useStyles()
  const notify = useNotify()

  // Primary State
  const [source, setSource] = useState('remote_server') // remote_server | google_drive
  const [query, setQuery] = useState('')
  const [driveFolder, setDriveFolder] = useState('')
  const [losslessOnly, setLosslessOnly] = useState(true)

  // Remote Servers list
  const [servers, setServers] = useState([])
  const [serverId, setServerId] = useState('')
  const [serverDialog, setServerDialog] = useState(null)
  const [testState, setTestState] = useState(null)
  const [artistLimit, setArtistLimit] = useState(50)

  // Statuses
  const [busy, setBusy] = useState(null)
  const [warnings, setWarnings] = useState([])
  const [importedIds, setImportedIds] = useState(new Set())

  // Raw Search Results
  const [rawSongs, setRawSongs] = useState([])
  const [rawAlbums, setRawAlbums] = useState([])
  const [rawArtists, setRawArtists] = useState([])

  // Hierarchical Data Tree
  const [artistsTree, setRawArtistsTree] = useState([])
  const [expandedArtists, setExpandedArtists] = useState({})
  const [expandedAlbums, setExpandedAlbums] = useState({})

  const resetTreeExpansion = useCallback(() => {
    setExpandedArtists({})
    setExpandedAlbums({})
  }, [])

  // UI View Levels & Layout Settings
  const [displayMode, setDisplayMode] = useState('hierarchy') // hierarchy | flat
  const [viewPath, setViewPath] = useState([]) // [] (Artists) -> [Artist] -> [Artist, Album]
  const [filterSinger, setFilterSinger] = useState('')
  const [filterComposer, setFilterComposer] = useState('')
  const [filterYear, setFilterYear] = useState('all')
  const [filterFormat, setFilterFormat] = useState('all')
  const [sortBy, setSortBy] = useState('quality') // quality | year | downloads | likes
  const [showFilters, setShowFilters] = useState(false)

  // Audio preview playback
  const [playing, setPlaying] = useState(null)
  const audioRef = useRef(null)

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
    if (audioRef.current) audioRef.current.pause()
    setPlaying(null)
  }

  const togglePreview = (hit) => {
    const isRemote = hit.source === 'remote'
    const key = isRemote ? hit.id : (hit.source === 'archive' ? `${hit.identifier}/${hit.filename}` : hit.fileId)

    if (playing === key) {
      stopPreview()
      return
    }

    const audio = audioRef.current
    audio.pause()

    let src = hit.previewUrl
    if (isRemote) {
      const token = localStorage.getItem('token')
      src = `/api/import/remote/preview?server=${encodeURIComponent(serverId)}&id=${encodeURIComponent(hit.id)}&jwt=${encodeURIComponent(token || '')}`
    } else if (src && src.startsWith('/')) {
      const token = localStorage.getItem('token')
      src = `${src}&jwt=${encodeURIComponent(token || '')}`
    }

    audio.src = src
    setPlaying(key)
    audio.play().catch(() => {
      notify('Không phát được bản nghe thử', 'warning')
      setPlaying(null)
    })
  }

  const handleSourceSelectChange = (e) => {
    const val = e.target.value
    if (val === 'google_drive') {
      setSource('google_drive')
    } else if (val.startsWith('public_search')) {
      setSource(val)
    } else if (val === 'internet_archive') {
      setSource('internet_archive')
    } else if (val.startsWith('subsonic_')) {
      const id = val.replace('subsonic_', '')
      setSource('remote_server')
      setServerId(id)
    }
  }

  const handleQueryChange = (val) => {
    if (/drive\.google\.com\/drive\/folders\/|drive\.google\.com\/open\?id=|drive\.google\.com\/folderview\?id=/i.test(val)) {
      setSource('google_drive')
      setDriveFolder(val)
      setQuery('')
      notify('Tự động nhận diện liên kết Google Drive!', 'info')
    } else if (/^(https?:\/\/)?(www\.)?(youtube\.com|youtu\.be|zingmp3\.vn|nhaccuatui\.com)/i.test(val)) {
      setQuery(val)
      setSource('public_search')
      notify('Tự động nhận diện liên kết nhạc trực tiếp! Nhấn "Tìm kiếm" để nạp bài.', 'info')
    } else {
      setQuery(val)
    }
  }

  // Load Remote Servers
  const loadServers = useCallback(() => {
    httpClient('/api/import/remote/servers')
      .then(({ json }) => {
        const list = json || []
        setServers(list)
        if (list.length > 0 && !serverId) {
          setServerId(list[0].id)
        }
      })
      .catch(() => {})
  }, [serverId])

  useEffect(() => {
    if (source === 'remote_server') {
      loadServers()
    }
  }, [source, loadServers])

  // Construct hierarchy (Artist -> Album -> Songs)
  const buildHierarchy = useCallback((songsList, albumsList = [], artistsList = []) => {
    resetTreeExpansion()
    const tree = {}

    // Add base artists from artistList
    artistsList.forEach((art) => {
      tree[art.name] = {
        id: art.id,
        name: art.name,
        albumCount: art.albumCount || 0,
        songCount: 0,
        albums: {},
        isBaseArtist: true,
        lazy: true,
      }
    })

    // Add base albums from albumList
    albumsList.forEach((alb) => {
      const artName = alb.artist || 'Không rõ Ca sĩ'
      if (!tree[artName]) {
        tree[artName] = { id: artName, name: artName, albumCount: 0, songCount: 0, albums: {} }
      }
      tree[artName].albums[alb.name] = {
        id: alb.id,
        name: alb.name,
        year: alb.year || null,
        songCount: alb.songCount || 0,
        songs: [],
      }
    })

    // Parse and group songs
    songsList.forEach((song) => {
      const artName = song.artist || 'Không rõ Ca sĩ'
      const albName = song.album || 'Không rõ Album'

      if (!tree[artName]) {
        tree[artName] = { id: artName, name: artName, albumCount: 0, songCount: 0, albums: {} }
      }

      if (!tree[artName].albums[albName]) {
        tree[artName].albums[albName] = {
          id: albName,
          name: albName,
          year: song.year || null,
          songCount: 0,
          songs: [],
        }
      }

      // Format mapping
      const mappedSong = {
        id: song.id || (song.source === 'archive' ? `${song.identifier}/${song.filename}` : song.fileId),
        title: song.title,
        artist: song.artist,
        album: song.album,
        year: song.year,
        size: song.size,
        length: song.length || song.duration,
        format: song.format || song.suffix,
        bitRate: song.bitRate,
        downloads: song.downloads || song.playCount || 0,
        likes: song.likes || (song.starred ? 5 : 0),
        previewUrl: song.previewUrl,
        source: song.source,
        payload: song,
      }

      tree[artName].albums[albName].songs.push(mappedSong)
    })

    // Clean up empty entities and convert objects to sorted arrays
    const finalTree = Object.values(tree).map((art) => {
      const albumsArr = Object.values(art.albums).map((alb) => {
        return {
          ...alb,
          songCount: alb.songs.length || alb.songCount || 0,
        }
      }).filter((alb) => {
        // Lọc bỏ album không có bài hát nào
        if (alb.lazy) {
          return alb.songCount === undefined || alb.songCount > 0
        }
        return alb.songs.length > 0 || alb.songCount > 0
      }).sort((a, b) => (b.year || 0) - (a.year || 0))

      return {
        ...art,
        albums: albumsArr,
        albumCount: albumsArr.length,
        songCount: albumsArr.reduce((sum, current) => sum + current.songCount, 0),
      }
    }).filter((art) => {
      // Lọc bỏ ca sĩ không có bài hát/album nào
      if (art.lazy) {
        return art.albumCount === undefined || art.albumCount > 0
      }
      return art.songCount > 0 || art.albums.length > 0
    }).sort((a, b) => {
      if (a.name.includes('Không rõ')) return 1
      if (b.name.includes('Không rõ')) return -1
      return a.name.localeCompare(b.name, 'vi')
    })

    setRawArtistsTree(finalTree)
  }, [resetTreeExpansion])

  // Auto-search when serverId is selected or changed
  useEffect(() => {
    if (source === 'remote_server' && serverId) {
      const autoSearch = async () => {
        setBusy('search')
        setRawSongs([])
        setViewPath([])
        try {
          const params = new URLSearchParams({ server: serverId, q: '', limit: String(artistLimit) })
          const { json } = await httpClient(`/api/import/remote/search?${params.toString()}`)
          const songsList = json.songs || []
          const albumsList = json.albums || []
          const artistsList = json.artists || []

          setRawSongs(songsList)
          setRawAlbums(albumsList)
          setRawArtists(artistsList)

          buildHierarchy(songsList, albumsList, artistsList)
        } catch (e) {
          // Ignore auto-search error on initial load
        } finally {
          setBusy(null)
        }
      }
      autoSearch()
    }
  }, [source, serverId, artistLimit, buildHierarchy])

  // Reset view & data on source change
  useEffect(() => {
    if (!source.startsWith('public_search') && source !== 'internet_archive') {
      setQuery('')
    }
    setRawSongs([])
    setRawAlbums([])
    setRawArtists([])
    setRawArtistsTree([])
    setViewPath([])
    resetTreeExpansion()
    setWarnings([])
    stopPreview()
  }, [source, resetTreeExpansion])

  // Auto-search for online search sources when selected
  useEffect(() => {
    if (source.startsWith('public_search')) {
      const defaultQuery = 'nhạc mới hot'
      setQuery(defaultQuery)
      
      const triggerAutoSearch = async () => {
        setBusy('search')
        setRawSongs([])
        setViewPath([])
        setWarnings([])
        try {
          const provider = source === 'public_search_youtube'
            ? 'youtube'
            : source === 'public_search_zing'
            ? 'zing'
            : source === 'public_search_deezer'
            ? 'deezer'
            : 'all'

          const params = new URLSearchParams({
            q: defaultQuery,
            lossless: losslessOnly ? 'true' : 'false',
            provider: provider,
          })
          const { json } = await httpClient(`/api/import/search/songs?${params.toString()}`)
          const hits = json.hits || []
          setRawSongs(hits)
          setWarnings(json.warnings || [])
          buildHierarchy(hits)
        } catch (e) {
          // Ignore
        } finally {
          setBusy(null)
        }
      }
      triggerAutoSearch()
    } else if (source === 'internet_archive') {
      const defaultQuery = 'vietnamese music'
      setQuery(defaultQuery)
      
      const triggerAutoSearch = async () => {
        setBusy('search')
        setRawSongs([])
        setViewPath([])
        try {
          const { json } = await httpClient(`/api/import/archive/search?q=${encodeURIComponent(defaultQuery)}&rows=40`)
          const items = json || []
          const archiveArtists = {}
          items.forEach((item) => {
            const creator = item.creator || 'Không rõ Nghệ sĩ'
            if (!archiveArtists[creator]) {
              archiveArtists[creator] = {
                id: creator,
                name: creator,
                albumCount: 0,
                songCount: 0,
                albums: {},
              }
            }
            const albumTitle = item.title || 'Không rõ Album'
            if (!archiveArtists[creator].albums[albumTitle]) {
              archiveArtists[creator].albums[albumTitle] = {
                id: `${creator}_${albumTitle}`,
                title: albumTitle,
                artistName: creator,
                year: item.year ? parseInt(item.year, 10) : undefined,
                songs: [],
                songCount: 0,
              }
              archiveArtists[creator].albumCount++
            }
            const songId = item.identifier
            archiveArtists[creator].albums[albumTitle].songs.push({
              id: songId,
              title: item.title || 'Không rõ bài hát',
              artist: creator,
              album: albumTitle,
              source: 'archive',
              payload: { identifier: songId, filename: item.filename || '' },
            })
            archiveArtists[creator].albums[albumTitle].songCount++
            archiveArtists[creator].songCount++
          })

          const finalTree = Object.values(archiveArtists).map((art) => ({
            ...art,
            albums: Object.values(art.albums),
          }))
          resetTreeExpansion()
          setRawArtistsTree(finalTree)
        } catch (e) {
          // Ignore
        } finally {
          setBusy(null)
        }
      }
      triggerAutoSearch()
    }
  }, [source, losslessOnly, buildHierarchy])

  // Execute unified search
  const handleSearch = async () => {
    if (source.startsWith('public_search')) {
      if (!query.trim() && !driveFolder.trim()) {
        notify('Vui lòng nhập từ khóa hoặc thư mục Google Drive', 'warning')
        return
      }
      setBusy('search')
      setRawSongs([])
      setViewPath([])
      setWarnings([])
      try {
        const provider = source === 'public_search_youtube'
          ? 'youtube'
          : source === 'public_search_zing'
          ? 'zing'
          : source === 'public_search_deezer'
          ? 'deezer'
          : 'all'

        const params = new URLSearchParams({
          q: query,
          lossless: losslessOnly ? 'true' : 'false',
          provider: provider,
        })
        if (driveFolder.trim()) params.set('drive', driveFolder.trim())
        const { json } = await httpClient(`/api/import/search/songs?${params.toString()}`)
        const hits = json.hits || []
        setRawSongs(hits)
        setWarnings(json.warnings || [])
        buildHierarchy(hits)
        if (hits.length === 0) notify('Không tìm thấy bài hát nào', 'info')
      } catch (e) {
        notify(`Tìm kiếm lỗi: ${e.message || 'Lỗi kết nối API'}`, 'error')
      } finally {
        setBusy(null)
      }
    } else if (source === 'internet_archive') {
      if (!query.trim()) {
        notify('Vui lòng nhập từ khóa tìm kiếm', 'warning')
        return
      }
      setBusy('search')
      setRawSongs([])
      setViewPath([])
      try {
        const { json } = await httpClient(`/api/import/archive/search?q=${encodeURIComponent(query)}&rows=40`)
        const items = json || []

        // Group Archive Items as Albums, creator as Artist
        const archiveArtists = {}
        items.forEach((item) => {
          const creator = item.creator || 'Không rõ Nghệ sĩ'
          if (!archiveArtists[creator]) {
            archiveArtists[creator] = {
              id: creator,
              name: creator,
              albumCount: 0,
              songCount: 0,
              albums: {},
            }
          }
          archiveArtists[creator].albums[item.identifier] = {
            id: item.identifier,
            name: item.title || item.identifier,
            year: item.year || null,
            songs: [],
            songCount: 0,
            lazy: true, // indicates files need to be fetched
          }
          archiveArtists[creator].albumCount++
        })

        const tree = Object.values(archiveArtists).sort((a, b) => a.name.localeCompare(b.name, 'vi'))
        resetTreeExpansion()
        setRawArtistsTree(tree)
        if (items.length === 0) notify('Không tìm thấy nội dung nào', 'info')
      } catch (e) {
        notify(`Không tìm được Archive: ${e.message}`, 'error')
      } finally {
        setBusy(null)
      }
    } else if (source === 'remote_server') {
      if (!serverId) {
        notify('Vui lòng cấu hình hoặc chọn một server nguồn', 'warning')
        return
      }
      setBusy('search')
      setRawSongs([])
      setViewPath([])
      try {
        const params = new URLSearchParams({ server: serverId, q: query, limit: String(artistLimit) })
        const { json } = await httpClient(`/api/import/remote/search?${params.toString()}`)
        const songsList = json.songs || []
        const albumsList = json.albums || []
        const artistsList = json.artists || []

        setRawSongs(songsList)
        setRawAlbums(albumsList)
        setRawArtists(artistsList)

        buildHierarchy(songsList, albumsList, artistsList)
        if (songsList.length === 0 && albumsList.length === 0 && artistsList.length === 0) {
          notify('Không tìm thấy kết quả nào trên server nguồn', 'info')
        }
      } catch (e) {
        notify(`Tìm kiếm server lỗi: ${e.message}`, 'error')
      } finally {
        setBusy(null)
      }
    } else if (source === 'google_drive') {
      if (!driveFolder.trim()) {
        notify('Vui lòng nhập link hoặc ID thư mục Google Drive', 'warning')
        return
      }
      setBusy('search')
      setRawSongs([])
      setViewPath([])
      try {
        const { json } = await httpClient('/api/import/drive/list', {
          method: 'POST',
          body: JSON.stringify({ url: driveFolder }),
        })
        const files = json || []

        // Parse drive files and group
        const parsedSongs = files.map((f) => {
          const songInfo = parseDriveFilename(f)
          return {
            ...songInfo,
            source: 'drive',
            fileId: f.id,
            filename: f.name,
            previewUrl: `/api/import/preview?source=drive&id=${f.id}&format=${encodeURIComponent(songInfo.suffix || '')}`,
          }
        })

        setRawSongs(parsedSongs)
        buildHierarchy(parsedSongs)
        if (files.length === 0) notify('Không tìm thấy file nhạc nào trong thư mục', 'info')
      } catch (e) {
        notify(`Không đọc được Drive: ${e.message}`, 'error')
      } finally {
        setBusy(null)
      }
    }
  }

  // Handle Lazy loading for sub-items
  const loadArchiveAlbumSongs = async (artistName, album) => {
    setBusy(`load-${album.id}`)
    try {
      const { json } = await httpClient(`/api/import/archive/files?id=${encodeURIComponent(album.id)}`)
      const files = json || []

      // Map files to UnifiedSong
      const mappedSongs = files.map((f) => ({
        id: `${album.id}/${f.name}`,
        title: f.title || f.name,
        artist: artistName,
        album: album.name,
        size: f.size || 0,
        format: f.format,
        source: 'archive',
        previewUrl: `https://archive.org/download/${encodeURIComponent(album.id)}/${encodeURIComponent(f.name)}`,
        payload: {
          type: 'archive',
          identifier: album.id,
          filename: f.name,
        },
      }))

      // Update tree state
      setRawArtistsTree((prevTree) =>
        prevTree.map((art) => {
          if (art.name !== artistName) return art
          const nextAlbums = art.albums.map((alb) => {
            if (alb.id !== album.id) return alb
            return {
              ...alb,
              songs: mappedSongs,
              songCount: mappedSongs.length,
              lazy: false,
            }
          }).filter((alb) => {
            if (alb.lazy) return alb.songCount > 0
            return alb.songs.length > 0 || alb.songCount > 0
          })
          return {
            ...art,
            albums: nextAlbums,
            albumCount: nextAlbums.length,
            songCount: nextAlbums.reduce((sum, current) => sum + current.songCount, 0),
          }
        }).filter((art) => {
          if (art.lazy) return art.albumCount > 0
          return art.songCount > 0 || art.albums.length > 0
        })
      )

      // Sync active viewPath if needed
      setViewPath((path) => {
        if (path.length === 2 && path[1].id === album.id) {
          return [
            path[0],
            {
              ...path[1],
              songs: mappedSongs,
              songCount: mappedSongs.length,
              lazy: false,
            },
          ]
        }
        return path
      })
    } catch (e) {
      notify(`Không lấy được danh sách file: ${e.message}`, 'error')
    } finally {
      setBusy(null)
    }
  }

  const loadRemoteAlbumSongs = async (artistName, album) => {
    setBusy(`load-${album.id}`)
    try {
      const params = new URLSearchParams({ server: serverId, id: album.id })
      const { json } = await httpClient(`/api/import/remote/album?${params.toString()}`)
      const remoteSongs = json || []

      const mappedSongs = remoteSongs.map((s) => ({
        id: s.id,
        title: s.title,
        artist: s.artist,
        album: s.album,
        year: s.year,
        size: s.size,
        length: formatDuration(s.duration),
        format: s.suffix,
        bitRate: s.bitRate,
        sampleRate: s.sampleRate,
        bitDepth: s.bitDepth,
        channelCount: s.channelCount,
        source: 'remote',
        previewUrl: `/api/import/remote/preview?server=${encodeURIComponent(serverId)}&id=${encodeURIComponent(s.id)}&format=${encodeURIComponent(s.suffix || '')}`,
        payload: {
          type: 'remote',
          serverId,
          id: s.id,
          name: s.title,
        },
      }))

      setRawArtistsTree((prevTree) =>
        prevTree.map((art) => {
          if (art.name !== artistName) return art
          const nextAlbums = art.albums.map((alb) => {
            if (alb.id !== album.id) return alb
            return {
              ...alb,
              songs: mappedSongs,
              songCount: mappedSongs.length,
              lazy: false,
            }
          }).filter((alb) => {
            if (alb.lazy) return alb.songCount > 0
            return alb.songs.length > 0 || alb.songCount > 0
          })
          return {
            ...art,
            albums: nextAlbums,
            albumCount: nextAlbums.length,
            songCount: nextAlbums.reduce((sum, current) => sum + current.songCount, 0),
          }
        }).filter((art) => {
          if (art.lazy) return art.albumCount > 0
          return art.songCount > 0 || art.albums.length > 0
        })
      )

      setViewPath((path) => {
        if (path.length === 2 && path[1].id === album.id) {
          return [
            path[0],
            {
              ...path[1],
              songs: mappedSongs,
              songCount: mappedSongs.length,
              lazy: false,
            },
          ]
        }
        return path
      })
    } catch (e) {
      notify(`Không lấy được bài hát của album: ${e.message}`, 'error')
    } finally {
      setBusy(null)
    }
  }

  const loadRemoteArtistAlbums = async (artist) => {
    setBusy(`load-${artist.id}`)
    try {
      const params = new URLSearchParams({ server: serverId, id: artist.id })
      const { json } = await httpClient(`/api/import/remote/artist?${params.toString()}`)
      const remoteAlbums = json || []

      const mappedAlbums = remoteAlbums.map((alb) => ({
        id: alb.id,
        name: alb.name,
        artist: artist.name,
        year: alb.year,
        songCount: alb.songCount || 0,
        songs: [],
        lazy: true,
      })).filter((alb) => alb.songCount > 0)

      setRawArtistsTree((prevTree) =>
        prevTree.map((art) => {
          if (art.name !== artist.name) return art
          return {
            ...art,
            albums: mappedAlbums,
            albumCount: mappedAlbums.length,
            lazy: false,
          }
        }).filter((art) => {
          if (art.lazy) return art.albumCount > 0
          return art.songCount > 0 || art.albums.length > 0
        })
      )

      setViewPath((path) => {
        if (path.length === 1 && path[0].id === artist.id) {
          return [
            {
              ...path[0],
              albums: mappedAlbums,
              albumCount: mappedAlbums.length,
              lazy: false,
            },
          ]
        }
        return path
      })
    } catch (e) {
      notify(`Không lấy được album của ca sĩ: ${e.message}`, 'error')
    } finally {
      setBusy(null)
    }
  }

  const toggleArtistExpand = useCallback((artist) => {
    const isExpanded = !!expandedArtists[artist.id]
    setExpandedArtists((prev) => ({
      ...prev,
      [artist.id]: !isExpanded,
    }))
    if (!isExpanded && artist.lazy && source === 'remote_server') {
      loadRemoteArtistAlbums(artist)
    }
  }, [expandedArtists, source, loadRemoteArtistAlbums])

  const toggleAlbumExpand = useCallback((artist, album) => {
    const isExpanded = !!expandedAlbums[album.id]
    setExpandedAlbums((prev) => ({
      ...prev,
      [album.id]: !isExpanded,
    }))
    if (!isExpanded && album.lazy) {
      if (source === 'internet_archive') {
        loadArchiveAlbumSongs(artist.name, album)
      } else if (source === 'remote_server') {
        loadRemoteAlbumSongs(artist.name, album)
      }
    }
  }, [expandedAlbums, source, loadArchiveAlbumSongs, loadRemoteAlbumSongs])

  // Handle drill down clicks
  const selectArtist = (artist) => {
    setViewPath([artist])
    if (artist.lazy && source === 'remote_server') {
      loadRemoteArtistAlbums(artist)
    }
    stopPreview()
  }

  const selectAlbum = (album) => {
    const artist = viewPath[0]
    setViewPath([artist, album])

    if (album.lazy) {
      if (source === 'internet_archive') {
        loadArchiveAlbumSongs(artist.name, album)
      } else if (source === 'remote_server') {
        loadRemoteAlbumSongs(artist.name, album)
      }
    }
    stopPreview()
  }

  const goHome = () => {
    setViewPath([])
    stopPreview()
  }

  // Import Action Handlers
  const handleImportSingle = async (song) => {
    const key = song.id
    setBusy(`import-${key}`)
    try {
      let res
      if (song.source === 'archive') {
        const payload = song.payload || { identifier: song.payload.identifier, filename: song.payload.filename }
        res = await httpClient('/api/import/archive', {
          method: 'POST',
          body: JSON.stringify({
            identifier: payload.identifier || song.payload.identifier,
            filename: payload.filename || song.payload.filename,
            libraryId,
          }),
        })
      } else if (song.source === 'drive') {
        res = await httpClient('/api/import/drive/file', {
          method: 'POST',
          body: JSON.stringify({
            id: song.id,
            name: song.title,
            libraryId,
          }),
        })
      } else if (song.source === 'remote') {
        res = await httpClient('/api/import/remote/import', {
          method: 'POST',
          body: JSON.stringify({
            serverId,
            type: 'song',
            id: song.id,
            libraryId,
          }),
        })
      } else if (song.source === 'youtube' || song.source === 'deezer' || song.source === 'zing') {
        res = await httpClient('/api/import/job', {
          method: 'POST',
          body: JSON.stringify({
            items: [{
              type: song.source,
              id: song.id,
              name: song.artist ? `${song.artist} - ${song.title}` : song.title,
            }],
            libraryId,
          }),
        })
      }
      notify(`Đã xếp hàng tải "${song.title}"`, 'info')
      setImportedIds(prev => {
        const next = new Set(prev)
        next.add(song.id)
        return next
      })
      if (res && res.json && res.json.savedName) {
        onImported(res.json.savedName)
      }
    } catch (e) {
      notify(`Không tải được "${song.title}": ${e.message}`, 'warning')
    } finally {
      setBusy(null)
    }
  }

  const handleImportBatch = async (title, songsList) => {
    if (!songsList || songsList.length === 0) return
    const key = `batch-${title}`
    setBusy(key)
    try {
      const items = songsList.map((song) => {
        if (song.source === 'archive') {
          const payload = song.payload || {}
          return {
            type: 'archive',
            identifier: payload.identifier,
            filename: payload.filename,
          }
        } else if (song.source === 'drive') {
          return {
            type: 'drive',
            id: song.id,
            name: song.title,
          }
        } else if (song.source === 'youtube' || song.source === 'deezer' || song.source === 'zing') {
          return {
            type: song.source,
            id: song.id,
            name: song.artist ? `${song.artist} - ${song.title}` : song.title,
          }
        } else {
          return {
            type: 'remote',
            serverId,
            id: song.id,
            name: song.title,
          }
        }
      })

      const { json } = await httpClient('/api/import/job', {
        method: 'POST',
        body: JSON.stringify({ items, libraryId }),
      })

      if (onJobStarted) {
        onJobStarted(json.jobId, items.length)
        notify(`Bắt đầu tải nhóm "${title}" (${items.length} bài)...`, 'info')
      } else {
        notify(`Đã thêm ${items.length} bài vào hàng đợi tải.`, 'info')
      }
    } catch (e) {
      notify(`Không khởi động được batch job: ${e.message}`, 'warning')
    } finally {
      setBusy(null)
    }
  }

  // Filters & Sorters Engine (For Local songs inside Flat mode / Level 3 view)
  const filterAndSortSongs = (songsList) => {
    return songsList.filter((song) => {
      if (filterSinger.trim()) {
        const queryFolded = foldSearch(filterSinger)
        if (!foldSearch(song.artist || '').includes(queryFolded)) return false
      }
      if (filterComposer.trim()) {
        const queryFolded = foldSearch(filterComposer)
        const inArtist = foldSearch(song.artist || '').includes(queryFolded)
        const inTitle = foldSearch(song.title || '').includes(queryFolded)
        if (!inArtist && !inTitle) return false
      }
      if (filterYear !== 'all') {
        if (String(song.year) !== String(filterYear)) return false
      }
      if (filterFormat !== 'all') {
        const formatString = (song.format || '').toLowerCase()
        if (!formatString.includes(filterFormat.toLowerCase())) return false
      }
      return true
    }).sort((a, b) => {
      if (sortBy === 'quality') {
        return getFormatRank(b.format) - getFormatRank(a.format)
      } else if (sortBy === 'year') {
        return (b.year || 0) - (a.year || 0)
      } else if (sortBy === 'downloads') {
        return (b.downloads || 0) - (a.downloads || 0)
      } else if (sortBy === 'likes') {
        return (b.likes || 0) - (a.likes || 0)
      }
      return 0
    })
  }

  // Remote Servers Form Dialog Handlers
  const handleOpenServerDialog = (server) => {
    setTestState(null)
    setServerDialog(server ? { ...server, password: '' } : { id: '', name: '', url: '', username: '', password: '' })
  }

  const handleTestConnection = async () => {
    setTestState('busy')
    try {
      await httpClient('/api/import/remote/servers/test', {
        method: 'POST',
        body: JSON.stringify(serverDialog),
      })
      setTestState('ok')
    } catch (e) {
      setTestState(e.message || 'Lỗi kết nối')
    }
  }

  const handleSaveServer = async () => {
    setBusy('save-server')
    try {
      const endpoint = serverDialog.id ? `/api/import/remote/servers/${serverDialog.id}` : '/api/import/remote/servers'
      const method = serverDialog.id ? 'PUT' : 'POST'
      await httpClient(endpoint, {
        method,
        body: JSON.stringify(serverDialog),
      })
      setServerDialog(null)
      loadServers()
      notify('Đã lưu server nguồn thành công', 'info')
    } catch (e) {
      notify(`Không lưu được: ${e.message}`, 'warning')
    } finally {
      setBusy(null)
    }
  }

  const handleDeleteServer = async () => {
    if (!serverId) return
    const s = servers.find((serv) => serv.id === serverId)
    if (!s || !window.confirm(`Xóa cấu hình server nguồn "${s.name}"?`)) return
    try {
      await httpClient(`/api/import/remote/servers/${s.id}`, { method: 'DELETE' })
      loadServers()
      setServerId('')
      notify('Đã xóa server nguồn', 'info')
    } catch (e) {
      notify(`Không xóa được: ${e.message}`, 'warning')
    }
  }

  // Dynamic filter values
  const uniqueYears = Array.from(new Set(rawSongs.map((s) => s.year).filter(Boolean))).sort().reverse()
  const uniqueFormats = Array.from(new Set(rawSongs.map((s) => s.format || s.suffix).filter(Boolean))).sort()

  const activeFiltersCount = 
    (filterSinger.trim() ? 1 : 0) + 
    (filterComposer.trim() ? 1 : 0) + 
    (filterYear !== 'all' ? 1 : 0) + 
    (filterFormat !== 'all' ? 1 : 0) + 
    (sortBy !== 'quality' ? 1 : 0)

  const renderedArtists = artistsTree

  // Render Premium Quality Chips
  const renderFormatChip = (song) => {
    const f = (song.format || '').toLowerCase()
    let styleClass = ''
    let label = (song.format || 'MP3').toUpperCase()

    const isLossless = getFormatRank(song.format) >= 70 || (song.bitRate && song.bitRate > 320)

    if (f.includes('dsd') || f.includes('dsf') || f.includes('dff')) {
      styleClass = classes.formatChipDSD
    } else if (isLossless) {
      styleClass = classes.formatChipLossless
    }

    return (
      <Chip
        size="small"
        label={label}
        className={styleClass}
        color={!styleClass && isLossless ? 'primary' : 'default'}
      />
    )
  }
  const renderAudioSpecs = (song) => {
    return (
      <Box display="flex" alignItems="center" gap={0.5} flexWrap="wrap">
        {renderFormatChip(song)}
        {song.bitRate > 0 && (
          <Chip
            size="small"
            variant="outlined"
            label={`${song.bitRate} kbps`}
            style={{ height: 18, fontSize: '0.7rem' }}
          />
        )}
        {song.bitDepth > 0 && (
          <Chip
            size="small"
            variant="outlined"
            label={song.bitDepth === 1 ? '1-bit DSD' : `${song.bitDepth}-bit`}
            style={{ height: 18, fontSize: '0.7rem', opacity: 0.8 }}
          />
        )}
        {song.sampleRate > 0 && (
          <Chip
            size="small"
            variant="outlined"
            label={formatSampleRate(song.sampleRate)}
            style={{ height: 18, fontSize: '0.7rem', opacity: 0.8 }}
          />
        )}
        {song.channelCount > 0 && (
          <Chip
            size="small"
            variant="outlined"
            label={formatChannels(song.channelCount)}
            style={{ height: 18, fontSize: '0.7rem', opacity: 0.8 }}
          />
        )}
      </Box>
    )
  }


  // JSX rendering sub-components
  const renderBreadcrumbs = () => {
    if (viewPath.length === 0) return null
    return (
      <Box className={classes.breadcrumbContainer}>
        <Box className={classes.breadcrumbItem} onClick={goHome}>
          <HomeIcon style={{ fontSize: '1.1rem' }} />
          <span>Tất cả ca sĩ</span>
        </Box>
        <ChevronRightIcon style={{ fontSize: '1rem', opacity: 0.5 }} />
        <Box
          className={`${classes.breadcrumbItem} ${viewPath.length === 1 ? classes.breadcrumbActive : ''}`}
          onClick={() => setViewPath([viewPath[0]])}
        >
          <MusicNoteIcon style={{ fontSize: '1.1rem' }} />
          <span>{viewPath[0].name}</span>
        </Box>
        {viewPath.length > 1 && (
          <>
            <ChevronRightIcon style={{ fontSize: '1rem', opacity: 0.5 }} />
            <Box className={`${classes.breadcrumbItem} ${classes.breadcrumbActive}`}>
              <AlbumIcon style={{ fontSize: '1.1rem' }} />
              <span>{viewPath[1].name}</span>
            </Box>
          </>
        )}
      </Box>
    )
  }

  return (
    <Box className={classes.root}>
      {/* SEARCH CONTROL BAR */}
      <Box className={classes.searchPanel}>
        <Grid container spacing={1} alignItems="center">
          <Grid item xs={source === 'remote_server' ? 7 : 12} sm={source === 'remote_server' ? 8 : 12} md={4}>
            <Box display="flex" gap={1} alignItems="center">
              <FormControl variant="outlined" size="small" fullWidth>
                <InputLabel>Chọn nguồn nhập</InputLabel>
                <Select
                  value={
                    source === 'google_drive'
                      ? 'google_drive'
                      : source.startsWith('public_search')
                      ? source
                      : source === 'internet_archive'
                      ? 'internet_archive'
                      : serverId
                      ? `subsonic_${serverId}`
                      : ''
                  }
                  label="Chọn nguồn nhập"
                  onChange={handleSourceSelectChange}
                >
                  {servers.map((s) => (
                    <MenuItem key={s.id} value={`subsonic_${s.id}`}>
                      🔗 Subsonic: {s.name}
                    </MenuItem>
                  ))}
                  <MenuItem value="google_drive">
                    📁 Google Drive Folder
                  </MenuItem>
                  <MenuItem value="public_search">
                    🌐 Tìm tất cả nguồn Online (Gộp)
                  </MenuItem>
                  <MenuItem value="public_search_youtube">
                    📺 Tìm nhạc YouTube Music (Opus)
                  </MenuItem>
                  <MenuItem value="public_search_zing">
                    💚 Tìm nhạc Zing MP3 (320kbps)
                  </MenuItem>
                  <MenuItem value="public_search_deezer">
                    💜 Tìm nhạc Deezer Lossless (FLAC)
                  </MenuItem>
                  <MenuItem value="internet_archive">
                    🏛️ Thư viện Internet Archive
                  </MenuItem>
                </Select>
              </FormControl>
              {source === 'remote_server' && (
                <Box display="flex" gap={0.5}>
                  <IconButton size="small" onClick={() => handleOpenServerDialog(null)} title="Thêm server">
                    <AddIcon color="primary" />
                  </IconButton>
                  {serverId && (
                    <>
                      <IconButton
                        size="small"
                        onClick={() => handleOpenServerDialog(servers.find((s) => s.id === serverId))}
                        title="Sửa server"
                      >
                        <EditIcon />
                      </IconButton>
                      <IconButton size="small" onClick={handleDeleteServer} title="Xóa server">
                        <DeleteIcon color="secondary" />
                      </IconButton>
                    </>
                  )}
                </Box>
              )}
            </Box>
          </Grid>

          <Grid item xs={12} md={source === 'remote_server' ? 4 : 5}>
            {source === 'google_drive' ? (
              <TextField
                label="Đường dẫn thư mục Google Drive"
                placeholder="https://drive.google.com/drive/folders/..."
                value={driveFolder}
                onChange={(e) => setDriveFolder(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                variant="outlined"
                size="small"
                fullWidth
              />
            ) : (
              <TextField
                label="Tìm kiếm nhạc trên server"
                placeholder="Nhập tên bài hát, album, nghệ sĩ..."
                value={query}
                onChange={(e) => handleQueryChange(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                variant="outlined"
                size="small"
                fullWidth
              />
            )}
          </Grid>

          {source === 'remote_server' && (
            <Grid item xs={5} sm={4} md={2}>
              <FormControl variant="outlined" size="small" fullWidth>
                <InputLabel>Số lượng ca sĩ</InputLabel>
                <Select
                  value={artistLimit}
                  label="Số lượng ca sĩ"
                  onChange={(e) => setArtistLimit(Number(e.target.value))}
                >
                  <MenuItem value={50}>50 ca sĩ</MenuItem>
                  <MenuItem value={100}>100 ca sĩ</MenuItem>
                  <MenuItem value={200}>200 ca sĩ</MenuItem>
                  <MenuItem value={500}>500 ca sĩ</MenuItem>
                </Select>
              </FormControl>
            </Grid>
          )}

          <Grid item xs={12} md={source === 'remote_server' ? 2 : 3}>
            <Box display="flex" gap={1} width="100%" alignItems="center">
              <Button
                variant="contained"
                color="primary"
                startIcon={busy === 'search' ? <CircularProgress size={16} color="inherit" /> : <SearchIcon />}
                onClick={handleSearch}
                disabled={busy === 'search'}
                style={{ flex: 1, height: 40 }}
              >
                {source === 'google_drive' ? 'Quét thư mục' : 'Tìm kiếm'}
              </Button>
              {rawSongs.length > 0 && (
                <Button
                  variant={showFilters || activeFiltersCount > 0 ? "contained" : "outlined"}
                  color={showFilters || activeFiltersCount > 0 ? "primary" : "default"}
                  onClick={() => setShowFilters(!showFilters)}
                  style={{ height: 40, minWidth: 90 }}
                  startIcon={<FilterListIcon />}
                >
                  Lọc{activeFiltersCount > 0 ? ` (${activeFiltersCount})` : ''}
                </Button>
              )}
            </Box>
          </Grid>
        </Grid>

        {source !== 'remote_server' && (
          <Box display="flex" alignItems="center" mt={1.5} pl={0.5}>
            <FormControlLabel
              control={
                <Switch
                  checked={losslessOnly}
                  onChange={(e) => {
                    setLosslessOnly(e.target.checked)
                    setRawSongs([])
                    setRawAlbums([])
                    setRawArtists([])
                    setRawArtistsTree([])
                  }}
                  color="primary"
                />
              }
              label={
                <Box display="flex" alignItems="center" gap={0.5}>
                  <GraphicEqIcon fontSize="small" style={{ color: losslessOnly ? '#C5A880' : 'inherit' }} />
                  <Typography variant="body2" style={{ fontWeight: losslessOnly ? 'bold' : 'normal', color: losslessOnly ? '#C5A880' : 'inherit' }}>
                    Chỉ tìm nhạc chất lượng cao Lossless / Hi-Res (FLAC, DSD, WAV...)
                  </Typography>
                </Box>
              }
            />
          </Box>
        )}

        {/* ADVANCED FILTERING PANEL */}
        {rawSongs.length > 0 && showFilters && (
          <Box className={classes.filterPanel}>
            <Box display="flex" alignItems="center" gap={1} mb={1.5}>
              <FilterListIcon color="primary" />
              <Typography variant="subtitle2" style={{ fontWeight: 'bold' }}>
                Bộ lọc kết quả ({rawSongs.length} bài tìm thấy)
              </Typography>
            </Box>
            <Grid container spacing={1}>
              <Grid item xs={6} sm={3}>
                <TextField
                  label="Tên ca sĩ"
                  placeholder="Nhập tên..."
                  value={filterSinger}
                  onChange={(e) => setFilterSinger(e.target.value)}
                  variant="outlined"
                  size="small"
                  fullWidth
                />
              </Grid>
              <Grid item xs={6} sm={3}>
                <TextField
                  label="Bài hát / Tác giả"
                  placeholder="Nhập tên..."
                  value={filterComposer}
                  onChange={(e) => setFilterComposer(e.target.value)}
                  variant="outlined"
                  size="small"
                  fullWidth
                />
              </Grid>
              <Grid item xs={4} sm={2}>
                <FormControl variant="outlined" size="small" fullWidth>
                  <InputLabel>Năm</InputLabel>
                  <Select value={filterYear} label="Năm" onChange={(e) => setFilterYear(e.target.value)}>
                    <MenuItem value="all">Tất cả</MenuItem>
                    {uniqueYears.map((y) => (
                      <MenuItem key={y} value={String(y)}>
                        {y}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
              <Grid item xs={4} sm={2}>
                <FormControl variant="outlined" size="small" fullWidth>
                  <InputLabel>Định dạng</InputLabel>
                  <Select value={filterFormat} label="Định dạng" onChange={(e) => setFilterFormat(e.target.value)}>
                    <MenuItem value="all">Tất cả</MenuItem>
                    {uniqueFormats.map((f) => (
                      <MenuItem key={f} value={f}>
                        {f.toUpperCase()}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
              <Grid item xs={4} sm={2}>
                <FormControl variant="outlined" size="small" fullWidth>
                  <InputLabel>Sắp xếp</InputLabel>
                  <Select value={sortBy} label="Sắp xếp" onChange={(e) => setSortBy(e.target.value)}>
                    <MenuItem value="quality">Chất lượng 🎧</MenuItem>
                    <MenuItem value="year">Mới phát hành 📅</MenuItem>
                    <MenuItem value="downloads">Lượt nghe 📥</MenuItem>
                    <MenuItem value="likes">Ưu thích ⭐</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
            </Grid>
          </Box>
        )}
      </Box>

      {/* WARNING MESSAGES */}
      {warnings.length > 0 && (
        <Box p={2} borderRadius={8} bgcolor="rgba(255, 152, 0, 0.1)" border="1px solid orange">
          {warnings.map((w, idx) => (
            <Typography key={idx} variant="body2" style={{ color: 'orange' }}>
              ⚠️ {w}
            </Typography>
          ))}
        </Box>
      )}

      {losslessOnly && (source === 'public_search_zing' || source === 'public_search_youtube') && (
        <Box p={2} borderRadius={8} bgcolor="rgba(197, 168, 128, 0.1)" border="1px solid #C5A880" mt={2}>
          <Typography variant="body2" style={{ color: '#C5A880', fontWeight: 'bold' }}>
            🔔 Nguồn Zing MP3 và YouTube Music chỉ cung cấp định dạng nén (MP3, Opus/AAC). Hãy tắt tùy chọn "Chỉ tìm nhạc chất lượng cao Lossless / Hi-Res" bên trên để tìm thấy kết quả từ các nguồn này.
          </Typography>
        </Box>
      )}

      {/* RESULTS DISPLAY CONTROLLER */}
      {renderedArtists.length > 0 && (
        <Box>
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={1.5}>
            <Typography variant="h6" className={classes.sectionTitle}>
              Danh mục kết quả
            </Typography>
            {/* FLAT / HIERARCHY SWITCH */}
            {source !== 'internet_archive' && (
              <ButtonGroup size="small" variant="outlined" color="primary">
                <Button
                  variant={displayMode === 'hierarchy' ? 'contained' : 'outlined'}
                  onClick={() => setDisplayMode('hierarchy')}
                  startIcon={<AccountTreeIcon />}
                >
                  <span className={classes.displayModeText}>Cấu trúc phân cấp</span>
                </Button>
                <Button
                  variant={displayMode === 'flat' ? 'contained' : 'outlined'}
                  onClick={() => setDisplayMode('flat')}
                  startIcon={<FormatListBulletedIcon />}
                >
                  <span className={classes.displayModeText}>Danh sách phẳng</span>
                </Button>
              </ButtonGroup>
            )}
          </Box>

          {displayMode === 'hierarchy' && source !== 'flat' ? (
            <Box>
              {/* TREE STRUCTURE: COLLAPSIBLE ARTISTS, ALBUMS & SONGS */}
              <List style={{ padding: 0 }}>
                {renderedArtists.map((artist) => {
                  const isArtistExpanded = !!expandedArtists[artist.id]
                  const artistBusy = busy === `load-${artist.id}`
                  const artistImportBusy = busy === `batch-${artist.name}`
                  return (
                    <Box key={artist.id} className={classes.treeArtistBlock}>
                      {/* ARTIST ROW */}
                      <Box 
                        className={classes.treeArtistHeader}
                        onClick={() => toggleArtistExpand(artist)}
                      >
                        <Box display="flex" alignItems="center" gap={1.5} style={{ flexGrow: 1, overflow: 'hidden' }}>
                          <IconButton size="small" style={{ color: '#C5A880', padding: 4 }}>
                            {isArtistExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                          </IconButton>
                          <Avatar style={{ width: 34, height: 34, backgroundColor: '#C5A880', color: '#0A0A0A', fontSize: '0.9rem', fontWeight: 'bold' }}>
                            {artist.name.charAt(0).toUpperCase()}
                          </Avatar>
                          <Box style={{ overflow: 'hidden' }}>
                            <Typography className={classes.treeArtistName}>
                              {artist.name}
                            </Typography>
                            <Typography variant="caption" color="textSecondary" style={{ fontSize: '0.75rem' }}>
                              {artist.albumCount} Album • {artist.songCount} Bài hát
                            </Typography>
                          </Box>
                        </Box>
                        <Box display="flex" alignItems="center">
                          {artistBusy && <CircularProgress size={14} style={{ marginRight: 8, color: '#C5A880' }} />}
                          {artist.songCount > 0 && (
                            <Button
                              size="small"
                              variant="outlined"
                              startIcon={artistImportBusy ? <CircularProgress size={12} color="inherit" /> : <GetAppIcon />}
                              disabled={artistImportBusy}
                              onClick={(e) => {
                                e.stopPropagation()
                                const allSongs = artist.albums.reduce((all, alb) => [...all, ...alb.songs], [])
                                handleImportBatch(artist.name, allSongs)
                              }}
                              style={{ border: '1px solid rgba(197, 168, 128, 0.3)', color: '#C5A880', padding: '2px 8px', fontSize: '0.75rem', borderRadius: 4 }}
                            >
                              <span className={classes.displayModeText}>Tải tất cả</span>
                            </Button>
                          )}
                        </Box>
                      </Box>

                      {/* ALBUMS ROW (Only visible if expanded) */}
                      {isArtistExpanded && (
                        <Box style={{ paddingLeft: 20, paddingRight: 12, paddingBottom: 8 }}>
                          {artistBusy ? (
                            <Box display="flex" justifyContent="center" py={1.5}>
                              <CircularProgress size={20} style={{ color: '#C5A880' }} />
                            </Box>
                          ) : (
                            <Box style={{ borderLeft: '1px dashed rgba(255, 255, 255, 0.1)', paddingLeft: 12 }}>
                              {artist.albums && artist.albums.length > 0 ? (
                                artist.albums.map((album) => {
                                  const isAlbumExpanded = !!expandedAlbums[album.id]
                                  const albumBusy = busy === `load-${album.id}`
                                  const albumImportBusy = busy === `batch-${album.name}`
                                  return (
                                    <Box key={album.id} className={classes.treeAlbumBlock}>
                                      {/* ALBUM ROW */}
                                      <Box 
                                        className={classes.treeAlbumHeader}
                                        onClick={() => toggleAlbumExpand(artist, album)}
                                      >
                                        <Box display="flex" alignItems="center" gap={1} style={{ flexGrow: 1, overflow: 'hidden' }}>
                                          <IconButton size="small" style={{ color: '#C5A880', padding: 4 }}>
                                            {isAlbumExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                                          </IconButton>
                                          <AlbumIcon style={{ fontSize: '1.25rem', color: '#C5A880' }} />
                                          <Box style={{ overflow: 'hidden' }}>
                                            <Typography className={classes.treeAlbumName}>
                                              {album.name} {album.year ? `(${album.year})` : ''}
                                            </Typography>
                                            <Typography variant="caption" color="textSecondary" style={{ fontSize: '0.72rem' }}>
                                              {album.songCount || album.songs.length || '?'} bài hát
                                            </Typography>
                                          </Box>
                                        </Box>
                                        <Box display="flex" alignItems="center">
                                          {albumBusy && <CircularProgress size={12} style={{ marginRight: 6, color: '#C5A880' }} />}
                                          {album.songs && album.songs.length > 0 && (
                                            <Button
                                              size="small"
                                              variant="text"
                                              startIcon={albumImportBusy ? <CircularProgress size={12} color="inherit" /> : <GetAppIcon />}
                                              disabled={albumImportBusy}
                                              onClick={(e) => {
                                                e.stopPropagation()
                                                handleImportBatch(album.name, album.songs)
                                              }}
                                              style={{ color: '#C5A880', padding: '2px 4px', fontSize: '0.72rem' }}
                                            >
                                              <span className={classes.displayModeText}>Tải album</span>
                                            </Button>
                                          )}
                                        </Box>
                                      </Box>

                                      {/* TRACKS LIST (Only visible if expanded) */}
                                      {isAlbumExpanded && (
                                        <Box style={{ paddingLeft: 8, paddingRight: 4, paddingBottom: 4 }}>
                                          {albumBusy ? (
                                            <Box display="flex" justifyContent="center" py={1.5}>
                                              <CircularProgress size={16} style={{ color: '#C5A880' }} />
                                            </Box>
                                          ) : (
                                            <List style={{ padding: 0 }}>
                                              {filterAndSortSongs(album.songs || []).map((song, sIdx) => {
                                                const isSongBusy = busy === `import-${song.id}`
                                                return (
                                                  <ListItem key={song.id} className={classes.songItem} divider style={{ borderBottom: '1px solid rgba(255,255,255,0.02)', padding: '6px 8px' }}>
                                                    <IconButton size="small" onClick={() => togglePreview(song)} style={{ marginRight: 4, padding: 4 }}>
                                                      {playing === song.id ? <StopIcon style={{ fontSize: '1.1rem' }} /> : <PlayArrowIcon style={{ fontSize: '1.1rem' }} />}
                                                    </IconButton>
                                                    <ListItemText
                                                      primary={
                                                        <Box display="flex" alignItems="center" gap={1} flexWrap="wrap">
                                                          <Typography variant="body2" style={{ fontWeight: 500, fontSize: '0.82rem' }}>
                                                            {sIdx + 1}. {song.title}
                                                          </Typography>
                                                          {renderAudioSpecs(song)}
                                                        </Box>
                                                      }
                                                      secondary={
                                                        <Box display="flex" gap={1.5} alignItems="center" flexWrap="wrap" mt={0.25} style={{ fontSize: '0.72rem', color: '#888' }}>
                                                          {song.length && <span>⏱️ {song.length}</span>}
                                                          {song.size > 0 && <span>💾 {formatBytes(song.size)}</span>}
                                                          {song.downloads > 0 && <span>📥 {song.downloads} lượt tải</span>}
                                                        </Box>
                                                      }
                                                      style={{ margin: 0 }}
                                                    />
                                                    <Box display="flex" alignItems="center" style={{ marginLeft: 4 }}>
                                                      <IconButton size="small" disabled={isSongBusy || importedIds.has(song.id)} onClick={() => handleImportSingle(song)} style={{ padding: 4 }}>
                                                        {isSongBusy ? (
                                                          <CircularProgress size={12} />
                                                        ) : importedIds.has(song.id) ? (
                                                          <CheckIcon style={{ fontSize: '1.1rem', color: '#4caf50' }} />
                                                        ) : (
                                                          <GetAppIcon style={{ fontSize: '1.1rem' }} />
                                                        )}
                                                      </IconButton>
                                                    </Box>
                                                  </ListItem>
                                                )
                                              })}
                                            </List>
                                          )}
                                        </Box>
                                      )}
                                    </Box>
                                  )
                                })
                              ) : (
                                <Typography variant="caption" style={{ color: '#888', fontStyle: 'italic', display: 'block', padding: 8 }}>
                                  Không tìm thấy album nào cho ca sĩ này
                                </Typography>
                              )}
                            </Box>
                          )}
                        </Box>
                      )}
                    </Box>
                  )
                })}
              </List>
            </Box>
          ) : (
            /* FLAT LIST VIEW */
            <Box>
              <List>
                {filterAndSortSongs(rawSongs).map((song, idx) => {
                  const isBusy = busy === `import-${song.id}`
                  return (
                    <ListItem key={song.id} className={classes.songItem} divider>
                      <IconButton onClick={() => togglePreview(song)}>
                        {playing === song.id ? <StopIcon /> : <PlayArrowIcon />}
                      </IconButton>
                      <ListItemText
                        primary={
                          <Box display="flex" alignItems="center" gap={1} flexWrap="wrap">
                            <Typography variant="body1" style={{ fontWeight: 500 }}>
                              {song.title}
                            </Typography>
                            {renderAudioSpecs(song)}
                          </Box>
                        }
                        secondary={
                          <Box display="flex" gap={1.5} alignItems="center" flexWrap="wrap" mt={0.5} className={classes.metaText}>
                            <span>🎤 {song.artist || 'Không rõ'}</span>
                            <span>📀 {song.album || 'Không rõ'}</span>
                            {song.length && <span>⏱️ {formatDuration(song.length)}</span>}
                            {song.size > 0 && <span>💾 {formatBytes(song.size)}</span>}
                          </Box>
                        }
                      />
                      <Box display="flex" alignItems="center" style={{ marginLeft: 8 }}>
                        <IconButton disabled={isBusy || importedIds.has(song.id)} onClick={() => handleImportSingle(song)}>
                          {isBusy ? (
                            <CircularProgress size={18} />
                          ) : importedIds.has(song.id) ? (
                            <CheckIcon style={{ color: '#4caf50' }} />
                          ) : (
                            <GetAppIcon />
                          )}
                        </IconButton>
                      </Box>
                    </ListItem>
                  )
                })}
              </List>
            </Box>
          )}
        </Box>
      )}

      {/* LOADING SPINNER */}
      {busy === 'search' && (
        <Box display="flex" justifyContent="center" py={8}>
          <CircularProgress />
        </Box>
      )}

      {/* REMOTE SERVER MANAGEMENT DIALOG */}
      <Dialog open={!!serverDialog} onClose={() => setServerDialog(null)} fullWidth>
        <DialogTitle>
          {serverDialog && serverDialog.id ? 'Sửa server nguồn' : 'Thêm server nguồn'}
        </DialogTitle>
        {serverDialog && (
          <DialogContent>
            <TextField
              label="Tên hiển thị (tùy chọn)"
              value={serverDialog.name || ''}
              onChange={(e) => setServerDialog({ ...serverDialog, name: e.target.value })}
              fullWidth
              margin="dense"
            />
            <TextField
              label="Địa chỉ server"
              placeholder="http://192.168.1.10:4533"
              value={serverDialog.url || ''}
              onChange={(e) => setServerDialog({ ...serverDialog, url: e.target.value })}
              fullWidth
              margin="dense"
              required
            />
            <TextField
              label="Tài khoản"
              value={serverDialog.username || ''}
              onChange={(e) => setServerDialog({ ...serverDialog, username: e.target.value })}
              fullWidth
              margin="dense"
              required
            />
            <TextField
              label="Mật khẩu"
              type="password"
              placeholder={serverDialog.id ? 'Để trống = giữ mật khẩu cũ' : ''}
              value={serverDialog.password || ''}
              onChange={(e) => setServerDialog({ ...serverDialog, password: e.target.value })}
              fullWidth
              margin="dense"
              required={!serverDialog.id}
            />
            {testState && testState !== 'busy' && (
              <Typography
                style={{ marginTop: 12, color: testState === 'ok' ? 'green' : 'red', fontSize: '0.85rem' }}
              >
                {testState === 'ok' ? '✔️ Kết nối thành công!' : `❌ Thất bại: ${testState}`}
              </Typography>
            )}
          </DialogContent>
        )}
        <DialogActions>
          <Button
            onClick={handleTestConnection}
            disabled={!serverDialog || !serverDialog.url || testState === 'busy'}
            startIcon={testState === 'busy' ? <CircularProgress size={16} /> : null}
          >
            Kiểm tra kết nối
          </Button>
          <Button onClick={() => setServerDialog(null)}>Hủy</Button>
          <Button
            color="primary"
            variant="contained"
            disabled={
              !serverDialog ||
              !serverDialog.url ||
              !serverDialog.username ||
              (!serverDialog.id && !serverDialog.password) ||
              busy === 'save-server'
            }
            onClick={handleSaveServer}
          >
            Lưu
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}

export default UnifiedImport
