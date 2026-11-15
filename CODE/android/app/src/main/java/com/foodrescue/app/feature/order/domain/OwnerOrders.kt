package com.foodrescue.app.feature.order.domain

import com.foodrescue.app.feature.surprisebox.domain.boxSearchKey
import java.util.Calendar
import java.util.TimeZone

internal enum class OwnerCancellationReason { OUT_OF_STOCK, FOOD_QUALITY, STORE_CLOSED, OTHER }
internal data class OwnerCancellationRequest(val reason: OwnerCancellationReason, val note: String, val requestedAtMillis: Long)
internal enum class OwnerCancellationIssue { NOT_ELIGIBLE, ALREADY_REQUESTED, REASON_REQUIRED, NOTE_REQUIRED, NOTE_TOO_LONG }
internal data class OwnerCancellationDraft(val orderId: String, val reason: OwnerCancellationReason? = null, val note: String = "")
internal class OwnerOrderException(val issue: OwnerCancellationIssue) : IllegalArgumentException()
internal interface OwnerOrderRepository {
    suspend fun load(): List<BoxReservation>
    suspend fun requestCancellation(draft: OwnerCancellationDraft): List<BoxReservation>
}

internal fun ownerCancellationIssue(order: BoxReservation, draft: OwnerCancellationDraft): OwnerCancellationIssue? = when {
    order.id != draft.orderId || !order.isPaid || order.cancelledAtMillis != null || order.collectedAtMillis != null -> OwnerCancellationIssue.NOT_ELIGIBLE
    order.ownerCancellation != null -> OwnerCancellationIssue.ALREADY_REQUESTED
    draft.reason == null -> OwnerCancellationIssue.REASON_REQUIRED
    draft.note.length > 500 -> OwnerCancellationIssue.NOTE_TOO_LONG
    draft.reason == OwnerCancellationReason.OTHER && draft.note.trim().length < 10 -> OwnerCancellationIssue.NOTE_REQUIRED
    else -> null
}
internal fun BoxReservation.requestOwnerCancellation(draft: OwnerCancellationDraft, now: Long): BoxReservation {
    ownerCancellationIssue(this, draft)?.let { throw OwnerOrderException(it) }
    require(now >= createdAtMillis)
    return copy(ownerCancellation = OwnerCancellationRequest(requireNotNull(draft.reason), draft.note.trim(), now))
}

internal enum class OwnerOrderFilter { ALL, AWAITING_PICKUP, OVERDUE, UNPAID, CANCELLATION_REQUESTED, COLLECTED, CLOSED }
internal enum class OwnerPickupFilter { ALL, TODAY, TOMORROW }
internal fun selectOwnerOrders(orders: List<BoxReservation>, query: String, filter: OwnerOrderFilter,
    pickup: OwnerPickupFilter, now: Long): List<BoxReservation> {
    val start = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh")).apply {
        timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val queryKey = query.boxSearchKey()
    return orders.filter { order ->
        val status = order.statusAt(now)
        val matches = when (filter) {
            OwnerOrderFilter.ALL -> true
            OwnerOrderFilter.AWAITING_PICKUP -> status == OrderStatus.WAITING_PICKUP && now < order.pickup.endMillis
            OwnerOrderFilter.OVERDUE -> status == OrderStatus.WAITING_PICKUP && now >= order.pickup.endMillis
            OwnerOrderFilter.UNPAID -> status == OrderStatus.WAITING_PAYMENT || status == OrderStatus.CHECKING_PAYMENT
            OwnerOrderFilter.CANCELLATION_REQUESTED -> status == OrderStatus.CANCELLATION_REQUESTED
            OwnerOrderFilter.COLLECTED -> status == OrderStatus.COLLECTED
            OwnerOrderFilter.CLOSED -> status == OrderStatus.CANCELLED || status == OrderStatus.EXPIRED
        }
        val dayMatches = when (pickup) {
            OwnerPickupFilter.ALL -> true
            OwnerPickupFilter.TODAY -> order.pickup.startMillis in start until start + 86_400_000
            OwnerPickupFilter.TOMORROW -> order.pickup.startMillis in start + 86_400_000 until start + 172_800_000
        }
        matches && dayMatches && (queryKey.isBlank() || (order.reference + " " + order.boxSnapshot?.name.orEmpty()).boxSearchKey().contains(queryKey))
    }.sortedWith(compareBy<BoxReservation> {
        when (it.statusAt(now)) {
            OrderStatus.CANCELLATION_REQUESTED -> 0
            OrderStatus.WAITING_PICKUP -> 1
            OrderStatus.WAITING_PAYMENT, OrderStatus.CHECKING_PAYMENT -> 2
            else -> 3
        }
    }.thenBy { if (it.cancelledAtMillis != null || it.collectedAtMillis != null || it.statusAt(now) == OrderStatus.EXPIRED) -it.createdAtMillis else it.pickup.endMillis })
}
