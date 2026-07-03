import React, { useCallback } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { Layout as RALayout, toggleSidebar, useTranslate } from 'react-admin'
import { makeStyles } from '@material-ui/core/styles'
import { HotKeys } from 'react-hotkeys'
import Menu from './Menu'
import AppBar from './AppBar'
import Notification from './Notification'
import useCurrentTheme from '../themes/useCurrentTheme'
import { useSearchRefocus } from '../common'

const useStyles = makeStyles((theme) => ({
  root: { paddingBottom: (props) => (props.addPadding ? '80px' : 0) },
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
    <HotKeys handlers={keyHandlers}>
      <a href="#main-content" className={classes.skipLink} onClick={handleSkip}>
        {translate('ra.navigation.skip_nav', { _: 'Skip to main content' })}
      </a>
      <RALayout
        {...props}
        className={classes.root}
        menu={Menu}
        appBar={AppBar}
        theme={theme}
        notification={Notification}
      />
    </HotKeys>
  )
}

export default Layout
