package com.foodrescue.app.feature.finance.data

import android.content.Context
import com.foodrescue.app.core.datastore.LocalCommerceLock
import com.foodrescue.app.feature.auth.domain.AdminIdentity
import com.foodrescue.app.feature.complaint.data.LocalComplaintHolds
import com.foodrescue.app.feature.finance.domain.*
import com.foodrescue.app.feature.order.data.LocalOrderLedger
import com.foodrescue.app.feature.refund.domain.AdminRefundStatus
import com.foodrescue.app.feature.store.data.*
import com.foodrescue.app.feature.store.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

internal class LocalAdminSettlementRepository(context: Context, private val actor: String,
    private val clock: () -> Long = System::currentTimeMillis) : AdminSettlementRepository {
    private val directory = LocalPublicStoreRepository(context)
    private val stores = LocalStoreRecords(context)
    private val ledger = LocalOrderLedger(context)
    private val records = LocalSettlementStore(context)
    private val complaints = LocalComplaintHolds(context)

    override suspend fun load(): AdminFinanceSnapshot = withContext(Dispatchers.IO) {
        check(AdminIdentity.matches(actor)); synchronized(LocalCommerceLock) { project() }
    }
    override suspend fun decide(expected: ManagedSettlement, decision: SettlementDecision): ManagedSettlement = withContext(Dispatchers.IO) {
        check(AdminIdentity.matches(actor))
        synchronized(LocalCommerceLock) {
            val snapshot = project()
            val current = snapshot.settlements.find { it.key == expected.key } ?: throw StaleFinanceData()
            if (current != expected) throw StaleFinanceData()
            if (decision.action == SettlementAction.COMPLETE) require(snapshot.settlements.none {
                it.record.status == SettlementStatus.PAID && it.record.transferReference.equals(decision.transferReference.trim(), true)
            })
            val updated = current.decide(decision, actor, clock(), "DS-" + UUID.randomUUID().toString().take(12).uppercase())
            val existing = records.read(updated.storeId)
            records.write(updated.storeId, existing.filterNot { it.id == updated.id } + updated)
            current.copy(record = updated)
        }
    }
    private fun project(): AdminFinanceSnapshot {
        val now = clock(); val allOrders = ledger.read()
        val entries = mutableListOf<RevenueEntry>()
        val result = directory.registeredAccounts().flatMap { (key, storeId) ->
            val store = stores.read(key) ?: return@flatMap emptyList()
            val rows = allOrders.filter { it.order.boxSnapshot?.storeId == storeId }
            val held = rows.groupBy { it.account }.flatMap { (account, orders) ->
                complaints.read(account, orders.map { it.order.id }.toSet(), orders.flatMap { it.order.adminRefunds }
                    .filter { it.status == AdminRefundStatus.COMPLETED }.map { it.id }.toSet())
            }.toSet()
            val queued = rows.groupBy { it.account }.flatMap { (account, orders) ->
                complaints.queuedRefunds(account, orders.map { it.order }).entries.map { it.key to it.value }
            }.toMap()
            val report = buildRevenueSnapshot(storeId, rows.map { it.order }, held, records.read(storeId), store.payout, 2000, now, queued)
            entries += report.entries
            val adjustmentsUnassigned = report.entries.filter { entry ->
                entry.settlementAdjustment > 0 && report.settlements.any { it.id == entry.settlementId && it.status == SettlementStatus.PAID }
            }.sumOf { it.settlementAdjustment } - report.settlements.filter { it.status != SettlementStatus.VOIDED && it.status != SettlementStatus.PENDING }.sumOf { it.adjustment }
            report.settlements.map { record ->
                val issues = buildSet {
                    if (store.status != StoreReviewStatus.APPROVED) add(SettlementIssue.STORE)
                    val bank = if (record.status == SettlementStatus.PENDING) store.payout else record.destination?.let {
                        StorePayoutAccount(it.bank, it.holder, it.accountNumber.orEmpty())
                    }
                    if (bank == null || bank.number.length !in 6..25 || bank.number.any { !it.isDigit() } ||
                        bank.holder.isBlank() || bank.bank.isBlank()) add(SettlementIssue.BANK)
                    if (record.net < 0) add(SettlementIssue.AMOUNT)
                    if (record.status != SettlementStatus.PENDING && record.lines.any { line ->
                        report.entries.none { it.order.id == line.orderId && it.status == RevenueEntryStatus.ELIGIBLE &&
                            it.refunded == line.refunded && it.commission == line.commission }
                    }) add(SettlementIssue.ORDER_CHANGED)
                    if (adjustmentsUnassigned > (if (record.status == SettlementStatus.PENDING) record.adjustment else 0)) add(SettlementIssue.ADJUSTMENT)
                }
                ManagedSettlement(store.profile.name, record, store.payout, issues)
            }
        }
        return AdminFinanceSnapshot(result.sortedByDescending { it.record.updatedAt ?: it.record.period.start }, entries, now)
    }
}
