# FoodRescue

Ứng dụng Android giúp cửa hàng bán các hộp thực phẩm còn tốt, giảm lãng phí. Nhóm phát triển theo kiến trúc Android MVVM và backend Spring Boot chia mô-đun nghiệp vụ.

## Trạng thái bộ khung

Đã có **một luồng kết nối thật**: app Android → REST API → PostgreSQL. Khi backend hoặc CSDL chưa chạy, app hiển thị lỗi và cho phép thử lại.

- Android: Kotlin, Jetpack Compose, ViewModel/StateFlow, Hilt, Retrofit.
- Backend: Kotlin, Spring Boot, Spring Security, JPA/JDBC, Flyway.
- CSDL: migration khởi tạo 33 bảng theo ERD, chưa có dữ liệu khách hàng hoặc giao dịch.
- Môi trường: Docker Compose cho PostgreSQL và backend; Gradle Wrapper cho cả hai dự án.
- Kiểm tra: unit test, kiểm thử migration trên PostgreSQL và build Android trong GitHub Actions.

**Chưa triển khai:** đăng nhập/JWT, phân quyền theo cửa hàng, màn hình nghiệp vụ, giữ hộp, thanh toán, thông báo, bản đồ và lưu tệp. API nghiệp vụ mặc định bị chặn. Chỉ `GET /api/v1/system/status` và `GET /actuator/health` được mở để kiểm tra hệ thống. Đây là bộ khung phát triển, chưa phải ứng dụng giao dịch hoàn chỉnh.

## Cấu trúc repository

```text
CODE/
  android/           # Mở thư mục này bằng Android Studio
  backend/           # Spring Boot; chạy bằng JDK 21
  infrastructure/    # Docker Compose và .env.example
DOCX/
  development/       # Hướng dẫn chạy, phân công, API, kiểm thử
EXTRA/               # Giữ nguyên thư mục của nhóm
PPTX/                # Giữ nguyên thư mục của nhóm
```

## Bắt đầu trong 3 bước

Cài Git, Docker Desktop với Linux containers, Android Studio, JDK 21, Android SDK Platform 36 và Build Tools 35.0.0. Docker Desktop phải đang chạy.

**1. Chạy PostgreSQL và backend** — từ thư mục repo, PowerShell:

```powershell
cd CODE/infrastructure
Copy-Item .env.example .env
docker compose up --build -d
Invoke-RestMethod http://localhost:8080/api/v1/system/status
```

Kết quả mong đợi: `service=FoodRescue`, `status=UP`, `database=UP`. Lần đầu cần tải dependencies/images. Nếu chưa sẵn sàng, xem `docker compose logs -f backend` rồi thử lại. `.env` chỉ dùng cục bộ và đã được Git bỏ qua; thay giá trị mẫu nếu đưa ra môi trường dùng chung.

**2. Chạy Android** — mở `CODE/android` bằng Android Studio, chọn Gradle JDK 21, đợi sync, chọn emulator API 26 trở lên và nhấn Run. App debug mặc định gọi `http://10.0.2.2:8080/` (máy tính từ Android Emulator).

**3. Kiểm tra** — màn hình FoodRescue báo đã kết nối backend và PostgreSQL. Dừng backend rồi bấm “Kiểm tra lại” để kiểm tra thông báo lỗi.

Linux/macOS thay `Copy-Item` bằng `cp`, dùng `./gradlew` thay `gradlew.bat`.

## Tài liệu cho từng thành viên

- [Hướng dẫn phát triển và chia việc](DOCX/development/GETTING_STARTED.md)
- [Kiến trúc, phạm vi bộ khung](DOCX/development/ARCHITECTURE.md)
- [API đang hoạt động — OpenAPI](DOCX/development/openapi.yml)
- [Quy tắc đóng góp](CONTRIBUTING.md)

Không thêm mật khẩu thật, token hoặc file khóa vào Git. Các tích hợp bên ngoài chỉ được thêm khi có cấu hình riêng cho từng môi trường.
