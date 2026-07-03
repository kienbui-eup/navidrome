// React Hook to get a list of all languages available. English is hardcoded
import { useGetList, useLocale } from 'react-admin'
import { localeToBCP47 } from '../utils'

const useGetLanguageChoices = () => {
  const locale = useLocale()
  const { ids, data, loaded, loading } = useGetList(
    'translation',
    { page: 1, perPage: -1 },
    { field: '', order: '' },
    {},
  )

  const choices = [{ id: 'en', name: 'English' }]
  if (loaded) {
    ids.forEach((id) => choices.push({ id: id, name: data[id].name }))
  }
  // Locale-aware sort so language names order correctly under the active locale
  // (e.g. Vietnamese diacritics with Intl.Collator('vi-VN')).
  const collator = new Intl.Collator(localeToBCP47(locale))
  choices.sort((a, b) => collator.compare(a.name, b.name))

  return { choices, loaded, loading }
}

export default useGetLanguageChoices
