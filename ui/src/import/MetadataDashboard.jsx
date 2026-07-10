import React, { useState, useEffect, useRef } from 'react'
import {
  Card,
  CardContent,
  Grid,
  Typography,
  Button,
  LinearProgress,
  Box,
  CircularProgress,
  makeStyles,
  Container,
} from '@material-ui/core'
import MusicNoteIcon from '@material-ui/icons/MusicNote'
import PeopleIcon from '@material-ui/icons/People'
import AlbumIcon from '@material-ui/icons/Album'
import BuildIcon from '@material-ui/icons/Build'
import ErrorIcon from '@material-ui/icons/Error'
import RefreshIcon from '@material-ui/icons/Refresh'
import { httpClient } from '../dataProvider'
import { REST_URL } from '../consts'
import { useTranslate } from 'react-admin'

const useStyles = makeStyles((theme) => ({
  container: {
    paddingTop: theme.spacing(4),
    paddingBottom: theme.spacing(4),
  },
  header: {
    marginBottom: theme.spacing(4),
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: theme.spacing(2),
  },
  titleSection: {
    '& h4': {
      fontWeight: 800,
      letterSpacing: '-0.5px',
      background: 'linear-gradient(90deg, #3f51b5 0%, #e91e63 100%)',
      WebkitBackgroundClip: 'text',
      WebkitTextFillColor: 'transparent',
    }
  },
  card: {
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'space-between',
    borderRadius: 24,
    background: theme.palette.type === 'dark' 
      ? 'rgba(30, 30, 30, 0.45)' 
      : 'rgba(255, 255, 255, 0.55)',
    backdropFilter: 'blur(20px)',
    border: '1px solid rgba(255, 255, 255, 0.08)',
    boxShadow: '0 8px 32px 0 rgba(31, 38, 135, 0.03)',
    transition: 'all 0.35s cubic-bezier(0.4, 0, 0.2, 1)',
    '&:hover': {
      transform: 'translateY(-6px)',
      boxShadow: '0 16px 40px 0 rgba(31, 38, 135, 0.12)',
      border: '1px solid ' + theme.palette.primary.main + '44',
      '& $iconBox': {
        transform: 'scale(1.1) rotate(5deg)',
      }
    },
  },
  iconBox: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: 52,
    height: 52,
    borderRadius: 16,
    transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
    marginBottom: theme.spacing(2),
  },
  statValue: {
    fontWeight: 800,
    marginTop: theme.spacing(1),
    letterSpacing: '-1px',
    color: theme.palette.text.primary,
  },
  actionCard: {
    padding: theme.spacing(3),
    borderRadius: 24,
    background: theme.palette.type === 'dark' 
      ? 'rgba(30, 30, 30, 0.4)' 
      : 'rgba(255, 255, 255, 0.6)',
    backdropFilter: 'blur(20px)',
    border: '1px solid rgba(255, 255, 255, 0.08)',
    boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.03)',
    transition: 'all 0.35s cubic-bezier(0.4, 0, 0.2, 1)',
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'space-between',
    height: '100%',
    '&:hover': {
      boxShadow: '0 12px 40px 0 rgba(0, 0, 0, 0.06)',
      transform: 'translateY(-3px)',
    }
  },
  allActionsCard: {
    padding: theme.spacing(3),
    borderRadius: 24,
    background: theme.palette.type === 'dark' 
      ? 'linear-gradient(135deg, rgba(233, 30, 99, 0.12) 0%, rgba(156, 39, 176, 0.12) 100%)' 
      : 'linear-gradient(135deg, rgba(233, 30, 99, 0.06) 0%, rgba(156, 39, 176, 0.06) 100%)',
    border: '2px dashed ' + theme.palette.secondary.main + '66',
    boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.03)',
    transition: 'all 0.35s cubic-bezier(0.4, 0, 0.2, 1)',
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'space-between',
    height: '100%',
    '&:hover': {
      boxShadow: '0 12px 40px 0 rgba(233, 30, 99, 0.12)',
      transform: 'translateY(-4px)',
      border: '2px dashed ' + theme.palette.secondary.main,
    }
  },
  progressBar: {
    height: 12,
    borderRadius: 6,
    marginTop: theme.spacing(1),
    marginBottom: theme.spacing(1),
    background: theme.palette.type === 'dark' ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.05)',
    '& .MuiLinearProgress-bar': {
      borderRadius: 6,
      background: 'linear-gradient(90deg, #3f51b5 0%, #e91e63 100%)',
    }
  },
  logBox: {
    maxHeight: 220,
    overflowY: 'auto',
    backgroundColor: '#0c0c0d',
    color: '#00ff66',
    fontFamily: '"Fira Code", monospace, "Courier New"',
    fontSize: '0.85rem',
    padding: theme.spacing(2.5),
    borderRadius: 16,
    marginTop: theme.spacing(2),
    border: '1px solid rgba(255, 255, 255, 0.05)',
    boxShadow: 'inset 0 4px 12px rgba(0, 0, 0, 0.5)',
    '&::-webkit-scrollbar': {
      width: 6,
    },
    '&::-webkit-scrollbar-thumb': {
      backgroundColor: 'rgba(255, 255, 255, 0.15)',
      borderRadius: 3,
    }
  },
  btn: {
    borderRadius: 14,
    textTransform: 'none',
    fontWeight: 700,
    padding: '10px 20px',
    boxShadow: 'none',
    transition: 'all 0.3s ease',
    '&:hover': {
      boxShadow: '0 4px 20px rgba(0,0,0,0.08)',
    }
  }
}))

