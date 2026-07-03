# Requirements Document

## Introduction

Tính năng này bao gồm hai mảng lớn cho dự án Navidrome: (1) **Việt hoá (Vietnamization)** — thêm hỗ trợ đầy đủ ngôn ngữ tiếng Việt bao gồm bản dịch giao diện, định dạng ngày tháng/số theo chuẩn Việt Nam, và tích hợp vào hệ thống i18n hiện có; (2) **Tối ưu UI/UX** — cải thiện trải nghiệm người dùng toàn diện bao gồm responsive design, accessibility, navigation, visual consistency và hiệu năng rendering.

Navidrome hiện đã có nền tảng i18n dựa trên `ra-i18n-polyglot` với ngôn ngữ Tiếng Anh được đóng gói và các ngôn ngữ khác được tải động từ server qua POEditor. Giao diện được xây dựng bằng React 17, react-admin 3.x, Material UI v4.

---

## Bảng Thuật Ngữ

- **I18n_System**: Hệ thống nội địa hoá hiện có của Navidrome, bao gồm `ra-i18n-polyglot`, `provider.js`, `en.json`, và API endpoint `translation`.
- **Vi_Translation**: Tệp bản dịch tiếng Việt (`vi.json`) chứa toàn bộ chuỗi văn bản giao diện được dịch sang tiếng Việt.
- **Date_Formatter**: Module tiện ích xử lý định dạng ngày tháng, số và thời gian theo locale người dùng.
- **Language_Selector**: Thành phần `SelectLanguage.jsx` cho phép người dùng chọn ngôn ngữ giao diện.
- **UI_Layout**: Hệ thống bố cục giao diện bao gồm `Layout.jsx`, `AppBar.jsx`, `Menu.jsx` và các thành phần liên quan.
- **Theme_System**: Hệ thống theme của Navidrome, bao gồm nhiều theme như dark, light, dracula, nord, v.v.
- **Player_Component**: Thành phần trình phát nhạc `navidrome-music-player` hiển thị ở cuối màn hình.
- **Personal_Settings**: Trang cài đặt cá nhân (`Personal.jsx`) nơi người dùng có thể chọn ngôn ngữ, theme, và các tùy chỉnh khác.
- **Accessibility_Standard**: Tiêu chuẩn WCAG 2.1 AA cho khả năng tiếp cận web.
- **RTL**: Right-to-Left — hướng văn bản từ phải sang trái (không áp dụng cho tiếng Việt nhưng cần lưu ý trong kiến trúc).
- **Breakpoint**: Các điểm ngắt responsive: xs (<600px), sm (600-960px), md (960-1280px), lg (1280-1920px), xl (>1920px).

---

## Requirements

### Requirement 1: Tạo Bản Dịch Tiếng Việt Đầy Đủ

**User Story:** Là người dùng Việt Nam, tôi muốn toàn bộ giao diện Navidrome hiển thị bằng tiếng Việt, để tôi có thể sử dụng ứng dụng một cách tự nhiên mà không cần biết tiếng Anh.

#### Acceptance Criteria

1. THE Vi_Translation SHALL chứa bản dịch tiếng Việt cho tất cả các khoá chuỗi văn bản có trong `en.json`, bao gồm tất cả các nhóm: `resources`, `ra`, `message`, `menu`, `player`, `about`, `activity`, `nowPlaying`, và `help` — số lượng khoá trong `vi.json` phải bằng số lượng khoá trong `en.json`.
2. WHEN người dùng chọn "Tiếng Việt" từ Language_Selector, THE I18n_System SHALL tải và áp dụng Vi_Translation cho toàn bộ giao diện trong vòng 2 giây.
3. THE Vi_Translation SHALL sử dụng định dạng pluralisation phù hợp với tiếng Việt — vì tiếng Việt không phân biệt số ít/số nhiều về mặt hình thức, THE Vi_Translation SHALL sử dụng cùng một chuỗi cho cả hai dạng số lượng (ví dụ: "Bài hát" thay vì "Bài hát |||| Bài hát").
4. THE Vi_Translation SHALL đặt trường `languageName` là "Tiếng Việt" để hiển thị đúng trong Language_Selector.
5. IF Vi_Translation thiếu một khoá dịch thuật, THEN THE I18n_System SHALL hiển thị chuỗi tiếng Anh tương ứng từ `en.json` — không hiển thị khoá thô (raw key) hoặc chuỗi rỗng — và không ném lỗi JavaScript làm gián đoạn giao diện.
6. THE Vi_Translation SHALL sử dụng thuật ngữ âm nhạc chuẩn bằng tiếng Việt: "Bài hát" (song), "Album" (album — giữ nguyên), "Nghệ sĩ" (artist), "Danh sách phát" (playlist), "Thể loại" (genre), "Phát ngẫu nhiên" (shuffle), "Yêu thích" (favourite/starred).

