# Luồng tài khoản — bản giao diện

Hiện chưa có API xác thực. Bản debug dùng `PreviewAuthGateway`; không gửi email, lưu tài khoản, lưu mật khẩu hoặc cấp phiên thật. Bản release luôn báo chưa kết nối dịch vụ, không chứa gateway mô phỏng.

- Đăng ký: họ tên, email, mật khẩu và nhập lại mật khẩu → mã email → hoàn tất → đăng nhập.
- Khôi phục: email → mã xác minh → mật khẩu mới và nhập lại → hoàn tất → đăng nhập.
- Mật khẩu mới: 15–128 ký tự; không ép chữ hoa, chữ số hay ký tự đặc biệt. Backend phải áp dụng cùng chính sách khi tích hợp.
- Mã thử hiện ngay dưới ô mã trong bản debug. Mã có hạn 3 phút, gửi lại sau 30 giây; mã mới thay mã cũ. Nhập sai 5 lần cần yêu cầu mã mới.
- Mục **Tình huống thử** cuối form cho phép xem trạng thái mất mạng, sai thông tin, email chưa xác minh và email đã sử dụng. Kết quả thành công chỉ mô phỏng giao diện, không chứng minh tài khoản tồn tại.
- Liên kết điều khoản/quyền riêng tư mở nội dung **dự thảo**, cần nhóm duyệt và hoàn thiện trước phát hành.
- ViewModel giữ trạng thái qua xoay màn hình; không ghi thông tin xác thực vào saved state, log hoặc đĩa. Đóng tiến trình sẽ bắt đầu lại luồng.

Khi nối backend, thay provider trong `src/release` và giữ `AuthGateway` làm ranh giới. Máy chủ phải kiểm tra quyền, mã/chứng cứ xác minh, thời hạn, giới hạn thử và phiên; kiểm tra trong app không thay thế kiểm tra ở máy chủ.

Kiểm tra tự động: `gradlew.bat :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug`.
