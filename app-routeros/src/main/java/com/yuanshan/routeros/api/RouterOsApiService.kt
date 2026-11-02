package com.yuanshan.routeros.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.legrange.mikrotik.ApiConnection
import me.legrange.mikrotik.ApiConnectionException
import java.io.Closeable
import javax.net.SocketFactory

/**
 * 单条 RouterOS API 连接（mikrotik-java）。
 *
 * mikrotik-java 写 socket 时没有加锁，因此同一连接上的连接、登录和命令
 * 都通过 [mutex] 串行执行；需要并行的任务应使用独立的实例。
 */
class RouterOsApiService(
    private val host: String,
    private val port: Int = 8728,
    private val username: String,
    private val password: String,
) : Closeable {

    companion object {
        private const val CONNECTION_TIMEOUT_MS = 5_000
        private const val COMMAND_TIMEOUT_MS = 10_000

        private val INTERFACE_FIELDS = listOf("name", "type", "rx-byte", "tx-byte", "running")
        private val RESOURCE_FIELDS = listOf(
            "board-name", "version", "uptime", "cpu-load", "cpu-count",
            "total-memory", "free-memory", "total-hdd-space", "free-hdd-space",
        )
    }

    private val mutex = Mutex()

    @Volatile
    private var connection: ApiConnection? = null

    /** 系统资源，只请求面板用到的字段。 */
    suspend fun getSystemResource(): List<Map<String, String>> =
        execute("/system/resource/print return ${RESOURCE_FIELDS.joinToString(",")}")

    /** PPPoE 接口计数器，由路由器端完成过滤，避免每次传回全部接口。 */
    suspend fun getPppoeInterfaces(): List<Map<String, String>> =
        execute(
            "/interface/print where type=pppoe-out or type=pppoe-in " +
                "return ${INTERFACE_FIELDS.joinToString(",")}"
        )

    /** 路由器自定义名称（/system identity），比板卡型号更易辨认。 */
    suspend fun getIdentity(): List<Map<String, String>> =
        execute("/system/identity/print")

    suspend fun getIpAddresses(): List<Map<String, String>> =
        execute("/ip/address/print return address,interface")

    /** 健康信息（温度等）；部分设备不支持，失败时返回空列表。 */
    suspend fun getHealth(): List<Map<String, String>> =
        try {
            execute("/system/health/print")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }

    suspend fun getConnectionTracking(): List<Map<String, String>> =
        execute("/ip/firewall/connection/tracking/print")

    /** 从 RouterOS 设备发起短 Ping 探测。target 需由调用方校验。 */
    suspend fun ping(target: String, count: Int = 3): List<Map<String, String>> =
        execute("/ping address=$target count=${count.coerceIn(1, 5)} interval=200ms")

    private suspend fun execute(command: String): List<Map<String, String>> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val active = connection?.takeIf { it.isConnected } ?: connect()
                try {
                    active.execute(command)
                } catch (e: Exception) {
                    // A timed-out command leaves mikrotik-java's session in an uncertain state.
                    // Drop that socket so the next refresh reconnects instead of timing out forever.
                    if (e.isRouterOsConnectionFailure() && connection === active) {
                        connection = null
                        runCatching { active.close() }
                    }
                    throw e
                }
            }
        }

    /** 调用方必须持有 [mutex]。 */
    private fun connect(): ApiConnection {
        closeQuietly()
        var newConnection: ApiConnection? = null
        var socketConnected = false
        try {
            newConnection = ApiConnection.connect(
                SocketFactory.getDefault(),
                host,
                port,
                CONNECTION_TIMEOUT_MS,
            )
            socketConnected = true
            newConnection.setTimeout(COMMAND_TIMEOUT_MS)
            newConnection.login(username, password)
            connection = newConnection
            return newConnection
        } catch (e: Exception) {
            runCatching { newConnection?.close() }
            val message = when {
                socketConnected && e.message?.contains("timed out", ignoreCase = true) == true ->
                    "RouterOS 登录超时 ($host:$port)：端口已连接，但未收到明文 API 响应。请确认 /ip service api 已启用，并使用 API 端口（通常 8728），不要填写 api-ssl 端口 8729"

                e.message?.contains("invalid", ignoreCase = true) == true ||
                    e.message?.contains("password", ignoreCase = true) == true ->
                    "RouterOS 登录失败：请检查用户名、密码和 API 登录权限"

                else ->
                    "无法连接 RouterOS ($host:$port)：${e.message ?: "未知错误"}"
            }
            throw IllegalStateException(message, e)
        }
    }

    private fun closeQuietly() {
        val old = connection
        connection = null
        // mikrotik-java 对已断开的连接调用 close 会抛异常，这里一律忽略。
        runCatching { old?.close() }
    }

    /** 可在任意线程调用；正在执行的命令会因 socket 关闭而失败。 */
    override fun close() {
        closeQuietly()
    }
}

/** mikrotik-java wraps command timeouts, so inspect the complete cause chain. */
internal fun Throwable.isRouterOsConnectionFailure(): Boolean =
    generateSequence(this) { it.cause }.any { it is ApiConnectionException }
