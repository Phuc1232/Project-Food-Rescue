package com.foodrescue.app.feature.staff

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.foodrescue.app.core.designsystem.theme.FoodRescueTheme
import com.foodrescue.app.dashboard.staff.StaffRoute
import com.foodrescue.app.feature.menu.domain.MenuUnit
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.order.presentation.OwnerOrderDetailScreen
import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.staff.presentation.*
import com.foodrescue.app.feature.surprisebox.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StaffHandoverUiTest {
    @get:Rule val compose = createComposeRule()
    private val now = System.currentTimeMillis()
    private val access = StaffAccess(StoreEmployee("e", 7, "Nguyễn An", "an@example.com", "", pickupPermissions, EmployeeStatus.ACTIVE, 1, 1, 1, emptyList()), "Tiệm Bánh An Nhiên", true)
    private val order = BoxReservation("o", 1, 1, PickupWindow(now - 3600000, now + 3600000), now - 7200000, now - 5400000, 25000,
        QrPayment("FR123456", 25000, PaymentStatus.SUCCEEDED, now - 6000000), boxSnapshot = CatalogBox(1, "Tiệm Bánh An Nhiên", BoxCategory.BAKERY, "Hộp bánh cuối ngày", 25000, 60000, 5, "", "", "Sữa", "", 0, 18, "AB", storeId = 7, batchId = "batch"))
    private val box = PackedBox("b12345678901", 7, "batch", "Hộp bánh cuối ngày", 60000, "Sữa",
        listOf(PackedItem("i", "Bánh mì bơ tỏi", MenuUnit.PIECE, 30000, "2", 60000)), now + 7200000, BoxStorage.AMBIENT, "p", "Bình", now - 4000000)
    private inner class Repository : StaffHandoverRepository {
        var current = order
        override suspend fun load() = HandoverWorkspace(access, listOf(current), listOf(box))
        override suspend fun complete(draft: HandoverDraft) = current.completeHandover(access, draft, listOf(box), now).also { current = it }
        override suspend fun recordFailure(draft: HandoverFailureDraft) = current.recordHandoverFailure(access, draft, now).also { current = it }
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(600, 5000)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "handover-$name.png").outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun verifyCustomerCodeSelectPhysicalBoxAndConfirmReceipt() {
        val repo = Repository(); val vm = StaffHandoverViewModel(SavedStateHandle(), repo) { now }
        val packing = StaffPackingViewModel(SavedStateHandle(), object : StaffPackingRepository {
            override suspend fun load() = StaffWorkspace(access, boxes = listOf(box))
            override suspend fun complete(draft: PackingDraft): PackedBox = error("Packing is not assigned")
        }) { now }
        val condition = StaffBoxConditionViewModel(SavedStateHandle(), object : StaffBoxConditionRepository {
            override suspend fun load() = BoxConditionWorkspace(access, listOf(box))
            override suspend fun inspect(draft: BoxInspectionDraft) = box.inspect(access, draft, now)
        }) { now }
        compose.setContent { FoodRescueTheme { StaffRoute(packing, vm, condition, unusedNoShow(), {}, {}) } }
        compose.waitUntil(5000) { !packing.state.loading }
        compose.onNodeWithText("Bàn giao").performClick()
        compose.waitUntil(5000) { !vm.state.loading }; shot("queue")
        compose.onNodeWithText("Hộp bánh cuối ngày").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Mã nhận hàng khách cung cấp").performScrollTo().performClick().performTextReplacement("wrong")
        shot("code-keyboard")
        compose.onNodeWithText("Xác minh mã").performScrollTo().performClick()
        compose.onNodeWithText("Mã nhận hàng chưa khớp với đơn này.").assertExists()
        compose.onNodeWithContentDescription("Mã nhận hàng khách cung cấp").performScrollTo().performTextReplacement("FR123456")
        compose.onNodeWithText("Xác minh mã").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Chọn hộp ${box.code}").performScrollTo().performClick(); shot("selection")
        compose.onNodeWithText("Xác nhận bàn giao").performClick()
        compose.onNodeWithText("Xác nhận đã giao").performClick()
        compose.waitUntil(5000) { vm.state.order?.handover != null && !vm.state.busy }
        compose.onNodeWithText("Đã bàn giao thành công").assertIsDisplayed()
        compose.onNodeWithText("Xác nhận đã giao").assertDoesNotExist(); shot("receipt")
        assertEquals(listOf(box.id), repo.current.handover!!.boxIds)
    }
    @Test fun inabilityToHandOverRequiresReasonAndKeepsOrderWaiting() {
        val repo = Repository(); val vm = StaffHandoverViewModel(SavedStateHandle(), repo) { now }
        compose.setContent { FoodRescueTheme { StaffHandoverRoute(vm) } }
        compose.waitUntil(5000) { !vm.state.loading }
        compose.onNodeWithText("Hộp bánh cuối ngày").performScrollTo().performClick()
        compose.onNodeWithText("Không thể bàn giao").performScrollTo().performClick()
        compose.onNodeWithText("Ghi nhận sự cố").performClick()
        compose.onNodeWithText("Chọn lý do không thể bàn giao.").assertExists()
        compose.onNodeWithText("Không đủ hộp đạt yêu cầu").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Ghi chú sự cố").performScrollTo().performClick().performTextReplacement("Một hộp không còn đạt chất lượng")
        shot("incident-keyboard")
        compose.onNodeWithText("Ghi nhận sự cố").performClick()
        compose.waitUntil(5000) { repo.current.handoverFailures.isNotEmpty() && !vm.state.busy }
        assertNull(repo.current.collectedAtMillis); assertNull(repo.current.cancelledAtMillis)
        compose.onNodeWithText("Đã ghi nhận. Chủ cửa hàng có thể xem để xử lý.").assertExists(); shot("incident")
    }
    @Test fun ownerCanSeeEmployeeIncidentAndCompletedHandoverHistory() {
        val failed = order.recordHandoverFailure(access, HandoverFailureDraft("incident", order.id,
            HandoverFailureReason.BOX_UNAVAILABLE, "Chưa đủ hộp đạt chất lượng"), now)
        val completed = failed.completeHandover(access, HandoverDraft("handover", order.id, order.reference, listOf(box.id)), listOf(box), now)
        compose.setContent { FoodRescueTheme { OwnerOrderDetailScreen(completed, now, {}, {}) } }
        compose.onNodeWithText("Nhật ký bàn giao").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Đã giao 1 hộp").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Không đủ hộp đạt yêu cầu").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Chưa đủ hộp đạt chất lượng").assertExists()
        compose.onNodeWithText("Yêu cầu hủy đơn").assertDoesNotExist()
        shot("owner-history")
    }
}

private fun unusedNoShow() = StaffNoShowViewModel(SavedStateHandle(), object : StaffNoShowRepository {
    override suspend fun load() = HandoverWorkspace()
    override suspend fun confirm(draft: com.foodrescue.app.feature.order.domain.NoShowDraft): com.foodrescue.app.feature.order.domain.BoxReservation = error("No No-show order in this fixture")
})
