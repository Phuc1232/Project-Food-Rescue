package com.foodrescue.app.feature.finance.domain

import androidx.lifecycle.SavedStateHandle
import com.foodrescue.app.feature.discovery.data.LocalCatalogRepository
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.order.presentation.BookingViewModel
import com.foodrescue.app.feature.payment.domain.*
import org.junit.Assert.*
import org.junit.Test

class BookingPolicyTest {
    private val now = 1794301200000L
    private var terms = FinancialTerms()
    private fun model(saved: SavedStateHandle = SavedStateHandle()) = BookingViewModel(saved,
        LocalCatalogRepository(), PaymentRepository { PaymentResult.CONFIRMED },
        policies = FinancialTermsSource { terms }, clock = { now })

    @Test fun changedPolicyRequiresFreshConsentAndIsCapturedAtReservation() {
        val vm = model()
        vm.prepare(1); vm.acceptPolicy(true)
        terms = FinancialTerms(1, 1500, 30, 7500, 15, 2500)
        assertNull(vm.confirm())
        assertEquals(BookingIssue.POLICY_REQUIRED, vm.state.issue)
        assertFalse(vm.state.draft!!.accepted)
        vm.acceptPolicy(true)
        assertNotNull(vm.confirm())
        assertEquals(terms, vm.state.reservations.single().financialTerms)
    }

    @Test fun savedReservationRetainsOriginalTermsAfterPolicyChanges() {
        val saved = SavedStateHandle()
        terms = FinancialTerms(1, 1525, 30, 7500, 15, 2500)
        val original = terms
        val vm = model(saved)
        vm.prepare(1); vm.acceptPolicy(true); assertNotNull(vm.confirm())
        terms = FinancialTerms(2, 1000, 60, 5000, 30, 5000)
        val restored = model(saved).state.reservations.single()
        assertEquals(original, restored.financialTerms)
    }

    @Test fun zeroRefundCancellationDoesNotCreateAnEmptyPaymentTransaction() {
        terms = FinancialTerms(1, cancellationRefundBps = 0)
        val vm = model()
        vm.prepare(1); vm.acceptPolicy(true)
        val id = vm.confirm()!!
        vm.openPayment(id); vm.checkPayment(id)
        // Restore a paid order through the same saved-state contract.
        val saved = SavedStateHandle()
        val held = vm.state.reservations.single()
        saved["booking.holds.v1"] = arrayListOf(listOf(held.id, held.boxId, held.quantity,
            held.pickup.startMillis, held.pickup.endMillis, held.createdAtMillis, held.expiresAtMillis,
            held.unitPrice, held.reference, held.total, "SUCCEEDED", now, "", "", "", "", "", "", "", "",
            1, 2000, 0, 0, 0, 0).joinToString("|"))
        val paid = model(saved)
        paid.prepareCancellation(id); paid.chooseCancellationReason(CancellationReason.entries.first())
        assertTrue(paid.cancelPaidOrder(id))
        assertNotNull(paid.state.reservations.single().cancelledAtMillis)
        assertNull(paid.state.reservations.single().refund)
    }
}
