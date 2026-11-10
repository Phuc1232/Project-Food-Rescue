# Vận hành API và xác thực FoodRescue

Các biến cần thiết có trong `.env.example`. Spring Boot được cấu hình nạp `.env` ở thư mục gốc backend qua Config Data (`optional:file:.env[.properties]`); không cần thư viện bên thứ ba hoặc export thủ công. `.env` có cú pháp properties đơn giản `TEN_BIEN=gia_tri`, không dùng cú pháp shell/nội suy biến. Biến môi trường hệ điều hành và tham số dòng lệnh vẫn có thể ghi đè giá trị trong file. `local.properties` chỉ là đường dẫn Android SDK, giữ nguyên.

## Chạy local

1. Dùng JDK 21 và Docker. Đặt DB_PASSWORD đúng DB local; không đổi mật khẩu/volume DB đang có.
2. Tạo RSA key một lần ngoài repository (OpenSSL):
   ```powershell
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out private.pem
   openssl pkey -in private.pem -pubout -out public.pem
   ```
   Đặt JWT_PRIVATE_KEY_LOCATION/JWT_PUBLIC_KEY_LOCATION dạng `file:C:/...`, JWT_KEY_ID và RATE_LIMIT_KEY_SECRET ngẫu nhiên >=32 byte. Bảo vệ ACL private key. Không commit key.
3. Dùng SMTP local không relay ra ngoài, ví dụ Mailpit ở port 1025; xem thư trên giao diện mail local. Đặt AUTH_ACTION_BASE_URL là URL frontend có trang verify-email/reset-password; token trong URL fragment, frontend đọc và POST token lên API. Backend không có UI này.
4. Sao chép `.env.example` thành `.env` ở thư mục gốc backend, điền các giá trị thật (đặc biệt DB_PASSWORD, key locations, JWT_KEY_ID và RATE_LIMIT_KEY_SECRET). `.env` đã được gitignore; không commit file hoặc private key. Chạy `docker compose up -d db`, rồi `.\gradlew.bat bootRun --args="--spring.profiles.active=dev"`.
5. Xem `/docs`, `/openapi.yaml`; hai đường dẫn chỉ mở dev/test. Health tối giản: `/actuator/health/liveness` và `/actuator/health/readiness`.

Luồng: POST register → lấy token từ thư → POST verify-email → POST login → Authorization: Bearer accessToken cho /api/v1/accounts/me. Client phải serialize refresh. Mỗi refresh trả một cặp token mới; token cũ dùng lại sẽ thu hồi cả phiên, kể cả access/refresh vừa cấp. Mất response refresh có thể cần login lại. Token không được ghi log, analytics hoặc lưu vào URL query.

## Production

Bắt buộc cấu hình DB_URL/USER/PASSWORD, RSA keys + key ID, JWT_ISSUER/AUDIENCE, RATE_LIMIT_KEY_SECRET, MAIL_HOST/PORT/FROM, AUTH_ACTION_BASE_URL HTTPS. Dùng HTTPS ở ingress, cấu hình SMTP xác thực/TLS và CORS_ORIGINS allowlist. Trusted proxies chỉ khai báo CIDR thực sự thuộc hạ tầng; mặc định bỏ qua X-Forwarded-For. Không thêm forwarded header filter tự đổi remote address. Không đặt profile dev/test ở production.

Access 15 phút, session tuyệt đối 7 ngày; reset 15 phút, verify 24 giờ. Mật khẩu 12–128 Unicode code points, Argon2id (19 MiB, 2 lượt, parallelism 1); đo lại chi phí trên máy triển khai trước tăng traffic. Đo tham khảo trên máy local/JDK 21: encode 81,8 ms, verify 69,1 ms, trung bình 5 lượt mỗi thao tác sau warmup; đây không phải kiểm thử tải production. Hash cũ không hỗ trợ phải reset. Không trim/cắt mật khẩu.

Bootstrap admin là lệnh opt-in với account đã xác minh:
```powershell
java -jar build/libs/foodrescue-backend.jar --spring.profiles.active=prod --auth.bootstrap-admin=true --admin-email=verified@example.com --spring.main.web-application-type=none
```
Chạy bằng môi trường vận hành kiểm soát, sau khi xong dừng process; bỏ flag bootstrap trong lần khởi động API thông thường. Không có endpoint tạo admin hoặc credential mặc định.

## Migration và rollback

Backup database đích và thử trên staging trước rollout. App mới tự áp dụng V4 qua Flyway lúc boot; lượt triển khai source này chỉ dùng DB container test. V1–V3 giữ nguyên. V4 thu hồi phiên cũ, giữ lịch sử và không tự xác minh account cũ. Account ACTIVE cũ cần resend/verify trước login. Không sửa flyway history hoặc drop bảng để rollback. Binary cũ không hiểu pending/null token hash: ưu tiên sửa tiến/maintenance hoặc phục hồi backup đã thử.

Key rotation an toàn tối thiểu: maintenance, thu hồi toàn bộ login_session đang hoạt động (revoked_at, updated_at và version), thay cặp key/key ID rồi restart. Tất cả client login lại. Không tạo key mới mỗi lần boot. Nếu cần rotation không logout, bổ sung keyring giữ public key cũ và kiểm chứng trước rollout.

