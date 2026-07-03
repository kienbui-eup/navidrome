# Kế Hoạch Triển Khai: Localization UI/UX Optimization

## Tổng Quan

Triển khai theo bốn mảng song song:
1. **Bản dịch tiếng Việt** — tệp `vi.json` đầy đủ, đăng ký server-side
2. **Định dạng locale** — cập nhật `formatters.js` hỗ trợ `locale` param
3. **Theme VibeND** — dark theme mới với CSS player và đăng ký vào `themes/index.js`
4. **Cải thiện UI/UX** — skeleton loading, breadcrumb, lazy routes, login gradient

---

## Nhiệm Vụ

- [ ] 1. Tạo bản dịch tiếng Việt (`vi.json`)
  - [ ] 1.1 Tạo `ui/src/i18n/vi.json` với đầy đủ translations cho mọi key trong `en.json`
    - Dịch toàn bộ các nhóm: `resources` (song, album, artist, user, player, transcoding, playlist, radio, share, missing, library, plugin), `ra`, `message`, `menu`, `player`, `about`, `activity`, `nowPlaying`, `help`
    - Đặt `languageName: "Tiếng Việt"`
    - Dùng thuật ngữ chuẩn: Bài hát / Album / Nghệ sĩ / Danh sách phát / Thể loại / Phát ngẫu nhiên / Yêu thích
    - Plural strings dùng form đơn giản (tiếng Việt không biến hình): `"Bài hát"` thay vì `"Bài hát |||| Bài hát"` — hoặc chỉ dùng form đơn, polyglot sẽ dùng toàn bộ chuỗi khi không có `||||`
    - _Yêu cầu: 1.1, 1.3, 1.4, 1.6_

  - [ ]* 1.2 Viết property test — Tính đầy đủ của Vi_Translation
    - **Property 2: Tính đầy đủ của Vi_Translation**
    - Dùng fast-check để kiểm tra mọi leaf key của `en.json` tồn tại trong `vi.json` và không rỗng (bỏ qua keys có `%{...}`)
    - **Validates: Yêu cầu 1.1**

  - [ ]* 1.3 Viết property test — Plural strings tiếng Việt
    - **Property 3: Plural strings tiếng Việt**
    - Với mọi entry trong `vi.json` chứa `"|||"`, phần trái và phải của `||||` phải giống nhau
    - **Validates: Yêu cầu 1.3**

  - [ ]* 1.4 Viết unit tests cho vi.json
    - Kiểm tra `languageName === "Tiếng Việt"`
    - Kiểm tra các thuật ngữ âm nhạc đặc trưng đúng: `resources.song.name`, `resources.artist.name`, `resources.playlist.name`
    - Kiểm tra `prepareLanguage` với `vi` data không throw error
    - _Yêu cầu: 1.4, 1.6_

- [ ] 2. Cập nhật `formatters.js` hỗ trợ locale tiếng Việt
  - [ ] 2.1 Thêm tham số `locale` vào `formatDuration2` trong `ui/src/utils/formatters.js`
    - Signature mới: `export const formatDuration2 = (totalSeconds, locale = 'en') => {...}`
    - Khi `locale === 'vi'`: dùng đơn vị `ng` (ngày), `g` (giờ), `ph` (phút), `gi` (giây)
    - Khi locale khác (mặc định): giữ nguyên `d`, `h`, `m`, `s`
    - Giữ nguyên logic tính toán hiện có, chỉ thay đơn vị chuỗi
    - Xử lý `totalSeconds == null || totalSeconds < 0` → trả về `'0gi'` khi `locale === 'vi'`
    - _Yêu cầu: 2.3_

  - [ ]* 2.2 Viết property test — Đơn vị thời gian tiếng Việt
    - **Property 6: Đơn vị thời gian tiếng Việt**
    - Dùng fast-check với `fc.integer({ min: 0, max: 86400 * 10 })`, kiểm tra `formatDuration2(seconds, 'vi')` không chứa ký hiệu đơn vị tiếng Anh (`d`, `h`, `m`, `s` đứng sau số)
    - **Validates: Yêu cầu 2.3**

  - [ ]* 2.3 Viết property test — Định dạng số locale vi
    - **Property 5: Định dạng số locale vi**
    - Dùng fast-check với `fc.integer({ min: 1000, max: 1_000_000 })`, kiểm tra `formatNumber(value, 'vi-VN')` chứa dấu chấm (`.`) phân cách hàng nghìn
    - **Validates: Yêu cầu 2.2**

  - [ ]* 2.4 Viết property test — Định dạng ngày tháng locale vi round-trip
    - **Property 4: Định dạng ngày tháng locale vi — round-trip**
    - Dùng fast-check với năm/tháng/ngày ngẫu nhiên hợp lệ, kiểm tra `formatFullDate(dateStr, 'vi-VN')` không rỗng và parse lại ra năm tương đương
    - **Validates: Yêu cầu 2.1, 2.5**

  - [ ]* 2.5 Viết unit tests cho formatters với locale vi
    - `formatDuration2(3661, 'vi')` → `"1g 1ph 1gi"`
    - `formatDuration2(0, 'vi')` → `"0gi"`
    - `formatDuration2(86400 + 3600 + 60, 'vi')` → `"1ng 1g 1ph"`
    - `formatFullDate('2024-03-15', 'vi-VN')` → chuỗi chứa `"tháng"`
    - _Yêu cầu: 2.1, 2.3_

