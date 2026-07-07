package nativeapi

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"strconv"
	"time"

	"github.com/Masterminds/squirrel"
	"github.com/go-chi/chi/v5"
	"github.com/navidrome/navidrome/core"
	"github.com/navidrome/navidrome/log"
	"github.com/navidrome/navidrome/model"
)

// addUpgradeRoute registers the (admin-only) Quality Upgrader endpoints, per
// the "API" section of docs/superpowers/specs/2026-07-06-quality-upgrader-design.md:
// start/cancel a candidate scan, poll its progress, list queued candidates and
// approve/reject them (individually or in batch).
func (api *Router) addUpgradeRoute(r chi.Router) {
	r.Route("/upgrade", func(r chi.Router) {
		r.Post("/scan", api.upgradeScanHandler)
		r.Post("/scan/cancel", api.upgradeScanCancelHandler)
		r.Get("/status", api.upgradeStatusHandler)
		r.Route("/candidates", func(r chi.Router) {
			r.Get("/", api.upgradeCandidatesHandler)
			r.Post("/approve-batch", api.upgradeApproveBatchHandler)
			r.Post("/{id}/approve", api.upgradeApproveHandler)
			r.Post("/{id}/reject", api.upgradeRejectHandler)
		})
	})
}

// upgradeCandidateDTO is the fixed wire format the (already committed) admin
// UI reads: every model.UpgradeCandidate field in camelCase (verifyInfo as the
// raw stored JSON string) plus the current track's display info, embedded so
// the UI does not need a second request per row.
type upgradeCandidateDTO struct {
	ID          string    `json:"id"`
	MediaFileID string    `json:"mediaFileId"`
	LibraryID   int       `json:"libraryId"`
	Source      string    `json:"source"`
	SourceRef   string    `json:"sourceRef"`
	Title       string    `json:"title"`
	Format      string    `json:"format"`
	EstBitRate  int       `json:"estBitRate"`
	EstSize     int64     `json:"estSize"`
	MatchScore  int       `json:"matchScore"`
	Status      string    `json:"status"`
	VerifyInfo  string    `json:"verifyInfo"`
	Error       string    `json:"error"`
	ReviewedBy  string    `json:"reviewedBy"`
	CreatedAt   time.Time `json:"createdAt"`
	UpdatedAt   time.Time `json:"updatedAt"`

	// Current-track info, looked up from the media_file table. Left as zero
	// values when the media file has been deleted since the candidate was
	// queued (never an error).
	CurrentTitle   string `json:"currentTitle"`
	CurrentArtist  string `json:"currentArtist"`
	CurrentFormat  string `json:"currentFormat"`
	CurrentBitRate int    `json:"currentBitRate"`
}

func (api *Router) upgradeScanHandler(w http.ResponseWriter, r *http.Request) {
	var body struct {
		LibraryID    int      `json:"libraryId"`
		MediaFileIDs []string `json:"mediaFileIds"`
	}
	// Both fields are optional (empty = scan every library), so an empty body
	// is accepted too.
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil && !errors.Is(err, io.EOF) {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}
	if err := api.upgrader.StartScan(r.Context(), body.LibraryID, body.MediaFileIDs); err != nil {
		upgradeError(w, r, "start upgrade scan", err)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusAccepted)
	if err := json.NewEncoder(w).Encode(map[string]string{"status": "started"}); err != nil {
		log.Error(r.Context(), "Error encoding upgrade response", err)
	}
}

func (api *Router) upgradeScanCancelHandler(w http.ResponseWriter, r *http.Request) {
	api.upgrader.CancelScan()
	writeJSON(w, r, map[string]string{"status": "canceled"})
}

func (api *Router) upgradeStatusHandler(w http.ResponseWriter, r *http.Request) {
	running, scanned, total, found := api.upgrader.ScanStatus()
	writeJSON(w, r, map[string]any{
		"running": running,
		"scanned": scanned,
		"total":   total,
		"found":   found,
	})
}

