import * as React from 'react'
import { cleanup, render } from '@testing-library/react'
import { SkeletonList } from './SkeletonList'

describe('<SkeletonList />', () => {
  afterEach(cleanup)

  it('renders the requested number of album-grid items', () => {
    const { container } = render(
      <SkeletonList variant="album-grid" count={6} />,
    )
    // MUI Skeleton renders elements with the MuiSkeleton-root class
    const skeletons = container.querySelectorAll('.MuiSkeleton-root')
    // each grid item renders 1 cover + 2 text lines = 3 skeletons
    expect(skeletons.length).toBe(6 * 3)
  })

  it('renders song-list rows without throwing', () => {
    const { container } = render(<SkeletonList variant="song-list" count={4} />)
    expect(
      container.querySelectorAll('.MuiSkeleton-root').length,
    ).toBeGreaterThan(0)
  })

  it('renders artist-list rows without throwing', () => {
    const { container } = render(
      <SkeletonList variant="artist-list" count={3} />,
    )
    expect(
      container.querySelectorAll('.MuiSkeleton-root').length,
    ).toBeGreaterThan(0)
  })

  it('falls back to default variant/count when props are omitted', () => {
    const { container } = render(<SkeletonList />)
    expect(
      container.querySelectorAll('.MuiSkeleton-root').length,
    ).toBeGreaterThan(0)
  })
})
