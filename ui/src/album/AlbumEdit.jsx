import React from 'react'
import { Edit, SimpleForm, TextInput, NumberInput } from 'react-admin'

const AlbumEdit = (props) => (
  <Edit {...props} mutationMode="pessimistic">
    <SimpleForm>
      <TextInput source="name" label="Album Title" fullWidth required />
      <TextInput source="albumArtist" label="Album Artist" fullWidth />
      <NumberInput source="minYear" label="Release Year" fullWidth />
      <TextInput source="sortAlbumName" label="Sort Album Title" fullWidth />
      <TextInput source="mbzArtistId" label="MusicBrainz ID" fullWidth />
      <TextInput source="description" label="Description" multiline rows={10} fullWidth />
    </SimpleForm>
  </Edit>
)

export default AlbumEdit
