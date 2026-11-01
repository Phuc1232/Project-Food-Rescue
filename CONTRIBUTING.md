# Cách làm việc của nhóm

1. Cập nhật nhánh mặc định `master`, tạo nhánh theo công việc, ví dụ `feature/login` hoặc `fix/order-validation`.
2. Chốt hợp đồng API, quyền truy cập và trạng thái nghiệp vụ trước khi Android/backend triển khai song song.
3. Mỗi PR chỉ tập trung một chức năng. Nêu phần thay đổi, cách chạy và tình huống đã kiểm tra.
4. Chạy kiểm tra liên quan trước khi mở PR; GitHub Actions phải đạt trước khi merge.
5. Không sửa migration đã được merge và dùng chung; thêm `V2__...sql`, `V3__...sql` theo thứ tự đã thống nhất. Không dùng Hibernate `ddl-auto=update` để thay migration.
6. Không commit `.env`, `local.properties`, mật khẩu, access token, keystore hay service-account JSON.

## Hoàn thành một chức năng nghĩa là gì?

- Android có trạng thái tải/rỗng/lỗi và luồng thành công.
- API kiểm tra định dạng, vai trò và phạm vi cửa hàng ở backend.
- Nghiệp vụ có test cho lỗi quan trọng, đặc biệt tiền, hết hạn và xử lý trùng.
- DTO và tài liệu API đã cập nhật; chạy được từ app tới CSDL.
- Có người khác trong nhóm review.
