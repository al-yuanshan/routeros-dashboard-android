package com.yuanshan.core.model.card

import androidx.annotation.DrawableRes

data class NetworkQualityData(
    val activeConnections: Long? = null,
    val maxConnections: Long? = null,
    val latencyMs: Int? = null,
    val packetLossPercent: Int? = null
)

/**
 * 服务器监控数据模型
 */
data class ServerMonitorData(
    val serverName: String = "DMIT EB WEE",
    val version: String = "Debian",
    val flag: String = "🇺🇸",
    @DrawableRes val flagResId: Int? = null, // 可选的旗帜图片资源ID
    val lastUpdate: String = "81 天 22 时 10 分 19 秒",
    val isOnline: Boolean = true,

    // CPU、内存、磁盘使用率 (0-100)
    val cpuUsage: Float = 0f,
    val cpuCores: Int? = null,
    val ramUsage: Float = 22f,
    val diskUsage: Float = 23f,

    // 详细信息
    val ramUsed: String = "211.9 MB",
    val diskUsed: String = "4.58 GB",

    // 网络速度
    val uploadSpeed: String = "54 B/s",
    val downloadSpeed: String = "108 B/s",

    // 流量统计
    val uploadTotal: String = "45.54 GB",
    val downloadTotal: String = "45.82 GB",

    // 流量历史数据（用于绘制图表）
    val uploadHistory: List<Float> = emptyList(),
    val downloadHistory: List<Float> = emptyList(),

    var diskWriteSpeed: String = "100MB/s",
    var diskReadSpeed: String = "120MB/s",

    var diskWriteTotal: String = "1.5TB",
    var diskReadTotal: String = "1.2TB",

    val diskWriteHistory: List<Float> = emptyList(),
    val diskReadHistory: List<Float> = emptyList(),

    // 系统信息标签数组（可选，如果为空则自动从 systemVersion 和 ipAddress 生成）
    // 例如：listOf("Ubuntu 22.04", "192.168.1.100")
    val systemInfoTags: List<String>? = null,

    // RouterOS 等网络设备可选的承载质量指标
    val networkQuality: NetworkQualityData? = null,
    val temperature: Float = 0f,
    val pingTarget: String? = null
)

