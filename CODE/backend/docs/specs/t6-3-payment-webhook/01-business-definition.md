# Business Definition: T6.3 — Webhook xác nhận thanh toán

**Status:** Gate A tự chốt bởi agent 2026-11-16 (quyền tự quyết trong phiên).
**Card:** [roadmap-3-9.md#t63](../roadmap-3-9.md) · **Nhánh:** `feature/t6-3-payment-webhook` (từ `feature/t6-2-payment-request` 123622f)
**Tài liệu:** UC-14 bước 5–8, A2, A3, E1, E2; UC-13 bước 7–8; BR-25, BR-26, BR-30, BR-32; CLAUDE.md §5 (checksum trước, trùng lặp trả thành công
không ghi lần hai, đổi trạng thái + event/ledger cùng transaction); payOS: webhook phải được phản hồi 2XX.

## 1. Bối cảnh

payOS gọi webhook khi tiền vào tài khoản theo mã đơn. Backend xác minh chữ ký trước mọi thứ, ghi khoản nhận một lần (unique theo provider + mã giao
dịch), và trong cùng transaction có khóa đơn: đủ tiền + đơn còn hiệu lực → xác nhận đơn, phân bổ chính thức hộp, ghi sự kiện tài chính và bút toán.
Kết quả của khách chỉ dựa vào dữ liệu đã xác minh (BR-32, UC-14 A2/A3).

## 2. Khảo sát mã

- `payment` (request, order nullable, provider, provider_transaction_id, received_amount > 0 nguyên đồng VND, status RECEIVED/VERIFIED/REQUIRES_REVIEW/
  REJECTED, verified_at, received_at; UNIQUE(provider, provider_transaction_id)). `financial_event` (order NOT NULL, type, payment, business_key UNIQUE;
  bất biến) + `ledger_entry` (event, store, type, amount, currency; bất biến). Entity có sẵn.
- T6.1 `PaymentGateway.verifyWebhook` (chữ ký trước, `InvalidSignatureException`); T6.2 `payment_request.provider_order_code`.
- T5.3: đơn PENDING_PAYMENT, job hết hạn dùng `FOR UPDATE SKIP LOCKED`; `OrderReleaser`. Hộp RESERVED → ALLOCATED; allocation HELD → CONFIRMED.
- Bảo mật: mọi `/api/v1/**` cần token → webhook phải được mở công khai trong `middleware/SecurityConfiguration` (sửa code task khác — card yêu cầu "endpoint webhook công khai").
- Migration: không cần. Baseline 123622f: 198 tests, 197 pass, 1 skipped.

## 3. Luồng

1. `POST /api/v1/payments/webhooks/payos` (công khai). Đọc body thô; `verifyWebhook` — sai/thiếu chữ ký → 400, không ghi gì.
2. Giao dịch thất bại (`data.code` ≠ "00") → 200, không ghi (không phải tiền vào).
3. Tìm `payment_request` theo mã đơn provider. Không có (vd payload thử của payOS khi đăng ký webhook) → ghi `payment` REQUIRES_REVIEW không gắn đơn (tiền
   không rõ chủ, đưa vào đối chiếu T6.4) → 200.
4. Có đơn: khóa đơn (FOR UPDATE) rồi kiểm trùng `(provider, reference)` → đã có → 200 không ghi lần hai.
5. Ghi `payment`; nếu đơn PENDING_PAYMENT, `now ≤ hold_expires_at`, số tiền = `total_amount`, VND → payment VERIFIED, đơn CONFIRMED, allocation CONFIRMED,
   hộp ALLOCATED, `financial_event` PAYMENT_RECEIVED (business_key `PAYMENT_RECEIVED:{payment}`) + `ledger_entry` PAYMENT_RECEIPT (= số tiền, store của đơn) —
   tất cả trong một transaction.
6. Ngược lại: đơn PENDING_PAYMENT nhưng đã quá hạn giữ → cho đơn hết hạn ngay (nhả hộp như T5.3) rồi ghi payment REQUIRES_REVIEW (BR-26: không khôi phục,
   không phân bổ lại); đơn đã hết hạn/hủy/đã xác nhận, sai số tiền → payment REQUIRES_REVIEW (T6.4 xử lý hoàn/đối chiếu).
7. Luôn trả 2XX khi chữ ký hợp lệ (payOS chỉ cần biết đã nhận).

## 4. Quy tắc

- **BR-T63-01** Chữ ký trước mọi xử lý; không log body đầy đủ (chỉ mã đơn, số tiền, kết quả).
- **BR-T63-02** Idempotent: unique `(provider, provider_transaction_id)` + kiểm trong khóa đơn; webhook trùng/đồng thời → đúng một payment, một event, một ledger.
- **BR-T63-03** Webhook ‖ job hết hạn: cả hai khóa dòng đơn; ai trước thắng, bên sau đọc trạng thái mới (đơn CONFIRMED → job bỏ qua; đơn EXPIRED → tiền thành REQUIRES_REVIEW).
- **BR-T63-04** Trạng thái thanh toán độc lập trạng thái đơn: payment luôn được ghi dù đơn không xác nhận được (không mất tiền — UC-13 hậu điều kiện).
- **BR-T63-05** Audit `ORDER_CONFIRMED` (actor NULL = hệ thống) vào lịch sử cửa hàng.

## 5. Phạm vi

Trong: endpoint webhook, xác nhận đơn, payment/event/ledger, mở công khai trong SecurityConfiguration, OpenAPI, test, hướng dẫn thử thật.
Ngoài: nghĩa vụ hoàn + đối chiếu + job tra cứu (T6.4), mã nhận hàng (T7.1), thông báo (T8.2).

## 6. Acceptance criteria

| ID | Given / When / Then |
|---|---|
| AC-01 | Webhook hợp lệ đủ tiền cho đơn còn hạn → 200; payment VERIFIED, đơn CONFIRMED, allocation CONFIRMED, hộp ALLOCATED, 1 event PAYMENT_RECEIVED + 1 ledger PAYMENT_RECEIPT = tổng; khách thấy CONFIRMED. |
| AC-02 | Sai chữ ký / sửa số tiền / thiếu chữ ký / body hỏng → 400, không ghi gì, đơn giữ nguyên. Không cần token. |
| AC-03 | Gửi lại cùng webhook (tuần tự và đồng thời) → 200, vẫn 1 payment, 1 event, 1 ledger. |
| AC-04 | Webhook ‖ job hết hạn đồng thời trên cùng đơn → hoặc CONFIRMED (job bỏ qua, hộp ALLOCATED) hoặc EXPIRED (payment REQUIRES_REVIEW, hộp nhả); không trộn. |
| AC-05 | Tiền đến sau khi hết hạn giữ (đơn còn PENDING) hoặc đơn đã EXPIRED/CANCELLED, hoặc sai số tiền → payment REQUIRES_REVIEW, đơn không CONFIRMED, không event/ledger. |
| AC-06 | Mã đơn không tồn tại → 200, payment REQUIRES_REVIEW không gắn đơn; giao dịch code ≠ "00" → 200 không ghi. OpenAPI có endpoint. |

## 7. Truy vết

| Nguồn | Đáp ứng |
|---|---|
| UC-14 bước 5–8; UC-13 bước 7–8; BR-25 | AC-01 |
| UC-14 bước 5 (xác minh nguồn/chữ ký), BR-32 | AC-02 |
| BR-30 (ghi một lần) | AC-03 |
| UC-14 E2, BR-26 | AC-04, AC-05 |
| UC-14 E1 (sai mã/số tiền → đối chiếu) | AC-05, AC-06 |
| UC-14 A2/A3 (khách chỉ thấy trạng thái đã xác minh) | AC-01 (GET đơn) |
| Card: webhook trùng; checksum sai; webhook ‖ job | AC-02..04 |

## 7b. Thử thật (hướng dẫn cho người dùng, không tự chạy)

1. `.env`: `PAYMENT_GATEWAY=payos`, `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY` của kênh thanh toán.
2. Mở đường hầm công khai tới cổng 8080: `cloudflared tunnel --url http://localhost:8080` (hoặc `ngrok http 8080`).
3. Đăng ký webhook: trang quản lý kênh thanh toán payOS → Webhook URL = `https://<tunnel>/api/v1/payments/webhooks/payos` (hoặc API `POST /confirm-webhook`).
4. Đặt đơn nhỏ, `POST /api/v1/orders/{id}/payment`, quét QR chuyển đúng số tiền; `GET /api/v1/orders/{id}` → CONFIRMED.

## 8. Quyết định (agent tự chốt)

- [x] Q1 Sai chữ ký → 400 (không 2XX để không bị coi là đã nhận); hợp lệ → luôn 200.
- [x] Q2 Hạn hiệu lực xét theo thời điểm server nhận webhook so với `hold_expires_at`.
- [x] Q3 Số tiền phải đúng bằng tổng đơn (thừa/thiếu → đối chiếu, T6.4).
- [x] Q4 Đường dẫn `POST /api/v1/payments/webhooks/payos`.
