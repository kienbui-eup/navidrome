import React from 'react'
import { render, fireEvent, screen, waitFor } from '@testing-library/react'
import { TestContext } from 'ra-test'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AlbumContextMenu, ArtistContextMenu } from './ContextMenus'

vi.mock('../dataProvider', () => ({
  httpClient: vi.fn(),
}))

vi.mock('../config', () => ({
  default: {
    enableDownloads: true,
    enableFavourites: true,
    enableSharing: true,
  },
}))

const mockDispatch = vi.fn()
vi.mock('react-redux', () => ({ useDispatch: () => mockDispatch }))

let mockPermissions = 'admin'
vi.mock('react-admin', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    usePermissions: () => ({ permissions: mockPermissions }),
    useDataProvider: () => ({ getList: vi.fn() }),
  }
})

const openMenu = (Component, record) => {
  render(
    <TestContext>
      <Component record={record} />
    </TestContext>,
  )
  fireEvent.click(screen.getAllByRole('button')[1])
}

describe('AlbumContextMenu', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockPermissions = 'admin'
  })

  it('shows the delete option for admin users', async () => {
    openMenu(AlbumContextMenu, { id: 'al1', name: 'Album 1' })
    await waitFor(() =>
      screen.getByText('resources.album.actions.addToPlaylist'),
    )
    expect(screen.getByText('resources.album.actions.delete')).toBeTruthy()
  })

  it('hides the delete option for non-admin users', async () => {
    mockPermissions = 'user'
    openMenu(AlbumContextMenu, { id: 'al1', name: 'Album 1' })
    await waitFor(() =>
      screen.getByText('resources.album.actions.addToPlaylist'),
    )
    expect(screen.queryByText('resources.album.actions.delete')).toBeNull()
  })

  it('dispatches openDeleteMediaDialog with mode "album" when clicked', async () => {
    const record = { id: 'al1', name: 'Album 1' }
    openMenu(AlbumContextMenu, record)
    await waitFor(() => screen.getByText('resources.album.actions.delete'))
    fireEvent.click(screen.getByText('resources.album.actions.delete'))

    expect(mockDispatch).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'DELETE_MEDIA_OPEN',
        mode: 'album',
        record,
      }),
    )
  })
})

describe('ArtistContextMenu', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockPermissions = 'admin'
  })

  it('never shows the delete option, even for admin users', async () => {
    openMenu(ArtistContextMenu, { id: 'ar1', name: 'Artist 1' })
    await waitFor(() =>
      screen.getByText('resources.album.actions.addToPlaylist'),
    )
    expect(screen.queryByText('resources.album.actions.delete')).toBeNull()
  })
})
