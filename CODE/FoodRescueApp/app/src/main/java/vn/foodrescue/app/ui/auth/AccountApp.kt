package vn.foodrescue.app.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import vn.foodrescue.app.BuildConfig
import vn.foodrescue.app.data.session.SessionRepository
import vn.foodrescue.app.ui.customer.*

/** Real account data and preview orders are deliberately separate until the order APIs exist. */
@Composable
fun AccountApp(repository: SessionRepository, startInPreview: Boolean = false) {
    val factory = remember(repository) { viewModelFactory { initializer { AccountViewModel(repository) } } }
    val vm: AccountViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val gateway = remember(repository) { RemoteAuthGateway(repository) }
    var preview by rememberSaveable { mutableStateOf(BuildConfig.DEBUG && startInPreview) }
    var editing by rememberSaveable { mutableStateOf(false) }
    if (BuildConfig.DEBUG && preview) {
        BackHandler { preview = false }
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Bản giao diện thử", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { preview = false }) { Text("Về tài khoản thật") }
                }
            }
            Box(Modifier.weight(1f)) { CustomerApp(startInPreview = true) }
        }
        return
    }
    if (state.loading) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.busy) CircularProgressIndicator()
                Text(if (state.busy) "Đang mở tài khoản…" else state.error ?: "Chưa kết nối được máy chủ.")
                if (!state.busy) Button(onClick = vm::reload) { Text("Thử lại") }
                if (BuildConfig.DEBUG && !state.busy) TextButton(onClick = { preview = true }) { Text("Xem giao diện thử") }
            }
        }
        return
    }
    val profile = state.profile
    if (profile == null) {
        editing = false
        key(state.epoch) {
            AuthFlow(onPreview = if (BuildConfig.DEBUG) ({ preview = true }) else null,
                gateway = gateway, instanceKey = "account-auth-${state.epoch}", onAuthenticated = vm::reload)
        }
        return
    }
    val customer = CustomerProfile(name = profile.name, email = profile.email, phone = profile.phone.orEmpty())
    if (editing) {
        BackHandler(!state.busy) { editing = false }
        EditProfileScreen(customer, { if (!state.busy) editing = false }, preview = false, busy = state.busy) { name, phone ->
            vm.save(name, phone) { editing = false }
        }
    } else Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (BuildConfig.DEBUG) TextButton(onClick = { preview = true }, modifier = Modifier.align(Alignment.End)) { Text("Xem giao diện thử") }
            Box(Modifier.weight(1f)) {
                ProfileScreen(customer, { editing = true }, {}, vm::logout, preview = false, busy = state.busy)
            }
        }
    }
    state.error?.let { message -> AlertDialog(onDismissRequest = vm::dismissError, title = { Text("Chưa thực hiện được") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = vm::dismissError) { Text("Đã hiểu") } }) }
}
