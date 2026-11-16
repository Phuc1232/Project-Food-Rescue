package com.foodrescue.app.feature.staff

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.foodrescue.app.core.designsystem.theme.FoodRescueTheme
import com.foodrescue.app.feature.menu.domain.MenuUnit
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.staff.presentation.*
import com.foodrescue.app.feature.surprisebox.domain.*
import com.foodrescue.app.feature.surprisebox.presentation.SaleBatchDetailScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StaffBoxConditionUiTest {
    @get:Rule val compose = createComposeRule()
    private val now = System.currentTimeMillis()
    private val access = StaffAccess(StoreEmployee("e", 7, "Nguyễn An", "an@example.com", "", setOf(EmployeePermission.HAND_OVER), EmployeeStatus.ACTIVE, 1, 1, 1, emptyList()), "Tiệm Bánh An Nhiên", true)
    private val box = PackedBox("b12345678901", 7, "batch", "Hộp bánh cuối ngày", 60000, "Sữa, lúa mì", listOf(PackedItem("i", "Bánh mì bơ tỏi", MenuUnit.PIECE, 30000, "2", 60000)), now + 7200000, BoxStorage.AMBIENT, "p", "Bình", now - 3600000)
    private inner class Repository : StaffBoxConditionRepository {
        var current = box
        override suspend fun load() = BoxConditionWorkspace(access, listOf(current))
        override suspend fun inspect(draft: BoxInspectionDraft) = current.inspect(access, draft, now).also { current = it }
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(600, 5000)
        File(instrumentation.targetContext.getExternalFilesDir(null), "condition-$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun rejectRequiresReasonAndFinalConfirmationThenShowsAuditReceipt() {
        val repo = Repository(); val vm = StaffBoxConditionViewModel(SavedStateHandle(), repo) { now }; vm.open(box)
        compose.setContent { FoodRescueTheme { StaffBoxConditionRoute(vm, {}) } }
        compose.waitUntil(5000) { vm.state.canEdit }; shot("form")
        compose.onNodeWithText("Không đạt").performScrollTo().performClick()
        compose.onNodeWithText("Lưu kết quả kiểm tra").performClick()
        compose.onNodeWithText("Chọn lý do hộp không đạt.").assertExists()
        compose.onNodeWithText("Bao bì không nguyên vẹn").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Ghi chú kiểm tra").performScrollTo().performClick().performTextReplacement("Bao bì rách, cần đóng hộp thay thế")
        shot("note-keyboard")
        compose.onNodeWithText("Lưu kết quả kiểm tra").performClick()
        compose.onNodeWithText("Loại hộp khỏi bàn giao?").assertIsDisplayed(); shot("confirmation")
        compose.onNodeWithText("Xác nhận loại hộp").performClick()
        compose.waitUntil(5000) { vm.state.saved && !vm.state.busy }
        assertEquals(BoxCondition.REJECTED, repo.current.conditionAt(now))
        compose.onNodeWithText("Đã loại khỏi bàn giao").assertIsDisplayed(); shot("receipt")
    }
    @Test fun passRequiresEveryQualityCheck() {
        val repo = Repository(); val vm = StaffBoxConditionViewModel(SavedStateHandle(), repo) { now }; vm.open(box)
        compose.setContent { FoodRescueTheme { StaffBoxConditionRoute(vm, {}) } }
        compose.waitUntil(5000) { vm.state.canEdit }
        compose.onNodeWithText("Đạt yêu cầu").performScrollTo().performClick()
        compose.onNodeWithText("Lưu kết quả kiểm tra").performClick()
        compose.onNodeWithText("Xác nhận đủ bốn mục kiểm tra.").assertExists()
        listOf("Chất lượng món ăn", "Bao bì nguyên vẹn", "Điều kiện bảo quản", "Thông tin dị ứng").forEach {
            compose.onNodeWithText(it).performScrollTo().performClick()
        }
        compose.onNodeWithText("Lưu kết quả kiểm tra").performClick()
        compose.onNodeWithText("Xác nhận đạt yêu cầu").performClick()
        compose.waitUntil(5000) { vm.state.saved && !vm.state.busy }
        assertTrue(repo.current.isEligibleAt(now)); assertEquals(1, repo.current.inspections.size)
    }
    @Test fun rejectedDetailShowsHistoryWithoutInspectionAction() {
        val rejected = box.inspect(access, BoxInspectionDraft("r", box.id, 0, InspectionResult.REJECTED, InspectionReason.FOOD_QUALITY, "Mùi bất thường"), now)
        compose.setContent { FoodRescueTheme { PackedBoxDetailScreen(rejected, now, {}, {}) } }
        compose.onNodeWithText("Không đạt").assertIsDisplayed()
        compose.onNodeWithText("Kiểm tra tình trạng hộp").assertDoesNotExist()
        compose.onNodeWithText("Lịch sử kiểm tra").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Mùi bất thường").performScrollTo().assertIsDisplayed(); shot("history")
    }
    @Test fun inventoryFilterExcludesRejectedFromReadyList() {
        val rejected = box.inspect(access, BoxInspectionDraft("r", box.id, 0, InspectionResult.REJECTED, InspectionReason.PACKAGING), now)
        val state = StaffPackingUiState(workspace = StaffWorkspace(access, boxes = listOf(rejected, box.copy(id = "ready"))),
            loading = false, verified = true, now = now, boxFilter = BoxCondition.READY)
        compose.setContent { FoodRescueTheme { StaffBoxesScreen(state, {}, {}, {}) } }
        compose.onNodeWithText("BOX-READY").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(box.code).assertDoesNotExist(); shot("inventory")
    }
    @Test fun ownerBatchDisplaysRejectedExpiredAndReadyCounts() {
        val template = BoxTemplate("t", box.templateName, BoxCategory.BAKERY, "Bánh tươi trong ngày", box.allergens, 60000, 25000)
        val batch = SaleBatch("batch", template, 4, now - 7200000, now - 3600000, now - 3600000, now + 3600000,
            BatchControl.PUBLISHED, packed = 5, eligible = 2, handedOver = 1, rejected = 1, expired = 1)
        compose.setContent { FoodRescueTheme { SaleBatchDetailScreen(batch, now, false, null, {}, {}, {}) } }
        compose.onNodeWithText("Đủ điều kiện bàn giao").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Không đạt").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hết hạn, chưa bàn giao").performScrollTo().assertIsDisplayed(); shot("owner-counts")
    }
}
