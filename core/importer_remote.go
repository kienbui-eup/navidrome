package core

import (
	"context"
	"crypto/md5" //nolint:gosec // required by the Subsonic API token scheme
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"slices"
	"strconv"
	"strings"

	"github.com/vi2play/vi2play/log"
	"github.com/vi2play/vi2play/model/id"
)

// Import from another Navidrome/Subsonic server. The admin saves a list of
// source servers (URL + user/pass); songs are located via the Subsonic API
// (search3/getArtist/getAlbum/getSong) and downloaded as original files via
// /rest/download, through the regular batch-job flow (dedup, history, scan).

const (
	remoteServersKey   = "importer.remoteServers"
	subsonicAPIVersion = "1.16.1"
	subsonicClientName = "navidrome-import"
	remoteSearchCount  = "20"
)

// RemoteServer is a saved remote Subsonic/Navidrome source server. The list is
// stored as JSON in the property table; Password is never sent to the UI
// (handlers blank it, and omitempty drops the field).
type RemoteServer struct {
	ID       string `json:"id"`
	Name     string `json:"name"`
	URL      string `json:"url"`
	Username string `json:"username"`
	Password string `json:"password,omitempty"`
}

// JSONInt represents an integer that can be unmarshalled from either a JSON number or string.
type JSONInt int

func (ji *JSONInt) UnmarshalJSON(data []byte) error {
	var val any
	if err := json.Unmarshal(data, &val); err != nil {
		return err
	}
	switch v := val.(type) {
	case float64:
		*ji = JSONInt(v)
	case string:
		var i int
		if _, err := fmt.Sscanf(v, "%d", &i); err != nil {
			*ji = 0
		} else {
			*ji = JSONInt(i)
		}
	case nil:
		*ji = 0
	default:
		*ji = 0
	}
	return nil
}

func (ji JSONInt) MarshalJSON() ([]byte, error) {
	return json.Marshal(int(ji))
}

// JSONInt64 represents an int64 that can be unmarshalled from either a JSON number or string.
type JSONInt64 int64

func (ji *JSONInt64) UnmarshalJSON(data []byte) error {
	var val any
	if err := json.Unmarshal(data, &val); err != nil {
		return err
	}
	switch v := val.(type) {
	case float64:
		*ji = JSONInt64(v)
	case string:
		var i int64
		if _, err := fmt.Sscanf(v, "%d", &i); err != nil {
			*ji = 0
		} else {
			*ji = JSONInt64(i)
		}
	case nil:
		*ji = 0
	default:
		*ji = 0
	}
	return nil
}

func (ji JSONInt64) MarshalJSON() ([]byte, error) {
	return json.Marshal(int64(ji))
}

type RemoteSong struct {
	ID           string     `json:"id"`
	Title        string     `json:"title"`
	Artist       string     `json:"artist"`
	Album        string     `json:"album"`
	Suffix       string     `json:"suffix"`
	Size         *JSONInt64 `json:"size,omitempty"`
	BitRate      *JSONInt   `json:"bitRate,omitempty"`
	SampleRate   *JSONInt   `json:"sampleRate,omitempty"`
	BitDepth     *JSONInt   `json:"bitDepth,omitempty"`
	ChannelCount *JSONInt   `json:"channelCount,omitempty"`
	Duration     *JSONInt   `json:"duration,omitempty"`
	Year         *JSONInt   `json:"year,omitempty"`
	Genre        *string    `json:"genre,omitempty"`
	PlayCount    *JSONInt   `json:"playCount,omitempty"`
	Starred      *string    `json:"starred,omitempty"`
}

type RemoteAlbum struct {
	ID        string   `json:"id"`
	Name      string   `json:"name"`
	Artist    string   `json:"artist"`
	SongCount *JSONInt `json:"songCount,omitempty"`
	Year      *JSONInt `json:"year,omitempty"`
}

