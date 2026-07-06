import React, { useEffect, useState } from 'react'
import {
  Create,
  SimpleForm,
  TextInput,
  BooleanInput,
  required,
  useTranslate,
  useRefresh,
  useNotify,
  useRedirect,
} from 'react-admin'
import {
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  TextField,
  Typography,
} from '@material-ui/core'
import { makeStyles } from '@material-ui/core/styles'
import { Title } from '../common'
import { httpClient } from '../dataProvider'

const useStyles = makeStyles((theme) => ({
  templateCard: { marginBottom: theme.spacing(2) },
  templateRow: {
    display: 'flex',
    alignItems: 'flex-start',
    gap: theme.spacing(2),
    flexWrap: 'wrap',
    marginTop: theme.spacing(1),
  },
  select: { minWidth: 260 },
  name: { minWidth: 220 },
  hint: { color: theme.palette.text.secondary, marginTop: theme.spacing(1) },
}))

// "Create from template" section: fetches the built-in smart playlist
// catalog from GET /api/playlist/template and, on submit, calls
// POST /api/playlist/template to create a playlist for the current user
// (see server/nativeapi/playlist_templates.go). Kept as a standalone
// section (rather than folded into the SimpleForm below) because it talks
// to a different endpoint/response shape than the standard playlist
// resource create used by react-admin's dataProvider.
const CreateFromTemplate = () => {
  const classes = useStyles()
  const translate = useTranslate()
  const notify = useNotify()
  const redirect = useRedirect()
  const [templates, setTemplates] = useState([])
  const [templateId, setTemplateId] = useState('')
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let ignore = false
    httpClient('/api/playlist/template')
      .then(({ json }) => {
        if (!ignore) {
          setTemplates(json || [])
        }
      })
      .catch(() => {})
    return () => {
      ignore = true
    }
  }, [])

  if (templates.length === 0) {
    return null
  }

  const selected = templates.find((t) => t.id === templateId)

  const createFromTemplate = async () => {
    if (!templateId) {
      return
    }
    setBusy(true)
    try {
      const { json } = await httpClient('/api/playlist/template', {
        method: 'POST',
        body: JSON.stringify({ templateId, name: name || undefined }),
      })
      notify('ra.notification.created', 'info', { smart_count: 1 })
      redirect('show', '/playlist', json.id)
    } catch (e) {
      notify('ra.page.error', 'warning')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card className={classes.templateCard}>
      <CardContent>
        <Typography variant="h6">
          {translate('resources.playlist.actions.createFromTemplate')}
        </Typography>
        <Box className={classes.templateRow}>
          <FormControl className={classes.select}>
            <InputLabel id="playlist-template-select-label">
              {translate('resources.playlist.fields.template')}
            </InputLabel>
            <Select
              labelId="playlist-template-select-label"
              value={templateId}
              onChange={(e) => setTemplateId(e.target.value)}
            >
              {templates.map((t) => (
                <MenuItem key={t.id} value={t.id}>
                  {translate(`resources.playlist.templates.${t.id}.name`, {
                    _: t.name,
                  })}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <TextField
            className={classes.name}
            label={translate('resources.playlist.fields.name')}
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <Button
            variant="contained"
            color="primary"
            disabled={!templateId || busy}
            onClick={createFromTemplate}
            startIcon={busy ? <CircularProgress size={16} /> : null}
          >
            {translate('resources.playlist.actions.createFromTemplate')}
          </Button>
        </Box>
        {selected && selected.description && (
          <Typography className={classes.hint}>
            {translate(
              `resources.playlist.templates.${selected.id}.description`,
              { _: selected.description },
            )}
          </Typography>
        )}
      </CardContent>
    </Card>
  )
}

const PlaylistCreate = (props) => {
  const { basePath } = props
  const refresh = useRefresh()
  const notify = useNotify()
  const redirect = useRedirect()
  const translate = useTranslate()
  const resourceName = translate('resources.playlist.name', { smart_count: 1 })
  const title = translate('ra.page.create', {
    name: `${resourceName}`,
  })

  const onSuccess = () => {
    notify('ra.notification.created', 'info', { smart_count: 1 })
    redirect('list', basePath)
    refresh()
  }

  return (
    <>
      <CreateFromTemplate />
      <Create
        title={<Title subTitle={title} />}
        {...props}
        onSuccess={onSuccess}
      >
        <SimpleForm redirect="list" variant={'outlined'}>
          <TextInput source="name" validate={required()} />
          <TextInput multiline source="comment" />
          <BooleanInput source="public" initialValue={true} />
        </SimpleForm>
      </Create>
    </>
  )
}

export default PlaylistCreate
