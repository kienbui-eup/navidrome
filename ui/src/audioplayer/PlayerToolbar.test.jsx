import React from 'react'
import { render, screen, fireEvent, cleanup } from '@testing-library/react'
import { useMediaQuery } from '@material-ui/core'
import { useDispatch, useSelector } from 'react-redux'
import { openSaveQueueDialog } from '../actions'
import PlayerToolbar from './PlayerToolbar'

// Mock dependencies
vi.mock('@material-ui/core', async () => {
  const actual = await import('@material-ui/core')
  return {
    ...actual,
    useMediaQuery: vi.fn(),
  }
})

vi.mock('react-redux', () => ({
  useDispatch: vi.fn(),
  useSelector: vi.fn(),
}))

vi.mock('../actions', () => ({
  openSaveQueueDialog: vi.fn(),
}))

describe('<PlayerToolbar />', () => {
  const mockDispatch = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    useDispatch.mockReturnValue(mockDispatch)
    // queueEmpty selector: default to a non-empty queue
    useSelector.mockImplementation((selector) =>
      selector({ player: { queue: [{ trackId: 'song-1' }] } }),
    )
    openSaveQueueDialog.mockReturnValue({ type: 'OPEN_SAVE_QUEUE_DIALOG' })
  })

  afterEach(cleanup)

  describe('Desktop layout', () => {
    beforeEach(() => {
      useMediaQuery.mockReturnValue(true) // isDesktop = true
    })

    it('renders the save queue button with desktop classes', () => {
      render(<PlayerToolbar />)

      const listItems = screen.getAllByRole('listitem')
      expect(listItems).toHaveLength(1)
      expect(screen.getByTestId('save-queue-button')).toBeInTheDocument()
      expect(listItems[0].className).toContain('toolbar')
    })

    it('disables save queue button when isRadio is true', () => {
      render(<PlayerToolbar isRadio={true} />)

      expect(screen.getByTestId('save-queue-button')).toBeDisabled()
    })

    it('opens save queue dialog when save button is clicked', () => {
      render(<PlayerToolbar />)

      fireEvent.click(screen.getByTestId('save-queue-button'))

      expect(mockDispatch).toHaveBeenCalledWith({
        type: 'OPEN_SAVE_QUEUE_DIALOG',
      })
    })
  })

  describe('Mobile layout', () => {
    beforeEach(() => {
      useMediaQuery.mockReturnValue(false) // isDesktop = false
    })

    it('renders the save queue button with mobile classes', () => {
      render(<PlayerToolbar />)

      const listItems = screen.getAllByRole('listitem')
      expect(listItems).toHaveLength(1)
      expect(screen.getByTestId('save-queue-button')).toBeInTheDocument()
      expect(listItems[0].className).toContain('mobileListItem')
    })

    it('disables save queue button when isRadio is true', () => {
      render(<PlayerToolbar isRadio={true} />)

      expect(screen.getByTestId('save-queue-button')).toBeDisabled()
    })
  })

  describe('Common behavior', () => {
    it('disables the save queue button when the queue is empty', () => {
      useMediaQuery.mockReturnValue(true)
      useSelector.mockImplementation((selector) =>
        selector({ player: { queue: [] } }),
      )

      render(<PlayerToolbar />)

      expect(screen.getByTestId('save-queue-button')).toBeDisabled()
    })
  })
})
