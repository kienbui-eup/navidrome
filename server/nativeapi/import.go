package nativeapi

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"net/http"
	"os"
	"os/exec"
	"strconv"
	"strings"

	"github.com/go-chi/chi/v5"
	"github.com/vi2play/vi2play/adapters/deezer"
	"github.com/vi2play/vi2play/adapters/ytdlp"
	"github.com/vi2play/vi2play/conf"
	"github.com/vi2play/vi2play/core"
	"github.com/vi2play/vi2play/log"
)

// addImportRoute registers the (admin-only) music import endpoints. These import
// audio from legal public sources — a direct URL / podcast RSS feed, or the
// Internet Archive's open catalog — into the default library folder.
func (api *Router) addImportRoute(r chi.Router) {
	r.Route("/import", func(r chi.Router) {
		r.Post("/url", api.importURLHandler)
		r.Post("/feed", api.importFeedHandler)
		r.Post("/scan", api.importScanHandler)
		r.Get("/search/songs", api.songSearchHandler)
		r.Get("/preview", api.importPreviewHandler)
		r.Route("/archive", func(r chi.Router) {
			r.Get("/search", api.archiveSearchHandler)
			r.Get("/files", api.archiveFilesHandler)
			r.Post("/", api.archiveImportHandler)
		})
		r.Route("/drive", func(r chi.Router) {
			r.Post("/list", api.driveListHandler)
			r.Post("/file", api.driveImportHandler)
		})
		r.Route("/remote", func(r chi.Router) {
			r.Get("/servers", api.remoteServersHandler)
			r.Post("/servers", api.remoteServerSaveHandler)
			r.Put("/servers/{id}", api.remoteServerSaveHandler)
			r.Delete("/servers/{id}", api.remoteServerDeleteHandler)
			r.Post("/servers/test", api.remoteServerTestHandler)
			r.Get("/search", api.remoteSearchHandler)
			r.Get("/artist", api.remoteArtistHandler)
			r.Get("/album", api.remoteAlbumHandler)
			r.Post("/import", api.remoteImportHandler)
			r.Get("/preview", api.remotePreviewHandler)
		})
		r.Route("/job", func(r chi.Router) {
			r.Post("/", api.importJobStartHandler)
			r.Get("/{id}", api.importJobStatusHandler)
			r.Post("/{id}/cancel", api.importJobCancelHandler)
		})
		r.Get("/history", api.importHistoryHandler)
	})
}

func (api *Router) importURLHandler(w http.ResponseWriter, r *http.Request) {
	var body struct {
		URL       string `json:"url"`
		LibraryID int    `json:"libraryId"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}
	res, err := api.importer.ImportURL(r.Context(), body.URL, body.LibraryID)
	if err != nil {
		importError(w, r, "import from URL", err)
		return
	}
	writeJSON(w, r, res)
}

func (api *Router) importFeedHandler(w http.ResponseWriter, r *http.Request) {
	var body struct {
		URL string `json:"url"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}
	items, err := api.importer.ParseFeed(r.Context(), body.URL)
	if err != nil {
		importError(w, r, "parse feed", err)
		return
	}
	writeJSON(w, r, items)
}

func (api *Router) archiveSearchHandler(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query().Get("q")
	rows, _ := strconv.Atoi(r.URL.Query().Get("rows"))
	items, err := api.importer.SearchArchive(r.Context(), q, rows)
	if err != nil {
		importError(w, r, "search Internet Archive", err)
		return
	}
	writeJSON(w, r, items)
}

func (api *Router) archiveFilesHandler(w http.ResponseWriter, r *http.Request) {
	id := r.URL.Query().Get("id")
	files, err := api.importer.ArchiveFiles(r.Context(), id)
	if err != nil {
		importError(w, r, "list Internet Archive files", err)
		return
	}
	writeJSON(w, r, files)
}

