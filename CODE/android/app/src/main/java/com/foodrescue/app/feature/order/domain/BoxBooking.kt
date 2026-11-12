package com.foodrescue.app.feature.order.domain

import com.foodrescue.app.feature.surprisebox.domain.CatalogBox
import com.foodrescue.app.feature.payment.domain.PaymentStatus
import com.foodrescue.app.feature.payment.domain.QrPayment
import com.foodrescue.app.feature.refund.domain.OrderRefund
import java.util.Calendar
import java.util.TimeZone

internal const val HOLD_MINUTES = 10
internal const val HOLD_MILLIS = HOLD_MINUTES * 60_000L

internal data class PickupWindow(val startMillis: Long, val endMillis: Long) {
    init { require(endMillis > startMillis) }
}

internal data class BookingDraft(
    val boxId: Int,
    val quantity: Int,
    val pickup: PickupWindow,
    val accepted: Boolean = false,
)

internal data class BoxReservation(
    val id: String,
    val boxId: Int,
    val quantity: Int,
    val pickup: PickupWindow,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val unitPrice: Int = 0,
    val payment: QrPayment? = null,
    val cancelledAtMillis: Long? = null,
    val collectedAtMillis: Long? = null,
    val cancellationReason: CancellationReason? = null,
    val cancellationNote: String = "",
    val refund: OrderRefund? = null,
) {
    init {
        require(id.isNotBlank() && quantity > 0)
        require(expiresAtMillis > createdAtMillis && expiresAtMillis <= pickup.startMillis)
        require(unitPrice >= 0)
        require(payment == null || payment.amount == total)
        require(cancelledAtMillis == null || cancelledAtMillis >= createdAtMillis)
        require(collectedAtMillis == null || (isPaid && collectedAtMillis >= createdAtMillis))
        require(cancelledAtMillis == null || collectedAtMillis == null)
        require(cancellationReason == null || cancelledAtMillis != null)
        require(cancellationNote.length <= 500)
        require(refund == null || (isPaid && cancelledAtMillis == refund.requestedAtMillis && refund.amount <= total))
    }

    val total: Long get() = bookingTotal(unitPrice, quantity)
    val isPaid: Boolean get() = payment?.status == PaymentStatus.SUCCEEDED
    val reference: String get() = payment?.reference ?: "FR" + id.replace("-", "").take(12).uppercase(java.util.Locale.ROOT)
    fun isActive(nowMillis: Long) = cancelledAtMillis == null && !isPaid &&
        payment?.status != PaymentStatus.EXPIRED && nowMillis < expiresAtMillis
    fun occupiesStock(nowMillis: Long) = cancelledAtMillis == null && (isPaid || isActive(nowMillis))
    fun remainingSeconds(nowMillis: Long): Long = ((expiresAtMillis - nowMillis).coerceAtLeast(0) + 999) / 1000
}

internal enum class BookingIssue { BOX_UNAVAILABLE, PICKUP_STARTED, SOLD_OUT, QUANTITY, POLICY_REQUIRED }

internal fun bookingTotal(unitPrice: Int, quantity: Int): Long = unitPrice.toLong() * quantity

/** Daily local catalog windows, always labelled with their date in the UI. */
internal fun pickupWindowFor(box: CatalogBox, nowMillis: Long): PickupWindow {
    val start = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh")).apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, box.pickupStartHour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= nowMillis) add(Calendar.DAY_OF_MONTH, 1)
    }
    val end = (start.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, box.pickupEndHour)
        if (timeInMillis <= start.timeInMillis) add(Calendar.DAY_OF_MONTH, 1)
    }
    return PickupWindow(start.timeInMillis, end.timeInMillis)
}

internal fun holdDeadline(nowMillis: Long, pickup: PickupWindow): Long =
    minOf(nowMillis + HOLD_MILLIS, pickup.startMillis)

internal fun availableQuantity(box: CatalogBox, pickup: PickupWindow, reservations: List<BoxReservation>, nowMillis: Long): Int {
    val held = reservations.filter {
        it.boxId == box.id && it.pickup.startMillis == pickup.startMillis && it.occupiesStock(nowMillis)
    }.sumOf { it.quantity.toLong() }
    return (box.remaining.toLong() - held).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
}

internal fun bookingIssue(draft: BookingDraft, box: CatalogBox?, reservations: List<BoxReservation>, nowMillis: Long): BookingIssue? {
    if (box == null || box.id != draft.boxId || box.price <= 0) return BookingIssue.BOX_UNAVAILABLE
    if (nowMillis >= draft.pickup.startMillis) return BookingIssue.PICKUP_STARTED
    val available = availableQuantity(box, draft.pickup, reservations, nowMillis)
    if (available == 0) return BookingIssue.SOLD_OUT
    if (draft.quantity !in 1..available) return BookingIssue.QUANTITY
    if (!draft.accepted) return BookingIssue.POLICY_REQUIRED
    return null
}
