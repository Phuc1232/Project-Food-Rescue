# Technical Plan: T3.3 — Thông tin cửa hàng, giờ mở cửa, khóa/mở cửa hàng

**Based on:** [01-business-definition.md](01-business-definition.md) (Gate A đã duyệt 2026-11-15)
**Status:** Done — đã triển khai, build xanh. Xem 03-implementation-control.txt. (Gate B không dừng chờ duyệt theo yêu cầu người dùng.)
**Last updated:** 2026-11-15

## 0. Approved decisions (từ Gate A)

- [x] Q1: `name`, `address`, `latitude`, `longitude` → yêu cầu xem xét; `description`, `openingHours` → áp dụng ngay;
  `timezone` không sửa.
- [x] Q2: bảng `store_change_request`. Q3: 0–10 tài liệu D04 tùy chọn qua `attachment.store_change_request_id`.
- [x] Q4: ADMIN APPROVE/REJECT; owner hủy PENDING. Q5: lịch sử ở `audit_log` (before/after). Q6: cột `store.description`
  + backfill. Q7: `status_reason`, `status_changed_at`; khóa → đợt không đơn BLOCKED; `StoreSalesPolicy`; owner giữ ngữ cảnh.
- [x] Q8: phương án đơn tồn để T7.5. Q9: `GET /api/v1/stores/{id}` công khai. Q10: không kiểm đợt khi đổi giờ.
  Q11: mã quyền `STORE_PROFILE_MANAGE` (không seed); ADMIN không xử lý cửa hàng của chính mình. Q12: đường dẫn như Gate A.
  Q13: không thông báo.

### Quyết định kỹ thuật ở Gate B (agent tự chọn theo khuyến nghị)

- **P1 — Phiên bản khi chỉ đổi giờ hoạt động.** Đổi ca không làm `store` bẩn nên `@Version` không tăng → lần sửa đồng thời
  thứ hai không bị phát hiện. Giải pháp: khóa `store` `PESSIMISTIC_WRITE`, so `version` do client gửi; nếu chỉ đổi ca thì
  `EntityManager.lock(store, PESSIMISTIC_FORCE_INCREMENT)` để tăng version. Request đồng thời cùng version: request sau chờ
  khóa, đọc version mới → 409.
- **P2 — PATCH parse JSON thô** (`StoreProfileUpdateParser`, mẫu T2.1) để báo đúng trường không được sửa (`name`, `status`,
  `timezone`, trường lạ). Phân biệt "không gửi `description`" và "`description: null`/rỗng" (= xóa mô tả).
- **P3 — Lưu yêu cầu thay đổi:** `current_values`, `proposed_values` (JSON `StoreChangeValues`, chỉ trường xin đổi),
  `documents` (JSON `[{type,fileId}]`, cùng dạng snapshot T3.2), `note`. Cột kết thúc đặt tên trung tính
  `resolved_by_id`/`resolved_at`/`resolution_reason` để dùng chung cho duyệt, từ chối, hủy.
- **P4 — Tọa độ đi theo cặp:** gửi một trong hai `latitude`/`longitude` → 400 trên trường còn thiếu.
- **P5 — Thứ tự kiểm tra quyết định (giống T3.2):** 404 → 403 (tự xét) → 409 (không còn PENDING) → 400 (thiếu lý do) →
  409 (store CLOSED/PENDING_APPROVAL). Khóa dòng yêu cầu trước, store sau.
- **P6 — Khóa đợt bán bằng JDBC** (`SaleBatchBlocker`) vì `SaleBatch` thuộc package `surprisebox` chưa có repository/nghiệp
  vụ (T4.3). Câu lệnh tăng `version`, đặt `updated_at`.
- **P7 — Lịch sử:** `AuditService.record` thêm `beforeState: String? = null` (tham số cuối, lời gọi cũ không đổi). Truy vấn lịch
  sử theo `store_id` + `entity_type IN ('STORE','STORE_CHANGE_REQUEST')`, sort `createdAt` desc (whitelist `PageQuery`).
  Vai trò người làm: `OWNER` nếu actor = chủ store, ngược lại `ADMIN`; owner không thấy `actorId` (`@JsonInclude(NON_NULL)`).
