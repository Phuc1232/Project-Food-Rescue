# Business Definition: T4.4 — Đóng gói hộp thực tế

**Status:** Gate A tự chốt bởi agent 2026-11-15 (quyền tự quyết trong phiên).
**Card:** [roadmap-3-9.md#t44](../roadmap-3-9.md) · **Nhánh:** `feature/t4-4-box-packing` (từ `feature/t4-3-sale-batch` 3796e4f)
**Tài liệu nguồn:** UC-25, UC-26, UC-32, UC-33, BR-11, BR-12, BR-13, BR-14, BR-17, BR-18, roadmap D7.

## 1. Bối cảnh

Hộp thực tế là đơn vị bán (BR-13): có mã riêng, danh sách món + số lượng + **giá niêm yết tại lúc đóng** (BR-07, BR-11), hạn an toàn,
cách bảo quản và kết quả kiểm tra thực phẩm của người đóng (BR-17, D7). Chỉ hộp đạt giá trị tối thiểu của đợt (BR-12) và an toàn tới hết
giờ nhận (BR-18) mới vào lượng bán (BR-14) — lượng tính bởi `BatchAvailability` (T4.3).

## 2. Khảo sát mã

- `physical_box` (store, current_batch, box_code UNIQUE, packed_by, packed_at, total_listed_value > 0 nguyên đồng, safe_until > packed_at,
  storage_instructions, safety_warnings, status), `box_item` (box, menu_item cùng store, quantity > 0, item_name/unit_price/safety_warnings
  snapshot, expires_at, UNIQUE(box, menu_item)), `box_inspection` (box, inspector, inspected_at, result PASSED/FAILED, safe_until, notes).
  Entity có sẵn, repository rỗng. **Không có cột khóa idempotency.**
- T4.1 menu (giá hiện hành, ACTIVE/DISCONTINUED), T4.3 đợt (minimum_value, pickup_ends_at, trạng thái), `BatchAvailability`.
- Quyền staff `BOX_PACK`, `BOX_MANAGE` (T3.4). Migration mới nhất V10 → **V11**. Baseline 3796e4f: 149 tests, 148 pass, 1 skipped.

## 3. Quyền

Đóng hộp và xem phiếu điền nhanh: owner hoặc staff ACTIVE có `BOX_PACK` (D7: người đóng = người kiểm, được cùng một người).
Xem danh sách/chi tiết hộp: owner, staff `BOX_PACK`/`BOX_MANAGE`, ADMIN (UC-33). Khác → 403.

## 4. Luồng (UC-26)

1. Người đóng xem yêu cầu đợt (UC-25 = chi tiết đợt T4.3: mô tả mẫu, nhóm, giá trị tối thiểu, cảnh báo, khung nhận).
2. Tùy chọn: lấy phiếu điền nhanh từ một hộp đã đóng của **cùng cửa hàng** → chỉ món + số lượng, kèm giá và trạng thái **hiện hành**.
3. Gửi phiếu: `requestKey` (UUID), danh sách `{menuItemId, quantity, expiresAt, expectedUnitPrice?}`, cách bảo quản, kết quả kiểm tra
   (`inspectionPassed`, `inspectionNotes`).
4. Server khóa đợt; cùng `requestKey` đã có → trả hộp cũ (E3). Kiểm tra món thuộc cửa hàng và đang kinh doanh (A1/E1), giá kỳ vọng khác
   giá hiện hành → 409 `PRICE_CHANGED` (E1, không ghi ngầm), tính giá trị = Σ giá hiện hành × số lượng (BR-11), `safe_until` = hạn sớm
   nhất; giá trị < tối thiểu hoặc `safe_until` < hết giờ nhận → 400, không tạo hộp (A1, E2).
5. Kiểm tra đạt → hộp `AVAILABLE` + inspection PASSED; không đạt (A2) → hộp `BLOCKED` + inspection FAILED (không vào lượng bán).
6. Cấp mã hộp duy nhất, lưu snapshot. Lượng khả dụng chỉ tăng khi đợt đang mở (tính động).

## 5. Quy tắc

- **BR-T44-01** 1–30 dòng món, không trùng món; số lượng 1..50; `expiresAt` sau thời điểm đóng; bảo quản 1–1000; ghi chú kiểm tra ≤ 2000
  (bắt buộc khi không đạt).
- **BR-T44-02** Đợt phải DRAFT/OPEN/PAUSED và chưa hết giờ nhận; khác → 409.
- **BR-T44-03** Giá/tên/cảnh báo món chụp lại vào `box_item`; đổi giá menu sau đó không đổi hộp (BR-07).
- **BR-T44-04** `requestKey` duy nhất toàn hệ thống (V11 unique); trùng key cho đợt khác → 409.
- **BR-T44-05** Mã hộp `FR-` + 10 ký tự base32 ngẫu nhiên, duy nhất (unique DB, thử lại khi trùng).
- **BR-T44-06** Cảnh báo hộp = hợp các cảnh báo món (bỏ trùng) — phục vụ kiểm tra dị ứng (BR-19).
- **BR-T44-07** Audit `BOX_PACKED` (mã hộp, giá trị, kết quả kiểm tra) — lịch sử cửa hàng.

## 6. Phạm vi

Trong: API đóng hộp, phiếu điền nhanh, danh sách/chi tiết hộp, V11, OpenAPI, test. Ngoài: đánh dấu hộp không đạt sau khi đóng (T7.5),
kiểm tra lại hộp no-show (T7.4), giữ/phân bổ (T5.2).

## 7. Acceptance criteria

| ID | Given / When / Then |
|---|---|
| AC-01 | Staff BOX_PACK đóng hộp hợp lệ → 201 AVAILABLE, mã duy nhất, giá trị = Σ giá hiện hành × SL, safe_until = hạn sớm nhất, snapshot món, inspection PASSED (inspector = người đóng); đợt OPEN → available tăng. |
| AC-02 | Giá trị < minimum_value → 400 (không tạo); safe_until < pickup_ends_at → 400 (BR-18); món ngừng kinh doanh/món cửa hàng khác/trùng/SL sai → 400 field. |
| AC-03 | `expectedUnitPrice` khác giá hiện hành → 409 `PRICE_CHANGED`, không tạo. |
| AC-04 | Gửi lại cùng `requestKey` → 200 hộp cũ, không thêm hộp; hai request đồng thời cùng key → một hộp. |
| AC-05 | Đổi giá menu sau khi đóng → tổng giá trị và giá snapshot của hộp không đổi. |
| AC-06 | Kiểm tra không đạt (notes bắt buộc) → hộp BLOCKED + inspection FAILED, không vào available. |
| AC-07 | Phiếu điền nhanh từ hộp cùng cửa hàng trả món + SL + giá/trạng thái hiện hành (món ngừng được đánh dấu); hộp cửa hàng khác → 404. |
| AC-08 | Danh sách hộp theo đợt (lọc trạng thái) và chi tiết (món, SL, giá khi đóng, tổng, an toàn, người đóng, kiểm tra, phân bổ) cho owner/staff/ADMIN. |
| AC-09 | Quyền: staff chỉ MENU_VIEW → 403 đóng hộp; khách/owner khác → 403; đợt ENDED/BLOCKED → 409; không token 401. |

## 8. Truy vết

| Nguồn | Đáp ứng |
|---|---|
| UC-25 (yêu cầu đóng gói) | Chi tiết đợt T4.3 + AC-07 |
| UC-26 bước 2–8, A1, A2, E1, E2, E3 | AC-01..07 |
| UC-32 (danh sách hộp theo đợt, trạng thái) | AC-08 |
| UC-33 (chi tiết hộp, admin kiểm tra) | AC-08 |
| BR-11, BR-12 (giá trị = Σ giá × SL; từng hộp độc lập) | AC-01, AC-02 |
| BR-13 (mã riêng) | AC-01 |
| BR-14 (chỉ hộp đạt vào lượng bán) | AC-01, AC-06 |
| BR-17 (kiểm tra thực phẩm trước khi xác nhận), D7 | AC-01, AC-06 |
| BR-18 (an toàn tới hết giờ nhận) | AC-02 |
| BR-07 (giá mới chỉ cho hộp sau) | AC-05 |

## 9. Quyết định (agent tự chốt)

- [x] Q1 Phiếu điền nhanh là API đọc; tạo hộp là một lệnh (không lưu "phiếu nháp").
- [x] Q2 Không đạt giá trị/an toàn → 400, không tạo hộp; kiểm tra thực phẩm không đạt → lưu hộp BLOCKED + FAILED (truy vết).
- [x] Q3 Idempotency bằng `requestKey` trong body, lưu `physical_box.pack_request_key` (V11, unique).
- [x] Q4 Khóa dòng đợt khi đóng hộp (tuần tự theo đợt, chống trùng key).
- [x] Q5 Đường dẫn: `/api/v1/stores/{storeId}/sale-batches/{batchId}/boxes` (GET ?status, POST), `/api/v1/stores/{storeId}/boxes/{boxId}` (GET),
  `/api/v1/stores/{storeId}/boxes/{boxId}/prefill` (GET).
