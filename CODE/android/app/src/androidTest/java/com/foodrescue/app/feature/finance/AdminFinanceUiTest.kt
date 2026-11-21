package com.foodrescue.app.feature.finance

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
import com.foodrescue.app.feature.finance.domain.*
import com.foodrescue.app.feature.finance.presentation.*
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.store.domain.StorePayoutAccount
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class AdminFinanceUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = 1_800_000_000_000L
    private val reason = "Đã kiểm tra tiền và thông tin tài khoản nhận."
    private val bank = StorePayoutAccount("Vietcombank", "NGUYEN AN", "0123456789")
    private val order = BoxReservation("one", 1, 1, PickupWindow(now - 1000, now + 1000), now - 4000,
        now - 2000, 100000, QrPayment("FRONE", 100000, PaymentStatus.SUCCEEDED, now - 3000), collectedAtMillis = now - 500)
    private fun pending() = ManagedSettlement("Tiệm Bánh An Nhiên", buildRevenueSnapshot(7, listOf(order), emptySet(), emptyList(), bank, 2000, now).settlements.single(), bank)
    private inner class Repository : AdminSettlementRepository {
        var item = pending()
        var writes = 0
        override suspend fun load() = AdminFinanceSnapshot(listOf(item), listOf(), now)
        override suspend fun decide(expected: ManagedSettlement, decision: SettlementDecision): ManagedSettlement {
            check(expected == item)
            item = item.copy(record = item.decide(decision, "admin@foodrescue.vn", now, "DS-000001")); writes++
            return item
        }
    }
    @Suppress("DEPRECATION")
    @Before fun window() { compose.activityRule.scenario.onActivity {
        it.enableEdgeToEdge(); it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } }
    private fun content(block: @Composable () -> Unit) { compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) { FoodRescueTheme { block() } }
    } }
    private fun shot(name: String) {
        compose.waitForIdle()
        val i = InstrumentationRegistry.getInstrumentation()
        i.uiAutomation.waitForIdle(500, 5000)
        File(i.targetContext.getExternalFilesDir(null), "admin-finance-$name.png").outputStream().use {
            i.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun settlementListSearchAndFilterHaveExplicitEmptyStates() {
        val item = pending()
        var state by mutableStateOf(AdminFinanceState(snapshot = AdminFinanceSnapshot(listOf(item), loadedAt = now)))
        content { AdminShell(AdminTab.FINANCE, {}, {}) { AdminSettlementsScreen(state,
            { state = state.copy(query = it) }, { state = state.copy(filter = it) }, {}, {}) } }
        compose.onNodeWithText("Đối soát & chi trả").assertIsDisplayed(); shot("overview")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Tiệm Bánh An Nhiên"))
        compose.onNodeWithText("Tiệm Bánh An Nhiên").assertIsDisplayed(); shot("periods")
        compose.onNodeWithText("Tìm cửa hàng, mã kỳ, mã chuyển tiền").performScrollTo().performTextInput("khongtimthay")
        closeSoftKeyboard()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Chưa có kỳ đối soát phù hợp"))
        compose.onNodeWithText("Chưa có kỳ đối soát phù hợp").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(query = "", filter = SettlementStatus.PAID) }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Chưa có kỳ đối soát phù hợp"))
        shot("empty")
    }
    @Test fun confirmationAndPayoutRequireReasonReferenceAndPersistThroughNavigation() {
        val repository = Repository()
        val vm = AdminFinanceViewModel(SavedStateHandle(), repository)
        content {
            val state = vm.state
            LaunchedEffect(vm) { vm.refresh() }
            when {
                state.decision != null -> AdminSettlementDecisionScreen(state, vm::back, vm::reason, vm::reference, vm::review, vm::confirm, vm::dismiss)
                state.selected != null -> AdminSettlementDetailScreen(state, vm::back, vm::prepare, vm::refresh)
                else -> AdminSettlementsScreen(state, vm::search, vm::filter, vm::open, vm::refresh)
            }
        }
        compose.waitUntil(5000) { vm.state.snapshot != null && !vm.state.busy }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Tiệm Bánh An Nhiên"))
        compose.onNodeWithText("Tiệm Bánh An Nhiên").performClick()
        compose.onNodeWithText("Chi tiết đối soát").assertIsDisplayed(); shot("detail")
        fun action(label: String) {
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.onNodeWithText("Kiểm tra và xác nhận").performClick()
            compose.runOnIdle { assertFalse(vm.state.confirming) }
            compose.onNodeWithText("Nội dung xác nhận").performScrollTo().performTextInput(reason)
            closeSoftKeyboard()
        }
        fun confirm() {
            compose.onNodeWithText("Kiểm tra và xác nhận").performClick()
            compose.onNodeWithText("Xác nhận").performClick()
            compose.waitUntil(5000) { vm.state.decision == null && !vm.state.busy }
        }
        action("Xác nhận đối soát"); shot("decision"); confirm()
        action("Bắt đầu chi trả"); confirm()
        action("Ghi nhận đã chi trả")
        compose.onNodeWithText("Kiểm tra và xác nhận").performClick()
        compose.runOnIdle { assertFalse(vm.state.confirming) }
        compose.onNodeWithText("Mã giao dịch chuyển tiền").performScrollTo().performTextInput("BANK-000001")
        shot("reference-keyboard"); closeSoftKeyboard(); confirm()
        compose.runOnIdle { assertEquals(3, repository.writes); assertEquals(SettlementStatus.PAID, vm.state.selected!!.record.status) }
        compose.onNodeWithText("BANK-000001").performScrollTo().assertIsDisplayed(); shot("paid")
        compose.onNodeWithText("Lịch sử xử lý").performScrollTo().assertIsDisplayed(); shot("audit")
    }
    @Test fun policyEditorValidatesAndShowsConfirmationBeforePublishing() {
        var publications = 0
        val repository = object : FinancialPolicyRepository {
            var book = FinancialPolicyBook()
            override suspend fun load() = book
            override suspend fun publish(expectedRevision: Long, terms: FinancialTerms, effectiveAt: Long, reason: String): FinancialPolicyBook {
                book = book.publish(expectedRevision, terms, if (effectiveAt == 0L) now else effectiveAt, "admin", reason, now)
                publications++; return book
            }
        }
        val vm = FinancialPolicyViewModel(repository) { now }
        content {
            LaunchedEffect(vm) { vm.refresh() }
            if (vm.state.draft == null) AdminShell(AdminTab.FINANCE, {}, {}) { FinancialPolicyScreen(vm.state, vm::edit, vm::refresh) }
            else FinancialPolicyEditorScreen(vm.state, vm::back, vm::change, vm::review, vm::confirm, vm::dismiss)
        }
        compose.waitUntil(5000) { vm.state.book != null && !vm.state.busy }
        shot("policy")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Điều chỉnh chính sách"))
        compose.onNodeWithText("Điều chỉnh chính sách").performClick()
        compose.onNodeWithText("Xem lại chính sách").performClick()
        compose.runOnIdle { assertFalse(vm.state.confirming) }
        compose.onNodeWithText("Tỷ lệ hoa hồng (%)").performScrollTo().performTextReplacement("15,25")
        compose.onNodeWithText("Tỷ lệ hoa hồng (%)").assertIsFocused()
        shot("policy-keyboard")
        val field = compose.onNodeWithText("Tỷ lệ hoa hồng (%)").fetchSemanticsNode().boundsInRoot
        val footer = compose.onNodeWithText("Xem lại chính sách").fetchSemanticsNode().boundsInRoot
        assertTrue("The entire commission field must stay above the footer while typing", field.bottom <= footer.top)
        closeSoftKeyboard()
        compose.onNodeWithText("Lý do điều chỉnh").performScrollTo().performTextInput(reason)
        closeSoftKeyboard()
        compose.onNodeWithText("Xem lại chính sách").performClick()
        compose.onNodeWithText("Công bố chính sách mới?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, publications) }; shot("policy-confirm")
        compose.onNodeWithText("Công bố").performClick()
        compose.waitUntil(5000) { vm.state.draft == null && !vm.state.busy }
        compose.onNodeWithText("15,25%").assertIsDisplayed(); shot("policy-published")
        compose.runOnIdle { assertEquals(1, publications); assertEquals(1L, vm.state.book!!.revision) }
    }
    @Test fun aScheduledPolicyDisplaysBothCurrentAndUpcomingRates() {
        val book = FinancialPolicyBook().publish(0, FinancialTerms(commissionBps = 1500), now + 86400000, "admin", reason, now)
        content { AdminShell(AdminTab.FINANCE, {}, {}) { FinancialPolicyScreen(FinancialPolicyState(book, now), {}, {}) } }
        compose.onNodeWithText("20%").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Phiên bản sắp có hiệu lực"))
        compose.onNodeWithText("Phiên bản sắp có hiệu lực").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Hoa hồng 15% · Phiên bản 1"))
        compose.onNodeWithText("Hoa hồng 15% · Phiên bản 1").assertIsDisplayed(); shot("scheduled")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Điều chỉnh chính sách"))
        compose.onNodeWithText("Điều chỉnh chính sách").assertIsNotEnabled()
    }
    @Test fun blockedSettlementOnlyOffersVoidAndNeverStartTransfer() {
        val ready = pending().let { it.copy(record = it.decide(SettlementDecision(SettlementAction.CONFIRM, reason), "admin", now, "DS-ONE"),
            issues = setOf(SettlementIssue.ORDER_CHANGED)) }
        content { AdminSettlementDetailScreen(AdminFinanceState(AdminFinanceSnapshot(listOf(ready)), selectedKey = ready.key), {}, {}, {}) }
        compose.onNodeWithText("Bắt đầu chi trả").assertDoesNotExist()
        compose.onNodeWithText("Hủy kỳ đối soát").performScrollTo().assertIsDisplayed(); shot("blocked")
    }
}
