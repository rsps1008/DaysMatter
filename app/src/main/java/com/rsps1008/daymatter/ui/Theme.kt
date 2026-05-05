package com.rsps1008.daymatter.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DayMatterColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF4EA1FF),
    secondary = Color(0xFFF7B32B),
    tertiary = Color(0xFF7DD3FC),
    background = Color(0xFF0F172A),
    surface = Color(0xFF162033),
    onPrimary = Color.White,
    onSecondary = Color(0xFF0F172A),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
)

@Composable
fun DayMatterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DayMatterColors,
        content = content,
    )
}
