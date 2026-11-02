package com.yuanshan.routeros.repository

import android.os.SystemClock
import android.util.Log
import com.yuanshan.routeros.api.RouterOsApiService
import com.yuanshan.routeros.model.CpuModel
import com.yuanshan.routeros.model.DiskModel
import com.yuanshan.routeros.model.MemoryModel
import com.yuanshan.routeros.model.RouterOsRoutingMetrics
import com.yuanshan.routeros.model.RouterOsSystemInfo
import kotlinx.coroutines.CancellationException

/**
 * RouterOS 数据仓库。
 *
 * 使用两条独立连接：主连接只做每轮必需的轻量查询；探测连接负责较慢的
 * Ping 和连接跟踪，避免它们阻塞主刷新。
 */
class RouterOsRepository {

    companion object {
        private const val TAG = "RouterOsRepository"
        private const val SLOW_DATA_INTERVAL_MS = 30_000L
        private const val PING_COUNT = 3
        const val DEFAULT_PING_TARGET = "223.5.5.5"
        private val VALID_PING_TARGET = Regex("""[A-Za-z0-9.:-]{1,253}""")
    }

    private data class Endpoint(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
    )

    private val lock = Any()
    private var endpoint: Endpoint? = null
    private var mainService: RouterOsApiService? = null
    private var probeService: RouterOsApiService? = null

    @Volatile
    private var pingTarget = DEFAULT_PING_TARGET

    // 以下缓存只由主刷新协程访问。
    private var ipByInterface: Map<String, String> = emptyMap()
    private var identity = ""
    private var temperature = 0f
    @Volatile
    private var slowDataAtMs = 0L
    private var interfaceSignature: List<Pair<String, Boolean>> = emptyList()

    fun configure(
        host: String,
        port: Int = 8728,
        username: String,
        password: String,
        pingTarget: String = DEFAULT_PING_TARGET,
    ) {
        updatePingTarget(pingTarget)
        val newEndpoint = Endpoint(host, port, username, password)
        val connectionChanged = synchronized(lock) { endpoint != newEndpoint }
        if (!connectionChanged) return

        closeConnection()
        synchronized(lock) {
            endpoint = newEndpoint
        }
    }

    /** 探测目标不是连接凭据，修改时无需断开 RouterOS API 连接。 */
    fun updatePingTarget(value: String) {
        pingTarget = value.trim()
            .takeIf { VALID_PING_TARGET.matches(it) }
            ?: DEFAULT_PING_TARGET
    }

    /**
     * 获取一轮系统指标。每轮固定 2 条命令；IP 地址、温度和设备名每 30 秒
     * 或接口状态变化（例如 PPPoE 重拨）时才刷新。
     */
    suspend fun getSystemInfo(
        routingMetrics: RouterOsRoutingMetrics,
    ): Result<RouterOsSystemInfo> = try {
        val api = service(probe = false)
        val resource = api.getSystemResource().firstOrNull()
            ?: throw IllegalStateException("RouterOS 未返回系统资源信息")
        val interfaces = api.getPppoeInterfaces()

        val signature = interfaces.map { it["name"].orEmpty() to (it["running"] == "true") }
        val now = SystemClock.elapsedRealtime()
        if (slowDataAtMs == 0L ||
            now - slowDataAtMs >= SLOW_DATA_INTERVAL_MS ||
            signature != interfaceSignature
        ) {
            ipByInterface = parseIpAddresses(api.getIpAddresses())
            temperature = parseTemperature(api.getHealth())
            identity = runCatching { api.getIdentity().firstOrNull()?.get("name") }
                .getOrNull()
                .orEmpty()
            slowDataAtMs = now
            interfaceSignature = signature
        }

        val totalMemory = resource["total-memory"]?.toLongOrNull() ?: 0L
        val freeMemory = resource["free-memory"]?.toLongOrNull() ?: 0L
        val totalHdd = resource["total-hdd-space"]?.toLongOrNull() ?: 0L
        val freeHdd = resource["free-hdd-space"]?.toLongOrNull() ?: 0L

        Result.success(
            RouterOsSystemInfo(
                identity = identity,
                boardName = resource["board-name"].orEmpty(),
                version = resource["version"].orEmpty(),
                uptime = resource["uptime"].orEmpty(),
                cpu = CpuModel(
                    cpuLoad = resource["cpu-load"]?.toFloatOrNull() ?: 0f,
                    temperature = temperature,
                    count = resource["cpu-count"]?.toIntOrNull() ?: 1,
                ),
                memory = MemoryModel(
                    totalMemory = totalMemory,
                    freeMemory = freeMemory,
                    usedMemory = (totalMemory - freeMemory).coerceAtLeast(0L),
                ),
                disk = DiskModel(
                    totalSpace = totalHdd,
                    freeSpace = freeHdd,
                    usedSpace = (totalHdd - freeHdd).coerceAtLeast(0L),
                ),
                networks = parsePppoeNetworks(interfaces, ipByInterface),
                routingMetrics = routingMetrics,
            )
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "获取系统信息失败: ${e.message}")
        Result.failure(e)
    }

    /** 在探测连接上采集连接跟踪和 Ping 指标；单项失败只影响该项。 */
    suspend fun probeRoutingMetrics(): RouterOsRoutingMetrics {
        val api = try {
            service(probe = true)
        } catch (e: IllegalStateException) {
            return RouterOsRoutingMetrics()
        }

        val (activeConnections, maxConnections) = try {
            parseConnectionTracking(api.getConnectionTracking())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "连接跟踪指标不可用: ${e.message}")
            null to null
        }

        val pingMetrics = try {
            parsePingMetrics(
                rows = api.ping(pingTarget, PING_COUNT),
                expectedCount = PING_COUNT,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Ping 指标不可用: ${e.message}")
            RouterOsRoutingMetrics()
        }

        return RouterOsRoutingMetrics(
            activeConnections = activeConnections,
            maxConnections = maxConnections,
            latencyMs = pingMetrics.latencyMs,
            packetLossPercent = pingMetrics.packetLossPercent,
        )
    }

    private fun service(probe: Boolean): RouterOsApiService = synchronized(lock) {
        val current = if (probe) probeService else mainService
        current ?: run {
            val target = endpoint ?: throw IllegalStateException("RouterOS 连接尚未配置")
            RouterOsApiService(target.host, target.port, target.username, target.password)
                .also { if (probe) probeService = it else mainService = it }
        }
    }

    /** 主连接出错后丢弃，下一轮重新建立；探测连接不受影响。 */
    fun resetMainConnection() {
        val old = synchronized(lock) { mainService.also { mainService = null } }
        old?.close()
        slowDataAtMs = 0L
    }

    fun closeConnection() {
        val (main, probe) = synchronized(lock) {
            (mainService to probeService).also {
                mainService = null
                probeService = null
            }
        }
        main?.close()
        probe?.close()
        slowDataAtMs = 0L
    }
}
