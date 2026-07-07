package nativeapi

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"slices"

	"github.com/go-chi/chi/v5"
	"github.com/vi2play/vi2play/core"
	"github.com/vi2play/vi2play/log"
	"github.com/vi2play/vi2play/model/request"
	"github.com/vi2play/vi2play/utils/req"
)

// addDeletionRoute registers the (admin-only) permanent-delete endpoints, per
// the "Routes" section of docs/superpowers/specs/2026-07-07-admin-delete-design.md:
//
//	DELETE /api/song/{id}      – delete one track
//	DELETE /api/song?id=a&id=b – delete many tracks (react-admin deleteMany style,
//	                             same query-param shape as DELETE /api/missing)
//	DELETE /api/album/{id}     – delete every track of an album
//
// These are siblings of the read-only /song and /album resource mounts
// registered by api.R in native_api.go. Coexistence was verified empirically
// against chi v5.3.0 (see "Adjustments" in plans/02-admin-delete.md): the
// explicit DELETE endpoints win over the mounts' catchall, the mounted GET
// routes keep working, and the admin group's inline middleware applies.
func (api *Router) addDeletionRoute(r chi.Router) {
	r.Delete("/song/{id}", api.deleteSongHandler)
	r.Delete("/song", api.deleteSongHandler)
	r.Delete("/album/{id}", api.deleteAlbumHandler)
}

func (api *Router) deleteSongHandler(w http.ResponseWriter, r *http.Request) {
	var ids []string
	if id := chi.URLParam(r, "id"); id != "" {
		ids = []string{id}
	} else {
		ids, _ = req.Params(r).Strings("id")
		ids = slices.DeleteFunc(slices.Clone(ids), func(s string) bool { return s == "" })
	}
	if len(ids) == 0 {
		http.Error(w, "missing id", http.StatusBadRequest)
		return
	}
	if err := api.maintenance.DeleteMediaFiles(r.Context(), ids); err != nil {
		deletionError(w, r, "delete songs", err)
		return
	}
	api.sweepStagingAsync(r.Context())
	writeJSON(w, r, map[string][]string{"ids": ids})
}

func (api *Router) deleteAlbumHandler(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")
	if id == "" {
		http.Error(w, "missing id", http.StatusBadRequest)
		return
	}
	if err := api.maintenance.DeleteAlbum(r.Context(), id); err != nil {
		deletionError(w, r, "delete album", err)
		return
	}
	api.sweepStagingAsync(r.Context())
	writeJSON(w, r, map[string][]string{"ids": {id}})
}

// deletionError maps core.Maintenance delete errors to HTTP status codes: a
// batch blocked by an in-progress upgrade → 409 (the message lists the blocked
// tracks; nothing was deleted), anything else (partial disk failures, DB
// errors) → 500 with the aggregate message. Unknown ids never get here — the
// delete is idempotent and reports them as success.
func deletionError(w http.ResponseWriter, r *http.Request, action string, err error) {
	log.Warn(r.Context(), "Deletion: failed to "+action, err)
	status := http.StatusInternalServerError
	if errors.Is(err, core.ErrUpgradeInProgress) {
		status = http.StatusConflict
	}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if encErr := json.NewEncoder(w).Encode(map[string]string{"error": err.Error()}); encErr != nil {
		log.Error(r.Context(), "Error encoding deletion error response", encErr)
	}
}

// sweepStagingAsync triggers a best-effort sweep of the upgrader's staging
// area after a successful delete, so staged files of candidates that were just
// cascade-deleted are cleaned immediately instead of waiting for the next
// startup/queue-drain sweep (design doc, "Tích hợp Quality Upgrader" #4). It
// runs detached from the request context — same pattern as
// maintenanceService.refreshStatsAsync — because the sweep must not be
// canceled when the HTTP request finishes.
func (api *Router) sweepStagingAsync(ctx context.Context) {
	if api.upgrader == nil {
		return
	}
	bgCtx := request.AddValues(context.Background(), ctx)
	go api.upgrader.SweepStaging(bgCtx)
}
