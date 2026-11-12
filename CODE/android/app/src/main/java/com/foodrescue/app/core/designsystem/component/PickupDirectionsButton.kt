package com.foodrescue.app.core.designsystem.component

import com.foodrescue.app.core.common.PickupLocation
import com.foodrescue.app.core.util.openDirectionsWithFallback

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.app.Activity
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal fun openDirections(context: Context, location: PickupLocation): Boolean = openDirectionsWithFallback(location) { url, packageName ->
    try {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).setPackage(packageName)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) { false
    } catch (_: SecurityException) { false }
}

@Composable
internal fun rememberDirectionsLauncher(): (PickupLocation) -> Unit {
    val context = LocalContext.current
    var failed by remember { mutableStateOf<PickupLocation?>(null) }
    val launch: (PickupLocation) -> Unit = { location ->
        failed = if (openDirections(context, location)) null else location
    }
    failed?.let { location ->
        AlertDialog(onDismissRequest = { failed = null }, title = { Text("Chưa mở được chỉ đường") },
            text = { Text("Bạn cần Google Maps hoặc trình duyệt để xem đường đi.\n\nĐịa chỉ: ${location.address}") },
            confirmButton = { TextButton(onClick = { launch(location) }) { Text("Thử lại") } },
            dismissButton = { TextButton(onClick = { failed = null }) { Text("Đóng") } })
    }
    return launch
}

@Composable
internal fun PickupDirectionsButton(location: PickupLocation?, modifier: Modifier = Modifier) {
    val directions = rememberDirectionsLauncher()
    OutlinedButton(onClick = { location?.let(directions) }, enabled = location != null,
        modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
        FoodRescueGlyph(FoodRescueIcon.MAP)
        Spacer(Modifier.width(8.dp))
        Text("Chỉ đường đến cửa hàng")
    }
    Text(if (location == null) "Chưa có tọa độ nhận hộp hợp lệ. Hãy đối chiếu địa chỉ với cửa hàng."
        else "Mở Google Maps để chọn điểm xuất phát và phương tiện.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
