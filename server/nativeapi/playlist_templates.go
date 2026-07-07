package nativeapi

import (
	"encoding/json"
	"errors"
	"net/http"

	"github.com/vi2play/vi2play/core/playlists"
	"github.com/vi2play/vi2play/log"
)

// templateDTO is the wire representation of a playlist.Template, returned by
// GET /playlist/template. It intentionally omits the Criteria, which is an
// implementation detail of the template, not something the client edits.
type templateDTO struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Description string `json:"description"`
}

// listPlaylistTemplates handles GET /playlist/template, returning the catalog
// of built-in smart playlist templates. Available to any authenticated user
// (not admin-only), following the same auth group as the rest of /playlist.
func listPlaylistTemplates(pls playlists.Playlists) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		tpls := pls.ListTemplates()
		dtos := make([]templateDTO, len(tpls))
		for i, t := range tpls {
			dtos[i] = templateDTO{ID: t.ID, Name: t.Name, Description: t.Description}
		}
		writeJSON(w, r, dtos)
	}
}

// createPlaylistFromTemplate handles POST /playlist/template, creating a new
// smart playlist for the current user (owner derived from request context) from
// a built-in template. Available to any authenticated user, not admin-only.
func createPlaylistFromTemplate(pls playlists.Playlists) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var body struct {
			TemplateID string `json:"templateId"`
			Name       string `json:"name"`
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			http.Error(w, "invalid request body", http.StatusBadRequest)
			return
		}
		if body.TemplateID == "" {
			http.Error(w, "templateId is required", http.StatusBadRequest)
			return
		}
		pl, err := pls.CreateFromTemplate(r.Context(), body.TemplateID, body.Name)
		switch {
		case errors.Is(err, playlists.ErrTemplateNotFound):
			http.Error(w, err.Error(), http.StatusNotFound)
			return
		case err != nil:
			log.Error(r.Context(), "Error creating playlist from template", "templateId", body.TemplateID, err)
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusCreated)
		if err := json.NewEncoder(w).Encode(pl); err != nil {
			log.Error(r.Context(), "Error encoding playlist response", err)
		}
	}
}
