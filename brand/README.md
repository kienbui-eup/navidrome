# Troly Play — nhận diện

Nhánh giải trí của Troly (Evolaw). Kế thừa Troly v2.2 (`kienbui-eup/troly`, `brand/v2`); ở đây chỉ giữ phần mở rộng.
Bản dùng chung: Design System "Troly Play" trên claude.ai (tham chiếu Design System "Troly").

- `tokens.json` — toàn bộ token (Troly + Coral/Stage/tier, nhóm chữ TV, size media). Theme Navidrome `ui/src/themes/trolyPlay*.js` ánh xạ từ đây.
- `source/build_identity.py` — sinh logo: `python3 brand/source/build_identity.py --png brand/png`. Cần `fonttools`, `cairosvg`.
- `svg/` — wordmark EN/VI (một màu, hai màu), playmark, app icon, banner TV, icon trụ cột nội dung.
- `png/` — icon PWA/Android/iOS/Windows (đã copy sang `ui/public`, `ui/src/icons`, `resources`).

Quy tắc ngắn: T của Troly giữ nguyên, dấu vuông → tam giác Play cùng hộp 24×24. Coral chỉ cho hành động phát. Theme tối mặc định. Tên viết "Troly Play" / "Trợ lý giải trí".
