package com.foodrescue.app.feature.finance.presentation

import com.foodrescue.app.feature.finance.domain.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun revenueDate(time: Long, pattern: String = "dd/MM/yyyy"): String = SimpleDateFormat(pattern, Locale.forLanguageTag("vi-VN")).apply {
    timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
}.format(Date(time))
internal fun revenueRange(window: RevenueWindow): String = "${revenueDate(window.start, "dd/MM")} – ${revenueDate(window.endExclusive - 1)}"
internal fun revenueRate(bps: Int): String = java.math.BigDecimal(bps).movePointLeft(2).stripTrailingZeros().toPlainString().replace('.', ',') + "%"
internal val RevenuePeriod.label: String get() = when (this) { RevenuePeriod.DAY -> "Ngày"; RevenuePeriod.WEEK -> "Tuần"; RevenuePeriod.MONTH -> "Tháng" }
internal val RevenueBasis.label: String get() = when (this) { RevenueBasis.PAYMENT -> "Ngày thanh toán"; RevenueBasis.COLLECTION -> "Ngày giao hộp" }
internal val RevenueFilter.label: String get() = when (this) {
    RevenueFilter.ALL -> "Tất cả"; RevenueFilter.ELIGIBLE -> "Đủ điều kiện"; RevenueFilter.WAITING -> "Chờ nhận"
    RevenueFilter.HELD -> "Tạm giữ"; RevenueFilter.REFUNDS -> "Hoàn tiền"
}
internal val SettlementFilter.label: String get() = when (this) {
    SettlementFilter.ALL -> "Tất cả"; SettlementFilter.AWAITING -> "Chờ xử lý"; SettlementFilter.PROCESSING -> "Đang chuyển"
    SettlementFilter.PAID -> "Đã chi trả"; SettlementFilter.FAILED -> "Thất bại"
}
internal val RevenueEntryStatus.label: String get() = when (this) {
    RevenueEntryStatus.WAITING_PICKUP -> "Chờ khách nhận"; RevenueEntryStatus.HELD -> "Tạm giữ"
    RevenueEntryStatus.ELIGIBLE -> "Đủ điều kiện"; RevenueEntryStatus.REFUND_PENDING -> "Chờ hoàn tiền"
    RevenueEntryStatus.REFUNDED -> "Đã hoàn tiền"; RevenueEntryStatus.CANCELLED -> "Đơn đã hủy"
}
internal val SettlementStatus.label: String get() = when (this) {
    SettlementStatus.PENDING -> "Chờ đối soát"; SettlementStatus.READY -> "Chờ chi trả"
    SettlementStatus.PROCESSING -> "Đang chuyển tiền"; SettlementStatus.PAID -> "Đã chi trả"; SettlementStatus.FAILED -> "Chuyển tiền thất bại"
}
internal val RevenueEntry.explanation: String get() {
    val currentStatus = when (status) {
    RevenueEntryStatus.ELIGIBLE -> "Đơn đã giao và đủ điều kiện tính khoản cửa hàng được hưởng."
    RevenueEntryStatus.WAITING_PICKUP -> "Khách đã thanh toán. Khoản này chưa được đưa vào đối soát vì chưa xác nhận giao hộp."
    RevenueEntryStatus.HELD -> if (order.ownerCancellation != null) "Đang chờ xử lý yêu cầu hủy của cửa hàng. Khoản này được tạm giữ."
        else "Đơn đang có khiếu nại cần xử lý. Khoản được hưởng hiện tại đang tạm giữ."
    RevenueEntryStatus.REFUND_PENDING -> if (order.refund?.status == com.foodrescue.app.feature.refund.domain.RefundStatus.FAILED)
        "Hoàn tiền chưa thành công. Số tiền vẫn được giữ để xử lý hoàn cho khách." else "Khoản hoàn cho khách đang được xử lý và không được tính vào khoản cửa hàng được hưởng."
    RevenueEntryStatus.REFUNDED -> "Khoản thanh toán đã được hoàn cho khách. Cửa hàng không được hưởng tiền từ đơn này."
    RevenueEntryStatus.CANCELLED -> "Đơn đã hủy. Phần tiền còn lại chờ được xác định, chưa đưa vào khoản cửa hàng được hưởng."
    }
    val hasConfirmedSettlement = settlementId?.let { !it.startsWith("pending:") } == true
    return if (hasConfirmedSettlement && status != RevenueEntryStatus.ELIGIBLE) {
        "$currentStatus Đơn đã có kỳ đối soát được xác nhận. Số tiền và hoa hồng trong kỳ đó được giữ theo bản ghi đối soát; trạng thái hiện tại của đơn được theo dõi riêng."
    } else currentStatus
}
