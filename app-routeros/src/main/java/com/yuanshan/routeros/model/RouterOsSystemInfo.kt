package com.yuanshan.routeros.model

/**
 * RouterOS 系统信息数据模型
 */
data class RouterOsSystemInfo(
    val identity: String = "",        // 路由器自定义名称
    val boardName: String = "",       // 主板名称
    val version: String = "",         // RouterOS 版本
    val uptime: String = "",          // 运行时间
    val cpu: CpuModel = CpuModel(), // CPU 信息
    val memory: MemoryModel = MemoryModel(), // 内存信息
    val disk: DiskModel = DiskModel(), // 磁盘信息
    val networks: List<NetworkModel> = emptyList(), // 网络接口信息列表
    val routingMetrics: RouterOsRoutingMetrics = RouterOsRoutingMetrics()
)

data class RouterOsRoutingMetrics(
    val activeConnections: Long? = null,
    val maxConnections: Long? = null,
    val latencyMs: Int? = null,
    val packetLossPercent: Int? = null
)


/**
 * CPU 信息数据模型
 */
data class CpuModel(
    val cpuLoad: Float = 0f,  // CPU 负载百分比 (0-100)
    val temperature: Float = 0f,  // CPU 温度
    val count: Int = 1  // CPU 核心数
)


/**
 * 磁盘信息数据模型
 */
data class DiskModel(
    val totalSpace: Long = 0L,  // 总空间 (bytes)
    val freeSpace: Long = 0L,   // 空闲空间 (bytes)
    val usedSpace: Long = 0L    // 已使用空间 (bytes)
) {
    val usagePercent: Float
        get() = if (totalSpace > 0) (usedSpace.toFloat() / totalSpace * 100) else 0f
}

/**
 * 内存信息数据模型
 */
data class MemoryModel(
    val totalMemory: Long = 0L,  // 总内存 (bytes)
    val freeMemory: Long = 0L,   // 空闲内存 (bytes)
    val usedMemory: Long = 0L    // 已使用内存 (bytes)
) {
    val usagePercent: Float
        get() = if (totalMemory > 0) (usedMemory.toFloat() / totalMemory * 100) else 0f
}

/**
 * 网络信息数据模型
 */
data class NetworkModel(
    val interfaceName: String = "",  // 接口名称
    val ipAddress: String = "",      // IP地址
    val rxBytes: Long = 0L,          // 接收字节数
    val txBytes: Long = 0L,          // 发送字节数
    val rxSpeed: Long = 0L,          // 接收速度 (bytes/s)
    val txSpeed: Long = 0L,          // 发送速度 (bytes/s)
    val running: Boolean = false     // 接口是否运行中
)
