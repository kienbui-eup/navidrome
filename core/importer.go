package core

import (
	"bufio"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"encoding/xml"
	"fmt"
	"html"
	"io"
	"mime"
	"net"
	"net/http"
	"net/http/cookiejar"
	"net/url"
	"os"
	"path"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
	"time"

	"github.com/vi2play/vi2play/conf"
	"github.com/vi2play/vi2play/log"
	"github.com/vi2play/vi2play/model"
	"github.com/vi2play/vi2play/model/request"
)

// Importer downloads audio from legal, public sources (a direct URL / podcast RSS
// feed the user has the right to fetch, or the Internet Archive's open catalog)
// into the default library folder, then lets the scanner pick the files up.
//
// It deliberately does NOT integrate with commercial catalogs (YouTube, Spotify,
// streaming services, etc.) whose terms forbid downloading.
type Importer interface {
	// ImportURL downloads a single audio file from a direct URL into libraryID.
	ImportURL(ctx context.Context, rawURL string, libraryID int) (*ImportResult, error)
	// ParseFeed fetches an RSS/podcast feed and returns its audio enclosures.
	ParseFeed(ctx context.Context, feedURL string) ([]FeedItem, error)
	// SearchArchive searches the Internet Archive for audio items.
	SearchArchive(ctx context.Context, query string, rows int) ([]ArchiveItem, error)
	// ArchiveFiles lists the audio files available inside an Internet Archive item.
	ArchiveFiles(ctx context.Context, identifier string) ([]ArchiveFile, error)
	// ImportArchive downloads one file from an Internet Archive item into libraryID.
	ImportArchive(ctx context.Context, identifier, filename string, libraryID int) (*ImportResult, error)
	// ListDrive lists the audio files inside a public Google Drive folder (or the
	// single file when a Drive file link is given).
	ListDrive(ctx context.Context, driveURL string) ([]DriveFile, error)
	// ImportDriveFile downloads one file from Google Drive by its file id into libraryID.
	ImportDriveFile(ctx context.Context, fileID, name string, libraryID int) (*ImportResult, error)
	// SearchSongs searches Archive.org (and optionally a public Google Drive
	// folder) at the individual-song level, ranking hi-end formats first.
	SearchSongs(ctx context.Context, query, driveFolder string, losslessOnly bool) (*SongSearchResult, error)
	// PreviewDrive streams a Drive file for in-browser preview (Range supported).
	// The caller must close the response body.
	PreviewDrive(ctx context.Context, fileID, rangeHeader string) (*http.Response, error)
	// RemoteServers lists the saved remote Subsonic/Navidrome source servers.
	RemoteServers(ctx context.Context) ([]RemoteServer, error)
	// SaveRemoteServer creates (empty ID) or updates a remote source server.
	SaveRemoteServer(ctx context.Context, s RemoteServer) (*RemoteServer, error)
	// DeleteRemoteServer removes a saved remote source server.
	DeleteRemoteServer(ctx context.Context, serverID string) error
	// TestRemoteServer pings a remote server to validate address/credentials.
	TestRemoteServer(ctx context.Context, s RemoteServer) error
	// RemoteSearch searches songs/albums/artists on a saved remote server.
	RemoteSearch(ctx context.Context, serverID, query string) (*RemoteSearchResult, error)
	// RemoteArtist lists the albums of an artist on a remote server.
	RemoteArtist(ctx context.Context, serverID, artistID string) ([]RemoteAlbum, error)
	// RemoteAlbum lists the songs of an album on a remote server.
	RemoteAlbum(ctx context.Context, serverID, albumID string) ([]RemoteSong, error)
	// StartRemoteImport expands a song/album/artist reference on a remote server
	// into a background job downloading the original files; returns the job id
	// and the number of songs queued.
	StartRemoteImport(ctx context.Context, serverID, kind, refID string, libraryID int) (string, int, error)
	// RemotePreview streams a song from a remote server for in-browser preview
	// (Range supported). The caller must close the response body.
	RemotePreview(ctx context.Context, serverID, songID, rangeHeader string) (*http.Response, error)
	// StartImportJob runs a batch import in the background and returns its job id.
	StartImportJob(ctx context.Context, items []ImportJobItem, libraryID int) (string, error)
	// GetImportJob returns a snapshot of a running/finished job.
	GetImportJob(id string) (*ImportJob, bool)
	// CancelImportJob requests cancellation of a running job.
	CancelImportJob(id string)
	// History returns recent import records, most recent first.
	History(ctx context.Context) []ImportRecord
	// TriggerScan asks the scanner to pick up newly imported files (async).
	TriggerScan(ctx context.Context)
}

type ImportResult struct {
	SavedName string `json:"savedName"`
	Bytes     int64  `json:"bytes"`
	// Duplicate is true when the same content was already imported and the new
	// download was discarded.
	Duplicate bool `json:"duplicate,omitempty"`
}

