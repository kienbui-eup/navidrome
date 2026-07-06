# Import Song Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tab "Tìm bài hát" trong màn Import: tìm theo bài từ Archive.org + Google Drive, preview inline, tải về, ưu tiên lossless/hi-res.

**Architecture:** Backend gộp 2 nguồn song song trong `core/importer_search.go` (endpoint `GET /api/import/search/songs`), preview Drive qua proxy `GET /api/import/preview` (Range passthrough, auth JWT query param `?jwt=` — middleware đã hỗ trợ sẵn tại server/auth.go:174). UI là component riêng `ui/src/import/SongSearch.jsx` gắn làm tab đầu của ImportMusic.jsx, một `<audio>` element dùng chung.

**Tech Stack:** Go (net/http, x/text cho fold dấu), chi router, React + material-ui v4 theo pattern ImportMusic.jsx hiện có. Test: table-driven Go test (theo style core/importer_test.go) + httptest.

**Facts đã xác minh:**
- Import routes admin-only (native_api.go:89-94), verifier nhận `jwtauth.TokenFromQuery` (param tên `jwt`).
- UI token: `localStorage.getItem('token')`. httpClient tự gắn Authorization header.
- `golang.org/x/text v0.37.0` đã có trong go.mod.
- `conf.Server.GoogleDriveAPIKey` + `reDriveID`/`reDriveFolderID`/`driveAPIGet`/`driveAPIErrorMessage` sẵn có trong importer.go.
- Archive: `SearchArchive` (item-level), `ArchiveFiles` (file-level, đã lọc audio ext) sẵn có.

---

### Task 1: Refactor base URL thành field injectable (tiền đề test)

**Files:**
- Modify: `core/importer.go` — struct `importer` thêm `archiveBase, driveBase string`; `NewImporter` set `archiveBase: archiveBaseURL`, `driveBase: "https://www.googleapis.com"`. Thay các chỗ dùng const/hardcode: `SearchArchive` (dòng ~276), `ArchiveFiles` (~316), `ImportArchive` (~354) dùng `imp.archiveBase`; `listDriveFolderAPI` (~769) và `ImportDriveFile` (~837) dùng `imp.driveBase + "/drive/v3/files..."`.

- [ ] Sửa xong chạy: `go build -tags=netgo,sqlite_fts5 ./core/...` — PASS, `go test ./core/ -run TestIsAudioExt -count=1` PASS (không hồi quy).

### Task 2: Quality scoring + query matching (`core/importer_search.go` + test)

**Files:**
- Create: `core/importer_search.go`
- Create: `core/importer_search_test.go`

- [ ] **Types + helpers** — `SongHit{Source,Title,Album,Artist,Identifier,FileID,Filename,Format,Size,Length,Quality,Lossless,PreviewURL}`, `SongSearchResult{Hits,Warnings}`, `losslessMinScore=70`, `qualityForFile(format, filename) (score int, label string)` bảng: DSD/dsf/dff=100; FLAC+`24bit` (trong format HOẶC filename)=95 "FLAC 24bit"; flac=80; Apple Lossless/alac=78; ape/wv/wavpack=75; wav/aiff=70; "vbr mp3"/"320"=40; mp3=35; ogg/vorbis/opus=35; aac/m4a/m4b/wma=30; default=20 (label=format gốc). `foldSearch` (NFD + bỏ Mn + lower, x/text) và `matchesQuery(query, candidates...)`: mọi token của query xuất hiện trong ít nhất một candidate (không phân biệt hoa/thường, dấu).
- [ ] **Test trước khi implement aggregation**: `TestQualityForFile` (bảng ≥8 case gồm "24bit Flac", "Flac", tên file `x (24bit).flac` với format rỗng, `song.dsf`, "VBR MP3", `track.m4a`), `TestMatchesQuery` ("trinh cong son" khớp "Trịnh Công Sơn - Diễm Xưa.flac"; token thiếu → false; query rỗng → true). Run: `go test ./core/ -run 'TestQualityForFile|TestMatchesQuery' -count=1 -v` → PASS.

### Task 3: SearchSongs aggregation (+ test httptest)

**Files:**
- Modify: `core/importer.go` — interface `Importer` thêm `SearchSongs(ctx, query, driveFolder string, losslessOnly bool) (*SongSearchResult, error)` và `PreviewDrive(ctx, fileID, rangeHeader string) (*http.Response, error)`.
- Modify: `core/importer_search.go`

