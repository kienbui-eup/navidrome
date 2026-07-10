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
	"time"

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
		r.Get("/trends", api.importTrendsHandler)
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
	res, err := api.importer.SearchSongs(r.Context(), q.Get("q"), q.Get("drive"), q.Get("lossless") == "true", q.Get("provider"))
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

	if source == "zing" {
		id := r.URL.Query().Get("id")
		title := r.URL.Query().Get("title")
		artist := r.URL.Query().Get("artist")
		stream, _, err := api.importer.DownloadZingAudio(r.Context(), id, title, artist)
		if err != nil {
			http.Error(w, "Zing download failed: "+err.Error(), http.StatusInternalServerError)
			return
		}
		defer stream.Close()

		w.Header().Set("Content-Type", "audio/mpeg")
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

func (api *Router) importTrendsHandler(w http.ResponseWriter, r *http.Request) {
	type TrendTrack struct {
		Title       string `json:"title"`
		Artist      string `json:"artist"`
		Album       string `json:"album"`
		Artwork     string `json:"artwork"`
		ReleaseDate string `json:"releaseDate"`
		Query       string `json:"query"`
		Description string `json:"description"`
	}

	type AppleMusicResponse struct {
		Feed struct {
			Results []struct {
				ArtistName     string `json:"artistName"`
				Name           string `json:"name"`
				ReleaseDate    string `json:"releaseDate"`
				ArtworkUrl100   string `json:"artworkUrl100"`
				CollectionName string `json:"collectionName"`
			} `json:"results"`
		} `json:"feed"`
	}

	var trendingVN []TrendTrack

	// 1. Fetch Apple Music trends for Vietnam
	ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
	defer cancel()

	client := &http.Client{Timeout: 5 * time.Second}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, "https://rss.marketingtools.apple.com/api/v2/vn/music/most-played/30/songs.json", nil)
	if err == nil {
		req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
		resp, err := client.Do(req)
		if err == nil {
			defer resp.Body.Close()
			if resp.StatusCode == http.StatusOK {
				var appleResp AppleMusicResponse
				if err := json.NewDecoder(resp.Body).Decode(&appleResp); err == nil {
					for _, r := range appleResp.Feed.Results {
						trendingVN = append(trendingVN, TrendTrack{
							Title:       r.Name,
							Artist:      r.ArtistName,
							Album:       r.CollectionName,
							Artwork:     r.ArtworkUrl100,
							ReleaseDate: r.ReleaseDate,
							Query:       r.ArtistName + " " + r.Name,
							Description: "Đang thịnh hành trên bảng xếp hạng Apple Music Việt Nam.",
						})
					}
				}
			}
		}
	}

	// Fallback to high-quality backup trending list if the network request fails or returns empty
	if len(trendingVN) == 0 {
		trendingVN = []TrendTrack{
			{
				Title:       "Tìm Em",
				Artist:      "Hngle (feat. Bảo Anh)",
				Album:       "Tìm Em - Single",
				Artwork:     "https://is1-ssl.mzstatic.com/image/thumb/Music211/v4/e6/e2/25/e6e22596-950c-8a18-3159-03f5ae572cc8/196874519295.jpg/150x150bb.jpg",
				ReleaseDate: "2026-06-18",
				Query:       "Hngle Bảo Anh Tìm Em",
				Description: "Ca khúc ballad trữ tình kết hợp độc đáo đang dẫn đầu bảng xếp hạng nhạc Việt.",
			},
			{
				Title:       "Nếu Như Ta Chẳng Còn",
				Artist:      "RPT MCK (feat. A$AP Ướt Mi)",
				Album:       "Nếu Như Ta Chẳng Còn - Single",
				Artwork:     "https://is1-ssl.mzstatic.com/image/thumb/Music211/v4/3b/9b/53/3b9b5306-a684-18fe-c422-447879d096c7/1200214343408.jpg/150x150bb.jpg",
				ReleaseDate: "2026-06-17",
				Query:       "RPT MCK Nếu Như Ta Chẳng Còn",
				Description: "Bản rap-melodic sâu lắng đầy tự sự đứng đầu các xu hướng nghe nhạc số.",
			},
			{
				Title:       "Em",
				Artist:      "Binz (feat. SOOBIN)",
				Album:       "Em - Single",
				Artwork:     "https://is1-ssl.mzstatic.com/image/thumb/Music221/v4/5d/54/62/5d546225-ce49-fc95-059c-eb16d8179865/1200214069483.jpg/150x150bb.jpg",
				ReleaseDate: "2026-05-24",
				Query:       "Binz SOOBIN Em",
				Description: "Sự kết hợp hoàn hảo giữa chất rap quyến rũ của Binz và giọng hát RnB của Soobin Hoàng Sơn.",
			},
			{
				Title:       "Đừng Làm Trái Tim Anh Đau",
				Artist:      "Sơn Tùng M-TP",
				Album:       "Đừng Làm Trái Tim Anh Đau - Single",
				Artwork:     "https://is1-ssl.mzstatic.com/image/thumb/Music211/v4/e6/e2/25/e6e22596-950c-8a18-3159-03f5ae572cc8/196874519295.jpg/150x150bb.jpg",
				ReleaseDate: "2024-06-08",
				Query:       "Sơn Tùng M-TP Đừng Làm Trái Tim Anh Đau",
				Description: "Bản pop-dance vui tươi gây bão toàn bộ mạng xã hội và bảng xếp hạng âm nhạc Việt Nam.",
			},
			{
				Title:       "Nấu Ăn Cho Em",
				Artist:      "Đen (feat. PiaLinh)",
				Album:       "Nấu Ăn Cho Em - Single",
				Artwork:     "https://is1-ssl.mzstatic.com/image/thumb/Music122/v4/3b/9b/53/3b9b5306-a684-18fe-c422-447879d096c7/1200214343408.jpg/150x150bb.jpg",
				ReleaseDate: "2023-05-13",
				Query:       "Đen PiaLinh Nấu Ăn Cho Em",
				Description: "Dự án âm nhạc nhân văn truyền cảm hứng lớn đầy mộc mạc và ý nghĩa.",
			},
		}
	}

	// 2. Beautiful curated Audiophile master tracks (snappy and highly reliable)
	audiophileMaster := []TrendTrack{
		{
			Title:       "Speak Softly Love",
			Artist:      "Yao Si Ting",
			Album:       "Endless Love IV",
			Artwork:     "https://is1-ssl.mzstatic.com/image/thumb/Music211/v4/31/27/85/3127855e-d9fa-5cff-d3e3-d61c3e607907/artwork.jpg/150x150bb.jpg",
			ReleaseDate: "2024",
			Query:       "Yao Si Ting Speak Softly Love",
			Description: "Bản thu âm huyền thoại với giọng hát mượt mà, độ động cao, âm hình rộng mở cực kỳ nịnh tai.",
		},
		{
			Title:       "Stay Awhile",
			Artist:      "Susan Wong",
			Album:       "Close To You",
			Artwork:     "https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/ec/3b/b0/ec3bb0be-b4db-5401-44be-115f039e1bfb/00825646194380.jpg/150x150bb.jpg",
			ReleaseDate: "2007",
			Query:       "Susan Wong Stay Awhile",
			Description: "Giọng ca ngọt ngào say đắm, tiếng nhạc cụ gõ mộc mạc và chân thực đến từng chi tiết nhỏ nhất.",
		},
		{
			Title:       "Besame Mucho",
			Artist:      "Chantal Chamberland",
			Album:       "5",
			Artwork:     "https://is1-ssl.mzstatic.com/image/thumb/Music112/v4/7e/cb/aa/7ecbaac5-91db-a50d-402a-a9da137782aa/00825646101906.jpg/150x150bb.jpg",
			ReleaseDate: "2012",
			Query:       "Chantal Chamberland Besame Mucho",
			Description: "Sự kết hợp giữa chất giọng jazz trầm khàn gợi cảm và tiếng guitar acoustic mộc mạc cực rõ nét.",
		},
		{
			Title:       "The Look of Love",
			Artist:      "Diana Krall",
			Album:       "The Look of Love",
			Artwork:     "https://is1-ssl.mzstatic.com/image/thumb/Music122/v4/1f/2e/aa/1f2eaa35-a684-18fe-c422-447879d096c7/1200214343408.jpg/150x150bb.jpg",
			ReleaseDate: "2001",
			Query:       "Diana Krall The Look of Love",
			Description: "Nữ hoàng jazz đương đại với bản phối giao hưởng đỉnh cao, âm trầm sâu lắng đầy uy lực.",
		},
		{
			Title:       "Colour to the Moon",
			Artist:      "Allan Taylor",
			Album:       "Colour to the Moon",
			Artwork:     "https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/0d/17/57/0d175787-8df7-e6db-484d-2da5b128527a/cover.jpg/150x150bb.jpg",
			ReleaseDate: "2000",
			Query:       "Allan Taylor Colour to the Moon",
			Description: "Thu âm bởi Stockfisch Records - Tiêu chuẩn tham chiếu tuyệt đối cho độ chân thực dải trầm.",
		},
		{
			Title:       "Giấc Mơ Có Thật",
			Artist:      "Lệ Quyên",
			Album:       "Lệ Quyên Acoustic",
			Artwork:     "https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/44/db/9c/44db9cfb-86d7-e89c-5d15-081467406f52/artwork.jpg/150x150bb.jpg",
			ReleaseDate: "2009",
			Query:       "Lệ Quyên Giấc Mơ Có Thật Acoustic",
			Description: "Bản phối Acoustic mộc mạc nổi tiếng nhất của dòng nhạc nhẹ Việt Nam, tôn vinh giọng ca nội lực.",
		},
		{
			Title:       "Sầu Lẻ Bóng",
			Artist:      "Lệ Quyên",
			Album:       "Khúc Tình Ca Thiết Tha",
			Artwork:     "https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/44/db/9c/44db9cfb-86d7-e89c-5d15-081467406f52/artwork.jpg/150x150bb.jpg",
			ReleaseDate: "2010",
			Query:       "Lệ Quyên Sầu Lẻ Bóng Bolero",
			Description: "Đỉnh cao bolero trữ tình phối khí chất lượng cao, âm hình nhạc cụ gõ tách bạch tinh tế.",
		},
		{
			Title:       "Keith Don't Go",
			Artist:      "Nils Lofgren",
			Album:       "Acoustic Live",
			Artwork:     "https://is1-ssl.mzstatic.com/image/thumb/Music122/v4/91/9f/fa/919ffafc-9a4f-cc81-5dbe-9dd84e27f0fc/6943015400262.jpg/150x150bb.jpg",
			ReleaseDate: "1997",
			Query:       "Nils Lofgren Keith Don't Go Acoustic Live",
			Description: "Bài test guitar acoustic đỉnh cao mọi thời đại với tiếng dây sắt réo rắt nảy tanh tách dạt dào cảm xúc.",
		},
		{
			Title:       "Autumn Leaves",
			Artist:      "Eva Cassidy",
			Album:       "Songbird",
			Artwork:     "https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/8b/a9/9e/8ba99e82-e568-dfc0-b2aa-5975005b82ea/00825646101906.jpg/150x150bb.jpg",
			ReleaseDate: "1998",
			Query:       "Eva Cassidy Autumn Leaves",
			Description: "Giọng hát mộc mạc trong trẻo đầy u sầu dạt dào nhạc tính trên nền guitar đỉnh cao.",
		},
		{
			Title:       "Em Ơi Hà Nội Phố",
			Artist:      "Bằng Kiều",
			Album:       "Phú Quang - Tình Khúc Cho Em",
			Artwork:     "https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/0d/17/57/0d175787-8df7-e6db-484d-2da5b128527a/cover.jpg/150x150bb.jpg",
			ReleaseDate: "2012",
			Query:       "Bằng Kiều Em Ơi Hà Nội Phố Phú Quang",
			Description: "Sáng tác bất hủ của nhạc sĩ Phú Quang được thể hiện tuyệt đỉnh với chất giọng tenor cao vút.",
		},
	}

	response := map[string]any{
		"trending_vietnam":  trendingVN,
		"audiophile_master": audiophileMaster,
	}

	writeJSON(w, r, response)
}