// ImportJobItem describes one file to import within a batch job.
type ImportJobItem struct {
	Type       string `json:"type"`               // "url" | "archive" | "drive" | "remote"
	URL        string `json:"url"`                // for type "url"
	Identifier string `json:"identifier"`         // for type "archive"
	Filename   string `json:"filename"`           // for type "archive"
	ID         string `json:"id"`                 // for type "drive" (file id) / "remote" (song id)
	Name       string `json:"name"`               // display label / target filename
	ServerID   string `json:"serverId,omitempty"` // for type "remote"

	// Resolved by StartRemoteImport so a running job survives the deletion of
	// its saved server entry. Nil for items posted via the generic job endpoint.
	remoteServer *RemoteServer
}

func (it ImportJobItem) label() string {
	switch {
	case it.Name != "":
		return it.Name
	case it.Filename != "":
		return it.Filename
	case it.URL != "":
		return it.URL
	default:
		return it.ID
	}
}

// ImportJob is the progress state of a background batch import.
type ImportJob struct {
	ID        string   `json:"id"`
	Status    string   `json:"status"` // "running" | "completed" | "canceled"
	Total     int      `json:"total"`
	Completed int      `json:"completed"`
	Failed    int      `json:"failed"`
	Skipped   int      `json:"skipped"` // duplicates
	Current   string   `json:"current"`
	Errors    []string `json:"errors"`
	cancel    context.CancelFunc
}

// ImportRecord is one entry in the persistent import history/audit log.
type ImportRecord struct {
	Time      string `json:"time"`   // RFC3339 UTC
	Source    string `json:"source"` // "url" | "archive" | "drive" | "remote"
	Ref       string `json:"ref"`    // url / identifier|filename / drive id / server:song id
	SavedName string `json:"savedName"`
	Bytes     int64  `json:"bytes"`
	SHA256    string `json:"sha256"`
	Library   int    `json:"library"`
	User      string `json:"user"`
	Status    string `json:"status"` // "imported" | "duplicate"
}

type FeedItem struct {
	Title string `json:"title"`
	URL   string `json:"url"`
	Type  string `json:"type"`
}

type ArchiveItem struct {
	Identifier string `json:"identifier"`
	Title      string `json:"title"`
	Creator    string `json:"creator"`
	Year       string `json:"year"`
}

type ArchiveFile struct {
	Name   string `json:"name"`
	Format string `json:"format"`
	Title  string `json:"title"`
	Length string `json:"length"`
}

type DriveFile struct {
	Name string `json:"name"`
	ID   string `json:"id"`
}

const (
	importSubfolder     = "Imported"
	archiveBaseURL      = "https://archive.org"
	maxDownloadBytes    = int64(1) << 30 // 1 GiB per file
	downloadTimeout     = 15 * time.Minute
	metadataTimeout     = 30 * time.Second
	maxFeedResponseSize = int64(10) << 20 // 10 MiB for feed/metadata JSON/XML
	maxDriveFolderSize  = int64(5) << 20  // 5 MiB of folder-listing HTML
)

// Google Drive id/URL patterns.
var (
	reDriveFolderID = regexp.MustCompile(`drive\.google\.com/(?:drive/)?(?:u/\d+/)?folders/([A-Za-z0-9_-]+)`)
	reDriveFileID   = regexp.MustCompile(`drive\.google\.com/(?:file/d/|open\?id=|uc\?(?:[^&]*&)*id=)([A-Za-z0-9_-]+)`)
	reDriveEntry    = regexp.MustCompile(`(?s)id="entry-([A-Za-z0-9_-]+)".*?flip-entry-title[^>]*>([^<]+)<`)
	reDriveID       = regexp.MustCompile(`^[A-Za-z0-9_-]+$`)
	reFormAction    = regexp.MustCompile(`(?s)id="download-form"[^>]*action="([^"]+)"`)
	reHiddenInput   = regexp.MustCompile(`(?s)<input[^>]*type="hidden"[^>]*name="([^"]+)"[^>]*value="([^"]*)"`)
)

// audioExtensions are the file extensions we accept for import.
var audioExtensions = map[string]bool{
	".mp3": true, ".flac": true, ".m4a": true, ".m4b": true, ".aac": true,
	".ogg": true, ".oga": true, ".opus": true, ".wav": true, ".wma": true,
	".alac": true, ".aiff": true, ".aif": true, ".ape": true, ".wv": true,
	".mpc": true, ".dsf": true, ".dff": true,
}

type importer struct {
	ds       model.DataStore
	scanner  model.Scanner
	download *http.Client
	api      *http.Client
	// Base URLs, overridable in tests.
	archiveBase string
	driveBase   string

	mu            sync.Mutex
	jobs          map[string]*ImportJob
	jobSeq        int
	history       []ImportRecord // most-recent-last, capped
	seenHashes    map[string]bool
	historyLoaded bool
}

const (
	historyFileName = "import_history.jsonl"
	maxHistoryKept  = 500
	maxJobsKept     = 30
	maxJobItems     = 1000
)

func NewImporter(ds model.DataStore, scanner model.Scanner) Importer {
	// A cookie jar is needed for Google Drive's large-file download confirmation
	// flow (it sets a download_warning cookie that the confirm request must echo).
	jar, _ := cookiejar.New(nil)
	return &importer{
		ds:          ds,
		scanner:     scanner,
		download:    &http.Client{Timeout: downloadTimeout, Jar: jar},
		api:         &http.Client{Timeout: metadataTimeout},
		archiveBase: archiveBaseURL,
		driveBase:   "https://www.googleapis.com",
	}
}

