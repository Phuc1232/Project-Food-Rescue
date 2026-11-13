# Technical Plan: T2.1 — Cập nhật hồ sơ cá nhân

**Based on:** [01-business-definition.md](01-business-definition.md) (Gate A đã duyệt 2026-11-14)
**Status:** Done — Gate B duyệt 2026-11-14 (P1–P3 theo đề xuất); đã triển khai, build xanh. Xem 03-implementation-control.txt.
**Last updated:** 2026-11-14

## 0. Approved decisions (từ Gate A)

- [x] Q1: `PATCH /api/v1/accounts/me`; sửa card roadmap cho khớp khi đóng task.
- [x] Q2: Chỉ sửa `displayName`; không thêm số điện thoại, **không có migration**.
- [x] Q3: Trường vượt quyền / không cho phép → 400 `VALIDATION_FAILED`, `fieldErrors` liệt kê từng trường, không lưu gì.
- [x] Q4: Audit `PROFILE_UPDATED` qua `AuthAuditService` sẵn có, không lưu giá trị.
- [x] Q5: `version` bắt buộc trong body PATCH, trả trong response GET/PATCH; lệch → 409 `CONFLICT`.
- [x] Q6: `displayName` trim, 1–255 ký tự (code point), cấm ký tự điều khiển.

### Quyết định kỹ thuật cần duyệt ở Gate B

- [x] **P1 — Thêm `PATCH` vào CORS `allowedMethods`** trong `middleware/SecurityConfiguration.kt`
  (hiện `GET, POST, DELETE, OPTIONS`). Swagger cùng origin và Android không bị ảnh hưởng, nhưng client web khác
  origin (nằm trong `CORS_ORIGINS`) sẽ bị chặn preflight PATCH nếu không thêm. Đây là sửa code của task khác
  (CLAUDE.md §1) → cần anh/chị đồng ý. Thay đổi một từ, kèm một test preflight.
  Phương án thay thế: không sửa middleware, ghi vào "phát hiện ngoài phạm vi".
- [x] **P2 — Đọc body dạng `Map<String, Any?>` và tự validate**, thay vì DTO + `@Valid`. Lý do: cấu hình
  `fail-on-unknown-properties: true` khiến trường lạ (và sai kiểu) bị Jackson ném lỗi trước khi tới Jakarta
  Validation → chỉ ra 400 `INVALID_REQUEST` không có tên trường, không đạt AC-02/AC-03. Muốn có tên trường mà dùng
  DTO thì phải sửa `ApiExceptionHandler` dùng chung (task khác). Cách đề xuất gói gọn trong package `account`;
  lệch khỏi quy ước "validate bằng Jakarta Validation" chỉ cho endpoint này. `ApiExceptionHandler` không đổi.
- [x] **P3 — Khóa dòng `PESSIMISTIC_WRITE`** (`AccountRepository.findForUpdate` sẵn có) rồi so `version` thủ công.
  Request thứ hai cùng `version` chờ khóa, đọc thấy `version` mới → 409 xác định, không phụ thuộc
  `ObjectOptimisticLockingFailureException` (sẽ thành 500 qua handler chung). `@Version` của JPA vẫn là lớp bảo vệ thứ hai.

## 1. Approach summary

Thêm `AccountProfileService` trong package `account` chịu trách nhiệm đọc và cập nhật hồ sơ; `AccountController`
chỉ map HTTP ↔ service (chuyển logic đọc hiện có của `GET /me` vào service, hành vi giữ nguyên, thêm `version`).
Một parser riêng biến body JSON thành `ProfileUpdateCommand` đã chuẩn hóa, gom mọi vi phạm (trường không được phép,
sai kiểu, sai ràng buộc) vào một `ApiException(400, "VALIDATION_FAILED", ...)`. Service trong một transaction:
khóa dòng account → so `version` → bỏ qua nếu không đổi → cập nhật, `saveAndFlush` để có `version` mới → audit.

## 2. Codebase survey results

- Như Gate A §2. Bổ sung khi lập plan:
  - `SwaggerUiTests` khẳng định `openapi.yaml` có đúng 15 `paths`; thêm operation `patch` vào path `/api/v1/accounts/me`
    có sẵn → vẫn 15.
  - `AuthTestFixtures.call(path, method, body, token)` dùng `HttpRequest.method(...)` → gửi được `PATCH`.
  - Jackson 3 (`tools.jackson`) + `jackson-module-kotlin`. Body `Map` không chịu ảnh hưởng `fail-on-unknown-properties`.
  - `MutableEntity.touch()` tự cập nhật `updatedAt` (dùng `Instant.now()`, code sẵn có, không đổi).
  - `docs/api/openapi.yaml` là JSON (OpenAPI 3.1), lỗi dùng `components/responses/Error400..`.

## 3. Design

### 3.1 API contract

`PATCH /api/v1/accounts/me` — bearer bắt buộc. Tag `Account`.

