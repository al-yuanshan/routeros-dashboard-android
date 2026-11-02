package com.yuanshan.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,

    // 关键：把 background/surface 从默认“偏白”拉回到更中性的淡色，配合渐变背景更像玻璃雾化
    // 注意：真正的渐变来自 FreshGradientBackground，这里主要是避免 Material3 默认 surface 覆盖导致“白一块”
    background = Color(0x00FFFFFF),
    surface = Color(0x00FFFFFF),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF334155)
)

@Composable
fun MonitorTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