// ---------------------------------------------------------------------------
// Direct URL / RSS import
// ---------------------------------------------------------------------------

func (imp *importer) ImportURL(ctx context.Context, rawURL string, libraryID int) (*ImportResult, error) {
	u, err := validatePublicURL(rawURL)
	if err != nil {
		return nil, err
	}
	name := safeAudioFilename(path.Base(u.Path))
	return imp.downloadTo(ctx, u.String(), name, true, importMeta{libraryID: libraryID, source: "url", ref: rawURL})
}

func (imp *importer) ParseFeed(ctx context.Context, feedURL string) ([]FeedItem, error) {
	u, err := validatePublicURL(feedURL)
	if err != nil {
		return nil, err
	}
	body, err := imp.getBody(ctx, u.String(), maxFeedResponseSize)
	if err != nil {
		return nil, err
	}
	var feed struct {
		Channel struct {
			Items []struct {
				Title     string `xml:"title"`
				Enclosure struct {
					URL  string `xml:"url,attr"`
					Type string `xml:"type,attr"`
				} `xml:"enclosure"`
			} `xml:"item"`
		} `xml:"channel"`
	}
	if err := xml.Unmarshal(body, &feed); err != nil {
		return nil, fmt.Errorf("parsing feed: %w", err)
	}
	var items []FeedItem
	for _, it := range feed.Channel.Items {
		enc := it.Enclosure
		if enc.URL == "" {
			continue
		}
		if strings.HasPrefix(enc.Type, "audio/") || isAudioExt(enc.URL) {
			items = append(items, FeedItem{Title: it.Title, URL: enc.URL, Type: enc.Type})
		}
	}
	return items, nil
}

// ---------------------------------------------------------------------------
// Internet Archive
// ---------------------------------------------------------------------------

func (imp *importer) SearchArchive(ctx context.Context, query string, rows int) ([]ArchiveItem, error) {
	query = strings.TrimSpace(query)
	if query == "" {
		return nil, fmt.Errorf("empty search query")
	}
	if rows <= 0 || rows > 100 {
		rows = 25
	}
	q := url.Values{}
	q.Set("q", fmt.Sprintf("(%s) AND mediatype:(audio)", query))
	q.Set("rows", fmt.Sprintf("%d", rows))
	q.Set("page", "1")
	q.Set("output", "json")
	// fl[] repeated for each field we want back
	endpoint := imp.archiveBase + "/advancedsearch.php?" +
		"fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&fl%5B%5D=year&" + q.Encode()

	body, err := imp.getBody(ctx, endpoint, maxFeedResponseSize)
	if err != nil {
		return nil, err
	}
	var res struct {
		Response struct {
			Docs []struct {
				Identifier string   `json:"identifier"`
				Title      iaString `json:"title"`
				Creator    iaString `json:"creator"`
				Year       iaString `json:"year"`
			} `json:"docs"`
		} `json:"response"`
	}
	if err := json.Unmarshal(body, &res); err != nil {
		return nil, fmt.Errorf("parsing search results: %w", err)
	}
	items := make([]ArchiveItem, 0, len(res.Response.Docs))
	for _, d := range res.Response.Docs {
		if d.Identifier == "" {
			continue
		}
		items = append(items, ArchiveItem{
			Identifier: d.Identifier,
			Title:      string(d.Title),
			Creator:    string(d.Creator),
			Year:       string(d.Year),
		})
	}
	return items, nil
}

func (imp *importer) ArchiveFiles(ctx context.Context, identifier string) ([]ArchiveFile, error) {
	identifier = strings.TrimSpace(identifier)
	if identifier == "" || strings.ContainsAny(identifier, "/\\") {
		return nil, fmt.Errorf("invalid identifier")
	}
	endpoint := imp.archiveBase + "/metadata/" + url.PathEscape(identifier)
	body, err := imp.getBody(ctx, endpoint, maxFeedResponseSize)
	if err != nil {
		return nil, err
	}
	var meta struct {
		Files []struct {
			Name   string `json:"name"`
			Format string `json:"format"`
			Title  string `json:"title"`
			Length string `json:"length"`
		} `json:"files"`
	}
	if err := json.Unmarshal(body, &meta); err != nil {
		return nil, fmt.Errorf("parsing item metadata: %w", err)
	}
	var files []ArchiveFile
	for _, f := range meta.Files {
		if isAudioExt(f.Name) {
			files = append(files, ArchiveFile{Name: f.Name, Format: f.Format, Title: f.Title, Length: f.Length})
		}
	}
	return files, nil
}

func (imp *importer) ImportArchive(ctx context.Context, identifier, filename string, libraryID int) (*ImportResult, error) {
	identifier = strings.TrimSpace(identifier)
	if identifier == "" || strings.ContainsAny(identifier, "/\\") {
		return nil, fmt.Errorf("invalid identifier")
	}
	if !isAudioExt(filename) {
		return nil, fmt.Errorf("unsupported file type: %q", filename)
	}
	dlURL := imp.archiveBase + "/download/" + url.PathEscape(identifier) + "/" + escapeArchivePath(filename)
	name := safeAudioFilename(path.Base(filename))
	meta := importMeta{libraryID: libraryID, source: "archive", ref: identifier + "/" + filename}
	return imp.downloadTo(ctx, dlURL, name, false, meta)
}