- [ ] 3. Checkpoint — Đảm bảo tất cả tests qua
  - Đảm bảo mọi test liên quan đến bản dịch và formatters đều pass. Hỏi người dùng nếu có thắc mắc.

- [ ] 4. Tạo Theme VibeND
  - [ ] 4.1 Tạo `ui/src/themes/vibeND.css.js` — CSS player cho VibeND
    - Player panel background `#161B22`, border-top `1px solid #30363D`, `min-height: 72px`
    - Progress bar: `border-radius: 2rem; height: 4px` cho `.rc-slider-rail` và `.rc-slider-track`
    - Track color `#3FB950` (Spotify-like green) cho `.rc-slider-track`
    - Tham khảo `ui/src/themes/nord.css.js` để hiểu cấu trúc export
    - _Yêu cầu: 5.1, 7.4_

  - [ ] 4.2 Tạo `ui/src/themes/vibeND.js` — theme object đầy đủ
    - Import stylesheet từ `./vibeND.css.js`
    - `themeName: 'VibeND'`, `palette.type: 'dark'`
    - Palette: `primary.main: '#58A6FF'`, `secondary.main: '#3FB950'`, `background.default: '#0D1117'`, `background.paper: '#161B22'`, `text.primary: '#E6EDF3'`, `text.secondary: '#7D8590'`
    - MUI overrides: `NDAlbumGridView` (albumContainer, albumName, albumPlayButton), `NDAudioPlayer`, `MuiTableRow` hover `#1C2128`, `MuiAppBar`, `RaLayout`, `RaSidebar`, `MuiPaper`, `MuiButton`, `MuiToolbar`
    - Hover transitions `0.2s ease`, rounded corners `8px`, `albumContainer: translateY(-2px)` on hover
    - Typography: `albumName fontWeight: 700`, `songTitle fontWeight: 600`, `fontSize: '0.9375rem'`
    - Export: `player: { theme: 'dark', stylesheet }`
    - Tham khảo `nord.js` cho cấu trúc và patterns
    - _Yêu cầu: 5.1, 7.4_

  - [ ] 4.3 Đăng ký VibeND theme trong `ui/src/themes/index.js`
    - Import `VibeNDTheme from './vibeND'`
    - Thêm `VibeNDTheme` vào object export, theo thứ tự alphabet (sau `TokyoNightTheme`)
    - _Yêu cầu: 7.4_

  - [ ]* 4.4 Viết property test — Tỷ lệ tương phản màu VibeND
    - **Property 8: Tỷ lệ tương phản màu sắc VibeND theme**
    - Implement hàm `computeContrastRatio(fg, bg)` theo WCAG 2.1 (relative luminance)
    - Implement `extractColorPairs(theme)` để lấy các cặp (foreground, background) từ VibeND palette
    - Kiểm tra mọi cặp màu đạt tối thiểu 4.5:1 cho văn bản thường
    - **Validates: Yêu cầu 5.1**

  - [ ]* 4.5 Viết unit tests cho VibeND theme
    - `VibeNDTheme.themeName === 'VibeND'`
    - `VibeNDTheme.palette.type === 'dark'`
    - Theme được export đúng từ `themes/index.js`
    - Theme switching không cần reload trang (kiểm tra qua Personal settings state)
    - _Yêu cầu: 7.4_

- [ ] 5. Tạo các component UI mới
  - [ ] 5.1 Tạo `ui/src/common/SkeletonList.jsx` — skeleton loading component
    - Props: `variant` (`"album-grid"` | `"song-list"` | `"artist-list"`), `count` (số lượng items)
    - Dùng `@material-ui/lab/Skeleton` (kiểm tra xem lab đã có trong package.json chưa, nếu chưa dùng `makeStyles` để tạo placeholder div với CSS animation)
    - Màu nền placeholder dùng `theme.palette.action.hover` để tự adapt theo theme
    - `album-grid`: render grid các card hình vuông với subtitle placeholder
    - `song-list`: render các row với avatar placeholder + text placeholder
    - Export component và thêm vào `ui/src/common/index.js`
    - _Yêu cầu: 7.1_

  - [ ] 5.2 Tạo `ui/src/common/Breadcrumb.jsx` — breadcrumb navigation component
    - Props: `items: Array<{ label: string, to?: string }>` — item cuối không có `to` là trang hiện tại
    - Dùng `react-router-dom Link` cho các item có `to`, text thuần cho item cuối
    - Separator: `/` hoặc `>` giữa các item
    - Style: font nhỏ, màu `text.secondary`, item hiện tại in đậm và không có underline
    - Export component và thêm vào `ui/src/common/index.js`
    - _Yêu cầu: 6.3_

  - [ ]* 5.3 Viết unit tests cho SkeletonList và Breadcrumb
    - SkeletonList render đúng số `count` items
    - SkeletonList render các variant khác nhau không throw
    - Breadcrumb render đúng labels
    - Breadcrumb item có `to` render như Link, item không có `to` render như text
    - Item cuối (trang hiện tại) có `aria-current="page"`
    - _Yêu cầu: 7.1, 6.3_