func (api *Router) archiveImportHandler(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Identifier string `json:"identifier"`
		Filename   string `json:"filename"`
		LibraryID  int    `json:"libraryId"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}
	res, err := api.importer.ImportArchive(r.Context(), body.Identifier, body.Filename, body.LibraryID)
	if err != nil {
		importError(w, r, "import from Internet Archive", err)
		return
	}
	writeJSON(w, r, res)
}

func (api *Router) driveListHandler(w http.ResponseWriter, r *http.Request) {
	var body struct {
		URL string `json:"url"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}
	files, err := api.importer.ListDrive(r.Context(), body.URL)
	if err != nil {
		importError(w, r, "list Google Drive folder", err)
		return
	}
	writeJSON(w, r, files)
}

func (api *Router) driveImportHandler(w http.ResponseWriter, r *http.Request) {
	var body struct {
		ID        string `json:"id"`
		Name      string `json:"name"`
		LibraryID int    `json:"libraryId"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}
	res, err := api.importer.ImportDriveFile(r.Context(), body.ID, body.Name, body.LibraryID)
	if err != nil {
		importError(w, r, "import from Google Drive", err)
		return
	}
	writeJSON(w, r, res)
}

// songSearchHandler searches Archive.org (and optionally a public Drive folder)
// at the song level, hi-end formats first.
func (api *Router) songSearchHandler(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	res, err := api.importer.SearchSongs(r.Context(), q.Get("q"), q.Get("drive"), q.Get("lossless") == "true")
	if err != nil {
		importError(w, r, "search songs", err)
		return
	}
	writeJSON(w, r, res)
}

// importPreviewHandler proxies a Drive file for in-browser preview. Drive needs
// the server-side API key (IP-restricted), so the browser cannot stream it
// directly; Archive.org previews use their public URLs and skip this proxy.
func (api *Router) importPreviewHandler(w http.ResponseWriter, r *http.Request) {
	source := r.URL.Query().Get("source")
	if source == "youtube" {
		id := r.URL.Query().Get("id")
		stream, _, err := ytdlp.DownloadAudio(r.Context(), id)
		if err != nil {
			http.Error(w, "YouTube download failed: "+err.Error(), http.StatusInternalServerError)
			return
		}
		defer stream.Close()

		w.Header().Set("Content-Type", "audio/ogg")
		w.WriteHeader(http.StatusOK)
		_, _ = io.Copy(w, stream)
		return
	}

	if source == "deezer" {
		id := r.URL.Query().Get("id")
		arl := conf.Server.Deezer.ARL
		// Download with preferFLAC = false for fast previewing (128kbps)
		stream, _, _, err := deezer.DownloadDecryptedTrack(r.Context(), id, arl, false)
		if err != nil {
			http.Error(w, "Deezer download failed: "+err.Error(), http.StatusInternalServerError)
			return
		}
		defer stream.(io.Closer).Close()

		w.Header().Set("Content-Type", "audio/mpeg")
		w.WriteHeader(http.StatusOK)
		_, _ = io.Copy(w, stream)
		return
	}

	if source != "drive" {
		http.Error(w, "unsupported preview source", http.StatusBadRequest)
		return
	}
	format := strings.ToLower(r.URL.Query().Get("format"))
	isTranscoded := format == "dsf" || format == "dff" || format == "dsd" || format == "ape" || format == "wv" || format == "wma"

	// If transcoding is needed, we don't request a Range because ffmpeg needs to decode from the beginning of the stream.
	rangeHeader := r.Header.Get("Range")
	if isTranscoded {
		rangeHeader = ""
	}

	resp, err := api.importer.PreviewDrive(r.Context(), r.URL.Query().Get("id"), rangeHeader)
	if err != nil {
		importError(w, r, "preview file", err)
		return
	}
	defer resp.Body.Close()

	if isTranscoded {
		transcodedBody, err := transcodeToMP3(r.Context(), resp.Body)
		if err != nil {
			http.Error(w, "transcoding failed: "+err.Error(), http.StatusInternalServerError)
			return
		}
		defer transcodedBody.Close()

		w.Header().Set("Content-Type", "audio/mpeg")
		w.Header().Del("Content-Length")
		w.Header().Del("Content-Range")
		w.Header().Del("Accept-Ranges")
		w.WriteHeader(http.StatusOK)
		_, _ = io.Copy(w, transcodedBody)
		return
	}

	for _, h := range []string{"Content-Type", "Content-Length", "Content-Range", "Accept-Ranges"} {
		if v := resp.Header.Get(h); v != "" {
			w.Header().Set(h, v)
		}
	}
	if w.Header().Get("Accept-Ranges") == "" {
		w.Header().Set("Accept-Ranges", "bytes")
	}
	w.WriteHeader(resp.StatusCode)
	_, _ = io.Copy(w, resp.Body)
}

// --- Import from another Navidrome/Subsonic server -------------------------

func (api *Router) remoteServersHandler(w http.ResponseWriter, r *http.Request) {
	servers, err := api.importer.RemoteServers(r.Context())
	if err != nil {
		importError(w, r, "list remote servers", err)
		return
	}
	// Passwords never leave the server.
	for i := range servers {
		servers[i].Password = ""
	}
	writeJSON(w, r, servers)
}

// remoteServerSaveHandler handles both POST /servers (create) and
// PUT /servers/{id} (update; empty password keeps the stored one).
func (api *Router) remoteServerSaveHandler(w http.ResponseWriter, r *http.Request) {
	var body core.RemoteServer
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}
	if id := chi.URLParam(r, "id"); id != "" {
		body.ID = id
	}
	saved, err := api.importer.SaveRemoteServer(r.Context(), body)
	if err != nil {
		importError(w, r, "save remote server", err)
		return
	}
	saved.Password = ""
	writeJSON(w, r, saved)
}

func (api *Router) remoteServerDeleteHandler(w http.ResponseWriter, r *http.Request) {
	if err := api.importer.DeleteRemoteServer(r.Context(), chi.URLParam(r, "id")); err != nil {
		importError(w, r, "delete remote server", err)
		return
	}
	writeJSON(w, r, map[string]string{"status": "deleted"})
}

func (api *Router) remoteServerTestHandler(w http.ResponseWriter, r *http.Request) {
	var body core.RemoteServer
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}
	if err := api.importer.TestRemoteServer(r.Context(), body); err != nil {
		importError(w, r, "test remote server", err)
		return
	}
	writeJSON(w, r, map[string]string{"status": "ok"})
}

func (api *Router) remoteSearchHandler(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	limit := 50
	if l, err := strconv.Atoi(q.Get("limit")); err == nil && l > 0 {
		limit = l
	}
	res, err := api.importer.RemoteSearch(r.Context(), q.Get("server"), q.Get("q"), limit)
	if err != nil {
		importError(w, r, "search remote server", err)
		return
	}
	writeJSON(w, r, res)
}

func (api *Router) remoteArtistHandler(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	albums, err := api.importer.RemoteArtist(r.Context(), q.Get("server"), q.Get("id"))
	if err != nil {
		importError(w, r, "list remote artist albums", err)
		return
	}
	writeJSON(w, r, albums)
}

func (api *Router) remoteAlbumHandler(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	songs, err := api.importer.RemoteAlbum(r.Context(), q.Get("server"), q.Get("id"))
	if err != nil {
		importError(w, r, "list remote album songs", err)
		return
	}
	writeJSON(w, r, songs)
}

func (api *Router) remoteImportHandler(w http.ResponseWriter, r *http.Request) {
	var body struct {
		ServerID  string `json:"serverId"`
		Type      string `json:"type"` // "song" | "album" | "artist"
		ID        string `json:"id"`
		LibraryID int    `json:"libraryId"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}
	jobID, count, err := api.importer.StartRemoteImport(r.Context(), body.ServerID, body.Type, body.ID, body.LibraryID)
	if err != nil {
		importError(w, r, "import from remote server", err)
		return
	}
	writeJSON(w, r, map[string]any{"jobId": jobID, "count": count})
}

