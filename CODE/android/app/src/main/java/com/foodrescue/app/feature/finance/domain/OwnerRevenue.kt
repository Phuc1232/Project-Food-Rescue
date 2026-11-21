package com.foodrescue.app.feature.finance.domain

import com.foodrescue.app.feature.order.domain.BoxReservation
import com.foodrescue.app.feature.store.domain.StorePayoutAccount
import com.foodrescue.app.feature.refund.domain.*

internal enum class RevenueEntryStatus { WAITING_PICKUP, HELD, ELIGIBLE, REFUND_PENDING, REFUNDED, CANCELLED }
internal enum class SettlementStatus { PENDING, READY, PROCESSING, PAID, FAILED, VOIDED }
internal data class RevenueEntry(
    val order: BoxReservation,
    val status: RevenueEntryStatus,
    val commissionBps: Int,
    val commission: Long,
    val refunded: Long,
    val refundPending: Long,
    val settlementId: String? = null,
    val queuedRefunds: List<RefundMovement> = emptyList(),
    val settlementAdjustment: Long = 0,
) {
    val gross: Long get() = order.total
    val paidAt: Long get() = requireNotNull(order.payment).updatedAtMillis
    val earnedAt: Long? get() = order.collectedAtMillis.takeIf { status == RevenueEntryStatus.ELIGIBLE }
    val net: Long get() = if (status == RevenueEntryStatus.ELIGIBLE) gross - refunded - commission else 0
}

/** Finalized lines retain the agreed fee at reconciliation, independent of current settings. */
internal data class SettlementLine(val orderId: String, val reference: String, val boxName: String,
    val quantity: Int, val gross: Long, val commissionBps: Int, val commission: Long, val refunded: Long = 0) {
    val net: Long get() = gross - refunded - commission
}
internal data class SettlementDestination(val bank: String, val holder: String, val lastFour: String,
    val accountNumber: String? = null)
internal data class SettlementEvent(val status: SettlementStatus, val actor: String, val atMillis: Long, val note: String)
internal data class SettlementAdjustment(val sourceId: String, val orderId: String, val amount: Long) {
    init { require(sourceId.isNotBlank() && orderId.isNotBlank() && amount > 0) }
}
internal data class RevenueSettlement(val id: String, val storeId: Int, val period: RevenueWindow,
    val lines: List<SettlementLine>, val status: SettlementStatus,
    val destination: SettlementDestination? = null, val updatedAt: Long? = null,
    val paidAt: Long? = null, val transferReference: String? = null, val failureReason: String? = null,
    val revision: Long = 0, val history: List<SettlementEvent> = emptyList(),
    val adjustments: List<SettlementAdjustment> = emptyList()) {
    val gross: Long get() = revenueSum(lines.map { it.gross })
    val commission: Long get() = revenueSum(lines.map { it.commission })
    val adjustment: Long get() = revenueSum(adjustments.map { it.amount })
    val net: Long get() = revenueSum(lines.map { it.net }) - adjustment
}
internal data class RevenueSnapshot(val entries: List<RevenueEntry> = emptyList(),
    val settlements: List<RevenueSettlement> = emptyList(), val payout: StorePayoutAccount? = null,
    val commissionBps: Int = 2000, val loadedAt: Long = 0) {
    val awaitingPayout: Long get() = revenueSum(settlements.filter { it.status != SettlementStatus.PAID && it.status != SettlementStatus.VOIDED }.map { it.net })
    val paidOut: Long get() = revenueSum(settlements.filter { it.status == SettlementStatus.PAID }.map { it.net })
    val settlementAdjustment: Long get() = revenueSum(entries.map { it.settlementAdjustment })
}
internal fun interface RevenueRepository { suspend fun load(): RevenueSnapshot }
internal fun revenueSum(values: List<Long>): Long = values.fold(0L, Math::addExact)
internal fun revenueCommission(gross: Long, basisPoints: Int): Long {
    require(gross >= 0 && basisPoints in 0..10_000)
    // Split before multiplying; amounts and rounding stay integer and cannot overflow.
    return gross / 10_000 * basisPoints + (gross % 10_000 * basisPoints + 5_000) / 10_000
}

