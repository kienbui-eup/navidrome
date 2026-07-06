# Plan 01 — Quản trị thư viện theo Album/Ca sĩ/Nhạc sĩ + Auto-playlist theo thói quen nghe

> Tạo bởi /make-plan 2026-07-06. Mỗi phase tự chứa đủ tham chiếu để execute trong context mới (dùng `/claude-mem:do`).
> Nhánh làm việc: tạo nhánh mới từ `fix/import-admin-hardening` hoặc `master` (KHÔNG commit đè lên working tree đang có thay đổi dở ở `core/importer.go`).

## Mục tiêu

1. Quản trị/duyệt thư viện theo **album, ca sĩ (artist), nhạc sĩ (composer)** — khai thác role data đã có sẵn trong data model.
2. **Auto-playlist chuyên nghiệp theo thói quen nghe**: bộ template smart playlist kiểu Spotify/Apple (Heavy Rotation, On Repeat, Tái khám phá, Mới thêm…) + job định kỳ tạo playlist cá nhân hoá cho từng user.

---

## Phase 0 — Kết quả Documentation Discovery (ĐÃ HOÀN THÀNH — chỉ đọc, không làm lại)

### Bối cảnh thị trường (đã research, nguồn trong claude-mem obs ngày 2026-07-06)

- Archetype auto-playlist chuẩn thị trường: **Discovery** (Discover Weekly), **Daily Mix**, **On Repeat** (nghe lặp gần đây), **Heavy Rotation** (Apple, ~25 bài thiên về tháng gần nhất), **Time Capsule/Rediscover** (bài cũ yêu thích lâu không nghe), **Recently Added**, **Skip-free favorites** (Apple: Skips=0 AND Plays>1).
- Tất cả trừ Discovery (cần collaborative filtering) đều dựng được bằng rule thuần trên play stats cục bộ — đúng năng lực engine `.nsp` của Navidrome.
- Discovery self-hosted khả thi qua ListenBrainz `GET /1/cf/recommendation/user/{mb_username}/recording` (experimental) — pattern đã chứng minh bởi project Explo (github.com/LumePart/Explo). Để Phase 6 (optional).
- Khoảng trống so với đối thủ (Roon/Jellyfin/Symfonium): browse theo composer (Roon có, Jellyfin/Symfonium không), template playlist dựng sẵn (không ai có), tag editor/dedupe in-app (không server nào có).

### Allowed APIs (đã xác minh trong code — CHỈ dùng những API này)

| API/Struct | Vị trí | Ghi chú |
|---|---|---|
| `RoleComposer`, `AllRoles` | `model/participants.go:17` | 13 role: artist, composer, conductor, lyricist, arranger, producer… |
| `Participants map[Role]ParticipantList` | `model/participants.go` | `Add`, `First`, `AllArtists`, `AllIDs` |
| `Album.Participants` | `model/album.go:64` | `AlbumArtist`/`AlbumArtistID` đã deprecated |
| `Artist.Stats map[Role]ArtistStats`, `Artist.Roles()` | `model/artist.go:23,70` | |
| Filter `role` cho artist list | `persistence/artist_repository.go:140,160-163` | `roleFilter` check `model.AllRoles` — **backend ĐÃ hỗ trợ `?role=composer`** |
| `criteria.Criteria{Expression, Sort, Order, Limit, LimitPercent}` | `model/criteria/criteria.go` | |
| Operators: `All/Any/Is/Gt/Lt/Before/After/Contains/InTheRange/InTheLast/NotInTheLast/InPlaylist/IsMissing/IsPresent…` | `model/criteria/operators.go` | 19 operator, JSON examples trong `operators_test.go` |
| Fields play-stats: `playcount, lastplayed, loved, dateloved, rating, dateadded` + biến thể `album*`/`artist*` | `model/criteria/fields.go:25-105` | Role fields (composer…) đăng ký động qua `AddRoles` (`fields.go:132`) |
| `Playlist.Rules *criteria.Criteria`, `IsSmartPlaylist()` | `model/playlist.go:31,35` | |
| Persist Rules dạng JSON | `persistence/playlist_repository.go:25-38` | **Tạo smart playlist bằng code được, không cần file .nsp** |
| NSP parser | `core/playlists/parse_nsp.go:83-88`, import `.nsp` tại `core/playlists/import.go:145` | |
| `Annotations{PlayCount, PlayDate, Rating, Starred…}` | `model/annotation.go:5-13` | Per-user (bảng annotation có user_id) |
| `IncPlayCount`, `SetRating`, `SetStar` | `persistence/sql_annotations.go:116,96,91` | |
| REST pattern `api.R()/RX()` | `server/nativeapi/native_api.go:102-124` | |
| Route thủ công + admin-only | `server/nativeapi/import.go:16-36`, group `adminOnlyMiddleware` tại `native_api.go:89-96` | |
| Playlist routes | `server/nativeapi/native_api.go:126-178`, `playlists.go` | |
| Scheduler `Add(crontab string, cmd func()) (int, error)` | `scheduler/scheduler.go:10-14,35-41` | robfig/cron v3, hỗ trợ `@daily`, `@weekly` |
| Config pattern | `conf/configuration.go:27-154` | struct + viper, key lowercase |
| UI resource registration | `ui/src/App.jsx:126-189` | |
| UI artist role filter ĐÃ CÓ | `ui/src/artist/ArtistList.jsx:88` (SelectInput `role`), default `albumartist` tại `:217` | |
| UI album filters | `ui/src/album/AlbumList.jsx:48-162` | |
| UI admin page pattern | `ui/src/import/ImportMusic.jsx` + route `/import` | feature import vừa build là pattern chuẩn |

