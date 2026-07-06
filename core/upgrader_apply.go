package core

import (
	"cmp"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path"
	"path/filepath"
	"strings"
	"time"

	"github.com/Masterminds/squirrel"
	"github.com/navidrome/navidrome/conf"
	"github.com/navidrome/navidrome/core/ffmpeg"
	"github.com/navidrome/navidrome/core/storage"
	"github.com/navidrome/navidrome/log"
	"github.com/navidrome/navidrome/model"
	"github.com/navidrome/navidrome/model/metadata"
	"github.com/navidrome/navidrome/model/request"
)

// This file implements steps 5-8 of the design doc's "Luồng xử lý": admin
// approval, download to a staging area, stage-2 verification (ffprobe +
// ebur128 dynamic range + tag comparison) and safe replacement with backup.
// See docs/superpowers/specs/2026-07-06-quality-upgrader-design.md, sections
// "Xác minh — giai đoạn 2", "Thay thế an toàn" and "Xử lý lỗi".

const (
	// upgradeStagingSubdir (under DataFolder) holds files downloaded for
	// verification. Nothing here is ever visible to the scanner.
	upgradeStagingSubdir = "upgrade-staging"
	// upgradeBackupSubdir (under DataFolder) receives replaced originals, in
	// per-day folders pruned after Upgrade.BackupRetentionDays.
	upgradeBackupSubdir = "upgrade-backup"
	// upgradeBackupDayFormat names the per-day backup folders.
	upgradeBackupDayFormat = "2006-01-02"
	// upgradeAuditStatus marks replacement entries in the Importer's audit
	// history (import_history.jsonl), distinguishable from regular imports.
	upgradeAuditStatus = "upgraded"
	// lraMaxDrop is how much lower (in LU) the new file's Loudness Range may
	// be before the candidate is rejected as dynamically over-compressed.
	lraMaxDrop = 2.0
)

// upgradeQueueItem is one approved candidate waiting for the background
// worker. ctx is the (detached) context of the approving request, so audit
// entries keep the admin user and logging fields.
type upgradeQueueItem struct {
	id    string
	force bool
	ctx   context.Context
}

// upgradeVerifyInfo is the persisted stage-2 verification result. It is
// marshaled into model.UpgradeCandidate.VerifyInfo; the first six fields are
// the fixed contract the (already committed) admin UI parses, extra fields
// are optional additions.
type upgradeVerifyInfo struct {
	ActualBitRate    int      `json:"actualBitRate"`
	ActualSampleRate int      `json:"actualSampleRate"`
	ActualBitDepth   int      `json:"actualBitDepth"`
	LRAOld           float64  `json:"lraOld"`
	LRANew           float64  `json:"lraNew"`
	MissingTags      []string `json:"missingTags"`
	// LRASkipped notes that the dynamic-range gate could not run (ffmpeg
	// unavailable or measurement failed) and was skipped, not passed.
	LRASkipped bool `json:"lraSkipped,omitempty"`
	// StagedPath keeps the downloaded file of a needs_review candidate so a
	// later force-approve can reuse it without downloading again.
	StagedPath string `json:"stagedPath,omitempty"`
}

func newUpgradeVerifyInfo() *upgradeVerifyInfo {
	return &upgradeVerifyInfo{MissingTags: []string{}}
}

func (vi *upgradeVerifyInfo) marshal() string {
	if vi.MissingTags == nil {
		vi.MissingTags = []string{}
	}
	b, err := json.Marshal(vi)
	if err != nil {
		log.Error("Upgrader: error marshaling verify info", err)
		return ""
	}
	return string(b)
}

// parseUpgradeVerifyInfo returns the verify info stored on a candidate, or
// nil when there is none (or it cannot be parsed).
func parseUpgradeVerifyInfo(s string) *upgradeVerifyInfo {
	if strings.TrimSpace(s) == "" {
		return nil
	}
	var vi upgradeVerifyInfo
	if err := json.Unmarshal([]byte(s), &vi); err != nil {
		return nil
	}
	return &vi
}

// ---------------------------------------------------------------------------
// Approval API
// ---------------------------------------------------------------------------

