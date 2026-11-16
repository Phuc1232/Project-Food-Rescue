package com.foodrescue.app.feature.order.domain

import com.foodrescue.app.feature.payment.domain.PaymentStatus

internal enum class OrderStatus { WAITING_PAYMENT, CHECKING_PAYMENT, WAITING_PICKUP, CANCELLATION_REQUESTED, COLLECTED, CANCELLED, EXPIRED, NO_SHOW }

internal fun BoxReservation.statusAt(nowMillis: Long): OrderStatus = when {
    cancelledAtMillis != null -> OrderStatus.CANCELLED
    collectedAtMillis != null -> OrderStatus.COLLECTED
    noShow != null -> OrderStatus.NO_SHOW
    ownerCancellation != null -> OrderStatus.CANCELLATION_REQUESTED
    isPaid -> OrderStatus.WAITING_PICKUP
    !isActive(nowMillis) -> OrderStatus.EXPIRED
    payment?.status == PaymentStatus.PROCESSING -> OrderStatus.CHECKING_PAYMENT
    else -> OrderStatus.WAITING_PAYMENT
}

/** The existing payment reference is also the pickup code; no second identifier is issued. */
internal fun BoxReservation.pickupCode(nowMillis: Long): String? =
    reference.takeIf { statusAt(nowMillis) == OrderStatus.WAITING_PICKUP && nowMillis < pickup.endMillis }

internal enum class OrderFilter {
    ALL, UNPAID, READY_FOR_PICKUP, COLLECTED, CANCELLED, EXPIRED, NO_SHOW;

    fun select(orders: List<BoxReservation>, nowMillis: Long): List<BoxReservation> =
        orders.filter { order ->
            val status = order.statusAt(nowMillis)
            when (this) {
                ALL -> true
                UNPAID -> status == OrderStatus.WAITING_PAYMENT || status == OrderStatus.CHECKING_PAYMENT
                READY_FOR_PICKUP -> status == OrderStatus.WAITING_PICKUP
                COLLECTED -> status == OrderStatus.COLLECTED
                CANCELLED -> status == OrderStatus.CANCELLED
                EXPIRED -> status == OrderStatus.EXPIRED
                NO_SHOW -> status == OrderStatus.NO_SHOW
            }
        }.sortedByDescending { it.createdAtMillis }
}