type RemoteArtist struct {
	ID         string   `json:"id"`
	Name       string   `json:"name"`
	AlbumCount *JSONInt `json:"albumCount,omitempty"`
}

type RemoteSearchResult struct {
	Songs   []RemoteSong   `json:"songs"`
	Albums  []RemoteAlbum  `json:"albums"`
	Artists []RemoteArtist `json:"artists"`
}

// ---------------------------------------------------------------------------
// Saved servers (CRUD on the property table)
// ---------------------------------------------------------------------------

func (imp *importer) loadRemoteServers(ctx context.Context) ([]RemoteServer, error) {
	raw, err := imp.ds.Property(ctx).DefaultGet(remoteServersKey, "[]")
	if err != nil {
		return nil, fmt.Errorf("loading remote servers: %w", err)
	}
	var servers []RemoteServer
	if err := json.Unmarshal([]byte(raw), &servers); err != nil {
		return nil, fmt.Errorf("parsing remote servers: %w", err)
	}
	return servers, nil
}

func (imp *importer) storeRemoteServers(ctx context.Context, servers []RemoteServer) error {
	raw, err := json.Marshal(servers)
	if err != nil {
		return err
	}
	return imp.ds.Property(ctx).Put(remoteServersKey, string(raw))
}

func (imp *importer) RemoteServers(ctx context.Context) ([]RemoteServer, error) {
	return imp.loadRemoteServers(ctx)
}

func (imp *importer) SaveRemoteServer(ctx context.Context, s RemoteServer) (*RemoteServer, error) {
	s.Name = strings.TrimSpace(s.Name)
	s.URL = strings.TrimRight(strings.TrimSpace(s.URL), "/")
	s.Username = strings.TrimSpace(s.Username)
	if err := validateRemoteServerURL(s.URL); err != nil {
		return nil, err
	}
	if s.Username == "" {
		return nil, fmt.Errorf("thiếu tài khoản đăng nhập server nguồn")
	}
	if s.Name == "" {
		if u, err := url.Parse(s.URL); err == nil && u.Host != "" {
			s.Name = u.Host
		} else {
			s.Name = s.URL
		}
	}

	imp.mu.Lock()
	defer imp.mu.Unlock()
	servers, err := imp.loadRemoteServers(ctx)
	if err != nil {
		return nil, err
	}
	if s.ID == "" {
		if s.Password == "" {
			return nil, fmt.Errorf("thiếu mật khẩu server nguồn")
		}
		s.ID = id.NewRandom()
		servers = append(servers, s)
	} else {
		idx := slices.IndexFunc(servers, func(o RemoteServer) bool { return o.ID == s.ID })
		if idx < 0 {
			return nil, fmt.Errorf("không tìm thấy server nguồn")
		}
		// The UI never has the password (GET masks it): empty means keep the old one.
		if s.Password == "" {
			s.Password = servers[idx].Password
		}
		servers[idx] = s
	}
	if err := imp.storeRemoteServers(ctx, servers); err != nil {
		return nil, err
	}
	return &s, nil
}

func (imp *importer) DeleteRemoteServer(ctx context.Context, serverID string) error {
	imp.mu.Lock()
	defer imp.mu.Unlock()
	servers, err := imp.loadRemoteServers(ctx)
	if err != nil {
		return err
	}
	next := slices.DeleteFunc(servers, func(o RemoteServer) bool { return o.ID == serverID })
	if len(next) == len(servers) {
		return fmt.Errorf("không tìm thấy server nguồn")
	}
	return imp.storeRemoteServers(ctx, next)
}

func (imp *importer) remoteServerByID(ctx context.Context, serverID string) (RemoteServer, error) {
	servers, err := imp.loadRemoteServers(ctx)
	if err != nil {
		return RemoteServer{}, err
	}
	for _, s := range servers {
		if s.ID == serverID {
			return s, nil
		}
	}
	return RemoteServer{}, fmt.Errorf("không tìm thấy server nguồn (đã bị xóa?)")
}

