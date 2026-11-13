# Business Definition: T3.1 — Lưu trữ file

**Status:** Gate A đã duyệt 2026-11-14 (chấp nhận nguyên đề xuất Q1–Q7).
**Updated:** 2026-11-14
**Card:** [roadmap-3-9.md#t31](../roadmap-3-9.md) · **Nhánh:** `feature/t3-1-file-storage` (tách từ `master` e112f7e)
**Tài liệu nguồn:** roadmap D4; FR 3.2 (chụp ảnh hộp); BR-08; UC-23 bước 3–4/E1; UC-30 bước 3; UC-38 bước 2–3;
UC-53 bước 3–4/E1/A1; UC-67 bước 3; NFR 3.1 (bảo vệ thông tin cá nhân).

## 1. Bối cảnh và mục tiêu

Nhiều nghiệp vụ sau cần file: hồ sơ đối tác (T3.2), tài liệu giá (T4.1), ảnh bìa mẫu hộp (T4.2), ảnh/bằng chứng hộp
(T4.4, T7.x), bằng chứng khiếu nại (T8.1), hoàn tiền và chi trả (T6.5, T8.4). T3.1 làm **nền dùng chung**: nhận file an
toàn, lưu ngoài DB qua một port, ghi metadata, và tải về có kiểm soát quyền. T3.1 **không** gắn file vào đối tượng
nghiệp vụ nào. Việc gắn (tạo `attachment`) thuộc các task sở hữu đối tượng đó.

## 2. Khảo sát mã hiện tại

Đường dẫn Kotlin tương đối với `src/main/kotlin/com/foodrescue/backend/`.

- `file/entity/StoredFile.kt` ↔ bảng `stored_file`: `storage_key` (unique), `original_name`, `mime_type`, `size_bytes`
  (≥ 0), `checksum`, `uploaded_by_id` → `account`. Kế thừa `MutableEntity` (`@Version`). Đủ cột cho card,
  **không cần migration**.
- `file/entity/Attachment.kt` ↔ `attachment`: `file_id` + 8 FK tùy chọn (`partner_application`, `menu_item`, `box`,
  `complaint`, `no_show_case`, `payment`, `refund`, `payout`), CHECK **đúng một** FK khác null.
- `box_template.cover_file_id` tham chiếu `stored_file` **trực tiếp**, không qua `attachment` (V2).
- `file/repository/*`: chỉ có `JpaRepository` rỗng. Chưa có controller, service hay storage.
- **Chặn quan trọng:** `middleware/ratelimit/RateLimitFilter.kt` đọc toàn bộ body mọi request `/api/v1/**` vào bộ nhớ và
  trả **413 nếu > 16 384 byte**. Upload ảnh hoặc PDF thật sẽ luôn bị chặn → buộc phải sửa middleware (Q1).
- Chưa cấu hình `spring.servlet.multipart.*`; `.env.example` chưa có biến cho file.
- `RateLimitService` + `RateLimitPolicy` (enum trong middleware) dùng cho quota theo IP/định danh.
- `common/security/CurrentAccount`, `PlatformRole.ADMIN`, `StoreAuthorizationService` có sẵn.
- Baseline build: xem mục 9.

## 3. Người dùng và quyền

- **Upload:** mọi tài khoản đã đăng nhập, `ACTIVE` (khách gửi bằng chứng, chủ cửa hàng, nhân viên, admin).
- **Xem/tải file chưa gắn đối tượng:** chỉ người upload.
- **Xem/tải file đã gắn đối tượng:** người có quyền với đối tượng đó. T3.1 cung cấp điểm mở rộng để mỗi task sau khai báo
  luật cho loại đối tượng của mình. Trong T3.1, chưa có task nào gắn file, nên luật mặc định cho file đã gắn là: người
  upload hoặc ADMIN (Q4).
- Không có truy cập công khai (không đăng nhập) tới file (roadmap D10: mọi chức năng xem yêu cầu đăng nhập).

## 4. Luồng chính

**Upload**
1. Người dùng gửi `POST /api/v1/files` (multipart, một phần `file`).
2. Hệ thống kiểm tra kích thước ≤ giới hạn cấu hình, rồi xác định loại file **từ nội dung** (magic bytes), không tin
   `Content-Type` hay đuôi tên file do client gửi; chỉ chấp nhận JPEG, PNG, WebP, PDF.
3. Hệ thống sinh khóa lưu ngẫu nhiên, ghi file qua port `FileStorage`, tính SHA-256 trong lúc ghi.
4. Hệ thống tạo `stored_file` (khóa, tên gốc đã làm sạch, mime đã xác định, kích thước, checksum, người upload).
5. Trả 201 với metadata (id, tên, mime, kích thước, checksum, thời điểm). **Không** trả khóa lưu hay đường dẫn đĩa.

**Tải về / xem metadata**
1. Người dùng gọi `GET /api/v1/files/{id}` (metadata) hoặc `GET /api/v1/files/{id}/content` (nội dung).
2. Hệ thống kiểm tra quyền (mục 3). Không có quyền hoặc không tồn tại → cùng một phản hồi 404.
3. Trả nội dung với `Content-Type` đã lưu, tải dạng đính kèm, header chống đoán kiểu (`nosniff`), không cache công khai.

## 5. Quy tắc nghiệp vụ

Mã BR-T31-xx là quy tắc riêng của task.

- **BR-T31-01** Loại file chấp nhận: `image/jpeg`, `image/png`, `image/webp`, `application/pdf`, xác định bằng chữ ký
  nội dung. Không khớp → 400. Mime lưu là mime đã xác định, không phải mime client khai.
- **BR-T31-02** Kích thước tối đa cấu hình được (đề xuất mặc định **10 MB** mỗi file, Q2). File rỗng → 400. Quá cỡ → 400
  (theo card), không nhận một phần.
- **BR-T31-03** Khóa lưu là chuỗi ngẫu nhiên do server sinh (≥ 128 bit), không chứa bất kỳ phần nào của tên file. Mọi
  đường dẫn trên đĩa được tạo từ khóa này và kiểm tra nằm trong thư mục gốc → không path traversal.
- **BR-T31-04** Tên gốc chỉ dùng để hiển thị và đặt tên khi tải: bỏ phần thư mục, ký tự điều khiển, cắt còn ≤ 255 ký tự;
  tên rỗng sau khi làm sạch → `"file"`.
- **BR-T31-05** Checksum là SHA-256 (hex) của đúng các byte đã lưu.
- **BR-T31-06** Ghi file và ghi DB nhất quán: lỗi khi ghi DB thì xóa file vừa ghi; lỗi khi ghi file thì không tạo dòng DB.
  Không bao giờ có `stored_file` trỏ tới file không tồn tại do lỗi upload.
- **BR-T31-07** Không tiết lộ sự tồn tại của file người khác: không có quyền và không tồn tại cùng trả 404 `NOT_FOUND`.
- **BR-T31-08** Tải về luôn kèm `Content-Disposition: attachment`, `X-Content-Type-Options: nosniff`,
  `Cache-Control: private, no-store`, để trình duyệt không chạy nội dung (ví dụ PDF/ảnh giả mạo) trong origin của API.
- **BR-T31-09** Giới hạn tần suất upload theo tài khoản (đề xuất 30 file / 15 phút, Q5) → vượt quá trả 429.
- **BR-T31-10** Thư mục lưu cấu hình qua biến môi trường, nằm ngoài source; production không có giá trị mặc định
  (thiếu → app không khởi động).

## 6. Phạm vi

### Trong phạm vi
- Port `FileStorage` (lưu, mở đọc, xóa) + adapter đĩa local; bản giả trong test.
- `POST /api/v1/files`, `GET /api/v1/files/{id}`, `GET /api/v1/files/{id}/content`.
- Điểm mở rộng quyền xem file đã gắn (task sau cài luật theo loại đối tượng).
- Sửa `RateLimitFilter` cho riêng upload (Q1), cấu hình multipart, biến môi trường, `.env.example`.
- OpenAPI (thử upload được trên Swagger), test tích hợp.

### Ngoài phạm vi
- Gắn file vào đối tượng (`attachment`), phiên bản tài liệu (UC-53 A1), ảnh bìa mẫu hộp: các task T3.2, T4.x, T7.x, T8.x.
- Xóa file, dọn file chưa gắn quá hạn (đề xuất ở Q6), quét virus, nén/đổi kích thước ảnh, xóa EXIF.
- Lưu trữ đám mây (S3...): chỉ cần thêm adapter mới cho port, không đổi API.
- Backup thư mục file: T9 (roadmap đã ghi).

## 7. Acceptance criteria

| ID | Given / When / Then |
|---|---|
| AC-01 | Given đã đăng nhập; When upload JPEG/PNG/WebP/PDF hợp lệ ≤ giới hạn; Then 201, metadata đúng mime đã xác định, kích thước, SHA-256 khớp nội dung, `uploadedBy` là chính mình; file có trên storage dưới khóa ngẫu nhiên; response không có khóa/đường dẫn. |
| AC-02 | When file có nội dung không phải 4 loại cho phép (kể cả đổi đuôi `.jpg` hoặc khai `Content-Type: image/png` cho file `.exe`/HTML/SVG), file rỗng, thiếu phần `file`; Then 400 kèm field `file`; không tạo dòng DB, không còn file trên storage. |
| AC-03 | When file lớn hơn giới hạn cấu hình; Then 400 (field `file`); không tạo dòng DB/file. File đúng bằng giới hạn được nhận. |
| AC-04 | Given file A do X upload, chưa gắn; When Y (kể cả ADMIN) gọi metadata hoặc content; Then 404 `NOT_FOUND`, giống hệt khi id không tồn tại. X gọi → 200, nội dung byte-for-byte đúng. |
| AC-05 | When tải content; Then `Content-Type` = mime đã lưu, `Content-Disposition: attachment` với tên đã làm sạch (hỗ trợ UTF-8), `X-Content-Type-Options: nosniff`, `Cache-Control: private, no-store`. |
| AC-06 | When tên file gốc chứa `../`, `/`, `\`, ký tự điều khiển hoặc > 255 ký tự; Then khóa lưu và đường dẫn trên đĩa không chịu ảnh hưởng (file nằm trong thư mục gốc), `originalName` đã làm sạch. Adapter từ chối khóa không hợp lệ (ví dụ chứa `..`). |
| AC-07 | Given storage ghi thành công nhưng ghi DB lỗi; Then không còn file mồ côi trên storage. Given storage lỗi; Then 503 và không có dòng DB. |
| AC-08 | Không token / token sai → 401 cho cả upload và tải. Id sai định dạng → 400. |
| AC-09 | Vượt quota upload theo tài khoản → 429 + `Retry-After`. |
| AC-10 | Upload lớn hơn 16 KB (nhưng ≤ giới hạn) không bị 413 bởi middleware; các API JSON khác vẫn giữ giới hạn 16 KB (test hiện có vẫn qua). |
| AC-11 | Endpoint có trong `openapi.yaml` (multipart, ví dụ, mã lỗi) và upload/tải thử được trên Swagger `/docs`; `ApiContractTests`, `SwaggerUiTests` vẫn qua. |
| AC-12 | File đã gắn đối tượng (tạo `attachment` trực tiếp trong test): người upload và ADMIN tải được; người khác 404 (luật mặc định, task sau thay bằng luật theo đối tượng). |

## 8. Truy vết tài liệu → AC

| Nguồn | Nội dung | Đáp ứng |
|---|---|---|
| Roadmap D4 | File qua interface `FileStorage`, bản đầu đĩa local, thư mục cấu hình ngoài source | BR-T31-10, AC-01, AC-07 |
| Card T3.1 | `POST /api/v1/files` multipart tạo `stored_file` (SHA-256, mime, size, uploader) | AC-01 |
| Card T3.1 | Whitelist jpg/png/webp/pdf; giới hạn kích thước cấu hình được; sai mime/quá cỡ → 400 | BR-T31-01/02, AC-02, AC-03 |
| Card T3.1 | Tên lưu là key ngẫu nhiên; không path traversal | BR-T31-03/04, AC-06 |
| Card T3.1 | Người lạ tải file người khác → 403/404; file chưa gắn chỉ người upload thấy | BR-T31-07, AC-04 |
| Card T3.1 | Tải file chỉ cho người có quyền với đối tượng gắn file | Điểm mở rộng + AC-12 (luật mặc định) |
| UC-23 bước 3–4, E1 | Khách đính kèm chứng cứ; hệ thống kiểm tra tệp; tệp không hợp lệ → quay lại | AC-01, AC-02 (gắn vào khiếu nại: T8.1) |
| UC-53 bước 4, E1 | Kiểm tra tệp và quyền; tệp không hợp lệ hoặc không tải xong → không đánh dấu đã nhận | AC-02, AC-03, AC-07 |
| UC-38 bước 2–3, UC-67 bước 3 | Đính kèm hồ sơ kinh doanh; admin kiểm tra hồ sơ | AC-01, AC-12 (gắn hồ sơ: T3.2) |
| UC-30 bước 3, FR 3.2 | Chứng cứ sự cố bàn giao; chụp ảnh hộp sau đóng gói | AC-01 (gắn: T7.x, T4.4) |
| BR-08 | Admin yêu cầu bảng giá, hình ảnh, tài liệu tham chiếu | AC-01, AC-12 |
| NFR 3.1 bảo vệ thông tin cá nhân | File cá nhân không lộ cho người khác | BR-T31-07/08, AC-04, AC-05 |

## 9. Baseline

`sh ./gradlew cleanTest build --console=plain` trên `feature/t3-1-file-storage` (= `master` e112f7e):
BUILD SUCCESSFUL (53s). 66 tests, 65 pass, 1 skipped, 0 failures, 0 errors (trùng kết quả cuối T2.1). Baseline xanh.

## 10. Câu hỏi mở / quyết định cần duyệt

- [x] **Q1 — Sửa `RateLimitFilter` (middleware, code của task khác).** Filter đang chặn mọi body > 16 KB. **Đề xuất:**
  với riêng `POST /api/v1/files` dạng `multipart/form-data`, filter **không** đọc body vào bộ nhớ mà cho đi tiếp; giới hạn
  kích thước do cấu hình multipart của Spring (từ chối khi vượt, file tạm trên đĩa, không nằm trong RAM) và service kiểm
  tra lại. Mọi endpoint khác giữ nguyên 16 KB. Có test cho cả hai phía.
- [x] **Q2 — Giới hạn kích thước.** **Đề xuất:** mặc định 10 MB/file (`FILE_MAX_SIZE`), một file mỗi request. Ảnh điện
  thoại thường 2–6 MB; PDF hồ sơ vài MB.
- [x] **Q3 — Thư mục lưu.** **Đề xuất:** biến `FILE_STORAGE_DIR`. Profile dev mặc định `${user.home}/.foodrescue/files`
  (cùng chỗ với key JWT local); test dùng thư mục tạm; prod bắt buộc khai báo. Cấu trúc trên đĩa: `ab/cd/<khóa>` theo
  2 cặp ký tự đầu của khóa để tránh một thư mục quá nhiều file.
- [x] **Q4 — Quyền với file đã gắn trong T3.1.** **Đề xuất:** interface `AttachmentAccessPolicy` (mỗi loại đối tượng một
  bean, task sau cài). Khi chưa có policy cho loại đó: chỉ người upload và ADMIN. File chưa gắn: chỉ người upload
  (ADMIN cũng không thấy, đúng card).
- [x] **Q5 — Quota upload.** **Đề xuất:** 30 file / 15 phút theo tài khoản, thêm `UPLOAD_ACCOUNT` vào `RateLimitPolicy`
  (enum trong middleware, cần đồng ý như Q1). Chống lạm dụng đĩa.
- [x] **Q6 — File upload mà không bao giờ gắn.** **Đề xuất:** ngoài phạm vi T3.1; ghi đề xuất job dọn file chưa gắn quá
  24 giờ vào T9 (vận hành). Lưu ý: `box_template.cover_file_id` trỏ thẳng `stored_file`, nên job dọn phải coi file được
  `box_template` tham chiếu là "đã gắn".
- [x] **Q7 — Mã lỗi.** **Đề xuất:** dùng mã sẵn có: 400 `VALIDATION_FAILED` (field `file`, message nêu lý do:
  `"Unsupported file type"`, `"File is empty"`, `"File exceeds 10485760 bytes"`), 404 `NOT_FOUND`, 429 `RATE_LIMITED`
  (mã hiện dùng cho quota), 503 `SERVICE_UNAVAILABLE` khi storage lỗi. Không thêm mã mới.

## 11. Change log

- 2026-11-14: Tạo Gate A.
- 2026-11-14: Người dùng duyệt Gate A, chấp nhận đề xuất Q1–Q7.