const MetadataDashboard = () => {
  const classes = useStyles()
  const translate = useTranslate()
  const [stats, setStats] = useState(null)
  const [loadingStats, setLoadingStats] = useState(true)
  const [activeJob, setActiveJob] = useState(null)
  const [jobProgress, setJobProgress] = useState(null)
  const pollingRef = useRef(null)
  const logBoxRef = useRef(null)

  // Fetch data statistics
  const fetchStats = () => {
    setLoadingStats(true)
    httpClient(`${REST_URL}/metadata/stats`)
      .then(({ json }) => {
        setStats(json)
        setLoadingStats(false)
      })
      .catch((err) => {
        console.error('Error fetching metadata stats:', err)
        setLoadingStats(false)
      })
  }

  useEffect(() => {
    fetchStats()
    return () => {
      if (pollingRef.current) clearInterval(pollingRef.current)
    }
  }, [])

  // Auto-scroll logs to bottom
  useEffect(() => {
    if (logBoxRef.current) {
      logBoxRef.current.scrollTop = logBoxRef.current.scrollHeight
    }
  }, [jobProgress?.errors])

  // Recover active autofix job on mount (prevent tab-switching state loss)
  useEffect(() => {
    const activeJobId = localStorage.getItem('activeMetadataJobId')
    if (activeJobId) {
      httpClient(`${REST_URL}/metadata/autofix/status/${activeJobId}`)
        .then(({ json }) => {
          if (json && json.status === 'running') {
            setActiveJob(json)
            setJobProgress(json)
            startPollingJob(json.id)
          } else {
            localStorage.removeItem('activeMetadataJobId')
          }
        })
        .catch(() => {
          localStorage.removeItem('activeMetadataJobId')
        })
    }
  }, [])

  // Poll job status
  const startPollingJob = (jobId) => {
    if (pollingRef.current) clearInterval(pollingRef.current)

    pollingRef.current = setInterval(() => {
      httpClient(`${REST_URL}/metadata/autofix/status/${jobId}`)
        .then(({ json }) => {
          setJobProgress(json)
          if (json.status === 'completed' || json.status === 'failed') {
            clearInterval(pollingRef.current)
            setActiveJob(null)
            localStorage.removeItem('activeMetadataJobId')
            fetchStats() // refresh statistics after completion
          }
        })
        .catch((err) => {
          console.error('Error polling job status:', err)
          clearInterval(pollingRef.current)
          localStorage.removeItem('activeMetadataJobId')
        })
    }, 1000)
  }

  // Trigger Bulk Auto-Fix
  const triggerAutoFix = (type) => {
    if (activeJob) return

    httpClient(`${REST_URL}/metadata/autofix`, {
      method: 'POST',
      body: JSON.stringify({ type }),
    })
      .then(({ json }) => {
        setActiveJob(json)
        setJobProgress(json)
        localStorage.setItem('activeMetadataJobId', json.id)
        startPollingJob(json.id)
      })
      .catch((err) => {
        console.error('Error triggering auto-fix:', err)
      })
  }

  const getProgressPercent = () => {
    if (!jobProgress || jobProgress.total === 0) return 0
    return Math.round((jobProgress.processed / jobProgress.total) * 100)
  }

  return (
    <Container maxWidth="lg" className={classes.container}>
      {/* Page Header */}
      <Box className={classes.header}>
        <Box className={classes.titleSection}>
          <Typography variant="h4">
            Quản Lý Metadata & Lời Bài Hát
          </Typography>
          <Typography variant="body2" color="textSecondary">
            Giám sát, chuẩn hóa siêu dữ liệu thư viện nhạc và đồng bộ lời bài hát chạy chữ tự động.
          </Typography>
        </Box>
        <Button
          variant="contained"
          color="primary"
          className={classes.btn}
          onClick={fetchStats}
          disabled={loadingStats || activeJob}
          startIcon={loadingStats ? <CircularProgress size={16} color="inherit" /> : <RefreshIcon />}
        >
          {loadingStats ? 'Đang Tải...' : 'Làm Mới Thống Kê'}
        </Button>
      </Box>

      {/* Statistics Cards */}
      <Grid container spacing={3} style={{ marginBottom: 40 }}>
        <Grid item xs={12} sm={6} md={3}>
          <Card className={classes.card}>
            <CardContent>
              <Box className={classes.iconBox} style={{ color: '#ff5722', backgroundColor: '#ff572218' }}>
                <PeopleIcon />
              </Box>
              <Typography color="textSecondary" variant="subtitle2" style={{ fontWeight: 600 }}>
                Ca Sĩ Thiếu Biography
              </Typography>
              <Typography variant="h4" className={classes.statValue}>
                {loadingStats ? <CircularProgress size={20} /> : stats?.artists?.missingBios || 0}
              </Typography>
              <Typography variant="caption" color="textSecondary">
                Trên tổng số {stats?.artists?.total || 0} ca sĩ
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card className={classes.card}>
            <CardContent>
              <Box className={classes.iconBox} style={{ color: '#3f51b5', backgroundColor: '#3f51b518' }}>
                <AlbumIcon />
              </Box>
              <Typography color="textSecondary" variant="subtitle2" style={{ fontWeight: 600 }}>
                Album Thiếu Mô Tả
              </Typography>
              <Typography variant="h4" className={classes.statValue}>
                {loadingStats ? <CircularProgress size={20} /> : stats?.albums?.missingDesc || 0}
              </Typography>
              <Typography variant="caption" color="textSecondary">
                Trên tổng số {stats?.albums?.total || 0} album
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card className={classes.card}>
            <CardContent>
              <Box className={classes.iconBox} style={{ color: '#9c27b0', backgroundColor: '#9c27b018' }}>
                <AlbumIcon />
              </Box>
              <Typography color="textSecondary" variant="subtitle2" style={{ fontWeight: 600 }}>
                Album Thiếu Năm
              </Typography>
              <Typography variant="h4" className={classes.statValue}>
                {loadingStats ? <CircularProgress size={20} /> : stats?.albums?.missingYear || 0}
              </Typography>
              <Typography variant="caption" color="textSecondary">
                Cần cập nhật ngày phát hành
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card className={classes.card}>
            <CardContent>
              <Box className={classes.iconBox} style={{ color: '#e91e63', backgroundColor: '#e91e6318' }}>
                <MusicNoteIcon />
              </Box>
              <Typography color="textSecondary" variant="subtitle2" style={{ fontWeight: 600 }}>
                Bài Hát Thiếu Lời Nhạc
              </Typography>
              <Typography variant="h4" className={classes.statValue}>
                {loadingStats ? <CircularProgress size={20} /> : stats?.songs?.missingLyrics || 0}
              </Typography>
              <Typography variant="caption" color="textSecondary">
                Trên tổng số {stats?.songs?.total || 0} bài hát
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Progress Block */}
      {jobProgress && (
        <Card className={classes.actionCard} style={{ marginBottom: 40, borderLeft: '6px solid #4caf50' }}>
          <CardContent style={{ padding: 0 }}>
            <Typography variant="h6" style={{ fontWeight: 800, marginBottom: 16 }}>
              Tiến Trình Tự Động Sửa Thẻ Hàng Loạt (ID: #{jobProgress.id})
            </Typography>
            <Grid container spacing={2} alignItems="center">
              <Grid item xs={12} sm={8}>
                <LinearProgress
                  variant="determinate"
                  value={getProgressPercent()}
                  className={classes.progressBar}
                  color={jobProgress.status === 'failed' ? 'secondary' : 'primary'}
                />
              </Grid>
              <Grid item xs={12} sm={4}>
                <Typography variant="subtitle1" style={{ fontWeight: 700 }} align="right">
                  {getProgressPercent()}% ({jobProgress.processed} / {jobProgress.total})
                </Typography>
              </Grid>
            </Grid>
            <Box style={{ display: 'flex', gap: 24, marginTop: 16, flexWrap: 'wrap' }}>
              <Typography variant="body2">
                <strong>Đang xử lý:</strong> <span style={{ textTransform: 'uppercase', color: '#3f51b5', fontWeight: 'bold' }}>{jobProgress.type}</span>
              </Typography>
              <Typography variant="body2" style={{ color: '#4caf50' }}>
                <strong>Đã cập nhật:</strong> {jobProgress.updated} mục
              </Typography>
              <Typography variant="body2">
                <strong>Trạng thái:</strong>{' '}
                <span
                  style={{
                    color: jobProgress.status === 'running' ? '#ff9800' : '#4caf50',
                    fontWeight: 'bold',
                  }}
                >
                  {jobProgress.status === 'running' ? 'Đang chạy...' : 'Đã hoàn thành'}
                </span>
              </Typography>
            </Box>

            {jobProgress.errors && jobProgress.errors.length > 0 && (
              <Box style={{ marginTop: 20 }}>
                <Typography variant="subtitle2" color="secondary" style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 700 }}>
                  <ErrorIcon fontSize="small" /> Nhật ký lỗi ({jobProgress.errors.length}):
                </Typography>
                <div className={classes.logBox} ref={logBoxRef}>
                  {jobProgress.errors.map((err, idx) => (
                    <div key={idx} style={{ marginBottom: 4 }}>
                      <span style={{ color: '#ff2255' }}>[{idx + 1}]</span> {err}
                    </div>
                  ))}
                </div>
              </Box>
            )}
          </CardContent>
        </Card>
      )}

      {/* Control Actions Grid */}
      <Typography variant="h5" style={{ fontWeight: 800, marginBottom: 20, color: theme => theme.palette.text.primary }}>
        Bảng Điều Khiển Tự Động Sửa Thẻ Hàng Loạt
      </Typography>
      <Grid container spacing={3}>
        <Grid item xs={12} sm={6} md={3}>
          <Card className={classes.actionCard}>
            <Box style={{ marginBottom: 20 }}>
              <Typography variant="h6" style={{ fontWeight: 700, marginBottom: 8 }}>
                Sửa Lời Nhạc
              </Typography>
              <Typography variant="body2" color="textSecondary">
                Quét các bài hát thiếu lời nhạc và tự động tải lời (đồng bộ/timed hoặc tĩnh) từ kho dữ liệu LrcLib.
              </Typography>
            </Box>
            <Button
              variant="contained"
              color="primary"
              className={classes.btn}
              fullWidth
              startIcon={<BuildIcon />}
              onClick={() => triggerAutoFix('lyrics')}
              disabled={activeJob}
            >
              Chạy Auto-Fix Lời Nhạc
            </Button>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card className={classes.actionCard}>
            <Box style={{ marginBottom: 20 }}>
              <Typography variant="h6" style={{ fontWeight: 700, marginBottom: 8 }}>
                Sửa Ca Sĩ
              </Typography>
              <Typography variant="body2" color="textSecondary">
                Khớp ca sĩ trên MusicBrainz và tải tiểu sử chi tiết từ Last.fm để tự động bổ sung thông tin thiếu.
              </Typography>
            </Box>
            <Button
              variant="contained"
              color="primary"
              className={classes.btn}
              fullWidth
              startIcon={<BuildIcon />}
              onClick={() => triggerAutoFix('artists')}
              disabled={activeJob}
            >
              Chạy Auto-Fix Ca Sĩ
            </Button>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card className={classes.actionCard}>
            <Box style={{ marginBottom: 20 }}>
              <Typography variant="h6" style={{ fontWeight: 700, marginBottom: 8 }}>
                Sửa Album
              </Typography>
              <Typography variant="body2" color="textSecondary">
                Khớp album để kéo năm phát hành chính xác từ MusicBrainz và tải mô tả album từ Last.fm.
              </Typography>
            </Box>
            <Button
              variant="contained"
              color="primary"
              className={classes.btn}
              fullWidth
              startIcon={<BuildIcon />}
              onClick={() => triggerAutoFix('albums')}
              disabled={activeJob}
            >
              Chạy Auto-Fix Album
            </Button>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card className={classes.allActionsCard}>
            <Box style={{ marginBottom: 20 }}>
              <Typography variant="h6" style={{ fontWeight: 700, marginBottom: 8, color: '#e91e63' }}>
                Sửa Tất Cả Lỗi Thẻ
              </Typography>
              <Typography variant="body2" color="textSecondary">
                Thực hiện quét toàn diện và tự động sửa mọi thông tin (lời nhạc, ca sĩ, album) còn thiếu trong một luồng duy nhất.
              </Typography>
            </Box>
            <Button
              variant="contained"
              color="secondary"
              className={classes.btn}
              fullWidth
              startIcon={<BuildIcon />}
              onClick={() => triggerAutoFix('all')}
              disabled={activeJob}
            >
              Sửa Toàn Bộ Lỗi Thẻ
            </Button>
          </Card>
        </Grid>
      </Grid>
    </Container>
  )
}

export default MetadataDashboard
