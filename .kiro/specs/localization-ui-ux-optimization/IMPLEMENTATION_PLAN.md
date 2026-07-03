# Kế Hoạch Triển Khai (Đã Hiệu Chỉnh Theo Codebase Thật)

> Plan này thay thế `tasks.md`. Nó được viết SAU khi điều tra codebase thật (6 recon agent),
> nên đã sửa các giả định sai trong `requirements.md`/`design.md`/`tasks.md`.
> Mỗi Phase là một phiên làm việc độc lập, có thể chạy tuần tự bằng `/do`.

---

## Phase 0 — Ground Truth (Bắt buộc đọc trước khi code)

Kết quả điều tra codebase thật. **Đây là "Allowed APIs" và "Anti-patterns" — không được phát minh API ngoài danh sách này.**

### 0.1 Những gì SPEC GIẢ ĐỊNH SAI (phải sửa khi thực thi)

| Spec nói | Thực tế | Hệ quả |
|---|---|---|
| `vi.json` đặt ở `ui/src/i18n/vi.json` | Server serve dịch từ `resources/i18n/*.json` (36 file); `ui/src/i18n/` chỉ có `en.json` (source of truth) | **vi.json phải đặt ở `resources/i18n/vi.json`** |
| Test bằng Jest | Test runner là **Vitest** (`ui/package.json` script `test: vitest --watch=false`) | PBT/unit test viết theo API `vitest` (`vi.fn`, `import { describe,it,expect } from 'vitest'`) |
| `fast-check` chỉ cần cài | **Chưa cài** | Nếu làm PBT: `yarn add -D fast-check` trong `ui/`. Nếu không muốn thêm dep → dùng `it.each` thay PBT |
| `@material-ui/lab` "cần thêm" | **Đã có** (`^4.0.0-alpha.61`) | Dùng `@material-ui/lab/Skeleton` trực tiếp, không cần cài |
| `loginBackgroundURL` mặc định rỗng | Mặc định = `'https://source.unsplash.com/collection/1065384/1600x900'` (`ui/src/config.js:10`) | Gradient fallback chỉ kích hoạt khi URL rỗng/lỗi → cần đổi default thành `''` HOẶC chỉ fallback khi ảnh onError |
| Req 11,12,13 (audio) cần build mới | **Đã tồn tại gần đủ** (xem 0.3) | Scope xuống: chỉ verify + enhancement nhỏ |
| Frontend chưa dùng `DefaultLanguage` | `App.jsx:92-110` ĐÃ apply sau login; chỉ initial/Login page dùng fallback `'en'` cứng | Gap thật nhỏ hơn spec mô tả |
| `routes.jsx` chứa route chính | `routes.jsx` chỉ có 1 custom route `/personal`; Resources định nghĩa trong `App.jsx:114-189` | Lazy-load phải làm ở `App.jsx` + file `index` từng resource |

### 0.2 Allowed APIs (đã xác minh, kèm file:line để COPY)

**i18n**
- `prepareLanguage(lang)` — `ui/src/i18n/provider.js:43-52` (deepmerge với en → fallback tự động; alias `song`→`albumSong`/`playlistTrack`; `ra.boolean.null=''`)
- `retrieveTranslation(locale)` — `ui/src/i18n/provider.js:21-26`
- `defaultLocale()` — `ui/src/i18n/provider.js:8-19` (hiện fallback cứng `'en'`)
- `useGetLanguageChoices()` — `ui/src/i18n/useGetLanguageChoices.jsx:4-21` (auto-populate dropdown + `localeCompare` sort)
- Server loader `loadTranslations()` — `server/nativeapi/translations.go:64-121` (đọc mọi `.json` trong `resources/i18n/`, yêu cầu field `languageName`) — **KHÔNG cần sửa Go để thêm ngôn ngữ**
- Config `DefaultLanguage` — `conf/configuration.go:92`, inject xuống `window.__APP_CONFIG__.defaultLanguage` tại `server/serve_index.go:55`
- FE đọc: `config.defaultLanguage` — `ui/src/config.js`; áp dụng: `ui/src/App.jsx:95-110`
- `en.json` — 10 nhóm top-level: `languageName, resources, ra, message, menu, player, about, activity, nowPlaying, help`; ~553 leaf keys

