package com.foodrescue.app.feature.surprisebox.presentation.component

import com.foodrescue.app.feature.surprisebox.domain.CatalogBox
import com.foodrescue.app.feature.surprisebox.domain.BoxCategory
import com.foodrescue.app.core.designsystem.component.FoodRescueGlyph
import com.foodrescue.app.core.designsystem.component.FoodRescueIcon
import com.foodrescue.app.core.util.money
import com.foodrescue.app.core.util.distance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodrescue.app.R

internal fun categoryPhoto(category: BoxCategory) = when (category) {
    BoxCategory.BAKERY -> R.drawable.welcome_bakery
    BoxCategory.MEALS -> R.drawable.discover_meal
    BoxCategory.PRODUCE, BoxCategory.MIXED -> R.drawable.discover_produce
    BoxCategory.DRINKS -> R.drawable.discover_drinks
}

internal fun CatalogBox.photo() = categoryPhoto(category)

@Composable
internal fun SurpriseBoxCard(box: CatalogBox, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = c.surface, border = BorderStroke(1.dp, c.outlineVariant.copy(alpha = .7f))) {
        Column {
            CatalogPhoto(box, Modifier.fillMaxWidth().height(if (LocalDensity.current.fontScale > 1.2f) 130.dp else 174.dp), showCount = true)
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StoreMonogram(box)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(box.store, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.25).sp)
                        Text(box.category.label, fontSize = 11.sp, lineHeight = 17.sp, color = c.onSurfaceVariant)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Giá trị tối thiểu", fontSize = 10.sp, lineHeight = 15.sp, color = c.onSurfaceVariant)
                        Text(money(box.minimumValue), fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
                    }
                    Text(money(box.price), fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-.7).sp, fontWeight = FontWeight.Bold, color = c.primary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FoodRescueGlyph(FoodRescueIcon.CLOCK, Modifier.size(14.dp), c.onSurfaceVariant)
                    Text("${box.pickup}", fontSize = 11.sp, color = c.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (box.distanceMeters >= 0) Text(distance(box.distanceMeters), fontSize = 11.sp, color = c.onSurfaceVariant)
                }
                HorizontalDivider(color = c.outlineVariant.copy(alpha = .6f))
            }
        }
    }
}

@Composable
internal fun CatalogPhoto(box: CatalogBox, modifier: Modifier = Modifier, showCount: Boolean = false) {
    Box(modifier) {
        Image(painterResource(box.photo()), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .4f)), startY = 100f)))
        if (showCount) Surface(Modifier.align(Alignment.TopStart).padding(12.dp), color = Color.White, shape = RoundedCornerShape(50)) {
            Text(if (box.isAvailable) "Còn ${box.remaining} hộp" else "Đã hết hộp", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 10.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
        Text("Ảnh minh họa", Modifier.align(Alignment.BottomEnd).padding(12.dp), color = Color.White, fontSize = 9.sp, lineHeight = 13.sp)
    }
}

@Composable
internal fun StoreMonogram(box: CatalogBox) {
    val c = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = c.primaryContainer) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Text(box.initials, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.primary) }
    }
}
