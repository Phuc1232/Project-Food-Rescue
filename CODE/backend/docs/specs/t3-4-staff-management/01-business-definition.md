# Business Definition: T3.4 — Quản lý nhân viên và phân quyền

**Status:** Gate A tự chốt bởi agent 2026-11-15 (người dùng cho phép tự quyết Gate A/B trong phiên; đề xuất Q1–Q9 được chấp nhận).
**Updated:** 2026-11-15
**Card:** [roadmap-3-9.md#t34](../roadmap-3-9.md) · **Nhánh:** `feature/t3-4-staff-management` (tách từ `feature/t3-3-store-profile` a6a3d66,
T3.3 chưa merge)
**Tài liệu nguồn:** UC-43, UC-44, UC-45, UC-46, BR-03, SD-02 (sequence 02), D01 (cơ chế cấp tài khoản, chưa có văn bản).

## 1. Bối cảnh và mục tiêu

Chủ cửa hàng cần người làm (đóng gói, quản lý hộp, bàn giao). Owner xem danh sách nhân viên, thêm nhân viên theo email (liên kết
tài khoản có sẵn hoặc tạo tài khoản mới), chọn bộ quyền nghiệp vụ hẹp, đổi quyền, thu hồi quyền có lý do. Quyền được kiểm tra lại ở
máy chủ mỗi request, nên thu hồi có hiệu lực ngay cả khi nhân viên còn phiên đăng nhập. Không có quyền giá/hoa hồng/chính sách/quản
trị cho staff (BR-03).

## 2. Khảo sát mã hiện tại

- `staff/entity/StaffAssignment` ↔ `staff_assignment` (account, store, status ∈ {ACTIVE, REVOKED}, assigned_at, revoked_at,
  UNIQUE(store_id, account_id)); `StaffPermission` ↔ `staff_permission` (assignment, permission, granted_by, granted_at,
  UNIQUE(assignment, permission)); `Permission` ↔ `permission(code UNIQUE, description)`. Repository rỗng. **Bảng `permission`
  chưa có seed.**
- `common/security/StoreAuthorizationService` đã đọc `staff_assignment` ACTIVE + `staff_permission` từ DB mỗi request → thu hồi
  có hiệu lực request kế tiếp mà không cần sửa. `GET /api/v1/accounts/me` trả `stores` (owner/permissions) từ cùng hàm.
- Auth: `RegistrationService` (tạo account PENDING_VERIFICATION + role CUSTOMER + mail xác minh), `PasswordRecoveryService.reset`
  chỉ nhận token RESET_PASSWORD của tài khoản ACTIVE đã xác minh; TTL reset 15 phút; `account_action_token.purpose` có CHECK
  {VERIFY_EMAIL, RESET_PASSWORD}; `AuthMailSender.send(email, purpose, token)` (test dùng `RecordingAuthMailSender`).
- T3.3: mã quyền owner-only `STORE_PROFILE_MANAGE` không seed; `AuditService` có before/after.
- Migration mới nhất: V6 → task này dùng **V7**.
- **Baseline** (`cleanTest build` trên a6a3d66): 113 tests, 112 pass, 1 skipped, 0 failures.

## 3. Người dùng và quyền

| Vai trò | Được làm |
|---|---|
| Owner (store ACTIVE/SUSPENDED) | Xem, thêm, đổi quyền, thu hồi nhân viên cửa hàng mình (UC-43..46) |
| Tài khoản được mời | Xem lời mời/phân công của mình, chấp nhận hoặc từ chối lời mời |
| Staff ACTIVE | Xem các cửa hàng mình làm và quyền được cấp |
| Staff, người khác, ADMIN không phải owner | Không quản lý nhân viên cửa hàng → 403 |

## 4. Luồng chính (SD-02)

- **Thêm (UC-44):** owner gửi email, tên hiển thị (khi tạo mới), bộ quyền. Hệ thống khóa store, chuẩn hóa email, kiểm tra quyền thuộc
  danh mục staff, không phải chính owner. Có tài khoản ACTIVE đã xác minh → tạo lời mời `INVITED` (chưa có quyền) chờ người đó
  chấp nhận (A1: "chứng minh đúng người và cho phép"). Chưa có tài khoản → tạo tài khoản + phân công `ACTIVE` + gửi link thiết lập
  mật khẩu (người nhận chứng minh sở hữu email bằng link). Tài khoản bị khóa/chưa xác minh → từ chối.
