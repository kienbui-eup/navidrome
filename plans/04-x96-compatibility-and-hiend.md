# Plan 04 — Sửa lỗi cài đặt trên X96 + tiếp tục tối ưu hi-end

Ngày: 2026-07-08
Trạng thái: Chưa thực thi
Bối cảnh: Cài APK lên box X96 báo "không tương thích với thiết bị". Plan này chẩn đoán và sửa dứt điểm việc cài đặt, rồi nối tiếp roadmap hi-end (tham chiếu báo cáo research `~/Documents/vi2play_HiEnd_Streaming_Research_20260708/`).

Mỗi phase tự chứa, có thể chạy ở context mới. Mọi tham chiếu đều là file:line thực tế tại thời điểm lập plan (nhánh `feat/library-admin-auto-playlists`).

---

## Phase 0 — Sự thật đã xác minh (Allowed changes & Anti-patterns)

Không giả định API. Đây là dữ kiện đã đọc trực tiếp từ code.

### Nguyên nhân gốc (đã xác minh)
Build hiện chỉ tạo hai APK: `app-arm64-v8a-release.apk` và `app-x86_64-release.apk` (thư mục `android/app/build/outputs/apk/release/`). Không có bản 32-bit nào. Chuỗi giới hạn ABI ở ba nơi:
- `android/app/build.gradle.kts:35-42` — `splits { abi { include("arm64-v8a","x86_64"); isUniversalApk=false } }`.
- `android/decent-player/libs/decent-usb-audio-driver/build.gradle.kts:14` — `ndk { abiFilters += listOf("arm64-v8a","x86_64") }`.
- `android/decent-player/libs/decent-media3-decoder-flac/build.gradle.kts:18` — `ndk { abiFilters += listOf("arm64-v8a","x86_64") }`.

Ba nghi phạm cho "không tương thích", theo thứ tự khả năng:
1. Box chạy Android **32-bit userland** (chỉ expose `armeabi-v7a`) → APK arm64 bị từ chối. Phổ biến nhất trên Amlogic S905.
2. Cài **nhầm** `app-x86_64-release.apk` lên box ARM → luôn báo không tương thích.
3. Box chạy Android < 10 (API < 29) → `minSdk=29` chặn (`app/build.gradle.kts:25`, và cả 3 module native đều `minSdk=29`).

### Sự thật về khả năng build 32-bit (đã xác minh)
- Lý do lịch sử loại 32-bit ("libFLAC tắt fseeko") **đã không còn hiệu lực** trong cấu hình hiện tại: cả hai wrapper CMake ép `set(HAVE_FSEEKO 1 ...)` trước khi add libflac (`decent-usb-audio-driver/src/main/jni/CMakeLists.txt:9-11`, `decent-media3-decoder-flac/src/main/jni/CMakeLists.txt:27-29`); `minSdk=29 > 24` nên nhánh disable trong `libflac/CMakeLists.txt:125-132` không kích hoạt; `config.cmake.h.in:206-208` đặt `_FILE_OFFSET_BITS 64`.
- Driver native dùng `off64_t`/`int64_t` và struct usbdevfs sized runtime → không có giả định 64-bit (`native-audio-engine.cpp`, `usb-audio-output.cpp`). ABI-clean.
- Wrapper media3 là pure-Kotlin, không native.
- Kết luận: thêm `armeabi-v7a` **dự kiến chỉ là đổi flag**, không sửa code. Rủi ro duy nhất còn lại: compile NEON intrinsics của FLAC cho armv7-a (chưa build thử) → phải verify bằng build thật.

### Lưu ý môi trường build
- `libflac/` là source clone qua `decent-player` setup (`docs/libs/DECODER_FLAC_BUILD.md`, bước `git clone https://github.com/xiph/flac.git --depth=1 libflac`). Trên checkout sạch có thể vắng → build sẽ fail vì thiếu source, không liên quan ABI. Kiểm tra tồn tại trước khi build.
- adb có sẵn tại `~/Library/Android/sdk/platform-tools/adb` (không trong PATH).

