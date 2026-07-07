# Quality Upgrader — Tự động tìm bản nhạc chất lượng cao hơn

Ngày: 2026-07-06
Trạng thái: Đã duyệt thiết kế

## Mục tiêu

Tự động tìm phiên bản chất lượng cao hơn (bitrate, dynamic range, metadata) cho các track
trong thư viện, từ các nguồn import hiện có (Internet Archive, Google Drive, RSS).
Admin duyệt từng gợi ý trước khi tải và thay thế. Không tự động thay file khi chưa duyệt.

## Phạm vi

- Trong phạm vi: quét thư viện, tìm ứng viên, chấm điểm, hàng đợi duyệt, tải + xác minh,
  thay file an toàn, UI admin, lịch cron, API admin-only.
- Ngoài phạm vi: ghi/sửa tag file (Navidrome không ghi tag), nguồn trả phí/streaming DRM,
  transcode, tự thay không cần duyệt.

## Kiến trúc

Module riêng `core/upgrader.go` (phương án A), tái dùng pipeline của Importer:

```text
core/upgrader.go                 — orchestration, scoring, verify, replace
db/migrations/..._upgrade_candidate.go — bảng hàng đợi ứng viên
server/nativeapi/upgrade.go      — API admin-only
ui/src/upgrade/UpgradeQuality.jsx — trang duyệt của admin
```

Upgrader gọi các method có sẵn của Importer: `SearchArchive`, `ArchiveFiles`, `ListDrive`,
`ParseFeed`, và pipeline download (`downloadTo`/`persist`) — giữ nguyên SSRF guard,
giới hạn dung lượng, checksum dedup và audit log hiện có.

## Luồng xử lý

1. **Quét** (admin bấm nút hoặc cron): chọn các track "có thể nâng cấp".
2. **Tìm ứng viên** từ các nguồn bật trong config, có delay 1–2s giữa các request search.
3. **Chấm điểm giai đoạn 1** (trước tải): lọc theo format/bitrate ước tính từ metadata nguồn.
4. **Lưu hàng đợi** vào bảng `upgrade_candidate`, trạng thái `pending`.
5. **Admin duyệt** trên UI (lẻ hoặc hàng loạt).
6. **Tải về vùng tạm** qua pipeline Importer.
7. **Xác minh giai đoạn 2** (sau tải): ffprobe + dynamic range + metadata.
8. **Thay file** nếu đạt; backup bản cũ; trigger scan thư viện.

## Track "có thể nâng cấp"

Chỉ quét track format lossy (mp3, aac, ogg, opus, wma...). `Upgrade.MinBitRate` giới hạn
thêm trong nhóm lossy: chỉ quét track có bitrate dưới ngưỡng này; giá trị `0` (mặc định)
nghĩa là quét mọi track lossy.

Track lossless hiện có không bị quét (không có nhu cầu nâng cấp thực tế, tránh tốn quota).

## Matching

| Nguồn | Cách tìm | Cách khớp |
| --- | --- | --- |
| Internet Archive | `SearchArchive("artist title", mediatype:audio)` | Tên + duration ±5s (nếu nguồn có) |
| Google Drive | `ListDrive` folder đã cấu hình | Fuzzy match tên file với `artist - title` |
| RSS | `ParseFeed` các feed đã cấu hình | Match tiêu đề item |

- Match score 0–100 (thuật toán: chuẩn hóa chuỗi — bỏ dấu, lowercase, bỏ ký tự đặc biệt —
  rồi tính token-set similarity; cộng điểm nếu duration khớp, trừ điểm nếu tiêu đề chứa
  "live", "cover", "remix", "karaoke" mà bản gốc không có).
- Chỉ đưa vào hàng đợi nếu score ≥ `Upgrade.MinMatchScore` (mặc định 70).
- Score hiển thị trên UI để admin tự đánh giá rủi ro match sai.

## Chấm điểm chất lượng — giai đoạn 1 (trước tải)

So sánh theo thứ tự, ứng viên phải thắng tuyệt đối ở tiêu chí đầu tiên có chênh lệch:

1. Hạng format: lossless (FLAC/ALAC/WAV/AIFF) > lossy.
2. Bitrate ước tính cao hơn (cùng hạng format).
3. Sample rate / bit depth cao hơn.

Nguồn không khai báo bitrate (Drive/RSS): xếp hạng theo extension; bitrate thực xác minh
ở giai đoạn 2.

## Xác minh — giai đoạn 2 (sau khi duyệt, trước khi thay)

