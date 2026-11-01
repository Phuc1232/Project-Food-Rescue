# FoodRescue

Ứng dụng Android kết nối khách hàng với cửa hàng bán thực phẩm còn tốt, góp phần giảm lãng phí.

## Công nghệ

- **Android:** Kotlin, Jetpack Compose, MVVM, Retrofit, Hilt.
- **Backend:** Kotlin, Spring Boot, PostgreSQL.
- **Công cụ:** Gradle, Docker Compose, GitHub Actions.

## Cấu trúc

```text
CODE/
├── android/       # Ứng dụng Android
├── backend/       # REST API và migration cơ sở dữ liệu
├── compose.yml    # Chạy backend và PostgreSQL
└── .env.example   # Cấu hình môi trường mẫu
docs/              # Hướng dẫn phát triển
DOCX/              # Báo cáo của nhóm
PPTX/              # Slide thuyết trình
EXTRA/             # Tài nguyên bổ sung
```

## Chạy dự án

Cần Docker Desktop, Android Studio, JDK 21 và Android SDK 36.

**1. Chạy backend và cơ sở dữ liệu** (PowerShell):

```powershell
cd CODE
Copy-Item .env.example .env
docker compose up --build -d
```

**2. Chạy app:** mở `CODE/android` bằng Android Studio, chọn Gradle JDK 21 và chạy trên Android Emulator (API 26 trở lên).

App hiển thị trạng thái kết nối máy chủ. API kiểm tra: `http://localhost:8080/api/v1/system/status`.

## Tiến độ

Bộ khung đã có kết nối Android → API → PostgreSQL, migration và kiểm thử tự động. Các chức năng đăng nhập, đặt hàng và thanh toán chưa được triển khai.

Xem [hướng dẫn phát triển](docs/development.md) để tổ chức code, chạy test và đóng góp.