func (u *upgrader) Approve(ctx context.Context, candidateID string, force bool) error {
	repo := u.ds.UpgradeCandidate(ctx)
	c, err := repo.Get(candidateID)
	if err != nil {
		return err
	}
	switch c.Status {
	case model.UpgradeCandidateStatusPending:
		// always approvable (force only matters later, for the metadata gate)
	case model.UpgradeCandidateStatusNeedsReview:
		if !force {
			return fmt.Errorf("%w: status %q requires force approval", ErrUpgradeInvalidStatus, c.Status)
		}
	default:
		return fmt.Errorf("%w: status is %q", ErrUpgradeInvalidStatus, c.Status)
	}
	user, _ := request.UserFrom(ctx)
	c.Status = model.UpgradeCandidateStatusApproved
	c.ReviewedBy = user.ID
	c.Error = ""
	if err := repo.Put(c); err != nil {
		return err
	}
	log.Info(ctx, "Upgrader: candidate approved", "candidateId", c.ID, "mediaFileId", c.MediaFileID, "force", force, "reviewedBy", user.UserName)
	u.enqueue(upgradeQueueItem{id: c.ID, force: force, ctx: context.WithoutCancel(ctx)})
	return nil
}

func (u *upgrader) Reject(ctx context.Context, candidateID string) error {
	repo := u.ds.UpgradeCandidate(ctx)
	c, err := repo.Get(candidateID)
	if err != nil {
		return err
	}
	if c.Status != model.UpgradeCandidateStatusPending && c.Status != model.UpgradeCandidateStatusNeedsReview {
		return fmt.Errorf("%w: status is %q", ErrUpgradeInvalidStatus, c.Status)
	}
	// Discard the staged download of a needs_review candidate, if any.
	if vi := parseUpgradeVerifyInfo(c.VerifyInfo); vi != nil && vi.StagedPath != "" {
		removeStagedFile(ctx, vi.StagedPath)
		vi.StagedPath = ""
		c.VerifyInfo = vi.marshal()
	}
	user, _ := request.UserFrom(ctx)
	c.Status = model.UpgradeCandidateStatusRejected
	c.ReviewedBy = user.ID
	if err := repo.Put(c); err != nil {
		return err
	}
	log.Info(ctx, "Upgrader: candidate rejected", "candidateId", c.ID, "mediaFileId", c.MediaFileID, "reviewedBy", user.UserName)
	return nil
}

func (u *upgrader) ApproveBatch(ctx context.Context, ids []string) ([]string, error) {
	repo := u.ds.UpgradeCandidate(ctx)
	user, _ := request.UserFrom(ctx)
	accepted := []string{}
	var errs []error
	for _, id := range ids {
		c, err := repo.Get(id)
		if err != nil {
			if !errors.Is(err, model.ErrNotFound) {
				errs = append(errs, fmt.Errorf("%s: %w", id, err))
			}
			continue // missing candidates are just skipped (reported by omission)
		}
		if c.Status != model.UpgradeCandidateStatusPending {
			continue // batch approval never force-approves; skip and report by omission
		}
		c.Status = model.UpgradeCandidateStatusApproved
		c.ReviewedBy = user.ID
		c.Error = ""
		if err := repo.Put(c); err != nil {
			errs = append(errs, fmt.Errorf("%s: %w", id, err))
			continue
		}
		u.enqueue(upgradeQueueItem{id: c.ID, ctx: context.WithoutCancel(ctx)})
		accepted = append(accepted, c.ID)
	}
	log.Info(ctx, "Upgrader: batch approval", "requested", len(ids), "accepted", len(accepted), "reviewedBy", user.UserName)
	return accepted, errors.Join(errs...)
}

// ---------------------------------------------------------------------------
// Startup recovery + backup retention
// ---------------------------------------------------------------------------

func (u *upgrader) Recover(ctx context.Context) error {
	repo := u.ds.UpgradeCandidate(ctx)
	interrupted, err := repo.GetAll(model.QueryOptions{Filters: squirrel.Eq{"status": []string{
		model.UpgradeCandidateStatusDownloading,
		model.UpgradeCandidateStatusApproved,
	}}})
	if err != nil {
		return err
	}
	requeued := 0
	for i := range interrupted {
		c := interrupted[i]
		switch c.Status {
		case model.UpgradeCandidateStatusDownloading:
			// The download was cut short by the restart: back to approved so it
			// is retried from the start ("Xử lý lỗi": downloading → approved).
			c.Status = model.UpgradeCandidateStatusApproved
			if err := repo.Put(&c); err != nil {
				log.Error(ctx, "Upgrader: error resetting interrupted candidate", "candidateId", c.ID, err)
				continue
			}
		case model.UpgradeCandidateStatusApproved:
			// Approved before the restart but never processed: just re-enqueue.
		default:
			continue // defensive: the SQL filter is best-effort (mocks ignore it)
		}
		u.enqueue(upgradeQueueItem{id: c.ID, ctx: context.WithoutCancel(ctx)})
		requeued++
	}
	if requeued > 0 {
		log.Info(ctx, "Upgrader: re-enqueued interrupted upgrade candidates", "count", requeued)
	}
	u.cleanupBackups(ctx)
	return nil
}