**formatters** (`ui/src/utils/formatters.js`)
- `formatFullDate(date, locale)` — line 84-96 — **ĐÃ nhận locale** (dùng `toLocaleDateString`)
- `formatNumber(value, locale)` — line 98-101 — **ĐÃ nhận locale** (dùng `toLocaleString`)
- `formatDuration2(totalSeconds)` — line 28-62 — **CHƯA có locale**, đơn vị `d/h/m/s` hardcoded
- `formatDuration(d)` — line 13-26 (HH:MM:SS); `formatShortDuration(ns)` — line 64-82; `formatBytes` — line 1-11 (đơn vị EN hardcoded)
- Locale hiện tại lấy qua react-admin `useLocale()` trong component
- Test hiện có: `ui/src/utils/formatters.test.js` (dùng `locale='en-CA'`)

**themes**
- Đăng ký: `ui/src/themes/index.js` — default export object, thêm alphabetical, key PascalCase `VibeNDTheme`, file `vibeND.js`
- Skeleton copy: `ui/src/themes/nord.js` (object) + `ui/src/themes/nord.css.js` (player CSS)
- Theme object bắt buộc: `themeName`, `palette{primary.main, secondary.main, type}`, `overrides`, `player{theme, stylesheet}`
- Override keys có thật: MUI (`MuiAppBar,MuiButton,MuiTableRow,MuiPaper,...`), ND (`NDAlbumGridView, NDAudioPlayer, NDLogin, NDAppBar,...`), Ra (`RaLayout, RaSidebar, RaList,...`)
- Đổi theme: Redux `changeTheme(key)` (`ui/src/actions/themes.js`), resolve `ui/src/themes/useCurrentTheme.js`, UI `ui/src/personal/SelectTheme.jsx` — **không reload trang** (đã đạt req 7.4)
- Player CSS inject runtime: `useCurrentTheme.js:23-44`

**UI infra**
- Login: `ui/src/layout/Login.jsx` — `useStyles` (theme)=>..., background tại line 35; wrapper `LoginWithTheme` line 405-414 có `useCurrentTheme`
- Loading state: `ui/src/common/useImageLoadingState.js`; cover grid: `ui/src/album/AlbumGridView.jsx:98-154` (opacity fade, `useImageUrl`)
- Export component: `ui/src/common/index.js`
- Resources: `ui/src/App.jsx:114-189`; resource index files: `transcoding/index.js`, `radio/index.jsx`, `missing/index.js`, `plugin/index.js`
- Test co-located `*.test.jsx`; setup `ui/src/setupTests.js`; ví dụ `ui/src/album/AlbumDetails.test.jsx`

**audio/player** (`ui/src/audioplayer/`, `ui/src/transcode/`)
- Player: `audioplayer/Player.jsx` (dùng `navidrome-music-player`; Web Audio GainNode line ~150-172)
- Direct-play detect: `transcode/browserProfile.js` (`canPlayType()`; `TRANSCODE_CODECS=['flac','opus','mp3']`; Safari=`['mp3']`)
- Decision: `transcode/decisionService.js` (`getDecision`, `resolveStreamUrl`, `getCachedDecision`; `/rest/stream` vs `/rest/getTranscodeStream`)
- ReplayGain: `utils/calculateReplayGain.js` (`calculateGain(gainInfo, song)`); toggle `personal/ReplayGainToggle.jsx` (album/track/none + preAmp); reducer `reducers/replayGainReducer.js`
- HUD: `audioplayer/AudioTitle.jsx` + `common/QualityInfo.jsx`
- Song metadata có sẵn: `suffix, bitRate, bitDepth, sampleRate, channels, rgTrackGain, rgAlbumGain, rgTrackPeak, rgAlbumPeak, isRadio`
- Config: `losslessFormats='FLAC,WAV,ALAC,DSF'`, `enableReplayGain`

### 0.3 Trạng thái Audio (Req 11,12,13) — ĐÃ CÓ vs CẦN LÀM

| Yêu cầu | Trạng thái |
|---|---|
| 11.1/11.2 direct play lossless qua `/rest/stream` | ĐÃ CÓ (decisionService) — chỉ **verify** |
| 11.5 fallback transcode theo `TRANSCODE_CODECS` | ĐÃ CÓ — verify |
| 11.3/11.4 badge LOSSLESS / HI-RES | **CẦN THÊM** (QualityInfo hiện chưa có badge hi-res riêng) |
| 11.6/13.1/13.2 HUD hover: sampleRate/bitDepth/bitrate/channels | Một phần (chỉ ở SongInfo panel) → **thêm vào QualityInfo/AudioTitle** |
| 12.1 hard limiter `min(10^((g+preAmp)/20), 1/peak)` | **VERIFY** công thức thật trong `calculateReplayGain.js`; sửa nếu thiếu 1/peak |
| 12.2/12.4/12.5 none=unity, cập nhật realtime, no-RG=unity | Phần lớn ĐÃ CÓ (GainNode `setValueAtTime`) — verify |
| 13.3 badge đổi màu theo tier | **CẦN THÊM** |
| 13.5 dịch quality terms sang vi | **CẦN THÊM** (nằm trong vi.json Phase 1) |
| 13.6 persist expanded `nd_quality_panel_expanded` | **CẦN THÊM** nếu làm Quality Panel mở rộng |

