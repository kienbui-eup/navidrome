import React from 'react'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import {
  AdminContext,
  Resource,
  Filter,
  SearchInput,
  useListContext,
  useDataProvider,
  useRefresh,
  useUpdate,
  useDelete,
  CRUD_UPDATE,
  CRUD_DELETE,
} from 'react-admin'
import { createMemoryHistory } from 'history'
import { ThemeProvider, createTheme } from '@material-ui/core/styles'
import { useDispatch } from 'react-redux'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { List } from './List'
import { libraryReducer } from '../reducers/libraryReducer'
import { setSelectedLibraries, setUserLibraries } from '../actions'
import { withAbortSignal } from '../dataProvider/withAbortSignal'

const Null = () => null
const Empty = () => <p>Empty library</p>
const Filters = (props) => (
  <Filter {...props}>
    <SearchInput source="title" alwaysOn />
  </Filter>
)
const Probe = (props) => {
  const context = useListContext()
  const provider = useDataProvider()
  const refresh = useRefresh()
  const dispatch = useDispatch()
  const [update] = useUpdate()
  const [remove] = useDelete()
  return (
    <>
      <output data-testid="agreement">
        {JSON.stringify({ props: props.ids, context: context.ids })}
      </output>
      <output data-testid="selection">
        {JSON.stringify(context.selectedIds)}
      </output>
      <output data-testid="total">{context.total}</output>
      <ul>
        {context.ids.map((id) => (
          <li key={id}>{context.data[id].title}</li>
        ))}
      </ul>
      <button onClick={() => context.onSelect(context.ids)}>
        Select loaded
      </button>
      <button
        onClick={() => {
          context.loadMore()
          context.loadMore()
        }}
      >
        Load twice
      </button>
      <button onClick={() => provider.getMany('song', { ids: [3] })}>
        Update record
      </button>
      <button onClick={refresh}>Refresh records</button>
      <button
        onClick={() =>
          update(
            'song',
            3,
            { id: 3, title: 'Updated by mutation' },
            context.data[3],
            { mutationMode: 'pessimistic', action: CRUD_UPDATE },
          )
        }
      >
        Edit third record
      </button>
      <button
        onClick={() =>
          remove('song', 2, context.data[2], {
            mutationMode: 'pessimistic',
            action: CRUD_DELETE,
            onSuccess: refresh,
          })
        }
      >
        Delete second record
      </button>
      <button
        onClick={() => {
          dispatch(setUserLibraries([{ id: 'first' }, { id: 'other' }]))
          dispatch(setSelectedLibraries(['other']))
        }}
      >
        Switch library
      </button>
    </>
  )
}

const deferred = () => {
  let resolve
  let reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}
const rows = (...ids) => ids.map((id) => ({ id, title: `Song ${id}` }))
const mount = (
  getList,
  props = {},
  initialEntries = ['/song'],
  mutations = {},
) => {
  const provider = {
    getList: vi.fn((resource, params) =>
      withAbortSignal(getList(resource, params), params.signal),
    ),
    getMany: vi.fn(async () => ({
      data: [{ id: 3, title: 'Updated third song' }],
    })),
    ...mutations,
  }
  const history = createMemoryHistory({
    initialEntries,
    initialIndex: initialEntries.length - 1,
  })
  render(
    <ThemeProvider theme={createTheme()}>
      <AdminContext
        dataProvider={provider}
        history={history}
        customReducers={{ library: libraryReducer }}
      >
        <Resource intent="registration" name="song" list={Null} />
        <div id="main-content">
          <List
            resource="song"
            basePath="/song"
            infinite
            syncWithLocation
            perPage={2}
            debounce={0}
            exporter={false}
            actions={false}
            bulkActionButtons={false}
            filters={<Filters />}
            {...props}
          >
            <Probe />
          </List>
        </div>
      </AdminContext>
    </ThemeProvider>,
  )
  return { provider, history }
}

