package com.foodrescue.app.feature.staff.presentation

import com.foodrescue.app.feature.staff.domain.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val EmployeePermission.label get() = when (this) {
    EmployeePermission.VIEW_ORDERS -> "Xem đơn hàng"
    EmployeePermission.PREPARE_BOXES -> "Chuẩn bị hộp"
    EmployeePermission.HAND_OVER -> "Bàn giao hộp"
    EmployeePermission.OPERATE_SALES -> "Điều hành đợt bán"
}
internal val EmployeePermission.description get() = when (this) {
    EmployeePermission.VIEW_ORDERS -> "Xem danh sách, chi tiết và giờ nhận của đơn tại cửa hàng."
    EmployeePermission.PREPARE_BOXES -> "Xem món và chuẩn bị hộp cho đơn. Bao gồm quyền xem đơn hàng."
    EmployeePermission.HAND_OVER -> "Kiểm tra mã nhận và xác nhận giao hộp. Bao gồm quyền xem đơn hàng."
    EmployeePermission.OPERATE_SALES -> "Theo dõi số lượng, tạm dừng và tiếp tục đợt bán đã được mở."
}
internal val EmployeeStatus.label get() = if (this == EmployeeStatus.ACTIVE) "Đang hoạt động" else "Đã thu hồi"
internal val EmployeeFilter.label get() = when (this) { EmployeeFilter.ALL -> "Tất cả"; EmployeeFilter.ACTIVE -> "Đang hoạt động"; EmployeeFilter.REVOKED -> "Đã thu hồi" }
internal val EmployeeIssue.message get() = when (this) {
    EmployeeIssue.NAME -> "Nhập họ tên từ 2 đến 120 ký tự."
    EmployeeIssue.EMAIL -> "Nhập địa chỉ email hợp lệ."
    EmployeeIssue.PHONE -> "Nhập số điện thoại 10 chữ số hoặc bắt đầu bằng +84."
    EmployeeIssue.PERMISSIONS -> "Chọn ít nhất một quyền và giữ quyền xem đơn khi chuẩn bị hoặc bàn giao hộp."
    EmployeeIssue.DUPLICATE -> "Email đã có tài khoản nhân viên. Hãy kiểm tra hồ sơ hiện có, kể cả tài khoản đã thu hồi."
    EmployeeIssue.OWNER_EMAIL -> "Email này thuộc chủ cửa hàng. Hãy dùng email riêng của nhân viên."
    EmployeeIssue.IDENTITY -> "Không thể đổi email của tài khoản đã tạo."
    EmployeeIssue.STALE -> "Hồ sơ đã thay đổi. Quay lại chi tiết, tải lại và mở chỉnh sửa để dùng thông tin mới nhất."
    EmployeeIssue.NOT_FOUND -> "Không tìm thấy nhân viên trong cửa hàng này."
    EmployeeIssue.STORE_ACCESS -> "Cửa hàng cần được duyệt để quản lý nhân viên."
    EmployeeIssue.REASON -> "Nhập lý do thu hồi từ 2 đến 300 ký tự."
}
internal fun employeeRole(permissions: Set<EmployeePermission>): String = when (permissions) {
    pickupPermissions -> "Nhận hàng"
    operationsPermissions -> "Vận hành"
    else -> "Tùy chỉnh"
}
internal fun employeeDate(at: Long): String = SimpleDateFormat("HH:mm · dd/MM/yyyy", Locale.forLanguageTag("vi-VN")).format(Date(at))
internal val EmployeeEventType.label get() = when (this) {
    EmployeeEventType.CREATED -> "Tạo tài khoản"
    EmployeeEventType.UPDATED -> "Cập nhật hồ sơ và quyền"
    EmployeeEventType.REVOKED -> "Thu hồi truy cập"
    EmployeeEventType.RESTORED -> "Khôi phục truy cập"
}
