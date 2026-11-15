package com.foodrescue.app.feature.order.presentation

import androidx.lifecycle.SavedStateHandle
import com.foodrescue.app.feature.discovery.data.LocalCatalogRepository
import com.foodrescue.app.feature.discovery.domain.CatalogRepository
import com.foodrescue.app.feature.discovery.presentation.DiscoveryViewModel
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OrderRecoveryTest {
    private val now = 1794301200000L
    private class History : OrderHistoryStore {
        var orders = emptyList<BoxReservation>()
        var readFailure = false
        var writeFailure = false
        var writes = 0
        var pending = false
        override fun load(): List<BoxReservation> { check(!readFailure); return orders }
        override fun save(orders: List<BoxReservation>) {
            check(!writeFailure); writes++
            if (!pending) this.orders = orders
        }
    }
    private fun model(history: History, saved: SavedStateHandle = SavedStateHandle()) =
        BookingViewModel(saved, LocalCatalogRepository(), PaymentRepository { PaymentResult.CONFIRMED }, history) { now }

    @Test fun unreadableHistoryDoesNotCrashOrRewriteAndRetryRestoresOrders() {
        val history = History().apply { readFailure = true }
        val vm = model(history)
        vm.prepare(1); vm.acceptPolicy(true)
        assertNull(vm.confirm())
        assertEquals(0, history.writes)
        history.readFailure = false
        vm.restoreHistory(); vm.prepare(1); vm.acceptPolicy(true)
        assertNotNull(vm.confirm())
    }

    @Test fun failedReservationWriteDoesNotReportSuccess() {
        val history = History(); val vm = model(history)
        vm.prepare(1); vm.acceptPolicy(true)
        history.writeFailure = true
        assertNull(vm.confirm())
        assertTrue(vm.state.reservations.isEmpty())
        assertTrue(history.orders.isEmpty())
    }

    @Test fun failedRefreshPreservesLastReadableHistoryAndLogoutStillWorks() {
        val history = History(); val vm = model(history)
        vm.prepare(1); vm.acceptPolicy(true); vm.confirm()
        val previous = vm.state.reservations
        history.readFailure = true
        vm.restoreHistory()
        assertEquals(previous, vm.state.reservations)
        vm.clear()
        assertTrue(vm.state.reservations.isEmpty())
    }

    @Test fun snapshotlessSavedStateCannotResurrectBatchOrders() {
        val saved = SavedStateHandle()
        saved["booking.holds.v1"] = arrayListOf("dynamic|-1000000|1|${now + 3600000}|${now + 7200000}|$now|${now + 600000}|49000||||||||||||")
        val history = History()
        assertTrue(model(history, saved).state.reservations.isEmpty())
        assertTrue(history.orders.isEmpty())
    }

    @Test fun ownerRequestArrivingDuringCustomerCancellationDoesNotReportRefundSuccess() {
        val history = History(); val vm = model(history)
        vm.prepare(1); vm.acceptPolicy(true); val id = vm.confirm()!!
        val paid = vm.state.reservations.single().copy(payment = QrPayment("FRTEST", 39000, PaymentStatus.SUCCEEDED, now))
        history.orders = listOf(paid); vm.restoreHistory()
        vm.prepareCancellation(id); vm.chooseCancellationReason(CancellationReason.entries.first())
        history.orders = listOf(paid.copy(ownerCancellation = OwnerCancellationRequest(OwnerCancellationReason.OUT_OF_STOCK, "", now)))
        history.pending = true
        assertFalse(vm.cancelPaidOrder(id))
        assertNull(vm.state.reservations.single().refund)
    }

    @Test fun catalogReadFailureCanRecoverWithoutCrashingDiscovery() = runBlocking {
        var broken = true
        val source = object : CatalogRepository {
            override fun getBoxes() = if (broken) error("unreadable") else LocalCatalogRepository().getBoxes()
        }
        val vm = DiscoveryViewModel(SavedStateHandle(), source)
        assertTrue(vm.state.catalog.isEmpty())
        broken = false; vm.refreshPublicStores()
        assertTrue(vm.state.catalog.isNotEmpty())
        broken = true; vm.refreshPublicStores()
        assertTrue(vm.state.catalog.isNotEmpty())
        vm.clear()
        assertTrue(vm.state.query.isEmpty())
    }
}
