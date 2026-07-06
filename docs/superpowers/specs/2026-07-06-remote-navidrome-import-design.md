# Import từ Navidrome server khác — Design (2026-07-06)

## Mục tiêu

Thêm nguồn import thứ 3 vào màn hình Import: **Navidrome/Subsonic server khác**. Admin lưu danh sách server nguồn (URL + user/pass), tìm kiếm trên server nguồn và import theo **từng bài / album / ca sĩ**. File tải về là **file gốc** (không transcode), đi qua job flow sẵn có (dedup, history, trigger scan).

## Quyết định đã chốt

- Giao tiếp bằng **Subsonic API** (`/rest/*`): `ping`, `search3`, `getArtist`, `getAlbum`, `download`. Chạy được với mọi server Subsonic-compatible, không phải quản lý JWT như native API.
- Credentials nhập trong UI, lưu server-side — **danh sách nhiều server**, JSON trong bảng `property` (key `importer.remoteServers`).
- Auth Subsonic dạng token: `u=<user>&t=md5(pass+salt)&s=<salt>` , salt random mỗi request; `v=1.16.1`, `c=navidrome-import`, `f=json`.

## Backend

### `core/importer_remote.go` (mới)

- `RemoteServer{ID, Name, URL, Username, Password string}` — CRUD trên property `importer.remoteServers` (JSON array). ID = uuid khi tạo.
- Subsonic client nội bộ (không thêm dependency):
  - `RemotePing(ctx, server)` — test kết nối/credentials.
  - `RemoteSearch(ctx, serverID, q)` → `{Songs, Albums, Artists}` (search3, mỗi loại ≤ 20).
  - `RemoteArtist(ctx, serverID, artistID)` → danh sách album (getArtist).
  - `RemoteAlbum(ctx, serverID, albumID)` → danh sách bài (getAlbum).
  - Field mỗi bài: id, title, artist, album, suffix (ext), size, bitRate, duration.
- Expand + import: `StartRemoteImport(ctx, serverID, kind song|album|artist, id, libraryID)`:
  - song → 1 item; album → getAlbum → N items; artist → getArtist → từng album → getAlbum → M items.
  - Mỗi item thành `ImportJobItem` kind `remote` `{ServerID, SongID, Name}` → `StartImportJob` sẵn có.
  - `runJob` xử lý kind `remote`: GET `/rest/download?id=<songID>` stream qua `persist()` hiện có (hưởng dedup checksum, history, uniqueDest, trigger scan, cancel).
  - Tên file: `safeAudioFilename("<artist> - <title>.<suffix>")`, lưu flat trong import dir như flow hiện tại (scanner đọc tags, không cần cấu trúc thư mục).

### Routes (`server/nativeapi/import.go`, dưới `/import/remote/`, admin-only như các route import khác)

- `GET/POST /servers`, `PUT/DELETE /servers/{id}` — CRUD server nguồn. GET **không trả password** (masked). PUT với password rỗng = giữ password cũ (UI không bao giờ có password để gửi lại).
- `POST /servers/{id}/test` — ping.
- `GET /search?server=&q=` — kết quả 3 nhóm.
- `GET /artist?server=&id=`, `GET /album?server=&id=` — drill-down trước khi import.
- `POST /import` `{serverID, type, id, libraryID}` → trả `{jobID, count}`; theo dõi bằng `/import/job/{id}` sẵn có.
- `GET /preview?server=&id=` — proxy stream từ `/rest/stream` server nguồn (passthrough `Range`/`Content-Type`), vì auth params phải nằm server-side.

### Security

- Toàn bộ routes admin-only.
- URL server nguồn **cho phép IP LAN/private** (khác `validatePublicURL` của import URL công khai) — đây là địa chỉ admin chủ động nhập, không phải input của user thường. Chỉ chấp nhận scheme http/https.
- Password lưu plaintext trong bảng property phase này (chỉ admin đọc được qua DB; API không bao giờ trả password). Mã hóa bằng `ND_PASSWORDENCRYPTIONKEY` để backlog.

## UI

- Tab mới "Server khác" trong `ImportMusic.jsx`; toàn bộ logic trong component riêng `ui/src/import/RemoteImport.jsx` (không phình file cũ).
- Quản lý server: dropdown chọn server + dialog Thêm/Sửa/Xóa (name, URL, user, pass) + nút "Kiểm tra kết nối" (hiện OK/lỗi ngay trong dialog).
- Tìm kiếm: 1 ô query → 3 nhóm kết quả **Bài hát / Album / Ca sĩ**.
  - Bài hát: badge format/bitrate + size, nút ▶ preview (một `<audio>` element dùng chung — play bài mới dừng bài cũ, pattern như SongSearch), nút "Import".
  - Album: click mở danh sách bài (drill-down), nút "Import" cả album.
  - Ca sĩ: click mở danh sách album; nút "Import" toàn bộ — confirm hiển thị số album trước khi chạy.
- Sau khi import: toast + jobID, tiến độ hiện trong phần job/history sẵn có của tab Import.
- Copy tiếng Việt, không emoji.

## Error handling

- Subsonic error 40 (sai user/pass) → "Sai tên đăng nhập hoặc mật khẩu"; lỗi mạng/timeout → message rõ kèm tên server.
- Timeout mỗi request API 15s; download theo job flow (đã có cancel).
- Lỗi từng bài trong job ghi per-item như hiện tại, không chặn các bài khác.
- Server bị xóa khi job đang chạy: job giữ credentials đã resolve lúc start, chạy tiếp bình thường.

## Testing

- Go: httptest fake Subsonic server — verify token auth (t=md5(pass+salt)), search3/getArtist/getAlbum parse, expand đúng số item cho song/album/artist, download stream về persist, error 40. Handler test cho CRUD servers (password không lộ qua GET).
- UI: eslint + `npm run build`.
- Chạy: `go test ./core/... ./server/nativeapi/...`.

## Không làm (YAGNI)

- Không sync 2 chiều, không scheduled sync định kỳ.
- Không import playlist/rating/play count từ server nguồn.
- Không transcode (luôn lấy file gốc qua `download`).
- Không mã hóa password trong DB phase này (backlog).
- Không phân trang kết quả search (search3 giới hạn 20/loại là đủ cho use case import).
