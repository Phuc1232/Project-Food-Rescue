package com.foodrescue.app.feature.auth.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodrescue.app.R
import com.foodrescue.app.core.designsystem.component.FoodRescueBrand
import com.foodrescue.app.core.designsystem.component.FoodRescueButton
import com.foodrescue.app.core.designsystem.component.FoodRescuePasswordField
import com.foodrescue.app.core.designsystem.component.FoodRescueTextField
import com.foodrescue.app.core.designsystem.theme.FoodRescueTheme

@Composable
fun AuthFormScreen(
    state: AuthUiState,
    onEdit: (AuthField, String) -> Unit,
    onNavigate: (AuthPage) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    onLegal: (LegalDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val isRegister = state.page == AuthPage.REGISTER
    val isReset = state.page == AuthPage.FORGOT_PASSWORD
    val isNewPassword = state.page == AuthPage.NEW_PASSWORD
    val submit = {
        focus.clearFocus()
        keyboard?.hide()
        onSubmit()
    }
    val actions = KeyboardActions(
        onNext = { focus.moveFocus(FocusDirection.Down) },
        onDone = { submit() },
    )
    val headingRes = when (state.page) {
        AuthPage.REGISTER -> R.string.auth_register
        AuthPage.FORGOT_PASSWORD -> R.string.auth_forgot_password
        AuthPage.NEW_PASSWORD -> R.string.auth_new_password
        else -> R.string.auth_login
    }
    Box(
        modifier = modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = colors.surfaceContainer) {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_auth_back), stringResource(R.string.auth_back))
                    }
                }
                Spacer(Modifier.weight(1f))
                FoodRescueBrand()
            }
            Text(
                text = stringResource(headingRes),
                modifier = Modifier.semantics { heading() },
                fontSize = 27.sp,
                lineHeight = 33.sp,
                letterSpacing = (-.5).sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (isReset) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.auth_forgot_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(28.dp))
            if (isRegister) {
                FoodRescueTextField(
                    label = stringResource(R.string.auth_name),
                    value = state.form.name,
                    onValueChange = { onEdit(AuthField.NAME, it) },
                    placeholder = stringResource(R.string.auth_name_placeholder),
                    error = state.errors[AuthField.NAME]?.let { stringResource(it) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    keyboardActions = actions,
                )
                Spacer(Modifier.height(16.dp))
            }
            if (!isNewPassword) FoodRescueTextField(
                label = stringResource(R.string.auth_email),
                value = state.form.email,
                onValueChange = { onEdit(AuthField.EMAIL, it) },
                placeholder = stringResource(R.string.auth_email_placeholder),
                error = state.errors[AuthField.EMAIL]?.let { stringResource(it) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = if (isReset) ImeAction.Done else ImeAction.Next),
                keyboardActions = actions,
            )
            if (!isReset) {
                Spacer(Modifier.height(16.dp))
                FoodRescuePasswordField(
                    label = stringResource(R.string.auth_password),
                    value = state.form.password,
                    onValueChange = { onEdit(AuthField.PASSWORD, it) },
                    error = state.errors[AuthField.PASSWORD]?.let { stringResource(it) },
                    hint = if (isRegister || isNewPassword) stringResource(R.string.auth_password_hint) else null,
                    imeAction = if (isRegister || isNewPassword) ImeAction.Next else ImeAction.Done,
                    keyboardActions = actions,
                )
            }
            if (isRegister || isNewPassword) {
                Spacer(Modifier.height(16.dp))
                FoodRescuePasswordField(
                    label = stringResource(R.string.auth_confirm_password),
                    value = state.form.confirmation,
                    onValueChange = { onEdit(AuthField.CONFIRMATION, it) },
                    error = state.errors[AuthField.CONFIRMATION]?.let { stringResource(it) },
                    keyboardActions = actions,
                )
            }
            if (state.page == AuthPage.LOGIN) {
                TextButton(
                    onClick = { onNavigate(AuthPage.FORGOT_PASSWORD) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.auth_forgot_password))
                }
            }
            Spacer(Modifier.height(24.dp))
            FoodRescueButton(
                text = stringResource(when {
                    isReset -> R.string.auth_send_reset
                    isNewPassword -> R.string.auth_save_password
                    else -> headingRes
                }),
                onClick = submit,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isRegister) {
                Spacer(Modifier.height(8.dp))
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = {
                        focus.clearFocus()
                        keyboard?.hide()
                        onLegal(LegalDocument.TERMS)
                    }) {
                        Text(stringResource(R.string.auth_terms), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    TextButton(onClick = {
                        focus.clearFocus()
                        keyboard?.hide()
                        onLegal(LegalDocument.PRIVACY)
                    }) {
                        Text(stringResource(R.string.auth_privacy), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
            if (!isReset && !isNewPassword) {
                TextButton(
                    onClick = { onNavigate(if (isRegister) AuthPage.LOGIN else AuthPage.REGISTER) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                ) {
                    Text(stringResource(if (isRegister) R.string.auth_login_prompt else R.string.auth_register_prompt))
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Preview(name = "Login", widthDp = 384, heightDp = 832, showBackground = true)
@Composable
private fun LoginPreview() {
    FoodRescueTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AuthFormScreen(AuthUiState(page = AuthPage.LOGIN), { _, _ -> }, {}, {}, {}, {})
        }
    }
}

@Preview(name = "Register", widthDp = 384, heightDp = 832, showBackground = true)
@Preview(name = "Register · large text", widthDp = 320, heightDp = 568, fontScale = 1.5f)
@Composable
private fun RegisterPreview() {
    FoodRescueTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AuthFormScreen(AuthUiState(page = AuthPage.REGISTER), { _, _ -> }, {}, {}, {}, {})
        }
    }
}