- **P8 — `ApplicationDocumentValidator`** thêm tham số `requireMandatory: Boolean = true` để yêu cầu thay đổi không bắt buộc
  đủ bộ D04 (sửa nhỏ code T3.2, lời gọi cũ không đổi). `PartnerApplicationService.submit` ghi `store.description`.
- **P9 — Số lượng trong test:** `SwaggerUiTests` 23→34 paths, 9→11 tags; `AuthMigrationTests`,
  `FoodRescueApplicationTests` 5→6 migration; `FoodRescueApplicationTests` cũng đếm entity JPA (40→41, phát hiện khi build).
- **P10 — Tìm cửa hàng (admin):** query dẫn xuất `findAllByNameContainingIgnoreCase` / `...AndStatus` (`q` rỗng = tất cả),
  tránh `:param is null` với enum trên PostgreSQL.

## 1. Approach summary

Package `store`: `StoreProfileService` (owner xem/sửa/lịch sử, xem công khai), `StoreChangeRequestService` (owner gửi/xem/hủy),
`StoreChangeReviewService` (admin danh sách/chi tiết/quyết định), `StoreStatusService` (admin khóa/kích hoạt),
`StoreAdminQueryService` (admin danh sách/chi tiết), `StoreSalesPolicy`, `SaleBatchBlocker`, `StoreViews`,
`StoreHistoryRecorder`, `StoreChangeAttachmentPolicy`. Bốn controller: owner, công khai, admin stores, admin change requests.
Migration V6.

## 2. Codebase survey results

Như Gate A §2. Bổ sung: `/api/v1/**` mặc định `authenticated()` → API công khai chỉ cần đăng nhập (D10). Method security có
sẵn (`@PreAuthorize("hasRole('ADMIN')")`). Ràng buộc CHECK của `attachment` tên `attachment_check` (kiểm trên DB dev).
`customer_order` có trigger `protect_order_terms` (V3) chặn sửa snapshot — T3.3 không sửa bảng này.

## 3. Design

### 3.1 API contract

**Owner** (tag `Stores`, bearer; quyền `requirePermission(account, store, "STORE_PROFILE_MANAGE")` → chỉ owner store ACTIVE/SUSPENDED):

| Method | Path | Body | 2xx | Lỗi |
|---|---|---|---|---|
| GET | `/api/v1/stores/{storeId}/profile` | — | 200 `StoreProfileView` | 401, 403 |
| PATCH | `/api/v1/stores/{storeId}/profile` | `{version, description?, openingHours?}` | 200 `StoreProfileView` | 400 field, 401, 403, 409 version |
| POST | `/api/v1/stores/{storeId}/change-requests` | `StoreChangeRequestRequest` | 201 `ChangeRequestView` | 400, 401, 403, 409 đã có PENDING |
| GET | `/api/v1/stores/{storeId}/change-requests` | — | 200 `ChangeRequestView[]` mới nhất trước | 401, 403 |
| POST | `/api/v1/stores/{storeId}/change-requests/{requestId}/cancel` | — | 200 `ChangeRequestView` | 401, 403, 404, 409 |
| GET | `/api/v1/stores/{storeId}/history?page&size` | — | 200 `PageResponse<StoreHistoryItem>` | 400 phân trang, 401, 403 |

**Công khai** (tag `Stores`, bearer): `GET /api/v1/stores/{storeId}` → 200 `StorePublicView` khi ACTIVE; 404 còn lại.

**Admin** (tag `Admin stores`, bearer + ROLE_ADMIN):

| Method | Path | 2xx | Lỗi |
|---|---|---|---|
| GET | `/api/v1/admin/stores?status&q&page&size` | 200 `PageResponse<AdminStoreSummary>` | 400, 401, 403 |
| GET | `/api/v1/admin/stores/{storeId}` | 200 `AdminStoreDetail` | 401, 403, 404 |
| POST | `/api/v1/admin/stores/{storeId}/status` `{action: SUSPEND\|REACTIVATE, reason}` | 200 `AdminStoreDetail` | 400, 401, 403 (không phải admin / store của mình), 404, 409 sai trạng thái |
| GET | `/api/v1/admin/store-change-requests?status&page&size` | 200 `PageResponse<AdminChangeRequestView>` | 400, 401, 403 |
| GET | `/api/v1/admin/store-change-requests/{id}` | 200 `AdminChangeRequestView` | 401, 403, 404 |
| POST | `/api/v1/admin/store-change-requests/{id}/decision` `{decision: APPROVE\|REJECT, reason}` | 200 `AdminChangeRequestView` | 400, 401, 403, 404, 409 |

