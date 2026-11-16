# Business Definition: T5.1 — Tìm kiếm Surprise Box và cửa hàng

**Status:** Gate A tự chốt bởi agent 2026-11-15 (quyền tự quyết trong phiên).
**Card:** [roadmap-3-9.md#t51](../roadmap-3-9.md) · **Nhánh:** `feature/t5-1-search` (từ `feature/t4-5-financial-policy` 6d8170a)
**Tài liệu nguồn:** UC-07, UC-08, UC-09, UC-10, UC-11, UC-12, BR-05, BR-19, BR-21, roadmap D10 (phải đăng nhập), T3.3 `StoreSalesPolicy`,
T4.3 `BatchAvailability`.

## 1. Bối cảnh

Khách đã đăng nhập tìm đợt Surprise Box đang bán gần mình, lọc theo nhóm/giá/giờ nhận, xem chi tiết (không lộ món thực tế — BR-05; có đủ
cảnh báo an toàn — BR-19), tìm cửa hàng theo tên, xem bản đồ, mở chỉ đường Google Maps (UC-12).

## 2. Khảo sát mã

- `sale_batch` (OPEN, order_deadline, giá, khung nhận, cảnh báo), `box_template` (tên, mô tả, nhóm, cover_file_id), `store` (tọa độ numeric,
  địa chỉ, status), `physical_box` + `box_item.safety_warnings_snapshot`, `box_allocation`.
- `BatchAvailability` (T4.3) định nghĩa hộp khả dụng; `StoreSalesPolicy` (T3.3) — chỉ ACTIVE; `GET /api/v1/stores/{id}` công khai (T3.3).
- File: `FileStorage.open(key)`; ảnh bìa mẫu chưa gắn attachment → khách chưa đọc được (giới hạn T4.2).
- Không có extension PostGIS; dữ liệu nhỏ → tính khoảng cách Haversine trong SQL. Baseline 6d8170a: 164 tests, 163 pass, 1 skipped.

## 3. Quyền

Mọi tài khoản đã đăng nhập (D10). Không token → 401.

## 4. Luồng

- Tìm đợt (UC-07, UC-08): vị trí (`lat`,`lng`) + bán kính (km) hoặc không vị trí (toàn bộ); lọc `categoryCode`, `minPrice`/`maxPrice`,
  `pickupFrom`/`pickupTo`; sắp xếp `distance` (khi có vị trí) / `price` / `pickupStartsAt`; phân trang ≤ 50.
- Chi tiết đợt (UC-10): nhóm, mô tả dự kiến, giá, giá trị tối thiểu, số khả dụng, hạn đặt, địa chỉ + tọa độ + link chỉ đường, khung nhận,
  cảnh báo đợt + cảnh báo của các hộp có thể được phân bổ (BR-19), `orderable` + lý do không đặt được (A2).
- Tìm cửa hàng (UC-09): theo tên (chứa, không phân biệt hoa thường) và/hoặc vị trí; kèm số đợt đang bán.
- Bản đồ (UC-11): cửa hàng ACTIVE trong khung tọa độ (`minLat..maxLat`, `minLng..maxLng`), cờ có hộp đang bán; tối đa 500 điểm.
- Chỉ đường (UC-12): `directionsUrl` Google Maps theo tọa độ (E1: thiếu tọa độ hợp lệ không xảy ra vì tọa độ bắt buộc).
- Ảnh bìa: `GET /api/v1/surprise-boxes/{batchId}/cover` khi đợt hiển thị được và mẫu có ảnh.

## 5. Quy tắc

- **BR-T51-01** Đợt "đang bán" = batch OPEN, `order_deadline > now`, store ACTIVE, số khả dụng (định nghĩa T4.3) > 0. Chỉ những đợt này có
  trong kết quả tìm kiếm và trong cờ `hasOpenOffers`.
- **BR-T51-02** Chi tiết đợt hiển thị được khi store ACTIVE và batch ∈ {OPEN, PAUSED, ENDED}; DRAFT/BLOCKED/không tồn tại → 404.
  `orderable` = đang bán; lý do khác: `PAUSED`, `ENDED`, `SOLD_OUT`, `DEADLINE_PASSED`.
- **BR-T51-03** Không bao giờ trả danh sách món, giá món, mã hộp (BR-05). Cảnh báo = cảnh báo đợt ∪ cảnh báo món của hộp đủ điều kiện.
- **BR-T51-04** Kiểm tra: `lat`/`lng` đi cặp, miền hợp lệ; `radiusKm` 0.1..50 (mặc định 5); `minPrice ≤ maxPrice`, ≥ 0; `pickupFrom < pickupTo`;
  khung bản đồ hợp lệ, không quá 2°×2°; `size` 1..50 → 400 field.
- **BR-T51-05** Khoảng cách Haversine, km, làm tròn 2 chữ số.

## 6. Phạm vi

Trong: 5 API công khai (đã đăng nhập), ảnh bìa, OpenAPI, test. Ngoài: tìm theo tên khu vực/địa giới hành chính (không có dữ liệu khu vực;
dùng vị trí/khung bản đồ), gợi ý cá nhân hóa, cache.

## 7. Acceptance criteria

| ID | Given / When / Then |
|---|---|
| AC-01 | Đợt OPEN còn hạn có hộp khả dụng của store ACTIVE trong bán kính → xuất hiện với giá, giá trị tối thiểu, số khả dụng, khoảng cách; ngoài bán kính → không. |
| AC-02 | Đợt PAUSED, ENDED, BLOCKED, DRAFT, hết hàng (0 khả dụng), quá hạn đặt, store SUSPENDED → không xuất hiện. |
| AC-03 | Lọc nhóm, khoảng giá, khoảng giờ nhận cho đúng tập; sắp xếp khoảng cách/giá. |
| AC-04 | Khoảng giá ngược, giờ ngược, chỉ có lat, bán kính > 50, size > 50 → 400 field. |
| AC-05 | Chi tiết đợt: không có món/giá món/mã hộp; có cảnh báo đợt + cảnh báo món của hộp; link chỉ đường; đợt PAUSED → orderable=false lý do PAUSED; hết hàng → SOLD_OUT; DRAFT/BLOCKED/store khóa → 404. |
| AC-06 | Tìm cửa hàng theo tên và vị trí → chỉ store ACTIVE, có `openOffers`. Bản đồ trả điểm trong khung, cờ `hasOpenOffers`; khung quá lớn → 400. |
| AC-07 | Ảnh bìa của đợt hiển thị được tải được bởi khách; đợt DRAFT → 404. Không token → 401 mọi API. OpenAPI có endpoint. |

## 8. Truy vết

| Nguồn | Đáp ứng |
|---|---|
| UC-07 bước 1–3, A1 (khu vực thủ công = khung bản đồ/không vị trí), A2 | AC-01, AC-02 |
| UC-08 bước 2–5, E1 | AC-03, AC-04 |
| UC-09 bước 1–3 | AC-06 |
| UC-10 bước 3, A2 | AC-05 |
| UC-11 bước 1–3, A2 | AC-06 |
| UC-12 bước 2–3 | AC-05 (`directionsUrl`) |
| BR-05 | AC-05 |
| BR-19 | AC-05 |
| BR-21, T3.3, T4.3 | AC-01, AC-02 |
| D10 | AC-07 |

## 9. Quyết định (agent tự chốt)

- [x] Q1 "Khu vực" = vị trí + bán kính hoặc khung bản đồ (không có dữ liệu địa giới).
- [x] Q2 Chi tiết đợt PAUSED/ENDED vẫn xem được với `orderable=false` (UC-10 A2); DRAFT/BLOCKED → 404.
- [x] Q3 Đường dẫn: `/api/v1/surprise-boxes` (GET), `/api/v1/surprise-boxes/{batchId}` (GET), `/api/v1/surprise-boxes/{batchId}/cover` (GET),
  `/api/v1/store-search` (GET), `/api/v1/store-map` (GET) — tránh đụng `/api/v1/stores/{storeId}` của T3.3.
