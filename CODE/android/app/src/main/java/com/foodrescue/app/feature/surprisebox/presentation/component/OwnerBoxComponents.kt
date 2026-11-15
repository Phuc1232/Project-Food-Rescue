package com.foodrescue.app.feature.surprisebox.presentation.component

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.surprisebox.domain.*
import com.foodrescue.app.feature.surprisebox.presentation.label
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal fun boxDateTime(millis: Long): String = SimpleDateFormat("HH:mm · dd/MM/yyyy", Locale.forLanguageTag("vi-VN")).format(Date(millis))

@Composable
internal fun BatchStatusBadge(status: BatchStatus, modifier: Modifier = Modifier) {
    val active = status == BatchStatus.SELLING
    Surface(modifier, color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(8.dp)) {
        Text(status.label, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun BoxMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun <T> BoxChoice(label: String, selected: T, options: List<T>, text: (T) -> String, onSelect: (T) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Text(text(selected), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                FoodRescueGlyph(FoodRescueIcon.CHEVRON)
            }
            DropdownMenu(expanded, { expanded = false }) {
                options.forEach { value -> DropdownMenuItem(text = { Text(text(value)) }, onClick = { expanded = false; onSelect(value) }) }
            }
        }
    }
}

@Composable
internal fun BoxDateTimeField(label: String, value: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val context = LocalContext.current
    var dateDialog by remember { mutableStateOf<DatePickerDialog?>(null) }
    var timeDialog by remember { mutableStateOf<TimePickerDialog?>(null) }
    DisposableEffect(context) { onDispose { dateDialog?.dismiss(); timeDialog?.dismiss() } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = {
            val calendar = Calendar.getInstance().apply { timeInMillis = value; set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            dateDialog = DatePickerDialog(context, { _, year, month, day ->
                calendar.set(year, month, day)
                timeDialog = TimePickerDialog(context, { _, hour, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hour); calendar.set(Calendar.MINUTE, minute); onChange(calendar.timeInMillis)
                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).also { it.show() }
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).also { it.show() }
        }, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(16.dp)) {
            FoodRescueGlyph(FoodRescueIcon.CLOCK)
            Spacer(Modifier.width(12.dp))
            Text(boxDateTime(value), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
internal fun BoxStock(batch: SaleBatch, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LinearProgressIndicator(progress = { batch.committed.toFloat() / batch.quantity }, modifier = Modifier.fillMaxWidth().height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BoxMetric("Còn lại", batch.remaining.toString(), Modifier.weight(1f))
            BoxMetric("Đang giữ", batch.held.toString(), Modifier.weight(1f))
            BoxMetric("Đã bán", batch.sold.toString(), Modifier.weight(1f))
        }
    }
}