Request (`application/json`, `ProfileUpdateRequest`, `additionalProperties: false`):
```json
{ "displayName": "Nguyễn Văn A", "version": 0 }
```

| Trường | Ràng buộc | Lỗi |
|---|---|---|
| `displayName` | bắt buộc; string JSON; sau trim: 1–255 code point; không có ký tự loại `Cc` | `fieldErrors[{field:"displayName", message:"Invalid value"}]` |
| `version` | bắt buộc; số nguyên JSON ≥ 0 (không nhận chuỗi, số thực) | `fieldErrors[{field:"version", message:"Invalid value"}]` |
| bất kỳ khóa khác | không được phép | `fieldErrors[{field:"<tên khóa>", message:"Field is not editable"}]` |

Phản hồi:

| HTTP | Code | Khi |
|---|---|---|
| 200 | — | Thành công hoặc không đổi; body `AccountProfileResponse` (có `version`) |
| 400 | `VALIDATION_FAILED` | Vi phạm bảng trên (gom tất cả trường lỗi, sắp theo tên trường) |
| 400 | `INVALID_REQUEST` | JSON hỏng, body rỗng, body không phải object (handler chung sẵn có) |
| 401 | `AUTHENTICATION_FAILED` | Không/sai token, phiên thu hồi, tài khoản không ACTIVE (cơ chế sẵn có) |
| 409 | `CONFLICT` | `version` khác bản đang lưu; message `"Profile was modified; reload and retry"` |

`GET /api/v1/accounts/me`: `AccountProfileResponse` thêm `version: integer (int64)` (bắt buộc). Thay đổi cộng thêm.

### 3.2 Xử lý trong service (`AccountProfileService.update`)

```
@Transactional
update(accountId, command):
  account = accounts.findForUpdate(accountId) ?: authenticationFailed()
  if account.version != command.version -> throw 409 CONFLICT
  if account.displayName == command.displayName -> return profile(account)   // AC-07: không flush, không audit
  account.displayName = command.displayName
  accounts.saveAndFlush(account)                                             // version +1, updatedAt
  audit.record(account, "PROFILE_UPDATED")                                   // AC-08
  return profile(account)
```

`profile(account)` = `AccountProfileResponse(id, email, displayName, roles, stores, version)` — dùng chung cho GET.

### 3.3 Parser (`ProfileUpdateRequestParser`)

- Input `Map<String, Any?>?` (null → 400 `INVALID_REQUEST`).
- Khóa ngoài `{displayName, version}` → vi phạm "Field is not editable" (kể cả `roles`, `stores`, `email`, `password`,
  `status`, `id`).
- `displayName`: phải là `String`; `trim()`; `codePointCount` trong 1..255; không ký tự `Character.CONTROL`.
- `version`: phải là `Int`/`Long` (Jackson map số nguyên JSON) ≥ 0; `BigInteger`, `Double`, `String`, `Boolean` → lỗi.
- Có vi phạm → `ApiException(400, "VALIDATION_FAILED", "Invalid request", fields)`.
- Hàm thuần, test được không cần Spring.

### 3.4 Data model và migration

Không có migration. Dùng cột `account.display_name`, `version`, `updated_at` sẵn có.

## 4. File layout

Đường dẫn Kotlin tương đối với `src/main/kotlin/com/foodrescue/backend/` (main) và `src/test/kotlin/com/foodrescue/backend/` (test).

| File | NEW/MODIFIED | Trách nhiệm |
|---|---|---|
| `account/service/AccountProfileService.kt` | NEW | Đọc hồ sơ, cập nhật hồ sơ (khóa, version, audit) |
| `account/service/ProfileUpdateRequestParser.kt` | NEW | Body JSON → `ProfileUpdateCommand`, gom field errors |
| `account/dto/ProfileUpdateCommand.kt` | NEW | Lệnh đã chuẩn hóa (`displayName`, `version`) |
| `account/dto/AccountProfileResponse.kt` | MODIFIED | Thêm `version` |
| `account/controller/AccountController.kt` | MODIFIED | GET gọi service; thêm `@PatchMapping("/me")` |
| `middleware/SecurityConfiguration.kt` | MODIFIED (chỉ khi P1 duyệt) | Thêm `"PATCH"` vào CORS `allowedMethods` |
| `docs/api/openapi.yaml` | MODIFIED | Operation `patch`, schema `ProfileUpdateRequest`, `version` trong `AccountProfileResponse`, ví dụ, 400/401/409 |
| test `ProfileUpdateTests.kt` | NEW | Integration AC-01..09 |
| test `ProfileUpdateRequestParserTests.kt` | NEW | Unit test parser (biên độ dài, Unicode, kiểu) |
| `docs/specs/t2-1-profile-update/03-implementation-control.txt` | NEW | Báo cáo |
| `docs/specs/roadmap-3-9.md` | MODIFIED | `[x] T2.1`, sửa path trong card |
| `.gitignore` | MODIFIED | `!docs/specs/t2-1-profile-update/` |

## 5. Task list

