package core

import (
	"context"
	"crypto/md5" //nolint:gosec // Subsonic API token scheme
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/vi2play/vi2play/conf"
	"github.com/vi2play/vi2play/model"
	"github.com/vi2play/vi2play/tests"
)

func newRemoteTestImporter(t *testing.T) (*importer, context.Context) {
	t.Helper()
	conf.Server.DataFolder = conf.NewDir(t.TempDir())
	lib := &tests.MockLibraryRepo{}
	lib.SetData(model.Libraries{{ID: 1, Path: t.TempDir()}})
	ds := &tests.MockDataStore{MockedLibrary: lib}
	imp := NewImporter(ds, tests.NewMockScanner()).(*importer)
	return imp, context.Background()
}

// fakeSubsonic starts a minimal Subsonic server that enforces token auth
// (t=md5(pass+salt)) on every request.
func fakeSubsonic(t *testing.T, user, pass string) *httptest.Server {
	t.Helper()
	ok := func(payload string) string {
		body := `{"status":"ok"`
		if payload != "" {
			body += "," + payload
		}
		return `{"subsonic-response":` + body + `}}`
	}
	fail := `{"subsonic-response":{"status":"failed","error":{"code":40,"message":"Wrong username or password"}}}`

	mux := http.NewServeMux()
	authed := func(next func(w http.ResponseWriter, r *http.Request)) http.HandlerFunc {
		return func(w http.ResponseWriter, r *http.Request) {
			q := r.URL.Query()
			sum := md5.Sum([]byte(pass + q.Get("s"))) //nolint:gosec
			if q.Get("u") != user || q.Get("s") == "" || q.Get("t") != hex.EncodeToString(sum[:]) {
				w.Header().Set("Content-Type", "application/json")
				_, _ = w.Write([]byte(fail))
				return
			}
			next(w, r)
		}
	}
	writeJSON := func(w http.ResponseWriter, s string) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(s))
	}

	mux.HandleFunc("/rest/ping", authed(func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, ok(""))
	}))
	mux.HandleFunc("/rest/search3", authed(func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, ok(`"searchResult3":{
			"song":[{"id":"s1","title":"Song One","artist":"Artist A","album":"Album One","suffix":"flac","size":1000,"bitRate":900,"duration":200}],
			"album":[{"id":"al1","name":"Album One","artist":"Artist A","songCount":2,"year":2020}],
			"artist":[{"id":"ar1","name":"Artist A","albumCount":2}]}`))
	}))
	mux.HandleFunc("/rest/getArtist", authed(func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, ok(`"artist":{"id":"ar1","name":"Artist A","album":[
			{"id":"al1","name":"Album One","artist":"Artist A","songCount":2,"year":2020},
			{"id":"al2","name":"Album Two","artist":"Artist A","songCount":1,"year":2021}]}`))
	}))
	mux.HandleFunc("/rest/getAlbum", authed(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Query().Get("id") {
		case "al1":
			writeJSON(w, ok(`"album":{"id":"al1","name":"Album One","song":[
				{"id":"s1","title":"Song One","artist":"Artist A","suffix":"flac"},
				{"id":"s2","title":"Song Two","artist":"Artist A","suffix":"mp3"}]}`))
		case "al2":
			writeJSON(w, ok(`"album":{"id":"al2","name":"Album Two","song":[
				{"id":"s3","title":"Song Three","artist":"Artist A","suffix":"flac"}]}`))
		default:
			writeJSON(w, ok(`"album":{"id":"x","name":"Empty"}`))
		}
	}))
	mux.HandleFunc("/rest/getSong", authed(func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, ok(`"song":{"id":"s1","title":"Song One","artist":"Artist A","suffix":"flac","size":1000}`))
	}))
	mux.HandleFunc("/rest/getRandomSongs", authed(func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, ok(`"randomSongs":{"song":[
			{"id":"s1","title":"Song One","artist":"Artist A","album":"Album One","suffix":"flac","size":1000,"bitRate":900,"duration":200}
		]}`))
	}))
	mux.HandleFunc("/rest/download", authed(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "audio/flac")
		_, _ = fmt.Fprintf(w, "AUDIO-%s", r.URL.Query().Get("id"))
	}))
	mux.HandleFunc("/rest/stream", authed(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "audio/flac")
		_, _ = fmt.Fprintf(w, "STREAM-%s", r.URL.Query().Get("id"))
	}))

	srv := httptest.NewServer(mux)
	t.Cleanup(srv.Close)
	return srv
}

