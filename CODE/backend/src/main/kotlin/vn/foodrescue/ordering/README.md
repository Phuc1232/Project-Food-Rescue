# ordering

Đặt hàng, giữ hộp, hủy, hết hạn và bàn giao. Transaction và khóa dữ liệu thuộc lớp application.

Khi triển khai, tạo `api/`, `application/`, `domain/`, `persistence/` theo mẫu mô-đun `system`. Chỉ gọi mô-đun khác qua dịch vụ công khai; không truy cập Repository của nhau. Chưa có chức năng nghiệp vụ được triển khai trong thư mục này.
