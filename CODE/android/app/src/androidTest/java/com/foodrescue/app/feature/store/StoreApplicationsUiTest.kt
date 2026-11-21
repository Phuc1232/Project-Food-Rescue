package com.foodrescue.app.feature.store

import android.graphics.Bitmap
import android.content.Context
import androidx.test.espresso.Espresso.closeSoftKeyboard
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.foodrescue.app.app.navigation.AppNavHost
import com.foodrescue.app.core.common.GeoPoint
import com.foodrescue.app.core.designsystem.theme.FoodRescueTheme
import com.foodrescue.app.dashboard.admin.*
import com.foodrescue.app.feature.store.data.*
import com.foodrescue.app.feature.store.domain.*
import com.foodrescue.app.feature.store.presentation.*
import com.foodrescue.app.feature.complaint.domain.*
import com.foodrescue.app.feature.complaint.presentation.AdminComplaintsViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class StoreApplicationsUiTest {
    private fun complaints() = AdminComplaintsViewModel(SavedStateHandle(), object : ComplaintReviewRepository {
        override suspend fun load() = emptyList<ComplaintReviewCase>()
        override suspend fun review(expected: ComplaintReviewCase, action: ComplaintReviewAction, note: String, refundAmount: Long): ComplaintReviewCase = error("Unused")
    })
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Suppress("DEPRECATION") // Match the shipping Activity's keyboard-resize policy on the device.
    @Before fun window() { compose.activityRule.scenario.onActivity {
        it.enableEdgeToEdge(); it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    } }
    private val profile = OwnerStoreProfile("Tiệm Bánh Mộc", "Tiệm bánh", "Nguyễn An", "0901234567", "47 Nguyễn Thượng Hiền",
        "Bàn Cờ", description = "Bánh tươi mỗi ngày, cùng nhau giảm lãng phí thực phẩm.", location = GeoPoint(10.777, 106.699), contactEmail = "lienhe@banhmoc.vn")
    private val submitted = OwnerStore().submit(profile, 1795219200000L)
    private inner class Repository : StoreApplicationRepository {
        var writes = 0
        var rows = listOf(StoreApplication("one", -10000, submitted),
            StoreApplication("two", -10001, submitted.copy(profile = profile.copy(name = "Bếp Nhà Lá", category = "Quán ăn", area = "Bến Thành"))),
            StoreApplication("three", -10002, submitted.copy(status = StoreReviewStatus.APPROVED, profile = profile.copy(name = "An Nhiên Bakery"))))
        override suspend fun applications() = rows
        override suspend fun review(accountKey: String, expectedRevision: Long, action: StoreReviewAction, note: String): StoreApplication {
            val old = rows.single { it.accountKey == accountKey }
            val updated = old.copy(store = old.store.review(expectedRevision, action, note, "admin@foodrescue.vn", 1795229200000L))
            rows = rows.map { if (it.accountKey == accountKey) updated else it }; writes++
            return updated
        }
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.getExternalFilesDir(null), "admin-$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun dashboardQueueDetailAmendmentValidationAndConfirmationAreConnected() {
        val repo = Repository(); val vm = StoreApplicationsViewModel(SavedStateHandle(), repo)
        val cases = complaints()
        compose.setContent { FoodRescueTheme { AdminRoute("admin@foodrescue.vn", vm, cases, {}) } }
        compose.waitUntil(5000) { !vm.state.loading }; shot("overview")
        compose.onNodeWithText("Xử lý").assertIsEnabled()
        compose.onNodeWithText("Xét duyệt hồ sơ").performClick()
        compose.onNodeWithText("Tìm hồ sơ").assertIsDisplayed(); shot("queue")
        compose.onNodeWithText("Tiệm Bánh Mộc").performScrollTo().performClick()
        compose.onNodeWithText("Duyệt cửa hàng").assertIsEnabled(); shot("detail")
        compose.onNodeWithText("Yêu cầu bổ sung").performClick()
        compose.onNodeWithText("Xem lại quyết định").performClick()
        compose.onNodeWithText("Nhập nội dung cụ thể từ 10 đến 1.000 ký tự.").performScrollTo().assertIsDisplayed()
        assertEquals(0, repo.writes)
        compose.onNodeWithText("Thông tin cần bổ sung").performScrollTo().performTextInput("Bổ sung số nhà chính xác và kiểm tra lại ghim vị trí.")
        shot("decision-keyboard")
        compose.onNodeWithText("Xem lại quyết định").performClick()
        compose.onNodeWithText("Gửi yêu cầu bổ sung?").assertIsDisplayed(); shot("confirmation")
        compose.onNodeWithText("Gửi yêu cầu").performClick()
        compose.waitUntil(5000) { vm.state.selected?.store?.status == StoreReviewStatus.CHANGES_REQUIRED }
        assertEquals(1, repo.writes)
        compose.onNodeWithText("Cần bổ sung").assertIsDisplayed()
        compose.onNodeWithText("Duyệt cửa hàng").assertDoesNotExist(); shot("changes-result")
    }
    @Test fun approvalNeedsConfirmationAndStaleDecisionShowsRecoverableError() {
        val repo = Repository(); val vm = StoreApplicationsViewModel(SavedStateHandle(), repo)
        val cases = complaints()
        compose.setContent { FoodRescueTheme { AdminRoute("admin@foodrescue.vn", vm, cases, {}) } }
        compose.waitUntil(5000) { !vm.state.loading }
        compose.runOnIdle { vm.open("one"); vm.decide(StoreReviewAction.APPROVED) }
        compose.onNodeWithText("Xem lại quyết định").performClick()
        compose.onNodeWithText("Xác nhận duyệt cửa hàng?").assertIsDisplayed()
        compose.runOnIdle { repo.rows = repo.rows.map { if (it.accountKey == "one") it.copy(store = it.store.copy(revision = 20)) else it } }
        compose.onNodeWithText("Xác nhận duyệt").performClick()
        compose.waitUntil(5000) { vm.state.error != null && !vm.state.busy }
        compose.onNodeWithText("Duyệt cửa hàng").assertIsNotEnabled()
        compose.onNodeWithText("Tải lại hồ sơ").performScrollTo().performClick()
        compose.waitUntil(5000) { vm.state.error == null && !vm.state.busy }
        compose.onNodeWithText("Duyệt cửa hàng").performClick()
        compose.onNodeWithText("Xem lại quyết định").performClick()
        compose.onNodeWithText("Xác nhận duyệt").performClick()
        compose.waitUntil(5000) { vm.state.selected?.store?.status == StoreReviewStatus.APPROVED }
        assertEquals(1, repo.writes); shot("approved")
    }
    @Test fun searchEmptyAndRetryAreUsableWithoutInventedCounts() {
        var tries = 0
        val vm = StoreApplicationsViewModel(SavedStateHandle(), object : StoreApplicationRepository {
            override suspend fun applications(): List<StoreApplication> { if (++tries == 1) error("Offline"); return emptyList() }
            override suspend fun review(accountKey: String, expectedRevision: Long, action: StoreReviewAction, note: String): StoreApplication = error("Not applicable")
        })
        val cases = complaints()
        compose.setContent { FoodRescueTheme { AdminRoute("admin@foodrescue.vn", vm, cases, {}) } }
        compose.waitUntil(5000) { vm.state.error != null }
        compose.onNodeWithText("Không tải được hồ sơ. Vui lòng thử lại.").assertIsDisplayed()
        compose.onNodeWithText("Làm mới").performClick()
        compose.waitUntil(5000) { !vm.state.loading }
        compose.onNodeWithText("Cửa hàng").performClick()
        compose.onNodeWithContentDescription("Tìm hồ sơ").performTextInput("Không tồn tại")
        closeSoftKeyboard()
        compose.onNodeWithText("Không tìm thấy hồ sơ").performScrollTo().assertIsDisplayed()
    }
    @Test fun ownerScreenRefreshesDecisionFromSamePersistedApplication() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val email = "ui.review.${UUID.randomUUID()}@example.com"
        val key = LocalStoreRecords.accountKey(email)
        val owner = LocalOwnerStoreRepository(context, email)
        try {
            val pending = owner.submit(profile)
            val vm = StoreManagementViewModel(SavedStateHandle(), owner)
            compose.setContent { FoodRescueTheme { vm.state.store?.let { StoreReviewScreen(it, email, {}, {}) } } }
            compose.runOnIdle { vm.load() }
            compose.waitUntil(5000) { !vm.state.loading }
            LocalStoreApplicationRepository(context, "admin@foodrescue.vn").review(key, pending.revision,
                StoreReviewAction.CHANGES_REQUESTED, "Bổ sung số nhà chính xác")
            compose.runOnIdle { vm.load(force = true) }
            compose.waitUntil(5000) { vm.state.store?.status == StoreReviewStatus.CHANGES_REQUIRED }
            compose.onNodeWithText("Bổ sung hồ sơ").assertIsDisplayed()
            compose.onAllNodesWithText("Bổ sung số nhà chính xác").onFirst().performScrollTo().assertIsDisplayed()
            shot("owner-feedback")
        } finally {
            context.getSharedPreferences("owner_store_$key", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("owner_store_directory.v1", Context.MODE_PRIVATE).edit().remove("store.$key").commit()
        }
    }
    @Test fun sharedLoginRoutesAdminAndLogoutReturnsToAuthentication() {
        compose.setContent { FoodRescueTheme { AppNavHost() } }
        compose.onNode(hasText("Đăng nhập", substring = true) and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Email").performTextInput("admin@foodrescue.vn")
        compose.onNodeWithContentDescription("Mật khẩu").performTextInput("FoodRescue123")
        compose.onNode(hasText("Đăng nhập") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Tiếp tục").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Tài khoản quản trị viên").fetchSemanticsNodes().isNotEmpty() }
        shot("actual-dashboard")
        compose.onNodeWithContentDescription("Tài khoản quản trị viên").performClick()
        compose.onNodeWithText("Đăng xuất").performClick()
        compose.onNodeWithText("Đăng nhập", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun storeMapShowsSavedPinAndAddress() {
        compose.setContent { FoodRescueTheme { StoreApplicationMapScreen(StoreApplication("one", -10000, submitted), {}) } }
        compose.onNodeWithText(profile.fullAddress).assertIsDisplayed()
        compose.onNodeWithText("10.777000, 106.699000").performScrollTo().assertIsDisplayed()
        shot("map")
    }
}
