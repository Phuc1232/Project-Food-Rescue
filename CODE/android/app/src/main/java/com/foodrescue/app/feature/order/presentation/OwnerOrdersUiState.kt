package com.foodrescue.app.feature.order.presentation

import com.foodrescue.app.feature.order.domain.*
import java.util.Calendar
import java.util.TimeZone

internal enum class OwnerOrderPage { LIST, DETAIL, CANCELLATION }
internal data class OwnerOrdersUiState(
    val orders: List<BoxReservation> = emptyList(), val now: Long = 0,
    val loading: Boolean = true, val busy: Boolean = false, val error: String? = null,
    val loadError: String? = null,
    val query: String = "", val filter: OwnerOrderFilter = OwnerOrderFilter.ALL,
    val pickupFilter: OwnerPickupFilter = OwnerPickupFilter.ALL,
    val page: OwnerOrderPage = OwnerOrderPage.LIST, val selectedId: String? = null,
    val draft: OwnerCancellationDraft? = null, val confirm: Boolean = false, val message: String? = null,
) {
    val selected get() = orders.firstOrNull { it.id == selectedId }
    val visible get() = selectOwnerOrders(orders, query, filter, pickupFilter, now)
    fun count(filter: OwnerOrderFilter) = selectOwnerOrders(orders, "", filter, OwnerPickupFilter.ALL, now).size
    val paidToday: List<BoxReservation> get() {
        val start = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh")).apply {
            timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return orders.filter { it.isPaid && it.cancelledAtMillis == null && it.payment!!.updatedAtMillis in start until start + 86_400_000 }
    }
}