---

### Requirement 2: Định Dạng Ngày Tháng và Số Theo Chuẩn Việt Nam

**User Story:** Là người dùng Việt Nam, tôi muốn ngày tháng và số hiển thị theo định dạng quen thuộc của Việt Nam, để tôi dễ đọc và hiểu thông tin.

#### Acceptance Criteria

1. WHEN locale của người dùng được đặt là `vi`, THE Date_Formatter SHALL định dạng ngày tháng đầy đủ theo chuẩn Việt Nam sử dụng `Intl.DateTimeFormat` với locale `vi-VN`, ví dụ ngày 15 tháng 3 năm 2024 phải hiển thị là "15 tháng 3, 2024".
2. WHEN locale của người dùng được đặt là `vi`, THE Date_Formatter SHALL định dạng số thập phân sử dụng dấu phẩy làm dấu phân cách thập phân và dấu chấm làm dấu phân cách hàng nghìn (ví dụ: "1.234,56" thay vì "1,234.56").
3. WHEN locale của người dùng được đặt là `vi`, THE Date_Formatter SHALL dịch đơn vị thời gian ngắn trong `formatDuration2` — "d" thành "ng", "h" thành "g", "m" thành "ph", "s" thành "gi".
4. THE Date_Formatter SHALL truyền locale hiện tại của người dùng vào các hàm `formatFullDate` và `formatNumber` dựa trên locale được lưu trong `localStorage`.
5. WHEN locale của người dùng được đặt là `vi` và một ngày tháng lịch hợp lệ (ngày tồn tại theo lịch Gregorian, ví dụ không phải ngày 30 tháng 2) được định dạng bởi `formatFullDate` rồi parse lại, THE Date_Formatter SHALL cho ra ngày, tháng, và năm trùng khớp với giá trị ngày gốc — đảm bảo round-trip này chỉ áp dụng cho các ngày hợp lệ; đầu vào ngày không hợp lệ có thể cho kết quả parse khác với giá trị gốc.

---

### Requirement 3: Tích Hợp Vi_Translation Vào Server và Hệ Thống POEditor

**User Story:** Là quản trị viên hệ thống, tôi muốn Navidrome tự động nhận diện và phục vụ bản dịch tiếng Việt, để người dùng có thể chọn tiếng Việt mà không cần cấu hình thêm.

#### Acceptance Criteria

1. WHEN một GET request được gửi tới endpoint `translation` với id là `vi` và tệp `vi` tồn tại trên server, THE I18n_System SHALL trả về HTTP 200 cùng Vi_Translation dưới dạng JSON hợp lệ với trường `id: "vi"` và `name: "Tiếng Việt"`. IF tệp bản dịch `vi` không tồn tại trên server, THEN THE I18n_System SHALL trả về HTTP 404 — không trả về HTTP 500 hoặc nội dung ngôn ngữ mặc định thay thế.
2. WHEN `defaultLanguage` trong cấu hình Navidrome được đặt là `vi` và người dùng chưa có giá trị `locale` trong localStorage, THE I18n_System SHALL tự động áp dụng tiếng Việt cho người dùng đó.
3. THE Language_Selector SHALL hiển thị "Tiếng Việt" trong danh sách ngôn ngữ có thể chọn, được sắp xếp theo thứ tự bảng chữ cái quốc tế (locale-aware sort).
4. WHERE cơ sở hạ tầng POEditor được sử dụng, THE I18n_System SHALL hỗ trợ đồng bộ Vi_Translation qua quy trình push/pull translations đã có của dự án.

