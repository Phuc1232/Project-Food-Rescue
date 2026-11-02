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
│   ├── compose.yml            # Chạy API và PostgreSQL
│   └── .env.example           # Cấu hình môi trường mẫu
├── DOCX/                      # Tài liệu và báo cáo
├── PPTX/                      # Slide thuyết trình
├── EXTRA/                     # Tài nguyên bổ sung
└── README.md
```

## Phạm vi chức năng

Các chức năng dưới đây là **phạm vi dự kiến**, chưa được triển khai trong bộ khung:

| Người sử dụng | Chức năng chính |
| --- | --- |
| Khách hàng | Tìm hộp, đặt hàng, thanh toán, theo dõi đơn và gửi khiếu nại |
| Chủ cửa hàng | Quản lý cửa hàng, nhân viên, thực đơn, hộp và đơn hàng |
| Nhân viên | Đóng gói, bàn giao hộp và ghi nhận khách không đến nhận |
| Quản trị viên | Xét duyệt cửa hàng, xử lý khiếu nại, hoàn tiền và đối soát |

**Đã có:** màn hình kiểm tra kết nối Android → API → PostgreSQL, migration cơ sở dữ liệu, kiểm thử và GitHub Actions.

## Cài đặt và chạy

### 1. Máy chủ và cơ sở dữ liệu

Cài Docker Desktop và bật Linux containers. Tại thư mục dự án, chạy bằng PowerShell:

```powershell
cd CODE
Copy-Item .env.example .env
docker compose up --build -d
```

Kiểm tra API tại `http://localhost:8080/api/v1/system/status`. Kết quả có `status: UP` và `database: UP` khi hệ thống sẵn sàng.

### 2. Ứng dụng Android

1. Mở `CODE/FoodRescueApp` trong Android Studio.
2. Chọn Gradle JDK 21, cài Android SDK 36 và Build Tools 35.0.0.
3. Chờ Gradle sync, chọn Android Emulator API 26 trở lên và nhấn **Run**.

App sử dụng `http://10.0.2.2:8080/` để kết nối máy chủ trên máy tính. Màn hình hiển thị kết quả kết nối và cho phép thử lại.

## Tài liệu

| Tài liệu | Nội dung |
| --- | --- |
| [Hướng dẫn phát triển](DOCX/Huong_Dan_Phat_Trien.md) | Tổ chức code, chạy trong IDE, kiểm thử và quy tắc đóng góp |

Không đưa `.env`, `local.properties`, mật khẩu hoặc khóa ký ứng dụng vào Git. Nhóm phát triển trên nhánh riêng và review PR trước khi merge vào `master`.