### Anti-patterns (KHÔNG làm)
- KHÔNG hạ `minSdk` xuống dưới 29 để "cho dễ cài" — driver USB isochronous cần usbdevfs ABI của Android 10+ (`app/build.gradle.kts:23-24`). Hạ sẽ crash runtime khi cắm DAC.
- KHÔNG bật `isUniversalApk=true` như cách sửa duy nhất khi box là 32-bit — universal APK vẫn cần chứa `.so` armeabi-v7a; nếu không thêm ABI đó vào abiFilters thì universal vẫn thiếu lib 32-bit và vẫn fail runtime.
- KHÔNG đoán box là 64-bit hay 32-bit — phải đọc `getprop` thực tế (Phase 1).
- KHÔNG sửa abiFilters của module mà quên sửa `splits` ở app (hoặc ngược lại) — phải đồng bộ cả ba nơi ở Phase 0.

---

## Phase 1 — Chẩn đoán trên chính box X96 (DECISION GATE)

Mục tiêu: xác định chính xác nghi phạm nào trong 3 cái ở Phase 0, để chọn nhánh sửa ở Phase 2. Không sửa code cho tới khi có kết quả.

### Chuẩn bị kết nối adb
1. Trên X96: Settings → About → bấm Build number 7 lần để mở Developer options; bật **USB debugging** và **ADB over network** (nếu có). Ghi lại IP LAN của box (Settings → Network).
2. Trên máy Mac:
   ```bash
   ADB=~/Library/Android/sdk/platform-tools/adb
   $ADB connect <IP_X96>:5555
   $ADB devices   # phải thấy box ở trạng thái "device"
   ```
   Nếu box không có ADB-over-network, cắm cáp USB từ Mac tới box và dùng `$ADB devices` trực tiếp.

### Thu thập dữ kiện (chạy hết, copy output vào plan/notes)
```bash
$ADB shell getprop ro.product.cpu.abi
$ADB shell getprop ro.product.cpu.abilist
$ADB shell getprop ro.product.cpu.abilist32
$ADB shell getprop ro.product.cpu.abilist64
$ADB shell getprop ro.build.version.sdk       # API level (29 = Android 10)
$ADB shell getprop ro.build.version.release    # ví dụ "14"
$ADB shell getprop ro.product.model
```

