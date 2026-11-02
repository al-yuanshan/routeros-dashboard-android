package com.yuanshan.core.utils

import kotlin.math.pow

/**
 * 格式化工具类
 */
object FormatUtils {

    /**
     * 格式化字节大小
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()

        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        return String.format("%.2f %s", value, units[digitGroups])
    }

    /**
     * 格式化速度 (bytes/s)
     */
    fun formatSpeed(bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0) return "0 B/s"

        val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
        val digitGroups = (Math.log10(bytesPerSecond.toDouble()) / Math.log10(1024.0)).toInt()

        val value = bytesPerSecond / 1024.0.pow(digitGroups.toDouble())
        return String.format("%.2f %s", value, units[digitGroups])
    }

    /**
     * 格式化百分比
     */
    fun formatPercent(percent: Float): String {
        return String.format("%.1f%%", percent)
    }

    /**
     * 格式化运行时间 - 只显示天数
     * 支持多种格式：
     * - "1w2d3h4m5s" (RouterOS 格式)
     * - "1:03:12.08" (SNMP TimeTicks 格式，天:小时:分钟.秒)
     */
    fun formatUptime(uptime: String): String {
        // 尝试解析 "天:小时:分钟.秒" 格式 (如 "1:03:12.08")
        if (uptime.contains(":")) {
            return try {
                val parts = uptime.split(":")
                if (parts.size >= 2) {
                    val days = parts[0].toIntOrNull() ?: 0
                    val hours = parts[1].toIntOrNull() ?: 0

                    if (days > 0) {
                        "${days}天${hours}小时"
                    } else {
                        "${hours}小时"
                    }
                } else {
                    uptime
                }
            } catch (e: Exception) {
                uptime
            }
        }

        // 原有的 RouterOS 格式解析 (1w2d3h4m5s)
        var totalDays = 0

        // 提取周数
        val weekRegex = """(\d+)w""".toRegex()
        weekRegex.find(uptime)?.let { match ->
            val weeks = match.groupValues[1].toIntOrNull() ?: 0
            totalDays += weeks * 7
        }

        // 提取天数
        val dayRegex = """(\d+)d""".toRegex()
        dayRegex.find(uptime)?.let { match ->
            val days = match.groupValues[1].toIntOrNull() ?: 0
            totalDays += days
        }

        return if (totalDays > 0) {
            "${totalDays}天"
        } else {
            // 如果没有天数，尝试提取小时数
            val hourRegex = """(\d+)h""".toRegex()
            hourRegex.find(uptime)?.let { match ->
                val hours = match.groupValues[1].toIntOrNull() ?: 0
                return "${hours}小时"
            }
            uptime
        }
    }

    /**
     * 将 uptimeSeconds（秒）格式化为"X天/小时/分钟"
     * 仅返回最大单位：天数不足一天时才返回小时数，不够一小时时才返回分钟数
     */
    fun formatUptimeSeconds(uptimeSeconds: Double): String {
        val totalSeconds = uptimeSeconds.toLong().coerceAtLeast(0)
        val days = totalSeconds / (24 * 3600)
        val hours = (totalSeconds % (24 * 3600)) / 3600
        val minutes = (totalSeconds % 3600) / 60

        return when {
            days > 0 -> "${days}天"
            hours > 0 -> "${hours}小时"
            else -> "${minutes}分钟"
        }
    }

    /**
     * 示例输入："11 days, 13:13:32.41"
     *
     * 解析规则：
     * - 先提取 days
     * - 再解析后面的 时:分:秒(.小数)
     *
     * 输出规则：与 [formatUptimeSeconds] 一致，只返回最大单位：
     * - >= 1 天："X天"
     * - 否则 >= 1 小时："X小时"
     * - 否则："X分钟"
     */
    fun formatSnmpUptime(timeticks: String): String {
        val raw = timeticks.trim()
        if (raw.isEmpty()) return ""

        // 兜底：如果传进来就是纯数字（部分实现会给 TimeTicks=百分之一秒），则沿用老逻辑
        raw.toLongOrNull()?.let { numeric ->
            return formatUptimeSeconds(numeric / 100.0)
        }

        // 解析 "11 days, 13:13:32.41"
        // days 可选；后面的 time 部分也可选
        val regex = Regex("""^\s*(?:(\d+)\s*days?,\s*)?(\d{1,2}):(\d{1,2}):(\d{1,2})(?:\.(\d+))?\s*$""")
        val m = regex.find(raw)
        if (m == null) {
            // 再兜底：尝试兼容 "1 day, 13:13:32.41" 或者 "0:12:33.00" 之外的格式
            return raw
        }

        val days = m.groupValues[1].toLongOrNull() ?: 0L
        val hours = m.groupValues[2].toLongOrNull() ?: 0L
        val minutes = m.groupValues[3].toLongOrNull() ?: 0L
        val seconds = m.groupValues[4].toLongOrNull() ?: 0L

        // 小数秒可忽略（和 formatUptimeSeconds 一样最后只输出最大单位）
        val totalSeconds = (days * 24 * 3600) + (hours * 3600) + (minutes * 60) + seconds

        return formatUptimeSeconds(totalSeconds.toDouble())
    }

    /**
     * 只保留版本号本体，去掉 "(stable)" 这类括号后缀。
     */
    fun formatVersion(versionRaw: String?): String {
        val v = versionRaw?.trim().orEmpty()
        if (v.isEmpty()) return ""

        // 去掉括号后缀："7.14.3 (stable)" -> "7.14.3"
        val withoutParen = v.substringBefore('(').trim()

        // 兜底：如果出现异常空白，压成单空格
        return withoutParen.replace(Regex("\\s+"), " ")
    }


    fun clampPercent(value: Double): Float = value.toFloat().coerceIn(0f, 100f)
}
