package com.foodrescue.app.feature.order.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.foodrescue.app.feature.discovery.domain.CatalogRepository
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.refund.domain.*
import com.foodrescue.app.feature.surprisebox.domain.CatalogBox
import com.foodrescue.app.feature.finance.domain.*
import java.util.UUID
import java.net.URLDecoder
import java.net.URLEncoder

internal data class BookingUiState(
    val catalog: List<CatalogBox>,
    val nowMillis: Long,
    val draft: BookingDraft? = null,
    val reservations: List<BoxReservation> = emptyList(),
    val issue: BookingIssue? = null,
    val orderFilter: OrderFilter = OrderFilter.ALL,
    val cancellationDraft: CancellationDraft? = null,
    val cancellationIssue: CancellationIssue? = null,
    val dataError: String? = null,
    val financialTerms: FinancialTerms = FinancialTerms(),
) {
    val visibleOrders: List<BoxReservation> get() = orderFilter.select(reservations, nowMillis)
    val box: CatalogBox? get() = catalog.firstOrNull { it.id == draft?.boxId }
    val available: Int get() = draft?.let { draft ->
        box?.let { availableQuantity(it, draft.pickup, reservations, nowMillis) }
    } ?: 0
    val total: Long get() = bookingTotal(box?.price ?: 0, draft?.quantity ?: 0)
    val canConfirm: Boolean get() = dataError == null && draft?.let { bookingIssue(it, box, reservations, nowMillis) == null } == true
    fun activeFor(boxId: Int) = reservations.firstOrNull { it.boxId == boxId && it.isActive(nowMillis) }
}

