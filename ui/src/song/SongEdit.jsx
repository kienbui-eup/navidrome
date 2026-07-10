import React from 'react'
import { Edit, SimpleForm, TextInput, NumberInput } from 'react-admin'

const formatLyrics = (value) => {
  if (!value) return ''
  if (typeof value === 'string' && value.trim().startsWith('[')) {
    try {
      const lyricList = JSON.parse(value)
      if (Array.isArray(lyricList) && lyricList.length > 0) {
        const lyricsObj = lyricList[0]
        if (lyricsObj.line && Array.isArray(lyricsObj.line)) {
          return lyricsObj.line
            .map((l) => {
              if (l.time !== undefined && l.time >= 0) {
                const totalSeconds = l.time / 1000
                const minutes = Math.floor(totalSeconds / 60)
                const seconds = (totalSeconds % 60).toFixed(2)
                const mm = String(minutes).padStart(2, '0')
                const ss = String(seconds).padStart(5, '0')
                return `[${mm}:${ss}]${l.value || ''}`
              }
              return l.value || ''
            })
            .join('\n')
        }
      }
    } catch (e) {
      // Return as-is if parsing fails
    }
  }
  return value
}

const parseLyrics = (value) => {
  return value // Let Go server parse timed LRC or raw text on the backend
}

const SongEdit = (props) => (
  <Edit {...props} mutationMode="pessimistic">
    <SimpleForm>
      <TextInput source="title" label="Title" fullWidth required />
      <NumberInput source="trackNumber" label="Track Number" fullWidth />
      <NumberInput source="discNumber" label="Disc Number" fullWidth />
      <NumberInput source="year" label="Year" fullWidth />
      <TextInput source="genre" label="Genre" fullWidth />
      <TextInput
        source="lyrics"
        label="Lyrics (Plain Text or LRC Timed format like [00:15.00]Hello)"
        multiline
        rows={12}
        fullWidth
        format={formatLyrics}
        parse={parseLyrics}
      />
    </SimpleForm>
  </Edit>
)

export default SongEdit
