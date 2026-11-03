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

## Các luồng khách hàng sau màn mở đầu

Trong bản debug, chọn **Xem thử**. Có thể thử trọn luồng tìm hộp → chi tiết → đặt số lượng → chọn trả toàn bộ/cọc → thanh toán thử → chi tiết đơn → hủy hoặc khiếu nại.

- **Tài khoản:** sửa họ tên/số điện thoại, kiểm tra đầu vào và lưu hồ sơ thử trên thiết bị. Email là mẫu, chưa có đổi email thật. Đăng xuất xóa hồ sơ và đơn thử, trở về màn chào.
- **Đặt hộp:** giữ 10 phút, kiểm tra lại số lượng, chốt giá và chính sách cho mỗi đơn. Đợt mẫu chuyển sang ngày tiếp theo nếu giờ nhận không còn phù hợp. Tỷ lệ cọc 30% chỉ là tham số minh họa trong `OrderPolicy`, chưa phải chính sách phát hành.
- **Thanh toán:** bảng chọn có VNPAY-QR, ví MoMo, thẻ ATM nội địa và Visa/Mastercard. Đây là lựa chọn giao diện mô phỏng, chưa tích hợp các nhà cung cấp; không nhập thẻ, tạo QR thu tiền hoặc mở ứng dụng ngân hàng. Màn xác nhận tách rõ trả toàn bộ/đặt cọc, khoản trả ngay và khoản còn lại. Màn kết quả phân biệt đặt cọc thành công với thanh toán toàn bộ. Mở **Tùy chọn bản thử** để thử thành công, thất bại, chờ xác nhận và tiền đến muộn. Khi chưa rõ kết quả, không cho tạo lượt thanh toán thứ hai. Nút dừng có xác nhận trước khi giải phóng hộp.
- **Đơn hàng:** danh sách đang thực hiện/lịch sử, mã nhận khi đơn đã xác nhận, số tiền thực thu/còn phải trả/được hoàn/đã hoàn/khấu trừ. Hết giờ nhận chỉ chuyển chờ xác minh, không tự kết luận No-show. Có nút thêm đơn mẫu để xem hoàn tất, hủy/hoàn và No-show.
- **Hủy:** trước giờ nhận hoàn khoản đã thu qua ứng dụng; sau giờ nhận cần xem xét, không hứa mức hoàn. Kết quả hủy và kết quả hoàn được theo dõi riêng.
- **Khiếu nại:** chọn lý do, nội dung 20–2.000 ký tự và tối đa 5 ảnh JPG/PNG/WebP, mỗi ảnh không quá 10 MB. Ảnh lưu dưới dạng quyền đọc URI trên thiết bị; chưa tải lên máy chủ. No-show dùng mốc 48 giờ từ khi nhận thông báo; quá hạn vẫn cho gửi xem xét.
- **Công cụ kiểm tra bản trải nghiệm** trong chi tiết đơn giả lập kết quả duyệt hủy, hoàn tiền và khiếu nại. Đây không phải quyền dành cho khách hàng khi phát hành.

Hồ sơ và đơn thử dùng vùng lưu trữ `customer_preview_v1`, tách khỏi luồng xác thực. `CustomerStorage` bản release không cung cấp dữ liệu mô phỏng; `CustomerApp` chặn truy cập bản thử ngoài debug. Backend cần triển khai xác thực, giao dịch tồn kho, chính sách, webhook đã xác minh, đối soát, lưu tệp và quyền truy cập trước khi có giao dịch thật. Các thử nghiệm trạng thái nằm trong `CustomerOrdersTest`.