---

### Requirement 4: Responsive Design Cho Màn Hình Di Động

**User Story:** Là người dùng trên thiết bị di động, tôi muốn giao diện Navidrome hiển thị và hoạt động tốt trên màn hình nhỏ, để tôi có thể quản lý thư viện nhạc và điều khiển trình phát thuận tiện.

#### Acceptance Criteria

1. WHEN chiều rộng viewport nhỏ hơn 600px (breakpoint xs), THE UI_Layout SHALL ẩn sidebar menu mặc định và hiển thị nút hamburger để mở menu.
2. WHEN chiều rộng viewport nhỏ hơn 600px, THE Player_Component SHALL co lại thành chế độ mini với chiều cao tối đa 64px, hiển thị tối thiểu: tên bài hát (truncated nếu dài hơn 30 ký tự), nút play/pause, và nút chuyển bài tiếp theo.
3. WHEN chiều rộng viewport nhỏ hơn 600px, THE UI_Layout SHALL hiển thị danh sách album, bài hát và nghệ sĩ trong bố cục một cột thay vì nhiều cột.
4. WHILE sidebar menu đang mở trên thiết bị di động (viewport chính xác nhỏ hơn 600px; tại đúng 600px áp dụng bố cục desktop), THE UI_Layout SHALL hiển thị overlay backdrop và đóng sidebar trong vòng 300ms khi người dùng tap vào backdrop.
5. THE UI_Layout SHALL sử dụng `touch-action: manipulation` trên tất cả các phần tử tương tác để loại bỏ delay 300ms trên thiết bị cảm ứng.
6. WHEN chiều rộng viewport từ 600px đến 960px (breakpoint sm), THE UI_Layout SHALL hiển thị ảnh bìa album ở kích thước từ 120px đến 160px nhưng vẫn giữ bố cục grid.

---

### Requirement 5: Cải Thiện Accessibility (Khả Năng Tiếp Cận)

**User Story:** Là người dùng sử dụng công nghệ hỗ trợ (screen reader, bàn phím), tôi muốn có thể điều hướng và sử dụng đầy đủ chức năng của Navidrome, để tôi có thể thưởng thức âm nhạc mà không bị cản trở bởi khiếm khuyết.

#### Acceptance Criteria

1. THE UI_Layout SHALL đảm bảo tỷ lệ tương phản màu sắc tối thiểu 4.5:1 cho văn bản thông thường (dưới 18pt hoặc dưới 14pt bold) và 3:1 cho văn bản lớn (từ 18pt trở lên hoặc từ 14pt bold trở lên) trong tất cả các theme mặc định, theo Accessibility_Standard WCAG 2.1 AA.
2. WHEN người dùng đang trong phiên điều hướng bằng bàn phím (sau khi nhấn Tab hoặc phím mũi tên), THE UI_Layout SHALL hiển thị focus indicator rõ ràng (outline tối thiểu 2px solid) trên tất cả các phần tử tương tác: nút, liên kết, input, và menu item. Focus indicator MAY được ẩn sau khi người dùng tương tác bằng chuột hoặc cảm ứng.
3. THE UI_Layout SHALL gán `aria-label` hoặc `aria-labelledby` cho tất cả các phần tử không có text hiển thị rõ ràng, bao gồm icon buttons trong Player_Component và LoveButton.
4. WHEN trạng thái trình phát thay đổi (play/pause/next/prev), THE Player_Component SHALL thông báo trạng thái mới thông qua vùng `aria-live="polite"` để screen reader đọc được mà không làm gián đoạn nội dung đang đọc.
5. THE UI_Layout SHALL đảm bảo thứ tự focus hợp lý theo DOM order — người dùng dùng Tab phải di chuyển qua AppBar, nội dung chính, rồi Player_Component theo thứ tự logic.
6. THE UI_Layout SHALL bao gồm liên kết "Skip to main content" ẩn (visually hidden) ở đầu trang, hiển thị khi được focus bằng bàn phím, để người dùng bàn phím có thể bỏ qua navigation.
7. WHEN Language_Selector hoặc bất kỳ dropdown nào mở ra, THE UI_Layout SHALL trap keyboard focus bên trong dropdown cho đến khi người dùng chọn xong hoặc nhấn Escape, và khi đóng, SHALL trả focus về phần tử đã kích hoạt dropdown.

