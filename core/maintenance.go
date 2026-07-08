package core

import (
	"context"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"slices"
	"strings"
	"sync"
	"time"

	"github.com/Masterminds/squirrel"
	"github.com/navidrome/navidrome/log"
	"github.com/navidrome/navidrome/model"
	"github.com/navidrome/navidrome/model/request"
	"github.com/navidrome/navidrome/utils/slice"
)

// ErrUpgradeInProgress is returned by DeleteMediaFiles/DeleteAlbum when one or
// more of the target media files has an upgrade candidate in status
// "approved" or "downloading". Deleting the original file while the upgrader
// worker may be about to download/verify/replace it would race with that
// pipeline, so the whole batch is rejected before any file is touched (see
// docs/superpowers/specs/2026-07-07-admin-delete-design.md, "Chặn xoá khi
// đang upgrade"). Use errors.Is to check for it; the wrapped message lists
// the blocked track ids/titles.
var ErrUpgradeInProgress = errors.New("cannot delete: an upgrade is in progress for one or more tracks")

type Maintenance interface {
	// DeleteMissingFiles deletes specific missing files by their IDs
	DeleteMissingFiles(ctx context.Context, ids []string) error
	// DeleteAllMissingFiles deletes all files marked as missing
	DeleteAllMissingFiles(ctx context.Context) error
	// DeleteMediaFiles permanently deletes the given media files: removes the
	// audio file from disk (idempotent if already absent) and the DB record.
	// IDs that don't exist in the DB are skipped silently. If any file fails
	// to be removed from disk (e.g. a permission error), its DB row is kept
	// and the error is included in the returned (joined) error, but the other
	// files in the batch are still deleted. Returns ErrUpgradeInProgress
	// (deleting nothing) if any target id has an "approved" or "downloading"
	// upgrade candidate.
	DeleteMediaFiles(ctx context.Context, ids []string) error
	// DeleteAlbum permanently deletes every media file belonging to albumID,
	// using the same semantics as DeleteMediaFiles. A no-op (no error) if the
	// album has no tracks.
	DeleteAlbum(ctx context.Context, albumID string) error
}

type maintenanceService struct {
	ds model.DataStore
	wg sync.WaitGroup
}

func NewMaintenance(ds model.DataStore) Maintenance {
	return &maintenanceService{
		ds: ds,
	}
}

func (s *maintenanceService) DeleteMissingFiles(ctx context.Context, ids []string) error {
	return s.deleteMissing(ctx, ids)
}

func (s *maintenanceService) DeleteAllMissingFiles(ctx context.Context) error {
	return s.deleteMissing(ctx, nil)
}

// requireAdmin is defense-in-depth: the HTTP routes are already inside the
// admin-only group, but files are removed from disk before the repo-level
// admin check in DeleteMissing would run, so re-check here to guarantee a
// misrouted call can never unlink files.
func requireAdmin(ctx context.Context) error {
	if user, ok := request.UserFrom(ctx); !ok || !user.IsAdmin {
		return model.ErrNotAuthorized
	}
	return nil
}

func (s *maintenanceService) DeleteMediaFiles(ctx context.Context, ids []string) error {
	if err := requireAdmin(ctx); err != nil {
		return err
	}
	if len(ids) == 0 {
		return nil
	}
	mfs, err := s.fetchMediaFilesByID(ctx, ids)
	if err != nil {
		return err
	}
	return s.deleteWithConflictCheck(ctx, mfs)
}

func (s *maintenanceService) DeleteAlbum(ctx context.Context, albumID string) error {
	if err := requireAdmin(ctx); err != nil {
		return err
	}
	if albumID == "" {
		return nil
	}
	mfs, err := s.ds.MediaFile(ctx).GetAll(model.QueryOptions{
		Filters: squirrel.Eq{"album_id": albumID},
	})
	if err != nil {
		return err
	}
	// The SQL filter above is best-effort (mocks in tests ignore Filters and
	// return everything), so re-check in-process, same defensive pattern used
	// by upgrader_apply.go's sweepStaging.
	filtered := make(model.MediaFiles, 0, len(mfs))
	for _, mf := range mfs {
		if mf.AlbumID == albumID {
			filtered = append(filtered, mf)
		}
	}
	return s.deleteWithConflictCheck(ctx, filtered)
}

