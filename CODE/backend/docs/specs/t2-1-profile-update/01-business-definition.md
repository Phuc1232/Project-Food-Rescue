# Business Definition: T2.1 — Cập nhật hồ sơ cá nhân

**Status:** Gate A đã duyệt 2026-11-14 (chấp nhận nguyên đề xuất Q1–Q6).
**Updated:** 2026-11-14
**Card:** [roadmap-3-9.md#t21](../roadmap-3-9.md) · **Nhánh:** `feature/t2-1-profile-update` (tách từ `master`)
**Tài liệu nguồn:** UC-05, UC-06 (Nghiệp Vụ FOOD&RECSUE final, mục "Use Case Description"); class diagram `TaiKhoan`;
báo cáo T0 mục 4 (F1).

## 1. Bối cảnh và mục tiêu

T0 ghi nhận F1: giai đoạn 1–2 mới có `GET /api/v1/accounts/me` (UC-05), chưa có cập nhật hồ sơ (UC-06).
Mục tiêu T2.1: người dùng đã đăng nhập sửa được **đúng các trường hồ sơ được phép** của chính mình, không thể
qua biểu mẫu hồ sơ đổi vai trò, cửa hàng hay thông tin xác thực, và không ghi đè im lặng thay đổi đồng thời.

## 2. Khảo sát mã hiện tại

Đường dẫn Kotlin tương đối với `src/main/kotlin/com/foodrescue/backend/`.

- `account/controller/AccountController.kt`: chỉ có `GET /api/v1/accounts/me` (đọc trực tiếp repository, trả
  `AccountProfileResponse(id, email, displayName, roles, stores)`). Chưa có service account.
- `account/entity/Account.kt`: `loginIdentifier`, `passwordHash`, `displayName` (`varchar(255) NOT NULL`), `status`,
  `emailVerifiedAt`; kế thừa `MutableEntity` → đã có `@Version version` và `updatedAt` (`@PreUpdate`).
  **Không có cột số điện thoại** (V1, V4). `AccountRepository.findForUpdate` có sẵn (khóa `PESSIMISTIC_WRITE`).
- Vai trò (`account_role`), cửa hàng (`store.owner_id`, `staff_assignment`) và xác thực (`login_identifier`,
  `password_hash`) nằm ở bảng/luồng khác; đổi mật khẩu đã có ở `PasswordChangeService` (`/api/v1/auth/...`).
- `auth/service/RegistrationService.kt`: `displayName` khi đăng ký được `trim()`; `RegisterRequest` ràng buộc
  `@NotBlank @Size(max=255)`.
- `application.yml`: `spring.jackson…fail-on-unknown-properties: true` → hiện tại body có trường lạ trả
  400 `INVALID_REQUEST` **không kèm tên trường** (qua `HttpMessageNotReadableException` trong `ApiExceptionHandler`).
- Lỗi chung: `ApiException(status, code, message, fields)`; mã có sẵn `VALIDATION_FAILED`, `INVALID_REQUEST`,
  `CONFLICT` (409).
- `audit/AuthAuditService.record(account, action)` ghi `audit_log` với `correlationId` = requestId.
- `docs/api/openapi.yaml`: có `/api/v1/accounts/me` (get) và schema `AccountProfileResponse`.
- Test: `support/AuthTestFixtures` (Testcontainers, HTTP thật); các test hiện có gọi `GET /api/v1/accounts/me`.
- Baseline build: xem mục 9.

## 3. Người dùng và quyền

- Người dùng đã đăng nhập, tài khoản `ACTIVE` (mọi vai trò: CUSTOMER, ADMIN, owner, staff): chỉ sửa hồ sơ **của chính
  mình**; account lấy từ token (`CurrentAccount`), không nhận accountId từ client.
- Chưa đăng nhập / token không hợp lệ / phiên bị thu hồi / tài khoản bị khóa → 401 (cơ chế sẵn có).
- Không có API để admin sửa hồ sơ người khác trong task này (quản trị tài khoản thuộc T3.5).

## 4. Luồng chính (UC-06)

1. Người dùng mở hồ sơ: `GET /api/v1/accounts/me` trả thông tin hiện tại + `version` (UC-06 bước 1–2, UC-05).
2. Người dùng gửi `PATCH /api/v1/accounts/me` với các trường được phép + `version` đã đọc (bước 3).
3. Hệ thống kiểm tra: body chỉ chứa trường được phép; giá trị hợp lệ; `version` khớp bản đang lưu (bước 4).
4. Hợp lệ → lưu trong một transaction, trả 200 với hồ sơ mới (cùng dạng `GET /me`, `version` mới) (bước 5).
5. Không hợp lệ → 400/409, dữ liệu đã lưu không đổi (hậu điều kiện thất bại, E1, E2).
6. Hủy chỉnh sửa (A1) là thao tác phía client — không gọi API, không cần backend xử lý.

## 5. Quy tắc nghiệp vụ

Mã BR-T21-xx là quy tắc riêng của task (không trùng BR-01..56 của tài liệu nghiệp vụ).

- **BR-T21-01** Trường được phép sửa: chỉ `displayName` (tên hiển thị = `hoTen` trong class diagram, phương thức
  `capNhatHoTen`). Số điện thoại **không** thêm (xem Q2).
- **BR-T21-02** Trường vượt quyền (vai trò, cửa hàng, quyền, trạng thái, email/định danh đăng nhập, mật khẩu,
  `id`… hoặc bất kỳ trường không có trong danh sách cho phép) → **từ chối cả request** 400, chỉ rõ tên trường;
  không lưu phần nào, không nâng quyền (UC-06 E2). Không "bỏ qua im lặng".
- **BR-T21-03** `displayName`: cắt khoảng trắng hai đầu (như đăng ký); sau khi cắt phải không rỗng, tối đa 255 ký tự,
  không chứa ký tự điều khiển (xuống dòng, tab…). Sai → 400 `VALIDATION_FAILED`, `fieldErrors[].field = "displayName"`
  (UC-06 E1).
- **BR-T21-04** Chống ghi đè: client gửi `version` đọc được từ `GET /me`; khác bản đang lưu → 409 `CONFLICT`
  ("hồ sơ đã được cập nhật ở nơi khác, tải lại"), không lưu. Hai request đồng thời cùng `version` → tối đa một
  thành công (khóa dòng hoặc `@Version` của JPA).
- **BR-T21-05** Không có thay đổi thực sự (giá trị sau chuẩn hóa trùng giá trị đang lưu) → 200 trả hồ sơ hiện tại,
  `version` không tăng.
- **BR-T21-06** Response không chứa mật khẩu, hash, token (UC-05 bước 3); chỉ dùng DTO, không serialize entity.
- **BR-T21-07** Ghi `audit_log` action `PROFILE_UPDATED` khi có thay đổi thực sự; không ghi giá trị cũ/mới (dữ liệu
  cá nhân, NFR bảo vệ thông tin cá nhân) — xem Q4.

## 6. Phạm vi

### Trong phạm vi
- `PATCH /api/v1/accounts/me` (đổi đường dẫn so với card, xem Q1) cho `displayName`.
- Bổ sung `version` vào response `GET /api/v1/accounts/me` (thêm trường, không phá client cũ).
- Validation, chặn trường vượt quyền có báo tên trường, 409 khi lệch version, audit.
- Cập nhật `docs/api/openapi.yaml` (operation, request/response schema, ví dụ, mã lỗi 400/401/409); test tích hợp.

### Ngoài phạm vi
- Số điện thoại / migration mới (trừ khi Q2 chọn thêm).
- Đổi email, đổi mật khẩu (đã có luồng riêng), đổi vai trò/cửa hàng (T3.x), admin sửa hồ sơ người khác (T3.5).
- Ảnh đại diện (cần T3.1, tài liệu không yêu cầu).
- Sửa `.env.example`/README (F4), quyền thực thi `gradlew` (F5), toolchain (F6) — ghi ở báo cáo T0, không gộp vào T2.1.

## 7. Acceptance criteria

| ID | Given / When / Then |
|---|---|
| AC-01 | Given người dùng ACTIVE đã đăng nhập, `version` đúng; When PATCH `displayName` hợp lệ (có khoảng trắng đầu/cuối); Then 200, hồ sơ trả về có tên đã trim và `version` tăng; `GET /me` sau đó trả tên mới. |
| AC-02 | Given đã đăng nhập; When body chứa trường không được phép (`roles`, `stores`, `email`, `password`, `status`, `id`, trường lạ), kể cả kèm `displayName` hợp lệ; Then 400 `VALIDATION_FAILED`, `fieldErrors` liệt kê từng trường bị từ chối; tên, email, vai trò, quyền cửa hàng, mật khẩu không đổi (đăng nhập bằng mật khẩu cũ vẫn được, `roles` vẫn như cũ). |
| AC-03 | Given đã đăng nhập; When `displayName` rỗng, chỉ khoảng trắng, > 255 ký tự, chứa ký tự điều khiển, sai kiểu, hoặc thiếu; Then 400 `VALIDATION_FAILED` với `fieldErrors[].field = "displayName"`; dữ liệu không đổi. Thiếu/sai kiểu `version` → 400 với `field = "version"`. JSON hỏng → 400 `INVALID_REQUEST`. |
| AC-04 | Given hồ sơ đang ở `version` N; When PATCH với `version` ≠ N; Then 409 `CONFLICT`, dữ liệu không đổi. |
| AC-05 | Given hai request PATCH đồng thời cùng `version` N, tên khác nhau; Then đúng một 200, request còn lại 409; giá trị lưu là của request thành công, `version` = N+1. |
| AC-06 | Given không token / token sai / phiên đã logout; When PATCH; Then 401 và không có thay đổi. Không có cách sửa hồ sơ tài khoản khác (accountId chỉ lấy từ token). |
| AC-07 | Given PATCH với tên trùng tên đang lưu (sau trim); Then 200, `version` không đổi, không ghi audit. |
| AC-08 | Given PATCH thành công có thay đổi; Then `audit_log` có một dòng action `PROFILE_UPDATED`, actor = chính tài khoản, `correlationId` = requestId, không chứa giá trị tên. Response/log không chứa mật khẩu/hash/token. |
| AC-09 | `GET /api/v1/accounts/me` trả thêm `version`; endpoint mới có trong `openapi.yaml` (tag, schema, ví dụ, 400/401/409) và thử được trên Swagger `/docs` profile dev; `ApiContractTests` vẫn qua. |

## 8. Truy vết tài liệu → AC

| Nguồn | Nội dung | Đáp ứng |
|---|---|---|
| UC-05 bước 2–3 | Xem hồ sơ của chính mình, không hiển thị mật khẩu/dữ liệu xác thực | Đã có (giai đoạn 2); AC-09 (thêm `version`), AC-08 (không lộ secret) |
| UC-05 E1 | Ngoài phạm vi quyền → từ chối | AC-06 |
| UC-06 tiền điều kiện | Đã đăng nhập, sửa hồ sơ của chính mình | AC-06 |
| UC-06 bước 2 | Hiển thị thông tin hiện tại và trường được phép sửa | AC-09 (GET + OpenAPI mô tả trường được phép) |
| UC-06 bước 4 | Kiểm tra hợp lệ và quyền sửa từng trường | AC-02, AC-03 |
| UC-06 bước 5 / hậu điều kiện thành công | Lưu, hiển thị hồ sơ mới, đúng trường cho phép | AC-01, AC-07 |
| UC-06 hậu điều kiện thất bại | Thông tin đã lưu không đổi khi bị từ chối | AC-02, AC-03, AC-04, AC-05 |
| UC-06 E1 | Dữ liệu không hợp lệ: chỉ rõ trường cần sửa | AC-03 |
| UC-06 E2 | Đổi vai trò/cửa hàng/thông tin xác thực qua hồ sơ: từ chối trường vượt quyền, không nâng quyền | AC-02 |
| UC-06 A1 | Hủy chỉnh sửa giữ thông tin đã lưu | Phía client; không cần API |
| Class diagram `TaiKhoan.capNhatHoTen(hoTen)` | Chỉ tên là thuộc tính hồ sơ sửa được | BR-T21-01, AC-01 |
| NFR bảo vệ thông tin cá nhân | Chỉ thu thập dữ liệu cần thiết | BR-T21-01 (không thêm SĐT), BR-T21-07 |
| Card T2.1 "kiểm tra `@Version` tránh ghi đè" | Không ghi đè im lặng | AC-04, AC-05 |
| Card T2.1 "gửi kèm role/store/email/password → 400 hoặc bỏ qua có báo" | | AC-02 (chọn 400) |
| Card T2.1 "dữ liệu sai → 400 kèm field" | | AC-03 |

## 9. Baseline

`sh ./gradlew cleanTest build --console=plain` trên `feature/t2-1-profile-update` (= `master` 50a6365):
BUILD SUCCESSFUL (48s). 50 tests, 49 pass, 1 skipped, 0 failures, 0 errors — trùng số liệu T0
(ca skip: `SwaggerUiContextPathTests` sandbox browser opt-in `SWAGGER_BROWSER_SMOKE`). Baseline xanh.

## 10. Câu hỏi mở / quyết định cần duyệt

- [x] **Q1 — Đường dẫn.** Card ghi `PATCH /api/v1/me`, nhưng endpoint đọc hiện có là `/api/v1/accounts/me`.
  **Đề xuất:** `PATCH /api/v1/accounts/me` (cùng tài nguyên với GET) và sửa card cho khớp.
- [x] **Q2 — Số điện thoại.** Tài liệu nghiệp vụ không có trường số điện thoại ở bất kỳ UC/BR nào; class diagram
  `TaiKhoan` không có; schema không có cột. **Đề xuất: không thêm**, chỉ sửa `displayName`. Nếu anh/chị muốn thêm
  thì cần chốt: migration `V5` thêm `phone_number varchar(20) NULL`, định dạng (đề xuất E.164 hoặc số VN `0xxxxxxxxx`),
  có cho xóa (null) không, có unique không (đề xuất không), có cần xác minh OTP không (đề xuất không trong MVP).
- [x] **Q3 — Cách từ chối trường vượt quyền.** Card cho phép "400 hoặc bỏ qua có báo". **Đề xuất:** 400
  `VALIDATION_FAILED` kèm `fieldErrors` cho từng trường bị từ chối (BR-T21-02) — đúng UC-06 E2 "từ chối" và E1 "chỉ rõ
  trường". (Hiện cấu hình Jackson trả 400 `INVALID_REQUEST` không có tên trường; cách lấy tên trường sẽ nêu ở Gate B,
  ưu tiên không sửa `ApiExceptionHandler` dùng chung.)
