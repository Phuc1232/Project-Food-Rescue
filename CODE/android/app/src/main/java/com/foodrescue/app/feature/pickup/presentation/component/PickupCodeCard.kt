package com.foodrescue.app.feature.pickup.presentation.component

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.foodrescue.app.core.designsystem.component.FoodRescueCard

@Composable
internal fun PickupCodeCard(code: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var copied by remember(code) { mutableStateOf(false) }
    FoodRescueCard(modifier) {
        Text("Mã nhận hàng", style = MaterialTheme.typography.titleMedium)
        Text(code, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        OutlinedButton(onClick = {
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Mã nhận hàng", code))
            copied = true
        }, modifier = Modifier.fillMaxWidth()) {
            Text(if (copied) "Đã sao chép" else "Sao chép mã")
        }
    }
}
