package nativeapi

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/vi2play/vi2play/core/agents"
	"github.com/vi2play/vi2play/db"
	"github.com/vi2play/vi2play/log"
	"github.com/vi2play/vi2play/model"
	"github.com/vi2play/vi2play/server"
)

// Structural declarations for updates
type artistUpdateRequest struct {
	Name           string `json:"name"`
	SortArtistName string `json:"sortArtistName"`
	MbzArtistID    string `json:"mbzArtistId"`
	Biography      string `json:"biography"`
}

type albumUpdateRequest struct {
	Name          string `json:"name"`
	AlbumArtist   string `json:"albumArtist"`
	MinYear       int    `json:"minYear"`
	MaxYear       int    `json:"maxYear"`
	SortAlbumName string `json:"sortAlbumName"`
	MbzAlbumID    string `json:"mbzArtistId"` // field name matched to JSON key if needed
	Description   string `json:"description"`
}

type songUpdateRequest struct {
	Title       string `json:"title"`
	TrackNumber int    `json:"trackNumber"`
	DiscNumber  int    `json:"discNumber"`
	Year        int    `json:"year"`
	Genre       string `json:"genre"`
	Lyrics      string `json:"lyrics"`
}

// Background Auto-Fix Job state
type AutoFixJob struct {
	mu        sync.RWMutex `json:"-"` // Thêm mutex để tránh race condition khi marshal JSON
	ID        string    `json:"id"`
	Type      string    `json:"type"`       // "artists", "albums", "lyrics", "all"
	Status    string    `json:"status"`     // "running", "completed", "failed"
	Total     int       `json:"total"`
	Processed int       `json:"processed"`
	Updated   int       `json:"updated"`
	Errors    []string  `json:"errors"`
	StartedAt time.Time `json:"startedAt"`
	EndedAt   time.Time `json:"endedAt"`
}

func (j *AutoFixJob) Snapshot() AutoFixJob {
	j.mu.RLock()
	defer j.mu.RUnlock()

	errs := make([]string, len(j.Errors))
	copy(errs, j.Errors)

	return AutoFixJob{
		ID:        j.ID,
		Type:      j.Type,
		Status:    j.Status,
		Total:     j.Total,
		Processed: j.Processed,
		Updated:   j.Updated,
		Errors:    errs,
		StartedAt: j.StartedAt,
		EndedAt:   j.EndedAt,
	}
}


var (
	autoFixJobs   = make(map[string]*AutoFixJob)
	autoFixJobsMu sync.RWMutex
	jobCounter    int
)

func (api *Router) addAdminMetadataRoute(r chi.Router) {
	r.Route("/metadata", func(r chi.Router) {
		r.Route("/artist/{id}", func(r chi.Router) {
			r.Use(server.URLParamsMiddleware)
			r.Put("/", api.updateArtistMetadata)
		})
		r.Route("/album/{id}", func(r chi.Router) {
			r.Use(server.URLParamsMiddleware)
			r.Put("/", api.updateAlbumMetadata)
		})
		r.Route("/song/{id}", func(r chi.Router) {
			r.Use(server.URLParamsMiddleware)
			r.Put("/", api.updateSongMetadata)
		})

		r.Get("/stats", api.getMetadataStats)
		r.Post("/autofix", api.triggerAutoFixJob)
		r.Get("/autofix/status/{jobId}", api.getAutoFixJobStatus)
	})
}