// cleanupBackups deletes per-day backup folders older than
// Upgrade.BackupRetentionDays. It runs at startup (Recover) and every time
// the processing queue drains. A retention of <= 0 disables pruning (the
// config default is 30 days).
func (u *upgrader) cleanupBackups(ctx context.Context) {
	days := conf.Server.Upgrade.BackupRetentionDays
	if days <= 0 {
		return
	}
	root := filepath.Join(conf.Server.DataFolder.String(), upgradeBackupSubdir)
	entries, err := os.ReadDir(root)
	if err != nil {
		if !os.IsNotExist(err) {
			log.Warn(ctx, "Upgrader: error reading backup folder", "path", root, err)
		}
		return
	}
	cutoff := time.Now().AddDate(0, 0, -days)
	for _, e := range entries {
		if !e.IsDir() {
			continue
		}
		day, err := time.ParseInLocation(upgradeBackupDayFormat, e.Name(), time.Local)
		if err != nil {
			continue // not one of our per-day folders; leave it alone
		}
		if !day.Before(cutoff) {
			continue
		}
		p := filepath.Join(root, e.Name())
		if err := os.RemoveAll(p); err != nil {
			log.Warn(ctx, "Upgrader: error pruning expired backup folder", "path", p, err)
		} else {
			log.Info(ctx, "Upgrader: pruned expired backup folder", "path", p, "retentionDays", days)
		}
	}
}

// ---------------------------------------------------------------------------
// Background worker (single queue, sequential downloads)
// ---------------------------------------------------------------------------

// enqueue appends item to the processing queue and starts the (single)
// background worker if it is not already draining the queue. Downloads are
// strictly sequential, mirroring the design doc's one-job-at-a-time rule.
func (u *upgrader) enqueue(item upgradeQueueItem) {
	u.mu.Lock()
	defer u.mu.Unlock()
	u.queue = append(u.queue, item)
	if !u.workerRunning {
		u.workerRunning = true
		go u.runWorker()
	}
}

func (u *upgrader) runWorker() {
	for {
		u.mu.Lock()
		if len(u.queue) == 0 {
			u.mu.Unlock()
			// Retention cleanup after each drained batch, per the design doc.
			u.cleanupBackups(context.Background())
			u.mu.Lock()
			if len(u.queue) == 0 { // nothing arrived while cleaning up
				u.workerRunning = false
				u.mu.Unlock()
				return
			}
			u.mu.Unlock()
			continue
		}
		item := u.queue[0]
		u.queue = u.queue[1:]
		u.mu.Unlock()
		u.processCandidate(item.ctx, item.id, item.force)
	}
}

// workerIdle reports whether the background queue is fully drained. Used by
// tests to synchronize with processing.
func (u *upgrader) workerIdle() bool {
	u.mu.Lock()
	defer u.mu.Unlock()
	return !u.workerRunning && len(u.queue) == 0
}

// ---------------------------------------------------------------------------
// Per-candidate processing pipeline
// ---------------------------------------------------------------------------