DTO:
- `StoreProfileView`: id, name, description, address, latitude, longitude, timezone, status, statusReason, statusChangedAt,
  version, openingHours[`OpeningHourView`], pendingChangeRequest (`ChangeRequestView`?).
- `StorePublicView`: id, name, description, address, latitude, longitude, timezone, openingHours.
- `StoreChangeRequestRequest`: name? (≤255), address? (≤1000), latitude?/longitude? (miền + 7 số lẻ), note? (≤2000),
  documents? (≤10, `ApplicationDocumentRequest`).
- `ChangeRequestView`: id, status, current/proposed (`StoreChangeValues`: name, address, latitude, longitude — null = không đổi),
  note, documents[`DocumentView`], requestedAt, resolvedAt, resolutionReason.
- `AdminChangeRequestView`: request (`ChangeRequestView`), store (`StoreSummary`), requestedBy, resolvedBy (`AccountSummary`?).
- `StoreHistoryItem`: action, occurredAt, actorRole (`OWNER`/`ADMIN`), actorId (chỉ admin), before, after (JSON).
- `AdminStoreSummary`: id, name, status, owner, statusChangedAt, createdAt.
- `AdminStoreDetail`: profile, owner, saleBatchCounts {status: n}, orderCounts {status: n}, recentHistory (≤ 50).

### 3.2 Sửa ngay (`StoreProfileService.update`, một transaction)

```
requirePermission → parse (400) → validate hours (400)
store = findForUpdate; version khác → 409
đổi description? ghi before/after; đổi ca (so danh sách OpeningHourView)? thay toàn bộ ca, ghi before/after
không đổi gì → trả view (không audit)
chỉ đổi ca → em.lock(store, PESSIMISTIC_FORCE_INCREMENT); saveAndFlush
audit STORE_PROFILE_UPDATED (entity STORE)
```

### 3.3 Yêu cầu thay đổi

Gửi: `requirePermission` → `findForUpdate(store)` (tuần tự hóa) → có PENDING → 409 → kiểm tra trường (trống, cặp tọa độ, không
khác gì → field `changes`), tài liệu (`requireMandatory=false`, file của owner) → lưu PENDING + attachment → audit
`STORE_CHANGE_REQUESTED`. Unique index `ux_store_change_request_pending` là lớp bảo vệ thứ hai (vi phạm → 409).
Hủy: khóa yêu cầu, phải thuộc store trên path (404), PENDING (409) → CANCELLED, audit.
Quyết định: P5; APPROVE áp `proposed` vào store (before = giá trị store lúc duyệt), audit `STORE_CHANGE_APPROVED` có
before/after; REJECT audit `STORE_CHANGE_REJECTED` + lý do.

### 3.4 Khóa / kích hoạt (`StoreStatusService.change`)

```
store = findForUpdate ?: 404; owner == admin → 403
SUSPEND: status != ACTIVE → 409; status=SUSPENDED, reason, changedAt; n = SaleBatchBlocker.block(store)
REACTIVATE: status != SUSPENDED → 409; status=ACTIVE, statusReason=null, changedAt
audit STORE_SUSPENDED|STORE_REACTIVATED before {status, statusReason} after {status, reason, blockedSaleBatches}
```
`SaleBatchBlocker`: `UPDATE sale_batch b SET status='BLOCKED', version=version+1, updated_at=? WHERE b.store_id=? AND
b.status IN ('DRAFT','OPEN','PAUSED') AND NOT EXISTS (SELECT 1 FROM customer_order o WHERE o.sale_batch_id=b.id)`.

### 3.5 Migration `V6__store_profile_and_change_requests.sql`