Thực hiện trên file đã tải về vùng tạm:

1. **ffprobe** (tái dùng `core/ffmpeg.AudioProbeResult`): codec, bitrate, sample rate,
   bit depth thực. Loại (`failed`) nếu không thắng bản cũ theo luật giai đoạn 1.
2. **Dynamic range**: `ffmpeg -af ebur128` đo Loudness Range (LRA) của cả file mới và cũ.
   Loại nếu LRA mới thấp hơn LRA cũ quá 2 LU (bản mới bị nén động học nặng hơn).
3. **Metadata**: đếm tag cốt lõi (title, artist, album, albumartist, tracknumber, year,
   genre, embedded cover). Nếu file mới thiếu tag mà file cũ có → chuyển `needs_review`;
   admin quyết ép thay (`force approve`) hoặc từ chối. Không tự ghi tag.

Kết quả xác minh (bitrate thực, LRA hai bản, so sánh tag) lưu vào record để UI hiển thị.

## Thay thế an toàn

- File mới ghi vào cùng thư mục file cũ, cùng tên gốc, extension theo format mới.
- File cũ chuyển vào `<DataFolder>/upgrade-backup/<ngày>/...` (giữ nguyên cây thư mục
  tương đối), tự xóa sau `Upgrade.BackupRetentionDays` (mặc định 30 ngày).
- Cùng vị trí + tag khớp → Persistent ID không đổi → giữ play count, rating, starred,
  playlist membership.
- Sau khi thay: trigger scan (tái dùng `TriggerScan`).
- Mọi thao tác thay thế ghi vào audit history của Importer.

## Lưu trữ

Bảng mới `upgrade_candidate` (migration mới):

| Cột | Kiểu | Ghi chú |
| --- | --- | --- |
| id | string PK | |
| media_file_id | string, FK media_file | track hiện tại |
| library_id | int | |
| source | string | `archive` / `drive` / `rss` |
| source_ref | string | identifier+filename / fileID / enclosure URL |
| title | string | tên hiển thị của ứng viên |
| format | string | extension/codec ước tính |
| est_bitrate | int | kbps, 0 nếu không rõ |
| est_size | int64 | bytes, 0 nếu không rõ |
| match_score | int | 0–100 |
| status | string | `pending/approved/rejected/downloading/needs_review/replaced/failed` |
| verify_info | JSON | kết quả giai đoạn 2 (bitrate thực, LRA, tag diff) |
| error | string | message khi failed |
| reviewed_by | string | user id admin đã duyệt |
| created_at, updated_at | time | |

Unique index `(media_file_id, source, source_ref)` — không gợi ý trùng. Candidate bị
reject không được tạo lại ở lần quét sau (tra unique index trước khi insert).

## Cấu hình

| Key | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `Upgrade.Enabled` | `false` | Bật/tắt toàn bộ tính năng |
| `Upgrade.Schedule` | `""` (tắt) | Cron expression quét định kỳ |
| `Upgrade.Sources` | `"archive"` | Danh sách nguồn: `archive,drive,rss` |
| `Upgrade.MinMatchScore` | `70` | Ngưỡng match score |
| `Upgrade.MinBitRate` | `0` | Chỉ quét track lossy có bitrate dưới ngưỡng; `0` = quét mọi track lossy |
| `Upgrade.MaxCandidatesPerScan` | `200` | Chặn quét quá lớn |
| `Upgrade.BackupRetentionDays` | `30` | Thời gian giữ file cũ; `<=0` = tắt prune |
| `Upgrade.DriveFolders` | `[]` | Folder Google Drive để tìm; rỗng = bỏ qua nguồn drive |
| `Upgrade.RSSFeeds` | `[]` | Feed RSS để tìm; rỗng = bỏ qua nguồn rss |

Cron dùng package `scheduler` có sẵn, đăng ký khi `Upgrade.Enabled=true` và
`Upgrade.Schedule` không rỗng.

## API (admin-only, dưới /api/upgrade)

| Method + Path | Chức năng |
| --- | --- |
| `POST /scan` | Bắt đầu quét; body: `{libraryId?, mediaFileIds?}` — rỗng = toàn thư viện |
| `GET /status` | Tiến độ quét đang chạy (đã quét/tổng, ứng viên tìm thấy) |
| `GET /candidates?status=` | Danh sách ứng viên, filter theo status, phân trang |
| `POST /candidates/{id}/approve` | Duyệt 1 ứng viên (body `{force: true}` cho needs_review) |
| `POST /candidates/{id}/reject` | Từ chối |
| `POST /candidates/approve-batch` | Duyệt hàng loạt theo danh sách id |

