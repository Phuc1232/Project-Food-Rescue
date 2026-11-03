package vn.foodrescue.app.ui.customer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PaymentPreviewLabel() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp)) {
            Text("BẢN THỬ", Modifier.padding(horizontal = 7.dp, vertical = 4.dp), fontSize = 10.sp, lineHeight = 14.sp,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text("Không thu tiền thật", Modifier.padding(top = 3.dp), fontSize = 12.sp, lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun PaymentSectionTitle(title: String) {
    Text(title, Modifier.semantics { heading() }, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun PaymentMethodBadge(method: PaymentMethod, modifier: Modifier = Modifier) {
    val fill = when (method) {
        PaymentMethod.WALLET -> Color(0xFFA50064)
        PaymentMethod.INTERNATIONAL_CARD -> Color(0xFF182D60)
        else -> Color(0xFFF0F4FA)
    }
    Surface(modifier.size(48.dp), color = fill, shape = RoundedCornerShape(12.dp)) {
        Box(contentAlignment = Alignment.Center) {
            when (method) {
                PaymentMethod.WALLET -> Text("MoMo", color = Color.White, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
                PaymentMethod.VNPAY_QR -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row {
                        Text("VN", color = Color(0xFFBE2637), fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.ExtraBold)
                        Text("PAY", color = Color(0xFF174A86), fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text("QR", fontSize = 10.sp, lineHeight = 13.sp, color = Color(0xFF174A86), fontWeight = FontWeight.Medium)
                }
                PaymentMethod.CARD -> Text("ATM", fontSize = 12.sp, lineHeight = 16.sp, color = Color(0xFF174A86), fontWeight = FontWeight.Bold)
                PaymentMethod.INTERNATIONAL_CARD -> Text("CARD", color = Color.White, fontSize = 9.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaymentMethodSheet(selected: PaymentMethod, onSelect: (PaymentMethod) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Thanh toán bằng", Modifier.weight(1f), fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { CatalogGlyph(CatalogIcon.CLOSE, description = "Đóng phương thức thanh toán") }
            }
            Text("Chọn cách thanh toán phù hợp với bạn.", fontSize = 13.sp, lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PaymentMethod.entries.forEach { method ->
                    val isSelected = method == selected
                    Surface(color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = .045f) else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp), border = BorderStroke(if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().selectable(isSelected, role = Role.RadioButton, onClick = { onSelect(method) })) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            PaymentMethodBadge(method)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(method.label, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                                Text(method.description, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RadioButton(isSelected, onClick = null, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
            Text("Các lựa chọn hiện dùng để trải nghiệm giao diện. Chưa kết nối ngân hàng hoặc ví và không phát sinh giao dịch thật.",
                fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SelectedPaymentMethod(method: PaymentMethod, onChange: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PaymentMethodBadge(method)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(method.label, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
                    Text(method.description, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (onChange != null) TextButton(onClick = onChange, modifier = Modifier.align(Alignment.End), contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text("Đổi phương thức", fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                CatalogGlyph(CatalogIcon.ARROW, Modifier.size(16.dp))
            }
        }
    }
}

@Composable
internal fun PaymentPlanOption(title: String, amount: Long, detail: String, selected: Boolean, onSelect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(if (selected) colors.primary.copy(alpha = .045f) else colors.surface, label = "planBackground")
    Surface(color = background, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
        modifier = Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onSelect)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            RadioButton(selected, onClick = null, modifier = Modifier.size(24.dp).padding(top = 2.dp))
            Column(Modifier.weight(1f).animateContentSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(money(amount), fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold,
                    color = if (selected) colors.primary else colors.onSurface)
                Text(detail, fontSize = 12.sp, lineHeight = 18.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun CheckoutSummary(box: DemoBox, count: Int, available: Int, pickupStart: Long, pickupEnd: Long,
    onDecrease: () -> Unit, onIncrease: () -> Unit) {
    CustomerCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(box.photo()), "Ảnh minh họa hộp", Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(box.store, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
                Text(box.category.label, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(money(box.price), fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                Text("mỗi hộp", fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onDecrease, enabled = count > 1, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(44.dp), shape = RoundedCornerShape(12.dp)) {
                Text("−", fontSize = 20.sp)
            }
            Text(count.toString(), Modifier.widthIn(min = 40.dp), textAlign = TextAlign.Center, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onIncrease, enabled = count < available, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(44.dp), shape = RoundedCornerShape(12.dp)) {
                Text("+", fontSize = 20.sp)
            }
        }
        if (available == 0) Text("Hộp vừa hết. Vui lòng chọn hộp khác.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CatalogGlyph(CatalogIcon.CLOCK, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Nhận ${orderTime(pickupStart).substringBefore(" ·")} – ${orderTime(pickupEnd).substringBefore(" ·")} · ${orderTime(pickupStart).substringAfter(" · ")}",
                    fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
                Text(box.location?.address ?: box.district, fontSize = 11.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun PaymentFooter(amount: Long, label: String, enabled: Boolean = true, remaining: Long? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Thanh toán ngay", Modifier.weight(1f), fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(money(amount), fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
    remaining?.takeIf { it > 0 }?.let {
        Text("Còn ${money(it)} trả khi nhận hộp", Modifier.padding(top = 3.dp), fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(10.dp))
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        CatalogGlyph(CatalogIcon.ARROW, Modifier.size(18.dp))
    }
}
