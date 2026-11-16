package com.foodrescue.app.feature.surprisebox.domain

import com.foodrescue.app.feature.menu.domain.*
import com.foodrescue.app.feature.staff.domain.*
import org.junit.Assert.*
import org.junit.Test

class PackingRulesTest {
    private val now = 100_000L
    private val item = MenuItem("bread", "Bánh mì", MenuCategory.BAKERY, MenuUnit.PIECE, 30_000, "", MenuStatus.ACTIVE, null, 1, 1)
    private val batch = SaleBatch("batch", BoxTemplate("template", "Hộp bánh", BoxCategory.BAKERY, "Bánh trong ngày", "Lúa mì", 60_000, 25_000), 2, 1, 200_000, 200_000, 300_000, BatchControl.PUBLISHED)
    private val access = StaffAccess(StoreEmployee("staff", 7, "An", "an@example.com", "", setOf(EmployeePermission.PREPARE_BOXES, EmployeePermission.VIEW_ORDERS), EmployeeStatus.ACTIVE, 1, 1, 1, emptyList()), "Tiệm bánh", true)
    private fun draft() = PackingDraft("request", batch.id, listOf(PackingSelection(item.id, "2", item.price, item.unit, item.updatedAtMillis)), 400_000, BoxStorage.AMBIENT, true, true, true)
    private fun issues(d: PackingDraft = draft(), b: SaleBatch = batch, items: List<MenuItem> = listOf(item), a: StaffAccess = access) = d.packingIssues(a, b, items, now)

    @Test fun twoThirtyThousandItemsMeetSixtyThousandMinimum() {
        assertTrue(issues().isEmpty())
        val box = draft().completePacking(access, batch, listOf(item), now)
        assertEquals(60_000L, box.value)
        assertEquals("An", box.preparedByName)
        assertEquals("Bánh mì", box.items.single().name)
        assertEquals(7, box.storeId)
    }
    @Test fun insufficientValueAndFractionalPiecesAreRejected() {
        assertTrue(PackingIssue.VALUE in issues(draft().copy(selections = listOf(draft().selections.single().copy(quantity = "1")))))
        assertTrue(PackingIssue.ITEMS in issues(draft().copy(selections = listOf(draft().selections.single().copy(quantity = "1.5")))))
        assertTrue(PackingIssue.ITEMS in issues(draft().copy(selections = emptyList())))
    }
    @Test fun kilogramQuantitiesUseExistingListedValueRounding() {
        val kg = item.copy(unit = MenuUnit.KILOGRAM, price = 120_001)
        val d = draft().copy(selections = listOf(PackingSelection(kg.id, "0,5", kg.price, kg.unit, 1)))
        assertEquals(60_001L, d.completePacking(access, batch, listOf(kg), now).value)
    }
    @Test fun changedPricesUnitsOrUnavailableItemsCannotBeSilentlyAccepted() {
        assertTrue(PackingIssue.MENU_CHANGED in issues(items = listOf(item.copy(price = 40_000))))
        assertTrue(PackingIssue.MENU_CHANGED in issues(items = listOf(item.copy(unit = MenuUnit.BOX))))
        assertTrue(PackingIssue.MENU_CHANGED in issues(items = listOf(item.copy(status = MenuStatus.INACTIVE))))
        assertTrue(PackingIssue.MENU_CHANGED in issues(items = emptyList()))
    }
    @Test fun inspectionStorageAndFullPickupWindowAreRequired() {
        assertTrue(PackingIssue.EXPIRY in issues(draft().copy(expiresAt = batch.pickupEnd - 1)))
        assertTrue(PackingIssue.STORAGE in issues(draft().copy(storage = null)))
        assertTrue(PackingIssue.INSPECTION in issues(draft().copy(foodChecked = false)))
        assertTrue(PackingIssue.INSPECTION in issues(draft().copy(packagingChecked = false)))
        assertTrue(PackingIssue.INSPECTION in issues(draft().copy(allergensChecked = false)))
    }
    @Test fun revokedWrongStoreAndMissingPermissionBlockPacking() {
        assertTrue(PackingIssue.ACCESS in issues(a = access.copy(employee = access.employee.copy(status = EmployeeStatus.REVOKED))))
        assertTrue(PackingIssue.ACCESS in issues(a = access.copy(storeApproved = false)))
        assertTrue(PackingIssue.ACCESS in issues(a = access.copy(employee = access.employee.copy(permissions = setOf(EmployeePermission.VIEW_ORDERS)))))
    }
    @Test fun draftExpiredAndFullBatchesCannotReceiveAnotherBox() {
        assertTrue(PackingIssue.BATCH in issues(b = batch.copy(control = BatchControl.DRAFT)))
        assertTrue(PackingIssue.BATCH in draft().packingIssues(access, batch, listOf(item), batch.pickupEnd))
        assertTrue(PackingIssue.CAPACITY in issues(b = batch.copy(packed = 2)))
        assertTrue(PackingIssue.BATCH in issues(b = batch.copy(control = BatchControl.ENDED)))
        assertTrue(issues(b = batch.copy(control = BatchControl.ENDED, sold = 1)).isEmpty())
    }
    @Test fun packedCountsPreventOwnerReducingCapacityOrChangingPickup() {
        val prepared = batch.copy(packed = 1)
        val catalog = OwnerBoxCatalog(listOf(batch.template), listOf(prepared))
        assertTrue(BoxField.LOCKED in prepared.toDraft().copy(pickupEnd = 400_000).validationErrors(catalog, now))
        val full = batch.copy(packed = 2)
        assertTrue(BoxField.QUANTITY in full.toDraft().copy(quantity = "1").validationErrors(catalog.copy(batches = listOf(full)), now))
        assertEquals(2, prepared.remaining)
    }
}