---

### Requirement 6: Cải Thiện Navigation và Trải Nghiệm Điều Hướng

**User Story:** Là người dùng Navidrome, tôi muốn điều hướng giữa các mục trong thư viện nhạc một cách nhanh chóng và trực quan, để tôi tìm thấy bài hát, album hoặc nghệ sĩ mình muốn với ít bước nhất.

#### Acceptance Criteria

1. THE UI_Layout SHALL lưu trữ và khôi phục vị trí cuộn (scroll position) của danh sách album, bài hát và nghệ sĩ khi người dùng quay lại trang đó trong cùng một browser session (tab chưa đóng).
2. WHEN người dùng nhấp vào tên nghệ sĩ từ bất kỳ trang nào (trang bài hát, trang album, thông tin bài hát), THE UI_Layout SHALL điều hướng đến trang danh sách album của nghệ sĩ đó trong vòng 500ms — đây là giới hạn cứng (hard limit), không phải mục tiêu nỗ lực tốt nhất; điều hướng vượt quá 500ms là không tuân thủ.
3. THE UI_Layout SHALL hiển thị breadcrumb navigation trên các trang chi tiết (album detail, artist detail) để người dùng biết vị trí hiện tại và có thể quay lại trang trước.
4. WHEN người dùng nhập từ khoá vào thanh tìm kiếm và không có kết quả trả về, THE UI_Layout SHALL hiển thị thông báo trong vòng 1 giây bằng ngôn ngữ hiện tại và gợi ý hành động thay thế (ví dụ: "Không tìm thấy kết quả cho '%{query}'. Hãy thử từ khoá khác.").
5. THE UI_Layout SHALL cung cấp khả năng lọc nhanh (quick filter) ngay trong danh sách album và bài hát, giữ nguyên trạng thái filter khi người dùng điều hướng trong cùng một resource.

---

### Requirement 7: Cải Thiện Visual Consistency và Hiệu Năng Rendering

**User Story:** Là người dùng Navidrome, tôi muốn giao diện có visual nhất quán và phản hồi nhanh, để tôi có trải nghiệm sử dụng mượt mà và chuyên nghiệp.

#### Acceptance Criteria

1. THE UI_Layout SHALL hiển thị skeleton loading placeholders trong khi dữ liệu danh sách album, bài hát và nghệ sĩ đang được tải, thay vì hiển thị màn hình trống hoặc spinner đơn lẻ.
2. WHEN hình ảnh bìa album đang tải, THE UI_Layout SHALL hiển thị placeholder với màu nền phù hợp với theme hiện tại và icon âm nhạc, thay vì hình ảnh vỡ (broken image).
3. THE UI_Layout SHALL đảm bảo khoảng cách (spacing) nhất quán theo Material UI spacing scale (bội số của 8px) trong tất cả các thành phần được tuỳ chỉnh, bao gồm các thành phần trong `common/`, `album/`, `artist/`, và `song/`.
4. WHEN người dùng thay đổi theme từ Personal_Settings, THE Theme_System SHALL áp dụng theme mới trong vòng 300ms mà không cần reload trang.
5. THE UI_Layout SHALL lazy-load các trang không được truy cập khi khởi động (transcoding, radio, missing, plugin) để giảm thời gian tải ban đầu xuống dưới 3 giây trên kết nối 10Mbps.
6. WHEN danh sách bài hát vượt quá 200 mục, THE UI_Layout SHALL sử dụng virtualized rendering để chỉ render các hàng hiển thị trong viewport cộng thêm buffer 20 hàng phía trên và 20 hàng phía dưới, tránh giảm hiệu năng trên danh sách lớn.

