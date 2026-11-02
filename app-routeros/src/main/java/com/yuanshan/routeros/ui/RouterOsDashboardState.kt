package com.yuanshan.routeros.ui

import androidx.compose.ui.graphics.ImageBitmap
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * 仪表盘渲染所需的全部数据。数值保持原始单位，格式化在界面层完成。
 */
data class RouterOsDashboardState(
    val deviceName: String = "RouterOS",
    val version: String = "",
    val connected: Boolean = false,
    val errorMessage: String? = null,
    val uptime: String = "--",
    val temperatureC: Float? = null,
    val accessHint: String? = null,
    val staleHint: String? = null,
    val traffic: TrafficState = TrafficState(),
    val quality: QualityState = QualityState(),
    val system: SystemState = SystemState(),
    val wan: WanState = WanState(),
)

data class TrafficState(
    val downloadBps: Long = 0L,
    val uploadBps: Long = 0L,
    val downloadTotalBytes: Long = 0L,
    val uploadTotalBytes: Long = 0L,
    val downloadHistory: List<Long> = emptyList(),
    val uploadHistory: List<Long> = emptyList(),
    /** 曲线窗口能容纳的点数；历史不足时曲线靠右对齐。 */
    val historyCapacity: Int = 30,
    val windowLabel: String = "近 5 分钟",
    /** 签约带宽（字节/秒），未配置为 null。 */
    val downloadCapBps: Long? = null,
    val uploadCapBps: Long? = null,
)

data class QualityState(
    val latencyMs: Int? = null,
    val packetLossPercent: Int? = null,
    val pingTarget: String = "",
    val latencyHistory: List<Int> = emptyList(),
    val activeConnections: Long? = null,
    val maxConnections: Long? = null,
    val connectionHistory: List<Long> = emptyList(),
)

data class SystemState(
    val cpuPercent: Int = 0,
    val cpuCores: Int = 1,
    val ramPercent: Int = 0,
    val ramUsedBytes: Long = 0L,
    val ramTotalBytes: Long = 0L,
    val diskPercent: Int = 0,
    val diskUsedBytes: Long = 0L,
    val diskTotalBytes: Long = 0L,
)

data class WanState(
    val interfaceName: String? = null,
    val ipAddress: String? = null,
    val running: Boolean = false,
)

/**
 * 用户自定义壁纸。
 *
 * @param image 原图，铺满整个背景。
 * @param blurred 预先模糊的小图，卡片内部按屏幕位置截取绘制，形成毛玻璃效果
 *   （Android 12 以下没有实时模糊，只能预先计算）。
 * @param luminance 平均相对亮度，0 为全黑、1 为全白，用于背景整体压暗。
 * @param luminanceMap 模糊小图逐像素亮度，用于判断每块玻璃背后的明暗。
 */
class DashboardWallpaper(
    val image: ImageBitmap,
    val blurred: ImageBitmap,
    val luminance: Float,
    val luminanceMap: LuminanceMap,
)

/** 指标告警等级：界面只在非 NORMAL 时使用醒目颜色。 */
enum class AlertLevel { NORMAL, WARNING, CRITICAL }

internal fun latencyLevel(latencyMs: Int?, packetLossPercent: Int?): AlertLevel = when {
    latencyMs == null -> AlertLevel.NORMAL
    (packetLossPercent ?: 0) >= 5 || latencyMs >= 150 -> AlertLevel.CRITICAL
    (packetLossPercent ?: 0) > 0 || latencyMs >= 80 -> AlertLevel.WARNING
    else -> AlertLevel.NORMAL
}

internal fun usageLevel(percent: Int, warning: Int = 70, critical: Int = 85): AlertLevel = when {
    percent >= critical -> AlertLevel.CRITICAL
    percent >= warning -> AlertLevel.WARNING
    else -> AlertLevel.NORMAL
}

internal fun temperatureLevel(celsius: Float?): AlertLevel = when {
    celsius == null -> AlertLevel.NORMAL
    celsius >= 75f -> AlertLevel.CRITICAL
    celsius >= 60f -> AlertLevel.WARNING
    else -> AlertLevel.NORMAL
}

/**
 * 把峰值向上取整到 1/2/5×10^n，作为曲线 Y 轴上限，保证刻度是整齐的数字。
 */
internal fun niceCeiling(value: Double): Double {
    if (value <= 0.0 || value.isNaN()) return 1.0
    val exponent = floor(log10(value))
    val base = 10.0.pow(exponent)
    val fraction = value / base
    val niceFraction = when {
        fraction <= 1.0 -> 1.0
        fraction <= 2.0 -> 2.0
        fraction <= 5.0 -> 5.0
        else -> 10.0
    }
    return niceFraction * base
}

/** 网速：十进制单位、三位有效数字，宽度稳定，例如 "8.42 MB/s"、"512 KB/s"。 */
internal fun formatRate(bytesPerSecond: Long): Pair<String, String> {
    val units = listOf("B/s", "KB/s", "MB/s", "GB/s")
    var value = bytesPerSecond.coerceAtLeast(0L).toDouble()
    var unit = 0
    while (value >= 999.5 && unit < units.lastIndex) {
        value /= 1000.0
        unit++
    }
    return formatSignificant(value) to units[unit]
}

/** 容量：二进制单位、三位有效数字，例如 "92.2 GB"。 */
internal fun formatSize(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB", "PB")
    var value = bytes.coerceAtLeast(0L).toDouble()
    var unit = 0
    while (value >= 1023.5 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return "${formatSignificant(value)} ${units[unit]}"
}

/** 底部窄栏容量：保留有效小数，但省略没有信息量的末尾零。 */
internal fun formatCompactSize(bytes: Long): String = formatSize(bytes)
    .replace(".00 ", " ")
    .replace(".0 ", " ")

private fun formatSignificant(value: Double): String = when {
    value == 0.0 -> "0"
    value < 10.0 -> String.format(Locale.US, "%.2f", value)
    value < 100.0 -> String.format(Locale.US, "%.1f", value)
    else -> String.format(Locale.US, "%.0f", value)
}

internal fun formatCount(value: Long): String = String.format(Locale.US, "%,d", value)