```sql
ALTER TABLE store ADD COLUMN description text, ADD COLUMN status_reason text, ADD COLUMN status_changed_at timestamptz;
UPDATE store s SET description = NULLIF(btrim(a.business_details::jsonb ->> 'businessDescription'), '')
  FROM partner_application a WHERE a.store_id = s.id
   AND a.revision = (SELECT max(revision) FROM partner_application x WHERE x.store_id = s.id);
CREATE TABLE store_change_request (... status CHECK IN ('PENDING','APPROVED','REJECTED','CANCELLED'), FK store/account ...);
CREATE UNIQUE INDEX ux_store_change_request_pending ON store_change_request (store_id) WHERE status = 'PENDING';
ALTER TABLE attachment ADD COLUMN store_change_request_id uuid REFERENCES store_change_request(id);
ALTER TABLE attachment DROP CONSTRAINT attachment_check;
ALTER TABLE attachment ADD CONSTRAINT attachment_check CHECK (num_nonnulls(..., store_change_request_id) = 1);
```

### 3.6 Quyền

- Owner API: `StoreAuthorizationService.requirePermission(..., "STORE_PROFILE_MANAGE")` (không sửa service này).
- `StoreChangeAttachmentPolicy`: đọc được nếu là chủ store của yêu cầu hoặc ADMIN.
- `StoreSalesPolicy.isSellable(store) = status == ACTIVE`; `requireSellable` → 409 `STORE_NOT_ACTIVE` (dành cho T4.3/T5.x).

## 4. File layout

Kotlin tương đối với `src/main/kotlin/com/foodrescue/backend/` và `src/test/kotlin/com/foodrescue/backend/`.

| File | NEW/MODIFIED | Trách nhiệm |
|---|---|---|
| `resources/db/migration/V6__store_profile_and_change_requests.sql` | NEW | 3.5 |
| `store/StoreChangeRequestStatus.kt`, `store/ChangeRequestDecision.kt`, `store/StoreStatusAction.kt` | NEW | Enum |
| `store/entity/Store.kt` | MODIFIED | `description`, `statusReason`, `statusChangedAt` |
| `store/entity/StoreChangeRequest.kt` | NEW | Entity yêu cầu |
| `file/entity/Attachment.kt` | MODIFIED | `storeChangeRequest` |
| `store/repository/StoreRepository.kt` | MODIFIED | Tìm kiếm admin |
| `store/repository/StoreChangeRequestRepository.kt` | NEW | Truy vấn + khóa |
| `audit/AuditService.kt`, `audit/repository/AuditLogRepository.kt` | MODIFIED | `beforeState`; truy vấn lịch sử store |
| `store/service/ApplicationDocumentValidator.kt`, `PartnerApplicationService.kt` | MODIFIED | P8 |
| `store/service/StoreProfileUpdateParser.kt` | NEW | P2 |
| `store/service/StoreProfileService.kt` | NEW | 3.2, xem, lịch sử, công khai |
| `store/service/StoreChangeRequestService.kt` | NEW | 3.3 owner |
| `store/service/StoreChangeReviewService.kt` | NEW | 3.3 admin |
| `store/service/StoreStatusService.kt` | NEW | 3.4 |
| `store/service/StoreAdminQueryService.kt` | NEW | Danh sách/chi tiết admin |
| `store/service/SaleBatchBlocker.kt`, `StoreSalesPolicy.kt` | NEW | BR-T33-07/08 |
| `store/service/StoreViews.kt`, `StoreHistoryRecorder.kt` | NEW | Map view; ghi audit before/after |
| `store/service/StoreChangeAttachmentPolicy.kt` | NEW | Quyền đọc tài liệu yêu cầu |
| `store/dto/*` (12 DTO) | NEW | Mục 3.1 |
| `store/controller/StoreProfileController.kt`, `PublicStoreController.kt`, `AdminStoreController.kt`, `AdminStoreChangeRequestController.kt` | NEW | HTTP |
| `docs/api/openapi.yaml` | MODIFIED | 11 path, 2 tag, schema |
| test `StoreProfileTests.kt`, `StoreChangeRequestTests.kt`, `StoreStatusTests.kt`, `StoreSalesPolicyTests.kt`, `support/StoreTestFixtures.kt` | NEW | AC |
| test `SwaggerUiTests.kt`, `AuthMigrationTests.kt`, `FoodRescueApplicationTests.kt` | MODIFIED | P9 (+ số entity 40→41) |

