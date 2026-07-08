import md5 from 'blueimp-md5'
import config from '../config'

// Fixed client-side salt used by the embedded Aonsoku player (utils/salt.ts)
// to derive its stored Subsonic token: token = md5(password + PLAYER_SALT).
export const PLAYER_SALT = '40n50kuPl4y3r'
// Aonsoku's zustand persist key and version (store/app.store.ts)
const PLAYER_STORE_KEY = 'app_store'
const PLAYER_STORE_VERSION = 1
const AUTH_TYPE_TOKEN = 1 // AuthType.TOKEN in player/src/types/serverConfig.ts

// Writes the player's persisted config so a login on the admin UI also signs
// the user into the embedded player. Both apps share the same origin, so
// they share localStorage.
export const syncPlayerSession = (username, password) => {
  try {
    const existing = JSON.parse(localStorage.getItem(PLAYER_STORE_KEY) || '{}')
    const state = existing.state || {}
    state.data = {
      ...(state.data || {}),
      isServerConfigured: true,
      url: window.location.origin + (config.baseURL || ''),
      username,
      password: md5(password + PLAYER_SALT),
      authType: AUTH_TYPE_TOKEN,
    }
    localStorage.setItem(
      PLAYER_STORE_KEY,
      JSON.stringify({
        ...existing,
        state,
        version: existing.version ?? PLAYER_STORE_VERSION,
      }),
    )
  } catch {
    // A malformed player store must never block the admin login
  }
}

// Reads the player's persisted Subsonic token credentials, if the user is
// signed into the embedded player. Used to auto-login the admin UI.
export const readPlayerSession = () => {
  try {
    const stored = JSON.parse(localStorage.getItem(PLAYER_STORE_KEY) || '{}')
    const data = stored?.state?.data
    if (
      data?.isServerConfigured &&
      data.username &&
      data.password &&
      data.authType === AUTH_TYPE_TOKEN
    ) {
      return { username: data.username, token: data.password }
    }
  } catch {
    // fall through
  }
  return null
}
