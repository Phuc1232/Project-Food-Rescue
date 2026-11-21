package com.foodrescue.app.feature.account.presentation

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/** One gate surrounds all role surfaces, including restored sessions. */
@Composable
internal fun AccountAccessGate(vm: AccountAccessViewModel, email: String, version: Long,
    onExit: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var attempt by remember { mutableIntStateOf(0) }
    val screens = rememberSaveableStateHolder()
    DisposableEffect(vm) { onDispose { vm.checking() } }
    LaunchedEffect(vm, email, version, lifecycle, attempt) {
        vm.checking()
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            do {
                vm.refresh(email, version)
                if (!vm.state.allowed) break
                delay(2_000)
            } while (true)
        }
    }
    if (vm.state.allowed) screens.SaveableStateProvider(email) { content() }
    else AccountAccessScreen(vm.state, { attempt++ }, onExit, modifier)
}
