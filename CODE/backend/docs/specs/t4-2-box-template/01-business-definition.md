# Business Definition: T4.2 — Mẫu Surprise Box

**Status:** Gate A tự chốt bởi agent 2026-11-15 (quyền tự quyết trong phiên).
**Card:** [roadmap-3-9.md#t42](../roadmap-3-9.md) · **Nhánh:** `feature/t4-2-box-template` (từ `feature/t4-1-menu` 523d093)
**Tài liệu nguồn:** UC-57, UC-58, BR-09 (mẫu ≠ đợt bán ≠ hộp thực tế), BR-10 (owner quyết giá), BR-19 (cảnh báo an toàn).

## 1. Bối cảnh

Mẫu là thông tin chung về loại hộp (tên, nhóm thực phẩm, mô tả dự kiến, cảnh báo an toàn, ảnh bìa) cùng giá bán và giá trị tối thiểu
**mặc định** gợi ý khi tạo đợt (T4.3). Mẫu không phải tồn kho, không công khai món thực tế (BR-05, BR-09). Đợt bán chụp lại cảnh báo/giá
nên sửa mẫu không đổi đợt/đơn đã tạo (UC-58 bước 5).

## 2. Khảo sát mã

- `box_template` (store, category, name, description, safety_warnings, default_selling_price > 0, default_minimum_value > 0, nguyên đồng,
  cover_file_id → stored_file, active, version) và entity `surprisebox/entity/BoxTemplate` có sẵn; repository rỗng.
- `sale_batch` tham chiếu `(template_id, store_id)` và có `safety_warnings`, `selling_price`, `minimum_value` riêng (snapshot).
- File (T3.1): file chưa gắn attachment chỉ uploader đọc; `cover_file_id` không phải attachment.
- T4.1: `food_category` seed; `PriceValidator` (nguyên đồng 1..100tr). Không cần migration. Baseline 523d093: 136 tests, 135 pass, 1 skipped.

## 3. Quyền

Ghi: owner (mã `BOX_TEMPLATE_MANAGE` không seed). Đọc: owner, staff có `BOX_PACK` hoặc `BOX_MANAGE`, ADMIN. Khác → 403.

## 4. Luồng

- Tạo (UC-57): tên, nhóm, mô tả dự kiến, cảnh báo, giá bán mặc định, giá trị tối thiểu mặc định, ảnh bìa tùy chọn → mẫu `active`.
  Không tạo đợt/hộp (A1).
- Sửa (UC-58): `version` + trường đổi; lưu phiên bản (version tăng, audit before/after); không xóa cảnh báo (E1); đợt/đơn giữ snapshot.
- Ngừng/dùng lại: `active` false/true có lý do; mẫu ngừng không dùng tạo đợt mới (T4.3). Không có xóa cứng.

## 5. Quy tắc

- **BR-T42-01** Tên 1–255; mô tả 1–2000 (không liệt kê món — trách nhiệm owner, hệ thống không công khai món thực tế); cảnh báo 1–2000.
- **BR-T42-02** Giá bán, giá trị tối thiểu mặc định: nguyên đồng 1..100.000.000; giá trị tối thiểu ≥ giá bán (khách nhận giá trị không
  thấp hơn tiền trả).
- **BR-T42-03** Ảnh bìa: file do owner upload, kiểu `image/*`.
- **BR-T42-04** Sửa/ngừng gửi `version`; lệch → 409. Ngừng khi đã ngừng (hoặc ngược lại) → 409.
- **BR-T42-05** Audit `BOX_TEMPLATE_CREATED`, `BOX_TEMPLATE_UPDATED` (before/after), `BOX_TEMPLATE_STATUS_CHANGED` (lý do); lịch sử cửa hàng.

## 6. Phạm vi

Trong: API mẫu (list/tạo/xem/sửa/ngừng), OpenAPI, test. Ngoài: hiển thị ảnh bìa cho khách (T5.1 cấp quyền đọc ảnh bìa mẫu đang dùng bởi
đợt OPEN), kiểm hộp liên quan khi cảnh báo phản ánh rủi ro thật (UC-58 A1 → T7.5), sửa điều kiện đợt (UC-60 → T4.3).

## 7. Acceptance criteria

| ID | Given / When / Then |
|---|---|
| AC-01 | Owner tạo mẫu hợp lệ (có/không ảnh bìa) → 201 active; không có `sale_batch`/`physical_box` mới; audit. |
| AC-02 | Thiếu tên/mô tả/cảnh báo, nhóm lạ, giá ≤ 0/lẻ đồng, giá trị tối thiểu < giá bán, ảnh bìa không phải ảnh/của người khác → 400 field. |
| AC-03 | Owner sửa với version đúng → 200, version tăng, audit before/after; xóa cảnh báo (rỗng) → 400; version cũ → 409; sửa đồng thời → một 200, một 409. |
| AC-04 | Sửa mẫu không đổi đợt bán đã tạo từ mẫu (cảnh báo, giá bán, giá trị tối thiểu của đợt giữ nguyên). |
| AC-05 | Ngừng có lý do → active=false; dùng lại → true; trùng trạng thái → 409; không có API xóa (DELETE → 405/404). |
| AC-06 | Staff `BOX_PACK` xem được; staff ghi → 403; staff chỉ `MENU_VIEW`, khách, owner khác → 403; ADMIN xem; không token → 401. |
| AC-07 | OpenAPI có endpoint; `SwaggerUiTests` qua. |

## 8. Truy vết

| Nguồn | Đáp ứng |
|---|---|
| UC-57 bước 2–5, A1, E1; hậu điều kiện (không tạo tồn kho) | AC-01, AC-02 |
| UC-58 bước 2–5, E1; hậu điều kiện (không đổi ngầm đơn cũ, không mất cảnh báo) | AC-03, AC-04 |
| BR-09 (mẫu ≠ đợt ≠ hộp) | AC-01, AC-04 |
| BR-10 (owner quyết giá) | AC-06 |
| BR-19 (cảnh báo không bị che) | AC-02, AC-03 |
| Card: không xóa cứng mẫu đã dùng | AC-05 |

## 9. Quyết định (agent tự chốt)

- [x] Q1 Không xóa mẫu (kể cả chưa dùng) — chỉ ngừng; đơn giản và an toàn cho tham chiếu đợt.
- [x] Q2 Giá trị tối thiểu mặc định ≥ giá bán mặc định.
- [x] Q3 Đọc mẫu: owner, staff BOX_PACK/BOX_MANAGE, ADMIN.
- [x] Q4 Đường dẫn: `/api/v1/stores/{storeId}/box-templates` (GET ?active, POST), `/{templateId}` (GET, PATCH), `/{templateId}/status` (POST).
