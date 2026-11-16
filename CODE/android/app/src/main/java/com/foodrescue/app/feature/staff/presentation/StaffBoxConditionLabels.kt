package com.foodrescue.app.feature.staff.presentation

import com.foodrescue.app.feature.surprisebox.domain.*

internal val BoxCondition.staffLabel: String get() = when (this) {
    BoxCondition.READY -> "Sẵn sàng"
    BoxCondition.RECHECK_REQUIRED -> "Chờ kiểm tra lại"
    BoxCondition.REJECTED -> "Không đạt"
    BoxCondition.EXPIRED -> "Hết hạn"
    BoxCondition.DELIVERED -> "Đã giao"
}
internal val InspectionReason.staffLabel: String get() = when (this) {
    InspectionReason.FOOD_QUALITY -> "Chất lượng món không đạt"
    InspectionReason.PACKAGING -> "Bao bì không nguyên vẹn"
    InspectionReason.STORAGE -> "Bảo quản không phù hợp"
    InspectionReason.ALLERGENS -> "Sai thông tin dị ứng"
    InspectionReason.EXPIRED -> "Đã quá hạn sử dụng"
    InspectionReason.OTHER -> "Lý do khác"
}
internal val InspectionIssue.staffMessage: String get() = when (this) {
    InspectionIssue.ACCESS -> "Bạn không còn quyền kiểm tra hộp này."
    InspectionIssue.BOX -> "Hộp đã bàn giao hoặc đã bị loại, không thể cập nhật."
    InspectionIssue.STALE -> "Hộp vừa được kiểm tra bởi nhân viên khác. Xem lại trước khi lưu."
    InspectionIssue.REQUEST -> "Phiếu kiểm tra không còn hợp lệ. Mở lại hộp để tiếp tục."
    InspectionIssue.RESULT -> "Chọn kết quả kiểm tra."
    InspectionIssue.REASON -> "Chọn lý do hộp không đạt."
    InspectionIssue.NOTE -> "Lý do khác cần ghi chú từ 10 ký tự, tối đa 500 ký tự."
    InspectionIssue.CHECKS -> "Xác nhận đủ bốn mục kiểm tra."
    InspectionIssue.EXPIRY -> "Hộp đã hết hạn sử dụng, không thể xác nhận đạt yêu cầu."
}
