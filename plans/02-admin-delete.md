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

- **Phase 2 — Chi routing decision (2026-07-07): dùng primary spec paths, KHÔNG cần fallback `/api/deletion/...`.**
  - Paths đăng ký (trong admin group, `addDeletionRoute`): `DELETE /api/song/{id}`, `DELETE /api/song?id=a&id=b`, `DELETE /api/album/{id}`.
  - Bằng chứng: probe test throwaway với chi v5.3.0 (đúng version trong go.mod) tái tạo shape của `native_api.go` — `r.Route("/song", …)` mount (như `api.R`) + sibling `r.Get("/song/{id}/playlists")` + admin group `r.With(adminMW).Group` đăng ký `Delete("/song/{id}")`, `Delete("/song")`, `Delete("/album/{id}")`. Kết quả 10/10 case PASS: không panic khi đăng ký; `GET /song`, `GET /song/{id}`, `GET /album/{id}`, `GET /song/{id}/playlists` vẫn route vào mount (chi tree backtrack từ param node sang catchall của mount khi method không khớp); cả 3 DELETE đều reachable và bị `adminMW` chặn 403 với non-admin (inline middleware của `With().Group()` được chain vào endpoint handler). Probe đã xoá sau khi chốt (theo hard rule); coexistence được guard lâu dài bằng specs "coexistence with the /song and /album resource mounts" trong `server/nativeapi/deletion_test.go` chạy trên Router thật.
  - Hệ quả behavioral: `DELETE /api/song/{id}` với user thường giờ trả **403** (trước là 405 vì route không tồn tại) — test cũ trong `native_api_song_test.go` ("Song endpoints are read-only") đã cập nhật 405→403.
  - Response body: thống nhất `200 {"ids": [...]}` cho MỌI case thành công (kể cả 1 id — khác `writeDeleteManyResponse` của `/missing` vốn trả `{"id":...}` cho single); lỗi trả JSON `{"error": "<msg>"}` (409 conflict / 500 aggregate). UI Phase 3 parse theo format này.
  - `?id=` rỗng bị lọc bỏ; không còn id nào → 400. `fakeUpgrader.sweepStagingCalls` trong `upgrade_test.go` đổi sang `atomic.Int32` (sweep giờ chạy trong goroutine từ deletion handler, test đọc qua Eventually).

- **Phase 3 — UI foundation (2026-07-07):**
  - **Dialog KHÔNG render cạnh `ExpandInfoDialog`.** `ExpandInfoDialog` thực ra được mount cục bộ ở cuối mỗi List component (`SongList.jsx`, `AlbumList.jsx`, `AlbumSongs.jsx`, `PlaylistSongs.jsx`, `DesktopArtistDetails.jsx`), không có 1 chỗ chung. `DeleteMediaDialog` cần mở được từ context menu (album/song, nhiều nơi) + bulk actions + (Phase 4) trang Upgrade, giống hệt usage pattern của `ShareDialog`/`DownloadMenuDialog`/`AddToPlaylistDialog` (dispatch từ `ContextMenus.jsx`/`SongContextMenu.jsx`, render 1 lần global). Đã đăng ký `<DeleteMediaDialog />` trong `ui/src/dialogs/Dialogs.jsx` (render 1 lần trong `AppBar.jsx` qua `<Dialogs />`), theo đúng pattern của 4 dialog kia — KHÔNG theo literal instruction "register alongside ExpandInfoDialog".
  - **Không có config flag "upgrade enabled" ở client.** Đã grep `UpgradeMenu.jsx`, `AppBar.jsx`, `routes.jsx`, `config.js`, `server/serve_index.go` (nơi build `window.__APP_CONFIG__`): `conf.Server.Upgrade.Enabled` (server-side kill switch, xem `core/upgrader.go:186`, `server/nativeapi/upgrade.go:223`) KHÔNG được truyền vào `__APP_CONFIG__`. UI hiện `UpgradeMenu` chỉ dựa vào `permissions === 'admin'` (`AppBar.jsx:135`, comment trong `UpgradeMenu.jsx:12-13`). Vậy "same mechanism" mà spec yêu cầu = chính điều kiện admin đã gate toàn bộ dialog. `DeleteMediaDialog` vẫn giữ 1 biến `upgradeUiAvailable = !hideUpgradeAction && permissions === 'admin'` tường minh (không phải no-op — dùng cho `hideUpgradeAction` ở Phase 4, và để component tự đúng nếu sau này bị dùng ở chỗ không admin-gated), nhưng không có flag riêng nào để check thêm.
  - **Album/songs mode: không lọc lossy phía client, dựa vào server-side scan.** Album record (`model/album.go`) không có field suffix/format list; `record.ids` ở mode `'songs'` cũng không mang thông tin định dạng. Nút "Tìm bản chất lượng cao hơn" hiện LUÔN xuất hiện cho mode `album`/`songs` khi đủ điều kiện admin (không check lossy) — chỉ mode `'song'` mới lọc theo `record.suffix` so với `LOSSLESS_SUFFIXES` (copy chính xác từ `losslessExtensions` trong `core/upgrader_match.go`: flac, alac, wav, aiff, aif, ape, shn, dsf, dff, wv, wvp, tak, tta). `POST /api/upgrade/scan` phía server tự lọc ứng viên hợp lệ, nên bấm nút với album toàn-lossless chỉ đơn giản là quét ra 0 kết quả — không sai, chỉ hơi thừa 1 request.
  - **Error message parsing:** `ra-core`'s `fetchJson` set `HttpError.message` từ `json.message` (không tồn tại trong response của mình) → fallback về `statusText` (vd "Conflict"). Payload thật nằm ở `HttpError.body.error` (theo response `{"error": "<msg>"}` đã chốt Phase 2). `DeleteMediaDialog` đọc `e.body.error` chứ không phải `e.message` — verify bằng test `shows the conflict message on a 409 response`.
  - **Bulk delete ids:** batch `DELETE /api/song?id=..&id=..` dùng query param `id` lặp lại (không phải CSV) — khớp `req.Params(r).Strings("id")` ở `deletion.go`.
  - **Test i18n:** `resources/i18n/vi.json` đã thêm đủ 31 dòng key mới (626/626 key parity với `ui/src/i18n/en.json`, verify bằng `./.github/workflows/validate-translations.sh -v`). `test-i18n` chỉ fail nếu 1 file locale có key THỪA so với en.json (không fail vì thiếu key) — nhưng vẫn làm đủ VI theo yêu cầu cứng của task.
  - **Node version:** máy dev có Node v22, project yêu cầu v24 (`.nvmrc`) cho `make buildjs`/`check_node_env`. Đã `nvm install 24` để verify build — không phải thay đổi code, chỉ ghi chú môi trường.

