# Plan 02 — Admin Delete: xoá album/bài hát từ UI, tích hợp Quality Upgrader

> Tạo 2026-07-07 từ spec `docs/superpowers/specs/2026-07-07-admin-delete-design.md` (commit 5dfde60a). Mỗi phase tự chứa đủ tham chiếu để execute trong context mới (`/claude-mem:do`).
> Nhánh làm việc: `feat/admin-delete` tạo từ `feat/quality-upgrader` (worktree `.claude/worktrees/admin-delete`). KHÔNG đụng branch khác.

## Mục tiêu

Admin xoá **vĩnh viễn** bài hát/album từ web UI (file trên disk + DB record), tích hợp hai chiều với Quality Upgrader: gợi ý nâng cấp thay vì xoá, xoá từ trang Upgrade, chặn xoá khi đang upgrade, cascade + sweep dọn sạch.

## Allowed APIs (đã xác minh trong worktree — CHỈ dùng những API này)

| API/Struct | Vị trí (worktree) | Ghi chú |
|---|---|---|
| `Maintenance` interface + `deleteMissing(ctx, ids)` | `core/maintenance.go:17,44` | Pipeline: DeleteMissing trong tx → `ds.GC` → `refreshStatsAsync`. Constructor `NewMaintenance(ds)` wired tại `cmd/wire_gen.go:86` — KHÔNG đổi signature |
| `getAffectedAlbumIDs` filter `missing=true` | `core/maintenance.go:164-171` | Lý do phải `MarkMissing(true)` TRƯỚC khi gọi `deleteMissing` |
| `MediaFileRepository.MarkMissing(missing bool, mfs ...*model.MediaFile)` | `persistence/mediafile_repository.go:344` | |
| `MediaFileRepository.DeleteMissing(ids []string)` | `persistence/mediafile_repository.go:331` | Admin check sẵn bên trong (loggedUser) |
| `MediaFile.AbsolutePath()` | `model/mediafile.go:179` | Đường dẫn tuyệt đối để `os.Remove` |
| `ds.UpgradeCandidate(ctx)` | `model/datastore.go:43` | Repo: `Put/Get/GetAll(QueryOptions)/Delete/CountAll/Exists` (`model/upgrade_candidate.go:38-47`) |
| Status candidate | `model/upgrade_candidate.go` | `pending, approved, downloading, needs_review, rejected, replaced, failed` |
| FK cascade | `db/migrations/20260706120000_create_upgrade_candidate_table.go` | `media_file_id → media_file(id) ON DELETE CASCADE` |
| `Upgrader` interface | `core/upgrader.go:63-100` | `StartScan(ctx, libraryID int, mediaFileIDs []string)`, … |
| `sweepStaging(ctx)` (unexported) | `core/upgrader_apply.go:305` | Phase 1 export qua method interface mới `SweepStaging(ctx)` |
| Router fields `maintenance core.Maintenance`, `upgrader core.Upgrader` | `server/nativeapi/native_api.go:37-50` | Wiring ĐÃ CÓ — không sửa wire |
| Admin group + `adminOnlyMiddleware` | `server/nativeapi/native_api.go:89-96,270` | Upgrade routes mount tại `:96` (`addUpgradeRoute`) — pattern chuẩn cho route mới |
| Upgrade API handler pattern + error mapping | `server/nativeapi/upgrade.go` | 404/409/403/400 mapping sẵn — copy pattern |
| `httpClient` UI | `ui/src/dataProvider` (import như `UpgradeQuality.jsx:32`) | `httpClient('/api/...', {method}).then(({json}) => …)` |
| Context menu options pattern `{enabled, needData, label, action}` | `ui/src/common/ContextMenus.jsx:73-136`, `ui/src/common/SongContextMenu.jsx:74-171` | |
| Dialog Redux pattern | `ui/src/dialogs/ExpandInfoDialog.jsx:14-56`, `ui/src/actions/dialogs.js` | Action constants + creators + reducer + đăng ký vào container dialogs |
| Bulk actions Song list | `ui/src/song/SongList.jsx:213` `bulkActionButtons={<SongBulkActions />}` | Thêm nút delete admin-only VÀO component có sẵn |
| Album list | `ui/src/album/AlbumList.jsx:239` `bulkActionButtons={false}` | Giữ nguyên — album xoá qua context menu |
| Admin check UI | `usePermissions()` / `localStorage 'role'` (xem `ui/src/album/AlbumList.jsx:52`, `authProvider.js:23`) | |
| Trang Upgrade | `ui/src/upgrade/UpgradeQuality.jsx` (row actions ~dòng 600-648) | Thêm nút "Xoá bài gốc" cạnh approve/reject |
| i18n | `ui/src/i18n/en.json` (`resources.song.actions.*`, `resources.album.actions.*`, `upgrade.*` từ :726) | File VI: tìm trong `resources/i18n/` (server-side) — upgrader đã có EN-VI parity, PHẢI cập nhật cả hai |

## Anti-patterns (KHÔNG được làm)

