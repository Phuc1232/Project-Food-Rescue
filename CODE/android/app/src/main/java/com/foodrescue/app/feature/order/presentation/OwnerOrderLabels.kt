package com.foodrescue.app.feature.order.presentation

import com.foodrescue.app.feature.order.domain.*

internal val OwnerCancellationReason.label: String get() = when (this) {
    OwnerCancellationReason.OUT_OF_STOCK -> "Không đủ thực phẩm"
    OwnerCancellationReason.FOOD_QUALITY -> "Không đảm bảo chất lượng"
    OwnerCancellationReason.STORE_CLOSED -> "Cửa hàng không thể mở cửa"
    OwnerCancellationReason.OTHER -> "Lý do khác"
}
internal val OwnerOrderFilter.label: String get() = when (this) {
    OwnerOrderFilter.ALL -> "Tất cả"
    OwnerOrderFilter.AWAITING_PICKUP -> "Chờ nhận"
    OwnerOrderFilter.OVERDUE -> "Quá giờ nhận"
    OwnerOrderFilter.UNPAID -> "Chờ thanh toán"
    OwnerOrderFilter.CANCELLATION_REQUESTED -> "Yêu cầu hủy"
    OwnerOrderFilter.COLLECTED -> "Đã nhận"
    OwnerOrderFilter.CLOSED -> "Đã đóng"
}
internal val OwnerPickupFilter.label: String get() = when (this) {
    OwnerPickupFilter.ALL -> "Mọi ngày nhận"
    OwnerPickupFilter.TODAY -> "Nhận hôm nay"
    OwnerPickupFilter.TOMORROW -> "Nhận ngày mai"
}
internal val OwnerCancellationIssue.message: String get() = when (this) {
    OwnerCancellationIssue.NOT_ELIGIBLE -> "Đơn không còn đủ điều kiện gửi yêu cầu hủy. Vui lòng tải lại."
    OwnerCancellationIssue.ALREADY_REQUESTED -> "Đơn này đã có yêu cầu hủy đang chờ xử lý."
    OwnerCancellationIssue.REASON_REQUIRED -> "Chọn lý do không thể cung cấp hàng."
    OwnerCancellationIssue.NOTE_REQUIRED -> "Mô tả lý do khác ít nhất 10 ký tự."
    OwnerCancellationIssue.NOTE_TOO_LONG -> "Nội dung tối đa 500 ký tự."
}
