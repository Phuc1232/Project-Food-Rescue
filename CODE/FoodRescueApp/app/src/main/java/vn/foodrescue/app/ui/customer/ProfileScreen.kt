package vn.foodrescue.app.ui.customer

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
internal fun ProfileScreen(profile: CustomerProfile, onEdit: () -> Unit, onOrders: () -> Unit, onLogout: () -> Unit,
    preview: Boolean = true, busy: Boolean = false) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text("Tài khoản", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        CustomerCard {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                    Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                        Text(profile.name.take(1).uppercase(), fontSize = 24.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium)
                    Text(if (preview) "Tài khoản trải nghiệm" else "Email đã xác minh", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            Text(profile.email, style = MaterialTheme.typography.bodyMedium)
            Text(profile.phone.ifBlank { "Chưa thêm số điện thoại" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onEdit, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Chỉnh sửa thông tin") }
        }
        if (preview) CustomerCard {
            Text("Đơn hàng và hỗ trợ", style = MaterialTheme.typography.titleMedium)
            Text("Xem lịch sử, mã nhận hộp và kết quả xử lý yêu cầu.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onOrders) { Text("Xem đơn hàng của tôi") }
        }
        if (preview) PreviewNotice("Thông tin và đơn thử chỉ được lưu trên điện thoại này, chưa đồng bộ vào tài khoản thật.")
        OutlinedButton(onClick = { confirm = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text(if (busy) "Đang xử lý…" else "Đăng xuất") }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(if (preview) "Thoát tài khoản trải nghiệm?" else "Đăng xuất khỏi FoodRescue?") },
        text = { Text(if (preview) "Thông tin cá nhân, đơn và khiếu nại thử trên máy này sẽ được xóa. Bạn sẽ trở về màn hình chào."
            else "Bạn cần đăng nhập lại để tiếp tục sử dụng tài khoản trên thiết bị này.") },
        confirmButton = { TextButton(onClick = { confirm = false; onLogout() }) { Text("Đăng xuất") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Ở lại") } })
}

@Composable
internal fun EditProfileScreen(profile: CustomerProfile, onBack: () -> Unit, preview: Boolean = true, busy: Boolean = false, onSave: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(profile.name) }
    var phone by rememberSaveable { mutableStateOf(profile.phone) }
    val nameError = name.isNotEmpty() && (name.trim().length !in 2..120 || name.any(Char::isISOControl))
    val phoneError = phone.isNotBlank() && !Regex("^(0[0-9]{9}|\\+84[0-9]{9})$").matches(phone.trim())
    CustomerPage("Thông tin cá nhân", onBack, footer = { PrimaryAction(if (busy) "Đang lưu…" else "Lưu thay đổi", !busy && name.trim().length in 2..120 && !nameError && !phoneError) { onSave(name, phone) } }) {
        Text("Để cửa hàng dễ liên hệ với bạn", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(name, { if (it.length <= 120) name = it }, label = { Text("Họ và tên") }, singleLine = true, enabled = !busy,
            isError = nameError, supportingText = { if (nameError) Text("Nhập họ tên từ 2 đến 120 ký tự.") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        OutlinedTextField(profile.email, {}, readOnly = true, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text(if (preview) "Email minh họa, chưa dùng để xác minh tài khoản." else "Email đã xác minh, không thay đổi tại đây.") }, shape = RoundedCornerShape(14.dp))
        OutlinedTextField(phone, { if (it.length <= 13) phone = it }, label = { Text("Số điện thoại") }, singleLine = true, enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), isError = phoneError,
            supportingText = { Text(if (phoneError) "Nhập 10 số bắt đầu bằng 0 hoặc số dạng +84." else "Không bắt buộc.") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        if (preview) PreviewNotice("Thay đổi được lưu trong hồ sơ trải nghiệm trên thiết bị.")
    }
}
