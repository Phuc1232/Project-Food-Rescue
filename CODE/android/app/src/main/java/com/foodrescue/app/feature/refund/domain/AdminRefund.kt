package com.foodrescue.app.feature.refund.domain

import com.foodrescue.app.feature.order.domain.BoxReservation

internal enum class RefundSource { COMPLAINT, STORE_CANCELLATION, NO_SHOW_POLICY }
internal enum class AdminRefundStatus { PENDING, PROCESSING, COMPLETED, FAILED, REJECTED }
internal data class RefundEvent(val status: AdminRefundStatus, val actor: String, val atMillis: Long, val note: String)

/** Persisted with the order. The same id identifies every retry; history is append-only. */
internal data class ManagedRefund(
    val id: String, val source: RefundSource, val sourceId: String, val reason: String,
    val amount: Long, val requestedAtMillis: Long, val status: AdminRefundStatus,
    val history: List<RefundEvent>,
) {
    init {
        require(id.isNotBlank() && sourceId.isNotBlank() && amount > 0 && requestedAtMillis >= 0)
        require(status != AdminRefundStatus.PENDING && history.isNotEmpty() && history.last().status == status)
        require(history.all { it.atMillis >= requestedAtMillis && it.actor.isNotBlank() && it.note.isNotBlank() })
        require(history.zipWithNext().all { (a, b) -> a.atMillis <= b.atMillis })
        require(status != AdminRefundStatus.REJECTED || source == RefundSource.STORE_CANCELLATION)
    }
    val updatedAtMillis: Long get() = history.last().atMillis
    val reservesMoney: Boolean get() = status != AdminRefundStatus.REJECTED
}

internal data class RefundCase(
    val accountKey: String, val orderId: String, val id: String, val source: RefundSource,
    val sourceId: String, val reason: String, val amount: Long, val requestedAtMillis: Long,
    val order: BoxReservation?, val transaction: ManagedRefund? = null,
    val otherReserved: Long = 0,
) {
    val key: String get() = "$accountKey:$orderId:$id"
    val status: AdminRefundStatus get() = transaction?.status ?: AdminRefundStatus.PENDING
    val completedAmount: Long get() = order?.completedRefundAmount ?: 0
    // Includes this request; other accepted requests and retryable failures stay reserved.
    val availableAmount: Long get() = ((order?.total ?: 0) - otherReserved).coerceAtLeast(0)
    val canProcess: Boolean get() = order?.isPaid == true && amount in 1..availableAmount &&
        (status == AdminRefundStatus.PENDING || status == AdminRefundStatus.FAILED)
}

internal interface AdminRefundRepository {
    suspend fun load(): List<RefundCase>
    suspend fun decide(expected: RefundCase, approve: Boolean, note: String): RefundCase
}
internal fun interface RefundProcessor {
    /** A replacement provider must use this stable key for idempotency, including retries. */
    fun check(key: String, paymentReference: String, amount: Long): RefundResult
}
internal class StaleRefund : IllegalStateException()
internal fun validRefundNote(note: String): Boolean = note.trim().length in 10..500 &&
    note.none { it.isISOControl() && it != '\n' }

internal val BoxReservation.completedRefundAmount: Long get() =
    (refund?.takeIf { it.status == RefundStatus.COMPLETED }?.amount ?: 0) +
        adminRefunds.filter { it.status == AdminRefundStatus.COMPLETED }.sumOf { it.amount }
internal val BoxReservation.pendingRefundAmount: Long get() =
    (refund?.takeIf { it.status != RefundStatus.COMPLETED }?.amount ?: 0) +
        adminRefunds.filter { it.status == AdminRefundStatus.PROCESSING || it.status == AdminRefundStatus.FAILED }.sumOf { it.amount }

internal data class RefundMovement(val amount: Long, val requestedAtMillis: Long, val completedAtMillis: Long? = null)
internal val BoxReservation.refundMovements: List<RefundMovement> get() =
    listOfNotNull(refund?.let { RefundMovement(it.amount, it.requestedAtMillis,
        it.updatedAtMillis.takeIf { _ -> it.status == RefundStatus.COMPLETED }) }) +
        adminRefunds.filter { it.reservesMoney }.map { RefundMovement(it.amount, it.requestedAtMillis,
            it.updatedAtMillis.takeIf { _ -> it.status == AdminRefundStatus.COMPLETED }) }

internal fun RefundCase.decide(approve: Boolean, note: String, actor: String, now: Long): BoxReservation {
    require(validRefundNote(note) && actor.isNotBlank() && now >= requestedAtMillis)
    val current = requireNotNull(order)
    require(current.isPaid)
    if (approve) require(canProcess)
    else require(source == RefundSource.STORE_CANCELLATION && status == AdminRefundStatus.PENDING)
    if (source == RefundSource.STORE_CANCELLATION && status == AdminRefundStatus.PENDING) {
        require(current.ownerCancellation?.requestedAtMillis == requestedAtMillis &&
            current.cancelledAtMillis == null && current.collectedAtMillis == null && current.noShow == null)
    }
    if (source == RefundSource.COMPLAINT) require(current.collectedAtMillis != null || current.cancelledAtMillis != null || current.noShow != null)
    if (source == RefundSource.NO_SHOW_POLICY) require(current.noShow != null && current.financialTerms?.noShowRefundBps?.let { it > 0 } == true)
    val next = if (approve) AdminRefundStatus.PROCESSING else AdminRefundStatus.REJECTED
    val updated = ManagedRefund(id, source, sourceId, reason, amount, requestedAtMillis, next,
        transaction?.history.orEmpty() + RefundEvent(next, actor, now, note.trim()))
    val refunds = current.adminRefunds.filterNot { it.id == id } + updated
    return if (source == RefundSource.STORE_CANCELLATION && status == AdminRefundStatus.PENDING) {
        current.copy(adminRefunds = refunds, ownerCancellation = null,
            cancelledAtMillis = if (approve) now else null,
            cancellationNote = if (approve) note.trim() else current.cancellationNote)
    } else current.copy(adminRefunds = refunds)
}
