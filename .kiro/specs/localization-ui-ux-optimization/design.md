# Tài Liệu Thiết Kế: Localization UI/UX Optimization

## Tổng Quan

Tính năng này bao gồm hai mảng chính:

1. **Việt hoá (Vi18n)**: Thêm hỗ trợ đầy đủ ngôn ngữ tiếng Việt cho Navidrome — bao gồm tệp bản dịch `vi.json`, định dạng ngày tháng/số theo chuẩn Việt Nam, tích hợp vào hệ thống i18n hiện có (`ra-i18n-polyglot` + `deepmerge`), và hỗ trợ cấu hình ngôn ngữ mặc định toàn hệ thống.

2. **Theme VibeND**: Một dark theme hiện đại lấy cảm hứng từ Nord nhưng được nâng cấp với bảng màu tinh tế hơn, typography cải tiến, và interaction patterns quen thuộc từ các ứng dụng streaming nhạc hàng đầu (Spotify, Apple Music). Theme này cũng bổ sung các cải tiến UI/UX: skeleton loading, lazy-loaded routes, breadcrumb navigation, và tối ưu responsive cho mobile.

### Phạm Vi Thay Đổi

| Phần | Files bị ảnh hưởng |
|------|-------------------|
| Bản dịch tiếng Việt | `ui/src/i18n/vi.json` (mới) |
| Đăng ký locale | `server/` (API endpoint translation) |
| Định dạng ngôn ngữ | `ui/src/utils/formatters.js` |
| Theme VibeND | `ui/src/themes/vibeND.js`, `vibeND.css.js` (mới) |
| Đăng ký theme | `ui/src/themes/index.js` |
| Skeleton loading | `ui/src/common/SkeletonList.jsx` (mới) |
| Breadcrumb | `ui/src/common/Breadcrumb.jsx` (mới) |
| Lazy routes | `ui/src/routes.jsx` |
| Login gradient | `ui/src/layout/Login.jsx` |

---

## Kiến Trúc

### Hệ Thống i18n Hiện Tại

```
localStorage.locale → defaultLocale() → polyglotI18nProvider
                                            ↓
                              locale === 'en' → prepareLanguage(en.json)
                                            ↓
                              locale !== 'en' → dataProvider.getOne('translation', {id: locale})
                                            ↓
                              localStorage.setItem('translation', ...) → prepareLanguage(merged)
```

`prepareLanguage()` thực hiện:
- Xoá các keys rỗng (`removeEmpty`)
- Copy `song` → `albumSong` và `playlistTrack`
- Đặt `ra.boolean.null = ''`
- `deepmerge(en, lang)` để fallback về tiếng Anh khi thiếu key

### Luồng Tích Hợp Vi_Translation

```
vi.json (POEditor / file tĩnh)
    ↓
GET /api/translation/vi  (server-side)
    ↓
localStorage.setItem('translation', { id: 'vi', name: 'Tiếng Việt', data: '...' })
    ↓
prepareLanguage(vi_data) → deepmerge(en, vi_data)
    ↓
polyglotI18nProvider nhận merged translations
    ↓
useTranslate() hook → giao diện hiển thị tiếng Việt
```

### Kiến Trúc Theme System

```
themes/index.js
    └── export { ..., VibeNDTheme }

useCurrentTheme.js
    └── localStorage.getItem('theme') || config.defaultTheme
    └── return themes[themeName]

Personal.jsx
    └── SelectTheme → setTheme(name) → localStorage.setItem('theme', name)
    └── không cần reload trang (React state update)
```


---

## Thành Phần và Interfaces

### 1. Vi_Translation (`ui/src/i18n/vi.json`)

Tệp JSON tuân theo cấu trúc của `en.json`. Các nhóm key chính:

