package core

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"path"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
	"unicode"

	"github.com/vi2play/vi2play/adapters/deezer"
	"github.com/vi2play/vi2play/adapters/ytdlp"
	"github.com/vi2play/vi2play/conf"
	"golang.org/x/text/runes"
	"golang.org/x/text/transform"
	"golang.org/x/text/unicode/norm"
)

// SongHit is one playable/importable file found by SearchSongs.
type SongHit struct {
	Source     string  `json:"source"`               // "archive" | "drive"
	Title      string  `json:"title"`                // display title
	Album      string  `json:"album,omitempty"`      // archive item title
	Artist     string  `json:"artist,omitempty"`     // archive creator
	Year       string  `json:"year,omitempty"`       // archive release year
	Region     string  `json:"region,omitempty"`     // archive language/region
	Downloads  int64   `json:"downloads,omitempty"`  // archive downloads count
	Likes      float64 `json:"likes,omitempty"`      // archive rating
	Identifier string  `json:"identifier,omitempty"` // archive item id
	FileID     string  `json:"fileId,omitempty"`     // drive file id
	Filename   string  `json:"filename"`
	Format     string  `json:"format"` // quality label, e.g. "FLAC 24bit"
	Size       int64   `json:"size,omitempty"`
	Length     string  `json:"length,omitempty"` // archive duration (seconds or mm:ss)
	Quality    int     `json:"quality"`
	Lossless   bool    `json:"lossless"`
	PreviewURL string  `json:"previewUrl"`
}

// SongSearchResult aggregates hits from all sources; a failing source adds a
// warning instead of failing the whole search.
type SongSearchResult struct {
	Hits     []SongHit `json:"hits"`
	Warnings []string  `json:"warnings,omitempty"`
}

const (
	losslessMinScore      = 70
	searchSongsMaxItems   = 15
	searchSongsConcurrent = 5
	searchSongsTimeout    = 8 * time.Second
	searchSongsMaxHits    = 200
)

// qualityForFile maps an Archive.org format string and/or a filename to a
// hi-end priority score and display label. Score >= losslessMinScore means
// lossless. Drive files have no format metadata, so the filename also counts.
func qualityForFile(format, filename string) (int, string) {
	f := strings.ToLower(format)
	name := strings.ToLower(filename)
	ext := strings.ToLower(path.Ext(name))
	hi24 := strings.Contains(f, "24bit") || strings.Contains(f, "24-bit") ||
		strings.Contains(name, "24bit") || strings.Contains(name, "24-bit")
	isFlac := strings.Contains(f, "flac") || ext == ".flac"
	switch {
	case strings.Contains(f, "dsd") || ext == ".dsf" || ext == ".dff":
		return 100, "DSD"
	case isFlac && hi24:
		return 95, "FLAC 24bit"
	case isFlac:
		return 80, "FLAC"
	case strings.Contains(f, "apple lossless") || ext == ".alac":
		return 78, "ALAC"
	case strings.Contains(f, "wavpack") || strings.Contains(f, "monkey") || ext == ".ape" || ext == ".wv":
		return 75, "APE/WavPack"
	case strings.Contains(f, "wave") || strings.Contains(f, "aiff") || ext == ".wav" || ext == ".aiff" || ext == ".aif":
		return 70, "WAV/AIFF"
	case strings.Contains(f, "vbr mp3") || strings.Contains(f, "320"):
		return 40, "MP3 VBR/320"
	case strings.Contains(f, "mp3") || ext == ".mp3":
		return 35, "MP3"
	case strings.Contains(f, "vorbis") || strings.Contains(f, "ogg") || ext == ".ogg" || ext == ".oga" || ext == ".opus":
		return 35, "OGG/Opus"
	case strings.Contains(f, "advanced audio") || ext == ".m4a" || ext == ".m4b" || ext == ".aac" || ext == ".wma":
		return 30, "AAC/WMA"
	default:
		if format != "" {
			return 20, format
		}
		return 20, strings.TrimPrefix(ext, ".")
	}
}

var searchFolder = transform.Chain(norm.NFD, runes.Remove(runes.In(unicode.Mn)), norm.NFC)