- [ ] `SearchSongs`: timeout 8s, 2 goroutine (archive luôn, drive khi `driveFolder != ""`), lỗi nguồn nào → append `Warnings`, không fail toàn cục; lọc `losslessOnly` (`score>=70`); sort: Quality desc → title chứa nguyên query (folded) trước → Size desc; cap 200 hit.
- [ ] `searchArchiveSongs`: `SearchArchive(q,15)` → per-item goroutine (semaphore 5) `ArchiveFiles` best-effort → lọc `matchesQuery(q, f.Title, f.Name, item.Title, item.Creator)` → PreviewURL = `imp.archiveBase + "/download/" + escape(identifier) + "/" + escapeArchivePath(name)` (extract helper `escapeArchivePath` từ logic ImportArchive, ImportArchive dùng lại — DRY).
- [ ] `searchDriveSongs`: cần `conf.Server.GoogleDriveAPIKey` (không có → error tiếng Việt rõ ràng); nhận cả link folder lẫn bare ID (reDriveFolderID → reDriveID); phân trang như `listDriveFolderAPI` nhưng fields thêm `size`; lọc local bằng `matchesQuery` (Drive `name contains` chỉ khớp prefix từ — không đủ); PreviewURL = `/api/import/preview?source=drive&id=<id>`.
- [ ] `PreviewDrive`: validate `reDriveID`, cần key; GET `alt=media` bằng `imp.download`, forward `Range`, chấp nhận 200/206, lỗi → `driveAPIErrorMessage`.
- [ ] **Test** `TestSearchSongs` (httptest 1 mux đóng cả 2 vai): `/advancedsearch.php` → 1 item; `/metadata/item1` → flac + mp3 (title khớp); `/drive/v3/files` → 1 file `Hotel California (24bit).flac` size "1000". Importer khởi tạo trực tiếp struct với `api/download: srv.Client()`, bases = srv.URL, set `conf.Server.GoogleDriveAPIKey="test"` (khôi phục bằng t.Cleanup). Assert: 3 hits; thứ tự đầu = drive 24bit (95); losslessOnly bỏ mp3; case drive trả 403 → 2 hits + 1 warning chứa "Google Drive". `TestPreviewDriveRange`: mux echo Range → 206, assert forward. Run: `go test ./core/ -run 'TestSearchSongs|TestPreviewDrive' -count=1 -v` → PASS.

### Task 4: Routes + handlers (+ mock fix nếu interface mở rộng làm gãy)

**Files:**
- Modify: `server/nativeapi/import.go` — trong `addImportRoute`: `r.Get("/search/songs", api.songSearchHandler)`; `r.Get("/preview", api.importPreviewHandler)`.
- Kiểm tra mock: `grep -rn "core.Importer" server/ tests/` — mock nào implement interface thì thêm 2 method stub.

- [ ] `songSearchHandler`: đọc `q`, `drive`, `lossless=true`; trả `writeJSON`.
- [ ] `importPreviewHandler`: chỉ `source=drive`; gọi `PreviewDrive(ctx, id, r.Header.Get("Range"))`; copy headers `Content-Type/Content-Length/Content-Range/Accept-Ranges` (thiếu Accept-Ranges thì set "bytes"), `WriteHeader(resp.StatusCode)`, `io.Copy`.
- [ ] Run: `go build -tags=netgo,sqlite_fts5 ./... && go test ./server/nativeapi/ -count=1` → PASS.

### Task 5: UI — `SongSearch.jsx` + gắn tab

**Files:**
- Create: `ui/src/import/SongSearch.jsx` — props `{libraryId, onImported, classes}`. State: query, driveFolder, losslessOnly (default true), result, busy, playing. Một `Audio()` trong useEffect (preload none, onended/onerror reset, cleanup pause+clear src). `previewSrc(hit)`: previewUrl tuyệt đối (archive) dùng thẳng; bắt đầu bằng `/` (drive) → append `&jwt=<localStorage token>`. Search: `GET /api/import/search/songs?q=&drive=&lossless=` qua httpClient; hiển thị `result.warnings` dạng Typography cảnh báo; list hit: ▶/⏹ toggle preview, ListItemText primary=title, secondary=artist/album • Chip label=format (màu primary nếu lossless) • size (formatBytes) • length; nút GetApp gọi import: archive → `POST /api/import/archive {identifier, filename, libraryId}`, drive → `POST /api/import/drive/file {id: fileId, name: filename, libraryId}` rồi `onImported(savedName)`.
- Modify: `ui/src/import/ImportMusic.jsx` — import SongSearch; thêm `<Tab label="Tìm bài hát" />` đầu danh sách; index cũ +1 (URL/RSS=1, Archive=2, Lịch sử=3, `handleTab` load history khi v===3); render `{tab === 0 && <SongSearch libraryId={libraryId} onImported={afterImport} classes={classes} />}`.

- [ ] Run: `cd ui && npm run build` → PASS (vite build).

### Task 6: Verify + deploy

- [ ] `go build -tags=netgo,sqlite_fts5 ./... && go test ./core/... ./server/nativeapi/... -count=1` PASS toàn bộ.
- [ ] Build image `troly-<sha>-4`, push, `update-container`, chờ VM, verify: `curl https://ms.troly.me/ping` 200; search endpoint trả JSON (cần JWT admin — verify qua log hoặc UI).
- [ ] Không commit (user chưa yêu cầu).
