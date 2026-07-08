# Plan 03 — Migration giao diện Aonsoku (nhac.troly.me) vào ms.troly.me

**Ngày lập**: 2026-07-07. **Execute bằng**: `/claude-mem:do` trên nhánh mới `feat/aonsoku-player` (từ `master`).

## Mục tiêu

Nhúng toàn bộ giao diện player Aonsoku (hiện chạy container riêng tại nhac.troly.me) vào binary Navidrome fork, serve trên cùng domain **ms.troly.me** tại path `/play`. Sau cutover: `/` redirect → `/play/` (player mặc định), react-admin giữ nguyên `/app` (admin: quality upgrader, import, composer, auto-playlists), nhac.troly.me redirect vĩnh viễn về ms.troly.me, gỡ container aonsoku.

## Bối cảnh & ràng buộc

- **nhac.troly.me** = Aonsoku (fork của `github.com/victoralvesf/aonsoku` — React + Vite + TanStack Query + Tailwind/Radix), container nginx port 4544 trên VM, backend trỏ `https://ms.troly.me` qua `env-config.js` (SERVER_URL/SERVER_TYPE=navidrome/HIDE_SERVER=true).
- **Source bản Việt hóa `aonsoku-vi` ĐÃ MẤT** — chỉ còn 2 Docker image local: `aonsoku-vi:latest` (build 2026-07-06, title "Trợ lý nhạc") và `ghcr.io/victoralvesf/aonsoku:latest` (upstream, 3 tháng tuổi). Patch vi khôi phục bằng cách diff 2 image.
- **ms.troly.me** = fork Navidrome này, deploy theo `memory/deploy-espeak-vm.md` (VM GCP espeak-490004, image AR `troly-<sha>`, konlet update-container).
- Client khác đang dùng `/rest` (UAPP Android, app android/ trong repo) — KHÔNG được vỡ.
- Dev local: `/Users/kien/.navidrome-local/aonsoku/docker-compose.yml` (SERVER_URL=http://localhost:4534).

## Quyết định kiến trúc (đã chọn, kèm lý do)

1. **Embed vào binary Go tại `/play`** (không giữ container riêng, không chiếm `/app`): copy đúng pattern react-admin `ui/embed.go` + `frontendAssetsHandler`. Một binary, một image, một domain — hết cảnh 2 container + 2 subdomain.
2. **Vendor source Aonsoku vào repo** dưới thư mục `player/` (subtree, không submodule): go:embed cần file có mặt lúc build; subtree cho build tái lập được trong Docker và giữ patch vi trong git history.
3. **`env-config.js` render từ Go handler** (thay nginx envsubst): SERVER_URL rỗng → same-origin, SERVER_TYPE=navidrome, HIDE_SERVER=true. Không cần CORS, không cần template stage nginx.
4. **Root redirect cấu hình được**: thêm `conf.Server.DefaultUIPath` (default `/app` — không đổi hành vi upstream); prod đặt `ND_DEFAULTUIPATH=/play`. Rollback = đổi 1 env var.

---

## Phase 0 — Documentation Discovery (ĐÃ CHẠY 2026-07-07, kết quả tổng hợp)

### Allowed APIs / patterns (đã verify trong source, KHÔNG được bịa thêm)

| Việc | Pattern copy từ | Ghi chú |
|---|---|---|
| Embed static FS | `ui/embed.go:1-15` (`//go:embed build/*` + `fs.Sub`) | Luôn compile-in, không build tag |
| Handler SPA + index | `server/server.go:235-241` (`frontendAssetsHandler`) | `Handle("/", Index)` + `Handle("/*", StripPrefix+FileServer)` |
| Mount router | `server/server.go:62` qua `MountRouter` (`server.go:51-57`) | Tự prefix `conf.Server.BasePath` — KHÔNG tự nối BasePath |
| Serve static ở subpath | `server/public/public.go:29-36,56` | `path.Join(BasePath, URLPath...)` |
| Root redirector | `server/server.go:224-233` (`mountRootRedirector`) | `/` → `appRoot+"/"` 302; chi ưu tiên subtree mount cụ thể hơn catch-all |
| Inject config vào index.html | `server/serve_index.go:32-116` + `ui/index.html:34-39` (`window.__APP_CONFIG__`) | Dùng cho env-config.js handler |
| Path const | `consts/consts.go:42` (`URLPathUI = "/app"`) | Thêm `URLPathPlayer = "/play"` cạnh đó |
| Mount điểm khác | `cmd/root.go:117-144` (`/api`, `/rest`, `/share`…) | |
| Config option mới | `conf/configuration.go:40-41` (BaseURL/BasePath) + viper default | Thêm `DefaultUIPath` |
| Auth Subsonic cho SPA | `server/subsonic/middlewares.go:100-156,175-198` — `u` + `t=md5(pass+salt)` + `s` + `c` + `v` + `f=json` | `/auth/login` đã trả `subsonicSalt`/`subsonicToken` (`server/auth.go:88-92`) — dùng cho SSO sau này, ngoài scope plan này |
| Build UI trong Docker | `Dockerfile:29-42` (stage `ui` node:lts-alpine npm ci → `ui-bundle`) + mount `Dockerfile:66,115` | Copy thành stage `player-ui` |
| Build UI local | `Makefile:146-165` (`buildjs`: `cd ./ui && npm run build`) | Thêm target tương tự cho `player/` |

### Facts còn PHẢI verify ở Phase 1 (recon chưa xác nhận được vì thiếu source)

- [x] **ĐÃ VERIFY (Phase 1, 2026-07-07)** Router mode: **hash router** — `createHashRouter` tại `player/src/routes/router.tsx:2,52` (react-router-dom `^6.30.3`, `package.json:100`). Kết luận: KHÔNG cần SPA fallback, KHÔNG cần `basename`; handler `/play/*` chỉ cần FileServer + index.
- [x] **ĐÃ VERIFY** Vite `base`: upstream đã đặt sẵn `base: './'` tại `player/vite.config.ts:9` — giống pattern react-admin, không phải sửa; dist/index.html ra asset path relative (`./assets/...`), serve dưới `/play/` OK.
- [x] **ĐÃ VERIFY** Locale `vi` là **do fork thêm, upstream KHÔNG có** (v0.14.0 chỉ có 19 locale). Đã khôi phục nguyên văn từ bundle image `aonsoku-vi` (465/465 keys khớp cấu trúc en.json) vào `player/src/i18n/locales/vi.json` + đăng ký `languages.ts` + `fallbackLng: 'vi'` (`src/i18n/index.ts:12`) + default `lang.store.ts:14-16`. Entry chunk build ra **byte-identical** với chunk trong image vi gốc (`index-B2dHbO6r.js`).
- [x] **ĐÃ VERIFY** `window.SERVER_URL` đọc tại `player/src/store/app.store.ts:23,46`, `src/utils/salt.ts:6-10`, `src/app/components/login/form.tsx:62` — đều module-scope. `env-config.js` được index.html load bằng **path RELATIVE** (`./env-config.js`, `player/index.html:18`) → serve dưới `/play/` không cần sửa; Go handler Phase 3 serve tại `/play/env-config.js`. Fallback same-origin thêm bằng inline script trong `index.html` (classic script, chạy trước mọi module ESM).
- [ ] Aonsoku player xử lý DSD/transcode thế nào — fork này có clamp codecProfiles ≤96kHz trong react-admin player (commit `caafa1fc`); Aonsoku không có logic đó → kiểm tra stream DSF qua Aonsoku có bị lỗi không. (Phase 4)

**Pin upstream (Phase 1)**: commit `c523f88ee70a6b761d62e24f0180eb26bd0a13d7` = tag `v0.14.0`, khớp label `org.opencontainers.image.revision` của image ghcr (created 2026-04-02). Build `player/` bằng **pnpm** (`pnpm install --frozen-lockfile --ignore-scripts && pnpm run build`) — upstream chỉ có `pnpm-lock.yaml`, Dockerfile upstream cũng dùng pnpm; đây là ngoại lệ có chủ đích của quy ước npm (quy ước npm áp cho `ui/`).

### Anti-patterns (cấm trong mọi phase)

- `http.FileServer` trần cho SPA BrowserRouter — deep-link sẽ 404; nếu Aonsoku là path-router thì handler `/*` PHẢI fallback về `index.html` khi file không tồn tại.
- Tự nối `conf.Server.BasePath` vào path đã đi qua `MountRouter` (double-prefix).
- Sửa handler `/app`, `/share`, `/rest` hiện có (chỉ THÊM mount mới + đổi target của root redirector qua config).
- Build UI bằng yarn — pipeline chuẩn của repo là **npm** (`package-lock.json`; `yarn.lock` mới thêm ở `c4f836a7` không phải build path).
- Đưa nginx/env-config template vào final image — final image chỉ có 1 binary Navidrome.
- Bịa config key kiểu `ND_AONSOKU_*` tràn lan — chỉ 1 key mới `ND_DEFAULTUIPATH`.

---

## Phase 1 — Khôi phục source Aonsoku + patch Việt hóa, vendor vào repo

**Làm gì:**
1. Clone upstream: `git clone https://github.com/victoralvesf/aonsoku /Users/kien/Project/aonsoku-upstream` — checkout tag/commit gần nhất với image `ghcr.io/victoralvesf/aonsoku:latest` (3 tháng tuổi — so `docker image inspect` created date với git log upstream).
2. Khôi phục patch vi bằng diff 2 image local:
   ```sh
   docker create --name av aonsoku-vi:latest && docker export av | tar -x -C /tmp/av usr/share/nginx/html
   docker create --name au ghcr.io/victoralvesf/aonsoku:latest && docker export au | tar -x -C /tmp/au usr/share/nginx/html
   diff -r /tmp/au/usr/share/nginx/html /tmp/av/usr/share/nginx/html
   ```
   (dùng scratchpad thay /tmp nếu chạy bằng agent). Kỳ vọng: khác `index.html` (title "Trợ lý nhạc"), có thể khác i18n bundle/branding asset. Nếu bundle JS khác nhiều → extract chuỗi vi từ `i18n-*.js` của image vi.
3. Verify các gap Phase 0: đọc source upstream — router mode (`src/routes` hoặc `main.tsx`), `vite.config.ts` (`base`?), thư mục i18n có `vi` chưa, cơ chế đọc `window.SERVER_URL` (file nào).
4. Vendor: copy source (không `.git`, không `node_modules`) vào `player/` của repo navidrome; áp patch vi (title, i18n nếu thiếu, default language vi); commit riêng "vendor aonsoku" + commit riêng "patch vi" để diff với upstream dễ.
5. Chỉnh build cho subpath + same-origin:
   - `vite.config`: `base: './'` (theo pattern `ui/vite.config.js:32`) hoặc `/play/` nếu router cần absolute.
   - Nếu BrowserRouter: set `basename` từ base path (theo cách upstream hỗ trợ, KHÔNG bịa — nếu upstream không hỗ trợ basename thì cân nhắc giữ nguyên và dựa vào SPA fallback + base absolute `/play/`).
   - `window.SERVER_URL` undefined/rỗng → fallback `window.location.origin` (patch nhỏ tại chỗ Aonsoku đọc SERVER_URL, đã xác định ở bước 3).

**Doc refs**: pattern base relative `ui/vite.config.js:32`; env-config vars từ image (`SERVER_URL, HIDE_SERVER, APP_USER, APP_PASSWORD, APP_AUTH_TYPE, SERVER_TYPE, APP_THEME, HIDE_*_SECTION`).

**Verification checklist:**
- [ ] `cd player && npm ci && npm run build` ra `player/dist/index.html` không lỗi.
- [ ] `npx vite preview` (hoặc serve dist tĩnh) + trỏ SERVER_URL vào Navidrome dev local (`http://localhost:4533`) → login + phát nhạc OK.
- [ ] Title tab = "Trợ lý nhạc"; UI hiển thị tiếng Việt.
- [ ] Ghi lại router mode + quyết định base path vào đầu file plan này (cập nhật mục Phase 0 gaps).

**Anti-pattern guards**: không sửa logic player của Aonsoku ngoài 3 điểm (title/i18n/SERVER_URL fallback); không upgrade dependency của Aonsoku trong phase này.

## Phase 2 — Build pipeline: Makefile + Dockerfile + embed

**Làm gì:**
1. `player/embed.go` — copy nguyên `ui/embed.go:1-15`, đổi `//go:embed build/*` → `//go:embed dist/*`, package `player`, export `BuildAssets()`.
2. Makefile: thêm target `buildplayer` copy pattern `Makefile:157-165` (`cd ./player && npm run build`); nối vào `build`/`buildall` cạnh `buildjs` (`Makefile:146-147`).
3. Dockerfile: thêm stage `player-ui` copy nguyên stage `ui` (`Dockerfile:29-42`, đổi WORKDIR/context sang `player/`, output `/build-player`); thêm `--mount=from=player-ui,source=/build-player,target=./player/dist,ro` cạnh 2 chỗ mount hiện có (`Dockerfile:66` và `:115`).
4. Cần file placeholder `player/dist/.gitkeep` (hoặc dùng pattern `all:` embed) để `go build` không fail khi chưa build player — kiểm tra cách `ui/build` xử lý (Makefile `buildjs` luôn chạy trước; giữ nguyên quy ước đó và thêm `player/dist` vào `.gitignore` giống `ui/build`).

**Doc refs**: `ui/embed.go:1-15`; `Makefile:146-165`; `Dockerfile:29-42,66,115`.

**Verification checklist:**
- [ ] `make buildplayer && make buildjs && go build -tags=netgo,sqlite_fts5 .` pass local.
- [ ] `docker buildx build --platform linux/amd64 --target final -t navidrome:aonsoku-test .` pass (KHÔNG push).
- [ ] Binary local: `unzip -l` không áp dụng — kiểm tra bằng chạy server dev và curl (Phase 3).

**Anti-pattern guards**: không đổi stage `ui` hiện có; npm không yarn; không thêm build tag mới.

## Phase 3 — Serve trong Go server: mount /play + SPA fallback + env-config.js + root redirect config

**Làm gì:**
1. `consts/consts.go:42`: thêm `URLPathPlayer = "/play"` cạnh `URLPathUI`.
2. `server/server.go`: thêm `playerAssetsHandler()` — copy `frontendAssetsHandler` (`server.go:235-241`) với 2 khác biệt:
   - Serve `player.BuildAssets()`; root path serve `index.html` thẳng (không cần template Index của react-admin).
   - Route `/*`: nếu file tồn tại trong FS → FileServer; nếu không và request không có extension → trả `index.html` (SPA fallback, chỉ cần khi Phase 1 xác nhận Aonsoku là path-router; nếu hash-router thì copy nguyên pattern cũ).
   - Thêm handler `GET /env-config.js` trả JS `window.SERVER_URL=""; window.SERVER_TYPE="navidrome"; window.HIDE_SERVER=true; ...` — giá trị viết cứng trừ khi Aonsoku cần thêm (theo template đã extract ở Phase 1); Content-Type `application/javascript`, no-cache.
3. Mount tại `Run` cạnh `server.go:62`: `s.MountRouter("Player", consts.URLPathPlayer, s.playerAssetsHandler())`.
4. `conf/configuration.go`: thêm `DefaultUIPath string` (viper default `consts.URLPathUI`); `server/server.go:224-233` `mountRootRedirector`: redirect target = `path.Join(conf.Server.BasePath, conf.Server.DefaultUIPath)` thay vì appRoot cứng.
5. KHÔNG đụng CORS (`server/middlewares.go:87-102` đã `*`), KHÔNG đụng `/share`, `/rest`.

**Doc refs**: toàn bộ ở bảng Phase 0; SPA fallback là code mới duy nhất — giữ ~15 dòng, test đơn vị kèm theo.

**Verification checklist:**
- [ ] `go test ./server/...` pass; thêm test: `GET /play/` → 200 + html; `GET /play/nonexistent-route` → 200 index.html (nếu path-router) hoặc 404 (nếu hash-router); `GET /play/env-config.js` → 200 JS; `GET /` → 302 về `/app/` khi config default, về `/play/` khi `ND_DEFAULTUIPATH=/play`.
- [ ] Chạy dev server + `make buildplayer`: login Aonsoku tại `http://localhost:4533/play/` same-origin (không nhập server URL), phát nhạc, chuyển trang, F5 giữa trang không 404.
- [ ] `/app` react-admin vẫn nguyên (login, quality upgrader tab, import).
- [ ] `ND_BASEURL=/music go run .` → `/music/play/` hoạt động (BasePath không double-prefix).

**Anti-pattern guards**: không hardcode `/play` trong handler (dùng const + StripPrefix theo `s.appRoot` pattern `server.go:168`); không bịa param mới cho `serveIndex`.

## Phase 4 — Kiểm thử tính năng player trên dev + vá gap DSD

**ĐÃ CHẠY 2026-07-08 (API-level, không có browser automation).** Phương pháp: đọc `player/src/api/httpClient.ts` + `player/src/service/*.ts` để lấy chính xác endpoint/param Aonsoku gọi, replay bằng curl lên dev server thật (binary build `netgo,sqlite_fts5`, `ND_DATAFOLDER`/`ND_MUSICFOLDER` ở scratchpad, `tests/fixtures` + 2 file DSD64 thật sẵn có tại `~/Music/Music/Top nhạc trẻ Lossless Update 2026/DSD Test Album/` — không cần tải 2L).

### Bảng tính năng → endpoint → kết quả

Client name Aonsoku gửi (`c=`): **`Trợ lý nhạc`** (hằng số `appName` tại `player/src/utils/appName.ts:3`). Auth mặc định: TOKEN (`u`,`t=md5(pass+salt)`,`s=salt`, salt cố định `player/src/utils/salt.ts:4` = `40n50kuPl4y3r`), `v=1.16.0`, `f=json` — dựng bởi `queryParams()`/`getUrl()` tại `player/src/api/httpClient.ts:37-70`.

| Tính năng | Endpoint (`/rest/...`) | Param chính | File nguồn Aonsoku | Kết quả replay |
|---|---|---|---|---|
| Login/ping | `ping.view`, `getOpenSubsonicExtensions.view` | u,t,s,v,c,f | `service/ping.ts`, `api/queryServerInfo.ts`, `api/pingServer.ts` | OK (status ok, openSubsonic=true, 6 extensions) |
| Home/album list | `getAlbumList2.view` | type,size,offset,fromYear,toYear,genre | `service/albums.ts:18-44` | OK (newest, random) |
| Album detail | `getAlbum.view`, `getAlbumInfo2.view` | id | `service/albums.ts:46-66` | OK |
| Artist list/detail | `getArtists.view`, `getArtist.view`, `getArtistInfo.view` | id | `service/artists.ts` | OK |
| Top songs | `getTopSongs.view` | artist (tên, không phải id) | `service/songs.ts:44-53` | OK |
| Random songs | `getRandomSongs.view` | size,genre,fromYear,toYear | `service/songs.ts:17-34` | OK |
| Get song | `getSong.view` | id | `service/songs.ts:67-76` | OK |
| Search | `search3.view` | query (Navidrome dùng `""` khi rỗng — `service/search.ts:26`),artistCount,albumCount,songCount + offsets | `service/search.ts` | OK |
| Genres | `getGenres.view`, `getSongsByGenre.view` | genre,count,offset | `service/genres.ts` | OK |
| Playlist đọc | `getPlaylists.view`, `getPlaylist.view` | id | `service/playlists.ts:11-31` | OK |
| Playlist tạo | `createPlaylist.view` (GET, query string) | name,songId[] | `service/playlists.ts:42-58` | OK (tạo + có entry) |
| Playlist sửa | `updatePlaylist.view` (GET) | playlistId,name,comment,public,songIdToAdd[],songIndexToRemove[] | `service/playlists.ts:60-96` | OK (rename, thêm bài) |
| Playlist xoá | `deletePlaylist.view` (method DELETE nhưng cùng REST path) | id | `service/playlists.ts:33-40` | OK |
| Star/unstar | `star.view`, `unstar.view` | id | `service/star.ts` | OK |
| Favorites | `getStarred2.view` | — | `service/songs.ts:36-42` | OK |
| Scrobble | `scrobble.view` | id,submission,time | `service/scrobble.ts` | OK (nowPlaying + submit) |
| Radio | `getInternetRadioStations.view`, `createInternetRadioStation.view`, `updateInternetRadioStation.view`, `deleteInternetRadioStation.view` | streamUrl,name,homepageUrl | `service/radios.ts` | OK |
| Lyrics (OpenSubsonic) | `getLyricsBySongId.view` | id | `service/lyrics.ts:54-62` | OK (extension `songLyrics` có sẵn — plugin nd-lyrics không bắt buộc để trả `ok`, nội dung rỗng vì fixture không có lyric) |
| Lyrics (fallback legacy) | `getLyrics.view` | artist,title | `service/lyrics.ts:102-108` | OK |
| Lyrics (fallback ngoài) | — | — | `service/lyrics.ts:134-204` gọi thẳng `https://lrclib.net/api/get`, không qua Navidrome | N/A — external, không phải gap của server |
| Scan status | `getScanStatus.view`, `startScan.view` | — | `service/library.ts` | OK |
| **Stream (play)** | `stream.view` | **chỉ `id` + `estimateContentLength=true`** — **KHÔNG gửi `format`, KHÔNG gửi `maxBitRate`** | `api/httpClient.ts:143-154`, gọi từ `app/components/player/player.tsx:241` (`getSongStreamUrl(song.id)`) | Xem mục DSD |
| Download | `download.view` | id,maxBitRate=0,format=raw | `api/httpClient.ts:156-162` | Không test riêng (cùng cơ chế `Router.Download`, `format=raw` tường minh → luôn raw, không phụ thuộc player) |
| Podcast | `/api/podcasts*`, `/api/episodes*` (KHÔNG phải `/rest`) | header `APP-USERNAME`/`APP-SERVER-URL`, base URL = `podcasts.serviceUrl` (mặc định rỗng, tính năng tắt mặc định) | `api/podcastClient.ts`, `service/podcasts.ts` | **Không áp dụng cho Navidrome** — xem Backlog |

### Cơ chế transcode DSD của fork (tóm tắt + file:line)

Fork có **2 đường quyết định transcode song song, không dùng chung logic client-capability**:

1. **Đường mới (OpenSubsonic extension, chỉ react-admin dùng)**: `POST /rest/getTranscodeDecision.view` (body JSON `codecProfiles`/`directPlayProfiles`/`transcodingProfiles` do `ui/src/transcode/browserProfile.js:80-95` dựng, có clamp `audioSamplerate <= 96000` cho codec `flac`) → `GET /rest/getTranscodeStream.view?transcodeParams=<jwt>`. Xử lý tại `server/subsonic/transcode.go:237-347`. Cap 96kHz áp dụng qua `applyCodecLimitations` (`core/stream/decider.go:407-434`) dựa trên `clientInfo.CodecProfiles` — **field này chỉ được set khi client tự gửi**.
2. **Đường cũ (legacy `/rest/stream`, Aonsoku + mọi Subsonic client khác dùng)**: `server/subsonic/stream.go:20-54` → `deciderService.ResolveRequest` (`core/stream/legacy_client.go:61-121`) → `buildLegacyClientInfo` (`core/stream/legacy_client.go:15-57`). Hàm này **không bao giờ set `ClientInfo.CodecProfiles`** — chỉ set `DirectPlayProfiles`/`TranscodingProfiles`/`MaxAudioBitrate` dựa trên 3 nguồn duy nhất: `format=` request param, `maxBitRate=` request param, và **`Player.MaxBitRate`** (server-side, do fix issue #5583). Vì Aonsoku không gửi `format`/`maxBitRate`, quyết định hoàn toàn phụ thuộc `Player.MaxBitRate` (và `Player.TranscodingId` qua `applyServerOverride`, `core/stream/decider.go:163-187`).
3. **Player record tự tạo mỗi login**: `core/players.go:34-78` (`Register`) — mỗi user+client+userAgent mới sinh 1 row bảng `player` (client field = đúng `c=` gửi lên, vd `Trợ lý nhạc`). Không có row nào ⇒ `TranscodingId=""`, `MaxBitRate=0` ⇒ `buildLegacyClientInfo` trả `DirectPlayProfiles: [{Protocols:[http]}]` không ràng buộc container/codec ⇒ **direct play mọi định dạng, kể cả DSD thô**.

### Kết quả stream DSF/DFF qua Aonsoku — trước/sau cấu hình

File test: DSD64 thật, `ffprobe` xác nhận `sample_rate=352800` (probe convention, chuẩn hoá về `2822400Hz/1-bit` theo `caafa1fc`), `bitRate` lưu trong DB = 5645 kbps (dsf) / 5644 kbps (dff).

- **TRƯỚC cấu hình** (player mới toanh, `MaxBitRate=0`, `TranscodingId=""`): `GET /rest/stream?id=<dsf>&estimateContentLength=true&c=Trợ lý nhạc...` → `200`, `Content-Type: audio/x-dsf`, `Content-Length` = đúng size file gốc, **byte-for-byte giống hệt file nguồn** (`cmp` xác nhận identical) → **raw DSD, browser không decode được `audio/x-dsf`**. DFF tương tự (`audio/x-dff`, không test cmp riêng nhưng cùng đường code).
- **Nguyên nhân**: không phải bug decider — decider hoạt động đúng thiết kế "không ràng buộc gì ⇒ direct play" (đây chính là cơ chế giữ bit-perfect cho UAPP). Gap là **thiếu cấu hình Player record** cho client `Trợ lý nhạc`, đúng như anti-pattern guard đã cảnh báo trước.
- **SAU khi set `Player.MaxBitRate=320`** (qua `PUT /api/player/<id>` — API react-admin dùng cho `PlayerEdit.jsx`) cho đúng row `client=Trợ lý nhạc`: replay **y hệt call trên, không đổi gì phía Aonsoku** → `200`, `Content-Type: audio/ogg`, `ffprobe` xác nhận `codec_name=opus, sample_rate=48000` (48kHz là **cứng theo codec**, `core/stream/codec.go:59-65 codecFixedOutputSampleRate("opus")=48000`, không phụ thuộc client gửi gì) — **browser phát được**. DFF cũng transcode ra `audio/ogg` opus 48kHz tương tự. Log server: `originalBitRate=5645 originalFormat=dsf ... format=opus transcoding=true`.
- **Đối chứng UAPP không bị ảnh hưởng**: replay cùng call `stream.view` với `c=UAPP` (player mới, chưa cấu hình gì) → `200`, `audio/x-dsf`, `cmp` xác nhận **byte-for-byte identical với file gốc** — bit-perfect giữ nguyên vì cấu hình chỉ áp cho đúng 1 row Player (`client=Trợ lý nhạc`), không đụng client khác.
- **Đối chứng react-admin (đường mới)**: `POST getTranscodeDecision` với `codecProfiles` clamp 96kHz (giả lập `browserProfile.js`) cho cùng file DSF → quyết định transcode FLAC, `audioSamplerate=96000` (đúng cap); `GET getTranscodeStream` với token trả về → `ffprobe` xác nhận `flac, sample_rate=96000`. Xác nhận đường react-admin hoạt động đúng thiết kế độc lập với đường legacy.

### Cấu hình đúng cho prod (từng bước)

1. Đăng nhập Aonsoku tại `/play` **ít nhất 1 lần** với tài khoản sẽ dùng thường xuyên — Navidrome tự tạo row Player `client="Trợ lý nhạc"` (không cần thao tác gì thêm, xảy ra ở lần gọi `/rest/*` đầu tiên).
2. Vào react-admin `/app` → **Player** (resource `player`) → tìm row có cột Client = `Trợ lý nhạc` (Name sẽ là `Trợ lý nhạc [<user-agent>]`) → Edit.
3. Set field **Max bit rate = 320** (hoặc bất kỳ giá trị trong `BITRATE_CHOICES`, `ui/src/consts.js:34-36`, nhỏ hơn bitrate danh nghĩa của nguồn DSD ~5644kbps) — **để trống Transcoding** (không chọn field Transcoding, giữ resettable/rỗng).
   - Tương đương API: `PUT /api/player/<id>` body `{"maxBitRate":320,"transcodingId":"",...các field khác giữ nguyên...}`.
4. KHÔNG gán `Transcoding = flac audio` trực tiếp cho player này — xem lý do ở mục "Đề xuất sửa code" (gây lỗi/response hỏng, xem dưới), dùng `MaxBitRate` để tận dụng `DefaultDownsamplingFormat=opus` (`consts/consts.go:149`) đã có cap sample-rate cứng.
5. Verify: phát 1 bài DSD từ Aonsoku (`/play`) → nghe được, không lỗi; nếu cần verify API thuần: `curl` `stream.view` với đúng `id` bài DSD, kiểm tra `Content-Type: audio/ogg` và không phải `audio/x-dsf`.
6. Áp dụng y hệt (không cần thêm) cho các client Subsonic thô khác nếu sau này thêm — mỗi client là 1 row Player riêng, không ảnh hưởng lẫn nhau.

### Đề xuất sửa code (CHƯA áp dụng — cần orchestrator duyệt trước khi đổi file)

Trong lúc test đường "gán `TranscodingId=flac` trực tiếp cho player" (một cách cấu hình hợp lệ khác nhưng KHÔNG dùng cho prod vì 2 lý do dưới), phát hiện 2 gap có thật ở tầng `core/stream`, độc lập với Aonsoku:

1. **`codecMaxSampleRate` (`core/stream/codec.go:69-77`) thiếu case `"flac"`.** Chỉ có `mp3→48000`, `aac→96000`. Khi 1 player được gán thẳng `TranscodingId=flac` (không qua `MaxBitRate`), `applyServerOverride` (`core/stream/decider.go:163-187`) tạo `ClientInfo` không có `CodecProfiles` → không cap nào áp dụng → DSD transcode ra FLAC **352.8kHz/24bit** (verify bằng `ffprobe`, log server: `sampleRate=352800`) — đúng loại hi-res mà chính comment của fork (`ui/src/transcode/browserProfile.js:26-31`) nói browser không chơi được ổn định. Đề xuất tối thiểu: thêm `case "flac": return 96000` vào `codecMaxSampleRate`, áp dụng đồng nhất cho cả đường legacy lẫn đường mới, không đụng logic direct-play (chỉ áp khi đã quyết định transcode).
2. **`computeBitrate` (`core/stream/decider.go:379-405`) để `ts.Bitrate=0` khi nguồn lossless + đích lossless** (nhánh `else` ở dòng ~389 không set `ts.Bitrate`, chỉ kiểm tra điều kiện reject). Kết hợp `estimateContentLength=true` (Aonsoku luôn gửi) → `Stream.EstimatedContentLength()` (`core/stream/media_streamer.go:149-151`) tính `duration * 0 / 8 * 1024 = 0` → header `Content-Length: 0` sai → ghi thật FLAC nhiều byte hơn → lỗi `http: wrote more than the declared Content-Length` (đã tái hiện, log server + response rỗng/treo). Đây là bug tổng quát cho **mọi** transcode lossless→lossless (không riêng DSD) khi có `estimateContentLength=true`, không phải riêng Aonsoku. Đề xuất tối thiểu: set `ts.Bitrate` trong nhánh lossless→lossless bằng ước lượng hợp lý (vd `src.Bitrate` hoặc bitrate FLAC điển hình theo sample rate/depth) thay vì để mặc định 0.

Cả 2 đều **không nằm trên đường dẫn được khuyến nghị cho prod** (dùng `MaxBitRate`, không gán `TranscodingId=flac` cho player Aonsoku) nên KHÔNG chặn cutover Phase 5. Đề xuất ghi nhận làm backlog/fix riêng, chờ duyệt.

### Backlog — tính năng Aonsoku không tương thích Navidrome

- **Podcast**: Aonsoku gọi 1 backend riêng (`/api/podcasts*`, `/api/episodes*`, header `APP-USERNAME`/`APP-SERVER-URL`, base URL cấu hình qua `podcasts.serviceUrl`) — đây là companion service riêng của Aonsoku, **không phải Subsonic API, Navidrome không và sẽ không implement**. Tính năng mặc định `active:false` (`player/src/store/app.store.ts:67-74`) nên không ảnh hưởng UX mặc định; nếu cần bật, phải tự deploy backend podcast riêng ngoài Navidrome — ghi backlog, không làm.
- **Lyrics LRCLib**: fallback lyric đồng bộ gọi thẳng `lrclib.net` từ trình duyệt (`player/src/service/lyrics.ts:134-204`), không qua Navidrome — hoạt động độc lập, không phải gap.

**Verification checklist:**
- [x] Checklist smoke ở trên pass từng mục, ghi kết quả vào plan (bảng trên).
- [x] DSF/DFF phát được qua Aonsoku sau khi cấu hình `Player.MaxBitRate=320` cho client `Trợ lý nhạc` (không cần đổi code) — verify bằng ffprobe (`opus/48000`) + đối chứng UAPP không đổi.

**Anti-pattern guards**: đã tuân thủ — không sửa transcode decider; gap là thiếu cấu hình Player record, xác nhận bằng cách cấu hình đúng qua API/UI có sẵn rồi verify lại. 2 gap code thật phát hiện thêm (case flac thiếu trong `codecMaxSampleRate`, bitrate=0 cho lossless→lossless) được báo cáo làm đề xuất, KHÔNG tự sửa.

## Phase 5 — Deploy prod + cutover nhac.troly.me

**Làm gì (theo đúng quy trình `memory/deploy-espeak-vm.md`):**
1. Merge nhánh vào master (sau khi user duyệt), build + push image: `docker buildx build --platform linux/amd64 --target final -t asia-southeast1-docker.pkg.dev/espeak-490004/navidrome/navidrome:troly-<sha> --push .` — **verify tag tồn tại trên AR trước bước 2** (bẫy HEAD đổi giữa chừng); kiểm tra `df -h /var/lib/docker` trên VM trước khi pull (bẫy disk đầy).
2. Đặt env mới rồi update container: `gcloud compute instances update-container navidrome --zone=asia-southeast1-b --project=espeak-490004 --container-image=...troly-<sha>` + `--container-env=ND_DEFAULTUIPATH=/play` (giữ các env cũ: ND_DEFAULTLANGUAGE=vi, ND_GOOGLEDRIVEAPIKEY). Chờ 1-4 phút (521 tạm thời bình thường).
3. Verify prod (checklist dưới) XONG mới cutover: sửa `/var/lib/caddy/cfg/Caddyfile` — block `nhac.troly.me` đổi từ proxy `localhost:4544` thành `redir https://ms.troly.me{uri} 308`; `docker exec caddy caddy validate` + reload.
4. Sau 1-2 ngày ổn định: `docker rm -f aonsoku` trên VM, xoá image aonsoku cũ (`docker rmi`), cập nhật `memory/deploy-espeak-vm.md` (bỏ mục container phụ aonsoku, thêm /play + ND_DEFAULTUIPATH) và `.navidrome-local/aonsoku/docker-compose.yml` đánh dấu deprecated.

**Verification checklist (prod):**
- [ ] `https://ms.troly.me/` → 302 `/play/`; `/play/` login + phát nhạc OK trên cả desktop lẫn mobile browser.
- [ ] `https://ms.troly.me/app/` react-admin OK (đủ tab fork: Nâng cấp chất lượng, Import, Nhạc sĩ).
- [ ] `/rest/ping` OK với client Android/UAPP hiện có (không đổi credentials).
- [ ] `/share/<id>` link share cũ vẫn mở được.
- [ ] `https://nhac.troly.me/` (sau cutover) → 308 về ms.troly.me.
- [ ] Rollback path sẵn: image `troly-c4f836a7` còn trên VM + `ND_DEFAULTUIPATH` bỏ đi là về hành vi cũ; container aonsoku chỉ xoá sau khi ổn định.

## Phase 6 — Verification cuối (chống anti-pattern toàn cục)

1. `go test ./... ` + `cd ui && npm test` + `cd player && npm run build` — tất cả pass.
2. Grep guards:
   - `grep -rn "localhost:4544\|nhac.troly.me" --include="*.go" --include="*.jsx" --include="*.tsx"` → 0 kết quả trong code (chỉ được xuất hiện trong docs/plan).
   - `grep -rn "BasePath" server/ | grep play` → chỉ qua `MountRouter`/`path.Join` pattern chuẩn.
   - Không có `yarn` trong target build mới của Makefile/Dockerfile.
3. Đối chiếu từng "Allowed API" ở Phase 0: mọi code mới phải trace về 1 dòng trong bảng đó hoặc là SPA-fallback/env-config handler đã khai báo.
4. Cập nhật ACTIVE.md + memory (`deploy-espeak-vm.md`) phản ánh kiến trúc mới.

## Backlog (ngoài scope, KHÔNG làm trong plan này)

- SSO bridge: `/auth/login` đã trả `subsonicSalt`/`subsonicToken` (`server/auth.go:88-92`) → auto-login Aonsoku từ session react-admin.
- Đồng bộ theme VibeND ↔ APP_THEME của Aonsoku.
- Nâng upstream Aonsoku lên bản mới hơn (image gốc đã 3 tháng tuổi) — làm sau khi migration ổn định.
- Gỡ hẳn subdomain nhac.troly.me khỏi Cloudflare sau 30 ngày redirect.
