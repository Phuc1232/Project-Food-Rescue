package com.foodrescue.app.feature.order.domain

internal enum class CancellationReason { CHANGED_PLANS, WRONG_ORDER, CANNOT_PICKUP, OTHER }
internal enum class CancellationIssue { ORDER_UNAVAILABLE, NOT_PAID, ALREADY_CANCELLED, COLLECTED, PICKUP_STARTED, REASON_REQUIRED, NOTE_TOO_LONG }

internal data class CancellationDraft(val orderId: String, val reason: CancellationReason? = null, val note: String = "")
internal data class CancellationQuote(val amount: Long, val deadlineMillis: Long, val issue: CancellationIssue?) {
    val canCancel: Boolean get() = issue == null
}

/** Matches the policy shown before booking: full refund only before the pickup window starts. */
internal fun cancellationQuote(order: BoxReservation, nowMillis: Long): CancellationQuote {
    val issue = when {
        order.cancelledAtMillis != null -> CancellationIssue.ALREADY_CANCELLED
        order.collectedAtMillis != null -> CancellationIssue.COLLECTED
        !order.isPaid -> CancellationIssue.NOT_PAID
        nowMillis >= order.pickup.startMillis -> CancellationIssue.PICKUP_STARTED
        else -> null
    }
    return CancellationQuote(if (issue == null) order.total else 0, order.pickup.startMillis, issue)
}
