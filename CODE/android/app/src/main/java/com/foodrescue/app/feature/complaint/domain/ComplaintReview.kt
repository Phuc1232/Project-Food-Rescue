package com.foodrescue.app.feature.complaint.domain

import com.foodrescue.app.feature.order.domain.BoxReservation
import com.foodrescue.app.feature.order.domain.OrderStatus
import com.foodrescue.app.feature.order.domain.statusAt
import com.foodrescue.app.feature.surprisebox.domain.PackedBox

internal enum class ComplaintReviewAction { RECEIVED, RESOLVED, REJECTED, REFUND_REQUESTED }
internal data class ComplaintReviewEvent(val action: ComplaintReviewAction, val actor: String,
    val atMillis: Long, val note: String = "", val refundAmount: Long = 0,
    val orderStatus: OrderStatus? = null, val paidAmount: Long = 0)

/** A queued instruction, not a completed payment-provider transaction. */
internal data class ComplaintRefundRequest(val id: String, val amount: Long, val requestedAtMillis: Long) {
    init { require(id.isNotBlank() && amount > 0 && requestedAtMillis >= 0) }
}

internal data class ComplaintReviewCase(val accountKey: String, val complaint: CustomerComplaint,
    val order: BoxReservation? = null, val packedBoxes: List<PackedBox> = emptyList(),
    val refundableAmount: Long = 0) {
    val key: String get() = "$accountKey:${complaint.id}"
}
internal interface ComplaintReviewRepository {
    suspend fun load(): List<ComplaintReviewCase>
    suspend fun review(expected: ComplaintReviewCase, action: ComplaintReviewAction,
        note: String, refundAmount: Long): ComplaintReviewCase
}
internal class StaleComplaintReview : IllegalStateException()
internal class ChangedComplaintOrder : IllegalStateException()
internal const val MIN_REVIEW_RESPONSE = 20
internal const val MAX_REVIEW_RESPONSE = 2000

internal fun validComplaintResponse(note: String): Boolean =
    note.trim().length in MIN_REVIEW_RESPONSE..MAX_REVIEW_RESPONSE && note.none { it.isISOControl() && it != '\n' }

/** All existing cancellation refunds remain reserved, including failed transactions that may be retried. */
internal fun complaintRefundableAmount(order: BoxReservation?, cases: List<CustomerComplaint>): Long {
    if (order == null || !order.isPaid || order.total <= 0 || order.ownerCancellation != null) return 0
    // An active pickup must be settled first; a complaint must not leave both pickup and refund available.
    if (order.collectedAtMillis == null && order.cancelledAtMillis == null && order.noShow == null) return 0
    val requests = order.adminRefunds.filter { it.reservesMoney }.associate { "${it.source}:${it.sourceId}" to it.amount }.toMutableMap()
    for (item in cases.filter { it.order.id == order.id }) {
        item.refundRequest?.let { requests["COMPLAINT:${item.id}"] = it.amount }
    }
    return requests.values.fold((order.total - (order.refund?.amount ?: 0)).coerceAtLeast(0)) { left, amount -> (left - amount).coerceAtLeast(0) }
}

internal fun CustomerComplaint.review(expectedRevision: Long, action: ComplaintReviewAction, note: String,
    refundAmount: Long, availableRefund: Long, order: BoxReservation?, actor: String, now: Long): CustomerComplaint {
    if (revision != expectedRevision || !status.isOpen) throw StaleComplaintReview()
    require(actor.isNotBlank() && now >= updatedAtMillis)
    if (action == ComplaintReviewAction.RECEIVED) {
        if (status != ComplaintStatus.PENDING) throw StaleComplaintReview()
        require(refundAmount == 0L && note.isBlank())
    } else {
        if (status != ComplaintStatus.IN_REVIEW) throw StaleComplaintReview()
        require(validComplaintResponse(note))
        if (action == ComplaintReviewAction.REFUND_REQUESTED) {
            require(order?.id == this.order.id && order.isPaid &&
                refundAmount in 1..minOf(availableRefund, complaintRefundableAmount(order, emptyList())))
        } else require(refundAmount == 0L)
    }
    val nextStatus = when (action) {
        ComplaintReviewAction.RECEIVED -> ComplaintStatus.IN_REVIEW
        ComplaintReviewAction.REJECTED -> ComplaintStatus.REJECTED
        ComplaintReviewAction.RESOLVED, ComplaintReviewAction.REFUND_REQUESTED -> ComplaintStatus.RESOLVED
    }
    return copy(status = nextStatus, revision = revision + 1, updatedAtMillis = now,
        response = if (action == ComplaintReviewAction.RECEIVED) response else note.trim(),
        history = history + ComplaintReviewEvent(action, actor, now, note.trim(), refundAmount,
            order?.statusAt(now), order?.takeIf { it.isPaid }?.total ?: 0),
        refundRequest = if (action == ComplaintReviewAction.REFUND_REQUESTED)
            ComplaintRefundRequest("HT-$id", refundAmount, now) else refundRequest)
}
