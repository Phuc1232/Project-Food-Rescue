# Technical Plan: T5.2 — Tạo đơn và giữ hộp

**Based on:** 01-business-definition.md (tự chốt) · **Status:** Done — xem 03-implementation-control.txt.

## 0. Quyết định Gate B

- P1 — Thứ tự khóa trong `OrderPlacementService.place`: `account` FOR UPDATE (cùng khách) → `store` FOR SHARE → `sale_batch` FOR SHARE → hộp
  `FOR UPDATE OF p SKIP LOCKED LIMIT N`. Đình chỉ (FOR UPDATE store) / tạm dừng (FOR UPDATE batch) chờ đơn đang tạo xong và ngược lại.
- P2 — Chọn hộp bằng SQL dùng `BatchAvailability.MEETS_TERMS` + `NOT_HELD`; `uq_box_active_allocation` là lớp bảo vệ cuối.
- P3 — V13: `customer_order.idempotency_key varchar(64)` + unique `(customer_id, idempotency_key)`. Lưu cả `quantity`/`batch` để so key tái dùng.
- P4 — `OrderProperties` (`orders.hold-minutes`, mặc định 10).
- P5 — Lỗi tồn kho: 409 `NOT_ENOUGH_BOXES` với `fieldErrors` `quantity` = số khả dụng.
- P6 — Hằng số: migration 12→13; entity giữ 42; Swagger 66→69 paths, 19→20 tags (`Orders`).

## 1. API (tag `Orders`)

| Method | Path | 2xx | Lỗi |
|---|---|---|---|
| POST | `/api/v1/orders/preview` `{batchId, quantity}` | 200 `OrderPreview` | 400, 401, 404, 409 |
| POST | `/api/v1/orders` (`Idempotency-Key`) `{batchId, quantity, acceptedPolicyVersion}` | 201 `OrderView` / 200 lặp | 400, 401, 404, 409 |
| GET | `/api/v1/orders/{orderId}` | 200 `OrderView` (chủ đơn) | 401, 404 |

## 2. File layout

`resources/db/migration/V13__order_idempotency_key.sql`; `order/entity/CustomerOrder.kt` (+idempotencyKey);
`order/repository/{CustomerOrderRepository,BoxAllocationRepository}.kt`; `order/OrderProperties.kt`;
`order/dto/{OrderPreviewRequest,OrderCreateRequest,OrderPreview,OrderView,PolicySummary}.kt`;
`order/service/{OrderOfferReader,OrderPlacementService,OrderCodeGenerator,OrderViews}.kt`; `order/controller/OrderController.kt`; openapi; test `OrderPlacementTests.kt`.

## 3. Task list

- [x] V13 + code + OpenAPI. - [x] `OrderPlacementTests` AC-01..08 (đồng thời AC-04, AC-05). - [x] Build, smoke, báo cáo, commit.
