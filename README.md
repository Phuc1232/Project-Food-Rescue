# FoodRescue — Ứng dụng giảm lãng phí thực phẩm

FoodRescue kết nối khách hàng với cửa hàng có thực phẩm còn tốt thông qua các hộp **Surprise Box**. Khách đặt hộp trên ứng dụng và đến cửa hàng nhận trong khung giờ đã chọn.

> **Ngôn ngữ:** Kotlin
>
> **Ứng dụng:** Jetpack Compose · MVVM · Retrofit · Hilt
>
> **Máy chủ:** Spring Boot · PostgreSQL
>
> **Môi trường:** Android Studio · JDK 21 · Android SDK 36 (minSdk 26)

## Cấu trúc dự án

```text
Project-Food-Rescue/
├── CODE/
│   ├── FoodRescueApp/           # Dự án mở bằng Android Studio
│   │   ├── app/src/main/
│   │   │   ├── java/vn/foodrescue/app/
│   │   │   │   ├── data/       # API và repository
│   │   │   │   ├── ui/         # Giao diện và ViewModel
│   │   │   │   └── di/         # Cấu hình dependency
│   │   │   └── res/            # Tài nguyên Android
│   │   ├── app/src/test/       # Kiểm thử ứng dụng
│   │   └── gradle/             # Gradle Wrapper
│   ├── FoodRescueApi/          # Dự án mở bằng IntelliJ IDEA
│   │   └── src/
│   │       ├── main/kotlin/vn/foodrescue/
│   │       │   ├── controller/ # Tiếp nhận request
│   │       │   ├── service/    # Xử lý nghiệp vụ
│   │       │   ├── repository/ # Truy cập dữ liệu
│   │       │   ├── dto/        # Dữ liệu request/response
│   │       │   ├── config/     # Cấu hình hệ thống
│   │       │   └── exception/  # Xử lý lỗi
│   │       ├── main/resources/ # Cấu hình và migration SQL
│   │       └── test/           # Kiểm thử API và cơ sở dữ liệu
│   ├── compose.yml            # API, PostgreSQL và hộp thư thử
│   └── .env.example           # Cấu hình môi trường mẫu
├── DOCX/                      # Tài liệu và báo cáo
├── PPTX/                      # Slide thuyết trình
├── EXTRA/                     # Tài nguyên bổ sung
└── README.md
```

## Phạm vi chức năng

Phạm vi nghiệp vụ của hệ thống:

| Người sử dụng | Chức năng chính |
| --- | --- |
| Khách hàng | Tìm hộp, đặt hàng, thanh toán, theo dõi đơn và gửi khiếu nại |
| Chủ cửa hàng | Quản lý cửa hàng, nhân viên, thực đơn, hộp và đơn hàng |
| Nhân viên | Đóng gói, bàn giao hộp và ghi nhận khách không đến nhận |
| Quản trị viên | Xét duyệt cửa hàng, xử lý khiếu nại, hoàn tiền và đối soát |

**Đã có:** API tài khoản khách hàng và Android kết nối thật: đăng ký, xác minh email, đăng nhập, khôi phục mật khẩu, xem/sửa hồ sơ và đăng xuất; migration, kiểm thử và GitHub Actions.

Khám phá, bản đồ, đặt hộp, thanh toán, đơn hàng và khiếu nại đã có giao diện thử, chưa nối API nghiệp vụ. Chế độ thử chỉ có trong debug và tách khỏi tài khoản thật. Email hỗ trợ Mailpit để phát triển hoặc Gmail/SMTP thật; xem [cấu hình email](CODE/FoodRescueApi/README.md#gửi-email-thật).

## Cài đặt và chạy

### 1. Máy chủ và cơ sở dữ liệu

Cài Docker Desktop và bật Linux containers. Tại thư mục dự án, chạy bằng PowerShell:

```powershell
cd CODE
Copy-Item .env.example .env
# Điền mật khẩu database và hai khóa ngẫu nhiên theo hướng dẫn API bên dưới.
docker compose up --build -d
```

Kiểm tra API tại `http://localhost:8080/api/v1/system/status`. Kết quả có `status: UP` và `database: UP` khi hệ thống sẵn sàng.

Xem [cấu hình khóa, email và API](CODE/FoodRescueApi/README.md). Mở `http://localhost:8025` để đọc OTP trong môi trường phát triển.

### 2. Ứng dụng Android

1. Mở `CODE/FoodRescueApp` trong Android Studio.
2. Chọn Gradle JDK 21, cài Android SDK 36 và Build Tools 35.0.0.
3. Chờ Gradle sync, chọn Android Emulator API 26 trở lên và nhấn **Run**.

App mở luồng tài khoản kết nối API; máy ảo mặc định dùng `http://10.0.2.2:8080/`. Điện thoại thật dùng ADB reverse và URL theo hướng dẫn API. “Xem giao diện thử” trong debug vẫn dùng được khi chưa chạy backend.

## Tài liệu

| Tài liệu | Nội dung |
| --- | --- |
| [Hướng dẫn phát triển](DOCX/Huong_Dan_Phat_Trien.md) | Tổ chức code, chạy trong IDE, kiểm thử và quy tắc đóng góp |

Không đưa `.env`, `local.properties`, mật khẩu hoặc khóa ký ứng dụng vào Git. Nhóm phát triển trên nhánh riêng và review PR trước khi merge vào `master`.