### Anti-patterns (KHÔNG được làm)

- **Không có skip tracking** trong schema (không `skip_count`/`skip_date`) — không viết rule dùng skips; archetype "Skip-free" thay bằng `loved` + `playcount`.
- **Không invent field criteria**: chỉ dùng field có trong `fieldMap` (`model/criteria/fields.go`). Role fields (composer…) qua `AddRoles` — trước khi dùng operator ngoài `isMissing`/`isPresent` với role field, PHẢI đọc `fields.go:132-140` xác minh operator được hỗ trợ.
- **Không invent REST endpoint**: mọi route mới theo pattern `addXRoute` của `import.go`; đăng ký trong `native_api.go`.
- **Không có UI builder smart playlist sẵn** trong `ui/src/playlist/` — đừng giả định component criteria editor tồn tại.
- **Không dùng `Album.AlbumArtist`/`AlbumArtistID`** cho logic mới (deprecated) — dùng `Participants`.
- **Không migration DB nếu tránh được** — đánh dấu playlist auto-generated bằng convention trên field có sẵn (xem Phase 2), không thêm cột mới trừ khi bắt buộc.
- Playlist auto phải **per-user** (annotation per-user) — không tạo playlist global từ stats của admin.

---

## Phase 1 — Backend: Template service cho smart playlist archetype

### Việc cần làm

Tạo `core/playlists/templates.go`: catalog template hard-coded, mỗi template = `criteria.Criteria` dựng bằng Go structs (COPY cấu trúc expression từ JSON examples trong `model/criteria/operators_test.go` và `core/playlists/parse_nsp.go`):

| ID | Tên (vi) | Rule chính |
|---|---|---|
| `heavy_rotation` | Nghe nhiều gần đây | `All{Gt{"playcount": 3}, InTheLast{"lastplayed": 30}}`, sort `playcount` desc, limit 25 |
| `on_repeat` | Đang nghe lặp lại | `All{InTheLast{"lastplayed": 7}, Gt{"playcount": 2}}`, sort `lastplayed` desc, limit 25 |
| `rediscover` | Tái khám phá | `All{Gt{"playcount": 5}, NotInTheLast{"lastplayed": 90}}`, sort random, limit 50 |
| `recently_added` | Mới thêm vào | `InTheLast{"dateadded": 14}`, sort `dateadded` desc, limit 100 |
| `favorites_mix` | Yêu thích trộn ngẫu nhiên | `Is{"loved": true}`, sort random, limit 50 |
| `top_rated` | Đánh giá cao | `All{Gt{"rating": 3}}`, sort `rating` desc, limit 50 |

Số ngày/limit lấy từ const có tên, không magic number rải rác.

API (thêm vào `addPlaylistRoute` trong `server/nativeapi/native_api.go:126-150`, handler đặt trong `server/nativeapi/playlists.go` — theo đúng pattern handler của `server/nativeapi/import.go:39-54`):

- `GET /playlist/template` → danh sách `{id, name, description}`
- `POST /playlist/template` body `{templateId, name?}` → tạo playlist cho **user hiện tại** (owner từ request context), `Rules` = criteria của template, trả playlist đã tạo. Không cần admin.