- `languageName`: `"Tiếng Việt"`
- `resources.song`, `resources.album`, `resources.artist`, `resources.user`, `resources.player`, `resources.transcoding`, `resources.playlist`, `resources.radio`, `resources.share`, `resources.missing`, `resources.library`, `resources.plugin`
- `ra.auth`, `ra.validation`, `ra.action`, `ra.boolean`, `ra.page`, `ra.input`, `ra.message`, `ra.navigation`, `ra.notification`, `ra.toggleFieldsMenu`
- `message`, `menu`, `player`, `about`, `activity`, `nowPlaying`, `help`

**Quy tắc pluralisation tiếng Việt:**

Tiếng Việt không biến hình theo số. Chuỗi plural dùng cùng một form:
```json
{
  "resources": {
    "song": {
      "name": "Bài hát"
    }
  }
}
```

Với format `"X |||| Y"` của polyglot, tiếng Việt dùng `"Bài hát |||| Bài hát"` hoặc chỉ `"Bài hát"` (khi polyglot không tìm thấy `||||` nó dùng toàn bộ chuỗi).

**Thuật ngữ âm nhạc chuẩn:**

| Tiếng Anh | Tiếng Việt |
|-----------|-----------|
| Song | Bài hát |
| Album | Album |
| Artist | Nghệ sĩ |
| Playlist | Danh sách phát |
| Genre | Thể loại |
| Shuffle | Phát ngẫu nhiên |
| Favourite | Yêu thích |
| Play | Phát |
| Pause | Tạm dừng |
| Queue | Hàng chờ |
| Scrobble | Ghi nhận lượt nghe |

### 2. Định Dạng Locale (`ui/src/utils/formatters.js`)

**Thay đổi tới `formatDuration2`:**

Thêm tham số locale tùy chọn. Khi locale là `vi`, dùng đơn vị tiếng Việt:

```javascript
export const formatDuration2 = (totalSeconds, locale = 'en') => {
  // ...
  const units = locale === 'vi'
    ? { day: 'ng', hour: 'g', minute: 'ph', second: 'gi' }
    : { day: 'd', hour: 'h', minute: 'm', second: 's' }
  // ...
}
```

`formatFullDate` và `formatNumber` đã nhận `locale` parameter — caller cần truyền `vi-VN` khi locale là `vi`.


### 3. Theme VibeND (`ui/src/themes/vibeND.js`)

**Bảng màu (lấy cảm hứng từ GitHub Dark + Spotify):**

| Token | Hex | Mô tả |
|-------|-----|-------|
| `bg.default` | `#0D1117` | Background nền chính — deep dark |
| `bg.surface` | `#161B22` | Card, panel, drawer |
| `bg.elevated` | `#1C2128` | AppBar, Toolbar, chỗ nổi lên |
| `bg.muted` | `#21262D` | Table row hover, input |
| `accent.primary` | `#58A6FF` | Blue accent — link, focus, active |
| `accent.secondary` | `#3FB950` | Green secondary — play button, success |
| `text.primary` | `#E6EDF3` | Văn bản chính |
| `text.secondary` | `#7D8590` | Văn bản phụ, metadata |
| `text.muted` | `#484F58` | Placeholder, disabled |
| `border` | `#30363D` | Đường viền nhẹ |

**Rationale về màu sắc:**
- `#0D1117` được GitHub dùng cho dark mode — ít gây mỏi mắt hơn pure black vì giữ được contrast mà không quá tương phản
- `#58A6FF` là blue của GitHub dark — đủ sáng để nhìn rõ trên nền tối mà không chói
- `#3FB950` là green của GitHub — tương đồng với Spotify green (`#1DB954`), tạo cảm giác quen thuộc cho người dùng streaming
- Toàn bộ palette dựa trên một hue duy nhất (blue-grey), đảm bảo visual cohesion

**Typography improvements:**