func (imp *importer) TriggerScan(ctx context.Context) {
	// Detach from the request context so cancellation of the HTTP request does not
	// abort the (blocking) scan, but keep the values (e.g. logging fields).
	scanCtx := context.WithoutCancel(ctx)
	go func() {
		if _, err := imp.scanner.ScanAll(scanCtx, false); err != nil {
			log.Error(scanCtx, "Import: error triggering scan", err)
		}
	}()
}

// ---------------------------------------------------------------------------
// Background batch jobs (progress + cancel)
// ---------------------------------------------------------------------------

func (imp *importer) StartImportJob(ctx context.Context, items []ImportJobItem, libraryID int) (string, error) {
	if len(items) == 0 {
		return "", fmt.Errorf("no items to import")
	}
	if len(items) > maxJobItems {
		return "", fmt.Errorf("too many items (max %d)", maxJobItems)
	}
	// Detach from the request so cancelling the HTTP call does not kill the job;
	// keep a dedicated cancel for explicit cancellation.
	jobCtx, cancel := context.WithCancel(context.WithoutCancel(ctx))

	imp.mu.Lock()
	imp.jobSeq++
	id := fmt.Sprintf("job-%d", imp.jobSeq)
	job := &ImportJob{ID: id, Status: "running", Total: len(items), cancel: cancel}
	if imp.jobs == nil {
		imp.jobs = map[string]*ImportJob{}
	}
	imp.jobs[id] = job
	imp.pruneJobsLocked()
	imp.mu.Unlock()

	go imp.runJob(jobCtx, id, items, libraryID)
	return id, nil
}

func (imp *importer) runJob(ctx context.Context, jobID string, items []ImportJobItem, libraryID int) {
	for _, it := range items {
		if ctx.Err() != nil {
			imp.updateJob(jobID, func(j *ImportJob) { j.Status = "canceled" })
			break
		}
		imp.updateJob(jobID, func(j *ImportJob) { j.Current = it.label() })

		var res *ImportResult
		var err error
		switch it.Type {
		case "url":
			res, err = imp.ImportURL(ctx, it.URL, libraryID)
		case "archive":
			res, err = imp.ImportArchive(ctx, it.Identifier, it.Filename, libraryID)
		case "drive":
			res, err = imp.ImportDriveFile(ctx, it.ID, it.Name, libraryID)
		case "remote":
			res, err = imp.importRemoteItem(ctx, it, libraryID)
		default:
			err = fmt.Errorf("unknown item type %q", it.Type)
		}

		imp.updateJob(jobID, func(j *ImportJob) {
			switch {
			case err != nil:
				j.Failed++
				msg := fmt.Sprintf("%s: %v", it.label(), err)
				if len(j.Errors) < 100 {
					j.Errors = append(j.Errors, msg)
				}
			case res != nil && res.Duplicate:
				j.Skipped++
			default:
				j.Completed++
			}
		})
	}
	imp.updateJob(jobID, func(j *ImportJob) {
		j.Current = ""
		if j.Status == "running" {
			j.Status = "completed"
		}
	})
	imp.TriggerScan(ctx)
}

func (imp *importer) updateJob(jobID string, fn func(*ImportJob)) {
	imp.mu.Lock()
	defer imp.mu.Unlock()
	if job := imp.jobs[jobID]; job != nil {
		fn(job)
	}
}

func (imp *importer) GetImportJob(id string) (*ImportJob, bool) {
	imp.mu.Lock()
	defer imp.mu.Unlock()
	job := imp.jobs[id]
	if job == nil {
		return nil, false
	}
	// Return a copy so callers can marshal it without holding the lock.
	snapshot := *job
	snapshot.cancel = nil
	snapshot.Errors = append([]string(nil), job.Errors...)
	return &snapshot, true
}

func (imp *importer) CancelImportJob(id string) {
	imp.mu.Lock()
	job := imp.jobs[id]
	imp.mu.Unlock()
	if job != nil && job.cancel != nil {
		job.cancel()
	}
}

func (imp *importer) pruneJobsLocked() {
	if len(imp.jobs) <= maxJobsKept {
		return
	}
	for id, j := range imp.jobs {
		if len(imp.jobs) <= maxJobsKept {
			break
		}
		if j.Status != "running" {
			delete(imp.jobs, id)
		}
	}
}

// ---------------------------------------------------------------------------
// Import history / audit log (persisted to <datafolder>/import_history.jsonl)
// ---------------------------------------------------------------------------

func (imp *importer) historyPath() string {
	return filepath.Join(conf.Server.DataFolder.String(), historyFileName)
}

