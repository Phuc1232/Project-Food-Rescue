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
fun AuthFlow(onPreview: (() -> Unit)? = null) {
    val factory = remember { viewModelFactory { initializer { AuthViewModel(createAuthGateway()) } } }
    val vm: AuthViewModel = viewModel(factory = factory)
    val s by vm.state.collectAsStateWithLifecycle()
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
                                        s.errors[AuthFieldKey.NAME], !s.busy,
                                        options = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                                        actions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }))
                                    Spacer(Modifier.height(18.dp))
                                }
                                AuthField("Email", s.email, { vm.edit(AuthFieldKey.EMAIL, it) }, "ban@example.com",
                                    s.errors[AuthFieldKey.EMAIL], !s.busy,
                                    options = KeyboardOptions(keyboardType = KeyboardType.Email,
                                        imeAction = if (s.page == AuthPage.FORGOT) ImeAction.Done else ImeAction.Next),
                                    actions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }, onDone = { submit() }))
                                if (s.page != AuthPage.FORGOT) {
                                    Spacer(Modifier.height(18.dp))
                                    PasswordFields(s, vm, s.page == AuthPage.REGISTER, submit)
                                }
                                if (s.page == AuthPage.LOGIN) {
                                    TextButton(onClick = { vm.open(AuthPage.FORGOT) }, enabled = !s.busy, modifier = Modifier.align(Alignment.End)) {
                                        Text("Quên mật khẩu?")
                                    }
                                }
                                if (s.page == AuthPage.REGISTER) {
                                    Spacer(Modifier.height(16.dp))
                                    Text("Thông tin trước khi tạo tài khoản", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                    TextButton(onClick = { vm.legal(AuthPage.TERMS) }, enabled = !s.busy) { Text("Điều khoản sử dụng") }
                                    TextButton(onClick = { vm.legal(AuthPage.PRIVACY) }, enabled = !s.busy) { Text("Chính sách quyền riêng tư") }
                                }
                            }
                            AuthPage.VERIFY -> {
                                Text(s.email, fontWeight = FontWeight.SemiBold, color = colors.primary)
                                Spacer(Modifier.height(18.dp))
                                AuthField("Mã xác minh", s.code, { vm.edit(AuthFieldKey.CODE, it) }, "6 chữ số",
                                    s.errors[AuthFieldKey.CODE], !s.busy,
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
                                Text(if (s.loginComplete) "Bạn đã hoàn tất luồng đăng nhập trong bản xem thử."
                                    else "Bạn có thể quay lại màn hình đăng nhập để tiếp tục.", style = MaterialTheme.typography.bodyLarge)
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
        AuthPage.LOGIN -> "Chào mừng trở lại"
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
        AuthPage.REGISTER -> "Bắt đầu khám phá món ngon gần bạn."
        AuthPage.LOGIN -> "Đăng nhập để tiếp tục cùng FoodRescue."
        AuthPage.FORGOT -> "Nhập email của tài khoản. Nếu thông tin phù hợp, bạn sẽ nhận hướng dẫn khôi phục."
        AuthPage.VERIFY -> "Nhập mã 6 số để xác minh email bên dưới."
        AuthPage.NEW_PASSWORD -> "Chọn mật khẩu mới để bảo vệ tài khoản của bạn."
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
        KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }, onDone = { submit() }))
    if (confirmation) {
        Spacer(Modifier.height(8.dp))
        Text("15–128 ký tự. Bạn có thể dùng một cụm từ dễ nhớ.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        PasswordField(if (s.page == AuthPage.NEW_PASSWORD) "Nhập lại mật khẩu mới" else "Nhập lại mật khẩu", s.confirmation,
            { vm.edit(AuthFieldKey.CONFIRMATION, it) }, s.errors[AuthFieldKey.CONFIRMATION], !s.busy,
            ImeAction.Done, KeyboardActions(onDone = { submit() }))
    }
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit, issue: AuthIssue?, enabled: Boolean,
    action: ImeAction, actions: KeyboardActions) {
    var visible by remember { mutableStateOf(false) }
    AuthField(label, value, onChange, "Nhập mật khẩu", issue, enabled,
        options = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = action), actions = actions,
        transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailing = {
            TextButton(onClick = { visible = !visible }, enabled = enabled,
                modifier = Modifier.semantics { contentDescription = if (visible) "Ẩn $label" else "Hiện $label" }) {
                Text(if (visible) "Ẩn" else "Hiện", style = MaterialTheme.typography.labelMedium)
            }
        })
}

@Composable
private fun AuthField(label: String, value: String, onChange: (String) -> Unit, placeholder: String, issue: AuthIssue?,
    enabled: Boolean, options: KeyboardOptions, actions: KeyboardActions,
    transformation: VisualTransformation = VisualTransformation.None, trailing: (@Composable () -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Text(label, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        enabled = enabled, placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
        singleLine = true, isError = issue != null, shape = RoundedCornerShape(12.dp),
        visualTransformation = transformation, trailingIcon = trailing, keyboardOptions = options, keyboardActions = actions,
        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
            unfocusedBorderColor = colors.outlineVariant, focusedBorderColor = colors.primary))
    if (issue != null) {
        Spacer(Modifier.height(6.dp))
        Text(issue.message(), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall, color = colors.error)
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