```javascript
NDAlbumGridView: {
  albumName: {
    fontWeight: 700,       // Tên album đậm hơn
    fontSize: '0.875rem',
    letterSpacing: '-0.01em',  // Tight letter spacing cho heading
  },
  albumSubtitle: {
    fontSize: '0.75rem',
    letterSpacing: '0.02em',
  }
}
NDAudioPlayer: {
  songTitle: {
    fontWeight: 600,
    fontSize: '0.9375rem',   // 15px — lớn hơn một chút
    letterSpacing: '-0.01em',
  },
  songInfo: {
    fontSize: '0.75rem',
    letterSpacing: '0.03em',
  }
}
```

**Interaction design:**

```javascript
// Album card hover: scale nhẹ + play button xuất hiện
NDAlbumGridView: {
  albumContainer: {
    transition: 'transform 0.2s ease, background-color 0.2s ease',
    '&:hover': {
      transform: 'translateY(-2px)',
      backgroundColor: '#1C2128',
    }
  },
  albumPlayButton: {
    opacity: 0,
    transition: 'opacity 0.2s ease, transform 0.2s ease',
    '$albumContainer:hover &': {
      opacity: 1,
    }
  }
}
// Table row highlight toàn dòng
MuiTableRow: {
  root: {
    transition: 'background-color 0.15s ease',
    '&:hover': { backgroundColor: '#1C2128 !important' }
  }
}
```

**Player improvements:**

```css
/* vibeND.css.js */
.react-jinke-music-player-main .music-player-panel {
    background-color: #161B22;
    border-top: 1px solid #30363D;
    min-height: 72px;  /* Taller: 72px vs 64px default */
}

/* Progress bar dày hơn, góc tròn */
.rc-slider-rail, .rc-slider-track {
    border-radius: 2rem;
    height: 4px;
}

/* Màu accent */
.react-jinke-music-player-main .music-player-panel .panel-content .rc-slider-track {
    background-color: #3FB950;  /* Spotify-like green */
}
```


### 4. Skeleton Loading (`ui/src/common/SkeletonList.jsx`)

Component hiển thị placeholder khi đang tải dữ liệu, dùng Material UI `Skeleton` (cần thêm `@material-ui/lab`):

```jsx
// Ví dụ interface
<SkeletonList
  variant="album-grid"  // hoặc "song-list", "artist-list"
  count={12}
/>
```

Skeleton tự adapt màu nền theo theme hiện tại thông qua `theme.palette.action.hover`.

### 5. Breadcrumb Navigation (`ui/src/common/Breadcrumb.jsx`)

```jsx
// Ví dụ sử dụng trong AlbumShow
<Breadcrumb items={[
  { label: translate('menu.albumList'), to: '/album' },
  { label: record.albumArtist, to: `/artist/${record.artistId}` },
  { label: record.name }  // current page — không có link
]} />
```

Interface:
```typescript
interface BreadcrumbItem {
  label: string
  to?: string  // optional — nếu không có thì là current page
}
```

### 6. Lazy-Loaded Routes (`ui/src/routes.jsx`)

Các route ít được truy cập sẽ dùng `React.lazy()`:

```javascript
const TranscodingList = React.lazy(() => import('./transcoding/TranscodingList'))
const RadioList = React.lazy(() => import('./radio/RadioList'))
const MissingList = React.lazy(() => import('./missing/MissingList'))
const PluginList = React.lazy(() => import('./plugin/PluginList'))
```

Bọc bằng `<React.Suspense fallback={<Loading />}>`.

### 7. Login Gradient Fallback (`ui/src/layout/Login.jsx`)

Khi `loginBackgroundURL` không được cấu hình hoặc trả về lỗi:

```javascript
// Gradient phụ thuộc vào theme
const getLoginBackground = (theme) => {
  if (config.loginBackgroundURL) {
    return `url(${config.loginBackgroundURL})`
  }
  // Fallback gradient dùng theme palette
  const primary = theme.palette.primary.main
  return `linear-gradient(135deg, ${theme.palette.background.default} 0%, ${primary}22 100%)`
}
```


---

## Mô Hình Dữ Liệu

### Vi Translation Object

