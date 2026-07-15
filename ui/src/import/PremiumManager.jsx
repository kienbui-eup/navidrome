import React, { useState, useEffect } from 'react'
import { useNotify } from 'react-admin'
import {
  Card,
  CardContent,
  Box,
  Typography,
  Button,
  TextField,
  Grid,
  Chip,
  IconButton,
  Tooltip,
} from '@material-ui/core'
import { makeStyles } from '@material-ui/core/styles'
import CloudDoneIcon from '@material-ui/icons/CloudDone'
import CloudOffIcon from '@material-ui/icons/CloudOff'
import HelpOutlineIcon from '@material-ui/icons/HelpOutline'
import CheckCircleIcon from '@material-ui/icons/CheckCircle'
import SyncIcon from '@material-ui/icons/Sync'
import { httpClient } from '../dataProvider'
import { formatBytes } from '../utils'

const useStyles = makeStyles((theme) => ({
  root: {
    marginTop: theme.spacing(2),
  },
  providerCard: {
    background: 'rgba(255, 255, 255, 0.03)',
    border: '1px solid rgba(255, 255, 255, 0.08)',
    borderRadius: 8,
    padding: theme.spacing(2.5),
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'space-between',
    transition: 'all 0.3s ease',
    '&:hover': {
      border: '1px solid rgba(255, 255, 255, 0.15)',
      background: 'rgba(255, 255, 255, 0.05)',
      boxShadow: '0 8px 24px rgba(0,0,0,0.2)',
    },
  },
  statusSection: {
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(1),
    marginBottom: theme.spacing(2),
  },
  bookmarkletBtn: {
    background: 'linear-gradient(45deg, #1db954 30%, #1ed760 90%)',
    color: '#000',
    fontWeight: 'bold',
    textTransform: 'none',
    padding: '8px 20px',
    borderRadius: 24,
    boxShadow: '0 3px 12px rgba(29, 185, 84, 0.2)',
    cursor: 'grab',
    display: 'inline-block',
    textDecoration: 'none',
    textAlign: 'center',
    fontSize: '0.88rem',
    '&:hover': {
      boxShadow: '0 4px 20px rgba(29, 185, 84, 0.4)',
    },
    '&:active': {
      cursor: 'grabbing',
    },
  },
  bookmarkletInstructions: {
    background: 'rgba(0, 0, 0, 0.2)',
    border: '1px dashed rgba(255, 255, 255, 0.1)',
    borderRadius: 6,
    padding: theme.spacing(1.5),
    marginTop: theme.spacing(2),
    marginBottom: theme.spacing(2),
  },
  textarea: {
    fontFamily: 'monospace',
    fontSize: '0.75rem',
  },
  saveBtn: {
    marginTop: theme.spacing(1),
  },
}))