// fetchMediaFilesByID resolves ids to their MediaFile rows, silently skipping
// any id that no longer exists in the DB (idempotent: deleting an
// already-gone track is a success, not an error).
func (s *maintenanceService) fetchMediaFilesByID(ctx context.Context, ids []string) (model.MediaFiles, error) {
	mfs := make(model.MediaFiles, 0, len(ids))
	for _, id := range ids {
		mf, err := s.ds.MediaFile(ctx).Get(id)
		if err != nil {
			if errors.Is(err, model.ErrNotFound) {
				continue
			}
			return nil, err
		}
		mfs = append(mfs, *mf)
	}
	return mfs, nil
}

// deleteWithConflictCheck is the shared entry point for DeleteMediaFiles and
// DeleteAlbum: check for in-progress upgrades across the whole set of mfs
// before touching any file (so a conflicting batch fails clean, with nothing
// removed), then delegate to deleteFiles.
func (s *maintenanceService) deleteWithConflictCheck(ctx context.Context, mfs model.MediaFiles) error {
	if len(mfs) == 0 {
		return nil
	}
	ids := make([]string, len(mfs))
	for i, mf := range mfs {
		ids[i] = mf.ID
	}
	if err := s.checkUpgradeConflicts(ctx, ids); err != nil {
		return err
	}
	return s.deleteFiles(ctx, mfs)
}

// checkUpgradeConflicts returns ErrUpgradeInProgress (wrapped, listing the
// blocked track titles/ids) if any of ids has an upgrade candidate currently
// "approved" or "downloading" — i.e. the upgrader worker may be about to
// download/verify/replace that file.
func (s *maintenanceService) checkUpgradeConflicts(ctx context.Context, ids []string) error {
	idSet := make(map[string]bool, len(ids))
	for _, id := range ids {
		idSet[id] = true
	}
	candidates, err := s.ds.UpgradeCandidate(ctx).GetAll(model.QueryOptions{
		Filters: squirrel.And{
			squirrel.Eq{"media_file_id": ids},
			squirrel.Eq{"status": []string{model.UpgradeCandidateStatusApproved, model.UpgradeCandidateStatusDownloading}},
		},
	})
	if err != nil {
		return fmt.Errorf("checking upgrade candidates: %w", err)
	}
	var blocked []string
	for _, c := range candidates {
		// The SQL filter above is best-effort (mocks ignore it), so re-check
		// in-process — same defensive pattern as sweepStaging.
		if !idSet[c.MediaFileID] {
			continue
		}
		if c.Status != model.UpgradeCandidateStatusApproved && c.Status != model.UpgradeCandidateStatusDownloading {
			continue
		}
		blocked = append(blocked, fmt.Sprintf("%s (%s)", c.Title, c.MediaFileID))
	}
	if len(blocked) == 0 {
		return nil
	}
	return fmt.Errorf("%w: %s", ErrUpgradeInProgress, strings.Join(blocked, ", "))
}