func (imp *importer) ensureHistoryLoaded() {
	imp.mu.Lock()
	defer imp.mu.Unlock()
	if imp.historyLoaded {
		return
	}
	imp.historyLoaded = true
	imp.seenHashes = map[string]bool{}
	f, err := os.Open(imp.historyPath())
	if err != nil {
		return // no history yet
	}
	defer f.Close()
	scanner := bufio.NewScanner(f)
	scanner.Buffer(make([]byte, 0, 64*1024), 1024*1024)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" {
			continue
		}
		var rec ImportRecord
		if json.Unmarshal([]byte(line), &rec) != nil {
			continue
		}
		imp.history = append(imp.history, rec)
		if rec.SHA256 != "" && rec.Status == "imported" {
			imp.seenHashes[rec.SHA256] = true
		}
	}
	if len(imp.history) > maxHistoryKept {
		imp.history = imp.history[len(imp.history)-maxHistoryKept:]
	}
}

func (imp *importer) recordImport(ctx context.Context, meta importMeta, savedName string, bytes int64, sum, status string) {
	user, _ := request.UserFrom(ctx)
	libraryID := meta.libraryID
	if libraryID <= 0 {
		libraryID = model.DefaultLibraryID
	}
	rec := ImportRecord{
		Time:      time.Now().UTC().Format(time.RFC3339),
		Source:    meta.source,
		Ref:       meta.ref,
		SavedName: savedName,
		Bytes:     bytes,
		SHA256:    sum,
		Library:   libraryID,
		User:      user.UserName,
		Status:    status,
	}
	imp.mu.Lock()
	defer imp.mu.Unlock()
	imp.history = append(imp.history, rec)
	if len(imp.history) > maxHistoryKept {
		imp.history = imp.history[len(imp.history)-maxHistoryKept:]
	}
	if status == "imported" && sum != "" {
		if imp.seenHashes == nil {
			imp.seenHashes = map[string]bool{}
		}
		imp.seenHashes[sum] = true
	}
	// Append to the persistent log (best-effort).
	if line, err := json.Marshal(rec); err == nil {
		if f, err := os.OpenFile(imp.historyPath(), os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o600); err == nil {
			_, _ = f.Write(append(line, '\n'))
			_ = f.Close()
		}
	}
}

func (imp *importer) History(ctx context.Context) []ImportRecord {
	imp.ensureHistoryLoaded()
	imp.mu.Lock()
	defer imp.mu.Unlock()
	// Return most-recent-first.
	out := make([]ImportRecord, 0, len(imp.history))
	for i := len(imp.history) - 1; i >= 0; i-- {
		out = append(out, imp.history[i])
	}
	return out
}

// ---------------------------------------------------------------------------
// Download core
// ---------------------------------------------------------------------------

// importMeta carries the target library and provenance for history/audit.
type importMeta struct {
	libraryID int
	source    string // "url" | "archive" | "drive" | "remote"
	ref       string
}

// downloadTo streams a remote URL into the target library's Imported/ folder.
// When validateContentType is true (arbitrary user URLs) it also checks that the
// response looks like audio; Internet Archive downloads skip that since the file
// list is already filtered by extension.
func (imp *importer) downloadTo(ctx context.Context, rawURL, name string, validateContentType bool, meta importMeta) (*ImportResult, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "Navidrome-Importer")
	resp, err := imp.download.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("download failed: HTTP %d", resp.StatusCode)
	}
	if resp.ContentLength > maxDownloadBytes {
		return nil, fmt.Errorf("file too large: %d bytes (max %d)", resp.ContentLength, maxDownloadBytes)
	}
	if validateContentType {
		ct := resp.Header.Get("Content-Type")
		if !strings.HasPrefix(ct, "audio/") && !isAudioExt(name) {
			return nil, fmt.Errorf("URL does not point to an audio file (Content-Type: %q)", ct)
		}
	}

	res, err := imp.persist(ctx, resp.Body, name, meta)
	if err != nil {
		return nil, err
	}
	log.Info(ctx, "Imported audio file", "name", res.SavedName, "bytes", res.Bytes, "duplicate", res.Duplicate, "url", rawURL)
	return res, nil
}

// persist streams r into the target library's Imported/ folder, hashing the
// content to skip already-imported duplicates and recording an audit entry.
func (imp *importer) persist(ctx context.Context, r io.Reader, name string, meta importMeta) (*ImportResult, error) {
	imp.ensureHistoryLoaded()
	destDir, err := imp.importDir(ctx, meta.libraryID)
	if err != nil {
		return nil, err
	}
	// Verify the intended name resolves inside the import dir before downloading.
	if _, err := safeJoin(destDir, name); err != nil {
		return nil, err
	}

	// Stream to a unique temp file while computing the content hash.
	out, err := os.CreateTemp(destDir, ".nd-import-*.part")
	if err != nil {
		return nil, err
	}
	tmpPath := out.Name()
	hasher := sha256.New()
	written, err := io.Copy(out, io.TeeReader(io.LimitReader(r, maxDownloadBytes+1), hasher))
	closeErr := out.Close()
	if err != nil {
		_ = os.Remove(tmpPath)
		return nil, err
	}
	if closeErr != nil {
		_ = os.Remove(tmpPath)
		return nil, closeErr
	}
	if written > maxDownloadBytes {
		_ = os.Remove(tmpPath)
		return nil, fmt.Errorf("file exceeded maximum size of %d bytes", maxDownloadBytes)
	}
	sum := hex.EncodeToString(hasher.Sum(nil))

	// Content-level dedup: if this exact content was already imported, discard.
	imp.mu.Lock()
	dup := imp.seenHashes[sum]
	imp.mu.Unlock()
	if dup {
		_ = os.Remove(tmpPath)
		imp.recordImport(ctx, meta, name, written, sum, "duplicate")
		return &ImportResult{SavedName: name, Bytes: written, Duplicate: true}, nil
	}

	finalPath, finalName := uniqueDest(filepath.Join(destDir, name))
	if err := os.Rename(tmpPath, finalPath); err != nil {
		_ = os.Remove(tmpPath)
		return nil, err
	}
	imp.recordImport(ctx, meta, finalName, written, sum, "imported")
	return &ImportResult{SavedName: finalName, Bytes: written}, nil
}