// validateRemoteServerURL accepts http(s) URLs including LAN/private addresses:
// the address is typed in by an admin on purpose, unlike arbitrary import URLs
// which must pass validatePublicURL.
func validateRemoteServerURL(rawURL string) error {
	u, err := url.Parse(rawURL)
	if err != nil || (u.Scheme != "http" && u.Scheme != "https") || u.Host == "" {
		return fmt.Errorf("địa chỉ server không hợp lệ (cần dạng http(s)://host[:port])")
	}
	return nil
}


// ---------------------------------------------------------------------------
// Minimal Subsonic API client
// ---------------------------------------------------------------------------

// JSONArray represents an array of T that can be unmarshalled from either a single JSON object, a JSON array, null, or empty string.
type JSONArray[T any] []T

func (ja *JSONArray[T]) UnmarshalJSON(data []byte) error {
	if len(data) == 0 {
		return nil
	}
	trimmed := strings.TrimSpace(string(data))
	if trimmed == "null" || trimmed == `""` || trimmed == "" {
		*ja = nil
		return nil
	}

	if trimmed[0] == '[' {
		var arr []T
		if err := json.Unmarshal(data, &arr); err != nil {
			return err
		}
		*ja = JSONArray[T](arr)
		return nil
	}

	var single T
	if err := json.Unmarshal(data, &single); err != nil {
		return err
	}
	*ja = JSONArray[T]{single}
	return nil
}

func (ja JSONArray[T]) MarshalJSON() ([]byte, error) {
	return json.Marshal([]T(ja))
}

type subsonicError struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
}

type subsonicResponse struct {
	Status        string             `json:"status"`
	Error         *subsonicError     `json:"error"`
	SearchResult3 *remoteSearch3     `json:"searchResult3"`
	Artist        *remoteArtistID3    `json:"artist"`
	Album         *remoteAlbumID3     `json:"album"`
	Song          *RemoteSong        `json:"song"`
	RandomSongs   *remoteRandomSongs `json:"randomSongs"`
	AlbumList     *remoteAlbumList   `json:"albumList"`
	AlbumList2    *remoteAlbumList   `json:"albumList2"`
	Starred       *remoteStarred     `json:"starred"`
	Starred2      *remoteStarred     `json:"starred2"`
	Indexes       *remoteIndexes     `json:"indexes"`
	Artists       *remoteArtists     `json:"artists"`
}

type remoteStarred struct {
	Songs   JSONArray[RemoteSong]   `json:"song"`
	Albums  JSONArray[RemoteAlbum]  `json:"album"`
	Artists JSONArray[RemoteArtist] `json:"artist"`
}

type remoteIndexes struct {
	Index JSONArray[remoteIndexItem] `json:"index"`
}

type remoteArtists struct {
	Index JSONArray[remoteIndexItem] `json:"index"`
}

type remoteIndexItem struct {
	Name   string                  `json:"name"`
	Artist JSONArray[RemoteArtist] `json:"artist"`
}

type remoteAlbumList struct {
	Albums JSONArray[RemoteAlbum] `json:"album"`
}

type subsonicEnvelope struct {
	Response subsonicResponse `json:"subsonic-response"`
}

type remoteSearch3 struct {
	Artists JSONArray[RemoteArtist] `json:"artist"`
	Albums  JSONArray[RemoteAlbum]  `json:"album"`
	Songs   JSONArray[RemoteSong]   `json:"song"`
}

type remoteRandomSongs struct {
	Songs JSONArray[RemoteSong] `json:"song"`
}

type remoteArtistID3 struct {
	Albums JSONArray[RemoteAlbum] `json:"album"`
}

type remoteAlbumID3 struct {
	Songs JSONArray[RemoteSong] `json:"song"`
}

