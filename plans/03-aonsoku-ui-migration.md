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

- Router mode của Aonsoku (BrowserRouter path-based hay hash) → quyết định có cần SPA fallback + `basename`.
- Vite `base` của Aonsoku có chỉnh được `/play/` không (react-admin dùng `base: './'` — `ui/vite.config.js:32`).
- Locale `vi` là của upstream hay do fork thêm (bundle `aonsoku-vi` có "Tiếng Việt" trong `i18n-*.js`).
- Aonsoku player xử lý DSD/transcode thế nào — fork này có clamp codecProfiles ≤96kHz trong react-admin player (commit `caafa1fc`); Aonsoku không có logic đó → kiểm tra stream DSF qua Aonsoku có bị lỗi không.

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

**Làm gì:**
1. Smoke toàn tính năng Aonsoku trên dev server với thư viện thật (copy vài album từ VM nếu cần, gồm ≥1 album DSF): browse album/artist, search, queue, playlist (đọc + sửa), radio, lyrics (plugin nd-lyrics), scrobble.
2. DSD: phát file .dsf/.dff qua Aonsoku — kiểm tra server trả transcode được browser decode (fork đã fix quy ước ffprobe DSD `caafa1fc`; react-admin gửi codecProfiles clamp — Aonsoku KHÔNG gửi). Nếu lỗi: fix phía server (default transcoding cho player client `c=Aonsoku` hoặc dựa trên player record trong bảng player), KHÔNG fork sâu Aonsoku.
3. Ghi các tính năng Aonsoku không hoạt động với Navidrome (nếu có) vào mục Backlog cuối file này thay vì cố fix trong plan.

**Verification checklist:**
- [ ] Checklist smoke ở trên pass từng mục, ghi kết quả vào plan.
- [ ] DSF phát được qua Aonsoku (hoặc quyết định + thực hiện fix server-side, có test).

**Anti-pattern guards**: không sửa transcode decider nếu nguyên nhân là thiếu player profile — cấu hình player record đúng cách trước.

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
