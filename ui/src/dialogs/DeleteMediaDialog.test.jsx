import * as React from 'react'
import { TestContext } from 'ra-test'
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react'
import { describe, afterEach, it, expect, vi, beforeEach } from 'vitest'
import { DeleteMediaDialog } from './DeleteMediaDialog'

const mockDispatch = vi.fn()
const mockNotify = vi.fn()
const mockRefresh = vi.fn()
const mockUnselectAll = vi.fn()
const mockHttpClient = vi.fn()

vi.mock('react-redux', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useDispatch: () => mockDispatch }
})

vi.mock('../dataProvider', () => ({
  httpClient: (...args) => mockHttpClient(...args),
}))

let mockPermissions = 'admin'
vi.mock('react-admin', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useNotify: () => mockNotify,
    useRefresh: () => mockRefresh,
    useUnselectAll: () => mockUnselectAll,
    usePermissions: () => ({ permissions: mockPermissions, loaded: true }),
    // The real i18nProvider isn't wired up under TestContext (identity
    // translate, interpolation args dropped), so use a translate stub that
    // keeps the interpolation options visible in the rendered/notified text.
    useTranslate: () => (key, options) =>
      options ? `${key}::${JSON.stringify(options)}` : key,
  }
})

const renderDialog = (state) =>
  render(
    <TestContext initialState={{ deleteMediaDialog: state }}>
      <DeleteMediaDialog />
    </TestContext>,
  )

describe('DeleteMediaDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockPermissions = 'admin'
  })
  afterEach(cleanup)

  it('renders nothing when there is no record', () => {
    renderDialog({ open: false, mode: undefined, record: undefined })
    expect(screen.queryByText('deleteMedia.buttons.confirm')).toBeNull()
  })

  it('renders song mode content, including quality and the upgrade option for a lossy song', () => {
    renderDialog({
      open: true,
      mode: 'song',
      record: {
        id: 'mf-1',
        title: 'My Song',
        artist: 'My Artist',
        suffix: 'mp3',
        bitRate: 320,
      },
    })
    expect(screen.getByText(/deleteMedia\.info\.song/)).toBeTruthy()
    expect(screen.getByText(/deleteMedia\.info\.quality/)).toBeTruthy()
    expect(screen.getByText('deleteMedia.buttons.upgradeInstead')).toBeTruthy()
  })

  it('hides the upgrade option for a lossless song', () => {
    renderDialog({
      open: true,
      mode: 'song',
      record: {
        id: 'mf-1',
        title: 'My Song',
        artist: 'My Artist',
        suffix: 'flac',
        bitRate: 1000,
      },
    })
    expect(screen.queryByText('deleteMedia.buttons.upgradeInstead')).toBeNull()
  })

  it('hides the upgrade option when hideUpgradeAction is set, even for a lossy song', () => {
    renderDialog({
      open: true,
      mode: 'song',
      hideUpgradeAction: true,
      record: {
        id: 'mf-1',
        title: 'My Song',
        artist: 'My Artist',
        suffix: 'mp3',
        bitRate: 320,
      },
    })
    expect(screen.queryByText('deleteMedia.buttons.upgradeInstead')).toBeNull()
  })

  it('hides the upgrade option for non-admins', () => {
    mockPermissions = 'user'
    renderDialog({
      open: true,
      mode: 'song',
      record: { id: 'mf-1', title: 'My Song', artist: 'A', suffix: 'mp3' },
    })
    expect(screen.queryByText('deleteMedia.buttons.upgradeInstead')).toBeNull()
  })

  it('calls DELETE on confirm, notifies and refreshes on success', async () => {
    mockHttpClient.mockResolvedValueOnce({ json: { ids: ['mf-1'] } })
    renderDialog({
      open: true,
      mode: 'song',
      record: { id: 'mf-1', title: 'My Song', artist: 'A', suffix: 'mp3' },
    })

    fireEvent.click(screen.getByText('deleteMedia.buttons.confirm'))

    await waitFor(() =>
      expect(mockHttpClient).toHaveBeenCalledWith('/api/song/mf-1', {
        method: 'DELETE',
      }),
    )
    await waitFor(() => expect(mockNotify).toHaveBeenCalled())
    expect(mockRefresh).toHaveBeenCalled()
    expect(mockDispatch).toHaveBeenCalledWith({ type: 'DELETE_MEDIA_CLOSE' })
  })

  it('deletes multiple songs via query params and unselects them all', async () => {
    mockHttpClient.mockResolvedValueOnce({ json: { ids: ['mf-1', 'mf-2'] } })
    renderDialog({
      open: true,
      mode: 'songs',
      record: { ids: ['mf-1', 'mf-2'], count: 2 },
    })

    fireEvent.click(screen.getByText('deleteMedia.buttons.confirm'))

    await waitFor(() =>
      expect(mockHttpClient).toHaveBeenCalledWith('/api/song?id=mf-1&id=mf-2', {
        method: 'DELETE',
      }),
    )
    await waitFor(() => expect(mockUnselectAll).toHaveBeenCalledWith('song'))
  })

  it('shows the conflict message on a 409 response', async () => {
    mockHttpClient.mockRejectedValueOnce({
      status: 409,
      body: { error: 'Track A (mf-1) is being upgraded' },
    })
    renderDialog({
      open: true,
      mode: 'song',
      record: { id: 'mf-1', title: 'My Song', artist: 'A', suffix: 'mp3' },
    })

    fireEvent.click(screen.getByText('deleteMedia.buttons.confirm'))

    // Confirms the handler reads the message from e.body.error (the
    // deletion API's JSON error shape), not e.message.
    await waitFor(() =>
      expect(mockNotify).toHaveBeenCalledWith(
        expect.stringContaining('Track A (mf-1) is being upgraded'),
        'warning',
      ),
    )
    // dialog stays open on failure
    expect(mockDispatch).not.toHaveBeenCalledWith({
      type: 'DELETE_MEDIA_CLOSE',
    })
  })

  it('queues an upgrade scan instead of deleting when "find higher quality" is clicked', async () => {
    mockHttpClient.mockResolvedValueOnce({ json: { status: 'ok' } })
    renderDialog({
      open: true,
      mode: 'song',
      record: { id: 'mf-1', title: 'My Song', artist: 'A', suffix: 'mp3' },
    })

    fireEvent.click(screen.getByText('deleteMedia.buttons.upgradeInstead'))

    await waitFor(() =>
      expect(mockHttpClient).toHaveBeenCalledWith('/api/upgrade/scan', {
        method: 'POST',
        body: JSON.stringify({ mediaFileIds: ['mf-1'] }),
      }),
    )
    await waitFor(() =>
      expect(mockNotify).toHaveBeenCalledWith(
        'deleteMedia.notify.upgradeQueued',
        'info',
      ),
    )
  })
})