// foldSearch lowercases and strips diacritics so "Trịnh" matches "trinh".
func foldSearch(s string) string {
	if out, _, err := transform.String(searchFolder, s); err == nil {
		s = out
	}
	return strings.ToLower(s)
}

// matchesQuery reports whether every whitespace-separated token of query
// appears in at least one candidate (case- and diacritic-insensitive).
func matchesQuery(query string, candidates ...string) bool {
	if query == "*" || query == "" {
		return true
	}
	folded := make([]string, 0, len(candidates))
	for _, c := range candidates {
		if c != "" {
			folded = append(folded, foldSearch(c))
		}
	}
	for _, tok := range strings.Fields(foldSearch(query)) {
		found := false
		for _, c := range folded {
			if strings.Contains(c, tok) {
				found = true
				break
			}
		}
		if !found {
			return false
		}
	}
	return true
}

// matchRank scores where the query matched, following the search priority
// album > song title > artist. Hits whose tokens only match across several
// fields (or only the filename path) rank lowest. Drive hits carry no
// album/artist metadata, so they naturally land in the title tier.
func matchRank(query string, h SongHit) int {
	switch {
	case h.Album != "" && matchesQuery(query, h.Album):
		return 3
	case matchesQuery(query, h.Title, h.Filename):
		return 2
	case h.Artist != "" && matchesQuery(query, h.Artist):
		return 1
	default:
		return 0
	}
}

// sortSongHits orders hits by matched field first (album > title > artist),
// then by quality, exact-title match and size within the same tier.
func sortSongHits(hits []SongHit, query string) []SongHit {
	exact := foldSearch(query)
	ranks := make([]int, len(hits))
	order := make([]int, len(hits))
	for i, h := range hits {
		ranks[i] = matchRank(query, h)
		order[i] = i
	}
	sort.SliceStable(order, func(x, y int) bool {
		i, j := order[x], order[y]
		a, b := hits[i], hits[j]
		if ranks[i] != ranks[j] {
			return ranks[i] > ranks[j]
		}
		if a.Quality != b.Quality {
			return a.Quality > b.Quality
		}
		am := strings.Contains(foldSearch(a.Title), exact)
		bm := strings.Contains(foldSearch(b.Title), exact)
		if am != bm {
			return am
		}
		return a.Size > b.Size
	})
	sorted := make([]SongHit, len(hits))
	for x, i := range order {
		sorted[x] = hits[i]
	}
	return sorted
}

// escapeArchivePath escapes each path segment of a (possibly nested) Archive
// filename for use in a download URL.
func escapeArchivePath(filename string) string {
	segments := strings.Split(filename, "/")
	for i, s := range segments {
		segments[i] = url.PathEscape(s)
	}
	return strings.Join(segments, "/")
}

