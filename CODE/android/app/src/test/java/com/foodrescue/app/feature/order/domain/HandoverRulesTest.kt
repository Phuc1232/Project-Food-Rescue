package com.foodrescue.app.feature.order.domain

import com.foodrescue.app.feature.menu.domain.MenuUnit
import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.surprisebox.domain.*
import org.junit.Assert.*
import org.junit.Test

class HandoverRulesTest {
    private val access = StaffAccess(StoreEmployee("e", 7, "An", "an@example.com", "", pickupPermissions,
        EmployeeStatus.ACTIVE, 1, 1, 1, emptyList()), "Tiệm bánh", true)
    private val catalog = CatalogBox(1, "Tiệm bánh", BoxCategory.BAKERY, "Hộp bánh", 25000, 60000, 5,
        "", "", "Lúa mì", "", 0, 18, "AB", storeId = 7, batchId = "batch")
    private val order = BoxReservation("o", 1, 1, PickupWindow(200, 400), 1, 100, 25000,
        QrPayment("FR123456", 25000, PaymentStatus.SUCCEEDED, 50), boxSnapshot = catalog)
    private val box = PackedBox("b", 7, "batch", "Hộp bánh", 60000, "Lúa mì",
        listOf(PackedItem("i", "Bánh", MenuUnit.PIECE, 30000, "2", 60000)), 500, BoxStorage.AMBIENT, "p", "Bình", 150)
    private val draft = HandoverDraft("request", "o", "fr123456", listOf("b"))
    @Test fun handoverRecordsPhysicalAllocationAndEmployeeWithoutChangingPayment() {
        val done = order.completeHandover(access, draft, listOf(box), 250)
        assertEquals(250L, done.collectedAtMillis)
        assertEquals(listOf("b"), done.handover!!.boxIds)
        assertEquals("e", done.handover!!.employeeId)
        assertEquals(order.payment, done.payment)
        assertEquals(OrderStatus.COLLECTED, done.statusAt(250)); assertNull(done.pickupCode(250))
    }
    @Test fun onlyPaidUncancelledUncollectedOrdersCanBeHandedOver() {
        assertTrue(HandoverIssue.PAYMENT in draft.handoverIssues(access, order.copy(payment = null), listOf(box), 250))
        listOf(order.copy(cancelledAtMillis = 150), order.copy(collectedAtMillis = 220),
            order.copy(ownerCancellation = OwnerCancellationRequest(OwnerCancellationReason.FOOD_QUALITY, "", 200)))
            .forEach { assertTrue(HandoverIssue.ORDER in draft.handoverIssues(access, it, listOf(box), 250)) }
    }
    @Test fun codeStorePermissionAndPickupBoundariesAreEnforced() {
        assertTrue(HandoverIssue.CODE in draft.copy(code = "wrong").handoverIssues(access, order, listOf(box), 250))
        assertTrue(HandoverIssue.ACCESS in draft.handoverIssues(access.copy(employee = access.employee.copy(storeId = 9)), order, listOf(box), 250))
        assertTrue(HandoverIssue.ACCESS in draft.handoverIssues(access.copy(employee = access.employee.copy(permissions = setOf(EmployeePermission.VIEW_ORDERS))), order, listOf(box), 250))
        assertTrue(HandoverIssue.EARLY in draft.handoverIssues(access, order, listOf(box), 199))
        assertTrue(draft.handoverIssues(access, order, listOf(box), 200).isEmpty())
        assertTrue(HandoverIssue.ENDED in draft.handoverIssues(access, order, listOf(box), 400))
    }
    @Test fun exactDistinctBoxCountAndActualValueAreRequired() {
        assertTrue(HandoverIssue.QUANTITY in draft.copy(boxIds = listOf("b", "b")).handoverIssues(access, order, listOf(box), 250))
        assertTrue(HandoverIssue.QUANTITY in draft.copy(boxIds = emptyList()).handoverIssues(access, order, listOf(box), 250))
        val invalid = listOf(box.copy(storeId = 8), box.copy(batchId = "other"), box.copy(expiresAt = 250),
            box.copy(handover = PackedBoxHandover("other", "FRother", 200)),
            box.copy(items = box.items.map { it.copy(value = 10000) }))
        invalid.forEach { assertTrue(HandoverIssue.BOX in draft.handoverIssues(access, order, listOf(it), 250)) }
    }
    @Test fun failureRecordsFollowUpWithoutCollectingCancellingOrRefunding() {
        val failed = order.recordHandoverFailure(access, HandoverFailureDraft("f", "o", HandoverFailureReason.BOX_UNAVAILABLE, "Chưa đủ hộp đạt chất lượng"), 250)
        assertEquals(1, failed.handoverFailures.size); assertNull(failed.collectedAtMillis)
        assertNull(failed.cancelledAtMillis); assertNull(failed.refund); assertEquals(OrderStatus.WAITING_PICKUP, failed.statusAt(250))
        assertTrue(runCatching { order.recordHandoverFailure(access, HandoverFailureDraft("f", "o", HandoverFailureReason.OTHER, ""), 250) }.isFailure)
    }
}
