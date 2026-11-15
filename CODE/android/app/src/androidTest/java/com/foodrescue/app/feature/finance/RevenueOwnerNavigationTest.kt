package com.foodrescue.app.feature.finance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import com.foodrescue.app.core.common.GeoPoint
import com.foodrescue.app.core.common.LocationSource
import com.foodrescue.app.core.designsystem.theme.FoodRescueTheme
import com.foodrescue.app.dashboard.owner.OwnerRoute
import com.foodrescue.app.feature.order.domain.BoxReservation
import com.foodrescue.app.feature.order.domain.PickupWindow
import com.foodrescue.app.feature.payment.domain.PaymentStatus
import com.foodrescue.app.feature.payment.domain.QrPayment
import com.foodrescue.app.feature.finance.domain.*
import com.foodrescue.app.feature.finance.presentation.*
import com.foodrescue.app.feature.store.data.LocalPayoutBanks
import com.foodrescue.app.feature.store.domain.*
import com.foodrescue.app.feature.store.presentation.StoreManagementViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercises owner-shell return routing; all store writes stay in this in-memory fixture. */
class RevenueOwnerNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val now = 1_800_000_000_000L
    private val bank = StorePayoutAccount("Vietcombank", "NGUYEN AN", "0123456789")
    private val repository = MemoryStore(OwnerStore(
        OwnerStoreProfile(name = "Tiệm Bánh An Nhiên"), StoreReviewStatus.APPROVED, payout = bank))
    private val store = StoreManagementViewModel(SavedStateHandle(), repository, LocalPayoutBanks.all)
    private val order = BoxReservation("one", 1, 1, PickupWindow(now - 1000, now + 1000), now - 3000, now - 2000,
        49000, QrPayment("FRONE", 49000, PaymentStatus.SUCCEEDED, now - 2500), collectedAtMillis = now - 500)
    private val revenue = RevenueViewModel(SavedStateHandle(), RevenueRepository {
        buildRevenueSnapshot(7, listOf(order), emptySet(), emptyList(), repository.value.payout, 2000, now)
    }) { now }
    private val location = object : LocationSource {
        override fun isEnabled() = false
        override suspend fun currentPoint(): GeoPoint? = null
    }
    private class MemoryStore(var value: OwnerStore) : OwnerStoreRepository {
        override suspend fun load() = value
        override suspend fun saveDraft(profile: OwnerStoreProfile): OwnerStore = error("Unexpected draft write")
        override suspend fun submit(profile: OwnerStoreProfile): OwnerStore = error("Unexpected application")
        override suspend fun updateProfile(profile: OwnerStoreProfile): OwnerStore = error("Unexpected profile write")
        override suspend fun updatePayout(payout: StorePayoutAccount): OwnerStore {
            value = value.updatePayout(payout, LocalPayoutBanks.all)
            return value
        }
    }
    private fun launch() {
        runBlocking { revenue.refresh() }
        compose.setContent { FoodRescueTheme {
            OwnerRoute("owner@example.com", store, {}, {}, location,
                revenueContent = { name, payout -> RevenueRoute(name, revenue, LocalPayoutBanks.all, payout) },
                revenueFullscreen = revenue.state.page != RevenuePage.OVERVIEW)
        } }
        compose.waitUntil(5000) { !store.state.loading }
        compose.onNode(hasText("Doanh thu") and isSelectable()).performClick()
    }
    private fun openBank() {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Quản lý tài khoản nhận tiền"))
        compose.onNodeWithText("Quản lý tài khoản nhận tiền").performClick()
        compose.onNodeWithText("Tài khoản nhận tiền").assertIsDisplayed()
    }
    private fun saveNumber() {
        compose.onNodeWithContentDescription("Số tài khoản").performScrollTo().performTextReplacement("0011223344")
        compose.onNodeWithText("Lưu tài khoản").performClick()
        compose.onNodeWithText("Xác nhận lưu").performClick()
        compose.waitUntil(5000) { repository.value.payout?.number == "0011223344" }
    }

    @Test fun bankBackReturnsToRevenueWithSelectedPeriod() {
        launch()
        compose.runOnIdle { revenue.period(RevenuePeriod.MONTH); revenue.movePeriod(-1) }
        openBank()
        compose.onNodeWithContentDescription("Quay lại").performClick()
        compose.onNode(hasText("Doanh thu") and isSelectable()).assertIsSelected()
        compose.runOnIdle {
            assertEquals(RevenuePeriod.MONTH, revenue.state.period)
            assertEquals(-1, revenue.state.offset)
            assertEquals(bank, repository.value.payout)
        }
    }

    @Test fun savedBankReturnsToRevenueAndRefreshesDisplayedAccount() {
        launch(); openBank(); saveNumber()
        compose.onNode(hasText("Doanh thu") and isSelectable()).assertIsSelected()
        compose.waitUntil(5000) { revenue.state.snapshot?.payout?.number == "0011223344" }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("•••• 3344"))
        compose.onNodeWithText("•••• 3344").assertIsDisplayed()
        compose.onNodeWithText("•••• 6789").assertDoesNotExist()
    }

    @Test fun savingBankFromPendingSettlementReturnsToTheSameDetail() {
        launch()
        compose.runOnIdle { revenue.openSettlement(revenue.state.snapshot!!.settlements.single().id) }
        compose.onNodeWithText("Kiểm tra tài khoản nhận tiền").performScrollTo().performClick()
        saveNumber()
        compose.onNodeWithText("Chi tiết đối soát").assertIsDisplayed()
        compose.onNodeWithText("Chờ đối soát").assertExists()
        compose.runOnIdle { assertEquals(RevenuePage.SETTLEMENT, revenue.state.page) }
        compose.onNodeWithContentDescription("Quay lại").performClick()
        compose.onNodeWithText("Đối soát & chi trả").assertIsDisplayed()
    }
}
