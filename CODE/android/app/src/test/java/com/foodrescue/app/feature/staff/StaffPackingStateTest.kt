package com.foodrescue.app.feature.staff

import androidx.lifecycle.SavedStateHandle
import com.foodrescue.app.feature.menu.domain.*
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.staff.presentation.*
import com.foodrescue.app.feature.surprisebox.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class StaffPackingStateTest {
    private val item = MenuItem("m", "Bánh mì", MenuCategory.BAKERY, MenuUnit.PIECE, 30000, "", MenuStatus.ACTIVE, null, 1, 1)
    private val batch = SaleBatch("b", BoxTemplate("t", "Hộp bánh", BoxCategory.BAKERY, "Bánh trong ngày", "Lúa mì", 60000, 20000), 5, 1, 200, 200, 300, BatchControl.PUBLISHED)
    private val access = StaffAccess(StoreEmployee("e", 1, "An", "an@example.com", "", operationsPermissions, EmployeeStatus.ACTIVE, 1, 1, 1, emptyList()), "Tiệm bánh", true)
    private inner class Repository : StaffPackingRepository {
        var workspace = StaffWorkspace(access, listOf(item), listOf(batch))
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        var writes = 0
        override suspend fun load(): StaffWorkspace { val result = workspace; gate?.await(); check(!fail); return result }
        override suspend fun complete(draft: PackingDraft): PackedBox { writes++; return draft.completePacking(access, batch, listOf(item), 100) }
    }
    @Test fun packingDraftStepAndChecksSurviveRecreation() = runBlocking {
        val saved = SavedStateHandle(); val repo = Repository(); val vm = StaffPackingViewModel(saved, repo) { 100 }
        vm.refresh(); vm.begin("b"); vm.quantity("m", "2"); vm.next()
        vm.safety(400, BoxStorage.AMBIENT, true, true, true); vm.next()
        val restored = StaffPackingViewModel(saved, repo) { 100 }; restored.refresh()
        assertEquals(vm.state.draft, restored.state.draft); assertEquals(2, restored.state.step)
        assertEquals(StaffPage.PACKING, restored.state.page)
        restored.back(); assertEquals(1, restored.state.step)
    }
    @Test fun contentsChangeInvalidatesInspectionAndCannotSkipValueCheck() = runBlocking {
        val vm = StaffPackingViewModel(SavedStateHandle(), Repository()) { 100 }; vm.refresh(); vm.begin("b"); vm.next()
        assertEquals(0, vm.state.step)
        vm.quantity("m", "2"); vm.next(); vm.safety(400, BoxStorage.AMBIENT, true, true, true)
        vm.quantity("m", "1")
        assertFalse(vm.state.draft.foodChecked); assertFalse(vm.state.draft.packagingChecked)
        vm.next(); assertFalse(vm.state.canComplete)
    }
    @Test fun refreshFailureBlocksCompletionUntilAccessIsCheckedAgain() = runBlocking {
        val repo = Repository(); val vm = StaffPackingViewModel(SavedStateHandle(), repo) { 100 }
        vm.refresh(); vm.begin("b"); vm.quantity("m", "2"); vm.next(); vm.safety(400, BoxStorage.AMBIENT, true, true, true); vm.next()
        repo.fail = true; vm.refresh(); vm.submit()
        assertEquals(0, repo.writes); assertFalse(vm.state.canComplete)
        repo.fail = false; vm.refresh(); vm.submit()
        assertEquals(1, repo.writes); assertEquals(StaffPage.DETAIL, vm.state.page)
        assertEquals(60000L, vm.state.selectedBox!!.value)
        assertEquals("", vm.state.draft.id)
    }
    @Test fun responseAfterLogoutCannotRestoreStoreData() = runBlocking {
        val repo = Repository().apply { gate = CompletableDeferred() }
        val vm = StaffPackingViewModel(SavedStateHandle(), repo) { 100 }
        val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.refresh() }
        vm.clear(); repo.gate!!.complete(Unit); job.join()
        assertNull(vm.state.workspace.access); assertTrue(vm.state.workspace.menu.isEmpty())
    }
    @Test fun revokedAccessClearsDraftAndStoreData() = runBlocking {
        val repo = Repository(); val vm = StaffPackingViewModel(SavedStateHandle(), repo) { 100 }
        vm.refresh(); vm.begin("b"); vm.quantity("m", "2")
        repo.workspace = StaffWorkspace(access.copy(employee = access.employee.copy(status = EmployeeStatus.REVOKED)))
        vm.refresh()
        assertEquals("", vm.state.draft.id); assertFalse(vm.state.canPrepare)
    }
    @Test fun handoverOnlyStaffCanStayInInventoryAndAccountAfterRefresh() = runBlocking {
        val repo = Repository().apply {
            workspace = workspace.copy(access = access.copy(employee = access.employee.copy(permissions = pickupPermissions)))
        }
        val vm = StaffPackingViewModel(SavedStateHandle(), repo) { 100 }
        vm.refresh(); vm.navigate(StaffPage.BOXES); vm.refresh()
        assertEquals(StaffPage.BOXES, vm.state.page)
        vm.navigate(StaffPage.ACCOUNT); vm.refresh()
        assertEquals(StaffPage.ACCOUNT, vm.state.page)
        vm.begin("b"); assertEquals("", vm.state.draft.id)
        assertFalse(vm.state.canPrepare)
    }
}
