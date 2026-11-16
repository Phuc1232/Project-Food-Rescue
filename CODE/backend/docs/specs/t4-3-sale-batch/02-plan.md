# Technical Plan: T4.3 — Đợt mở bán

**Based on:** 01-business-definition.md (tự chốt) · **Status:** Done — xem 03-implementation-control.txt.

## 0. Quyết định Gate B

- P1 — `PickupWindowValidator`: chuyển `pickupStartsAt/EndsAt` sang `ZonedDateTime` theo `store.timezone`; xét ca của ngày bắt đầu và ca
  qua đêm của ngày trước; khung nằm trọn trong `[ca.mở, ca.đóng)`.
- P2 — `BatchAvailability` (JDBC, một câu SQL `count(*) FILTER (...)`) trả các nhóm UC-62; dùng cho công khai và T5.x.
- P3 — Chuyển trạng thái + sửa: khóa dòng `sale_batch` (`findForUpdate`), sửa dùng `version`.
- P4 — Mã lỗi mới: `NO_ELIGIBLE_BOX`, `ORDER_DEADLINE_PASSED` (409); `STORE_NOT_ACTIVE` (T3.3).
- P5 — V10: `sale_batch.status_reason text`; `SaleBatchBlocker` (T3.3) ghi lý do "Store suspended".
- P6 — Hằng số test: migration 9→10; paths 51→56; tags 15→16 (`Sale batches`; admin chặn nằm cùng tag).

## 1. API

| Method | Path | Quyền | 2xx | Lỗi |
|---|---|---|---|---|
| GET | `/api/v1/stores/{storeId}/sale-batches?status` | đọc | `SaleBatchView[]` | 401, 403 |
| POST | `/api/v1/stores/{storeId}/sale-batches` | owner | 201 `SaleBatchView` | 400, 401, 403 |
| GET / PATCH | `/api/v1/stores/{storeId}/sale-batches/{batchId}` | đọc / owner | `SaleBatchView` | 400, 401, 403, 404, 409 |
| POST | `/api/v1/stores/{storeId}/sale-batches/{batchId}/status` | owner | `SaleBatchView` | 400, 401, 403, 404, 409 |
| GET | `/api/v1/stores/{storeId}/sale-batches/{batchId}/box-counts` | đọc | `BoxCountsView` | 401, 403, 404 |
| POST | `/api/v1/admin/sale-batches/{batchId}/block` | ADMIN | `SaleBatchView` | 400, 401, 403, 404, 409 |

## 2. File layout

`resources/db/migration/V10__sale_batch_status_reason.sql`; `surprisebox/entity/SaleBatch.kt` (+statusReason);
`surprisebox/repository/SaleBatchRepository.kt`; `surprisebox/SaleBatchAction.kt`; dto (SaleBatchCreateRequest, SaleBatchUpdateRequest,
SaleBatchStatusRequest, SaleBatchBlockRequest, SaleBatchView, BoxCountsView); service (SaleBatchAccess, SaleBatchService,
SaleBatchLifecycleService, PickupWindowValidator, BatchAvailability, SaleBatchViews); controller (SaleBatchController,
AdminSaleBatchController); `store/service/SaleBatchBlocker.kt`, `StoreHistoryRecorder.kt` (M); openapi; test `SaleBatchTests.kt`,
fixture `StoreTestFixtures.box(...)`.

## 3. Task list

- [x] V10 + code + OpenAPI. - [x] `SaleBatchTests` AC-01..09 (đồng thời AC-05). - [x] Build, smoke, báo cáo, commit.