Tất cả endpoint yêu cầu quyền admin (middleware hiện có của nativeapi). Quét chỉ chạy
một job tại một thời điểm; gọi `/scan` khi đang chạy trả 409.

## UI

Trang admin mới `ui/src/upgrade/UpgradeQuality.jsx`, route `/upgrade`, chỉ hiện với admin:

- **Tab Chờ duyệt**: bảng mỗi dòng gồm — track hiện tại (format, bitrate) | ứng viên
  (format, bitrate ước tính, nguồn, match score) | nút Duyệt / Từ chối. Chọn nhiều dòng
  để duyệt hàng loạt. Dòng `needs_review` hiển thị tag diff và nút Ép thay.
- **Tab Lịch sử**: các candidate đã replaced/rejected/failed kèm kết quả xác minh.
- Nút **Quét ngay** (chọn thư viện hoặc toàn bộ) + hiển thị tiến độ quét.
- i18n đầy đủ tiếng Việt + English, theo theme VibeND, tuân thủ a11y hiện có.

## Xử lý lỗi

- Mỗi candidate độc lập: lỗi tải/xác minh → `failed` + error message, không chặn batch.
- Job quét hủy được (context cancellation, giống import job hiện có).
- Nguồn lỗi (IA timeout, Drive quota) → log warning, bỏ qua nguồn đó trong lần quét,
  các nguồn khác tiếp tục.
- Thay file là bước cuối và có backup — mọi lỗi trước đó không đụng file gốc.
- Server restart giữa chừng: candidate `downloading` quay về `approved` khi khởi động
  (tải lại được), không mất hàng đợi vì nằm trong DB.

## Kiểm thử

- Table-driven tests cho match score và luật chấm điểm giai đoạn 1.
- Verify pipeline test với fixture audio (mp3 128k vs flac) — skip nếu môi trường không
  có ffmpeg.
- Handler tests cho các endpoint (quyền admin, 409 khi quét trùng) theo convention
  ginkgo/gomega của project.
- Test thay thế: dùng thư mục tạm, kiểm tra backup + tên file + trigger scan được gọi.

## Quyết định đã chốt

1. Nguồn: tất cả nguồn import hiện có (IA + Drive + RSS).
2. Quy trình: gợi ý — admin duyệt, không tự thay.
3. Kích hoạt: thủ công + cron định kỳ.
4. Tiêu chí: 2 giai đoạn — format/bitrate trước tải; DR + metadata sau tải.
5. Kiến trúc: module Upgrader riêng, tái dùng pipeline Importer.

## Điều chỉnh khi triển khai (đã chốt trong quá trình thực thi)

- Thêm `Upgrade.DriveFolders` / `Upgrade.RSSFeeds` — Drive/RSS cần biết quét ở đâu;
  spec gốc thiếu.
- Similarity = Dice coefficient trên token set đã chuẩn hóa (không thêm dependency
  Levenshtein). Cả 3 nguồn đều ước tính chất lượng best-effort (extension class + regex
  parse bitrate/bit depth từ text); xác minh thực ở giai đoạn 2.
- Staging download tại `<DataFolder>/upgrade-staging` (ngoài library, scanner không
  thấy); tái dùng SSRF guard/size cap của Importer; checksum dedup không áp cho staging
  để force re-download hoạt động, audit vẫn ghi checksum lúc thay.
- `verifyInfo` JSON: `{actualBitRate, actualSampleRate, actualBitDepth, lraOld, lraNew,
  missingTags[]}` + optional `lraSkipped`, `stagedPath`. LRA đo lỗi hoặc thiếu ffmpeg →
  bỏ qua gate kèm warning, không fail candidate.
- Recovery khi khởi động re-enqueue cả `approved` lẫn `downloading`. Force flag không
  persist qua restart — resume như approve thường, nếu vẫn thiếu tag thì quay lại
  `needs_review`.
- Upgrader là singleton (`singleton.GetInstance`) — router, cron, recovery chung một
  instance/queue.
- API thêm `POST /scan/cancel`. Force-approve chỉ có ở endpoint đơn lẻ (batch không
  hỗ trợ force). Danh sách candidates sort `updatedAt` giảm dần, phân trang
  `_start/_end`, media file gốc bị xóa → trả field current* rỗng thay vì lỗi.
- Audit thay thế ghi vào import history với status `upgraded`.
