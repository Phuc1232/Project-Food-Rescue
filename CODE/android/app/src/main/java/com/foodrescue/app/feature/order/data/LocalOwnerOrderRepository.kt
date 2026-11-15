package com.foodrescue.app.feature.order.data

import com.foodrescue.app.core.datastore.LocalCommerceLock
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.store.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class LocalOwnerOrderRepository(private val ledger: LocalOrderLedger, private val storeId: Int,
    private val owner: OwnerStoreRepository, private val clock: () -> Long = System::currentTimeMillis) : OwnerOrderRepository {
    override suspend fun load(): List<BoxReservation> = withContext(Dispatchers.IO) {
        check(owner.load().status == StoreReviewStatus.APPROVED)
        synchronized(LocalCommerceLock) { ledger.read().map { it.order }.filter { it.boxSnapshot?.storeId == storeId } }
    }
    override suspend fun requestCancellation(draft: OwnerCancellationDraft): List<BoxReservation> = withContext(Dispatchers.IO) {
        check(owner.load().status == StoreReviewStatus.APPROVED)
        synchronized(LocalCommerceLock) {
            val rows = ledger.read()
            val index = rows.indexOfFirst { it.order.id == draft.orderId && it.order.boxSnapshot?.storeId == storeId }
            if (index < 0) throw OwnerOrderException(OwnerCancellationIssue.NOT_ELIGIBLE)
            val changed = rows.toMutableList()
            changed[index] = rows[index].copy(order = rows[index].order.requestOwnerCancellation(draft, clock()))
            ledger.write(changed)
            changed.map { it.order }.filter { it.boxSnapshot?.storeId == storeId }
        }
    }
}
