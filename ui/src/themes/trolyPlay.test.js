import { getContrastRatio } from '@material-ui/core/styles'
import dark from './trolyPlay'
import light from './trolyPlayLight'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'

const vendorStylesheet = readFileSync(
  createRequire(import.meta.url).resolve(
    'navidrome-music-player/assets/index.css',
  ),
  'utf8',
)

it('keeps light player icons in the Troly palette', () => {
  const vendor = document.createElement('style')
  vendor.textContent = vendorStylesheet
  const brand = document.createElement('style')
  brand.textContent = light.player.stylesheet
  document.head.append(vendor, brand)
  const player = document.createElement('div')
  player.className = 'react-jinke-music-player-main light-theme'
  player.innerHTML = '<div class="music-player-panel"><svg></svg></div>'
  document.body.appendChild(player)
  try {
    expect(getComputedStyle(player.querySelector('svg')).color).toBe(
      'rgb(32, 34, 58)',
    )
  } finally {
    player.remove()
    vendor.remove()
    brand.remove()
  }
})

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
