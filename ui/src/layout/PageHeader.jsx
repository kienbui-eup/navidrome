import React, {
  createContext,
  useContext,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import { createPortal } from 'react-dom'
import { makeStyles } from '@material-ui/core/styles'
import clsx from 'clsx'

const HeaderContext = createContext(null)

const useStyles = makeStyles((theme) => ({
  shell: { minWidth: 0 },
  frame: {
    position: 'sticky',
    top: 0,
    zIndex: theme.zIndex.appBar,
    backgroundColor: theme.palette.background.paper,
  },
  slot: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    flex: '1 1 auto',
    minWidth: 0,
    gap: theme.spacing(1),
    [theme.breakpoints.down('md')]: { gridColumn: '1 / -1', gridRow: 2 },
    [theme.breakpoints.down('xs')]: { display: 'contents' },
  },
  target: {
    flex: 1,
    minWidth: 0,
    maxWidth: 960,
    [theme.breakpoints.down('xs')]: { display: 'contents' },
  },
  fallback: {
    minWidth: 0,
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
    [theme.breakpoints.down('xs')]: {
      order: 1,
      gridColumn: '1 / -1',
      gridRow: 2,
    },
  },
  hiddenTitle: {
    position: 'absolute',
    width: 1,
    height: 1,
    padding: 0,
    margin: -1,
    overflow: 'hidden',
    clip: 'rect(0, 0, 0, 0)',
    whiteSpace: 'nowrap',
  },
}))

export const PageHeaderProvider = ({ children }) => {
  const classes = useStyles()
  const [target, setTarget] = useState(null)
  const [owner, setOwner] = useState(null)
  const [height, setHeight] = useState(64)
  const value = useMemo(
    () => ({ target, setTarget, owner, setOwner, setHeight }),
    [target, owner],
  )
  return (
    <HeaderContext.Provider value={value}>
      <div
        className={classes.shell}
        style={{ '--troly-header-height': `${height}px` }}
      >
        {children}
      </div>
    </HeaderContext.Provider>
  )
}

export const PageHeaderFrame = ({ children }) => {
  const classes = useStyles()
  const ref = useRef(null)
  const { setHeight } = useContext(HeaderContext) || {}
  useLayoutEffect(() => {
    if (!setHeight || !ref.current) return
    const measure = () => {
      const height = Math.ceil(ref.current.getBoundingClientRect().height)
      if (height) setHeight(height)
    }
    measure()
    if (typeof ResizeObserver !== 'undefined') {
      const observer = new ResizeObserver(measure)
      observer.observe(ref.current)
      return () => observer.disconnect()
    }
    window.addEventListener('resize', measure)
    return () => window.removeEventListener('resize', measure)
  }, [setHeight])
  return (
    <div ref={ref} className={classes.frame}>
      {children}
    </div>
  )
}

export const PageHeaderSlot = ({ fallback }) => {
  const classes = useStyles()
  const { setTarget, owner } = useContext(HeaderContext) || {}
  return (
    <div className={classes.slot}>
      <div className={clsx(classes.fallback, owner && classes.hiddenTitle)}>
        {fallback}
      </div>
      <div ref={setTarget} className={classes.target} />
    </div>
  )
}

export const PageHeaderContent = ({ children, replaceTitle = true }) => {
  const context = useContext(HeaderContext)
  const owner = useRef(Symbol('page-header'))
  const { target, setOwner } = context || {}
  useLayoutEffect(() => {
    if (!target || !replaceTitle) return
    const currentOwner = owner.current
    setOwner(currentOwner)
    return () => {
      setOwner((active) => (active === currentOwner ? null : active))
    }
  }, [target, setOwner, replaceTitle])
  if (!context) return children
  return target ? createPortal(children, target) : null
}
