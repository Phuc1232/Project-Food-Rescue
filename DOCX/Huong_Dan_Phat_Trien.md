# Hướng dẫn phát triển

## Tổ chức mã nguồn

Android dùng MVVM. Các package nằm trong `vn.foodrescue.app`:

| Package | Nội dung |
| --- | --- |
| `ui` | Màn hình, ViewModel và trạng thái giao diện; chia theo chức năng |
| `data/remote` | Retrofit API và dữ liệu nhận từ backend |
| `data/repository` | Truy xuất dữ liệu và xử lý lỗi kết nối |
| `di` | Cấu hình cung cấp dependency bằng Hilt |

Backend dùng kiến trúc phân lớp. Các package nằm trong `vn.foodrescue`:

| Package | Nội dung |
| --- | --- |
| `controller` | Nhận request và trả response |
| `service` | Xử lý nghiệp vụ |
| `repository` | Truy cập cơ sở dữ liệu |
| `dto` | Dữ liệu trao đổi qua API |
| `config` | Cấu hình ứng dụng và bảo mật |
| `exception` | Xử lý lỗi API |

Tạo entity và các lớp nghiệp vụ khi triển khai chức năng cụ thể. Migration nằm tại `CODE/FoodRescueApi/src/main/resources/db/migration`; không sửa migration đã dùng chung, hãy thêm phiên bản mới.

## Chạy backend trong IDE

Từ `CODE`, chạy `docker compose up -d postgres`. Mở `CODE/FoodRescueApi` bằng IntelliJ IDEA, chọn JDK 21, đặt biến môi trường `DB_PASSWORD` trùng `POSTGRES_PASSWORD` trong `.env` và chạy `FoodRescueApplication`.

Không chạy đồng thời backend trong IDE và Docker trên cổng 8080. Nếu cần, dùng `docker compose stop backend`.

Android Emulator gọi `http://10.0.2.2:8080/`. Với điện thoại qua USB: chạy `adb reverse tcp:8080 tcp:8080`, rồi đặt `foodrescue.apiBaseUrl=http://127.0.0.1:8080/` trong Gradle properties cục bộ. Bản release cần API HTTPS riêng.

## Kiểm tra

Trong `CODE/FoodRescueApi`:

```powershell
.\gradlew.bat test bootJar
```

Kiểm thử cơ sở dữ liệu chạy bằng `integrationTest`; cần PostgreSQL và các biến `DB_URL`, `DB_USER`, `DB_PASSWORD` trỏ đến cơ sở dữ liệu kiểm thử riêng. GitHub Actions đã cấu hình bước này.

Trong `CODE/FoodRescueApp`:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Linux/macOS dùng `./gradlew` thay `gradlew.bat`, và `cp` thay `Copy-Item`.

## Làm việc nhóm

- Tạo nhánh `feature/ten-chuc-nang` hoặc `fix/ten-loi`; mở PR vào `master`.
- Commit theo mẫu `feat: add login`, `fix: validate order quantity`, `chore: update build configuration`.
- Thống nhất request/response trước khi triển khai app và API.
- Không đưa `.env`, `local.properties`, mật khẩu hoặc khóa ký ứng dụng vào Git.
- Chạy kiểm tra liên quan và review trước khi merge.

Hiện chỉ API status và health được truy cập công khai; các API nghiệp vụ bị chặn cho đến khi triển khai xác thực và phân quyền. Docker Compose phục vụ phát triển cục bộ.
