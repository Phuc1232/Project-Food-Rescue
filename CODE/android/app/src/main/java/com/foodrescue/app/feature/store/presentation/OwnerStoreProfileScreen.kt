package com.foodrescue.app.feature.store.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.store.domain.*
import com.foodrescue.app.feature.store.presentation.component.*

@Composable
internal fun OwnerStoreProfileScreen(store: OwnerStore, email: String, onBack: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    FoodRescuePage("Thông tin cửa hàng", onBack, modifier, footer = {
        FoodRescueButton("Chỉnh sửa thông tin", onEdit, Modifier.fillMaxWidth())
    }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StoreIconTile(FoodRescueIcon.STORE)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(store.profile.name, style = MaterialTheme.typography.headlineMedium)
                StoreStatusBadge(store.status)
            }
        }
        FoodRescueCard { StoreProfileValues(store.profile, email) }
        store.profile.pickupLocation?.let { PickupDirectionsButton(it, Modifier.fillMaxWidth()) }
    }
}

@Composable
internal fun StoreProfileValues(profile: OwnerStoreProfile, email: String) {
    StoreValue("Loại hình", profile.category)
    if (profile.description.isNotBlank()) StoreValue("Giới thiệu", profile.description)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    StoreValue("Người đại diện", profile.contactName)
    StoreValue("Số điện thoại", profile.phone)
    StoreValue("Email đăng nhập", email)
    if (profile.contactEmail.isNotBlank()) StoreValue("Email liên hệ cửa hàng", profile.contactEmail)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    StoreValue("Địa điểm", profile.address + ", " + profile.area + ", TP. Hồ Chí Minh")
    StoreValue("Vị trí trên bản đồ", if (profile.location == null) "Chưa chọn vị trí" else "Đã ghim vị trí cửa hàng")
    StoreValue("Giờ hoạt động", profile.opensAt + " – " + profile.closesAt +
        if (profile.closesAt < profile.opensAt) " (ngày hôm sau)" else "")
}
