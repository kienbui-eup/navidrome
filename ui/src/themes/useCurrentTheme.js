import { useSelector } from 'react-redux'
import useMediaQuery from '@material-ui/core/useMediaQuery'
import themes from './index'
import { AUTO_THEME_ID } from '../consts'
import config from '../config'
import { useEffect } from 'react'

const useCurrentTheme = () => {
  const prefersLightMode = useMediaQuery('(prefers-color-scheme: light)')
  const theme = useSelector((state) => {
    if (state.theme === AUTO_THEME_ID) {
      return prefersLightMode ? themes.LightTheme : themes.DarkTheme
    }
    const themeName =
      Object.keys(themes).find((t) => t === state.theme) ||
      Object.keys(themes).find(
        (t) => themes[t].themeName === config.defaultTheme,
      ) ||
      'DarkTheme'
    return themes[themeName]
  })

  useEffect(() => {
    const styles = document.getElementsByTagName('style')
    let style
    for (let i = 0; i < styles.length; i++) {
      if (styles[i].id === 'vi-player-style-override') {
        style = styles[i]
      }
    }

    // Extract palette values and inject as CSS custom properties (variables)
    const isDark = theme.palette?.type === 'dark'
    const primaryColor = theme.palette?.primary?.main || '#90caf9'
    const primaryLight = theme.palette?.primary?.light || primaryColor
    const primaryDark = theme.palette?.primary?.dark || primaryColor
    const secondaryColor = theme.palette?.secondary?.main || '#f50057'
    const bgColor = theme.palette?.background?.default || (isDark ? '#303030' : '#fafafa')
    const paperColor = theme.palette?.background?.paper || (isDark ? '#424242' : '#fff')
    const textPrimary = theme.palette?.text?.primary || (isDark ? '#ffffff' : 'rgba(0, 0, 0, 0.87)')
    const textSecondary = theme.palette?.text?.secondary || (isDark ? 'rgba(255, 255, 255, 0.7)' : 'rgba(0, 0, 0, 0.54)')

    document.documentElement.style.setProperty('--primary-color', primaryColor)
    document.documentElement.style.setProperty('--primary-light', primaryLight)
    document.documentElement.style.setProperty('--primary-dark', primaryDark)
    document.documentElement.style.setProperty('--secondary-color', secondaryColor)
    document.documentElement.style.setProperty('--bg-default', bgColor)
    document.documentElement.style.setProperty('--bg-paper', paperColor)
    document.documentElement.style.setProperty('--text-primary', textPrimary)
    document.documentElement.style.setProperty('--text-secondary', textSecondary)
    document.documentElement.style.setProperty('--player-bg', isDark ? '#1a1a1a' : '#ffffff')

    // Base harmonizing stylesheet that synchronizes player controls with the theme palette
    const basePlayerStyle = `
      .react-jinke-music-player-main svg:active,
      .react-jinke-music-player-main svg:hover {
        color: var(--primary-color) !important;
      }
      .react-jinke-music-player-main .music-player-panel .panel-content .rc-slider-handle,
      .react-jinke-music-player-main .music-player-panel .panel-content .rc-slider-track {
        background-color: var(--primary-color) !important;
      }
      .react-jinke-music-player-main ::-webkit-scrollbar-thumb {
        background-color: var(--primary-color) !important;
      }
      .react-jinke-music-player-main .music-player-panel .panel-content .rc-slider-handle:active {
        box-shadow: 0 0 2px var(--primary-color) !important;
      }
      .react-jinke-music-player-main .audio-item.playing svg {
        color: var(--primary-color) !important;
      }
      .react-jinke-music-player-main .audio-item.playing .player-singer {
        color: var(--primary-color) !important;
      }
      .audio-lists-panel-content .audio-item.playing,
      .audio-lists-panel-content .audio-item.playing svg {
        color: var(--primary-color) !important;
      }
      .audio-lists-panel-content .audio-item:active .group:not([class=".player-delete"]) svg,
      .audio-lists-panel-content .audio-item:hover .group:not([class=".player-delete"]) svg {
        color: var(--primary-color) !important;
      }
      
      /* Harmonize bottom player panel container with theme default and paper background colors */
      .react-jinke-music-player-main .music-player-panel {
        background-color: var(--bg-paper) !important;
        border-top: 1px solid var(--primary-color) !important;
        color: var(--text-primary) !important;
      }
      .react-jinke-music-player-main .music-player-panel .panel-content .player-content .con {
        color: var(--text-primary) !important;
      }
      .react-jinke-music-player-main .music-player-panel .panel-content .player-content .con .title {
        color: var(--text-primary) !important;
      }
      .react-jinke-music-player-main .music-player-panel .panel-content .player-content .con .singer {
        color: var(--text-secondary) !important;
      }
      
      /* Harmonize bottom player playlist drawer with theme backgrounds */
      .react-jinke-music-player-main .audio-lists-panel {
        background-color: var(--bg-paper) !important;
        border: 1px solid var(--primary-color) !important;
        color: var(--text-primary) !important;
      }
      .react-jinke-music-player-main .audio-lists-panel-header {
        background-color: var(--bg-default) !important;
        border-bottom: 1px solid var(--primary-color) !important;
        color: var(--text-primary) !important;
      }
      .react-jinke-music-player-main .audio-lists-panel-header .title {
        color: var(--text-primary) !important;
      }
      .react-jinke-music-player-main .audio-lists-panel-content {
        background-color: var(--bg-paper) !important;
      }
      .react-jinke-music-player-main .audio-lists-panel-content .audio-item {
        background-color: var(--bg-paper) !important;
        color: var(--text-primary) !important;
        border-bottom: 1px solid rgba(255, 255, 255, 0.05) !important;
      }
      .react-jinke-music-player-main .audio-lists-panel-content .audio-item:nth-child(odd) {
        background-color: var(--bg-default) !important;
      }
      .react-jinke-music-player-main .audio-lists-panel-content .audio-item.playing {
        background-color: var(--bg-default) !important;
        color: var(--primary-color) !important;
      }
    `

    const finalStylesheet = theme.player?.stylesheet
      ? `${basePlayerStyle}\n${theme.player.stylesheet}`
      : basePlayerStyle

    if (style === undefined) {
      style = document.createElement('style')
      style.id = 'vi-player-style-override'
      style.innerHTML = finalStylesheet
      document.head.appendChild(style)
    } else {
      style.innerHTML = finalStylesheet
    }

    // Set body background color to match theme (fixes white background on pull-to-refresh)
    document.body.style.backgroundColor = bgColor
  }, [theme])

  return theme
}

export default useCurrentTheme
