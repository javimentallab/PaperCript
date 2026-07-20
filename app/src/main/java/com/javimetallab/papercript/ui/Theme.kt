package com.javimetallab.papercript.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B1B33),
    secondary = Color(0xFF9DD1A5),
    background = Color(0xFF111119),
    surface = Color(0xFF1B1B2A),
    surfaceVariant = Color(0xFF262636),
    error = Color(0xFFFF8A80)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2D5FA8),
    secondary = Color(0xFF2E7D4F),
    background = Color(0xFFF6F6FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6E6EE)
)

/** Style for showing the code: monospaced, large and widely spaced. */
val CodeStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 26.sp,
    lineHeight = 38.sp,
    letterSpacing = 3.sp,
    fontWeight = FontWeight.Medium
)

@Composable
fun PaperCriptTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
