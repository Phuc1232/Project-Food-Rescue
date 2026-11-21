package com.foodrescue.app.feature.store

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
import com.foodrescue.app.dashboard.admin.AdminShell
import com.foodrescue.app.dashboard.admin.AdminTab
import com.foodrescue.app.feature.menu.domain.*
import com.foodrescue.app.feature.store.domain.*
import com.foodrescue.app.feature.store.presentation.*
import com.foodrescue.app.feature.store.presentation.component.OwnerGovernanceNotice
import com.foodrescue.app.feature.surprisebox.domain.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class StoreSupervisionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = 1795230000000L
    private val note = "Đối chiếu cho thấy giá trị hộp chưa đúng cam kết. Cần cập nhật và kiểm tra trước khi mở bán lại."
    private val store = OwnerStore(OwnerStoreProfile("Tiệm Bánh Mộc", "Tiệm bánh", "Nguyễn An", "0901234567", "47 Nguyễn Thượng Hiền", "Bàn Cờ"), StoreReviewStatus.APPROVED)
    private val template = BoxTemplate("template", "Hộp bánh cuối ngày", BoxCategory.BAKERY, "Bánh tươi", "Sữa, trứng", 90_000, 30_000)
    private val batch = SaleBatch("batch-12345678", template, 20, now - 100_000, now + 100_000, now + 200_000, now + 300_000, BatchControl.PUBLISHED, held = 2, sold = 8)
    private val item = MenuItem("bread", "Bánh croissant", MenuCategory.BAKERY, MenuUnit.PIECE, 50_000, "Bánh bơ nướng trong ngày", MenuStatus.ACTIVE, null, now - 20_000, now - 10_000)
    private val box = PackedBox("physical-123456789", -10000, batch.id, template.name, 90_000, template.allergens,
        listOf(PackedItem(item.id, item.name, item.unit, 45_000, "2", 90_000)), now + 400_000, BoxStorage.AMBIENT, "staff", "Trần Minh Anh", now - 5000)
    private inner class Repository : StoreSupervisionRepository {
        var entry = SupervisedStore("account", -10000, store)
        var fail = false
        var stale = false
        var writes = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun stores() = listOf(entry)
        override suspend fun detail(accountKey: String) = StoreSupervisionDetail(entry, StoreMenu(listOf(item)),
            OwnerBoxCatalog(listOf(template), listOf(batch)), listOf(box), entry.store.governance.events.map {
                StoreActivity(it.id, it.atMillis, it.action.label, it.note)
            }, 8, 2, now)
        override suspend fun decide(accountKey: String, draft: StoreRestrictionDraft): SupervisedStore {
            gate?.await()
            if (stale) throw StaleStoreSupervision()
            check(!fail)
            entry = entry.copy(store = entry.store.restrict(draft, "admin@foodrescue.vn", now).copy(revision = entry.store.revision + 1))
            writes++
            return entry
        }
    }
    @Suppress("DEPRECATION")
    @Before fun window() { compose.activityRule.scenario.onActivity {
        it.enableEdgeToEdge(); it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } }
    private fun start(repo: Repository, large: Boolean = true): StoreSupervisionViewModel {
        val vm = StoreSupervisionViewModel(SavedStateHandle(), repo)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.5f else density.fontScale)) {
                FoodRescueTheme {
                    val state = vm.state
                    LaunchedEffect(vm) { vm.refresh() }
                    val detail = state.detail
                    val physical = detail?.boxes?.find { it.id == state.boxId }
                    val menuItem = detail?.menu?.items?.find { it.id == state.menuId }
                    when {
                        state.draft != null -> StoreRestrictionDecisionScreen(state, vm::back, vm::note, vm::review, vm::confirm, vm::dismiss)
                        physical != null -> SupervisedPackedBoxScreen(physical, detail, vm::back)
                        menuItem != null -> SupervisedMenuItemScreen(menuItem, vm::back)
                        state.selectedKey != null -> StoreSupervisionDetailScreen(state, vm::back, vm::section, vm::prepare, vm::openBox, vm::openMenu, vm::refresh)
                        else -> AdminShell(AdminTab.STORES, {}, {}) { StoreSupervisionScreen(state, vm::search, vm::filter, vm::open, vm::refresh) }
                    }
                }
            }
        }
        compose.waitUntil(5000) { !vm.state.loading }
        return vm
    }
    private fun open() {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(store.profile.name))
        compose.onNodeWithText(store.profile.name).performClick()
        compose.onNodeWithText("Đơn đang cần phục vụ").assertExists()
    }
    private fun suspendStore() {
        open()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Tạm ngừng bán cửa hàng"))
        compose.onNodeWithText("Tạm ngừng bán cửa hàng").performClick()
    }
    private fun enterNote() {
        compose.onNodeWithText("Lý do và yêu cầu xử lý").performScrollTo().performTextInput(note)
        closeSoftKeyboard()
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val i = InstrumentationRegistry.getInstrumentation()
        i.uiAutomation.waitForIdle(500, 5000)
        File(i.targetContext.getExternalFilesDir(null), "admin-supervision-$name.png").outputStream().use {
            i.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun queueSearchAndFilterShowRelevantEmptyState() {
        start(Repository()); shot("queue")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Tìm cửa hàng"))
        compose.onNodeWithContentDescription("Tìm cửa hàng").performTextInput("khongco")
        closeSoftKeyboard()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Chưa có cửa hàng phù hợp"))
        compose.onNodeWithText("Chưa có cửa hàng phù hợp").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Xóa tìm kiếm"))
        compose.onNodeWithContentDescription("Xóa tìm kiếm").performClick()
        compose.onNode(hasText("Có hạn chế") and hasClickAction()).performScrollTo().performClick()
    }
    @Test fun menuAndPackedEvidenceKeepOriginalPricesAndReturnToSameSection() {
        start(Repository()); open(); shot("overview")
        compose.onNodeWithText("Menu").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(item.name))
        compose.onNodeWithText(item.name).performClick()
        compose.onNodeWithText("Đối chiếu menu").assertIsDisplayed(); shot("menu-item")
        compose.onNodeWithContentDescription("Quay lại").performClick()
        compose.onNodeWithText("Hộp thực tế").performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(box.code))
        compose.onNodeWithText(box.code).performClick()
        compose.onNodeWithText("Giá trị khi đóng gói").assertIsDisplayed(); shot("packed-box")
        compose.onNodeWithText("Đơn giá lúc đóng gói").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("45.000 đ").assertExists()
        compose.onNodeWithText("Menu hiện tại:", substring = true).performScrollTo().assertIsDisplayed(); shot("price-comparison")
    }
    @Test fun largeTextKeyboardReasonConfirmationAndDuplicateClick() {
        val repo = Repository(); val vm = start(repo); suspendStore()
        compose.onNodeWithText("Xem lại quyết định").performClick()
        compose.onNodeWithText("Nhập lý do cụ thể từ 10 đến 500 ký tự.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Lý do và yêu cầu xử lý").performScrollTo().performTextInput(note)
        compose.onNodeWithText("Lý do và yêu cầu xử lý").assertIsFocused(); shot("keyboard")
        closeSoftKeyboard(); compose.onNodeWithText("Xem lại quyết định").performClick(); shot("confirmation")
        repo.gate = CompletableDeferred()
        compose.onNodeWithText("Xác nhận lưu").performClick()
        compose.runOnIdle { vm.confirm() }
        compose.onNodeWithText("Xác nhận lưu").assertIsNotEnabled()
        compose.runOnIdle { repo.gate!!.complete(Unit) }
        compose.waitUntil(5000) { !vm.state.busy }
        assertEquals(1, repo.writes); assertNotNull(vm.state.detail!!.entry.store.governance.suspension)
        compose.onNodeWithText("Nhật ký").performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(note))
        compose.onNodeWithText(note).assertIsDisplayed(); shot("history")
    }
    @Test fun failedWriteKeepsDraftAndRetryCreatesOneDecision() {
        val repo = Repository(); val vm = start(repo); suspendStore(); enterNote()
        repo.fail = true
        compose.onNodeWithText("Xem lại quyết định").performClick(); compose.onNodeWithText("Xác nhận lưu").performClick()
        compose.waitUntil(5000) { !vm.state.busy }
        assertEquals(note, vm.state.draft!!.note); assertEquals(0, repo.writes)
        compose.onNodeWithText("Chưa lưu được quyết định.", substring = true).performScrollTo().assertIsDisplayed(); shot("save-error")
        repo.fail = false
        compose.onNodeWithText("Xem lại quyết định").performClick(); compose.onNodeWithText("Xác nhận lưu").performClick()
        compose.waitUntil(5000) { !vm.state.busy }; assertEquals(1, repo.writes)
    }
    @Test fun staleDecisionCannotBeRetriedWithoutRefresh() {
        val repo = Repository(); val vm = start(repo); suspendStore(); enterNote(); repo.stale = true
        compose.onNodeWithText("Xem lại quyết định").performClick(); compose.onNodeWithText("Xác nhận lưu").performClick()
        compose.waitUntil(5000) { !vm.state.busy }
        assertNull(vm.state.draft); assertEquals(0, repo.writes)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Tạm ngừng bán cửa hàng"))
        compose.onNodeWithText("Tạm ngừng bán cửa hàng").assertIsNotEnabled()
        compose.onNodeWithText("Làm mới").performClick()
        compose.waitUntil(5000) { !vm.state.busy }
        compose.onNodeWithText("Tạm ngừng bán cửa hàng").assertIsEnabled()
    }
    @Test fun batchActionHasExplicitScopeAndPreservesStorePermission() {
        val repo = Repository(); val vm = start(repo); open()
        compose.onNodeWithText("Đợt bán").performScrollTo().performClick(); shot("batches")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Ngừng bán đợt này"))
        compose.onNodeWithText("Ngừng bán đợt này").performClick(); enterNote()
        compose.onNodeWithText("Xem lại quyết định").performClick(); compose.onNodeWithText("Xác nhận lưu").performClick()
        compose.waitUntil(5000) { !vm.state.busy }
        assertNull(repo.entry.store.governance.suspension)
        assertEquals(setOf(batch.id), repo.entry.store.governance.blockedBatches.keys)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Gỡ hạn chế đợt bán"))
        compose.onNodeWithText("Gỡ hạn chế đợt bán").assertIsEnabled(); shot("blocked-batch")
    }
    @Test fun ownerSeesReasonAndExistingOrderObligation() {
        val restricted = store.restrict(StoreRestrictionDraft("1", 0, StoreRestrictionAction.SUSPEND_STORE, note = note), "admin", now)
        compose.setContent { FoodRescueTheme { com.foodrescue.app.core.designsystem.component.FoodRescuePage("Tổng quan", {}) {
            OwnerGovernanceNotice(restricted.governance)
        } } }
        compose.onNodeWithText(note).assertExists()
        compose.onNodeWithText("Hãy khắc phục", substring = true).performScrollTo().assertIsDisplayed(); shot("owner-notice")
    }
    @Test fun clearRemovesAccountSelectionAndPendingDecision() {
        val vm = start(Repository()); suspendStore(); enterNote()
        compose.runOnIdle { vm.clear() }
        assertNull(vm.state.draft); assertNull(vm.state.selectedKey); assertNull(vm.state.detail)
        assertEquals("", vm.state.query); assertTrue(vm.state.stores.isEmpty())
    }
}