func saveTestServer(t *testing.T, imp *importer, ctx context.Context, url string) RemoteServer {
	t.Helper()
	saved, err := imp.SaveRemoteServer(ctx, RemoteServer{
		Name: "nguon", URL: url, Username: "admin", Password: "secret",
	})
	if err != nil {
		t.Fatalf("SaveRemoteServer: %v", err)
	}
	return *saved
}

func TestRemoteServerCRUD(t *testing.T) {
	imp, ctx := newRemoteTestImporter(t)

	// Create requires URL, username and password.
	if _, err := imp.SaveRemoteServer(ctx, RemoteServer{URL: "http://x", Username: "u"}); err == nil {
		t.Fatal("create without password should fail")
	}
	if _, err := imp.SaveRemoteServer(ctx, RemoteServer{URL: "http://x", Password: "p"}); err == nil {
		t.Fatal("create without username should fail")
	}

	s := saveTestServer(t, imp, ctx, "http://192.168.1.10:4533/")
	if s.ID == "" {
		t.Fatal("expected an ID to be assigned")
	}
	if s.URL != "http://192.168.1.10:4533" {
		t.Fatalf("URL not normalized: %q", s.URL)
	}

	// Update without password keeps the stored one.
	upd, err := imp.SaveRemoteServer(ctx, RemoteServer{ID: s.ID, Name: "renamed", URL: s.URL, Username: "admin"})
	if err != nil {
		t.Fatalf("update: %v", err)
	}
	if upd.Password != "secret" {
		t.Fatalf("update with empty password should keep the old one, got %q", upd.Password)
	}
	servers, _ := imp.RemoteServers(ctx)
	if len(servers) != 1 || servers[0].Name != "renamed" || servers[0].Password != "secret" {
		t.Fatalf("unexpected stored servers: %+v", servers)
	}

	// Delete.
	if err := imp.DeleteRemoteServer(ctx, s.ID); err != nil {
		t.Fatalf("delete: %v", err)
	}
	if err := imp.DeleteRemoteServer(ctx, s.ID); err == nil {
		t.Fatal("deleting a missing server should fail")
	}
	servers, _ = imp.RemoteServers(ctx)
	if len(servers) != 0 {
		t.Fatalf("expected no servers left, got %+v", servers)
	}
}

func TestValidateRemoteServerURL(t *testing.T) {
	for _, bad := range []string{"", "ftp://x", "file:///etc", "not a url", "http://"} {
		if err := validateRemoteServerURL(bad); err == nil {
			t.Errorf("validateRemoteServerURL(%q) should fail", bad)
		}
	}
	// LAN/private addresses are deliberately allowed (admin-provided).
	for _, good := range []string{"http://192.168.1.10:4533", "https://music.example.com", "http://localhost:4533"} {
		if err := validateRemoteServerURL(good); err != nil {
			t.Errorf("validateRemoteServerURL(%q) = %v, want nil", good, err)
		}
	}
}

