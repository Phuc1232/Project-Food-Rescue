package com.foodrescue.app.feature.finance.presentation

import androidx.lifecycle.SavedStateHandle
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.finance.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class RevenueStateTest {
    private val now = 1_800_000_000_000L
    private val order = BoxReservation("one", 1, 1, PickupWindow(now - 1000, now + 1000), now - 3000, now - 2000,
        49000, QrPayment("FRONE", 49000, PaymentStatus.SUCCEEDED, now - 2500), collectedAtMillis = now - 500)
    private fun snapshot() = buildRevenueSnapshot(7, listOf(order), emptySet(), emptyList(), null, 2000, now)
    @Test fun inFlightRefreshCannotRestoreAccountDataAfterExit() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val vm = RevenueViewModel(SavedStateHandle(), RevenueRepository { gate.await(); snapshot() }) { now }
        val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.refresh() }
        vm.clear(); gate.complete(Unit); job.join()
        assertNull(vm.state.snapshot)
    }
    @Test fun newerRefreshWinsEvenWhenOlderReadFinishesLater() = runBlocking {
        val gate = CompletableDeferred<Unit>(); var reads = 0
        val vm = RevenueViewModel(SavedStateHandle(), RevenueRepository {
            if (++reads == 1) { gate.await(); snapshot() } else RevenueSnapshot(loadedAt = now + 1)
        }) { now }
        val older = launch(start = CoroutineStart.UNDISPATCHED) { vm.refresh() }
        vm.refresh(); gate.complete(Unit); older.join()
        assertEquals(now + 1, vm.state.snapshot!!.loadedAt); assertTrue(vm.state.snapshot!!.entries.isEmpty())
    }
    @Test fun failedRefreshHidesOutdatedFinancialValuesAndRetryRestoresThem() = runBlocking {
        var fail = false
        val vm = RevenueViewModel(SavedStateHandle(), RevenueRepository { check(!fail); snapshot() }) { now }
        vm.refresh(); assertNotNull(vm.state.report)
        fail = true; vm.refresh(); assertNull(vm.state.report); assertNotNull(vm.state.error)
        fail = false; vm.refresh(); assertEquals(39200L, vm.state.report!!.net); assertNull(vm.state.error)
    }
    @Test fun filtersAndDetailNavigationSurviveRecreationButExitClearsThem() = runBlocking {
        val saved = SavedStateHandle(); val repo = RevenueRepository { snapshot() }
        val vm = RevenueViewModel(saved, repo) { now }
        vm.refresh(); vm.period(RevenuePeriod.MONTH); vm.movePeriod(-1); vm.search("FRONE")
        vm.transactions(RevenueBasis.COLLECTION); vm.filter(RevenueFilter.ELIGIBLE); vm.openEntry("one")
        val restored = RevenueViewModel(saved, repo) { now }; restored.refresh()
        assertEquals(RevenuePage.TRANSACTION, restored.state.page); assertEquals("one", restored.state.selectedEntry!!.order.id)
        assertEquals(-1, restored.state.offset); assertEquals(RevenueBasis.COLLECTION, restored.state.basis)
        assertEquals("FRONE", restored.state.query)
        restored.back(); assertEquals(RevenuePage.TRANSACTIONS, restored.state.page)
        restored.clear(); assertEquals(RevenuePage.OVERVIEW, RevenueViewModel(saved, repo).state.page)
    }
    @Test fun pendingSettlementAndTransactionCanNavigateBothDirections() = runBlocking {
        val vm = RevenueViewModel(SavedStateHandle(), RevenueRepository { snapshot() }) { now }
        vm.refresh(); vm.settlements(); val id = vm.state.snapshot!!.settlements.single().id
        vm.openSettlement(id); vm.openEntry("one"); vm.back()
        assertEquals(RevenuePage.SETTLEMENT, vm.state.page)
        vm.back(); assertEquals(RevenuePage.SETTLEMENTS, vm.state.page)
        vm.transactions(); vm.openEntry("one"); vm.openSettlement(id); vm.back()
        assertEquals(RevenuePage.TRANSACTION, vm.state.page)
        vm.back(); assertEquals(RevenuePage.TRANSACTIONS, vm.state.page)
    }
    @Test fun futurePeriodsAreBlockedAndPeriodChangeResetsOffset() {
        val vm = RevenueViewModel(SavedStateHandle(), RevenueRepository { snapshot() }) { now }
        vm.movePeriod(1); assertEquals(0, vm.state.offset)
        vm.movePeriod(-1); assertEquals(-1, vm.state.offset)
        vm.period(RevenuePeriod.DAY); assertEquals(0, vm.state.offset)
    }
}
