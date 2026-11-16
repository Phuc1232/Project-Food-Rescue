# Technical Plan: T5.3 — Hết hạn giữ hộp và theo dõi đơn

**Based on:** 01-business-definition.md (tự chốt) · **Status:** Done — xem 03-implementation-control.txt.

## 0. Quyết định Gate B

- P1 — `OrderExpiryService.expireDue(now)` (gọi trực tiếp trong test với thời điểm cố định) chọn id `FOR UPDATE SKIP LOCKED LIMIT 100`, rồi
  `OrderReleaser.release(order, status, reason)` dùng chung với hủy. `OrderExpiryJob` `@Scheduled(fixedDelayString="${orders.expiry-scan-ms:30000}")`,
  có thể tắt bằng `orders.expiry-job-enabled=false` (test bật/tắt qua property để không chạy nền khi test).
- P2 — Hủy: `findForUpdate(order)` → kiểm trạng thái → `release`. Job ‖ hủy: một bên khóa trước, bên kia thấy trạng thái mới → bỏ qua / 409.
- P3 — `AuditService.record` cần actor; thêm overload cho actor NULL (hệ thống) — tham số `actor: Account?`.
- P4 — Khoản tiền: JDBC tổng `payment.received_amount` VERIFIED theo đơn, tổng `refund.amount` SUCCEEDED; giữ lại dự kiến = MoneyRounding(total, rate).
- P5 — Hằng số: migration 13→14; Swagger 69→73 paths (tag `Orders`).

## 1. File layout

`resources/db/migration/V14__order_status_reason.sql`; `order/entity/CustomerOrder.kt` (M); `order/repository/*` (M); `order/service/{OrderReleaser,
OrderExpiryService,OrderExpiryJob,OrderQueryService,OrderCancellationService,OrderMoneyReader}.kt`; `order/dto/{OrderSummary,OrderMoneyView,
StoreOrderDetail,AllocatedBoxView,OrderCancelRequest}.kt`; `order/controller/{OrderController (M),StoreOrderController}.kt`; `audit/AuditService.kt` (M);
openapi; test `OrderLifecycleTests.kt`.

## 2. Task list

- [x] V14 + code + OpenAPI. - [x] `OrderLifecycleTests` AC-01..07 (đồng thời AC-02, AC-04). - [x] Build, smoke, báo cáo, commit.
