package com.dengao.noticetodo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Brand = Color(0xFF4C5BD4)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFE2FF),
    onPrimaryContainer = Color(0xFF10164A),
    secondary = Color(0xFF5B6078),
    background = Color(0xFFFBFBFF),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F2F8),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C2FF),
    onPrimary = Color(0xFF1B235E),
    primaryContainer = Color(0xFF333B75),
    onPrimaryContainer = Color(0xFFDFE2FF),
    secondary = Color(0xFFC3C7DD),
    background = Color(0xFF141218),
    surface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFF2A2830),
    error = Color(0xFFFFB4AB)
)

@Composable
fun NoticeTodoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
