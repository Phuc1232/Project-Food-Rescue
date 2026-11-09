package com.foodrescue.app.feature.auth.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.foodrescue.app.R

@Composable
internal fun AuthPreviewDialog(dialog: AuthDialog, onDismiss: () -> Unit) {
    val content = when (dialog) {
        AuthDialog.LOGIN_PREVIEW -> R.string.auth_login_preview
        AuthDialog.REGISTER_PREVIEW -> R.string.auth_register_preview
        AuthDialog.RESET_PREVIEW -> R.string.auth_reset_preview
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auth_preview_title)) },
        text = { Text(stringResource(content)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.auth_dialog_close)) }
        },
    )
}
