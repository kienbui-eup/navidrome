package core

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/vi2play/vi2play/conf"
	"github.com/vi2play/vi2play/model"
	"github.com/vi2play/vi2play/tests"
)

// stubImporter is a minimal Importer stub for Upgrader scan tests. It embeds
// the (nil) Importer interface so any method not overridden here panics if
// called, the same trick tests/mock_mediafile_repo.go uses for
// model.MediaFileRepository.
type stubImporter struct {
	Importer

	searchArchiveFunc func(ctx context.Context, query string, rows int) ([]ArchiveItem, error)
	archiveFilesFunc  func(ctx context.Context, identifier string) ([]ArchiveFile, error)
	listDriveFunc     func(ctx context.Context, driveURL string) ([]DriveFile, error)
	parseFeedFunc     func(ctx context.Context, feedURL string) ([]FeedItem, error)
}

func (s *stubImporter) SearchArchive(ctx context.Context, query string, rows int) ([]ArchiveItem, error) {
	if s.searchArchiveFunc == nil {
		return nil, nil
	}
	return s.searchArchiveFunc(ctx, query, rows)
}

func (s *stubImporter) ArchiveFiles(ctx context.Context, identifier string) ([]ArchiveFile, error) {
	if s.archiveFilesFunc == nil {
		return nil, nil
	}
	return s.archiveFilesFunc(ctx, identifier)
}

func (s *stubImporter) ListDrive(ctx context.Context, driveURL string) ([]DriveFile, error) {
	if s.listDriveFunc == nil {
		return nil, nil
	}
	return s.listDriveFunc(ctx, driveURL)
}

func (s *stubImporter) ParseFeed(ctx context.Context, feedURL string) ([]FeedItem, error) {
	if s.parseFeedFunc == nil {
		return nil, nil
	}
	return s.parseFeedFunc(ctx, feedURL)
}

var _ Importer = (*stubImporter)(nil)

// withTestUpgradeConfig sets conf.Server.Upgrade to sane test defaults and
// zeroes the inter-search delay so scans run instantly; returns a restore func.
func withTestUpgradeConfig(t *testing.T) {
	t.Helper()
	orig := conf.Server.Upgrade
	origDelay := upgradeSearchDelay
	conf.Server.Upgrade.Enabled = true
	conf.Server.Upgrade.Sources = "archive"
	conf.Server.Upgrade.MinMatchScore = 70
	conf.Server.Upgrade.MinBitRate = 0
	conf.Server.Upgrade.MaxCandidatesPerScan = 200
	upgradeSearchDelay = 0
	t.Cleanup(func() {
		conf.Server.Upgrade = orig
		upgradeSearchDelay = origDelay
	})
}

// waitScanDone polls u.ScanStatus() until the scan stops running or the
// timeout elapses (failing the test in that case).
func waitScanDone(t *testing.T, u Upgrader, timeout time.Duration) {
	t.Helper()
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		if running, _, _, _ := u.ScanStatus(); !running {
			return
		}
		time.Sleep(2 * time.Millisecond)
	}
	t.Fatal("scan did not finish before the timeout")
}

func newTestUpgradeDS() (*tests.MockDataStore, *tests.MockMediaFileRepo, *tests.MockUpgradeCandidateRepo) {
	ds := &tests.MockDataStore{}
	mfRepo := tests.CreateMockMediaFileRepo()
	candRepo := tests.CreateMockUpgradeCandidateRepo()
	ds.MockedMediaFile = mfRepo
	ds.MockedUpgradeCandidate = candRepo
	return ds, mfRepo, candRepo
}

