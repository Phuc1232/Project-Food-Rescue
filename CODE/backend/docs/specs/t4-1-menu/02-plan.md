# Technical Plan: T4.1 — Menu nội bộ và lịch sử giá

**Based on:** 01-business-definition.md (tự chốt) · **Status:** Done — xem 03-implementation-control.txt.

## 0. Quyết định Gate B (agent chọn)

- P1 — Đọc: `MenuAccess.requireRead(account, store)` = ADMIN hoặc `requirePermission(..., "MENU_VIEW")`; ghi: `requirePermission(..., "MENU_MANAGE")`.
- P2 — PATCH dùng DTO kiểu (Jackson từ chối trường lạ → 400 `INVALID_REQUEST`), `version` bắt buộc; khóa dòng `menu_item`
  (PESSIMISTIC_WRITE) rồi so version → hai request cùng version: một 200, một 409.
- P3 — Giá nhận `BigDecimal`; kiểm `scale` sau `stripTrailingZeros` ≤ 0 (lẻ đồng → 400), 1..100.000.000.
- P4 — Tài liệu: `replacesDocumentId` khóa dòng tài liệu cũ; đã bị thay → 409.
- P5 — Hằng số test: migration 8→9; entity 41→42 (`PriceReferenceDocument`); Swagger paths 43→49, tags 13→14 (`Menu`).
  `REFERENCE_TABLES` thêm `food_category`.

## 1. API (tag `Menu`)

| Method | Path | Quyền | 2xx |
|---|---|---|---|
| GET | `/api/v1/food-categories` | đăng nhập | `FoodCategoryView[]` (active) |
| GET | `/api/v1/stores/{storeId}/menu-items?status&categoryCode` | đọc | `MenuItemView[]` |
| POST | `/api/v1/stores/{storeId}/menu-items` | owner | 201 `MenuItemDetail` |
| GET | `/api/v1/stores/{storeId}/menu-items/{itemId}` | đọc | `MenuItemDetail` (priceHistory, documents) |
| PATCH | `/api/v1/stores/{storeId}/menu-items/{itemId}` | owner | `MenuItemDetail` (409 version) |
| POST | `/api/v1/stores/{storeId}/menu-items/{itemId}/status` | owner | `MenuItemDetail` |
| GET/POST | `/api/v1/stores/{storeId}/price-documents` | đọc / owner | `PriceDocumentView[]` / 201 |

## 2. Migration V9

Seed `food_category` (9 mã); bảng `price_reference_document`; `attachment.price_document_id` + CHECK `attachment_check` mới.

## 3. File layout

`catalog/` — `MenuItemStatus` (giữ), `FoodCategoryRepository`, `MenuItemRepository`, `MenuPriceHistoryRepository`,
`PriceReferenceDocumentRepository`, entity `PriceReferenceDocument`; dto (MenuItemCreateRequest, MenuItemUpdateRequest,
MenuItemStatusRequest, PriceDocumentRequest, MenuItemView, MenuItemDetail, PriceHistoryView, PriceDocumentView, FoodCategoryView);
service (MenuAccess, MenuItemService, MenuQueryService, PriceDocumentService, PriceValidator, MenuViews,
PriceDocumentAttachmentPolicy); controller (FoodCategoryController, MenuItemController, PriceDocumentController).
`file/entity/Attachment.kt` (+priceDocument). `store/service/StoreHistoryRecorder` (+MENU_ITEM, PRICE_DOCUMENT).
Test `MenuTests.kt`; support fixture `REFERENCE_TABLES`.

## 4. Task list / traceability

- [x] V9, entity, repository; service/controller; OpenAPI.
- [x] `MenuTests` AC-01..10 (đồng thời AC-04; trigger AC-05).
- [x] Build, smoke, báo cáo, commit.
