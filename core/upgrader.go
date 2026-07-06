package core

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"sync"
	"time"

	"github.com/Masterminds/squirrel"
	"github.com/navidrome/navidrome/conf"
	"github.com/navidrome/navidrome/log"
	"github.com/navidrome/navidrome/model"
)

// Upgrade candidate sources, per the "Matching" table in
// docs/superpowers/specs/2026-07-06-quality-upgrader-design.md.
const (
	UpgradeSourceArchive = "archive"
	UpgradeSourceDrive   = "drive"
	UpgradeSourceRSS     = "rss"
)

var knownUpgradeSources = map[string]bool{
	UpgradeSourceArchive: true,
	UpgradeSourceDrive:   true,
	UpgradeSourceRSS:     true,
}

// upgradeSearchDelay is the pause observed between remote search calls to a
// given source while scanning (design doc: "delay 1–2s giữa các request
// search"). It is a package var (rather than a fixed const) so tests can zero
// it to run instantly.
var upgradeSearchDelay = 1500 * time.Millisecond

// ErrUpgradeScanInProgress is returned by StartScan when a scan is already
// running. Phase 4's HTTP handler is expected to map this to a 409 response.
var ErrUpgradeScanInProgress = errors.New("upgrade scan already in progress")

// Upgrader finds higher-quality versions of tracks already in the library,
// searching the same public sources Importer downloads from (Internet
// Archive, Google Drive, RSS), and queues them (status "pending") for admin
// review. It does not download, verify, or replace anything — see
// docs/superpowers/specs/2026-07-06-quality-upgrader-design.md, "Luồng xử lý"
// steps 1-4 (steps 5-8 are Phase 3/4).
type Upgrader interface {
	// StartScan starts a background scan for upgrade candidates. When
	// mediaFileIDs is non-empty, only those tracks are considered; otherwise
	// libraryID restricts the scan to one library (libraryID <= 0 = all
	// libraries). Returns ErrUpgradeScanInProgress if a scan is already
	// running.
	StartScan(ctx context.Context, libraryID int, mediaFileIDs []string) error
	// ScanStatus reports the progress of the current (or most recently
	// finished) scan: scanned/total tracks examined, and how many candidates
	// were found (queued) so far.
	ScanStatus() (running bool, scanned, total, found int)
	// CancelScan requests cancellation of a running scan; a no-op if none is
	// running.
	CancelScan()
}

// upgradeScanState is the mutex-guarded progress of the current/last scan,
// mirroring the ImportJob pattern in core/importer.go.
type upgradeScanState struct {
	running bool
	scanned int
	total   int
	found   int
	cancel  context.CancelFunc
}

type upgrader struct {
	ds  model.DataStore
	imp Importer

	mu    sync.Mutex
	state upgradeScanState
}

// NewUpgrader creates an Upgrader backed by ds and imp. imp's
// SearchArchive/ArchiveFiles/ListDrive/ParseFeed methods are reused to find
// candidates; nothing is downloaded here (that's Phase 3).
func NewUpgrader(ds model.DataStore, imp Importer) Upgrader {
	return &upgrader{ds: ds, imp: imp}
}

func (u *upgrader) StartScan(ctx context.Context, libraryID int, mediaFileIDs []string) error {
	u.mu.Lock()
	if u.state.running {
		u.mu.Unlock()
		return ErrUpgradeScanInProgress
	}
	// Detach from the request context (so an HTTP request timing out does not
	// abort the scan) but keep a dedicated cancel for explicit cancellation,
	// mirroring Importer.StartImportJob/runJob.
	scanCtx, cancel := context.WithCancel(context.WithoutCancel(ctx))
	u.state = upgradeScanState{running: true, cancel: cancel}
	u.mu.Unlock()

	go u.runScan(scanCtx, libraryID, mediaFileIDs)
	return nil
}

func (u *upgrader) ScanStatus() (running bool, scanned, total, found int) {
	u.mu.Lock()
	defer u.mu.Unlock()
	return u.state.running, u.state.scanned, u.state.total, u.state.found
}

func (u *upgrader) CancelScan() {
	u.mu.Lock()
	cancel := u.state.cancel
	u.mu.Unlock()
	if cancel != nil {
		cancel()
	}
}

