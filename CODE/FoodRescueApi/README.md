# FoodRescue API

Kotlin · Spring Boot · PostgreSQL · Flyway. Phần đã triển khai: tài khoản khách hàng, OTP email, phiên đăng nhập và hồ sơ cá nhân. Các API đơn hàng, tồn kho và thanh toán chưa được mở.

## Chạy trên máy

1. Trong `CODE`, sao chép `.env.example` thành `.env`, đặt mật khẩu database.
2. Tạo **hai khóa khác nhau**, mỗi khóa ít nhất 32 byte ngẫu nhiên; điền vào `AUTH_SIGNING_KEY` và `AUTH_CHALLENGE_KEY`. Với PowerShell 7:

   ```powershell
   [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
   ```

   Chạy hai lần. Giữ nguyên khóa giữa các lần khởi động; không commit `.env`, không đưa khóa vào Android. Thay khóa sẽ ảnh hưởng token hoặc OTP đang dùng.
3. Tại `CODE`, chạy `docker compose up -d --build`. Flyway tự áp dụng migration; không sửa V1 đã dùng chung.
4. API: `http://localhost:8080/api/v1/system/status`. Hộp thư thử: `http://localhost:8025`.

Mailpit chỉ dùng khi `MAIL_MODE=sandbox` và `COMPOSE_PROFILES=mail-preview`, không gửi ra ngoài. Để dùng tài khoản thật, cấu hình email bên dưới. Backend không tự chuyển sang hộp thư thử khi SMTP lỗi.

Chạy API từ IDE: đặt `DB_URL`, `DB_USER`, `DB_PASSWORD`, hai khóa trên và `MAIL_HOST=localhost`, `MAIL_PORT=1025`, rồi chạy `./gradlew bootRun`. Docker Compose mặc định dùng `MAIL_HOST=mailpit` vì backend nằm cùng mạng Docker.

## Gửi email thật

### Gmail riêng của dự án (chưa có tên miền)

Repo đã có cấu hình Gmail SMTP trong `SetupEmail.ps1` (máy chủ, cổng, TLS và kiểm tra kết nối). Mỗi máy chạy backend riêng cần nhập email gửi thư và mật khẩu ứng dụng tại cửa sổ này; các giá trị được lưu trong `.env`, không đi theo bản clone. Người nhận OTP chỉ cần nhập email của mình trong app. Nếu cả nhóm dùng một backend đã cấu hình, chỉ máy chủ backend đó cần thông tin gửi thư.

