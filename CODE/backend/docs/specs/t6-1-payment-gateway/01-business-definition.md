# Business Definition: T6.1 — Port thanh toán và adapter payOS

**Status:** Gate A tự chốt bởi agent 2026-11-16 (quyền tự quyết trong phiên).
**Card:** [roadmap-3-9.md#t61](../roadmap-3-9.md) · **Nhánh:** `feature/t6-1-payment-gateway` (từ `feature/t5-3-order-expiry` ae38956)
**Nguồn:** roadmap D2 (payOS qua `PaymentGateway`, test dùng `FakePaymentGateway`), D3, CLAUDE.md §5–6 (không gọi HTTP trong transaction,
xác minh checksum trước mọi xử lý, không log key), tài liệu payOS chính thức (đọc 2026-11-16): https://payos.vn/docs/api/,
https://payos.vn/docs/tich-hop-webhook/kiem-tra-du-lieu-voi-signature/, https://payos.vn/docs/du-lieu-tra-ve/webhook/.

## 1. Bối cảnh

Task hạ tầng (không có UC trực tiếp): một cổng (port) thanh toán độc lập nhà cung cấp, adapter payOS thật, adapter giả cho test/dev.
T6.2 (tạo yêu cầu), T6.3 (webhook), T6.4 (đối chiếu) dùng port này.

## 2. Đối chiếu tài liệu payOS (Gate A theo card)

| Mục | Tài liệu nói | Áp dụng |
|---|---|---|
| Base URL | `https://api-merchant.payos.vn` | `payos.base-url` (mặc định này) |
| Tạo link | `POST /v2/payment-requests` | `createLink` |
| Tra cứu | `GET /v2/payment-requests/{id}` (`id` = orderCode hoặc paymentLinkId) | `getLink(orderCode)` |
| Hủy link | `POST /v2/payment-requests/{id}/cancel` `{cancellationReason}` | `cancelLink` |
| Đăng ký webhook | `POST /confirm-webhook` `{webhookUrl}` | để hướng dẫn thủ công (T6.3) |
| Header | `x-client-id`, `x-api-key` | từ env |
| Body tạo | `orderCode` integer, `amount` integer, `description`, `cancelUrl`, `returnUrl`, `expiredAt` (Unix timestamp, Int32), `signature` | `orderCode` = Long dương ≤ 2^53, `expiredAt` = epoch **giây** (quy ước Unix; tài liệu không nói rõ đơn vị) |
| Chữ ký tạo | HMAC_SHA256(checksum key) trên `amount=..&cancelUrl=..&description=..&orderCode=..&returnUrl=..` | hex thường |
| Thành công | `code = "00"` | khác → lỗi REJECTED kèm `desc` |
| Trạng thái link | tài liệu chỉ thấy `PENDING`, `CANCELLED` | ánh xạ thêm `PAID`, `EXPIRED`, `PROCESSING`, `UNDERPAID` theo SDK chính thức; lạ → `UNKNOWN` (không suy ra đã trả) |
| Webhook | `{code, desc, success, data{…}, signature}`; ký trên **data**: key sắp xếp alphabet, `key=value&…`, null/"null"/"undefined" → "", mảng → JSON, không URL-encode, HMAC_SHA256 hex | đã kiểm bằng vector mẫu trong tài liệu (`412e915d…`) |
| Phản hồi webhook | trả 2XX | T6.3 |
| Mô tả | ≤ 9 ký tự với tài khoản ngân hàng không liên kết qua payOS | dùng ≤ 9 ký tự an toàn (vd mã ngắn) |

## 3. Quy tắc

- **BR-T61-01** Xác minh chữ ký webhook bằng so sánh thời gian hằng (constant-time) trước mọi xử lý; sai/thiếu → `InvalidSignature`, không ghi gì.
- **BR-T61-02** Mọi lỗi gọi payOS thành `PaymentGatewayException(kind)`: `NOT_CONFIGURED`, `TIMEOUT`, `NETWORK`, `REJECTED` (code ≠ "00" hoặc HTTP 4xx),
  `UNAVAILABLE` (5xx), `INVALID_RESPONSE`. Thông điệp không chứa client id, api key, checksum key, chữ ký.
- **BR-T61-03** Timeout kết nối và đọc cấu hình được (`payos.connect-timeout` 5s, `payos.read-timeout` 10s).
- **BR-T61-04** Chọn adapter bằng `payment.gateway` = `payos` | `fake`; dev mặc định `fake`, prod bắt buộc `payos` và đủ 3 khóa (thiếu → không khởi động).
- **BR-T61-05** `FakePaymentGateway` mô phỏng đúng hợp đồng (tạo/tra cứu/hủy, ký và xác minh webhook bằng cùng thuật toán) để test T6.2–T6.4 không gọi mạng.
- **BR-T61-06** Không log body webhook đầy đủ, không log khóa.

## 4. Phạm vi

Trong: port + kiểu dữ liệu, adapter payOS, adapter giả, chữ ký, cấu hình env, `.env.example`, test (vector chữ ký từ tài liệu; adapter với HTTP server giả).
Ngoài: endpoint (T6.2/T6.3), lưu `payment_request`/`payment` (T6.2+), đăng ký webhook thật (hướng dẫn ở T6.3). Không có endpoint mới → OpenAPI không đổi.

## 5. Acceptance criteria

| ID | Given / When / Then |
|---|---|
| AC-01 | Vector mẫu tài liệu: data webhook + checksum key mẫu → chữ ký `412e915d…`; verify chấp nhận; đổi một trường/sai chữ ký/thiếu chữ ký → `InvalidSignature`. |
| AC-02 | Chữ ký tạo link đúng chuỗi `amount&cancelUrl&description&orderCode&returnUrl` (so với HMAC tính độc lập). |
| AC-03 | HTTP server giả: tạo link gửi đúng path, header, body (orderCode số, expiredAt giây, signature) và đọc `checkoutUrl`, `qrCode`, `paymentLinkId`, `status`; tra cứu đọc `status`, `amountPaid`; hủy gửi lý do. |
| AC-04 | Server trả `code` ≠ "00" → REJECTED kèm desc; 5xx → UNAVAILABLE; không phản hồi quá read-timeout → TIMEOUT; cổng đóng → NETWORK; JSON hỏng → INVALID_RESPONSE. Không thông điệp nào chứa khóa. |
| AC-05 | Thiếu khóa khi `payment.gateway=payos` → NOT_CONFIGURED (prod: lỗi khởi động). Fake: tạo/tra cứu/hủy/ký webhook nhất quán. |

## 6. Quyết định (agent tự chốt)

- [x] Q1 `expiredAt` gửi epoch giây; Q2 trạng thái lạ → UNKNOWN; Q3 dev dùng fake mặc định (không cần khóa); Q4 hướng dẫn thử thật ở T6.3.
