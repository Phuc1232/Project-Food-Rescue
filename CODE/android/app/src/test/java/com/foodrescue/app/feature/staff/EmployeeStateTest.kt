package com.foodrescue.app.feature.staff

import androidx.lifecycle.SavedStateHandle
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.staff.presentation.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class EmployeeStateTest {
    private val member = StoreEmployee("a", -1, "Nguyễn An", "an@example.com", "", pickupPermissions, EmployeeStatus.ACTIVE,
        1, 100, 100, listOf(EmployeeEvent(EmployeeEventType.CREATED, 100, pickupPermissions)))
    private class Repository(var members: List<StoreEmployee>) : EmployeeRepository {
        var gate: CompletableDeferred<Unit>? = null
        var fail = false
        override suspend fun load(): List<StoreEmployee> { val before = members; gate?.await(); check(!fail); return before }
        override suspend fun save(draft: EmployeeDraft): StoreEmployee = error("Not used")
        override suspend fun changeAccess(id: String, revision: Int, status: EmployeeStatus, reason: String): StoreEmployee = error("Not used")
    }
    @Test fun refreshAfterAccountExitCannotRestoreOldEmployees() = runBlocking {
        val repo = Repository(listOf(member)).apply { gate = CompletableDeferred() }
        val vm = EmployeeViewModel(SavedStateHandle(), repo)
        val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.refresh() }
        vm.clear(); repo.gate!!.complete(Unit); job.join()
        assertTrue(vm.state.employees.isEmpty())
    }
    @Test fun draftAndStepSurviveRecreationAndExitClearsThem() = runBlocking {
        val saved = SavedStateHandle(); val repo = Repository(emptyList())
        val vm = EmployeeViewModel(saved, repo)
        vm.refresh(); vm.beginCreate(); vm.editName("Trần An"); vm.editEmail("an@example.com"); vm.next(); vm.preset(pickupPermissions)
        val restored = EmployeeViewModel(saved, repo)
        assertEquals(vm.state.draft, restored.state.draft)
        assertEquals(1, restored.state.step)
        assertEquals(EmployeePage.FORM, restored.state.page)
        vm.clear()
        assertEquals(EmployeeDraft(), EmployeeViewModel(saved, repo).state.draft)
    }
    @Test fun failedRefreshKeepsReadableEmployeesAndBlocksMutationsUntilRetry() = runBlocking {
        val repo = Repository(listOf(member)); val vm = EmployeeViewModel(SavedStateHandle(), repo)
        vm.refresh(); repo.fail = true; vm.refresh(); vm.beginCreate()
        assertEquals(listOf(member), vm.state.employees)
        assertEquals(EmployeePage.LIST, vm.state.page)
        assertFalse(vm.state.canMutate)
        repo.fail = false; vm.refresh(); assertTrue(vm.state.canMutate)
    }
    @Test fun staleAccessConfirmationCannotApplyToNewRevision() = runBlocking {
        val repo = Repository(listOf(member)); val vm = EmployeeViewModel(SavedStateHandle(), repo)
        vm.refresh(); vm.open("a"); vm.requestAccess(); vm.reason("Nghỉ việc")
        repo.members = listOf(member.copy(revision = 2, permissions = operationsPermissions)); vm.refresh()
        vm.confirmAccess()
        assertNotNull(vm.state.error)
        assertFalse(vm.state.busy)
        assertEquals(EmployeeStatus.ACTIVE, vm.state.selected!!.status)
    }
    @Test fun formCannotSkipIdentityOrCreateWithNoPermissions() = runBlocking {
        val vm = EmployeeViewModel(SavedStateHandle(), Repository(emptyList()))
        vm.refresh(); vm.beginCreate(); vm.next()
        assertEquals(0, vm.state.step)
        vm.editName("Nguyễn An"); vm.editEmail("an@example.com"); vm.next(); vm.next()
        assertEquals(1, vm.state.step)
        assertTrue(EmployeeIssue.PERMISSIONS in vm.state.issues)
        vm.back {}; vm.back {}
        assertTrue(vm.state.confirmDiscard)
        vm.keepEditing(); assertEquals("an@example.com", vm.state.draft.email)
    }
    @Test fun staffRefreshFailureImmediatelyRemovesVisibleAccess() = runBlocking {
        var fail = false
        val repo = object : StaffAccessRepository {
            override suspend fun find(email: String): StaffAccess { check(!fail); return StaffAccess(member, "Store", true) }
        }
        val vm = StaffAccessViewModel(member.email, repo)
        vm.refresh(); assertTrue(vm.state.access!!.allowed)
        fail = true; vm.refresh()
        assertNull(vm.state.access)
        assertNotNull(vm.state.error)
    }
    @Test fun revocationReasonSurvivesRecreationWithoutAutoSubmitting() = runBlocking {
        val saved = SavedStateHandle(); val repo = Repository(listOf(member)); val vm = EmployeeViewModel(saved, repo)
        vm.refresh(); vm.open("a"); vm.requestAccess(); vm.reason("Kết thúc hợp đồng")
        val restored = EmployeeViewModel(saved, repo)
        assertEquals("Kết thúc hợp đồng", restored.state.reason)
        assertEquals(EmployeeStatus.REVOKED, restored.state.accessTarget)
        assertEquals(1, restored.state.accessRevision)
        assertFalse(restored.state.busy)
        restored.dismissAccess()
        assertNull(EmployeeViewModel(saved, repo).state.accessTarget)
        assertEquals("", EmployeeViewModel(saved, repo).state.reason)
    }
}
