package com.foodrescue.app.feature.staff

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.foodrescue.app.core.designsystem.theme.FoodRescueTheme
import com.foodrescue.app.dashboard.staff.StaffRoute
import com.foodrescue.app.feature.menu.domain.MenuUnit
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.staff.presentation.*
import com.foodrescue.app.feature.surprisebox.domain.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class StaffNoShowUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun matchMainActivityWindow() {
        compose.activityRule.scenario.onActivity {
            it.enableEdgeToEdge()
            it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }
    private val now = System.currentTimeMillis()
    private val access = StaffAccess(StoreEmployee("e", 7, "Nguyễn An", "an@example.com", "", pickupPermissions,
        EmployeeStatus.ACTIVE, 1, 1, 1, emptyList()), "Tiệm Bánh An Nhiên", true)
    private val order = BoxReservation("o", 1, 1, PickupWindow(now - 3600000, now - 600000), now - 7200000, now - 6600000, 25000,
        QrPayment("FR123456", 25000, PaymentStatus.SUCCEEDED, now - 7000000), boxSnapshot = CatalogBox(1, "Tiệm", BoxCategory.BAKERY,
            "Hộp bánh cuối ngày", 25000, 60000, 5, "", "", "Sữa", "", 0, 18, "AB", storeId = 7, batchId = "batch"))
    private val box = PackedBox("b12345678901", 7, "batch", "Hộp bánh cuối ngày", 60000, "Sữa, lúa mì",
        listOf(PackedItem("i", "Bánh mì bơ tỏi", MenuUnit.PIECE, 30000, "2", 60000)), now + 3600000, BoxStorage.AMBIENT, "p", "Bình", now - 3600000)
    private inner class Repository : StaffNoShowRepository {
        var row = order; var writes = 0
        override suspend fun load() = HandoverWorkspace(access, listOf(row), listOf(box))
        override suspend fun confirm(draft: NoShowDraft) = row.confirmNoShow(access, draft, listOf(box), now).also { row = it; writes++ }
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(600, 5000)
        File(instrumentation.targetContext.getExternalFilesDir(null), "noshow-$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun confirmationRequiresChecksThenShowsReceiptAndReinspectionAction() {
        val repo = Repository(); val vm = StaffNoShowViewModel(SavedStateHandle(), repo) { now }; vm.open("o")
        var selected: PackedBox? = null
        compose.setContent { FoodRescueTheme { StaffNoShowRoute(vm, {}, { selected = it }) } }
        compose.waitUntil(5000) { vm.state.canEdit }; shot("form")
        compose.onNodeWithText("Xác nhận không đến nhận").performClick()
        assertEquals(0, repo.writes)
        compose.onNodeWithText("Đã đối chiếu: khách chưa nhận hàng").performScrollTo().performClick()
        compose.onNodeWithText("Cửa hàng có thể giao đúng cam kết").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Hộp để riêng ${box.code}").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Ghi chú xác minh").performScrollTo().performClick()
        compose.waitUntil(5000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        compose.onNodeWithContentDescription("Ghi chú xác minh").performScrollTo().assertIsDisplayed()
            .performTextReplacement("Đã kiểm tra đơn và quầy nhận hàng")
        shot("selection")
        compose.onNodeWithText("Xác nhận không đến nhận").performClick()
        compose.onNodeWithText("Ghi nhận khách không đến?").assertIsDisplayed(); shot("confirmation")
        compose.onNodeWithText("Xác nhận No-show").performClick()
        compose.waitUntil(5000) { vm.state.order?.noShow != null && !vm.state.busy }
        compose.onNodeWithText("Đã xác nhận không đến nhận").assertIsDisplayed(); shot("receipt")
        compose.onNodeWithText("Kiểm tra lại hộp").performScrollTo().performClick()
        assertEquals(box.id, selected?.id); assertEquals(1, repo.writes)
    }
    @Test fun viewOnlyEmployeeCannotConfirmAndExistingIncidentExplainsBlock() {
        val incident = order.recordHandoverFailure(access, HandoverFailureDraft("f", "o", HandoverFailureReason.BOX_UNAVAILABLE), now)
        val state = StaffNoShowUiState(HandoverWorkspace(access.copy(employee = access.employee.copy(permissions = setOf(EmployeePermission.VIEW_ORDERS))), listOf(incident)),
            NoShowDraft("n", "o"), loading = false, current = true, now = now)
        compose.setContent { FoodRescueTheme { StaffNoShowScreen(state, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Xác nhận không đến nhận").assertIsNotEnabled()
        compose.onNodeWithText("Đơn có sự cố bàn giao. Chuyển chủ cửa hàng xử lý, không xác nhận No-show.").performScrollTo().assertIsDisplayed()
    }
    @Test fun noAssignedBoxesIsAnExplicitSupportedState() {
        val state = StaffNoShowUiState(HandoverWorkspace(access, listOf(order)), NoShowDraft("n", "o", customerAbsent = true, storeReady = true),
            loading = false, current = true, now = now)
        var requested = false
        compose.setContent { FoodRescueTheme { StaffNoShowScreen(state, {}, {}, { requested = true }, {}, {}) } }
        compose.onNodeWithText("Chưa chọn hộp để riêng").performScrollTo().assertIsDisplayed(); shot("no-boxes")
        compose.onNodeWithText("Xác nhận không đến nhận").performClick(); assertTrue(requested)
    }
    @Test fun shellNavigatesOverdueToNoShowReinspectionAndBackWithoutChangingPayment() {
        var currentOrder = order
        var currentBox = box
        val packing = StaffPackingViewModel(SavedStateHandle(), object : StaffPackingRepository {
            override suspend fun load() = StaffWorkspace(access, boxes = listOf(currentBox))
            override suspend fun complete(draft: PackingDraft): PackedBox = error("Packing is outside this flow")
        }) { now }
        val handover = StaffHandoverViewModel(SavedStateHandle(), object : StaffHandoverRepository {
            override suspend fun load() = HandoverWorkspace(access, listOf(currentOrder), listOf(currentBox))
            override suspend fun complete(draft: HandoverDraft): BoxReservation = error("No-show cannot be handed over")
            override suspend fun recordFailure(draft: HandoverFailureDraft): BoxReservation = error("No incident in this flow")
        }) { now }
        val noShow = StaffNoShowViewModel(SavedStateHandle(), object : StaffNoShowRepository {
            override suspend fun load() = HandoverWorkspace(access, listOf(currentOrder), listOf(currentBox))
            override suspend fun confirm(draft: NoShowDraft): BoxReservation {
                currentOrder = currentOrder.confirmNoShow(access, draft, listOf(currentBox), now)
                val receipt = checkNotNull(currentOrder.noShow)
                currentBox = currentBox.copy(noShow = PackedBoxNoShow(currentOrder.id, currentOrder.reference, receipt.requestId, receipt.atMillis))
                return currentOrder
            }
        }) { now }
        val condition = StaffBoxConditionViewModel(SavedStateHandle(), object : StaffBoxConditionRepository {
            override suspend fun load() = BoxConditionWorkspace(access, listOf(currentBox))
            override suspend fun inspect(draft: BoxInspectionDraft) = currentBox.inspect(access, draft, now).also { currentBox = it }
        }) { now }
        compose.setContent { FoodRescueTheme { StaffRoute(packing, handover, condition, noShow, {}, {}) } }
        compose.waitUntil(5000) { !packing.state.loading }
        compose.onNodeWithText("Bàn giao").performClick()
        compose.waitUntil(5000) { !handover.state.loading }
        compose.onNodeWithText("Quá giờ").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(HandoverFilter.OVERDUE, handover.state.filter) }
        shot("overdue-queue")
        compose.onNodeWithText("Hộp bánh cuối ngày").performScrollTo().performClick()
        compose.onNodeWithText("Xác minh khách không đến").performScrollTo().performClick()
        compose.waitUntil(5000) { noShow.state.canEdit }
        compose.onNodeWithText("Đã đối chiếu: khách chưa nhận hàng").performScrollTo().performClick()
        compose.onNodeWithText("Cửa hàng có thể giao đúng cam kết").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Hộp để riêng ${box.code}").performScrollTo().performClick()
        compose.onNodeWithText("Xác nhận không đến nhận").performClick()
        compose.onNodeWithText("Xác nhận No-show").performClick()
        compose.waitUntil(5000) { noShow.state.order?.noShow != null && !noShow.state.busy }
        assertEquals(BoxCondition.RECHECK_REQUIRED, currentBox.conditionAt(now))
        compose.onNodeWithText("Kiểm tra lại hộp").performScrollTo().performClick()
        compose.waitUntil(5000) { condition.state.canEdit }
        assertEquals(currentOrder.noShow!!.requestId, condition.state.draft.noShowRequestId)
        compose.onNodeWithText("Đạt yêu cầu").performScrollTo().performClick()
        listOf("Chất lượng món ăn", "Bao bì nguyên vẹn", "Điều kiện bảo quản", "Thông tin dị ứng").forEach {
            compose.onNodeWithText(it).performScrollTo().performClick()
        }
        compose.onNodeWithText("Lưu kết quả kiểm tra").performClick()
        compose.onNodeWithText("Xác nhận đạt yêu cầu").performClick()
        compose.waitUntil(5000) { condition.state.saved && !condition.state.busy }
        shot("reinspection")
        compose.onNodeWithText("Xem chi tiết hộp").performClick()
        compose.onNodeWithText("Đã xác nhận không đến nhận").assertIsDisplayed()
        compose.onNodeWithText("Về danh sách đơn").performClick()
        compose.waitUntil(5000) { handover.state.draft.orderId.isBlank() && !handover.state.loading }
        compose.onNodeWithText("No-show").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Hộp bánh cuối ngày").performScrollTo().assertIsDisplayed()
        assertEquals(order.payment, currentOrder.payment)
        assertEquals(box.expiresAt, currentBox.expiresAt)
        assertEquals(OrderStatus.NO_SHOW, currentOrder.statusAt(now))
        assertEquals(BoxCondition.READY, currentBox.conditionAt(now))
        assertNull(currentOrder.collectedAtMillis)
    }
}
