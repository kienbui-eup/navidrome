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
      'TrolyPlayTheme'
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

    // Brand accent for the player chrome: the theme's "play" token (Troly Play: Coral),
    // falling back to the primary colour for themes that don't define one.
    const accentColor = theme.palette?.play?.main || primaryColor
    const toRgb = (c) => {
      const m = /^#?([0-9a-f]{6})$/i.exec((c || '').trim())
      if (!m) return '160, 160, 160'
      const n = parseInt(m[1], 16)
      return `${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}`
    }
    const accentRgb = toRgb(accentColor)
    const stageRgb = toRgb(theme.palette?.stage?.main || '#121110')
    const fontFamily =
      theme.typography?.fontFamily ||
      "'Be Vietnam Pro', 'Inter', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif"

    document.documentElement.style.setProperty('--accent-color', accentColor)
    document.documentElement.style.setProperty('--accent-rgb', accentRgb)
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
      .react-jinke-music-player-main * {
        font-family: ${fontFamily} !important;
      }

      .react-jinke-music-player-main svg:active,
      .react-jinke-music-player-main svg:hover {
        color: var(--accent-color) !important;
        filter: drop-shadow(0 0 4px rgba(var(--accent-rgb), 0.6)) !important;
      }

      .react-jinke-music-player-main .music-player-panel .panel-content .rc-slider-handle,
      .react-jinke-music-player-main .music-player-panel .panel-content .rc-slider-track {
        background-color: var(--accent-color) !important;
      }

      .react-jinke-music-player-main ::-webkit-scrollbar-thumb {
        background-color: rgba(var(--accent-rgb), 0.4) !important;
        border-radius: 4px !important;
      }

      .react-jinke-music-player-main ::-webkit-scrollbar-thumb:hover {
        background-color: var(--accent-color) !important;
      }

      .react-jinke-music-player-main .music-player-panel .panel-content .rc-slider-handle:active {
        box-shadow: 0 0 8px var(--accent-color) !important;
      }

      .react-jinke-music-player-main .audio-item.playing svg {
        color: var(--accent-color) !important;
      }

      .react-jinke-music-player-main .audio-item.playing .player-singer {
        color: var(--accent-color) !important;
      }

      .audio-lists-panel-content .audio-item.playing,
      .audio-lists-panel-content .audio-item.playing svg {
        color: var(--accent-color) !important;
      }

      .audio-lists-panel-content .audio-item:active .group:not([class=".player-delete"]) svg,
      .audio-lists-panel-content .audio-item:hover .group:not([class=".player-delete"]) svg {
        color: var(--accent-color) !important;
      }
      
      /* Floating Capsule bottom player panel container */
      .react-jinke-music-player-main .music-player-panel {
        position: fixed !important;
        bottom: 24px !important;
        left: 24px !important;
        right: 24px !important;
        width: auto !important;
        background-color: rgba(${stageRgb}, 0.88) !important;
        backdrop-filter: blur(24px) !important;
        -webkit-backdrop-filter: blur(24px) !important;
        border: 1px solid rgba(var(--accent-rgb), 0.22) !important;
        border-top: 1px solid rgba(var(--accent-rgb), 0.22) !important;
        border-radius: 16px !important;
        color: #ffffff !important;
        box-shadow: 0 16px 40px rgba(0, 0, 0, 0.65), inset 0 1px 1px rgba(255, 255, 255, 0.1) !important;
        transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1) !important;
        height: 80px !important;
        padding: 0 20px !important;
      }

      /* Album Art rounded rectangle override (no-rotate for pristine audiophile look) */
      .react-jinke-music-player-main .music-player-panel .panel-content .img-rotate {
        animation: none !important;
        border-radius: 8px !important;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.6) !important;
        border: 1px solid rgba(255, 255, 255, 0.1) !important;
        width: 48px !important;
        height: 48px !important;
      }

      /* Slim, elegant progress bar */
      .react-jinke-music-player-main .music-player-panel .panel-content .rc-slider-rail {
        background-color: rgba(255, 255, 255, 0.08) !important;
        height: 3px !important;
      }
      .react-jinke-music-player-main .music-player-panel .panel-content .rc-slider-track {
        height: 3px !important;
        background: var(--accent-color) !important;
      }
      .react-jinke-music-player-main .music-player-panel .panel-content .rc-slider-handle {
        width: 10px !important;
        height: 10px !important;
        margin-top: -3px !important;
        background-color: var(--accent-color) !important;
        border: 2px solid #ffffff !important;
        box-shadow: 0 0 6px rgba(var(--accent-rgb), 0.8) !important;
      }

      .react-jinke-music-player-main .music-player-panel .panel-content .player-content .con {
        color: #ffffff !important;
      }
      .react-jinke-music-player-main .music-player-panel .panel-content .player-content .con .title {
        color: #ffffff !important;
        font-weight: 500 !important;
      }
      .react-jinke-music-player-main .music-player-panel .panel-content .player-content .con .singer {
        color: rgba(255, 255, 255, 0.6) !important;
      }
      
      /* Harmonize bottom player playlist drawer with theme backgrounds */
      .react-jinke-music-player-main .audio-lists-panel {
        position: fixed !important;
        bottom: 114px !important;
        right: 24px !important;
        left: auto !important;
        width: 420px !important;
        background-color: rgba(${stageRgb}, 0.94) !important;
        backdrop-filter: blur(24px) !important;
        border: 1px solid rgba(var(--accent-rgb), 0.25) !important;
        border-radius: 16px !important;
        box-shadow: 0 20px 50px rgba(0, 0, 0, 0.7) !important;
        color: #ffffff !important;
        overflow: hidden !important;
      }
      .react-jinke-music-player-main .audio-lists-panel-header {
        background-color: rgba(${stageRgb}, 0.98) !important;
        border-bottom: 1px solid rgba(var(--accent-rgb), 0.2) !important;
        color: #ffffff !important;
        padding: 12px 20px !important;
      }
      .react-jinke-music-player-main .audio-lists-panel-header .title {
        color: var(--accent-color) !important;
        font-weight: 600 !important;
      }
      .react-jinke-music-player-main .audio-lists-panel-content {
        background-color: transparent !important;
      }
      .react-jinke-music-player-main .audio-lists-panel-content .audio-item {
        background-color: transparent !important;
        color: #ffffff !important;
        border-bottom: 1px solid rgba(255, 255, 255, 0.05) !important;
        padding: 10px 20px !important;
        transition: all 0.2s ease !important;
      }
      .react-jinke-music-player-main .audio-lists-panel-content .audio-item:hover {
        background-color: rgba(255, 255, 255, 0.03) !important;
      }
      .react-jinke-music-player-main .audio-lists-panel-content .audio-item:nth-child(odd) {
        background-color: rgba(255, 255, 255, 0.01) !important;
      }
      .react-jinke-music-player-main .audio-lists-panel-content .audio-item.playing {
        background-color: rgba(var(--accent-rgb), 0.08) !important;
        color: var(--accent-color) !important;
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
