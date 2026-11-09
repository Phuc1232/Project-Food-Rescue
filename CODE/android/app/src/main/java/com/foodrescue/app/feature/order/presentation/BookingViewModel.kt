package com.foodrescue.app.feature.order.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.foodrescue.app.feature.discovery.domain.CatalogRepository
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.surprisebox.domain.CatalogBox
import java.util.UUID

internal data class BookingUiState(
    val catalog: List<CatalogBox>,
    val nowMillis: Long,
    val draft: BookingDraft? = null,
    val reservations: List<BoxReservation> = emptyList(),
    val issue: BookingIssue? = null,
    val orderFilter: OrderFilter = OrderFilter.ALL,
) {
    val visibleOrders: List<BoxReservation> get() = orderFilter.select(reservations, nowMillis)
    val box: CatalogBox? get() = catalog.firstOrNull { it.id == draft?.boxId }
    val available: Int get() = draft?.let { draft ->
        box?.let { availableQuantity(it, draft.pickup, reservations, nowMillis) }
    } ?: 0
    val total: Long get() = bookingTotal(box?.price ?: 0, draft?.quantity ?: 0)
    val canConfirm: Boolean get() = draft?.let { bookingIssue(it, box, reservations, nowMillis) == null } == true
    fun activeFor(boxId: Int) = reservations.firstOrNull { it.boxId == boxId && it.isActive(nowMillis) }
}

