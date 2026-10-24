import { getContrastRatio } from '@material-ui/core/styles'
import dark from './trolyPlay'
import light from './trolyPlayLight'

it.each([dark, light])('keeps player text readable in $themeName', (theme) => {
  const style = document.createElement('style')
  style.textContent = theme.player.stylesheet
  document.head.appendChild(style)
  const wrapper = document.createElement('div')
  wrapper.className = 'react-jinke-music-player-main'
  const panel = document.createElement('div')
  panel.className = 'music-player-panel'
  wrapper.appendChild(panel)
  document.body.appendChild(wrapper)
  const expected = document.createElement('div')
  expected.style.color = theme.palette.text.primary
  try {
    expect(getComputedStyle(panel).color).toBe(expected.style.color)
    expect(theme.player.theme).toBe(theme.palette.type)
    expect(
      getContrastRatio(
        theme.palette.play.main,
        theme.overrides.NDAlbumGridView.albumPlayButton.color,
      ),
    ).toBeGreaterThanOrEqual(4.5)
  } finally {
    wrapper.remove()
    style.remove()
  }
})
