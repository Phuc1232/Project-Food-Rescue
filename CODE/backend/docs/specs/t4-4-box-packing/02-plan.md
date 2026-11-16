# Technical Plan: T4.4 — Đóng gói hộp thực tế

**Based on:** 01-business-definition.md (tự chốt) · **Status:** Done — xem 03-implementation-control.txt.

## 0. Quyết định Gate B

- P1 — `BoxPackingService.pack` một transaction: khóa đợt → key đã có? trả hộp (200) → kiểm món (khóa đọc menu trong tx) → tính → lưu box,
  items, inspection → audit. Controller trả 201 khi tạo mới, 200 khi trùng key.
- P2 — Lỗi giá: 409 `PRICE_CHANGED` với `fieldErrors` `items[i].expectedUnitPrice` = giá hiện hành.
- P3 — Lỗi giá trị/an toàn: 400 `VALIDATION_FAILED` field `items` ("Total listed value X is below the batch minimum Y") / `items[i].expiresAt`.
- P4 — `BoxCodeGenerator` (SecureRandom base32, 10 ký tự), thử lại 5 lần nếu trùng (`existsByBoxCode`).
- P5 — Hằng số: migration 10→11; Swagger 56→59 paths, 16→17 tags (`Boxes`).

## 1. File layout

`resources/db/migration/V11__box_pack_request_key.sql`; `surprisebox/entity/PhysicalBox.kt` (+packRequestKey);
`surprisebox/repository/{PhysicalBoxRepository,BoxItemRepository,BoxInspectionRepository}.kt`;
dto (BoxPackRequest, BoxPackItemRequest, BoxView, BoxDetail, BoxItemView, BoxInspectionView, PrefillItemView);
service (BoxAccess, BoxPackingService, BoxQueryService, BoxCodeGenerator); controller (BoxController); openapi; test `BoxPackingTests.kt`.

## 2. Task list

- [x] V11 + code + OpenAPI. - [x] `BoxPackingTests` AC-01..09 (đồng thời AC-04). - [x] Build, smoke, báo cáo, commit.
