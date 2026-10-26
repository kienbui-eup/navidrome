import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useSelector } from 'react-redux'
import { useHistory, useLocation } from 'react-router-dom'
import {
  useDataProvider,
  useGetResourceLabel,
  useListParams,
  useRecordSelection,
  useRefresh,
  useTranslate,
  useVersion,
} from 'react-admin'

const emptyIds = []
const emptyRecords = {}

export const useContinuousList = ({
  resource,
  basePath,
  filter = {},
  perPage = 50,
  exporter,
  hasCreate,
  filterDefaultValues,
  sort,
  debounce,
  syncWithLocation,
}) => {
  const provider = useDataProvider()
  const providerRef = useRef(provider)
  providerRef.current = provider
  const version = useVersion()
  const refetch = useRefresh()
  const translate = useTranslate()
  const getResourceLabel = useGetResourceLabel()
  const history = useHistory()
  const location = useLocation()
  const [params, modifiers] = useListParams({
    resource,
    perPage,
    filterDefaultValues,
    sort,
    debounce,
    syncWithLocation,
  })
  const [selectedIds, selection] = useRecordSelection(resource)
  const libraries = useSelector(
    (state) => state.library?.selectedLibraries || emptyIds,
  )
  const userLibraries = useSelector(
    (state) => state.library?.userLibraries || emptyIds,
  )
  const libraryIds =
    userLibraries.length === 1
      ? []
      : libraries.filter((id) =>
          userLibraries.some((library) => library.id === id),
        )
  const records = useSelector(
    (state) => state.admin.resources[resource]?.data || emptyRecords,
  )
  const currentSort = useMemo(
    () => ({ field: params.sort, order: params.order }),
    [params.sort, params.order],
  )
  const queryKey = JSON.stringify({
    resource,
    perPage,
    sort: currentSort,
    filter: { ...params.filter, ...filter },
    libraries: libraryIds,
    version,
  })
  const session = useRef(null)
  const [state, setState] = useState(null)

  useEffect(() => {
    if (params.perPage === perPage && params.page === 1) return
    if (syncWithLocation) {
      const normalized = new URLSearchParams(location.search)
      const values = {
        page: 1,
        perPage,
        sort: params.sort,
        order: params.order,
        filter: JSON.stringify(params.filter),
        displayedFilters: JSON.stringify(params.displayedFilters),
      }
      for (const [key, value] of Object.entries(values))
        normalized.set(key, String(value))
      history.replace({ ...location, search: `?${normalized}` })
    } else {
      modifiers.setPerPage(perPage)
      modifiers.setPage(1)
    }
  }, [params, perPage, modifiers, syncWithLocation, history, location])

  const requestPage = useCallback(async (current) => {
    if (
      session.current !== current ||
      current.pending ||
      current.state.status === 'complete'
    )
      return
    current.pending = true
    const publish = (next) => {
      current.state = next
      setState(next)
    }
    publish({ ...current.state, status: 'loading', error: null })
    const page = current.state.nextPage
    try {
      const response = await providerRef.current.getList(
        current.query.resource,
        {
          pagination: { page, perPage: current.query.perPage },
          sort: { ...current.query.sort },
          filter: {
            ...JSON.parse(JSON.stringify(current.query.filter)),
            ...(current.query.resource !== 'playlist' && {
              library_id: current.query.libraries,
            }),
          },
          signal: current.abort.signal,
        },
      )
      if (session.current !== current) return
      if (
        !response ||
        !Array.isArray(response.data) ||
        !Number.isFinite(response.total)
      ) {
        throw new Error('Invalid list response')
      }
      const seen = new Set(current.state.ids.map(String))
      const appended = []
      for (const record of response.data) {
        if (!seen.has(String(record.id))) {
          seen.add(String(record.id))
          appended.push(record.id)
        }
      }
      if (response.data.length && !appended.length)
        throw new Error('List page made no progress')
      const complete =
        response.data.length < current.query.perPage ||
        page * current.query.perPage >= response.total
      publish({
        key: current.state.key,
        ids: [...current.state.ids, ...appended],
        nextPage: page + 1,
        total: response.total,
        status: complete ? 'complete' : 'ready',
        error: null,
      })
    } catch (error) {
      if (session.current === current)
        publish({ ...current.state, status: 'error', error })
    } finally {
      current.pending = false
    }
  }, [])

  useEffect(() => {
    const current = {
      query: JSON.parse(queryKey),
      abort: new AbortController(),
      pending: false,
      state: {
        key: queryKey,
        ids: [],
        nextPage: 1,
        total: 0,
        status: 'loading',
        error: null,
      },
    }
    session.current = current
    requestPage(current)
    return () => {
      current.abort.abort()
      if (session.current === current) session.current = null
    }
  }, [queryKey, requestPage])

  const loadMore = useCallback(() => {
    if (session.current) requestPage(session.current)
  }, [requestPage])
  const active = state?.key === queryKey ? state : null
  const ids = useMemo(
    () => (active?.ids || emptyIds).filter((id) => records[id]),
    [active?.ids, records],
  )
  const data = useMemo(
    () => Object.fromEntries(ids.map((id) => [id, records[id]])),
    [ids, records],
  )
  const loaded = !!active && active.nextPage > 1

  return {
    resource,
    basePath,
    exporter,
    hasCreate,
    currentSort,
    filter,
    filterValues: params.filterValues,
    displayedFilters: params.displayedFilters,
    hideFilter: modifiers.hideFilter,
    showFilter: modifiers.showFilter,
    setFilters: modifiers.setFilters,
    setSort: modifiers.setSort,
    setPage: modifiers.setPage,
    setPerPage: modifiers.setPerPage,
    selectedIds,
    onSelect: selection.select,
    onToggleItem: selection.toggle,
    onUnselectItems: selection.clearSelection,
    defaultTitle: translate('ra.page.list', {
      name: getResourceLabel(resource, 2),
    }),
    ids,
    data,
    page: 1,
    perPage,
    refetch,
    loaded,
    total: active?.total || 0,
    loading: !active || (!loaded && active.status === 'loading'),
    error: active?.error,
    loadingMore: loaded && active.status === 'loading',
    hasMore: !active || active.status !== 'complete',
    loadMore,
    queryKey,
  }
}
