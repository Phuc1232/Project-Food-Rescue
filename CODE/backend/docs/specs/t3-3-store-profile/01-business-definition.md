# Business Definition: T3.3 — Thông tin cửa hàng, giờ mở cửa, khóa/mở cửa hàng

**Status:** Gate A đã duyệt 2026-11-15 (chấp nhận nguyên đề xuất Q1–Q13).
**Updated:** 2026-11-15
**Card:** [roadmap-3-9.md#t33](../roadmap-3-9.md) · **Nhánh:** `feature/t3-3-store-profile` (tách từ `origin/master` 6947d48,
đã gồm T3.2 / PR #13)
**Tài liệu nguồn:** UC-40, UC-41, UC-77 (phần trạng thái), BR-55; tham chiếu UC-61 (mở bán, E1/E2), UC-07 bước 2
(khách chỉ thấy cửa hàng được phép bán), BR-02, BR-03, BR-54, BR-56; D04 (danh mục hồ sơ, chốt ở T3.2 Gate A Q4).

## 1. Bối cảnh và mục tiêu

Sau khi được duyệt (T3.2), chủ cửa hàng cần xem và giữ thông tin cửa hàng đúng thực tế (UC-40, UC-41): mô tả, giờ hoạt
động sửa được ngay; thông tin gắn với hồ sơ pháp lý đã duyệt (D04) không tự áp dụng mà thành **yêu cầu xem xét** cho
quản trị viên. Mọi thay đổi có lịch sử. Quản trị viên tạm khóa / kích hoạt lại cửa hàng có lý do (UC-77, BR-54); cửa
hàng bị khóa không mở bán, không nhận đơn mới, không hiện cho khách, các đợt chưa phát sinh đơn bị ngừng (BR-55).
Xử lý **đơn đã xác nhận** khi khóa thuộc T7.5.

## 2. Khảo sát mã hiện tại

Đường dẫn Kotlin tương đối với `src/main/kotlin/com/foodrescue/backend/`.

- `store/entity/Store.kt` ↔ `store`: `name`, `address`, `latitude`/`longitude` (numeric 10,7 + CHECK), `timezone`,
  `status` ∈ {PENDING_APPROVAL, ACTIVE, SUSPENDED, CLOSED}, `version` (`MutableEntity`, dùng được cho optimistic lock).
  **Không có cột mô tả**, **không có cột lý do trạng thái**. Mô tả kinh doanh và SĐT liên hệ hiện chỉ nằm trong snapshot
  JSON `partner_application.business_details` (T3.2 Q6).
- `store/entity/StoreOpeningHour.kt` ↔ `store_opening_hour` (đã `@JdbcType(LocalTimeJdbcType)`), repository có
  `deleteAllByStoreId` + `findAllByStoreIdOrderBy...`; `store/service/OpeningHoursValidator.kt` kiểm tra ngày 1–7, giờ
  `HH:mm`, ca rỗng, ≤ 3 ca/ngày, chồng ca kể cả ca qua nửa đêm/ CN→T2, đánh số ca lại → **tái dùng nguyên**.
- `store/repository/StoreRepository.kt` có `findForUpdate` (PESSIMISTIC_WRITE), `findOpenByOwner`.
- `common/security/StoreAuthorizationService`: ngữ cảnh owner/staff chỉ cho store khác PENDING_APPROVAL/CLOSED; owner
  qua mọi `requirePermission`; store không thuộc ngữ cảnh → 403. SUSPENDED **vẫn có** ngữ cảnh (T3.2 Q1 để T3.3 quyết).
- `audit/AuditService.record(actor, entityType, entityId, action, store, afterState)` — chưa nhận `beforeState` dù cột
  `audit_log.before_state` có sẵn; `audit_log` bất biến (trigger V3).
- `sale_batch.status` ∈ {DRAFT, OPEN, PAUSED, ENDED, BLOCKED} (V1), chưa có entity/code (T4.3). `customer_order` đã có
  snapshot `pickup_address_snapshot`, `pickup_starts_at/ends_at` → thay đổi cửa hàng không thể áp ngược vào đơn nếu
  không ai sửa bảng này (UC-41 A2).
- `attachment` có CHECK `num_nonnulls(...) = 1` trên 8 cột FK cố định; chưa có cột cho yêu cầu thay đổi cửa hàng.
- `account/service/ProfileUpdateRequestParser` (T2.1): mẫu parse JSON thô để báo đúng tên trường lạ/không được sửa.
- Chưa có API khách xem cửa hàng; tìm kiếm cửa hàng/đợt là T5.1, mở bán là T4.3.
- Migration mới nhất trên master: V5 → task này dùng **V6**.
- Test khẳng định số lượng: `SwaggerUiTests` (23 paths, 9 tags), `AuthMigrationTests` và `FoodRescueApplicationTests`
  (5 migration) → phải cập nhật khi thêm endpoint/tag/migration.
- **Baseline** `sh ./gradlew cleanTest build --console=plain` trên 6947d48: BUILD SUCCESSFUL, 97 tests, 96 pass,
  1 skipped (`SwaggerUiContextPathTests`, opt-in), 0 failures, 0 errors.

## 3. Người dùng và quyền

| Vai trò | Được làm |
|---|---|
| Chủ cửa hàng (owner, store ACTIVE hoặc SUSPENDED) | Xem thông tin cửa hàng của mình; sửa mô tả, giờ hoạt động; gửi/hủy yêu cầu thay đổi thông tin cần xét duyệt; xem lịch sử thay đổi (UC-40, UC-41) |
| Nhân viên (T3.4) | Không xem/sửa thông tin quản lý cửa hàng (BR-03: staff chỉ đóng gói, hộp, bàn giao) → 403 |
| ADMIN | Xem danh sách/chi tiết cửa hàng; duyệt/từ chối yêu cầu thay đổi; tạm khóa/kích hoạt lại có lý do (UC-77, BR-54) |
| Người dùng đã đăng nhập (khách) | Xem thông tin công khai của cửa hàng **ACTIVE** (Q9) |

Không token → 401. Owner cửa hàng khác / staff / người lạ gọi API owner → 403. Không phải ADMIN gọi API admin → 403.
Owner **không** có thao tác nào đổi trạng thái cửa hàng (UC-41 E2).

## 4. Luồng chính

**Xem (UC-40):** owner mở cửa hàng → hệ thống kiểm tra quyền → trả tên, mô tả, địa chỉ, tọa độ, múi giờ, giờ hoạt động,
trạng thái (+ lý do nếu bị khóa), yêu cầu thay đổi đang chờ (nếu có), `version`.

**Sửa ngay (UC-41 bước 3–5):** owner gửi `version` + mô tả và/hoặc toàn bộ giờ hoạt động → kiểm tra hợp lệ (E1) → lưu,
thay toàn bộ ca, ghi lịch sử trước/sau → trả thông tin mới.

**Yêu cầu xem xét (UC-41 A1):** owner gửi tên/địa chỉ/tọa độ mới (+ ghi chú, tài liệu D04 tùy chọn) → tạo yêu cầu
`PENDING`, **không đổi** dữ liệu cửa hàng và trạng thái được duyệt → ADMIN duyệt (áp dụng vào `store` trong cùng
transaction) hoặc từ chối có lý do; owner được hủy yêu cầu đang chờ.

**Khóa (UC-77 bước 3–4):** ADMIN chọn tạm khóa store ACTIVE + lý do → store SUSPENDED, lưu lý do/thời điểm, các đợt
**chưa phát sinh đơn** chuyển BLOCKED, ghi lịch sử. Đơn đã xác nhận và đợt có đơn: không chạm, T7.5 xử lý.
**Kích hoạt lại (UC-77 A1):** ADMIN chọn kích hoạt store SUSPENDED + ghi chú khắc phục → ACTIVE; **không** tự mở lại
đợt đã BLOCKED.

## 5. Quy tắc nghiệp vụ

Mã BR-T33-xx là quy tắc riêng của task.

- **BR-T33-01** Trường sửa ngay: `description` (≤ 2000, rỗng = xóa), `openingHours` (thay toàn bộ, theo BR-T32-11).
  Trường cần xét duyệt (D04): `name`, `address`, `latitude`, `longitude` (Q1). `timezone`, `status`, `ownerId`... không
  sửa được qua API owner → 400 nêu tên trường.
- **BR-T33-02** Sửa ngay bắt buộc `version` hiện tại; lệch → 409, không ghi (chống ghi đè UC-41).
- **BR-T33-03** Mỗi cửa hàng tối đa một yêu cầu thay đổi `PENDING` (unique index một phần ở DB). Yêu cầu phải khác dữ liệu
  hiện tại ở ít nhất một trường.
- **BR-T33-04** Quyết định yêu cầu chỉ áp cho `PENDING`, khóa dòng; lần xử lý thứ hai/hủy sau quyết định → 409. Duyệt
  → áp các trường đề xuất vào `store`; từ chối bắt buộc lý do (1–2000). ADMIN không xét yêu cầu của cửa hàng mình (403).
- **BR-T33-05** Thay đổi cửa hàng không sửa đơn/đợt đã có: đơn giữ snapshot nơi/giờ nhận; đợt giữ khung giờ đã đặt (UC-41 A2).
- **BR-T33-06** Chỉ ADMIN đổi trạng thái: ACTIVE → SUSPENDED (tạm khóa), SUSPENDED → ACTIVE (kích hoạt lại); lý do bắt buộc
  1–2000; chuyển sai trạng thái nguồn → 409; khóa dòng store. Không áp cho PENDING_APPROVAL/CLOSED.
- **BR-T33-07** Khi khóa: các `sale_batch` của cửa hàng ở DRAFT/OPEN/PAUSED **không có `customer_order` nào** → BLOCKED
  trong cùng transaction (BR-55). Đợt có đơn giữ nguyên (T7.5). Kích hoạt lại không đổi đợt BLOCKED.
- **BR-T33-08** Cửa hàng chỉ "được phép bán / hiện cho khách" khi `status = ACTIVE`. Quy tắc nằm ở **một chỗ**
  (`StoreSalesPolicy`) để T4.3 (mở bán), T5.1 (tìm kiếm), T5.2 (giữ hộp/đặt) gọi lại; T3.3 dùng nó cho API công khai.
- **BR-T33-09** Lịch sử: mỗi thay đổi ghi `audit_log` (bất biến) với actor, store, thời điểm, `before_state`/`after_state`
  JSON chỉ gồm trường thay đổi, lý do, correlationId (BR-56). Hành động: `STORE_PROFILE_UPDATED`,
  `STORE_CHANGE_REQUESTED`, `STORE_CHANGE_CANCELLED`, `STORE_CHANGE_APPROVED`, `STORE_CHANGE_REJECTED`,
  `STORE_SUSPENDED`, `STORE_REACTIVATED`.
- **BR-T33-10** Owner xem lịch sử cửa hàng mình không thấy id ADMIN (giống T3.2 AC-13), chỉ thấy vai trò "ADMIN"/"OWNER".

## 6. Phạm vi

### Trong phạm vi
- API owner: xem, sửa ngay, gửi/xem/hủy yêu cầu thay đổi, xem lịch sử.
- API admin: danh sách/chi tiết cửa hàng (kèm số đợt theo trạng thái, số đơn theo trạng thái — UC-77 bước 2), duyệt/từ
  chối yêu cầu thay đổi, tạm khóa/kích hoạt lại.
- API công khai xem cửa hàng ACTIVE (Q9); `StoreSalesPolicy`.
- Migration V6 (Q2, Q3, Q6, Q7); mở rộng `AuditService` nhận `beforeState` (tham số mặc định, không đổi lời gọi cũ).
- OpenAPI, test tích hợp (gồm đồng thời).

### Ngoài phạm vi
- Xử lý đơn đã xác nhận / đợt có đơn khi khóa, phương án đơn tồn (UC-77 bước 5, E1, E2) → T7.5.
- Mở bán, kiểm tra khung nhận của đợt nằm trong giờ hoạt động → T4.3; tìm kiếm cửa hàng → T5.1.
- Thông báo các bên (UC-77 bước 6) → T8.2. Lịch sử hoạt động toàn diện cho admin (UC-76) → T8.5 (dùng lại audit).
- Đóng cửa hàng (CLOSED), đổi múi giờ, đổi SĐT liên hệ, tài khoản nhận tiền (UC-42) → không thuộc card.

## 7. Acceptance criteria

| ID | Given / When / Then |
|---|---|
| AC-01 | Given owner của store ACTIVE; When xem thông tin; Then 200 gồm tên, mô tả, địa chỉ, tọa độ, múi giờ, ca theo ngày (HH:mm, `closesNextDay`), trạng thái, `version`, yêu cầu thay đổi đang chờ (null nếu không). Store SUSPENDED: owner vẫn xem được, có lý do và thời điểm khóa. |
| AC-02 | Given owner, `version` đúng; When sửa mô tả và/hoặc giờ hoạt động (gồm ca qua nửa đêm, nhiều ca/ngày); Then 200, dữ liệu mới có hiệu lực ngay, ca cũ bị thay toàn bộ, `version` tăng; audit `STORE_PROFILE_UPDATED` có before/after chỉ gồm trường đổi. Gửi y hệt dữ liệu cũ → 200, không ghi audit. |
| AC-03 | When giờ hoạt động rỗng / chồng ca / ca rỗng / sai ngày / sai định dạng giờ / > 3 ca/ngày, mô tả > 2000, thiếu `version`, hoặc gửi trường không được sửa (`name`, `status`, `timezone`, trường lạ); Then 400 `VALIDATION_FAILED` nêu đúng field; không đổi dữ liệu. |
| AC-04 | Given `version` cũ; When sửa; Then 409, không đổi dữ liệu. Hai lần sửa đồng thời cùng `version` → đúng một 200, một 409. |
| AC-05 | Given owner; When gửi yêu cầu đổi tên/địa chỉ/tọa độ (+ ghi chú, tài liệu D04 tùy chọn); Then 201 yêu cầu `PENDING` lưu giá trị hiện tại và đề xuất; dữ liệu `store` và trạng thái **không đổi**; audit `STORE_CHANGE_REQUESTED`. Không trường nào khác giá trị hiện tại / tọa độ sai / tài liệu không thuộc owner → 400. |
| AC-06 | Given đã có yêu cầu PENDING; When gửi yêu cầu khác → 409. Hai yêu cầu đồng thời → đúng một 201, một 409. Owner hủy yêu cầu PENDING → `CANCELLED`, gửi được yêu cầu mới; hủy yêu cầu đã xử lý → 409. Owner xem danh sách yêu cầu của cửa hàng (mới nhất trước, có trạng thái, lý do từ chối). |
| AC-07 | ADMIN xem danh sách yêu cầu (lọc trạng thái, phân trang) và chi tiết (giá trị hiện tại vs đề xuất, tài liệu tải được). Duyệt → `APPROVED`, `store` nhận giá trị đề xuất trong cùng transaction, audit before/after; từ chối không lý do → 400, có lý do → `REJECTED`, store không đổi. Quyết định yêu cầu không còn PENDING → 409; duyệt ‖ hủy, duyệt ‖ từ chối đồng thời → đúng một thành công. ADMIN xét yêu cầu của store mình → 403. |
| AC-08 | Quyền: không token → 401; owner store khác, staff, người lạ gọi API owner → 403; owner store PENDING_APPROVAL/CLOSED → 403; không phải ADMIN gọi API admin → 403; người khác tải tài liệu của yêu cầu → 404. |
| AC-09 | Given store ACTIVE có đợt DRAFT/OPEN/PAUSED không đơn, một đợt OPEN có đơn, một đợt ENDED; When ADMIN tạm khóa có lý do; Then 200, store SUSPENDED với lý do/thời điểm, đợt không đơn → BLOCKED, đợt có đơn và đợt ENDED giữ nguyên, đơn không đổi; audit `STORE_SUSPENDED` có lý do. Thiếu lý do → 400; store không ACTIVE → 409. |
| AC-10 | Given store SUSPENDED; When ADMIN kích hoạt lại có ghi chú; Then ACTIVE, lý do khóa bị xóa khỏi trạng thái hiện tại (vẫn còn trong lịch sử), đợt BLOCKED **vẫn** BLOCKED; audit `STORE_REACTIVATED`. Store không SUSPENDED → 409. Khóa ‖ kích hoạt đồng thời / hai lần khóa đồng thời → trạng thái cuối nhất quán, mỗi chuyển trạng thái thật đúng một audit, lần còn lại 409. |
| AC-11 | Owner không tự kích hoạt: không có API owner đổi trạng thái; gửi `status` vào API sửa → 400; owner gọi API khóa/kích hoạt → 403 (UC-41 E2). |
| AC-12 | Khách đã đăng nhập xem cửa hàng ACTIVE → 200 thông tin công khai (tên, mô tả, địa chỉ, tọa độ, múi giờ, giờ hoạt động; không có owner, lý do khóa, version). Store SUSPENDED/PENDING_APPROVAL/CLOSED/không tồn tại → 404. `StoreSalesPolicy` chỉ cho ACTIVE được bán (test đơn vị cho mọi trạng thái). |
| AC-13 | Lịch sử: owner xem lịch sử cửa hàng (phân trang, mới nhất trước): hành động, thời điểm, vai trò người làm, trường trước/sau, lý do; không có id ADMIN. ADMIN xem chi tiết cửa hàng có cùng lịch sử kèm id người làm. |
| AC-14 | Given đơn đã có snapshot nơi/giờ nhận; When owner sửa giờ hoạt động và ADMIN duyệt đổi địa chỉ, rồi khóa cửa hàng; Then `pickup_address_snapshot`, `pickup_starts_at/ends_at`, trạng thái đơn không đổi (UC-41 A2, BR-55 "không tự ý xóa"). |
| AC-15 | ADMIN xem danh sách cửa hàng (lọc trạng thái, tìm theo tên, phân trang) và chi tiết: thông tin, chủ (id, email, tên), trạng thái + lý do, yêu cầu đang chờ, số đợt theo trạng thái, số đơn theo trạng thái (UC-77 bước 1–2). Endpoint có trong OpenAPI, thử được trên Swagger; `ApiContractTests`, `SwaggerUiTests` qua. |

## 8. Truy vết tài liệu → AC

| Nguồn | Nội dung | Đáp ứng |
|---|---|---|
| UC-40 bước 1–3 | Chọn cửa hàng đang quản lý; kiểm tra quyền; hiển thị kinh doanh, địa chỉ, vị trí, giờ, trạng thái duyệt/hoạt động | AC-01 |
| UC-40 E1 | Ngoài phạm vi quyền → từ chối | AC-08 |
| UC-40 hậu điều kiện | Xem không sửa trạng thái | AC-01 (GET thuần đọc) |
| UC-41 bước 3–5 | Sửa địa chỉ, vị trí, giờ, mô tả; kiểm tra; lưu phiên bản, ghi lịch sử | AC-02, AC-05, AC-13, BR-T33-09 |
| UC-41 hậu điều kiện | Có lịch sử; không sửa ngầm/vượt quyền xét duyệt | AC-02, AC-04, AC-05 |
| UC-41 A1; D04 | Thay đổi thuộc hồ sơ xét duyệt → yêu cầu xem xét, chưa tự cập nhật | AC-05, AC-06, AC-07 (Q1–Q4) |
| UC-41 A2 | Không áp ngược nơi/giờ nhận đơn đã xác nhận | AC-14, BR-T33-05 |
| UC-41 E1 | Địa chỉ/vị trí/giờ không hợp lệ | AC-03, AC-05 |
| UC-41 E2 | Tự kích hoạt cửa hàng bị khóa → từ chối | AC-11 |
| UC-77 bước 1–2 | Mở cửa hàng, thấy đợt bán, đơn liên quan | AC-15 |
| UC-77 bước 3–4 | Tạm khóa có lý do; chặn mở bán/nhận đơn mới; ngừng đợt chưa có đơn; ghi lịch sử | AC-09, AC-12, BR-T33-07/08 |
| UC-77 A1 | Kích hoạt lại, không tự mở đợt cũ | AC-10 |
| UC-77 bước 3 "phương án đơn", bước 5–6, E1, E2 | Đơn tồn, thông báo | Ngoài phạm vi → T7.5, T8.2 (Q8) |
| UC-77 hậu điều kiện | Không xóa cửa hàng/đơn | AC-09, AC-14 |
| BR-55 | Khóa → đợt chưa có đơn ngừng; đơn đã xác nhận không bị xóa | AC-09, AC-14 (phần đơn: T7.5) |
| BR-54 | ADMIN được tạm khóa cửa hàng | AC-09, AC-10 |
| BR-02; UC-61 E1/E2; UC-07 bước 2 | Chỉ cửa hàng hoạt động mới mở bán / hiện cho khách | AC-12, BR-T33-08 |
| BR-03 | Staff không quản lý thông tin cửa hàng | AC-08 |
| BR-56 | Người, thời điểm, nội dung thay đổi | AC-13, BR-T33-09 |
| Card AC "ca chồng lấn/giờ sai → 400" | | AC-03 |
| Card AC "owner khác → 403" | | AC-08 |
| Card AC "store bị khóa không hiện cho khách và không mở bán" | | AC-09, AC-12 |

## 9. Câu hỏi mở / quyết định cần duyệt

- [x] **Q1 — Trường nào phải xét duyệt lại theo D04.** D04 (T3.2 Q4) gồm giấy ĐKKD (ghi tên + địa chỉ kinh doanh) và giấy
  ATTP (gắn với cơ sở). **Đề xuất:** `name`, `address`, `latitude`, `longitude` → yêu cầu xem xét; `description`,
  `openingHours` → áp dụng ngay (không thuộc hồ sơ D04, UC-41 bước 3 cho owner sửa). `timezone` không cho sửa (MVP
  chỉ VN; đổi múi giờ làm sai giờ của đợt/đơn). Phương án khác: chỉ địa chỉ/tọa độ cần duyệt, tên sửa ngay.
- [x] **Q2 — Lưu "yêu cầu xem xét" ở đâu.** **Đề xuất:** bảng mới `store_change_request` (V6): `store_id`, `requested_by_id`,
  `current_values` + `proposed_values` (JSON chỉ gồm trường xin đổi), `note`, `status` ∈ {PENDING, APPROVED, REJECTED,
  CANCELLED}, `reviewer_id`, `reviewed_at`, `review_reason`, `version`; unique index một PENDING mỗi store.
  Lý do: tách hẳn khỏi luồng đăng ký T3.2 (không sửa `PartnerApplicationReviewService`, không làm lẫn lịch sử revision
  đăng ký). Phương án khác: thêm cột `kind` vào `partner_application` và dùng lại API admin T3.2 (ít bảng hơn nhưng
  phải sửa logic duyệt/`GET /partner-applications/me` của T3.2).
- [x] **Q3 — Tài liệu kèm yêu cầu thay đổi.** Đổi địa chỉ thường cần giấy ĐKKD/ATTP mới. **Đề xuất:** cho kèm 0–10 tài liệu
  loại D04 (`ApplicationDocumentType`), file phải do owner upload; V6 thêm `attachment.store_change_request_id` + cập nhật
  CHECK `num_nonnulls`; cài `AttachmentAccessPolicy` (owner của store + ADMIN). Không bắt buộc tài liệu — ADMIN từ chối
  nếu thiếu căn cứ. Phương án nhẹ hơn: không kèm tài liệu trong T3.3.
- [x] **Q4 — Xử lý yêu cầu thay đổi.** **Đề xuất:** ADMIN APPROVE / REJECT (lý do bắt buộc khi từ chối); không có
  "cần bổ sung" — owner hủy rồi gửi lại. Owner hủy được yêu cầu PENDING. Duyệt áp dụng cả khi store đang SUSPENDED
  (không đổi trạng thái). Store CLOSED → 409.
- [x] **Q5 — Lưu lịch sử thay đổi.** **Đề xuất:** dùng `audit_log` (đã bất biến bằng trigger V3, có `store_id`,
  `before_state`/`after_state`), `entity_type` = `STORE` hoặc `STORE_CHANGE_REQUEST`, JSON chỉ chứa trường thay đổi
  (giờ hoạt động lưu cả danh sách ca trước/sau). Mở rộng `AuditService.record` thêm tham số `beforeState` có mặc định.
  Có API lịch sử cho owner + hiện trong chi tiết admin; UC-76 (T8.5) đọc lại cùng nguồn. Phương án khác: bảng
  `store_profile_version` lưu snapshot đầy đủ mỗi lần (dư thừa với audit).
- [x] **Q6 — Cột mô tả.** `store` chưa có. **Đề xuất:** V6 thêm `store.description text NULL`, backfill từ
  `businessDescription` của revision mới nhất trong `partner_application.business_details`. Hồ sơ T3.2 đã nộp không đổi.
  (Tương lai T3.2 nộp lại hồ sơ cũng nên ghi cột này — T3.3 thêm một dòng gán `description` khi nộp hồ sơ, coi là sửa
  nhỏ có lý do vào code T3.2.)
- [x] **Q7 — Khóa cửa hàng ảnh hưởng gì khi chưa có đợt bán/đơn (phần đơn đã xác nhận để T7.5).** **Đề xuất:**
  1. V6 thêm `store.status_reason text`, `store.status_changed_at timestamptz` (trạng thái hiện tại + lý do; lịch sử ở audit).
  2. Đợt DRAFT/OPEN/PAUSED không có đơn → BLOCKED bằng một câu `UPDATE ... NOT EXISTS (customer_order)` trong cùng
     transaction (bảng đã có từ V1; test chèn dữ liệu bằng SQL). Đợt có đơn: giữ nguyên, T7.5 rà.
  3. Chặn mở bán / nhận đơn mới / ẩn với khách: qua `StoreSalesPolicy` (chỉ ACTIVE); T4.3, T5.1, T5.2 bắt buộc gọi
     (ghi vào card các task đó khi tới lượt). T3.3 kiểm chứng bằng API công khai (Q9) + test đơn vị policy.
  4. Owner/staff **giữ** ngữ cảnh khi SUSPENDED (không sửa `StoreAuthorizationService`): owner xem được lý do, vẫn sửa
     mô tả/giờ và gửi yêu cầu thay đổi để khắc phục; mọi thao tác bán hàng sau này bị chặn bởi policy ở bước 3.
  5. Kích hoạt lại: ACTIVE, xóa `status_reason` hiện tại, không mở lại đợt BLOCKED (UC-77 A1).
- [x] **Q8 — "Phương án xử lý đơn hiện hữu" khi khóa (UC-77 bước 3, E1).** Trước T5.3 không thể có đơn. **Đề xuất:** T3.3 chỉ
  bắt buộc lý do; T7.5 bổ sung phương án và chặn khóa khi có đơn đã xác nhận mà thiếu phương án. Ghi chú vào card T7.5.
- [x] **Q9 — API công khai cho khách.** Card yêu cầu "không hiện cho khách" nhưng chưa có API khách (T5.1). **Đề xuất:** thêm
  `GET /api/v1/stores/{storeId}` (đăng nhập bất kỳ, D10) chỉ trả store ACTIVE, còn lại 404 — vừa kiểm chứng được AC,
  vừa là "thông tin công khai" UC-41 bước 5, T5.1 dùng lại. Phương án khác: chỉ có `StoreSalesPolicy` + test đơn vị.
- [x] **Q10 — Đổi giờ hoạt động khi đã có đợt bán.** **Đề xuất:** T3.3 không kiểm tra (chưa có đợt); đợt/đơn đã tạo giữ
  khung giờ riêng (BR-T33-05). T4.3 kiểm tra khung nhận nằm trong giờ hoạt động **tại lúc tạo/sửa đợt**.
- [x] **Q11 — Quyền thao tác.** **Đề xuất:** API owner kiểm tra bằng `requirePermission(account, store, "STORE_PROFILE_MANAGE")`
  — mã này **không seed** cho staff nên chỉ owner qua (BR-03); T3.4 không được cấp mã này cho staff. ADMIN sở hữu cửa hàng
  không tự xét yêu cầu/khóa/kích hoạt cửa hàng mình (403), giống T3.2 Q8.
- [x] **Q12 — Đường dẫn API.** **Đề xuất:**
  - Owner: `GET`/`PATCH /api/v1/stores/{storeId}/profile`; `POST`/`GET /api/v1/stores/{storeId}/change-requests`;
    `POST /api/v1/stores/{storeId}/change-requests/{requestId}/cancel`; `GET /api/v1/stores/{storeId}/history`.
  - Khách: `GET /api/v1/stores/{storeId}`.
  - Admin: `GET /api/v1/admin/stores`, `GET /api/v1/admin/stores/{storeId}`,
    `POST /api/v1/admin/stores/{storeId}/status` `{action: SUSPEND|REACTIVATE, reason}`;
    `GET /api/v1/admin/store-change-requests`, `GET /api/v1/admin/store-change-requests/{id}`,
    `POST /api/v1/admin/store-change-requests/{id}/decision` `{decision: APPROVE|REJECT, reason}`.
  - `PATCH` parse JSON thô như T2.1 để nêu đúng trường không được sửa; cần `PATCH` đã có trong CORS.
- [x] **Q13 — Thông báo.** **Đề xuất:** không gửi trong T3.3 (owner xem qua API); T8.2 phát cho các sự kiện audit trên.

**Đề xuất sửa card:** không bắt buộc. Nếu duyệt Q7/Q8, thêm ghi chú vào card T4.3/T5.1/T5.2 ("dùng `StoreSalesPolicy`") và
T7.5 ("phương án đơn tồn khi khóa") lúc đóng task.

## 10. Change log

- 2026-11-15: Tạo Gate A.
- 2026-11-15: Người dùng duyệt Gate A ("ok"), chấp nhận đề xuất Q1–Q13.
