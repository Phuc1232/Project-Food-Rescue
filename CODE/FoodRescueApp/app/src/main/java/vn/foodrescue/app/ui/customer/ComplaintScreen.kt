package vn.foodrescue.app.ui.customer

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComplaintScreen(order: CustomerOrder, now: Long, onBack: () -> Unit,
    onSubmit: (ComplaintReason, String, List<Evidence>) -> Unit) {
    val context = LocalContext.current
    var reason by rememberSaveable { mutableStateOf(if (order.status == OrderStatus.NO_SHOW) ComplaintReason.NO_SHOW else ComplaintReason.QUALITY) }
    var content by rememberSaveable { mutableStateOf("") }
    var uris by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var names by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var expanded by remember { mutableStateOf(false) }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    var tried by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selected ->
        attachmentError = null
        if (selected.size + uris.size > 5) attachmentError = "Chỉ chọn tối đa 5 ảnh cho một yêu cầu."
        else selected.forEach { uri ->
            if (uri.toString() !in uris) runCatching {
                val resolver = context.contentResolver
                require(resolver.getType(uri) in setOf("image/jpeg", "image/png", "image/webp")) { "Chỉ hỗ trợ ảnh JPG, PNG hoặc WebP." }
                var name = "Ảnh đính kèm"
                var size = -1L
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) { name = cursor.getString(0) ?: name; if (!cursor.isNull(1)) size = cursor.getLong(1) }
                }
                require(size in 1..10_485_760) { "Mỗi ảnh tối đa 10 MB. Hãy chọn ảnh đã tải về máy." }
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                uris = uris + uri.toString(); names = names + name
            }.onFailure { attachmentError = it.message ?: "Không đọc được ảnh. Vui lòng chọn lại." }
        }
    }
    CustomerPage("Gửi yêu cầu hỗ trợ", onBack, footer = {
        PrimaryAction("Gửi yêu cầu") {
            tried = true
            if (content.trim().length in 20..2000) onSubmit(reason, content, uris.mapIndexed { i, uri -> Evidence(uri, names[i]) })
        }
    }) {
        PreviewNotice("Bản trải nghiệm · Nội dung chưa gửi đến quản trị viên")
        OrderBoxSummary(order.box, order.quantity)
        Text("Bạn cần hỗ trợ điều gì?", style = MaterialTheme.typography.headlineSmall)
        ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
            OutlinedTextField(reason.label, {}, readOnly = true, label = { Text("Lý do") }, modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
            ExposedDropdownMenu(expanded, { expanded = false }) {
                ComplaintReason.entries.filter { it != ComplaintReason.NO_SHOW || order.status == OrderStatus.NO_SHOW }.forEach { option ->
                    DropdownMenuItem(text = { Text(option.label) }, onClick = { reason = option; expanded = false })
                }
            }
        }
        if (reason == ComplaintReason.NO_SHOW) CustomerCard {
            Text("Phản ánh kết luận không đến nhận", style = MaterialTheme.typography.titleSmall)
            Text(order.noShowDeadline?.let {
                if (now <= it) "Bạn có thể phản ánh đến ${orderTime(it)}. Khoản bù đắp chưa được giải tỏa khi phản ánh đang được xem xét."
                else "Đã qua mốc 48 giờ kể từ khi nhận thông báo. Yêu cầu vẫn được chuyển xem xét; chưa thể cam kết hoàn tiền."
            } ?: "Chưa xác định thời điểm bạn nhận thông báo; chưa tự tính yêu cầu là quá hạn.", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(content, { if (it.length <= 2000) content = it }, label = { Text("Mô tả vấn đề") },
            placeholder = { Text("Bạn gặp vấn đề gì, vào lúc nào? Cung cấp chi tiết để việc xử lý thuận lợi hơn.") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 170.dp), minLines = 5,
            isError = tried && content.trim().length < 20,
            supportingText = { Text(if (tried && content.trim().length < 20) "Mô tả ít nhất 20 ký tự." else "${content.length}/2.000 ký tự") })
        Text("Ảnh bằng chứng", style = MaterialTheme.typography.titleMedium)
        Text("Tối đa 5 ảnh JPG, PNG hoặc WebP; mỗi ảnh tối đa 10 MB. Không đưa mật khẩu, mã OTP hay thông tin thẻ vào ảnh.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        uris.forEachIndexed { index, uri -> CustomerCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}. ${names[index]}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { uris = uris.filterIndexed { i, _ -> i != index }; names = names.filterIndexed { i, _ -> i != index } }) {
                    CatalogGlyph(CatalogIcon.CLOSE, description = "Bỏ ảnh ${index + 1}")
                }
            }
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                    .onFailure { attachmentError = "Không có ứng dụng mở ảnh này." }
            }) { Text("Xem ảnh") }
        } }
        if (uris.size < 5) OutlinedButton(onClick = { picker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Thêm ảnh (${uris.size}/5)") }
        attachmentError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Text("Sau khi gửi, bạn theo dõi trạng thái tại chi tiết đơn. Tiếp nhận yêu cầu chưa đồng nghĩa chấp thuận hoàn tiền.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