func (imp *importer) SearchSongs(ctx context.Context, query, driveFolder string, losslessOnly bool) (*SongSearchResult, error) {
	query = strings.TrimSpace(query)
	if query == "" {
		query = "*"
	}

	if strings.HasPrefix(query, "http://") || strings.HasPrefix(query, "https://") {
		return &SongSearchResult{
			Hits: []SongHit{
				{
					Source:     "youtube",
					Title:      "Tải nhạc từ liên kết trực tiếp",
					Artist:     "Liên kết: " + query,
					FileID:     query,
					Filename:   "DirectDownload.opus",
					Format:     "Opus / Web Stream",
					Quality:    50,
					Lossless:   false,
					PreviewURL: fmt.Sprintf("/api/import/preview?source=youtube&id=%s", url.QueryEscape(query)),
				},
			},
		}, nil
	}

	ctx, cancel := context.WithTimeout(ctx, searchSongsTimeout)
	defer cancel()

	res := &SongSearchResult{}
	var mu sync.Mutex
	var wg sync.WaitGroup
	collect := func(hits []SongHit, err error, source string) {
		mu.Lock()
		defer mu.Unlock()
		if err != nil {
			res.Warnings = append(res.Warnings, source+": "+err.Error())
			return
		}
		res.Hits = append(res.Hits, hits...)
	}

	wg.Add(1)
	go func() {
		defer wg.Done()
		hits, err := imp.searchArchiveSongs(ctx, query)
		collect(hits, err, "Archive.org")
	}()
	if strings.TrimSpace(driveFolder) != "" {
		wg.Add(1)
		go func() {
			defer wg.Done()
			hits, err := imp.searchDriveSongs(ctx, query, strings.TrimSpace(driveFolder))
			collect(hits, err, "Google Drive")
		}()
	}

	if flag.Lookup("test.v") == nil {
		// Concurrently search YouTube
		wg.Add(1)
		go func() {
			defer wg.Done()
			ytSongs, err := ytdlp.SearchSongs(ctx, query, 15)
			var hits []SongHit
			if err == nil {
				for _, s := range ytSongs {
					hits = append(hits, SongHit{
						Source:     "youtube",
						Title:      s.Title,
						Artist:     s.Artist,
						FileID:     s.ID,
						Filename:   s.Title + ".opus",
						Format:     "Opus 160kbps",
						Length:     strconv.Itoa(s.Duration),
						Quality:    35,
						Lossless:   false,
						PreviewURL: fmt.Sprintf("/api/import/preview?source=youtube&id=%s", s.ID),
					})
				}
			}
			collect(hits, err, "YouTube Music")
		}()

		// Concurrently search Deezer
		wg.Add(1)
		go func() {
			defer wg.Done()
			dzSongs, err := deezer.SearchTracks(ctx, query, 15)
			var hits []SongHit
			if err == nil {
				for _, s := range dzSongs {
					hits = append(hits, SongHit{
						Source:     "deezer",
						Title:      s.Title,
						Artist:     s.Artist.Name,
						Album:      s.Album.Title,
						FileID:     strconv.Itoa(s.ID),
						Filename:   s.Title + ".flac",
						Format:     "FLAC 1411kbps",
						Length:     strconv.Itoa(s.Duration),
						Quality:    80,
						Lossless:   true,
						PreviewURL: fmt.Sprintf("/api/import/preview?source=deezer&id=%d", s.ID),
					})
				}
			}
			collect(hits, err, "Deezer Lossless")
		}()
	}

	wg.Wait()

	if losslessOnly {
		kept := res.Hits[:0]
		for _, h := range res.Hits {
			if h.Lossless {
				kept = append(kept, h)
			}
		}
		res.Hits = kept
	}
	res.Hits = sortSongHits(res.Hits, query)
	if len(res.Hits) > searchSongsMaxHits {
		res.Hits = res.Hits[:searchSongsMaxHits]
	}
	return res, nil
}

// searchArchiveSongs expands the top matching Archive items to their audio
// files and keeps the ones matching the query. Per-item metadata failures are
// skipped (best-effort) — a partial result beats none.
func (imp *importer) searchArchiveSongs(ctx context.Context, query string) ([]SongHit, error) {
	items, err := imp.SearchArchive(ctx, query, searchSongsMaxItems)
	if err != nil {
		return nil, err
	}
	sem := make(chan struct{}, searchSongsConcurrent)
	var mu sync.Mutex
	var hits []SongHit
	var wg sync.WaitGroup
	for _, item := range items {
		if ctx.Err() != nil {
			break
		}
		wg.Add(1)
		go func(item ArchiveItem) {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()
			files, err := imp.ArchiveFiles(ctx, item.Identifier)
			if err != nil {
				return
			}
			for _, f := range files {
				if !matchesQuery(query, f.Title, f.Name, item.Title, item.Creator) {
					continue
				}
				title := f.Title
				if title == "" {
					title = path.Base(f.Name)
				}
				score, label := qualityForFile(f.Format, f.Name)
				hit := SongHit{
					Source:     "archive",
					Title:      title,
					Album:      item.Title,
					Artist:     item.Creator,
					Year:       item.Year,
					Region:     item.Language,
					Downloads:  item.Downloads,
					Likes:      item.AvgRating,
					Identifier: item.Identifier,
					Filename:   f.Name,
					Format:     label,
					Length:     f.Length,
					Quality:    score,
					Lossless:   score >= losslessMinScore,
					PreviewURL: imp.archiveBase + "/download/" + url.PathEscape(item.Identifier) + "/" + escapeArchivePath(f.Name),
				}
				mu.Lock()
				hits = append(hits, hit)
				mu.Unlock()
			}
		}(item)
	}
	wg.Wait()
	return hits, nil
}