---

### Requirement 8: Cải Thiện Trang Đăng Nhập (Login Page)

**User Story:** Là người dùng Navidrome, tôi muốn trang đăng nhập có trải nghiệm nhất quán với ngôn ngữ và theme đã được cấu hình, để tôi không bị gián đoạn khi đăng nhập.

#### Acceptance Criteria

1. THE UI_Layout SHALL hiển thị trang đăng nhập với ngôn ngữ theo thứ tự ưu tiên: (1) locale trong `localStorage` nếu tồn tại, (2) `defaultLanguage` trong server config nếu được đặt, (3) tiếng Anh làm fallback cuối cùng.
2. WHEN `defaultLanguage` được đặt là `vi` và không có locale nào trong `localStorage`, THE UI_Layout SHALL hiển thị nhãn "Tên đăng nhập", "Mật khẩu", và nút "Đăng nhập" trên trang login bằng tiếng Việt.
3. THE UI_Layout SHALL duy trì ngôn ngữ người dùng đã chọn sau khi đăng xuất và đăng nhập lại, đọc từ `localStorage` trước khi kiểm tra server config.
4. WHEN `loginBackgroundURL` được cấu hình và URL đó trả về HTTP response thành công, THE UI_Layout SHALL hiển thị CHỈ hình nền từ URL đó mà không hiển thị gradient overlay đồng thời. IF `loginBackgroundURL` không được cấu hình, hoặc request tới URL đó trả về HTTP 4xx/5xx, hoặc request timeout sau 5 giây, THEN THE UI_Layout SHALL hiển thị gradient background phù hợp với theme hiện tại thay vì nền trắng/đen.
5. THE UI_Layout SHALL hiển thị thông báo chào mừng (`welcomeMessage`) có hỗ trợ HTML đã được sanitize, cho phép admin nhúng nội dung song ngữ (Anh-Việt).

---

### Requirement 9: Hỗ Trợ Định Dạng Nội Dung Đa Ngôn Ngữ

**User Story:** Là người dùng có thư viện nhạc với tên bài hát và nghệ sĩ bằng tiếng Việt (có dấu), tôi muốn giao diện hiển thị và sắp xếp đúng các ký tự tiếng Việt, để tôi dễ dàng tìm kiếm và duyệt thư viện.

#### Acceptance Criteria

1. THE UI_Layout SHALL hiển thị đầy đủ các ký tự Unicode tiếng Việt (có dấu: à, á, ả, ã, ạ, ă, â, đ, ê, ô, ơ, ư, v.v.) trong tên bài hát, tên nghệ sĩ, và tên album — không hiển thị ký tự thay thế U+FFFD hoặc dấu hỏi thay cho ký tự tiếng Việt.
2. WHEN locale người dùng được đặt là `vi`, THE UI_Layout SHALL sắp xếp danh sách nghệ sĩ, bài hát và album sử dụng `Intl.Collator` với locale `vi-VN` — ví dụ "Đen" phải đứng sau "Dê" và trước "Em" trong thứ tự sắp xếp.
3. THE UI_Layout SHALL sử dụng font chứa đầy đủ glyph Unicode tiếng Việt trong tất cả các theme — font stack hiện tại của Material UI (Roboto, sans-serif) đã hỗ trợ Unicode và không cần thay đổi.
4. WHEN người dùng tìm kiếm với từ khoá có dấu tiếng Việt (ví dụ: "Sơn Tùng"), THE UI_Layout SHALL gửi query với encoding UTF-8 đúng cách và hiển thị kết quả khớp với từ khoá gốc. IF encoding UTF-8 thất bại, THEN THE UI_Layout SHALL không gửi search request và SHALL hiển thị thông báo lỗi cho người dùng.
5. WHEN người dùng tìm kiếm với từ khoá không dấu (ví dụ: "Son Tung"), THE UI_Layout SHALL chuyển query đến server mà không tự động biến đổi, và hiển thị kết quả theo đúng những gì server trả về.

---

### Requirement 10: Cài Đặt Ngôn Ngữ Mặc Định Toàn Hệ Thống

