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
import UpgradeQuality from './UpgradeQuality'

const mockDispatch = vi.fn()
const mockNotify = vi.fn()
const mockHttpClient = vi.fn()

vi.mock('react-redux', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useDispatch: () => mockDispatch }
})

vi.mock('../dataProvider', () => ({
  httpClient: (...args) => mockHttpClient(...args),
}))

vi.mock('react-admin', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useNotify: () => mockNotify,
    useVersion: () => 0,
    usePermissions: () => ({ permissions: 'admin', loaded: true }),
    // Keep interpolation options visible so assertions can target specific
    // keys without a real i18nProvider (mirrors DeleteMediaDialog.test.jsx).
    useTranslate: () => (key, options) =>
      options ? `${key}::${JSON.stringify(options)}` : key,
  }
})

const pendingCandidate = {
  id: 'cand-1',
  mediaFileId: 'mf-1',
  status: 'pending',
  currentTitle: 'Song A',
  currentArtist: 'Artist A',
  currentFormat: 'mp3',
  currentBitRate: 128,
  title: 'Song A (FLAC)',
  format: 'flac',
  estBitRate: 900,
  matchScore: 95,
  source: 'archive',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

const rejectedCandidate = {
  ...pendingCandidate,
  id: 'cand-2',
  mediaFileId: 'mf-2',
  status: 'rejected',
  currentTitle: 'Song B',
}

const replacedCandidate = {
  ...pendingCandidate,
  id: 'cand-3',
  mediaFileId: 'mf-3',
  status: 'replaced',
  currentTitle: 'Song C',
}

const mockHttpClientImpl = (url) => {
  if (url.startsWith('/api/library')) {
    return Promise.resolve({ json: [] })
  }
  if (url.startsWith('/api/upgrade/status')) {
    return Promise.resolve({
      json: { running: false, scanned: 0, total: 0, found: 0 },
    })
  }
  if (url.includes('status=pending')) {
    return Promise.resolve({ json: [pendingCandidate] })
  }
  if (url.includes('status=rejected')) {
    return Promise.resolve({ json: [rejectedCandidate] })
  }
  if (url.includes('status=replaced')) {
    return Promise.resolve({ json: [replacedCandidate] })
  }
  return Promise.resolve({ json: [] })
}

describe('UpgradeQuality', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockHttpClient.mockImplementation(mockHttpClientImpl)
  })
  afterEach(cleanup)

  it('shows a delete-original action on pending rows and dispatches openDeleteMediaDialog', async () => {
    render(
      <TestContext>
        <UpgradeQuality />
      </TestContext>,
    )

    await waitFor(() => screen.getByText(/Artist A - Song A/))

    const deleteButton = screen.getByLabelText('upgrade.actions.deleteOriginal')
    fireEvent.click(deleteButton)

    expect(mockDispatch).toHaveBeenCalledWith({
      type: 'DELETE_MEDIA_OPEN',
      mode: 'song',
      hideUpgradeAction: true,
      record: {
        id: 'mf-1',
        title: 'Song A',
        artist: 'Artist A',
        suffix: 'mp3',
        bitRate: 128,
      },
    })
  })

  it('only shows delete-original on history rows with a failed/rejected status', async () => {
    render(
      <TestContext>
        <UpgradeQuality />
      </TestContext>,
    )

    fireEvent.click(screen.getByText('upgrade.tabs.history'))

    await waitFor(() => screen.getByText(/Artist A - Song B/))
    screen.getByText(/Artist A - Song C/)

    // Exactly one delete-original button (the rejected row); the replaced
    // row must not have one.
    const deleteButtons = screen.getAllByLabelText(
      'upgrade.actions.deleteOriginal',
    )
    expect(deleteButtons).toHaveLength(1)
  })
})