func TestUpgraderStartScanQueuesWinningCandidate(t *testing.T) {
	withTestUpgradeConfig(t)
	ds, mfRepo, candRepo := newTestUpgradeDS()
	mfRepo.SetData(model.MediaFiles{
		{ID: "mf1", LibraryID: 1, Artist: "Queen", Title: "Bohemian Rhapsody", Suffix: "mp3", BitRate: 128},
	})

	imp := &stubImporter{
		searchArchiveFunc: func(_ context.Context, query string, _ int) ([]ArchiveItem, error) {
			return []ArchiveItem{{Identifier: "queen-1975"}}, nil
		},
		archiveFilesFunc: func(_ context.Context, identifier string) ([]ArchiveFile, error) {
			return []ArchiveFile{{Name: "bohemian_rhapsody.flac", Format: "Flac", Title: "Bohemian Rhapsody"}}, nil
		},
	}
	u := NewUpgrader(ds, imp, nil)

	if err := u.StartScan(context.Background(), 0, nil); err != nil {
		t.Fatalf("StartScan() error = %v", err)
	}
	waitScanDone(t, u, time.Second)

	running, scanned, total, found := u.ScanStatus()
	if running {
		t.Fatal("scan should have finished")
	}
	if total != 1 || scanned != 1 {
		t.Errorf("ScanStatus() scanned/total = %d/%d, want 1/1", scanned, total)
	}
	if found != 1 {
		t.Errorf("ScanStatus() found = %d, want 1", found)
	}

	all, err := candRepo.GetAll()
	if err != nil {
		t.Fatalf("GetAll() error = %v", err)
	}
	if len(all) != 1 {
		t.Fatalf("len(candidates) = %d, want 1", len(all))
	}
	c := all[0]
	if c.MediaFileID != "mf1" || c.Source != UpgradeSourceArchive || c.SourceRef != "queen-1975/bohemian_rhapsody.flac" {
		t.Errorf("unexpected candidate: %+v", c)
	}
	if c.Status != model.UpgradeCandidateStatusPending {
		t.Errorf("candidate status = %q, want %q", c.Status, model.UpgradeCandidateStatusPending)
	}
	if c.Format != "flac" {
		t.Errorf("candidate format = %q, want flac", c.Format)
	}
	if c.MatchScore < conf.Server.Upgrade.MinMatchScore {
		t.Errorf("candidate match score = %d, below threshold %d", c.MatchScore, conf.Server.Upgrade.MinMatchScore)
	}
}

func TestUpgraderStartScanSkipsLosslessTracks(t *testing.T) {
	withTestUpgradeConfig(t)
	ds, mfRepo, candRepo := newTestUpgradeDS()
	mfRepo.SetData(model.MediaFiles{
		{ID: "mf1", LibraryID: 1, Artist: "Queen", Title: "Bohemian Rhapsody", Suffix: "flac", BitRate: 1000},
	})

	called := false
	imp := &stubImporter{
		searchArchiveFunc: func(_ context.Context, _ string, _ int) ([]ArchiveItem, error) {
			called = true
			return nil, nil
		},
	}
	u := NewUpgrader(ds, imp, nil)

	if err := u.StartScan(context.Background(), 0, nil); err != nil {
		t.Fatalf("StartScan() error = %v", err)
	}
	waitScanDone(t, u, time.Second)

	if called {
		t.Error("a lossless track should never be searched against a source")
	}
	_, _, total, found := u.ScanStatus()
	if total != 0 {
		t.Errorf("total = %d, want 0 (lossless track excluded)", total)
	}
	if found != 0 {
		t.Errorf("found = %d, want 0", found)
	}
	all, _ := candRepo.GetAll()
	if len(all) != 0 {
		t.Errorf("len(candidates) = %d, want 0", len(all))
	}
}

func TestUpgraderStartScanDedupesAcrossRuns(t *testing.T) {
	withTestUpgradeConfig(t)
	ds, mfRepo, candRepo := newTestUpgradeDS()
	mfRepo.SetData(model.MediaFiles{
		{ID: "mf1", LibraryID: 1, Artist: "Queen", Title: "Bohemian Rhapsody", Suffix: "mp3", BitRate: 128},
	})
	imp := &stubImporter{
		searchArchiveFunc: func(_ context.Context, _ string, _ int) ([]ArchiveItem, error) {
			return []ArchiveItem{{Identifier: "queen-1975"}}, nil
		},
		archiveFilesFunc: func(_ context.Context, _ string) ([]ArchiveFile, error) {
			return []ArchiveFile{{Name: "bohemian_rhapsody.flac", Format: "Flac", Title: "Bohemian Rhapsody"}}, nil
		},
	}
	u := NewUpgrader(ds, imp, nil)

	for i := 0; i < 2; i++ {
		if err := u.StartScan(context.Background(), 0, nil); err != nil {
			t.Fatalf("StartScan() [run %d] error = %v", i, err)
		}
		waitScanDone(t, u, time.Second)
	}

	all, _ := candRepo.GetAll()
	if len(all) != 1 {
		t.Errorf("len(candidates) after 2 scans = %d, want 1 (dedup via Exists)", len(all))
	}
}