> Khuyến nghị: Audio để **Phase 7**, ưu tiên thấp, chủ yếu verify + vá nhỏ. Không rebuild.

---

## Phase 1 — Việt hoá + Locale mặc định (Req 1, 3, 10)

**Mục tiêu:** Người dùng chọn được "Tiếng Việt"; server auto-serve; honor `DefaultLanguage=vi`.

**Thực thi:**
1. Tạo `resources/i18n/vi.json` (KHÔNG phải ui/src/i18n).
   - Copy nguyên cấu trúc `ui/src/i18n/en.json`, dịch toàn bộ ~553 leaf keys.
   - `languageName: "Tiếng Việt"`.
   - Thuật ngữ: Bài hát / Album / Nghệ sĩ / Danh sách phát / Thể loại / Phát ngẫu nhiên / Yêu thích / Phát / Tạm dừng / Hàng chờ / Ghi nhận lượt nghe.
   - Plural: tiếng Việt dùng 1 form. Với key có `||||`, để 2 vế giống nhau (hoặc bỏ `||||`).
   - Bỏ qua/không cần dịch `albumSong`, `playlistTrack` (prepareLanguage tự alias từ `song`).
   - Giữ nguyên placeholder `%{...}` y hệt en.json.
2. Honor server default ở initial load (Req 8.1, 10.2): sửa `ui/src/i18n/provider.js:8-19` `defaultLocale()` — trước khi trả `'en'`, đọc `config.defaultLanguage` (import từ `../config`); nếu có và != '' thì trả nó (App.jsx đã lo phần fetch+set). Đảm bảo locale không hợp lệ → vẫn fallback `'en'` im lặng (Req 10.4).
3. (Optional) POEditor: thêm `vi` vào project POEditor để pull sync quản lý (`.github/workflows/update-translations.sh`). Không bắt buộc cho MVP.

**Verification:**
- `make test-i18n` (chạy `validate-translations.sh`) → vi.json khớp cấu trúc en.json, có `languageName`, JSON hợp lệ.
- Chạy server → `GET /api/translation/vi` trả 200, `id:"vi"`, `name:"Tiếng Việt"`.
- Dropdown ngôn ngữ hiện "Tiếng Việt" (sort alphabet), chọn → UI đổi tiếng Việt < 2s.
- Đặt `ND_DEFAULTLANGUAGE=vi`, xoá `localStorage.locale` → app + Login khởi tạo tiếng Việt.
- `ND_DEFAULTLANGUAGE=xyz` → app vẫn chạy, dùng English (Req 10.4).

**Anti-patterns:** KHÔNG đặt vi.json vào `ui/src/i18n/`. KHÔNG sửa `translations.go` (loader tự nhận). KHÔNG bỏ placeholder `%{...}`. KHÔNG hiển thị raw key khi thiếu (deepmerge đã lo).

---

## Phase 2 — Locale-aware formatting + collation (Req 2, 9.2)

**Thực thi:**
1. `formatDuration2` (`ui/src/utils/formatters.js:28-62`): thêm param `locale='en'`; khi `locale==='vi'` dùng `{d:'ng',h:'g',m:'ph',s:'gi'}`, else giữ `d/h/m/s`. Giữ nguyên logic tính. Case null/âm khi vi → `'0gi'`.
2. Thread locale vào callers (chỉ vài chỗ — danh sách đủ ở recon):
   - `formatDuration2`: `library/LibraryEdit.jsx:188` (dùng như `format` prop → wrap `(v)=>formatDuration2(v, locale)` với `useLocale()`).
   - `formatFullDate` (đã nhận locale): `album/AlbumDetails.jsx:168,171,173` → truyền `mapLocale(useLocale())` (map `'vi'`→`'vi-VN'`).
   - `formatNumber`: hiện không có call thật; nếu thêm dùng thì truyền locale.
