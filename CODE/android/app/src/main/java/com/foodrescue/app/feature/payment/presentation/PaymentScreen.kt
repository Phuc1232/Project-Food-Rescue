package com.foodrescue.app.feature.payment.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.payment.domain.PaymentStatus
import com.foodrescue.app.feature.payment.domain.QrPayment
import com.foodrescue.app.feature.pickup.presentation.component.PickupCodeCard

internal data class PaymentUiState(
    val payment: QrPayment,
    val store: String,
    val quantity: Int,
    val pickup: String,
    val address: String,
    val remainingSeconds: Long,
    val pickupCode: String? = null,
)

@Composable
internal fun PaymentScreen(
    state: PaymentUiState,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onOrders: () -> Unit,
    onRebook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val payment = state.payment
    val status = payment.status
    val time = (state.remainingSeconds / 60).toString().padStart(2, '0') + ":" +
        (state.remainingSeconds % 60).toString().padStart(2, '0')
    FoodRescuePage("Thanh toán VNPAY", onBack, modifier, footer = {
        val label = when (status) {
            PaymentStatus.READY -> "Kiểm tra thanh toán"
            PaymentStatus.PROCESSING -> "Theo dõi trong Đơn hàng"
            PaymentStatus.SUCCEEDED -> "Về Đơn hàng"
            PaymentStatus.FAILED -> "Kiểm tra lại"
            PaymentStatus.EXPIRED -> "Đặt lại hộp"
        }
        FoodRescueButton(label, when (status) {
            PaymentStatus.READY, PaymentStatus.FAILED -> onCheck
            PaymentStatus.PROCESSING, PaymentStatus.SUCCEEDED -> onOrders
            PaymentStatus.EXPIRED -> onRebook
        }, Modifier.fillMaxWidth())
    }) {
        if (status == PaymentStatus.READY) {
            PaymentQrCard(payment.amount, time)
        } else {
            PaymentStatusHeader(status)
            if (status == PaymentStatus.SUCCEEDED && state.pickupCode != null) PickupCodeCard(state.pickupCode)
            FoodRescueCard {
                Text(if (status == PaymentStatus.SUCCEEDED) "Đã thanh toán toàn bộ" else "Số tiền thanh toán",
                    style = MaterialTheme.typography.bodyMedium)
                Text(money(payment.amount), style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("QR VNPAY", style = MaterialTheme.typography.labelLarge)
                if (status == PaymentStatus.PROCESSING || status == PaymentStatus.FAILED) {
                    Text("Thời gian còn lại: $time", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        FoodRescueCard {
            Text("Thông tin đơn hàng", style = MaterialTheme.typography.titleMedium)
            Text(state.store, style = MaterialTheme.typography.titleSmall)
            Text("${state.quantity} hộp · " + money(payment.amount), style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            Text("Mã thanh toán", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(payment.reference, style = MaterialTheme.typography.labelLarge)
            Text("Nhận tại cửa hàng", style = MaterialTheme.typography.titleSmall)
            Text(state.pickup, style = MaterialTheme.typography.bodyMedium)
            Text(state.address, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PaymentStatusHeader(status: PaymentStatus) {
    val failed = status == PaymentStatus.FAILED
    val accent = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = CircleShape, color = accent.copy(alpha = .09f)) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                if (status == PaymentStatus.PROCESSING) CircularProgressIndicator(Modifier.size(32.dp), color = accent)
                else FoodRescueGlyph(when (status) {
                    PaymentStatus.SUCCEEDED -> FoodRescueIcon.CHECK
                    PaymentStatus.FAILED -> FoodRescueIcon.CLOSE
                    else -> FoodRescueIcon.CLOCK
                }, tint = accent)
            }
        }
        Text(when (status) {
            PaymentStatus.READY -> "Sẵn sàng thanh toán"
            PaymentStatus.PROCESSING -> "Đang xác nhận thanh toán"
            PaymentStatus.SUCCEEDED -> "Thanh toán thành công"
            PaymentStatus.FAILED -> "Chưa thể xác nhận thanh toán"
            PaymentStatus.EXPIRED -> "Đã hết hạn thanh toán"
        }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(when (status) {
            PaymentStatus.READY -> "Quét mã QR để thanh toán toàn bộ đơn hàng."
            PaymentStatus.PROCESSING -> "Kết quả sẽ được cập nhật tại đây và trong Đơn hàng."
            PaymentStatus.SUCCEEDED -> "Đã thanh toán toàn bộ giá trị đơn hàng."
            PaymentStatus.FAILED -> "Kiểm tra kết nối rồi thử lại. Vui lòng xác nhận kết quả trước khi thanh toán thêm."
            PaymentStatus.EXPIRED -> "Lượt giữ hộp đã kết thúc. Bạn có thể kiểm tra số lượng còn lại và đặt lại."
        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
    }
}