- [ ] 6. Cập nhật `routes.jsx` với lazy loading
  - [ ] 6.1 Cập nhật `ui/src/routes.jsx` để lazy load các route ít dùng
    - Thêm `React.lazy()` cho: `TranscodingList`, `RadioList`, `MissingList`, `PluginList`
    - Bọc mỗi lazy component trong `<React.Suspense fallback={<Loading />}>`
    - Import `Loading` từ `react-admin`
    - Thêm `ErrorBoundary` bọc ngoài Suspense, fallback là component hiển thị lỗi đơn giản
    - Kiểm tra các đường dẫn import đúng với cấu trúc thư mục hiện có (transcoding/, radio/, missing/, plugin/)
    - _Yêu cầu: 7.5_

- [ ] 7. Cập nhật Login.jsx — gradient fallback
  - [ ] 7.1 Cập nhật `ui/src/layout/Login.jsx` — gradient background fallback
    - Trong `useStyles`, sửa style `main.background`:
      - Nếu `config.loginBackgroundURL` không rỗng: `background: \`url(${config.loginBackgroundURL}), ${fallbackGradient}\``
      - Nếu rỗng: `background: fallbackGradient`
    - `fallbackGradient` dùng `theme.palette.primary.main` và `theme.palette.background.default`:
      `\`linear-gradient(135deg, ${theme.palette.background.default} 0%, ${theme.palette.primary.main}22 100%)\``
    - Đảm bảo `main` style nhận `theme` từ makeStyles callback (đã có `(theme) => ({...})`)
    - _Yêu cầu: 8.4_

  - [ ]* 7.2 Viết unit tests cho Login gradient fallback
    - Khi `config.loginBackgroundURL = ''`, style `main.background` chứa `linear-gradient`
    - Khi `config.loginBackgroundURL` có giá trị, style chứa cả `url(...)` và gradient fallback
    - _Yêu cầu: 8.4_

- [ ] 8. Checkpoint cuối — Đảm bảo tất cả tests qua
  - Chạy toàn bộ test suite (`cd ui && yarn test --run` hoặc `jest --passWithNoTests`). Đảm bảo không có regression. Hỏi người dùng nếu có thắc mắc.

- [ ] 9. Fallback property — Kiểm tra i18n fallback
  - [ ] 9.1 Viết property test và unit test cho fallback tiếng Anh
    - **Property 1: Fallback về tiếng Anh khi thiếu key dịch thuật**
    - Dùng fast-check: với partial object không có key, sau `prepareLanguage({})` output phải bằng `en.json` cho mọi key kiểm tra
    - Unit test: import `prepareLanguage` từ `ui/src/i18n/provider.js`, gọi với object rỗng → `result.ra.action.save === en.ra.action.save`
    - _File: `ui/src/__tests__/i18n-fallback.test.js` (hoặc gần `provider.js`)_
    - **Validates: Yêu cầu 1.5**

  - [ ]* 9.2 Viết property test — Sắp xếp tiếng Việt locale-aware
    - **Property 7: Sắp xếp tiếng Việt locale-aware**
    - Dùng fast-check với mảng chuỗi Unicode ngẫu nhiên, kiểm tra kết quả sort bằng `Intl.Collator('vi-VN')` thỏa mãn transitivity: `sorted[i] <= sorted[i+1]`
    - **Validates: Yêu cầu 9.2**

---

## Ghi Chú

- Các task đánh dấu `*` là tùy chọn và có thể bỏ qua cho phiên bản MVP nhanh
- Mỗi task tham chiếu đến yêu cầu cụ thể để có thể truy vết
- Các checkpoint đảm bảo xác thực gia tăng sau mỗi mảng chức năng
- Property tests dùng `fast-check` — cần cài nếu chưa có: `yarn add --dev fast-check` trong thư mục `ui/`
- Kiểm tra `@material-ui/lab` trước khi dùng `Skeleton` — nếu chưa có thì dùng CSS animation thay thế
- Khi implement `SkeletonList`, tham khảo `ui/src/common/useImageLoadingState.js` để hiểu pattern xử lý loading state hiện có
- `formatDuration2` callers cần được cập nhật để truyền `locale` — tìm bằng `grep -r "formatDuration2" ui/src`
- Property tests và unit tests là bổ sung cho nhau, không thay thế nhau

---

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1", "4.1"] },
    { "id": 1, "tasks": ["1.2", "1.3", "1.4", "2.2", "2.3", "2.4", "2.5", "4.2"] },
    { "id": 2, "tasks": ["4.3", "5.1", "5.2", "6.1", "7.1", "9.1"] },
    { "id": 3, "tasks": ["4.4", "4.5", "5.3", "7.2", "9.2"] }
  ]
}
```
