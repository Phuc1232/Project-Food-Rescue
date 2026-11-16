# Business Definition: T6.5 — Hoàn tiền có kiểm soát (thủ công qua admin)

**Status:** Gate A tự chốt bởi agent 2026-11-16 (quyền tự quyết trong phiên).
**Card:** [roadmap-3-9.md#t65](../roadmap-3-9.md) · **Nhánh:** `feature/t6-5-manual-refund` (từ `feature/t6-4-payment-reconciliation` 6d1a199)
**Tài liệu:** UC-72, UC-74, BR-45, BR-52, roadmap **D3 (lệch có chủ đích)**: payOS không có API hoàn tiền → admin chuyển khoản thủ công rồi xác nhận trên
hệ thống kèm mã giao dịch ngân hàng và bằng chứng; vẫn giữ: ghi một lần, gắn giao dịch gốc, không báo thành công khi chưa xác nhận.

## 1. Bối cảnh

Nghĩa vụ hoàn (`refund_request`, T6.4) cần được thực hiện: admin quyết định số hoàn (có thể một phần — UC-72 A3), tự chuyển khoản ngoài hệ thống, rồi
xác nhận kèm mã giao dịch ngân hàng (không dùng lại) và bằng chứng. Chỉ khi admin xác nhận, hệ thống mới ghi khoản hoàn thành công, sự kiện tài chính và
bút toán (BR-52 điều chỉnh, gắn giao dịch gốc). Ghi thất bại không làm mất nghĩa vụ (UC-72 E3). Khách xem trạng thái hoàn (UC-74).

## 2. Khảo sát mã

- `refund_request` (PENDING/APPROVED/REJECTED/FULFILLED, requested/approved, payment_id, reason_code — T6.4). `refund` (order, request, original_payment,
  amount > 0, status PENDING/PROCESSING/SUCCEEDED/FAILED, provider_refund_id UNIQUE, idempotency_key UNIQUE, verified_at). `attachment.refund_id` có sẵn.
- `financial_event` (REFUND_SUCCEEDED, business_key UNIQUE), `ledger_entry` (CUSTOMER_REFUND) bất biến. T6.3 chỉ ghi ledger PAYMENT_RECEIPT khi tiền được dùng cho đơn.
- Migration mới nhất V16 → **V17**. Baseline 6d1a199: 208 tests, 207 pass, 1 skipped.

## 3. Quyền

ADMIN: xem, duyệt số hoàn, ghi thành công / thất bại. Khách: xem trạng thái hoàn của đơn mình và tải bằng chứng. Khác → 403/404.

## 4. Luồng

1. Admin xem danh sách nghĩa vụ (lọc trạng thái), chi tiết: đơn, khách (tên, email để liên hệ chuyển khoản), giao dịch gốc, đã thu / đã hoàn / còn hoàn được (UC-72 bước 1–2).
2. **Duyệt** (UC-72 bước 3, A3): số hoàn ≤ số yêu cầu và ≤ số còn hoàn được của giao dịch gốc (E1) → APPROVED.
3. Admin tự chuyển khoản ngoài hệ thống (D3).
4. **Xác nhận thành công**: mã giao dịch ngân hàng (duy nhất), thời điểm chuyển, bằng chứng (file đã upload), ghi chú → trong một transaction có khóa:
   `refund` SUCCEEDED (verified_at), nghĩa vụ FULFILLED, `financial_event` REFUND_SUCCEEDED + `ledger_entry` CUSTOMER_REFUND (−số hoàn) **chỉ khi giao dịch gốc
   đã được ghi nhận doanh thu cho đơn** (payment VERIFIED); khoản chưa từng vào sổ cửa hàng (tiền đến muộn) chỉ ghi sự kiện. Audit.
5. **Ghi thất bại** (E3): `refund` FAILED với ghi chú; nghĩa vụ giữ APPROVED để thử lại có kiểm soát.
6. Khách xem: nghĩa vụ, số được duyệt, trạng thái; "đã hoàn" chỉ khi SUCCEEDED (UC-72 hậu điều kiện, UC-74).

## 5. Quy tắc

- **BR-T65-01** Tổng hoàn thành công của một giao dịch gốc ≤ số tiền giao dịch đó; tổng hoàn của đơn ≤ tổng đã nhận của đơn.
- **BR-T65-02** Xác nhận chỉ khi nghĩa vụ APPROVED; khóa dòng nghĩa vụ → hai xác nhận đồng thời: một 200, một 409. Đã FULFILLED → trả kết quả (A1) qua GET, POST → 409.
- **BR-T65-03** Mã giao dịch ngân hàng 4–64 ký tự, duy nhất toàn hệ thống (`refund.provider_refund_id`); dùng lại → 409.
- **BR-T65-04** Bằng chứng: file do chính admin upload (T3.1), gắn qua `attachment.refund_id`; đọc được bởi ADMIN và khách của đơn.
- **BR-T65-05** Sổ: REFUND_SUCCEEDED (business_key `REFUND_SUCCEEDED:{refund}`); CUSTOMER_REFUND = −số hoàn, cửa hàng của đơn, chỉ khi giao dịch gốc VERIFIED.
- **BR-T65-06** Cơ chế dùng chung: T7.3, T7.4, T8.1 tạo nghĩa vụ qua `RefundObligationService` (có thể cho khoản đã dùng cho đơn).

## 6. Phạm vi

Trong: API admin (list, detail, approve, confirm, fail), API khách xem hoàn, chính sách đọc bằng chứng, V17, OpenAPI, test.
Ngoài: điều chỉnh hoa hồng/phải trả cửa hàng khi hoàn đơn đã hoàn tất (T8.3/T8.4 dùng sổ), thông báo (T8.2).

## 7. Acceptance criteria

| ID | Given / When / Then |
|---|---|
| AC-01 | Admin duyệt toàn bộ / một phần → APPROVED với số duyệt; vượt số yêu cầu hoặc vượt số còn hoàn được → 400; không phải ADMIN → 403. |
| AC-02 | Xác nhận với mã giao dịch + bằng chứng → refund SUCCEEDED, nghĩa vụ FULFILLED, REFUND_SUCCEEDED; CUSTOMER_REFUND (−) chỉ khi giao dịch gốc VERIFIED; khách thấy đã hoàn; tổng hoàn ≤ đã nhận. |
| AC-03 | Hai xác nhận đồng thời → một 200, một 409; chỉ một refund SUCCEEDED, một event. Mã giao dịch đã dùng → 409, không đổi gì. |
| AC-04 | Ghi thất bại → refund FAILED, nghĩa vụ vẫn APPROVED, khách không thấy "đã hoàn"; sau đó xác nhận thành công được. |
| AC-05 | Xác nhận khi chưa duyệt (PENDING) → 409; bằng chứng không phải file của admin → 400; nghĩa vụ không tồn tại → 404. |
| AC-06 | Khách xem trạng thái hoàn của đơn mình (người khác → 404) và tải bằng chứng; OpenAPI có endpoint. |

## 8. Truy vết

| Nguồn | Đáp ứng |
|---|---|
| UC-72 bước 1–3, A3, E1 | AC-01 |
| UC-72 bước 5–7 (xác minh, ghi một lần, điều chỉnh), BR-45, BR-52 | AC-02, AC-03 |
| UC-72 A1 (đã hoàn đủ → không tạo mới) | AC-03, AC-05 |
| UC-72 E3 (thất bại giữ nghĩa vụ) | AC-04 |
| UC-72 hậu điều kiện (không báo đã nhận khi chưa xác nhận) | AC-04, AC-06 |
| UC-74 | AC-06 |
| D3 (thủ công, mã giao dịch + bằng chứng) | AC-02, AC-03 |
| Card: tổng hoàn ≤ đã xác minh; đồng thời một lần; mã không dùng lại | AC-01..03 |

## 9. Quyết định (agent tự chốt)

- [x] Q1 Hai bước duyệt → xác nhận (A3 rõ ràng, tránh nhập nhầm số khi xác nhận). Số xác nhận = số đã duyệt.
- [x] Q2 Bút toán cửa hàng chỉ cho khoản từng là doanh thu đơn (VERIFIED); tiền đến muộn không vào sổ cửa hàng.
- [x] Q3 Không có "từ chối" nghĩa vụ trong T6.5 (tiền khách gửi luôn phải được hoàn hoặc được quyết định một phần); để T8.1 nếu cần.
- [x] Q4 Đường dẫn: `GET /api/v1/admin/refund-requests`, `GET .../{id}`, `POST .../{id}/approve`, `POST .../{id}/confirm`, `POST .../{id}/fail`;
  `GET /api/v1/orders/{orderId}/refunds`.
