package vn.foodrescue.app.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import vn.foodrescue.app.R
import vn.foodrescue.app.ui.welcome.WelcomeScreen

@Composable
fun AuthFlow(onPreview: (() -> Unit)? = null, gateway: AuthGateway? = null, instanceKey: String = "preview-auth", onAuthenticated: () -> Unit = {}) {
    val factory = remember(gateway) { viewModelFactory { initializer { AuthViewModel(gateway ?: createAuthGateway()) } } }
    val vm: AuthViewModel = viewModel(key = instanceKey, factory = factory)
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.loginComplete) { if (s.loginComplete && !vm.isPreview) onAuthenticated() }
    BackHandler(s.page != AuthPage.WELCOME) { vm.back() }
    AnimatedContent(s.page, modifier = Modifier.fillMaxSize(), transitionSpec = {
        (fadeIn(tween(180, delayMillis = 90)) + slideInVertically(tween(240, delayMillis = 90)) { it / 60 })
            .togetherWith(fadeOut(tween(90)))
    }, label = "authNavigation") { page ->
        if (page == AuthPage.WELCOME) {
            WelcomeScreen(onLogin = { vm.open(AuthPage.LOGIN) }, onRegister = { vm.open(AuthPage.REGISTER) }, onPreview = onPreview)
        } else {
            AuthScreen(s.copy(page = page), vm, onPreview)
        }
    }
}