// processCandidate drives one approved candidate through
// downloading → (needs_review | replaced | failed), persisting every status
// transition. Every candidate is independent: any failure marks only this
// candidate failed and never touches the original file (replacement is the
// last step and is backed up).
func (u *upgrader) processCandidate(ctx context.Context, id string, force bool) {
	repo := u.ds.UpgradeCandidate(ctx)
	c, err := repo.Get(id)
	if err != nil {
		log.Error(ctx, "Upgrader: error loading queued candidate", "candidateId", id, err)
		return
	}
	if c.Status != model.UpgradeCandidateStatusApproved {
		// e.g. rejected between approval and processing, or already handled
		// by a previous run.
		log.Debug(ctx, "Upgrader: skipping queued candidate, no longer approved", "candidateId", id, "status", c.Status)
		return
	}

	mf, err := u.ds.MediaFile(ctx).Get(c.MediaFileID)
	if err != nil {
		u.failCandidate(ctx, c, fmt.Errorf("loading media file %q: %w", c.MediaFileID, err))
		return
	}
	oldPath, err := u.mediaFilePath(ctx, *mf)
	if err != nil {
		u.failCandidate(ctx, c, err)
		return
	}

	// Force-approve of a needs_review candidate: reuse the file staged during
	// the earlier verification pass when it is still around. All gates
	// already ran then (that pass is what set needs_review), so this goes
	// straight to replacement, keeping the recorded verify info.
	if force {
		if vi := parseUpgradeVerifyInfo(c.VerifyInfo); vi != nil && vi.StagedPath != "" {
			if _, statErr := os.Stat(vi.StagedPath); statErr == nil {
				c.Status = model.UpgradeCandidateStatusDownloading
				c.Error = ""
				if err := repo.Put(c); err != nil {
					log.Error(ctx, "Upgrader: error persisting status", "candidateId", c.ID, err)
					return
				}
				u.finishReplace(ctx, c, mf, oldPath, vi.StagedPath, vi)
				return
			}
			log.Debug(ctx, "Upgrader: staged file of forced candidate is gone, downloading again", "candidateId", c.ID, "stagedPath", vi.StagedPath)
		}
	}

	c.Status = model.UpgradeCandidateStatusDownloading
	c.Error = ""
	if err := repo.Put(c); err != nil {
		log.Error(ctx, "Upgrader: error persisting status", "candidateId", c.ID, err)
		return
	}

	staged, err := u.downloadFn(ctx, c)
	if err != nil {
		u.failCandidate(ctx, c, fmt.Errorf("downloading candidate: %w", err))
		return
	}

	info := newUpgradeVerifyInfo()

	// Gate 1: ffprobe — the actual quality must strictly beat the current
	// file under the same stage-1 rules used while scanning.
	probe, err := u.probeFn(ctx, staged)
	if err != nil {
		removeStagedFile(ctx, staged)
		u.failCandidate(ctx, c, fmt.Errorf("probing downloaded file: %w", err))
		return
	}
	info.ActualBitRate = probe.BitRate
	info.ActualSampleRate = probe.SampleRate
	info.ActualBitDepth = probe.BitDepth
	if !candidateWins(originalQuality(*mf), probeQuality(probe)) {
		c.VerifyInfo = info.marshal()
		removeStagedFile(ctx, staged)
		u.failCandidate(ctx, c, fmt.Errorf(
			"downloaded file does not beat current quality (codec %s, %d kbps, %d Hz, %d bit)",
			probe.Codec, probe.BitRate, probe.SampleRate, probe.BitDepth))
		return
	}

	// Gate 2: dynamic range — the new file's Loudness Range must not be more
	// than lraMaxDrop LU below the old one's. When ffmpeg is unavailable (or
	// measurement fails) the gate is skipped with a warning, never failing
	// the candidate.
	lraOld, errOld := u.lraFn(ctx, oldPath)
	lraNew, errNew := u.lraFn(ctx, staged)
	if errOld != nil || errNew != nil {
		log.Warn(ctx, "Upgrader: could not measure LRA, skipping dynamic-range gate",
			"candidateId", c.ID, "errOld", errOld, "errNew", errNew)
		info.LRASkipped = true
	} else {
		info.LRAOld = lraOld
		info.LRANew = lraNew
		if lraNew < lraOld-lraMaxDrop {
			c.VerifyInfo = info.marshal()
			removeStagedFile(ctx, staged)
			u.failCandidate(ctx, c, fmt.Errorf(
				"dynamic range too compressed: LRA %.1f LU vs current %.1f LU (max drop %.1f LU)",
				lraNew, lraOld, lraMaxDrop))
			return
		}
	}

	// Gate 3: metadata — every core tag the old file has must be present in
	// the new one; otherwise the admin decides (needs_review). Skipped (but
	// still recorded) on force-approve. Tags are never written (Navidrome
	// does not write tags).
	oldTags := u.safeTags(ctx, oldPath)
	newTags := u.safeTags(ctx, staged)
	info.MissingTags = missingCoreTags(oldTags, newTags)
	if len(info.MissingTags) > 0 && !force {
		info.StagedPath = staged // keep the download for a later force-approve
		c.VerifyInfo = info.marshal()
		c.Status = model.UpgradeCandidateStatusNeedsReview
		c.Error = ""
		if err := repo.Put(c); err != nil {
			log.Error(ctx, "Upgrader: error persisting needs_review candidate", "candidateId", c.ID, err)
		}
		log.Info(ctx, "Upgrader: candidate needs review", "candidateId", c.ID, "missingTags", info.MissingTags)
		return
	}

	u.finishReplace(ctx, c, mf, oldPath, staged, info)
}