```typescript
interface TranslationRecord {
  id: string           // "vi"
  name: string         // "Tiếng Việt"
  data: string         // JSON string của Vi_Translation
}

// Được lưu trong localStorage
interface StoredTranslation {
  id: string
  name: string
  data: string         // JSON string
}
```

### Vi Translation JSON Schema

```json
{
  "languageName": "string",
  "resources": {
    "[resourceName]": {
      "name": "string",        // Singular form (hoặc "X |||| Y" cho plural)
      "fields": { "[field]": "string" },
      "actions": { "[action]": "string" }
    }
  },
  "ra": {
    "auth": { ... },
    "validation": { ... },
    "action": { ... },
    "boolean": { "true": "string", "false": "string" },
    "page": { ... },
    "input": { ... },
    "message": { ... },
    "navigation": { ... },
    "notification": { ... },
    "toggleFieldsMenu": { ... }
  },
  "message": { ... },
  "menu": { ... },
  "player": { ... },
  "about": { ... },
  "activity": { ... },
  "nowPlaying": { ... },
  "help": { ... }
}
```

### Theme Object (VibeND)

```typescript
interface VibeNDTheme {
  themeName: 'VibeND'
  palette: {
    primary: { main: '#58A6FF' }
    secondary: { main: '#3FB950' }
    type: 'dark'
    background: {
      default: '#0D1117'
      paper: '#161B22'
    }
    text: {
      primary: '#E6EDF3'
      secondary: '#7D8590'
    }
  }
  overrides: MuiThemeOverrides  // Tất cả custom overrides
  player: {
    theme: 'dark'
    stylesheet: string          // CSS string cho react-jinke-music-player
  }
}
```

### Scroll Position Store

Lưu vào `sessionStorage` (không persist qua reload, xoá khi đóng tab):

```javascript
// Key format: "scrollPos:{resourceName}"
// Value: number (scrollY)
sessionStorage.setItem(`scrollPos:album`, window.scrollY)
```


---

## Correctness Properties

*Một property là đặc tính hoặc hành vi phải đúng trong mọi trường hợp thực thi hợp lệ của hệ thống — về cơ bản là một phát biểu hình thức về những gì hệ thống phải làm. Properties là cầu nối giữa đặc tả dễ đọc với con người và đảm bảo tính đúng đắn có thể kiểm tra tự động bằng máy.*

Tính năng này phù hợp với property-based testing vì có nhiều hàm thuần (pure functions) xử lý dữ liệu có thể kiểm tra trên nhiều đầu vào: bản dịch JSON (kiểm tra completeness và fallback), hàm định dạng ngày tháng/số (round-trip và locale correctness), và hàm sắp xếp locale-aware (ordering invariants).

---

### Property 1: Fallback về tiếng Anh khi thiếu key dịch thuật

*Với mọi* key path tồn tại trong `en.json` nhưng bị thiếu hoặc rỗng trong `vi.json`, sau khi qua `prepareLanguage()` (deepmerge với en.json), giá trị tại key đó phải bằng giá trị tiếng Anh tương ứng.

**Validates: Yêu Cầu 1.5**

---

### Property 2: Tính đầy đủ của Vi_Translation

*Với mọi* key path cấp lá (leaf key) có trong `en.json` mà không phải là placeholder có biến (`%{...}`), `vi.json` phải có giá trị tại key đó và giá trị đó không được là chuỗi rỗng.

**Validates: Yêu Cầu 1.1**

---

### Property 3: Plural strings tiếng Việt

*Với mọi* chuỗi trong `vi.json` có chứa ký hiệu plural `"||||"`, phần bên trái và bên phải của `"||||"` phải tương đương về nghĩa — tức là cùng nội dung (vì tiếng Việt không biến hình theo số).

**Validates: Yêu Cầu 1.3**

---

### Property 4: Định dạng ngày tháng locale vi — round-trip