// deleteFiles removes the audio file for each mf from disk, then reuses the
// existing missing-files pipeline (MarkMissing → deleteMissing) to drop the
// DB rows for the ones that were successfully removed (or already absent).
// Files that fail to be removed (e.g. a permission error) keep their DB row;
// the error is reported but doesn't stop the rest of the batch from being
// deleted.
func (s *maintenanceService) deleteFiles(ctx context.Context, mfs model.MediaFiles) error {
	if len(mfs) == 0 {
		return nil
	}
	var okIDs []string
	var okMfs []*model.MediaFile
	var errs []error
	for i := range mfs {
		mf := &mfs[i]
		err := os.Remove(mf.AbsolutePath())
		if err != nil && !os.IsNotExist(err) {
			log.Error(ctx, "Error deleting media file from disk", "id", mf.ID, "path", mf.AbsolutePath(), err)
			// The aggregated error reaches the HTTP response body; unwrap the
			// PathError so the absolute library path is not exposed to clients.
			var pathErr *fs.PathError
			if errors.As(err, &pathErr) {
				err = pathErr.Err
			}
			errs = append(errs, fmt.Errorf("%s (%s): %w", mf.Title, mf.ID, err))
			continue
		}
		okIDs = append(okIDs, mf.ID)
		okMfs = append(okMfs, mf)
	}

	if len(okIDs) > 0 {
		// Mark-missing-before-delete: getAffectedAlbumIDs and
		// MediaFileRepository.DeleteMissing both filter on missing=true, so
		// marking first lets deleteMissing's pipeline (tx delete → GC →
		// stats refresh) be reused as-is, with no new persistence method.
		if err := s.ds.MediaFile(ctx).MarkMissing(true, okMfs...); err != nil {
			log.Error(ctx, "Error marking media files missing before delete", "ids", okIDs, err)
			return fmt.Errorf("marking media files missing: %w", err)
		}
		if err := s.deleteMissing(ctx, okIDs); err != nil {
			return err
		}
	}

	if len(errs) > 0 {
		return fmt.Errorf("deleted %d/%d files, %d failed: %w", len(okIDs), len(mfs), len(errs), errors.Join(errs...))
	}
	return nil
}

// deleteMissing handles the deletion of missing files and triggers necessary cleanup operations
func (s *maintenanceService) deleteMissing(ctx context.Context, ids []string) error {
	// Track affected album IDs before deletion for refresh
	affectedAlbumIDs, err := s.getAffectedAlbumIDs(ctx, ids)
	if err != nil {
		log.Warn(ctx, "Error tracking affected albums for refresh", err)
		// Don't fail the operation, just log the warning
	}

	// Delete missing files within a transaction
	err = s.ds.WithTx(func(tx model.DataStore) error {
		if len(ids) == 0 {
			_, err := tx.MediaFile(ctx).DeleteAllMissing()
			return err
		}
		return tx.MediaFile(ctx).DeleteMissing(ids)
	})
	if err != nil {
		log.Error(ctx, "Error deleting missing tracks from DB", "ids", ids, err)
		return err
	}

	// Run garbage collection to clean up orphaned records
	if err := s.ds.GC(ctx); err != nil {
		log.Error(ctx, "Error running GC after deleting missing tracks", err)
		return err
	}

	// Refresh statistics in background
	s.refreshStatsAsync(ctx, affectedAlbumIDs)

	return nil
}

// refreshAlbums recalculates album attributes (size, duration, song count, etc.) from media files.
// It uses batch queries to minimize database round-trips for efficiency.
func (s *maintenanceService) refreshAlbums(ctx context.Context, albumIDs []string) error {
	if len(albumIDs) == 0 {
		return nil
	}

	log.Debug(ctx, "Refreshing albums", "count", len(albumIDs))

	// Process in chunks to avoid query size limits
	const chunkSize = 100
	for chunk := range slice.CollectChunks(slices.Values(albumIDs), chunkSize) {
		if err := s.refreshAlbumChunk(ctx, chunk); err != nil {
			return fmt.Errorf("refreshing album chunk: %w", err)
		}
	}

	log.Debug(ctx, "Successfully refreshed albums", "count", len(albumIDs))
	return nil
}

