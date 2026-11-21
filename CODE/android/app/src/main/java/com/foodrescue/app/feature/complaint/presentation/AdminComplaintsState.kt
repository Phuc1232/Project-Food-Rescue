package com.foodrescue.app.feature.complaint.presentation

import com.foodrescue.app.feature.complaint.domain.*
import java.text.Normalizer
import java.util.Locale

internal enum class AdminComplaintFilter(val label: String) {
    OPEN("Cần xử lý"), PENDING("Chờ tiếp nhận"), REVIEW("Đang xem xét"), REFUND("Chờ hoàn tiền"), CLOSED("Đã có kết quả"), ALL("Tất cả");
    fun matches(item: CustomerComplaint): Boolean = when (this) {
        OPEN -> item.status.isOpen
        PENDING -> item.status == ComplaintStatus.PENDING
        REVIEW -> item.status == ComplaintStatus.IN_REVIEW
        REFUND -> item.refundRequest != null && item.refundTransaction?.status != com.foodrescue.app.feature.refund.domain.AdminRefundStatus.COMPLETED
        CLOSED -> !item.status.isOpen
        ALL -> true
    }
}
internal data class AdminComplaintsState(
    val cases: List<ComplaintReviewCase> = emptyList(), val loading: Boolean = true, val busy: Boolean = false,
    val query: String = "", val filter: AdminComplaintFilter = AdminComplaintFilter.OPEN,
    val selectedKey: String? = null, val decision: ComplaintReviewAction? = null,
    val note: String = "", val amount: String = "", val attempted: Boolean = false,
    val confirming: Boolean = false, val error: String? = null, val notice: String? = null,
    val now: Long = System.currentTimeMillis(),
) {
    val selected: ComplaintReviewCase? get() = cases.find { it.key == selectedKey }
    fun count(filter: AdminComplaintFilter) = cases.count { filter.matches(it.complaint) }
    val visible: List<ComplaintReviewCase> get() {
        val needle = query.searchKey()
        return cases.filter { filter.matches(it.complaint) &&
            listOf(it.complaint.id, it.complaint.order.reference, it.complaint.order.store, it.complaint.reason.label)
                .any { value -> value.searchKey().contains(needle) } }
            .let { if (filter == AdminComplaintFilter.CLOSED) it.sortedByDescending { row -> row.complaint.updatedAtMillis }
                else it.sortedBy { row -> row.complaint.createdAtMillis } }
    }
    val noteError: String? get() = if (attempted && !validComplaintResponse(note)) "Nhập phản hồi từ 20 đến 2.000 ký tự hợp lệ." else null
    val amountError: String? get() = if (attempted && decision == ComplaintReviewAction.REFUND_REQUESTED &&
        (amount.toLongOrNull() ?: 0) !in 1..(selected?.refundableAmount ?: 0)) "Số tiền phải lớn hơn 0 và không vượt khoản có thể hoàn." else null
}
private fun String.searchKey(): String = Normalizer.normalize(trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").replace('đ', 'd')
