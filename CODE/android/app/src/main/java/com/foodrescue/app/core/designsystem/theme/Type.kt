package com.foodrescue.app.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun style(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = weight)

val FoodRescueTypography = Typography(
    headlineLarge = style(30, 38, FontWeight.Bold),
    headlineMedium = style(24, 32, FontWeight.Bold),
    titleLarge = style(20, 28, FontWeight.Bold),
    titleMedium = style(18, 24, FontWeight.SemiBold),
    titleSmall = style(16, 22, FontWeight.SemiBold),
    bodyLarge = style(16, 24),
    bodyMedium = style(14, 20),
    bodySmall = style(12, 18),
    labelLarge = style(14, 20, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.SemiBold),
)
