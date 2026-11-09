package com.foodrescue.app.feature.complaint.presentation

import com.foodrescue.app.feature.complaint.domain.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal val ComplaintReason.label: String get() = when (this) {
    ComplaintReason.FOOD_QUALITY -> "Chất lượng thực phẩm"
    ComplaintReason.MISSING_ITEMS -> "Thiếu hoặc sai sản phẩm"
    ComplaintReason.STORE_UNAVAILABLE -> "Cửa hàng không thể giao hộp"
    ComplaintReason.PICKUP_ISSUE -> "Vấn đề khi nhận hộp"
    ComplaintReason.PAYMENT_REFUND -> "Thanh toán và hoàn tiền"
    ComplaintReason.OTHER -> "Vấn đề khác"
}

internal val ComplaintStatus.label: String get() = when (this) {
    ComplaintStatus.PENDING -> "Chờ xử lý"
    ComplaintStatus.IN_REVIEW -> "Đang xem xét"
    ComplaintStatus.RESOLVED -> "Đã giải quyết"
    ComplaintStatus.REJECTED -> "Không được chấp nhận"
}

internal val ComplaintFilter.label: String get() = when (this) {
    ComplaintFilter.ALL -> "Tất cả"
    ComplaintFilter.OPEN -> "Đang xử lý"
    ComplaintFilter.CLOSED -> "Đã có kết quả"
}

internal val ComplaintIssue.message: String get() = when (this) {
    ComplaintIssue.ORDER_REQUIRED -> "Vui lòng chọn đơn hàng."
    ComplaintIssue.ORDER_UNAVAILABLE -> "Đơn hàng này không còn trong danh sách. Vui lòng chọn lại."
    ComplaintIssue.REASON_REQUIRED -> "Vui lòng chọn lý do khiếu nại."
    ComplaintIssue.DESCRIPTION_TOO_SHORT -> "Nội dung cần ít nhất 20 ký tự."
    ComplaintIssue.DESCRIPTION_TOO_LONG -> "Nội dung tối đa 2.000 ký tự."
    ComplaintIssue.TOO_MANY_ATTACHMENTS -> "Mỗi khiếu nại được đính kèm tối đa 3 ảnh."
    ComplaintIssue.OPEN_CASE_EXISTS -> "Đơn này đã có khiếu nại đang xử lý."
    ComplaintIssue.NETWORK_UNAVAILABLE -> "Chưa gửi được khiếu nại. Kiểm tra kết nối và thử lại; nội dung của bạn đã được giữ lại."
    ComplaintIssue.ATTACHMENT_UNAVAILABLE -> "Không đọc được ảnh đính kèm. Vui lòng xóa ảnh đó và chọn lại."
}

internal val EvidenceIssue.message: String get() = when (this) {
    EvidenceIssue.UNSUPPORTED -> "Chọn ảnh JPG, PNG, WebP hoặc HEIC hợp lệ."
    EvidenceIssue.TOO_LARGE -> "Ảnh vượt quá 5 MB. Vui lòng chọn ảnh nhỏ hơn."
    EvidenceIssue.UNREADABLE -> "Không mở được ảnh. Vui lòng chọn lại."
    EvidenceIssue.LIMIT_REACHED -> "Bạn có thể đính kèm tối đa 3 ảnh."
}

internal fun complaintTimestamp(millis: Long): String = SimpleDateFormat("HH:mm · dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))
    .apply { timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh") }.format(Date(millis))