// updateArtistMetadata updates artist attributes directly in the repository/database
func (api *Router) updateArtistMetadata(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	id := chi.URLParam(r, "id")

	var req artistUpdateRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	artist, err := api.ds.Artist(ctx).Get(id)
	if err != nil {
		http.Error(w, "Artist not found: "+err.Error(), http.StatusNotFound)
		return
	}

	if req.Name != "" {
		artist.Name = req.Name
	}
	artist.SortArtistName = req.SortArtistName
	artist.MbzArtistID = req.MbzArtistID
	artist.Biography = req.Biography

	// Put update with explicit columns to ensure persistent saving
	err = api.ds.Artist(ctx).Put(artist, "name", "sort_artist_name", "mbz_artist_id", "biography", "updated_at")
	if err != nil {
		http.Error(w, "Error saving artist: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(artist)
}

// updateAlbumMetadata updates album attributes directly in the database
func (api *Router) updateAlbumMetadata(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	id := chi.URLParam(r, "id")

	var req albumUpdateRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	album, err := api.ds.Album(ctx).Get(id)
	if err != nil {
		http.Error(w, "Album not found: "+err.Error(), http.StatusNotFound)
		return
	}

	if req.Name != "" {
		album.Name = req.Name
	}
	album.AlbumArtist = req.AlbumArtist
	album.MinYear = req.MinYear
	album.MaxYear = req.MaxYear
	album.SortAlbumName = req.SortAlbumName
	album.MbzAlbumID = req.MbzAlbumID
	album.Description = req.Description

	err = api.ds.Album(ctx).Put(album)
	if err != nil {
		http.Error(w, "Error saving album: "+err.Error(), http.StatusInternalServerError)
		return
	}

	// Update external metadata fields explicitly
	_ = api.ds.Album(ctx).UpdateExternalInfo(album)

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(album)
}

// updateSongMetadata updates song attributes and lyrics inside the database
func (api *Router) updateSongMetadata(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	id := chi.URLParam(r, "id")

	var req songUpdateRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	song, err := api.ds.MediaFile(ctx).Get(id)
	if err != nil {
		http.Error(w, "Song not found: "+err.Error(), http.StatusNotFound)
		return
	}

	if req.Title != "" {
		song.Title = req.Title
	}
	song.TrackNumber = req.TrackNumber
	song.DiscNumber = req.DiscNumber
	song.Year = req.Year
	song.Genre = req.Genre

	if req.Lyrics != "" {
		// Attempt to parse text with potential timing stamps (LRC)
		lyricsObj, err := model.ToLyrics("eng", req.Lyrics)
		if err == nil && lyricsObj != nil {
			lyricList := model.LyricList{*lyricsObj}
			jsonBytes, _ := json.Marshal(lyricList)
			song.Lyrics = string(jsonBytes)
		} else {
			// Save as flat plain lyrics line if timed parsing fails
			line := model.Line{Value: req.Lyrics}
			flatObj := &model.Lyrics{Line: []model.Line{line}}
			lyricList := model.LyricList{*flatObj}
			jsonBytes, _ := json.Marshal(lyricList)
			song.Lyrics = string(jsonBytes)
		}
	} else {
		song.Lyrics = ""
	}

	err = api.ds.MediaFile(ctx).Put(song)
	if err != nil {
		http.Error(w, "Error saving song: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(song)
}

// getMetadataStats fetches data coverage and missing parameters for the dashboard
func (api *Router) getMetadataStats(w http.ResponseWriter, r *http.Request) {
	var totalArtists, missingBios int
	_ = db.Db().QueryRow("SELECT COUNT(*), SUM(CASE WHEN biography IS NULL OR biography = '' THEN 1 ELSE 0 END) FROM artist").Scan(&totalArtists, &missingBios)

	var totalAlbums, missingDesc, missingYear int
	_ = db.Db().QueryRow("SELECT COUNT(*), SUM(CASE WHEN description IS NULL OR description = '' THEN 1 ELSE 0 END), SUM(CASE WHEN min_year IS NULL OR min_year = 0 THEN 1 ELSE 0 END) FROM album").Scan(&totalAlbums, &missingDesc, &missingYear)

	var totalSongs, missingLyrics int
	_ = db.Db().QueryRow("SELECT COUNT(*), SUM(CASE WHEN lyrics IS NULL OR lyrics = '' OR lyrics = '[]' THEN 1 ELSE 0 END) FROM media_file").Scan(&totalSongs, &missingLyrics)

	stats := map[string]any{
		"artists": map[string]int{
			"total":       totalArtists,
			"missingBios": missingBios,
		},
		"albums": map[string]int{
			"total":       totalAlbums,
			"missingDesc": missingDesc,
			"missingYear": missingYear,
		},
		"songs": map[string]int{
			"total":         totalSongs,
			"missingLyrics": missingLyrics,
		},
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(stats)
}

// triggerAutoFixJob starts a metadata correction background process
func (api *Router) triggerAutoFixJob(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Type string `json:"type"` // "artists", "albums", "lyrics", "all"
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	if req.Type == "" {
		req.Type = "all"
	}

	autoFixJobsMu.Lock()
	jobCounter++
	jobID := strconv.Itoa(jobCounter)
	job := &AutoFixJob{
		ID:        jobID,
		Type:      req.Type,
		Status:    "running",
		StartedAt: time.Now(),
	}
	autoFixJobs[jobID] = job
	autoFixJobsMu.Unlock()

	// Launch async correction routine
	go api.runAutoFixBackground(job)

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(job)
}

// getAutoFixJobStatus polls current status of a bulk fix job
func (api *Router) getAutoFixJobStatus(w http.ResponseWriter, r *http.Request) {
	jobID := chi.URLParam(r, "jobId")

	autoFixJobsMu.RLock()
	job, exists := autoFixJobs[jobID]
	autoFixJobsMu.RUnlock()

	if !exists {
		http.Error(w, "Job not found", http.StatusNotFound)
		return
	}

	snapshot := job.Snapshot()
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(snapshot)
}

// queryMusicBrainzWithRetry executes a request to MusicBrainz API with exponential backoff on rate limits (503/429)
func queryMusicBrainzWithRetry(client *http.Client, reqUrl string, maxRetries int) (*http.Response, error) {
	var resp *http.Response
	var err error
	backoff := 2 * time.Second

	for i := 0; i < maxRetries; i++ {
		req, _ := http.NewRequest("GET", reqUrl, nil)
		// Precise User-Agent in line with MusicBrainz API recommendations
		req.Header.Set("User-Agent", "AonsokuAutoFix/2.0 (https://ms.troly.me; kienbui-eup/navidrome) contact@troly.me")
		resp, err = client.Do(req)
		if err == nil {
			if resp.StatusCode == http.StatusOK {
				return resp, nil
			}
			resp.Body.Close()
			if resp.StatusCode == http.StatusServiceUnavailable || resp.StatusCode == http.StatusTooManyRequests {
				// MusicBrainz Rate Limit (503 / 429) - backoff and retry
				time.Sleep(backoff)
				backoff *= 2
				continue
			}
			return nil, fmt.Errorf("MusicBrainz returned status code %d", resp.StatusCode)
		}
		time.Sleep(backoff)
		backoff *= 2
	}
	if err != nil {
		return nil, err
	}
	return nil, fmt.Errorf("max retries reached with status rate limit")
}

func isIgnoredArtist(name string) bool {
	n := strings.ToLower(strings.TrimSpace(name))
	return n == "" || n == "unknown" || n == "unknown artist" || n == "various artists" || n == "various" || n == "va" || n == "v.a" || n == "v.a."
}

func isIgnoredAlbum(name string) bool {
	n := strings.ToLower(strings.TrimSpace(name))
	return n == "" || n == "unknown" || n == "unknown album" || n == "various"
}

// runAutoFixBackground executes the scanning and correction processes
func (api *Router) runAutoFixBackground(job *AutoFixJob) {
	defer func() {
		job.mu.Lock()
		job.Status = "completed"
		job.EndedAt = time.Now()
		job.mu.Unlock()
		
		job.mu.RLock()
		updated := job.Updated
		job.mu.RUnlock()
		log.Info(context.Background(), "Metadata Auto-Fix Job Completed", "jobId", job.ID, "type", job.Type, "updated", updated)
	}()

	ctx := context.Background()
	loader, _ := api.pluginManager.(agents.PluginLoader)
	ag := agents.GetAgents(api.ds, loader)

	httpClient := &http.Client{Timeout: 10 * time.Second}

	// 1. Lyrics Auto-Fix
	if job.Type == "lyrics" || job.Type == "all" {
		rows, err := db.Db().Query("SELECT id, artist, title, album FROM media_file WHERE lyrics IS NULL OR lyrics = '' OR lyrics = '[]'")
		if err == nil {
			var songs []struct{ ID, Artist, Title, Album string }
			for rows.Next() {
				var s struct{ ID, Artist, Title, Album string }
				if errScan := rows.Scan(&s.ID, &s.Artist, &s.Title, &s.Album); errScan == nil {
					songs = append(songs, s)
				}
			}
			rows.Close()

			job.mu.Lock()
			job.Total += len(songs)
			job.mu.Unlock()

			// Use 5 concurrent workers for quick parallel fetching of lyrics via LrcLib
			numWorkers := 5
			if len(songs) < numWorkers {
				numWorkers = len(songs)
			}

			if numWorkers > 0 {
				jobsChan := make(chan struct{ ID, Artist, Title, Album string }, len(songs))
				for _, s := range songs {
					jobsChan <- s
				}
				close(jobsChan)

				var wg sync.WaitGroup
				for w := 0; w < numWorkers; w++ {
					wg.Add(1)
					go func() {
						defer wg.Done()
						for s := range jobsChan {
							job.mu.Lock()
							job.Processed++
							job.mu.Unlock()

							if s.Artist == "" || s.Title == "" || isIgnoredArtist(s.Artist) {
								continue
							}

							// LrcLib is highly responsive, slight sleep to play nice
							time.Sleep(50 * time.Millisecond)

							var lyricText string

							// 1.1 Try LrcLib GET API first (precise matching)
							getUrl := fmt.Sprintf("https://lrclib.net/api/get?artist_name=%s&track_name=%s", url.QueryEscape(s.Artist), url.QueryEscape(s.Title))
							reqGet, _ := http.NewRequest("GET", getUrl, nil)
							reqGet.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
							
							// Forwarding IP headers configured by user to ensure regional compliance
							reqGet.Header.Set("X-Forwarded-For", "113.160.0.1")
							reqGet.Header.Set("Client-IP", "113.160.0.1")
							reqGet.Header.Set("X-Real-IP", "113.160.0.1")

							resp, errGet := httpClient.Do(reqGet)
							if errGet == nil && resp.StatusCode == http.StatusOK {
								var result struct {
									SyncedLyrics string `json:"syncedLyrics"`
									PlainLyrics  string `json:"plainLyrics"`
								}
								if errDecode := json.NewDecoder(resp.Body).Decode(&result); errDecode == nil {
									lyricText = result.SyncedLyrics
									if lyricText == "" {
										lyricText = result.PlainLyrics
									}
								}
								resp.Body.Close()
							} else {
								if resp != nil {
									resp.Body.Close()
								}
								// 1.2 Fallback to Search API
								searchUrl := fmt.Sprintf("https://lrclib.net/api/search?artist_name=%s&track_name=%s", url.QueryEscape(s.Artist), url.QueryEscape(s.Title))
								reqSearch, _ := http.NewRequest("GET", searchUrl, nil)
								reqSearch.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
								
								reqSearch.Header.Set("X-Forwarded-For", "113.160.0.1")
								reqSearch.Header.Set("Client-IP", "113.160.0.1")
								reqSearch.Header.Set("X-Real-IP", "113.160.0.1")

								respSearch, errSearch := httpClient.Do(reqSearch)
								if errSearch == nil {
									if respSearch.StatusCode == http.StatusOK {
										var results []struct {
											SyncedLyrics string `json:"syncedLyrics"`
											PlainLyrics  string `json:"plainLyrics"`
										}
										if errDecode := json.NewDecoder(respSearch.Body).Decode(&results); errDecode == nil && len(results) > 0 {
											lyricText = results[0].SyncedLyrics
											if lyricText == "" {
												lyricText = results[0].PlainLyrics
											}
										}
									}
									respSearch.Body.Close()
								} else {
									job.mu.Lock()
									job.Errors = append(job.Errors, fmt.Sprintf("LrcLib connection error for %s: %s", s.Title, errSearch.Error()))
									job.mu.Unlock()
								}
							}

							if lyricText != "" {
								lyricsObj, errParse := model.ToLyrics("eng", lyricText)
								if errParse == nil && lyricsObj != nil {
									lyricList := model.LyricList{*lyricsObj}
									jsonBytes, _ := json.Marshal(lyricList)

									_, errUpd := db.Db().Exec("UPDATE media_file SET lyrics = ? WHERE id = ?", string(jsonBytes), s.ID)
									if errUpd == nil {
										job.mu.Lock()
										job.Updated++
										job.mu.Unlock()
									} else {
										job.mu.Lock()
										job.Errors = append(job.Errors, "DB save error: "+errUpd.Error())
										job.mu.Unlock()
									}
								}
							}
						}
					}()
				}
				wg.Wait()
			}
		}
	}

	// 2. Artist Auto-Fix (MusicBrainz & Bio via Agents)
	if job.Type == "artists" || job.Type == "all" {
		rows, err := db.Db().Query("SELECT id, name, mbz_artist_id FROM artist WHERE biography IS NULL OR biography = ''")
		if err == nil {
			var artists []struct{ ID, Name, MBZ string }
			for rows.Next() {
				var a struct{ ID, Name, MBZ string }
				if errScan := rows.Scan(&a.ID, &a.Name, &a.MBZ); errScan == nil {
					artists = append(artists, a)
				}
			}
			rows.Close()

			job.mu.Lock()
			job.Total += len(artists)
			job.mu.Unlock()

			for _, a := range artists {
				job.mu.Lock()
				job.Processed++
				job.mu.Unlock()
				mbid := a.MBZ

				if isIgnoredArtist(a.Name) {
					continue
				}

				if mbid == "" {
					// Pull MBID from MusicBrainz - delay 1.0s to respect strict rate-limit
					time.Sleep(1000 * time.Millisecond)
					reqUrl := fmt.Sprintf("https://musicbrainz.org/ws/2/artist/?query=artist:%s&fmt=json", url.QueryEscape(a.Name))
					
					resp, errGet := queryMusicBrainzWithRetry(httpClient, reqUrl, 3)
					if errGet == nil {
						var res struct {
							Artists []struct {
								ID string `json:"id"`
							} `json:"artists"`
						}
						if errDec := json.NewDecoder(resp.Body).Decode(&res); errDec == nil && len(res.Artists) > 0 {
							mbid = res.Artists[0].ID
							_, _ = db.Db().Exec("UPDATE artist SET mbz_artist_id = ? WHERE id = ?", mbid, a.ID)
						}
						resp.Body.Close()
					} else {
						job.mu.Lock()
						job.Errors = append(job.Errors, fmt.Sprintf("MusicBrainz API error for artist %s: %s", a.Name, errGet.Error()))
						job.mu.Unlock()
					}
				}

				if mbid != "" && ag != nil {
					bio, errBio := ag.GetArtistBiography(ctx, a.ID, a.Name, mbid)
					if errBio == nil && bio != "" {
						_, errUpd := db.Db().Exec("UPDATE artist SET biography = ? WHERE id = ?", bio, a.ID)
						if errUpd == nil {
							job.mu.Lock()
							job.Updated++
							job.mu.Unlock()
						} else {
							job.mu.Lock()
							job.Errors = append(job.Errors, "DB update error for artist bio: "+errUpd.Error())
							job.mu.Unlock()
						}
					}
				}
			}
		}
	}

	// 3. Album Auto-Fix (MusicBrainz Date & Desc via Agents)
	if job.Type == "albums" || job.Type == "all" {
		rows, err := db.Db().Query("SELECT id, name, album_artist, mbz_album_id FROM album WHERE description IS NULL OR description = '' OR min_year = 0")
		if err == nil {
			var albums []struct{ ID, Name, Artist, MBZ string }
			for rows.Next() {
				var al struct{ ID, Name, Artist, MBZ string }
				if errScan := rows.Scan(&al.ID, &al.Name, &al.Artist, &al.MBZ); errScan == nil {
					albums = append(albums, al)
				}
			}
			rows.Close()

			job.mu.Lock()
			job.Total += len(albums)
			job.mu.Unlock()

			for _, al := range albums {
				job.mu.Lock()
				job.Processed++
				job.mu.Unlock()
				mbid := al.MBZ
				year := 0

				if isIgnoredAlbum(al.Name) || isIgnoredArtist(al.Artist) {
					continue
				}

				if mbid == "" {
					// Pull MBID from MusicBrainz - delay 1.0s to respect strict rate-limit
					time.Sleep(1000 * time.Millisecond)
					reqUrl := fmt.Sprintf("https://musicbrainz.org/ws/2/release/?query=release:%s AND artist:%s&fmt=json", url.QueryEscape(al.Name), url.QueryEscape(al.Artist))
					
					resp, errGet := queryMusicBrainzWithRetry(httpClient, reqUrl, 3)
					if errGet == nil {
						var res struct {
							Releases []struct {
								ID   string `json:"id"`
								Date string `json:"date"`
							} `json:"releases"`
						}
						if errDec := json.NewDecoder(resp.Body).Decode(&res); errDec == nil && len(res.Releases) > 0 {
							mbid = res.Releases[0].ID
							dateStr := res.Releases[0].Date
							if len(dateStr) >= 4 {
								if y, errY := strconv.Atoi(dateStr[0:4]); errY == nil {
									year = y
								}
							}
							_, _ = db.Db().Exec("UPDATE album SET mbz_album_id = ?, min_year = ?, max_year = ? WHERE id = ?", mbid, year, year, al.ID)
						}
						resp.Body.Close()
					} else {
						job.mu.Lock()
						job.Errors = append(job.Errors, fmt.Sprintf("MusicBrainz API error for album %s: %s", al.Name, errGet.Error()))
						job.mu.Unlock()
					}
				}

				if mbid != "" && ag != nil {
					info, errInfo := ag.GetAlbumInfo(ctx, al.Name, al.Artist, mbid)
					if errInfo == nil && info != nil && info.Description != "" {
						_, errUpd := db.Db().Exec("UPDATE album SET description = ? WHERE id = ?", info.Description, al.ID)
						if errUpd == nil {
							job.mu.Lock()
							job.Updated++
							job.mu.Unlock()
						} else {
							job.mu.Lock()
							job.Errors = append(job.Errors, "DB update error for album desc: "+errUpd.Error())
							job.mu.Unlock()
						}
					}
				}
			}
		}
	}
}
