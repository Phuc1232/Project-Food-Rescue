package com.foodrescue.app.feature.finance.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.finance.domain.*
import com.foodrescue.app.feature.store.presentation.component.*

@Composable
internal fun FinanceError(message: String, onRefresh: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    FoodRescueCard(modifier) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onRefresh, enabled = enabled) { Text("Tải lại") }
    }
}