Service tạo playlist qua `model.DataStore.Playlist(ctx).Put()` — persist Rules đã có sẵn (`persistence/playlist_repository.go:25-38`).

### Tham chiếu tài liệu

- Expression JSON/Go: `model/criteria/operators_test.go`, `model/criteria/criteria_test.go`
- NSP struct mẫu: `core/playlists/parse_nsp.go:83-88`
- Handler pattern: `server/nativeapi/import.go:39-54`
- User từ context: xem cách `playlists.go` hiện tại lấy owner

### Checklist verify

- [ ] `go build ./...` sạch
- [ ] Unit test cho templates: mỗi template marshal→unmarshal JSON round-trip qua `criteria` package không lỗi (chứng minh field/operator hợp lệ)
- [ ] `go test ./core/playlists/... ./server/nativeapi/...` pass
- [ ] `curl POST /api/playlist/template` tạo playlist, `GET /api/playlist/{id}/tracks` trả bài đúng rule (test thủ công với navidrome.db local)
- [ ] Grep guard: mọi field name trong templates.go tồn tại trong `model/criteria/fields.go`

### Anti-pattern guard

- Không dùng field/operator ngoài danh sách Phase 0. Không thêm cột DB. Không endpoint ngoài 2 route trên.

---

## Phase 2 — Backend: Job định kỳ tạo auto-playlist cá nhân hoá

### Việc cần làm

1. Config mới trong `conf/configuration.go` (theo pattern struct + viper defaults lowercase có sẵn trong file):
   - `AutoPlaylists.Enabled bool` (default false)
   - `AutoPlaylists.Schedule string` (default `@weekly`)
   - `AutoPlaylists.Templates []string` (default: heavy_rotation, rediscover, recently_added)
2. Service `core/playlists/auto.go`: với mỗi user (list qua `model.DataStore.User(ctx)`), với mỗi template bật: nếu user chưa có playlist auto tương ứng thì tạo, nếu có rồi thì bỏ qua (smart playlist tự re-evaluate khi truy cập theo `SmartPlaylistRefreshDelay` — không cần job tính lại tracks).
3. **Convention đánh dấu playlist auto** (tránh migration): `Playlist.Comment` = `"auto:" + templateID`. Tìm playlist auto = query playlist của owner có comment prefix này. Documented trong code.
4. Đăng ký job trong startup (tìm nơi các job/scheduler hiện có được wire — grep `scheduler.GetInstance\|Schedule(` trong `cmd/` và `core/`) dùng `scheduler.Add(conf.Server.AutoPlaylists.Schedule, …)` theo signature `scheduler/scheduler.go:10-14`.

### Tham chiếu tài liệu

- Scheduler: `scheduler/scheduler.go:35-41`
- Config: `conf/configuration.go` (xem `SmartPlaylistRefreshDelay`, `DefaultPlaylistPublicVisibility` làm mẫu đặt tên)
- Wiring: grep cách `SmartPlaylistRefreshDelay` được dùng để hiểu cơ chế re-evaluate

### Checklist verify

- [ ] `go build ./...` + `go test ./core/... ./conf/...` pass
- [ ] Bật config, chạy server local: sau khi job chạy, mỗi user có đúng N playlist auto, không duplicate khi job chạy lần 2 (idempotent)
- [ ] Tắt config → job không đăng ký
- [ ] `ND_AUTOPLAYLISTS_ENABLED=true` hoạt động qua env var (viper)

### Anti-pattern guard

- Job KHÔNG tự evaluate tracks (để engine sẵn có làm). Không tạo playlist cho user bằng stats của user khác. Idempotent bắt buộc.

---

## Phase 3 — UI: Duyệt thư viện theo Nhạc sĩ (composer) + filter nâng cao

### Việc cần làm

