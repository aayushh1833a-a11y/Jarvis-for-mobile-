package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val JarvisColorScheme = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = Color(0xFF00262C),
    primaryContainer = Color(0xFF004E5C),
    onPrimaryContainer = JarvisCyanBright,
    secondary = JarvisViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2D1B69),
    onSecondaryContainer = JarvisVioletSoft,
    tertiary = JarvisEmerald,
    onTertiary = Color(0xFF002912),
    tertiaryContainer = Color(0xFF004D25),
    onTertiaryContainer = Color(0xFF80FFBA),
    background = JarvisDeepNavy,
    onBackground = JarvisTextPrimary,
    surface = JarvisSurfaceNavy,
    onSurface = JarvisTextPrimary,
    surfaceVariant = JarvisCardNavy,
    onSurfaceVariant = JarvisTextSecondary,
    outline = JarvisCyanDim,
    outlineVariant = Color(0xFF1F3858),
    error = JarvisCoral,
    onError = Color.White,
    errorContainer = Color(0xFF5A1219),
    onErrorContainer = Color(0xFFFFB4AB)
)

val JarvisShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = Typography,
        shapes = JarvisShapes,
        content = content
    )
}
