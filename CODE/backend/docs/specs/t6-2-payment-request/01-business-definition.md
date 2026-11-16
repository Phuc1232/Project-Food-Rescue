# Business Definition: T6.2 — Tạo yêu cầu thanh toán

**Status:** Gate A tự chốt bởi agent 2026-11-16 (quyền tự quyết trong phiên).
**Card:** [roadmap-3-9.md#t62](../roadmap-3-9.md) · **Nhánh:** `feature/t6-2-payment-request` (từ `feature/t6-1-payment-gateway` 1d7fb44)
**Tài liệu:** UC-14 bước 1–3, A1, A3; UC-15 (toàn bộ, gồm A1, E1); BR-28, BR-29; D2; CLAUDE.md §5 (không gọi HTTP trong transaction).

## 1. Bối cảnh

Khách lấy QR/liên kết chuyển đúng 100% tổng tiền đơn `PENDING_PAYMENT` còn hạn giữ. Mỗi đơn có tối đa một yêu cầu hợp lệ tại một thời điểm;
mở lại màn hình dùng lại yêu cầu cũ (A1). payOS cần `orderCode` **số** → ánh xạ đơn → mã số duy nhất. Gọi payOS ngoài transaction: ghi ý định trước,
gọi, ghi kết quả sau; lỗi không rõ → tra cứu theo mã trước khi tạo lại (UC-15 E1).

## 2. Khảo sát mã

- `payment_request` (order, provider, provider_request_id, idempotency_key UNIQUE, expected_amount > 0 nguyên đồng VND, payment_url, expires_at,
  status PENDING/READY/EXPIRED/FAILED, UNIQUE(provider, provider_request_id)); entity có sẵn. **Không có cột mã số cho provider, không có cột QR.**
- T6.1 `PaymentGateway` (createLink/getLink/cancelLink; REJECTED khi trùng orderCode; lỗi có kiểu). T5.2/T5.3 đơn, `hold_expires_at`, khóa dòng đơn.
- Migration mới nhất V14 → **V15**. Baseline 1d7fb44: 192 tests, 191 pass, 1 skipped.

## 3. Quyền

Chỉ khách chủ đơn (đơn người khác → 404). Không token → 401.

## 4. Luồng

1. **Bước ghi ý định (transaction 1):** khóa đơn; kiểm chủ đơn, `PENDING_PAYMENT`, còn hạn giữ ≥ 60 giây (đủ thời gian chuyển). Có yêu cầu `READY`
   còn hạn → trả luôn (A1). Có yêu cầu `PENDING` (lần gọi trước chưa rõ kết quả) → dùng lại mã đó. Không có → tạo `PENDING` với mã số mới
   (`provider_order_code` từ sequence), số tiền = `total_amount`, hạn = `hold_expires_at`.
2. **Gọi provider (ngoài transaction):** `createLink(code, amount, "FR"+code, returnUrl, cancelUrl, expiresAt)`. Trùng mã (đã tạo trước đó) hoặc
   TIMEOUT/NETWORK/UNAVAILABLE → `getLink(code)` (E1); thấy link PENDING → dùng lại; không thấy/lỗi tạm → để yêu cầu `PENDING`, trả 503
   `PAYMENT_PROVIDER_UNAVAILABLE` (khách thử lại; lần sau tra cứu trước).
3. **Bước ghi kết quả (transaction 2):** khóa yêu cầu; còn `PENDING` → `READY` + paymentLinkId, checkoutUrl, qrCode. Bị từ chối dứt khoát → `FAILED`,
   lần sau tạo yêu cầu mới (mã mới), trả 502 `PAYMENT_PROVIDER_REJECTED`.
4. Khách xem trạng thái yêu cầu và đơn (A3: "đang chờ xác minh"; quay về app không phải là đã trả).

## 5. Quy tắc

- **BR-T62-01** Số tiền = `total_amount` của đơn (nguyên đồng, BR-28/29); hạn link ≤ `hold_expires_at`.
- **BR-T62-02** Mã đơn provider: số nguyên dương duy nhất (sequence bắt đầu 100000), cột `provider_order_code` UNIQUE; mô tả chuyển khoản `FR`+mã (≤ 9 ký tự tới mã 9 999 999).
- **BR-T62-03** Tối đa một yêu cầu `PENDING`/`READY` còn hạn mỗi đơn (unique index một phần theo order khi status ∈ PENDING, READY).
- **BR-T62-04** Không gọi provider khi giữ khóa DB. Hai request đồng thời → cùng một yêu cầu, cùng một link (provider chặn trùng mã; bên sau tra cứu).
- **BR-T62-05** Đơn đã CONFIRMED / EXPIRED / CANCELLED, hoặc còn < 60 giây giữ → 409 `ORDER_NOT_PAYABLE`.
- **BR-T62-06** Không ghi bất kỳ khóa/chữ ký nào; lỗi provider ghi log ngắn.

## 6. Phạm vi

Trong: `POST /api/v1/orders/{orderId}/payment` (lấy hoặc tạo), `GET /api/v1/orders/{orderId}/payment` (xem yêu cầu hiện hành), V15, OpenAPI, test (fake gateway).
Ngoài: webhook/xác nhận (T6.3), hủy link khi đơn hết hạn, tiền đến muộn, đối chiếu (T6.4).

## 7. Acceptance criteria

| ID | Given / When / Then |
|---|---|
| AC-01 | Đơn PENDING_PAYMENT của khách → 201 yêu cầu READY: mã số, số tiền = tổng đơn, checkoutUrl, qrCode, hạn = hạn giữ; gateway gọi đúng một lần. |
| AC-02 | Gọi lại → 200 cùng yêu cầu, không gọi gateway lần hai (A1). |
| AC-03 | Hai request đồng thời → cùng một paymentLinkId; một yêu cầu trong DB. |
| AC-04 | Gateway TIMEOUT → 503, yêu cầu còn PENDING; gọi lại → tra cứu theo mã thấy link → READY cùng mã (E1), không tạo mã mới. Gateway từ chối dứt khoát → 502, FAILED; gọi lại → mã mới. |
| AC-05 | Đơn người khác → 404; đơn EXPIRED/CANCELLED/còn < 60 giây giữ → 409 `ORDER_NOT_PAYABLE`; không token → 401. |
| AC-06 | `GET .../payment` trả yêu cầu hiện hành (hoặc 404 khi chưa có); OpenAPI có endpoint. |

## 8. Truy vết

| Nguồn | Đáp ứng |
|---|---|
| UC-14 bước 1–3, UC-15 bước 1–4 | AC-01 |
| UC-14 A1, UC-15 A1 | AC-02 |
| UC-15 E1 | AC-04 |
| UC-14 A3 | AC-06 (trạng thái chờ, không suy ra đã trả) |
| BR-28, BR-29 | AC-01 |
| Card: đơn người khác 403/404; đã xác nhận/hết hạn 409; đồng thời một link | AC-03, AC-05 |

## 9. Quyết định (agent tự chốt)

- [x] Q1 Mã số riêng cho provider (sequence), không dùng `order_code` dạng chữ.
- [x] Q2 Ngưỡng 60 giây hạn giữ tối thiểu để tạo link.
- [x] Q3 Đường dẫn `POST/GET /api/v1/orders/{orderId}/payment`.
