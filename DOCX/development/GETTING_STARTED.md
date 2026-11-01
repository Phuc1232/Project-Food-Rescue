# Hướng dẫn cho thành viên

## Công cụ và phiên bản đã cố định

- JDK 21 cho cả Gradle Android và backend. Nếu máy có nhiều JDK, kiểm tra `java -version` và `gradlew.bat --version`.
- Gradle 8.14.3 qua Wrapper, không cần cài Gradle toàn máy.
- Kotlin 2.2.21; Spring Boot 3.5.16; Android Gradle Plugin 8.13.2.
- Android compile/target SDK 36; min SDK 26; Build Tools 35.0.0 do AGP chọn mặc định.
- PostgreSQL 17 qua Docker Compose.

## Chạy backend trong IDE để sửa code nhanh

Chạy riêng database từ `CODE/infrastructure` sau khi tạo `.env`:

```powershell
docker compose up -d postgres
```

Mở terminal tại `CODE/backend`:

```powershell
$env:DB_PASSWORD='foodrescue_local_only' # thay cho khớp .env local
$env:DB_USER='foodrescue'
$env:DB_URL='jdbc:postgresql://localhost:5432/foodrescue'
.\gradlew.bat bootRun
```

File `.env` của Docker Compose không tự được nạp vào terminal/IDE chạy Gradle. Không chạy container backend và IDE backend cùng cổng 8080; dùng `docker compose stop backend` trước khi chuyển sang IDE.

Flyway tự chạy khi backend khởi động. CSDL hiện chỉ chứa cấu trúc, không có tài khoản đăng nhập hay dữ liệu giao dịch mẫu. File V1 không bao gồm giới hạn MVP một chủ chỉ có một cửa hàng được duyệt. Nếu nhóm chốt giới hạn đó, chuyển script trong `CODE/backend/database/optional` thành migration mới sau khi kiểm tra dữ liệu.

## Chạy Android bằng terminal

Tại `CODE/android`, cấu hình Android SDK bằng Android Studio (`local.properties`) hoặc biến `ANDROID_HOME` rồi chạy:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

Emulator dùng `http://10.0.2.2:8080/`. Khi dùng điện thoại cắm USB:

```powershell
adb reverse tcp:8080 tcp:8080
.\gradlew.bat :app:installDebug -Pfoodrescue.apiBaseUrl=http://127.0.0.1:8080/
```

Debug chỉ cho HTTP tới loopback/emulator. Release dùng HTTPS và mặc định trỏ tên miền `.invalid`; phải cấu hình `-Pfoodrescue.releaseApiBaseUrl=https://.../` trước khi phát hành. URL phải có dấu `/` cuối. Chưa có signing release hoặc pipeline phát hành.

## Kiểm thử backend

```powershell
.\gradlew.bat test bootJar
# Cần PostgreSQL đang chạy và các biến DB_* đã cấu hình:
.\gradlew.bat integrationTest
```

Unit test không cần Docker. Integration test dùng CSDL trống dành riêng cho kiểm thử; Flyway tạo schema và test kiểm tra đủ 33 bảng, kết nối DB, FK chặn phiên mồ côi. Không chạy test trên CSDL thật chứa dữ liệu người dùng. GitHub Actions tự cung cấp PostgreSQL riêng cho job.

## Phân công theo trách nhiệm

| Phần việc | Người nhận sẽ làm gì tiếp? | Mẫu đã có |
|---|---|---|
| Android nền tảng | Điều hướng, đăng nhập, quản lý phiên, UI dùng chung | `feature/starter`, `core/network` |
| Backend nền tảng | Đăng nhập thật, cấp/đổi/thu hồi token, phân quyền theo cửa hàng | `shared/security` |
| Cửa hàng/hàng hóa | Hồ sơ, nhân viên, món ăn, hộp và đợt bán | `store`, `catalog` |
| Đơn hàng | Giữ hộp, hết hạn, hủy, bàn giao và kiểm thử cạnh tranh | `ordering` |
| Thanh toán/tài chính | Sandbox, callback, chống trùng, hoàn tiền, đối soát | `payment`, `settlement` |
| Tích hợp/chất lượng | CI, dữ liệu demo, kiểm thử xuyên suốt, thông báo/tệp | `.github/workflows`, `notification`, `file` |

Các thư mục nghiệp vụ đang có README hướng dẫn, chưa có API giả hoặc CRUD đại trà. Gán tên thành viên vào bảng khi nhóm thống nhất phân công.

## Khắc phục nhanh

- `DB_PASSWORD` chưa có: đặt biến môi trường trong terminal/IDE hoặc chạy bằng Compose.
- Port đang dùng: dừng backend cũ hoặc đặt `API_PORT`; cập nhật URL app tương ứng.
- App không kết nối: kiểm tra API trên máy tính trước, sau đó kiểm tra emulator/adb reverse.
- Gradle báo Java không phù hợp: chọn JDK 21; JDK mặc định toàn máy có thể khác JDK của Android Studio.
- Không có Docker: vẫn build/unit test được; cần Docker hoặc PostgreSQL riêng để chạy luồng đầy đủ.
- Dừng local: `docker compose down` giữ volume. Không dùng `down -v` nếu muốn giữ dữ liệu.
