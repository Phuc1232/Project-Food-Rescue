package com.foodrescue.app.feature.store.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.store.domain.*
import com.foodrescue.app.feature.store.presentation.component.*

@Composable
internal fun StorePayoutScreen(state: StoreManagementState, onEdit: (PayoutField, String) -> Unit, onBack: () -> Unit,
    onValidate: () -> Boolean, onSave: () -> Unit, modifier: Modifier = Modifier) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    var chooseBank by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    FoodRescuePage("Tài khoản nhận tiền", onBack, modifier, footer = {
        if (state.payoutErrors.isNotEmpty()) Text("Kiểm tra lại thông tin tài khoản.",
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
        FoodRescueButton(if (state.busy) "Đang lưu…" else "Lưu tài khoản",
            { focus.clearFocus(); keyboard?.hide(); if (onValidate()) confirm = true }, Modifier.fillMaxWidth(), enabled = !state.busy)
    }) {
        Text("Tiền về đúng nơi", style = MaterialTheme.typography.headlineMedium)
        Text("Tài khoản ngân hàng nhận các khoản chi trả của cửa hàng.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StoreErrorMessage(state.error)
        state.store?.payout?.let {
            PayoutAccountCard(it, state.banks)
        }
        FoodRescueCard {
            StoreSectionTitle(if (state.store?.payout == null) "Thông tin nhận tiền" else "Cập nhật tài khoản")
            Text("Ngân hàng", style = MaterialTheme.typography.labelLarge)
            Surface(onClick = { focus.clearFocus(); keyboard?.hide(); chooseBank = true }, enabled = !state.busy,
                color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    BankLogo(state.banks.findBank(state.payoutDraft.bank), Modifier.width(64.dp))
                    Text(state.banks.findBank(state.payoutDraft.bank)?.name ?: state.payoutDraft.bank.ifBlank { "Chọn ngân hàng" },
                        Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    FoodRescueGlyph(FoodRescueIcon.CHEVRON)
                }
            }
            if (PayoutField.BANK in state.payoutErrors) Text("Chọn ngân hàng nhận tiền.", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
            FoodRescueTextField("Tên chủ tài khoản", state.payoutDraft.holder, { onEdit(PayoutField.HOLDER, it) },
                enabled = !state.busy, error = if (PayoutField.HOLDER in state.payoutErrors) "Nhập tên cá nhân hoặc doanh nghiệp đứng tên tài khoản từ 2 đến 100 ký tự." else null)
            FoodRescueTextField("Số tài khoản", state.payoutDraft.number, { onEdit(PayoutField.NUMBER, it) },
                enabled = !state.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                error = if (PayoutField.NUMBER in state.payoutErrors) "Nhập số tài khoản từ 6 đến 19 chữ số." else null)
        }
        Text("Tên chủ tài khoản cần khớp với thông tin tại ngân hàng.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (chooseBank) PayoutBankPicker(state.banks, state.payoutDraft.bank,
        { focus.clearFocus(); keyboard?.hide(); onEdit(PayoutField.BANK, it.name); chooseBank = false }, { chooseBank = false })
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Lưu tài khoản nhận tiền?") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BankLogo(state.banks.findBank(state.payoutDraft.bank))
            StoreValue("Ngân hàng", state.banks.findBank(state.payoutDraft.bank)?.name ?: state.payoutDraft.bank)
            StoreValue("Số tài khoản", state.payoutDraft.number)
            StoreValue("Tên chủ tài khoản", state.payoutDraft.normalized().holder)
            Text("Kiểm tra đúng ngân hàng và chủ tài khoản trước khi xác nhận.")
        } },
        confirmButton = { TextButton(onClick = { confirm = false; onSave() }) { Text("Xác nhận lưu") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Kiểm tra lại") } })
}
