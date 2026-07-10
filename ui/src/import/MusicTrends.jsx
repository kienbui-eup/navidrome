import React, { useState, useEffect, useRef } from 'react'
import { useSelector } from 'react-redux'
import { useTranslate, useNotify } from 'react-admin'
import {
  Box,
  Card,
  CardContent,
  Grid,
  Typography,
  Button,
  CircularProgress,
  Tabs,
  Tab,
  makeStyles,
  Container,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  IconButton,
  LinearProgress,
  Tooltip,
} from '@material-ui/core'
import {
  MdTrendingUp,
  MdPlayArrow,
  MdPause,
  MdCloudDownload,
  MdCheckCircle,
  MdError,
  MdMusicNote,
  MdSearch,
  MdFlashOn,
} from 'react-icons/md'
import { httpClient } from '../dataProvider'
import { REST_URL } from '../consts'

const useStyles = makeStyles((theme) => ({
  container: {
    marginTop: theme.spacing(3),
    marginBottom: theme.spacing(3),
    fontFamily: 'Outfit, Inter, Roboto, sans-serif',
  },
  headerPanel: {
    background: 'linear-gradient(135deg, rgba(20, 20, 35, 0.75) 0%, rgba(35, 20, 50, 0.65) 100%)',
    backdropFilter: 'blur(20px)',
    WebkitBackdropFilter: 'blur(20px)',
    borderRadius: 24,
    border: '1px solid rgba(255, 255, 255, 0.12)',
    padding: theme.spacing(4),
    marginBottom: theme.spacing(4),
    boxShadow: '0 12px 40px 0 rgba(0, 0, 0, 0.4)',
    color: '#fff',
    position: 'relative',
    overflow: 'hidden',
    '&::before': {
      content: '""',
      position: 'absolute',
      top: '-50%',
      left: '-50%',
      width: '200%',
      height: '200%',
      background: 'radial-gradient(circle, rgba(160, 60, 255, 0.15) 0%, transparent 60%)',
      pointerEvents: 'none',
    },
  },
  title: {
    fontWeight: 800,
    letterSpacing: '-1px',
    background: 'linear-gradient(45deg, #ff007f 10%, #7f00ff 90%)',
    WebkitBackgroundClip: 'text',
    WebkitTextFillColor: 'transparent',
    marginBottom: theme.spacing(1),
  },
  subtitle: {
    color: 'rgba(255, 255, 255, 0.7)',
    maxWidth: 700,
    lineHeight: 1.6,
  },
  tabsContainer: {
    marginBottom: theme.spacing(4),
    background: 'rgba(255, 255, 255, 0.03)',
    backdropFilter: 'blur(10px)',
    WebkitBackdropFilter: 'blur(10px)',
    borderRadius: 16,
    padding: 6,
    border: '1px solid rgba(255, 255, 255, 0.05)',
    display: 'flex',
    justifyContent: 'center',
  },
  tab: {
    borderRadius: 12,
    fontWeight: 700,
    textTransform: 'none',
    minWidth: 180,
    fontSize: '0.95rem',
    color: 'rgba(255, 255, 255, 0.5)',
    transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
    margin: '0 4px',
    '&.Mui-selected': {
      color: '#fff',
      background: 'linear-gradient(90deg, #ff007f 0%, #7f00ff 100%)',
      boxShadow: '0 6px 20px rgba(255, 0, 127, 0.4)',
    },
  },
  gridContainer: {
    marginTop: theme.spacing(2),
  },
  glassCard: {
    background: 'rgba(255, 255, 255, 0.03)',
    backdropFilter: 'blur(12px)',
    WebkitBackdropFilter: 'blur(12px)',
    border: '1px solid rgba(255, 255, 255, 0.08)',
    borderRadius: 22,
    overflow: 'hidden',
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
    transition: 'all 0.4s cubic-bezier(0.4, 0, 0.2, 1)',
    boxShadow: '0 8px 32px rgba(0, 0, 0, 0.2)',
    position: 'relative',
    '&:hover': {
      transform: 'translateY(-8px)',
      boxShadow: '0 16px 45px rgba(127, 0, 255, 0.3)',
      borderColor: 'rgba(127, 0, 255, 0.45)',
      background: 'rgba(255, 255, 255, 0.05)',
      '& $artwork': {
        transform: 'scale(1.1)',
      },
      '& $playOverlay': {
        opacity: 1,
      },
    },
  },
  cardContent: {
    padding: theme.spacing(3),
    display: 'flex',
    flexDirection: 'column',
    flexGrow: 1,
  },
  artworkWrapper: {
    position: 'relative',
    borderRadius: 16,
    overflow: 'hidden',
    paddingTop: '100%',
    boxShadow: '0 8px 24px rgba(0,0,0,0.3)',
    backgroundColor: 'rgba(0,0,0,0.2)',
    marginBottom: theme.spacing(2.5),
  },
  artwork: {
    position: 'absolute',
    top: 0,
    left: 0,
    width: '100%',
    height: '100%',
    objectFit: 'cover',
    transition: 'transform 0.6s cubic-bezier(0.4, 0, 0.2, 1)',
  },
  playOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    background: 'rgba(0, 0, 0, 0.45)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    opacity: 0,
    transition: 'opacity 0.3s ease',
  },
  playIconBtn: {
    background: 'linear-gradient(135deg, #ff007f 0%, #7f00ff 100%)',
    color: '#fff',
    width: 60,
    height: 60,
    boxShadow: '0 6px 20px rgba(255, 0, 127, 0.4)',
    '&:hover': {
      background: 'linear-gradient(135deg, #ff00af 0%, #8f00ff 100%)',
      transform: 'scale(1.1)',
    },
  },
  songTitle: {
    fontWeight: 700,
    fontSize: '1.15rem',
    color: '#fff',
    marginBottom: theme.spacing(0.5),
    display: '-webkit-box',
    '-webkit-line-clamp': 1,
    '-webkit-box-orient': 'vertical',
    overflow: 'hidden',
  },
  songArtist: {
    fontWeight: 500,
    fontSize: '0.95rem',
    color: 'rgba(255, 255, 255, 0.6)',
    marginBottom: theme.spacing(1.5),
    display: '-webkit-box',
    '-webkit-line-clamp': 1,
    '-webkit-box-orient': 'vertical',
    overflow: 'hidden',
  },
  songDesc: {
    fontSize: '0.85rem',
    color: 'rgba(255, 255, 255, 0.45)',
    lineHeight: 1.5,
    marginBottom: theme.spacing(2.5),
    flexGrow: 1,
    display: '-webkit-box',
    '-webkit-line-clamp': 2,
    '-webkit-box-orient': 'vertical',
    overflow: 'hidden',
  },
  cardActions: {
    display: 'flex',
    gap: theme.spacing(1.5),
    width: '100%',
  },
  btnPrimary: {
    background: 'linear-gradient(90deg, #ff007f 0%, #7f00ff 100%)',
    color: '#fff',
    borderRadius: 12,
    textTransform: 'none',
    fontWeight: 700,
    padding: '10px 20px',
    flexGrow: 1,
    boxShadow: '0 4px 15px rgba(255, 0, 127, 0.3)',
    transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
    '&:hover': {
      background: 'linear-gradient(90deg, #ff00af 0%, #8f00ff 100%)',
      boxShadow: '0 6px 22px rgba(255, 0, 127, 0.5)',
      transform: 'translateY(-2px)',
    },
    '&:disabled': {
      background: 'rgba(255,255,255,0.08)',
      color: 'rgba(255,255,255,0.3)',
    },
  },
  btnSecondary: {
    background: 'rgba(255, 255, 255, 0.05)',
    color: '#fff',
    borderRadius: 12,
    textTransform: 'none',
    fontWeight: 600,
    padding: '10px 20px',
    flexGrow: 1,
    border: '1px solid rgba(255, 255, 255, 0.1)',
    transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
    '&:hover': {
      background: 'rgba(255, 255, 255, 0.12)',
      borderColor: 'rgba(255, 255, 255, 0.25)',
      transform: 'translateY(-2px)',
    },
  },
  badge: {
    background: 'rgba(127, 0, 255, 0.15)',
    color: '#c080ff',
    border: '1px solid rgba(127, 0, 255, 0.3)',
    borderRadius: 8,
    padding: '4px 10px',
    fontSize: '0.75rem',
    fontWeight: 700,
    textTransform: 'uppercase',
    letterSpacing: '1px',
    display: 'inline-block',
    marginBottom: theme.spacing(2),
  },
  dialog: {
    '& .MuiPaper-root': {
      background: 'rgba(25, 20, 35, 0.9)',
      backdropFilter: 'blur(30px)',
      WebkitBackdropFilter: 'blur(30px)',
      border: '1px solid rgba(255, 255, 255, 0.1)',
      borderRadius: 24,
      color: '#fff',
    },
  },
  table: {
    color: '#fff',
    '& .MuiTableCell-root': {
      borderBottom: '1px solid rgba(255,255,255,0.08)',
      color: 'rgba(255,255,255,0.8)',
    },
    '& .MuiTableHead-root .MuiTableCell-root': {
      color: '#fff',
      fontWeight: 700,
      background: 'rgba(255,255,255,0.03)',
    },
  },
  miniPlayer: {
    position: 'fixed',
    bottom: 24,
    right: 24,
    background: 'linear-gradient(135deg, rgba(20, 15, 35, 0.95) 0%, rgba(35, 15, 55, 0.95) 100%)',
    backdropFilter: 'blur(20px)',
    WebkitBackdropFilter: 'blur(20px)',
    borderRadius: 20,
    border: '1px solid rgba(127, 0, 255, 0.35)',
    padding: '12px 24px',
    display: 'flex',
    alignItems: 'center',
    gap: 16,
    boxShadow: '0 8px 32px rgba(127, 0, 255, 0.4)',
    color: '#fff',
    zIndex: 2000,
    animation: '$pulseGlow 2s infinite ease-in-out',
  },
  '@keyframes pulseGlow': {
    '0%': {
      boxShadow: '0 8px 32px rgba(127, 0, 255, 0.3)',
    },
    '50%': {
      boxShadow: '0 8px 32px rgba(255, 0, 127, 0.55)',
    },
    '100%': {
      boxShadow: '0 8px 32px rgba(127, 0, 255, 0.3)',
    },
  },
}))

