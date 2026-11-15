# Business Definition: T4.3 — Đợt mở bán

**Status:** Gate A tự chốt bởi agent 2026-11-15 (quyền tự quyết trong phiên).
**Card:** [roadmap-3-9.md#t43](../roadmap-3-9.md) · **Nhánh:** `feature/t4-3-sale-batch` (từ `feature/t4-2-box-template` 7debb84)
**Tài liệu nguồn:** UC-59, UC-60, UC-61, UC-62, BR-10, BR-14, BR-15, BR-16, BR-18, BR-21, SD-03; T3.3 `StoreSalesPolicy`.

## 1. Bối cảnh

Đợt bán gắn mẫu với điều kiện thương mại: ngày bán, giá bán cố định, giá trị tối thiểu cam kết mỗi hộp, hạn đặt, khung giờ nhận, cảnh
báo (snapshot). Tạo đợt không tạo số lượng (UC-59 A1). Công khai chỉ khi cửa hàng ACTIVE, còn hạn đặt và có ít nhất một hộp thực tế đạt
yêu cầu (SD-03 #16–22). Lượng khả dụng luôn tính từ hộp thực tế (BR-15), không có trường "số lượng khai báo".

## 2. Khảo sát mã

- `sale_batch` (store, template (cùng store, FK ghép), sale_date, selling_price, minimum_value > 0 nguyên đồng, order_deadline ≤
  pickup_starts_at < pickup_ends_at (CHECK), safety_warnings, status ∈ DRAFT/OPEN/PAUSED/ENDED/BLOCKED, version); entity có sẵn.
  Không có cột lý do trạng thái.
- `physical_box` (current_batch_id, total_listed_value, safe_until, status ∈ AVAILABLE/RESERVED/ALLOCATED/DELIVERED/BLOCKED/
  AWAITING_INSPECTION), `box_allocation` (HELD/CONFIRMED/RELEASED/DELIVERED, unique một allocation đang hoạt động/hộp) — tạo ở T4.4/T5.2.
- `store_opening_hour` + `store.timezone` (T3.2/T3.3); `StoreSalesPolicy` (T3.3); `SaleBatchBlocker` (T3.3 chặn đợt khi khóa cửa hàng).
- T4.2 `BoxTemplate` (active). Migration mới nhất V9 → **V10**. Baseline 7debb84: 141 tests, 140 pass, 1 skipped.

## 3. Quyền

Ghi (tạo, sửa, công khai/tạm dừng/tiếp tục/kết thúc): owner (`SALE_BATCH_MANAGE` không seed; BR-10 staff không quyết giá).
Đọc (danh sách, chi tiết, số lượng hộp): owner, staff có BOX_PACK/BOX_MANAGE/PICKUP_HANDLE/ORDER_VIEW, ADMIN. Chặn đợt: ADMIN.

## 4. Luồng

- Tạo (UC-59): chọn mẫu đang dùng → giá bán, giá trị tối thiểu (mặc định từ mẫu), hạn đặt, khung nhận → kiểm tra số tiền, thứ tự thời
  gian, khung nhận nằm trọn trong một ca hoạt động theo múi giờ cửa hàng → lưu DRAFT, cảnh báo = cảnh báo mẫu.
- Sửa (UC-60): chỉ khi DRAFT/PAUSED; có đơn (bất kỳ trạng thái) → không đổi giá, giá trị tối thiểu, khung nhận, cảnh báo (BR-16); hộp
  không còn đạt điều kiện mới tự bị loại khỏi lượng khả dụng (A1) vì lượng luôn tính lại.
- Vận hành (UC-61): PUBLISH DRAFT→OPEN; PAUSE OPEN→PAUSED; RESUME PAUSED→OPEN (kiểm lại như công khai, A2); END DRAFT/OPEN/PAUSED→ENDED.
  Công khai/tiếp tục: store ACTIVE, mẫu đang dùng, now < hạn đặt, ≥ 1 hộp khả dụng; thiếu hộp → 409 `NO_ELIGIBLE_BOX`, đợt giữ nguyên.
- Admin chặn (card): OPEN/PAUSED/DRAFT → BLOCKED có lý do; BLOCKED/ENDED không mở lại.
- Số lượng (UC-62): đã đóng, đủ điều kiện, đang giữ, đã phân bổ, đã giao, bị chặn, chờ kiểm tra, khả dụng, thời điểm tính.

## 5. Quy tắc

- **BR-T43-01** Giá bán nguyên đồng 1..100tr; giá trị tối thiểu nguyên đồng ≥ giá bán (như T4.2).
- **BR-T43-02** `orderDeadline ≤ pickupStartsAt < pickupEndsAt`, `pickupEndsAt > now`, khung nhận ≤ 24h, `orderDeadline > now` khi tạo.
- **BR-T43-03** Khung nhận nằm trọn trong một ca (kể cả ca qua nửa đêm) theo `store.timezone`; `saleDate` = ngày địa phương của `pickupStartsAt`.
- **BR-T43-04** Hộp khả dụng = hộp của đợt có `status = AVAILABLE`, `total_listed_value ≥ minimum_value`, `safe_until ≥ pickup_ends_at`
  (BR-14, BR-18) và không có allocation HELD/CONFIRMED. Đủ điều kiện = AVAILABLE/RESERVED/ALLOCATED thỏa giá trị + an toàn.
  Đặt một chỗ (`BatchAvailability`) để T5.1/T5.2 dùng lại.
- **BR-T43-05** Chuyển trạng thái sai → 409 `CONFLICT`; khóa dòng đợt.
- **BR-T43-06** Mẫu ngừng dùng → không tạo/công khai đợt mới (400/409).
- **BR-T43-07** Audit `SALE_BATCH_CREATED`, `SALE_BATCH_UPDATED` (before/after), `SALE_BATCH_PUBLISHED|PAUSED|RESUMED|ENDED|BLOCKED`.

## 6. Phạm vi

Trong: API đợt (owner/admin), số lượng hộp, V10 (`sale_batch.status_reason`), OpenAPI, test (hộp/allocation chèn SQL vì T4.4/T5.2 chưa có).
Ngoài: đóng hộp (T4.4), tìm kiếm khách (T5.1), giữ hộp (T5.2), xử lý đơn khi đợt bị chặn/kết thúc (T7.x), D02 (giữ chỗ khi tạm dừng — T5.2).

## 7. Acceptance criteria

| ID | Given / When / Then |
|---|---|
| AC-01 | Owner tạo đợt hợp lệ từ mẫu → 201 DRAFT, giá/giá trị mặc định từ mẫu nếu không gửi, cảnh báo = cảnh báo mẫu, saleDate theo múi giờ, 0 hộp. |
| AC-02 | Giá ≤ 0/lẻ, giá trị < giá bán, thứ tự thời gian sai, khung nhận quá khứ/> 24h, ngoài giờ hoạt động, mẫu ngừng/khác cửa hàng → 400 field; không tạo. |
| AC-03 | Công khai khi chưa có hộp khả dụng → 409 `NO_ELIGIBLE_BOX`, vẫn DRAFT; có hộp đạt → OPEN. Hộp giá trị < tối thiểu hoặc safe_until < pickup_end không được tính. |
| AC-04 | Store SUSPENDED → công khai/tiếp tục 409 `STORE_NOT_ACTIVE`; quá hạn đặt → 409 `ORDER_DEADLINE_PASSED`. |
| AC-05 | Chuyển trạng thái sai (tạm dừng DRAFT, tiếp tục OPEN, mọi thao tác trên ENDED/BLOCKED) → 409; hai công khai đồng thời → một 200, một 409. |
| AC-06 | Sửa đợt OPEN → 409; sửa DRAFT/PAUSED hợp lệ → 200, audit; đợt có đơn → đổi giá/khung nhận → 409; version cũ → 409. |
| AC-07 | Số lượng: đúng từng nhóm với dữ liệu hộp/allocation mẫu; khả dụng = AVAILABLE đạt điều kiện và chưa giữ; tăng giá trị tối thiểu (khi PAUSED) làm hộp chưa đạt rơi khỏi khả dụng. |
| AC-08 | ADMIN chặn đợt có lý do → BLOCKED; owner không mở lại được (409); owner gọi API chặn → 403. |
| AC-09 | Quyền: staff (BOX_PACK) xem được, ghi → 403; khách/owner khác → 403; không token 401. OpenAPI có endpoint. |

## 8. Truy vết

| Nguồn | Đáp ứng |
|---|---|
| UC-59 bước 2–5, A1, E1; SD-03 #01–11 | AC-01, AC-02 |
| UC-60 bước 4–6, A1, E1; BR-16 | AC-06, AC-07 |
| UC-61 bước 2–5, A1–A3, E1, E2; SD-03 #12–24 | AC-03, AC-04, AC-05, AC-08 |
| UC-62 bước 3 | AC-07 |
| BR-10 (owner quyết giá) | AC-09 |
| BR-14, BR-15, BR-18 (chỉ hộp đạt; lượng từ hộp thực tế; an toàn ≥ khung nhận) | AC-03, AC-07 |
| BR-21 (đợt hoạt động, còn hạn, đủ hộp) | AC-03, AC-04 |
| Card: store bị khóa không mở; không có "số lượng khai báo" | AC-04, AC-07 |

## 9. Quyết định (agent tự chốt)

- [x] Q1 `saleDate` do server suy ra từ `pickupStartsAt` (địa phương) — tránh mâu thuẫn.
- [x] Q2 Khung nhận phải nằm trong giờ hoạt động (card); tối đa 24h.
- [x] Q3 Chỉ sửa khi DRAFT/PAUSED; có đơn → chỉ được đổi `orderDeadline` (vẫn ≤ pickupStartsAt).
- [x] Q4 Admin chặn = BLOCKED vĩnh viễn (như khóa cửa hàng T3.3); V10 lưu `status_reason`. Bỏ chặn: ngoài phạm vi.
- [x] Q5 Đường dẫn: `/api/v1/stores/{storeId}/sale-batches` (GET ?status, POST), `/{batchId}` (GET, PATCH), `/{batchId}/status`
  (POST `{action: PUBLISH|PAUSE|RESUME|END}`), `/{batchId}/box-counts` (GET); `/api/v1/admin/sale-batches/{batchId}/block` (POST).
