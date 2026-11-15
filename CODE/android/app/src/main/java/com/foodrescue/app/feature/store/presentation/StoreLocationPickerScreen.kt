package com.foodrescue.app.feature.store.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.foodrescue.app.core.common.GeoPoint
import com.foodrescue.app.core.common.LocationSource
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.designsystem.component.map.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun StoreLocationPickerRoute(initialPoint: GeoPoint?, address: String, source: LocationSource,
    onBack: () -> Unit, onConfirm: (GeoPoint) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var viewport by rememberSaveable { mutableStateOf(listOf(initialPoint?.latitude ?: 10.777,
        initialPoint?.longitude ?: 106.699, if (initialPoint == null) 14.0 else 17.0, 0.0)) }
    var gpsPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var locateRequest by remember { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    var load by remember { mutableStateOf(MapLoadState.LOADING) }
    var moving by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf<String?>(null) }
    fun locate() {
        if (locating) return
        locating = true
        message = null
        settings = null
        scope.launch {
            try {
                if (!source.isEnabled()) {
                    message = "Vị trí đang tắt. Bạn có thể bật vị trí hoặc tự ghim trên bản đồ."
                    settings = Settings.ACTION_LOCATION_SOURCE_SETTINGS
                } else {
                    val point = source.currentPoint()
                    if (point == null || point.latitude !in -85.0..85.0) {
                        message = "Chưa lấy được vị trí. Thử lại hoặc tự ghim trên bản đồ."
                    } else {
                        gpsPoint = point
                        locateRequest++
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                            message = "Đang dùng vị trí gần đúng. Điều chỉnh ghim đến đúng cửa hàng."
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: SecurityException) {
                message = "Chưa có quyền vị trí. Bạn vẫn có thể tự ghim trên bản đồ."
                settings = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            } catch (_: Exception) { message = "Chưa lấy được vị trí. Bạn vẫn có thể tự ghim trên bản đồ."
            } finally { locating = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) locate() else {
            message = "Chưa có quyền vị trí. Bạn vẫn có thể tự ghim trên bản đồ."
            settings = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        }
    }
    LaunchedEffect(retry) { delay(20_000); if (load == MapLoadState.LOADING) load = MapLoadState.ERROR }
    val validPoint = viewport[0] in -85.0..85.0
    StoreLocationPickerScreen(address, load, moving || !validPoint, locating,
        if (!validPoint) "Di chuyển ghim về vùng bản đồ được hỗ trợ." else message, onBack,
        onLocate = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
            else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        },
        onSettings = settings?.let { action -> { runCatching {
            context.startActivity(Intent(action).apply {
                if (action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) data = "package:${context.packageName}".toUri()
            })
        }; Unit } },
        onRetry = { load = MapLoadState.LOADING; moving = false; retry++ },
        onConfirm = { if (load == MapLoadState.READY && !moving && !locating && validPoint) onConfirm(GeoPoint(viewport[0], viewport[1])) },
        mapContent = { modifier -> NativeLocationMap(emptyList(), null, 0, retry, 0, 0, viewport,
            {}, { viewport = it }, { load = it }, modifier, customerPoint = gpsPoint,
            locateRequest = locateRequest, mode = LocationMapMode.PICK, onMoving = { moving = it }) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StoreLocationPickerScreen(address: String, load: MapLoadState, moving: Boolean, locating: Boolean,
    message: String?, onBack: () -> Unit, onLocate: () -> Unit, onSettings: (() -> Unit)?, onRetry: () -> Unit,
    onConfirm: () -> Unit, mapContent: @Composable (Modifier) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val detailsWidth = maxWidth * .42f
        val details: @Composable () -> Unit = {
            PickerDetails(address, message, onSettings, onConfirm,
                load == MapLoadState.READY && !moving && !locating)
        }
        Scaffold(topBar = { TopAppBar(title = { Text("Vị trí cửa hàng") }, navigationIcon = {
            IconButton(onClick = onBack) { FoodRescueGlyph(FoodRescueIcon.BACK, description = "Quay lại hồ sơ") }
        }) }, bottomBar = {
            if (!landscape) Surface(shadowElevation = 6.dp) {
                Box(Modifier.navigationBarsPadding()) { details() }
            }
        }) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            mapContent(Modifier.fillMaxSize())
            if (load == MapLoadState.READY) FoodRescueGlyph(FoodRescueIcon.PIN,
                Modifier.align(Alignment.Center).offset(y = (-20).dp).size(44.dp), tint = MaterialTheme.colorScheme.primary)
            Surface(Modifier.align(Alignment.TopEnd).padding(12.dp), shape = MaterialTheme.shapes.large, shadowElevation = 3.dp) {
                TextButton(onClick = onLocate, enabled = !locating && load == MapLoadState.READY) {
                    if (locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(if (locating) "Đang định vị…" else "Vị trí hiện tại", Modifier.padding(horizontal = 6.dp))
                }
            }
            if (load != MapLoadState.READY) Surface(Modifier.align(Alignment.Center).padding(24.dp),
                shape = MaterialTheme.shapes.large, shadowElevation = 3.dp) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (load == MapLoadState.LOADING) { CircularProgressIndicator(); Text("Đang tải bản đồ…") }
                    else { Text("Chưa tải được bản đồ"); TextButton(onClick = onRetry) { Text("Thử lại") } }
                }
            }
        }
        if (landscape) Surface(Modifier.width(detailsWidth).fillMaxHeight(), shadowElevation = 6.dp) {
            Box(Modifier.verticalScroll(rememberScrollState())) { details() }
        }
        }
        }
    }
}

@Composable
private fun PickerDetails(address: String, message: String?, onSettings: (() -> Unit)?, onConfirm: () -> Unit, enabled: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(address, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(message ?: "Kéo hoặc chạm bản đồ để ghim đúng cửa hàng.", style = MaterialTheme.typography.bodySmall)
        onSettings?.let { TextButton(onClick = it) { Text("Mở cài đặt vị trí") } }
        FoodRescueButton("Xác nhận vị trí", onConfirm, Modifier.fillMaxWidth(), enabled = enabled)
    }
}
