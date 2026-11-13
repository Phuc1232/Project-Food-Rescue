# Technical Plan: T3.1 — Lưu trữ file

**Based on:** [01-business-definition.md](01-business-definition.md) (Gate A đã duyệt 2026-11-14)
**Status:** Done — đã triển khai, build xanh. Xem 03-implementation-control.txt.
**Last updated:** 2026-11-14

## 0. Approved decisions (từ Gate A)

- [x] Q1: Sửa `RateLimitFilter`: riêng `POST /api/v1/files` dạng multipart không đọc body vào RAM; endpoint khác giữ 16 KB.
- [x] Q2: Mặc định 10 MB/file (`FILE_MAX_SIZE`), một file mỗi request.
- [x] Q3: `FILE_STORAGE_DIR`; mặc định `${user.home}/.foodrescue/files` ngoài prod; prod bắt buộc; đĩa `ab/cd/<key>`.
- [x] Q4: `AttachmentAccessPolicy` theo loại đối tượng; chưa có policy → người upload + ADMIN; file chưa gắn → chỉ người upload.
- [x] Q5: Quota `UPLOAD_ACCOUNT` 30 file / 15 phút theo tài khoản (thêm vào enum `RateLimitPolicy`).
- [x] Q6: Dọn file chưa gắn để T9; ghi chú `box_template.cover_file_id`.
- [x] Q7: Chỉ dùng mã lỗi có sẵn.

### Quyết định kỹ thuật phát sinh ở Gate B (theo hướng ít đụng code chung nhất)

- **P1 — Lỗi multipart không đi vào handler chung.** `MaxUploadSizeExceededException`/`MultipartException` hiện rơi vào
  nhánh `else` của `ApiExceptionHandler` → 500. Thay vì sửa handler chung, thêm `file/FileUploadExceptionHandler`
  (`@RestControllerAdvice`, `@Order(HIGHEST_PRECEDENCE)`, chỉ bắt 2 loại này) và **ủy quyền** cho
  `ApiExceptionHandler.handle(...)` để giữ nguyên định dạng lỗi.
- **P2 — Giới hạn hai tầng.** `spring.servlet.multipart.max-file-size=-1`, `max-request-size=${FILE_MAX_REQUEST_SIZE:12MB}`
  (trần cứng, Tomcat ghi phần upload ra file tạm, không giữ trong RAM). Giới hạn nghiệp vụ `files.max-size=${FILE_MAX_SIZE:10MB}`
  do service kiểm tra → trả 400 sạch kèm field `file`. Request vượt trần cứng → 400 qua P1 (Tomcat có thể đóng kết nối
  với body rất lớn; đây là hành vi bảo vệ chấp nhận được).
- **P3 — Không bọc IO file trong transaction DB.** Ghi file trước (ngoài transaction), sau đó lưu `stored_file` trong
  `TransactionTemplate`; lỗi DB → xóa file vừa ghi rồi ném lại lỗi.
- **P4 — `SwaggerUiTests` khẳng định 15 paths.** Thêm 3 path mới → sửa số kỳ vọng thành 18 (test của task khác, chỉ đổi
  hằng số phản ánh hợp đồng API).
- **P5 — Thư mục test.** `support/AuthTestFixtures.TestAuthKeys.configure` thêm `files.storage-dir` trỏ thư mục tạm
  (fixture dùng chung; mọi test kể cả profile prod đều đi qua đây).

## 1. Approach summary

Package `file`: port `FileStorage` (store/open/delete theo key) + adapter `LocalDiskFileStorage`; `FileTypeDetector`
nhận diện loại từ magic bytes; `FileService` điều phối upload (quota → kiểm tra → ghi file + SHA-256 → lưu DB, bù trừ
khi lỗi) và đọc (kiểm tra quyền qua `FileAccessService`); `FileController` chỉ map HTTP. Quyền với file đã gắn đi qua
danh sách bean `AttachmentAccessPolicy` (chưa có bean nào trong T3.1 → luật mặc định).

## 2. Codebase survey results

Như Gate A §2, bổ sung:
- `RateLimitFilter` được tạo trực tiếp trong `SecurityConfiguration` (không phải bean), chạy trước xác thực, áp cho
  `/api/v1/**`. Body bị đọc hết (tối đa 16 385 byte) rồi bọc lại.
