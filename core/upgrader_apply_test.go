package core

import (
	"context"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/navidrome/navidrome/conf"
	"github.com/navidrome/navidrome/core/ffmpeg"
	"github.com/navidrome/navidrome/model"
	"github.com/navidrome/navidrome/model/request"
	"github.com/navidrome/navidrome/tests"
)

// newApplyTestUpgrader builds an *upgrader with mock repositories, a
// throwaway DataFolder and fast-failing pipeline stubs (tests override the
// function fields they care about — the same DI-by-field style used by
// stubImporter).
func newApplyTestUpgrader(t *testing.T) (*upgrader, *tests.MockMediaFileRepo, *tests.MockUpgradeCandidateRepo) {
	t.Helper()
	withTestUpgradeConfig(t)
	conf.Server.Upgrade.BackupRetentionDays = 30
	origDF := conf.Server.DataFolder
	conf.Server.DataFolder = conf.NewDir(t.TempDir())
	t.Cleanup(func() { conf.Server.DataFolder = origDF })

	ds, mfRepo, candRepo := newTestUpgradeDS()
	u := NewUpgrader(ds, &stubImporter{}, nil).(*upgrader)
	u.downloadFn = func(context.Context, *model.UpgradeCandidate) (string, error) {
		return "", errors.New("downloadFn not stubbed")
	}
	u.probeFn = func(context.Context, string) (*ffmpeg.AudioProbeResult, error) {
		return nil, errors.New("probeFn not stubbed")
	}
	u.lraFn = func(context.Context, string) (float64, error) {
		return 0, errors.New("lraFn not stubbed")
	}
	u.tagsFn = func(context.Context, string) (*trackTags, error) { return &trackTags{}, nil }
	u.scanFn = func(context.Context) {}
	u.auditFn = func(context.Context, *model.UpgradeCandidate, string, int64, string) {}
	return u, mfRepo, candRepo
}

// waitWorkerIdle blocks until the background processing queue drains, so the
// test can safely inspect the mock repositories.
func waitWorkerIdle(t *testing.T, u *upgrader, timeout time.Duration) {
	t.Helper()
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		if u.workerIdle() {
			return
		}
		time.Sleep(2 * time.Millisecond)
	}
	t.Fatal("upgrade worker did not go idle before the timeout")
}

