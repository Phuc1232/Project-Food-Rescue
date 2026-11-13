# CLAUDE.md — Quy tắc cho agent làm backend FoodRescue

Backend Kotlin 2.3 / Spring Boot 4.1 / JDK 21 / PostgreSQL + Flyway. Package gốc `com.foodrescue.backend`.
Đường dẫn Kotlin trong file này tương đối với `src/main/kotlin/com/foodrescue/backend/`.

Giai đoạn 1–2 (nền API, xác thực/phân quyền) đã xong. Công việc còn lại là giai đoạn 3–9, chia thành
task trong [docs/specs/roadmap-3-9.md](docs/specs/roadmap-3-9.md). Đọc file đó trước khi làm bất cứ việc gì.

## 0. Tài liệu nghiệp vụ là nguồn sự thật

- Tài liệu nghiệp vụ, class diagram, sequence, activity nằm **ngoài repo**; đường dẫn ở mục "Tài liệu nguồn" của
  roadmap. Trước Gate A, đọc phần UC/BR/SD mà card liệt kê (PDF: dùng Read với `pages`; zip: giải nén vào scratchpad).
- `01-business-definition.md` phải có bảng truy vết UC-xx/BR-xx/SD-xx → AC-xx. Khi tài liệu mâu thuẫn với card
  hoặc schema, nêu ra trong Gate A, không tự chọn.
- Lệch có chủ đích đã duyệt: hoàn tiền thủ công qua admin thay cho cổng tự hoàn (roadmap D3); các UC ngừng áp dụng
  trong MVP (D9) không làm.

## 1. Phạm vi

- **Mỗi phiên chỉ làm đúng một task** do người dùng chỉ định (ví dụ "làm T6.2"). Không làm trước task sau,
  không "tiện tay" sửa phần ngoài task.
- Chỉ bắt đầu khi mọi task trong mục "Phụ thuộc" của card đã đánh dấu `[x]`. Nếu chưa, báo người dùng và dừng.
- Không sửa code đã hoàn thành của task khác (đặc biệt `auth`, `common/security`, `middleware`) trừ khi
  card hoặc plan đã duyệt yêu cầu. Nếu buộc phải sửa, nêu lý do trong `02-plan.md` và chờ duyệt.
- Phát hiện lỗi ngoài phạm vi task: ghi vào mục "Phát hiện ngoài phạm vi" của `03-implementation-control.txt`,
  không tự sửa.

## 2. Quy trình một task (bắt buộc, không bỏ bước)

1. **Khảo sát:** đọc task card, các entity/repository/migration liên quan và code của task phụ thuộc.
   Chạy `./gradlew build` để có baseline; nếu baseline đỏ thì báo và dừng.
2. **Gate A:** viết `docs/specs/<task-id>-<slug>/01-business-definition.md` theo mẫu
   `docs/specs/api-foundation-and-auth/01-business-definition.md` (bối cảnh, khảo sát mã, người dùng/quyền,
   luồng, quy tắc BR-xx, phạm vi, AC-xx dạng Given/When/Then, câu hỏi mở).
   **Dừng và chờ người dùng duyệt.** Không viết code.
3. **Gate B:** viết `02-plan.md` theo mẫu `docs/specs/api-foundation-and-auth/02-plan.md` (quyết định đã duyệt,
   thiết kế, API contract, migration, bảng file layout, task list có checkbox, traceability AC → task → test,
   test strategy, rollout/rollback, rủi ro). **Dừng và chờ người dùng duyệt.**
4. **Triển khai:** test trước rồi code, đi lần lượt theo task list trong plan; cập nhật checkbox khi xong từng bước.
5. **Kiểm chứng:** `./gradlew build --console=plain`; đọc `build/reports/tests/test/index.html` (số pass/skip/fail
   theo suite), không chỉ nhìn exit code. Không bao giờ tắt, skip hay xóa test để build xanh.
6. **Báo cáo:** viết `03-implementation-control.txt` theo mẫu `docs/specs/swagger-ui/03-implementation-control.txt`
   (kết quả, kiểm chứng, bảng file NEW/MODIFIED ↔ AC/test, **kịch bản bấm thử trên Swagger**, giới hạn,
   phát hiện ngoài phạm vi).
7. **Đóng task:** đánh dấu `[x]` cho task trong roadmap, thêm thư mục spec mới vào whitelist `.gitignore`
   (xem mục 8), commit.

Tên slug thư mục spec: `t6-2-payment-request` (chữ thường, gạch ngang).

## 3. Git

- Mỗi task một nhánh `feature/<task-id>-<slug>` tách từ `master` mới nhất (ví dụ `feature/t6-2-payment-request`).
- Commit theo Conventional Commits, có task id: `feat(payment): T6.2 create payment request API`.
  Tài liệu Gate A/B có thể commit riêng: `docs(spec): T6.2 business definition`.
- **Không push, không tạo PR, không merge, không rebase lại lịch sử đã push.** Người dùng tự làm.
- Không commit `.env`, key, file trong `tmp/`, `build/`.

## 4. Quy ước code (theo code hiện có)

- API dưới `/api/v1`. Controller chỉ map HTTP ↔ service, không chứa nghiệp vụ.
- Lỗi: `throw ApiException(status, "UPPER_SNAKE_CODE", "English message")`; code ổn định, ghi vào OpenAPI.
  Không trả stack trace, SQL, secret. Dùng các code hiện có (`VALIDATION_FAILED`, `ACCESS_DENIED`,
  `NOT_FOUND`, `CONFLICT`...) trước khi đặt code mới.