// finishReplace performs the final, backed-up file swap and marks the
// candidate replaced (or failed, if the swap itself fails).
func (u *upgrader) finishReplace(ctx context.Context, c *model.UpgradeCandidate, mf *model.MediaFile, oldPath, staged string, info *upgradeVerifyInfo) {
	newPath, size, sum, err := replaceMediaFile(*mf, oldPath, staged, c.Format)
	if err != nil {
		info.StagedPath = ""
		c.VerifyInfo = info.marshal()
		removeStagedFile(ctx, staged)
		u.failCandidate(ctx, c, fmt.Errorf("replacing file: %w", err))
		return
	}
	info.StagedPath = ""
	c.VerifyInfo = info.marshal()
	c.Status = model.UpgradeCandidateStatusReplaced
	c.Error = ""
	if err := u.ds.UpgradeCandidate(ctx).Put(c); err != nil {
		log.Error(ctx, "Upgrader: error persisting replaced candidate", "candidateId", c.ID, err)
	}
	u.auditFn(ctx, c, filepath.Base(newPath), size, sum)
	log.Info(ctx, "Upgrader: replaced file with higher-quality version",
		"candidateId", c.ID, "mediaFileId", c.MediaFileID, "old", oldPath, "new", newPath, "bytes", size)
	u.scanFn(ctx)
}

func (u *upgrader) failCandidate(ctx context.Context, c *model.UpgradeCandidate, cause error) {
	log.Warn(ctx, "Upgrader: candidate failed", "candidateId", c.ID, "mediaFileId", c.MediaFileID, cause)
	c.Status = model.UpgradeCandidateStatusFailed
	c.Error = cause.Error()
	if err := u.ds.UpgradeCandidate(ctx).Put(c); err != nil {
		log.Error(ctx, "Upgrader: error persisting failed candidate", "candidateId", c.ID, err)
	}
}

// mediaFilePath resolves the absolute path of mf, filling LibraryPath from
// the library table when the row was loaded without it.
func (u *upgrader) mediaFilePath(ctx context.Context, mf model.MediaFile) (string, error) {
	if mf.LibraryPath == "" {
		p, err := u.ds.Library(ctx).GetPath(mf.LibraryID)
		if err != nil {
			return "", fmt.Errorf("resolving library %d path: %w", mf.LibraryID, err)
		}
		mf.LibraryPath = p
	}
	return mf.AbsolutePath(), nil
}

// recordUpgradeAudit appends the replacement to the Importer's persistent
// audit history (import_history.jsonl) with the distinguishable status
// "upgraded". No-op when the Importer is not the real implementation (tests).
func (u *upgrader) recordUpgradeAudit(ctx context.Context, c *model.UpgradeCandidate, savedName string, size int64, sum string) {
	ci, ok := u.imp.(*importer)
	if !ok {
		return
	}
	ci.ensureHistoryLoaded()
	ci.recordImport(ctx, importMeta{libraryID: c.LibraryID, source: c.Source, ref: c.SourceRef},
		savedName, size, sum, upgradeAuditStatus)
}

func removeStagedFile(ctx context.Context, path string) {
	if path == "" {
		return
	}
	if err := os.Remove(path); err != nil && !os.IsNotExist(err) {
		log.Warn(ctx, "Upgrader: error removing staged file", "path", path, err)
	}
}

// ---------------------------------------------------------------------------
// Safe replacement ("Thay thế an toàn")
// ---------------------------------------------------------------------------