// setupLibraryFile creates a real "current" file inside a temp library and
// registers its MediaFile row.
func setupLibraryFile(t *testing.T, mfRepo *tests.MockMediaFileRepo) (libDir string, mf model.MediaFile) {
	t.Helper()
	libDir = t.TempDir()
	rel := filepath.Join("Queen", "01 - Bohemian Rhapsody.mp3")
	abs := filepath.Join(libDir, rel)
	if err := os.MkdirAll(filepath.Dir(abs), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(abs, []byte("old-mp3-content"), 0o644); err != nil {
		t.Fatal(err)
	}
	mf = model.MediaFile{
		ID: "mf1", LibraryID: 1, LibraryPath: libDir, Path: rel,
		Artist: "Queen", Title: "Bohemian Rhapsody", Suffix: "mp3", BitRate: 128,
	}
	mfRepo.SetData(model.MediaFiles{mf})
	return libDir, mf
}

func putCandidate(t *testing.T, repo *tests.MockUpgradeCandidateRepo, status string) *model.UpgradeCandidate {
	t.Helper()
	c := &model.UpgradeCandidate{
		MediaFileID: "mf1", LibraryID: 1,
		Source: UpgradeSourceArchive, SourceRef: "queen-1975/bohemian_rhapsody.flac",
		Title: "Bohemian Rhapsody", Format: "flac", MatchScore: 90,
		Status: status,
	}
	if err := repo.Put(c); err != nil {
		t.Fatal(err)
	}
	return c
}

type pipelineCalls struct {
	downloads atomic.Int32
	scans     atomic.Int32
	audits    atomic.Int32
}

var fullTrackTags = trackTags{
	Title: "Bohemian Rhapsody", Artist: "Queen", Album: "A Night at the Opera",
	AlbumArtist: "Queen", TrackNumber: 1, Year: 1975, Genre: "Rock", HasCover: true,
}

// stubHappyPipeline wires every verification step to pass: the "download"
// writes newContent into the staging dir, ffprobe reports a winning FLAC,
// both LRA measurements match, and both files have the full core tag set.
func stubHappyPipeline(u *upgrader, newContent string) *pipelineCalls {
	calls := &pipelineCalls{}
	u.downloadFn = func(_ context.Context, c *model.UpgradeCandidate) (string, error) {
		calls.downloads.Add(1)
		dir, err := upgradeStagingDir()
		if err != nil {
			return "", err
		}
		p := filepath.Join(dir, "bohemian_rhapsody.flac")
		return p, os.WriteFile(p, []byte(newContent), 0o600)
	}
	u.probeFn = func(context.Context, string) (*ffmpeg.AudioProbeResult, error) {
		return &ffmpeg.AudioProbeResult{Codec: "flac", BitRate: 900, SampleRate: 44100, BitDepth: 16}, nil
	}
	u.lraFn = func(context.Context, string) (float64, error) { return 9.5, nil }
	u.tagsFn = func(context.Context, string) (*trackTags, error) { tt := fullTrackTags; return &tt, nil }
	u.scanFn = func(context.Context) { calls.scans.Add(1) }
	u.auditFn = func(context.Context, *model.UpgradeCandidate, string, int64, string) { calls.audits.Add(1) }
	return calls
}

func adminCtx() context.Context {
	return request.WithUser(context.Background(), model.User{ID: "admin-1", UserName: "admin", IsAdmin: true})
}

func TestUpgraderApproveValidation(t *testing.T) {
	u, mfRepo, candRepo := newApplyTestUpgrader(t)
	setupLibraryFile(t, mfRepo)

	if err := u.Approve(adminCtx(), "no-such-id", false); !errors.Is(err, model.ErrNotFound) {
		t.Errorf("Approve(missing) err = %v, want model.ErrNotFound", err)
	}

	rejected := putCandidate(t, candRepo, model.UpgradeCandidateStatusRejected)
	if err := u.Approve(adminCtx(), rejected.ID, false); !errors.Is(err, ErrUpgradeInvalidStatus) {
		t.Errorf("Approve(rejected) err = %v, want ErrUpgradeInvalidStatus", err)
	}
	if err := u.Approve(adminCtx(), rejected.ID, true); !errors.Is(err, ErrUpgradeInvalidStatus) {
		t.Errorf("Approve(rejected, force) err = %v, want ErrUpgradeInvalidStatus (force does not bypass terminal statuses)", err)
	}

	review := &model.UpgradeCandidate{
		MediaFileID: "mf1", LibraryID: 1, Source: UpgradeSourceArchive,
		SourceRef: "queen-1975/other.flac", Status: model.UpgradeCandidateStatusNeedsReview,
	}
	if err := candRepo.Put(review); err != nil {
		t.Fatal(err)
	}
	if err := u.Reject(adminCtx(), review.ID); err != nil {
		t.Fatalf("Reject(needs_review) error = %v", err)
	}
	if err := u.Approve(adminCtx(), review.ID, true); !errors.Is(err, ErrUpgradeInvalidStatus) {
		t.Errorf("Approve after Reject err = %v, want ErrUpgradeInvalidStatus", err)
	}
	waitWorkerIdle(t, u, time.Second)
}

// TestUpgraderDisabledRejectsOperations covers M2: the kill switch
// (conf.Server.Upgrade.Enabled=false) must gate the HTTP-reachable surface
// (Approve/Reject/ApproveBatch/StartScan), not just the cron job and startup
// recovery, so it is checked here in core rather than only at the router.
func TestUpgraderDisabledRejectsOperations(t *testing.T) {
	u, mfRepo, candRepo := newApplyTestUpgrader(t)
	setupLibraryFile(t, mfRepo)
	c := putCandidate(t, candRepo, model.UpgradeCandidateStatusPending)

	orig := conf.Server.Upgrade.Enabled
	conf.Server.Upgrade.Enabled = false
	defer func() { conf.Server.Upgrade.Enabled = orig }()

	if err := u.Approve(adminCtx(), c.ID, false); !errors.Is(err, ErrUpgradeDisabled) {
		t.Errorf("Approve() err = %v, want ErrUpgradeDisabled", err)
	}
	if err := u.Reject(adminCtx(), c.ID); !errors.Is(err, ErrUpgradeDisabled) {
		t.Errorf("Reject() err = %v, want ErrUpgradeDisabled", err)
	}
	if _, err := u.ApproveBatch(adminCtx(), []string{c.ID}); !errors.Is(err, ErrUpgradeDisabled) {
		t.Errorf("ApproveBatch() err = %v, want ErrUpgradeDisabled", err)
	}
	if err := u.StartScan(context.Background(), 0, nil); !errors.Is(err, ErrUpgradeDisabled) {
		t.Errorf("StartScan() err = %v, want ErrUpgradeDisabled", err)
	}

	// Candidate must be untouched (still pending, never enqueued).
	got, _ := candRepo.Get(c.ID)
	if got.Status != model.UpgradeCandidateStatusPending {
		t.Errorf("status = %q, want unchanged pending", got.Status)
	}
}

func TestUpgraderApproveNeedsReviewRequiresForce(t *testing.T) {
	u, mfRepo, candRepo := newApplyTestUpgrader(t)
	setupLibraryFile(t, mfRepo)
	c := putCandidate(t, candRepo, model.UpgradeCandidateStatusNeedsReview)

	if err := u.Approve(adminCtx(), c.ID, false); !errors.Is(err, ErrUpgradeInvalidStatus) {
		t.Errorf("Approve(needs_review, force=false) err = %v, want ErrUpgradeInvalidStatus", err)
	}
	got, _ := candRepo.Get(c.ID)
	if got.Status != model.UpgradeCandidateStatusNeedsReview {
		t.Errorf("status = %q, want unchanged needs_review", got.Status)
	}
}

func TestUpgraderApproveReplacesFile(t *testing.T) {
	u, mfRepo, candRepo := newApplyTestUpgrader(t)
	libDir, mf := setupLibraryFile(t, mfRepo)
	c := putCandidate(t, candRepo, model.UpgradeCandidateStatusPending)
	calls := stubHappyPipeline(u, "new-flac-content")

	// L1: an earlier same-day backup already occupies the exact path this
	// upgrade's backup would use; it must be kept, not renamed over.
	backup := filepath.Join(conf.Server.DataFolder.String(), upgradeBackupSubdir,
		time.Now().Format(upgradeBackupDayFormat), mf.Path)
	if err := os.MkdirAll(filepath.Dir(backup), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(backup, []byte("earlier-same-day-backup"), 0o644); err != nil {
		t.Fatal(err)
	}

	if err := u.Approve(adminCtx(), c.ID, false); err != nil {
		t.Fatalf("Approve() error = %v", err)
	}
	waitWorkerIdle(t, u, 2*time.Second)

	got, err := candRepo.Get(c.ID)
	if err != nil {
		t.Fatal(err)
	}
	if got.Status != model.UpgradeCandidateStatusReplaced {
		t.Fatalf("status = %q (error=%q), want replaced", got.Status, got.Error)
	}
	if got.ReviewedBy != "admin-1" {
		t.Errorf("reviewedBy = %q, want admin-1", got.ReviewedBy)
	}

	// New file: old base name, new extension, staged content.
	newPath := filepath.Join(libDir, "Queen", "01 - Bohemian Rhapsody.flac")
	data, err := os.ReadFile(newPath)
	if err != nil {
		t.Fatalf("new file not in place: %v", err)
	}
	if string(data) != "new-flac-content" {
		t.Errorf("new file content = %q, want the staged download", data)
	}

	// Old file: gone from the library, present in the dated backup tree
	// under a unique name (L1), since the exact backup path was already taken.
	if _, err := os.Stat(mf.AbsolutePath()); !os.IsNotExist(err) {
		t.Errorf("old file still in the library (stat err = %v)", err)
	}
	if bdata, err := os.ReadFile(backup); err != nil || string(bdata) != "earlier-same-day-backup" {
		t.Errorf("earlier same-day backup was overwritten: data=%q, err=%v", bdata, err)
	}
	uniqueBackup := filepath.Join(filepath.Dir(backup), "01 - Bohemian Rhapsody (1).mp3")
	bdata, err := os.ReadFile(uniqueBackup)
	if err != nil {
		t.Fatalf("backup not found at %s: %v", uniqueBackup, err)
	}
	if string(bdata) != "old-mp3-content" {
		t.Errorf("backup content = %q, want the original file", bdata)
	}

	// verifyInfo: exact JSON contract (camelCase keys, all six present).
	var vi map[string]any
	if err := json.Unmarshal([]byte(got.VerifyInfo), &vi); err != nil {
		t.Fatalf("verifyInfo is not valid JSON: %v (%q)", err, got.VerifyInfo)
	}
	for _, key := range []string{"actualBitRate", "actualSampleRate", "actualBitDepth", "lraOld", "lraNew", "missingTags"} {
		if _, ok := vi[key]; !ok {
			t.Errorf("verifyInfo missing required key %q: %s", key, got.VerifyInfo)
		}
	}
	if vi["actualBitRate"] != float64(900) || vi["lraOld"] != 9.5 || vi["lraNew"] != 9.5 {
		t.Errorf("unexpected verifyInfo values: %s", got.VerifyInfo)
	}
	t.Logf("verifyInfo sample: %s", got.VerifyInfo)

	if calls.scans.Load() != 1 {
		t.Errorf("scan trigger calls = %d, want 1", calls.scans.Load())
	}
	if calls.audits.Load() != 1 {
		t.Errorf("audit calls = %d, want 1", calls.audits.Load())
	}
}

func TestUpgraderQualityGateFailsCandidate(t *testing.T) {
	u, mfRepo, candRepo := newApplyTestUpgrader(t)
	libDir, mf := setupLibraryFile(t, mfRepo)
	c := putCandidate(t, candRepo, model.UpgradeCandidateStatusPending)
	stubHappyPipeline(u, "worse-content")
	// The download claimed FLAC, but the actual file is a 96 kbps MP3 —
	// strictly worse than the current 128 kbps MP3.
	u.probeFn = func(context.Context, string) (*ffmpeg.AudioProbeResult, error) {
		return &ffmpeg.AudioProbeResult{Codec: "mp3", BitRate: 96, SampleRate: 44100}, nil
	}

	if err := u.Approve(adminCtx(), c.ID, false); err != nil {
		t.Fatalf("Approve() error = %v", err)
	}
	waitWorkerIdle(t, u, 2*time.Second)

	got, _ := candRepo.Get(c.ID)
	if got.Status != model.UpgradeCandidateStatusFailed {
		t.Fatalf("status = %q, want failed", got.Status)
	}
	if !strings.Contains(got.Error, "does not beat current quality") {
		t.Errorf("error = %q, want quality-gate message", got.Error)
	}
	// The original file must be untouched, and the staged download discarded.
	if _, err := os.Stat(filepath.Join(libDir, mf.Path)); err != nil {
		t.Errorf("original file was touched: %v", err)
	}
	staged := filepath.Join(conf.Server.DataFolder.String(), upgradeStagingSubdir, "bohemian_rhapsody.flac")
	if _, err := os.Stat(staged); !os.IsNotExist(err) {
		t.Errorf("staged file was not cleaned up (stat err = %v)", err)
	}
}

func TestUpgraderLRAGateFailsCandidate(t *testing.T) {
	u, mfRepo, candRepo := newApplyTestUpgrader(t)
	libDir, mf := setupLibraryFile(t, mfRepo)
	c := putCandidate(t, candRepo, model.UpgradeCandidateStatusPending)
	stubHappyPipeline(u, "squashed-content")
	// Old file has LRA 12 LU; the new one is squashed to 5 LU — more than
	// the allowed 2 LU drop.
	u.lraFn = func(_ context.Context, path string) (float64, error) {
		if strings.HasPrefix(path, libDir) {
			return 12.0, nil
		}
		return 5.0, nil
	}

	if err := u.Approve(adminCtx(), c.ID, false); err != nil {
		t.Fatalf("Approve() error = %v", err)
	}
	waitWorkerIdle(t, u, 2*time.Second)

	got, _ := candRepo.Get(c.ID)
	if got.Status != model.UpgradeCandidateStatusFailed {
		t.Fatalf("status = %q, want failed", got.Status)
	}
	if !strings.Contains(got.Error, "dynamic range") {
		t.Errorf("error = %q, want dynamic-range message", got.Error)
	}
	if _, err := os.Stat(filepath.Join(libDir, mf.Path)); err != nil {
		t.Errorf("original file was touched: %v", err)
	}
}

func TestUpgraderLRAUnavailableSkipsGate(t *testing.T) {
	u, mfRepo, candRepo := newApplyTestUpgrader(t)
	setupLibraryFile(t, mfRepo)
	c := putCandidate(t, candRepo, model.UpgradeCandidateStatusPending)
	stubHappyPipeline(u, "new-flac-content")
	u.lraFn = func(context.Context, string) (float64, error) {
		return 0, errors.New("ffmpeg not found")
	}

	if err := u.Approve(adminCtx(), c.ID, false); err != nil {
		t.Fatalf("Approve() error = %v", err)
	}
	waitWorkerIdle(t, u, 2*time.Second)

	got, _ := candRepo.Get(c.ID)
	if got.Status != model.UpgradeCandidateStatusReplaced {
		t.Fatalf("status = %q (error=%q), want replaced (LRA gate skipped, not failed)", got.Status, got.Error)
	}
	vi := parseUpgradeVerifyInfo(got.VerifyInfo)
	if vi == nil || !vi.LRASkipped {
		t.Errorf("verifyInfo should note the skipped LRA gate: %s", got.VerifyInfo)
	}
}

func TestUpgraderNeedsReviewThenForceApprove(t *testing.T) {
	u, mfRepo, candRepo := newApplyTestUpgrader(t)
	libDir, _ := setupLibraryFile(t, mfRepo)
	c := putCandidate(t, candRepo, model.UpgradeCandidateStatusPending)
	calls := stubHappyPipeline(u, "new-flac-content")
	// The new file lacks genre and cover; the old file has both.
	u.tagsFn = func(_ context.Context, path string) (*trackTags, error) {
		tt := fullTrackTags
		if strings.Contains(path, upgradeStagingSubdir) {
			tt.Genre = ""
			tt.HasCover = false
		}
		return &tt, nil
	}

	if err := u.Approve(adminCtx(), c.ID, false); err != nil {
		t.Fatalf("Approve() error = %v", err)
	}
	waitWorkerIdle(t, u, 2*time.Second)

	got, _ := candRepo.Get(c.ID)
	if got.Status != model.UpgradeCandidateStatusNeedsReview {
		t.Fatalf("status = %q (error=%q), want needs_review", got.Status, got.Error)
	}
	vi := parseUpgradeVerifyInfo(got.VerifyInfo)
	if vi == nil {
		t.Fatalf("needs_review candidate has no verifyInfo")
	}
	if len(vi.MissingTags) != 2 || vi.MissingTags[0] != "genre" || vi.MissingTags[1] != "cover" {
		t.Errorf("missingTags = %v, want [genre cover]", vi.MissingTags)
	}
	if vi.StagedPath == "" {
		t.Fatal("stagedPath not kept for a later force-approve")
	}
	if _, err := os.Stat(vi.StagedPath); err != nil {
		t.Fatalf("staged file was not kept: %v", err)
	}

	// Force-approve: must reuse the staged file (no second download) and
	// replace despite the missing tags.
	if err := u.Approve(adminCtx(), c.ID, true); err != nil {
		t.Fatalf("Approve(force) error = %v", err)
	}
	waitWorkerIdle(t, u, 2*time.Second)

	got, _ = candRepo.Get(c.ID)
	if got.Status != model.UpgradeCandidateStatusReplaced {
		t.Fatalf("status after force = %q (error=%q), want replaced", got.Status, got.Error)
	}
	if calls.downloads.Load() != 1 {
		t.Errorf("downloads = %d, want 1 (staged file must be reused)", calls.downloads.Load())
	}
	if _, err := os.Stat(filepath.Join(libDir, "Queen", "01 - Bohemian Rhapsody.flac")); err != nil {
		t.Errorf("replaced file not in place: %v", err)
	}
	vi = parseUpgradeVerifyInfo(got.VerifyInfo)
	if vi == nil || vi.StagedPath != "" {
		t.Errorf("stagedPath should be cleared after replacement: %s", got.VerifyInfo)
	}
	if len(vi.MissingTags) != 2 {
		t.Errorf("missingTags should stay on record after a forced replace: %s", got.VerifyInfo)
	}
}

func TestUpgraderRejectDiscardsStagedFile(t *testing.T) {
	u, mfRepo, candRepo := newApplyTestUpgrader(t)
	setupLibraryFile(t, mfRepo)

	dir, err := upgradeStagingDir()
	if err != nil {
		t.Fatal(err)
	}
	staged := filepath.Join(dir, "kept-for-review.flac")
	if err := os.WriteFile(staged, []byte("staged"), 0o600); err != nil {
		t.Fatal(err)
	}
	c := putCandidate(t, candRepo, model.UpgradeCandidateStatusNeedsReview)
	c.VerifyInfo = (&upgradeVerifyInfo{MissingTags: []string{"genre"}, StagedPath: staged}).marshal()
	if err := candRepo.Put(c); err != nil {
		t.Fatal(err)
	}

	if err := u.Reject(adminCtx(), c.ID); err != nil {
		t.Fatalf("Reject() error = %v", err)
	}
	got, _ := candRepo.Get(c.ID)
	if got.Status != model.UpgradeCandidateStatusRejected {
		t.Errorf("status = %q, want rejected", got.Status)
	}
	if got.ReviewedBy != "admin-1" {
		t.Errorf("reviewedBy = %q, want admin-1", got.ReviewedBy)
	}
	if _, err := os.Stat(staged); !os.IsNotExist(err) {
		t.Errorf("staged file should have been discarded (stat err = %v)", err)
	}

	if err := u.Reject(adminCtx(), c.ID); !errors.Is(err, ErrUpgradeInvalidStatus) {
		t.Errorf("Reject(rejected) err = %v, want ErrUpgradeInvalidStatus", err)
	}
}

func TestUpgraderApproveBatchOnlyPending(t *testing.T) {
	u, mfRepo, candRepo := newApplyTestUpgrader(t)
	setupLibraryFile(t, mfRepo)

	pending := putCandidate(t, candRepo, model.UpgradeCandidateStatusPending)
	review := &model.UpgradeCandidate{MediaFileID: "mf1", LibraryID: 1, Source: UpgradeSourceArchive,
		SourceRef: "a/b.flac", Status: model.UpgradeCandidateStatusNeedsReview}
	replaced := &model.UpgradeCandidate{MediaFileID: "mf1", LibraryID: 1, Source: UpgradeSourceArchive,
		SourceRef: "c/d.flac", Status: model.UpgradeCandidateStatusReplaced}
	for _, c := range []*model.UpgradeCandidate{review, replaced} {
		if err := candRepo.Put(c); err != nil {
			t.Fatal(err)
		}
	}

	accepted, err := u.ApproveBatch(adminCtx(), []string{pending.ID, review.ID, replaced.ID, "missing-id"})
	if err != nil {
		t.Fatalf("ApproveBatch() error = %v", err)
	}
	if len(accepted) != 1 || accepted[0] != pending.ID {
		t.Errorf("accepted = %v, want only the pending candidate %q", accepted, pending.ID)
	}
	waitWorkerIdle(t, u, 2*time.Second)

	if got, _ := candRepo.Get(review.ID); got.Status != model.UpgradeCandidateStatusNeedsReview {
		t.Errorf("needs_review candidate status = %q, must not change in a batch", got.Status)
	}
	if got, _ := candRepo.Get(replaced.ID); got.Status != model.UpgradeCandidateStatusReplaced {
		t.Errorf("replaced candidate status = %q, must not change in a batch", got.Status)
	}
	if got, _ := candRepo.Get(pending.ID); got.Status == model.UpgradeCandidateStatusPending {
		t.Errorf("accepted candidate was never processed (still pending)")
	}
}

func TestUpgraderRecoverResetsInterruptedDownloads(t *testing.T) {
	u, mfRepo, candRepo := newApplyTestUpgrader(t)
	setupLibraryFile(t, mfRepo)
	c := putCandidate(t, candRepo, model.UpgradeCandidateStatusDownloading)
	stubHappyPipeline(u, "new-flac-content")

	// Expired and current backup folders, to exercise retention cleanup.
	backupRoot := filepath.Join(conf.Server.DataFolder.String(), upgradeBackupSubdir)
	expired := filepath.Join(backupRoot, "2020-01-01")
	current := filepath.Join(backupRoot, time.Now().Format(upgradeBackupDayFormat))
	for _, dir := range []string{expired, current} {
		if err := os.MkdirAll(dir, 0o755); err != nil {
			t.Fatal(err)
		}
	}

	if err := u.Recover(context.Background()); err != nil {
		t.Fatalf("Recover() error = %v", err)
	}
	waitWorkerIdle(t, u, 2*time.Second)

	got, _ := candRepo.Get(c.ID)
	if got.Status != model.UpgradeCandidateStatusReplaced {
		t.Fatalf("status = %q (error=%q), want replaced (downloading → approved → processed)", got.Status, got.Error)
	}
	if _, err := os.Stat(expired); !os.IsNotExist(err) {
		t.Errorf("expired backup folder was not pruned (stat err = %v)", err)
	}
	if _, err := os.Stat(current); err != nil {
		t.Errorf("current backup folder must be kept: %v", err)
	}
}

// TestUpgraderRedownloadRemovesStaleStagedFile covers the other half of M1:
// a candidate that still carries a StagedPath from an earlier processing
// round (e.g. a crash-interrupted force-replace, requeued by Recover without
// its "force" flag — model.UpgradeCandidate does not persist it) goes through
// a fresh download here, and the stale file from the earlier round must be
// removed instead of leaking in the staging folder once VerifyInfo is
// overwritten.
func TestUpgraderRedownloadRemovesStaleStagedFile(t *testing.T) {
	u, mfRepo, candRepo := newApplyTestUpgrader(t)
	setupLibraryFile(t, mfRepo)
	stubHappyPipeline(u, "new-flac-content")

	dir, err := upgradeStagingDir()
	if err != nil {
		t.Fatal(err)
	}
	staleStaged := filepath.Join(dir, "stale-from-before-crash.flac")
	if err := os.WriteFile(staleStaged, []byte("stale"), 0o600); err != nil {
		t.Fatal(err)
	}

	c := putCandidate(t, candRepo, model.UpgradeCandidateStatusDownloading)
	c.VerifyInfo = (&upgradeVerifyInfo{StagedPath: staleStaged}).marshal()
	if err := candRepo.Put(c); err != nil {
		t.Fatal(err)
	}

	if err := u.Recover(context.Background()); err != nil {
		t.Fatalf("Recover() error = %v", err)
	}
	waitWorkerIdle(t, u, 2*time.Second)

	got, _ := candRepo.Get(c.ID)
	if got.Status != model.UpgradeCandidateStatusReplaced {
		t.Fatalf("status = %q (error=%q), want replaced", got.Status, got.Error)
	}
	if _, err := os.Stat(staleStaged); !os.IsNotExist(err) {
		t.Errorf("stale staged file from the earlier round was not cleaned up (stat err = %v)", err)
	}
}

// TestUpgraderSweepStaging covers M1: sweepStaging must remove orphaned
// *.part temp files (crash mid-download) and any staged file no candidate
// still needs, while never touching files referenced by the StagedPath of a
// candidate currently in needs_review or downloading status.
func TestUpgraderSweepStaging(t *testing.T) {
	u, _, candRepo := newApplyTestUpgrader(t)

	dir, err := upgradeStagingDir()
	if err != nil {
		t.Fatal(err)
	}
	write := func(name, content string) string {
		p := filepath.Join(dir, name)
		if err := os.WriteFile(p, []byte(content), 0o600); err != nil {
			t.Fatal(err)
		}
		return p
	}
	putWithStagedPath := func(sourceRef, status, stagedPath string) {
		c := &model.UpgradeCandidate{
			MediaFileID: "mf1", LibraryID: 1, Source: UpgradeSourceArchive,
			SourceRef: sourceRef, Status: status,
		}
		if stagedPath != "" {
			c.VerifyInfo = (&upgradeVerifyInfo{StagedPath: stagedPath}).marshal()
		}
		if err := candRepo.Put(c); err != nil {
			t.Fatal(err)
		}
	}

	keptReview := write("kept-needs-review.flac", "kept-review")
	keptDownloading := write("kept-downloading.flac", "kept-downloading")
	staleFromFailed := write("stale-referenced-by-failed.flac", "stale")
	orphan := write("orphan.flac", "orphan")

	oldPart := write(".nd-upgrade-old.part", "old part")
	if err := os.Chtimes(oldPart, time.Now().Add(-2*time.Hour), time.Now().Add(-2*time.Hour)); err != nil {
		t.Fatal(err)
	}
	freshPart := write(".nd-upgrade-fresh.part", "fresh part")

	putWithStagedPath("a/needs-review.flac", model.UpgradeCandidateStatusNeedsReview, keptReview)
	putWithStagedPath("b/downloading.flac", model.UpgradeCandidateStatusDownloading, keptDownloading)
	// A failed candidate's old StagedPath no longer protects the file: only
	// needs_review/downloading candidates do.
	putWithStagedPath("c/failed.flac", model.UpgradeCandidateStatusFailed, staleFromFailed)

	u.sweepStaging(context.Background())

	assertExists := func(path string, want bool) {
		t.Helper()
		_, err := os.Stat(path)
		if got := err == nil; got != want {
			t.Errorf("exists(%s) = %v (err=%v), want %v", path, got, err, want)
		}
	}
	assertExists(keptReview, true)
	assertExists(keptDownloading, true)
	assertExists(staleFromFailed, false)
	assertExists(orphan, false)
	assertExists(oldPart, false)
	assertExists(freshPart, true)
}

func TestUpgraderReplaceRollsBackOnFailure(t *testing.T) {
	u, mfRepo, candRepo := newApplyTestUpgrader(t)
	libDir, mf := setupLibraryFile(t, mfRepo)
	c := putCandidate(t, candRepo, model.UpgradeCandidateStatusPending)
	stubHappyPipeline(u, "new-flac-content")
	// Sabotage the placement step (after the original was already moved to
	// the backup): a non-empty directory squats on the new file's path, so
	// both the rename and the copy fallback fail.
	squatter := filepath.Join(libDir, "Queen", "01 - Bohemian Rhapsody.flac")
	if err := os.MkdirAll(filepath.Join(squatter, "sub"), 0o755); err != nil {
		t.Fatal(err)
	}

	if err := u.Approve(adminCtx(), c.ID, false); err != nil {
		t.Fatalf("Approve() error = %v", err)
	}
	waitWorkerIdle(t, u, 2*time.Second)

	got, _ := candRepo.Get(c.ID)
	if got.Status != model.UpgradeCandidateStatusFailed {
		t.Fatalf("status = %q, want failed", got.Status)
	}
	// The original must have been restored from the backup.
	data, err := os.ReadFile(filepath.Join(libDir, mf.Path))
	if err != nil {
		t.Fatalf("original file was not restored: %v", err)
	}
	if string(data) != "old-mp3-content" {
		t.Errorf("restored content = %q, want the original", data)
	}
}

// TestUpgraderReplaceRejectsCollisionWithUnrelatedFile covers C1: the upgrade
// changes the extension (mp3 -> flac), and the user already owns an unrelated
// "01 - Bohemian Rhapsody.flac" next to the mp3 being upgraded. Without a
// conflict check, moveFile's rename-or-copy would silently clobber that
// unrelated file. The candidate must instead fail, the unrelated file must be
// left untouched, and the original mp3 must be restored to its original path.
func TestUpgraderReplaceRejectsCollisionWithUnrelatedFile(t *testing.T) {
	u, mfRepo, candRepo := newApplyTestUpgrader(t)
	libDir, mf := setupLibraryFile(t, mfRepo)
	c := putCandidate(t, candRepo, model.UpgradeCandidateStatusPending)
	stubHappyPipeline(u, "new-flac-content")

	unrelated := filepath.Join(libDir, "Queen", "01 - Bohemian Rhapsody.flac")
	if err := os.WriteFile(unrelated, []byte("unrelated-preexisting-flac"), 0o644); err != nil {
		t.Fatal(err)
	}

	if err := u.Approve(adminCtx(), c.ID, false); err != nil {
		t.Fatalf("Approve() error = %v", err)
	}
	waitWorkerIdle(t, u, 2*time.Second)

	got, _ := candRepo.Get(c.ID)
	if got.Status != model.UpgradeCandidateStatusFailed {
		t.Fatalf("status = %q (error=%q), want failed", got.Status, got.Error)
	}
	if !strings.Contains(got.Error, "already exists") {
		t.Errorf("error = %q, want a message about the target filename already existing", got.Error)
	}

	// The unrelated pre-existing file must be completely untouched.
	data, err := os.ReadFile(unrelated)
	if err != nil {
		t.Fatalf("unrelated file was removed: %v", err)
	}
	if string(data) != "unrelated-preexisting-flac" {
		t.Errorf("unrelated file content = %q, want untouched", data)
	}

	// The original mp3 must be restored from the backup to its original path.
	data, err = os.ReadFile(filepath.Join(libDir, mf.Path))
	if err != nil {
		t.Fatalf("original file was not restored: %v", err)
	}
	if string(data) != "old-mp3-content" {
		t.Errorf("restored content = %q, want the original", data)
	}
}
