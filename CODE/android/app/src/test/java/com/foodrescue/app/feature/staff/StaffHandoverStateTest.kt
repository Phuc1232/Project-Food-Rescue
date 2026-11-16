package com.foodrescue.app.feature.staff

import androidx.lifecycle.SavedStateHandle
import com.foodrescue.app.feature.menu.domain.MenuUnit
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.staff.presentation.*
import com.foodrescue.app.feature.surprisebox.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class StaffHandoverStateTest {
    private val access = StaffAccess(StoreEmployee("e", 7, "An", "an@example.com", "", pickupPermissions, EmployeeStatus.ACTIVE, 1, 1, 1, emptyList()), "Tiệm bánh", true)
    private val order = BoxReservation("o", 1, 1, PickupWindow(200, 400), 1, 100, 25000,
        QrPayment("FR123456", 25000, PaymentStatus.SUCCEEDED, 50), boxSnapshot = CatalogBox(1, "Tiệm bánh", BoxCategory.BAKERY, "Hộp bánh", 25000, 60000, 5, "", "", "Sữa", "", 0, 18, "AB", storeId = 7, batchId = "batch"))
    private val box = PackedBox("b", 7, "batch", "Hộp bánh", 60000, "Sữa", listOf(PackedItem("i", "Bánh", MenuUnit.PIECE, 30000, "2", 60000)), 500, BoxStorage.AMBIENT, "p", "Bình", 150)
    private inner class Repository : StaffHandoverRepository {
        var data = HandoverWorkspace(access, listOf(order), listOf(box)); var writes = 0; var fail = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun load(): HandoverWorkspace { val result = data; gate?.await(); check(!fail); return result }
        override suspend fun complete(draft: HandoverDraft): BoxReservation { writes++; return order.completeHandover(access, draft, listOf(box), 250) }
        override suspend fun recordFailure(draft: HandoverFailureDraft) = order.recordHandoverFailure(access, draft, 250)
    }
    private fun prepare(vm: StaffHandoverViewModel) { vm.open("o"); vm.editCode("FR123456"); vm.verifyCode(); vm.toggleBox("b") }
    @Test fun codeEditInvalidatesVerificationAndFinalConfirmationIsRequired() = runBlocking {
        val repo = Repository(); val vm = StaffHandoverViewModel(SavedStateHandle(), repo) { 250 }; vm.refresh(); prepare(vm)
        vm.submit(); assertEquals(0, repo.writes)
        vm.editCode("wrong"); assertFalse(vm.state.codeVerified); assertTrue(vm.state.draft.boxIds.isEmpty())
        vm.verifyCode(); assertTrue(HandoverIssue.CODE in vm.state.issues)
        vm.editCode("FR123456"); vm.verifyCode(); vm.toggleBox("b"); vm.requestConfirmation(); vm.submit()
        assertEquals(1, repo.writes); assertNotNull(vm.state.order!!.handover)
    }
    @Test fun restoredSelectionRequiresCustomerCodeAgain() = runBlocking {
        val saved = SavedStateHandle(); val repo = Repository(); val vm = StaffHandoverViewModel(saved, repo) { 250 }; vm.refresh(); prepare(vm)
        val restored = StaffHandoverViewModel(saved, repo) { 250 }; restored.refresh()
        assertEquals("o", restored.state.order!!.id); assertFalse(restored.state.codeVerified); assertTrue(restored.state.draft.code.isEmpty())
    }
    @Test fun failedRefreshAndRevocationBlockHandover() = runBlocking {
        val repo = Repository(); val vm = StaffHandoverViewModel(SavedStateHandle(), repo) { 250 }; vm.refresh(); prepare(vm)
        repo.fail = true; vm.refresh(); vm.requestConfirmation(); vm.submit(); assertEquals(0, repo.writes)
        repo.fail = false; repo.data = HandoverWorkspace(access.copy(employee = access.employee.copy(status = EmployeeStatus.REVOKED)))
        vm.refresh(); assertNull(vm.state.order); assertTrue(vm.state.draft.code.isEmpty())
    }
    @Test fun refreshAfterLogoutCannotRestoreOrderData() = runBlocking {
        val repo = Repository().apply { gate = CompletableDeferred() }; val vm = StaffHandoverViewModel(SavedStateHandle(), repo) { 250 }
        val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.refresh() }; vm.clear(); repo.gate!!.complete(Unit); job.join()
        assertTrue(vm.state.workspace.orders.isEmpty()); assertNull(vm.state.workspace.access)
    }
    @Test fun refreshRemovesUnavailableSelectionAndClosesOldConfirmation() = runBlocking {
        val repo = Repository(); val vm = StaffHandoverViewModel(SavedStateHandle(), repo) { 250 }
        vm.refresh(); prepare(vm); vm.requestConfirmation()
        val replacement = box.copy(id = "replacement")
        repo.data = repo.data.copy(boxes = listOf(box.copy(handover = PackedBoxHandover("other", "FRother", 240)), replacement))
        vm.refresh()
        assertTrue(vm.state.draft.boxIds.isEmpty()); assertFalse(vm.state.confirm)
        vm.toggleBox("replacement")
        assertEquals(listOf("replacement"), vm.state.draft.boxIds); assertTrue(vm.state.ready)
    }
}
