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
import { httpClient } from '../dataProvider'
import { APP_NAME } from '../consts'

const useStyles = makeStyles((theme) => ({
  root: {
    padding: theme.spacing(3),
    backgroundColor: '#0A0A0A',
    minHeight: '80vh',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: theme.spacing(4),
  },
  titleContainer: {
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(1.5),
  },
  titleIcon: {
    color: '#C5A880',
    fontSize: '2rem',
  },
  titleText: {
    fontWeight: 700,
    color: '#F1E5AC',
    textTransform: 'uppercase',
    letterSpacing: '1.5px',
  },
  refreshButton: {
    color: '#C5A880',
    '&:hover': {
      backgroundColor: 'rgba(197, 168, 128, 0.08)',
      transform: 'rotate(180deg)',
    },
    transition: 'transform 0.4s ease, background-color 0.2s',
  },
  card: {
    backgroundColor: '#121212',
    border: '1px solid rgba(197, 168, 128, 0.12)',
    borderRadius: '12px',
    boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.4)',
    height: '100%',
    transition: 'transform 0.3s ease, border-color 0.3s ease',
    '&:hover': {
      transform: 'translateY(-4px)',
      borderColor: 'rgba(197, 168, 128, 0.24)',
    },
  },
  cardHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(1.5),
    marginBottom: theme.spacing(2),
  },
  cardIcon: {
    color: '#C5A880',
    fontSize: '1.75rem',
  },
  cardTitle: {
    fontWeight: 600,
    color: '#C5A880',
    textTransform: 'uppercase',
    fontSize: '0.85rem',
    letterSpacing: '0.8px',
  },
  metricValue: {
    fontWeight: 700,
    color: '#FFFFFF',
    marginTop: theme.spacing(1),
  },
  progressContainer: {
    marginTop: theme.spacing(2.5),
    marginBottom: theme.spacing(1),
  },
  progressBar: {
    height: 8,
    borderRadius: 4,
    backgroundColor: 'rgba(255, 255, 255, 0.08)',
    '& .MuiLinearProgress-barColorPrimary': {
      backgroundColor: '#C5A880',
    },
  },
  chipContainer: {
    display: 'flex',
    gap: theme.spacing(1),
    marginTop: theme.spacing(2),
    flexWrap: 'wrap',
  },
  chip: {
    backgroundColor: 'rgba(197, 168, 128, 0.04)',
    border: '1px solid rgba(197, 168, 128, 0.12)',
    borderRadius: '16px',
    padding: theme.spacing(0.5, 1.5),
    color: '#C5A880',
    fontSize: '0.75rem',
    fontWeight: 500,
  },
  loadingContainer: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    height: '40vh',
  },
  divider: {
    backgroundColor: 'rgba(197, 168, 128, 0.08)',
    margin: theme.spacing(2, 0),
  },
  detailRow: {
    display: 'flex',
    justifyContent: 'space-between',
    marginBottom: theme.spacing(1),
    '& :first-child': {
      color: '#8A8A82',
      fontSize: '0.85rem',
    },
    '& :last-child': {
      color: '#FFFFFF',
      fontSize: '0.85rem',
      fontWeight: 500,
    },
  },
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
    const interval = setInterval(() => fetchStatus(), 5000)
    return () => clearInterval(interval)
  }, [])

  if (loading) {
    return (
      <div className={classes.loadingContainer}>
        <CircularProgress style={{ color: '#C5A880' }} />
      </div>
    )
  }

  return (
    <div className={classes.root}>
      <Title title={APP_NAME + ' - ' + translate('menu.serverStatus', { _: 'Theo dõi Server VM' })} />
      
      <div className={classes.header}>
        <div className={classes.titleContainer}>
          <DnsIcon className={classes.titleIcon} />
          <Typography variant="h5" className={classes.titleText}>
            Hệ Thống & Máy Chủ VM
          </Typography>
        </div>
        <Tooltip title="Làm mới tức thì">
          <IconButton 
            className={classes.refreshButton} 
            onClick={() => fetchStatus(true)}
            disabled={refreshing}
          >
            {refreshing ? (
              <CircularProgress size={24} style={{ color: '#C5A880' }} />
            ) : (
              <RefreshIcon />
            )}
          </IconButton>
        </Tooltip>
      </div>

      <Grid container spacing={3}>
        {/* Card 1: CPU */}
        <Grid item xs={12} md={6}>
          <Card className={classes.card}>
            <CardContent>
              <div className={classes.cardHeader}>
                <SpeedIcon className={classes.cardIcon} />
                <Typography variant="h6" className={classes.cardTitle}>
                  Bộ Vi Xử Lý (CPU)
                </Typography>
              </div>
              <Typography variant="body2" style={{ color: '#8A8A82' }}>
                {data?.cpu?.model || 'Generic CPU'}
              </Typography>
              <Typography variant="h3" className={classes.metricValue}>
                {data?.cpu?.cores || 1} <span style={{ fontSize: '1.2rem', fontWeight: 500, color: '#8A8A82' }}>Cores</span>
              </Typography>

              <div className={classes.progressContainer}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                  <Typography variant="body2" style={{ color: '#8A8A82' }}>Tải hệ thống (1 phút)</Typography>
                  <Typography variant="body2" style={{ color: '#C5A880', fontWeight: 600 }}>
                    {((data?.cpu?.load1m || 0) * 100).toFixed(0)}%
                  </Typography>
                </div>
                <LinearProgress 
                  variant="determinate" 
                  value={Math.min((data?.cpu?.load1m || 0) * 100, 100)} 
                  className={classes.progressBar}
                />
              </div>

              <div className={classes.chipContainer}>
                <span className={classes.chip}>Load 1m: {data?.cpu?.load1m?.toFixed(2) || '0.00'}</span>
                <span className={classes.chip}>Load 5m: {data?.cpu?.load5m?.toFixed(2) || '0.00'}</span>
                <span className={classes.chip}>Load 15m: {data?.cpu?.load15m?.toFixed(2) || '0.00'}</span>
              </div>
            </CardContent>
          </Card>
        </Grid>

        {/* Card 2: Memory (RAM) */}
        <Grid item xs={12} md={6}>
          <Card className={classes.card}>
            <CardContent>
              <div className={classes.cardHeader}>
                <MemoryIcon className={classes.cardIcon} />
                <Typography variant="h6" className={classes.cardTitle}>
                  Bộ Nhớ (RAM)
                </Typography>
              </div>
              <Typography variant="body2" style={{ color: '#8A8A82' }}>
                Đã dùng {formatBytes(data?.memory?.used)} trên tổng {formatBytes(data?.memory?.total)}
              </Typography>
              <Typography variant="h3" className={classes.metricValue}>
                {data?.memory?.percentage?.toFixed(1) || '0.0'}<span style={{ fontSize: '1.5rem', color: '#C5A880' }}>%</span>
              </Typography>

              <div className={classes.progressContainer}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                  <Typography variant="body2" style={{ color: '#8A8A82' }}>Sử dụng bộ nhớ</Typography>
                  <Typography variant="body2" style={{ color: '#C5A880', fontWeight: 600 }}>
                    {data?.memory?.percentage?.toFixed(1) || '0.0'}%
                  </Typography>
                </div>
                <LinearProgress 
                  variant="determinate" 
                  value={data?.memory?.percentage || 0} 
                  className={classes.progressBar}
                />
              </div>

              <div className={classes.chipContainer}>
                <span className={classes.chip}>Trống: {formatBytes(data?.memory?.available)}</span>
              </div>
            </CardContent>
          </Card>
        </Grid>

        {/* Card 3: Disk (Storage) */}
        <Grid item xs={12} md={6}>
          <Card className={classes.card}>
            <CardContent>
              <div className={classes.cardHeader}>
                <StorageIcon className={classes.cardIcon} />
                <Typography variant="h6" className={classes.cardTitle}>
                  Lưu Trữ (Disk sda1)
                </Typography>
              </div>
              <Typography variant="body2" style={{ color: '#8A8A82' }}>
                Đã dùng {formatBytes(data?.disk?.used)} trên tổng {formatBytes(data?.disk?.total)}
              </Typography>
              <Typography variant="h3" className={classes.metricValue}>
                {data?.disk?.percentage?.toFixed(1) || '0.0'}<span style={{ fontSize: '1.5rem', color: '#C5A880' }}>%</span>
              </Typography>

              <div className={classes.progressContainer}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                  <Typography variant="body2" style={{ color: '#8A8A82' }}>Sử dụng lưu trữ</Typography>
                  <Typography variant="body2" style={{ color: '#C5A880', fontWeight: 600 }}>
                    {data?.disk?.percentage?.toFixed(1) || '0.0'}%
                  </Typography>
                </div>
                <LinearProgress 
                  variant="determinate" 
                  value={data?.disk?.percentage || 0} 
                  className={classes.progressBar}
                />
              </div>

              <div className={classes.chipContainer}>
                <span className={classes.chip}>Khả dụng: {formatBytes(data?.disk?.available)}</span>
              </div>
            </CardContent>
          </Card>
        </Grid>

        {/* Card 4: Uptime & App Info */}
        <Grid item xs={12} md={6}>
          <Card className={classes.card}>
            <CardContent style={{ height: '100%' }}>
              <div className={classes.cardHeader}>
                <AccessTimeIcon className={classes.cardIcon} />
                <Typography variant="h6" className={classes.cardTitle}>
                  Thông Tin Ứng Dụng
                </Typography>
              </div>
              
              <div style={{ marginTop: 12 }}>
                <div className={classes.detailRow}>
                  <Typography variant="body2">Thời gian hoạt động</Typography>
                  <Typography variant="body2" style={{ color: '#C5A880' }}>
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
                  <Typography variant="body2">Số Goroutines hoạt động</Typography>
                  <Typography variant="body2">{data?.app?.numGoroutines || 0}</Typography>
                </div>
                <Divider className={classes.divider} />

                <div className={classes.detailRow}>
                  <Typography variant="body2">Go Allocated Heap / Sys Memory</Typography>
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