internal fun buildRevenueSnapshot(storeId: Int, orders: List<BoxReservation>, openComplaintIds: Set<String>,
    records: List<RevenueSettlement>, payout: StorePayoutAccount?, commissionBps: Int, now: Long,
    queuedRefunds: Map<String, List<RefundMovement>> = emptyMap()): RevenueSnapshot {
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
        require(record.revision >= 0 && record.net >= 0)
        if (record.history.isNotEmpty()) {
            require(record.revision == record.history.size.toLong() && record.history.last().status == record.status)
            require(record.history.all { it.actor.isNotBlank() && validFinanceReason(it.note) && it.atMillis in 0..record.updatedAt })
            require(record.history.zipWithNext().all { (a, b) -> a.atMillis <= b.atMillis })
        }
        val destination = requireNotNull(record.destination)
        require(destination.bank.isNotBlank() && destination.holder.isNotBlank() && destination.lastFour.matches(Regex("[0-9]{4}")))
        destination.accountNumber?.let { require(it.matches(Regex("[0-9]{6,25}")) && it.endsWith(destination.lastFour)) }
        if (record.status == SettlementStatus.PAID) {
            require(record.paidAt != null && record.paidAt in 0..record.updatedAt && !record.transferReference.isNullOrBlank())
        } else require(record.paidAt == null)
        if (record.status == SettlementStatus.FAILED) require(!record.failureReason.isNullOrBlank())
        record.lines.forEach { line ->
            val order = requireNotNull(byId[line.orderId])
            require(order.collectedAtMillis != null && order.collectedAtMillis in record.period && order.collectedAtMillis <= now)
            require(line.gross == order.total && line.quantity == order.quantity && line.reference == order.reference)
            require(line.refunded in 0..order.completedRefundAmount)
            require(line.commission == revenueCommission(line.gross - line.refunded, line.commissionBps))
            if (record.status != SettlementStatus.VOIDED) require(assigned.put(line.orderId, record to line) == null)
        }
    }
    val entries = paid.map { order ->
        val refunded = order.completedRefundAmount
        val queued = queuedRefunds[order.id].orEmpty()
        val refundPending = order.pendingRefundAmount + revenueSum(queued.map { it.amount })
        require(refunded + refundPending <= order.total)
        val status = when {
            refundPending > 0 -> RevenueEntryStatus.REFUND_PENDING
            refunded == order.total -> RevenueEntryStatus.REFUNDED
            order.cancelledAtMillis != null -> RevenueEntryStatus.CANCELLED
            order.noShow != null || order.ownerCancellation != null || order.id in openComplaintIds -> RevenueEntryStatus.HELD
            order.collectedAtMillis != null && order.collectedAtMillis <= now -> RevenueEntryStatus.ELIGIBLE
            else -> RevenueEntryStatus.WAITING_PICKUP
        }
        val assignedLine = assigned[order.id]
        val rate = assignedLine?.second?.commissionBps ?: order.financialTerms?.commissionBps ?: commissionBps
        val adjustedCommission = revenueCommission(order.total - refunded, rate)
        val adjustment = assignedLine?.second?.let { (it.net - (order.total - refunded - adjustedCommission)).coerceAtLeast(0) } ?: 0
        RevenueEntry(order, status, rate,
            if (status == RevenueEntryStatus.ELIGIBLE) adjustedCommission else 0,
            refunded, refundPending, assignedLine?.first?.id, queued, adjustment)
    }.sortedByDescending { it.paidAt }
    var pending = entries.filter { it.status == RevenueEntryStatus.ELIGIBLE && it.settlementId == null }
        .groupBy { RevenuePeriod.WEEK.window(requireNotNull(it.earnedAt)) }
        .map { (period, lines) -> RevenueSettlement("pending:$storeId:${period.start}", storeId, period,
            lines.map { SettlementLine(it.order.id, it.order.reference, it.order.boxSnapshot?.name ?: "Surprise Box",
                it.order.quantity, it.gross, it.commissionBps, it.commission, it.refunded) }, SettlementStatus.PENDING) }
    val applied = records.filter { it.status != SettlementStatus.VOIDED }.flatMap { it.adjustments }
    applied.forEach { offset ->
        val source = requireNotNull(records.find { it.id == offset.sourceId && it.status == SettlementStatus.PAID })
        require(source.lines.any { it.orderId == offset.orderId })
        val entry = requireNotNull(entries.find { it.order.id == offset.orderId })
        require(revenueSum(applied.filter { it.sourceId == offset.sourceId && it.orderId == offset.orderId }.map { it.amount }) <= entry.settlementAdjustment)
    }
    val adjustments = entries.filter { it.settlementAdjustment > 0 && assigned[it.order.id]?.first?.status == SettlementStatus.PAID }
        .mapNotNull { entry ->
            val sourceId = checkNotNull(entry.settlementId)
            val amount = entry.settlementAdjustment - revenueSum(applied.filter { it.sourceId == sourceId && it.orderId == entry.order.id }.map { it.amount })
            require(amount >= 0)
            if (amount > 0) SettlementAdjustment(sourceId, entry.order.id, amount) else null
        }
    if (adjustments.isNotEmpty() && pending.isNotEmpty()) {
        val period = RevenueWindow(pending.minOf { it.period.start }, pending.maxOf { it.period.endExclusive })
        pending = listOf(RevenueSettlement("pending:$storeId:${period.start}", storeId, period,
            pending.flatMap { it.lines }, SettlementStatus.PENDING, adjustments = adjustments))
    }
    val pendingByOrder = pending.flatMap { settlement -> settlement.lines.map { it.orderId to settlement.id } }.toMap()
    val linkedEntries = entries.map { it.copy(settlementId = it.settlementId ?: pendingByOrder[it.order.id]) }
    return RevenueSnapshot(linkedEntries, (records + pending).sortedByDescending { it.period.start }, payout, commissionBps, now)
}