- **Phase 4 — Upgrade page integration (2026-07-07):**
  - **`hideUpgradeAction` đã tồn tại nhưng bị gãy — sửa lại thành redux, không phải prop.** Phase 3 để lại `DeleteMediaDialog = ({ hideUpgradeAction }) => …` như 1 component prop, nhưng `Dialogs.jsx` mount `<DeleteMediaDialog />` **1 lần, toàn cục, không truyền prop nào** — nghĩa là flag này luôn `undefined`/`false` bất kể ai mở dialog, dead code. Đã chuyển `hideUpgradeAction` vào action payload/reducer đúng như mode/record đã đi (`openDeleteMediaDialog({ mode, record, hideUpgradeAction })` → `DELETE_MEDIA_OPEN` → `deleteMediaDialogReducer` lưu vào state → component đọc qua `useSelector`). Bỏ `PropTypes`/`defaultProps` cho prop này (component giờ không nhận prop nào). Các call site Phase 3 (context menu, bulk) không set field này nên default `false` — hành vi không đổi cho chúng; test `objectContaining` của chúng vẫn pass với field mới.
  - **Field mapping DTO → dialog record (mode `'song'`):** `upgradeCandidateDTO` (`server/nativeapi/upgrade.go:40-65`) → `record`: `mediaFileId → id`, `currentTitle → title`, `currentArtist → artist`, `currentFormat → suffix`, `currentBitRate → bitRate`. Không đụng logic render mode `'song'` trong `DeleteMediaDialog.jsx` — chỉ đổi field nguồn.
  - **Nút "Xoá bài gốc":** Pending tab — mọi dòng, cạnh approve/reject. History tab — chỉ dòng `status ∈ {failed, rejected}`; thêm cột "Actions" mới vào bảng History (trước đây bảng này không có cột actions).
  - **Cơ chế refetch sau khi xoá từ trang Upgrade — chọn `useVersion()` (react-admin), không phải state redux mới.** `UpgradeQuality` tự fetch qua `httpClient` (không qua react-admin store), nên `DeleteMediaDialog.handleDelete()` gọi `refresh()` xong không tự kéo lại danh sách candidate. Đã grep thấy `useVersion()` (react-admin v3, bump bởi mọi `refresh()`) đã được dùng đúng pattern này ở `AlbumList.jsx`, `AlbumSongs.jsx`, `PlaylistSongs.jsx`, `Login.jsx` trong codebase. Thêm `const version = useVersion()` vào `UpgradeQuality` và đưa `version` vào dependency array của 2 effect fetch sẵn có (`loadPending`, `loadHistory`) — KHÔNG thêm effect mới (tránh double-fetch lúc mount) mà chỉ mở rộng deps của effect đã tồn tại. Đây là lựa chọn nhỏ nhất, tái dùng cơ chế `refresh()`/`useVersion()` sẵn có của react-admin thay vì tự chế thêm field `deletedAt`/`lastDeletedIds` vào redux — dòng candidate biến mất sau xoá nhờ FK cascade + refetch, không cần biết id nào vừa bị xoá.
  - **Test:** thêm `ui/src/upgrade/UpgradeQuality.test.jsx` (chưa tồn tại trước đó) theo đúng harness pattern của `DeleteMediaDialog.test.jsx`/`SongContextMenu.test.jsx` (mock `react-redux.useDispatch`, mock `httpClient`, mock các hook `react-admin` gồm `useVersion`) — 2 case: nút xoá xuất hiện ở dòng pending và dispatch đúng action shape (kèm `hideUpgradeAction: true`); nút xoá chỉ xuất hiện ở dòng history có status rejected/failed, không xuất hiện ở dòng status replaced. Mở rộng `DeleteMediaDialog.test.jsx` thêm case `hideUpgradeAction` ẩn nút upgrade dù bài lossy.
