package com.foodrescue.app.dashboard.owner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.store.domain.*
import com.foodrescue.app.feature.store.presentation.component.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Operational totals come from orders and current sale-batch availability. */
internal data class OwnerOverviewUiState(val sold: Int = 0, val available: Int = 0, val awaitingPickup: Int = 0, val revenue: Long = 0)

@Composable
internal fun OwnerOverviewScreen(store: OwnerStore, summary: OwnerOverviewUiState, onStore: () -> Unit,
    onBoxes: () -> Unit, onOrders: () -> Unit, onPayout: () -> Unit, modifier: Modifier = Modifier,
    banks: List<PayoutBank> = emptyList()) {
    val colors = MaterialTheme.colorScheme
    val steps = listOf(store.status == StoreReviewStatus.APPROVED, store.profile.location != null,
        store.payout?.validationErrors(banks)?.isEmpty() == true)
    val complete = steps.count { it }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tổng quan", style = MaterialTheme.typography.headlineLarge)
                Text(SimpleDateFormat("EEEE, dd/MM", Locale.forLanguageTag("vi-VN")).format(Date()),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Surface(onClick = onStore, shape = RoundedCornerShape(16.dp), color = colors.surface) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    FoodRescueGlyph(FoodRescueIcon.STORE, tint = colors.primary, description = "Thông tin cửa hàng")
                }
            }
        }
        OwnerGovernanceNotice(store.governance)
        StoreHeroCard("ĐÃ THANH TOÁN HÔM NAY", money(summary.revenue)) {
            HorizontalDivider(color = Color.White.copy(alpha = .16f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(7.dp).background(Color(0xFFCAE8AE), CircleShape))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(store.profile.name, style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Text("${store.profile.opensAt} – ${store.profile.closesAt} · ${store.profile.area}",
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFFCCE2D7))
                }
            }
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompactMetric("Đã bán", summary.sold, FoodRescueIcon.BAG, onBoxes, Modifier.weight(1f))
            CompactMetric("Còn lại", summary.available, FoodRescueIcon.STORE, onBoxes, Modifier.weight(1f))
            CompactMetric("Chờ nhận", summary.awaitingPickup, FoodRescueIcon.CLOCK, onOrders, Modifier.weight(1f), true)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoreSectionTitle("Cần xử lý", Modifier.weight(1f))
                TextButton(onClick = onOrders) { Text("Xem đơn") }
            }
            Surface(onClick = onOrders, shape = RoundedCornerShape(22.dp), color = colors.surface,
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .6f))) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StoreIconTile(FoodRescueIcon.RECEIPT, container = Color(0xFFF1EDD9), color = Color(0xFF7A6632))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(if (summary.awaitingPickup == 0) "Không có đơn chờ nhận" else "${summary.awaitingPickup} đơn đang chờ nhận",
                            style = MaterialTheme.typography.titleSmall)
                        Text("Theo dõi giờ nhận và trạng thái đơn tại đây.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    FoodRescueGlyph(FoodRescueIcon.ARROW, tint = colors.primary)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoreSectionTitle("Thiết lập cửa hàng", Modifier.weight(1f))
                Text("$complete / 3", style = MaterialTheme.typography.labelLarge, color = colors.primary)
            }
            FoodRescueCard {
                LinearProgressIndicator(progress = { complete / 3f }, modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = colors.primary, trackColor = colors.primaryContainer)
                SetupAction("Hồ sơ đối tác", if (steps[0]) "Đã được xét duyệt" else "Chưa hoàn tất xét duyệt", steps[0], onStore)
                HorizontalDivider(color = colors.outlineVariant)
                SetupAction("Vị trí nhận hàng", if (steps[1]) "Đã ghim vị trí" else "Cần xác nhận vị trí trên bản đồ", steps[1], onStore)
                HorizontalDivider(color = colors.outlineVariant)
                SetupAction("Tài khoản nhận tiền", if (steps[2]) "Đã lưu thông tin" else "Cần thêm tài khoản nhận tiền", steps[2], onPayout)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoreSectionTitle("Nhận tiền đối soát", Modifier.weight(1f))
                TextButton(onClick = onPayout) { Text(if (store.payout == null) "Thiết lập" else "Quản lý") }
            }
            val account = store.payout
            if (account == null) StoreMenuRow("Thêm tài khoản ngân hàng", "Chọn ngân hàng nhận tiền của cửa hàng", FoodRescueIcon.BANK, onPayout)
            else Surface(onClick = onPayout, color = colors.surface, shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    BankLogo(banks.findBank(account.bank))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(account.bank, style = MaterialTheme.typography.titleSmall)
                        Text(account.maskedNumber, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    FoodRescueGlyph(FoodRescueIcon.ARROW, tint = colors.primary)
                }
            }
        }
        StoreMenuRow("Hộp bán của cửa hàng", "Đợt bán · Mẫu hộp · Menu nội bộ", FoodRescueIcon.BAG, onBoxes)
    }
}

@Composable
private fun CompactMetric(label: String, value: Int, icon: FoodRescueIcon, onClick: () -> Unit,
    modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Surface(onClick = onClick, modifier = modifier.fillMaxHeight(), shape = RoundedCornerShape(18.dp),
        color = if (emphasized) Color(0xFFE9EBD8) else MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            FoodRescueGlyph(icon, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
            Text(value.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun OverviewMetric(label: String, value: String, icon: FoodRescueIcon, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FoodRescueGlyph(icon, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetupAction(title: String, detail: String, complete: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StoreIconTile(if (complete) FoodRescueIcon.CHECK else FoodRescueIcon.EDIT, Modifier.size(34.dp),
                container = if (complete) MaterialTheme.colorScheme.primaryContainer else Color(0xFFF1EDD9))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FoodRescueGlyph(FoodRescueIcon.ARROW, Modifier.size(18.dp), MaterialTheme.colorScheme.outline)
        }
    }
}
