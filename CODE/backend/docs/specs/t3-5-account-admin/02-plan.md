# Technical Plan: T3.5 — Quản trị tài khoản người dùng

**Based on:** 01-business-definition.md (tự chốt 2026-11-15) · **Status:** Done — xem 03-implementation-control.txt.

## 0. Quyết định Gate B (agent chọn)

- P1 — Tìm kiếm dựng JPQL động (EntityManager) cho `q`/`role`/`status` (tránh `:param is null` với enum trên PostgreSQL);
  sort `createdAt desc, id`.
- P2 — Đếm đơn tồn đọng bằng JDBC (`customer_order` chưa có entity nghiệp vụ dùng): theo `customer_id` và theo store sở hữu chưa CLOSED.
- P3 — Lịch sử: `audit_log` `entity_id = account` và `entity_type IN ('AUTH','ACCOUNT')`, 50 mục mới nhất.
- P4 — Lỗi tồn đọng: 409 `OUTSTANDING_WORK`, `fieldErrors` chứa `customerOrders`, `storeOrders` (message = số lượng) để client hiển thị.
- P5 — Hằng số test: migration 7→8; Swagger paths 40→43, tags 12→13 (`Admin accounts`).

## 1. API

| Method | Path | 2xx | Lỗi |
|---|---|---|---|
| GET | `/api/v1/admin/accounts?q&role&status&page&size` | `PageResponse<AdminAccountSummary>` | 400, 401, 403 |
| GET | `/api/v1/admin/accounts/{id}` | `AdminAccountDetail` | 401, 403, 404 |
| POST | `/api/v1/admin/accounts/{id}/status` | `AdminAccountDetail` | 400, 401, 403, 404, 409 (`CONFLICT`, `OUTSTANDING_WORK`) |

## 2. File layout

| File | NEW/MODIFIED |
|---|---|
| `resources/db/migration/V8__account_status_reason.sql` | NEW |
| `account/entity/Account.kt` | MODIFIED (statusReason, statusChangedAt) |
| `account/AccountStatusAction.kt`, `account/dto/AdminAccountSummary.kt`, `AdminAccountDetail.kt`, `AccountStatusRequest.kt`, `AccountStoreLink.kt`, `AccountHistoryItem.kt` | NEW |
| `account/service/AdminAccountQueryService.kt`, `AdminAccountStatusService.kt`, `OutstandingWorkCounter.kt` | NEW |
| `account/controller/AdminAccountController.kt` | NEW |
| `audit/repository/AuditLogRepository.kt` | MODIFIED (truy vấn theo entity) |
| `docs/api/openapi.yaml` | MODIFIED |
| test `AdminAccountTests.kt` + hằng số | NEW / MODIFIED |

## 3. Task list / traceability

- [x] V8 + entity; service + controller; OpenAPI.
- [x] `AdminAccountTests`: AC-01 search, AC-02 detail, AC-03 lock, AC-04 unlock, AC-05 invalid transitions, AC-06 outstanding,
  AC-07 permissions + concurrency; AC-08 `SwaggerUiTests`.
- [x] Build xanh, smoke dev, báo cáo, commit.

## 4. Rủi ro

Khóa ADMIN cuối cùng: chỉ chặn tự khóa; nếu cần, bootstrap admin sẵn có (`AdminBootstrapCommand`). Rollback: revert; V8 chỉ thêm cột nullable.
