# Technical Plan: T6.2 — Tạo yêu cầu thanh toán

**Based on:** 01-business-definition.md (tự chốt) · **Status:** Done — xem 03-implementation-control.txt.

## 0. Quyết định Gate B

- P1 — Ba bước: `PaymentRequestStore.reserve(...)` (@Transactional, khóa đơn) → `PaymentGateway` (không transaction) → `PaymentRequestStore.complete/fail(...)`
  (@Transactional, khóa yêu cầu). Điều phối ở `PaymentRequestService` (không @Transactional).
- P2 — V15: `payment_request.provider_order_code bigint UNIQUE` (sequence `payment_order_code_seq` start 100000), `qr_code text`, unique index một phần
  `(order_id) WHERE status IN ('PENDING','READY')`.
- P3 — Fake gateway là bean trong test (payment.gateway mặc định fake); test lấy bean `PaymentGateway` cast `FakePaymentGateway` để đếm/giả lỗi; reset mỗi test.
- P4 — Lỗi: 503 `PAYMENT_PROVIDER_UNAVAILABLE`, 502 `PAYMENT_PROVIDER_REJECTED`, 409 `ORDER_NOT_PAYABLE`.
- P5 — Hằng số: migration 14→15; Swagger 73→74 paths.

## 1. File layout

`resources/db/migration/V15__payment_request_order_code.sql`; `payment/entity/PaymentRequest.kt` (M); `payment/repository/PaymentRequestRepository.kt` (M);
`payment/dto/PaymentRequestView.kt`; `payment/service/{PaymentRequestStore,PaymentRequestService,PaymentProperties?}.kt`; `payment/controller/OrderPaymentController.kt`;
openapi; test `PaymentRequestTests.kt`.

## 2. Task list

- [x] V15 + code + OpenAPI. - [x] `PaymentRequestTests` AC-01..06 (đồng thời AC-03). - [x] Build, smoke, báo cáo, commit.