// importDir returns (creating if needed) the Imported/ folder under the given
// library's path (falling back to the default library).
func (imp *importer) importDir(ctx context.Context, libraryID int) (string, error) {
	if libraryID <= 0 {
		libraryID = model.DefaultLibraryID
	}
	base, err := imp.ds.Library(ctx).GetPath(libraryID)
	if err != nil {
		return "", fmt.Errorf("resolving library path: %w", err)
	}
	if base == "" {
		return "", fmt.Errorf("music library path is not configured")
	}
	dir := filepath.Join(base, importSubfolder)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return "", fmt.Errorf("creating import folder: %w", err)
	}
	return dir, nil
}

func (imp *importer) getBody(ctx context.Context, rawURL string, maxBytes int64) ([]byte, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "Navidrome-Importer")
	resp, err := imp.api.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("request failed: HTTP %d", resp.StatusCode)
	}
	return io.ReadAll(io.LimitReader(resp.Body, maxBytes))
}

// ---------------------------------------------------------------------------
// Google Drive (public folders/files, no API key)
// ---------------------------------------------------------------------------

// ListDrive returns the audio files in a public Google Drive folder. If driveURL
// points at a single file instead, it returns that one file (name resolved at
// download time from the response headers).
func (imp *importer) ListDrive(ctx context.Context, driveURL string) ([]DriveFile, error) {
	if m := reDriveFolderID.FindStringSubmatch(driveURL); m != nil {
		return imp.listDriveFolder(ctx, m[1])
	}
	if m := reDriveFileID.FindStringSubmatch(driveURL); m != nil {
		// Single file: name is unknown from the URL; it is filled in on download.
		return []DriveFile{{ID: m[1], Name: ""}}, nil
	}
	return nil, fmt.Errorf("not a recognized Google Drive folder or file link")
}

func (imp *importer) listDriveFolder(ctx context.Context, folderID string) ([]DriveFile, error) {
	if !reDriveID.MatchString(folderID) {
		return nil, fmt.Errorf("invalid Google Drive folder id")
	}
	// Prefer the official Drive API (robust, paginated) when an API key is
	// configured; otherwise fall back to scraping the public embedded view.
	if key := conf.Server.GoogleDriveAPIKey; key != "" {
		return imp.listDriveFolderAPI(ctx, folderID, key)
	}
	return imp.listDriveFolderScrape(ctx, folderID)
}

// listDriveFolderAPI lists a public folder via Drive API v3 files.list, following
// pagination so large folders are fully enumerated.
func (imp *importer) listDriveFolderAPI(ctx context.Context, folderID, apiKey string) ([]DriveFile, error) {
	var files []DriveFile
	pageToken := ""
	for {
		q := url.Values{}
		q.Set("q", fmt.Sprintf("'%s' in parents and trashed=false", folderID))
		q.Set("key", apiKey)
		q.Set("fields", "nextPageToken,files(id,name,mimeType)")
		q.Set("pageSize", "1000")
		q.Set("supportsAllDrives", "true")
		q.Set("includeItemsFromAllDrives", "true")
		q.Set("orderBy", "name")
		if pageToken != "" {
			q.Set("pageToken", pageToken)
		}
		body, err := imp.driveAPIGet(ctx, imp.driveBase+"/drive/v3/files?"+q.Encode())
		if err != nil {
			return nil, err
		}
		var res struct {
			NextPageToken string `json:"nextPageToken"`
			Files         []struct {
				ID       string `json:"id"`
				Name     string `json:"name"`
				MimeType string `json:"mimeType"`
			} `json:"files"`
		}
		if err := json.Unmarshal(body, &res); err != nil {
			return nil, fmt.Errorf("parsing Drive API response: %w", err)
		}
		for _, f := range res.Files {
			if isAudioExt(f.Name) || strings.HasPrefix(f.MimeType, "audio/") {
				files = append(files, DriveFile{ID: f.ID, Name: f.Name})
			}
		}
		if res.NextPageToken == "" {
			break
		}
		pageToken = res.NextPageToken
	}
	if len(files) == 0 {
		return nil, fmt.Errorf("no audio files found (check the folder is shared publicly and the API key is valid)")
	}
	return files, nil
}

