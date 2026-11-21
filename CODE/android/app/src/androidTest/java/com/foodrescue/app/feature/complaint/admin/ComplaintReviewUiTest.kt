package com.foodrescue.app.feature.complaint.admin

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
import com.foodrescue.app.dashboard.admin.AdminRoute
import com.foodrescue.app.feature.complaint.domain.*
import com.foodrescue.app.feature.complaint.presentation.*
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.store.domain.*
import com.foodrescue.app.feature.store.presentation.StoreApplicationsViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class ComplaintReviewUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Suppress("DEPRECATION")
    @Before fun window() { compose.activityRule.scenario.onActivity {
        it.enableEdgeToEdge(); it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    } }
    private val now = 1795230000000L
    private val order = BoxReservation("order1", 1, 2, PickupWindow(now - 10_000, now + 50_000), now - 100_000, now - 50_000, 30_000,
        QrPayment("FR128609", 60_000, PaymentStatus.SUCCEEDED, now - 60_000), collectedAtMillis = now - 5000)
    private val item = CustomerComplaint("KN268501", ComplaintOrder(order.id, order.reference, "Tiệm Bánh Mộc", "Hộp bánh cuối ngày", 2, 60_000, order.createdAtMillis),
        ComplaintReason.MISSING_ITEMS, "Hộp bánh nhận được thiếu hai sản phẩm so với thông tin cửa hàng đã xác nhận.", emptyList(), ComplaintStatus.PENDING, now, now)
    private val note = "Đã đối chiếu nội dung đơn và bằng chứng. Chấp nhận hoàn phần giá trị sản phẩm còn thiếu."
    private inner class Repository : ComplaintReviewRepository {
        var writes = 0
        var failure = false
        var stale = false
        var rows = listOf(ComplaintReviewCase("customer1", item, order, refundableAmount = 60_000),
            ComplaintReviewCase("customer2", item.copy(id = "KN268502", order = item.order.copy(store = "Bếp Nhà Lá"),
                reason = ComplaintReason.FOOD_QUALITY, status = ComplaintStatus.IN_REVIEW, createdAtMillis = now + 1000, updatedAtMillis = now + 1000), order, refundableAmount = 60_000))
        override suspend fun load(): List<ComplaintReviewCase> { if (failure) error("Read failed"); return rows }
        override suspend fun review(expected: ComplaintReviewCase, action: ComplaintReviewAction, note: String, refundAmount: Long): ComplaintReviewCase {
            if (stale) throw StaleComplaintReview()
            if (failure) error("Write failed")
            val row = rows.single { it.key == expected.key }
            val updated = row.complaint.review(expected.complaint.revision, action, note, refundAmount,
                row.refundableAmount, row.order, "admin@foodrescue.vn", now + 10_000)
            val result = row.copy(complaint = updated, refundableAmount = row.refundableAmount - refundAmount)
            rows = rows.map { if (it.key == row.key) result else it }; writes++
            return result
        }
    }
    private fun stores() = StoreApplicationsViewModel(SavedStateHandle(), object : StoreApplicationRepository {
        override suspend fun applications() = emptyList<StoreApplication>()
        override suspend fun review(accountKey: String, expectedRevision: Long, action: StoreReviewAction, note: String): StoreApplication = error("Unused")
    })
    private fun start(repository: Repository): AdminComplaintsViewModel {
        val vm = AdminComplaintsViewModel(SavedStateHandle(), repository) { now + 10_000 }
        val stores = stores()
        compose.setContent { FoodRescueTheme { AdminRoute("admin@foodrescue.vn", stores, vm, {}) } }
        compose.waitUntil(5000) { !vm.state.loading }
        compose.onNodeWithText("Xử lý", useUnmergedTree = true).performClick()
        return vm
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Compose idleness does not wait for Android's dialog-window and IME animations.
        instrumentation.uiAutomation.waitForIdle(500, 5000)
        File(instrumentation.targetContext.getExternalFilesDir(null), "complaint-review-$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private fun openFirst() {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Tiệm Bánh Mộc"))
        compose.onNodeWithText("Tiệm Bánh Mộc").performClick()
    }

    @Test fun queueReceiptComparisonPartialRefundConfirmationAndHistoryAreConnected() {
        val repo = Repository(); val vm = start(repo)
        shot("queue")
        openFirst(); shot("detail")
        compose.onNodeWithText("Tiếp nhận khiếu nại").performClick()
        compose.waitUntil(3000) { vm.state.selected?.complaint?.status == ComplaintStatus.IN_REVIEW }
        compose.onNodeWithText("Đối chiếu").performScrollTo().performClick()
        compose.onNodeWithText("Đã thanh toán", substring = true).performScrollTo().assertIsDisplayed()
        shot("comparison")
        compose.onNodeWithText("Ra quyết định").performClick()
        compose.onNodeWithText("Xem lại quyết định").performClick()
        compose.onNodeWithText("Nhập phản hồi từ 20", substring = true).assertExists()
        compose.onNodeWithText("Chấp nhận và đề nghị hoàn tiền").performScrollTo().performClick()
        compose.onNodeWithText("Số tiền hoàn (đ)").performScrollTo().performTextInput("60001")
        closeSoftKeyboard()
        compose.onNodeWithText("Phản hồi cho khách hàng").performScrollTo().performTextInput(note)
        shot("keyboard")
        closeSoftKeyboard()
        compose.onNodeWithText("Xem lại quyết định").performClick()
        compose.onNodeWithText("Số tiền phải lớn hơn", substring = true).assertExists()
        compose.onNodeWithText("Số tiền hoàn (đ)").performScrollTo().performTextReplacement("20000")
        closeSoftKeyboard()
        compose.onNodeWithText("Xem lại quyết định").performClick()
        compose.onNodeWithText("Xác nhận quyết định?").assertIsDisplayed(); shot("confirmation")
        compose.onNodeWithText("Xem lại", substring = false).performClick()
        assertEquals(1, repo.writes)
        compose.onNodeWithText("Xem lại quyết định").performClick()
        compose.onNodeWithText("Lưu quyết định").performClick()
        compose.waitUntil(3000) { vm.state.selected?.complaint?.refundRequest != null }
        assertEquals(20_000L, vm.state.selected!!.complaint.refundRequest!!.amount)
        assertEquals(2, repo.writes)
        compose.onNodeWithText("Lịch sử").performScrollTo().performClick()
        compose.onNodeWithText(note).performScrollTo().assertIsDisplayed(); shot("history")
    }
    @Test fun refreshErrorSearchEmptyAndMissingOrderHaveClearStates() {
        val repo = Repository(); repo.failure = true
        val vm = start(repo)
        compose.onNodeWithText("Không tải được khiếu nại", substring = true).assertExists()
        compose.runOnIdle { repo.failure = false; vm.refresh() }
        compose.waitUntil(3000) { vm.state.error == null && !vm.state.busy }
        compose.onNodeWithContentDescription("Tìm khiếu nại").performScrollTo().performTextInput("khong tim thay")
        closeSoftKeyboard()
        compose.onNodeWithText("Không tìm thấy khiếu nại").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { vm.search("tiem banh moc") }
        openFirst()
        compose.runOnIdle { repo.rows = repo.rows.map { it.copy(order = null, refundableAmount = 0) }; vm.refresh() }
        compose.waitUntil(3000) { vm.state.selected?.order == null && !vm.state.busy }
        compose.onNodeWithText("Đối chiếu").performScrollTo().performClick()
        compose.onNodeWithText("Chưa có dữ liệu đối chiếu").performScrollTo().assertIsDisplayed()
    }
    @Test fun staleReviewBlocksSavingUntilReloadAndFailedWritePreservesResponse() {
        val repo = Repository(); val vm = start(repo)
        openFirst()
        compose.onNodeWithText("Tiếp nhận khiếu nại").performClick()
        compose.waitUntil(3000) { vm.state.selected?.complaint?.status == ComplaintStatus.IN_REVIEW }
        compose.onNodeWithText("Ra quyết định").performClick()
        compose.onNodeWithText("Phản hồi cho khách hàng").performScrollTo().performTextInput(note)
        closeSoftKeyboard()
        compose.runOnIdle { repo.failure = true }
        compose.onNodeWithText("Xem lại quyết định").performClick()
        compose.onNodeWithText("Lưu quyết định").performClick()
        compose.waitUntil(3000) { vm.state.error != null }
        assertEquals(note, vm.state.note)
        compose.runOnIdle { repo.failure = false; repo.stale = true }
        compose.onNodeWithText("Xem lại quyết định").performClick()
        compose.onNodeWithText("Lưu quyết định").performClick()
        compose.waitUntil(3000) { vm.state.decision == null }
        compose.onNodeWithText("Ra quyết định").assertIsNotEnabled()
        compose.onNodeWithText("Tải lại").performScrollTo().performClick()
        compose.waitUntil(3000) { vm.state.error == null }
        compose.onNodeWithText("Ra quyết định").assertIsEnabled()
        assertEquals(1, repo.writes)
    }
    @Test fun customerSeesResponseTimelineAndQueuedRefundWithoutCompletedClaim() {
        val received = item.review(0, ComplaintReviewAction.RECEIVED, "", 0, 60_000, order, "admin", now + 1000)
        val queued = received.review(1, ComplaintReviewAction.REFUND_REQUESTED, note, 20_000, 60_000, order, "admin", now + 2000)
        compose.setContent { FoodRescueTheme { ComplaintDetailScreen(queued, {}, {}) } }
        compose.onNodeWithText("Chờ xử lý hoàn tiền").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Đề nghị đã được ghi nhận.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hoàn tiền thành công").assertDoesNotExist()
        shot("customer-result")
    }
    @Test fun largeTextDecisionKeepsResponseAndConfirmationReachable() {
        val row = Repository().rows.first()
        var state by mutableStateOf(AdminComplaintsState(cases = listOf(row), loading = false,
            selectedKey = row.key, decision = ComplaintReviewAction.REFUND_REQUESTED, amount = "20000", note = note, now = now))
        var confirmed = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                FoodRescueTheme {
                    AdminComplaintDecisionScreen(state, {}, { state = state.copy(decision = it) },
                        { state = state.copy(note = it) }, { state = state.copy(amount = it) },
                        { state = state.copy(confirming = true) }, { confirmed = true }, { state = state.copy(confirming = false) })
                }
            }
        }
        compose.onNodeWithText("Phản hồi cho khách hàng").performScrollTo().assertIsDisplayed()
        shot("large-text")
        compose.onNodeWithText("Xem lại quyết định").performClick()
        compose.onNodeWithText("Xác nhận quyết định?").assertIsDisplayed()
        shot("large-text-confirmation")
        compose.onNodeWithText("Lưu quyết định").performClick()
        assertTrue(confirmed)
    }
}
