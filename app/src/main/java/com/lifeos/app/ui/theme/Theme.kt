package com.lifeos.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// LifeOS brand colors, kept in one place.
private val Teal = Color(0xFF0E7C7B)
private val Ink = Color(0xFF0B0F14)

private val DarkColors = darkColorScheme(
    primary = Teal,
    background = Ink,
    surface = Ink,
)

private val LightColors = lightColorScheme(
    primary = Teal,
)

// Wrap the whole app in this so every screen shares the same look.
@Composable
fun LifeOSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