3. Collation (Req 9.2): nơi UI sort client-side theo tên, dùng `new Intl.Collator(localeToBCP47(locale))`. Xác định các chỗ sort thật trước khi sửa (nhiều list sort server-side — chỉ đụng chỗ sort ở FE, vd `useGetLanguageChoices` đã dùng `localeCompare`).
4. Helper `localeToBCP47(locale)`: `'vi'→'vi-VN'`, else pass-through.

**Verification:** `cd ui && yarn test --run` (vitest). Unit: `formatDuration2(3661,'vi')==='1g 1ph 1gi'`, `formatDuration2(0,'vi')==='0gi'`, `formatDuration2(90061,'vi')==='1ng 1g 1ph'`, `formatFullDate('2024-03-15','vi-VN')` chứa `'tháng'`.

**Anti-patterns:** KHÔNG đổi signature phá vỡ callers hiện tại (giữ default `'en'`). KHÔNG localize `formatBytes` bằng Intl (đơn vị là chuỗi, cần chiến lược riêng — bỏ qua MVP). KHÔNG tự transliterate query tìm kiếm (Req 9.5).

---

## Phase 3 — Theme VibeND (Req 5.1 contrast, 7.4)

> Lưu ý: đã có nhiều dark theme (nord, dracula, tokyoNight, spotify, gruvboxDark). VibeND là bổ sung thẩm mỹ. Nếu người dùng không cần theme mới, có thể bỏ Phase này.

**Thực thi:**
1. `ui/src/themes/vibeND.css.js` — copy cấu trúc `nord.css.js`; player panel `#161B22`, border-top `#30363D`, progress `#3FB950`, bo góc.
2. `ui/src/themes/vibeND.js` — copy skeleton `nord.js`; palette (0.2 design.md), overrides `NDAlbumGridView/NDAudioPlayer/MuiTableRow/...`, `player{theme:'dark', stylesheet}`.
3. Đăng ký `ui/src/themes/index.js`: import + thêm key `VibeNDTheme` đúng vị trí alphabet.

**Verification:** Theme hiện trong SelectTheme, đổi không reload. Contrast mọi cặp (text/bg) ≥ 4.5:1 (tính WCAG). Build `cd ui && yarn build` không lỗi.

**Anti-patterns:** KHÔNG bịa override key không tồn tại (dùng danh sách 0.2). KHÔNG named-export (index.js là default object).

---

## Phase 4 — Perf & Polish (Req 4 responsive, 6 nav, 7 perf)

**Thực thi (mỗi mục độc lập, làm dần):**
1. `SkeletonList` — `ui/src/common/SkeletonList.jsx` dùng `@material-ui/lab/Skeleton` (đã có dep); props `variant` + `count`; màu theo `theme.palette.action.hover`; export ở `common/index.js`. Cắm vào AlbumList/SongList/ArtistList lúc loading.
2. Cover placeholder (Req 7.2) — mở rộng `AlbumGridView.jsx:98-154`: khi lỗi ảnh → icon MusicNote thay broken image (dùng `useImageLoadingState` `imageError`).
3. Lazy routes (Req 7.5) — ở `App.jsx` bọc `React.lazy()` + `<Suspense fallback={<Loading/>}>` cho `transcoding/radio/missing/plugin` (import từ resource index). Thêm ErrorBoundary (tham khảo `plugin/SchemaConfigEditor.jsx` `SchemaErrorBoundary`).
4. Breadcrumb (Req 6.3) — `ui/src/common/Breadcrumb.jsx`; dùng trong AlbumShow/ArtistShow; item cuối `aria-current="page"`.
5. Scroll restore (Req 6.1) — `sessionStorage` key `scrollPos:{resource}`.
6. Empty search message (Req 6.4) — text i18n key mới (thêm vào en.json + vi.json).

**Verification:** vitest cho SkeletonList/Breadcrumb; kiểm thủ công viewport <600px (menu hamburger, mini-player, 1 cột); mạng chậm thấy skeleton; lazy chunk tách trong `yarn build`.

**Anti-patterns:** KHÔNG virtualize nếu list đã dùng react-admin pagination (Req 7.6 chỉ áp dụng list >200 render 1 lần). KHÔNG thêm dep mới cho skeleton.

---

## Phase 5 — Accessibility (Req 5)

**Thực thi:** aria-label cho icon buttons (Player, LoveButton); focus indicator `:focus-visible` outline ≥2px (theme overrides); `aria-live="polite"` region báo play/pause/next/prev trong Player; "Skip to main content" link ẩn; focus trap cho dropdown/Language selector + trả focus khi đóng; kiểm tra tab order.

