import React, { useState, useEffect } from 'react'
import { Title, useTranslate } from 'react-admin'
import {
  Card,
  CardContent,
  Grid,
  Typography,
  LinearProgress,
  IconButton,
  Tooltip,
  CircularProgress,
  Box,
  Divider,
} from '@material-ui/core'
import { makeStyles } from '@material-ui/core/styles'
import RefreshIcon from '@material-ui/icons/Refresh'
import DnsIcon from '@material-ui/icons/Dns'
import MemoryIcon from '@material-ui/icons/Memory'
import StorageIcon from '@material-ui/icons/Storage'
import AccessTimeIcon from '@material-ui/icons/AccessTime'
import SpeedIcon from '@material-ui/icons/Speed'
import FiberManualRecordIcon from '@material-ui/icons/FiberManualRecord'
import { httpClient } from '../dataProvider'
import { APP_NAME } from '../consts'

const useStyles = makeStyles((theme) => ({
  root: {
    padding: theme.spacing(4),
    background: 'radial-gradient(circle at 50% 50%, #141419 0%, #08080a 100%)',
    minHeight: '85vh',
    [theme.breakpoints.down('xs')]: {
      padding: theme.spacing(2),
    }
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: theme.spacing(4),
    borderBottom: '1px solid rgba(255, 255, 255, 0.05)',
    paddingBottom: theme.spacing(2.5),
  },
  titleContainer: {
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(2),
  },
  titleIcon: {
    color: '#00e5ff',
    fontSize: '2.4rem',
    filter: 'drop-shadow(0 0 10px rgba(0, 229, 255, 0.3))',
    animation: '$pulseGlow 3s infinite ease-in-out',
  },
  titleText: {
    fontWeight: 800,
    background: 'linear-gradient(135deg, #ffffff 0%, #94a3b8 100%)',
    WebkitBackgroundClip: 'text',
    WebkitTextFillColor: 'transparent',
    textTransform: 'uppercase',
    letterSpacing: '1.8px',
    fontSize: '1.6rem',
    [theme.breakpoints.down('xs')]: {
      fontSize: '1.15rem',
    }
  },
  statusIndicator: {
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(1),
    marginLeft: theme.spacing(2),
    padding: '4px 12px',
    background: 'rgba(0, 229, 255, 0.05)',
    border: '1px solid rgba(0, 229, 255, 0.15)',
    borderRadius: 20,
    [theme.breakpoints.down('xs')]: {
      display: 'none',
    }
  },
  statusDot: {
    color: '#00ff66',
    fontSize: '0.75rem',
    animation: '$pulseDot 1.8s infinite ease-in-out',
  },
  statusText: {
    color: '#00ff66',
    fontSize: '0.75rem',
    fontWeight: 700,
    textTransform: 'uppercase',
    letterSpacing: '1px',
  },
  refreshButton: {
    background: 'rgba(255, 255, 255, 0.02)',
    border: '1px solid rgba(255, 255, 255, 0.06)',
    backdropFilter: 'blur(10px)',
    color: '#00e5ff',
    padding: theme.spacing(1.2),
    '&:hover': {
      backgroundColor: 'rgba(0, 229, 255, 0.08)',
      borderColor: '#00e5ff',
      boxShadow: '0 0 15px rgba(0, 229, 255, 0.25)',
      transform: 'rotate(180deg)',
    },
    transition: 'all 0.5s cubic-bezier(0.4, 0, 0.2, 1)',
  },
  cardCpu: {
    background: 'rgba(30, 41, 59, 0.15) !important',
    backdropFilter: 'blur(20px)',
    border: '1px solid rgba(59, 130, 246, 0.15) !important',
    borderRadius: '24px !important',
    boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.2)',
    height: '100%',
    transition: 'all 0.4s cubic-bezier(0.4, 0, 0.2, 1)',
    '&:hover': {
      transform: 'translateY(-6px)',
      borderColor: '#3b82f6 !important',
      boxShadow: '0 12px 40px rgba(59, 130, 246, 0.22)',
    },
  },
  cardRam: {
    background: 'rgba(30, 41, 59, 0.15) !important',
    backdropFilter: 'blur(20px)',
    border: '1px solid rgba(16, 185, 129, 0.15) !important',
    borderRadius: '24px !important',
    boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.2)',
    height: '100%',
    transition: 'all 0.4s cubic-bezier(0.4, 0, 0.2, 1)',
    '&:hover': {
      transform: 'translateY(-6px)',
      borderColor: '#10b981 !important',
      boxShadow: '0 12px 40px rgba(16, 185, 129, 0.22)',
    },
  },
  cardDisk: {
    background: 'rgba(30, 41, 59, 0.15) !important',
    backdropFilter: 'blur(20px)',
    border: '1px solid rgba(245, 158, 11, 0.15) !important',
    borderRadius: '24px !important',
    boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.2)',
    height: '100%',
    transition: 'all 0.4s cubic-bezier(0.4, 0, 0.2, 1)',
    '&:hover': {
      transform: 'translateY(-6px)',
      borderColor: '#f59e0b !important',
      boxShadow: '0 12px 40px rgba(245, 158, 11, 0.22)',
    },
  },
  cardApp: {
    background: 'rgba(30, 41, 59, 0.15) !important',
    backdropFilter: 'blur(20px)',
    border: '1px solid rgba(139, 92, 246, 0.15) !important',
    borderRadius: '24px !important',
    boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.2)',
    height: '100%',
    transition: 'all 0.4s cubic-bezier(0.4, 0, 0.2, 1)',
    '&:hover': {
      transform: 'translateY(-6px)',
      borderColor: '#8b5cf6 !important',
      boxShadow: '0 12px 40px rgba(139, 92, 246, 0.22)',
    },
  },
  cardHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(1.5),
    marginBottom: theme.spacing(2),
  },
  cardIconCpu: {
    color: '#3b82f6',
    fontSize: '1.8rem',
    filter: 'drop-shadow(0 0 8px rgba(59, 130, 246, 0.3))',
  },
  cardIconRam: {
    color: '#10b981',
    fontSize: '1.8rem',
    filter: 'drop-shadow(0 0 8px rgba(16, 185, 129, 0.3))',
  },
  cardIconDisk: {
    color: '#f59e0b',
    fontSize: '1.8rem',
    filter: 'drop-shadow(0 0 8px rgba(245, 158, 11, 0.3))',
  },
  cardIconApp: {
    color: '#8b5cf6',
    fontSize: '1.8rem',
    filter: 'drop-shadow(0 0 8px rgba(139, 92, 246, 0.3))',
  },
  cardTitle: {
    fontWeight: 700,
    color: '#ffffff',
    textTransform: 'uppercase',
    fontSize: '0.85rem',
    letterSpacing: '1px',
    opacity: 0.9,
  },
  metricValue: {
    fontWeight: 800,
    color: '#ffffff',
    marginTop: theme.spacing(1),
    letterSpacing: '-1px',
  },
  progressContainer: {
    marginTop: theme.spacing(3),
    marginBottom: theme.spacing(1),
  },
  progressBarCpu: {
    height: 10,
    borderRadius: 5,
    backgroundColor: 'rgba(255, 255, 255, 0.03)',
    '& .MuiLinearProgress-barColorPrimary': {
      background: 'linear-gradient(90deg, #3b82f6 0%, #60a5fa 100%)',
      borderRadius: 5,
    },
  },
  progressBarRam: {
    height: 10,
    borderRadius: 5,
    backgroundColor: 'rgba(255, 255, 255, 0.03)',
    '& .MuiLinearProgress-barColorPrimary': {
      background: 'linear-gradient(90deg, #10b981 0%, #34d399 100%)',
      borderRadius: 5,
    },
  },
  progressBarDisk: {
    height: 10,
    borderRadius: 5,
    backgroundColor: 'rgba(255, 255, 255, 0.03)',
    '& .MuiLinearProgress-barColorPrimary': {
      background: 'linear-gradient(90deg, #f59e0b 0%, #fbbf24 100%)',
      borderRadius: 5,
    },
  },
  chipContainer: {
    display: 'flex',
    gap: theme.spacing(1),
    marginTop: theme.spacing(2.5),
    flexWrap: 'wrap',
  },
  chipCpu: {
    backgroundColor: 'rgba(59, 130, 246, 0.05)',
    border: '1px solid rgba(59, 130, 246, 0.15)',
    borderRadius: '12px',
    padding: '4px 12px',
    color: '#93c5fd',
    fontSize: '0.78rem',
    fontWeight: 600,
  },
  chipRam: {
    backgroundColor: 'rgba(16, 185, 129, 0.05)',
    border: '1px solid rgba(16, 185, 129, 0.15)',
    borderRadius: '12px',
    padding: '4px 12px',
    color: '#a7f3d0',
    fontSize: '0.78rem',
    fontWeight: 600,
  },
  chipDisk: {
    backgroundColor: 'rgba(245, 158, 11, 0.05)',
    border: '1px solid rgba(245, 158, 11, 0.15)',
    borderRadius: '12px',
    padding: '4px 12px',
    color: '#fde68a',
    fontSize: '0.78rem',
    fontWeight: 600,
  },
  loadingContainer: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    height: '60vh',
    background: '#08080a',
  },
  divider: {
    backgroundColor: 'rgba(255, 255, 255, 0.04)',
    margin: theme.spacing(1.8, 0),
  },
  detailRow: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: theme.spacing(1),
    '& :first-child': {
      color: '#94a3b8',
      fontSize: '0.85rem',
      fontWeight: 500,
    },
    '& :last-child': {
      color: '#ffffff',
      fontSize: '0.85rem',
      fontWeight: 600,
    },
  },
  '@keyframes pulseGlow': {
    '0%, 100%': {
      filter: 'drop-shadow(0 0 6px rgba(0, 229, 255, 0.2))',
    },
    '50%': {
      filter: 'drop-shadow(0 0 15px rgba(0, 229, 255, 0.45))',
    }
  },
  '@keyframes pulseDot': {
    '0%, 100%': {
      transform: 'scale(1)',
      opacity: 1,
    },
    '50%': {
      transform: 'scale(1.3)',
      opacity: 0.35,
    }
  }
}))

