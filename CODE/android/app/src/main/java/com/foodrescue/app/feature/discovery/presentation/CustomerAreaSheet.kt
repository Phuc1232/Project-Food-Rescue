package com.foodrescue.app.feature.discovery.presentation

import com.foodrescue.app.core.common.GeoPoint
import com.foodrescue.app.feature.discovery.domain.LocationSource
import com.foodrescue.app.core.designsystem.component.FoodRescueGlyph
import com.foodrescue.app.core.designsystem.component.FoodRescueIcon
import com.foodrescue.app.core.util.searchKey
import androidx.core.net.toUri

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Familiar search areas, not a declaration of current administrative districts.
internal val customerAreas = listOf("TP. Hồ Chí Minh", "Quận 1", "Quận 3", "Quận 4", "Quận 5", "Quận 6", "Quận 7",
    "Quận 8", "Quận 10", "Quận 11", "Quận 12", "Bình Thạnh", "Bình Tân", "Gò Vấp", "Phú Nhuận", "Tân Bình", "Tân Phú",
    "Thủ Đức", "Bình Chánh", "Cần Giờ", "Củ Chi", "Hóc Môn", "Nhà Bè")

@Composable
internal fun CustomerAreaSheet(state: DiscoveryUiState, locationSource: LocationSource, onArea: (String) -> Unit, onLocation: (GeoPoint) -> Unit,
    areaNames: List<String> = customerAreas,
    areaHint: String = "Tìm theo tên khu vực hoặc quận, huyện quen thuộc.",
    onClearLocation: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf(false) }
    var gpsOff by remember { mutableStateOf(false) }
    val locate: () -> Unit = {
        scope.launch {
            busy = true; message = null; settings = false; gpsOff = false
            try {
                if (!locationSource.isEnabled()) {
                    gpsOff = true; message = "Vị trí trên điện thoại đang tắt. Bật vị trí rồi thử lại, hoặc chọn khu vực bên dưới."
                } else {
                    val point = locationSource.currentPoint()
                    if (point != null) onLocation(point)
                    else message = "Chưa lấy được vị trí. Thử lại ở nơi có tín hiệu tốt hơn hoặc chọn khu vực."
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: SecurityException) {
                settings = true; message = "Chưa có quyền truy cập vị trí. Bạn vẫn có thể chọn khu vực thủ công."
            } catch (_: Exception) { message = "Không lấy được vị trí lúc này. Bạn có thể chọn khu vực bên dưới." }
            finally { busy = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) locate() else { settings = true; message = "Bạn chưa cho phép dùng vị trí. Chọn khu vực bên dưới hoặc cấp quyền trong Cài đặt." }
    }
    LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.9f).navigationBarsPadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Khu vực nhận hộp", style = MaterialTheme.typography.headlineMedium) }
        item {
            OutlinedButton(onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (granted) locate() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(14.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else FoodRescueGlyph(FoodRescueIcon.COMPASS)
                Spacer(Modifier.width(10.dp)); Text(if (busy) "Đang tìm vị trí…" else "Dùng vị trí hiện tại")
            }
        }
        item { Text("Chỉ lấy vị trí khi bạn yêu cầu. Không theo dõi khi ứng dụng ở nền.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (state.point != null && onClearLocation != null) item { TextButton(onClick = onClearLocation) { Text("Ngừng dùng vị trí đã chọn") } }
        message?.let { note -> item {
            Text(note, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            if (settings || gpsOff) TextButton(onClick = {
                val intent = if (gpsOff) Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS) else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                context.startActivity(intent)
            }) { Text("Mở cài đặt") }
        } }
        item { HorizontalDivider(); Spacer(Modifier.height(14.dp)); Text("Chọn khu vực tại TP.HCM", style = MaterialTheme.typography.titleMedium) }
        item { OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Tìm khu vực") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) }
        item { Text(areaHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        val areas = areaNames.filter { it.searchKey().contains(query.searchKey()) }
        if (areas.isEmpty()) item { Text("Không tìm thấy khu vực. Thử tên khác.") }
        items(areas) { area ->
            Surface(onClick = { onArea(area) }, color = if (state.district == area) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(area, Modifier.weight(1f))
                    if (state.district == area) FoodRescueGlyph(FoodRescueIcon.CHECK, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