**Verification:** axe/lighthouse a11y; test aria-label tồn tại; kiểm bàn phím thủ công (Tab, Escape).

**Anti-patterns:** KHÔNG đặt `tabindex` dương. KHÔNG ẩn focus outline toàn cục (chỉ ẩn cho mouse qua `:focus-visible`).

---

## Phase 6 — Login page (Req 8)

**Thực thi:** `Login.jsx:35` — gradient fallback. Vì default `loginBackgroundURL` là URL unsplash: đổi default `config.js` thành `''` HOẶC dựng `background` = `url(...) , gradient` và onError chuyển sang chỉ gradient. Gradient dùng `theme.palette.background.default` + `theme.palette.primary.main` (đã có `theme` trong makeStyles). Ngôn ngữ Login theo thứ tự localStorage → defaultLanguage → en (đã lo ở Phase 1). `welcomeMessage` sanitize HTML.

**Verification:** unit: `loginBackgroundURL=''` → style chứa `linear-gradient`; có URL → chứa cả `url()` + gradient. Thủ công: URL lỗi → thấy gradient, không trắng/đen.

**Anti-patterns:** KHÔNG render welcomeMessage HTML chưa sanitize.

---

## Phase 7 — Audio quality: verify + enhancement (Req 11, 12, 13)

> Hạ tầng đã có (xem 0.3). Chỉ verify + vá nhỏ. KHÔNG rebuild.

**Thực thi:**
1. **Verify** công thức limiter trong `utils/calculateReplayGain.js` = `min(10^((gain+preAmp)/20), 1/peak)` cho album/track; `none`→1.0; no-RG→1.0; peak>1→áp 1/peak. Sửa nếu thiếu.
2. Badge LOSSLESS/HI-RES (11.3/11.4): trong `QualityInfo.jsx` — lossless khi `suffix ∈ losslessFormats`; HI-RES khi `bitDepth>16`.
3. HUD hover (11.6/13.1/13.2): thêm sampleRate/bitDepth/bitrate/channels + trạng thái Direct Play/Transcoded vào `AudioTitle.jsx`/`QualityInfo.jsx` (metadata đã có).
4. Badge màu theo tier (13.3): xanh lá lossless/hi-res, xanh dương lossy≥256, vàng <256, xám unknown.
5. Ẩn badge khi `isRadio` (13.4).
6. Dịch quality terms (13.5): keys nằm trong vi.json (Phase 1).
7. (Optional) Quality Panel mở rộng + persist `localStorage.nd_quality_panel_expanded` (13.6).

**Verification:** phát FLAC → badge LOSSLESS + Direct Play; 24-bit → HI-RES; radio → ẩn badge; đổi gainMode realtime không ngắt nhạc; kiểm `GainNode.gain.value ≤ 1.0`.

**Anti-patterns:** KHÔNG tạo lại `browserProfile.js`/`decisionService.js`/`calculateReplayGain.js` (đã có). KHÔNG bỏ hard limiter (clip số).

---

## Phase 8 — Verification tổng

1. `cd ui && yarn test --run` (vitest) — không regression.
2. `cd ui && yarn build` — build sạch, lazy chunks tách.
3. `make test-i18n` — vi.json hợp lệ.
4. Go: `go test ./server/nativeapi/...` — translation endpoint.
5. Grep anti-pattern: không còn `vi.json` trong `ui/src/i18n/`; không import jest; không API bịa.
6. Kiểm thủ công: đổi ngôn ngữ, đổi theme, mobile viewport, a11y, phát lossless.

---

## Thứ tự khuyến nghị & phụ thuộc

```
Phase 1 (vi.json) ─┬─> Phase 2 (formatters/collation)
                   ├─> Phase 6 (login lang, cần Phase 1)
                   └─> Phase 7.6 (quality terms cần vi.json)
Phase 3 (theme)  ── độc lập
Phase 4 (perf)   ── độc lập (Skeleton/lazy/breadcrumb)
Phase 5 (a11y)   ── độc lập, nên sau Phase 3 (theme ảnh hưởng contrast/focus)
Phase 7 (audio)  ── độc lập, ưu tiên thấp (mostly done)
Phase 8          ── cuối cùng
```

**Ưu tiên cao (giá trị/công thấp):** Phase 1 → 2 → 6.
**Trung bình:** Phase 4 → 5.
**Thấp / tùy chọn:** Phase 3 (đã nhiều dark theme), Phase 7 (đã gần đủ).
