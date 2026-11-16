package com.foodrescue.app.feature.surprisebox.domain

import com.foodrescue.app.feature.menu.domain.MenuUnit
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.order.domain.*
import org.junit.Assert.*
import org.junit.Test

class BoxInspectionTest {
    private val access = StaffAccess(StoreEmployee("e", 7, "An", "an@example.com", "", setOf(EmployeePermission.HAND_OVER), EmployeeStatus.ACTIVE, 1, 1, 1, emptyList()), "Tiệm bánh", true)
    private val box = PackedBox("b", 7, "batch", "Hộp bánh", 60000, "Sữa", listOf(PackedItem("i", "Bánh", MenuUnit.PIECE, 30000, "2", 60000)), 500, BoxStorage.AMBIENT, "p", "Bình", 100)
    private fun reject() = BoxInspectionDraft("r", box.id, 0, InspectionResult.REJECTED, InspectionReason.FOOD_QUALITY, "Mùi bất thường")
    private fun pass() = BoxInspectionDraft("p", box.id, 0, InspectionResult.PASSED, foodChecked = true, packagingChecked = true, storageChecked = true, allergensChecked = true)
    private fun issues(d: BoxInspectionDraft = reject(), b: PackedBox = box, a: StaffAccess = access, now: Long = 250) = d.inspectionIssues(a, b, now)

    @Test fun rejectionPreservesSnapshotAndCannotBeReopened() {
        val rejected = box.inspect(access, reject(), 250)
        assertEquals(box.items, rejected.items); assertEquals(500L, rejected.expiresAt)
        assertEquals("An", rejected.inspections.single().employeeName)
        assertEquals(250L, rejected.inspections.single().atMillis)
        assertEquals(BoxCondition.REJECTED, rejected.conditionAt(250)); assertFalse(rejected.isEligibleAt(250))
        assertTrue(InspectionIssue.BOX in issues(pass().copy(expectedRevision = 1), rejected))
    }
    @Test fun passingRequiresAllFourChecksAndRetainsAuditHistory() {
        listOf(pass().copy(foodChecked = false), pass().copy(packagingChecked = false), pass().copy(storageChecked = false), pass().copy(allergensChecked = false)).forEach {
            assertTrue(InspectionIssue.CHECKS in issues(it))
        }
        val checked = box.inspect(access, pass(), 250)
        val rejected = checked.inspect(access, reject().copy(expectedRevision = 1), 300)
        assertEquals(listOf(InspectionResult.PASSED, InspectionResult.REJECTED), rejected.inspections.map { it.result })
    }
    @Test fun expiryBoundaryAndDeliveredBoxesCannotBePassed() {
        assertTrue(InspectionIssue.EXPIRY in issues(pass(), now = 500))
        assertTrue(issues(reject().copy(reason = InspectionReason.EXPIRED), now = 500).isEmpty())
        assertTrue(InspectionIssue.REASON in issues(reject().copy(reason = InspectionReason.EXPIRED)))
        assertTrue(InspectionIssue.BOX in issues(b = box.copy(handover = PackedBoxHandover("o", "FR1", 200))))
        assertFalse(box.isEligibleAt(500))
    }
    @Test fun unauthorizedWrongStoreAndStaleRevisionAreRejected() {
        assertTrue(InspectionIssue.ACCESS in issues(a = access.copy(employee = access.employee.copy(permissions = setOf(EmployeePermission.VIEW_ORDERS)))))
        assertTrue(InspectionIssue.ACCESS in issues(a = access.copy(employee = access.employee.copy(status = EmployeeStatus.REVOKED))))
        assertTrue(InspectionIssue.ACCESS in issues(a = access.copy(storeApproved = false)))
        assertTrue(InspectionIssue.ACCESS in issues(b = box.copy(storeId = 8)))
        assertTrue(InspectionIssue.STALE in issues(reject().copy(expectedRevision = 1)))
        assertTrue(InspectionIssue.REQUEST in issues(reject().copy(id = "")))
    }
    @Test fun reasonAndNoteValidationCannotBeBypassed() {
        assertTrue(InspectionIssue.REASON in issues(reject().copy(reason = null)))
        assertTrue(InspectionIssue.NOTE in issues(reject().copy(reason = InspectionReason.OTHER, note = "  ngắn  ")))
        assertTrue(InspectionIssue.NOTE in issues(reject().copy(note = "x".repeat(501))))
        assertTrue(InspectionIssue.NOTE in issues(reject().copy(note = "a\u0000b")))
        assertTrue(InspectionIssue.REASON in issues(pass().copy(reason = InspectionReason.PACKAGING)))
    }
    @Test fun rejectedBoxCannotBeAllocatedToPaidOrder() {
        val order = BoxReservation("o", 1, 1, PickupWindow(200, 400), 1, 100, 25000,
            boxSnapshot = CatalogBox(1, "Tiệm", BoxCategory.BAKERY, "Hộp", 25000, 60000, 5, "", "", "Sữa", "", 0, 18, "AB", storeId = 7, batchId = "batch"))
        assertTrue(box.canHandOverFor(order, 250))
        assertFalse(box.inspect(access, reject(), 250).canHandOverFor(order, 250))
    }
    @Test fun rejectedAndExpiredCountsAllowOnlyTheRequiredReplacement() {
        val template = BoxTemplate("t", "Hộp bánh", BoxCategory.BAKERY, "Bánh trong ngày", "Sữa", 60000, 25000)
        val batch = SaleBatch("batch", template, 2, 1, 200, 200, 400, BatchControl.PUBLISHED, sold = 1, packed = 3, eligible = 1, rejected = 1, expired = 1)
        assertEquals(1, batch.prepared); assertTrue(batch.canPackAt(250)); assertEquals(1, batch.remaining)
        assertFalse(batch.copy(packed = 4, eligible = 2).canPackAt(250))
        assertFalse(batch.copy(control = BatchControl.ENDED).canPackAt(250))
        assertFalse(batch.canPackAt(400))
        val catalog = OwnerBoxCatalog(listOf(template), listOf(batch))
        assertFalse(BoxField.QUANTITY in batch.toDraft().validationErrors(catalog, 100))
        assertEquals(3, catalog.saveBatch(batch.toDraft(), 100, "unused").batches.single().packed)
        assertEquals(1, catalog.saveBatch(batch.toDraft(), 100, "unused").batches.single().rejected)
    }
}