// replaceMediaFile swaps the library file at oldPath with the staged
// download: the original is moved to
// <DataFolder>/upgrade-backup/<YYYY-MM-DD>/<library-relative path>, then the
// staged file is moved next to where the original was, keeping its base name
// and taking the new extension. If the second move fails, the original is
// restored from the backup, so any error leaves the library untouched.
// Returns the new file's path, size and SHA-256 (for the audit log).
func replaceMediaFile(mf model.MediaFile, oldPath, staged, fallbackExt string) (newPath string, size int64, sum string, err error) {
	newExt := extOf(staged)
	if newExt == "" {
		newExt = strings.ToLower(strings.TrimPrefix(fallbackExt, "."))
	}
	if newExt == "" {
		return "", 0, "", fmt.Errorf("cannot determine the new file's extension")
	}
	sum, size, err = hashFileSHA256(staged)
	if err != nil {
		return "", 0, "", fmt.Errorf("hashing staged file: %w", err)
	}

	backupPath := filepath.Join(conf.Server.DataFolder.String(), upgradeBackupSubdir,
		time.Now().Format(upgradeBackupDayFormat), filepath.FromSlash(mf.Path))
	if err = os.MkdirAll(filepath.Dir(backupPath), 0o755); err != nil {
		return "", 0, "", fmt.Errorf("creating backup folder: %w", err)
	}
	if err = moveFile(oldPath, backupPath); err != nil {
		return "", 0, "", fmt.Errorf("backing up original: %w", err)
	}

	base := strings.TrimSuffix(filepath.Base(oldPath), filepath.Ext(oldPath))
	newPath = filepath.Join(filepath.Dir(oldPath), base+"."+newExt)
	if err = moveFile(staged, newPath); err != nil {
		// Put the original back so the library is exactly as before.
		if rbErr := moveFile(backupPath, oldPath); rbErr != nil {
			log.Error("Upgrader: FAILED TO RESTORE original after aborted replacement — restore manually",
				"backup", backupPath, "original", oldPath, rbErr)
		}
		return "", 0, "", fmt.Errorf("placing new file: %w", err)
	}
	return newPath, size, sum, nil
}

// moveFile renames src to dst, falling back to copy+delete when rename fails
// (e.g. the backup folder is on a different filesystem than the library).
func moveFile(src, dst string) error {
	if err := os.Rename(src, dst); err == nil {
		return nil
	}
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()
	out, err := os.OpenFile(dst, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0o644)
	if err != nil {
		return err
	}
	if _, err = io.Copy(out, in); err != nil {
		_ = out.Close()
		_ = os.Remove(dst)
		return err
	}
	if err = out.Close(); err != nil {
		_ = os.Remove(dst)
		return err
	}
	return os.Remove(src)
}

func hashFileSHA256(path string) (sum string, size int64, err error) {
	f, err := os.Open(path)
	if err != nil {
		return "", 0, err
	}
	defer f.Close()
	h := sha256.New()
	size, err = io.Copy(h, f)
	if err != nil {
		return "", 0, err
	}
	return hex.EncodeToString(h.Sum(nil)), size, nil
}

// ---------------------------------------------------------------------------
// Stage-2 quality comparison helpers
// ---------------------------------------------------------------------------

// probeQuality builds the actual (post-download) quality tier from an ffprobe
// result, so it can be compared with compareQualityTier/candidateWins exactly
// like the stage-1 estimates were.
func probeQuality(p *ffmpeg.AudioProbeResult) qualityTier {
	return qualityTier{
		Lossless:    isLosslessProbeCodec(p.Codec),
		BitRateKbps: p.BitRate,
		SampleRate:  p.SampleRate,
		BitDepth:    p.BitDepth,
	}
}

// isLosslessProbeCodec classifies ffprobe codec names, which are more
// specific than model.MediaFile.AudioCodec()'s vocabulary (e.g. "pcm_s16le"
// instead of "pcm", "dsd_lsbf" instead of "dsd").
func isLosslessProbeCodec(codec string) bool {
	c := strings.ToLower(codec)
	if strings.HasPrefix(c, "pcm_") || strings.HasPrefix(c, "dsd_") {
		return true
	}
	return isLosslessCodec(c)
}

// ---------------------------------------------------------------------------
// Metadata comparison (core tags only; tags are never written)
// ---------------------------------------------------------------------------

// trackTags is the fixed set of core tags compared between the old and new
// file, per the design doc's "Xác minh — giai đoạn 2" step 3.
type trackTags struct {
	Title       string
	Artist      string
	Album       string
	AlbumArtist string
	TrackNumber int
	Year        int
	Genre       string
	HasCover    bool
}

// extractTrackTags reads the core tags of a local audio file with the same
// extractor stack the scanner uses (storage → localFS.ReadTags → taglib →
// metadata mapping); nothing shells out.
func extractTrackTags(_ context.Context, filePath string) (*trackTags, error) {
	dir, file := filepath.Split(filePath)
	s, err := storage.For(dir)
	if err != nil {
		return nil, err
	}
	fsys, err := s.FS()
	if err != nil {
		return nil, err
	}
	tags, err := fsys.ReadTags(file)
	if err != nil {
		return nil, err
	}
	info, ok := tags[file]
	if !ok {
		return nil, fmt.Errorf("could not read tags from %q", filePath)
	}
	md := metadata.New(file, info)
	trackNum, _ := md.NumAndTotal(model.TagTrackNumber)
	return &trackTags{
		Title:       md.String(model.TagTitle),
		Artist:      md.String(model.TagTrackArtist),
		Album:       md.String(model.TagAlbum),
		AlbumArtist: md.String(model.TagAlbumArtist),
		TrackNumber: trackNum,
		Year: cmp.Or(
			md.Date(model.TagRecordingDate).Year(),
			md.Date(model.TagOriginalDate).Year(),
			md.Date(model.TagReleaseDate).Year(),
		),
		Genre:    md.String(model.TagGenre),
		HasCover: md.HasPicture(),
	}, nil
}

