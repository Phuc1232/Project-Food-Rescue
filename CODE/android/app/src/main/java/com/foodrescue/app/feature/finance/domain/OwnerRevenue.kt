package com.foodrescue.app.feature.finance.domain

import com.foodrescue.app.feature.order.domain.BoxReservation
import com.foodrescue.app.feature.store.domain.StorePayoutAccount

internal enum class RevenueEntryStatus { WAITING_PICKUP, HELD, ELIGIBLE, REFUND_PENDING, REFUNDED, CANCELLED }
internal enum class SettlementStatus { PENDING, READY, PROCESSING, PAID, FAILED }
internal data class RevenueEntry(
    val order: BoxReservation,
    val status: RevenueEntryStatus,
    val commissionBps: Int,
    val commission: Long,
    val refunded: Long,
    val refundPending: Long,
    val settlementId: String? = null,
) {
    val gross: Long get() = order.total
    val paidAt: Long get() = requireNotNull(order.payment).updatedAtMillis
    val earnedAt: Long? get() = order.collectedAtMillis.takeIf { status == RevenueEntryStatus.ELIGIBLE }
    val net: Long get() = if (status == RevenueEntryStatus.ELIGIBLE) gross - commission else 0
}

/** Finalized lines retain the agreed fee at reconciliation, independent of current settings. */
internal data class SettlementLine(val orderId: String, val reference: String, val boxName: String,
    val quantity: Int, val gross: Long, val commissionBps: Int, val commission: Long) {
    val net: Long get() = gross - commission
}
internal data class SettlementDestination(val bank: String, val holder: String, val lastFour: String)
internal data class RevenueSettlement(val id: String, val storeId: Int, val period: RevenueWindow,
    val lines: List<SettlementLine>, val status: SettlementStatus,
    val destination: SettlementDestination? = null, val updatedAt: Long? = null,
    val paidAt: Long? = null, val transferReference: String? = null, val failureReason: String? = null) {
    val gross: Long get() = revenueSum(lines.map { it.gross })
    val commission: Long get() = revenueSum(lines.map { it.commission })
    val net: Long get() = revenueSum(lines.map { it.net })
}
internal data class RevenueSnapshot(val entries: List<RevenueEntry> = emptyList(),
    val settlements: List<RevenueSettlement> = emptyList(), val payout: StorePayoutAccount? = null,
    val commissionBps: Int = 2000, val loadedAt: Long = 0) {
    val awaitingPayout: Long get() = revenueSum(settlements.filter { it.status != SettlementStatus.PAID }.map { it.net })
    val paidOut: Long get() = revenueSum(settlements.filter { it.status == SettlementStatus.PAID }.map { it.net })
}
internal fun interface RevenueRepository { suspend fun load(): RevenueSnapshot }
internal fun revenueSum(values: List<Long>): Long = values.fold(0L, Math::addExact)
internal fun revenueCommission(gross: Long, basisPoints: Int): Long {
    require(gross >= 0 && basisPoints in 0..10_000)
    // Split before multiplying; amounts and rounding stay integer and cannot overflow.
    return gross / 10_000 * basisPoints + (gross % 10_000 * basisPoints + 5_000) / 10_000
}

internal fun buildRevenueSnapshot(storeId: Int, orders: List<BoxReservation>, openComplaintIds: Set<String>,
    records: List<RevenueSettlement>, payout: StorePayoutAccount?, commissionBps: Int, now: Long): RevenueSnapshot {
    require(commissionBps in 0..10_000 && now >= 0)
    require(orders.distinctBy { it.id }.size == orders.size)
    val paid = orders.filter { it.isPaid && requireNotNull(it.payment).updatedAtMillis <= now }
    val byId = paid.associateBy { it.id }
    val assigned = mutableMapOf<String, Pair<RevenueSettlement, SettlementLine>>()
    require(records.distinctBy { it.id }.size == records.size)
    records.forEach { record ->
        require(record.id.isNotBlank() && !record.id.startsWith("pending:") && record.storeId == storeId)
        require(record.status != SettlementStatus.PENDING && record.lines.isNotEmpty())
        require(record.updatedAt != null && record.updatedAt in 0..now)
        val destination = requireNotNull(record.destination)
        require(destination.bank.isNotBlank() && destination.holder.isNotBlank() && destination.lastFour.matches(Regex("[0-9]{4}")))
        if (record.status == SettlementStatus.PAID) {
            require(record.paidAt != null && record.paidAt in 0..record.updatedAt && !record.transferReference.isNullOrBlank())
        } else require(record.paidAt == null)
        if (record.status == SettlementStatus.FAILED) require(!record.failureReason.isNullOrBlank())
        record.lines.forEach { line ->
            val order = requireNotNull(byId[line.orderId])
            require(order.collectedAtMillis != null && order.collectedAtMillis in record.period && order.collectedAtMillis <= now)
            require(line.gross == order.total && line.quantity == order.quantity && line.reference == order.reference)
            require(line.commission == revenueCommission(line.gross, line.commissionBps))
            require(assigned.put(line.orderId, record to line) == null)
        }
    }
    val entries = paid.map { order ->
        val refund = order.refund
        val status = when {
            refund != null && refund.status != com.foodrescue.app.feature.refund.domain.RefundStatus.COMPLETED -> RevenueEntryStatus.REFUND_PENDING
            refund != null && refund.amount == order.total -> RevenueEntryStatus.REFUNDED
            order.cancelledAtMillis != null -> RevenueEntryStatus.CANCELLED
            order.ownerCancellation != null || order.id in openComplaintIds -> RevenueEntryStatus.HELD
            order.collectedAtMillis != null && order.collectedAtMillis <= now -> RevenueEntryStatus.ELIGIBLE
            else -> RevenueEntryStatus.WAITING_PICKUP
        }
        val assignedLine = assigned[order.id]
        val rate = assignedLine?.second?.commissionBps ?: commissionBps
        RevenueEntry(order, status, rate,
            assignedLine?.second?.commission ?: if (status == RevenueEntryStatus.ELIGIBLE) revenueCommission(order.total, rate) else 0,
            if (refund?.status == com.foodrescue.app.feature.refund.domain.RefundStatus.COMPLETED) refund.amount else 0,
            if (refund != null && refund.status != com.foodrescue.app.feature.refund.domain.RefundStatus.COMPLETED) refund.amount else 0,
            assignedLine?.first?.id)
    }.sortedByDescending { it.paidAt }
    val pending = entries.filter { it.status == RevenueEntryStatus.ELIGIBLE && it.settlementId == null }
        .groupBy { RevenuePeriod.WEEK.window(requireNotNull(it.earnedAt)) }
        .map { (period, lines) -> RevenueSettlement("pending:$storeId:${period.start}", storeId, period,
            lines.map { SettlementLine(it.order.id, it.order.reference, it.order.boxSnapshot?.name ?: "Surprise Box",
                it.order.quantity, it.gross, it.commissionBps, it.commission) }, SettlementStatus.PENDING) }
    val pendingByOrder = pending.flatMap { settlement -> settlement.lines.map { it.orderId to settlement.id } }.toMap()
    val linkedEntries = entries.map { it.copy(settlementId = it.settlementId ?: pendingByOrder[it.order.id]) }
    return RevenueSnapshot(linkedEntries, (records + pending).sortedByDescending { it.period.start }, payout, commissionBps, now)
}