- **Chấp nhận/từ chối lời mời:** tài khoản được mời → ACTIVE (có quyền) hoặc DECLINED.
- **Đổi quyền (UC-45):** thay toàn bộ bộ quyền của phân công INVITED/ACTIVE; lưu người cấp, thời điểm; áp dụng từ request kế tiếp.
- **Thu hồi (UC-46):** lý do bắt buộc → REVOKED, xóa quyền hiện hành, lịch sử còn trong audit; đã thu hồi → trả trạng thái hiện tại,
  không ghi trùng (A1). Tài khoản vẫn hoạt động trên nền tảng.

## 5. Quy tắc nghiệp vụ

- **BR-T34-01** Danh mục quyền staff (seed V7): `MENU_VIEW`, `BOX_PACK`, `BOX_MANAGE`, `PICKUP_HANDLE`, `ORDER_VIEW`, `NO_SHOW_CONFIRM`.
  Mã ngoài danh mục (kể cả owner/admin/giá/hoa hồng/chính sách, `STORE_PROFILE_MANAGE`, `STAFF_MANAGE`) → 400 (UC-44 E1, UC-45 E1).
  Bộ quyền có thể rỗng (nhân viên chưa làm gì).
- **BR-T34-02** Không thêm owner của chính cửa hàng; không trùng phân công đang INVITED/ACTIVE (409). Phân công cũ REVOKED/DECLINED
  được mời lại (dùng lại dòng, giữ ràng buộc UNIQUE).
- **BR-T34-03** Không tạo tài khoản trùng: email chuẩn hóa như đăng ký; tạo đồng thời cùng email → một tài khoản.
- **BR-T34-04** Tài khoản mới: PENDING_VERIFICATION, mật khẩu ngẫu nhiên không dùng được, role CUSTOMER; token `SET_PASSWORD` (TTL
  72h, cấu hình được) gửi qua `AuthMailSender`; đặt mật khẩu qua `POST /api/v1/auth/reset-password` sẵn có → ACTIVE + đã xác minh.
- **BR-T34-05** Quyền chỉ có hiệu lực khi phân công ACTIVE và store khác PENDING_APPROVAL/CLOSED (cơ chế T3.2 Q1 giữ nguyên).
- **BR-T34-06** Đổi quyền / thu hồi khóa dòng phân công; thao tác lên phân công REVOKED/DECLINED (trừ thu hồi lặp) → 409.
- **BR-T34-07** Audit (entity `STAFF_ASSIGNMENT`, store): `STAFF_INVITED`, `STAFF_ACCOUNT_CREATED`, `STAFF_INVITATION_ACCEPTED`,
  `STAFF_INVITATION_DECLINED`, `STAFF_PERMISSIONS_CHANGED` (before/after), `STAFF_REVOKED` (lý do). Hiện trong lịch sử cửa hàng T3.3.
- **BR-T34-08** Giới hạn 50 phân công INVITED/ACTIVE mỗi cửa hàng (chống lạm dụng gửi mail).

## 6. Phạm vi

Trong: seed quyền, API owner (list/thêm/đổi quyền/thu hồi), API tài khoản được mời/staff (xem, chấp nhận, từ chối), token
SET_PASSWORD, audit, OpenAPI, test. Ngoài: dùng quyền staff trong nghiệp vụ (T4.x, T7.x kiểm bằng `requirePermission`); khóa tài
khoản toàn nền tảng (T3.5); thông báo in-app (T8.2).

## 7. Acceptance criteria

| ID | Given / When / Then |
|---|---|
| AC-01 | Owner xem danh sách nhân viên (lọc trạng thái): tài khoản (id, email, tên), trạng thái, quyền, thời điểm mời/thu hồi. Chưa có → []. |
| AC-02 | Owner thêm email chưa có tài khoản + quyền hợp lệ → 201 ACTIVE, `accountCreated=true`; đúng một tài khoản PENDING_VERIFICATION, một mail SET_PASSWORD; người nhận đặt mật khẩu qua reset-password → đăng nhập được, `GET /me` có store với đúng quyền; audit. |
| AC-03 | Owner thêm email đã có tài khoản ACTIVE → 201 INVITED, chưa có ngữ cảnh store; người được mời xem lời mời, chấp nhận → ACTIVE có quyền; từ chối → DECLINED. Người khác chấp nhận lời mời không phải của mình → 404. |
| AC-04 | Quyền ngoài danh mục (`OWNER`, `PRICE_EDIT`, `STORE_PROFILE_MANAGE`...), email sai, thiếu tên khi tạo mới → 400 field; thêm chính owner → 400; đã INVITED/ACTIVE → 409; tài khoản LOCKED/chưa xác minh → 409. Không tạo gì. |
| AC-05 | Hai yêu cầu thêm cùng email mới đồng thời (cùng hoặc khác cửa hàng) → không trùng tài khoản; cùng cửa hàng → một 201, một 409. |
| AC-06 | Owner đổi bộ quyền → 200, `GET /me` của staff phản ánh ngay; audit before/after; quyền ngoài danh mục → 400; phân công REVOKED → 409. |
| AC-07 | Owner thu hồi có lý do → REVOKED; request kế tiếp của staff (token cũ) không còn ngữ cảnh store, `requirePermission` → 403; tài khoản vẫn đăng nhập/dùng nền tảng được; thu hồi lần hai → 200 không ghi audit thêm; thiếu lý do → 400. Mời lại sau thu hồi → INVITED. |
| AC-08 | Quyền: không token 401; staff, owner khác, ADMIN không sở hữu → 403 trên API owner; store PENDING_APPROVAL → 403. |
| AC-09 | Staff xem phân công của mình: các cửa hàng (id, tên, trạng thái), trạng thái phân công, quyền. |
| AC-10 | Seed V7 đúng 6 mã; OpenAPI có endpoint mới; `SwaggerUiTests`, `ApiContractTests` qua. |

