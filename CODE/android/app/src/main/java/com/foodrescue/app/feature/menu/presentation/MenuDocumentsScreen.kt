package com.foodrescue.app.feature.menu.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.menu.domain.*
import com.foodrescue.app.feature.menu.presentation.component.*

@Composable
internal fun MenuDocumentsScreen(documents: List<MenuAsset>, busy: Boolean, error: String?, onBack: () -> Unit,
    onAdd: () -> Unit, onPreview: (MenuAsset) -> Unit, onRemove: (String) -> Unit, modifier: Modifier = Modifier) {
    FoodRescuePage("Bảng giá & tài liệu", onBack, modifier, footer = {
        FoodRescueButton(if (busy) "Đang xử lý…" else "Thêm tài liệu", onAdd, Modifier.fillMaxWidth(), enabled = !busy && documents.size < MAX_MENU_DOCUMENTS)
    }) {
        MenuError(error)
        Text("Giá rõ ràng.\nThông tin đầy đủ.", style = MaterialTheme.typography.headlineMedium)
        Text("Lưu bảng giá và tài liệu tham chiếu của cửa hàng tại một nơi.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FoodRescueCard {
            Text("${documents.size} / $MAX_MENU_DOCUMENTS tài liệu", style = MaterialTheme.typography.titleMedium)
            Text("PDF hoặc ảnh · Tối đa 10 MB mỗi tệp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (documents.size == MAX_MENU_DOCUMENTS) Text("Gỡ một tài liệu để thêm tài liệu mới.", style = MaterialTheme.typography.bodySmall)
        }
        if (documents.isEmpty()) FoodRescueCard {
            FoodRescueGlyph(FoodRescueIcon.RECEIPT, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Chưa có tài liệu", style = MaterialTheme.typography.titleMedium)
            Text("Thêm ảnh thực đơn, bảng giá hoặc tài liệu giới thiệu món.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        documents.forEach { document -> FoodRescueCard {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                MenuPhoto(document, Modifier.size(64.dp), fit = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(document.name, style = MaterialTheme.typography.titleSmall)
                    Text("${if (document.mimeType == "application/pdf") "PDF · ${document.pageCount} trang" else "Ảnh"} · ${document.sizeLabel()}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton({ onPreview(document) }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Xem tài liệu") }
            TextButton({ onRemove(document.id) }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Gỡ tài liệu", color = MaterialTheme.colorScheme.error) }
        } }
    }
}