- [x] **Q4 — Audit.** CLAUDE.md chỉ bắt buộc audit thao tác quan trọng. **Đề xuất:** vẫn ghi `PROFILE_UPDATED`
  (không lưu giá trị) qua `AuthAuditService` sẵn có, để truy vết khi có tranh chấp danh tính người nhận hàng.
- [x] **Q5 — Cách gửi `version`.** **Đề xuất:** trường `version` bắt buộc trong body PATCH và có trong response GET/PATCH
  (dễ thử trên Swagger và Android). Phương án khác: header `If-Match`/`ETag` (chuẩn HTTP hơn nhưng khó thử hơn).
- [x] **Q6 — Ràng buộc `displayName`.** **Đề xuất:** trim, 1–255 ký tự (giữ như đăng ký và cột DB), cấm ký tự điều khiển.
  Không sửa `RegisterRequest` (ngoài phạm vi) nên đăng ký vẫn có thể nhận ký tự điều khiển — ghi vào "phát hiện ngoài
  phạm vi" nếu xác nhận.

**Ghi chú nhánh:** `master` đã có roadmap nhưng chưa có báo cáo T0 và dấu `[x] T0` (nằm ở `feature/t0-baseline`,
chưa merge). T2.1 phụ thuộc T0 — coi như đã xong theo xác nhận của người dùng. Khi đóng task, dòng tiến độ
`| 0. Baseline | … |` trong roadmap sẽ bị sửa ở cả hai nhánh → có thể xung đột merge một dòng (giải quyết thành
`[x] T0 · [x] T2.1`).

## 11. Change log

- 2026-11-14: Tạo Gate A.
- 2026-11-14: Người dùng duyệt Gate A, chấp nhận đề xuất Q1–Q6 (PATCH `/api/v1/accounts/me`, chỉ `displayName`, không thêm SĐT,
  400 kèm field cho trường vượt quyền, audit `PROFILE_UPDATED`, `version` trong body, tên 1–255 ký tự không ký tự điều khiển).
