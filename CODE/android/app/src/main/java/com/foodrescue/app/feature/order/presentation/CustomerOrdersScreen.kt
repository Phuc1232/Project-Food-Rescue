package com.foodrescue.app.feature.order.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.FoodRescueEmptyState

@Composable
internal fun CustomerOrdersScreen(onExplore: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
        Text("Đơn hàng", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        FoodRescueEmptyState("Bạn chưa có đơn hàng", "Khám phá những hộp bất ngờ từ các cửa hàng quanh bạn.", "Khám phá hộp", onExplore)
        Spacer(Modifier.weight(1f))
    }
}