// runScan performs steps 1-4 of the design doc's "Luồng xử lý": select
// upgradable tracks, search enabled sources for candidates (with a delay
// between search calls), score/gate them, and queue the survivors.
func (u *upgrader) runScan(ctx context.Context, libraryID int, mediaFileIDs []string) {
	defer func() {
		u.mu.Lock()
		u.state.running = false
		u.state.cancel = nil
		u.mu.Unlock()
	}()

	tracks, err := u.selectUpgradableTracks(ctx, libraryID, mediaFileIDs)
	if err != nil {
		log.Error(ctx, "Upgrader: error selecting tracks to scan", err)
		return
	}
	u.mu.Lock()
	u.state.total = len(tracks)
	u.mu.Unlock()

	sources := parseUpgradeSources(conf.Server.Upgrade.Sources)
	maxCandidates := conf.Server.Upgrade.MaxCandidatesPerScan
	inserted := 0

	log.Info(ctx, "Upgrader: starting scan", "tracks", len(tracks), "sources", sources, "libraryId", libraryID)

scan:
	for _, mf := range tracks {
		if ctx.Err() != nil {
			break
		}
		for _, source := range sources {
			if ctx.Err() != nil {
				break scan
			}
			if maxCandidates > 0 && inserted >= maxCandidates {
				break scan
			}

			matches, err := u.searchSource(ctx, source, mf)
			if err != nil {
				log.Warn(ctx, "Upgrader: source failed, skipping for this track", "source", source, "mediaFileId", mf.ID, err)
			}
			for _, m := range matches {
				if maxCandidates > 0 && inserted >= maxCandidates {
					break scan
				}
				queued, err := u.queueCandidate(ctx, mf, m)
				if err != nil {
					log.Warn(ctx, "Upgrader: error queuing candidate", "mediaFileId", mf.ID, "source", m.source, "ref", m.sourceRef, err)
					continue
				}
				if queued {
					inserted++
					u.mu.Lock()
					u.state.found++
					u.mu.Unlock()
				}
			}

			if !sleepCtx(ctx, upgradeSearchDelay) {
				break scan
			}
		}
		u.mu.Lock()
		u.state.scanned++
		u.mu.Unlock()
	}

	_, scanned, total, found := u.ScanStatus()
	log.Info(ctx, "Upgrader: scan finished", "scanned", scanned, "total", total, "found", found, "canceled", ctx.Err() != nil)
}

// sleepCtx sleeps for d or until ctx is done, whichever comes first. Returns
// false when ctx was done, so callers can stop immediately instead of
// starting another iteration.
func sleepCtx(ctx context.Context, d time.Duration) bool {
	if d <= 0 {
		return ctx.Err() == nil
	}
	t := time.NewTimer(d)
	defer t.Stop()
	select {
	case <-t.C:
		return true
	case <-ctx.Done():
		return false
	}
}

// selectUpgradableTracks resolves the set of tracks to scan (see "Track có
// thể nâng cấp" in the design doc): either the explicit mediaFileIDs, or all
// non-missing tracks in libraryID (0 = every library), further narrowed down
// in-process to lossy tracks under the configured bitrate ceiling. The
// in-process check (isUpgradableTrack) is authoritative — the SQL filters
// here are a best-effort narrowing only, since "is this track lossy" depends
// on model.MediaFile.AudioCodec()'s suffix+bit-depth heuristic (e.g. m4a
// AAC-vs-ALAC), not a single column.
func (u *upgrader) selectUpgradableTracks(ctx context.Context, libraryID int, mediaFileIDs []string) (model.MediaFiles, error) {
	filters := []squirrel.Sqlizer{squirrel.Eq{"missing": false}}
	switch {
	case len(mediaFileIDs) > 0:
		filters = append(filters, squirrel.Eq{"media_file.id": mediaFileIDs})
	case libraryID > 0:
		filters = append(filters, squirrel.Eq{"library_id": libraryID})
	}
	all, err := u.ds.MediaFile(ctx).GetAll(model.QueryOptions{Filters: squirrel.And(filters)})
	if err != nil {
		return nil, err
	}
	result := make(model.MediaFiles, 0, len(all))
	for _, mf := range all {
		if isUpgradableTrack(mf) {
			result = append(result, mf)
		}
	}
	return result, nil
}

