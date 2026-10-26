import { beforeEach, describe, expect, it, vi } from 'vitest'

const { getList } = vi.hoisted(() => ({
  getList: vi.fn(async () => ({ data: [], total: 0 })),
}))
vi.mock('ra-data-json-server', () => ({ default: () => ({ getList }) }))
import provider from './wrapperDataProvider'

describe('current library filter ownership', () => {
  beforeEach(() => {
    getList.mockClear()
    localStorage.setItem('role', 'admin')
    localStorage.setItem(
      'state',
      JSON.stringify({
        library: {
          userLibraries: [{ id: 'old' }, { id: 'current' }],
          selectedLibraries: ['old'],
        },
      }),
    )
  })

  it('keeps the explicit current selection while persisted state is older', async () => {
    await provider.getList('song', {
      pagination: { page: 1, perPage: 50 },
      sort: { field: 'title', order: 'ASC' },
      filter: { library_id: ['current'] },
    })
    expect(getList.mock.calls[0][1].filter.library_id).toEqual(['current'])
  })

  it('honors an explicit all-libraries selection without retaining the old filter', async () => {
    await provider.getList('album', {
      pagination: { page: 1, perPage: 50 },
      sort: { field: 'name', order: 'ASC' },
      filter: { library_id: [] },
    })
    expect(getList.mock.calls[0][1].filter).not.toHaveProperty('library_id')
  })

  it('retains persisted filtering for existing callers without an explicit selection', async () => {
    await provider.getList('artist', {
      pagination: { page: 1, perPage: 50 },
      sort: { field: 'name', order: 'ASC' },
      filter: {},
    })
    expect(getList.mock.calls[0][1].filter.library_id).toEqual(['old'])
  })
})
