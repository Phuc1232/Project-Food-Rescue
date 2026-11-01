# identity

Tài khoản, đăng nhập và phiên. Chưa có API đăng nhập; không mở quyền cho endpoint nghiệp vụ trước khi có xác thực.

Khi triển khai, tạo `api/`, `application/`, `domain/`, `persistence/` theo mẫu mô-đun `system`. Chỉ gọi mô-đun khác qua dịch vụ công khai; không truy cập Repository của nhau. Chưa có chức năng nghiệp vụ được triển khai trong thư mục này.
