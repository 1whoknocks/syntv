package com.synocam.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3DDC84),
    onPrimary = Color(0xFF062012),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE7ECF3),
    surface = Color(0xFF121A2B),
    onSurface = Color(0xFFE7ECF3),
    surfaceVariant = Color(0xFF1B2740),
    error = Color(0xFFFF6B6B),
)

@Composable
fun SynoCamTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = DarkColors, content = content)
