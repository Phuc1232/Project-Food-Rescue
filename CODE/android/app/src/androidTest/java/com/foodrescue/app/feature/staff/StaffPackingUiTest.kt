package com.foodrescue.app.feature.staff

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.foodrescue.app.core.designsystem.theme.FoodRescueTheme
import com.foodrescue.app.dashboard.staff.StaffRoute
import com.foodrescue.app.feature.menu.domain.*
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.staff.presentation.*
import com.foodrescue.app.feature.surprisebox.domain.*
import com.foodrescue.app.feature.surprisebox.presentation.component.boxDateTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StaffPackingUiTest {
    @get:Rule val compose = createComposeRule()
    private val now = System.currentTimeMillis() / 60000 * 60000
    private val member = StoreEmployee("an", -1, "Nguyễn An", "an@example.com", "", operationsPermissions, EmployeeStatus.ACTIVE, 1, now, now, emptyList())
    private val access = StaffAccess(member, "Tiệm Bánh An Nhiên", true)
    private val item = MenuItem("bread", "Bánh mì bơ tỏi", MenuCategory.BAKERY, MenuUnit.PIECE, 30000,
        "Bánh nướng giòn với bơ tỏi và lá thơm.", MenuStatus.ACTIVE, null, 1, 1)
    private val batch = SaleBatch("batch", BoxTemplate("t", "Hộp bánh cuối ngày", BoxCategory.BAKERY, "Bánh tươi trong ngày, phối hợp theo menu của tiệm.", "Lúa mì, sữa, trứng", 60000, 25000),
        12, now - 3600000, now + 3600000, now + 3600000, now + 7200000, BatchControl.PUBLISHED, sold = 4)
    private inner class Repository : StaffPackingRepository {
        var rows = emptyList<PackedBox>()
        override suspend fun load() = StaffWorkspace(access, listOf(item), listOf(batch.copy(packed = rows.size, eligible = rows.size)), rows)
        override suspend fun complete(draft: PackingDraft): PackedBox = draft.completePacking(access, batch, listOf(item), now).also { rows = rows + it }
    }
    private fun handover() = StaffHandoverViewModel(SavedStateHandle(), object : StaffHandoverRepository {
        override suspend fun load() = HandoverWorkspace(access)
        override suspend fun complete(draft: com.foodrescue.app.feature.order.domain.HandoverDraft): com.foodrescue.app.feature.order.domain.BoxReservation = error("No order in this fixture")
        override suspend fun recordFailure(draft: com.foodrescue.app.feature.order.domain.HandoverFailureDraft): com.foodrescue.app.feature.order.domain.BoxReservation = error("No order in this fixture")
    }) { now }
    private fun shot(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "staff-$name.png").outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private fun condition(repo: Repository) = StaffBoxConditionViewModel(SavedStateHandle(), object : StaffBoxConditionRepository {
        override suspend fun load() = BoxConditionWorkspace(access, repo.rows)
        override suspend fun inspect(draft: BoxInspectionDraft): PackedBox = repo.rows.first { it.id == draft.boxId }.inspect(access, draft, now).also { updated ->
            repo.rows = repo.rows.map { if (it.id == updated.id) updated else it }
        }
    }) { now }
    @Test fun packOneBoxThroughValueInspectionReviewAndReceipt() {
        val repo = Repository(); val vm = StaffPackingViewModel(SavedStateHandle(), repo) { now }
        val handover = handover()
        val condition = condition(repo)
        compose.setContent { FoodRescueTheme { StaffRoute(vm, handover, condition, unusedNoShow(), {}, {}) } }
        compose.waitUntil(5000) { !vm.state.loading }
        compose.onNodeWithText("Ca làm việc").assertIsDisplayed(); shot("overview")
        compose.onNodeWithText("Đóng gói hộp").performScrollTo().performClick()
        compose.onNodeWithText("Tiếp tục").performClick()
        assertEquals(0, vm.state.step)
        compose.onNodeWithContentDescription("Số lượng Bánh mì bơ tỏi").performScrollTo().performTextReplacement("2")
        compose.onNodeWithText("Tiếp tục").performClick()
        assertEquals(1, vm.state.step)
        compose.onNodeWithText("Kiểm tra trước khi đóng").assertIsDisplayed()
        compose.onNodeWithText(boxDateTime(batch.pickupEnd)).performScrollTo().performClick()
        onView(withId(android.R.id.button1)).perform(click())
        onView(withId(android.R.id.button1)).perform(click())
        compose.onNodeWithText("Chọn cách bảo quản").performScrollTo().performClick()
        compose.onNodeWithText("Nhiệt độ thường").performClick()
        compose.onNodeWithText("Món ăn đạt chất lượng").performScrollTo().performClick()
        compose.onNodeWithText("Bao bì sạch, kín và nguyên vẹn").performScrollTo().performClick()
        compose.onNodeWithText("Đã đối chiếu thành phần gây dị ứng").performScrollTo().performClick()
        compose.onNodeWithText("Tiếp tục").performClick()
        compose.onNodeWithText("Sẵn sàng lên kệ").assertIsDisplayed()
        compose.onNodeWithText("Hoàn tất đóng gói").assertIsEnabled(); shot("review")
        compose.onNodeWithText("Hoàn tất đóng gói").performClick()
        compose.waitUntil(5000) { vm.state.page == StaffPage.DETAIL && !vm.state.busy }
        compose.onNodeWithText("Đóng gói hoàn tất").assertIsDisplayed(); shot("receipt")
        assertEquals(1, repo.rows.size); assertEquals(60000L, repo.rows.single().value)
        compose.onNodeWithText("Kiểm tra tình trạng hộp").performClick()
        compose.waitUntil(5000) { condition.state.canEdit }
        compose.onNodeWithText("Không đạt").performScrollTo().performClick()
        compose.onNodeWithText("Bao bì không nguyên vẹn").performScrollTo().performClick()
        compose.onNodeWithText("Lưu kết quả kiểm tra").performClick()
        compose.onNodeWithText("Xác nhận loại hộp").performClick()
        compose.waitUntil(5000) { condition.state.saved && !condition.state.busy }
        compose.onNodeWithText("Xem chi tiết hộp").performClick()
        compose.waitUntil(5000) { vm.state.selectedBox?.inspections?.size == 1 }
        compose.onNodeWithText("Không đạt").assertIsDisplayed()
    }
    @Test fun menuAndDraftStayUsableWhenReturningToOverview() {
        val repo = Repository(); val vm = StaffPackingViewModel(SavedStateHandle(), repo) { now }
        val handover = handover()
        val condition = condition(repo)
        compose.setContent { FoodRescueTheme { StaffRoute(vm, handover, condition, unusedNoShow(), {}, {}) } }
        compose.waitUntil(5000) { !vm.state.loading }
        compose.onNodeWithText("Menu").performClick()
        compose.onNodeWithText("Bánh mì bơ tỏi").assertIsDisplayed(); shot("menu")
        compose.onNodeWithText("Ca làm").performClick()
        compose.onNodeWithText("Đóng gói hộp").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Số lượng Bánh mì bơ tỏi").performScrollTo().performClick().performTextReplacement("2")
        shot("contents")
        compose.onNodeWithText("Lưu và thoát").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(StaffPage.OVERVIEW, vm.state.page); assertEquals("2", vm.state.draft.selections.single().quantity) }
        compose.onNodeWithText("Tiếp tục đóng gói").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Số lượng Bánh mì bơ tỏi").performScrollTo().assertTextContains("2")
    }
}

private fun unusedNoShow() = StaffNoShowViewModel(SavedStateHandle(), object : StaffNoShowRepository {
    override suspend fun load() = HandoverWorkspace()
    override suspend fun confirm(draft: com.foodrescue.app.feature.order.domain.NoShowDraft): com.foodrescue.app.feature.order.domain.BoxReservation = error("No No-show order in this fixture")
})