**User Story:** Là quản trị viên Navidrome, tôi muốn đặt ngôn ngữ mặc định là tiếng Việt cho toàn bộ hệ thống, để tất cả người dùng mới sẽ thấy giao diện tiếng Việt mà không cần tự cài đặt.

#### Acceptance Criteria

1. THE I18n_System SHALL hỗ trợ tuỳ chọn `DefaultLanguage = vi` trong tệp cấu hình Navidrome (`navidrome.toml` hoặc biến môi trường `ND_DEFAULTLANGUAGE=vi`).
2. WHEN `DefaultLanguage` được đặt là `vi` và người dùng chưa có `locale` trong localStorage, THE I18n_System SHALL tải và áp dụng Vi_Translation trong vòng 2 giây khi ứng dụng khởi tạo.
3. WHEN `DefaultLanguage` được đặt là `vi`, THE Language_Selector SHALL hiển thị "Tiếng Việt" là lựa chọn mặc định được chọn sẵn trong Personal_Settings.
4. IF `DefaultLanguage` được đặt là một locale không tồn tại trong hệ thống, THEN THE I18n_System SHALL tiếp tục khởi động ứng dụng bình thường (không có màn hình trắng hay crash) và áp dụng tiếng Anh làm ngôn ngữ mặc định một cách im lặng — không hiển thị thông báo lỗi hoặc cảnh báo cho người dùng.
5. THE I18n_System SHALL ưu tiên ngôn ngữ do người dùng tự chọn trong Personal_Settings (được lưu trong `localStorage`) hơn `DefaultLanguage` của server khi cả hai đều được đặt.
6. IF `DefaultLanguage` được đặt là một locale không hợp lệ và tiếng Anh được áp dụng làm fallback, THE Language_Selector SHALL vẫn cho phép người dùng chọn và sử dụng bất kỳ ngôn ngữ được hỗ trợ nào (bao gồm "Tiếng Việt") từ Personal_Settings.

---

### Requirement 11: Phát Lossless Bit-Perfect Trên Web

**User Story:** Là người dùng audiophile, tôi muốn Navidrome phát file FLAC/ALAC/WAV nguyên bản không qua transcoding lossy khi trình duyệt của tôi hỗ trợ, để tôi nhận được chất lượng âm thanh cao nhất có thể trên web.

#### Acceptance Criteria

1. WHEN trình duyệt hỗ trợ direct play định dạng lossless (FLAC, ALAC, WAV) theo kết quả `canPlayType()` trong `browserProfile.js`, THE Player_Component SHALL yêu cầu server stream file gốc không qua transcoding — không truyền tham số `maxBitRate` hay `format` giới hạn vào URL stream.
2. WHEN `decisionService` trả về quyết định `directPlay` cho một bài hát lossless (tức là `decision.transcodeParams` là null), THE Player_Component SHALL sử dụng `/rest/stream` endpoint trực tiếp thay vì `/rest/getTranscodeStream`.
3. THE Player_Component SHALL hiển thị badge "LOSSLESS" trong khu vực AudioTitle khi bài hát đang phát là định dạng lossless (FLAC, ALAC, WAV, DSF) và đang được direct play.
4. IF `bitDepth` của bài hát lớn hơn 16 bit (Hi-Res Audio), THEN THE Player_Component SHALL hiển thị badge "HI-RES" thay vì "LOSSLESS" để phân biệt với lossless CD quality.
5. WHEN trình duyệt không hỗ trợ định dạng lossless của bài hát gốc, THE Player_Component SHALL tự động fallback sang transcoding theo thứ tự ưu tiên trong `TRANSCODE_CODECS` (flac → opus → mp3), mà không yêu cầu thao tác thêm từ người dùng.
6. THE Player_Component SHALL hiển thị thông tin kỹ thuật của stream đang phát khi hover vào AudioTitle: sample rate (Hz), bit depth, bitrate (kbps), và trạng thái "Direct Play" hoặc "Transcoded to [format]".

---

### Requirement 12: Bảo Toàn Dải Động (Dynamic Range Preservation)

