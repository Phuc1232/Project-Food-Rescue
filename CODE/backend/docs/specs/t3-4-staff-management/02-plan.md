# Technical Plan: T3.4 — Quản lý nhân viên và phân quyền

**Based on:** [01-business-definition.md](01-business-definition.md) (Gate A tự chốt 2026-11-15)
**Status:** Done — xem 03-implementation-control.txt.
**Last updated:** 2026-11-15

## 0. Quyết định

Gate A Q1–Q9. Gate B (agent chọn):
- **P1 — Khóa:** thêm nhân viên khóa dòng `store` (tuần tự hóa theo cửa hàng); tạo tài khoản mới bắt lỗi unique `login_identifier`
  → 409 (đồng thời khác cửa hàng). Đổi quyền / thu hồi / chấp nhận / từ chối khóa dòng `staff_assignment`.
- **P2 — Auth:** `TokenPurpose.SET_PASSWORD`; `AuthProperties.setupTtl` (mặc định 72h); `EmailVerificationService.issue` chọn TTL
  theo purpose; `SmtpAuthMailSender` tiêu đề "Set up your FoodRescue staff account", dùng trang `reset-password`;
  `PasswordRecoveryService.reset` chấp nhận SET_PASSWORD khi tài khoản PENDING_VERIFICATION hoặc ACTIVE chưa xác minh → ACTIVE,
  `emailVerifiedAt = now`. RESET_PASSWORD giữ nguyên điều kiện cũ.
- **P3 — Mật khẩu tài khoản mới:** hash của chuỗi ngẫu nhiên 32 byte (không ai biết) → không đăng nhập được trước khi đặt mật khẩu
  (login còn chặn PENDING_VERIFICATION).
- **P4 — Body:** typed DTO + Bean Validation (`email` @Email ≤ 255, `displayName` ≤ 255, `permissions` ≤ 10 phần tử chuỗi);
  mã quyền kiểm ở service để báo `permissions[i]`.
- **P5 — Test hằng số:** migration 6→7; Swagger paths 34→39, tags 11→12 (tag `Staff`).

## 1. API

| Method | Path | Body | 2xx | Lỗi |
|---|---|---|---|---|
| GET | `/api/v1/stores/{storeId}/staff?status=` | — | 200 `StaffMemberView[]` | 401, 403 |
| POST | `/api/v1/stores/{storeId}/staff` | `{email, displayName?, permissions[]}` | 201 `StaffMemberView` (+`accountCreated`) | 400, 401, 403, 409 |
| PUT | `/api/v1/stores/{storeId}/staff/{assignmentId}/permissions` | `{permissions[]}` | 200 `StaffMemberView` | 400, 401, 403, 404, 409 |
| POST | `/api/v1/stores/{storeId}/staff/{assignmentId}/revoke` | `{reason}` | 200 `StaffMemberView` | 400, 401, 403, 404 |
| GET | `/api/v1/staff/assignments` | — | 200 `MyAssignmentView[]` (INVITED, ACTIVE) | 401 |
| POST | `/api/v1/staff/assignments/{id}/accept` · `/decline` | — | 200 `MyAssignmentView` | 401, 404, 409 |

`StaffMemberView`: assignmentId, account {id, email, displayName}, status, permissions[], assignedAt, revokedAt, accountCreated.
`MyAssignmentView`: assignmentId, store {id, name, status}, status, permissions[], assignedAt.

## 2. Migration V7

```sql
ALTER TABLE staff_assignment DROP CONSTRAINT staff_assignment_status_check;  -- tên kiểm trên DB
ALTER TABLE staff_assignment ADD CONSTRAINT staff_assignment_status_check CHECK (status IN ('INVITED','ACTIVE','DECLINED','REVOKED'));
ALTER TABLE account_action_token DROP CONSTRAINT <purpose check>; ADD CHECK (purpose IN ('VERIFY_EMAIL','RESET_PASSWORD','SET_PASSWORD'));
INSERT INTO permission ... 6 mã (gen_random_uuid(), now()).
```

## 3. File layout

| File | NEW/MODIFIED |
|---|---|
| `resources/db/migration/V7__staff_permissions_and_invitations.sql` | NEW |
| `staff/AssignmentStatus.kt`, `staff/StaffPermissionCode.kt` | MODIFIED / NEW |
| `staff/repository/*` (3) | MODIFIED |
| `staff/dto/*` (StaffInviteRequest, StaffPermissionsRequest, StaffRevokeRequest, StaffMemberView, MyAssignmentView) | NEW |
| `staff/service/StaffAccountProvisioner.kt` (tạo tài khoản + token) | NEW |
| `staff/service/StaffManagementService.kt` (owner), `StaffSelfService.kt` (người được mời/staff), `StaffViews.kt` | NEW |
| `staff/controller/StoreStaffController.kt`, `MyStaffAssignmentController.kt` | NEW |
| `auth/TokenPurpose.kt`, `auth/AuthProperties.kt`, `auth/mail/SmtpAuthMailSender.kt`, `auth/service/EmailVerificationService.kt`, `auth/service/PasswordRecoveryService.kt` | MODIFIED (P2) |
| `store/service/StoreHistoryRecorder.kt` | MODIFIED (Q8) |
| `docs/api/openapi.yaml` | MODIFIED |
| test `StaffManagementTests.kt`; hằng số `SwaggerUiTests`, `AuthMigrationTests`, `FoodRescueApplicationTests` | NEW / MODIFIED |

## 4. Task list

- [x] V7 + enum + repository; auth P2.
- [x] Service/controller owner + staff; audit.
- [x] Test AC-01..10 (đồng thời AC-05); OpenAPI; build xanh; smoke dev; báo cáo; commit.

## 5. Traceability

| AC | Test (`StaffManagementTests`) |
|---|---|
| AC-01, AC-09 | `AC-01 AC-09 owner lists staff and staff sees own stores` |
| AC-02 | `AC-02 new email creates account with setup link` |
| AC-03 | `AC-03 existing account must accept the invitation` |
| AC-04 | `AC-04 invalid invitations are rejected without side effects` |
| AC-05 | `AC-05 concurrent invitations never duplicate accounts` |
| AC-06 | `AC-06 permission changes apply on the next request` |
| AC-07 | `AC-07 revocation is immediate and does not lock the account` |
| AC-08 | `AC-08 only the owner manages staff` |
| AC-10 | `AC-10 seeded catalogue` + `SwaggerUiTests` |

## 6. Rủi ro / rollback

- Sửa auth: test `PasswordRecoveryTests` hiện có phải vẫn qua (RESET_PASSWORD không đổi điều kiện).
- Rollback: revert code; V7 chỉ nới CHECK và thêm seed (code cũ vẫn chạy).