// remoteAPIURL builds a Subsonic REST URL with token auth (t=md5(pass+salt),
// fresh random salt per request — the password itself is never sent).
func remoteAPIURL(server RemoteServer, endpoint string, params url.Values) string {
	q := url.Values{}
	for k, vs := range params {
		q[k] = vs
	}
	saltBytes := make([]byte, 8)
	_, _ = rand.Read(saltBytes)
	salt := hex.EncodeToString(saltBytes)
	token := md5.Sum([]byte(server.Password + salt)) //nolint:gosec // Subsonic API scheme
	q.Set("u", server.Username)
	q.Set("t", hex.EncodeToString(token[:]))
	q.Set("s", salt)
	q.Set("v", subsonicAPIVersion)
	q.Set("c", subsonicClientName)
	q.Set("f", "json")
	return strings.TrimRight(server.URL, "/") + "/rest/" + endpoint + "?" + q.Encode()
}

func subsonicErrorMessage(e *subsonicError) string {
	switch {
	case e == nil:
		return "lỗi không xác định từ server nguồn"
	case e.Code == 40:
		return "sai tên đăng nhập hoặc mật khẩu"
	case e.Message != "":
		return e.Message
	default:
		return fmt.Sprintf("lỗi Subsonic (mã %d)", e.Code)
	}
}

func (imp *importer) remoteCall(ctx context.Context, server RemoteServer, endpoint string, params url.Values) (*subsonicResponse, error) {
	body, err := imp.getBody(ctx, remoteAPIURL(server, endpoint, params), maxFeedResponseSize)
	if err != nil {
		return nil, fmt.Errorf("không kết nối được tới %q: %w", server.Name, err)
	}
	var env subsonicEnvelope
	if err := json.Unmarshal(body, &env); err != nil {
		return nil, fmt.Errorf("lỗi đọc dữ liệu từ %q (JSON error: %w)", server.Name, err)
	}
	if env.Response.Status == "" {
		return nil, fmt.Errorf("%q không trả lời như một server Subsonic/Navidrome", server.Name)
	}
	if env.Response.Status != "ok" {
		return nil, fmt.Errorf("%q: %s", server.Name, subsonicErrorMessage(env.Response.Error))
	}
	return &env.Response, nil
}

// TestRemoteServer pings a server to validate address and credentials. Empty
// fields are filled from the saved entry (edit dialogs send no password).
func (imp *importer) TestRemoteServer(ctx context.Context, s RemoteServer) error {
	s.URL = strings.TrimRight(strings.TrimSpace(s.URL), "/")
	if s.ID != "" {
		if saved, err := imp.remoteServerByID(ctx, s.ID); err == nil {
			if s.URL == "" {
				s.URL = saved.URL
			}
			if s.Username == "" {
				s.Username = saved.Username
			}
			if s.Password == "" {
				s.Password = saved.Password
			}
			if s.Name == "" {
				s.Name = saved.Name
			}
		}
	}
	if s.Name == "" {
		s.Name = s.URL
	}
	if err := validateRemoteServerURL(s.URL); err != nil {
		return err
	}
	_, err := imp.remoteCall(ctx, s, "ping", nil)
	return err
}

// ---------------------------------------------------------------------------
// Browse / search
// ---------------------------------------------------------------------------

