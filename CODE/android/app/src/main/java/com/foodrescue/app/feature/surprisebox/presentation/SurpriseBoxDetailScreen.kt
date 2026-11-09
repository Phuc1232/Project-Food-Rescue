package com.foodrescue.app.feature.surprisebox.presentation

import com.foodrescue.app.feature.surprisebox.domain.CatalogBox
import com.foodrescue.app.feature.surprisebox.presentation.component.CatalogPhoto
import com.foodrescue.app.feature.surprisebox.presentation.component.StoreMonogram
import com.foodrescue.app.core.designsystem.component.FoodRescueGlyph
import com.foodrescue.app.core.designsystem.component.FoodRescueIcon
import com.foodrescue.app.core.designsystem.component.PickupDirectionsButton
import com.foodrescue.app.core.util.money
import com.foodrescue.app.core.util.distance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SurpriseBoxDetailScreen(box: CatalogBox, onBack: () -> Unit, onMap: () -> Unit, onStore: () -> Unit) {
    val c = MaterialTheme.colorScheme
    LazyColumn(contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { FoodRescueGlyph(FoodRescueIcon.BACK, description = "Quay lại khám phá") }
            Text("Thông tin hộp", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        } }
        item { CatalogPhoto(box, Modifier.fillMaxWidth().height(230.dp), showCount = true) }
        item { Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StoreMonogram(box)
                Column { Text(box.store, style = MaterialTheme.typography.titleMedium);Text(box.district + if (box.distanceMeters >= 0) " · ${distance(box.distanceMeters)}" else "", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant) }
            }
            TextButton(onClick = onStore, contentPadding = PaddingValues(0.dp)) { Text("Xem cửa hàng") }
            Text(box.name, style = MaterialTheme.typography.headlineMedium)
            Text(box.description, style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
            Surface(color = c.primaryContainer, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FoodRescueGlyph(FoodRescueIcon.CLOCK, tint = c.primary)
                    Column { Text("Nhận tại cửa hàng", fontWeight = FontWeight.SemiBold);Text(box.pickup, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Địa điểm nhận hộp", style = MaterialTheme.typography.titleSmall)
                Text(box.location?.address ?: "Cửa hàng chưa cập nhật địa chỉ.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onMap, contentPadding = PaddingValues(0.dp)) { Text("Xem trên bản đồ") }
                PickupDirectionsButton(box.location,Modifier.fillMaxWidth())
            }
            Text("Giá trị thực phẩm tối thiểu", style = MaterialTheme.typography.titleSmall)
            Text("${money(box.minimumValue)} cho mỗi hộp. Thành phần và số lượng từng món không được công bố trước.", style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(color = c.outlineVariant)
            Text("Thông tin dị ứng", style = MaterialTheme.typography.titleSmall)
            Text(box.allergens, style = MaterialTheme.typography.bodyMedium)
            Text("Ảnh không thể hiện chính xác các món sẽ có trong hộp.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        } }
    }
}

@Composable
internal fun SurpriseBoxDetailFooter(box: CatalogBox, hasReservation: Boolean, onBook: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Giá mỗi hộp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(money(box.price), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Button(onClick = onBook, enabled = hasReservation || box.isAvailable, shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
                    Text(if (hasReservation) "Xem hộp đang giữ" else if (box.isAvailable) "Đặt hộp" else "Đã hết hộp")
                }
            }
    }
}
