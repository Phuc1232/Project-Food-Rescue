# Business Definition: T3.5 — Quản trị tài khoản người dùng

**Status:** Gate A tự chốt bởi agent 2026-11-15 (quyền tự quyết trong phiên; Q1–Q6 theo đề xuất).
**Card:** [roadmap-3-9.md#t35](../roadmap-3-9.md) · **Nhánh:** `feature/t3-5-account-admin` (tách từ `feature/t3-4-staff-management` 6ebb8c7)
**Tài liệu nguồn:** UC-68 (tìm tài khoản), UC-69 (xem tài khoản), UC-70 (cập nhật trạng thái tài khoản), D10 của tài liệu nghiệp vụ
(điều kiện khóa khi còn nghiệp vụ tồn đọng — khác D10 của roadmap về đăng nhập khi tìm kiếm).

## 1. Bối cảnh

ADMIN tìm, xem và hạn chế/khôi phục tài khoản có căn cứ. Khóa phải có hiệu lực ngay request kế tiếp; không xóa đơn, tiền, lịch sử;
khôi phục không tự kích hoạt cửa hàng bị khóa hay cấp thêm vai trò; khóa người còn nghiệp vụ tồn đọng phải có phương án.

## 2. Khảo sát mã

- `account` (status ∈ ACTIVE, LOCKED, DISABLED, PENDING_VERIFICATION; email_verified_at), `account_role` (CUSTOMER, ADMIN).
  Không có cột lý do trạng thái.
- `SessionJwtAuthenticationConverter` kiểm mỗi request: phiên chưa thu hồi, chưa hết hạn, account ACTIVE + đã xác minh → khóa có
  hiệu lực ngay; `LoginService` từ chối tài khoản không ACTIVE. `SessionService.revokeAll(account, action)` thu hồi mọi phiên.
- `AuthAuditService` ghi `entity_type='AUTH'`, `entity_id=account` (REGISTERED, EMAIL_VERIFIED, LOGIN..., PASSWORD_RESET).
- Liên kết cửa hàng: `store.owner_id` (T3.2), `staff_assignment` (T3.4). Đơn: `customer_order` (chưa có API, T5.x).
- Migration mới nhất V7 → **V8**. Baseline 6ebb8c7: 122 tests, 121 pass, 1 skipped.

## 3. Quyền

Chỉ ADMIN (`@PreAuthorize`, role đọc DB mỗi request). Không phải ADMIN → 403, không token → 401. ADMIN không tự khóa mình.

## 4. Luồng

- Tìm (UC-68): lọc theo một phần email, vai trò, trạng thái; phân trang, mới nhất trước; thông tin tối thiểu (id, email, tên, vai
  trò, trạng thái, đã xác minh, ngày tạo).
- Xem (UC-69): hồ sơ, vai trò, cửa hàng sở hữu, phân công nhân viên, trạng thái + lý do, số nghiệp vụ tồn đọng, lịch sử (audit
  AUTH và ACCOUNT của tài khoản, mới nhất trước); không có mật khẩu/hash/token.
- Khóa (UC-70): ACTIVE → LOCKED với lý do; nếu tài khoản còn nghiệp vụ tồn đọng (đơn chưa kết thúc với tư cách khách hoặc ở cửa
  hàng mình sở hữu) mà không có `outstandingPlan` → 409 `OUTSTANDING_WORK` kèm số lượng (E2); thu hồi mọi phiên; lưu lý do; audit.
- Mở khóa (A1): LOCKED → ACTIVE với lý do; không đổi cửa hàng, vai trò, phân công; phiên cũ vẫn thu hồi (đăng nhập lại).

## 5. Quy tắc

- **BR-T35-01** Chuyển hợp lệ: ACTIVE → LOCKED, LOCKED → ACTIVE; khác → 409. Lý do 1–2000 bắt buộc.
- **BR-T35-02** Không tự khóa (403). Khóa dòng account khi đổi trạng thái (tuần tự hóa).
- **BR-T35-03** Đơn tồn đọng = `customer_order.status` ∈ {PENDING_PAYMENT, CONFIRMED, HANDOVER_IN_PROGRESS, PENDING_NO_SHOW} của
  khách là tài khoản hoặc của store tài khoản sở hữu (chưa CLOSED). Có tồn đọng → bắt buộc `outstandingPlan` (1–2000).
- **BR-T35-04** Không xóa/đổi đơn, tiền, cửa hàng, vai trò, phân công khi khóa/mở khóa.
- **BR-T35-05** Audit `ACCOUNT_LOCKED` / `ACCOUNT_UNLOCKED` (entity `ACCOUNT`, actor = admin, before/after, lý do, phương án).

## 6. Phạm vi

Trong: 3 API admin, V8 (`account.status_reason`, `status_changed_at`), audit, OpenAPI, test. Ngoài: vô hiệu hóa vĩnh viễn (DISABLED),
xóa tài khoản, cấp/thu hồi vai trò ADMIN (dùng bootstrap sẵn có), thông báo (T8.2), xử lý đơn tồn (T7.x).

## 7. Acceptance criteria

| ID | Given / When / Then |
|---|---|
| AC-01 | ADMIN tìm theo `q` (một phần email), `role`, `status`, phân trang → đúng tập, không có trường mật khẩu; tham số sai → 400. |
| AC-02 | ADMIN xem chi tiết: vai trò, cửa hàng sở hữu, phân công nhân viên, trạng thái, lý do, số đơn tồn đọng, lịch sử; không lộ hash/token. Không tồn tại → 404. |
| AC-03 | Khóa có lý do → LOCKED; request kế tiếp của tài khoản (token cũ) → 401; đăng nhập lại → 401; audit đủ người/thời điểm/lý do. |
| AC-04 | Mở khóa → ACTIVE; đăng nhập lại được; cửa hàng SUSPENDED vẫn SUSPENDED, vai trò/phân công không đổi. |
| AC-05 | Thiếu lý do → 400; khóa tài khoản đã LOCKED / mở khóa tài khoản ACTIVE / tài khoản PENDING_VERIFICATION → 409; tự khóa → 403. |
| AC-06 | Tài khoản có đơn tồn đọng (khách hoặc chủ cửa hàng) → khóa không phương án → 409 `OUTSTANDING_WORK` kèm số lượng, không đổi gì; có phương án → LOCKED, đơn không đổi, phương án trong audit. |
| AC-07 | Không phải ADMIN → 403 mọi API; không token → 401. Hai ADMIN khóa đồng thời → một 200, một 409, một audit. |
| AC-08 | OpenAPI có endpoint; `SwaggerUiTests` qua. |

## 8. Truy vết

| Nguồn | Đáp ứng |
|---|---|
| UC-68 bước 1–3, E1 | AC-01, AC-07 |
| UC-69 bước 3 (vai trò, liên kết cửa hàng, trạng thái, lịch sử; không mật khẩu) | AC-02 |
| UC-70 bước 3–5 (lý do, căn cứ, kiểm soát truy cập tiếp theo) | AC-03, AC-05 |
| UC-70 bước 6, hậu điều kiện (đơn, tiền giữ nguyên) | AC-06, BR-T35-04 |
| UC-70 A1 (khôi phục không kích hoạt cửa hàng/không thêm vai trò) | AC-04 |
| UC-70 E1 / E2 (D10: phương án tồn đọng) | AC-05 / AC-06 |
| Card: admin không tự khóa; audit đủ người/thời điểm/lý do | AC-05, AC-03 |

## 9. Quyết định (agent tự chốt)

- [x] Q1 — Khóa = `LOCKED` (có thể mở); không dùng DISABLED trong T3.5.
- [x] Q2 — D10: chặn khóa khi có đơn tồn đọng trừ khi gửi `outstandingPlan` (văn bản, lưu audit); không tự hủy đơn.
- [x] Q3 — Khóa thu hồi mọi phiên (`SessionService.revokeAll`), dù converter đã chặn theo trạng thái.
- [x] Q4 — V8 thêm `account.status_reason`, `account.status_changed_at`; sửa entity `Account` (code T0) — chỉ thêm field.
- [x] Q5 — ADMIN khóa được ADMIN khác (không được tự khóa).
- [x] Q6 — Đường dẫn: `GET /api/v1/admin/accounts`, `GET /api/v1/admin/accounts/{id}`, `POST /api/v1/admin/accounts/{id}/status`
  `{action: LOCK|UNLOCK, reason, outstandingPlan?}`.
