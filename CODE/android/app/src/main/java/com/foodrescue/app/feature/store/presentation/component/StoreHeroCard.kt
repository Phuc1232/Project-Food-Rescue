package com.foodrescue.app.feature.store.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun StoreHeroCard(eyebrow: String, title: String, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {}) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF103D35), Color(0xFF00665E))))
        .drawBehind {
            val center = Offset(size.width * 1.04f, size.height * .06f)
            repeat(3) { i -> drawCircle(Color.White.copy(alpha = .06f), size.width * (.22f + i * .11f), center, style = Stroke(1.dp.toPx())) }
        }.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = Color(0xFFB9D9CB), letterSpacing = 1.sp)
        Text(title, style = MaterialTheme.typography.headlineLarge, color = Color.White)
        content()
    }
}
