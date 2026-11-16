# Business Definition: T6.4 — Tiền đến muộn, sai số tiền và đối chiếu

**Status:** Gate A tự chốt bởi agent 2026-11-16 (quyền tự quyết trong phiên).
**Card:** [roadmap-3-9.md#t64](../roadmap-3-9.md) · **Nhánh:** `feature/t6-4-payment-reconciliation` (từ `feature/t6-3-payment-webhook` 3be9a49)
**Tài liệu:** UC-13 E3; UC-14 E1, E2, E4; BR-26, BR-29, BR-30; D3 (hoàn tiền thủ công qua admin, T6.5).

## 1. Bối cảnh

T6.3 đã ghi mọi khoản tiền đến (không mất tiền) và đánh dấu `REQUIRES_REVIEW` khi không dùng được cho đơn. T6.4 bảo đảm **mọi đồng nhận được đều ở
một trạng thái rõ ràng**: đã dùng cho đơn, chờ hoàn, đã hoàn — và khoản không xác định được đơn chờ admin gán. Thêm job đối chiếu với payOS cho yêu cầu
còn treo và API admin tra cứu giao dịch.

## 2. Khảo sát mã

- `payment` (REQUIRES_REVIEW, order nullable), `refund_request` (order NOT NULL, reason, requested/approved amount, status PENDING/APPROVED/REJECTED/
  FULFILLED, **không liên kết payment gốc**), `refund` (original_payment_id; T6.5). `payment_request` READY/PENDING/EXPIRED/FAILED.
- T6.1 `getLink`/`cancelLink`; T6.3 `PaymentConfirmationService` (review); T5.3 `OrderReleaser`, `OrderMoneyReader`.
- Migration mới nhất V15 → **V16**. Baseline 3be9a49: 204 tests, 203 pass, 1 skipped.

## 3. Luồng

1. **Tiền không dùng được cho đơn (có đơn):** khi T6.3 đưa payment vào REQUIRES_REVIEW và biết đơn (đơn hết hạn/hủy/đã xác nhận bằng giao dịch khác, sai
   số tiền) → cùng transaction tạo **nghĩa vụ hoàn** `refund_request` PENDING = toàn bộ số tiền nhận, gắn payment gốc, lý do (`LATE_PAYMENT`,
   `ORDER_CANCELLED`, `ALREADY_PAID`, `AMOUNT_MISMATCH`). Đơn không bị khôi phục, hộp không phân bổ lại (BR-26, UC-13 E3). Thiếu tiền: hoàn khoản thiếu,
   khách chuyển lại đúng số qua link (đơn còn chờ trong hạn giữ).
2. **Tiền không rõ đơn (mã đơn lạ):** payment REQUIRES_REVIEW không gắn đơn — trạng thái "chưa gán". ADMIN gán vào đơn (theo mã đơn, kèm ghi chú căn cứ)
   → tạo nghĩa vụ hoàn như (1). Không tự xác nhận đơn từ khoản gán thủ công.
3. **Job đối chiếu (5 phút, chạy tay được):**
   - yêu cầu READY/PENDING của đơn không còn PENDING_PAYMENT → EXPIRED (đơn đã xác nhận giữ READY) và hủy link ở payOS (ngoài transaction, lỗi bỏ qua);
   - yêu cầu READY của đơn còn chờ: tra cứu payOS — link CANCELLED/EXPIRED (giao dịch thất bại xác định, UC-14 E4) → yêu cầu EXPIRED, đơn hết hạn,
     nhả hộp; link PAID/UNDERPAID nhưng chưa có payment nào → ghi chú `MISSING_WEBHOOK` trên yêu cầu cho admin (không tự tạo payment để tránh ghi trùng
     khi webhook đến sau). Chạy lại không tạo payment/nghĩa vụ trùng.
4. **Admin tra cứu:** danh sách payment (lọc trạng thái, mã đơn, mã giao dịch), chi tiết với nhóm tiền; tóm tắt tiền của một đơn: đã nhận, đã dùng, chờ hoàn,
   đã hoàn.

## 4. Quy tắc

- **BR-T64-01** Mỗi payment thuộc đúng một nhóm: `APPLIED` (VERIFIED, dùng cho đơn), `REFUND_PENDING` (có nghĩa vụ PENDING/APPROVED), `REFUNDED`
  (nghĩa vụ FULFILLED), `UNASSIGNED` (REQUIRES_REVIEW chưa gắn đơn). Mỗi payment có tối đa một nghĩa vụ hoàn (unique `refund_request.payment_id`).
- **BR-T64-02** Không ghi nghĩa vụ trùng: tạo trong cùng transaction với việc đánh dấu review; gán thủ công khóa dòng payment.
- **BR-T64-03** Job khóa từng yêu cầu khi ghi kết quả, gọi payOS ngoài transaction, lô ≤ 50; `payments.reconcile-job-enabled`, `payments.reconcile-interval-ms`.
- **BR-T64-04** Khoản tiền của khách hiển thị "đang chờ hoàn" theo nghĩa vụ (UC-22).

## 5. Phạm vi

Trong: nghĩa vụ hoàn tự động, gán khoản chưa rõ, job đối chiếu, API admin (tra cứu, gán, chạy đối chiếu), V16, OpenAPI, test.
Ngoài: thực hiện hoàn (admin xác nhận chuyển khoản) — T6.5.

## 6. Acceptance criteria

| ID | Given / When / Then |
|---|---|
| AC-01 | Tiền đến sau hạn / đơn đã hủy / sai số tiền / trả lần hai cho đơn đã xác nhận → payment REQUIRES_REVIEW + đúng một refund_request PENDING = số tiền, lý do tương ứng, gắn payment; đơn không đổi sang CONFIRMED. |
| AC-02 | Mã đơn lạ → payment UNASSIGNED; ADMIN gán vào đơn (mã đơn + ghi chú) → nghĩa vụ hoàn; gán lại → 409; mã đơn sai → 404; không phải ADMIN → 403. |
| AC-03 | Job: link payOS CANCELLED/EXPIRED cho đơn còn chờ → yêu cầu EXPIRED, đơn EXPIRED, hộp nhả; PAID mà chưa có payment → ghi chú MISSING_WEBHOOK, không tạo payment; đơn đã hết hạn còn link READY → EXPIRED + hủy link. Chạy lại → không thay đổi thêm, không payment/nghĩa vụ mới. |
| AC-04 | Bất biến: sau mọi kịch bản, tổng đã nhận = APPLIED + REFUND_PENDING + REFUNDED + UNASSIGNED theo từng payment; không payment nào hai nhóm. |
| AC-05 | Admin tra cứu payment theo mã đơn / mã giao dịch / trạng thái; tóm tắt tiền đơn; khách thấy "đang chờ hoàn" trong money. OpenAPI có endpoint. |

## 7. Truy vết

| Nguồn | Đáp ứng |
|---|---|
| UC-13 E3, UC-14 E2, BR-26 | AC-01 |
| UC-14 E1 (mã/số tiền không khớp → đối chiếu) | AC-01, AC-02 |
| UC-14 E4 (thất bại xác định → nhả hộp) | AC-03 |
| BR-29 (hết hiệu lực → khoản sau đó đối chiếu hoặc hoàn) | AC-01, AC-03 |
| BR-30 (đối chiếu; ghi một lần) | AC-03, AC-04 |
| Card: mọi đồng ở một trạng thái; đối chiếu chạy lại không tạo trùng; API admin tra cứu | AC-03..05 |

## 8. Quyết định (agent tự chốt)

- [x] Q1 Thừa tiền cũng không xác nhận đơn (đúng card): hoàn toàn bộ khoản, khách trả lại đúng số.
- [x] Q2 Job không tự tạo payment từ kết quả tra cứu (tránh trùng khi webhook đến sau) — chỉ ghi chú cho admin.
- [x] Q3 Đường dẫn: `GET /api/v1/admin/payments`, `GET /api/v1/admin/payments/{id}`, `POST /api/v1/admin/payments/{id}/assign`,
  `GET /api/v1/admin/orders/{orderId}/money`, `POST /api/v1/admin/payment-reconciliation/run`.
