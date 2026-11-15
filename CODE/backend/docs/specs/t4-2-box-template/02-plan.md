# Technical Plan: T4.2 — Mẫu Surprise Box

**Based on:** 01-business-definition.md (tự chốt) · **Status:** Done — xem 03-implementation-control.txt.

## 0. Quyết định Gate B

- P1 — Không migration. Tái dùng `PriceValidator` (T4.1), mẫu khóa dòng + so `version` như T4.1.
- P2 — Quyền: `BoxTemplateAccess` — ghi `requirePermission(..., "BOX_TEMPLATE_MANAGE")`; đọc ADMIN hoặc `contexts()` (owner / staff có
  BOX_PACK hoặc BOX_MANAGE) — kiểm tra không ném rồi mới ném 403 (bài học T4.1).
- P3 — Hằng số Swagger 48 → 51 paths, 14 → 15 tags (`Box templates`).

## 1. API (tag `Box templates`)

| Method | Path | Quyền | 2xx |
|---|---|---|---|
| GET | `/api/v1/stores/{storeId}/box-templates?active=` | đọc | `BoxTemplateView[]` |
| POST | `/api/v1/stores/{storeId}/box-templates` | owner | 201 `BoxTemplateView` |
| GET / PATCH | `/api/v1/stores/{storeId}/box-templates/{templateId}` | đọc / owner | `BoxTemplateView` |
| POST | `/api/v1/stores/{storeId}/box-templates/{templateId}/status` | owner | `BoxTemplateView` |

## 2. File layout

`surprisebox/repository/BoxTemplateRepository.kt` (M); `surprisebox/dto/{BoxTemplateCreateRequest,BoxTemplateUpdateRequest,BoxTemplateStatusRequest,BoxTemplateView}.kt`;
`surprisebox/service/{BoxTemplateAccess,BoxTemplateService}.kt`; `surprisebox/controller/BoxTemplateController.kt`;
`store/service/StoreHistoryRecorder.kt` (+BOX_TEMPLATE); openapi; test `BoxTemplateTests.kt`.

## 3. Task list

- [x] Code + OpenAPI. - [x] `BoxTemplateTests` AC-01..07 (đồng thời AC-03). - [x] Build, smoke, báo cáo, commit.
