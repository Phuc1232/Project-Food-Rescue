# Technical Plan: T3.2 — Đăng ký đối tác và xét duyệt

**Based on:** [01-business-definition.md](01-business-definition.md) (Gate A đã duyệt 2026-11-14)
**Status:** Done — đã triển khai, build xanh. Xem 03-implementation-control.txt.
**Last updated:** 2026-11-14

## 0. Approved decisions (từ Gate A)

- [x] Q1: Ngữ cảnh cửa hàng chỉ tính cho store `status NOT IN ('PENDING_APPROVAL','CLOSED')` (sửa `StoreAuthorizationService`).
- [x] Q2: `NEEDS_INFO`. Q3: luôn cho nộp lại sau từ chối. Q4: D04 = `BUSINESS_REGISTRATION`*, `FOOD_SAFETY`*,
  `STOREFRONT_PHOTO`, `OTHER` (* bắt buộc), ≤ 10 tài liệu. Q5: loại tài liệu trong snapshot `business_details` + `attachment`.
  Q6: `contactPhone` (bắt buộc), `businessDescription` trong snapshot. Q7: `audit/AuditService` chung. Q8: ADMIN không tự xét.
  Q9: đường dẫn như Gate A. Q10: không gửi thông báo.

### Quyết định kỹ thuật phát sinh ở Gate B

- **P1 — Số migration trong test.** `AuthMigrationTests` và `FoodRescueApplicationTests` khẳng định đúng 4 migration →
  đổi thành 5 (chỉ đổi hằng số).
