package vn.foodrescue.app.ui.customer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal enum class CatalogIcon { SEARCH, PIN, FILTER, MAP, COMPASS, RECEIPT, PERSON, CLOCK, CHEVRON, BACK, CLOSE, CHECK, BAG, ARROW }
@Composable
internal fun CatalogGlyph(icon: CatalogIcon, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, description: String? = null) {
    Canvas(modifier.size(24.dp).then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.65f, cap = StrokeCap.Round)
            fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x,y), Offset(x2,y2), 1.65f, StrokeCap.Round)
            fun path(block: Path.() -> Unit) = drawPath(Path().apply(block), tint, style = stroke)
            when (icon) {
                CatalogIcon.SEARCH -> { drawCircle(tint,6.5f,Offset(10f,10f),style=stroke);line(15f,15f,21f,21f) }
                CatalogIcon.PIN -> { path { moveTo(12f,22f);cubicTo(8f,17f,4f,13f,4f,9f);cubicTo(4f,-1f,20f,-1f,20f,9f);cubicTo(20f,13f,16f,17f,12f,22f) };drawCircle(tint,2.5f,Offset(12f,9f),style=stroke) }
                CatalogIcon.FILTER -> { line(3f,6f,21f,6f);line(3f,18f,21f,18f);drawCircle(tint,2.5f,Offset(8f,6f),style=stroke);drawCircle(tint,2.5f,Offset(16f,18f),style=stroke) }
                CatalogIcon.MAP -> { path {moveTo(3f,5f);lineTo(9f,2f);lineTo(15f,5f);lineTo(21f,2f);lineTo(21f,19f);lineTo(15f,22f);lineTo(9f,19f);lineTo(3f,22f);close()};line(9f,2f,9f,19f);line(15f,5f,15f,22f) }
                CatalogIcon.COMPASS -> { drawCircle(tint,9f,Offset(12f,12f),style=stroke);path {moveTo(16f,8f);lineTo(14f,14f);lineTo(8f,16f);lineTo(10f,10f);close()} }
                CatalogIcon.RECEIPT -> { path {moveTo(5f,2f);lineTo(19f,2f);lineTo(19f,22f);lineTo(15f,20f);lineTo(12f,22f);lineTo(9f,20f);lineTo(5f,22f);close()};line(9f,8f,15f,8f);line(9f,12f,15f,12f) }
                CatalogIcon.PERSON -> { drawCircle(tint,4f,Offset(12f,7f),style=stroke);path {moveTo(4f,22f);cubicTo(4f,11f,20f,11f,20f,22f)} }
                CatalogIcon.CLOCK -> { drawCircle(tint,9f,Offset(12f,12f),style=stroke);line(12f,6f,12f,12f);line(12f,12f,16f,14f) }
                CatalogIcon.CHEVRON -> {line(7f,10f,12f,15f);line(12f,15f,17f,10f)}
                CatalogIcon.BACK -> {line(15f,5f,8f,12f);line(8f,12f,15f,19f)}
                CatalogIcon.CLOSE -> {line(6f,6f,18f,18f);line(18f,6f,6f,18f)}
                CatalogIcon.CHECK -> {line(5f,12f,10f,17f);line(10f,17f,19f,7f)}
                CatalogIcon.BAG -> { drawRoundRect(tint,Offset(4f,7f),Size(16f,15f),CornerRadius(2f),style=stroke);path {moveTo(8f,8f);lineTo(8f,6f);cubicTo(8f,0f,16f,0f,16f,6f);lineTo(16f,8f)} }
                CatalogIcon.ARROW -> {line(5f,12f,19f,12f);line(14f,7f,19f,12f);line(19f,12f,14f,17f)}
            }
        }
    }
}
