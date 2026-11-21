package com.foodrescue.app.feature.menu.presentation.component

import androidx.core.net.toUri
import android.widget.ImageView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.menu.domain.*
import com.foodrescue.app.feature.menu.presentation.label

internal val MenuCategory.label: String get() = when (this) {
    MenuCategory.BAKERY -> "Bánh"; MenuCategory.MEAL -> "Món chính"; MenuCategory.DRINK -> "Đồ uống"
    MenuCategory.SNACK -> "Ăn nhẹ"; MenuCategory.GROCERY -> "Thực phẩm"; MenuCategory.OTHER -> "Khác"
}

internal val MenuUnit.label: String get() = when (this) {
    MenuUnit.PORTION -> "phần"; MenuUnit.PIECE -> "cái"; MenuUnit.CUP -> "ly"; MenuUnit.BOTTLE -> "chai"
    MenuUnit.BOX -> "hộp"; MenuUnit.PACK -> "gói"; MenuUnit.KILOGRAM -> "kg"
}

internal val MenuForest = Color(0xFF133E32)
internal val MenuMint = Color(0xFFD8EDB4)

@Composable
internal fun MenuPhoto(asset: MenuAsset?, modifier: Modifier = Modifier, fit: Boolean = false) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        if (asset?.previewUri?.isNotBlank() == true) {
            AndroidView(factory = { ImageView(it) }, update = {
                it.scaleType = if (fit) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
                it.contentDescription = asset.name
                if (it.tag != asset.previewUri) { it.setImageURI(asset.previewUri.toUri()); it.tag = asset.previewUri }
            }, modifier = Modifier.fillMaxSize())
        } else FoodRescueGlyph(FoodRescueIcon.RECEIPT, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun MenuStatusBadge(status: MenuStatus, modifier: Modifier = Modifier) {
    val active = status == MenuStatus.ACTIVE
    Surface(modifier, shape = RoundedCornerShape(8.dp), color = if (active) Color(0xFFE3F1E6) else MaterialTheme.colorScheme.surfaceVariant) {
        Text(status.label, Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = if (active) MenuForest else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun MenuItemCard(item: MenuItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                MenuPhoto(item.photo, Modifier.size(76.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.category.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                }
                FoodRescueGlyph(FoodRescueIcon.CHEVRON, modifier = Modifier.size(18.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
            Text("${money(item.price)} / ${item.unit.label}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            MenuStatusBadge(item.status)
        }
    }
}

@Composable
internal fun <T> MenuChoice(label: String, value: T, options: List<T>, optionLabel: (T) -> String,
    onChoose: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(14.dp)) {
                Text(optionLabel(value), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                FoodRescueGlyph(FoodRescueIcon.CHEVRON, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded && enabled, { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                options.forEach { option -> DropdownMenuItem(text = { Text(optionLabel(option)) },
                    onClick = { expanded = false; onChoose(option) }, trailingIcon = {
                        if (option == value) FoodRescueGlyph(FoodRescueIcon.CHECK, modifier = Modifier.size(18.dp))
                    }) }
            }
        }
    }
}

@Composable
internal fun MenuError(message: String?, modifier: Modifier = Modifier) {
    if (message != null) Surface(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
        Text(message, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
    }
}

internal fun MenuAsset.sizeLabel(): String = if (byteCount < 1024 * 1024) "${(byteCount / 1024).coerceAtLeast(1)} KB"
    else String.format(java.util.Locale.forLanguageTag("vi-VN"), "%.1f MB", byteCount / (1024.0 * 1024))
