import React, { useCallback } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import {
  Layout as RALayout,
  Sidebar as RASidebar,
  toggleSidebar,
  useTranslate,
} from 'react-admin'
import { makeStyles } from '@material-ui/core/styles'
import { HotKeys } from 'react-hotkeys'
import Menu from './Menu'
import AppBar from './AppBar'
import Notification from './Notification'
import useCurrentTheme from '../themes/useCurrentTheme'
import { useSearchRefocus } from '../common'
import { PageHeaderProvider } from './PageHeader'

const useStyles = makeStyles((theme) => ({
  root: {
    '--troly-player-height': (props) => (props.addPadding ? '80px' : '0px'),
    paddingBottom: 'var(--troly-player-height)',
    '&&': {
      height: '100dvh',
      minHeight: 0,
      minWidth: 0,
      overflow: 'hidden',
      boxSizing: 'border-box',
    },
  },
  appFrame: { '&&': { marginTop: 0, minHeight: 0 } },
  contentWithSidebar: { minHeight: 0, overflow: 'hidden' },
  content: {
    '&&': { minHeight: 0, minWidth: 0, overflow: 'auto' },
  },
  sidebar: {
    '&&': {
      height:
        'calc(100dvh - var(--troly-header-height, 64px) - var(--troly-player-height, 0px))',
      overflow: 'hidden',
      paddingTop: 0,
    },
  },
  // Keyboard "skip to main content" link: off-screen until focused.
  skipLink: {
    position: 'absolute',
    left: -9999,
    top: 0,
    zIndex: 10000,
    padding: theme.spacing(1, 2),
    borderRadius: theme.shape.borderRadius,
    backgroundColor: theme.palette.background.paper,
    color: theme.palette.text.primary,
    '&:focus': {
      left: theme.spacing(1),
      top: theme.spacing(1),
    },
  },
}))

const Sidebar = (props) => {
  const classes = useStyles(props)
  return (
    <RASidebar
      {...props}
      classes={{ root: classes.sidebar, fixed: classes.sidebar }}
    />
  )
}

const Layout = (props) => {
  const theme = useCurrentTheme()
  const translate = useTranslate()
  const queue = useSelector((state) => state.player?.queue)
  const classes = useStyles({ addPadding: queue.length > 0 })
  const dispatch = useDispatch()
  useSearchRefocus()

  const keyHandlers = {
    TOGGLE_MENU: useCallback(() => dispatch(toggleSidebar()), [dispatch]),
  }

  const handleSkip = useCallback((e) => {
    e.preventDefault()
    const main = document.querySelector(
      'main, [role="main"], [class*="RaLayout-content"]',
    )
    if (main) {
      main.setAttribute('tabindex', '-1')
      main.focus()
      main.scrollIntoView()
    }
  }, [])

  return (
    <PageHeaderProvider>
      <HotKeys handlers={keyHandlers}>
        <a
          href="#main-content"
          className={classes.skipLink}
          onClick={handleSkip}
        >
          {translate('ra.navigation.skip_nav', { _: 'Skip to main content' })}
        </a>
        <RALayout
          {...props}
          className={classes.root}
          menu={Menu}
          sidebar={Sidebar}
          appBar={AppBar}
          classes={{
            appFrame: classes.appFrame,
            contentWithSidebar: classes.contentWithSidebar,
            content: classes.content,
          }}
          theme={theme}
          notification={Notification}
        />
      </HotKeys>
    </PageHeaderProvider>
  )
}

export default Layout
