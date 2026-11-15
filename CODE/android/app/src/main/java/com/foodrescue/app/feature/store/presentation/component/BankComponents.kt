package com.foodrescue.app.feature.store.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.store.domain.*

@Composable
internal fun BankLogo(bank: PayoutBank?, modifier: Modifier = Modifier) {
    Surface(modifier, color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFFE8ECE8))) {
        Box(Modifier.size(width = 88.dp, height = 48.dp), contentAlignment = Alignment.Center) {
            val logo = bank?.let { payoutBankLogo(it.code) }
            if (logo != null) Image(painterResource(logo), null, Modifier.fillMaxSize().padding(3.dp), contentScale = ContentScale.Fit)
            else FoodRescueGlyph(FoodRescueIcon.BANK, tint = Color(0xFF00665E))
        }
    }
}

@Composable
internal fun PayoutAccountCard(account: StorePayoutAccount, banks: List<PayoutBank>, modifier: Modifier = Modifier) {
    StoreHeroCard("TÀI KHOẢN NHẬN TIỀN", account.maskedNumber, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(account.holder, style = MaterialTheme.typography.labelLarge, color = Color.White)
                Text(banks.findBank(account.bank)?.name ?: account.bank, style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCCE2D7))
            }
            BankLogo(banks.findBank(account.bank))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PayoutBankPicker(banks: List<PayoutBank>, selected: String, onChoose: (PayoutBank) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(banks, query) { banks.filter { it.matchesQuery(query) } }
    val imeVisible = WindowInsets.isImeVisible
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { if (!imeVisible) BottomSheetDefaults.DragHandle() }) {
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight(.96f).imePadding().padding(horizontal = 20.dp)) {
            val compact = maxHeight < 400.dp
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp)) {
                if (!imeVisible) Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Chọn ngân hàng", style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall)
                        if (!compact) Text("${banks.size} ngân hàng", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) { FoodRescueGlyph(FoodRescueIcon.CLOSE, description = "Đóng danh sách ngân hàng") }
                }
                OutlinedTextField(query, { query = it.take(100) }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Tìm ngân hàng") }, leadingIcon = { FoodRescueGlyph(FoodRescueIcon.SEARCH) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); keyboard?.hide() }),
                    shape = RoundedCornerShape(16.dp))
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (results.isEmpty()) item { StoreEmptyPanel(FoodRescueIcon.SEARCH, "Không tìm thấy ngân hàng", "Thử tên đầy đủ hoặc tên viết tắt khác.") }
                    items(results, key = { it.code }) { bank ->
                        val chosen = bank.matches(selected)
                        Surface(onClick = { onChoose(bank) }, shape = RoundedCornerShape(16.dp),
                            color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                BankLogo(bank, if (compact) Modifier.size(width = 64.dp, height = 36.dp) else Modifier)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(bank.name, style = MaterialTheme.typography.titleSmall)
                                    if (!compact) Text(bank.fullName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (chosen) FoodRescueGlyph(FoodRescueIcon.CHECK, tint = MaterialTheme.colorScheme.primary, description = "Ngân hàng đã chọn")
                            }
                        }
                    }
                }
            }
        }
    }
}
