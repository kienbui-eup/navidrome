package core

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/navidrome/navidrome/conf"
)

func TestQualityForFile(t *testing.T) {
	cases := []struct {
		format, filename string
		wantScore        int
		wantLabel        string
	}{
		{"24bit Flac", "song.flac", 95, "FLAC 24bit"},
		{"Flac", "song.flac", 80, "FLAC"},
		{"", "Hotel California (24bit).flac", 95, "FLAC 24bit"},
		{"", "song.dsf", 100, "DSD"},
		{"VBR MP3", "song.mp3", 40, "MP3 VBR/320"},
		{"", "track.m4a", 30, "AAC/WMA"},
		{"Apple Lossless Audio", "track.m4a", 78, "ALAC"},
		{"", "track.ape", 75, "APE/WavPack"},
		{"WAVE", "track.wav", 70, "WAV/AIFF"},
		{"Ogg Vorbis", "track.ogg", 35, "OGG/Opus"},
		{"Columbia Peaks", "x.bin", 20, "Columbia Peaks"},
	}
	for _, c := range cases {
		score, label := qualityForFile(c.format, c.filename)
		if score != c.wantScore || label != c.wantLabel {
			t.Errorf("qualityForFile(%q, %q) = (%d, %q), want (%d, %q)",
				c.format, c.filename, score, label, c.wantScore, c.wantLabel)
		}
	}
}

func TestMatchesQuery(t *testing.T) {
	cases := []struct {
		query      string
		candidates []string
		want       bool
	}{
		{"trinh cong son", []string{"Trịnh Công Sơn - Diễm Xưa.flac"}, true},
		{"diễm xưa", []string{"Trinh Cong Son - Diem Xua.flac"}, true},
		{"hotel california", []string{"Track01.flac", "Hotel California Live 1977"}, true},
		{"hotel narnia", []string{"Hotel California Live"}, false},
		{"", []string{"anything"}, true},
		{"abba", []string{}, false},
	}
	for _, c := range cases {
		if got := matchesQuery(c.query, c.candidates...); got != c.want {
			t.Errorf("matchesQuery(%q, %v) = %v, want %v", c.query, c.candidates, got, c.want)
		}
	}
}

// newSearchTestServer fakes both the Archive.org and Drive API endpoints.
// driveStatus lets a test force the Drive source to fail.
func newSearchTestServer(t *testing.T, driveStatus int) *httptest.Server {
	t.Helper()
	mux := http.NewServeMux()
	mux.HandleFunc("/advancedsearch.php", func(w http.ResponseWriter, r *http.Request) {
		_ = json.NewEncoder(w).Encode(map[string]any{
			"response": map[string]any{
				"docs": []map[string]any{
					{"identifier": "item1", "title": "Hotel California Live", "creator": "Eagles", "year": "1977"},
				},
			},
		})
	})
	mux.HandleFunc("/metadata/item1", func(w http.ResponseWriter, r *http.Request) {
		_ = json.NewEncoder(w).Encode(map[string]any{
			"files": []map[string]any{
				{"name": "hotel-california.flac", "format": "Flac", "title": "Hotel California", "length": "391.05"},
				{"name": "hotel-california.mp3", "format": "VBR MP3", "title": "Hotel California", "length": "391.05"},
				{"name": "cover.jpg", "format": "JPEG"},
			},
		})
	})
	mux.HandleFunc("/drive/v3/files", func(w http.ResponseWriter, r *http.Request) {
		if driveStatus != http.StatusOK {
			w.WriteHeader(driveStatus)
			_ = json.NewEncoder(w).Encode(map[string]any{"error": map[string]any{"message": "boom"}})
			return
		}
		_ = json.NewEncoder(w).Encode(map[string]any{
			"files": []map[string]any{
				{"id": "d1", "name": "Hotel California (24bit).flac", "mimeType": "audio/flac", "size": "1000"},
				{"id": "d2", "name": "notes.txt", "mimeType": "text/plain", "size": "10"},
			},
		})
	})
	return httptest.NewServer(mux)
}

func newTestImporter(srv *httptest.Server) *importer {
	return &importer{
		api:         srv.Client(),
		download:    srv.Client(),
		archiveBase: srv.URL,
		driveBase:   srv.URL,
	}
}

func withDriveKey(t *testing.T, key string) {
	t.Helper()
	old := conf.Server.GoogleDriveAPIKey
	conf.Server.GoogleDriveAPIKey = key
	t.Cleanup(func() { conf.Server.GoogleDriveAPIKey = old })
}

