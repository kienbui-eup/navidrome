import * as React from 'react'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { Breadcrumb } from './Breadcrumb'

const renderWithRouter = (ui) => render(<MemoryRouter>{ui}</MemoryRouter>)

describe('<Breadcrumb />', () => {
  afterEach(cleanup)

  const items = [
    { label: 'Albums', to: '/album' },
    { label: 'Some Artist', to: '/artist/1' },
    { label: 'Current Album' },
  ]

  it('renders every label', () => {
    renderWithRouter(<Breadcrumb items={items} />)
    expect(screen.getByText('Albums')).toBeInTheDocument()
    expect(screen.getByText('Some Artist')).toBeInTheDocument()
    expect(screen.getByText('Current Album')).toBeInTheDocument()
  })

  it('renders items with `to` as links', () => {
    renderWithRouter(<Breadcrumb items={items} />)
    const albumsLink = screen.getByText('Albums').closest('a')
    expect(albumsLink).toHaveAttribute('href', '/album')
  })

  it('renders the last item as the current page, not a link', () => {
    renderWithRouter(<Breadcrumb items={items} />)
    const current = screen.getByText('Current Album')
    expect(current.closest('a')).toBeNull()
    expect(current).toHaveAttribute('aria-current', 'page')
  })
})