// safeTags extracts tags, degrading to an empty tag set (with a warning)
// when extraction fails. An unreadable OLD file therefore never blocks an
// upgrade, while an unreadable NEW file makes every tag the old file has
// count as missing — pushing the candidate to needs_review, not failed.
func (u *upgrader) safeTags(ctx context.Context, path string) *trackTags {
	t, err := u.tagsFn(ctx, path)
	if err != nil || t == nil {
		log.Warn(ctx, "Upgrader: could not extract tags", "path", path, err)
		return &trackTags{}
	}
	return t
}

// missingCoreTags lists the core tags present in the old file but absent
// from the new one (names match the chips rendered by the admin UI).
func missingCoreTags(oldTags, newTags *trackTags) []string {
	missing := []string{}
	check := func(name string, oldHas, newHas bool) {
		if oldHas && !newHas {
			missing = append(missing, name)
		}
	}
	check("title", oldTags.Title != "", newTags.Title != "")
	check("artist", oldTags.Artist != "", newTags.Artist != "")
	check("album", oldTags.Album != "", newTags.Album != "")
	check("albumartist", oldTags.AlbumArtist != "", newTags.AlbumArtist != "")
	check("tracknumber", oldTags.TrackNumber > 0, newTags.TrackNumber > 0)
	check("year", oldTags.Year > 0, newTags.Year > 0)
	check("genre", oldTags.Genre != "", newTags.Genre != "")
	check("cover", oldTags.HasCover, newTags.HasCover)
	return missing
}

// ---------------------------------------------------------------------------
// Staged downloads (reusing the Importer's pipeline and guards)
// ---------------------------------------------------------------------------

// downloadCandidate is the default downloadFn: it stages the candidate's
// remote file via the concrete importer's download pipeline (same HTTP
// clients, SSRF guard, size caps and filename sanitization as imports).
func (u *upgrader) downloadCandidate(ctx context.Context, c *model.UpgradeCandidate) (string, error) {
	ci, ok := u.imp.(*importer)
	if !ok {
		return "", errors.New("importer does not support staged downloads")
	}
	return ci.stageDownload(ctx, c)
}

// upgradeStagingDir returns (creating it if needed) the staging folder for
// downloaded-but-not-yet-applied upgrade files. It lives under DataFolder —
// never under a library folder — so the scanner cannot pick these files up.
func upgradeStagingDir() (string, error) {
	dir := filepath.Join(conf.Server.DataFolder.String(), upgradeStagingSubdir)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return "", fmt.Errorf("creating upgrade staging folder: %w", err)
	}
	return dir, nil
}

// stageDownload downloads the remote file behind c into the upgrade staging
// area and returns its path. It mirrors downloadTo/persist (temp file, size
// cap, unique final name) but deliberately does NOT import into the library,
// dedup against import history, or write an audit entry — that happens only
// at replacement time.
func (imp *importer) stageDownload(ctx context.Context, c *model.UpgradeCandidate) (string, error) {
	dir, err := upgradeStagingDir()
	if err != nil {
		return "", err
	}
	body, name, err := imp.openCandidateStream(ctx, c)
	if err != nil {
		return "", err
	}
	defer body.Close()

	out, err := os.CreateTemp(dir, ".nd-upgrade-*.part")
	if err != nil {
		return "", err
	}
	tmpPath := out.Name()
	written, err := io.Copy(out, io.LimitReader(body, maxDownloadBytes+1))
	closeErr := out.Close()
	if err == nil {
		err = closeErr
	}
	if err == nil && written > maxDownloadBytes {
		err = fmt.Errorf("file exceeded maximum size of %d bytes", maxDownloadBytes)
	}
	if err != nil {
		_ = os.Remove(tmpPath)
		return "", err
	}

	finalPath, _ := uniqueDest(filepath.Join(dir, name))
	if err := os.Rename(tmpPath, finalPath); err != nil {
		_ = os.Remove(tmpPath)
		return "", err
	}
	log.Info(ctx, "Upgrader: staged candidate download", "name", filepath.Base(finalPath), "bytes", written, "source", c.Source, "ref", c.SourceRef)
	return finalPath, nil
}