func (api *Router) upgradeCandidatesHandler(w http.ResponseWriter, r *http.Request) {
	options := model.QueryOptions{Sort: "updated_at", Order: "desc"}
	if status := r.URL.Query().Get("status"); status != "" {
		options.Filters = squirrel.Eq{"status": status}
	}
	// React-admin style pagination: _start/_end map to offset/limit.
	start, _ := strconv.Atoi(r.URL.Query().Get("_start"))
	end, _ := strconv.Atoi(r.URL.Query().Get("_end"))
	if start > 0 {
		options.Offset = start
	}
	if end > start {
		options.Max = end - start
	}

	candidates, err := api.ds.UpgradeCandidate(r.Context()).GetAll(options)
	if err != nil {
		upgradeError(w, r, "list upgrade candidates", err)
		return
	}

	mfRepo := api.ds.MediaFile(r.Context())
	mfCache := map[string]*model.MediaFile{}
	dtos := make([]upgradeCandidateDTO, 0, len(candidates))
	for _, c := range candidates {
		dto := upgradeCandidateDTO{
			ID:          c.ID,
			MediaFileID: c.MediaFileID,
			LibraryID:   c.LibraryID,
			Source:      c.Source,
			SourceRef:   c.SourceRef,
			Title:       c.Title,
			Format:      c.Format,
			EstBitRate:  c.EstBitRate,
			EstSize:     c.EstSize,
			MatchScore:  c.MatchScore,
			Status:      c.Status,
			VerifyInfo:  c.VerifyInfo,
			Error:       c.Error,
			ReviewedBy:  c.ReviewedBy,
			CreatedAt:   c.CreatedAt,
			UpdatedAt:   c.UpdatedAt,
		}
		mf, ok := mfCache[c.MediaFileID]
		if !ok {
			mf, err = mfRepo.Get(c.MediaFileID)
			if err != nil {
				// Deleted/missing media file: keep the zero-valued current-track
				// fields instead of failing the whole listing.
				if !errors.Is(err, model.ErrNotFound) {
					log.Warn(r.Context(), "Upgrade: error loading media file for candidate",
						"candidateId", c.ID, "mediaFileId", c.MediaFileID, err)
				}
				mf = nil
			}
			mfCache[c.MediaFileID] = mf
		}
		if mf != nil {
			dto.CurrentTitle = mf.Title
			dto.CurrentArtist = mf.Artist
			dto.CurrentFormat = mf.Suffix
			dto.CurrentBitRate = mf.BitRate
		}
		dtos = append(dtos, dto)
	}
	writeJSON(w, r, dtos)
}

func (api *Router) upgradeApproveHandler(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Force bool `json:"force"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil && !errors.Is(err, io.EOF) {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}
	if err := api.upgrader.Approve(r.Context(), chi.URLParam(r, "id"), body.Force); err != nil {
		upgradeError(w, r, "approve upgrade candidate", err)
		return
	}
	writeJSON(w, r, map[string]string{"status": "ok"})
}

func (api *Router) upgradeRejectHandler(w http.ResponseWriter, r *http.Request) {
	if err := api.upgrader.Reject(r.Context(), chi.URLParam(r, "id")); err != nil {
		upgradeError(w, r, "reject upgrade candidate", err)
		return
	}
	writeJSON(w, r, map[string]string{"status": "ok"})
}

func (api *Router) upgradeApproveBatchHandler(w http.ResponseWriter, r *http.Request) {
	var body struct {
		IDs []string `json:"ids"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}
	accepted, err := api.upgrader.ApproveBatch(r.Context(), body.IDs)
	if errors.Is(err, core.ErrUpgradeDisabled) {
		upgradeError(w, r, "approve upgrade candidates", err)
		return
	}
	if err != nil {
		// Batch approval is best-effort: candidates that could not be approved
		// are reported by omission from "accepted", so per-candidate errors are
		// only logged.
		log.Warn(r.Context(), "Upgrade: some candidates could not be batch-approved", err)
	}
	if accepted == nil {
		accepted = []string{}
	}
	writeJSON(w, r, map[string][]string{"accepted": accepted})
}

// upgradeError maps Upgrader errors to HTTP status codes (same plain
// http.Error response shape as importError): missing candidate → 404, scan
// already running / status that does not allow the operation → 409, the
// feature's kill switch (conf.Server.Upgrade.Enabled=false) → 403 (same
// convention as the artwork-upload kill switch in image_upload.go), anything
// else → 400.
func upgradeError(w http.ResponseWriter, r *http.Request, action string, err error) {
	log.Warn(r.Context(), "Upgrade: failed to "+action, err)
	switch {
	case errors.Is(err, model.ErrNotFound):
		http.Error(w, err.Error(), http.StatusNotFound)
	case errors.Is(err, core.ErrUpgradeScanInProgress), errors.Is(err, core.ErrUpgradeInvalidStatus):
		http.Error(w, err.Error(), http.StatusConflict)
	case errors.Is(err, core.ErrUpgradeDisabled):
		http.Error(w, err.Error(), http.StatusForbidden)
	default:
		http.Error(w, err.Error(), http.StatusBadRequest)
	}
}