func TestRemoteAuthAndPing(t *testing.T) {
	imp, ctx := newRemoteTestImporter(t)
	srv := fakeSubsonic(t, "admin", "secret")
	s := saveTestServer(t, imp, ctx, srv.URL)

	// Correct credentials pass.
	if err := imp.TestRemoteServer(ctx, RemoteServer{ID: s.ID}); err != nil {
		t.Fatalf("TestRemoteServer with saved credentials: %v", err)
	}
	// Wrong password fails with the Subsonic code-40 message.
	err := imp.TestRemoteServer(ctx, RemoteServer{URL: srv.URL, Username: "admin", Password: "wrong"})
	if err == nil || !strings.Contains(err.Error(), "sai tên đăng nhập hoặc mật khẩu") {
		t.Fatalf("wrong password should map to the code-40 message, got %v", err)
	}
	// Non-Subsonic endpoint fails cleanly.
	plain := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte("<html>hi</html>"))
	}))
	t.Cleanup(plain.Close)
	if err := imp.TestRemoteServer(ctx, RemoteServer{URL: plain.URL, Username: "u", Password: "p"}); err == nil {
		t.Fatal("non-Subsonic server should fail the test")
	}
}

func TestRemoteSearchAndBrowse(t *testing.T) {
	imp, ctx := newRemoteTestImporter(t)
	srv := fakeSubsonic(t, "admin", "secret")
	s := saveTestServer(t, imp, ctx, srv.URL)

	res, err := imp.RemoteSearch(ctx, s.ID, "song")
	if err != nil {
		t.Fatalf("RemoteSearch: %v", err)
	}
	if len(res.Songs) != 1 || len(res.Albums) != 1 || len(res.Artists) != 1 {
		t.Fatalf("unexpected search result: %+v", res)
	}
	if res.Songs[0].Title != "Song One" || res.Songs[0].Suffix != "flac" {
		t.Fatalf("unexpected song: %+v", res.Songs[0])
	}

	albums, err := imp.RemoteArtist(ctx, s.ID, "ar1")
	if err != nil || len(albums) != 2 {
		t.Fatalf("RemoteArtist = %+v, %v", albums, err)
	}
	songs, err := imp.RemoteAlbum(ctx, s.ID, "al1")
	if err != nil || len(songs) != 2 {
		t.Fatalf("RemoteAlbum = %+v, %v", songs, err)
	}

	resEmpty, err := imp.RemoteSearch(ctx, s.ID, "  ")
	if err != nil {
		t.Fatalf("empty query RemoteSearch failed: %v", err)
	}
	if len(resEmpty.Songs) == 0 {
		t.Fatalf("empty query RemoteSearch should auto-populate and return songs")
	}

	if _, err := imp.RemoteSearch(ctx, "missing-id", "x"); err == nil {
		t.Fatal("unknown server id should fail")
	}
}

func waitForJob(t *testing.T, imp *importer, jobID string) *ImportJob {
	t.Helper()
	deadline := time.Now().Add(10 * time.Second)
	for time.Now().Before(deadline) {
		job, ok := imp.GetImportJob(jobID)
		if !ok {
			t.Fatalf("job %s not found", jobID)
		}
		if job.Status != "running" {
			return job
		}
		time.Sleep(20 * time.Millisecond)
	}
	t.Fatalf("job %s did not finish in time", jobID)
	return nil
}

