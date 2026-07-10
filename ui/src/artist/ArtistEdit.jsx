import React from 'react'
import { Edit, SimpleForm, TextInput } from 'react-admin'

const ArtistEdit = (props) => (
  <Edit {...props} mutationMode="pessimistic">
    <SimpleForm>
      <TextInput source="name" label="Artist Name" fullWidth required />
      <TextInput source="sortArtistName" label="Sort Artist Name" fullWidth />
      <TextInput source="mbzArtistId" label="MusicBrainz ID" fullWidth />
      <TextInput source="biography" label="Biography" multiline rows={10} fullWidth />
    </SimpleForm>
  </Edit>
)

export default ArtistEdit