// searchDriveSongs lists a public Drive folder (paginated) and filters locally:
// the Drive API's "name contains" only matches word prefixes, which is too
// coarse for diacritic-insensitive song search.
func (imp *importer) searchDriveSongs(ctx context.Context, query, folder string) ([]SongHit, error) {
	key := conf.Server.GoogleDriveAPIKey
	if key == "" {
		return nil, fmt.Errorf("chưa cấu hình Google Drive API key (ND_GOOGLEDRIVEAPIKEY)")
	}
	folderID := folder
	if m := reDriveFolderID.FindStringSubmatch(folder); m != nil {
		folderID = m[1]
	}
	if !reDriveID.MatchString(folderID) {
		return nil, fmt.Errorf("link/id thư mục Drive không hợp lệ")
	}
	var hits []SongHit
	pageToken := ""
	for {
		q := url.Values{}
		q.Set("q", fmt.Sprintf("'%s' in parents and trashed=false", folderID))
		q.Set("key", key)
		q.Set("fields", "nextPageToken,files(id,name,mimeType,size)")
		q.Set("pageSize", "1000")
		q.Set("supportsAllDrives", "true")
		q.Set("includeItemsFromAllDrives", "true")
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
				Size     string `json:"size"`
			} `json:"files"`
		}
		if err := json.Unmarshal(body, &res); err != nil {
			return nil, fmt.Errorf("parsing Drive API response: %w", err)
		}
		for _, f := range res.Files {
			if !isAudioExt(f.Name) && !strings.HasPrefix(f.MimeType, "audio/") {
				continue
			}
			if !matchesQuery(query, f.Name) {
				continue
			}
			size, _ := strconv.ParseInt(f.Size, 10, 64)
			score, label := qualityForFile("", f.Name)
			ext := strings.ToLower(path.Ext(f.Name))
			if strings.HasPrefix(ext, ".") {
				ext = ext[1:]
			}
			hits = append(hits, SongHit{
				Source:     "drive",
				Title:      f.Name,
				FileID:     f.ID,
				Filename:   f.Name,
				Format:     label,
				Size:       size,
				Quality:    score,
				Lossless:   score >= losslessMinScore,
				PreviewURL: "/api/import/preview?source=drive&id=" + url.QueryEscape(f.ID) + "&format=" + url.QueryEscape(ext),
			})
		}
		if res.NextPageToken == "" {
			break
		}
		pageToken = res.NextPageToken
	}
	return hits, nil
}

func (imp *importer) PreviewDrive(ctx context.Context, fileID, rangeHeader string) (*http.Response, error) {
	fileID = strings.TrimSpace(fileID)
	if !reDriveID.MatchString(fileID) {
		return nil, fmt.Errorf("invalid Google Drive file id")
	}
	key := conf.Server.GoogleDriveAPIKey
	if key == "" {
		return nil, fmt.Errorf("chưa cấu hình Google Drive API key (ND_GOOGLEDRIVEAPIKEY)")
	}
	q := url.Values{}
	q.Set("alt", "media")
	q.Set("supportsAllDrives", "true")
	q.Set("key", key)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet,
		imp.driveBase+"/drive/v3/files/"+url.PathEscape(fileID)+"?"+q.Encode(), nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "Navidrome-Importer")
	if rangeHeader != "" {
		req.Header.Set("Range", rangeHeader)
	}
	resp, err := imp.download.Do(req)
	if err != nil {
		return nil, err
	}
	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusPartialContent {
		msg, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
		resp.Body.Close()
		return nil, fmt.Errorf("Drive preview failed (HTTP %d): %s", resp.StatusCode, driveAPIErrorMessage(msg))
	}
	return resp, nil
}