## 8. Truy vết

| Nguồn | Đáp ứng |
|---|---|
| UC-43 bước 1–3 (danh sách, quyền, trạng thái truy cập); E1 | AC-01, AC-08 |
| UC-44 bước 2–5; hậu điều kiện "không tạo trùng, không đổi cửa hàng khác" | AC-02, AC-04, AC-05 |
| UC-44 A1 (đã có tài khoản: liên kết khi chứng minh đúng người và cho phép) | AC-03 (lời mời phải chấp nhận) |
| UC-44 E1 (cấp quyền chủ/quản trị) / E2 (thông tin sai) | AC-04 |
| UC-44 bước 6 (hướng dẫn thiết lập truy cập an toàn) | AC-02 (link SET_PASSWORD) |
| UC-45 bước 2–5; E1 (giá, hoa hồng, quản trị); E2 (không còn thuộc phạm vi) | AC-06 |
| UC-46 bước 3–5 (lý do, từ chối request tiếp theo kể cả còn phiên); A1; hậu điều kiện (không khóa toàn nền tảng) | AC-07 |
| UC-46 E2 (thao tác đang chạy) | BR-T34-05: quyền đọc lại trong transaction ghi của nghiệp vụ |
| BR-03 | BR-T34-01, AC-04 |
| SD-02 #03–05, #08–13, #14–17 | AC-02..07 |
| Card: "staff xem các cửa hàng mình làm" | AC-09 |

## 9. Quyết định (agent tự chốt)

- [x] **Q1 — Danh mục quyền:** 6 mã như card (BR-T34-01).
- [x] **Q2 — D01 tài khoản có sẵn:** lời mời INVITED + chấp nhận (V7 thêm INVITED, DECLINED vào CHECK). Phương án bỏ: liên kết thẳng
  (trái UC-44 A1 "chứng minh đúng người").
- [x] **Q3 — D01 tài khoản mới:** tạo tài khoản + token `SET_PASSWORD` 72h qua reset-password sẵn có; sửa nhỏ auth (thêm purpose,
  `reset` chấp nhận SET_PASSWORD cho tài khoản chưa xác minh và kích hoạt nó). Lý do: TTL reset 15 phút quá ngắn cho lời mời,
  và reset hiện từ chối tài khoản chưa xác minh.
- [x] **Q4 — Thu hồi xóa `staff_permission` hiện hành**, lịch sử ở audit (bảng không có cột thu hồi quyền riêng).
- [x] **Q5 — Owner-only:** mã `STAFF_MANAGE` không seed (như `STORE_PROFILE_MANAGE`).
- [x] **Q6 — Đường dẫn:** owner `/api/v1/stores/{storeId}/staff` (GET, POST), `/{assignmentId}/permissions` (PUT),
  `/{assignmentId}/revoke` (POST); tài khoản `/api/v1/staff/assignments` (GET), `/{id}/accept`, `/{id}/decline` (POST).
- [x] **Q7 — Giới hạn 50 phân công mở/cửa hàng.**
- [x] **Q8 — Lịch sử cửa hàng T3.3 gồm sự kiện nhân viên** (thêm `STAFF_ASSIGNMENT` vào danh sách entity của lịch sử).
- [x] **Q9 — Store SUSPENDED:** owner vẫn quản lý nhân viên (khắc phục), staff vẫn có ngữ cảnh (nghiệp vụ bán bị chặn bởi
  `StoreSalesPolicy`).

## 10. Change log

- 2026-11-15: Tạo và tự chốt Gate A (quyền tự quyết từ người dùng).