const PremiumManager = () => {
  const classes = useStyles()
  const notify = useNotify()
  const [status, setStatus] = useState(null)
  const [loading, setLoading] = useState(true)

  // Direct paste fields
  const [ytRaw, setYtRaw] = useState('')
  const [zingRaw, setZingRaw] = useState('')
  const [saving, setSaving] = useState(null)

  const loadStatus = () => {
    setLoading(true)
    httpClient('/api/import/cookies')
      .then(({ json }) => {
        setStatus(json)
      })
      .catch((err) => {
        notify('Không lấy được trạng thái tài khoản Premium: ' + (err.message || err), 'warning')
      })
      .finally(() => {
        setLoading(false)
      })
  }

  useEffect(() => {
    loadStatus()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const saveCookies = (provider, rawCookies) => {
    if (!rawCookies.trim()) {
      notify('Vui lòng nhập nội dung cookies trước!', 'warning')
      return
    }
    setSaving(provider)
    httpClient('/api/import/cookies', {
      method: 'POST',
      body: JSON.stringify({ provider, raw_cookies: rawCookies }),
    })
      .then(({ json }) => {
        if (json.status === 'ok') {
          notify(`Cập nhật Premium Cookie cho ${provider.toUpperCase()} thành công!`, 'info')
          if (provider === 'youtube') setYtRaw('')
          if (provider === 'zing') setZingRaw('')
          loadStatus()
        }
      })
      .catch((err) => {
        notify('Cập nhật thất bại: ' + (err.message || err), 'warning')
      })
      .finally(() => {
        setSaving(null)
      })
  }

  const makeBookmarklet = (provider) => {
    const token = localStorage.getItem('token') || ''
    const origin = window.location.origin
    const label = provider === 'youtube' ? 'YouTube Premium' : 'Zing MP3'
    const jsCode = `javascript:(function(){var cookies=document.cookie;if(!cookies){alert('Không tìm thấy cookie nào. Vui lòng đăng nhập trước!');return;}var btn=document.createElement('div');btn.style='position:fixed;top:20px;right:20px;z-index:999999;padding:14px 22px;background:#1db954;color:#000;font-family:system-ui,-apple-system,sans-serif;font-weight:bold;border-radius:12px;box-shadow:0 8px 32px rgba(0,0,0,0.45);cursor:pointer;transition:all 0.3s;border:1px solid rgba(255,255,255,0.25);';btn.innerHTML='⚡ Đang đồng bộ Premium với Navidrome...';document.body.appendChild(btn);fetch('${origin}/api/import/cookies',{method:'POST',headers:{'Content-Type':'application/json','X-VI-Authorization':'Bearer ${token}'},body:JSON.stringify({provider:'${provider}',raw_cookies:cookies})}).then(function(r){return r.json();}).then(function(data){if(data.status==='ok'){btn.style.background='#4caf50';btn.style.color='#fff';btn.innerHTML='✅ Đồng bộ ${label} Thành Công!';setTimeout(function(){btn.remove();},2500);}else{btn.style.background='#f44336';btn.style.color='#fff';btn.innerHTML='❌ Đồng bộ Thất Bại!';setTimeout(function(){btn.remove();},3000);}}).catch(function(err){btn.style.background='#f44336';btn.style.color='#fff';btn.innerHTML='❌ Lỗi kết nối máy chủ!';console.error(err);setTimeout(function(){btn.remove();},3000);});})();`
    return jsCode
  }

  const renderStatus = (provider) => {
    const s = status && status[provider]
    if (!s) return null

    if (s.exists) {
      return (
        <Box className={classes.statusSection}>
          <CheckCircleIcon style={{ color: '#4caf50' }} />
          <Box>
            <Typography variant="body2" style={{ fontWeight: 'bold', color: '#4caf50' }}>
              ĐÃ ĐỒNG BỘ PREMIUM
            </Typography>
            <Typography variant="caption" color="textSecondary" style={{ display: 'block' }}>
              Cập nhật: {new Date(s.updatedAt).toLocaleString()} • {formatBytes(s.size || 0)}
            </Typography>
          </Box>
        </Box>
      )
    }

    return (
      <Box className={classes.statusSection}>
        <CloudOffIcon style={{ color: '#aaa' }} />
        <Box>
          <Typography variant="body2" style={{ fontWeight: 'bold', color: '#aaa' }}>
            CHƯA ĐỒNG BỘ
          </Typography>
          <Typography variant="caption" color="textSecondary">
            Chưa có session cookies hoạt động.
          </Typography>
        </Box>
      </Box>
    )
  }

  return (
    <Box className={classes.root}>
      <Typography variant="h6" style={{ fontWeight: 'bold', marginBottom: 8 }}>
        Cấu hình Phiên Premium & Đồng bộ Tự động
      </Typography>
      <Typography variant="body2" color="textSecondary" style={{ marginBottom: 24 }}>
        Để tải nhạc chất lượng cao từ các nguồn hạn chế (như YouTube Premium, Zing MP3 Vip), 
        hệ thống cần phiên làm việc hoạt động (session cookies) từ tài duyệt của bạn. Hãy chọn một trong hai cách dưới đây.
      </Typography>

      <Grid container spacing={3}>
        {/* YouTube Section */}
        <Grid item xs={12} md={6}>
          <Box className={classes.providerCard}>
            <Box>
              <Typography variant="subtitle1" style={{ fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
                🎥 YouTube Premium / Music
                <Tooltip title="Đồng bộ Premium giúp bỏ qua màn hình chặn robot và mở khóa các luồng audio 256kbps/AAC chất lượng cao nhất!">
                  <IconButton size="small" style={{ color: '#aaa' }}><HelpOutlineIcon fontSize="small" /></IconButton>
                </Tooltip>
              </Typography>

              {renderStatus('youtube')}

              <Typography variant="body2" style={{ fontWeight: 'bold', marginTop: 16, marginBottom: 8 }}>
                Cách 1: Kéo & Thả Bookmarklet (Tự động 1-Click)
              </Typography>
              <Box className={classes.bookmarkletInstructions}>
                <Typography variant="caption" color="textSecondary" style={{ display: 'block', marginBottom: 12 }}>
                  1. Hãy kéo nút màu xanh dưới đây và <strong>thả vào thanh Dấu trang (Bookmark bar)</strong> của trình duyệt.<br />
                  2. Mở trang <a href="https://www.youtube.com" target="_blank" rel="noopener noreferrer" style={{ color: '#1db954', textDecoration: 'underline' }}>youtube.com</a> và đăng nhập tài khoản của bạn.<br />
                  3. <strong>Nhấp vào Dấu trang vừa kéo</strong>. Một thông báo đồng bộ sẽ hiện lên góc màn hình và nạp phiên tức thì!
                </Typography>
                <a
                  href={makeBookmarklet('youtube')}
                  className={classes.bookmarkletBtn}
                  onClick={(e) => e.preventDefault()}
                >
                  🟢 Đồng bộ YouTube Premium
                </a>
              </Box>

              <Typography variant="body2" style={{ fontWeight: 'bold', marginTop: 16, marginBottom: 8 }}>
                Cách 2: Nạp Cookies Thủ Công (Dành cho nhà phát triển)
              </Typography>
              <TextField
                multiline
                rows={3}
                variant="outlined"
                fullWidth
                placeholder="Dán nội dung Netscape cookies hoặc document.cookie của YouTube vào đây..."
                value={ytRaw}
                onChange={(e) => setYtRaw(e.target.value)}
                InputProps={{ classes: { input: classes.textarea } }}
              />
              <Button
                variant="contained"
                color="primary"
                size="small"
                className={classes.saveBtn}
                disabled={saving !== null}
                onClick={() => saveCookies('youtube', ytRaw)}
              >
                {saving === 'youtube' ? 'Đang lưu...' : 'Lưu YouTube Cookies'}
              </Button>
            </Box>
          </Box>
        </Grid>

        {/* Zing MP3 Section */}
        <Grid item xs={12} md={6}>
          <Box className={classes.providerCard}>
            <Box>
              <Typography variant="subtitle1" style={{ fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
                🎵 Zing MP3 VIP / Premium
                <Tooltip title="Đồng bộ VIP Zing MP3 giúp tải các bản nhạc chất lượng phòng thu lossless (FLAC) thay vì mp3 128kbps mặc định!">
                  <IconButton size="small" style={{ color: '#aaa' }}><HelpOutlineIcon fontSize="small" /></IconButton>
                </Tooltip>
              </Typography>

              {renderStatus('zing')}

              <Typography variant="body2" style={{ fontWeight: 'bold', marginTop: 16, marginBottom: 8 }}>
                Cách 1: Kéo & Thả Bookmarklet (Tự động 1-Click)
              </Typography>
              <Box className={classes.bookmarkletInstructions}>
                <Typography variant="caption" color="textSecondary" style={{ display: 'block', marginBottom: 12 }}>
                  1. Hãy kéo nút màu xanh dưới đây và <strong>thả vào thanh Dấu trang (Bookmark bar)</strong> của trình duyệt.<br />
                  2. Mở trang <a href="https://zingmp3.vn" target="_blank" rel="noopener noreferrer" style={{ color: '#1db954', textDecoration: 'underline' }}>zingmp3.vn</a> và đăng nhập tài khoản VIP của bạn.<br />
                  3. <strong>Nhấp vào Dấu trang vừa kéo</strong>. Một thông báo đồng bộ sẽ hiện lên góc màn hình và nạp phiên tức thì!
                </Typography>
                <a
                  href={makeBookmarklet('zing')}
                  className={classes.bookmarkletBtn}
                  onClick={(e) => e.preventDefault()}
                >
                  🟢 Đồng bộ Zing MP3 VIP
                </a>
              </Box>

              <Typography variant="body2" style={{ fontWeight: 'bold', marginTop: 16, marginBottom: 8 }}>
                Cách 2: Nạp Cookies Thủ Công (Dành cho nhà phát triển)
              </Typography>
              <TextField
                multiline
                rows={3}
                variant="outlined"
                fullWidth
                placeholder="Dán nội dung Netscape cookies hoặc document.cookie của Zing MP3 vào đây..."
                value={zingRaw}
                onChange={(e) => setZingRaw(e.target.value)}
                InputProps={{ classes: { input: classes.textarea } }}
              />
              <Button
                variant="contained"
                color="primary"
                size="small"
                className={classes.saveBtn}
                disabled={saving !== null}
                onClick={() => saveCookies('zing', zingRaw)}
              >
                {saving === 'zing' ? 'Đang lưu...' : 'Lưu Zing Cookies'}
              </Button>
            </Box>
          </Box>
        </Grid>
      </Grid>
    </Box>
  )
}

export default PremiumManager