- **Không thêm Delete method mới vào persistence layer** — tái dùng `MarkMissing` + `deleteMissing` pipeline (spec đã chốt lý do).
- **Không đổi signature `NewMaintenance`/`New` (nativeapi)** — wiring wire_gen.go giữ nguyên; conflict-check dùng `ds.UpgradeCandidate`, sweep gọi từ handler qua Router.upgrader.
- **Không xoá folder, ảnh cover, backup của upgrader** — chỉ `os.Remove` file audio của track bị xoá.
- **Không nhận path từ client** — mọi path lấy từ DB qua id.
- **Route mới PHẢI nằm trong admin group** (`native_api.go:89-96`). KHÔNG dựa vào UI ẩn nút làm security.
- **Chi router: KHÔNG giả định** `r.Delete("/song/{id}")` sống chung được với `r.Route("/song", …)` (mount catchall) — PHẢI viết test route hoặc thử compile+run trước; nếu chi panic → fallback đăng ký `DELETE /api/deletion/song`, `DELETE /api/deletion/album/{id}` trong admin group và cập nhật spec + UI path tương ứng.
- **Không invent API upgrader** — chỉ `StartScan` cho "nâng cấp thay vì xoá"; không gọi internal scan/queue functions.
- **UI không gọi dataProvider.delete cho song/album** (resource không persistable) — dùng `httpClient` trực tiếp như trang Upgrade.
- **Partial batch delete bị conflict → fail cả batch TRƯỚC khi xoá bất kỳ file nào** (check conflict trước, remove file sau).
- i18n: mọi key mới có ở **cả EN lẫn VI**; không hardcode string trong JSX.

## Phase 1 — Backend core (Maintenance + Upgrader export)

Trong worktree `.claude/worktrees/admin-delete`:

1. `core/maintenance.go`:
   - Interface thêm `DeleteMediaFiles(ctx, ids []string) error`, `DeleteAlbum(ctx, albumID string) error`.
   - Sentinel error `ErrUpgradeInProgress` (var, wrap được) — trả khi tồn tại candidate `approved`/`downloading` trỏ tới id sắp xoá (query `ds.UpgradeCandidate(ctx).GetAll` filter `squirrel.And{Eq{"media_file_id": ids}, Eq{"status": []{"approved","downloading"}}}`; nhớ CountAll đủ dùng). Check TRƯỚC khi đụng file; lỗi kèm danh sách title/id bài bị chặn.
   - `deleteFiles(ctx, mfs)` flow: os.Remove(AbsolutePath) từng file → `IsNotExist` coi như OK → lỗi khác: giữ lại, gom; các file OK: `MarkMissing(true, …)` rồi `deleteMissing(ctx, okIDs)`; cuối cùng trả joined error nếu có file fail (kèm đếm removed/failed).
   - `DeleteAlbum`: fetch `ds.MediaFile(ctx).GetAll(Eq{"album_id": id})` → cùng flow. Album không track → no-op OK.
2. `core/upgrader.go`: interface thêm `SweepStaging(ctx context.Context)`; implement gọi `u.sweepStaging(ctx)`. Mọi mock/stub Upgrader trong test hiện có phải thêm method này.
3. Tests `core/maintenance_delete_test.go` (ginkgo — theo convention test file cạnh): temp dir + file thật; cases: xoá 1 bài (file mất, row mất qua pipeline, không lỗi), file not-exist (idempotent OK), file remove lỗi permission (row còn, error trả, các file khác trong batch vẫn xoá), DeleteAlbum (mọi track + no-op album rỗng), conflict candidate approved/downloading → ErrUpgradeInProgress và KHÔNG file nào bị remove, candidate pending/needs_review → xoá bình thường. Dùng tests.MockDataStore nếu có mock UpgradeCandidate repo; nếu mock thiếu, bổ sung vào `tests/` theo pattern mock repo khác.

Verify checklist Phase 1: `go build ./...`, `make test PKG=./core/...` xanh; grep không còn TODO; không sửa file ngoài core/ tests/ (trừ mock).

## Phase 2 — API routes

1. Thử nghiệm chi trước (xem Anti-patterns): quyết định path chính `DELETE /api/song/{id}`, `DELETE /api/song?id=a&id=b`, `DELETE /api/album/{id}` hay fallback `/api/deletion/...`. Ghi quyết định vào cuối plan này (mục Adjustments).
2. `server/nativeapi/deletion.go` (mới): handler theo pattern `upgrade.go` — parse id từ chi URLParam hoặc query `id` (nhiều), gọi `maintenance.DeleteMediaFiles`/`DeleteAlbum`; mapping: `ErrUpgradeInProgress` → 409 + body message liệt kê bài; not found → vẫn 200 (idempotent, spec); lỗi file → 500 kèm message; thành công → `200 {"ids": [...]}`. Sau thành công: `go upgrader.SweepStaging(ctx-detached)` best-effort (dùng `request.AddValues(context.Background(), ctx)` pattern như `refreshStatsAsync` `core/maintenance.go:200`).
3. Đăng ký trong admin group `native_api.go` cạnh `addUpgradeRoute(r)` (dòng ~96).
4. Tests `server/nativeapi/deletion_test.go`: 403 non-admin (gọi route không qua admin ctx), 200 + ids admin, batch query param, 409 conflict (mock maintenance trả ErrUpgradeInProgress), album route. Theo pattern test upgrade handlers hiện có.