- `RateLimitService.check(policy, key)` dùng cửa sổ cố định 15 phút trong PostgreSQL, ném 429 `RATE_LIMITED` + `retryAfter`.
- `AccountRoleRepository.existsByAccountIdAndRole` có sẵn để kiểm tra ADMIN. Role đọc từ DB mỗi request.
- Spring Boot mặc định `file-size-threshold=0` → phần upload ghi ra file tạm của Tomcat.
- Không có `src/test/resources`; mọi test đặt thuộc tính qua `TestAuthKeys.configure`.

## 3. Design

### 3.1 API contract (tag `Files`, bearer bắt buộc)

| Method | Path | Request | 2xx | Lỗi |
|---|---|---|---|---|
| POST | `/api/v1/files` | `multipart/form-data`, phần `file` (binary) | 201 `FileResponse`, header `Location` | 400 `VALIDATION_FAILED` field `file` (thiếu, rỗng, sai loại, quá cỡ); 400 `INVALID_REQUEST` (không phải multipart); 401; 429 `RATE_LIMITED`; 503 `SERVICE_UNAVAILABLE` (storage lỗi) |
| GET | `/api/v1/files/{id}` | — | 200 `FileResponse` | 400 id sai; 401; 404 `NOT_FOUND` |
| GET | `/api/v1/files/{id}/content` | — | 200 bytes, headers BR-T31-08 | 400; 401; 404; 503 |

`FileResponse`: `id` (uuid), `originalName`, `mimeType`, `sizeBytes`, `checksum` (sha256 hex), `createdAt` (UTC).
Không có `storageKey`, không có `uploadedBy` (chỉ người upload thấy file chưa gắn, nên trường này thừa).

Thông điệp field `file`: `"File is required"`, `"File is empty"`, `"Unsupported file type"`,
`"File exceeds <n> bytes"`.

### 3.2 Nhận diện loại file (`FileTypeDetector`)

Đọc tối đa 12 byte đầu:
- JPEG `FF D8 FF` → `image/jpeg`
- PNG `89 50 4E 47 0D 0A 1A 0A` → `image/png`
- WebP `52 49 46 46 ?? ?? ?? ?? 57 45 42 50` (`RIFF....WEBP`) → `image/webp`
- PDF `25 50 44 46 2D` (`%PDF-`) → `application/pdf`
- Còn lại → không hỗ trợ (SVG, HTML, GIF, exe...).

### 3.3 Storage (`FileStorage`, `LocalDiskFileStorage`)

```
interface FileStorage {
  fun store(key: String, content: InputStream): StoredObject   // StoredObject(sizeBytes, sha256Hex)
  fun open(key: String): InputStream
  fun delete(key: String)
}
```
- Key do service sinh: 32 byte `SecureRandom` → 64 ký tự hex. Adapter chỉ nhận key khớp `^[a-f0-9]{64}$`, ngược lại
  `IllegalArgumentException` (chặn traversal tầng 2).
- Đường dẫn `root/ab/cd/<key>`; `normalize()` và kiểm tra `startsWith(root)` (tầng 3).
- Ghi vào file tạm cùng thư mục rồi `ATOMIC_MOVE`; SHA-256 tính qua `DigestInputStream` trong lúc ghi.
- Thư mục tạo khi ghi (không tạo lúc khởi động) → cấu hình sai bộc lộ thành 503 khi upload, không làm app chết ở dev.
  Prod vẫn bắt buộc khai báo biến (placeholder không có mặc định).

### 3.4 Upload (`FileService.upload`)

```
upload(accountId, part?):
  limits.check(UPLOAD_ACCOUNT, accountId)                 // 429
  part == null → 400 "File is required"; part.size == 0 → 400 "File is empty"
  part.size > maxSize → 400 "File exceeds"
  header = first 12 bytes; mime = detector(header) ?: 400 "Unsupported file type"
  key = random; stored = storage.store(key, header + rest)  // IOException → 503
  if stored.sizeBytes > maxSize → storage.delete; 400
  try tx { save StoredFile(key, sanitize(originalName), mime, size, sha256, account) }
  catch → storage.delete(key); rethrow
  return FileResponse
```

### 3.5 Đọc (`FileAccessService.canRead(accountId, file)`)