1. **Menu "Nhạc sĩ"**: backend đã hỗ trợ `?role=composer` (`persistence/artist_repository.go:160-163`) và UI đã có `SelectInput role` (`ui/src/artist/ArtistList.jsx:88`, default `albumartist` dòng 217). Thêm menu entry sidebar "Nhạc sĩ" trỏ tới artist list với `filterDefaultValues={{ role: 'composer' }}` — COPY cách menu/subMenu được khai báo trong `ui/src/App.jsx:126-189` và component menu hiện có (grep `subMenu` trong `ui/src/layout/`).
2. Tương tự cân nhắc entry "Chỉ huy/Conductor" nếu rẻ (cùng pattern, chỉ khác role) — làm nếu không phát sinh code riêng.
3. **AlbumList filter theo composer**: TRƯỚC KHI làm, xác minh album repository có filter theo participant role không (grep `role` trong `persistence/album_repository.go`). Nếu có → thêm `ReferenceInput` composer vào `ui/src/album/AlbumList.jsx:48-162` theo pattern filter `artist_id` sẵn có. Nếu KHÔNG có → ghi nhận gap, chỉ thêm filter khi backend hỗ trợ trong 1 commit riêng (mở rộng `albumFilter` theo pattern `roleFilter` của artist repo).
4. Dịch vi: labels "Nhạc sĩ", "Ca sĩ" — cập nhật file translation (kiểm tra `ui/src/i18n/` và `resources/i18n/vi.json`; bản dịch tiếng Việt nằm ở đâu thì sửa ở đó).

### Checklist verify

- [ ] `cd ui && npm run test` (vitest) pass, `npm run build` sạch
- [ ] Mở UI: menu Nhạc sĩ hiện danh sách composer (thư viện test cần file có tag composer), click composer → xem được album/bài liên quan
- [ ] Filter role trên artist list vẫn hoạt động như cũ (không regress default albumartist)

### Anti-pattern guard

- KHÔNG tạo REST resource mới cho composer — dùng artist list + role filter sẵn có. Không hardcode chuỗi tiếng Việt trong JSX — qua translation keys.

---

## Phase 4 — UI: Tạo playlist từ mẫu + tổ chức playlist

### Việc cần làm

1. `ui/src/playlist/PlaylistCreate.jsx`: thêm lựa chọn "Tạo từ mẫu" — dropdown fetch `GET /api/playlist/template` (dùng pattern gọi API custom của feature import: xem `ui/src/import/ImportMusic.jsx` — lưu ý codebase này dùng header `X-ND-Authorization`, làm đúng theo pattern fetch hiện có, không tự chế client mới).
2. Chọn mẫu → `POST /api/playlist/template` → redirect tới playlist vừa tạo.
3. `ui/src/playlist/PlaylistList.jsx`: hiển thị chip/badge cho smart playlist (`rules != null`) và badge "Tự động" cho playlist có comment prefix `auto:` — giúp user phân biệt playlist tay/thông minh/tự động.
4. Dịch vi cho toàn bộ label mới.

### Checklist verify

- [ ] `cd ui && npm run test` pass, `npm run build` sạch
- [ ] E2E tay: tạo playlist từ mẫu "Nghe nhiều gần đây" → có tracks, badge hiển thị đúng
- [ ] User thường (không admin) tạo được playlist mẫu cho chính mình

### Anti-pattern guard

- Không gọi endpoint chưa tồn tại (Phase 1 phải merge trước). Không bypass data provider/auth header pattern sẵn có.

---

## Phase 5 — Verification tổng (bắt buộc trước khi mở PR)

1. `go build ./... && go test ./...` — toàn bộ pass
2. `cd ui && npm run test && npm run build`
3. Grep guards:
   - `grep -rn "AlbumArtistID\|\.AlbumArtist\b" core/playlists/ server/nativeapi/` — code mới không dùng field deprecated
   - Mọi field trong `templates.go` xuất hiện trong `model/criteria/fields.go`
   - `grep -rn "skip" core/playlists/templates.go` — không rule nào dựa skip
4. Chạy server local với navidrome.db, đi hết flow: menu Nhạc sĩ → chọn composer → nghe bài; tạo playlist từ mẫu; bật AutoPlaylists → restart → check playlist auto per-user.
5. Đối chiếu từng endpoint mới với pattern `import.go` (đăng ký, error handling, JSON shape).

## Phase 6 (BACKLOG — không làm trong plan này)

- ListenBrainz Discover integration (pattern Explo, endpoint `GET /1/cf/recommendation/user/{user}/recording`, cần user link ListenBrainz — đã có scrobbler LB trong `core/scrobbler/`?  verify trước)
- Dedupe tool admin (mô hình beets duplicates: match MBID → fuzzy title/duration)
- Tag editor in-app (khoảng trống lớn nhất so với đối thủ, nhưng scope nặng — plan riêng)