- DTO: mỗi DTO một file trong `<feature>/dto/`, không serialize entity. Validate bằng Jakarta Validation.
- Mỗi trách nhiệm một file. Dòng đầu file mới: `// AC-xx,yy: <TênClass> responsibility.`
- Tài khoản hiện tại: `CurrentAccount.get()`. Quyền theo cửa hàng:
  `StoreAuthorizationService.requirePermission(accountId, storeId, "<PERMISSION_CODE>")`; owner có mọi quyền.
  Kiểm tra lại quyền ở service trước khi ghi, không tin storeId/role do client gửi.
- Admin: kiểm tra `PlatformRole.ADMIN` ở server.
- Thời gian: inject `Clock`, không gọi `Instant.now()` trong nghiệp vụ. Lưu/trả UTC, ISO-8601.
- Phân trang: `PageQuery` + `PageResponse`, sort theo whitelist.
- Audit thao tác quan trọng (duyệt, cấp quyền, thay đổi tiền) vào `audit_log` với `correlationId` = requestId.
- Job định kỳ: `@Scheduled` + khóa để hai instance không chạy trùng (ví dụ `SELECT ... FOR UPDATE SKIP LOCKED`);
  logic job nằm trong service để test gọi trực tiếp với `Clock` cố định.
- Tích hợp bên ngoài (lưu file, payOS, gửi thông báo) luôn qua interface (port) + adapter; test dùng bản giả.

## 5. Bất biến tiền và tồn kho (vi phạm = task chưa xong)

- Tiền là `BigDecimal` VND, số nguyên đồng (scale 0 khi tính). Không dùng `Double`/`Float`.
- `financial_event`, `ledger_entry`, `financial_policy` chỉ INSERT (trigger V3 chặn sửa/xóa).
  Sửa sai bằng bút toán `ADJUSTMENT` trỏ `original_entry_id`. `financial_event.business_key` là khóa idempotency.
- Mọi thao tác làm thay đổi tiền hoặc tồn kho phải idempotent: unique constraint ở DB + khóa dòng
  (`PESSIMISTIC_WRITE`/`FOR UPDATE`) hoặc `@Version`. Đổi trạng thái + ghi event/ledger trong **một** transaction.
- **Không gọi HTTP ra ngoài (payOS) bên trong transaction DB.** Ghi ý định trước, gọi ngoài, ghi kết quả sau.
- Webhook payOS: xác minh checksum **trước** mọi xử lý; trùng lặp trả thành công mà không ghi lần hai.
- Đơn chỉ `CONFIRMED` khi tổng tiền đã xác minh ≥ `total_amount` và còn trong hạn giữ hộp.
- Tổng hoàn của một đơn không vượt tổng tiền đã xác minh. Không giải tỏa/chi trả khoản chưa qua cửa sổ tranh chấp.
- Không bán trùng: số hộp giữ/bán không vượt số hộp `AVAILABLE` đã qua kiểm tra; giữ hộp trong một transaction.
- Thay đổi giá menu/mẫu hộp không làm đổi giá trị của hộp, đơn đã tạo (dùng snapshot).

## 6. Bí mật và payOS

- Key payOS (`PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY`) chỉ đọc từ biến môi trường / `.env`
  (đã gitignore); cập nhật `.env.example` với giá trị rỗng.
- **Test tuyệt đối không dùng key thật hay gọi payOS thật**; dùng `FakePaymentGateway`.
- Không log key, checksum, nội dung webhook đầy đủ, JWT, refresh token, mật khẩu.
- Agent không tự chạy thao tác tạo giao dịch thật; nếu cần thử với tiền thật, đưa hướng dẫn để người dùng tự làm.

## 7. Database

- Chỉ thêm migration mới `V<n>__<mo_ta>.sql`; **không sửa V1–V4** hay migration đã commit.
  `n` = số lớn nhất hiện có trên `master` + 1.
- Hibernate `ddl-auto: validate`: entity và migration phải khớp.
- Dữ liệu tham chiếu (permission code, food category, financial policy mặc định) seed bằng migration,
  không seed trong code khởi động.

## 8. Test và tài liệu API

- Integration test kế thừa `support/AuthTestFixtures` (PostgreSQL Testcontainers, random port, HTTP thật).
  Không dùng DB của Compose. Fixture dùng chung đặt trong `src/test/kotlin/.../support/`.
- Mỗi AC có ít nhất một test; thao tác tiền/tồn kho có test đồng thời (hai request/webhook cùng lúc).
  Test quyền: 401 khi thiếu token, 403 khi sai cửa hàng/sai vai trò.
- Mỗi endpoint mới phải có trong `docs/api/openapi.yaml` (tag, schema, ví dụ, mã lỗi) để thử được trên
  Swagger `/docs` (profile dev). `ApiContractTests` phải vẫn qua.
- Whitelist thư mục spec mới trong `.gitignore`, ví dụ:
  ```
  !docs/specs/t6-2-payment-request/
  ```

## 9. Chạy trên macOS

```bash
cp .env.example .env    # điền giá trị local, không commit
docker compose up -d db
./gradlew build
./gradlew bootRun --args="--spring.profiles.active=dev"   # Swagger: http://localhost:8080/docs
```
Test cần Docker đang chạy. Xem thêm `docs/auth-setup.md` (RSA key, rate limit secret, mail).

## 10. Cấm

- Sửa migration cũ; commit secret; push/PR/merge; tắt hoặc xóa test; thêm endpoint giả chỉ để test;
  làm nhiều task trong một phiên; bỏ qua Gate A/B; gọi payOS thật trong test.