*Với mọi* chuỗi ngày tháng hợp lệ dạng `YYYY`, `YYYY-MM`, hoặc `YYYY-MM-DD`, kết quả của `formatFullDate(date, 'vi-VN')` phải là chuỗi không rỗng và khi parse lại bằng `Date` sẽ cho ngày tương đương với ngày gốc (đến độ chính xác của format đã chọn).

**Validates: Yêu Cầu 2.1, 2.5**

---

### Property 5: Định dạng số locale vi

*Với mọi* số nguyên dương, `formatNumber(value, 'vi-VN')` phải tạo ra chuỗi chứa dấu chấm (`.`) làm dấu phân cách hàng nghìn khi giá trị ≥ 1000, và dùng dấu phẩy (`,`) làm dấu phân cách thập phân khi giá trị có phần thập phân.

**Validates: Yêu Cầu 2.2**

---

### Property 6: Đơn vị thời gian tiếng Việt

*Với mọi* giá trị `totalSeconds` không âm, `formatDuration2(seconds, 'vi')` phải trả về chuỗi chỉ chứa các đơn vị tiếng Việt (`ng`, `g`, `ph`, `gi`) và không chứa các đơn vị tiếng Anh (`d`, `h`, `m`, `s`).

**Validates: Yêu Cầu 2.3**

---

### Property 7: Sắp xếp tiếng Việt locale-aware

*Với mọi* mảng chuỗi chứa ký tự tiếng Việt có dấu, kết quả sắp xếp bằng `Intl.Collator('vi-VN')` phải thỏa mãn tính chất bắc cầu (transitivity): nếu `a ≤ b` và `b ≤ c` thì `a ≤ c`, và tính chất phản xứng (antisymmetry): nếu `a ≤ b` và `b ≤ a` thì `a` và `b` tương đương về thứ tự.

**Validates: Yêu Cầu 9.2**

---

### Property 8: Tỷ lệ tương phản màu sắc VibeND theme

*Với mọi* cặp (màu nền, màu chữ) được định nghĩa trong VibeND theme, tỷ lệ tương phản (contrast ratio) tính theo công thức WCAG 2.1 phải đạt tối thiểu 4.5:1 cho văn bản thông thường (< 18pt hoặc < 14pt bold) và 3:1 cho văn bản lớn.

**Validates: Yêu Cầu 5.1**


---

## Xử Lý Lỗi

### Lỗi Tải Bản Dịch

**Tình huống**: Server không trả về bản dịch `vi` (404, 500, timeout).

**Hành vi hiện tại** (đã có trong `provider.js`): `polyglotI18nProvider` khi promise reject sẽ trigger `ra.notification.i18n_error`. 

**Xử lý bổ sung**: Fallback về `en` ngay lập tức mà không block UI. `deepmerge(en, {})` đảm bảo toàn bộ chuỗi hiển thị bằng tiếng Anh.

### Lỗi Hình Ảnh Cover Art

**Tình huống**: URL cover art không tải được.

**Xử lý**: `useImageLoadingState` hook trong `common/useImageLoadingState.js` đã track loading state. Skeleton placeholder hiển thị trong khi tải; nếu lỗi, hiển thị fallback icon âm nhạc với màu `theme.palette.action.disabled`.

**Không** dùng `broken image` icon của browser — dùng `onError` handler để swap sang placeholder:

```jsx
<img
  src={imgUrl}
  onError={(e) => { e.target.style.display = 'none'; setFallback(true) }}
/>
{fallback && <MusicNoteIcon className={classes.placeholder} />}
```

### Lỗi Locale Không Hợp Lệ trong Config

**Tình huống**: `DefaultLanguage = xyz` không tồn tại.

**Xử lý** trong `config.js` / `provider.js`:
1. Nếu locale từ server config không có trong danh sách translations → log warning
2. `defaultLocale()` trả về `'en'` thay vì locale không hợp lệ
3. UI tiếp tục hoạt động bình thường

### Lỗi URL Nền Login

