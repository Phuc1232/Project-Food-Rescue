package com.foodrescue.app.feature.store.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.designsystem.component.map.*
import com.foodrescue.app.feature.store.domain.StoreApplication
import java.util.Locale

@Composable
internal fun StoreApplicationMapScreen(application: StoreApplication, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val location = application.store.profile.pickupLocation ?: return
    var retry by rememberSaveable { mutableIntStateOf(0) }
    var load by remember { mutableStateOf(MapLoadState.LOADING) }
    FoodRescuePage("Vị trí cửa hàng", onBack, modifier) {
        Text(application.store.profile.name, style = MaterialTheme.typography.titleLarge)
        Text(location.address, color = MaterialTheme.colorScheme.onSurfaceVariant)
        NativeLocationMap(listOf(LocationMapPin(application.storeId, location, application.store.profile.name)),
            application.storeId, 1, retry, 0, 0, listOf(location.latitude, location.longitude, 16.0, 0.0),
            {}, {}, { load = it }, Modifier.fillMaxWidth().height(340.dp))
        if (load == MapLoadState.LOADING) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (load == MapLoadState.ERROR) {
            Text("Không tải được bản đồ. Vị trí đã lưu vẫn được giữ nguyên.", color = MaterialTheme.colorScheme.error)
            OutlinedButton({ retry++ }) { Text("Tải lại bản đồ") }
        }
        Text(String.format(Locale.ROOT, "%.6f, %.6f", location.latitude, location.longitude),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PickupDirectionsButton(location, Modifier.fillMaxWidth())
    }
}