// isUpgradableTrack reports whether mf is a candidate for scanning: a lossy
// track (per model.MediaFile.AudioCodec(), see isLosslessCodec) whose bitrate
// is below Upgrade.MinBitRate (MinBitRate <= 0 means no ceiling: scan every
// lossy track). Lossless tracks are never scanned.
func isUpgradableTrack(mf model.MediaFile) bool {
	if isLosslessCodec(mf.AudioCodec()) {
		return false
	}
	minBitRate := conf.Server.Upgrade.MinBitRate
	if minBitRate > 0 && mf.BitRate >= minBitRate {
		return false
	}
	return true
}

// parseUpgradeSources parses Upgrade.Sources ("archive,drive,rss") into a
// deduplicated, order-preserving list of known sources. Unknown tokens are
// silently ignored (defensive: config validation should already catch this at
// startup).
func parseUpgradeSources(csv string) []string {
	seen := map[string]bool{}
	var out []string
	for _, s := range strings.Split(csv, ",") {
		s = strings.ToLower(strings.TrimSpace(s))
		if s == "" || seen[s] || !knownUpgradeSources[s] {
			continue
		}
		seen[s] = true
		out = append(out, s)
	}
	return out
}

// upgradeMatch is the result of matching one candidate (found via one of the
// enabled sources) against a track, ready to become a model.UpgradeCandidate.
type upgradeMatch struct {
	source     string
	sourceRef  string
	title      string
	format     string
	estBitRate int
	estSize    int64
	matchScore int
}

func (u *upgrader) searchSource(ctx context.Context, source string, mf model.MediaFile) ([]upgradeMatch, error) {
	switch source {
	case UpgradeSourceArchive:
		return u.searchArchive(ctx, mf)
	case UpgradeSourceDrive:
		return u.searchDrive(ctx, mf)
	case UpgradeSourceRSS:
		return u.searchRSS(ctx, mf)
	default:
		return nil, fmt.Errorf("unknown upgrade source %q", source)
	}
}

// searchArchive looks for mf on the Internet Archive: SearchArchive("artist
// title", mediatype:audio) then ArchiveFiles per hit, matched by name +
// duration (±5s) and gated by the stage-1 quality rule.
func (u *upgrader) searchArchive(ctx context.Context, mf model.MediaFile) ([]upgradeMatch, error) {
	query := strings.TrimSpace(mf.Artist + " " + mf.Title)
	if query == "" {
		return nil, nil
	}
	items, err := u.imp.SearchArchive(ctx, query, 10)
	if err != nil {
		return nil, err
	}
	original := originalQuality(mf)
	var matches []upgradeMatch
	for _, item := range items {
		if ctx.Err() != nil {
			return matches, ctx.Err()
		}
		files, err := u.imp.ArchiveFiles(ctx, item.Identifier)
		if err != nil {
			log.Warn(ctx, "Upgrader: could not list Internet Archive item files, skipping item", "identifier", item.Identifier, err)
			continue
		}
		for _, f := range files {
			title := displayTitle(f.Title, f.Name)
			durSec, hasDur := parseIADuration(f.Length)
			var deltaSec float64
			if hasDur {
				deltaSec = durSec - float64(mf.Duration)
			}
			score := matchScore(mf.Artist, mf.Title, title, hasDur, deltaSec)
			if score < conf.Server.Upgrade.MinMatchScore {
				continue
			}
			q := estimateQuality(f.Name, f.Format)
			if !candidateWins(original, q) {
				continue
			}
			matches = append(matches, upgradeMatch{
				source:     UpgradeSourceArchive,
				sourceRef:  item.Identifier + "/" + f.Name,
				title:      title,
				format:     extOf(f.Name),
				estBitRate: q.BitRateKbps,
				matchScore: score,
			})
		}
	}
	return matches, nil
}

