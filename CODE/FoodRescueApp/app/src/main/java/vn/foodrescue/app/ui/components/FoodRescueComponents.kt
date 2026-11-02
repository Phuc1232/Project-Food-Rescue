package vn.foodrescue.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import vn.foodrescue.app.R
import vn.foodrescue.app.data.model.SurpriseBox
import vn.foodrescue.app.data.model.vnd
import vn.foodrescue.app.ui.theme.FoodRescueColors

enum class AppSymbol { SEARCH, HEART, PIN, BACK, LEAF, CLOSE }

@Composable
fun AppIcon(symbol: AppSymbol, label: String?, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp).semantics { if (label != null) contentDescription = label }) {
        val u = size.minDimension / 24f
        val stroke = Stroke(1.8f * u, cap = StrokeCap.Round)
        fun point(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, point(x,y), point(x2,y2), 1.8f*u, StrokeCap.Round)
        when (symbol) {
            AppSymbol.SEARCH -> { drawCircle(color, 7*u, point(10f,10f), style=stroke); line(15f,15f,21f,21f) }
            AppSymbol.BACK -> { line(5f,12f,20f,12f); line(5f,12f,11f,6f); line(5f,12f,11f,18f) }
            AppSymbol.CLOSE -> { line(6f,6f,18f,18f); line(6f,18f,18f,6f) }
            AppSymbol.PIN -> {
                val p=Path().apply { moveTo(12*u,22*u); cubicTo(5*u,14*u,3*u,11*u,5*u,6*u); cubicTo(8*u,-1*u,20*u,1*u,20*u,9*u); cubicTo(20*u,14*u,15*u,19*u,12*u,22*u) }
                drawPath(p,color,style=stroke); drawCircle(color,2.5f*u,point(12f,9f),style=stroke)
            }
            AppSymbol.HEART -> {
                val p=Path().apply { moveTo(12*u,21*u); cubicTo(-7*u,9*u,4*u,-4*u,12*u,6*u); cubicTo(20*u,-4*u,31*u,9*u,12*u,21*u); close() }
                drawPath(p,color,style=stroke)
            }
            AppSymbol.LEAF -> {
                val p=Path().apply { moveTo(4*u,19*u); cubicTo(0f,5*u,14*u,2*u,21*u,3*u); cubicTo(23*u,17*u,12*u,24*u,4*u,19*u); close() }
                drawPath(p,color,style=stroke); line(4f,21f,16f,9f)
            }
        }
    }
}

@Composable
fun FoodRescueButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick=onClick, modifier=modifier.heightIn(min=52.dp), enabled=enabled, shape=CircleShape,
        colors=ButtonDefaults.buttonColors(disabledContainerColor=FoodRescueColors.DisabledContainer, disabledContentColor=FoodRescueColors.OnDisabled)) {
        Text(text, style=MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun StatusBadge(text: String, modifier: Modifier = Modifier) {
    Surface(modifier=modifier, color=MaterialTheme.colorScheme.tertiaryContainer,
        contentColor=MaterialTheme.colorScheme.onTertiaryContainer, shape=CircleShape) {
        Text(text, Modifier.padding(horizontal=12.dp, vertical=6.dp), style=MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun BoxPhoto(box: SurpriseBox, modifier: Modifier = Modifier) {
    val resource = when (box.artwork) { 0 -> R.drawable.sample_bakery; 1 -> R.drawable.sample_meal; else -> R.drawable.sample_grocery }
    Image(painterResource(resource), contentDescription="Ảnh minh họa ${box.category.label.lowercase()}",
        modifier=modifier, contentScale=ContentScale.Crop)
}

@Composable
fun SurpriseBoxCard(box: SurpriseBox, favorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit) {
    Card(onClick=onClick, modifier=Modifier.fillMaxWidth(), shape=MaterialTheme.shapes.medium,
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface), elevation=CardDefaults.cardElevation(1.dp)) {
        Box {
            BoxPhoto(box, Modifier.fillMaxWidth().height(172.dp))
            StatusBadge("Còn ${box.remaining} hộp", Modifier.align(Alignment.TopStart).padding(12.dp))
            Surface(modifier=Modifier.align(Alignment.TopEnd).padding(8.dp), shape=CircleShape, color=MaterialTheme.colorScheme.surface) {
                IconToggleButton(checked=favorite,onCheckedChange={onFavorite()}) {
                    AppIcon(AppSymbol.HEART, if(favorite) "Bỏ lưu ${box.storeName}" else "Lưu ${box.storeName}")
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(box.storeName, Modifier.weight(1f), style=MaterialTheme.typography.titleMedium)
                Text("${box.distanceKm.toString().replace('.', ',')} km", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(box.name, style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Nhận ${box.pickup.lowercase()}", style=MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Giá trị tham chiếu", Modifier.weight(1f), style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(box.referenceValue.vnd(), style=MaterialTheme.typography.bodySmall, textDecoration=TextDecoration.LineThrough, color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(box.price.vnd(), style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold, color=MaterialTheme.colorScheme.primary)
            }
        }
    }
}
