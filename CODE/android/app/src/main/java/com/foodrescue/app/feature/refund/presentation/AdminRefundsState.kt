package com.foodrescue.app.feature.refund.presentation

import com.foodrescue.app.feature.refund.domain.*
import com.foodrescue.app.feature.surprisebox.domain.boxSearchKey

internal enum class RefundFilter(val label: String) {
    ACTION("Cần xử lý"), PROCESSING("Đang xử lý"), COMPLETED("Đã hoàn"), REJECTED("Từ chối hủy"), ALL("Tất cả");
    fun matches(status: AdminRefundStatus) = when (this) {
        ACTION -> status == AdminRefundStatus.PENDING || status == AdminRefundStatus.FAILED
        PROCESSING -> status == AdminRefundStatus.PROCESSING
        COMPLETED -> status == AdminRefundStatus.COMPLETED
        REJECTED -> status == AdminRefundStatus.REJECTED
        ALL -> true
    }
}
internal data class AdminRefundsState(
    val cases: List<RefundCase> = emptyList(), val loading: Boolean = true, val busy: Boolean = false,
    val query: String = "", val filter: RefundFilter = RefundFilter.ACTION, val selectedKey: String? = null,
    val approve: Boolean? = null, val note: String = "", val attempted: Boolean = false,
    val confirming: Boolean = false, val error: String? = null, val notice: String? = null,
) {
    val selected: RefundCase? get() = cases.singleOrNull { it.key == selectedKey }
    fun count(filter: RefundFilter) = cases.count { filter.matches(it.status) }
    val actionableAmount: Long get() = cases.filter { RefundFilter.ACTION.matches(it.status) }.sumOf { it.amount }
    val visible: List<RefundCase> get() = cases.filter { filter.matches(it.status) &&
        listOf(it.id, it.sourceId, it.order?.reference.orEmpty(), it.order?.boxSnapshot?.store.orEmpty())
            .any { value -> value.boxSearchKey().contains(query.boxSearchKey()) } }
        .sortedWith(compareBy<RefundCase> { if (it.status == AdminRefundStatus.FAILED) 0 else 1 }
            .thenBy { if (it.status == AdminRefundStatus.COMPLETED || it.status == AdminRefundStatus.REJECTED)
                -(it.transaction?.updatedAtMillis ?: 0) else it.requestedAtMillis })
    val noteError: String? get() = if (attempted && !validRefundNote(note)) "Nhập lý do từ 10 đến 500 ký tự hợp lệ." else null
}