```
attachments = attachmentRepo.findAllByFileId(file.id)
if attachments.isEmpty() → return file.uploadedBy.id == accountId
for a in attachments: policy = policies.firstOrNull { it.supports(a) }
  if policy != null && policy.canRead(accountId, a) → true
uploader or ADMIN → true (luật mặc định khi chưa có policy cho loại đó)
else false → 404
```

### 3.6 Cấu hình

`application.yml`:
```yaml
spring.servlet.multipart: { max-file-size: -1, max-request-size: ${FILE_MAX_REQUEST_SIZE:12MB} }
files:
  storage-dir: ${FILE_STORAGE_DIR:${user.home}/.foodrescue/files}
  max-size: ${FILE_MAX_SIZE:10MB}
```
`application-prod.yml`: `files.storage-dir: ${FILE_STORAGE_DIR}` (không mặc định).
`.env.example`: `FILE_STORAGE_DIR=`, `FILE_MAX_SIZE=10MB`, `FILE_MAX_REQUEST_SIZE=12MB`.

### 3.7 Data model và migration

Không migration. Thêm `AttachmentRepository.findAllByFileId`.

## 4. File layout

Kotlin tương đối với `src/main/kotlin/com/foodrescue/backend/` (main) và `src/test/kotlin/com/foodrescue/backend/` (test).

| File | NEW/MODIFIED | Trách nhiệm |
|---|---|---|
| `file/FileProperties.kt` | NEW | `files.storage-dir`, `files.max-size` |
| `file/storage/FileStorage.kt` | NEW | Port + `StoredObject` |
| `file/storage/LocalDiskFileStorage.kt` | NEW | Adapter đĩa local |
| `file/service/FileTypeDetector.kt` | NEW | Magic bytes → mime |
| `file/service/FileNameSanitizer.kt` | NEW | Làm sạch tên gốc |
| `file/service/FileService.kt` | NEW | Upload/đọc metadata/mở nội dung |
| `file/service/FileAccessService.kt` | NEW | Luật quyền đọc |
| `file/service/AttachmentAccessPolicy.kt` | NEW | Điểm mở rộng cho task sau |
| `file/dto/FileResponse.kt` | NEW | DTO metadata |
| `file/controller/FileController.kt` | NEW | 3 endpoint |
| `file/FileUploadExceptionHandler.kt` | NEW | Lỗi multipart → 400 (P1) |
| `file/repository/AttachmentRepository.kt` | MODIFIED | `findAllByFileId` |
| `middleware/ratelimit/RateLimitFilter.kt` | MODIFIED | Bỏ qua buffer cho upload multipart (Q1) |
| `middleware/ratelimit/RateLimitPolicy.kt` | MODIFIED | `UPLOAD_ACCOUNT(30)` (Q5) |
| `resources/application.yml`, `application-prod.yml` | MODIFIED | Cấu hình 3.6 |
| `.env.example` | MODIFIED | Biến file |
| `docs/api/openapi.yaml` | MODIFIED | 3 path, schema, ví dụ |
| test `support/AuthTestFixtures.kt` | MODIFIED | Thư mục file tạm (P5) + helper upload multipart |
| test `SwaggerUiTests.kt` | MODIFIED | 15 → 18 paths (P4) |
| test `FileTypeDetectorTests.kt` | NEW | Unit |
| test `LocalDiskFileStorageTests.kt` | NEW | Unit: traversal, sharding, checksum, atomic |
| test `FileUploadTests.kt` | NEW | Integration AC-01..12 |
| test `FileStorageFailureTests.kt` | NEW | Storage lỗi → 503, không có dòng DB |

## 5. Task list

- [x] B1. Unit test + `FileTypeDetector`, `FileNameSanitizer`.
- [x] B2. Unit test + `FileStorage`, `LocalDiskFileStorage`, `FileProperties`, cấu hình.
- [x] B3. Integration test `FileUploadTests`, `FileStorageFailureTests` (đỏ).
- [x] B4. `FileService`, `FileAccessService`, `AttachmentAccessPolicy`, DTO, controller, exception handler, repository.
- [x] B5. `RateLimitFilter` + `UPLOAD_ACCOUNT`.
- [x] B6. OpenAPI + `SwaggerUiTests` 18 paths; `.env.example`.
- [x] B7. Build đầy đủ, đọc kết quả theo suite; smoke trên app dev (upload/tải qua HTTP thật).
- [x] B8. Báo cáo 03, roadmap `[x]`, whitelist `.gitignore`, commit.