- **P2 — AC-12 "đã có cửa hàng được duyệt".** Với unique index `store(owner_id) WHERE status <> 'CLOSED'`, một chủ không thể
  có cửa hàng thứ hai chưa đóng, nên tình huống chỉ xảy ra khi chính store của hồ sơ đã đổi trạng thái (SD-01 #32 "hồ sơ
  hoặc quyền sở hữu đã thay đổi"). Chấp thuận kiểm tra lại store vẫn `PENDING_APPROVAL` (đã khóa dòng) và không có store
  khác chưa đóng của người nộp → nếu không, 409. Test giả lập bằng cách đổi trạng thái store.
- **P3 — Giờ hoạt động nhập dạng chuỗi `HH:mm`** (`@Pattern`) để lỗi định dạng ra field cụ thể; `closesNextDay` do server suy ra
  (`closesAt <= opensAt`), giờ mở bằng giờ đóng → 400. `shiftNumber` gán theo thứ tự giờ mở trong ngày.
- **P4 — Loại tài liệu và múi giờ nhận dạng chuỗi**, kiểm tra ở service để báo đúng field (`documents[i].type`,
  `timezone`). Body sai kiểu JSON (số thay chuỗi...) vẫn là 400 `INVALID_REQUEST` của handler chung.

## 1. Approach summary

Package `store`: `PartnerApplicationService` (nộp/xem của tôi), `PartnerApplicationReviewService` (danh sách, chi tiết, quyết
định), `OpeningHoursValidator`, `ApplicationDocumentValidator`, mapper snapshot JSON; controller cho người nộp và controller
admin riêng. `audit/AuditService` mới. `PartnerApplicationAttachmentPolicy` cài điểm mở rộng của T3.1. Migration V5.

## 2. Codebase survey results

Như Gate A §2, bổ sung: role nằm trong authorities `ROLE_ADMIN` (converter đọc DB mỗi request) → admin controller dùng
`@PreAuthorize("hasRole('ADMIN')")` (method security đã bật); `PageQuery` whitelist sort `createdAt`; `StoreAuthorizationTests`
tạo store `ACTIVE` nên vẫn qua sau Q1; `Clock` bean có sẵn (`AuthClockConfiguration`).

## 3. Design

### 3.1 API contract

**Người nộp** (tag `Partner applications`, bearer):

| Method | Path | Body | 2xx | Lỗi |
|---|---|---|---|---|
| POST | `/api/v1/partner-applications` | `PartnerApplicationRequest` | 201 hồ sơ mới; **200** khi đã có revision SUBMITTED (trả nguyên trạng) | 400 field; 401; 409 đã sở hữu cửa hàng |
| GET | `/api/v1/partner-applications/me` | — | 200 `MyPartnerApplicationResponse` | 401; 404 chưa nộp |

`PartnerApplicationRequest`:
```json
{ "storeName": "Bánh mì Cô Ba", "address": "12 Lê Lợi, Q1, TP.HCM",
  "latitude": 10.7769, "longitude": 106.7009, "timezone": "Asia/Ho_Chi_Minh",
  "contactPhone": "0901234567", "businessDescription": "Bánh mì, bánh ngọt",
  "openingHours": [ { "dayOfWeek": 1, "opensAt": "06:00", "closesAt": "21:00" },
                    { "dayOfWeek": 5, "opensAt": "18:00", "closesAt": "01:00" } ],
  "documents": [ { "type": "BUSINESS_REGISTRATION", "fileId": "…" }, { "type": "FOOD_SAFETY", "fileId": "…" } ] }
```
Ràng buộc: `storeName` 1–255; `address` 1–1000; `latitude` −90..90, `longitude` −180..180, ≤ 7 chữ số thập phân;
`timezone` IANA (bỏ trống → `Asia/Ho_Chi_Minh`); `contactPhone` `^(0|\+84)[35789][0-9]{8}$`; `businessDescription` ≤ 2000;
`openingHours` 1–21 ca, `dayOfWeek` 1 (Thứ 2)..7 (CN), giờ `HH:mm`; `documents` 1–10, `fileId` không trùng.

`MyPartnerApplicationResponse`: `store {id, name, status}`, `current` (= `ApplicationView`: id, revision, status, submittedAt,
reviewedAt, reviewReason, submission snapshot, documents [{fileId, type, originalName, mimeType, sizeBytes}]),
`history` [{id, revision, status, submittedAt, reviewedAt, reviewReason}] (mới nhất trước). Không có reviewer.

**Admin** (tag `Admin partner applications`, bearer + ROLE_ADMIN):

| Method | Path | 2xx | Lỗi |
|---|---|---|---|
| GET | `/api/v1/admin/partner-applications?status=&page=0&size=20` | 200 `PageResponse<AdminApplicationSummary>` (mới nhất trước) | 400 status/phân trang sai; 401; 403 |
| GET | `/api/v1/admin/partner-applications/{id}` | 200 `AdminApplicationDetail` | 400; 401; 403; 404 |
| POST | `/api/v1/admin/partner-applications/{id}/decision` | 200 `AdminApplicationDetail` sau quyết định | 400 (`decision`, `reason`); 401; 403 (không phải admin, hoặc tự xét); 404; 409 đã xử lý / store đã đổi |

`AdminApplicationSummary`: id, storeId, storeName, applicant {id, email, displayName}, revision, status, submittedAt.
`AdminApplicationDetail`: summary + store {id, status, name, address, latitude, longitude, timezone, openingHours},
reviewer {id, displayName}?, reviewedAt, reviewReason, submission, documents, history (mọi revision của store).
`DecisionRequest`: `{ "decision": "APPROVE" | "REJECT" | "REQUEST_INFO", "reason": "…" }` — `reason` 1–2000, bắt buộc với
REJECT/REQUEST_INFO.

### 3.2 Nộp hồ sơ (`PartnerApplicationService.submit`, một transaction)

```
lock account (findForUpdate)
store = stores.findOpenByOwner(applicant)                 // status <> CLOSED
if store != null && store.status != PENDING_APPROVAL → 409 "Account already owns a store"
latest = store?.let { apps.findTopByStoreIdOrderByRevisionDesc(it.id) }
if latest?.status == SUBMITTED → return (latest, created=false)   // 200
validate hours (OpeningHoursValidator), timezone, documents (ApplicationDocumentValidator: loại hợp lệ, đủ bắt buộc,
  file tồn tại và uploadedBy == applicant) → gom field errors → 400
store = store?.apply { cập nhật name/address/lat/lng/tz } ?: new Store(applicant, ..., PENDING_APPROVAL)
replace opening hours (delete + flush + insert)
app = PartnerApplication(store, applicant, (latest?.revision ?: 0)+1, snapshotJson, SUBMITTED, submittedAt=clock)
attachments (file, partnerApplication=app) cho từng tài liệu
audit PARTNER_APPLICATION_SUBMITTED → return (app, created=true)  // 201
```
Hai request nộp lần đầu đồng thời: khóa account tuần tự hóa → request sau thấy revision SUBMITTED → 200 cùng hồ sơ.
Unique index là lớp bảo vệ thứ hai.

### 3.3 Quyết định (`PartnerApplicationReviewService.decide`, một transaction)

```
app = apps.findForUpdate(id) ?: 404
if app.applicant.id == admin → 403
if app.status != SUBMITTED → 409 "Application has already been decided"
validate reason theo decision → 400 field reason
APPROVE: store = stores.findForUpdate(app.store.id)
         if store.status != PENDING_APPROVAL || stores.existsOtherOpenStore(owner, store.id) → 409
         store.status = ACTIVE
REJECT → REJECTED; REQUEST_INFO → NEEDS_INFO
app.reviewer, reviewedAt=clock, reviewReason=reason (APPROVE: ghi chú tùy chọn)
audit PARTNER_APPLICATION_APPROVED|REJECTED|INFO_REQUESTED
```

### 3.4 Giờ hoạt động (`OpeningHoursValidator`)

Mỗi ca → khoảng phút trong tuần `[start, end)`, `start = (day−1)·1440 + opens`, `end = start + duration`
(`duration = closes − opens`, cộng 1440 nếu qua đêm). Hai ca chồng nhau nếu giao nhau, xét cả bản dịch ±10080 phút
(CN qua đêm chạm sáng Thứ 2). Mỗi ngày ≤ 3 ca. Lỗi gắn field `openingHours[i]`.

### 3.5 Migration `V5__partner_application_review.sql`

```sql
ALTER TABLE partner_application DROP CONSTRAINT partner_application_status_check;
ALTER TABLE partner_application ADD CONSTRAINT partner_application_status_check
  CHECK (status IN ('SUBMITTED','NEEDS_INFO','APPROVED','REJECTED'));
CREATE UNIQUE INDEX ux_partner_application_pending ON partner_application(store_id) WHERE status = 'SUBMITTED';
CREATE UNIQUE INDEX ux_store_open_owner ON store(owner_id) WHERE status <> 'CLOSED';
```

### 3.6 Quyền

- `StoreAuthorizationService.contexts`: thêm `s.status NOT IN ('PENDING_APPROVAL','CLOSED') AND (...)`.
- `PartnerApplicationAttachmentPolicy`: `supports` khi `attachment.partnerApplication != null`; đọc được nếu là người nộp
  hoặc ADMIN.

## 4. File layout

Kotlin tương đối với `src/main/kotlin/com/foodrescue/backend/` và `src/test/kotlin/com/foodrescue/backend/`.

| File | NEW/MODIFIED | Trách nhiệm |
|---|---|---|
| `resources/db/migration/V5__partner_application_review.sql` | NEW | 3.5 |
| `store/ApplicationStatus.kt` | MODIFIED | Thêm `NEEDS_INFO` |
| `store/ApplicationDecision.kt`, `store/ApplicationDocumentType.kt` | NEW | Enum quyết định, loại tài liệu D04 |
| `store/repository/StoreRepository.kt`, `PartnerApplicationRepository.kt`, `StoreOpeningHourRepository.kt` | MODIFIED | Truy vấn + khóa |
| `store/dto/*` (request, opening hour, document, views, decision) | NEW | DTO, mỗi DTO một file |
| `store/service/OpeningHoursValidator.kt` | NEW | 3.4 |
| `store/service/ApplicationDocumentValidator.kt` | NEW | D04 + sở hữu file |
| `store/service/ApplicationSnapshot.kt` | NEW | Snapshot JSON ↔ DTO |
| `store/service/PartnerApplicationViews.kt` | NEW | Dựng view cho người nộp/admin |
| `store/service/PartnerApplicationService.kt` | NEW | Nộp, xem của tôi |
| `store/service/PartnerApplicationReviewService.kt` | NEW | Danh sách, chi tiết, quyết định |
| `store/service/PartnerApplicationAttachmentPolicy.kt` | NEW | Quyền đọc tài liệu hồ sơ |
| `store/controller/PartnerApplicationController.kt`, `AdminPartnerApplicationController.kt` | NEW | HTTP |
| `audit/AuditService.kt` | NEW | Audit chung (Q7) |
| `common/security/StoreAuthorizationService.kt` | MODIFIED | Q1 |
| `docs/api/openapi.yaml` | MODIFIED | 4 path mới, 2 tag |
| test `OpeningHoursValidatorTests.kt` | NEW | Unit |
| test `PartnerApplicationTests.kt` | NEW | Integration AC-01..14 |
| test `SwaggerUiTests.kt`, `AuthMigrationTests.kt`, `FoodRescueApplicationTests.kt` | MODIFIED | Hằng số path/tag/migration (P1) |

## 5. Task list

- [x] B1. V5 + enum; chạy migration test.
- [x] B2. Unit test + `OpeningHoursValidator`.
- [x] B3. Integration test `PartnerApplicationTests` (đỏ).
- [x] B4. Repository, DTO, validator tài liệu, snapshot, `AuditService`, service nộp/xem, controller người nộp.
- [x] B5. Service/controller admin, attachment policy.
- [x] B6. `StoreAuthorizationService` (Q1).
- [x] B7. OpenAPI + hằng số test; build đầy đủ; smoke trên app dev.
- [x] B8. Báo cáo 03, roadmap `[x]`, `.gitignore`, commit, push, PR.

## 6. Traceability

| AC | Task | Test (`PartnerApplicationTests` trừ khi ghi khác) |
|---|---|---|
| AC-01 nộp hợp lệ | B4 | submit happy path (store, giờ, revision, attachment, audit) |
| AC-02 đã chờ → trả hồ sơ cũ; đồng thời | B4 | resubmit while pending; concurrent first submit |
| AC-03 dữ liệu sai → 400 field | B2, B4 | invalid submissions; `OpeningHoursValidatorTests` |
| AC-04 đã sở hữu → 409 | B4 | owner of active store |
| AC-05 chờ duyệt không có quyền owner | B6 | pending store has no store context (`/me`, `requirePermission`) |
| AC-06 403/401/404 | B5 | admin endpoints forbidden; stranger cannot read |
| AC-07 admin xem danh sách/chi tiết/tài liệu | B5 | admin list and detail |
| AC-08 bổ sung → nộp lại → duyệt | B4, B5 | request info flow |
| AC-09 từ chối cần lý do → nộp lại | B5 | reject flow |
| AC-10 chấp thuận → ACTIVE + owner | B5, B6 | approve grants owner context |
| AC-11 quyết định đồng thời | B5 | concurrent decisions |
| AC-12 store đã đổi → 409; tự xét → 403 | B5 | approval rechecks store; self review |
| AC-13 người nộp xem của tôi | B4 | my application view (không lộ reviewer) |
| AC-14 audit, OpenAPI | B4, B7 | audit rows; `SwaggerUiTests`, `ApiContractTests` |

## 7. Test strategy

Integration kế thừa `AuthTestFixtures`; tài liệu upload thật qua `POST /api/v1/files`; ADMIN tạo bằng insert `account_role`.
Đồng thời: hai luồng + `CountDownLatch`. Không sửa/skip test cũ ngoài hằng số P1.

## 8. Rollout & rollback

V5 chỉ thêm CHECK value và index. Rollback DB: migration bù (drop 2 index, khôi phục CHECK cũ nếu không còn `NEEDS_INFO`).
Trước khi chạy V5 trên DB có dữ liệu: kiểm tra không có chủ nào có > 1 store chưa đóng (hiện chưa có API tạo store nên rỗng).

## 9. Risks

| Rủi ro | Giảm thiểu |
|---|---|
| Q1 đổi hành vi quyền chung | Chỉ loại PENDING_APPROVAL/CLOSED; test cũ (store ACTIVE) giữ nguyên; thêm test pending |
| Deadlock giữa nộp và duyệt | Nộp khóa account; duyệt khóa application → store; không có thứ tự ngược trên cùng dòng |
| Snapshot JSON lệch schema theo thời gian | Snapshot chỉ để hiển thị lịch sử; dữ liệu hiện hành nằm ở cột `store` |

## 10. Out of scope / later

Sửa cửa hàng sau duyệt, khóa/mở (T3.3); thông báo (T8.2); rút hồ sơ; nhiều cửa hàng.

## 11. References & open questions

Gate A §8. Không còn câu hỏi mở.

## 12. Change log

- 2026-11-14: Tạo Gate B.
- 2026-11-14: Khi triển khai phát hiện và sửa: `StoreOpeningHour` lưu sai giờ (Hibernate `jdbc.time_zone=UTC` quy đổi
  `LocalTime` theo múi giờ JVM, offset năm 1970 của VN là +8) → gắn `@JdbcType(LocalTimeJdbcType)` cho `opens_at`/`closes_at`.
  `FoodRescueApplicationTests` tạo store thứ hai cho cùng chủ → đổi store đó thành CLOSED (ràng buộc D8). `SwaggerUiTests`
  23 paths / 9 tags. `@Valid` trên phần tử list đổi sang `@field:Valid` (Kotlin không phát annotation type-use).
