package nativeapi

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
	"github.com/navidrome/navidrome/core"
	"github.com/navidrome/navidrome/log"
)

// addImportRoute registers the (admin-only) music import endpoints. These import
// audio from legal public sources — a direct URL / podcast RSS feed, or the
// Internet Archive's open catalog — into the default library folder.
func (api *Router) addImportRoute(r chi.Router) {
	r.Route("/import", func(r chi.Router) {
		r.Post("/url", api.importURLHandler)
		r.Post("/feed", api.importFeedHandler)
		r.Post("/scan", api.importScanHandler)
		r.Route("/archive", func(r chi.Router) {
			r.Get("/search", api.archiveSearchHandler)
			r.Get("/files", api.archiveFilesHandler)
			r.Post("/", api.archiveImportHandler)
		})
		r.Route("/drive", func(r chi.Router) {
			r.Post("/list", api.driveListHandler)
			r.Post("/file", api.driveImportHandler)
		})
		r.Route("/job", func(r chi.Router) {
			r.Post("/", api.importJobStartHandler)
			r.Get("/{id}", api.importJobStatusHandler)
			r.Post("/{id}/cancel", api.importJobCancelHandler)
		})
		r.Get("/history", api.importHistoryHandler)
	})
}

func (api *Router) importURLHandler(w http.ResponseWriter, r *http.Request) {
	var body struct {
		URL       string `json:"url"`
		LibraryID int    `json:"libraryId"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}
	res, err := api.importer.ImportURL(r.Context(), body.URL, body.LibraryID)
	if err != nil {
		importError(w, r, "import from URL", err)
		return
	}
	writeJSON(w, r, res)
}

func (api *Router) importFeedHandler(w http.ResponseWriter, r *http.Request) {
	var body struct {
		URL string `json:"url"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}
	items, err := api.importer.ParseFeed(r.Context(), body.URL)
	if err != nil {
		importError(w, r, "parse feed", err)
		return
	}
	writeJSON(w, r, items)
}

func (api *Router) archiveSearchHandler(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query().Get("q")
	rows, _ := strconv.Atoi(r.URL.Query().Get("rows"))
	items, err := api.importer.SearchArchive(r.Context(), q, rows)
	if err != nil {
		importError(w, r, "search Internet Archive", err)
		return
	}
	writeJSON(w, r, items)
}

func (api *Router) archiveFilesHandler(w http.ResponseWriter, r *http.Request) {
	id := r.URL.Query().Get("id")
	files, err := api.importer.ArchiveFiles(r.Context(), id)
	if err != nil {
		importError(w, r, "list Internet Archive files", err)
		return
	}
	writeJSON(w, r, files)
}

func (api *Router) archiveImportHandler(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Identifier string `json:"identifier"`
		Filename   string `json:"filename"`
		LibraryID  int    `json:"libraryId"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}
	res, err := api.importer.ImportArchive(r.Context(), body.Identifier, body.Filename, body.LibraryID)
	if err != nil {
		importError(w, r, "import from Internet Archive", err)
		return
	}
	writeJSON(w, r, res)
}

func (api *Router) driveListHandler(w http.ResponseWriter, r *http.Request) {
	var body struct {
		URL string `json:"url"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}
	files, err := api.importer.ListDrive(r.Context(), body.URL)
	if err != nil {
		importError(w, r, "list Google Drive folder", err)
		return
	}
	writeJSON(w, r, files)
}

func (api *Router) driveImportHandler(w http.ResponseWriter, r *http.Request) {
	var body struct {
		ID        string `json:"id"`
		Name      string `json:"name"`
		LibraryID int    `json:"libraryId"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}
	res, err := api.importer.ImportDriveFile(r.Context(), body.ID, body.Name, body.LibraryID)
	if err != nil {
		importError(w, r, "import from Google Drive", err)
		return
	}
	writeJSON(w, r, res)
}

func (api *Router) importJobStartHandler(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Items     []core.ImportJobItem `json:"items"`
		LibraryID int                  `json:"libraryId"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}
	id, err := api.importer.StartImportJob(r.Context(), body.Items, body.LibraryID)
	if err != nil {
		importError(w, r, "start import job", err)
		return
	}
	writeJSON(w, r, map[string]string{"jobId": id})
}

func (api *Router) importJobStatusHandler(w http.ResponseWriter, r *http.Request) {
	job, ok := api.importer.GetImportJob(chi.URLParam(r, "id"))
	if !ok {
		http.Error(w, "job not found", http.StatusNotFound)
		return
	}
	writeJSON(w, r, job)
}

func (api *Router) importJobCancelHandler(w http.ResponseWriter, r *http.Request) {
	api.importer.CancelImportJob(chi.URLParam(r, "id"))
	writeJSON(w, r, map[string]string{"status": "canceling"})
}

func (api *Router) importHistoryHandler(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, r, api.importer.History(r.Context()))
}

func (api *Router) importScanHandler(w http.ResponseWriter, r *http.Request) {
	api.importer.TriggerScan(r.Context())
	writeJSON(w, r, map[string]string{"status": "scan_started"})
}

func writeJSON(w http.ResponseWriter, r *http.Request, v any) {
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(v); err != nil {
		log.Error(r.Context(), "Error encoding import response", err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
	}
}

func importError(w http.ResponseWriter, r *http.Request, action string, err error) {
	log.Warn(r.Context(), "Import: failed to "+action, err)
	http.Error(w, err.Error(), http.StatusBadRequest)
}