/** Local UI state only; a future server adapter owns authoritative reservations. */
internal class BookingViewModel(
    private val saved: SavedStateHandle,
    private val catalog: CatalogRepository,
    private val payments: PaymentRepository,
    private val history: OrderHistoryStore? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    var state by mutableStateOf(BookingUiState(
        catalog = catalog.getBoxes(),
        nowMillis = clock(),
        draft = saved.get<ArrayList<String>>(DRAFT)?.let(::restoreDraft),
        reservations = initialReservations(),
        orderFilter = saved.get<String>(FILTER)?.let { runCatching { OrderFilter.valueOf(it) }.getOrNull() } ?: OrderFilter.ALL,
    ))
        private set

    fun prepare(boxId: Int, reset: Boolean = false): String? {
        refreshCatalog()
        state.activeFor(boxId)?.let { return it.id }
        val box = state.catalog.firstOrNull { it.id == boxId }
        if (box == null) {
            update(state.copy(draft = null, issue = BookingIssue.BOX_UNAVAILABLE))
            return null
        }
        val previous = state.draft?.takeIf { draft ->
            !reset && draft.boxId == boxId && state.reservations.none { it.boxId == boxId && it.pickup == draft.pickup }
        }
        update(state.copy(draft = previous ?: BookingDraft(boxId, 1, pickupWindowFor(box, state.nowMillis)), issue = null))
        return null
    }

    fun changeQuantity(delta: Int) {
        refreshCatalog()
        val draft = state.draft ?: return
        val quantity = (draft.quantity + delta.coerceIn(-1, 1)).coerceIn(1, state.available.coerceAtLeast(1))
        update(state.copy(draft = draft.copy(quantity = quantity), issue = null))
    }

    fun acceptPolicy(value: Boolean) {
        val draft = state.draft ?: return
        update(state.copy(draft = draft.copy(accepted = value), issue = null))
    }

    fun chooseNextPickup() {
        val boxId = state.draft?.boxId ?: return
        prepare(boxId, reset = true)
    }

    fun confirm(): String? {
        refreshCatalog()
        val draft = state.draft ?: return null
        state.activeFor(draft.boxId)?.let { return it.id }
        val issue = bookingIssue(draft, state.box, state.reservations, state.nowMillis)
        if (issue != null) {
            update(state.copy(issue = issue))
            return null
        }
        val reservation = BoxReservation(
            UUID.randomUUID().toString(), draft.boxId, draft.quantity, draft.pickup,
            state.nowMillis, holdDeadline(state.nowMillis, draft.pickup),
            unitPrice = requireNotNull(state.box).price,
        )
        update(state.copy(reservations = listOf(reservation) + state.reservations, issue = null))
        return reservation.id
    }

    fun release(id: String) {
        refreshTime()
        val reservation = state.reservations.firstOrNull { it.id == id } ?: return
        if (!reservation.isActive(state.nowMillis) || reservation.payment?.status == PaymentStatus.PROCESSING) return
        update(state.copy(
            reservations = state.reservations.map { if (it.id == id) it.copy(cancelledAtMillis = state.nowMillis) else it },
            draft = state.draft?.takeUnless { it.boxId == reservation.boxId },
            issue = null, nowMillis = clock(),
        ))
    }

    fun openPayment(id: String): Boolean {
        refreshTime()
        val reservation = state.reservations.firstOrNull { it.id == id } ?: return false
        if (reservation.cancelledAtMillis != null) return false
        if (reservation.payment != null) return true
        if (reservation.total <= 0) return false
        replace(reservation.copy(payment = QrPayment(
            reservation.reference, reservation.total,
            if (reservation.isActive(state.nowMillis)) PaymentStatus.READY else PaymentStatus.EXPIRED,
            state.nowMillis,
        )))
        return true
    }

    fun checkPayment(id: String) {
        refreshTime()
        val reservation = state.reservations.firstOrNull { it.id == id } ?: return
        val payment = reservation.payment ?: return
        if (!reservation.isActive(state.nowMillis) || payment.status !in listOf(PaymentStatus.READY, PaymentStatus.FAILED)) return
        replace(reservation.copy(payment = payment.copy(status = PaymentStatus.PROCESSING, updatedAtMillis = state.nowMillis)))
    }

    fun refreshTime() {
        val now = clock()
        val reservations = state.reservations.map { reservation ->
            if (reservation.cancelledAtMillis != null) return@map reservation
            val payment = reservation.payment ?: return@map reservation
            val status = when {
                reservation.isPaid -> PaymentStatus.SUCCEEDED
                now >= reservation.expiresAtMillis -> PaymentStatus.EXPIRED
                payment.status == PaymentStatus.PROCESSING && now - payment.updatedAtMillis >= 2_000 ->
                    when (payments.check(payment)) {
                        PaymentResult.CONFIRMED -> PaymentStatus.SUCCEEDED
                        PaymentResult.UNAVAILABLE -> PaymentStatus.FAILED
                    }
                else -> payment.status
            }
            if (status == payment.status) reservation
            else reservation.copy(payment = payment.copy(status = status, updatedAtMillis = now))
        }
        val next = state.copy(nowMillis = now, reservations = reservations)
        if (reservations == state.reservations) state = next else update(next)
    }

    private fun replace(reservation: BoxReservation) {
        update(state.copy(reservations = state.reservations.map { if (it.id == reservation.id) reservation else it }))
    }
    private fun refreshCatalog() { state = state.copy(catalog = catalog.getBoxes(), nowMillis = clock()) }

    fun selectFilter(filter: OrderFilter) {
        state = state.copy(orderFilter = filter)
        saved[FILTER] = filter.name
    }

    fun restoreHistory() {
        history?.let { state = state.copy(reservations = it.load()) }
        refreshTime()
    }

    private fun initialReservations(): List<BoxReservation> {
        val stored = history?.load().orEmpty()
        if (stored.isNotEmpty()) return stored
        val restored = saved.get<ArrayList<String>>(HOLDS)
            ?.mapNotNull { restoreReservation(it, catalog.getBoxes()) }.orEmpty()
        if (restored.isNotEmpty()) history?.save(restored)
        return restored
    }

    fun clear() {
        saved.remove<ArrayList<String>>(DRAFT)
        saved.remove<ArrayList<String>>(HOLDS)
        saved.remove<String>(FILTER)
        state = BookingUiState(catalog.getBoxes(), clock())
    }

    private fun update(next: BookingUiState) {
        if (next.reservations != state.reservations) history?.save(next.reservations)
        state = next
        if (next.draft == null) saved.remove<ArrayList<String>>(DRAFT)
        else saved[DRAFT] = next.draft.let {
            arrayListOf(it.boxId.toString(), it.quantity.toString(), it.pickup.startMillis.toString(),
                it.pickup.endMillis.toString(), it.accepted.toString())
        }
        saved[HOLDS] = ArrayList(next.reservations.map {
            (listOf(it.id, it.boxId, it.quantity, it.pickup.startMillis, it.pickup.endMillis,
                it.createdAtMillis, it.expiresAtMillis, it.unitPrice) +
                (it.payment?.let { payment -> listOf(payment.reference, payment.amount, payment.status.name, payment.updatedAtMillis) }
                    ?: listOf("", "", "", "")) +
                listOf(it.cancelledAtMillis ?: "", it.collectedAtMillis ?: "")).joinToString("|")
        })
    }

    private companion object {
        const val DRAFT = "booking.draft.v1"
        const val HOLDS = "booking.holds.v1"
        const val FILTER = "orders.filter.v1"
        fun restoreDraft(parts: List<String>): BookingDraft? = runCatching {
            require(parts.size == 5 && parts[1].toInt() > 0)
            BookingDraft(parts[0].toInt(), parts[1].toInt(), PickupWindow(parts[2].toLong(), parts[3].toLong()), parts[4].toBooleanStrict())
        }.getOrNull()
        fun restoreReservation(row: String, boxes: List<CatalogBox>): BoxReservation? = runCatching {
            val parts = row.split('|')
            require(parts.size in listOf(7, 12, 14))
            val price = if (parts.size == 7) boxes.firstOrNull { it.id == parts[1].toInt() }?.price ?: 0 else parts[7].toInt()
            val payment = if (parts.size >= 12 && parts[8].isNotBlank()) {
                QrPayment(parts[8], parts[9].toLong(), PaymentStatus.valueOf(parts[10]), parts[11].toLong())
            } else null
            BoxReservation(parts[0], parts[1].toInt(), parts[2].toInt(), PickupWindow(parts[3].toLong(), parts[4].toLong()),
                parts[5].toLong(), parts[6].toLong(), price, payment,
                parts.getOrNull(12)?.takeIf { it.isNotBlank() }?.toLong(),
                parts.getOrNull(13)?.takeIf { it.isNotBlank() }?.toLong())
        }.getOrNull()
    }
}
