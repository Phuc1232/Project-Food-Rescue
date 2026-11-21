package com.foodrescue.app.feature.store.domain

import com.foodrescue.app.feature.store.presentation.*
import com.foodrescue.app.feature.surprisebox.domain.*
import org.junit.Assert.*
import org.junit.Test

class StoreSupervisionTest {
    private val owner = OwnerStore(OwnerStoreProfile(name = "Bánh Mộc", area = "Bàn Cờ"), StoreReviewStatus.APPROVED,
        payout = StorePayoutAccount("ACB", "NGUYEN AN", "00123456789"))
    private val batch = SaleBatch("batch", BoxTemplate("template", "Hộp bánh", BoxCategory.BAKERY, "Bánh tươi", "Sữa", 90_000, 30_000),
        10, 1000, 5000, 6000, 9000, BatchControl.PUBLISHED)
    private val note = "Đã kiểm tra bằng chứng và đối chiếu dữ liệu."
    private fun draft(action: StoreRestrictionAction, store: OwnerStore = owner, target: SaleBatch? = null, id: String = action.name) =
        StoreRestrictionDraft(id, store.revision, action, target, note)

    @Test fun suspensionPreservesApprovalProfilePayoutAndReviewHistory() {
        val changed = owner.restrict(draft(StoreRestrictionAction.SUSPEND_STORE), "admin", 1000)
        assertEquals(owner.copy(governance = changed.governance), changed)
        assertFalse(changed.governance.permitsNewSales("any"))
        assertEquals("admin", changed.governance.events.single().actor)
    }
    @Test fun restoringStoreDoesNotReleaseAnIndividuallyBlockedBatch() {
        val blocked = owner.restrict(draft(StoreRestrictionAction.STOP_BATCH, target = batch), "admin", 1000)
        val suspended = blocked.restrict(draft(StoreRestrictionAction.SUSPEND_STORE, blocked), "admin", 2000)
        val restored = suspended.restrict(draft(StoreRestrictionAction.RESTORE_STORE, suspended), "admin", 3000)
        assertFalse(restored.governance.permitsNewSales(batch.id)); assertTrue(restored.governance.permitsNewSales("other"))
        assertEquals(3, restored.governance.events.size)
    }
    @Test fun liftingBatchRestrictionDoesNotLiftStoreSuspension() {
        val blocked = owner.restrict(draft(StoreRestrictionAction.STOP_BATCH, target = batch), "admin", 1000)
        val suspended = blocked.restrict(draft(StoreRestrictionAction.SUSPEND_STORE, blocked), "admin", 2000)
        val released = suspended.restrict(draft(StoreRestrictionAction.RELEASE_BATCH, suspended, batch), "admin", 3000)
        assertTrue(released.governance.blockedBatches.isEmpty()); assertFalse(released.governance.permitsNewSales(batch.id))
    }
    @Test fun blockedBatchScopeDoesNotChangeOtherBatches() {
        val updated = owner.restrict(draft(StoreRestrictionAction.STOP_BATCH, target = batch), "admin", 1000)
        assertEquals(setOf(batch.id), updated.governance.blockedBatches.keys)
        assertTrue(updated.governance.permitsNewSales("another")); assertNull(updated.governance.suspension)
    }
    @Test fun repeatedAndOutdatedDecisionsAreRejected() {
        val request = draft(StoreRestrictionAction.SUSPEND_STORE)
        val updated = owner.restrict(request, "admin", 1000)
        assertThrows(StaleStoreSupervision::class.java) { updated.restrict(request, "admin", 2000) }
        assertThrows(StaleStoreSupervision::class.java) { owner.copy(revision = 1).restrict(request, "admin", 2000) }
    }
    @Test fun reasonsCannotBeEmptyShortOversizedOrContainControlCharacters() {
        listOf("", "ngắn", "x".repeat(501), note + '\u0000').forEach { assertFalse(validRestrictionNote(it)) }
        assertTrue(validRestrictionNote("  $note\nCần khắc phục  "))
    }
    @Test fun unapprovedStoreCannotBeGovernedAndTargetMustMatchAction() {
        assertThrows(StaleStoreSupervision::class.java) { owner.copy(status = StoreReviewStatus.PENDING).restrict(draft(StoreRestrictionAction.SUSPEND_STORE), "admin", 1000) }
        assertThrows(StaleStoreSupervision::class.java) { owner.restrict(draft(StoreRestrictionAction.SUSPEND_STORE, target = batch), "admin", 1000) }
        assertThrows(StaleStoreSupervision::class.java) { owner.restrict(draft(StoreRestrictionAction.STOP_BATCH), "admin", 1000) }
    }
    @Test fun invalidPersistedEventsFailClosed() {
        val event = StoreRestrictionEvent("1", StoreRestrictionAction.RESTORE_STORE, null, null, note, "admin", 1000)
        assertThrows(IllegalArgumentException::class.java) { StoreGovernance(listOf(event)) }
        val stopped = event.copy(action = StoreRestrictionAction.STOP_BATCH, batchId = "batch")
        assertThrows(IllegalArgumentException::class.java) { StoreGovernance(listOf(stopped, stopped)) }
    }
    @Test fun reopeningIsAuditedAndAllowsLaterSuspension() {
        val suspended = owner.restrict(draft(StoreRestrictionAction.SUSPEND_STORE), "admin", 1000)
        val restored = suspended.restrict(draft(StoreRestrictionAction.RESTORE_STORE, suspended), "admin", 2000)
        assertTrue(restored.governance.permitsNewSales(batch.id))
        val again = restored.restrict(draft(StoreRestrictionAction.SUSPEND_STORE, restored, id = "again"), "admin", 3000)
        assertEquals(3, again.governance.events.size)
    }
    @Test fun searchIgnoresVietnameseAccentsAndComposesWithRestrictions() {
        val blocked = owner.restrict(draft(StoreRestrictionAction.STOP_BATCH, target = batch), "admin", 1000)
        val state = StoreSupervisionState(stores = listOf(SupervisedStore("a", -1, owner), SupervisedStore("b", -2, blocked)), query = "banh moc")
        assertEquals(2, state.visible.size); assertEquals(1, state.restrictedCount)
        assertEquals("a", state.copy(filter = SupervisionFilter.ACTIVE).visible.single().accountKey)
        assertEquals("b", state.copy(filter = SupervisionFilter.RESTRICTED).visible.single().accountKey)
        assertEquals(0, state.copy(query = "khong co").visible.size)
    }
}
