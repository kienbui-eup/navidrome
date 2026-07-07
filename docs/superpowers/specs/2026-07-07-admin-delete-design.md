# Admin Delete — Xoá album/bài hát từ giao diện (2026-07-07)

## Mục tiêu

Cho phép **admin** xoá album hoặc bài hát trực tiếp từ web UI. Xoá là **vĩnh viễn**: remove file audio trên disk + xoá record DB (đã chốt với user, không làm trash/undo).

## Phạm vi

- Đối tượng xoá: **bài hát** (một hoặc nhiều) và **album** (toàn bộ track).
- Điểm vào UI: context menu của album/song (mọi nơi menu xuất hiện: list, grid, detail) + **bulk delete** trên Song list (checkbox chọn nhiều bài).
- Chỉ admin thấy và gọi được; non-admin → 403.
- Không xoá: artist, playlist, folder, file ảnh cover rời — chỉ file audio thuộc track bị xoá.

## Backend

### `core/maintenance.go` (mở rộng)

Interface `Maintenance` thêm:

- `DeleteMediaFiles(ctx, ids []string) error` — xoá danh sách bài hát.
- `DeleteAlbum(ctx, albumID string) error` — resolve toàn bộ mediafile của album (`Filters: Eq{"album_id": id}`) rồi gọi chung flow với DeleteMediaFiles.

Flow chung (`deleteFiles`):

1. Fetch mediafiles theo ids; id không tồn tại → bỏ qua.
2. Với từng file: `os.Remove(mf.AbsolutePath())`.
   - Lỗi `os.IsNotExist` → coi như đã xoá (idempotent), vẫn tiếp tục.
   - Lỗi khác (permission, I/O) → giữ record DB của file đó, gom lỗi.
3. Với các file đã remove thành công (hoặc not-exist): `MarkMissing(true, ...)` rồi gọi pipeline `deleteMissing(ctx, ids)` sẵn có — được nguyên: xoá DB row trong transaction, `ds.GC` dọn orphan album/artist, refresh album/artist stats async.
4. Có lỗi ở bước 2 → trả error tổng hợp (kèm số bài xoá được / thất bại) sau khi đã dọn xong phần thành công.

Lý do chọn cách này: `getAffectedAlbumIDs` và `DeleteMissing` (repo) đều filter `missing=true`, nên mark-missing-trước cho phép tái dùng 100% pipeline, không thêm method mới vào persistence layer.

### Routes (`server/nativeapi`)

Bọc `adminOnlyMiddleware` (pattern có sẵn, native_api.go):

- `DELETE /api/song/{id}` — xoá một bài.
- `DELETE /api/song?id=a&id=b` — xoá nhiều bài (giống pattern `/missing` hiện có; react-admin `deleteMany` map vào đây qua dataProvider).
- `DELETE /api/album/{id}` — xoá cả album.

Response: `200 {"ids": [...]}` (react-admin convention) khi xoá hết; partial failure → `500` kèm message số bài lỗi. Path file lấy từ DB, không nhận từ client → không có path traversal.

## UI

- `AlbumContextMenu` (`ui/src/common/ContextMenus.jsx`) và `SongContextMenu` (`ui/src/common/SongContextMenu.jsx`): thêm mục "Xoá vĩnh viễn", chỉ render khi `permissions === 'admin'` (hook `usePermissions`), tách biệt phía cuối menu.
- **Confirm dialog** dùng chung (component mới trong `ui/src/dialogs/`): hiện tên bài / tên album + số bài, cảnh báo "xoá file trên ổ đĩa, không khôi phục được". Xác nhận → gọi API, thành công → notify + `refresh()`; lỗi → notify error kèm chi tiết.
- **Bulk delete Song list** (`ui/src/song/SongList.jsx`): admin → `bulkActionButtons` = nút xoá (mở cùng confirm dialog, hiện số bài đã chọn) → `DELETE /api/song?id=...`; non-admin giữ nguyên hành vi hiện tại (không checkbox).
- i18n: đủ EN + VI (label menu, tiêu đề/nội dung dialog, notify thành công/lỗi), giữ parity như các feature trước.

## Error handling

- Non-admin gọi API → 403 (middleware); UI không hiện menu nên chỉ là defense-in-depth.
- File đang bị lock/permission denied → track đó giữ nguyên trong DB, notify liệt kê số bài không xoá được; user có thể thử lại.
- Scan đang chạy song song: không chặn. Trường hợp xấu nhất file bị xoá giữa scan → scan sau đánh dấu missing rồi GC dọn — không hỏng dữ liệu.
- Bài đang phát ở client khác: stream đang mở trên file descriptor cũ vẫn chạy hết (POSIX), lần play sau trả 404 — chấp nhận.

## Testing

- `core/maintenance_test.go`: temp dir + file thật — xoá 1 bài trong album (file mất, row mất, album stats refresh đúng), xoá cả album (orphan album bị GC), file not-exist (idempotent), file remove lỗi (row còn nguyên, error trả về).
- `server/nativeapi`: 403 với user thường, 200 + ids với admin, batch qua query param.
- UI: test menu item chỉ hiện với admin (pattern test context menu sẵn có).

## Giới hạn chấp nhận

- Không xoá folder rỗng / ảnh cover rời sau khi xoá album (tránh rủi ro layout flat nhiều album chung folder). Scanner tự bỏ qua folder không còn audio.
- Không có undo. Đã cảnh báo rõ trong confirm dialog.