const MusicTrends = () => {
  const classes = useStyles()
  const notify = useNotify()
  const translate = useTranslate()

  // Get current active library ID from Redux
  const { userLibraries, selectedLibraries } = useSelector((state) => state.library)
  const libraryId = selectedLibraries && selectedLibraries.length > 0
    ? selectedLibraries[0]
    : (userLibraries && userLibraries.length > 0 ? userLibraries[0].id : 'default')

  // Component States
  const [tabIndex, setTabIndex] = useState(0)
  const [loading, setLoading] = useState(true)
  const [trends, setTrends] = useState({ trending_vietnam: [], audiophile_master: [] })

  // Audio Preview State
  const [previewTrack, setPreviewTrack] = useState(null) // { query, source, fileId, title, artist, status: 'searching' | 'playing' | 'paused' | 'error' }
  const audioRef = useRef(new Audio())

  // Import Dialog State
  const [importDialogOpen, setImportDialogOpen] = useState(false)
  const [selectedTrack, setSelectedTrack] = useState(null)
  const [searchResults, setSearchResults] = useState([])
  const [searchingSources, setSearchingSources] = useState(false)
  const [importJob, setImportJob] = useState(null)
  const pollingIntervalRef = useRef(null)

  // Load trends from backend
  const fetchTrends = () => {
    setLoading(true)
    httpClient(`${REST_URL}/import/trends`)
      .then(({ json }) => {
        setTrends(json)
        setLoading(false)
      })
      .catch((err) => {
        console.error('Error fetching music trends:', err)
        notify('Không thể kết nối danh sách xu hướng từ máy chủ.', 'warning')
        setLoading(false)
      })
  }

  useEffect(() => {
    fetchTrends()
    return () => {
      audioRef.current.pause()
      if (pollingIntervalRef.current) clearInterval(pollingIntervalRef.current)
    }
  }, [])

  // Audio Player Listeners & Synchronization
  useEffect(() => {
    const audio = audioRef.current

    const handlePlay = () => {
      setPreviewTrack((prev) => (prev ? { ...prev, status: 'playing' } : prev))
    }
    const handlePause = () => {
      setPreviewTrack((prev) => (prev ? { ...prev, status: 'paused' } : prev))
    }
    const handleEnded = () => {
      setPreviewTrack(null)
    }
    const handleError = () => {
      notify('Không tải được nguồn phát thử cho bài hát này.', 'warning')
      setPreviewTrack(null)
    }

    audio.addEventListener('play', handlePlay)
    audio.addEventListener('pause', handlePause)
    audio.addEventListener('ended', handleEnded)
    audio.addEventListener('error', handleError)

    return () => {
      audio.removeEventListener('play', handlePlay)
      audio.removeEventListener('pause', handlePause)
      audio.removeEventListener('ended', handleEnded)
      audio.removeEventListener('error', handleError)
    }
  }, [notify])

  // Handle Play/Pause Preview Song
  const handleTogglePreview = async (track) => {
    const query = `${track.artist} - ${track.title}`

    // If already active preview track
    if (previewTrack && previewTrack.query === query) {
      if (previewTrack.status === 'playing') {
        audioRef.current.pause()
        setPreviewTrack((prev) => ({ ...prev, status: 'paused' }))
      } else {
        audioRef.current.play().catch(() => {})
        setPreviewTrack((prev) => ({ ...prev, status: 'playing' }))
      }
      return
    }

    // Load new preview track
    audioRef.current.pause()
    setPreviewTrack({ query, status: 'searching', title: track.title, artist: track.artist })

    try {
      // Find playable online stream on-the-fly
      const { json } = await httpClient(`/api/import/search/songs?q=${encodeURIComponent(query)}`)
      const playableHits = json.hits || []

      // Pick first zing or youtube hit
      const streamHit = playableHits.find((h) => h.source === 'zing' || h.source === 'youtube' || h.source === 'deezer')

      if (!streamHit) {
        notify('Không tìm thấy bản phát thử miễn phí trực tuyến.', 'info')
        setPreviewTrack(null)
        return
      }

      // Build backend preview stream proxy url
      let url = ''
      if (streamHit.source === 'zing') {
        url = `${REST_URL}/import/preview?source=zing&id=${streamHit.fileId}&title=${encodeURIComponent(streamHit.title)}&artist=${encodeURIComponent(streamHit.artist)}`
      } else if (streamHit.source === 'youtube') {
        url = `${REST_URL}/import/preview?source=youtube&id=${streamHit.fileId}`
      } else if (streamHit.source === 'deezer') {
        url = `${REST_URL}/import/preview?source=deezer&id=${streamHit.fileId}`
      } else if (streamHit.previewUrl) {
        url = streamHit.previewUrl
      }

      setPreviewTrack({
        query,
        source: streamHit.source,
        fileId: streamHit.fileId,
        title: track.title,
        artist: track.artist,
        status: 'playing',
      })

      audioRef.current.src = url
      audioRef.current.load()
      audioRef.current.play().catch(() => {
        setPreviewTrack(null)
      })
    } catch (err) {
      console.error('Error finding preview stream:', err)
      notify('Gặp lỗi khi tìm kiếm luồng nhạc thử.', 'warning')
      setPreviewTrack(null)
    }
  }

  // Open Import Dialog & Search Multi-Provider Sources
  const handleOpenImportDialog = async (track) => {
    setSelectedTrack(track)
    setSearchResults([])
    setSearchingSources(true)
    setImportJob(null)
    setImportDialogOpen(true)

    const query = `${track.artist} - ${track.title}`
    try {
      const { json } = await httpClient(`/api/import/search/songs?q=${encodeURIComponent(query)}`)
      // Group and sort sources by lossless, quality desc
      const hits = json.hits || []
      setSearchResults(hits)
      setSearchingSources(false)
    } catch (err) {
      console.error('Error searching sources:', err)
      notify('Lỗi tìm kiếm nguồn tải từ các nhà mạng.', 'warning')
      setSearchingSources(false)
    }
  }

  // Trigger Background Import Job
  const handleStartImport = async (hit) => {
    const label = `${hit.artist} - ${hit.title}`
    setImportJob({ status: 'running', completed: 0, failed: 0, total: 1, message: 'Đang gửi yêu cầu...' })

    try {
      const { json } = await httpClient('/api/import/job', {
        method: 'POST',
        body: JSON.stringify({
          items: [{
            type: hit.source,
            id: hit.fileId || hit.id || hit.filename,
            name: label,
          }],
          libraryId,
        }),
      })

      const jobId = json.jobId
      pollJobStatus(jobId)
    } catch (err) {
      console.error('Error starting import:', err)
      setImportJob({ status: 'failed', error: err.message || 'Lỗi không xác định' })
      notify('Không thể xếp hàng công việc tải về.', 'warning')
    }
  }

  // One-Click Fast Quality Import (Automatically chooses best quality)
  const handleQuickImport = async (track) => {
    const label = `${track.artist} - ${track.title}`
    notify(`Đang tự động chọn nguồn chất lượng cao nhất cho "${label}"...`, 'info')

    try {
      const { json: searchJson } = await httpClient(`/api/import/search/songs?q=${encodeURIComponent(label)}`)
      const hits = searchJson.hits || []

      if (hits.length === 0) {
        notify(`Không tìm thấy nguồn tải tương thích cho "${label}".`, 'warning')
        return
      }

      // Pick the best match (Deezer FLAC lossless first, then Zing, then YouTube)
      let bestHit = hits.find((h) => h.source === 'deezer' && h.lossless)
      if (!bestHit) bestHit = hits.find((h) => h.source === 'zing')
      if (!bestHit) bestHit = hits.find((h) => h.source === 'youtube')
      if (!bestHit) bestHit = hits[0]

      notify(`Đã chọn nguồn [${bestHit.source.toUpperCase()}] cho "${label}". Bắt đầu tải...`, 'info')

      const { json: jobJson } = await httpClient('/api/import/job', {
        method: 'POST',
        body: JSON.stringify({
          items: [{
            type: bestHit.source,
            id: bestHit.fileId || bestHit.id || bestHit.filename,
            name: label,
          }],
          libraryId,
        }),
      })

      notify(`Đã đưa bài hát "${label}" vào tiến trình tải ngầm thành công.`, 'success')
    } catch (err) {
      console.error('Error in quick import:', err)
      notify('Tải nhanh không thành công. Hãy chọn Import thủ công.', 'warning')
    }
  }

  // Poll Import Job Status
  const pollJobStatus = (jobId) => {
    if (pollingIntervalRef.current) clearInterval(pollingIntervalRef.current)

    pollingIntervalRef.current = setInterval(() => {
      httpClient(`/api/import/job/${jobId}`)
        .then(({ json }) => {
          setImportJob(json)
          if (json.status !== 'running') {
            clearInterval(pollingIntervalRef.current)
            if (json.failed > 0) {
              notify('Có lỗi xảy ra trong quá trình tải xuống và giải mã.', 'warning')
            } else {
              notify(`Nhập thư viện hoàn tất! Đã thêm "${selectedTrack?.title}"`, 'success')
              // Trigger a rescan so changes appear instantly
              httpClient('/api/import/scan', { method: 'POST' }).catch(() => {})
            }
          }
        })
        .catch(() => {
          clearInterval(pollingIntervalRef.current)
          setImportJob({ status: 'error', error: 'Mất kết nối theo dõi tiến trình.' })
        })
    }, 1500)
  }

  const handleCloseDialog = () => {
    if (pollingIntervalRef.current) clearInterval(pollingIntervalRef.current)
    setImportDialogOpen(false)
    setImportJob(null)
  }

  const activeTracks = tabIndex === 0 ? trends.trending_vietnam : trends.audiophile_master

  return (
    <Container maxWidth="lg" className={classes.container}>
      {/* Sleek Glassmorphism Header */}
      <Box className={classes.headerPanel}>
        <Typography variant="h4" className={classes.title}>
          Nhạc Hot & Xu Hướng
        </Typography>
        <Typography variant="body1" className={classes.subtitle}>
          Khám phá những ca khúc Việt Nam đang thịnh hành và các tuyệt phẩm Audiophile tham chiếu chất lượng đỉnh cao.
          Nghe thử tức thời và đồng bộ hóa liền mạch vào thư viện Navidrome cá nhân của bạn chỉ với một lần nhấp chuột.
        </Typography>
      </Box>

      {/* Futuristic Tabs Navigation */}
      <Box className={classes.tabsContainer}>
        <Tabs
          value={tabIndex}
          onChange={(e, val) => setTabIndex(val)}
          indicatorColor="none"
          textColor="none"
        >
          <Tab
            icon={<MdTrendingUp size={22} style={{ marginRight: 8, verticalAlign: 'middle' }} />}
            label="Bảng Xếp Hạng Việt Nam"
            className={classes.tab}
          />
          <Tab
            icon={<MdMusicNote size={22} style={{ marginRight: 8, verticalAlign: 'middle' }} />}
            label="Audiophile Thượng Hạng"
            className={classes.tab}
          />
        </Tabs>
      </Box>

      {/* Dynamic Contents Grid */}
      {loading ? (
        <Box display="flex" justifyContent="center" py={12}>
          <CircularProgress size={50} style={{ color: '#ff007f' }} />
        </Box>
      ) : (
        <Grid container spacing={3.5} className={classes.gridContainer}>
          {activeTracks && activeTracks.length > 0 ? (
            activeTracks.map((track, idx) => (
              <Grid item xs={12} sm={6} md={4} key={idx}>
                <Card className={classes.glassCard}>
                  <CardContent className={classes.cardContent}>
                    {/* Floating Glow Indicator */}
                    <Box style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span className={classes.badge}>
                        {tabIndex === 0 ? `TOP ${idx + 1}` : 'Audiophile Master'}
                      </span>
                      {track.releaseDate && (
                        <Typography variant="caption" style={{ color: 'rgba(255,255,255,0.45)', fontWeight: 600 }}>
                          {track.releaseDate}
                        </Typography>
                      )}
                    </Box>

                    {/* Artwork Wrapper with Zoom & Hover Trigger */}
                    <Box className={classes.artworkWrapper}>
                      <img
                        src={track.artwork || 'https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?q=80&w=300&auto=format&fit=crop'}
                        alt={track.title}
                        className={classes.artwork}
                        onError={(e) => {
                          e.target.src = 'https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?q=80&w=300&auto=format&fit=crop'
                        }}
                      />
                      {/* Play Overlay */}
                      <Box className={classes.playOverlay}>
                        <IconButton
                          className={classes.playIconBtn}
                          onClick={() => handleTogglePreview(track)}
                        >
                          {previewTrack && previewTrack.query === `${track.artist} - ${track.title}` && previewTrack.status === 'playing' ? (
                            <MdPause size={32} />
                          ) : (
                            <MdPlayArrow size={32} style={{ marginLeft: 4 }} />
                          )}
                        </IconButton>
                      </Box>
                    </Box>

                    {/* Metadata Content */}
                    <Typography className={classes.songTitle}>{track.title}</Typography>
                    <Typography className={classes.songArtist}>{track.artist}</Typography>
                    <Typography className={classes.songDesc}>{track.description || 'Không có mô tả chi tiết từ máy chủ.'}</Typography>

                    {/* Action Group */}
                    <Box className={classes.cardActions}>
                      <Button
                        className={`${classes.actionButton} ${classes.btnPrimary}`}
                        startIcon={<MdCloudDownload size={20} />}
                        onClick={() => handleOpenImportDialog(track)}
                      >
                        Import
                      </Button>
                      <Tooltip title="Tải nhanh nguồn nhạc chất lượng cao nhất tự động" arrow>
                        <Button
                          className={`${classes.actionButton} ${classes.btnSecondary}`}
                          style={{ minWidth: 48, padding: '10px 14px' }}
                          onClick={() => handleQuickImport(track)}
                        >
                          <MdFlashOn size={22} style={{ color: '#ffb300' }} />
                        </Button>
                      </Tooltip>
                    </Box>
                  </CardContent>
                </Card>
              </Grid>
            ))
          ) : (
            <Grid item xs={12}>
              <Box py={8} textAlign="center">
                <Typography style={{ color: 'rgba(255,255,255,0.4)' }}>
                  Không tìm thấy bài hát nào trong chuyên mục này.
                </Typography>
              </Box>
            </Grid>
          )}
        </Grid>
      )}

      {/* Floating Modern Preview Playback Controller */}
      {previewTrack && (
        <Box className={classes.miniPlayer}>
          <Box display="flex" alignItems="center" justifyContent="center">
            {previewTrack.status === 'searching' ? (
              <CircularProgress size={24} style={{ color: '#ff007f' }} />
            ) : (
              <IconButton
                style={{ background: 'rgba(255, 0, 127, 0.1)', color: '#ff007f', padding: 8 }}
                onClick={() => {
                  if (previewTrack.status === 'playing') {
                    audioRef.current.pause()
                  } else {
                    audioRef.current.play().catch(() => {})
                  }
                }}
              >
                {previewTrack.status === 'playing' ? <MdPause size={24} /> : <MdPlayArrow size={24} />}
              </IconButton>
            )}
          </Box>
          <Box>
            <Typography variant="body2" style={{ fontWeight: 700, color: '#fff' }}>
              {previewTrack.status === 'searching' ? 'Đang tìm kiếm luồng nhạc thử...' : previewTrack.title}
            </Typography>
            <Typography variant="caption" style={{ color: 'rgba(255, 255, 255, 0.5)', fontWeight: 600 }}>
              {previewTrack.status === 'searching' ? 'Vui lòng chờ giây lát' : previewTrack.artist}
            </Typography>
          </Box>
        </Box>
      )}

      {/* Seamless Multi-Source Import Dialog */}
      <Dialog
        open={importDialogOpen}
        onClose={handleCloseDialog}
        className={classes.dialog}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle style={{ borderBottom: '1px solid rgba(255,255,255,0.08)', fontWeight: 700 }}>
          Nhập nhạc: {selectedTrack?.title} - {selectedTrack?.artist}
        </DialogTitle>
        <DialogContent style={{ padding: '24px' }}>
          {searchingSources ? (
            <Box display="flex" flexDirection="column" alignItems="center" py={6} gap={2}>
              <CircularProgress size={40} style={{ color: '#7f00ff' }} />
              <Typography style={{ color: 'rgba(255,255,255,0.6)' }}>
                Đang quét nguồn nhạc trực tuyến chất lượng cao...
              </Typography>
            </Box>
          ) : importJob ? (
            /* Import Processing View */
            <Box py={4} textAlign="center" display="flex" flexDirection="column" gap={3}>
              {importJob.status === 'running' ? (
                <>
                  <CircularProgress size={54} style={{ color: '#ff007f' }} />
                  <Box>
                    <Typography variant="h6" style={{ fontWeight: 700, color: '#fff', marginBottom: 8 }}>
                      Đang đồng bộ hóa vào Navidrome...
                    </Typography>
                    <Typography variant="body2" style={{ color: 'rgba(255,255,255,0.5)' }}>
                      Mã tiến trình: {importJob.id}
                    </Typography>
                  </Box>
                  <Box width="100%" px={4}>
                    <LinearProgress style={{ borderRadius: 4, height: 8 }} />
                  </Box>
                </>
              ) : importJob.status === 'failed' || importJob.status === 'error' ? (
                <>
                  <MdError size={64} style={{ color: '#f44336' }} />
                  <Typography variant="h6" style={{ fontWeight: 700, color: '#fff' }}>
                    Nhập nhạc không thành công
                  </Typography>
                  <Typography variant="body2" style={{ color: '#f44336' }}>
                    Chi tiết lỗi: {importJob.error || 'Quá trình tải về thất bại.'}
                  </Typography>
                </>
              ) : (
                <>
                  <MdCheckCircle size={64} style={{ color: '#4caf50' }} />
                  <Typography variant="h6" style={{ fontWeight: 700, color: '#fff' }}>
                    Nhập nhạc hoàn tất!
                  </Typography>
                  <Typography variant="body2" style={{ color: 'rgba(255,255,255,0.6)' }}>
                    Bài hát đã được lưu, chuẩn hóa siêu dữ liệu và thêm vào thư viện của bạn.
                  </Typography>
                </>
              )}
            </Box>
          ) : (
            /* Sources Table View */
            <Box>
              <Typography variant="body2" style={{ color: 'rgba(255,255,255,0.5)', marginBottom: 16 }}>
                Tìm thấy {searchResults.length} nguồn tải tương thích. Hãy chọn phiên bản chất lượng tối ưu nhất bên dưới để lưu vào thư viện:
              </Typography>
              <TableContainer style={{ background: 'rgba(0,0,0,0.15)', borderRadius: 16 }}>
                <Table className={classes.table}>
                  <TableHead>
                    <TableRow>
                      <TableCell>Nhà Cung Cấp</TableCell>
                      <TableCell>Tiêu Đề / Nghệ Sĩ</TableCell>
                      <TableCell>Định Dạng</TableCell>
                      <TableCell>Kích Thước</TableCell>
                      <TableCell align="right">Hành Động</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {searchResults.length > 0 ? (
                      searchResults.map((hit, i) => (
                        <TableRow key={i}>
                          <TableCell style={{ fontWeight: 700, color: hit.lossless ? '#00e676' : '#2196f3', textTransform: 'uppercase' }}>
                            {hit.source} {hit.lossless && '✨ LOSSLESS'}
                          </TableCell>
                          <TableCell>
                            <Typography style={{ fontWeight: 600, fontSize: '0.9rem' }}>{hit.title}</Typography>
                            <Typography variant="caption" style={{ color: 'rgba(255,255,255,0.45)' }}>{hit.artist}</Typography>
                          </TableCell>
                          <TableCell style={{ color: hit.lossless ? '#00e676' : 'rgba(255,255,255,0.7)' }}>{hit.format}</TableCell>
                          <TableCell>{hit.size ? `${(hit.size / 1024 / 1024).toFixed(2)} MB` : '--'}</TableCell>
                          <TableCell align="right">
                            <Button
                              variant="contained"
                              size="small"
                              className={classes.actionButton}
                              style={{
                                background: hit.lossless ? 'linear-gradient(90deg, #00c853 0%, #00e676 100%)' : 'rgba(255,255,255,0.08)',
                                color: '#fff',
                                textTransform: 'none',
                                fontWeight: 700,
                              }}
                              onClick={() => handleStartImport(hit)}
                            >
                              Tải nguồn này
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))
                    ) : (
                      <TableRow>
                        <TableCell colSpan={5} align="center" style={{ py: 4, color: 'rgba(255,255,255,0.4)' }}>
                          Không tìm thấy nguồn tải tự động nào cho bài hát này trực tuyến.
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </TableContainer>
            </Box>
          )}
        </DialogContent>
        <DialogActions style={{ padding: '16px 24px', borderTop: '1px solid rgba(255,255,255,0.08)' }}>
          <Button onClick={handleCloseDialog} className={`${classes.actionButton} ${classes.btnSecondary}`}>
            Đóng
          </Button>
        </DialogActions>
      </Dialog>
    </Container>
  )
}

export default MusicTrends