func TestStartRemoteImportExpansion(t *testing.T) {
	imp, ctx := newRemoteTestImporter(t)
	srv := fakeSubsonic(t, "admin", "secret")
	s := saveTestServer(t, imp, ctx, srv.URL)

	cases := []struct {
		kind, ref string
		count     int
	}{
		{"song", "s1", 1},
		{"album", "al1", 2},
		{"artist", "ar1", 3}, // al1 (2 songs) + al2 (1 song)
	}
	for _, c := range cases {
		// Fresh importer state per case so content dedup does not skip downloads.
		imp, ctx = newRemoteTestImporter(t)
		s = saveTestServer(t, imp, ctx, srv.URL)

		jobID, count, err := imp.StartRemoteImport(ctx, s.ID, c.kind, c.ref, 1)
		if err != nil {
			t.Fatalf("StartRemoteImport(%s): %v", c.kind, err)
		}
		if count != c.count {
			t.Fatalf("StartRemoteImport(%s) count = %d, want %d", c.kind, count, c.count)
		}
		job := waitForJob(t, imp, jobID)
		if job.Completed != c.count || job.Failed != 0 {
			t.Fatalf("job(%s) = %+v, want %d completed", c.kind, job, c.count)
		}
	}

	// The last run (artist) must have produced real files in Imported/.
	libPath, _ := imp.ds.Library(ctx).GetPath(1)
	entries, err := os.ReadDir(filepath.Join(libPath, importSubfolder))
	if err != nil {
		t.Fatalf("reading import dir: %v", err)
	}
	if len(entries) != 3 {
		t.Fatalf("expected 3 imported files, got %d", len(entries))
	}
	data, _ := os.ReadFile(filepath.Join(libPath, importSubfolder, "Artist A - Song One.flac"))
	if string(data) != "AUDIO-s1" {
		t.Fatalf("unexpected file content %q", data)
	}
	// History records the remote source.
	recs := imp.History(ctx)
	if len(recs) != 3 || recs[0].Source != "remote" {
		t.Fatalf("unexpected history: %+v", recs)
	}

	if _, _, err := imp.StartRemoteImport(ctx, s.ID, "playlist", "x", 1); err == nil {
		t.Fatal("unknown kind should fail")
	}
}

func TestRemoteSongFilename(t *testing.T) {
	cases := []struct {
		song RemoteSong
		want string
	}{
		{RemoteSong{Title: "Song", Artist: "Artist", Suffix: "flac"}, "Artist - Song.flac"},
		{RemoteSong{Title: "Song", Suffix: "mp3"}, "Song.mp3"},
		{RemoteSong{Title: "A/B: C", Artist: "X", Suffix: "flac"}, "X - A_B_ C.flac"},
		{RemoteSong{ID: "id9"}, "id9.mp3"},
	}
	for _, c := range cases {
		if got := remoteSongFilename(c.song); got != c.want {
			t.Errorf("remoteSongFilename(%+v) = %q, want %q", c.song, got, c.want)
		}
	}
}

func TestImportRemoteItemSubsonicError(t *testing.T) {
	imp, ctx := newRemoteTestImporter(t)
	// Server returns an error envelope with HTTP 200 for downloads.
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"subsonic-response":{"status":"failed","error":{"code":70,"message":"not found"}}}`))
	}))
	t.Cleanup(srv.Close)
	s := saveTestServer(t, imp, ctx, srv.URL)

	_, err := imp.importRemoteItem(ctx, ImportJobItem{Type: "remote", ServerID: s.ID, ID: "s9", Name: "x.mp3"}, 1)
	if err == nil || !strings.Contains(err.Error(), "not found") {
		t.Fatalf("expected the subsonic error message, got %v", err)
	}
}

func TestRemotePreview(t *testing.T) {
	imp, ctx := newRemoteTestImporter(t)
	srv := fakeSubsonic(t, "admin", "secret")
	s := saveTestServer(t, imp, ctx, srv.URL)

	resp, err := imp.RemotePreview(ctx, s.ID, "s1", "")
	if err != nil {
		t.Fatalf("RemotePreview: %v", err)
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		t.Fatal(err)
	}
	if string(body) != "STREAM-s1" {
		t.Fatalf("unexpected preview body %q", body)
	}
}

// Guard: the marshaled server list must round-trip passwords (they are only
// masked at the HTTP layer, never in storage).
func TestRemoteServersRoundTripPassword(t *testing.T) {
	raw, _ := json.Marshal([]RemoteServer{{ID: "1", Name: "n", URL: "http://x", Username: "u", Password: "p"}})
	var back []RemoteServer
	if err := json.Unmarshal(raw, &back); err != nil {
		t.Fatal(err)
	}
	if back[0].Password != "p" {
		t.Fatalf("password lost in round-trip: %+v", back[0])
	}
}
