package com.foodrescue.app.feature.order.domain

import com.foodrescue.app.feature.menu.domain.MenuUnit
import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.surprisebox.domain.*
import org.junit.Assert.*
import org.junit.Test

class NoShowRulesTest {
    private val access = StaffAccess(StoreEmployee("e", 7, "An", "an@example.com", "", pickupPermissions,
        EmployeeStatus.ACTIVE, 1, 1, 1, emptyList()), "Tiệm bánh", true)
    private val catalog = CatalogBox(1, "Tiệm bánh", BoxCategory.BAKERY, "Hộp bánh", 25000, 60000, 5,
        "", "", "Lúa mì", "", 0, 18, "AB", storeId = 7, batchId = "batch")
    private val order = BoxReservation("o", 1, 1, PickupWindow(200, 400), 1, 100, 25000,
        QrPayment("FR123456", 25000, PaymentStatus.SUCCEEDED, 50), boxSnapshot = catalog)
    private val box = PackedBox("b", 7, "batch", "Hộp bánh", 60000, "Lúa mì",
        listOf(PackedItem("i", "Bánh", MenuUnit.PIECE, 30000, "2", 60000)), 500, BoxStorage.AMBIENT, "p", "Bình", 150)

    private val draft = NoShowDraft("request", "o", listOf("b"), "Đã đối chiếu tại quầy", true, true)

    @Test fun noShowIsHeldForResolutionWithoutEarningCommissionOrPayout() {
        val result = order.confirmNoShow(access, draft, listOf(box), 400)
        val revenue = com.foodrescue.app.feature.finance.domain.buildRevenueSnapshot(7, listOf(result), emptySet(), emptyList(), null, 2000, 401)
        assertEquals(com.foodrescue.app.feature.finance.domain.RevenueEntryStatus.HELD, revenue.entries.single().status)
        assertEquals(0L, revenue.entries.single().commission); assertEquals(0L, revenue.entries.single().net)
        assertTrue(revenue.settlements.isEmpty())
    }

    @Test fun confirmationStartsAtEndAndDoesNotTouchMoneyOrSaleStock() {
        assertTrue(NoShowIssue.EARLY in draft.noShowIssues(access, order, listOf(box), 399))
        val result = order.confirmNoShow(access, draft, listOf(box), 400)
        assertEquals(OrderStatus.NO_SHOW, result.statusAt(400)); assertNull(result.pickupCode(400))
        assertEquals("e", result.noShow!!.employeeId); assertEquals(listOf("b"), result.noShow!!.boxIds)
        assertEquals(order.payment, result.payment); assertNull(result.refund); assertNull(result.cancelledAtMillis)
        assertTrue(result.occupiesStock(400)); assertNull(result.collectedAtMillis)
        assertTrue(HandoverIssue.ORDER in result.handoverAvailability(access, 400))
        assertEquals(OwnerCancellationIssue.NOT_ELIGIBLE, ownerCancellationIssue(result, OwnerCancellationDraft("o", OwnerCancellationReason.OTHER, "Đã kiểm tra")))
    }
    @Test fun confirmationRequiresAuthorityPaymentAndExplicitAttestations() {
        assertTrue(NoShowIssue.ACCESS in draft.noShowIssues(null, order, listOf(box), 400))
        assertTrue(NoShowIssue.ACCESS in draft.noShowIssues(access.copy(employee = access.employee.copy(storeId = 9)), order, listOf(box), 400))
        assertTrue(NoShowIssue.PAYMENT in draft.noShowIssues(access, order.copy(payment = null), listOf(box), 400))
        assertTrue(NoShowIssue.CHECKS in draft.copy(customerAbsent = false).noShowIssues(access, order, listOf(box), 400))
        assertTrue(NoShowIssue.CHECKS in draft.copy(storeReady = false).noShowIssues(access, order, listOf(box), 400))
        assertTrue(NoShowIssue.NOTE in draft.copy(note = "a".repeat(501)).noShowIssues(access, order, listOf(box), 400))
    }
    @Test fun cancelledCollectedDisputedAndIncidentOrdersCannotBeNoShow() {
        listOf(order.copy(cancelledAtMillis = 250), order.copy(collectedAtMillis = 250),
            order.copy(ownerCancellation = OwnerCancellationRequest(OwnerCancellationReason.STORE_CLOSED, "", 250)))
            .forEach { assertTrue(NoShowIssue.ORDER in draft.noShowIssues(access, it, listOf(box), 400)) }
        val incident = order.recordHandoverFailure(access, HandoverFailureDraft("f", "o", HandoverFailureReason.CUSTOMER_DECLINED), 250)
        assertTrue(NoShowIssue.INCIDENT in draft.noShowIssues(access, incident, listOf(box), 400))
    }
    @Test fun physicalAssociationIsExplicitAndCannotStealAnotherBox() {
        assertTrue(draft.copy(boxIds = emptyList()).noShowIssues(access, order, listOf(box), 400).isEmpty())
        assertTrue(NoShowIssue.BOXES in draft.copy(boxIds = listOf("b", "b")).noShowIssues(access, order, listOf(box), 400))
        listOf(box.copy(storeId = 9), box.copy(batchId = "wrong"), box.copy(packedAt = 401),
            box.copy(handover = PackedBoxHandover("other", "FROTHER", 250)),
            box.copy(noShow = PackedBoxNoShow("other", "FROTHER", "other-request", 399)))
            .forEach { assertTrue(NoShowIssue.BOXES in draft.noShowIssues(access, order, listOf(it), 400)) }
        assertTrue(draft.noShowIssues(access, order, listOf(box.copy(expiresAt = 400)), 400).isEmpty())
    }
    @Test fun onlyAnInspectionOfThisNoShowCanReleaseItsHold() {
        val held = box.copy(noShow = PackedBoxNoShow("o", order.reference, "request", 400))
        assertEquals(BoxCondition.RECHECK_REQUIRED, held.conditionAt(401)); assertFalse(held.isEligibleAt(401))
        val inspection = BoxInspectionDraft("check", "b", result = InspectionResult.PASSED,
            foodChecked = true, packagingChecked = true, storageChecked = true, allergensChecked = true)
        assertTrue(InspectionIssue.STALE in inspection.inspectionIssues(access, held, 401))
        val checked = held.inspect(access, inspection.copy(noShowRequestId = "request"), 401)
        assertEquals(BoxCondition.READY, checked.conditionAt(401)); assertTrue(checked.isEligibleAt(401))
        assertEquals("request", checked.inspections.single().noShowRequestId)
        assertEquals(box.items, checked.items); assertEquals(box.expiresAt, checked.expiresAt)
        assertEquals(BoxCondition.EXPIRED, held.conditionAt(500))
        assertTrue(InspectionIssue.EXPIRY in inspection.copy(noShowRequestId = "request").inspectionIssues(access, held, 500))
    }
    @Test fun overdueAloneDoesNotDeclareAbsenceAndIncidentsRemainRecordable() {
        assertEquals(OrderStatus.WAITING_PICKUP, order.statusAt(400))
        val incident = order.recordHandoverFailure(access,
            HandoverFailureDraft("failure", "o", HandoverFailureReason.BOX_UNAVAILABLE, "Không đủ hộp"), 401)
        assertEquals(1, incident.handoverFailures.size)
        assertNull(incident.cancelledAtMillis); assertNull(incident.refund)
        assertEquals(OrderStatus.WAITING_PICKUP, incident.statusAt(401))
    }
}
