package com.foodrescue.app.feature.order.presentation

import androidx.lifecycle.SavedStateHandle
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class OwnerOrderStateTest {
    private val now = 1_800_000_000_000L
    private val order = BoxReservation("order", 1, 1, PickupWindow(now + 1000000, now + 2000000), now, now + 600000, 49000,
        QrPayment("FRORDER", 49000, PaymentStatus.SUCCEEDED, now))
    private class Repository(var orders: List<BoxReservation>) : OwnerOrderRepository {
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun load(): List<BoxReservation> { val before = orders; gate?.await(); return before }
        override suspend fun requestCancellation(draft: OwnerCancellationDraft) = orders
    }
    @Test fun refreshStartedBeforeAccountExitCannotRestoreOldOrders() = runBlocking {
        val repository = Repository(listOf(order)).apply { gate = CompletableDeferred() }
        val vm = OwnerOrdersViewModel(SavedStateHandle(), repository) { now }
        val refresh = launch(start = CoroutineStart.UNDISPATCHED) { vm.refresh() }
        vm.clear(); repository.gate!!.complete(Unit); refresh.join()
        assertTrue(vm.state.orders.isEmpty())
    }
    @Test fun backgroundRefreshKeepsCancellationValidationVisible() = runBlocking {
        val vm = OwnerOrdersViewModel(SavedStateHandle(), Repository(listOf(order))) { now }
        vm.refresh(); vm.open(order.id); vm.beginCancellation(); vm.review()
        val error = vm.state.error
        assertNotNull(error)
        vm.refresh()
        assertEquals(error, vm.state.error)
    }
}
