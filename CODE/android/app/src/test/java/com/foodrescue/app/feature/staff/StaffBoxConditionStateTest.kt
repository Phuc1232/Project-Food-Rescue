package com.foodrescue.app.feature.staff

import androidx.lifecycle.SavedStateHandle
import com.foodrescue.app.feature.menu.domain.MenuUnit
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.staff.presentation.*
import com.foodrescue.app.feature.surprisebox.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class StaffBoxConditionStateTest {
    private val access = StaffAccess(StoreEmployee("e", 7, "An", "an@example.com", "", setOf(EmployeePermission.HAND_OVER), EmployeeStatus.ACTIVE, 1, 1, 1, emptyList()), "Tiệm bánh", true)
    private val box = PackedBox("b", 7, "batch", "Hộp bánh", 60000, "Sữa", listOf(PackedItem("i", "Bánh", MenuUnit.PIECE, 30000, "2", 60000)), 500, BoxStorage.AMBIENT, "p", "Bình", 100)
    private inner class Repository : StaffBoxConditionRepository {
        var data = BoxConditionWorkspace(access, listOf(box)); var writes = 0; var failLoad = false; var loseReceipt = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun load(): BoxConditionWorkspace { val value = data; gate?.await(); check(!failLoad); return value }
        override suspend fun inspect(draft: BoxInspectionDraft): PackedBox {
            val updated = data.boxes.single().inspect(checkNotNull(data.access), draft, 250)
            data = data.copy(boxes = listOf(updated)); writes++; check(!loseReceipt); return updated
        }
    }
    private suspend fun prepare(vm: StaffBoxConditionViewModel) { vm.open(box); vm.refresh(); vm.edit(vm.state.draft.copy(result = InspectionResult.REJECTED, reason = InspectionReason.PACKAGING, note = "Bao bì rách")) }
    @Test fun finalConfirmationRequiredAndRepeatedSubmitDoesNotWriteTwice() = runBlocking {
        val repo = Repository(); val vm = StaffBoxConditionViewModel(SavedStateHandle(), repo) { 250 }
        prepare(vm); vm.refresh(); vm.submit(); assertEquals(0, repo.writes)
        vm.requestConfirmation(); vm.submit(); vm.submit()
        assertEquals(1, repo.writes); assertTrue(vm.state.saved); assertEquals(BoxCondition.REJECTED, vm.state.box!!.conditionAt(250))
    }
    @Test fun draftRestoresWithoutRestoringConfirmationOrTrust() = runBlocking {
        val saved = SavedStateHandle(); val repo = Repository(); val vm = StaffBoxConditionViewModel(saved, repo) { 250 }
        prepare(vm); vm.refresh(); vm.requestConfirmation()
        val restored = StaffBoxConditionViewModel(saved, repo) { 250 }
        assertEquals("Bao bì rách", restored.state.draft.note); assertFalse(restored.state.confirm); assertFalse(restored.state.verified)
        restored.submit(); assertEquals(0, repo.writes)
        restored.refresh(); assertTrue(restored.state.canEdit)
    }
    @Test fun failedLoadAndRevokedAccessBlockStaleForm() = runBlocking {
        val repo = Repository(); val vm = StaffBoxConditionViewModel(SavedStateHandle(), repo) { 250 }; prepare(vm); vm.refresh()
        repo.failLoad = true; vm.refresh(); vm.requestConfirmation(); vm.submit(); assertEquals(0, repo.writes)
        repo.failLoad = false; repo.data = BoxConditionWorkspace(access.copy(employee = access.employee.copy(status = EmployeeStatus.REVOKED)))
        vm.refresh(); assertFalse(vm.state.canEdit); assertNull(vm.state.box)
    }
    @Test fun lostWriteReceiptRecoversOnRefreshWithoutAnotherMutation() = runBlocking {
        val repo = Repository().apply { loseReceipt = true }; val vm = StaffBoxConditionViewModel(SavedStateHandle(), repo) { 250 }
        prepare(vm); vm.refresh(); vm.requestConfirmation(); vm.submit()
        assertFalse(vm.state.saved); assertFalse(vm.state.verified); assertEquals("Bao bì rách", vm.state.draft.note)
        vm.refresh(); assertTrue(vm.state.saved); assertEquals(1, repo.writes)
    }
    @Test fun changedRevisionNeedsExplicitReviewAndFreshChecks() = runBlocking {
        val repo = Repository(); val vm = StaffBoxConditionViewModel(SavedStateHandle(), repo) { 250 }; prepare(vm); vm.refresh()
        val inspected = box.inspect(access, BoxInspectionDraft("other", box.id, 0, InspectionResult.PASSED, foodChecked = true, packagingChecked = true, storageChecked = true, allergensChecked = true), 220)
        repo.data = repo.data.copy(boxes = listOf(inspected)); vm.refresh(); vm.requestConfirmation(); vm.submit()
        assertEquals(0, repo.writes); assertTrue(InspectionIssue.STALE in vm.state.issues)
        vm.reviewLatest(); assertEquals(1, vm.state.draft.expectedRevision); assertEquals("Bao bì rách", vm.state.draft.note)
        vm.requestConfirmation(); vm.submit(); assertEquals(1, repo.writes)
    }
    @Test fun delayedRefreshCannotRestoreDataAfterClose() = runBlocking {
        val repo = Repository(); val vm = StaffBoxConditionViewModel(SavedStateHandle(), repo) { 250 }; prepare(vm); repo.gate = CompletableDeferred()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.refresh() }; vm.clear(); repo.gate!!.complete(Unit); job.join()
        assertTrue(vm.state.draft.boxId.isEmpty()); assertTrue(vm.state.workspace.boxes.isEmpty())
    }
    @Test fun newNoShowContextRequiresExplicitReviewBeforeInspection() = runBlocking {
        val repo = Repository(); val vm = StaffBoxConditionViewModel(SavedStateHandle(), repo) { 250 }; prepare(vm)
        repo.data = repo.data.copy(boxes = listOf(box.copy(noShow = PackedBoxNoShow("o", "FR123", "n", 220))))
        vm.refresh(); vm.requestConfirmation(); assertTrue(InspectionIssue.STALE in vm.state.issues)
        vm.reviewLatest(); assertEquals("n", vm.state.draft.noShowRequestId)
        vm.requestConfirmation(); vm.submit(); assertEquals("n", repo.data.boxes.single().inspections.single().noShowRequestId)
    }
}