## Quota, email và lưu trữ

Counter PostgreSQL atomic UPSERT, cửa sổ DB 15 phút; HMAC email/IP; cleanup tối đa 1000 counter/phút. DB hỏng trả 503. Mỗi lỗi 429 có Retry-After. Incoming login quota trước hashing; failed quota không khóa account. Các quota chi tiết nằm trong 02-plan.md và RateLimitPolicy.kt.

SMTP gửi sau commit, lỗi chỉ log thông báo đã loại credential. Chưa có durable outbox: crash sau commit trước gửi hoặc SMTP lỗi phục hồi bằng resend/forgot có quota. Reset không thay mật khẩu trước khi người dùng POST token.

Refresh-token history phải giữ ít nhất đến hết session expiry để phát hiện reuse. Hiện giữ lịch sử credential hết hạn; cần kế hoạch retention khi có số liệu dung lượng, không xóa LoginSession/audit. Rate counter được dọn tự động. Không log request body, Authorization hoặc bật SMTP protocol debug.

## Kiểm thử

`.\gradlew.bat build --console=plain` chạy unit, HTTP và PostgreSQL Testcontainers; không dùng DB Compose. Keys tạm được sinh riêng trong test; adapter mail ghi bộ nhớ, có mô phỏng lỗi. Báo cáo HTML tại build/reports/tests/test/index.html. Tham khảo API thư viện: [Spring JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).


## Swagger UI tương tác (dev/test)

Mở `http://localhost:8080/docs` (thay port nếu cấu hình PORT); hệ thống chuyển tới `/docs/`. Nếu dùng servlet context path, thêm prefix tương ứng, ví dụ `/foodrescue/docs`. Giao diện và OpenAPI cùng backend, asset được đóng gói trong JAR; không cần CDN/validator online ở runtime.

1. Trong nhóm Registration, chọn register → Try it out, điền email thử và mật khẩu của bạn → Execute. Các examples chỉ là dữ liệu minh họa, không phải tài khoản có sẵn.
2. Lấy token xác minh trong SMTP local, gọi verify-email. Backend không có trang frontend xác minh email.
3. Trong Authentication, Execute login. Copy `accessToken` ở response, nhấn Authorize, dán JWT thuần (không thêm chữ `Bearer`) rồi Authorize → Close.
4. Trong Account, Execute `/api/v1/accounts/me`; UI tự thêm header Bearer cho endpoint bảo vệ. Thiếu/sai/hết hạn token trả 401 như client khác.
5. Muốn refresh: nhập refreshToken hiện tại vào endpoint refresh, chỉ Execute một lần, lưu refreshToken mới; thay accessToken mới trong Authorize. Không gửi hai refresh đồng thời hoặc retry token cũ; reuse thu hồi cả phiên.
6. Execute logout hoặc logout-all trong Sessions; gọi me lại với access cũ nhận 401. UI không tự login/refresh, không tự thay authorization khi token bị thu hồi.

Authorization chỉ ở bộ nhớ trang; reload xóa lựa chọn Authorize. Request/response vẫn hiển thị token trên trang khi vừa thử login/refresh; đóng tab sau sử dụng và không chia sẻ ảnh/cURL chứa credential. Không lưu trace/HAR chứa token vào repository. Quota vẫn áp dụng; 429 có Retry-After.

Swagger UI chỉ mở khi có dev hoặc test và KHÔNG có prod. Production, default và mọi tổ hợp chứa prod chặn cả HTML/spec/assets (anonymous 401, bearer hợp lệ 403). Các route API vẫn giữ yêu cầu xác thực hiện hành. Không bật dev/test trên production để mở docs.

Swagger UI version 5.34.1 được pin trong build configuration `swaggerUiAssets`; không có runtime dependency WebJar. Khi cập nhật phiên bản, kiểm tra upstream/license, build resources và chạy test/browser smoke trước khi đổi version. Hợp đồng nguồn duy nhất vẫn là `docs/api/openapi.yaml`.


Để mở sandbox browser dùng PostgreSQL Testcontainers và mail giả (không dùng DB/SMTP ứng dụng), có thể chạy kiểm thử opt-in trong PowerShell:
```powershell
$env:SWAGGER_BROWSER_SMOKE = "true"
.\gradlew.bat test --tests "*SwaggerUiContextPathTests.hold disposable browser sandbox until stop signal" --console=plain
```
Khi fixture sẵn sàng, `tmp/swagger-browser-sandbox.json` chứa URL port ngẫu nhiên và account thử của container. Mở URL đó để thử UI. Sau khi xong, tạo file `tmp/swagger-browser-stop` từ terminal khác để kết thúc fixture; fixture tự xóa file thông tin và stop. Thời gian chờ tối đa 20 phút. Bỏ biến `SWAGGER_BROWSER_SMOKE` sau khi thử. Build thông thường bỏ qua đúng ca giữ sandbox này; các test HTTP/profile vẫn chạy bình thường.

Chi tiết thay đổi và bằng chứng kiểm chứng: [báo cáo kiểm soát Swagger UI](specs/swagger-ui/03-implementation-control.txt).
