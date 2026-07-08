package core

import (
	"encoding/json"
	"net"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestIsAudioExt(t *testing.T) {
	cases := map[string]bool{
		"song.mp3":             true,
		"song.FLAC":            true,
		"a/b/c.m4a":            true,
		"track.opus?token=abc": true, // query stripped before extension check
		"cover.jpg":            false,
		"noext":                false,
		"archive.zip":          false,
		"weird.MP3?x=1&y=2":    true,
	}
	for name, want := range cases {
		if got := isAudioExt(name); got != want {
			t.Errorf("isAudioExt(%q) = %v, want %v", name, got, want)
		}
	}
}

func TestSafeAudioFilename(t *testing.T) {
	cases := []struct{ in, want string }{
		{"song.mp3", "song.mp3"},
		{"../../etc/passwd.mp3", "passwd.mp3"},
		{"/abs/path/track.flac", "track.flac"},
		{"na:me*.mp3", "na_me_.mp3"},
		{"track.opus?token=abc", "track.opus"},
		{"noextension", "noextension.mp3"},
		{"", "import.mp3"},
		{"weird%20name.mp3", "weird name.mp3"},
		{"50%off.flac", "50%off.flac"},
	}
	for _, c := range cases {
		if got := safeAudioFilename(c.in); got != c.want {
			t.Errorf("safeAudioFilename(%q) = %q, want %q", c.in, got, c.want)
		}
	}
}

func TestSafeAudioFilenameNoTraversal(t *testing.T) {
	// The result must never contain a path separator.
	for _, in := range []string{"../../x.mp3", "a/b/c.mp3", "..\\..\\y.flac"} {
		got := safeAudioFilename(in)
		if filepath.Base(got) != got {
			t.Errorf("safeAudioFilename(%q) = %q leaked a path separator", in, got)
		}
	}
}

func TestValidatePublicURLRejects(t *testing.T) {
	bad := []string{
		"ftp://example.com/a.mp3",  // scheme
		"file:///etc/passwd",       // scheme
		"http://127.0.0.1/a.mp3",   // loopback
		"http://[::1]/a.mp3",       // loopback v6
		"http://10.0.0.1/a.mp3",    // private
		"http://192.168.1.5/a.mp3", // private
		"http://169.254.1.1/a.mp3", // link-local
		"http:///nohost",           // no host
		"not a url",                // parse/scheme
	}
	for _, u := range bad {
		if _, err := validatePublicURL(u); err == nil {
			t.Errorf("validatePublicURL(%q) should have failed", u)
		}
	}
}

func TestIsPublicIP(t *testing.T) {
	cases := map[string]bool{
		"8.8.8.8":     true,
		"1.1.1.1":     true,
		"127.0.0.1":   false,
		"10.1.2.3":    false,
		"192.168.0.1": false,
		"172.16.0.1":  false,
		"169.254.0.1": false,
		"::1":         false,
		"224.0.0.1":   false, // multicast
	}
	for ipStr, want := range cases {
		ip := net.ParseIP(ipStr)
		if ip == nil {
			t.Fatalf("bad test IP %q", ipStr)
		}
		if got := isPublicIP(ip); got != want {
			t.Errorf("isPublicIP(%q) = %v, want %v", ipStr, got, want)
		}
	}
}

func TestSafeJoin(t *testing.T) {
	dir := "/music/Imported"
	if _, err := safeJoin(dir, "song.mp3"); err != nil {
		t.Errorf("safeJoin should allow simple name: %v", err)
	}
	// A name that has already been through safeAudioFilename cannot traverse, but
	// safeJoin is a defence-in-depth check against raw names.
	if _, err := safeJoin(dir, "../../etc/passwd"); err == nil {
		t.Error("safeJoin should reject path traversal")
	}
}

func TestDriveFolderAndFileID(t *testing.T) {
	folderURLs := map[string]string{
		"https://drive.google.com/drive/folders/1iTdnBUG9N1m4yQVZsCdq52O1JM8zAnPh": "1iTdnBUG9N1m4yQVZsCdq52O1JM8zAnPh",
		"https://drive.google.com/drive/u/0/folders/ABC-123_xyz":                   "ABC-123_xyz",
		"https://drive.google.com/drive/folders/ABC?usp=sharing":                   "ABC",
	}
	for u, want := range folderURLs {
		m := reDriveFolderID.FindStringSubmatch(u)
		if m == nil || m[1] != want {
			t.Errorf("folder id from %q = %v, want %q", u, m, want)
		}
	}

	fileURLs := map[string]string{
		"https://drive.google.com/file/d/FILEID123/view?usp=sharing": "FILEID123",
		"https://drive.google.com/open?id=FILEID456":                 "FILEID456",
		"https://drive.google.com/uc?export=download&id=FILEID789":   "FILEID789",
	}
	for u, want := range fileURLs {
		m := reDriveFileID.FindStringSubmatch(u)
		if m == nil || m[1] != want {
			t.Errorf("file id from %q = %v, want %q", u, m, want)
		}
	}
}

func TestDriveEntryParsing(t *testing.T) {
	html := `
	<div class="flip-entry" id="entry-1AAAaaa_-1"><div class="flip-entry-thumb"></div>
	  <div class="flip-entry-title">Bài hát một.flac</div></div>
	<div class="flip-entry" id="entry-2BBBbbb"><div class="flip-entry-thumb"></div>
	  <div class="flip-entry-title">song &amp; two.mp3</div></div>
	<div class="flip-entry" id="entry-3CCCccc"><div class="flip-entry-thumb"></div>
	  <div class="flip-entry-title">cover.jpg</div></div>`
	matches := reDriveEntry.FindAllStringSubmatch(html, -1)
	if len(matches) != 3 {
		t.Fatalf("expected 3 raw entries, got %d", len(matches))
	}
	// Simulate the audio filter used in listDriveFolder.
	got := map[string]string{}
	for _, m := range matches {
		name := m[2]
		if isAudioExt(name) {
			got[m[1]] = name
		}
	}
	if len(got) != 2 {
		t.Errorf("expected 2 audio files after filter, got %d: %v", len(got), got)
	}
	if got["entry-1AAAaaa_-1"] == "" && got["1AAAaaa_-1"] != "Bài hát một.flac" {
		t.Errorf("first entry name wrong: %v", got)
	}
}

func TestDriveConfirmURL(t *testing.T) {
	page := `<html><body>
	<form id="download-form" action="https://drive.usercontent.google.com/download" method="get">
	  <input type="hidden" name="id" value="FILEID">
	  <input type="hidden" name="export" value="download">
	  <input type="hidden" name="confirm" value="t">
	  <input type="hidden" name="uuid" value="abc-uuid">
	</form></body></html>`
	u, ok := driveConfirmURL(page)
	if !ok {
		t.Fatal("expected to extract confirm URL")
	}
	if !strings.HasPrefix(u, "https://drive.usercontent.google.com/download?") {
		t.Errorf("unexpected action: %q", u)
	}
	for _, want := range []string{"id=FILEID", "confirm=t", "uuid=abc-uuid", "export=download"} {
		if !strings.Contains(u, want) {
			t.Errorf("confirm URL %q missing %q", u, want)
		}
	}
	if _, ok := driveConfirmURL("<html>no form here</html>"); ok {
		t.Error("should not extract a URL when there is no download form")
	}
}

func TestDriveFilename(t *testing.T) {
	if got := driveFilename("My Song.flac", "", "id"); got != "My Song.flac" {
		t.Errorf("explicit name: got %q", got)
	}
	if got := driveFilename("", `attachment; filename="From Header.mp3"`, "id"); got != "From Header.mp3" {
		t.Errorf("content-disposition name: got %q", got)
	}
	if got := driveFilename("", "", "fileid123"); got != "fileid123.mp3" {
		t.Errorf("fallback name: got %q", got)
	}
}

func TestDriveAPIErrorMessage(t *testing.T) {
	jsonErr := `{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","errors":[]}}`
	if got := driveAPIErrorMessage([]byte(jsonErr)); got != "API key not valid. Please pass a valid API key." {
		t.Errorf("json error: got %q", got)
	}
	if got := driveAPIErrorMessage([]byte("plain text error")); got != "plain text error" {
		t.Errorf("plain error: got %q", got)
	}
	long := strings.Repeat("x", 500)
	if got := driveAPIErrorMessage([]byte(long)); len(got) != 200 {
		t.Errorf("long body should be truncated to 200, got %d", len(got))
	}
}

func TestImportJobItemLabel(t *testing.T) {
	cases := []struct {
		it   ImportJobItem
		want string
	}{
		{ImportJobItem{Name: "Song.flac"}, "Song.flac"},
		{ImportJobItem{Type: "archive", Filename: "a/b.mp3"}, "a/b.mp3"},
		{ImportJobItem{Type: "url", URL: "https://x/y.mp3"}, "https://x/y.mp3"},
		{ImportJobItem{Type: "drive", ID: "abc"}, "abc"},
	}
	for _, c := range cases {
		if got := c.it.label(); got != c.want {
			t.Errorf("label(%+v) = %q, want %q", c.it, got, c.want)
		}
	}
}

func TestUniqueDest(t *testing.T) {
	dir := t.TempDir()
	p := filepath.Join(dir, "song.mp3")

	// Free path is returned unchanged.
	if got, name := uniqueDest(p); got != p || name != "song.mp3" {
		t.Fatalf("free path: got %q / %q", got, name)
	}

	// Taken -> "song (1).mp3".
	if err := os.WriteFile(p, []byte("x"), 0o600); err != nil {
		t.Fatal(err)
	}
	got, name := uniqueDest(p)
	if name != "song (1).mp3" {
		t.Errorf("first collision name = %q, want %q", name, "song (1).mp3")
	}

	// Both taken -> "song (2).mp3".
	if err := os.WriteFile(got, []byte("x"), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, name := uniqueDest(p); name != "song (2).mp3" {
		t.Errorf("second collision name = %q, want %q", name, "song (2).mp3")
	}
}

func TestIAStringUnmarshal(t *testing.T) {
	var s iaString
	if err := json.Unmarshal([]byte(`"hello"`), &s); err != nil || string(s) != "hello" {
		t.Errorf("string form: got %q err %v", s, err)
	}
	if err := json.Unmarshal([]byte(`["a","b"]`), &s); err != nil || string(s) != "a, b" {
		t.Errorf("array form: got %q err %v", s, err)
	}
	if err := json.Unmarshal([]byte(`123`), &s); err != nil || string(s) != "" {
		t.Errorf("number form should coerce to empty: got %q err %v", s, err)
	}
}