describe('continuous library with real React Admin', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('appends bounded pages once, deduplicates, shares selection and ends', async () => {
    const pending = deferred()
    const { provider } = mount(async (_, { pagination: { page } }) => {
      if (page === 1) return { data: rows(1, 2), total: 5 }
      if (page === 2) return pending.promise
      return { data: rows(4, 5), total: 5 }
    })
    await screen.findByText('Song 1')
    fireEvent.click(screen.getByRole('button', { name: 'Load twice' }))
    await waitFor(() => expect(provider.getList).toHaveBeenCalledTimes(2))
    await act(async () => pending.resolve({ data: rows(2, 3), total: 5 }))
    await screen.findByText('Song 3')
    expect(screen.getAllByText('Song 2')).toHaveLength(1)
    fireEvent.click(screen.getByRole('button', { name: 'Select loaded' }))
    expect(screen.getByTestId('selection')).toHaveTextContent('[1,2,3]')
    expect(JSON.parse(screen.getByTestId('agreement').textContent)).toEqual({
      props: [1, 2, 3],
      context: [1, 2, 3],
    })
    fireEvent.click(screen.getByRole('button', { name: 'Load more' }))
    await screen.findByText('Song 5')
    expect(
      screen.queryByRole('button', { name: 'Load more' }),
    ).not.toBeInTheDocument()
    expect(provider.getList.mock.calls.map(([, p]) => p.pagination)).toEqual([
      { page: 1, perPage: 2 },
      { page: 2, perPage: 2 },
      { page: 3, perPage: 2 },
    ])
  })

  it('discards an older append when the real search filter changes', async () => {
    const pending = deferred()
    const { provider, history } = mount(async (_, { pagination, filter }) => {
      if (filter.title === 'new') return { data: rows(9), total: 1 }
      return pagination.page === 1
        ? { data: rows(1, 2), total: 6 }
        : pending.promise
    })
    await screen.findByText('Song 1')
    fireEvent.click(screen.getByRole('button', { name: 'Load more' }))
    await waitFor(() => expect(provider.getList).toHaveBeenCalledTimes(2))
    fireEvent.change(screen.getByPlaceholderText('Search'), {
      target: { value: 'new' },
    })
    await screen.findByText('Song 9')
    await act(async () => pending.resolve({ data: rows(3, 4), total: 6 }))
    expect(screen.queryByText('Song 1')).not.toBeInTheDocument()
    expect(screen.queryByText('Song 3')).not.toBeInTheDocument()
    expect(history.location.search).toContain('new')
  })

  it('does not let an old response overwrite a current record with the same ID', async () => {
    const pending = deferred()
    const { provider } = mount(async (_, { pagination, filter }) => {
      if (filter.title === 'new')
        return { data: [{ id: 1, title: 'New record' }], total: 1 }
      return pagination.page === 1
        ? { data: rows(1, 2), total: 4 }
        : pending.promise
    })
    await screen.findByText('Song 1')
    fireEvent.click(screen.getByRole('button', { name: 'Load more' }))
    await waitFor(() => expect(provider.getList).toHaveBeenCalledTimes(2))
    fireEvent.change(screen.getByPlaceholderText('Search'), {
      target: { value: 'new' },
    })
    await screen.findByText('New record')
    await act(async () =>
      pending.resolve({
        data: [{ id: 1, title: 'Stale record' }, ...rows(3)],
        total: 4,
      }),
    )
    expect(screen.getByText('New record')).toBeInTheDocument()
    expect(screen.queryByText('Stale record')).not.toBeInTheDocument()
  })

  it('replaces a legacy numbered-page URL and leaves Back usable', async () => {
    const { history, provider } = mount(
      async () => ({ data: rows(1), total: 1 }),
      {},
      ['/previous', '/song?page=7&perPage=25'],
    )
    await screen.findByText('Song 1')
    await waitFor(() =>
      expect(new URLSearchParams(history.location.search).get('perPage')).toBe(
        '2',
      ),
    )
    expect(new URLSearchParams(history.location.search).get('page')).toBe('1')
    expect(history.entries).toHaveLength(2)
    act(() => history.goBack())
    await waitFor(() => expect(history.location.pathname).toBe('/previous'))
    expect(provider.getList.mock.calls[0][1].pagination).toEqual({
      page: 1,
      perPage: 2,
    })
  })

  it('keeps loaded rows on failure and retries the same page', async () => {
    let failed = false
    const { provider } = mount(async (_, { pagination }) => {
      if (pagination.page === 1) return { data: rows(1, 2), total: 3 }
      if (!failed) {
        failed = true
        throw new Error('offline')
      }
      return { data: rows(3), total: 3 }
    })
    await screen.findByText('Song 1')
    fireEvent.click(screen.getByRole('button', { name: 'Load more' }))
    await screen.findByRole('alert')
    expect(screen.getByText('Song 1')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    await screen.findByText('Song 3')
    expect(
      provider.getList.mock.calls.map(([, p]) => p.pagination.page),
    ).toEqual([1, 2, 2])
  })

  it('reads later rows from the real record cache and resets on refresh or library change', async () => {
    const { provider } = mount(async (_, { pagination }) => ({
      data: pagination.page === 1 ? rows(1, 2) : rows(3),
      total: 3,
    }))
    await screen.findByText('Song 1')
    fireEvent.click(screen.getByRole('button', { name: 'Load more' }))
    await screen.findByText('Song 3')
    fireEvent.click(screen.getByRole('button', { name: 'Update record' }))
    await screen.findByText('Updated third song')
    expect(provider.getList).toHaveBeenCalledTimes(2)
    fireEvent.click(screen.getByRole('button', { name: 'Refresh records' }))
    await waitFor(() => expect(provider.getList).toHaveBeenCalledTimes(3))
    expect(screen.queryByText('Updated third song')).not.toBeInTheDocument()
    await screen.findByText('Song 1')
    fireEvent.click(screen.getByRole('button', { name: 'Switch library' }))
    await waitFor(() => expect(provider.getList).toHaveBeenCalledTimes(4))
    expect(provider.getList.mock.calls.at(-1)[1].pagination.page).toBe(1)
    expect(provider.getList.mock.calls.at(-1)[1].filter.library_id).toEqual([
      'other',
    ])
  })

  it('pauses rather than fetching forever when a page contains only duplicates', async () => {
    const { provider } = mount(async () => ({ data: rows(1, 2), total: 8 }))
    await screen.findByText('Song 1')
    fireEvent.click(screen.getByRole('button', { name: 'Load more' }))
    await screen.findByRole('alert')
    expect(provider.getList).toHaveBeenCalledTimes(2)
    expect(screen.getAllByText('Song 1')).toHaveLength(1)
  })

  it('observes the content scroller and automatically appends only once per page', async () => {
    const observers = []
    vi.stubGlobal(
      'IntersectionObserver',
      class {
        constructor(callback, options) {
          this.callback = callback
          this.options = options
          observers.push(this)
        }
        observe() {}
        disconnect() {}
      },
    )
    const pending = deferred()
    const { provider } = mount(async (_, { pagination }) =>
      pagination.page === 1 ? { data: rows(1, 2), total: 3 } : pending.promise,
    )
    await screen.findByText('Song 1')
    expect(observers.at(-1).options.root).toBe(
      document.getElementById('main-content'),
    )
    act(() => {
      observers.at(-1).callback([{ isIntersecting: true }])
      observers.at(-1).callback([{ isIntersecting: true }])
    })
    await waitFor(() => expect(provider.getList).toHaveBeenCalledTimes(2))
    await act(async () => pending.resolve({ data: rows(3), total: 3 }))
    await screen.findByText('Song 3')
    expect(screen.getByText('All results loaded')).toBeInTheDocument()
  })

  it('can retry an initial failure and keeps an empty result free of loading controls', async () => {
    let failed = false
    const { provider } = mount(
      async () => {
        if (!failed) {
          failed = true
          throw new Error('offline')
        }
        return { data: [], total: 0 }
      },
      { empty: <Empty /> },
    )
    await screen.findByRole('alert')
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    await screen.findByText('Empty library')
    expect(
      screen.queryByRole('button', { name: 'Load more' }),
    ).not.toBeInTheDocument()
    expect(
      provider.getList.mock.calls.map(([, p]) => p.pagination.page),
    ).toEqual([1, 1])
  })

  it('keeps real RA edits visible and reconciles paging and selection after deletion', async () => {
    let data = rows(1, 2, 3)
    const { provider } = mount(
      async (_, { pagination: { page, perPage } }) => ({
        data: data.slice((page - 1) * perPage, page * perPage),
        total: data.length,
      }),
      {},
      ['/song'],
      {
        update: vi.fn(async (_, { id, data: change }) => {
          data = data.map((record) =>
            record.id === id ? { ...record, ...change } : record,
          )
          return { data: data.find((record) => record.id === id) }
        }),
        delete: vi.fn(async (_, { id }) => {
          const record = data.find((record) => record.id === id)
          data = data.filter((record) => record.id !== id)
          return { data: record }
        }),
      },
    )
    await screen.findByText('Song 1')
    fireEvent.click(screen.getByRole('button', { name: 'Load more' }))
    await screen.findByText('Song 3')
    fireEvent.click(screen.getByRole('button', { name: 'Edit third record' }))
    await screen.findByText('Updated by mutation')
    fireEvent.click(screen.getByRole('button', { name: 'Select loaded' }))
    expect(screen.getByTestId('selection')).toHaveTextContent('[1,2,3]')
    fireEvent.click(
      screen.getByRole('button', { name: 'Delete second record' }),
    )
    await waitFor(() => expect(provider.getList).toHaveBeenCalledTimes(3))
    await screen.findByText('Updated by mutation')
    expect(screen.queryByText('Song 2')).not.toBeInTheDocument()
    expect(screen.getByTestId('selection')).toHaveTextContent('[1,3]')
    expect(screen.getByTestId('total')).toHaveTextContent('2')
    expect(provider.getList.mock.calls.at(-1)[1].pagination).toEqual({
      page: 1,
      perPage: 2,
    })
  })
})