**User Story:** Là người dùng nghe nhạc chất lượng cao, tôi muốn âm thanh không bị clipping hay méo tiếng khi phát qua trình duyệt, để tôi nghe được dải động đầy đủ như tác giả sản xuất.

#### Acceptance Criteria

1. THE Player_Component SHALL áp dụng ReplayGain với hard limiter: giá trị `GainNode.gain.value` KHÔNG BAO GIỜ vượt quá 1.0 (0 dBFS) — công thức `min(10^((gain+preAmp)/20), 1/peak)` trong `calculateReplayGain.js` phải được áp dụng khi `gainMode` là `'album'` hoặc `'track'`.
2. WHEN `gainMode` là `'none'`, THE Player_Component SHALL không áp dụng bất kỳ gain nào (multiplier = 1.0) và SHALL trả về 1 từ `calculateGain`, để bảo toàn dải động gốc.
3. WHEN `rgTrackPeak` hoặc `rgAlbumPeak` của bài hát có giá trị lớn hơn 1.0, THE Player_Component SHALL áp dụng peak normalization (1/peak) ngay cả khi giá trị gain là 0 dB, để tránh clipping kỹ thuật số.
4. WHEN người dùng thay đổi `gainMode` hoặc `preAmp` trong Personal_Settings, THE Player_Component SHALL cập nhật `GainNode.gain.setValueAtTime` trong vòng một Web Audio quantum (~23ms) mà không làm gián đoạn playback đang chạy.
5. IF bài hát không có dữ liệu ReplayGain (cả `rgTrackGain` lẫn `rgAlbumGain` đều là undefined), THEN THE Player_Component SHALL phát ở unity gain (1.0) và hiển thị "No RG data" trong thông tin kỹ thuật khi hover AudioTitle.
6. THE Player_Component SHALL hiển thị khi hover vào AudioTitle: giá trị gain hiện đang áp dụng (dB), giá trị peak của bài hát, và chế độ ReplayGain đang dùng (Album/Track/Off).

---

### Requirement 13: Hiển Thị Thông Tin Chất Lượng Âm Thanh (Audio Quality HUD)

**User Story:** Là người dùng audiophile, tôi muốn thấy thông tin kỹ thuật chi tiết về stream đang phát ngay trong player, để tôi biết chính xác chất lượng âm thanh đang được delivery.

#### Acceptance Criteria

1. THE Player_Component SHALL hiển thị một Quality Badge compact trong khu vực AudioTitle với nội dung tóm tắt: định dạng (FLAC/MP3/Opus/...), sample rate (44.1k/96k/...), bit depth nếu lớn hơn 0 (16/24/32), và bitrate nếu là lossy codec.
2. WHEN người dùng hover hoặc click vào Quality Badge, THE Player_Component SHALL mở rộng hiển thị Quality Panel với thông tin đầy đủ: file format, codec, sample rate, bit depth, bitrate, channels, ReplayGain mode, gain value (dB), peak value, và trạng thái Direct Play hoặc Transcoded.
3. THE Quality Badge SHALL thay đổi màu sắc theo chất lượng stream: màu xanh lá (#3FB950) cho lossless hoặc hi-res, màu xanh dương (#58A6FF) cho lossy chất lượng cao (bitrate từ 256kbps trở lên), màu vàng (#E3B341) cho lossy chất lượng thấp (bitrate dưới 256kbps), và màu xám khi không xác định được chất lượng.
4. WHEN bài hát đang phát là radio stream (isRadio = true), THE Player_Component SHALL ẩn Quality Badge vì không có quality metadata cho stream radio.
5. WHEN locale là `vi`, THE Player_Component SHALL dịch Quality Badge và Quality Panel sang tiếng Việt: "Lossless" thành "Không nén", "Hi-Res" thành "Độ phân giải cao", "Transcoded" thành "Đã chuyển mã", "Direct Play" thành "Phát trực tiếp".
6. THE Player_Component SHALL persist trạng thái expanded/collapsed của Quality Panel vào `localStorage` với key `nd_quality_panel_expanded` để duy trì tuỳ chọn xem qua các phiên phát nhạc.