- [x] B1. Viết `ProfileUpdateRequestParserTests` (đỏ) → `ProfileUpdateCommand`, `ProfileUpdateRequestParser` (xanh).
- [x] B2. Viết `ProfileUpdateTests` (đỏ): happy path, trường cấm, dữ liệu sai, version lệch, đồng thời, 401, no-op, audit, GET có `version`.
- [x] B3. `AccountProfileResponse` thêm `version`; `AccountProfileService` (get + update); `AccountController` dùng service + PATCH.
- [x] B4. (Nếu P1) thêm `PATCH` vào CORS + test preflight.
- [x] B5. Cập nhật `openapi.yaml`; chạy `ApiContractTests`, `SwaggerUiTests`.
- [x] B6. `sh ./gradlew cleanTest build --console=plain`; đọc kết quả theo suite.
- [x] B7. Chạy app dev, bấm thử Swagger theo kịch bản; viết `03-implementation-control.txt`.
- [x] B8. Roadmap `[x]` + sửa path card; whitelist `.gitignore`; commit (không push).

## 6. Traceability

| AC | Task | Test |
|---|---|---|
| AC-01 cập nhật hợp lệ, trim, version tăng, GET thấy tên mới | B3 | `ProfileUpdateTests` happy path |
| AC-02 trường vượt quyền → 400 kèm field, không đổi gì, không nâng quyền | B1, B3 | `ProfileUpdateTests` forbidden fields (roles/stores/email/password/status/id/lạ; kiểm tra lại GET, login mật khẩu cũ, `roles`) ; parser test |
| AC-03 dữ liệu sai → 400 field `displayName`/`version`; JSON hỏng → `INVALID_REQUEST` | B1, B3 | parser tests (rỗng, khoảng trắng, 255/256 code point, emoji, `\n`, số, null, thiếu); `ProfileUpdateTests` invalid |
| AC-04 version lệch → 409 | B3 | `ProfileUpdateTests` stale version |
| AC-05 hai PATCH đồng thời → một 200, một 409 | B3 | `ProfileUpdateTests` concurrent (2 luồng, `CountDownLatch`) |
| AC-06 401 khi không token/đã logout | B3 | `ProfileUpdateTests` unauthenticated |
| AC-07 không đổi → 200, version giữ, không audit | B3 | `ProfileUpdateTests` no-op |
| AC-08 audit đúng actor/correlationId, không lộ secret | B3 | `ProfileUpdateTests` audit (query `audit_log`), response không có `password*` |
| AC-09 GET có `version`; OpenAPI; Swagger | B3, B5, B7 | `ProfileUpdateTests` GET; `ApiContractTests`, `SwaggerUiTests` (15 paths); bấm thử thủ công |

## 7. Test strategy

- Integration kế thừa `AuthTestFixtures` (Testcontainers, HTTP thật); unit test parser không cần Spring.
- Đồng thời: hai luồng gửi PATCH cùng `version`, đếm status `{200:1, 409:1}`, kiểm `version` trong DB = N+1.
- Không sửa/skip test hiện có. Kỳ vọng tổng: 50 test cũ + test mới, 1 skip như baseline.

## 8. Rollout & rollback

- Không migration → rollback = revert commit. Thêm `version` vào response GET là thay đổi cộng thêm, client cũ bỏ qua.
- Android/web cần gửi `version` khi PATCH (lấy từ GET /me).

## 9. Risks

| Rủi ro | Giảm thiểu |
|---|---|
| Lệch quy ước Jakarta Validation (P2) | Giới hạn trong một parser có unit test; ghi rõ trong báo cáo |
| Jackson map số lớn thành `BigInteger`/`Double` | Parser chỉ nhận `Int`/`Long`, có test |
| Khóa dòng `account` chặn đồng thời với login/refresh cùng account | Transaction ngắn (một UPDATE); login cũng khóa theo thứ tự account trước |
| `updatedAt` dùng `Instant.now()` trong `MutableEntity` (không qua `Clock`) | Code sẵn có, không sửa; test không kiểm giá trị `updatedAt` |
| Audit entityType là `"AUTH"` (do `AuthAuditService` cố định) | Chấp nhận; ghi chú trong báo cáo |

## 10. Out of scope / later

Số điện thoại, ảnh đại diện, đổi email, admin sửa hồ sơ người khác (T3.5), sửa `RegisterRequest` để cấm ký tự
điều khiển (ghi ở "phát hiện ngoài phạm vi"), F4–F6 của T0.

## 11. References & open questions

- UC-05, UC-06; class diagram `TaiKhoan.capNhatHoTen`; card T2.1; báo cáo T0 F1.
- Mở: P1, P2, P3 (mục 0).

## 12. Change log

- 2026-11-14: Tạo Gate B.
- 2026-11-14: Người dùng duyệt P1–P3 theo đề xuất và cho làm liền mạch tới commit. Thêm `ProfileUpdateCorsTests` (preflight PATCH) cho P1.