func (imp *importer) RemoteSearch(ctx context.Context, serverID, query string, limit int) (*RemoteSearchResult, error) {
	server, err := imp.remoteServerByID(ctx, serverID)
	if err != nil {
		return nil, err
	}
	query = strings.TrimSpace(query)
	if query == "" || query == "*" {
		// Ping first to ensure the server is reachable and credentials are valid.
		if _, err := imp.remoteCall(ctx, server, "ping", nil); err != nil {
			return nil, err
		}

		log.Info(ctx, "Khởi động tính năng đồng bộ tự động cho 'Server khác'", "server", server.Name)
		var lastErr error

		// Step 1: ALWAYS fetch the complete list of artists alphabetically
		var artists []RemoteArtist
		for _, endpoint := range []string{"getIndexes", "getArtists"} {
			log.Info(ctx, "Lấy danh sách nghệ sĩ mặc định", "endpoint", endpoint)
			respIndexes, errIndex := imp.remoteCall(ctx, server, endpoint, nil)
			if errIndex == nil && respIndexes != nil {
				var indexes []remoteIndexItem
				if endpoint == "getIndexes" && respIndexes.Indexes != nil {
					indexes = respIndexes.Indexes.Index
				} else if endpoint == "getArtists" && respIndexes.Artists != nil {
					indexes = respIndexes.Artists.Index
				}
				for _, idx := range indexes {
					artists = append(artists, idx.Artist...)
				}
				if len(artists) > 0 {
					log.Info(ctx, "Lấy danh sách nghệ sĩ thành công", "count", len(artists))
					break
				}
			} else if errIndex != nil {
				log.Error(ctx, "Lỗi khi gọi endpoint lấy nghệ sĩ", "endpoint", endpoint, "err", errIndex)
				lastErr = errIndex
			}
		}

		// Apply the user's limit to the artists list (e.g. 50, 100, 200, 500)
		if len(artists) > limit && limit > 0 {
			artists = artists[:limit]
		}

		// Step 2: Also fetch some random songs (up to the limit) to populate the song list
		var songs []RemoteSong
		size := "50"
		if limit > 0 {
			size = strconv.Itoa(limit)
		}
		log.Info(ctx, "Thử tự động lấy danh sách bài hát ngẫu nhiên", "endpoint", "getRandomSongs", "size", size)
		respRandom, errRand := imp.remoteCall(ctx, server, "getRandomSongs", url.Values{
			"size": {size},
		})
		if errRand == nil && respRandom != nil && respRandom.RandomSongs != nil {
			songs = respRandom.RandomSongs.Songs
			log.Info(ctx, "Lấy danh sách bài hát ngẫu nhiên thành công", "count", len(songs))
		} else if errRand != nil {
			log.Error(ctx, "Lỗi khi gọi endpoint getRandomSongs", "err", errRand)
			lastErr = errRand
		}

		// Step 3: Also fetch some newest albums
		var albums []RemoteAlbum
		respAlbums, errAlb := imp.remoteCall(ctx, server, "getAlbumList2", url.Values{
			"type": {"newest"},
			"size": {"20"},
		})
		if errAlb == nil && respAlbums != nil && respAlbums.AlbumList2 != nil {
			albums = respAlbums.AlbumList2.Albums
		} else {
			// fallback to getAlbumList
			respAlbums, errAlb = imp.remoteCall(ctx, server, "getAlbumList", url.Values{
				"type": {"newest"},
				"size": {"20"},
			})
			if errAlb == nil && respAlbums != nil && respAlbums.AlbumList != nil {
				albums = respAlbums.AlbumList.Albums
			}
		}

		// If we found artists or songs or albums, return them combined
		if len(artists) > 0 || len(songs) > 0 || len(albums) > 0 {
			log.Info(ctx, "Hoàn tất tự động lấy dữ liệu mặc định", "artists", len(artists), "songs", len(songs), "albums", len(albums))
			return &RemoteSearchResult{
				Songs:   songs,
				Albums:  albums,
				Artists: artists,
			}, nil
		}

		// Bubble up the last error if all mechanisms failed.
		if lastErr != nil {
			log.Error(ctx, "Tất cả các cơ chế tự động lấy nhạc đều thất bại", "lastErr", lastErr)
			return nil, fmt.Errorf("không thể tự động lấy danh sách nhạc từ server nguồn: %w", lastErr)
		}

		log.Info(ctx, "Kết nối thành công nhưng server nguồn trống hoặc không có dữ liệu nhạc")
		return &RemoteSearchResult{
			Songs:   []RemoteSong{},
			Albums:  []RemoteAlbum{},
			Artists: []RemoteArtist{},
		}, nil
	}
	limitStr := strconv.Itoa(limit)
	if limit <= 0 {
		limitStr = remoteSearchCount
	}
	resp, err := imp.remoteCall(ctx, server, "search3", url.Values{
		"query":       {query},
		"songCount":   {limitStr},
		"albumCount":  {limitStr},
		"artistCount": {limitStr},
	})
	if err != nil {
		return nil, err
	}
	res := &RemoteSearchResult{
		Songs:   []RemoteSong{},
		Albums:  []RemoteAlbum{},
		Artists: []RemoteArtist{},
	}
	if resp.SearchResult3 != nil {
		res.Songs = append(res.Songs, resp.SearchResult3.Songs...)
		res.Albums = append(res.Albums, resp.SearchResult3.Albums...)
		res.Artists = append(res.Artists, resp.SearchResult3.Artists...)
	}
	return res, nil
}

