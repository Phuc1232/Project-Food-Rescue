package com.foodrescue.app.dashboard.owner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.store.domain.OwnerStore
import com.foodrescue.app.feature.store.presentation.component.*

@Composable
internal fun OwnerStoreHubScreen(store: OwnerStore, email: String, onProfile: () -> Unit, onReview: () -> Unit,
    onPayout: () -> Unit, onStaff: () -> Unit, onCustomer: () -> Unit, onLogout: () -> Unit, modifier: Modifier = Modifier,
    onMenu: () -> Unit = {}) {
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Cửa hàng", style = MaterialTheme.typography.headlineLarge)
        StoreHeroCard("ĐỐI TÁC FOODRESCUE", store.profile.name) {
            StoreStatusBadge(store.status)
            Text(store.profile.category + " · " + store.profile.area, color = androidx.compose.ui.graphics.Color(0xFFCCE2D7),
                style = MaterialTheme.typography.bodyMedium)
        }
        StoreSectionTitle("Quản lý cửa hàng")
        OwnerGovernanceNotice(store.governance)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StoreMenuRow("Thông tin cửa hàng", "Địa chỉ, liên hệ và giờ hoạt động", FoodRescueIcon.STORE, onProfile)
            StoreMenuRow("Menu nội bộ", "Món ăn, giá niêm yết và tài liệu", FoodRescueIcon.RECEIPT, onMenu)
            StoreMenuRow("Hồ sơ đối tác", "Thông tin và trạng thái xét duyệt", FoodRescueIcon.CHECK, onReview)
            StoreMenuRow("Tài khoản nhận tiền", store.payout?.let { it.bank + " · " + it.maskedNumber } ?: "Thiết lập tài khoản đối soát",
                FoodRescueIcon.BANK, onPayout)
            StoreMenuRow("Nhân viên", "Tài khoản và quyền truy cập cửa hàng", FoodRescueIcon.PEOPLE, onStaff)
        }
        StoreSectionTitle("Tài khoản dùng chung")
        FoodRescueCard {
            StoreValue("Email đăng nhập", email)
            OutlinedButton(onClick = onCustomer, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Khám phá với tư cách khách hàng") }
            TextButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth()) { Text("Đăng xuất", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text("Đăng xuất khỏi FoodRescue?") },
        text = { Text("Thông tin cửa hàng đã lưu sẽ được giữ lại cho lần đăng nhập tiếp theo.") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("Đăng xuất") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Ở lại") } })
}
