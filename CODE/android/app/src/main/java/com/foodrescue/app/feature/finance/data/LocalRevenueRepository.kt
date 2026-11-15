package com.foodrescue.app.feature.finance.data

import com.foodrescue.app.core.datastore.LocalCommerceLock
import com.foodrescue.app.feature.complaint.data.LocalComplaintHolds
import com.foodrescue.app.feature.order.data.LocalOrderLedger
import com.foodrescue.app.feature.finance.domain.*
import com.foodrescue.app.feature.store.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class LocalRevenueRepository(private val storeId: Int, private val owner: OwnerStoreRepository,
    private val ledger: LocalOrderLedger, private val settlements: LocalSettlementStore,
    private val complaints: LocalComplaintHolds, private val commissionBps: Int,
    private val clock: () -> Long = System::currentTimeMillis) : RevenueRepository {
    override suspend fun load(): RevenueSnapshot = withContext(Dispatchers.IO) {
        val store = owner.load()
        check(store.status == StoreReviewStatus.APPROVED)
        synchronized(LocalCommerceLock) {
            val rows = ledger.read().filter { it.order.boxSnapshot?.storeId == storeId }
            val held = rows.groupBy { it.account }.flatMap { (account, orders) ->
                complaints.read(account, orders.map { it.order.id }.toSet())
            }.toSet()
            buildRevenueSnapshot(storeId, rows.map { it.order }, held, settlements.read(storeId),
                store.payout, commissionBps, clock())
        }
    }
}