func (imp *importer) RemoteArtist(ctx context.Context, serverID, artistID string) ([]RemoteAlbum, error) {
	server, err := imp.remoteServerByID(ctx, serverID)
	if err != nil {
		return nil, err
	}
	return imp.remoteArtistAlbums(ctx, server, artistID)
}

func (imp *importer) remoteArtistAlbums(ctx context.Context, server RemoteServer, artistID string) ([]RemoteAlbum, error) {
	resp, err := imp.remoteCall(ctx, server, "getArtist", url.Values{"id": {artistID}})
	if err != nil {
		return nil, err
	}
	if resp.Artist == nil {
		return []RemoteAlbum{}, nil
	}
	return resp.Artist.Albums, nil
}

func (imp *importer) RemoteAlbum(ctx context.Context, serverID, albumID string) ([]RemoteSong, error) {
	server, err := imp.remoteServerByID(ctx, serverID)
	if err != nil {
		return nil, err
	}
	return imp.remoteAlbumSongs(ctx, server, albumID)
}

func (imp *importer) remoteAlbumSongs(ctx context.Context, server RemoteServer, albumID string) ([]RemoteSong, error) {
	resp, err := imp.remoteCall(ctx, server, "getAlbum", url.Values{"id": {albumID}})
	if err != nil {
		return nil, err
	}
	if resp.Album == nil {
		return []RemoteSong{}, nil
	}
	return resp.Album.Songs, nil
}

// ---------------------------------------------------------------------------
// Import (expand song/album/artist into a batch job)
// ---------------------------------------------------------------------------

// remoteSongsFor resolves the songs referenced by kind+refID: the song itself,
// all songs of an album, or every song of every album of an artist.
func (imp *importer) remoteSongsFor(ctx context.Context, server RemoteServer, kind, refID string) ([]RemoteSong, error) {
	switch kind {
	case "song":
		resp, err := imp.remoteCall(ctx, server, "getSong", url.Values{"id": {refID}})
		if err != nil {
			return nil, err
		}
		if resp.Song == nil {
			return nil, fmt.Errorf("không tìm thấy bài hát trên server nguồn")
		}
		return []RemoteSong{*resp.Song}, nil
	case "album":
		return imp.remoteAlbumSongs(ctx, server, refID)
	case "artist":
		albums, err := imp.remoteArtistAlbums(ctx, server, refID)
		if err != nil {
			return nil, err
		}
		var songs []RemoteSong
		for _, al := range albums {
			s, err := imp.remoteAlbumSongs(ctx, server, al.ID)
			if err != nil {
				return nil, fmt.Errorf("album %q: %w", al.Name, err)
			}
			songs = append(songs, s...)
		}
		return songs, nil
	default:
		return nil, fmt.Errorf("loại import không hợp lệ %q", kind)
	}
}

