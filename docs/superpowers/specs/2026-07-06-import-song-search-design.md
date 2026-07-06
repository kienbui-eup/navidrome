# Import Song Search — Design (2026-07-06)

## Mục tiêu

Thêm vào màn hình Import (Trợ lý nhạc / Navidrome fork) khả năng tìm kiếm theo **bài hát** từ Archive.org + Google Drive, cho **nghe thử nhanh** (preview inline) và **tải về** (import vào thư viện), **ưu tiên chất lượng hi-end** (lossless/hi-res).

## Phạm vi

- Nguồn: Archive.org (public, hợp pháp) + Google Drive folder công khai (API key server-side, đã cấu hình `ND_GOOGLEDRIVEAPIKEY`).
- Preview: mini player inline trong tab Import, không đụng player chính.
- Tải về: tái dùng import job flow sẵn có (`StartImportJob`/`ImportArchive`/`ImportDriveFile`).

## Backend

### `core/importer_search.go` (mới)

- `SearchSongs(ctx, q, driveFolderID string, losslessOnly bool) (*SongSearchResult, error)`
  - Archive: `SearchArchive(q + mediatype:audio, 15 items)` → fetch files song song (5 concurrent, timeout tổng 8s) → lọc file audio có tên/title khớp query (case/diacritic-insensitive substring).
  - Drive (nếu có folderID): `files.list` q=`name contains '<q>' and '<folder>' in parents and trashed=false`, fields: id/name/size/mimeType/fileExtension. Không đệ quy.
  - Hai nguồn chạy song song; lỗi một nguồn → vẫn trả kết quả nguồn kia + `warnings[]`.
- Chấm điểm chất lượng (`qualityScore`): DSD/DSF 100 > FLAC 24bit 90 > FLAC/ALAC/WAV 70–80 > MP3 320 40 > lossy khác ≤35. Nhận diện từ format string (Archive) hoặc extension (Drive).
- Sort: qualityScore desc → relevance (khớp tên) → size desc. `losslessOnly` lọc score ≥ 70.
- `SongHit`: `{source: "archive"|"drive", title, artist?, identifier/fileID, filename, format, size, quality, previewURL, importPayload}`.

### Routes (`server/nativeapi/import.go`)

- `GET /api/import/search/songs?q=&drive=&lossless=` → SearchSongs (auth như các route import khác).
- `GET /api/import/preview?source=drive&id=<fileID>` → proxy stream `alt=media` với API key; passthrough `Range`/`Content-Type`/`Accept-Ranges`; chỉ cho phép host `www.googleapis.com` (chống SSRF). Archive preview phát thẳng URL công khai `https://archive.org/download/...`, không cần proxy.

## UI

- Component mới `ui/src/import/SongSearch.jsx`, thêm tab "Tìm bài hát" (tab đầu) vào `ImportMusic.jsx` (không phình file cũ).
- Ô query + ô link folder Drive (tùy chọn, parse folderID từ link) + toggle "Chỉ lossless".
- Kết quả: badge nguồn (Archive/Drive), badge chất lượng (VD "FLAC 24bit", "MP3 320", kèm size), nút ▶/⏸ preview (một `<audio>` element dùng chung — play bài mới thì dừng bài cũ), nút "Tải về" gọi import như flow hiện tại (toast + hiện trong lịch sử import).
- Lỗi từng nguồn hiển thị dạng warning nhỏ, không chặn kết quả nguồn còn lại.

## Error handling

- Timeout mỗi nguồn ≤ 8s; context cancel khi user rời tab.
- Drive chưa cấu hình key / folder không public → warning rõ ràng bằng tiếng Việt.
- Preview 403/404 → toast lỗi, không crash player.

## Testing

- Go: unit test scoring (bảng format → score), aggregation với httptest mock (Archive + Drive), case một nguồn lỗi, case losslessOnly.
- Chạy: `go test ./core/... ./server/nativeapi/...` với build tags chuẩn; `npm run build` UI.

## Không làm (YAGNI)

- Không crawl/index trước; không đệ quy subfolder Drive; không tích hợp player chính; không thêm nguồn trả phí (Qobuz/Deezer chỉ cho preview 30s, không tải được hợp pháp).
