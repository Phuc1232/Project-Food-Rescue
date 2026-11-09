package com.foodrescue.app.feature.complaint.presentation.component

import androidx.core.net.toUri
import android.widget.ImageView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.foodrescue.app.core.designsystem.component.FoodRescueCard
import com.foodrescue.app.feature.complaint.domain.ComplaintEvidence

@Composable
internal fun ComplaintEvidenceCard(evidence: ComplaintEvidence, modifier: Modifier = Modifier, onRemove: (() -> Unit)? = null) {
    var expanded by rememberSaveable(evidence.id) { mutableStateOf(false) }
    FoodRescueCard(modifier) {
        if (evidence.previewUri.isNotBlank()) EvidenceImage(evidence, Modifier.fillMaxWidth().height(150.dp))
        else Text("Ảnh hiện không khả dụng", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(evidence.name, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { expanded = true }, enabled = evidence.previewUri.isNotBlank()) { Text("Xem ảnh") }
            if (onRemove != null) TextButton(onClick = onRemove) { Text("Xóa ảnh") }
        }
    }
    if (expanded) AlertDialog(onDismissRequest = { expanded = false },
        title = { Text(evidence.name, style = MaterialTheme.typography.titleMedium) },
        text = { EvidenceImage(evidence, Modifier.fillMaxWidth().heightIn(max = 420.dp).aspectRatio(1f)) },
        confirmButton = { TextButton(onClick = { expanded = false }) { Text("Đóng ảnh") } })
}

@Composable
private fun EvidenceImage(evidence: ComplaintEvidence, modifier: Modifier = Modifier) {
    // The data layer prepares a bounded, account-private preview; never decode the original in composition.
    AndroidView(modifier = modifier, factory = { context -> ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
    } }, update = { view ->
        view.contentDescription = "Ảnh bằng chứng: " + evidence.name
        if (view.tag != evidence.previewUri) {
            view.setImageURI(evidence.previewUri.toUri())
            view.tag = evidence.previewUri
        }
    })
}