func TestSearchSongs(t *testing.T) {
	srv := newSearchTestServer(t, http.StatusOK)
	defer srv.Close()
	withDriveKey(t, "test-key")
	imp := newTestImporter(srv)

	res, err := imp.SearchSongs(context.Background(), "hotel california", "folder123", false)
	if err != nil {
		t.Fatalf("SearchSongs: %v", err)
	}
	if len(res.Warnings) != 0 {
		t.Fatalf("unexpected warnings: %v", res.Warnings)
	}
	if len(res.Hits) != 3 {
		t.Fatalf("got %d hits, want 3: %+v", len(res.Hits), res.Hits)
	}
	// Hi-end first: drive 24bit FLAC (95) > archive FLAC (80) > archive MP3 (40).
	if res.Hits[0].Source != "drive" || res.Hits[0].Quality != 95 {
		t.Errorf("hits[0] = %+v, want drive FLAC 24bit", res.Hits[0])
	}
	if res.Hits[1].Format != "FLAC" || !res.Hits[1].Lossless {
		t.Errorf("hits[1] = %+v, want archive FLAC lossless", res.Hits[1])
	}
	if res.Hits[2].Format != "MP3 VBR/320" || res.Hits[2].Lossless {
		t.Errorf("hits[2] = %+v, want lossy MP3", res.Hits[2])
	}
	if !strings.HasPrefix(res.Hits[0].PreviewURL, "/api/import/preview?source=drive&id=d1") {
		t.Errorf("drive preview URL = %q", res.Hits[0].PreviewURL)
	}
	if want := srv.URL + "/download/item1/hotel-california.flac"; res.Hits[1].PreviewURL != want {
		t.Errorf("archive preview URL = %q, want %q", res.Hits[1].PreviewURL, want)
	}
}

func TestSearchSongsLosslessOnly(t *testing.T) {
	srv := newSearchTestServer(t, http.StatusOK)
	defer srv.Close()
	withDriveKey(t, "test-key")
	imp := newTestImporter(srv)

	res, err := imp.SearchSongs(context.Background(), "hotel california", "folder123", true)
	if err != nil {
		t.Fatalf("SearchSongs: %v", err)
	}
	if len(res.Hits) != 2 {
		t.Fatalf("got %d hits, want 2 (mp3 filtered): %+v", len(res.Hits), res.Hits)
	}
	for _, h := range res.Hits {
		if !h.Lossless {
			t.Errorf("lossy hit leaked through: %+v", h)
		}
	}
}

func TestSearchSongsDriveFailureIsWarning(t *testing.T) {
	srv := newSearchTestServer(t, http.StatusForbidden)
	defer srv.Close()
	withDriveKey(t, "test-key")
	imp := newTestImporter(srv)

	res, err := imp.SearchSongs(context.Background(), "hotel california", "folder123", false)
	if err != nil {
		t.Fatalf("SearchSongs: %v", err)
	}
	if len(res.Hits) != 2 {
		t.Fatalf("got %d archive hits, want 2", len(res.Hits))
	}
	if len(res.Warnings) != 1 || !strings.Contains(res.Warnings[0], "Google Drive") {
		t.Fatalf("warnings = %v, want one Google Drive warning", res.Warnings)
	}
}

func TestSearchSongsWithoutDrive(t *testing.T) {
	srv := newSearchTestServer(t, http.StatusOK)
	defer srv.Close()
	imp := newTestImporter(srv)

	res, err := imp.SearchSongs(context.Background(), "hotel california", "", false)
	if err != nil {
		t.Fatalf("SearchSongs: %v", err)
	}
	for _, h := range res.Hits {
		if h.Source != "archive" {
			t.Errorf("unexpected non-archive hit: %+v", h)
		}
	}
}

func TestPreviewDriveForwardsRange(t *testing.T) {
	var gotRange string
	mux := http.NewServeMux()
	mux.HandleFunc("/drive/v3/files/d1", func(w http.ResponseWriter, r *http.Request) {
		gotRange = r.Header.Get("Range")
		w.Header().Set("Content-Range", "bytes 0-99/1000")
		w.WriteHeader(http.StatusPartialContent)
		_, _ = w.Write(make([]byte, 100))
	})
	srv := httptest.NewServer(mux)
	defer srv.Close()
	withDriveKey(t, "test-key")
	imp := newTestImporter(srv)

	resp, err := imp.PreviewDrive(context.Background(), "d1", "bytes=0-99")
	if err != nil {
		t.Fatalf("PreviewDrive: %v", err)
	}
	defer resp.Body.Close()
	if gotRange != "bytes=0-99" {
		t.Errorf("upstream Range = %q, want bytes=0-99", gotRange)
	}
	if resp.StatusCode != http.StatusPartialContent {
		t.Errorf("status = %d, want 206", resp.StatusCode)
	}
}
