import React, { useEffect, useRef } from 'react'
import {
  Button,
  CircularProgress,
  Typography,
  makeStyles,
} from '@material-ui/core'
import { ListContextProvider, useListContext, useTranslate } from 'react-admin'
import { useContinuousList } from './useContinuousList'

const useStyles = makeStyles((theme) => ({
  end: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    gap: theme.spacing(1),
    minHeight: 64,
    padding: theme.spacing(1, 2),
    boxSizing: 'border-box',
    color: theme.palette.text.secondary,
  },
  loading: { display: 'flex', alignItems: 'center', gap: theme.spacing(1) },
  button: { minHeight: 44 },
}))

export const ContinuousListBase = ({ children, ...props }) => {
  const controller = useContinuousList(props)
  useEffect(() => {
    const root = document.getElementById('main-content')
    if (root) root.scrollTop = 0
  }, [controller.queryKey])
  return (
    <ListContextProvider value={controller}>{children}</ListContextProvider>
  )
}

export const ContinuousListEnd = () => {
  const { ids, loading, loadingMore, hasMore, loadMore, error, queryKey } =
    useListContext()
  const translate = useTranslate()
  const classes = useStyles()
  const ref = useRef(null)
  useEffect(() => {
    if (
      loading ||
      loadingMore ||
      error ||
      !hasMore ||
      !ref.current ||
      typeof IntersectionObserver === 'undefined'
    )
      return
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) loadMore()
      },
      { root: ref.current.closest('#main-content'), rootMargin: '240px 0px' },
    )
    observer.observe(ref.current)
    return () => observer.disconnect()
  }, [ids.length, loading, loadingMore, error, hasMore, loadMore, queryKey])

  return (
    <div ref={ref} className={classes.end} data-testid="continuous-list-end">
      {loading || loadingMore ? (
        <div role="status" className={classes.loading}>
          <CircularProgress size={20} aria-hidden="true" />
          <Typography>
            {translate('ra.continuous.loading', { _: 'Loading more…' })}
          </Typography>
        </div>
      ) : error ? (
        <>
          <Typography role="alert">
            {translate('ra.continuous.error', {
              _: 'Unable to load more results.',
            })}
          </Typography>
          <Button className={classes.button} onClick={loadMore}>
            {translate('ra.continuous.retry', { _: 'Try again' })}
          </Button>
        </>
      ) : hasMore ? (
        <Button className={classes.button} onClick={loadMore}>
          {translate('ra.continuous.loadMore', { _: 'Load more' })}
        </Button>
      ) : ids.length > 0 ? (
        <Typography role="status">
          {translate('ra.continuous.end', { _: 'All results loaded' })}
        </Typography>
      ) : (
        <Typography role="status">
          {translate('ra.navigation.no_results', { _: 'No results found' })}
        </Typography>
      )}
    </div>
  )
}
