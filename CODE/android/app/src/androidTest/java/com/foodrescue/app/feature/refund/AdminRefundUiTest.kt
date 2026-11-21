package com.foodrescue.app.feature.refund

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.foodrescue.app.core.designsystem.theme.FoodRescueTheme
import com.foodrescue.app.core.designsystem.component.FoodRescuePage
import com.foodrescue.app.dashboard.admin.AdminShell
import com.foodrescue.app.dashboard.admin.AdminTab
import com.foodrescue.app.feature.discovery.data.LocalCatalogRepository
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.refund.domain.*
import com.foodrescue.app.feature.refund.presentation.*
import com.foodrescue.app.feature.refund.presentation.component.ManagedRefundSummary
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class AdminRefundUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = 1795230000000L
    private val order = BoxReservation("one", 1, 2, PickupWindow(now - 10_000, now + 50_000), now - 100_000, now - 50_000, 30_000,
        QrPayment("FR128609", 60_000, PaymentStatus.SUCCEEDED, now - 60_000), collectedAtMillis = now - 5000,
        boxSnapshot = LocalCatalogRepository().getBoxes().first().copy(store = "Tiệm Bánh Mộc", name = "Hộp bánh cuối ngày"))
    private val note = "Đã đối chiếu bằng chứng và giao dịch gốc. Hoàn phần giá trị bị thiếu cho khách hàng."
    private val pending = RefundCase("account1", "one", "HT268501", RefundSource.COMPLAINT, "KN268501", note, 20_000, now, order)
    private val owner = pending.copy(accountKey = "account2", orderId = "two", id = "HT268502", source = RefundSource.STORE_CANCELLATION,
        sourceId = "HC1", amount = 60_000, reason = "Cửa hàng không còn đủ hộp để giao", order = order.copy(id = "two",
            collectedAtMillis = null, payment = order.payment!!.copy(reference = "FR128610"),
            ownerCancellation = OwnerCancellationRequest(OwnerCancellationReason.OUT_OF_STOCK, "Không đủ hộp", now)))
    private inner class Repository : AdminRefundRepository {
        var rows = listOf(pending, owner)
        var fail = false
        var stale = false
        var writes = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun load(): List<RefundCase> { check(!fail); return rows }
        override suspend fun decide(expected: RefundCase, approve: Boolean, note: String): RefundCase {
            gate?.await()
            if (stale) throw StaleRefund()
            check(!fail)
            val order = expected.decide(approve, note, "admin@foodrescue.vn", now + 5000)
            val updated = expected.copy(order = order, transaction = order.adminRefunds.single())
            rows = rows.map { if (it.key == updated.key) updated else it }; writes++
            return updated
        }
    }
    @Suppress("DEPRECATION")
    @Before fun window() { compose.activityRule.scenario.onActivity {
        it.enableEdgeToEdge(); it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    } }
    private fun start(repo: Repository, large: Boolean = false): AdminRefundsViewModel {
        val vm = AdminRefundsViewModel(SavedStateHandle(), repo)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.5f else density.fontScale)) {
                FoodRescueTheme {
                    val state = vm.state
                    LaunchedEffect(vm) { vm.refresh() }
                    when {
                        state.approve != null -> AdminRefundDecisionScreen(state, vm::back, vm::note, vm::review, vm::confirm, vm::dismiss)
                        state.selected != null -> AdminRefundDetailScreen(state, vm::back, vm::prepare, vm::refresh)
                        else -> AdminShell(AdminTab.FINANCE, {}, {}) {
                            AdminRefundsScreen(state, vm::search, vm::filter, vm::open, vm::refresh)
                        }
                    }
                }
            }
        }
        compose.waitUntil(5000) { !vm.state.loading }
        return vm
    }
    private fun open(reference: String) {
        compose.onNode(hasScrollToIndexAction())
            .performScrollToNode(hasText(reference))
        compose.onNodeWithText(reference).performClick()
    }
    private fun enterNote() {
        compose.onNodeWithText("Lý do xử lý").performScrollTo().performTextInput(note)
        closeSoftKeyboard()
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(500, 5000)
        File(instrumentation.targetContext.getExternalFilesDir(null), "admin-refund-$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun queueSearchAndFiltersHandleEmptyResults() {
        start(Repository()); shot("queue")
        compose.onNodeWithContentDescription("Tìm yêu cầu hoàn tiền").performTextInput("khongtontai")
        closeSoftKeyboard()
        compose.onNode(hasScrollToIndexAction())
            .performScrollToNode(hasText("Không tìm thấy yêu cầu"))
        compose.onNodeWithText("Không tìm thấy yêu cầu").assertIsDisplayed()
        compose.onNodeWithContentDescription("Xóa tìm kiếm").performScrollTo().performClick()
        compose.onNodeWithText("Đã hoàn · 0").performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction())
            .performScrollToNode(hasText("Không có yêu cầu trong mục này"))
        compose.onNodeWithText("Không có yêu cầu trong mục này").assertIsDisplayed()
    }
    @Test fun largeTextKeyboardValidationConfirmationAndDuplicateClick() {
        val repo = Repository(); val vm = start(repo, true)
        open("FR128609"); shot("detail-large-text")
        compose.onNodeWithText("Xử lý hoàn tiền").performClick()
        compose.onNodeWithText("Xem lại quyết định").performClick()
        compose.onNodeWithText("Nhập lý do từ 10 đến 500 ký tự hợp lệ.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Lý do xử lý").performScrollTo().performTextInput(note)
        compose.onNodeWithText("Lý do xử lý").assertIsFocused().assertIsDisplayed()
        shot("keyboard"); closeSoftKeyboard()
        compose.onNodeWithText("Xem lại quyết định").performClick(); shot("confirmation-large-text")
        repo.gate = CompletableDeferred()
        compose.onNodeWithText("Xác nhận hoàn tiền").performClick()
        compose.runOnIdle { vm.confirm() }
        compose.onNodeWithText("Xác nhận hoàn tiền").assertIsNotEnabled()
        compose.runOnIdle { repo.gate!!.complete(Unit) }
        compose.waitUntil(5000) { !vm.state.busy }
        assertEquals(1, repo.writes); assertEquals(AdminRefundStatus.PROCESSING, vm.state.selected!!.status)
        compose.onAllNodesWithText("Đang hoàn tiền").onFirst().assertIsDisplayed(); shot("processing")
    }
    @Test fun ownerRejectionDoesNotCreateRefundAndShowsAudit() {
        val repo = Repository(); val vm = start(repo)
        open("FR128610"); compose.onNodeWithText("Từ chối yêu cầu hủy").performClick()
        enterNote(); compose.onNodeWithText("Xem lại quyết định").performClick()
        compose.onNodeWithText("Xác nhận từ chối").performClick()
        compose.waitUntil(5000) { !vm.state.busy }
        assertEquals(AdminRefundStatus.REJECTED, vm.state.selected!!.status)
        assertNull(vm.state.selected!!.order!!.cancelledAtMillis)
        compose.onNodeWithText("Lịch sử xử lý").performScrollTo().assertIsDisplayed(); shot("rejected-history")
    }
    @Test fun writeFailureKeepsDraftAndRetryDoesNotDuplicate() {
        val repo = Repository(); val vm = start(repo)
        open("FR128609"); compose.onNodeWithText("Xử lý hoàn tiền").performClick(); enterNote()
        repo.fail = true
        compose.onNodeWithText("Xem lại quyết định").performClick(); compose.onNodeWithText("Xác nhận hoàn tiền").performClick()
        compose.waitUntil(5000) { !vm.state.busy }
        assertEquals(note, vm.state.note); assertEquals(0, repo.writes)
        assertNotNull(vm.state.error)
        compose.onNodeWithText(vm.state.error!!).assertIsDisplayed(); shot("save-error")
        repo.fail = false
        compose.onNodeWithText("Xem lại quyết định").performClick(); compose.onNodeWithText("Xác nhận hoàn tiền").performClick()
        compose.waitUntil(5000) { !vm.state.busy }; assertEquals(1, repo.writes)
    }
    @Test fun staleSnapshotClosesConfirmationAndRequiresReload() {
        val repo = Repository(); val vm = start(repo)
        open("FR128609"); compose.onNodeWithText("Xử lý hoàn tiền").performClick(); enterNote(); repo.stale = true
        compose.onNodeWithText("Xem lại quyết định").performClick(); compose.onNodeWithText("Xác nhận hoàn tiền").performClick()
        compose.waitUntil(5000) { !vm.state.busy }
        assertNull(vm.state.approve); assertFalse(vm.state.confirming)
        compose.onNodeWithText("Xử lý hoàn tiền").assertIsNotEnabled()
        compose.onNodeWithText("Tải lại").performScrollTo().performClick()
        compose.waitUntil(5000) { !vm.state.busy }; assertNull(vm.state.error)
    }
    @Test fun customerCompletedRefundDisplaysRealOutcome() {
        val refund = ManagedRefund("HT268501", RefundSource.COMPLAINT, "KN268501", note, 20_000, now,
            AdminRefundStatus.COMPLETED, listOf(RefundEvent(AdminRefundStatus.COMPLETED, "Hệ thống", now + 5000, "Đã hoàn tiền")))
        compose.setContent { FoodRescueTheme { FoodRescuePage("Chi tiết hoàn tiền", {}) { ManagedRefundSummary(refund) } } }
        compose.onNodeWithText("Đã hoàn tiền").assertIsDisplayed()
        compose.onNodeWithText("Khoản hoàn đã được ghi nhận về phương thức thanh toán ban đầu.").assertIsDisplayed()
        shot("customer-result")
    }
    @Test fun failedTransactionCanBeRetriedWithSameAmountAndHistory() {
        val repo = Repository()
        val refund = ManagedRefund(pending.id, pending.source, pending.sourceId, note, pending.amount, now,
            AdminRefundStatus.FAILED, listOf(RefundEvent(AdminRefundStatus.PROCESSING, "admin@foodrescue.vn", now, note),
                RefundEvent(AdminRefundStatus.FAILED, "Hệ thống", now + 2000, "Chưa kết nối được dịch vụ thanh toán.")))
        repo.rows = listOf(pending.copy(order = order.copy(adminRefunds = listOf(refund)), transaction = refund))
        val vm = start(repo)
        open("FR128609"); shot("failed")
        compose.onNodeWithText("Thử lại hoàn tiền").performClick(); enterNote()
        compose.onNodeWithText("Xem lại quyết định").performClick(); compose.onNodeWithText("Xác nhận hoàn tiền").performClick()
        compose.waitUntil(5000) { !vm.state.busy }
        assertEquals(pending.id, vm.state.selected!!.transaction!!.id)
        assertEquals(pending.amount, vm.state.selected!!.transaction!!.amount)
        assertEquals(3, vm.state.selected!!.transaction!!.history.size)
    }
    @Test fun leavingAccountCannotRestoreOldRefundState() {
        val repo = Repository(); val vm = start(repo)
        open("FR128609"); compose.onNodeWithText("Xử lý hoàn tiền").performClick(); enterNote()
        repo.gate = CompletableDeferred()
        compose.onNodeWithText("Xem lại quyết định").performClick(); compose.onNodeWithText("Xác nhận hoàn tiền").performClick()
        compose.runOnIdle { vm.clear(); repo.gate!!.complete(Unit) }
        compose.waitForIdle()
        assertTrue(vm.state.cases.isEmpty()); assertNull(vm.state.selectedKey); assertEquals(0, repo.writes)
    }
}