/** Local UI state only; a future server adapter owns authoritative reservations. */
internal class BookingViewModel(
    private val saved: SavedStateHandle,
    private val catalog: CatalogRepository,
    private val payments: PaymentRepository,
    private val history: OrderHistoryStore? = null,
    private val refunds: RefundRepository = RefundRepository { RefundResult.UNAVAILABLE },
    private val policies: FinancialTermsSource = FinancialTermsSource { FinancialTerms() },
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private var initialError: String? = null
    var state by mutableStateOf(BookingUiState(
        catalog = try { catalog.getBoxes() } catch (_: Exception) { initialError = READ_ERROR; emptyList() },
        nowMillis = clock(),
        draft = saved.get<ArrayList<String>>(DRAFT)?.let(::restoreDraft),
        reservations = try { initialReservations() } catch (_: Exception) { initialError = READ_ERROR; emptyList() },
        orderFilter = saved.get<String>(FILTER)?.let { runCatching { OrderFilter.valueOf(it) }.getOrNull() } ?: OrderFilter.ALL,
        cancellationDraft = saved.get<ArrayList<String>>(CANCEL_DRAFT)?.let { parts -> runCatching {
            require(parts.size == 3)
            CancellationDraft(parts[0], parts[1].takeIf(String::isNotBlank)?.let(CancellationReason::valueOf), parts[2])
        }.getOrNull() },
        dataError = initialError,
    ))
        private set

    fun prepare(boxId: Int, reset: Boolean = false): String? {
        if (!refreshCatalog()) return null
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
        if (!refreshCatalog()) return
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
        if (!refreshCatalog()) return null
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
            unitPrice = requireNotNull(state.box).price, boxSnapshot = state.box?.takeIf { it.batchId != null },
            financialTerms = state.financialTerms,
        )
        val reserved = try { history?.reserve(reservation) != false } catch (_: Exception) {
            state = state.copy(dataError = WRITE_ERROR)
            return null
        }
        if (!reserved) {
            refreshCatalog()
            update(state.copy(issue = BookingIssue.QUANTITY))
            return null
        }
        return reservation.id.takeIf { update(state.copy(reservations = listOf(reservation) + state.reservations, issue = null)) }
    }

    fun release(id: String) {
        refreshTime()
        if (state.dataError != null) return
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
        if (state.dataError != null) return false
        val reservation = state.reservations.firstOrNull { it.id == id } ?: return false
        if (reservation.cancelledAtMillis != null) return false
        if (reservation.payment != null) return true
        if (reservation.total <= 0) return false
        return replace(reservation.copy(payment = QrPayment(
            reservation.reference, reservation.total,
            if (reservation.isActive(state.nowMillis)) PaymentStatus.READY else PaymentStatus.EXPIRED,
            state.nowMillis,
        )))
    }

    fun checkPayment(id: String) {
        refreshTime()
        if (state.dataError != null) return
        val reservation = state.reservations.firstOrNull { it.id == id } ?: return
        val payment = reservation.payment ?: return
        if (!reservation.isActive(state.nowMillis) || payment.status !in listOf(PaymentStatus.READY, PaymentStatus.FAILED)) return
        replace(reservation.copy(payment = payment.copy(status = PaymentStatus.PROCESSING, updatedAtMillis = state.nowMillis)))
    }

    fun prepareCancellation(id: String) {
        refreshTime()
        update(state.copy(cancellationDraft = state.cancellationDraft?.takeIf { it.orderId == id } ?: CancellationDraft(id),
            cancellationIssue = null))
    }

    fun chooseCancellationReason(reason: CancellationReason?) {
        val draft = state.cancellationDraft ?: return
        update(state.copy(cancellationDraft = draft.copy(reason = reason), cancellationIssue = null))
    }

    fun changeCancellationNote(note: String) {
        val draft = state.cancellationDraft ?: return
        update(state.copy(cancellationDraft = draft.copy(note = note.take(500)), cancellationIssue = null))
    }

    fun cancelPaidOrder(id: String): Boolean {
        restoreHistory()
        if (state.dataError != null) return false
        val order = state.reservations.firstOrNull { it.id == id }
        val draft = state.cancellationDraft?.takeIf { it.orderId == id }
        val quote = order?.let { cancellationQuote(it, state.nowMillis) }
        val issue = when {
            order == null -> CancellationIssue.ORDER_UNAVAILABLE
            quote?.issue != null -> quote.issue
            draft?.reason == null -> CancellationIssue.REASON_REQUIRED
            draft.note.length > 500 -> CancellationIssue.NOTE_TOO_LONG
            else -> null
        }
        if (issue != null) {
            update(state.copy(cancellationIssue = issue))
            return false
        }
        val cancelled = requireNotNull(order).copy(cancelledAtMillis = state.nowMillis,
            cancellationReason = requireNotNull(draft).reason, cancellationNote = draft.note.trim(),
            refund = requireNotNull(quote).amount.takeIf { it > 0 }?.let {
                OrderRefund(it, RefundStatus.PROCESSING, state.nowMillis, state.nowMillis) })
        val stored = update(state.copy(reservations = state.reservations.map { if (it.id == id) cancelled else it },
            cancellationDraft = null, cancellationIssue = null))
        return stored && state.reservations.any { it.id == id && it.cancelledAtMillis != null }
    }

    fun retryRefund(id: String) {
        refreshTime()
        if (state.dataError != null) return
        val order = state.reservations.firstOrNull { it.id == id } ?: return
        val refund = order.refund ?: return
        if (refund.status != RefundStatus.FAILED) return
        replace(order.copy(refund = refund.copy(status = RefundStatus.PROCESSING, updatedAtMillis = state.nowMillis)))
    }

    fun refreshTime() {
        if (state.dataError != null) return
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
        }.map { order ->
            val refund = order.refund ?: return@map order
            if (refund.status != RefundStatus.PROCESSING || now - refund.updatedAtMillis < 2_000) return@map order
            val status = when (refunds.check(refund)) {
                RefundResult.COMPLETED -> RefundStatus.COMPLETED
                RefundResult.UNAVAILABLE -> RefundStatus.FAILED
            }
            order.copy(refund = refund.copy(status = status, updatedAtMillis = now))
        }
        val next = state.copy(nowMillis = now, reservations = reservations)
        if (reservations == state.reservations) state = next else update(next)
    }

    private fun replace(reservation: BoxReservation): Boolean =
        update(state.copy(reservations = state.reservations.map { if (it.id == reservation.id) reservation else it }))

    private fun refreshCatalog(): Boolean {
        if (state.dataError != null) return false
        return try {
            val now = clock(); val terms = policies.at(now)
            state = state.copy(catalog = catalog.getBoxes(), nowMillis = now, financialTerms = terms,
                draft = if (terms != state.financialTerms) state.draft?.copy(accepted = false) else state.draft)
            true
        }
        catch (_: Exception) { state = state.copy(dataError = READ_ERROR); false }
    }

    fun selectFilter(filter: OrderFilter) {
        state = state.copy(orderFilter = filter)
        saved[FILTER] = filter.name
    }

    fun restoreHistory() {
        try {
            val boxes = catalog.getBoxes()
            val orders = history?.load() ?: state.reservations
            state = state.copy(catalog = boxes, reservations = orders, dataError = null)
        } catch (_: Exception) { state = state.copy(dataError = READ_ERROR); return }
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
        saved.remove<ArrayList<String>>(CANCEL_DRAFT)
        state = BookingUiState(state.catalog, clock())
    }

    private fun update(next: BookingUiState): Boolean {
        if (state.dataError != null) return false
        try {
            if (next.reservations != state.reservations) history?.save(next.reservations)
            state = if (next.reservations != state.reservations && history != null) next.copy(reservations = history.load()) else next
        } catch (_: Exception) { state = state.copy(dataError = WRITE_ERROR); return false }
        if (next.cancellationDraft == null) saved.remove<ArrayList<String>>(CANCEL_DRAFT)
        else saved[CANCEL_DRAFT] = next.cancellationDraft.let { arrayListOf(it.orderId, it.reason?.name.orEmpty(), it.note) }
        if (next.draft == null) saved.remove<ArrayList<String>>(DRAFT)
        else saved[DRAFT] = next.draft.let {
            arrayListOf(it.boxId.toString(), it.quantity.toString(), it.pickup.startMillis.toString(),
                it.pickup.endMillis.toString(), it.accepted.toString())
        }
        // Dynamic orders restore only from durable history, which owns their immutable snapshots.
        saved[HOLDS] = ArrayList(state.reservations.filter { it.boxSnapshot?.batchId == null }.map {
            (listOf(it.id, it.boxId, it.quantity, it.pickup.startMillis, it.pickup.endMillis,
                it.createdAtMillis, it.expiresAtMillis, it.unitPrice) +
                (it.payment?.let { payment -> listOf(payment.reference, payment.amount, payment.status.name, payment.updatedAtMillis) }
                    ?: listOf("", "", "", "")) +
                listOf(it.cancelledAtMillis ?: "", it.collectedAtMillis ?: "", it.cancellationReason?.name.orEmpty(),
                    URLEncoder.encode(it.cancellationNote, "UTF-8")) +
                (it.refund?.let { refund -> listOf(refund.amount, refund.status.name, refund.requestedAtMillis, refund.updatedAtMillis) }
                    ?: listOf("", "", "", "")) +
                (it.financialTerms?.let { terms -> listOf(terms.version, terms.commissionBps, terms.cancellationMinutes,
                    terms.cancellationRefundBps, terms.noShowGraceMinutes, terms.noShowRefundBps) } ?: emptyList())).joinToString("|")
        })
        return true
    }

    private companion object {
        const val READ_ERROR = "Chưa đọc được dữ liệu đặt hộp. Vui lòng thử lại."
        const val WRITE_ERROR = "Chưa lưu được thay đổi. Vui lòng tải lại để kiểm tra trạng thái đơn."
        const val DRAFT = "booking.draft.v1"
        const val HOLDS = "booking.holds.v1"
        const val FILTER = "orders.filter.v1"
        const val CANCEL_DRAFT = "orders.cancellation.draft.v1"
        fun restoreDraft(parts: List<String>): BookingDraft? = runCatching {
            require(parts.size == 5 && parts[1].toInt() > 0)
            BookingDraft(parts[0].toInt(), parts[1].toInt(), PickupWindow(parts[2].toLong(), parts[3].toLong()), parts[4].toBooleanStrict())
        }.getOrNull()
        fun restoreReservation(row: String, boxes: List<CatalogBox>): BoxReservation? = runCatching {
            val parts = row.split('|')
            require(parts.size in listOf(7, 12, 14, 20, 26))
            require(parts[1].toInt() >= 0) // Negative IDs belong to durable, batch-backed orders.
            val price = if (parts.size == 7) boxes.firstOrNull { it.id == parts[1].toInt() }?.price ?: 0 else parts[7].toInt()
            val payment = if (parts.size >= 12 && parts[8].isNotBlank()) {
                QrPayment(parts[8], parts[9].toLong(), PaymentStatus.valueOf(parts[10]), parts[11].toLong())
            } else null
            BoxReservation(parts[0], parts[1].toInt(), parts[2].toInt(), PickupWindow(parts[3].toLong(), parts[4].toLong()),
                parts[5].toLong(), parts[6].toLong(), price, payment,
                parts.getOrNull(12)?.takeIf { it.isNotBlank() }?.toLong(),
                parts.getOrNull(13)?.takeIf { it.isNotBlank() }?.toLong(),
                parts.getOrNull(14)?.takeIf(String::isNotBlank)?.let(CancellationReason::valueOf),
                parts.getOrNull(15)?.let { URLDecoder.decode(it, "UTF-8") }.orEmpty(),
                if (parts.size >= 20 && parts[16].isNotBlank()) OrderRefund(parts[16].toLong(),
                    RefundStatus.valueOf(parts[17]), parts[18].toLong(), parts[19].toLong()) else null,
                financialTerms = if (parts.size == 26) FinancialTerms(parts[20].toLong(), parts[21].toInt(), parts[22].toInt(),
                    parts[23].toInt(), parts[24].toInt(), parts[25].toInt()) else null)
        }.getOrNull()
    }
}