## 6. Traceability

| AC | Task | Test |
|---|---|---|
| AC-01 upload hợp lệ, metadata, SHA-256, không lộ key | B2, B4 | `FileUploadTests` 4 loại; `LocalDiskFileStorageTests` checksum |
| AC-02 sai loại/rỗng/thiếu → 400 field `file`, không rác | B1, B4 | `FileTypeDetectorTests`; `FileUploadTests` invalid (đổi đuôi, khai mime giả, SVG/HTML, rỗng, thiếu phần, JSON) |
| AC-03 quá cỡ → 400; đúng giới hạn được nhận | B4 | `FileUploadTests` boundary (max, max+1) |
| AC-04 người khác/ADMIN không thấy file chưa gắn → 404 như id lạ | B4 | `FileUploadTests` access |
| AC-05 header tải về | B4 | `FileUploadTests` content headers + byte-for-byte |
| AC-06 tên độc hại không ảnh hưởng đường dẫn; adapter chặn key xấu | B1, B2 | `LocalDiskFileStorageTests`; `FileUploadTests` hostile names |
| AC-07 lỗi DB → không file mồ côi; storage lỗi → 503, không dòng DB | B4 | `FileUploadTests` trigger chặn insert; `FileStorageFailureTests` |
| AC-08 401 không token; id sai → 400 | B4 | `FileUploadTests` auth |
| AC-09 quota → 429 + Retry-After | B5 | `FileUploadTests` quota |
| AC-10 upload > 16 KB qua filter; JSON vẫn 16 KB | B5 | `FileUploadTests` large; `ApiContractTests` (413 sẵn có) |
| AC-11 OpenAPI/Swagger | B6, B7 | `SwaggerUiTests`, `ApiContractTests`; smoke |
| AC-12 file đã gắn: uploader + ADMIN đọc được, người khác 404 | B4 | `FileUploadTests` attached (tạo store/partner_application/attachment bằng JDBC) |

## 7. Test strategy

Integration kế thừa `AuthTestFixtures` (Testcontainers, HTTP thật, multipart dựng tay). Thư mục file của test là thư mục
tạm; mỗi test đếm file trên đĩa trước/sau để kiểm không có rác. Storage lỗi chạy context riêng với `files.storage-dir`
trỏ vào đường dẫn không tạo được. Không sửa/skip test cũ (trừ hằng số ở P4).

## 8. Rollout & rollback

- Không migration. Rollback = revert commit; file đã upload nằm ngoài DB, có thể xóa thư mục nếu cần.
- Triển khai prod cần khai báo `FILE_STORAGE_DIR` (volume bền vững) và đưa thư mục vào backup (T9).

## 9. Risks

| Rủi ro | Giảm thiểu |
|---|---|
| Nới filter làm upload không bị giới hạn 16 KB | Chỉ đúng 1 method + path + content type; trần cứng multipart; Spring Security chặn 401 trước khi parse |
| Tomcat đóng kết nối với body vượt trần cứng thay vì trả 400 | Giới hạn nghiệp vụ (10 MB) thấp hơn trần (12 MB) → trường hợp thường gặp vẫn ra 400 sạch |
| File nội dung độc (PDF chứa script) | Tải về dạng attachment + `nosniff`; chưa quét virus (ngoài phạm vi, ghi giới hạn) |
| Đĩa đầy do upload rác | Quota 30/15 phút/tài khoản; job dọn ở T9 |
| Ảnh chứa EXIF/GPS | Ghi giới hạn; xem xét khi làm T4.4/T8.1 |

## 10. Out of scope / later

Gắn file (`attachment`) và policy cụ thể theo đối tượng; xóa file; job dọn file chưa gắn; quét virus; xử lý ảnh; S3.

## 11. References & open questions

Gate A §8. Không còn câu hỏi mở.

## 12. Change log

- 2026-11-14: Tạo Gate B; người dùng cho triển khai liền mạch theo quy trình.
- 2026-11-14: Khi triển khai: `FileStorageFailureTests` dùng `@MockitoBean FileStorage` (lớp con không ghi đè được
  `@DynamicPropertySource` của fixture); thêm unit test adapter với thư mục gốc không dùng được; port khai báo
  `@Throws(IOException::class)`; `SwaggerUiTests` đổi thêm số tag 6 → 7 (tag `Files`), cùng lý do P4.