// refreshAlbumChunk processes a single chunk of album IDs
func (s *maintenanceService) refreshAlbumChunk(ctx context.Context, albumIDs []string) error {
	albumRepo := s.ds.Album(ctx)
	mfRepo := s.ds.MediaFile(ctx)

	// Batch load existing albums
	albums, err := albumRepo.GetAll(model.QueryOptions{
		Filters: squirrel.Eq{"album.id": albumIDs},
	})
	if err != nil {
		return fmt.Errorf("loading albums: %w", err)
	}

	// Create a map for quick lookup
	albumMap := make(map[string]*model.Album, len(albums))
	for i := range albums {
		albumMap[albums[i].ID] = &albums[i]
	}

	// Batch load all media files for these albums
	mediaFiles, err := mfRepo.GetAll(model.QueryOptions{
		Filters: squirrel.Eq{"album_id": albumIDs},
		Sort:    "album_id, path",
	})
	if err != nil {
		return fmt.Errorf("loading media files: %w", err)
	}

	// Group media files by album ID
	filesByAlbum := make(map[string]model.MediaFiles)
	for i := range mediaFiles {
		albumID := mediaFiles[i].AlbumID
		filesByAlbum[albumID] = append(filesByAlbum[albumID], mediaFiles[i])
	}

	// Recalculate each album from its media files
	for albumID, oldAlbum := range albumMap {
		mfs, hasTracks := filesByAlbum[albumID]
		if !hasTracks {
			// Album has no tracks anymore, skip (will be cleaned up by GC)
			log.Debug(ctx, "Skipping album with no tracks", "albumID", albumID)
			continue
		}

		// Recalculate album from media files
		newAlbum := mfs.ToAlbum()

		// Only update if something changed (avoid unnecessary writes)
		if !oldAlbum.Equals(newAlbum) {
			// Preserve original timestamps
			newAlbum.UpdatedAt = time.Now()
			newAlbum.CreatedAt = oldAlbum.CreatedAt

			if err := albumRepo.Put(&newAlbum); err != nil {
				log.Error(ctx, "Error updating album during refresh", "albumID", albumID, err)
				// Continue with other albums instead of failing entirely
				continue
			}
			log.Trace(ctx, "Refreshed album", "albumID", albumID, "name", newAlbum.Name)
		}
	}

	return nil
}

// getAffectedAlbumIDs returns distinct album IDs from missing media files
func (s *maintenanceService) getAffectedAlbumIDs(ctx context.Context, ids []string) ([]string, error) {
	var filters squirrel.Sqlizer = squirrel.Eq{"missing": true}
	if len(ids) > 0 {
		filters = squirrel.And{
			squirrel.Eq{"missing": true},
			squirrel.Eq{"media_file.id": ids},
		}
	}

	mfs, err := s.ds.MediaFile(ctx).GetAll(model.QueryOptions{
		Filters: filters,
	})
	if err != nil {
		return nil, err
	}

	// Extract unique album IDs
	albumIDMap := make(map[string]struct{}, len(mfs))
	for _, mf := range mfs {
		if mf.AlbumID != "" {
			albumIDMap[mf.AlbumID] = struct{}{}
		}
	}

	albumIDs := make([]string, 0, len(albumIDMap))
	for id := range albumIDMap {
		albumIDs = append(albumIDs, id)
	}

	return albumIDs, nil
}

// refreshStatsAsync refreshes artist and album statistics in background goroutines
func (s *maintenanceService) refreshStatsAsync(ctx context.Context, affectedAlbumIDs []string) {
	// Refresh artist stats in background
	s.wg.Go(func() {
		bgCtx := request.AddValues(context.Background(), ctx)
		if _, err := s.ds.Artist(bgCtx).RefreshStats(true); err != nil {
			log.Error(bgCtx, "Error refreshing artist stats after deleting missing files", err)
		} else {
			log.Debug(bgCtx, "Successfully refreshed artist stats after deleting missing files")
		}

		// Refresh album stats in background if we have affected albums
		if len(affectedAlbumIDs) > 0 {
			if err := s.refreshAlbums(bgCtx, affectedAlbumIDs); err != nil {
				log.Error(bgCtx, "Error refreshing album stats after deleting missing files", err)
			} else {
				log.Debug(bgCtx, "Successfully refreshed album stats after deleting missing files", "count", len(affectedAlbumIDs))
			}
		}
	})
}

// Wait waits for all background goroutines to complete.
// WARNING: This method is ONLY for testing. Never call this in production code.
// Calling Wait() in production will block until ALL background operations complete
// and may cause race conditions with new operations starting.
func (s *maintenanceService) wait() {
	s.wg.Wait()
}
