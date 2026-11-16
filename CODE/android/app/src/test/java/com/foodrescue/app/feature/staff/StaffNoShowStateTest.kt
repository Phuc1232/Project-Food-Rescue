package com.foodrescue.app.feature.staff

import androidx.lifecycle.SavedStateHandle
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.staff.presentation.*
import com.foodrescue.app.feature.surprisebox.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class StaffNoShowStateTest {
    private val access = StaffAccess(StoreEmployee("e", 7, "An", "an@example.com", "", pickupPermissions,
        EmployeeStatus.ACTIVE, 1, 1, 1, emptyList()), "Tiệm bánh", true)
    private val order = BoxReservation("o", 1, 1, PickupWindow(200, 400), 1, 100, 25000,
        QrPayment("FR123456", 25000, PaymentStatus.SUCCEEDED, 50), boxSnapshot = CatalogBox(1, "Tiệm", BoxCategory.BAKERY,
            "Hộp", 25000, 60000, 5, "", "", "Sữa", "", 0, 18, "AB", storeId = 7, batchId = "batch"))
    private inner class Repository : StaffNoShowRepository {
        var data = HandoverWorkspace(access, listOf(order)); var writes = 0; var failLoad = false; var loseReceipt = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun load(): HandoverWorkspace { val value = data; gate?.await(); check(!failLoad); return value }
        override suspend fun confirm(draft: NoShowDraft): BoxReservation {
            val result = data.orders.single().confirmNoShow(checkNotNull(data.access), draft, data.boxes, 400)
            data = data.copy(orders = listOf(result)); writes++; check(!loseReceipt); return result
        }
    }
    private suspend fun prepare(vm: StaffNoShowViewModel) {
        vm.open("o"); vm.refresh(); vm.edit(vm.state.draft.copy(customerAbsent = true, storeReady = true, note = "Đã đối chiếu"))
    }
    @Test fun bothAttestationsAndFinalConfirmationAreRequired() = runBlocking {
        val repo = Repository(); val vm = StaffNoShowViewModel(SavedStateHandle(), repo) { 400 }; prepare(vm)
        vm.submit(); assertEquals(0, repo.writes)
        vm.edit(vm.state.draft.copy(storeReady = false)); vm.requestConfirmation(); vm.submit(); assertEquals(0, repo.writes)
        vm.edit(vm.state.draft.copy(storeReady = true)); vm.requestConfirmation(); vm.submit(); vm.submit()
        assertEquals(1, repo.writes); assertNotNull(vm.state.order!!.noShow)
    }
    @Test fun restoredDraftDoesNotRestoreTrustChecksOrConfirmation() = runBlocking {
        val saved = SavedStateHandle(); val repo = Repository(); val vm = StaffNoShowViewModel(saved, repo) { 400 }; prepare(vm); vm.requestConfirmation()
        val restored = StaffNoShowViewModel(saved, repo) { 400 }
        assertEquals(vm.state.draft.requestId, restored.state.draft.requestId); assertEquals("Đã đối chiếu", restored.state.draft.note)
        assertFalse(restored.state.draft.customerAbsent); assertFalse(restored.state.draft.storeReady)
        assertFalse(restored.state.current); assertFalse(restored.state.confirm); restored.submit(); assertEquals(0, repo.writes)
    }
    @Test fun failedRefreshAndRevokedPermissionBlockWrites() = runBlocking {
        val repo = Repository(); val vm = StaffNoShowViewModel(SavedStateHandle(), repo) { 400 }; prepare(vm)
        repo.failLoad = true; vm.refresh(); vm.requestConfirmation(); vm.submit(); assertEquals(0, repo.writes)
        repo.failLoad = false; repo.data = HandoverWorkspace(access.copy(employee = access.employee.copy(status = EmployeeStatus.REVOKED)))
        vm.refresh(); assertFalse(vm.state.canEdit); assertNull(vm.state.order)
    }
    @Test fun lostReceiptRecoversWithoutDuplicateMutation() = runBlocking {
        val repo = Repository().apply { loseReceipt = true }; val vm = StaffNoShowViewModel(SavedStateHandle(), repo) { 400 }; prepare(vm)
        vm.requestConfirmation(); vm.submit(); assertFalse(vm.state.current)
        vm.refresh(); assertEquals(vm.state.draft.requestId, vm.state.order!!.noShow!!.requestId)
        vm.requestConfirmation(); vm.submit(); assertEquals(1, repo.writes)
    }
    @Test fun lateRefreshCannotRestoreClearedAccountData() = runBlocking {
        val repo = Repository(); val vm = StaffNoShowViewModel(SavedStateHandle(), repo) { 400 }; prepare(vm)
        repo.gate = CompletableDeferred(); val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.refresh() }
        vm.clear(); repo.gate!!.complete(Unit); job.join(); assertTrue(vm.state.draft.orderId.isBlank()); assertTrue(vm.state.workspace.orders.isEmpty())
    }
    @Test fun newIncidentInvalidatesOpenConfirmation() = runBlocking {
        val repo = Repository(); val vm = StaffNoShowViewModel(SavedStateHandle(), repo) { 400 }; prepare(vm); vm.requestConfirmation()
        repo.data = repo.data.copy(orders = listOf(order.recordHandoverFailure(access, HandoverFailureDraft("f", "o", HandoverFailureReason.BOX_UNAVAILABLE), 400)))
        vm.refresh(); assertFalse(vm.state.confirm); vm.submit(); assertEquals(0, repo.writes)
    }
}
