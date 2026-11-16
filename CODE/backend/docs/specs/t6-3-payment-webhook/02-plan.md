# Technical Plan: T6.3 — Webhook xác nhận thanh toán

**Based on:** 01-business-definition.md (tự chốt) · **Status:** Done — xem 03-implementation-control.txt.

## 0. Quyết định Gate B

- P1 — Controller nhận body thô (`@RequestBody String`), gọi `PaymentGateway.verifyWebhook` (không transaction), rồi `PaymentConfirmationService.record(payment)`
  (@Transactional). Đồng thời cùng giao dịch khi chưa gắn đơn: bắt `DataIntegrityViolationException` ở controller → coi là trùng (200).
- P2 — **Sửa code task khác (cần nêu theo CLAUDE.md §1):** `middleware/SecurityConfiguration` thêm `permitAll` cho đúng `POST /api/v1/payments/webhooks/payos`
  (card yêu cầu endpoint công khai; xác thực bằng chữ ký HMAC thay cho token).
- P3 — `OrderReleaser` (T5.3) dùng lại để hết hạn đơn khi tiền đến sau hạn giữ.
- P4 — Không migration. Swagger 74→75 paths, 20→21 tags (`Payments`).

## 1. File layout

`payment/controller/PaymentWebhookController.kt`; `payment/service/PaymentConfirmationService.kt`; `payment/repository/PaymentRepository.kt` (M);
`finance/repository/{FinancialEventRepository,LedgerEntryRepository}.kt` (dùng); `middleware/SecurityConfiguration.kt` (M); openapi; test `PaymentWebhookTests.kt`.

## 2. Task list

- [x] Code + OpenAPI. - [x] `PaymentWebhookTests` AC-01..06 (đồng thời AC-03, AC-04). - [x] Build, smoke, báo cáo, commit.