func TestUpgraderStartScanContinuesAfterSourceError(t *testing.T) {
	withTestUpgradeConfig(t)
	ds, mfRepo, candRepo := newTestUpgradeDS()
	mfRepo.SetData(model.MediaFiles{
		{ID: "mf1", LibraryID: 1, Artist: "Queen", Title: "Bohemian Rhapsody", Suffix: "mp3", BitRate: 128},
		{ID: "mf2", LibraryID: 1, Artist: "Pink Floyd", Title: "Money", Suffix: "mp3", BitRate: 128},
	})
	imp := &stubImporter{
		searchArchiveFunc: func(_ context.Context, query string, _ int) ([]ArchiveItem, error) {
			return nil, errors.New("archive.org is down")
		},
	}
	u := NewUpgrader(ds, imp, nil)

	if err := u.StartScan(context.Background(), 0, nil); err != nil {
		t.Fatalf("StartScan() error = %v", err)
	}
	waitScanDone(t, u, time.Second)

	_, scanned, total, found := u.ScanStatus()
	if total != 2 || scanned != 2 {
		t.Errorf("scanned/total = %d/%d, want 2/2 (a source failure must not abort the scan)", scanned, total)
	}
	if found != 0 {
		t.Errorf("found = %d, want 0", found)
	}
	all, _ := candRepo.GetAll()
	if len(all) != 0 {
		t.Errorf("len(candidates) = %d, want 0", len(all))
	}
}

func TestUpgraderStartScanRejectsConcurrentScans(t *testing.T) {
	withTestUpgradeConfig(t)
	ds, mfRepo, _ := newTestUpgradeDS()
	mfRepo.SetData(model.MediaFiles{
		{ID: "mf1", LibraryID: 1, Artist: "Queen", Title: "Bohemian Rhapsody", Suffix: "mp3", BitRate: 128},
	})

	release := make(chan struct{})
	started := make(chan struct{})
	imp := &stubImporter{
		searchArchiveFunc: func(_ context.Context, _ string, _ int) ([]ArchiveItem, error) {
			close(started)
			<-release
			return nil, nil
		},
	}
	u := NewUpgrader(ds, imp, nil)

	if err := u.StartScan(context.Background(), 0, nil); err != nil {
		t.Fatalf("StartScan() error = %v", err)
	}
	<-started // make sure the first scan is actually in flight

	if err := u.StartScan(context.Background(), 0, nil); !errors.Is(err, ErrUpgradeScanInProgress) {
		t.Errorf("StartScan() while running: err = %v, want ErrUpgradeScanInProgress", err)
	}

	close(release)
	waitScanDone(t, u, time.Second)
}

func TestUpgraderMaxCandidatesPerScan(t *testing.T) {
	withTestUpgradeConfig(t)
	conf.Server.Upgrade.MaxCandidatesPerScan = 1
	ds, mfRepo, candRepo := newTestUpgradeDS()
	mfRepo.SetData(model.MediaFiles{
		{ID: "mf1", LibraryID: 1, Artist: "Queen", Title: "Bohemian Rhapsody", Suffix: "mp3", BitRate: 128},
		{ID: "mf2", LibraryID: 1, Artist: "Pink Floyd", Title: "Money", Suffix: "mp3", BitRate: 128},
	})
	imp := &stubImporter{
		searchArchiveFunc: func(_ context.Context, query string, _ int) ([]ArchiveItem, error) {
			return []ArchiveItem{{Identifier: "id-" + query}}, nil
		},
		archiveFilesFunc: func(_ context.Context, identifier string) ([]ArchiveFile, error) {
			// Match whichever track this identifier was searched for by tagging
			// the filename with the artist/title so both tracks can win.
			return []ArchiveFile{{Name: identifier + ".flac", Format: "Flac", Title: identifier}}, nil
		},
	}
	u := NewUpgrader(ds, imp, nil)

	if err := u.StartScan(context.Background(), 0, nil); err != nil {
		t.Fatalf("StartScan() error = %v", err)
	}
	waitScanDone(t, u, time.Second)

	all, _ := candRepo.GetAll()
	if len(all) != 1 {
		t.Errorf("len(candidates) = %d, want 1 (MaxCandidatesPerScan=1)", len(all))
	}
}

