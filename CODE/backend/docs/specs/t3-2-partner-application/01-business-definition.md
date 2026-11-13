# Business Definition: T3.2 — Đăng ký đối tác và xét duyệt

**Status:** Gate A đã duyệt 2026-11-14 (chấp nhận nguyên đề xuất Q1–Q10).
**Updated:** 2026-11-14
**Card:** [roadmap-3-9.md#t32](../roadmap-3-9.md) · **Nhánh:** `feature/t3-2-partner-application` (tách từ `master` 10ab695)
**Tài liệu nguồn:** UC-38, UC-39, UC-66, UC-67, BR-02, SD-01 (sequence 01), activity 01, class diagram
`HoSoDoiTac`/`CuaHang`, roadmap D8 (một chủ một cửa hàng), D04 (danh mục hồ sơ, chưa có văn bản).

## 1. Bối cảnh và mục tiêu

Người dùng đã đăng nhập nộp hồ sơ để trở thành chủ cửa hàng; quản trị viên xem hồ sơ chờ và ra một trong ba quyết định
(yêu cầu bổ sung, từ chối, chấp thuận). Chỉ khi được chấp thuận, cửa hàng mới ở trạng thái hoạt động và người nộp mới có
quyền chủ cửa hàng. Được duyệt **chưa** đồng nghĩa mở bán: mở bán là UC-61 (giai đoạn 4).

## 2. Khảo sát mã hiện tại

Đường dẫn Kotlin tương đối với `src/main/kotlin/com/foodrescue/backend/`.

- `store/entity/Store.kt` ↔ `store`: `owner_id` **NOT NULL**, `name`, `address`, `latitude`/`longitude` (numeric 10,7, có
  CHECK miền), `timezone`, `status` ∈ {PENDING_APPROVAL, ACTIVE, SUSPENDED, CLOSED}. Chỉ có index trên `owner_id`,
  **chưa có ràng buộc một chủ một cửa hàng** (D8).
- `store/entity/PartnerApplication.kt` ↔ `partner_application`: `store_id`, `applicant_id`, `revision` (> 0,
  UNIQUE(store_id, revision)), `business_details` (text), `status` ∈ {SUBMITTED, APPROVED, REJECTED} (enum + CHECK),
  `reviewer_id`, `reviewed_at`, `review_reason`, `submitted_at`. Không có trigger chặn sửa. **Thiếu trạng thái "cần bổ sung"**
  → cần migration (card đã nêu).
- `store/entity/StoreOpeningHour.kt` ↔ `store_opening_hour`: `day_of_week` 1–7, `shift_number` > 0, `opens_at`,
  `closes_at`, `closes_next_day` (CHECK `closes_next_day OR closes_at > opens_at`), UNIQUE(store_id, day, shift).
- `file/entity/Attachment.kt`: có FK `partner_application_id`; `file/service/AttachmentAccessPolicy` (T3.1) là điểm mở rộng
  quyền đọc file đã gắn. Bảng `attachment` **không có cột loại tài liệu**.
- Repository `store/*` rỗng; chưa có controller/service.
- **Rủi ro bảo mật phát hiện khi khảo sát:** `common/security/StoreAuthorizationService.contexts` coi tài khoản là owner chỉ
  dựa vào `store.owner_id`, **không xét trạng thái cửa hàng**. Vì `owner_id` bắt buộc khi tạo `store`, người vừa nộp hồ sơ
  (cửa hàng PENDING_APPROVAL) sẽ lập tức có mọi quyền owner trong `requirePermission` và xuất hiện trong `stores` của
  `GET /me`. Trái UC-38 ("chưa có quyền mở bán"), BR-02 và card ("chấp thuận → người nộp thành owner") → Q1.
- `audit/AuthAuditService` cố định `entity_type = "AUTH"`, không có `store_id` → cần writer audit chung (Q7).
- `common/validation/PageQuery` + `PageResponse` có sẵn; sort whitelist `createdAt`, `expiresAt`.
- Thông báo (outbox, email) là T8.2; T3.2 không gửi thông báo, người nộp xem trạng thái qua API (UC-39).
- Migration mới nhất trên `master`: V4 → task này dùng **V5**.
- Baseline build: xem mục 9.

## 3. Người dùng và quyền

| Vai trò | Được làm |
|---|---|
| Người dùng đã đăng nhập, `ACTIVE` | Nộp hồ sơ cho chính mình; xem hồ sơ và lịch sử xét duyệt của chính mình (UC-38, UC-39) |
| ADMIN | Xem danh sách/chi tiết hồ sơ (UC-66), ra quyết định (UC-67), tải tài liệu đính kèm |
| Người khác | Không xem được hồ sơ/tài liệu của người khác (404) |

Không phải ADMIN gọi API quản trị → 403. ADMIN không được tự xét hồ sơ của chính mình (Q8).

## 4. Luồng chính

**Nộp hồ sơ (UC-38, SD-01 #01–11)**
1. Người dùng gửi: tên cửa hàng, địa chỉ, tọa độ, múi giờ, giờ hoạt động, mô tả kinh doanh, số điện thoại liên hệ,
   danh sách tài liệu `{loại, fileId}` (file đã upload qua T3.1).
2. Hệ thống khóa tài khoản người nộp (tuần tự hóa), rồi kiểm tra:
   - đã có cửa hàng ACTIVE/SUSPENDED → từ chối (E2, D8);
   - đã có hồ sơ đang chờ (SUBMITTED) → **trả lại hồ sơ đó**, không tạo mới, không ghi đè (A1);
   - dữ liệu, giờ hoạt động, tài liệu bắt buộc theo D04 hợp lệ; file thuộc chính người nộp (E1).
3. Lần đầu: tạo `store` PENDING_APPROVAL + giờ hoạt động + `partner_application` revision 1 SUBMITTED + `attachment`.
   Nộp lại sau "cần bổ sung"/"từ chối": cập nhật thông tin hiện hành của `store`, thay giờ hoạt động, tạo revision
   n+1 SUBMITTED; các revision cũ giữ nguyên quyết định (lịch sử, A2).
4. Trả mã hồ sơ, revision, trạng thái. Audit.

**Xét duyệt (UC-66, UC-67, SD-01 #12–33)**
1. ADMIN xem danh sách hồ sơ (lọc theo trạng thái, phân trang) và chi tiết: thông tin kinh doanh, địa chỉ, vị trí, giờ,
   người nộp, tài liệu, lịch sử các revision.
2. ADMIN gửi quyết định cho đúng revision đang chờ:
   - **Yêu cầu bổ sung** (bắt buộc nội dung cần làm rõ) → revision `NEEDS_INFO` (tên đề xuất, Q2); store giữ PENDING_APPROVAL.
   - **Từ chối** (bắt buộc lý do) → revision `REJECTED`; store giữ PENDING_APPROVAL; được nộp lại (Q3).
   - **Chấp thuận** → kiểm tra lại trong cùng transaction có khóa: revision vẫn SUBMITTED **và** người nộp chưa có cửa hàng
     được duyệt → revision `APPROVED`, store `ACTIVE`, người nộp có quyền owner (qua Q1).
3. Revision không còn SUBMITTED (đã có người xử lý) → 409, không ghi đè; client tải lại để thấy kết quả đã có (E1, SD-01 #32).
4. Mỗi quyết định ghi người duyệt, thời điểm, lý do; audit.

## 5. Quy tắc nghiệp vụ

Mã BR-T32-xx là quy tắc riêng của task.

- **BR-T32-01** Một tài khoản sở hữu tối đa một cửa hàng chưa đóng (D8), bảo đảm ở cả service và DB (unique index
  `store(owner_id) WHERE status <> 'CLOSED'`).
- **BR-T32-02** Mỗi cửa hàng có tối đa một revision đang chờ (SUBMITTED), bảo đảm ở DB (unique index một phần).
- **BR-T32-03** Nộp khi đã có revision SUBMITTED → trả revision đó (200), không tạo bản mới, không sửa nội dung.
- **BR-T32-04** Quyết định chỉ áp dụng cho revision SUBMITTED; khóa dòng revision và store; lần xử lý thứ hai → 409.
- **BR-T32-05** Từ chối và yêu cầu bổ sung bắt buộc nội dung (1–2000 ký tự); chấp thuận cho phép ghi chú tùy chọn.
- **BR-T32-06** Chấp thuận → store ACTIVE. Quyền owner (và mọi ngữ cảnh cửa hàng) chỉ có hiệu lực khi store đã qua duyệt
  (Q1). Store ACTIVE chưa tự mở bán (BR-02, UC-61).
- **BR-T32-07** Lịch sử: mỗi lần nộp là một revision bất biến về nội dung (snapshot thông tin cửa hàng, giờ, tài liệu trong
  `business_details`); chỉ phần quyết định được ghi một lần.
- **BR-T32-08** Tài liệu: loại theo danh mục D04 (Q4); fileId phải tồn tại và do chính người nộp upload; tài liệu bắt buộc
  phải có. Tài liệu gắn vào revision qua `attachment`.
- **BR-T32-09** Quyền đọc file gắn với hồ sơ: người nộp và ADMIN (cài `AttachmentAccessPolicy` cho `partner_application`).
- **BR-T32-10** Dữ liệu cửa hàng: tên 1–255; địa chỉ 1–1000; vĩ độ −90..90, kinh độ −180..180 (≤ 7 chữ số thập phân);
  múi giờ là IANA hợp lệ (mặc định `Asia/Ho_Chi_Minh`); điện thoại liên hệ dạng số VN; mô tả ≤ 2000.
- **BR-T32-11** Giờ hoạt động: ≥ 1 ca; ngày 1–7; mỗi ngày ≤ 3 ca; ca qua nửa đêm đánh dấu `closesNextDay`; ca không được
  rỗng hay chồng nhau trong cùng ngày (kể cả phần tràn sang ngày sau của ca qua đêm).
- **BR-T32-12** Audit: `PARTNER_APPLICATION_SUBMITTED`, `..._INFO_REQUESTED`, `..._REJECTED`, `..._APPROVED`
  (actor, store_id, revision, lý do; không chứa dữ liệu cá nhân ngoài id).

## 6. Phạm vi

### Trong phạm vi
- API người nộp: nộp/nộp lại hồ sơ, xem hồ sơ của tôi + lịch sử.
- API admin: danh sách (lọc trạng thái, phân trang), chi tiết + lịch sử + tài liệu, ra quyết định.
- Migration V5: trạng thái mới, unique index (BR-T32-01, 02).
- Sửa `StoreAuthorizationService` theo Q1; `AttachmentAccessPolicy` cho hồ sơ; writer audit chung.
- OpenAPI, test tích hợp (gồm đồng thời).

### Ngoài phạm vi
- Sửa thông tin cửa hàng sau khi duyệt, khóa/mở cửa hàng (T3.3); nhân viên (T3.4); mở bán (UC-61, giai đoạn 4).
- Gửi thông báo/email cho người nộp (T8.2); rút hồ sơ; nhiều cửa hàng một chủ (D9).

## 7. Acceptance criteria

| ID | Given / When / Then |
|---|---|
| AC-01 | Given người dùng chưa có cửa hàng, có file tài liệu bắt buộc đã upload; When nộp hồ sơ hợp lệ; Then 201, store PENDING_APPROVAL (đúng tên, địa chỉ, tọa độ, múi giờ, giờ hoạt động), revision 1 SUBMITTED, attachment gắn đúng file và loại; audit SUBMITTED. |
| AC-02 | Given đã có revision SUBMITTED; When nộp lại (kể cả nội dung khác); Then 200 trả đúng revision đó, không tạo store/revision mới, nội dung không đổi. Hai lần nộp đồng thời lần đầu → đúng một store, một revision. |
| AC-03 | When thiếu trường bắt buộc, tọa độ/múi giờ/điện thoại sai, giờ hoạt động rỗng/chồng ca/sai ngày, thiếu tài liệu bắt buộc, loại tài liệu lạ, fileId không tồn tại hoặc của người khác; Then 400 `VALIDATION_FAILED` chỉ rõ field; không tạo dữ liệu. |
| AC-04 | Given người nộp có cửa hàng ACTIVE; When nộp hồ sơ; Then 409, không tạo gì (E2). |
| AC-05 | Given hồ sơ vừa nộp (store PENDING_APPROVAL); Then người nộp **không** có ngữ cảnh owner: `GET /me` không liệt kê store đó, `requirePermission` từ chối (403). |
| AC-06 | Không phải ADMIN gọi API admin → 403; không token → 401. Người khác xem hồ sơ/tải tài liệu của người nộp → 404. |
| AC-07 | ADMIN xem danh sách lọc SUBMITTED (phân trang, mới nhất trước) và chi tiết: thông tin cửa hàng, người nộp (id, email, tên), tài liệu (id, loại, tên file), lịch sử revision; tải được tài liệu. |
| AC-08 | ADMIN yêu cầu bổ sung (có nội dung) → revision NEEDS_INFO; store PENDING_APPROVAL; người nộp thấy nội dung cần bổ sung; nộp lại → revision 2 SUBMITTED, store cập nhật thông tin mới, revision 1 giữ nguyên; duyệt revision 2 → APPROVED. |
| AC-09 | ADMIN từ chối không lý do → 400; có lý do → REJECTED; người nộp thấy lý do; nộp lại → revision mới SUBMITTED. |
| AC-10 | ADMIN chấp thuận → revision APPROVED (reviewer, thời điểm), store ACTIVE; người nộp có ngữ cảnh owner (`GET /me` có store, `owner: true`). Không có thay đổi nào khác (không tạo đợt bán). |
| AC-11 | Hai ADMIN quyết định đồng thời trên cùng revision (mọi tổ hợp) → đúng một thành công, còn lại 409; trạng thái cuối khớp quyết định thành công; chỉ một audit quyết định. Quyết định trên revision đã xử lý → 409. |
| AC-12 | Chấp thuận khi người nộp đã có cửa hàng được duyệt (dữ liệu bất thường) → 409, không đổi gì (E2). ADMIN xét hồ sơ của chính mình → 403. |
| AC-13 | Người nộp xem hồ sơ của tôi: store, revision hiện tại, trạng thái, lý do/nội dung bổ sung, lịch sử (revision, trạng thái, thời điểm nộp/xét); không lộ id người duyệt. Chưa nộp → 404. |
| AC-14 | Audit đủ 4 loại sự kiện với actor, store_id, correlationId. Endpoint có trong OpenAPI và thử được trên Swagger; `ApiContractTests`, `SwaggerUiTests` qua. |

## 8. Truy vết tài liệu → AC

| Nguồn | Nội dung | Đáp ứng |
|---|---|---|
| UC-38 tiền điều kiện, E2; D8 | Chưa có cửa hàng khác; đã sở hữu → từ chối | BR-T32-01, AC-04, AC-12 |
| UC-38 bước 2–5 | Thông tin cửa hàng, địa chỉ, vị trí, giờ, hồ sơ theo D04; kiểm tra; lưu hồ sơ chờ | AC-01, AC-03 |
| UC-38 bước 6, hậu điều kiện | Hiển thị mã hồ sơ/trạng thái; chưa có quyền mở bán | AC-01, AC-05 |
| UC-38 A1 | Đã có hồ sơ chờ → mở hồ sơ đó, không tạo trùng | BR-T32-02/03, AC-02 |
| UC-38 A2 | Bị từ chối, được nộp lại; giữ lịch sử | BR-T32-07, AC-09 |
| UC-38 E1 | Thiếu thông tin/hồ sơ không hợp lệ → sửa | AC-03 |
| UC-39 | Người nộp xem trạng thái, lý do, lịch sử; ngoài phạm vi → từ chối | AC-13, AC-06 |
| UC-66 | Admin xem hồ sơ chờ theo thời gian/trạng thái; người đăng ký, cửa hàng, ngày gửi, tài liệu | AC-07 |
| UC-67 bước 1–3 | Mở hồ sơ, tài liệu, lịch sử | AC-07 |
| UC-67 bước 4–6 | Phê duyệt có căn cứ; kiểm tra còn chờ + giới hạn sở hữu; cấp quyền chủ; mở bán qua UC-61 | AC-10, AC-12, BR-T32-06 |
| UC-67 A1 | Thiếu tài liệu → yêu cầu bổ sung, giữ chưa duyệt | AC-08 |
| UC-67 A2 | Từ chối bắt buộc lý do | AC-09 |
| UC-67 E1; SD-01 #28–33 | Hồ sơ vừa được xử lý → hiện kết quả đã có, không ghi đè | BR-T32-04, AC-11 |
| UC-67 E2 | Vi phạm giới hạn sở hữu/hồ sơ chưa đủ → không duyệt | AC-12 |
| SD-01 #03–07 | Kiểm tra người đăng ký, cửa hàng đã duyệt, giới hạn sở hữu trước khi lưu | AC-03, AC-04 |
| SD-01 #20–27 | Lưu yêu cầu bổ sung/từ chối và lịch sử xử lý | AC-08, AC-09 |
| BR-02 | Chỉ mở bán sau khi được duyệt và cửa hàng hoạt động | AC-05, AC-10 |
| Activity 01 | Luồng nộp → kiểm tra → admin thẩm định → 3 nhánh | AC-01, AC-08..10 (lệch nhánh từ chối: Q3) |
| Card "chặn một chủ nhiều cửa hàng ở DB" | | BR-T32-01 |

## 9. Baseline

`sh ./gradlew cleanTest build --console=plain` trên `feature/t3-2-partner-application` (= `master` 10ab695):
BUILD SUCCESSFUL. 83 tests, 82 pass, 1 skipped, 0 failures, 0 errors (trùng kết quả cuối T3.1). Baseline xanh.

## 10. Câu hỏi mở / quyết định cần duyệt

- [x] **Q1 — Sửa `StoreAuthorizationService` (common/security, code của task khác).** Hiện owner = `store.owner_id` bất kể
  trạng thái → người nộp có quyền owner ngay khi hồ sơ còn chờ. **Đề xuất:** ngữ cảnh cửa hàng (owner lẫn staff) chỉ tính
  cho store **khác PENDING_APPROVAL và khác CLOSED**. SUSPENDED vẫn giữ ngữ cảnh (để T3.3/T7.5 quyết định thao tác nào bị
  chặn khi khóa). Thêm test. `StoreAuthorizationTests` hiện có phải vẫn qua.
- [x] **Q2 — Tên trạng thái "cần bổ sung".** **Đề xuất:** `NEEDS_INFO` (enum `ApplicationStatus` + CHECK trong V5).
- [x] **Q3 — Nộp lại sau khi bị từ chối.** Activity 01 cho nhánh "Từ chối" kết thúc; UC-38 A2 và card cho nộp lại
  ("được phép nộp lại"). **Đề xuất:** luôn cho nộp lại (theo card, có test "từ chối → nộp lại"); không thêm cờ "cấm nộp lại".
  Phương án khác: admin chọn "từ chối vĩnh viễn" khi từ chối.
- [x] **Q4 — Danh mục tài liệu D04 (chưa có văn bản).** **Đề xuất:**
  - bắt buộc: `BUSINESS_REGISTRATION` (giấy chứng nhận đăng ký kinh doanh/hộ kinh doanh),
    `FOOD_SAFETY` (giấy chứng nhận cơ sở đủ điều kiện ATTP hoặc bản cam kết ATTP);
  - tùy chọn: `STOREFRONT_PHOTO` (ảnh mặt tiền), `OTHER`.
  Không thu CCCD/giấy tờ tùy thân (giảm dữ liệu cá nhân nhạy cảm, NFR 3.1). Tối đa 10 tài liệu mỗi lần nộp.
- [x] **Q5 — Lưu loại tài liệu ở đâu.** `attachment` không có cột loại. **Đề xuất:** không đổi bảng `attachment` dùng chung;
  lưu loại trong snapshot JSON `business_details` của revision (`documents: [{type, fileId}]`) và tạo `attachment` để liên
  kết file ↔ revision (phục vụ quyền đọc). Phương án khác: V5 thêm cột `document_type` vào `attachment`.
- [x] **Q6 — Thông tin thêm ngoài cột `store`.** **Đề xuất:** `contactPhone` (bắt buộc, để admin liên hệ) và
  `businessDescription` (tùy chọn) chỉ lưu trong snapshot `business_details`, không thêm cột vào `store`.
- [x] **Q7 — Audit.** **Đề xuất:** thêm `audit/AuditService` chung (actor, store, entityType, entityId, action, after-state
  ngắn gọn) cho các task từ T3.2 trở đi; giữ nguyên `AuthAuditService`.
- [x] **Q8 — ADMIN tự nộp/tự duyệt.** **Đề xuất:** ADMIN vẫn được nộp hồ sơ như người dùng thường, nhưng không được ra quyết
  định trên hồ sơ của chính mình (403) — tách người nộp và người duyệt.
- [x] **Q9 — Đường dẫn API.** **Đề xuất:** người nộp `POST /api/v1/partner-applications`, `GET /api/v1/partner-applications/me`;
  admin `GET /api/v1/admin/partner-applications?status=&page=&size=`, `GET /api/v1/admin/partner-applications/{id}`,
  `POST /api/v1/admin/partner-applications/{id}/decision` với `{decision: APPROVE|REJECT|REQUEST_INFO, reason}`.
  `{id}` là id của revision.
- [x] **Q10 — Thông báo cho người nộp.** **Đề xuất:** không gửi trong T3.2; người nộp xem qua `GET .../me`. T8.2 (outbox
  thông báo) sẽ phát cho các sự kiện này.

## 11. Change log

- 2026-11-14: Tạo Gate A.
- 2026-11-14: Người dùng duyệt Gate A, chấp nhận đề xuất Q1–Q10.
