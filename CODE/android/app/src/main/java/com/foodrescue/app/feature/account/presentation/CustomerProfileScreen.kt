package com.foodrescue.app.feature.account.presentation

import com.foodrescue.app.core.designsystem.component.FoodRescueCard
import com.foodrescue.app.core.designsystem.component.FoodRescuePage
import com.foodrescue.app.core.designsystem.component.FoodRescueButton
import com.foodrescue.app.feature.account.domain.CustomerProfile
import com.foodrescue.app.feature.account.domain.isValidCustomerName
import com.foodrescue.app.feature.account.domain.isValidCustomerPhone
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
internal fun CustomerProfileScreen(profile: CustomerProfile, onEdit: () -> Unit, onOrders: () -> Unit, onComplaints: () -> Unit, onLogout: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text("Tài khoản", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        FoodRescueCard {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                    Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                        Text(profile.name.take(1).uppercase(), fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider()
            Text(profile.email, style = MaterialTheme.typography.bodyMedium)
            Text(profile.phone.ifBlank { "Chưa thêm số điện thoại" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Chỉnh sửa thông tin") }
        }
        FoodRescueCard {
            Text("Đơn hàng của tôi", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onOrders) { Text("Xem đơn hàng") }
        }
        FoodRescueCard {
            Text("Khiếu nại của tôi", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onComplaints) { Text("Xem khiếu nại") }
        }
        OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("Đăng xuất") }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Đăng xuất khỏi FoodRescue?") },
        text = { Text("Bạn cần đăng nhập lại để tiếp tục sử dụng tài khoản trên thiết bị này.") },
        confirmButton = { TextButton(onClick = { confirm = false; onLogout() }) { Text("Đăng xuất") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Ở lại") } })
}

@Composable
internal fun EditCustomerProfileScreen(profile: CustomerProfile, onBack: () -> Unit, onSave: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(profile.name) }
    var phone by rememberSaveable { mutableStateOf(profile.phone) }
    val nameError = !isValidCustomerName(name)
    val phoneError = !isValidCustomerPhone(phone)
    FoodRescuePage("Thông tin cá nhân", onBack, footer = {
        FoodRescueButton("Lưu thay đổi", { onSave(name.trim(), phone.trim()) }, Modifier.fillMaxWidth(), enabled = !nameError && !phoneError)
    }) {
        Text("Để cửa hàng dễ liên hệ với bạn", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(name, { name = it.take(120) }, label = { Text("Họ và tên") }, singleLine = true,
            isError = nameError, supportingText = { if (nameError) Text("Nhập họ tên từ 2 đến 120 ký tự.") },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        OutlinedTextField(profile.email, {}, readOnly = true, label = { Text("Email") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        OutlinedTextField(phone, { phone = it.take(13) }, label = { Text("Số điện thoại") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), isError = phoneError,
            supportingText = { if (phoneError) Text("Nhập 10 số bắt đầu bằng 0 hoặc số dạng +84.") },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
    }
}
