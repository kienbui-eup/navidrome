import React, { useEffect, useRef, useState } from 'react'
import { useNotify } from 'react-admin'
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  FormControlLabel,
  IconButton,
  List,
  ListItem,
  ListItemSecondaryAction,
  ListItemText,
  Switch,
  TextField,
  Typography,
} from '@material-ui/core'
import SearchIcon from '@material-ui/icons/Search'
import GetAppIcon from '@material-ui/icons/GetApp'
import PlayArrowIcon from '@material-ui/icons/PlayArrow'
import StopIcon from '@material-ui/icons/Stop'
import { httpClient } from '../dataProvider'
import { formatBytes } from '../utils'

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

const SongSearch = ({ libraryId, onImported, classes }) => {
  const notify = useNotify()
  const [query, setQuery] = useState('')
  const [driveFolder, setDriveFolder] = useState('')
  const [losslessOnly, setLosslessOnly] = useState(true)
  const [result, setResult] = useState(null)
  const [busy, setBusy] = useState(null)
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
    audioRef.current.pause()
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

  const hits = (result && result.hits) || []

  return (
    <Box className={classes.section}>
      <TextField
        className={classes.field}
        label="Tìm bài hát"
        placeholder="tên bài, nghệ sĩ..."
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && query && search()}
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
          disabled={!query || busy === 'search'}
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

      {hits.length > 0 && (
        <List className={classes.section}>
          {hits.map((hit) => {
            const key = hitKey(hit)
            const meta = [
              hit.artist,
              hit.album !== hit.title ? hit.album : null,
              formatLength(hit.length),
              hit.size ? formatBytes(hit.size) : null,
            ]
              .filter(Boolean)
              .join(' • ')
            return (
              <ListItem key={key} divider>
                <IconButton
                  aria-label="Nghe thử"
                  onClick={() => togglePreview(hit)}
                >
                  {playing === key ? <StopIcon /> : <PlayArrowIcon />}
                </IconButton>
                <ListItemText
                  primary={
                    <>
                      {hit.title}{' '}
                      <Chip
                        size="small"
                        label={hit.format}
                        color={hit.lossless ? 'primary' : 'default'}
                      />{' '}
                      <Chip
                        size="small"
                        variant="outlined"
                        label={
                          hit.source === 'archive' ? 'Archive' : 'Drive'
                        }
                      />
                    </>
                  }
                  secondary={<span className={classes.itemMeta}>{meta}</span>}
                />
                <ListItemSecondaryAction>
                  <IconButton
                    edge="end"
                    aria-label="Tải về"
                    disabled={busy === key}
                    onClick={() => importHit(hit)}
                  >
                    {busy === key ? (
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
    </Box>
  )
}

export default SongSearch
