import * as React from 'react'
import {
  cleanup,
  render,
  screen,
  fireEvent,
  waitFor,
} from '@testing-library/react'
import { useDispatch } from 'react-redux'
import { useDataProvider, useNotify } from 'react-admin'
import { QuickAddToQueueButton } from './QuickAddToQueueButton'

vi.mock('react-redux', () => ({
  useDispatch: vi.fn(),
}))

vi.mock('react-admin', () => ({
  useDataProvider: vi.fn(),
  useNotify: vi.fn(),
  useTranslate: () => (key, options) => options?._ || key,
}))

describe('<QuickAddToQueueButton />', () => {
  const mockDispatch = vi.fn()
  const mockNotify = vi.fn()
  const mockGetList = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    useDispatch.mockReturnValue(mockDispatch)
    useNotify.mockReturnValue(mockNotify)
    useDataProvider.mockReturnValue({ getList: mockGetList })
  })

  afterEach(cleanup)

  it('adds a single song to the play queue on click', () => {
    const record = { id: 'song-1', title: 'Song' }
    render(<QuickAddToQueueButton record={record} />)

    fireEvent.click(screen.getByRole('button'))

    expect(mockDispatch).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'PLAYER_ADD_TRACKS',
        data: { 'song-1': record },
      }),
    )
    expect(mockNotify).toHaveBeenCalledWith('message.songsAddedToQueue', {
      type: 'info',
      messageArgs: { smart_count: 1 },
    })
    expect(mockGetList).not.toHaveBeenCalled()
  })

  it('fetches songs and adds them all when songQueryParams is given', async () => {
    const songs = [
      { id: 's1', title: 'One' },
      { id: 's2', title: 'Two' },
    ]
    mockGetList.mockResolvedValue({ data: songs })
    const queryParams = {
      pagination: { page: 1, perPage: -1 },
      sort: { field: 'album', order: 'ASC' },
      filter: { album_id: 'album-1' },
    }
    render(
      <QuickAddToQueueButton
        record={{ id: 'album-1' }}
        resource={'album'}
        songQueryParams={queryParams}
      />,
    )

    fireEvent.click(screen.getByRole('button'))

    expect(mockGetList).toHaveBeenCalledWith('song', queryParams)
    await waitFor(() =>
      expect(mockDispatch).toHaveBeenCalledWith(
        expect.objectContaining({
          type: 'PLAYER_ADD_TRACKS',
          data: { s1: songs[0], s2: songs[1] },
        }),
      ),
    )
    expect(mockNotify).toHaveBeenCalledWith('message.songsAddedToQueue', {
      type: 'info',
      messageArgs: { smart_count: 2 },
    })
  })

  it('notifies an error when the song fetch fails', async () => {
    mockGetList.mockRejectedValue(new Error('boom'))
    render(
      <QuickAddToQueueButton
        record={{ id: 'album-1' }}
        resource={'album'}
        songQueryParams={{ filter: { album_id: 'album-1' } }}
      />,
    )

    fireEvent.click(screen.getByRole('button'))

    await waitFor(() =>
      expect(mockNotify).toHaveBeenCalledWith('ra.page.error', {
        type: 'warning',
      }),
    )
    expect(mockDispatch).not.toHaveBeenCalled()
  })

  it('is disabled for missing records', () => {
    render(<QuickAddToQueueButton record={{ id: 's1', missing: true }} />)
    expect(screen.getByRole('button')).toBeDisabled()
  })
})
