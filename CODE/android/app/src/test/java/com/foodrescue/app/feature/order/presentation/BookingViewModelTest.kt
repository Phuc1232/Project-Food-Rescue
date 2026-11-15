package com.foodrescue.app.feature.order.presentation

import androidx.lifecycle.SavedStateHandle
import com.foodrescue.app.feature.discovery.data.LocalCatalogRepository
import com.foodrescue.app.feature.discovery.domain.CatalogRepository
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.PaymentRepository
import com.foodrescue.app.feature.payment.domain.PaymentResult
import org.junit.Assert.*
import org.junit.Test

class BookingViewModelTest {
    private var now = 1794301200000L
    private var boxes = LocalCatalogRepository().getBoxes()
    private val repository = object : CatalogRepository { override fun getBoxes() = boxes }
    private fun model(saved: SavedStateHandle = SavedStateHandle()) = BookingViewModel(saved, repository, PaymentRepository { PaymentResult.CONFIRMED }) { now }

    @Test fun draftQuantityConsentAndReservationsSurviveRecreation() {
        val saved = SavedStateHandle()
        val first = model(saved)
        first.prepare(1); first.changeQuantity(1); first.acceptPolicy(true)
        val restored = model(saved)
        assertEquals(2, restored.state.draft?.quantity)
        assertTrue(restored.state.draft!!.accepted)
        val id = restored.confirm()!!
        val held = model(saved).state.reservations.single()
        assertEquals(id, held.id)
        assertEquals(78000L, model(saved).state.total)
        assertEquals(now + HOLD_MILLIS, held.expiresAtMillis)
    }

    @Test fun confirmationRequiresConsentAndRepeatedTapsDoNotCreateAnotherHold() {
        val vm = model(); vm.prepare(1)
        assertNull(vm.confirm())
        assertEquals(BookingIssue.POLICY_REQUIRED, vm.state.issue)
        vm.acceptPolicy(true)
        val id = vm.confirm()
        now += 30000
        assertEquals(id, vm.confirm())
        assertEquals(1, vm.state.reservations.size)
        assertEquals(id, vm.prepare(1))
        assertEquals(570L, vm.state.reservations.single().remainingSeconds(now))
    }

    @Test fun expiryUsesAnAbsoluteDeadlineAndDoesNotRestartAfterBackgroundOrRestore() {
        val saved = SavedStateHandle(); val vm = model(saved)
        vm.prepare(1); vm.acceptPolicy(true); vm.confirm()
        val deadline = vm.state.reservations.single().expiresAtMillis
        now = deadline
        val restored = model(saved)
        assertFalse(restored.state.reservations.single().isActive(now))
        assertNull(restored.prepare(1))
        assertFalse(restored.state.draft!!.accepted)
        assertEquals(4, restored.state.available)
    }

    @Test fun quantityButtonsStayWithinStockAndConfirmationRechecksChangedStock() {
        val vm = model(); vm.prepare(1)
        repeat(10) { vm.changeQuantity(1) }
        assertEquals(4, vm.state.draft?.quantity)
        vm.acceptPolicy(true)
        boxes = boxes.map { if (it.id == 1) it.copy(remaining = 2) else it }
        assertNull(vm.confirm())
        assertEquals(BookingIssue.QUANTITY, vm.state.issue)
        vm.changeQuantity(-1)
        assertEquals(2, vm.state.draft?.quantity)
        repeat(10) { vm.changeQuantity(-1) }
        assertEquals(1, vm.state.draft?.quantity)
    }

    @Test fun expiredPickupRequiresExplicitNewSelectionAndFreshConsent() {
        val vm = model(); vm.prepare(1); vm.acceptPolicy(true)
        val oldStart = vm.state.draft!!.pickup.startMillis
        now = oldStart
        assertNull(vm.confirm())
        assertEquals(BookingIssue.PICKUP_STARTED, vm.state.issue)
        vm.chooseNextPickup()
        assertTrue(vm.state.draft!!.pickup.startMillis > now)
        assertFalse(vm.state.draft!!.accepted)
    }

    @Test fun releaseKeepsCancelledHistoryAndLogoutClearsVisibleState() {
        val saved = SavedStateHandle(); val vm = model(saved)
        vm.prepare(1); vm.acceptPolicy(true)
        val id = vm.confirm()!!
        vm.release(id)
        assertEquals(OrderStatus.CANCELLED, vm.state.reservations.single().statusAt(now))
        vm.prepare(1); vm.acceptPolicy(true); vm.confirm()
        vm.clear()
        assertTrue(model(saved).state.reservations.isEmpty())
        assertNull(model(saved).state.draft)
    }

    @Test fun soldOutOrUnknownBoxesCannotBeReserved() {
        boxes = boxes.map { it.copy(remaining = 0) }
        val vm = model(); vm.prepare(1); vm.acceptPolicy(true)
        assertNull(vm.confirm())
        assertEquals(BookingIssue.SOLD_OUT, vm.state.issue)
        vm.prepare(999)
        assertNull(vm.confirm())
        assertEquals(BookingIssue.BOX_UNAVAILABLE, vm.state.issue)
    }
}
