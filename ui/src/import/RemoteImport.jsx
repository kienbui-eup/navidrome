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
} from '@material-ui/core'
import AddIcon from '@material-ui/icons/Add'
import ArrowBackIcon from '@material-ui/icons/ArrowBack'
import DeleteIcon from '@material-ui/icons/Delete'
import EditIcon from '@material-ui/icons/Edit'
import GetAppIcon from '@material-ui/icons/GetApp'
import PlayArrowIcon from '@material-ui/icons/PlayArrow'
import SearchIcon from '@material-ui/icons/Search'
import StopIcon from '@material-ui/icons/Stop'
import { httpClient } from '../dataProvider'
import { formatBytes } from '../utils'

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

const emptyForm = { id: '', name: '', url: '', username: '', password: '' }

// Import from another vi2play/Subsonic server: manage saved source servers,
// search songs/albums/artists there and queue original-file downloads.
const RemoteImport = ({ libraryId, onJobStarted, classes }) => {
  const notify = useNotify()
  const [servers, setServers] = useState([])
  const [serverId, setServerId] = useState('')
  const [dialog, setDialog] = useState(null) // null | form object
  const [testState, setTestState] = useState(null) // null | 'busy' | 'ok' | error text
  const [query, setQuery] = useState('')
  const [result, setResult] = useState(null)
  // view: {type:'search'} | {type:'artist',artist,albums} | {type:'album',album,songs}
  const [view, setView] = useState({ type: 'search' })
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
      const total =
        ((json && json.songs) || []).length +
        ((json && json.albums) || []).length +
        ((json && json.artists) || []).length
      if (total === 0) notify('Không tìm thấy kết quả nào', 'info')
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
      <ListItem key={song.id} divider>
        <IconButton aria-label="Nghe thử" onClick={() => togglePreview(song)}>
          {playing === song.id ? <StopIcon /> : <PlayArrowIcon />}
        </IconButton>
        <ListItemText
          primary={
            <>
              {song.title}{' '}
              {song.suffix && (
                <Chip size="small" label={song.suffix.toUpperCase()} />
              )}{' '}
              {song.bitRate > 0 && (
                <Chip
                  size="small"
                  variant="outlined"
                  label={`${song.bitRate} kbps`}
                />
              )}
            </>
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
              onKeyDown={(e) => e.key === 'Enter' && query && search()}
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
              disabled={!query || busy === 'search'}
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

          {view.type === 'search' && result && (
            <>
              {result.songs.length > 0 && (
                <Box className={classes.section}>
                  <Typography variant="subtitle1">Bài hát</Typography>
                  <List dense>{result.songs.map(songItem)}</List>
                </Box>
              )}
              {result.albums.length > 0 && (
                <Box className={classes.section}>
                  <Typography variant="subtitle1">Album</Typography>
                  <List dense>{result.albums.map(albumItem)}</List>
                </Box>
              )}
              {result.artists.length > 0 && (
                <Box className={classes.section}>
                  <Typography variant="subtitle1">Ca sĩ</Typography>
                  <List dense>{result.artists.map(artistItem)}</List>
                </Box>
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