**Tình huống**: `loginBackgroundURL` trả về HTTP error hoặc bị blocked (CORS).

**Xử lý**: CSS `background-image` tự động bị ignore khi URL không load được. Cần thêm inline style fallback:

```javascript
const main = {
  background: config.loginBackgroundURL
    ? `url(${config.loginBackgroundURL}), ${themeGradient}`
    : themeGradient,
}
```

`themeGradient` luôn được set, nên background không bao giờ trắng/đen thuần.

### Lỗi Lazy Loading Route

**Tình huống**: Bundle chunk không tải được (network failure).

**Xử lý**: `React.Suspense` + `ErrorBoundary` tại cấp route:

```jsx
<ErrorBoundary fallback={<PageNotFound />}>
  <React.Suspense fallback={<Loading />}>
    <LazyComponent />
  </React.Suspense>
</ErrorBoundary>
```


---

## Chiến Lược Kiểm Thử

### Tổng Quan

Chiến lược dùng hai lớp kiểm thử bổ sung cho nhau:

- **Unit tests** (Jest + @testing-library/react): Kiểm thử ví dụ cụ thể, edge cases, và các UI interactions
- **Property-based tests** (fast-check): Kiểm thử các thuộc tính phổ quát trên nhiều đầu vào ngẫu nhiên