// openCandidateStream opens the remote stream for a candidate, building the
// download exactly the way the corresponding Import* method does:
// archive = https://archive.org/download/<identifier>/<filename> (like
// ImportArchive), drive = the fileID flow of ImportDriveFile, rss = the
// enclosure URL directly (like ImportURL, including the SSRF guard). It
// returns the body and the sanitized filename to stage under.
func (imp *importer) openCandidateStream(ctx context.Context, c *model.UpgradeCandidate) (io.ReadCloser, string, error) {
	switch c.Source {
	case UpgradeSourceArchive:
		identifier, filename, ok := strings.Cut(c.SourceRef, "/")
		if !ok || identifier == "" || filename == "" {
			return nil, "", fmt.Errorf("invalid archive reference %q", c.SourceRef)
		}
		if strings.ContainsAny(identifier, "\\") || !isAudioExt(filename) {
			return nil, "", fmt.Errorf("invalid archive reference %q", c.SourceRef)
		}
		segments := strings.Split(filename, "/")
		for i, s := range segments {
			segments[i] = url.PathEscape(s)
		}
		dlURL := archiveBaseURL + "/download/" + url.PathEscape(identifier) + "/" + strings.Join(segments, "/")
		resp, err := imp.openDownload(ctx, dlURL)
		if err != nil {
			return nil, "", err
		}
		return resp.Body, safeAudioFilename(path.Base(filename)), nil

	case UpgradeSourceDrive:
		fileID := strings.TrimSpace(c.SourceRef)
		if !reDriveID.MatchString(fileID) {
			return nil, "", fmt.Errorf("invalid Google Drive file id")
		}
		var resp *http.Response
		var err error
		if key := conf.Server.GoogleDriveAPIKey; key != "" {
			q := url.Values{}
			q.Set("alt", "media")
			q.Set("supportsAllDrives", "true")
			q.Set("key", key)
			dlURL := "https://www.googleapis.com/drive/v3/files/" + url.PathEscape(fileID) + "?" + q.Encode()
			resp, err = imp.driveGet(ctx, dlURL)
			if err != nil {
				return nil, "", err
			}
			if resp.StatusCode != http.StatusOK {
				msg, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
				resp.Body.Close()
				return nil, "", fmt.Errorf("Drive API download failed (HTTP %d): %s", resp.StatusCode, driveAPIErrorMessage(msg))
			}
		} else {
			resp, err = imp.driveScrapeDownload(ctx, fileID)
			if err != nil {
				return nil, "", err
			}
		}
		name := driveFilename(c.Title, resp.Header.Get("Content-Disposition"), fileID)
		if !isAudioExt(name) {
			resp.Body.Close()
			return nil, "", fmt.Errorf("file %q is not a supported audio type", name)
		}
		return resp.Body, name, nil

	case UpgradeSourceRSS:
		u, err := validatePublicURL(c.SourceRef) // SSRF guard, same as ImportURL
		if err != nil {
			return nil, "", err
		}
		resp, err := imp.openDownload(ctx, u.String())
		if err != nil {
			return nil, "", err
		}
		name := safeAudioFilename(path.Base(u.Path))
		if ct := resp.Header.Get("Content-Type"); !strings.HasPrefix(ct, "audio/") && !isAudioExt(u.Path) {
			resp.Body.Close()
			return nil, "", fmt.Errorf("enclosure does not point to an audio file (Content-Type: %q)", ct)
		}
		return resp.Body, name, nil

	default:
		return nil, "", fmt.Errorf("unknown upgrade source %q", c.Source)
	}
}

// openDownload issues the GET used for staged downloads, with the same
// User-Agent, status and declared-size checks as downloadTo.
func (imp *importer) openDownload(ctx context.Context, rawURL string) (*http.Response, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "Navidrome-Importer")
	resp, err := imp.download.Do(req)
	if err != nil {
		return nil, err
	}
	if resp.StatusCode != http.StatusOK {
		resp.Body.Close()
		return nil, fmt.Errorf("download failed: HTTP %d", resp.StatusCode)
	}
	if resp.ContentLength > maxDownloadBytes {
		resp.Body.Close()
		return nil, fmt.Errorf("file too large: %d bytes (max %d)", resp.ContentLength, maxDownloadBytes)
	}
	return resp, nil
}