func (imp *importer) listDriveFolderScrape(ctx context.Context, folderID string) ([]DriveFile, error) {
	// The embedded folder view lists a public folder's entries as HTML without
	// needing an API key.
	viewURL := "https://drive.google.com/embeddedfolderview?id=" + url.QueryEscape(folderID) + "#list"
	body, err := imp.getBody(ctx, viewURL, maxDriveFolderSize)
	if err != nil {
		return nil, fmt.Errorf("listing Drive folder: %w", err)
	}
	seen := map[string]bool{}
	var files []DriveFile
	for _, m := range reDriveEntry.FindAllStringSubmatch(string(body), -1) {
		id, name := m[1], strings.TrimSpace(html.UnescapeString(m[2]))
		if seen[id] || !isAudioExt(name) {
			continue
		}
		seen[id] = true
		files = append(files, DriveFile{ID: id, Name: name})
	}
	if len(files) == 0 {
		return nil, fmt.Errorf("no audio files found (folder may be private, empty, or too large to list)")
	}
	return files, nil
}

func (imp *importer) ImportDriveFile(ctx context.Context, fileID, name string, libraryID int) (*ImportResult, error) {
	fileID = strings.TrimSpace(fileID)
	if !reDriveID.MatchString(fileID) {
		return nil, fmt.Errorf("invalid Google Drive file id")
	}
	var resp *http.Response
	var err error
	if key := conf.Server.GoogleDriveAPIKey; key != "" {
		// Drive API v3 media download: clean, handles large files, no interstitial.
		q := url.Values{}
		q.Set("alt", "media")
		q.Set("supportsAllDrives", "true")
		q.Set("key", key)
		dlURL := imp.driveBase + "/drive/v3/files/" + url.PathEscape(fileID) + "?" + q.Encode()
		resp, err = imp.driveGet(ctx, dlURL)
		if err != nil {
			return nil, err
		}
		defer resp.Body.Close()
		if resp.StatusCode != http.StatusOK {
			msg, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
			return nil, fmt.Errorf("Drive API download failed (HTTP %d): %s", resp.StatusCode, driveAPIErrorMessage(msg))
		}
	} else {
		resp, err = imp.driveScrapeDownload(ctx, fileID)
		if err != nil {
			return nil, err
		}
		defer resp.Body.Close()
	}

	finalName := driveFilename(name, resp.Header.Get("Content-Disposition"), fileID)
	if !isAudioExt(finalName) {
		return nil, fmt.Errorf("file %q is not a supported audio type", finalName)
	}
	meta := importMeta{libraryID: libraryID, source: "drive", ref: fileID}
	res, err := imp.persist(ctx, resp.Body, finalName, meta)
	if err != nil {
		return nil, err
	}
	log.Info(ctx, "Imported audio file from Google Drive", "name", res.SavedName, "bytes", res.Bytes, "duplicate", res.Duplicate, "id", fileID)
	return res, nil
}

// driveScrapeDownload downloads a public file without an API key, handling the
// large-file confirmation interstitial. Returns a response whose Body is the
// file bytes (caller closes it).
func (imp *importer) driveScrapeDownload(ctx context.Context, fileID string) (*http.Response, error) {
	dlURL := "https://drive.google.com/uc?export=download&id=" + url.QueryEscape(fileID)
	resp, err := imp.driveGet(ctx, dlURL)
	if err != nil {
		return nil, err
	}
	if ct := resp.Header.Get("Content-Type"); strings.HasPrefix(ct, "text/html") {
		page, _ := io.ReadAll(io.LimitReader(resp.Body, maxDriveFolderSize))
		resp.Body.Close()
		confirmURL, ok := driveConfirmURL(string(page))
		if !ok {
			return nil, fmt.Errorf("could not confirm Drive download (file may be private or require sign-in)")
		}
		resp, err = imp.driveGet(ctx, confirmURL)
		if err != nil {
			return nil, err
		}
	}
	if resp.StatusCode != http.StatusOK {
		resp.Body.Close()
		return nil, fmt.Errorf("Drive download failed: HTTP %d", resp.StatusCode)
	}
	return resp, nil
}

func (imp *importer) driveGet(ctx context.Context, rawURL string) (*http.Response, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 Navidrome-Importer")
	return imp.download.Do(req)
}

// driveAPIGet performs a Drive API GET and returns the body, surfacing the API's
// JSON error message on non-200 responses.
func (imp *importer) driveAPIGet(ctx context.Context, rawURL string) ([]byte, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "Navidrome-Importer")
	resp, err := imp.api.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(io.LimitReader(resp.Body, maxFeedResponseSize))
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("Google Drive API error (HTTP %d): %s", resp.StatusCode, driveAPIErrorMessage(body))
	}
	return body, nil
}

// driveAPIErrorMessage extracts the human-readable message from a Drive API error
// JSON body, falling back to a trimmed raw body.
func driveAPIErrorMessage(body []byte) string {
	var e struct {
		Error struct {
			Message string `json:"message"`
		} `json:"error"`
	}
	if json.Unmarshal(body, &e) == nil && e.Error.Message != "" {
		return e.Error.Message
	}
	s := strings.TrimSpace(string(body))
	if len(s) > 200 {
		s = s[:200]
	}
	return s
}