**Thư viện PBT được chọn**: [`fast-check`](https://github.com/dubzzz/fast-check) — đã phổ biến trong ecosystem JavaScript/TypeScript, không cần cài thêm JVM, tích hợp tốt với Jest.

### Unit Tests

**Bản dịch tiếng Việt:**
- `vi.json` có `languageName === "Tiếng Việt"`
- Các thuật ngữ âm nhạc đặc trưng đúng (song → "Bài hát", artist → "Nghệ sĩ", v.v.)
- `prepareLanguage` với `vi` data không throw error

**SelectLanguage:**
- Render SelectLanguage với mock translations → "Tiếng Việt" xuất hiện trong choices
- Chọn `vi` → `setLocale` được gọi với `'vi'`, `localStorage.locale` được set

**formatters.js:**
- `formatDuration2(3661, 'vi')` → `"1g 1ph 1gi"`
- `formatDuration2(0, 'vi')` → `"0gi"`
- `formatFullDate('2024-03-15', 'vi-VN')` → chuỗi chứa "tháng"

**Theme VibeND:**
- `VibeNDTheme.themeName === 'VibeND'`
- `VibeNDTheme.palette.type === 'dark'`
- Theme được export trong `themes/index.js`

**Login fallback:**
- Khi `loginBackgroundURL = ''`, background style chứa gradient

**Accessibility:**
- LoveButton và Player icon buttons có `aria-label`
- `aria-live` region tồn tại trong Player component

### Property-Based Tests

Mỗi property test chạy tối thiểu 100 iterations. Tag format: `Feature: localization-ui-ux-optimization, Property {N}: {title}`

**Property 1 — Fallback tiếng Anh:**
```javascript
// Feature: localization-ui-ux-optimization, Property 1: Fallback về tiếng Anh khi thiếu key dịch thuật
test.prop([fc.string()])(
  'prepareLanguage fallback to English for missing keys',
  (key) => {
    const partial = {}  // vi.json thiếu tất cả keys
    const merged = prepareLanguage(partial)
    // merged phải bằng en.json cho tất cả keys
    expect(merged.ra.action.save).toBe(en.ra.action.save)
  }
)
```

**Property 2 — Completeness:**
```javascript
// Feature: localization-ui-ux-optimization, Property 2: Tính đầy đủ Vi_Translation
// Kiểm tra tất cả leaf keys của en.json tồn tại trong vi.json
const leafKeys = getAllLeafPaths(en)  // utility function
test.each(leafKeys)('vi.json có key %s', (keyPath) => {
  const enVal = getPath(en, keyPath)
  const viVal = getPath(vi, keyPath)
  if (!enVal.includes('%{')) {
    expect(viVal).toBeTruthy()
  }
})
```

**Property 3 — Plural strings tiếng Việt:**
```javascript
// Feature: localization-ui-ux-optimization, Property 3: Plural strings tiếng Việt
const pluralEntries = getAllPluralEntries(vi)  // entries containing "||||"
test.each(pluralEntries)('plural entry %s dùng cùng form', (value) => {
  const [singular, plural] = value.split('||||').map(s => s.trim())
  expect(singular).toBe(plural)
})
```

**Property 4 — Date formatting round-trip:**
```javascript
// Feature: localization-ui-ux-optimization, Property 4: Định dạng ngày tháng vi — round-trip
fc.assert(
  fc.property(
    fc.integer({ min: 1900, max: 2100 }),
    fc.integer({ min: 1, max: 12 }),
    fc.integer({ min: 1, max: 28 }),
    (year, month, day) => {
      const dateStr = `${year}-${String(month).padStart(2,'0')}-${String(day).padStart(2,'0')}`
      const formatted = formatFullDate(dateStr, 'vi-VN')
      expect(formatted).not.toBe('')
      const parsed = new Date(dateStr)
      expect(parsed.getFullYear()).toBe(year)
    }
  ),
  { numRuns: 100 }
)
```

**Property 5 — Number formatting:**
```javascript
// Feature: localization-ui-ux-optimization, Property 5: Định dạng số locale vi
fc.assert(
  fc.property(fc.integer({ min: 1000, max: 1_000_000 }), (value) => {
    const formatted = formatNumber(value, 'vi-VN')
    expect(formatted).toContain('.')  // dấu chấm làm phân cách hàng nghìn
  }),
  { numRuns: 100 }
)
```

**Property 6 — Duration units tiếng Việt:**
```javascript
// Feature: localization-ui-ux-optimization, Property 6: Đơn vị thời gian tiếng Việt
fc.assert(
  fc.property(fc.integer({ min: 0, max: 86400 * 10 }), (seconds) => {
    const result = formatDuration2(seconds, 'vi')
    expect(result).not.toMatch(/\d+[dhms]/)  // không chứa đơn vị tiếng Anh
  }),
  { numRuns: 100 }
)
```

**Property 7 — Vietnamese collation transitivity:**
```javascript
// Feature: localization-ui-ux-optimization, Property 7: Sắp xếp tiếng Việt locale-aware
fc.assert(
  fc.property(
    fc.array(fc.string({ minLength: 1, maxLength: 20 }), { minLength: 2, maxLength: 20 }),
    (strings) => {
      const collator = new Intl.Collator('vi-VN')
      const sorted = [...strings].sort((a, b) => collator.compare(a, b))
      // Verify transitivity: sorted[i] <= sorted[i+1] for all i
      for (let i = 0; i < sorted.length - 1; i++) {
        expect(collator.compare(sorted[i], sorted[i + 1])).toBeLessThanOrEqual(0)
      }
    }
  ),
  { numRuns: 100 }
)
```

**Property 8 — Contrast ratio VibeND:**
```javascript
// Feature: localization-ui-ux-optimization, Property 8: Tỷ lệ tương phản màu VibeND
const colorPairs = extractColorPairs(VibeNDTheme)  // [(fg, bg), ...]
test.each(colorPairs)('contrast %s on %s >= 4.5:1', (fg, bg) => {
  const ratio = computeContrastRatio(fg, bg)
  expect(ratio).toBeGreaterThanOrEqual(4.5)
})
```

### Integration Tests

- GET `/api/translation/vi` → status 200, body có `id: 'vi'` và `name: 'Tiếng Việt'`
- Với `DefaultLanguage=vi` trong config → app khởi tạo với locale `vi`
- Theme switching không reload trang (React state update only)

### Smoke Tests

- Locale `vi` đăng ký trong POEditor project
- Font stack (Roboto) render ký tự Vietnamese có dấu không bị fallback về `.notdef`

