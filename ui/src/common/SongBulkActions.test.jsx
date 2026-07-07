import React from 'react'
import { render, screen, fireEvent } from '@testing-library/react'
import { TestContext } from 'ra-test'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ThemeProvider, createTheme } from '@material-ui/core/styles'
import { SongBulkActions } from './SongBulkActions'

vi.mock('../config', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, default: { ...actual.default, enableSharing: false } }
})

const mockDispatch = vi.fn()
vi.mock('react-redux', () => ({ useDispatch: () => mockDispatch }))

let mockPermissions = 'admin'
vi.mock('react-admin', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    usePermissions: () => ({ permissions: mockPermissions }),
    useUnselectAll: () => vi.fn(),
  }
})

const renderBulkActions = (selectedIds) => {
  const theme = createTheme()
  return render(
    <TestContext>
      <ThemeProvider theme={theme}>
        <SongBulkActions
          resource="song"
          selectedIds={selectedIds}
          basePath="/song"
          filterValues={{}}
        />
      </ThemeProvider>
    </TestContext>,
  )
}

describe('SongBulkActions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockPermissions = 'admin'
  })

  it('shows the delete button for admin users', () => {
    renderBulkActions(['s1', 's2'])
    expect(screen.getByText('resources.song.actions.delete')).toBeTruthy()
  })

  it('hides the delete button for non-admin users', () => {
    mockPermissions = 'user'
    renderBulkActions(['s1', 's2'])
    expect(screen.queryByText('resources.song.actions.delete')).toBeNull()
  })

  it('dispatches openDeleteMediaDialog with mode "songs" and the selection', () => {
    renderBulkActions(['s1', 's2', 's3'])
    fireEvent.click(screen.getByText('resources.song.actions.delete'))

    expect(mockDispatch).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'DELETE_MEDIA_OPEN',
        mode: 'songs',
        record: { ids: ['s1', 's2', 's3'], count: 3 },
      }),
    )
  })
})
