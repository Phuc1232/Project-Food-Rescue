package com.foodrescue.app.feature.finance.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.foodrescue.app.core.designsystem.component.FoodRescueEmptyState
import com.foodrescue.app.core.designsystem.component.FoodRescuePage
import com.foodrescue.app.feature.store.domain.PayoutBank
import kotlinx.coroutines.delay

@Composable
internal fun RevenueRoute(storeName: String, vm: RevenueViewModel, banks: List<PayoutBank>, onPayout: () -> Unit,
    modifier: Modifier = Modifier) {
    val state = vm.state
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(vm, lifecycle) { lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (true) { vm.refresh(); delay(5_000) }
    } }
    BackHandler(state.page != RevenuePage.OVERVIEW, onBack = vm::back)
    when (state.page) {
        RevenuePage.OVERVIEW -> RevenueOverviewScreen(storeName, state, banks, vm::period, vm::movePeriod, vm::currentPeriod,
            vm::transactions, vm::settlements, onPayout, vm::reload, modifier)
        RevenuePage.TRANSACTIONS -> RevenueTransactionsScreen(state, vm::back, vm::period, vm::movePeriod, vm::currentPeriod,
            vm::basis, vm::search, vm::filter, vm::resetFilters, vm::openEntry, vm::reload, modifier)
        RevenuePage.SETTLEMENTS -> RevenueSettlementsScreen(state, vm::back, vm::settlementFilter, vm::openSettlement, vm::reload, modifier)
        RevenuePage.TRANSACTION -> state.selectedEntry?.let { RevenueTransactionScreen(it, vm::back, vm::openSettlement, modifier) }
            ?: RevenueMissingScreen(state, vm::back, vm::reload, modifier)
        RevenuePage.SETTLEMENT -> state.selectedSettlement?.let {
            RevenueSettlementScreen(it, state.snapshot?.payout, banks, vm::back, vm::openEntry, onPayout, modifier)
        } ?: RevenueMissingScreen(state, vm::back, vm::reload, modifier)
    }
}

@Composable
private fun RevenueMissingScreen(state: RevenueUiState, onBack: () -> Unit, onRetry: () -> Unit, modifier: Modifier) {
    FoodRescuePage("Chi tiết doanh thu", onBack, modifier) {
        if (state.loading) CircularProgressIndicator()
        else FoodRescueEmptyState("Chưa có thông tin chi tiết", state.error ?: "Dữ liệu đã thay đổi. Quay lại danh sách để xem bản cập nhật.", "Tải lại", onRetry)
    }
}