// searchDrive looks for mf in each configured Google Drive folder
// (Upgrade.DriveFolders), fuzzy-matching filenames against "artist - title".
// An empty DriveFolders list silently skips this source even when it's
// listed in Upgrade.Sources (see conf/configuration.go and the deviation note
// in this package's design handoff).
func (u *upgrader) searchDrive(ctx context.Context, mf model.MediaFile) ([]upgradeMatch, error) {
	folders := conf.Server.Upgrade.DriveFolders
	if len(folders) == 0 {
		return nil, nil
	}
	original := originalQuality(mf)
	var matches []upgradeMatch
	for _, folder := range folders {
		if ctx.Err() != nil {
			return matches, ctx.Err()
		}
		files, err := u.imp.ListDrive(ctx, folder)
		if err != nil {
			log.Warn(ctx, "Upgrader: could not list Google Drive folder, skipping", "folder", folder, err)
			continue
		}
		for _, f := range files {
			if f.Name == "" {
				continue // single-file link with an unknown name: nothing to match against
			}
			score := matchScore(mf.Artist, mf.Title, f.Name, false, 0)
			if score < conf.Server.Upgrade.MinMatchScore {
				continue
			}
			q := estimateQuality(f.Name, "")
			if !candidateWins(original, q) {
				continue
			}
			matches = append(matches, upgradeMatch{
				source:     UpgradeSourceDrive,
				sourceRef:  f.ID,
				title:      f.Name,
				format:     extOf(f.Name),
				matchScore: score,
			})
		}
	}
	return matches, nil
}

// searchRSS looks for mf in each configured RSS/podcast feed
// (Upgrade.RSSFeeds), matching item titles. An empty RSSFeeds list silently
// skips this source even when listed in Upgrade.Sources.
func (u *upgrader) searchRSS(ctx context.Context, mf model.MediaFile) ([]upgradeMatch, error) {
	feeds := conf.Server.Upgrade.RSSFeeds
	if len(feeds) == 0 {
		return nil, nil
	}
	original := originalQuality(mf)
	var matches []upgradeMatch
	for _, feed := range feeds {
		if ctx.Err() != nil {
			return matches, ctx.Err()
		}
		items, err := u.imp.ParseFeed(ctx, feed)
		if err != nil {
			log.Warn(ctx, "Upgrader: could not parse RSS feed, skipping", "feed", feed, err)
			continue
		}
		for _, it := range items {
			score := matchScore(mf.Artist, mf.Title, it.Title, false, 0)
			if score < conf.Server.Upgrade.MinMatchScore {
				continue
			}
			q := estimateQuality(it.URL, "")
			if !candidateWins(original, q) {
				continue
			}
			matches = append(matches, upgradeMatch{
				source:     UpgradeSourceRSS,
				sourceRef:  it.URL,
				title:      it.Title,
				format:     extOf(it.URL),
				matchScore: score,
			})
		}
	}
	return matches, nil
}

// queueCandidate inserts m as a pending model.UpgradeCandidate for mf, unless
// one already exists for the same (media_file_id, source, source_ref) —
// which also keeps previously rejected candidates from reappearing, per the
// design doc. Returns whether a new row was actually inserted.
func (u *upgrader) queueCandidate(ctx context.Context, mf model.MediaFile, m upgradeMatch) (bool, error) {
	exists, err := u.ds.UpgradeCandidate(ctx).Exists(mf.ID, m.source, m.sourceRef)
	if err != nil {
		return false, err
	}
	if exists {
		return false, nil
	}
	c := &model.UpgradeCandidate{
		MediaFileID: mf.ID,
		LibraryID:   mf.LibraryID,
		Source:      m.source,
		SourceRef:   m.sourceRef,
		Title:       m.title,
		Format:      m.format,
		EstBitRate:  m.estBitRate,
		EstSize:     m.estSize,
		MatchScore:  m.matchScore,
		Status:      model.UpgradeCandidateStatusPending,
	}
	if err := u.ds.UpgradeCandidate(ctx).Put(c); err != nil {
		return false, err
	}
	log.Debug(ctx, "Upgrader: queued candidate", "mediaFileId", mf.ID, "source", m.source, "ref", m.sourceRef, "score", m.matchScore)
	return true, nil
}