// driveConfirmURL extracts the confirmed download URL from Drive's interstitial
// HTML form (action + hidden inputs).
func driveConfirmURL(page string) (string, bool) {
	am := reFormAction.FindStringSubmatch(page)
	if am == nil {
		return "", false
	}
	action := html.UnescapeString(am[1])
	q := url.Values{}
	for _, in := range reHiddenInput.FindAllStringSubmatch(page, -1) {
		q.Set(in[1], html.UnescapeString(in[2]))
	}
	if len(q) == 0 {
		return action, true
	}
	sep := "?"
	if strings.Contains(action, "?") {
		sep = "&"
	}
	return action + sep + q.Encode(), true
}

// driveFilename decides the on-disk name: prefer the folder-listing name, then
// the server's Content-Disposition filename, then a fallback based on the id.
func driveFilename(name, contentDisposition, fileID string) string {
	if name != "" {
		return safeAudioFilename(name)
	}
	if contentDisposition != "" {
		if _, params, err := mime.ParseMediaType(contentDisposition); err == nil {
			if fn := params["filename"]; fn != "" {
				return safeAudioFilename(fn)
			}
		}
	}
	return safeAudioFilename(fileID)
}

// ---------------------------------------------------------------------------
// Security / sanitisation helpers
// ---------------------------------------------------------------------------

// validatePublicURL parses rawURL, requires http/https, and rejects hosts that
// resolve to private, loopback, link-local, or otherwise non-public addresses
// (a basic SSRF guard so an admin cannot make the server probe its own network).
func validatePublicURL(rawURL string) (*url.URL, error) {
	u, err := url.Parse(strings.TrimSpace(rawURL))
	if err != nil {
		return nil, fmt.Errorf("invalid URL: %w", err)
	}
	if u.Scheme != "http" && u.Scheme != "https" {
		return nil, fmt.Errorf("only http and https URLs are allowed")
	}
	host := u.Hostname()
	if host == "" {
		return nil, fmt.Errorf("URL has no host")
	}
	ips, err := net.LookupIP(host)
	if err != nil {
		return nil, fmt.Errorf("cannot resolve host %q: %w", host, err)
	}
	for _, ip := range ips {
		if !isPublicIP(ip) {
			return nil, fmt.Errorf("host %q resolves to a non-public address", host)
		}
	}
	return u, nil
}

func isPublicIP(ip net.IP) bool {
	if ip.IsLoopback() || ip.IsUnspecified() || ip.IsPrivate() ||
		ip.IsLinkLocalUnicast() || ip.IsLinkLocalMulticast() || ip.IsMulticast() {
		return false
	}
	return true
}

func isAudioExt(name string) bool {
	ext := strings.ToLower(path.Ext(strings.Split(name, "?")[0]))
	return audioExtensions[ext]
}

// safeAudioFilename reduces an arbitrary name to a safe basename with an audio
// extension, stripping path separators and control/unsafe characters.
func safeAudioFilename(name string) string {
	// Percent-decode when it is valid; keep the original otherwise (a literal '%'
	// in a filename must not blank the whole name).
	if unescaped, err := url.QueryUnescape(name); err == nil {
		name = unescaped
	}
	name = path.Base(strings.Split(name, "?")[0])
	name = strings.Map(func(r rune) rune {
		switch {
		case r < 0x20:
			return -1
		case strings.ContainsRune(`/\:*?"<>|`, r):
			return '_'
		default:
			return r
		}
	}, name)
	name = strings.TrimSpace(strings.Trim(name, "."))
	if name == "" || !isAudioExt(name) {
		if name == "" {
			name = "import"
		}
		name += ".mp3"
	}
	return name
}

// safeJoin joins dir and name and verifies the result stays inside dir.
func safeJoin(dir, name string) (string, error) {
	p := filepath.Join(dir, name)
	cleanDir := filepath.Clean(dir) + string(os.PathSeparator)
	if !strings.HasPrefix(p, cleanDir) {
		return "", fmt.Errorf("invalid destination path")
	}
	return p, nil
}

// uniqueDest returns a destination path that does not yet exist, appending
// " (1)", " (2)"... before the extension when the original name is taken.
// Returns the chosen full path and its base name.
func uniqueDest(destPath string) (string, string) {
	if _, err := os.Stat(destPath); os.IsNotExist(err) {
		return destPath, filepath.Base(destPath)
	}
	dir := filepath.Dir(destPath)
	base := filepath.Base(destPath)
	ext := filepath.Ext(base)
	stem := strings.TrimSuffix(base, ext)
	for i := 1; i < 10000; i++ {
		candidate := fmt.Sprintf("%s (%d)%s", stem, i, ext)
		p := filepath.Join(dir, candidate)
		if _, err := os.Stat(p); os.IsNotExist(err) {
			return p, candidate
		}
	}
	return destPath, base
}

// iaString unmarshals an Internet Archive metadata field that may be either a
// JSON string or an array of strings (IA returns both forms).
type iaString string

func (s *iaString) UnmarshalJSON(b []byte) error {
	var str string
	if err := json.Unmarshal(b, &str); err == nil {
		*s = iaString(str)
		return nil
	}
	var arr []string
	if err := json.Unmarshal(b, &arr); err == nil {
		*s = iaString(strings.Join(arr, ", "))
		return nil
	}
	*s = ""
	return nil
}
