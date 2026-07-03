import polyglotI18nProvider from 'ra-i18n-polyglot'
import deepmerge from 'deepmerge'
import dataProvider from '../dataProvider'
import en from './en.json'
import { i18nProvider } from './index'
import config from '../config'

// Only returns current selected locale if its translations are found in localStorage
const defaultLocale = function () {
  const locale = localStorage.getItem('locale')
  const current = JSON.parse(localStorage.getItem('translation'))
  if (current && current.id === locale) {
    // Asynchronously reload the translation from the server
    retrieveTranslation(locale).then(() => {
      i18nProvider.changeLocale(locale)
    })
    return locale
  }
  // No user-selected locale yet: honor the server's DefaultLanguage so the
  // Login page and initial render use it too. polyglotI18nProvider requires
  // the initial locale's messages synchronously, so we can only boot straight
  // into the default language when its translation is already cached.
  if (!locale && config.defaultLanguage && config.defaultLanguage !== 'en') {
    if (current && current.id === config.defaultLanguage) {
      return config.defaultLanguage
    }
    // First visit: fetch the translation, persist the locale and reload once.
    // The 'locale' key set before reloading guarantees this cannot loop; on
    // fetch failure (unknown language) we silently stay in English.
    retrieveTranslation(config.defaultLanguage)
      .then(() => {
        const chosen = localStorage.getItem('locale')
        if (!chosen || chosen === config.defaultLanguage) {
          localStorage.setItem('locale', config.defaultLanguage)
          window.location.reload()
        }
      })
      .catch(() => {})
  }
  return 'en'
}

export function retrieveTranslation(locale) {
  return dataProvider.getOne('translation', { id: locale }).then((res) => {
    localStorage.setItem('translation', JSON.stringify(res.data))
    return prepareLanguage(JSON.parse(res.data.data))
  })
}

const removeEmpty = (obj) => {
  for (let k in obj) {
    if (
      Object.prototype.hasOwnProperty.call(obj, k) &&
      typeof obj[k] === 'object'
    ) {
      removeEmpty(obj[k])
    } else {
      if (!obj[k]) {
        delete obj[k]
      }
    }
  }
}

const prepareLanguage = (lang) => {
  removeEmpty(lang)
  // Make "albumSong" and "playlistTrack" resource use the same translations as "song"
  lang.resources.albumSong = lang.resources.song
  lang.resources.playlistTrack = lang.resources.song
  // ra.boolean.null should always be empty
  lang.ra.boolean.null = ''
  // Fallback to english translations
  return deepmerge(en, lang)
}

export default polyglotI18nProvider((locale) => {
  // English is bundled
  if (locale === 'en') {
    return prepareLanguage(en)
  }
  // If the requested locale is in already loaded, return it
  const current = JSON.parse(localStorage.getItem('translation'))
  if (current && current.id === locale) {
    return prepareLanguage(JSON.parse(current.data))
  }
  // If not, get it from the server, and store it in localStorage
  return retrieveTranslation(locale)
}, defaultLocale())