### Xác nhận nguyên nhân trực tiếp: thử cài và đọc lỗi
```bash
$ADB install -r android/app/build/outputs/apk/release/app-arm64-v8a-release.apk
```
Diễn giải mã lỗi:
- `INSTALL_FAILED_NO_MATCHING_ABIS` → **ABI mismatch** (nghi phạm #1). Đối chiếu `abilist64`: nếu rỗng → box 32-bit → nhánh **2B**. Nếu `abilist64` có `arm64-v8a` mà vẫn lỗi này thì bất thường, kiểm tra lại file APK.
- `INSTALL_FAILED_OLDER_SDK` → **Android quá cũ** (nghi phạm #3) → nhánh **2C**.
- Cài **thành công** → nghĩa là trước đó bạn đã cài nhầm bản `x86_64` (nghi phạm #2) → nhánh **2A** (thực ra đã xong, chỉ cần xác nhận).

### Verification checklist Phase 1
- [ ] Có output đầy đủ 7 lệnh getprop.
- [ ] Biết `abilist64` rỗng hay không (quyết định 32/64-bit).
- [ ] Biết `ro.build.version.sdk` ≥ 29 hay không.
- [ ] Có mã lỗi cụ thể của lệnh `adb install`.
- [ ] Đã chọn được nhánh 2A / 2B / 2C.

---

## Phase 2A — Box là 64-bit (chỉ cài nhầm APK)

Điều kiện vào: `abilist64` chứa `arm64-v8a`, `sdk ≥ 29`, và `adb install app-arm64-v8a-release.apk` thành công.

Không cần sửa code. Việc cần làm:
1. Cài đúng bản: `$ADB install -r android/app/build/outputs/apk/release/app-arm64-v8a-release.apk` (KHÔNG dùng bản `x86_64` — bản đó chỉ cho emulator, `app/build.gradle.kts:34`).
2. Nếu sideload thủ công (USB stick / file manager trên box), đảm bảo copy đúng file `app-arm64-v8a-release.apk`, không phải `x86_64`.
3. Ghi chú vận hành: đổi tên/đóng gói phát hành để tránh nhầm lần sau (xem Phase 3, mục đóng gói).

Verification: app xuất hiện ở cả launcher thường lẫn Android TV launcher, mở được. → sang Phase 3.

---

## Phase 2B — Box là 32-bit (thêm armeabi-v7a) [nhánh nhiều khả năng nhất]

Điều kiện vào: `abilist64` rỗng / `abilist` chỉ có `armeabi-v7a,armeabi`, và `adb install` báo `INSTALL_FAILED_NO_MATCHING_ABIS`.

### Bước 1 — Đảm bảo source libflac tồn tại
```bash
ls android/decent-player/libs/decent-media3-decoder-flac/src/main/jni/libflac/CMakeLists.txt
```
Nếu thiếu: chạy script setup của decent-player (theo `android/decent-player/docs/libs/DECODER_FLAC_BUILD.md`) để clone xiph/flac vào đúng vị trí. KHÔNG build tiếp khi thiếu source.

### Bước 2 — Thêm armeabi-v7a vào ĐÚNG BA NƠI (đồng bộ)
Đây là thay đổi flag, sao chép chính xác pattern đang có, chỉ thêm một phần tử list:
1. `android/decent-player/libs/decent-usb-audio-driver/build.gradle.kts:14`
   `abiFilters += listOf("arm64-v8a", "x86_64")` → thêm `"armeabi-v7a"`.
2. `android/decent-player/libs/decent-media3-decoder-flac/build.gradle.kts:18`
   `abiFilters += listOf("arm64-v8a", "x86_64")` → thêm `"armeabi-v7a"`.
3. `android/app/build.gradle.kts:39`
   `include("arm64-v8a", "x86_64")` → thêm `"armeabi-v7a"`.

Cập nhật luôn comment ở `app/build.gradle.kts:29-30` và hai comment module (driver:13, decoder-flac:17) để không còn ghi "64-bit only" gây hiểu nhầm về sau.

### Bước 3 — Build và xử lý rủi ro NEON
```bash
cd android && ./gradlew :app:assembleRelease
```
- Thành công → sẽ có `app-armeabi-v7a-release.apk` trong `app/build/outputs/apk/release/`.
- Nếu FAIL ở compile FLAC intrinsics cho armv7-a (các file `libflac/src/libFLAC/*_intrin_*.c` hoặc lỗi NEON): tắt tối ưu asm/intrinsic của FLAC cho ABI này. Cách làm (theo CMake của xiph/flac): truyền `-DWITH_ASM=OFF` cho externalNativeBuild của module decoder-flac (thêm vào `externalNativeBuild.cmake.arguments` trong `decent-media3-decoder-flac/build.gradle.kts`). Đây là đánh đổi tốc độ decode nhỏ trên 32-bit, chấp nhận được. Chỉ áp cho armv7-a nếu có thể tách; nếu không, áp chung rồi đo lại hiệu năng.
- Nếu FAIL vì lý do khác: đọc log CMake, KHÔNG vá bừa — báo lại với log cụ thể.

### Bước 4 — Cài bản 32-bit lên box
```bash
$ADB install -r android/app/build/outputs/apk/release/app-armeabi-v7a-release.apk
```

Verification checklist Phase 2B:
- [ ] Ba nơi abiFilters/splits đã có `armeabi-v7a` (grep xác nhận: `grep -rn armeabi-v7a android/app/build.gradle.kts android/decent-player/libs/*/build.gradle.kts`).
- [ ] `assembleRelease` tạo ra `app-armeabi-v7a-release.apk`.
- [ ] `adb install` trả về `Success`.
- [ ] Không hạ minSdk, không đụng code Kotlin/C++ (trừ cờ WITH_ASM nếu buộc phải).

---

## Phase 2C — Android trên box quá cũ (API < 29)

Điều kiện vào: `ro.build.version.sdk` < 29, `adb install` báo `INSTALL_FAILED_OLDER_SDK`.

Đây là ràng buộc thật, không nên hạ đại. Lựa chọn, theo thứ tự ưu tiên:
1. Cập nhật firmware box lên Android 10+ nếu có (nhiều X96 có bản ROM mới hơn). Đây là hướng đúng nhất vì giữ được đường bit-perfect USB.
2. Nếu bắt buộc chạy trên box cũ và **chấp nhận mất bit-perfect USB**: tạo một product flavor riêng minSdk thấp hơn, LOẠI ba module `decent-*` (chỉ dùng ExoPlayer thường). Đây là thay đổi lớn (tách flavor, guard mọi call tới `BitPerfectRenderersFactory`), chỉ làm nếu thực sự cần. Ghi rõ đây là bản "không hi-end".

Verification: xác nhận API level box; quyết định update firmware hay tách flavor; nếu tách flavor, app chạy được không cần module USB.

---

## Phase 3 — Verify cài đặt + playback thực tế trên X96 (bắt buộc, HDMI)

Không tuyên bố "đã sửa" cho tới khi thấy app phát nhạc trên chính box.

1. Mở app từ **Android TV launcher** (LEANBACK) trên X96, điều hướng bằng remote D-pad (`ui/tv/TvApp.kt`).
2. Đăng nhập server vi2play, phát một bài FLAC. Xác nhận có tiếng ra TV qua HDMI.
3. Đo đường tín hiệu (liên quan Finding của research — HDMI có bị resample 48k không): phát file 24/96, kiểm tra sample-rate thực ở đầu ra (nếu có AVR hiển thị, hoặc `adb shell dumpsys media.audio_flinger | grep -i sampl`). Ghi lại kết quả — đây là dữ kiện cho Phase 4.
4. Nếu có USB DAC: cắm vào cổng USB của X96, xác nhận app claim DAC (intent `USB_DEVICE_ATTACHED`, `AndroidManifest.xml:43-48`) và phát bit-perfect.
5. Đóng gói phát hành: đặt tên rõ ràng cho từng APK khi giao cho người dùng (ví dụ thư mục `release/x96/` chứa đúng bản ABI của box) để tránh tái diễn lỗi cài nhầm.

Verification checklist:
- [ ] App mở từ TV launcher, D-pad điều hướng được.
- [ ] Phát FLAC ra HDMI có tiếng.
- [ ] Đã ghi lại sample-rate thực khi phát 24/96 qua HDMI.
- [ ] (Nếu có DAC) bit-perfect USB hoạt động.

---

## Phase 4+ — Tiếp tục tối ưu hi-end (follow-on, tham chiếu research report)

Chỉ bắt đầu sau khi app đã cài và phát được trên X96. Chi tiết đầy đủ + dẫn nguồn trong báo cáo `~/Documents/vi2play_HiEnd_Streaming_Research_20260708/research_report_20260708_vi2play_hiend_streaming.md`. Tóm tắt để nối tiếp:

- **P0 — ReplayGain trên Android**: xác nhận app chưa áp gain, rồi áp `RGTrackGain`/`RGAlbumGain` từ metadata server (backend đã trả sẵn — `model/mediafile.go:86-88`). Đồng bộ với web player. Fix rẻ, tác động rõ.
- **P0 — Signal-path indicator**: hiển thị trong player đang phát định dạng gì / qua đường nào (bit-perfect USB / HDMI 48k / transcode). Biến chất lượng vô hình thành thứ người dùng tin được.
- **P0 — Cấu hình server**: tăng `TranscodingCacheSize` (mặc định 100MB, `configuration.go:808`) lên 1–4GB; bật `Transcoding.MaxConcurrent`/`MaxConcurrentPerUser` (mặc định 0=unlimited, `configuration.go:901-903`) để bảo vệ CPU VM.
- **P1 — Gapless web player Aonsoku**: mô hình một `<audio>` swap-src (`player.tsx:238-252`) không thể gapless; cần hai phần tử `<audio>` luân phiên + preload, hoặc Web Audio buffer scheduling. Merge nhánh `feat/aonsoku-player` vào dòng chính.
- **P1 — Kiểm chứng gapless Android** với album live/classical.
- **P2 — AAudio bit-perfect mode (Android 14)** trên X96 để có hi-res không cần USB DAC; EQ/crossfade tiệm cận Symfonium.

Mỗi mục trên nên tách thành plan riêng khi thực thi (dùng lại make-plan), không gộp vào plan cài đặt này.

---

## Final Phase — Verification tổng

1. `grep -rn "armeabi-v7a" android/app/build.gradle.kts android/decent-player/libs/*/build.gradle.kts` — nếu đi nhánh 2B, phải thấy đủ 3 nơi.
2. `ls android/app/build/outputs/apk/release/` — có đúng bản APK cho ABI của box.
3. `adb shell pm list packages | grep me.troly.nhac` — app đã cài trên box.
4. Phát nhạc thực tế trên X96 thành công (Phase 3).
5. Không vi phạm anti-pattern Phase 0 (minSdk không bị hạ; abiFilters đồng bộ; không bật universal APK như fix duy nhất cho box 32-bit).
6. `cd android && ./gradlew :app:assembleRelease` build sạch.