func TestUpgraderCancelScan(t *testing.T) {
	withTestUpgradeConfig(t)
	upgradeSearchDelay = time.Hour // never elapses on its own; only cancellation should stop the scan
	ds, mfRepo, _ := newTestUpgradeDS()
	mfRepo.SetData(model.MediaFiles{
		{ID: "mf1", LibraryID: 1, Artist: "Queen", Title: "Bohemian Rhapsody", Suffix: "mp3", BitRate: 128},
		{ID: "mf2", LibraryID: 1, Artist: "Pink Floyd", Title: "Money", Suffix: "mp3", BitRate: 128},
	})
	started := make(chan struct{}, 1)
	imp := &stubImporter{
		searchArchiveFunc: func(_ context.Context, _ string, _ int) ([]ArchiveItem, error) {
			select {
			case started <- struct{}{}:
			default:
			}
			return nil, nil
		},
	}
	u := NewUpgrader(ds, imp, nil)

	if err := u.StartScan(context.Background(), 0, nil); err != nil {
		t.Fatalf("StartScan() error = %v", err)
	}
	<-started
	u.CancelScan()
	waitScanDone(t, u, time.Second)

	_, scanned, total, _ := u.ScanStatus()
	if scanned >= total {
		t.Errorf("scanned/total = %d/%d, expected the scan to stop early after cancellation", scanned, total)
	}
}

func TestUpgraderSoulseekScan(t *testing.T) {
	withTestUpgradeConfig(t)
	conf.Server.Upgrade.Sources = "soulseek"
	conf.Server.Soulseek.Enabled = true

	// Set up mock Soulseek REST server
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		if r.Method == "POST" && r.URL.Path == "/api/v0/searches" {
			w.Write([]byte(`{"id":"soulseek-search-uuid"}`))
			return
		}
		if r.Method == "GET" && r.URL.Path == "/api/v0/searches/soulseek-search-uuid" {
			w.Write([]byte(`{
				"id": "soulseek-search-uuid",
				"searchText": "Queen Bohemian Rhapsody",
				"results": [
					{
						"username": "flac_collector",
						"files": [
							{
								"filename": "Queen/A Night at the Opera/Bohemian Rhapsody.flac",
								"size": 45000000,
								"bitRate": 1411,
								"extension": "flac",
								"length": 355
							}
						]
					}
				]
			}`))
			return
		}
	}))
	defer server.Close()

	conf.Server.Soulseek.BaseURL = server.URL

	ds, mfRepo, candRepo := newTestUpgradeDS()
	mfRepo.SetData(model.MediaFiles{
		{ID: "mf1", LibraryID: 1, Artist: "Queen", Title: "Bohemian Rhapsody", Suffix: "mp3", BitRate: 128, Duration: 355},
	})

	u := NewUpgrader(ds, nil, nil)

	if err := u.StartScan(context.Background(), 0, nil); err != nil {
		t.Fatalf("StartScan() error = %v", err)
	}
	waitScanDone(t, u, 2*time.Second)

	_, scanned, total, found := u.ScanStatus()
	if total != 1 || scanned != 1 || found != 1 {
		t.Errorf("ScanStatus() total/scanned/found = %d/%d/%d, want 1/1/1", total, scanned, found)
	}

	all, err := candRepo.GetAll()
	if err != nil {
		t.Fatalf("GetAll() error = %v", err)
	}
	if len(all) != 1 {
		t.Fatalf("len(candidates) = %d, want 1", len(all))
	}

	c := all[0]
	if c.MediaFileID != "mf1" || c.Source != "soulseek" || c.SourceRef != "flac_collector|Queen/A Night at the Opera/Bohemian Rhapsody.flac|45000000" {
		t.Errorf("unexpected candidate: %+v", c)
	}
	if c.Format != "flac" {
		t.Errorf("candidate format = %q, want flac", c.Format)
	}
}
