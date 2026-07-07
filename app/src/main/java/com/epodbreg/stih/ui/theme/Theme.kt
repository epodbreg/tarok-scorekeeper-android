package com.epodbreg.stih.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Accent matches the iOS default tint
val StihAccent = Color(0xFF007AFF)
val StihGreen = Color(0xFF34C759)
val StihRed = Color(0xFFFF3B30)
val StihOrange = Color(0xFFFF9500)
val StihYellow = Color(0xFFFFCC00)
val StihPurple = Color(0xFFAF52DE)
val StihTeal = Color(0xFF30B0C7)
val StihBlue = Color(0xFF007AFF)
val StihIndigo = Color(0xFF5856D6)
val StihBrown = Color(0xFFA2845E)

private val LightColors = lightColorScheme(
    primary = StihAccent,
    onPrimary = Color.White,
    secondary = StihTeal,
    background = Color(0xFFF7F7F9),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDEDF0),
    error = StihRed,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    secondary = StihTeal,
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFF2C2C2E),
    error = Color(0xFFFF453A),
)

@Composable
fun StihTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

/** Score colour: green positive, red negative, default otherwise. */
@Composable
fun scoreColor(score: Int): Color = when {
    score > 0 -> StihGreen
    score < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurface
}
