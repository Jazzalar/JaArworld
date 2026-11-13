package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AuroraDarkColorScheme =
  darkColorScheme(
    primary = FocusMint,
    onPrimary = SkyTop,
    secondary = AuroraCyan,
    onSecondary = SkyTop,
    tertiary = WindowLit,
    background = SkyTop,
    onBackground = FrostTextPrimary,
    surface = Color(0xFF08162C),
    onSurface = FrostTextPrimary,
    surfaceVariant = Color(0xFF0F2442),
    onSurfaceVariant = FrostTextMuted,
    outline = FrostButtonBorder,
  )

@Composable
fun AuroraExpressTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = AuroraDarkColorScheme,
    typography = Typography,
    content = content,
  )
}