const formatBytes = (bytes, decimals = 2) => {
  if (!bytes || bytes === 0) return '0 Bytes'
  const k = 1024
  const dm = decimals < 0 ? 0 : decimals
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i]
}

const formatUptime = (seconds) => {
  if (!seconds) return '0 giây'
  const d = Math.floor(seconds / (3600 * 24))
  const h = Math.floor((seconds % (3600 * 24)) / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)

  const parts = []
  if (d > 0) parts.push(`${d} ngày`)
  if (h > 0) parts.push(`${h} giờ`)
  if (m > 0) parts.push(`${m} phút`)
  if (parts.length === 0 || s > 0) parts.push(`${s} giây`)

  return parts.join(', ')
}

const ServerStatus = () => {
  const translate = useTranslate()
  const classes = useStyles()
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)

  const fetchStatus = async (isManual = false) => {
    if (isManual) setRefreshing(true)
    try {
      const { json } = await httpClient('/api/server/status')
      setData(json)
    } catch (err) {
      console.error('Failed to fetch server status:', err)
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }

  useEffect(() => {
    fetchStatus()
    const interval = setInterval(() => fetchStatus(), 4000)
    return () => clearInterval(interval)
  }, [])

  if (loading) {
    return (
      <div className={classes.loadingContainer}>
        <CircularProgress style={{ color: '#00e5ff' }} />
      </div>
    )
  }

  return (
    <div className={classes.root}>
      <Title title={APP_NAME + ' - ' + translate('menu.serverStatus', { _: 'Giám sát Server VM' })} />
      
      <div className={classes.header}>
        <div className={classes.titleContainer}>
          <DnsIcon className={classes.titleIcon} />
          <Typography variant="h5" className={classes.titleText}>
            Hệ Thống & Máy Chủ VM
          </Typography>
          <div className={classes.statusIndicator}>
            <FiberManualRecordIcon className={classes.statusDot} />
            <span className={classes.statusText}>Trực tuyến</span>
          </div>
        </div>
        <Tooltip title="Cập nhật tức thì">
          <IconButton 
            className={classes.refreshButton} 
            onClick={() => fetchStatus(true)}
            disabled={refreshing}
          >
            {refreshing ? (
              <CircularProgress size={22} style={{ color: '#00e5ff' }} />
            ) : (
              <RefreshIcon />
            )}
          </IconButton>
        </Tooltip>
      </div>

      <Grid container spacing={4}>
        {/* Card 1: CPU */}
        <Grid item xs={12} md={6}>
          <Card className={classes.cardCpu}>
            <CardContent>
              <div className={classes.cardHeader}>
                <SpeedIcon className={classes.cardIconCpu} />
                <Typography variant="h6" className={classes.cardTitle}>
                  Bộ Vi Xử Lý (CPU)
                </Typography>
              </div>
              <Typography variant="body2" style={{ color: '#94a3b8', fontSize: '0.85rem', minHeight: 20 }}>
                {data?.cpu?.model || 'Generic Intel/AMD Processor'}
              </Typography>
              <Typography variant="h3" className={classes.metricValue}>
                {data?.cpu?.cores || 1} <span style={{ fontSize: '1.2rem', fontWeight: 600, color: '#94a3b8' }}>Cores</span>
              </Typography>

              <div className={classes.progressContainer}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                  <Typography variant="body2" style={{ color: '#94a3b8', fontSize: '0.85rem' }}>Tải hệ thống (1 phút)</Typography>
                  <Typography variant="body2" style={{ color: '#3b82f6', fontWeight: 700, fontSize: '0.85rem' }}>
                    {((data?.cpu?.load1m || 0) * 100).toFixed(0)}%
                  </Typography>
                </div>
                <LinearProgress 
                  variant="determinate" 
                  value={Math.min((data?.cpu?.load1m || 0) * 100, 100)} 
                  className={classes.progressBarCpu}
                />
              </div>

              <div className={classes.chipContainer}>
                <span className={classes.chipCpu}>Load 1m: {data?.cpu?.load1m?.toFixed(2) || '0.00'}</span>
                <span className={classes.chipCpu}>Load 5m: {data?.cpu?.load5m?.toFixed(2) || '0.00'}</span>
                <span className={classes.chipCpu}>Load 15m: {data?.cpu?.load15m?.toFixed(2) || '0.00'}</span>
              </div>
            </CardContent>
          </Card>
        </Grid>

        {/* Card 2: Memory (RAM) */}
        <Grid item xs={12} md={6}>
          <Card className={classes.cardRam}>
            <CardContent>
              <div className={classes.cardHeader}>
                <MemoryIcon className={classes.cardIconRam} />
                <Typography variant="h6" className={classes.cardTitle}>
                  Bộ Nhớ (RAM)
                </Typography>
              </div>
              <Typography variant="body2" style={{ color: '#94a3b8', fontSize: '0.85rem', minHeight: 20 }}>
                Đã dùng {formatBytes(data?.memory?.used)} / {formatBytes(data?.memory?.total)}
              </Typography>
              <Typography variant="h3" className={classes.metricValue}>
                {data?.memory?.percentage?.toFixed(1) || '0.0'}<span style={{ fontSize: '1.5rem', color: '#10b981' }}>%</span>
              </Typography>

              <div className={classes.progressContainer}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                  <Typography variant="body2" style={{ color: '#94a3b8', fontSize: '0.85rem' }}>Hiệu suất sử dụng</Typography>
                  <Typography variant="body2" style={{ color: '#10b981', fontWeight: 700, fontSize: '0.85rem' }}>
                    {data?.memory?.percentage?.toFixed(1) || '0.0'}%
                  </Typography>
                </div>
                <LinearProgress 
                  variant="determinate" 
                  value={data?.memory?.percentage || 0} 
                  className={classes.progressBarRam}
                />
              </div>

              <div className={classes.chipContainer}>
                <span className={classes.chipRam}>Còn trống: {formatBytes(data?.memory?.available)}</span>
              </div>
            </CardContent>
          </Card>
        </Grid>

        {/* Card 3: Disk (Storage) */}
        <Grid item xs={12} md={6}>
          <Card className={classes.cardDisk}>
            <CardContent>
              <div className={classes.cardHeader}>
                <StorageIcon className={classes.cardIconDisk} />
                <Typography variant="h6" className={classes.cardTitle}>
                  Lưu Trữ (Disk Storage)
                </Typography>
              </div>
              <Typography variant="body2" style={{ color: '#94a3b8', fontSize: '0.85rem', minHeight: 20 }}>
                Không gian đã dùng {formatBytes(data?.disk?.used)} / {formatBytes(data?.disk?.total)}
              </Typography>
              <Typography variant="h3" className={classes.metricValue}>
                {data?.disk?.percentage?.toFixed(1) || '0.0'}<span style={{ fontSize: '1.5rem', color: '#f59e0b' }}>%</span>
              </Typography>

              <div className={classes.progressContainer}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                  <Typography variant="body2" style={{ color: '#94a3b8', fontSize: '0.85rem' }}>Bộ nhớ lưu trữ</Typography>
                  <Typography variant="body2" style={{ color: '#f59e0b', fontWeight: 700, fontSize: '0.85rem' }}>
                    {data?.disk?.percentage?.toFixed(1) || '0.0'}%
                  </Typography>
                </div>
                <LinearProgress 
                  variant="determinate" 
                  value={data?.disk?.percentage || 0} 
                  className={classes.progressBarDisk}
                />
              </div>

              <div className={classes.chipContainer}>
                <span className={classes.chipDisk}>Khả dụng: {formatBytes(data?.disk?.available)}</span>
              </div>
            </CardContent>
          </Card>
        </Grid>

        {/* Card 4: Uptime & App Info */}
        <Grid item xs={12} md={6}>
          <Card className={classes.cardApp}>
            <CardContent style={{ height: '100%' }}>
              <div className={classes.cardHeader}>
                <AccessTimeIcon className={classes.cardIconApp} />
                <Typography variant="h6" className={classes.cardTitle}>
                  Thông Tin Ứng Dụng & Runtime
                </Typography>
              </div>
              
              <div style={{ marginTop: 16 }}>
                <div className={classes.detailRow}>
                  <Typography variant="body2">Thời gian hoạt động</Typography>
                  <Typography variant="body2" style={{ color: '#a78bfa' }}>
                    {formatUptime(data?.uptime)}
                  </Typography>
                </div>
                <Divider className={classes.divider} />
                
                <div className={classes.detailRow}>
                  <Typography variant="body2">Phiên bản Golang</Typography>
                  <Typography variant="body2">{data?.app?.goVersion || 'Unknown'}</Typography>
                </div>
                <Divider className={classes.divider} />

                <div className={classes.detailRow}>
                  <Typography variant="body2">Số Goroutines đang chạy</Typography>
                  <Typography variant="body2" style={{ color: '#8b5cf6' }}>{data?.app?.numGoroutines || 0}</Typography>
                </div>
                <Divider className={classes.divider} />

                <div className={classes.detailRow}>
                  <Typography variant="body2">Go Alloc Heap / Sys Memory</Typography>
                  <Typography variant="body2">
                    {formatBytes(data?.app?.allocBytes)} / {formatBytes(data?.app?.sysBytes)}
                  </Typography>
                </div>
              </div>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </div>
  )
}

export default ServerStatus
