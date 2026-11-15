package com.foodrescue.app.feature.order.data

import com.foodrescue.app.core.datastore.LocalCommerceLock
import com.foodrescue.app.feature.discovery.domain.CatalogRepository
import com.foodrescue.app.feature.order.domain.*

internal class SharedOrderHistoryStore(email: String, private val ledger: LocalOrderLedger,
    private val legacy: OrderHistoryStore, private val catalog: CatalogRepository,
    private val clock: () -> Long = System::currentTimeMillis) : OrderHistoryStore {
    private val account = LocalOrderLedger.accountKey(email)
    override fun load(): List<BoxReservation> = synchronized(LocalCommerceLock) {
        ledger.migrate(account, legacy)
        ledger.read().filter { it.account == account }.map { it.order }
    }
    override fun reserve(order: BoxReservation): Boolean = synchronized(LocalCommerceLock) {
        load()
        val rows = ledger.read()
        if (rows.any { it.account == account && it.order.id == order.id }) return@synchronized true
        if (order.boxSnapshot?.batchId != null) {
            val current = catalog.getBoxes().firstOrNull { it.id == order.boxId } ?: return@synchronized false
            if (current.batchId != order.boxSnapshot.batchId || current.storeId != order.boxSnapshot.storeId || current.price != order.unitPrice ||
                bookingIssue(BookingDraft(order.boxId, order.quantity, order.pickup, true), current, emptyList(), clock()) != null) return@synchronized false
        }
        ledger.write(listOf(LocalOrderLedger.Row(account, order)) + rows)
        true
    }
    override fun save(orders: List<BoxReservation>) = synchronized(LocalCommerceLock) {
        load()
        val rows = ledger.read().toMutableList()
        for (incoming in orders) {
            val index = rows.indexOfFirst { it.account == account && it.order.id == incoming.id }
            if (index < 0) {
                // Batch-backed reservations must pass the atomic reserve operation first.
                if (incoming.boxSnapshot?.batchId == null) rows.add(LocalOrderLedger.Row(account, incoming))
                continue
            }
            val old = rows[index].order
            val next = when {
                old.ownerCancellation != null || old.collectedAtMillis != null -> old
                old.cancelledAtMillis != null -> old.copy(refund = incoming.refund?.takeIf {
                    it.amount == old.refund?.amount && it.updatedAtMillis >= (old.refund?.updatedAtMillis ?: Long.MAX_VALUE)
                } ?: old.refund)
                old.isPaid && !incoming.isPaid -> old
                old.boxSnapshot?.batchId != null && !old.isPaid && !old.isActive(clock()) && incoming.isPaid -> old
                else -> old.copy(payment = incoming.payment, cancelledAtMillis = incoming.cancelledAtMillis,
                    cancellationReason = incoming.cancellationReason, cancellationNote = incoming.cancellationNote, refund = incoming.refund)
            }
            rows[index] = rows[index].copy(order = next)
        }
        ledger.write(rows)
    }
}