Verify checklist Phase 2: `go build ./...`, `make test PKG=./server/nativeapi/...` xanh; route nằm TRONG admin group (đọc lại diff); curl thử nếu tiện (không bắt buộc).

## Phase 3 — UI nền tảng (dialog + context menu + bulk)

1. `ui/src/actions/dialogs.js`: `DELETE_MEDIA_OPEN/CLOSE` + creators `openDeleteMediaDialog({type: 'song'|'album'|'songs', records})`, reducer tương ứng (theo pattern ExpandInfo), đăng ký dialog vào container dialogs chung (tìm nơi ExpandInfoDialog được render, thêm cạnh đó).
2. `ui/src/dialogs/DeleteMediaDialog.jsx` (mới): Material-UI Dialog; nội dung: tên bài / tên album + số bài (`songCount`), chất lượng hiện tại (`suffix` + `bitRate`, album: đếm bài lossy theo danh sách suffix lossless — hardcode list FE khớp `core/upgrader_match.go` lossless set); cảnh báo vĩnh viễn; nút Hủy / **Xoá vĩnh viễn** (màu error). Xác nhận → `httpClient(DELETE path đã chốt Phase 2)` → notify success (translate) + `refresh()` (ra-core `useRefresh`) + đóng; lỗi 409 → notify message riêng "đang được nâng cấp"; lỗi khác → notify error.
   - Nút phụ "Tìm bản chất lượng cao hơn": chỉ render khi config upgrade bật (kiểm tra cách UpgradeQuality/menu Upgrade biết feature bật — dùng cùng cơ chế) VÀ có ≥1 bài lossy → `httpClient('/api/upgrade/scan', {method:'POST', body: JSON.stringify({mediaFileIds})})` → notify "đã đưa vào quét nâng cấp" + đóng, KHÔNG xoá.
3. Context menu: `ContextMenus.jsx` (album) + `SongContextMenu.jsx` thêm option `delete` cuối danh sách, chỉ khi admin (`localStorage role === 'admin'` theo pattern có sẵn trong codebase UI) → dispatch `openDeleteMediaDialog`.
4. Bulk: trong `SongBulkActions` (tìm component từ `SongList.jsx:213`) thêm nút xoá admin-only → mở cùng dialog với `type:'songs'`, records = selectedIds (fetch record tối thiểu: chỉ cần ids + count; dialog hiện "N bài đã chọn"); sau xoá `unselectAll('song')`.
5. i18n: en.json keys mới dưới `resources.song.actions.delete`, `resources.album.actions.delete`, và block `deleteMedia.*` (title/messages/buttons/notify); file VI (tìm `resources/i18n/vi.json` — upgrader đã thêm; nếu tên khác, grep key `upgrade.pageTitle` trong resources/i18n) cập nhật đủ parity.

Verify checklist Phase 3: `make buildjs` hoặc `cd ui && npm run build` (đúng lệnh trong Makefile:157) pass; `make test-js` pass nếu có test liên quan; menu chỉ hiện admin (viết 1 test jest theo pattern test contextmenu có sẵn nếu tồn tại; nếu không có pattern, skip test — KHÔNG invent hạ tầng test mới); `make test-i18n` pass.

## Phase 4 — UI tích hợp trang Upgrade

1. `ui/src/upgrade/UpgradeQuality.jsx`: nút "Xoá bài gốc" (icon Delete, màu error) trên mỗi dòng candidate ở tab Pending và ở History khi status `failed`/`rejected` → mở `DeleteMediaDialog` với `type:'song'`, record = current track info (đã có `currentTitle/currentFormat/currentBitRate` trong DTO; cần mediaFileId — DTO đã có, xác minh field name trong `server/nativeapi/upgrade.go:40-65`). Sau xoá thành công → refetch candidates list (dòng biến mất nhờ cascade).
2. Ẩn nút "Tìm bản chất lượng cao hơn" trong dialog khi mở TỪ trang Upgrade (đang ở context upgrade rồi — prop `hideUpgradeAction`).
3. i18n: `upgrade.actions.deleteOriginal` EN+VI.

Verify checklist Phase 4: build UI pass; mở trang Upgrade còn hoạt động (test js nếu có); i18n parity.

## Phase 5 — Verification tổng

1. `make test` (toàn bộ Go) + `make test-js` + `make test-i18n` + `make lint` (golangci) — tất cả xanh.
2. Review diff toàn branch: đúng scope spec, không file lạc, route trong admin group, không path từ client.
3. Cập nhật spec (`docs/superpowers/specs/2026-07-07-admin-delete-design.md` — copy vào worktree nếu chưa có) mục Adjustments nếu có sai khác (VD fallback path `/api/deletion/...`).
4. Commit cuối + push `feat/admin-delete`.

## Adjustments (ghi trong lúc execute)

- (trống — điền khi có quyết định sai khác spec)
