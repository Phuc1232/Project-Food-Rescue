# Technical Plan: T6.4 — Tiền đến muộn, sai số tiền và đối chiếu

**Based on:** 01-business-definition.md (tự chốt) · **Status:** Done — xem 03-implementation-control.txt.

## 0. Quyết định Gate B

- P1 — V16: `refund_request.payment_id` (FK payment, unique khi khác NULL), `refund_request.reason_code varchar(50)`; `payment_request.reconcile_note text`,
  `reconciled_at timestamptz`.
- P2 — `RefundObligationService.open(payment, reasonCode)` gọi từ `PaymentConfirmationService.review` (T6.3) khi có đơn — cùng transaction.
- P3 — `PaymentReconciliationService.run(now)`: chọn id yêu cầu (transaction đọc) → từng yêu cầu: gọi payOS ngoài transaction → `ReconciliationStore.apply(...)`
  khóa yêu cầu + đơn. `PaymentReconciliationJob` @Scheduled (tắt trong test).
- P4 — Nhóm tiền tính bằng SQL CASE; `OrderMoneyReader.refundInProgress` cộng nghĩa vụ PENDING/APPROVED.
- P5 — Hằng số: migration 15→16; Swagger 75→80 paths, 21→22 tags (`Admin payments`).

## 1. File layout

`resources/db/migration/V16__refund_obligations_and_reconciliation.sql`; `payment/entity/{RefundRequest,PaymentRequest}.kt` (M); repositories (M);
`payment/service/{RefundObligationService,PaymentReconciliationService,ReconciliationStore,PaymentReconciliationJob,AdminPaymentService}.kt`;
`payment/service/PaymentConfirmationService.kt` (M); `order/service/OrderMoneyReader.kt` (M); `payment/dto/{AdminPaymentView,OrderMoneySummary,
PaymentAssignRequest,ReconciliationReport}.kt`; `payment/controller/AdminPaymentController.kt`; openapi; test `PaymentReconciliationTests.kt`.

## 2. Task list

- [x] V16 + code + OpenAPI. - [x] Test AC-01..05. - [x] Build, smoke, báo cáo, commit.