## 5. Task list

- [x] 1. V6 + entity/enum/repository; sửa số migration trong test.
- [x] 2. `AuditService` beforeState, `StoreHistoryRecorder`, `StoreViews`, `StoreSalesPolicy`, `SaleBatchBlocker`.
- [x] 3. Owner xem/sửa (parser, service, controller) + test AC-01..04, AC-08, AC-11, AC-13.
- [x] 4. Yêu cầu thay đổi owner + admin + attachment policy + test AC-05..07.
- [x] 5. Admin danh sách/chi tiết/khóa/kích hoạt + test AC-09, AC-10, AC-14, AC-15.
- [x] 6. API công khai + test AC-12.
- [x] 7. OpenAPI + `SwaggerUiTests`; `cleanTest build` xanh.
- [x] 8. Chạy thử app dev, viết 03, whitelist, roadmap, commit.

## 6. Traceability AC → task → test

| AC | Task | Test |
|---|---|---|
| AC-01 | 3 | `StoreProfileTests` `AC-01 owner sees profile...` |
| AC-02 | 3 | `StoreProfileTests` `AC-02 ...` |
| AC-03 | 3 | `StoreProfileTests` `AC-03 ...` |
| AC-04 | 3 | `StoreProfileTests` `AC-04 ...` (gồm đồng thời) |
| AC-05, AC-06 | 4 | `StoreChangeRequestTests` `AC-05 ...`, `AC-06 ...` (đồng thời) |
| AC-07 | 4 | `StoreChangeRequestTests` `AC-07 ...` (duyệt ‖ hủy đồng thời) |
| AC-08 | 3, 4 | `StoreProfileTests` `AC-08 ...`, `StoreChangeRequestTests` (tài liệu 404) |
| AC-09, AC-10 | 5 | `StoreStatusTests` `AC-09 ...`, `AC-10 ...` (đồng thời) |
| AC-11 | 3, 5 | `StoreProfileTests` `AC-03` (status → 400), `StoreStatusTests` `AC-11 ...` |
| AC-12 | 6 | `StoreStatusTests` `AC-12 ...`, `StoreSalesPolicyTests` |
| AC-13 | 3 | `StoreProfileTests` `AC-13 ...` |
| AC-14 | 5 | `StoreStatusTests` `AC-14 ...` |
| AC-15 | 5, 7 | `StoreStatusTests` `AC-15 ...`, `SwaggerUiTests` |

## 7. Test strategy

Integration test kế thừa `AuthTestFixtures` (Testcontainers, HTTP thật). Store được tạo qua API T3.2 (nộp + duyệt) rồi dùng
API T3.3. Đợt bán/đơn chèn bằng SQL (`support/StoreTestFixtures`: food_category, box_template, financial_policy, sale_batch,
customer_order) vì T4/T5 chưa có. Đồng thời: `CountDownLatch` + 2 luồng (sửa cùng version, gửi 2 yêu cầu, duyệt ‖ hủy,
khóa ‖ khóa). Unit test `StoreSalesPolicy` cho mọi `StoreStatus`.

## 8. Rollout / rollback

V6 chỉ thêm cột/bảng/index và thay CHECK `attachment` (thêm cột vào danh sách) — tương thích dữ liệu cũ. Rollback: revert code;
DB giữ V6 (cột thêm nullable, không ảnh hưởng code cũ trừ `ddl-auto validate` vẫn qua vì code cũ không ánh xạ cột mới).

## 9. Rủi ro

- Version không tăng khi chỉ đổi ca → P1 + test đồng thời.
- Khóa ‖ nộp đơn (T5.2) sau này: T5.2 phải khóa/đọc trạng thái store trong transaction giữ hộp — ghi chú cho T5.2.
- `audit_log` lớn dần → lịch sử phân trang; index `audit_log(store_id)` đã có từ V2 nên V6 không thêm.