1. Bật Xác minh 2 bước cho Gmail gửi thư. Mở [Mật khẩu ứng dụng Google](https://myaccount.google.com/apppasswords), tạo mật khẩu tên `FoodRescue`. Không dùng mật khẩu đăng nhập Gmail. Một số tài khoản tổ chức hoặc tài khoản có bảo vệ nâng cao không hỗ trợ tính năng này; xem [hướng dẫn Google](https://support.google.com/accounts/answer/185833?hl=vi).
2. Trên Windows, chạy `pwsh -STA -File ./SetupEmail.ps1 -Provider Gmail`. Điền Gmail và mật khẩu ứng dụng tại cửa sổ có ô che ký tự. Script chỉ cập nhật phần email trong `CODE/.env`, giữ nguyên khóa xác thực và database; không tự gửi thư.
3. Trong `CODE`, chạy `docker compose up -d backend`. Với môi trường đang dùng project riêng, thêm `-p foodrescue-auth-dev`. Backend kiểm tra xác thực SMTP khi khởi động; sai thông tin thì dừng thay vì báo sẵn sàng giả.
4. Đăng ký bằng địa chỉ email thật trong app, mở Gmail kiểm tra mã, nhập mã rồi đăng nhập. Kiểm tra thêm gửi lại mã và quên mật khẩu. Kiểm tra cả Spam nếu chưa thấy thư. SMTP chấp nhận thư chưa có nghĩa thư chắc chắn vào Inbox; cần xác nhận ở hộp thư người nhận.

Script đặt `MAIL_MODE=smtp`, `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`, `MAIL_AUTH=true`, `MAIL_STARTTLS=true`, `MAIL_SSL=false`, `MAIL_TEST_CONNECTION=true`; `MAIL_FROM` trùng Gmail gửi thư. `COMPOSE_PROFILES` rỗng để không khởi tạo Mailpit mới. Mailpit cũ có thể dừng riêng bằng `docker compose --profile mail-preview stop mailpit` sau khi đã kiểm chứng Gmail.

Mật khẩu ứng dụng chỉ nằm trong `.env` bị Git bỏ qua. Không chụp file, không dùng `docker compose config` hoặc `docker inspect` để chia sẻ cấu hình đầy đủ. Nếu đổi mật khẩu tài khoản Google, có thể cần tạo lại mật khẩu ứng dụng. Gmail phù hợp giai đoạn nhóm thử dùng thật, chịu hạn mức của tài khoản; khi mở rộng nên dùng dịch vụ email giao dịch.

### Resend khi có tên miền

Xác minh tên miền theo [tài liệu Resend](https://resend.com/docs/dashboard/domains/introduction), tạo API key có quyền gửi thư cho tên miền, rồi chạy `pwsh -STA -File ./SetupEmail.ps1 -Provider Resend`. SMTP dùng `smtp.resend.com:587`, username `resend`, password là API key; địa chỉ gửi thuộc tên miền đã xác minh. Không dùng `gmail.com` làm tên miền của mình. [Cấu hình SMTP chính thức](https://resend.com/docs/send-with-smtp).

Cả hai cách đều gửi email UTF-8 gồm bản HTML và văn bản thuần, không tải ảnh theo dõi. TLS bắt buộc và kiểm tra danh tính máy chủ. Không có API công khai để gửi nội dung email tùy ý. Tài khoản đăng ký, gửi lại mã và khôi phục tiếp tục được giới hạn theo IP/email.

## Nối điện thoại Android

Mở project `CODE/FoodRescueApp`. Build debug với địa chỉ API của môi trường đang chạy:

```powershell
./gradlew.bat :app:assembleDebug '-Pfoodrescue.apiBaseUrl=http://127.0.0.1:8080/'
adb -s <device> reverse tcp:8080 tcp:8080
```

Nếu chạy API ở cổng 8081, đổi cả hai cổng và URL thành 8081. ADB reverse cần thiết lập lại khi điện thoại kết nối lại. Với emulator có thể dùng mặc định `http://10.0.2.2:8080/`. Release cần `foodrescue.releaseApiBaseUrl` là URL HTTPS thật; mặc định `.invalid` cố ý không trỏ đến dịch vụ bất kỳ.

App mở luồng tài khoản thật. Sau đăng nhập, hồ sơ được tải từ API; sửa thông tin và đăng xuất gọi API. Phiên lưu mã hóa bằng Android Keystore, không nằm trong backup. Nút “Xem giao diện thử” chỉ có trong debug; đơn, thanh toán và catalog của bản thử vẫn là dữ liệu mô phỏng, tách khỏi phiên thật.

## Hợp đồng API

Import [openapi.json](openapi.json) vào Postman hoặc công cụ hỗ trợ OpenAPI.

| Endpoint | Vai trò |
|---|---|
| POST `/api/v1/auth/register` | Tạo khách hàng chờ xác minh |
| POST `/api/v1/auth/email-verifications` | Yêu cầu mã xác minh email |
| POST `/api/v1/auth/challenges/{id}/verify` | Xác minh OTP đúng mục đích |
| POST `/api/v1/auth/challenges/{id}/resend` | Gửi lại và vô hiệu mã cũ |
| POST `/api/v1/auth/login` | Cấp access/refresh token |
| POST `/api/v1/auth/refresh` | Xoay vòng refresh token |
| POST `/api/v1/auth/logout` | Thu hồi phiên thiết bị hiện tại |
| POST `/api/v1/auth/password-reset-requests` | Bắt đầu khôi phục mật khẩu |
| POST `/api/v1/auth/password-resets` | Đổi mật khẩu bằng quyền đã xác minh |
| GET/PATCH `/api/v1/me` | Đọc/sửa hồ sơ của chính người đăng nhập |

Họ tên 2–120 ký tự, email tối đa 254 ký tự được chuẩn hóa chữ thường, mật khẩu 8–128 ký tự (cho phép cụm từ/Unicode). Không trim mật khẩu; không bắt buộc chữ hoa, số hoặc ký tự đặc biệt. Số điện thoại không bắt buộc; nhận 10 số bắt đầu 0 hoặc +84 và 9 số. Sửa email/vai trò không thuộc endpoint hồ sơ.

OTP 6 số, tối đa 10 phút; gửi lại sau 60 giây; sai 5 lần thì cần mã mới. Ngoài giới hạn mỗi challenge còn có giới hạn email/IP lưu trong PostgreSQL. Thời gian API trả về là **số giây còn lại**, để Android dùng đồng hồ đếm thời gian thay vì nhầm với ngày giờ UTC.

Access token sống 15 phút, nhưng vẫn kiểm tra trạng thái tài khoản/phiên mỗi request. Refresh token ngẫu nhiên lưu hash tại server, xoay vòng và có hạn tuyệt đối 7 ngày. Dùng lại refresh token cũ thu hồi cả nhóm phiên; Android khóa đồng thời việc refresh. Nếu phản hồi refresh bị mất sau khi server đã đổi token, người dùng có thể cần đăng nhập lại; không tự thử lại POST nhạy cảm một cách âm thầm.

Quyền đặt lại mật khẩu chỉ cấp sau OTP mục đích RESET_PASSWORD, dùng một lần trong 10 phút. Đổi mật khẩu thu hồi tất cả phiên. Đăng xuất chỉ thu hồi nhóm phiên thiết bị hiện tại. Phân quyền lấy từ database, không nhận account ID/vai trò từ request hồ sơ. Các endpoint chưa triển khai tiếp tục bị chặn.

Không lưu mật khẩu/OTP/token thô vào log. Argon2id băm mật khẩu; OTP băm HMAC với khóa server riêng để tránh dò 1 triệu mã từ bản database bị lộ. Khóa ký JWT và khóa OTP phải được giữ ngoài database. Email gửi đồng bộ với timeout 5 giây cho mỗi giai đoạn kết nối/đọc/ghi; lỗi gửi sẽ rollback thao tác và báo 503. Log SMTP chỉ gồm mã truy vết và loại lỗi, không ghi phản hồi máy chủ, địa chỉ hoặc bí mật. Khi tải lớn, cân nhắc hàng đợi email bền vững có bảo vệ nội dung OTP.

## Kiểm thử

```powershell
./gradlew.bat test
# DB_URL phải trỏ vào database riêng, tên kết thúc bằng _test.
./gradlew.bat integrationTest
```

Integration test **xóa dữ liệu trong database kiểm thử**, kiểm tra tên database trước khi xóa. Không đặt DB_URL thành database dự án/production. Khóa test công khai chỉ nằm trong `src/test/resources`, không đóng gói trong JAR ứng dụng. Test tích hợp giả lập SMTP để kiểm tra cả lỗi gửi; kiểm thử thực tế bằng Mailpit kiểm tra đường SMTP thật riêng.

Các kiểm thử bao phủ đăng ký trùng/đồng thời, mã sai/hết hạn/gửi lại/dùng lại, khôi phục, JWT bị sửa, tài khoản bị khóa, refresh đồng thời/replay, đăng xuất, giới hạn yêu cầu và quyền hồ sơ. Android có test nối Retrofit, lưu/khôi phục phiên, refresh nối tiếp, lỗi mạng/server và quy đổi thời gian OTP.

Trước production: cấu hình HTTPS, SMTP thật, quản lý/luân chuyển khóa, tài khoản database ít quyền, backup và giám sát. Hiện không tin `X-Forwarded-For` do client gửi; khi đặt sau reverse proxy phải cấu hình rõ proxy được tin cậy và giới hạn ở gateway. Chính sách pháp lý trong app vẫn là dự thảo.

Tham chiếu: [Spring Security JWT](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html), [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html), [Mailpit Docker](https://mailpit.axllent.org/docs/install/docker/).