func (imp *importer) StartRemoteImport(ctx context.Context, serverID, kind, refID string, libraryID int) (string, int, error) {
	server, err := imp.remoteServerByID(ctx, serverID)
	if err != nil {
		return "", 0, err
	}
	songs, err := imp.remoteSongsFor(ctx, server, kind, refID)
	if err != nil {
		return "", 0, err
	}
	if len(songs) == 0 {
		return "", 0, fmt.Errorf("không có bài nào để import")
	}
	// All items share one resolved copy of the server, so deleting the saved
	// entry while the job runs does not break the remaining downloads.
	srv := server
	items := make([]ImportJobItem, len(songs))
	for i, s := range songs {
		items[i] = ImportJobItem{
			Type:         "remote",
			ServerID:     serverID,
			ID:           s.ID,
			Name:         remoteSongFilename(s),
			remoteServer: &srv,
		}
	}
	jobID, err := imp.StartImportJob(ctx, items, libraryID)
	if err != nil {
		return "", 0, err
	}
	return jobID, len(items), nil
}

func remoteSongFilename(s RemoteSong) string {
	name := s.Title
	if name == "" {
		name = s.ID
	}
	if s.Artist != "" {
		name = s.Artist + " - " + name
	}
	// Path separators must be replaced up front: safeAudioFilename keeps only
	// the part after the last "/" (path.Base), which would truncate the title.
	name = strings.NewReplacer("/", "_", "\\", "_").Replace(name)
	suffix := s.Suffix
	if suffix == "" {
		suffix = "mp3"
	}
	return safeAudioFilename(name + "." + suffix)
}

// importRemoteItem downloads one song (original file, /rest/download) from the
// item's remote server into libraryID. Items expanded by StartRemoteImport
// carry the resolved server; items posted through the generic job endpoint are
// resolved by ServerID here.
func (imp *importer) importRemoteItem(ctx context.Context, it ImportJobItem, libraryID int) (*ImportResult, error) {
	var server RemoteServer
	if it.remoteServer != nil {
		server = *it.remoteServer
	} else {
		var err error
		server, err = imp.remoteServerByID(ctx, it.ServerID)
		if err != nil {
			return nil, err
		}
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, remoteAPIURL(server, "download", url.Values{"id": {it.ID}}), nil)
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
	// Subsonic reports download errors as a JSON/XML envelope with HTTP 200.
	ct := resp.Header.Get("Content-Type")
	if strings.Contains(ct, "json") || strings.Contains(ct, "xml") {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, maxFeedResponseSize))
		var env subsonicEnvelope
		if json.Unmarshal(body, &env) == nil && env.Response.Error != nil {
			return nil, fmt.Errorf("%s", subsonicErrorMessage(env.Response.Error))
		}
		return nil, fmt.Errorf("server nguồn không trả về file nhạc (Content-Type %q)", ct)
	}

	name := safeAudioFilename(it.Name)
	res, err := imp.persist(ctx, resp.Body, name, importMeta{
		libraryID: libraryID,
		source:    "remote",
		ref:       server.Name + ":" + it.ID,
	})
	if err != nil {
		return nil, err
	}
	log.Info(ctx, "Imported remote song", "server", server.Name, "name", res.SavedName,
		"bytes", res.Bytes, "duplicate", res.Duplicate)
	return res, nil
}

// RemotePreview streams a song from the remote server for in-browser preview
// (/rest/stream, Range passthrough). A proxy is required because the Subsonic
// auth token must be computed server-side.
func (imp *importer) RemotePreview(ctx context.Context, serverID, songID, format, rangeHeader string) (*http.Response, error) {
	server, err := imp.remoteServerByID(ctx, serverID)
	if err != nil {
		return nil, err
	}
	params := url.Values{"id": {songID}}
	format = strings.ToLower(strings.TrimSpace(format))
	if format == "dsf" || format == "dff" || format == "dsd" || format == "ape" || format == "wv" || format == "wma" {
		params.Set("format", "mp3")
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, remoteAPIURL(server, "stream", params), nil)
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
		_ = resp.Body.Close()
		return nil, fmt.Errorf("preview failed: HTTP %d", resp.StatusCode)
	}
	return resp, nil
}