@Composable
private fun AuthScreen(s: AuthUiState, vm: AuthViewModel, onPreview: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val submit: () -> Unit = {
        if (AuthValidation.errors(s).isEmpty()) { focus.clearFocus(); keyboard?.hide() }
        vm.submit()
    }
    Scaffold(containerColor = colors.background) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding(), contentAlignment = Alignment.TopCenter) {
            key(s.page) {
                Column(Modifier.widthIn(max = 480.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp)) {
                    AuthHeader { vm.back() }
                    if (s.page == AuthPage.TERMS || s.page == AuthPage.PRIVACY) {
                        LegalDocument(s.page)
                    } else {
                        if (vm.isPreview) {
                            Surface(color = colors.surfaceContainer, shape = RoundedCornerShape(10.dp)) {
                                Text("Bản xem thử · Không gửi email, không tạo tài khoản thật",
                                    Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(20.dp))
                        }
                        AuthHeading(s)
                        Spacer(Modifier.height(24.dp))
                        when (s.page) {
                            AuthPage.REGISTER, AuthPage.LOGIN, AuthPage.FORGOT -> {
                                if (s.page == AuthPage.REGISTER) {
                                    AuthField("Họ và tên", s.name, { vm.edit(AuthFieldKey.NAME, it) }, "Tên của bạn",
                                        s.errors[AuthFieldKey.NAME]?.message(), !s.busy,
                                        options = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                                        actions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }))
                                    Spacer(Modifier.height(16.dp))
                                }
                                AuthField("Email", s.email, { vm.edit(AuthFieldKey.EMAIL, it) }, "ban@example.com",
                                    s.errors[AuthFieldKey.EMAIL]?.message(), !s.busy,
                                    options = KeyboardOptions(keyboardType = KeyboardType.Email,
                                        imeAction = if (s.page == AuthPage.FORGOT) ImeAction.Done else ImeAction.Next),
                                    actions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }, onDone = { submit() }))
                                if (s.page != AuthPage.FORGOT) {
                                    Spacer(Modifier.height(16.dp))
                                    PasswordFields(s, vm, s.page == AuthPage.REGISTER, submit)
                                }
                                if (s.page == AuthPage.LOGIN) {
                                    TextButton(onClick = { vm.open(AuthPage.FORGOT) }, enabled = !s.busy, modifier = Modifier.align(Alignment.End)) {
                                        Text("Quên mật khẩu?")
                                    }
                                }
                            }
                            AuthPage.VERIFY -> {
                                Text(s.email, fontWeight = FontWeight.SemiBold, color = colors.primary)
                                Spacer(Modifier.height(18.dp))
                                AuthField("Mã xác minh", s.code, { vm.edit(AuthFieldKey.CODE, it) }, "6 chữ số",
                                    s.errors[AuthFieldKey.CODE]?.message(), !s.busy,
                                    options = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                                    actions = KeyboardActions(onDone = { submit() }))
                                Spacer(Modifier.height(10.dp))
                                Text(if (s.expiresIn > 0) "Mã có hiệu lực trong ${s.expiresIn / 60}:${(s.expiresIn % 60).toString().padStart(2, '0')}"
                                    else "Mã đã hết hạn. Hãy yêu cầu mã mới.", style = MaterialTheme.typography.bodySmall,
                                    color = if (s.expiresIn > 0) colors.onSurfaceVariant else colors.error)
                                if (vm.isPreview && s.previewCode != null) {
                                    Spacer(Modifier.height(10.dp))
                                    Text("Mã dùng thử: ${s.previewCode}", style = MaterialTheme.typography.bodySmall, color = colors.primary)
                                }
                            }
                            AuthPage.NEW_PASSWORD -> PasswordFields(s, vm, true, submit)
                            AuthPage.COMPLETE -> {
                                Surface(shape = CircleShape, color = colors.primaryContainer, modifier = Modifier.size(64.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(painterResource(R.drawable.ic_auth_check), null, tint = colors.primary, modifier = Modifier.size(32.dp))
                                    }
                                }
                                Spacer(Modifier.height(20.dp))
                                Text(if (s.loginComplete && vm.isPreview) "Tiếp tục với dữ liệu mẫu."
                                    else if (s.loginComplete) "Đang mở tài khoản…"
                                    else "Đăng nhập để tiếp tục.", style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant)
                            }
                            else -> Unit
                        }
                        if (s.issue != null) {
                            Spacer(Modifier.height(16.dp))
                            Surface(color = colors.errorContainer, shape = RoundedCornerShape(12.dp)) {
                                Text(s.issue.message(), Modifier.fillMaxWidth().padding(14.dp).semantics { liveRegion = LiveRegionMode.Polite },
                                    color = colors.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (s.page == AuthPage.NEW_PASSWORD && s.issue == AuthIssue.CODE_EXPIRED)
                                TextButton(onClick = { vm.open(AuthPage.FORGOT) }) { Text("Xác minh lại") }
                        }
                        if (s.unverified) {
                            Spacer(Modifier.height(16.dp))
                            Surface(color = colors.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("Email của bạn chưa được xác minh.", fontWeight = FontWeight.SemiBold)
                                    Text("Hoàn tất xác minh để tiếp tục đăng nhập.", style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = vm::requestVerification, enabled = !s.busy) { Text("Xác minh email") }
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        val mainLabel = when (s.page) {
                            AuthPage.REGISTER -> "Tạo tài khoản"
                            AuthPage.LOGIN -> "Đăng nhập"
                            AuthPage.FORGOT -> "Gửi mã xác minh"
                            AuthPage.VERIFY -> "Xác minh"
                            AuthPage.NEW_PASSWORD -> "Lưu mật khẩu"
                            else -> if (s.loginComplete && onPreview != null) "Xem giao diện khách hàng" else "Đăng nhập"
                        }
                        Button(onClick = {
                            if (s.page == AuthPage.COMPLETE) {
                                if (s.loginComplete && onPreview != null && vm.isPreview) onPreview() else vm.open(AuthPage.LOGIN)
                            } else submit()
                        }, enabled = !s.busy && !(s.page == AuthPage.VERIFY && s.expiresIn <= 0),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(16.dp)) {
                            if (s.busy) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.primary)
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(if (s.busy) "Đang xử lý…" else mainLabel, style = MaterialTheme.typography.labelLarge)
                        }
                        if (s.page == AuthPage.REGISTER) {
                            Spacer(Modifier.height(8.dp))
                            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                TextButton(onClick = { vm.legal(AuthPage.TERMS) }, enabled = !s.busy,
                                    modifier = Modifier.semantics { contentDescription = "Điều khoản sử dụng" }) {
                                    Text("Điều khoản", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                }
                                TextButton(onClick = { vm.legal(AuthPage.PRIVACY) }, enabled = !s.busy,
                                    modifier = Modifier.semantics { contentDescription = "Chính sách quyền riêng tư" }) {
                                    Text("Quyền riêng tư", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                        when (s.page) {
                            AuthPage.VERIFY -> {
                                TextButton(onClick = vm::resend, enabled = !s.busy && s.resendIn == 0, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (s.resendIn > 0) "Gửi lại mã sau ${s.resendIn}s" else "Gửi lại mã")
                                }
                                TextButton(onClick = vm::changeEmail, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text("Đổi email") }
                            }
                            AuthPage.LOGIN, AuthPage.REGISTER -> TextButton(onClick = {
                                vm.open(if (s.page == AuthPage.REGISTER) AuthPage.LOGIN else AuthPage.REGISTER)
                            }, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) {
                                Text(if (s.page == AuthPage.REGISTER) "Đã có tài khoản? Đăng nhập" else "Chưa có tài khoản? Đăng ký")
                            }
                            else -> Unit
                        }
                        if (vm.isPreview && s.page != AuthPage.COMPLETE) {
                            Spacer(Modifier.height(16.dp))
                            PreviewScenario(s.scenario, !s.busy, vm::scenario)
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
private fun AuthHeader(onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = colors.surfaceContainer) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_auth_back), "Quay lại") }
        }
        Spacer(Modifier.weight(1f))
        Icon(painterResource(R.drawable.ic_foodrescue_mark), null, tint = colors.primary, modifier = Modifier.size(25.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.app_name), color = colors.primary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AuthHeading(s: AuthUiState) {
    val title = when (s.page) {
        AuthPage.REGISTER -> "Tạo tài khoản"
        AuthPage.LOGIN -> "Đăng nhập"
        AuthPage.FORGOT -> "Quên mật khẩu?"
        AuthPage.VERIFY -> "Xác minh email"
        AuthPage.NEW_PASSWORD -> "Đặt mật khẩu mới"
        else -> when {
            s.loginComplete -> "Đăng nhập thành công"
            s.purpose == AuthPurpose.RESET_PASSWORD -> "Đã đổi mật khẩu"
            s.purpose == AuthPurpose.VERIFY_LOGIN -> "Email đã xác minh"
            else -> "Tạo tài khoản thành công"
        }
    }
    Text(title, Modifier.semantics { heading() }, fontSize = 27.sp, lineHeight = 33.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-.5).sp)
    val subtitle = when (s.page) {
        AuthPage.FORGOT -> "Nhập email để nhận mã đặt lại mật khẩu."
        AuthPage.VERIFY -> "Nhập mã 6 số đã gửi đến"
        else -> null
    }
    if (subtitle != null) {
        Spacer(Modifier.height(12.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PasswordFields(s: AuthUiState, vm: AuthViewModel, confirmation: Boolean, submit: () -> Unit) {
    val focus = LocalFocusManager.current
    PasswordField(if (s.page == AuthPage.NEW_PASSWORD) "Mật khẩu mới" else "Mật khẩu", s.password,
        { vm.edit(AuthFieldKey.PASSWORD, it) }, s.errors[AuthFieldKey.PASSWORD], !s.busy,
        if (confirmation) ImeAction.Next else ImeAction.Done,
        KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }, onDone = { submit() }),
        hint = if (confirmation) "Tối thiểu ${AuthValidation.MIN_PASSWORD} ký tự" else null)
    if (confirmation) {
        Spacer(Modifier.height(16.dp))
        PasswordField("Nhập lại mật khẩu", s.confirmation,
            { vm.edit(AuthFieldKey.CONFIRMATION, it) }, s.errors[AuthFieldKey.CONFIRMATION], !s.busy,
            ImeAction.Done, KeyboardActions(onDone = { submit() }), visibilityLabel = "mật khẩu nhập lại")
    }
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit, issue: AuthIssue?, enabled: Boolean,
    action: ImeAction, actions: KeyboardActions, hint: String? = null, visibilityLabel: String = label.lowercase()) {
    var visible by remember { mutableStateOf(false) }
    val error = when (issue) {
        AuthIssue.PASSWORD_POLICY -> when {
            value.isBlank() -> "Nhập mật khẩu."
            value.length < AuthValidation.MIN_PASSWORD -> "Tối thiểu ${AuthValidation.MIN_PASSWORD} ký tự."
            else -> "Tối đa ${AuthValidation.MAX_PASSWORD} ký tự."
        }
        AuthIssue.CONFIRMATION -> if (value.isEmpty()) "Nhập lại mật khẩu." else "Mật khẩu chưa khớp."
        else -> issue?.message()
    }
    AuthField(label, value, onChange, "", error, enabled,
        options = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = action), actions = actions,
        hint = hint,
        transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailing = {
            IconButton(onClick = { visible = !visible }, enabled = enabled) {
                Icon(painterResource(if (visible) R.drawable.ic_password_hide else R.drawable.ic_password_show),
                    contentDescription = if (visible) "Ẩn $visibilityLabel" else "Hiện $visibilityLabel",
                    modifier = Modifier.size(22.dp))
            }
        })
}

@Composable
private fun AuthField(label: String, value: String, onChange: (String) -> Unit, placeholder: String, error: String?,
    enabled: Boolean, options: KeyboardOptions, actions: KeyboardActions,
    transformation: VisualTransformation = VisualTransformation.None, trailing: (@Composable () -> Unit)? = null,
    hint: String? = null) {
    val colors = MaterialTheme.colorScheme
    Text(label, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        enabled = enabled, placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        textStyle = MaterialTheme.typography.bodyLarge,
        singleLine = true, isError = error != null, shape = RoundedCornerShape(12.dp),
        visualTransformation = transformation, trailingIcon = trailing, keyboardOptions = options, keyboardActions = actions,
        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
            unfocusedBorderColor = colors.outlineVariant, focusedBorderColor = colors.primary))
    val supportingText = error ?: hint
    if (supportingText != null) {
        Spacer(Modifier.height(6.dp))
        Text(supportingText, Modifier.semantics { if (error != null) liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall, color = if (error != null) colors.error else colors.onSurfaceVariant)
    }
}

@Composable
private fun PreviewScenario(value: AuthScenario, enabled: Boolean, onSelect: (AuthScenario) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled) { Text("Tình huống thử: ${value.label}", style = MaterialTheme.typography.bodySmall) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            AuthScenario.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { expanded = false; onSelect(option) })
            }
        }
    }
}

@Composable
private fun LegalDocument(page: AuthPage) {
    Text(if (page == AuthPage.TERMS) "Điều khoản sử dụng" else "Chính sách quyền riêng tư",
        Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(12.dp))
    Text("Bản dự thảo · Chưa ban hành", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    (if (page == AuthPage.TERMS) termsDraft else privacyDraft).forEach { (title, body) ->
        Spacer(Modifier.height(24.dp))
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}
