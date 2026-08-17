package com.yizhidao.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object AppTheme {
    val parchmentTop = Color(0xFFF5EDE0)
    val parchmentBottom = Color(0xFFEBE5DB)
    val accent = Color(0xFF73382E)
    val cardFill = Color.White.copy(alpha = 0.72f)
    val fieldFill = Color.White
    val fieldStroke = Color.Black.copy(alpha = 0.12f)
    val yangRed = Color(0xFFBF3333)
    val movingHighlight = Color(0xFFFF9800).copy(alpha = 0.15f)

    val parchmentBrush = Brush.verticalGradient(
        colors = listOf(parchmentTop, parchmentBottom),
    )
}

private val LightColors = lightColorScheme(
    primary = AppTheme.accent,
    onPrimary = Color.White,
    secondary = AppTheme.accent,
    background = AppTheme.parchmentTop,
    surface = AppTheme.parchmentTop,
    onBackground = Color(0xFF1C140F),
    onSurface = Color(0xFF1C140F),
    surfaceVariant = AppTheme.cardFill,
)

@Composable
fun YizhidaoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
