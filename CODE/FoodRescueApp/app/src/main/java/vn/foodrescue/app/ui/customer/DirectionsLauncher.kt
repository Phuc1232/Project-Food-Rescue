package vn.foodrescue.app.ui.customer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

internal fun openDirections(context: Context, location: StoreLocation) {
    val uri = directionsUrl(location).toUri()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps"))
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Bạn cần Google Maps hoặc trình duyệt để xem đường đi.", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
internal fun PreviewDirectionsDialog(onOpen: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Xem đường đến địa điểm mẫu?") },
        text = { Text("Vị trí này dùng để xem thử giao diện, chưa phải địa chỉ cửa hàng đã xác minh. Google Maps sẽ mở đường đi đến điểm mẫu này.") },
        confirmButton = { TextButton(onClick = onOpen) { Text("Mở Google Maps") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Quay lại") } })
}
