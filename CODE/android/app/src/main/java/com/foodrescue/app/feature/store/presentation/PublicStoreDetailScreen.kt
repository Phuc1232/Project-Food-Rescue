package com.foodrescue.app.feature.store.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.store.domain.PublicStore
import com.foodrescue.app.feature.store.presentation.component.*
import com.foodrescue.app.feature.surprisebox.domain.CatalogBox
import com.foodrescue.app.feature.surprisebox.presentation.component.SurpriseBoxCard

@Composable
internal fun PublicStoreDetailScreen(store: PublicStore, onBack: () -> Unit, onMap: () -> Unit,
    offers: List<CatalogBox>, onBox: (Int) -> Unit) {
    FoodRescuePage("Thông tin cửa hàng", onBack) {
        Text(store.category, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(store.name, style = MaterialTheme.typography.headlineMedium)
        if (store.description.isNotBlank()) Text(store.description, style = MaterialTheme.typography.bodyMedium)
        FoodRescueCard {
            StoreValue("Địa điểm", store.location.address)
            StoreValue("Giờ hoạt động", "${store.opensAt} – ${store.closesAt}" + if (store.closesAt < store.opensAt) " (ngày hôm sau)" else "")
            StoreValue("Số điện thoại", store.phone)
            if (store.contactEmail.isNotBlank()) StoreValue("Email liên hệ", store.contactEmail)
            OutlinedButton(onClick = onMap, modifier = Modifier.fillMaxWidth()) {
                FoodRescueGlyph(FoodRescueIcon.MAP); Text("Xem trên bản đồ", Modifier.padding(start = 8.dp))
            }
            PickupDirectionsButton(store.location, Modifier.fillMaxWidth())
        }
        if (offers.isEmpty()) {
            StoreEmptyPanel(FoodRescueIcon.BAG, "Chưa có đợt bán", "Cửa hàng chưa mở bán hộp. Hãy quay lại sau để xem các hộp mới.")
        } else {
            Text("Hộp đang mở bán", style = MaterialTheme.typography.titleLarge)
            offers.forEach { offer -> SurpriseBoxCard(offer, Modifier.fillMaxWidth()) { onBox(offer.id) } }
        }
    }
}
