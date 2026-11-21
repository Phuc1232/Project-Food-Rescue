package com.foodrescue.app.feature.account

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.foodrescue.app.core.designsystem.theme.FoodRescueTheme
import com.foodrescue.app.dashboard.admin.AdminShell
import com.foodrescue.app.dashboard.admin.AdminTab
import com.foodrescue.app.feature.account.domain.*
import com.foodrescue.app.feature.account.presentation.*
import com.foodrescue.app.feature.auth.presentation.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class AdminUsersUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val note = "Đã đối chiếu phản ánh và xác nhận tài khoản cần tạm khóa để xử lý."
    private val user = ManagedAccount(AccountRecord("a".repeat(64), CustomerProfile("Nguyễn Minh An", "an@example.com")),
        "Nguyễn Minh An", "an@example.com", "0901234567", setOf(AccountRole.CUSTOMER, AccountRole.OWNER),
        listOf(AccountRelationship(AccountRole.OWNER, -10000, "Tiệm Bánh Mộc", "Đã duyệt")), 8, 2)
    private inner class Repository : AccountManagementRepository {
        var entry = user
        var fail = false
        var stale = false
        var readFail = false
        var writes = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun accounts(): List<ManagedAccount> { check(!readFail); return listOf(entry) }
        override suspend fun decide(key: String, decision: AccountDecision): ManagedAccount {
            gate?.await(); check(!fail)
            if (stale) throw StaleAccountDecision()
            entry = entry.copy(record = entry.record.changeStatus(decision, "admin@foodrescue.vn", 1795230000000L, false, false))
            writes++; return entry
        }
    }
    @Suppress("DEPRECATION")
    @Before fun window() { compose.activityRule.scenario.onActivity {
        it.enableEdgeToEdge(); it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } }
    private fun start(repo: Repository): AdminUsersViewModel {
        val vm = AdminUsersViewModel(SavedStateHandle(), repo)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) { FoodRescueTheme {
                val state = vm.state
                LaunchedEffect(vm) { vm.refresh() }
                when {
                    state.draft != null -> AdminUserDecisionScreen(state, vm::back, vm::reason, vm::review, vm::confirm, vm::dismiss)
                    state.selected != null -> AdminUserDetailScreen(state, vm::back, vm::prepare, vm::refresh)
                    else -> AdminShell(AdminTab.USERS, {}, {}) {
                        AdminUsersScreen(state, vm::search, vm::role, vm::status, vm::open, vm::refresh)
                    }
                }
            } }
        }
        compose.waitUntil(5000) { !vm.state.loading }
        return vm
    }
    private fun open() {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(user.name))
        compose.onNodeWithText(user.name).performClick()
        compose.onNodeWithText("Chi tiết tài khoản").assertIsDisplayed()
    }
    private fun prepare() { open(); compose.onNodeWithText("Khóa tài khoản").performClick() }
    private fun reason() {
        compose.onNodeWithText("Lý do thay đổi trạng thái").performScrollTo().performTextInput(note)
        compose.onNodeWithText("Lý do thay đổi trạng thái").assertIsFocused()
        shot("reason-keyboard"); closeSoftKeyboard()
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val i = InstrumentationRegistry.getInstrumentation()
        i.uiAutomation.waitForIdle(500, 5000)
        File(i.targetContext.getExternalFilesDir(null), "admin-users-$name.png").outputStream().use {
            i.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun searchAndCombinedFiltersExposeEmptyAndMatchingResults() {
        val vm = start(Repository()); shot("overview")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Tìm tài khoản"))
        compose.onNodeWithContentDescription("Tìm tài khoản").performTextInput("khong ton tai")
        closeSoftKeyboard()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Không tìm thấy tài khoản"))
        compose.onNodeWithText("Không tìm thấy tài khoản").assertIsDisplayed(); shot("empty")
        compose.runOnIdle { vm.search("nguyen minh an"); vm.role(AccountRole.OWNER); vm.status(AccountStatus.ACTIVE) }
        open(); shot("identity")
        compose.onNodeWithText("Liên kết cửa hàng").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tiệm Bánh Mộc").performScrollTo().assertIsDisplayed(); shot("relationships")
    }
    @Test fun accountTypesFilterSeparatelyAndDetailSeparatesStoreApproval() {
        val customer = user.copy(record = AccountRecord("b".repeat(64)), name = "Khách mua hộp",
            roles = setOf(AccountRole.CUSTOMER), relationships = emptyList())
        val owner = user.copy(relationships = user.relationships.map { it.copy(status = "Chờ xét duyệt") })
        val staff = user.copy(record = AccountRecord("c".repeat(64)), name = "Nhân viên cửa hàng",
            roles = setOf(AccountRole.CUSTOMER, AccountRole.STAFF),
            relationships = listOf(AccountRelationship(AccountRole.STAFF, -10000, "Tiệm Bánh Mộc", "Đã thu hồi quyền nhân viên")))
        var state by mutableStateOf(AdminUsersState(loading = false, accounts = listOf(customer, owner, staff)))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) { FoodRescueTheme {
                if (state.selected != null) AdminUserDetailScreen(state, { state = state.copy(selectedKey = null) }, {}, {})
                else AdminShell(AdminTab.USERS, {}, {}) {
                    AdminUsersScreen(state, { state = state.copy(query = it) }, { state = state.copy(role = it) },
                        { state = state.copy(status = it) }, { state = state.copy(selectedKey = it) }, {})
                }
            } }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Khách hàng · 1"))
        compose.onNodeWithText("Khách hàng · 1").performClick().assertIsSelected()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(customer.name))
        compose.onNodeWithText(customer.name).assertIsDisplayed()
        assertEquals(listOf(customer), state.visible); shot("types-customer")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Nhân viên · 1"))
        compose.onNodeWithText("Nhân viên · 1").performClick().assertIsSelected()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(staff.name))
        compose.onNodeWithText(staff.name).assertIsDisplayed()
        assertEquals(listOf(staff), state.visible); shot("types-staff")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Chủ cửa hàng · 1"))
        compose.onNodeWithText("Chủ cửa hàng · 1").performClick().assertIsSelected()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(owner.name))
        compose.onNodeWithText("Khách hàng · Chủ cửa hàng").assertDoesNotExist()
        compose.onNodeWithText(owner.name).performClick()
        compose.onNodeWithText("Loại tài khoản").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Liên kết cửa hàng").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Trạng thái cửa hàng").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Chờ xét duyệt").performScrollTo().assertIsDisplayed(); shot("types-store-approval")
        compose.onNodeWithText("Dữ liệu cần lưu ý").assertDoesNotExist()
    }
    @Test fun emptyCustomerGroupIsExplicitAndDoesNotContainOwnerAccounts() {
        var state by mutableStateOf(AdminUsersState(loading = false, accounts = listOf(user)))
        compose.setContent { FoodRescueTheme {
            AdminUsersScreen(state, {}, { state = state.copy(role = it) }, {}, {}, {})
        } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Khách hàng · 0"))
        compose.onNodeWithText("Khách hàng · 0").performClick().assertIsSelected()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Chưa có tài khoản thuộc nhóm này"))
        compose.onNodeWithText("Chưa có tài khoản thuộc nhóm này").assertIsDisplayed()
        compose.onNodeWithText(user.name).assertDoesNotExist()
        assertTrue(state.visible.isEmpty()); shot("types-empty-customer")
    }
    @Test fun requiredReasonConfirmationAndBusyGuardCreateExactlyOneAuditEvent() {
        val repo = Repository(); val vm = start(repo); prepare()
        compose.onNodeWithText("Xem lại thay đổi").performClick()
        compose.onNodeWithText("Nhập lý do cụ thể từ 10 đến 500 ký tự.").assertIsDisplayed()
        assertFalse(vm.state.confirming); reason()
        compose.onNodeWithText("Xem lại thay đổi").performClick(); shot("confirmation")
        val gate = CompletableDeferred<Unit>(); repo.gate = gate
        compose.onNodeWithText("Xác nhận lưu").performClick()
        compose.onNodeWithText("Xác nhận lưu").assertIsNotEnabled()
        compose.runOnIdle { vm.confirm(); gate.complete(Unit) }
        compose.waitUntil(5000) { !vm.state.busy }
        assertEquals(1, repo.writes); assertEquals(1, repo.entry.record.events.size)
        compose.onNodeWithText(note).performScrollTo().assertIsDisplayed(); shot("audit")
        compose.onNodeWithText("Mở khóa tài khoản").assertIsDisplayed()
    }
    @Test fun failedWriteRetainsReasonAndCanRetryWithoutDoubleWrite() {
        val repo = Repository(); val vm = start(repo); prepare(); reason(); repo.fail = true
        compose.onNodeWithText("Xem lại thay đổi").performClick(); compose.onNodeWithText("Xác nhận lưu").performClick()
        compose.waitUntil(5000) { !vm.state.busy }
        assertEquals(note, vm.state.draft!!.reason); assertEquals(0, repo.writes)
        compose.onNodeWithText("Chưa lưu được thay đổi.", substring = true).performScrollTo().assertIsDisplayed(); shot("save-error")
        repo.fail = false
        compose.onNodeWithText("Xem lại thay đổi").performClick(); compose.onNodeWithText("Xác nhận lưu").performClick()
        compose.waitUntil(5000) { !vm.state.busy }; assertEquals(1, repo.writes)
    }
    @Test fun staleDecisionRequiresRefreshBeforeAnotherAction() {
        val repo = Repository(); val vm = start(repo); prepare(); reason(); repo.stale = true
        compose.onNodeWithText("Xem lại thay đổi").performClick(); compose.onNodeWithText("Xác nhận lưu").performClick()
        compose.waitUntil(5000) { !vm.state.busy }
        assertNull(vm.state.draft); assertEquals(0, repo.writes)
        compose.onNodeWithText("Khóa tài khoản").assertIsNotEnabled()
        compose.onNodeWithText("Làm mới").performScrollTo().performClick()
        compose.waitUntil(5000) { !vm.state.busy }
        compose.onNodeWithText("Khóa tài khoản").assertIsEnabled()
    }
    @Test fun protectedAdminHasNoLockActionAndLegacyAccountHasNoInventedEmail() {
        val repo = Repository(); repo.entry = user.copy(roles = setOf(AccountRole.ADMIN), email = null)
        start(repo); open()
        compose.onNodeWithText("Khóa tài khoản").assertDoesNotExist()
        compose.onNodeWithText("Tài khoản được bảo vệ").assertExists()
        compose.onNodeWithText("Chưa ghi nhận email đăng nhập").performScrollTo().assertIsDisplayed(); shot("protected")
    }
    @Test fun readErrorCanBeRetriedAndClearCancelsPendingDecision() {
        val repo = Repository(); repo.readFail = true; val vm = start(repo)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Chưa tải được danh sách", substring = true))
        compose.onNodeWithText("Chưa tải được danh sách", substring = true).assertIsDisplayed()
        repo.readFail = false
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Làm mới"))
        compose.onNodeWithText("Làm mới").performClick()
        compose.waitUntil(5000) { !vm.state.busy }; prepare(); reason()
        val gate = CompletableDeferred<Unit>(); repo.gate = gate
        compose.onNodeWithText("Xem lại thay đổi").performClick(); compose.onNodeWithText("Xác nhận lưu").performClick()
        compose.runOnIdle { vm.clear(); gate.complete(Unit) }
        compose.waitForIdle(); assertEquals(0, repo.writes); assertTrue(vm.state.accounts.isEmpty())
    }
    private class SessionRepository : AccountSessionRepository {
        var record = AccountRecord("b".repeat(64), CustomerProfile("An", "an@example.com"))
        var fail = false
        override suspend fun register(name: String, email: String) { check(!fail) }
        override suspend fun signIn(name: String, email: String) = AccountLogin(record.profile!!, record.accessVersion)
        override suspend fun access(email: String): AccountRecord { check(!fail); return record }
        override suspend fun updateProfile(profile: CustomerProfile, accessVersion: Long): CustomerProfile { check(!fail); return profile }
    }
    @Test fun foregroundRecheckRemovesRoleSurfaceAndRequiresFreshLoginAfterUnlock() {
        val repo = SessionRepository(); val vm = AccountAccessViewModel(repo)
        compose.setContent { FoodRescueTheme { AccountAccessGate(vm, "an@example.com", 0, {}) { Text("Nội dung vai trò") } } }
        compose.waitUntil(5000) { vm.state.allowed }; compose.onNodeWithText("Nội dung vai trò").assertIsDisplayed()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        repo.record = repo.record.changeStatus(AccountDecision("lock", 0, AccountStatus.SUSPENDED, note), "admin", 1000, false, false)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(5000) { vm.state.reason != null }
        compose.onNodeWithText("Nội dung vai trò").assertDoesNotExist(); shot("blocked-access")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        repo.record = repo.record.changeStatus(AccountDecision("unlock", 1, AccountStatus.ACTIVE, note), "admin", 2000, false, false)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(5000) { vm.state.expired }
        compose.onNodeWithText("Vui lòng đăng nhập lại").assertIsDisplayed(); shot("expired-session")
    }
    @Test fun profileEditorWaitsForPersistenceAndShowsFailureWithoutLeaving() {
        var left = false; val result = CompletableDeferred<Boolean>()
        compose.setContent { FoodRescueTheme { EditCustomerProfileScreen(user.record.profile!!, { left = true }) { _, _ -> result.await() } } }
        compose.onNodeWithText("Lưu thay đổi").performClick()
        compose.onNodeWithText("Đang lưu…").assertIsNotEnabled(); assertFalse(left)
        compose.runOnIdle { result.complete(false) }
        compose.onNodeWithText("Chưa lưu được thông tin. Vui lòng thử lại.").assertExists(); assertFalse(left)
        shot("profile-error")
    }
    @Test fun registrationSuccessWaitsForProfileCommit() {
        val auth = AuthViewModel(); val committed = CompletableDeferred<Unit>()
        compose.runOnIdle {
            auth.navigate(AuthPage.REGISTER)
            auth.edit(AuthField.NAME, "Nguyễn An"); auth.edit(AuthField.EMAIL, "an@example.com")
            auth.edit(AuthField.PASSWORD, "FoodRescue123"); auth.edit(AuthField.CONFIRMATION, "FoodRescue123")
            auth.submit(); auth.edit(AuthField.OTP, auth.uiState.otp!!.sampleCode); auth.submit()
        }
        var saved = false
        compose.setContent { FoodRescueTheme { AuthRoute(viewModel = auth,
            onRegistered = { _, _ -> committed.await(); saved = true }) } }
        compose.onNodeWithText("Đang lưu thông tin tài khoản…").assertIsDisplayed(); assertFalse(saved)
        compose.runOnIdle { committed.complete(Unit) }
        compose.waitUntil(5000) { saved }
        compose.onNodeWithText("Đang lưu thông tin tài khoản…").assertDoesNotExist()
    }
}
