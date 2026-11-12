package com.foodrescue.app.feature.store.presentation

import com.foodrescue.app.feature.surprisebox.domain.CatalogBox
import com.foodrescue.app.feature.surprisebox.presentation.component.CatalogPhoto
import com.foodrescue.app.feature.surprisebox.presentation.component.SurpriseBoxCard
import com.foodrescue.app.core.designsystem.component.FoodRescueGlyph
import com.foodrescue.app.core.designsystem.component.FoodRescueIcon
import com.foodrescue.app.core.designsystem.component.PickupDirectionsButton
import com.foodrescue.app.core.util.distance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun StoreDetailScreen(offers: List<CatalogBox>, onBack: () -> Unit, onBox: (Int) -> Unit, onMap: () -> Unit) {
    val store = offers.firstOrNull() ?: return
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { FoodRescueGlyph(FoodRescueIcon.BACK, description = "Quay lại") }
                Text("Cửa hàng", style = MaterialTheme.typography.titleMedium)
            }
        }
        item { CatalogPhoto(store, Modifier.fillMaxWidth().height(190.dp)) }
        item {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(store.store, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(store.district + if (store.distanceMeters >= 0) " · ${distance(store.distanceMeters)}" else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Điểm nhận hộp", style = MaterialTheme.typography.titleMedium)
                Text(store.location?.address ?: "Cửa hàng chưa cập nhật địa chỉ.")
                TextButton(onClick = onMap, contentPadding = PaddingValues(0.dp)) { Text("Xem trên bản đồ") }
                PickupDirectionsButton(store.location, Modifier.fillMaxWidth())
                HorizontalDivider()
                Text("Khung giờ nhận hộp", style = MaterialTheme.typography.titleMedium)
                Text(offers.map { it.pickup }.distinct().joinToString("\n"))
                Text("${offers.size} lựa chọn tại cửa hàng", style = MaterialTheme.typography.titleMedium)
            }
        }
        items(offers, key = { it.id }) { offer -> SurpriseBoxCard(offer, Modifier.padding(horizontal = 24.dp)) { onBox(offer.id) } }
    }
}
