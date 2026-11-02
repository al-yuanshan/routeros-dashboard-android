package com.yuanshan.routeros.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * RouterOS 仪表盘统一色彩体系规范
 */
@Immutable
data class RouterOsColors(
    // 背景与渐变
    val bgRadialCenter: Color,
    val bgRadialEdge: Color,
    val cardBackground: Color = Color.Transparent,
    val cardBorder: Color = Color.Transparent,

    // 结构网格与分割线
    val gridDivider: Color,
    val subtleBorder: Color,

    // 系统/网络状态指示
    val statusOnline: Color,
    val statusOffline: Color,
    val statusBadgeBg: Color,
    val statusBadgeText: Color,

    // 网络流量监控相关色彩
    val downloadAccent: Color,
    val uploadAccent: Color,
    val chartGridLine: Color,
    val chartBaseAxis: Color,

    // 系统硬件监控指标 (CPU, RAM, DISK)
    val cpuMetric: Color,
    val ramMetric: Color,
    val diskMetric: Color,
    val progressTrack: Color,

    // 网络质量与连接指标
    val sparklineAccent: Color,
    val errorAccent: Color,
    val warningAccent: Color = Color(0xFFFF9F0A),
    val logoTint: Color?,
    val logoBadgeBg: Color,
    val logoBadgeText: Color,

    // 文字排印层次
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textTitle: Color,
    val tagBackground: Color,
    val tagText: Color
)

/**
 * 苹果设计风格色彩 (Apple Pro Dark Palette — macOS Sonoma / iPadOS Pro Bento Dark)
 * 深空钛金属底盘、光学折光描边、SF Pro 系统高对比动态色、层次分明
 */
val AppleRouterOsColors = RouterOsColors(
    bgRadialCenter = Color(0xFF141821), // 顶部极微冷调钛空灰中心
    bgRadialEdge = Color(0xFF0A0B0E),   // 深空黑底盘边缘 (防 OLED 拖影同时保持极致深邃)
    cardBackground = Color(0xD916181D), // Apple 磨砂钛灰卡片底色 (高实度防背景杂色干扰)
    cardBorder = Color(0x22FFFFFF),     // 顶部环境光折射微高光边

    gridDivider = Color(0x16FFFFFF),    // 8.5% 细致内敛分割线
    subtleBorder = Color(0x1EFFFFFF),

    statusOnline = Color(0xFF30D158),   // Apple System Green (Dark)
    statusOffline = Color(0xFFFF453A),  // Apple System Red (Dark)
    statusBadgeBg = Color(0x2430D158),
    statusBadgeText = Color(0xFF30D158),

    downloadAccent = Color(0xFF0A84FF), // Apple System Blue (下载主色)
    uploadAccent = Color(0xFFBF5AF2),   // Apple System Purple / Indigo (上传流向，与蓝绿状态严密区分)
    chartGridLine = Color(0x12FFFFFF),  // 7% 极简刻度虚线
    chartBaseAxis = Color(0x24FFFFFF),  // 14% 精细基线

    cpuMetric = Color(0xFF0A84FF),      // Apple System Blue (核心算力)
    ramMetric = Color(0xFF64D2FF),      // Apple System Cyan (内存吞吐)
    diskMetric = Color(0xFFBF5AF2),     // Apple System Purple (存储阵列)
    progressTrack = Color(0x1AFFFFFF),  // 10% 胶囊凹槽底轨

    sparklineAccent = Color(0xFF30D158),// Apple Green / Cyan
    errorAccent = Color(0xFFFF453A),    // Apple Red
    logoTint = Color(0xFFF5F5F7),       // 经典星光白铝色
    logoBadgeBg = Color(0x28FFFFFF),
    logoBadgeText = Color(0xFFF5F5F7),

    textPrimary = Color(0xFFF5F5F7),    // Apple Primary Label
    textSecondary = Color(0xFFAEAEB2),  // 次级文字（提高对比度，适配远距离观看）
    textMuted = Color(0xFF8E8E93),      // 辅助文字（原 #636366 在小屏上对比度不足）
    textTitle = Color(0xFFD1D1D6),      // 卡片标题
    tagBackground = Color(0x1AFFFFFF),
    tagText = Color(0xFFE5E5EA)
)

/** 壁纸模式·深色玻璃：次级文字整体提亮，保证在透明玻璃上仍清晰。 */
val AppleDarkGlassColors = AppleRouterOsColors.copy(
    textSecondary = Color(0xFFD1D1D6),
    textMuted = Color(0xFFB8B8BE),
    textTitle = Color(0xFFE5E5EA),
    chartGridLine = Color(0x1FFFFFFF),
    chartBaseAxis = Color(0x33FFFFFF),
    progressTrack = Color(0x26FFFFFF),
    gridDivider = Color(0x24FFFFFF),
)

/** 壁纸模式·浅色玻璃：亮背景上改用深色文字，强调色取 Apple 浅色模式的加深版本。 */
val AppleLightGlassColors = AppleRouterOsColors.copy(
    gridDivider = Color(0x24000000),
    subtleBorder = Color(0x24000000),
    statusOnline = Color(0xFF248A3D),
    statusOffline = Color(0xFFD70015),
    statusBadgeBg = Color(0x24248A3D),
    statusBadgeText = Color(0xFF248A3D),
    downloadAccent = Color(0xFF0058D0),
    uploadAccent = Color(0xFF8944AB),
    chartGridLine = Color(0x24000000),
    chartBaseAxis = Color(0x40000000),
    cpuMetric = Color(0xFF0058D0),
    ramMetric = Color(0xFF00658C),
    diskMetric = Color(0xFF8944AB),
    progressTrack = Color(0x24000000),
    sparklineAccent = Color(0xFF248A3D),
    errorAccent = Color(0xFFD70015),
    warningAccent = Color(0xFFB25000),
    logoTint = Color(0xFF1C1C1E),
    logoBadgeBg = Color(0x1F000000),
    logoBadgeText = Color(0xFF1C1C1E),
    textPrimary = Color(0xFF1C1C1E),
    textSecondary = Color(0xFF3A3A3C),
    textMuted = Color(0xFF48484A),
    textTitle = Color(0xFF1C1C1E),
    tagBackground = Color(0x1F000000),
    tagText = Color(0xFF1C1C1E),
)

val LocalRouterOsColors = staticCompositionLocalOf { AppleRouterOsColors }

/**
 * 便捷获取当前 RouterOS 主题颜色的对象
 */
object RouterOsTheme {
    val colors: RouterOsColors
        @Composable
        get() = LocalRouterOsColors.current
}

/**
 * RouterOS 主题 Provider Composable
 */
@Composable
fun RouterOsThemeProvider(
    colors: RouterOsColors,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalRouterOsColors provides colors) {
        content()
    }
}
