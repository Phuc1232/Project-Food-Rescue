# Giao diện khách hàng — bản đầu

## Đã có

- Màn Khám phá, tìm cửa hàng/hộp có hoặc không dấu, lọc theo loại thực phẩm.
- Thẻ hộp có ảnh, thời gian nhận, giá bán và giá trị tham chiếu.
- Lưu/bỏ lưu; màn Đã lưu và trạng thái không có kết quả.
- Chi tiết hộp, thông tin dị ứng, giờ nhận và địa chỉ.
- Theme sáng cố định theo bảng màu FoodRescue; không dùng Dynamic Color.

Đây là bản mẫu với ba cửa hàng hư cấu. Không yêu cầu đăng nhập để xem trước bản mẫu; khi triển khai API thật phải áp dụng yêu cầu đăng nhập của dự án. Không gửi yêu cầu đặt hàng/thanh toán. Các giá trị “Hôm nay”, khoảng cách và số lượng chỉ là minh họa. Danh sách đã lưu dùng saved state của giao diện, chưa lưu vào tài khoản hoặc cơ sở dữ liệu.

## Hai thành viên dùng chung

| Vị trí | Trách nhiệm |
| --- | --- |
| `ui/theme` | Màu, font và theme; thống nhất trước khi sửa |
| `ui/components` | Nút chính, nhãn, icon và thẻ Surprise Box |
| `ui/customer/CustomerScreen.kt` | Khám phá, đã lưu và chi tiết hộp |
| `data/model/SurpriseBox.kt` | Mô hình hiển thị, tìm kiếm, định dạng giá |
| `data/sample/SampleBoxes.kt` | Dữ liệu mẫu, thay bằng repository/API khi có backend |

Không gọi API trực tiếp trong Composable. Khi nối API: đưa trạng thái loading/error/data vào ViewModel, giữ backend làm nguồn quyết định giá và tồn kho. MainActivity hiện mở giao diện khách hàng; API kiểm tra hệ thống vẫn tồn tại ở backend, màn kiểm tra kết nối cũ không còn là màn mặc định.

## Kiểm tra giao diện khi có điện thoại

1. Mở app: thấy Khám phá và ba hộp mẫu.
2. Tìm “tiem banh moc”: chỉ còn Tiệm Bánh Mộc. Xóa từ khóa để trở lại.
3. Chọn “Bữa ăn”: chỉ còn Bếp Nhà Xanh. Kết hợp từ khóa không phù hợp để xem trạng thái rỗng.
4. Chạm trái tim, mở Đã lưu, bỏ lưu và kiểm tra màn trống.
5. Mở hộp, xem chi tiết, quay lại; kiểm tra danh sách và bộ lọc vẫn giữ nguyên.
6. Nút đặt hộp bị vô hiệu hóa. Không có bước thanh toán giả.
7. Kiểm tra trên màn hình nhỏ và cỡ chữ lớn; nội dung chi tiết cuộn được.

## Ảnh minh họa

Ảnh lưu cục bộ để bản mẫu không cần mạng tải ảnh; không đại diện cho thành phần hộp thực tế. Nguồn Unsplash:

- Bánh: https://images.unsplash.com/photo-1509440159596-0249088772ff
- Món ăn: https://images.unsplash.com/photo-1512621776951-a57141f2eefd
- Thực phẩm: https://images.unsplash.com/photo-1542838132-92c53300491e

Thay bằng ảnh cửa hàng được phép sử dụng khi triển khai dữ liệu thật.
