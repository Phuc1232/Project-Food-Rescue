package com.foodrescue.app.feature.finance.domain

import com.foodrescue.app.feature.store.domain.StorePayoutAccount

internal enum class SettlementAction { CONFIRM, START, COMPLETE, FAIL, VOID }
internal enum class SettlementIssue { STORE, BANK, ORDER_CHANGED, ADJUSTMENT, AMOUNT }
internal data class ManagedSettlement(val storeName: String, val record: RevenueSettlement,
    val payout: StorePayoutAccount?, val issues: Set<SettlementIssue> = emptySet()) {
    val key: String get() = "${record.storeId}:${record.id}"
    val actions: List<SettlementAction> get() = when (record.status) {
        SettlementStatus.PENDING -> if (issues.isEmpty()) listOf(SettlementAction.CONFIRM) else emptyList()
        SettlementStatus.READY, SettlementStatus.FAILED ->
            (if (issues.isEmpty()) listOf(SettlementAction.START) else emptyList()) + SettlementAction.VOID
        SettlementStatus.PROCESSING ->
            (if (issues.isEmpty()) listOf(SettlementAction.COMPLETE) else emptyList()) + SettlementAction.FAIL
        SettlementStatus.PAID, SettlementStatus.VOIDED -> emptyList()
    }
}
internal data class AdminFinanceSnapshot(val settlements: List<ManagedSettlement> = emptyList(),
    val entries: List<RevenueEntry> = emptyList(), val loadedAt: Long = 0)
internal data class SettlementDecision(val action: SettlementAction, val reason: String = "",
    val transferReference: String = "")
internal interface AdminSettlementRepository {
    suspend fun load(): AdminFinanceSnapshot
    suspend fun decide(expected: ManagedSettlement, decision: SettlementDecision): ManagedSettlement
}
internal fun validTransferReference(value: String): Boolean = value.trim().matches(Regex("[A-Za-z0-9][A-Za-z0-9._/-]{5,79}"))
internal fun ManagedSettlement.decide(decision: SettlementDecision, actor: String, now: Long,
    newId: String): RevenueSettlement {
    require(decision.action in actions && validFinanceReason(decision.reason) && actor.isNotBlank())
    require(now >= (record.updatedAt ?: 0))
    val status = when (decision.action) {
        SettlementAction.CONFIRM -> if (record.net == 0L) SettlementStatus.PAID else SettlementStatus.READY
        SettlementAction.START -> SettlementStatus.PROCESSING
        SettlementAction.COMPLETE -> SettlementStatus.PAID
        SettlementAction.FAIL -> SettlementStatus.FAILED
        SettlementAction.VOID -> SettlementStatus.VOIDED
    }
    if (decision.action == SettlementAction.COMPLETE) require(validTransferReference(decision.transferReference))
    val destination = if (decision.action == SettlementAction.CONFIRM) requireNotNull(payout).let {
        SettlementDestination(it.bank, it.holder, it.number.takeLast(4), it.number)
    } else record.destination
    return record.copy(id = if (decision.action == SettlementAction.CONFIRM) newId else record.id,
        status = status, destination = destination, updatedAt = now,
        paidAt = now.takeIf { status == SettlementStatus.PAID },
        transferReference = if (status == SettlementStatus.PAID && record.net == 0L) "BT-$newId"
            else decision.transferReference.trim().takeIf { status == SettlementStatus.PAID },
        failureReason = decision.reason.trim().takeIf { status == SettlementStatus.FAILED },
        revision = record.revision + 1,
        history = record.history + SettlementEvent(status, actor, now, decision.reason.trim()))
}
