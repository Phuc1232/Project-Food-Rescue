package com.foodrescue.app.feature.auth.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.foodrescue.app.R
import com.foodrescue.app.core.designsystem.component.FoodRescueBrand
import com.foodrescue.app.core.designsystem.component.FoodRescueButton
import com.foodrescue.app.core.designsystem.component.FoodRescueTextField

/** Plain content: sample state renders without a ViewModel, timer or application session. */
@Composable
internal fun AuthVerificationScreen(
    state: AuthUiState,
    onEdit: (AuthField, String) -> Unit,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    val submit = { focus.clearFocus(); onSubmit() }
    AuthStepLayout(onBack = onBack, modifier = modifier) {
        Text(stringResource(R.string.auth_verify_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.auth_verify_email, state.form.email), style = MaterialTheme.typography.bodyMedium)
        state.otp?.let { otp ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = stringResource(R.string.auth_verification_code, otp.sampleCode),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        val remaining = ((state.otp?.expiresAtSeconds ?: 0) - state.nowSeconds).coerceAtLeast(0)
        val expired = remaining == 0L
        FoodRescueTextField(
            label = stringResource(R.string.auth_otp),
            value = state.form.otp,
            onValueChange = { onEdit(AuthField.OTP, it) },
            placeholder = "000000",
            error = state.errors[AuthField.OTP]?.let { stringResource(it) }
                ?: if (expired) stringResource(R.string.auth_error_otp_expired) else null,
            hint = stringResource(R.string.auth_code_expires, remaining),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        FoodRescueButton(stringResource(R.string.auth_verify_action), submit, Modifier.fillMaxWidth())
        val resendRemaining = ((state.otp?.resendAtSeconds ?: 0) - state.nowSeconds).coerceAtLeast(0)
        TextButton(onClick = onResend, enabled = resendRemaining == 0L, modifier = Modifier.fillMaxWidth()) {
            Text(if (resendRemaining == 0L) stringResource(R.string.auth_resend) else stringResource(R.string.auth_resend_countdown, resendRemaining))
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.auth_change_email))
        }
    }
}

@Composable
internal fun AuthSuccessScreen(state: AuthUiState, onLogin: () -> Unit, modifier: Modifier = Modifier) {
    val registered = state.page == AuthPage.REGISTER_SUCCESS
    AuthStepLayout(onBack = onLogin, modifier = modifier) {
        Text(
            stringResource(if (registered) R.string.auth_register_success else R.string.auth_reset_success),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(if (registered) R.string.auth_register_success_body else R.string.auth_reset_success_body))
        FoodRescueButton(stringResource(R.string.auth_return_login), onLogin, Modifier.fillMaxWidth())
    }
}

@Composable
private fun AuthStepLayout(
    onBack: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.auth_back)) }
                Spacer(Modifier.weight(1f))
                FoodRescueBrand()
            }
            content()
        }
    }
}
