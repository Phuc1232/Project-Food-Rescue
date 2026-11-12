package com.foodrescue.app.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val Colors = with(FoodRescueColors) {
    lightColorScheme(
        primary = Primary, onPrimary = OnPrimary,
        primaryContainer = PrimaryContainer, onPrimaryContainer = OnPrimaryContainer,
        secondary = Secondary, onSecondary = Surface,
        secondaryContainer = SecondaryContainer, onSecondaryContainer = OnSecondaryContainer,
        tertiary = Tertiary, onTertiary = Surface,
        tertiaryContainer = TertiaryContainer, onTertiaryContainer = OnTertiaryContainer,
        background = Background, onBackground = OnSurface,
        surface = Surface, onSurface = OnSurface, onSurfaceVariant = OnSurfaceVariant,
        surfaceVariant = SurfaceContainer, surfaceContainerLow = SurfaceContainerLow,
        surfaceContainer = SurfaceContainer, surfaceContainerHigh = SurfaceContainerHigh,
        outline = Outline, outlineVariant = OutlineVariant,
        error = Error, onError = Surface, errorContainer = ErrorContainer, onErrorContainer = Error,
        inverseSurface = InverseSurface, inverseOnSurface = InverseOnSurface,
        inversePrimary = InversePrimary, scrim = Scrim,
    )
}

/** Fixed light palette shared by every customer screen; no wallpaper-dependent colors. */
@Composable
fun FoodRescueTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = FoodRescueTypography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)),
        content = content,
    )
}
