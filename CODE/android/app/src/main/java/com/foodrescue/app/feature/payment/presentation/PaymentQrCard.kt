package com.foodrescue.app.feature.payment.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foodrescue.app.R
import com.foodrescue.app.core.designsystem.component.FoodRescueCard
import com.foodrescue.app.core.util.money

@Composable
internal fun PaymentQrCard(amount: Long, timeRemaining: String, modifier: Modifier = Modifier) {
    val qrSide = if (LocalConfiguration.current.screenHeightDp < 480) 144.dp else 240.dp
    FoodRescueCard(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("VN", color = Color(0xFFBE2637), style = MaterialTheme.typography.titleLarge)
                Text("PAY", color = Color(0xFF174A86), style = MaterialTheme.typography.titleLarge)
                Text(" QR", color = Color(0xFF174A86), style = MaterialTheme.typography.titleMedium)
            }
            Text(money(amount), style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Thanh toán toàn bộ", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Non-payable local QR fixture. The server-backed adapter will supply the transaction QR.
            Image(painterResource(R.drawable.payment_qr), "Mã QR thanh toán VNPAY",
                Modifier.widthIn(max = qrSide).fillMaxWidth().aspectRatio(1f))
            Text("Quét mã để thanh toán", style = MaterialTheme.typography.bodyMedium)
            Text("Mã hết hạn sau $timeRemaining", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}