func (api *Router) remotePreviewHandler(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	resp, err := api.importer.RemotePreview(r.Context(), q.Get("server"), q.Get("id"), q.Get("format"), r.Header.Get("Range"))
	if err != nil {
		importError(w, r, "preview remote song", err)
		return
	}
	defer resp.Body.Close()

	// If format is a live transcoded stream, some headers like Content-Length might be empty or invalid depending on subsonic server,
	// but we just pass whatever the remote server gives us.
	for _, h := range []string{"Content-Type", "Content-Length", "Content-Range", "Accept-Ranges"} {
		if v := resp.Header.Get(h); v != "" {
			w.Header().Set(h, v)
		}
	}
	if w.Header().Get("Accept-Ranges") == "" {
		w.Header().Set("Accept-Ranges", "bytes")
	}
	w.WriteHeader(resp.StatusCode)
	_, _ = io.Copy(w, resp.Body)
}

func (api *Router) importJobStartHandler(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Items     []core.ImportJobItem `json:"items"`
		LibraryID int                  `json:"libraryId"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}
	id, err := api.importer.StartImportJob(r.Context(), body.Items, body.LibraryID)
	if err != nil {
		importError(w, r, "start import job", err)
		return
	}
	writeJSON(w, r, map[string]string{"jobId": id})
}

func (api *Router) importJobStatusHandler(w http.ResponseWriter, r *http.Request) {
	job, ok := api.importer.GetImportJob(chi.URLParam(r, "id"))
	if !ok {
		http.Error(w, "job not found", http.StatusNotFound)
		return
	}
	writeJSON(w, r, job)
}

func (api *Router) importJobCancelHandler(w http.ResponseWriter, r *http.Request) {
	api.importer.CancelImportJob(chi.URLParam(r, "id"))
	writeJSON(w, r, map[string]string{"status": "canceling"})
}

func (api *Router) importHistoryHandler(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, r, api.importer.History(r.Context()))
}

func (api *Router) importScanHandler(w http.ResponseWriter, r *http.Request) {
	api.importer.TriggerScan(r.Context())
	writeJSON(w, r, map[string]string{"status": "scan_started"})
}

func writeJSON(w http.ResponseWriter, r *http.Request, v any) {
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(v); err != nil {
		log.Error(r.Context(), "Error encoding import response", err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
	}
}

func importError(w http.ResponseWriter, r *http.Request, action string, err error) {
	log.Warn(r.Context(), "Import: failed to "+action, err)
	http.Error(w, err.Error(), http.StatusBadRequest)
}

type commandReadCloser struct {
	io.ReadCloser
	cmd    *exec.Cmd
	stderr *bytes.Buffer
}

func (c *commandReadCloser) Close() error {
	err := c.ReadCloser.Close()
	if c.cmd.Process != nil {
		_ = c.cmd.Process.Kill()
	}
	_ = c.cmd.Wait()
	return err
}

func transcodeToMP3(ctx context.Context, input io.Reader) (io.ReadCloser, error) {
	cmdPath := "/opt/homebrew/bin/ffmpeg"
	if _, err := os.Stat(cmdPath); err != nil {
		cmdPath = "ffmpeg"
	}

	cmd := exec.CommandContext(ctx, cmdPath, "-i", "pipe:0", "-f", "mp3", "-ab", "320k", "-")
	cmd.Stdin = input

	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return nil, err
	}

	var stderr bytes.Buffer
	cmd.Stderr = &stderr

	if err := cmd.Start(); err != nil {
		return nil, err
	}

	return &commandReadCloser{
		ReadCloser: stdout,
		cmd:        cmd,
		stderr:     &stderr,
	}, nil
}
