package com.foodrescue.app.feature.store.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.FoodRescueGlyph
import com.foodrescue.app.core.designsystem.component.FoodRescueIcon
import com.foodrescue.app.feature.store.domain.StoreReviewStatus

@Composable
internal fun StoreSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
}

@Composable
internal fun StoreIconTile(icon: FoodRescueIcon, modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary, container: Color = MaterialTheme.colorScheme.primaryContainer) {
    Surface(modifier, color = container, shape = RoundedCornerShape(14.dp)) {
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) { FoodRescueGlyph(icon, tint = color) }
    }
}

@Composable
internal fun StoreMenuRow(title: String, description: String, icon: FoodRescueIcon, onClick: () -> Unit,
    modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            StoreIconTile(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FoodRescueGlyph(FoodRescueIcon.ARROW, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
internal fun StoreValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "Chưa cập nhật" }, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun StoreStatusBadge(status: StoreReviewStatus, modifier: Modifier = Modifier) {
    val text = when (status) {
        StoreReviewStatus.DRAFT -> "Bản nháp"
        StoreReviewStatus.PENDING -> "Chờ xét duyệt"
        StoreReviewStatus.APPROVED -> "Đã duyệt"
        StoreReviewStatus.CHANGES_REQUIRED -> "Cần bổ sung"
    }
    val approved = status == StoreReviewStatus.APPROVED
    Surface(modifier, color = if (approved) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(8.dp)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium,
            color = if (approved) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

@Composable
internal fun StoreEmptyPanel(icon: FoodRescueIcon, title: String, description: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StoreIconTile(icon)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun StoreErrorMessage(message: String?, modifier: Modifier = Modifier) {
    message?.let {
        Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
            Text(it, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
