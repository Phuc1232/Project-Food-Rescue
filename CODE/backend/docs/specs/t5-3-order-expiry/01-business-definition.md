# Business Definition: T5.3 — Hết hạn giữ hộp và theo dõi đơn

**Status:** Gate A tự chốt bởi agent 2026-11-15 (quyền tự quyết trong phiên).
**Card:** [roadmap-3-9.md#t53](../roadmap-3-9.md) · **Nhánh:** `feature/t5-3-order-expiry` (từ `feature/t5-2-order-hold` 7e1c26c)
**Tài liệu nguồn:** UC-13 E2, UC-17 A2, UC-19, UC-20, UC-21 (khung — mã nhận ở T7.1), UC-22, UC-54, UC-55, BR-23, BR-24, BR-26.

## 1. Bối cảnh

Đơn `PENDING_PAYMENT` quá 10 phút hết hiệu lực (BR-23): hộp được nhả và chỉ bán lại nếu còn đủ điều kiện (BR-24). Khách hủy được đơn chưa
thanh toán, không tạo giao dịch hoàn 0đ (UC-17 A2). Khách xem danh sách/chi tiết/khoản tiền của đơn; cửa hàng xem đơn của mình.

## 2. Khảo sát mã

- `customer_order` (status, hold_expires_at; trigger V3 `protect_order_terms` chỉ cho đổi status, pickup_code_hash, completed_at, updated_at,
  version — **không có cột lý do trạng thái**), `box_allocation` (HELD → RELEASED cần released_at + release_reason), `physical_box` RESERVED.
- `payment`, `refund` (giai đoạn 6) — đọc tổng đã xác minh / đã hoàn để hiển thị (hiện bằng 0).
- `@EnableScheduling` đã bật (RateLimitCleanupJob). T5.2 khóa đơn qua tạo mới; T6.3 webhook sẽ khóa đơn FOR UPDATE.
- Migration mới nhất V13 → **V14**. Baseline 7e1c26c: 178 tests, 177 pass, 1 skipped.

## 3. Quyền

Khách: chỉ đơn của mình (khác → 404). Cửa hàng: owner hoặc staff `ORDER_VIEW` (khác → 403). Job: hệ thống.

## 4. Luồng

- **Hết hạn (BR-23, BR-24, UC-13 E2):** job định kỳ (30 giây) lấy đơn PENDING_PAYMENT có `hold_expires_at ≤ now` bằng
  `FOR UPDATE SKIP LOCKED` (lô ≤ 100), mỗi đơn: EXPIRED + lý do, allocation HELD → RELEASED ("HOLD_EXPIRED"), hộp RESERVED → AVAILABLE
  (công thức khả dụng T4.3 tự loại hộp không còn đạt). Hai instance/hai lần chạy không xử lý trùng.
- **Khách hủy đơn chưa thanh toán (UC-17 A2):** lý do → CANCELLED, nhả hộp như trên ("CUSTOMER_CANCELLED"); không tạo hoàn. Đơn đã xác nhận
  → 409 (hủy đơn đã thanh toán thuộc T7.3).
- **Khách xem (UC-19, UC-20, UC-22):** danh sách theo trạng thái/khoảng thời gian (phân trang, mới nhất trước); chi tiết = snapshot + trạng thái +
  lý do + thời điểm đổi trạng thái + số hộp; khoản tiền: tổng phải trả, đã xác minh, đã hoàn, khoản giữ lại no-show theo chính sách (dự kiến).
- **Cửa hàng xem (UC-54, UC-55):** danh sách theo trạng thái/thời gian; chi tiết = snapshot + tên hiển thị khách (không email) + hộp được giữ/phân bổ
  (mã hộp, giá trị, trạng thái) + khoản tiền.

## 5. Quy tắc

- **BR-T53-01** Chỉ PENDING_PAYMENT hết hạn/hủy theo luồng này; mọi chuyển trạng thái khóa dòng đơn và kiểm lại trạng thái (đơn vừa được xác nhận
  bởi webhook T6.3 thì không bị hết hạn — ai khóa trước thắng, bên sau thấy trạng thái mới).
- **BR-T53-02** Nhả hộp: chỉ allocation HELD của đơn; hộp chỉ về AVAILABLE nếu đang RESERVED.
- **BR-T53-03** V14: `customer_order.status_reason`, `status_changed_at`; cập nhật trigger cho phép đổi hai cột này (điều khoản vẫn bất biến).
- **BR-T53-04** Audit `ORDER_EXPIRED` (actor NULL = hệ thống), `ORDER_CANCELLED_BY_CUSTOMER` (actor = khách).
- **BR-T53-05** Khoảng thời gian lọc theo `accepted_at`; `from < to`; size ≤ 50.

## 6. Phạm vi

Trong: job hết hạn, hủy đơn chưa thanh toán, xem đơn khách/cửa hàng, khoản tiền (khung), V14, OpenAPI, test (gồm hai job song song, job ‖ hủy).
Ngoài: mã nhận (T7.1), hủy đơn đã thanh toán (T7.3), tiền đến muộn (T6.4).

## 7. Acceptance criteria

| ID | Given / When / Then |
|---|---|
| AC-01 | Đơn PENDING_PAYMENT quá hạn → job: EXPIRED, lý do, allocation RELEASED (HOLD_EXPIRED), hộp AVAILABLE, số khả dụng tăng lại; đơn chưa quá hạn không đổi. |
| AC-02 | Hai lần chạy job đồng thời (và lặp lại) → mỗi đơn hết hạn đúng một lần, mỗi allocation nhả một lần, một audit. |
| AC-03 | Hộp nhả nhưng không còn đạt (hết an toàn) → AVAILABLE nhưng không được tính khả dụng (BR-24). |
| AC-04 | Khách hủy đơn chưa thanh toán (lý do) → CANCELLED, hộp nhả, không có bản ghi `refund`; hủy lại / hủy đơn EXPIRED → 409; người khác → 404; job ‖ hủy đồng thời → đúng một kết quả. |
| AC-05 | Khách xem danh sách (lọc trạng thái, khoảng thời gian, phân trang) và chi tiết + khoản tiền (tổng, đã xác minh 0, đã hoàn 0, giữ lại dự kiến); khoảng thời gian ngược → 400. |
| AC-06 | Owner/staff ORDER_VIEW xem đơn cửa hàng + hộp được giữ; staff không ORDER_VIEW, owner khác, khách → 403; không lộ email khách. |
| AC-07 | OpenAPI có endpoint; `SwaggerUiTests` qua. |

## 8. Truy vết

| Nguồn | Đáp ứng |
|---|---|
| BR-23, UC-13 E2 | AC-01, AC-02 |
| BR-24 | AC-01, AC-03 |
| UC-17 A2 | AC-04 |
| UC-19, UC-20, UC-22 | AC-05 |
| UC-54, UC-55 | AC-06 |
| Card: job chạy trùng không nhả hai lần; khách không xem đơn người khác | AC-02, AC-04 |
| Card: thiết kế khóa đơn cho T6.3 | BR-T53-01 |

## 9. Quyết định (agent tự chốt)

- [x] Q1 Chu kỳ job 30 giây (`orders.expiry-scan-ms`), lô 100.
- [x] Q2 Hủy đơn đã xác nhận để T7.3 (409 ở T5.3).
- [x] Q3 Cửa hàng thấy tên hiển thị khách, không email (dữ liệu tối thiểu).
- [x] Q4 Đường dẫn: `GET /api/v1/orders` (của tôi), `GET /api/v1/orders/{id}/money`, `POST /api/v1/orders/{id}/cancel`,
  `GET /api/v1/stores/{storeId}/orders`, `GET /api/v1/stores/{storeId}/orders/{orderId}`.
