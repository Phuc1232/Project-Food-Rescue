# Technical Plan: T5.1 — Tìm kiếm Surprise Box và cửa hàng

**Based on:** 01-business-definition.md (tự chốt) · **Status:** Done — xem 03-implementation-control.txt.

## 0. Quyết định Gate B

- P1 — SQL tay bằng `NamedParameterJdbcTemplate`; điều kiện "hộp khả dụng" lấy từ `BatchAvailability.AVAILABLE_BOX_SQL` (một nguồn, T4.3 refactor
  dùng chung). Haversine: `6371 * 2 * asin(sqrt(...))` trên `latitude/longitude` (numeric → float8).
- P2 — Lọc trước bằng khung tọa độ xấp xỉ (± radius/111 km) rồi Haversine — dùng index khi dữ liệu lớn (đủ cho MVP).
- P3 — `OfferQueryService` (tìm, chi tiết, ảnh bìa), `StoreSearchService` (tìm cửa hàng, bản đồ), `SearchParams` kiểm tra tham số.
- P4 — Không migration. Hằng số Swagger 61→66 paths, 18→19 tags (`Search`).

## 1. API (tag `Search`, đăng nhập)

| Method | Path | 2xx |
|---|---|---|
| GET | `/api/v1/surprise-boxes?lat&lng&radiusKm&categoryCode&minPrice&maxPrice&pickupFrom&pickupTo&sort&page&size` | `PageResponse<OfferSummary>` |
| GET | `/api/v1/surprise-boxes/{batchId}` | `OfferDetail` |
| GET | `/api/v1/surprise-boxes/{batchId}/cover` | ảnh (image/*) |
| GET | `/api/v1/store-search?q&lat&lng&radiusKm&page&size` | `PageResponse<StoreCard>` |
| GET | `/api/v1/store-map?minLat&maxLat&minLng&maxLng` | `StorePin[]` |

## 2. File layout

`search/dto/{OfferSummary,OfferDetail,StoreCard,StorePin}.kt`; `search/service/{SearchParams,OfferQueryService,StoreSearchService,Geo}.kt`;
`search/controller/{OfferController,StoreSearchController}.kt`; `surprisebox/service/BatchAvailability.kt` (M: hằng SQL dùng chung);
openapi; test `SearchTests.kt`.

## 3. Task list

- [x] Code + OpenAPI. - [x] `SearchTests` AC-01..07. - [x] Build, smoke, báo cáo, commit.
