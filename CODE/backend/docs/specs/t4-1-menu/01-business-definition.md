# Business Definition: T4.1 — Menu nội bộ và lịch sử giá

**Status:** Gate A tự chốt bởi agent 2026-11-15 (quyền tự quyết trong phiên).
**Card:** [roadmap-3-9.md#t41](../roadmap-3-9.md) · **Nhánh:** `feature/t4-1-menu` (từ `feature/t3-5-account-admin` b2125e8)
**Tài liệu nguồn:** UC-24, UC-50, UC-51, UC-52, UC-53, UC-75 (phần xem), BR-04..08.

## 1. Bối cảnh

Menu nội bộ là danh sách món với **giá niêm yết thông thường** (BR-04), dùng làm căn cứ tính giá trị hộp khi đóng gói (T4.4). Chỉ owner
tạo/sửa món và giá; đổi giá có lý do và lịch sử bất biến; giá mới chỉ áp cho hộp đóng sau (BR-07, snapshot ở T4.4). Món ngừng kinh
doanh không được chọn đóng hộp mới (BR-06). Owner cung cấp tài liệu tham chiếu giá có phiên bản (UC-53, BR-08). Khách không xem menu (BR-05).

## 2. Khảo sát mã

- `menu_item` (store, category, name, listed_price > 0 nguyên đồng, ingredients, safety_warnings, status ∈ {ACTIVE, DISCONTINUED},
  version), `menu_price_history` (previous/new > 0, changed_by, changed_at, reason; **trigger V3 chặn UPDATE/DELETE**),
  `food_category` (code UNIQUE, name, active) **chưa seed**. Entity `catalog/entity/*` có sẵn, repository rỗng.
- `attachment.menu_item_id` có sẵn; tài liệu cấp cửa hàng/nhóm món không có chỗ gắn và không có mô tả/thời gian áp dụng/phiên bản.
- Quyền: `requirePermission` (owner mọi quyền; staff theo mã, T3.4 có `MENU_VIEW`). ADMIN kiểm qua `AccountRoleRepository`.
- Test: `AuthTestFixtures.REFERENCE_TABLES` (T3.4) — bảng seed phải thêm vào.
- Migration mới nhất V8 → **V9**. Baseline b2125e8: 128 tests, 127 pass, 1 skipped.

## 3. Quyền

| Vai trò | Được làm |
|---|---|
| Owner (store ACTIVE/SUSPENDED) | Xem, tạo, sửa món/giá, đổi trạng thái, tải tài liệu giá |
| Staff ACTIVE có `MENU_VIEW` | Xem menu, chi tiết món gồm lịch sử giá và tài liệu (chỉ đọc) |
| ADMIN | Xem menu, lịch sử giá, tài liệu của mọi cửa hàng (UC-75) |
| Khách / staff không có quyền / người khác | 403 |

Staff gửi thao tác ghi → 403 (UC-50 E2). Mã quyền ghi `MENU_MANAGE` không seed (owner-only).

## 4. Luồng

- Thêm (UC-50): tên, nhóm (`categoryCode`), giá niêm yết, thành phần, cảnh báo an toàn, `active` (false = chưa kinh doanh, A1) → lưu món
  + phiên bản giá ban đầu (history previous = new, lý do "Initial price").
- Sửa (UC-51): gửi `version` + trường cần đổi; đổi giá bắt buộc `priceChangeReason` → ghi history cùng transaction; version cũ → 409 (E2).
- Trạng thái (UC-52): ACTIVE ⇄ DISCONTINUED với lý do; mở lại kiểm tra đủ dữ liệu và nhóm còn hoạt động (A1/E1). Lịch sử trạng thái ở audit.
- Tài liệu (UC-53): owner tải file (T3.1) rồi đăng ký tài liệu: mô tả nguồn, ngày áp dụng, phạm vi (một món hoặc toàn menu), tùy chọn
  thay thế tài liệu cũ (A1: tài liệu cũ đánh dấu bị thay, vẫn giữ). Đăng ký không xác nhận giá trung thực.
- Xem (UC-24, UC-75): danh sách món (lọc trạng thái, nhóm), chi tiết món gồm lịch sử giá và tài liệu liên quan.

## 5. Quy tắc

- **BR-T41-01** Giá niêm yết: số nguyên đồng, 1..100.000.000; ≤ 0, có phần lẻ, quá trần → 400.
- **BR-T41-02** Tên 1–255, thành phần 1–4000, cảnh báo an toàn 1–2000 (ghi "Không có" nếu không có) — BR-06; nhóm phải tồn tại và active.
- **BR-T41-03** Đổi giá: lý do 1–1000 bắt buộc; ghi `menu_price_history` cùng transaction với `menu_item`; lịch sử chỉ thêm (trigger V3).
- **BR-T41-04** Mọi sửa/đổi trạng thái gửi `version`; lệch → 409, không ghi.
- **BR-T41-05** Trạng thái chỉ ACTIVE/DISCONTINUED; đổi sang chính trạng thái hiện tại → 409. Lý do bắt buộc. Không xóa món (hộp cũ tham chiếu).
- **BR-T41-06** Tài liệu giá: file do owner upload, mô tả 1–1000, ngày áp dụng; thay thế chỉ tài liệu cùng cửa hàng chưa bị thay.
- **BR-T41-07** Audit: `MENU_ITEM_CREATED`, `MENU_ITEM_UPDATED` (before/after), `MENU_ITEM_STATUS_CHANGED`, `PRICE_DOCUMENT_ADDED`.
  Hiện trong lịch sử cửa hàng (T3.3).

## 6. Phạm vi

Trong: seed nhóm thực phẩm, API món, giá, trạng thái, tài liệu, xem (owner/staff/admin), V9, OpenAPI, test. Ngoài: kiểm hộp đang bán khi
sửa an toàn / ngừng do an toàn (UC-51 A1, UC-52 A2 → T7.5, chỉ ghi chú); giá đã lưu trong hộp (T4.4); admin yêu cầu giải trình (UC-75 sau).

## 7. Acceptance criteria

| ID | Given / When / Then |
|---|---|
| AC-01 | Owner tạo món hợp lệ → 201 ACTIVE (hoặc DISCONTINUED nếu `active=false`), history 1 dòng ban đầu, audit. |
| AC-02 | Giá ≤ 0, lẻ đồng, quá trần; thiếu tên/thành phần/cảnh báo; nhóm lạ → 400 field; không tạo gì. |
| AC-03 | Owner đổi giá có lý do → 200, giá mới, history thêm dòng (previous, new, người, lúc, lý do); thiếu lý do → 400; đổi thông tin không giá → không thêm history. |
| AC-04 | Sửa với `version` cũ → 409; hai lần sửa đồng thời cùng version → một 200, một 409; history đúng một dòng mới. |
| AC-05 | Lịch sử giá không sửa/xóa được (UPDATE/DELETE bị DB từ chối); không có API sửa/xóa. |
| AC-06 | Ngừng kinh doanh có lý do → DISCONTINUED; mở lại → ACTIVE; mở lại khi nhóm đã ngừng → 400; cùng trạng thái → 409; thiếu lý do → 400. |
| AC-07 | Staff có `MENU_VIEW` xem danh sách/chi tiết; staff ghi (tạo/sửa giá/trạng thái/tài liệu) → 403; staff không có `MENU_VIEW` → 403; khách → 403; owner cửa hàng khác → 403; không token → 401. |
| AC-08 | ADMIN xem menu, chi tiết món (lịch sử giá, tài liệu) của mọi cửa hàng và tải file tài liệu. |
| AC-09 | Owner đăng ký tài liệu (toàn menu hoặc một món) → 201; thay thế → tài liệu cũ `replacedBy` trỏ tài liệu mới, vẫn liệt kê; file của người khác → 400; thay tài liệu đã bị thay → 409. |
| AC-10 | `GET /api/v1/food-categories` trả danh mục seed; OpenAPI có endpoint mới; `SwaggerUiTests` qua. |

## 8. Truy vết

| Nguồn | Đáp ứng |
|---|---|
| UC-24 (owner, staff có quyền xem tên, nhóm, giá, thành phần, cảnh báo, trạng thái) | AC-07 |
| UC-50 bước 2–5, A1, E1, E2 | AC-01, AC-02, AC-07 |
| UC-51 bước 3–5, E1, E2; hậu điều kiện | AC-03, AC-04 |
| UC-52 bước 3–5, A1, E1 | AC-06 |
| UC-53 bước 3–4, A1, E1 | AC-09 |
| UC-75 bước 3 (giá, lịch sử, tài liệu) | AC-08 |
| BR-04, BR-07 (giá thông thường; giá mới chỉ cho hộp sau) | AC-03 (snapshot ở T4.4) |
| BR-05 (khách không xem menu) | AC-07 |
| BR-06 (đủ dữ liệu; món ngừng không đóng hộp) | AC-02, AC-06 |
| BR-08 (tài liệu tham chiếu) | AC-09 |
| Card: lịch sử giá không sửa/xóa | AC-05 |

## 9. Quyết định (agent tự chốt)

- [x] Q1 Danh mục nhóm (seed V9): BAKERY, COOKED_MEAL, NOODLE_SOUP, RICE_DISH, FRESH_PRODUCE, DAIRY, DESSERT, BEVERAGE, MIXED.
- [x] Q2 "Chưa kinh doanh" (UC-50 A1) = DISCONTINUED khi tạo (`active=false`), không thêm trạng thái mới.
- [x] Q3 Phiên bản giá ban đầu = dòng history previous = new, lý do "Initial price".
- [x] Q4 Tài liệu giá: bảng mới `price_reference_document` (store, menu_item tùy chọn, file, mô tả, ngày áp dụng, replaced_by) +
  `attachment.price_document_id` (để quyền đọc file đi qua `AttachmentAccessPolicy`: owner, staff `MENU_VIEW`, ADMIN).
- [x] Q5 Đọc menu: owner, staff `MENU_VIEW`, ADMIN dùng cùng endpoint; ghi: owner (`MENU_MANAGE` không seed).
- [x] Q6 Đường dẫn: `/api/v1/food-categories`; `/api/v1/stores/{storeId}/menu-items` (GET, POST), `/{itemId}` (GET, PATCH),
  `/{itemId}/status` (POST); `/api/v1/stores/{storeId}/price-documents` (GET, POST).
