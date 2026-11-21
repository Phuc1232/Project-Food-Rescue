package com.foodrescue.app.feature.auth.presentation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import com.foodrescue.app.core.designsystem.component.FoodRescuePage
import com.foodrescue.app.core.designsystem.component.FoodRescueButton

@Composable
fun AuthRoute(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = viewModel(),
    onAuthenticated: (name: String, email: String) -> Unit = { _, _ -> },
    onRegistered: suspend (name: String, email: String) -> Unit = { _, _ -> },
) {
    val state = viewModel.uiState
    val screenStates = rememberSaveableStateHolder()
    val activity = LocalActivity.current
    val window = activity?.window
    LaunchedEffect(state.otp?.expiresAtSeconds, viewModel) {
        if (state.otp != null) {
            while (true) {
                viewModel.tick()
                delay(1_000)
            }
        }
    }
    DisposableEffect(window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previousStatusBar = controller?.isAppearanceLightStatusBars
        val previousNavigationBar = controller?.isAppearanceLightNavigationBars
        onDispose {
            if (previousStatusBar != null) controller?.isAppearanceLightStatusBars = previousStatusBar
            if (previousNavigationBar != null) controller?.isAppearanceLightNavigationBars = previousNavigationBar
        }
    }
    SideEffect {
        window?.let {
            WindowCompat.getInsetsController(it, it.decorView).apply {
                isAppearanceLightStatusBars = state.page != AuthPage.WELCOME || state.legalDocument != null
                isAppearanceLightNavigationBars = true
            }
        }
    }
    BackHandler(
        enabled = state.page != AuthPage.WELCOME || state.dialog != null || state.legalDocument != null,
        onBack = viewModel::back,
    )
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = state.page to state.legalDocument,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 40 }) togetherWith fadeOut(tween(120))
            },
            label = "Auth page",
        ) { (page, document) ->
            screenStates.SaveableStateProvider(key = document?.name ?: page.name) {
                if (document != null) {
                    LegalScreen(
                        document = document,
                        onBack = viewModel::back,
                        onOpenDocument = viewModel::openLegal,
                    )
                } else when (page) {
                    AuthPage.WELCOME -> WelcomeScreen(
                        onLogin = { viewModel.navigate(AuthPage.LOGIN) },
                        onRegister = { viewModel.navigate(AuthPage.REGISTER) },
                    )
                    AuthPage.REGISTER_OTP, AuthPage.RESET_OTP -> AuthVerificationScreen(
                        state = state.copy(page = page),
                        onEdit = viewModel::edit,
                        onSubmit = viewModel::submit,
                        onResend = viewModel::resend,
                        onBack = viewModel::back,
                    )
                    AuthPage.REGISTER_SUCCESS -> RegisteredAccountContent(state.form.name, state.form.email,
                        onRegistered, { viewModel.navigate(AuthPage.LOGIN) }) {
                        AuthSuccessScreen(state.copy(page = page), { viewModel.navigate(AuthPage.LOGIN) })
                    }
                    AuthPage.RESET_SUCCESS -> AuthSuccessScreen(
                        state = state.copy(page = page),
                        onLogin = { viewModel.navigate(AuthPage.LOGIN) },
                    )
                    else -> AuthFormScreen(
                        state = state.copy(page = page),
                        onEdit = viewModel::edit,
                        onNavigate = viewModel::navigate,
                        onBack = viewModel::back,
                        onSubmit = viewModel::submit,
                        onLegal = viewModel::openLegal,
                    )
                }
            }
        }
        state.dialog?.let {
            AuthPreviewDialog(
                dialog = it,
                onDismiss = viewModel::dismissDialog,
                onConfirm = { viewModel.enterPreview(onAuthenticated) },
            )
        }
    }
}

/** Registration success is shown only after the account profile has been saved. */
@Composable
private fun RegisteredAccountContent(name: String, email: String,
    onRegister: suspend (String, String) -> Unit, onBack: () -> Unit, content: @Composable () -> Unit) {
    var complete by rememberSaveable(email) { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val register by rememberUpdatedState(onRegister)
    LaunchedEffect(email, name, attempt) {
        if (!complete) {
            error = false
            try { register(name, email); complete = true }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
        }
    }
    if (complete) content() else FoodRescuePage("Hoàn tất tài khoản", onBack) {
        if (error) {
            Text("Chưa hoàn tất được tài khoản. Vui lòng thử lại hoặc quay lại đăng nhập.")
            FoodRescueButton("Thử lại", { attempt++ })
        } else { CircularProgressIndicator(); Text("Đang lưu thông tin tài khoản…") }
    }
}
