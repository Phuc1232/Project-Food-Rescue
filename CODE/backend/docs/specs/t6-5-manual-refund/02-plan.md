# Technical Plan: T6.5 — Hoàn tiền có kiểm soát (thủ công qua admin)

**Based on:** 01-business-definition.md (tự chốt) · **Status:** Done — xem 03-implementation-control.txt.

## 0. Quyết định Gate B

- P1 — V17: `refund.recorded_by_id` (FK account), `refund.note text`, `refund.transferred_at timestamptz`.
- P2 — `ManualRefundService` (approve/confirm/fail) khóa `refund_request` FOR UPDATE; tính "còn hoàn được" = payment.received_amount − Σ refund SUCCEEDED của payment.
- P3 — `idempotency_key` = `refund:{requestId}:{SUCCESS|FAILED-n}`; `provider_refund_id` = mã giao dịch ngân hàng (chỉ cho SUCCEEDED; FAILED để NULL).
- P4 — `RefundAttachmentPolicy` (attachment.refund): ADMIN hoặc khách của đơn.
- P5 — `AdminPaymentService` bucket REFUNDED đã dựa trên FULFILLED; tóm tắt đơn `refunded` = Σ refund SUCCEEDED (đúng cho hoàn một phần).
- P6 — Hằng số: migration 16→17; Swagger 80→86 paths, 22→23 tags (`Admin refunds`).

## 1. File layout

`resources/db/migration/V17__manual_refund_details.sql`; `payment/entity/Refund.kt` (M); repositories (M); `payment/dto/{RefundRequestView,RefundAttemptView,
RefundApproveRequest,RefundConfirmRequest,RefundFailRequest,CustomerRefundView}.kt`; `payment/service/{ManualRefundService,RefundViews,RefundAttachmentPolicy}.kt`;
`payment/controller/{AdminRefundController,OrderRefundController}.kt`; `payment/service/AdminPaymentService.kt` (M); openapi; test `ManualRefundTests.kt`.

## 2. Task list

- [x] V17 + code + OpenAPI. - [x] `ManualRefundTests` AC-01..06 (đồng thời AC-03). - [x] Build, smoke, báo cáo, commit.
