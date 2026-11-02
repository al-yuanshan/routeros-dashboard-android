package com.yuanshan.routeros.viewmodel

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuanshan.routeros.model.NetworkModel
import com.yuanshan.routeros.model.RouterOsRoutingMetrics
import com.yuanshan.routeros.model.RouterOsSystemInfo
import com.yuanshan.routeros.repository.RouterOsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * RouterOS 监控 ViewModel
 */
class RouterOsViewModel : ViewModel() {

    companion object {
        private const val TAG = "RouterOsViewModel"
        private const val MIN_REFRESH_INTERVAL_MS = 100L
        private const val MAX_RETRY_INTERVAL_MS = 60_000L
        private const val ROUTING_PROBE_INTERVAL_MS = 15_000L

        /** 流量曲线覆盖的时间窗口。 */
        const val TRAFFIC_WINDOW_MS = 5 * 60_000L

        /** 延迟和连接数曲线保留的探测点数（15 秒一次，约 15 分钟）。 */
        const val PROBE_HISTORY_SIZE = 60

        private const val MIN_HISTORY_SIZE = 30
        private const val MAX_HISTORY_SIZE = 600
    }

    // RouterOS数据接口
    private val repository = RouterOsRepository()

    // 网速历史记录的最大数据点数，按刷新间隔换算成 TRAFFIC_WINDOW_MS 的时间窗口
    private val _historyCapacity = MutableStateFlow(MIN_HISTORY_SIZE)
    val historyCapacity: StateFlow<Int> = _historyCapacity.asStateFlow()
    private val maxHistorySize: Int
        get() = _historyCapacity.value

    // 每个接口的速率估算器
    private val rateEstimators = mutableMapOf<String, CounterRateEstimator>()

    // 网速历史数据 - 使用 Map 来存储每个接口的历史数据
    private val _speedHistory =
        MutableStateFlow<Map<String, Pair<List<Long>, List<Long>>>>(emptyMap())
    val speedHistory: StateFlow<Map<String, Pair<List<Long>, List<Long>>>> =
        _speedHistory.asStateFlow()

    // 当前网速 - Map<接口名, Pair<rxSpeed, txSpeed>>
    private val _currentSpeed = MutableStateFlow<Map<String, Pair<Long, Long>>>(emptyMap())
    val currentSpeed: StateFlow<Map<String, Pair<Long, Long>>> = _currentSpeed.asStateFlow()

    // 每次探测的延迟（成功时）和活跃连接数历史
    private val _latencyHistory = MutableStateFlow<List<Int>>(emptyList())
    val latencyHistory: StateFlow<List<Int>> = _latencyHistory.asStateFlow()
    private val _connectionHistory = MutableStateFlow<List<Long>>(emptyList())
    val connectionHistory: StateFlow<List<Long>> = _connectionHistory.asStateFlow()

    // UI 状态
    private val _systemInfo = MutableStateFlow(RouterOsSystemInfo())
    val systemInfo: StateFlow<RouterOsSystemInfo> = _systemInfo.asStateFlow()

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 错误信息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 连接状态
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // 最近一次成功刷新的时间（SystemClock.elapsedRealtime），从未成功时为 null
    private val _lastSuccessAtMs = MutableStateFlow<Long?>(null)
    val lastSuccessAtMs: StateFlow<Long?> = _lastSuccessAtMs.asStateFlow()

    // 由独立探测协程更新的 Ping / 连接跟踪指标
    @Volatile
    private var routingMetrics = RouterOsRoutingMetrics()

    // 自动刷新任务
    private var refreshJob: Job? = null
    private var probeJob: Job? = null
    private var refreshIntervalMs = 3000L  // 默认 3 秒刷新一次
    private var connectionConfig: ConnectionConfig? = null
    private var configuredPingTarget = RouterOsRepository.DEFAULT_PING_TARGET

    private data class ConnectionConfig(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
    )

    /** 当前生效的刷新间隔，用于判断数据是否过期。 */
    val effectiveRefreshIntervalMs: Long
        get() = refreshIntervalMs

    /**
     * 配置连接信息
     */
    fun configure(
        host: String,
        port: Int = 8728,
        username: String = "admin",
        password: String = "",
        pingTarget: String = "223.5.5.5",
        refreshIntervalMs: Long = 3000L
    ) {
        val newConnectionConfig = ConnectionConfig(host, port, username, password)
        val safeRefreshInterval = refreshIntervalMs.coerceAtLeast(MIN_REFRESH_INTERVAL_MS)
        val connectionChanged = connectionConfig != newConnectionConfig
        val pingTargetChanged = configuredPingTarget != pingTarget
        val refreshIntervalChanged = this.refreshIntervalMs != safeRefreshInterval

        if (connectionChanged) {
            stopAutoRefresh()
            resetCollectedData()
            repository.configure(host, port, username, password, pingTarget)
            connectionConfig = newConnectionConfig
            configuredPingTarget = pingTarget
            startDataRefresh(safeRefreshInterval)
            startRoutingProbe()
            return
        }

        if (pingTargetChanged) {
            configuredPingTarget = pingTarget
            repository.updatePingTarget(pingTarget)
            routingMetrics = RouterOsRoutingMetrics()
            _latencyHistory.value = emptyList()
        }

        if (refreshIntervalChanged) {
            updateRefreshInterval(safeRefreshInterval)
        } else if (refreshJob?.isActive != true) {
            startDataRefresh(safeRefreshInterval)
        }
        if (probeJob?.isActive != true) startRoutingProbe()
    }

    private fun resetCollectedData() {
        routingMetrics = RouterOsRoutingMetrics()
        _latencyHistory.value = emptyList()
        _connectionHistory.value = emptyList()
        // 连接切换后的计数器不属于同一采样序列，必须重新建立基线。
        rateEstimators.clear()
        _speedHistory.value = emptyMap()
        _currentSpeed.value = emptyMap()
    }

    /**
     * 刷新一次数据。由唯一的自动刷新协程串行调用，避免慢网络下任务重叠。
     */
    private suspend fun refreshOnce(): Boolean {
        val isFirstConnection = !_isConnected.value
        if (isFirstConnection) {
            _isLoading.value = true
        }
        _errorMessage.value = null

        val result = repository.getSystemInfo(routingMetrics)
        result.fold(
            onSuccess = { info ->
                _systemInfo.value = info
                _isConnected.value = true
                _lastSuccessAtMs.value = SystemClock.elapsedRealtime()
                clearError()
                updateSpeedHistory(info.networks)
            },
            onFailure = { error ->
                _isConnected.value = false
                _errorMessage.value = error.message
                    ?: "无法连接到 RouterOS 设备，请检查网络和连接配置"
                Log.w(TAG, "数据刷新失败，稍后重试: ${error.message}")
                repository.resetMainConnection()
            }
        )

        if (isFirstConnection) {
            _isLoading.value = false
        }
        return result.isSuccess
    }

    /**
     * 更新网速和历史曲线。速率由滑动窗口估算，首个样本不计入历史。
     */
    private fun updateSpeedHistory(networks: List<NetworkModel>) {
        val currentHistory = _speedHistory.value.toMutableMap()
        val currentSpeeds = mutableMapOf<String, Pair<Long, Long>>()
        val now = SystemClock.elapsedRealtime()
        val activeInterfaces = networks.mapTo(mutableSetOf()) { it.interfaceName }

        // 接口被删除或改名时及时释放对应历史，避免 Map 长期增长。
        currentHistory.keys.retainAll(activeInterfaces)
        rateEstimators.keys.retainAll(activeInterfaces)

        networks.forEach { network ->
            val name = network.interfaceName
            val estimator = rateEstimators.getOrPut(name) { CounterRateEstimator() }
            val rate = estimator.add(now, network.rxBytes, network.txBytes) ?: return@forEach
            val (rxHistory, txHistory) = currentHistory[name] ?: (emptyList<Long>() to emptyList())
            currentHistory[name] =
                (rxHistory + rate.first).takeLast(maxHistorySize) to
                    (txHistory + rate.second).takeLast(maxHistorySize)
            currentSpeeds[name] = rate
        }

        _speedHistory.value = currentHistory
        _currentSpeed.value = currentSpeeds
    }

    /**
     * 开始自动刷新数据
     */
    fun startAutoRefresh(intervalMs: Long = 3000L) {
        startDataRefresh(intervalMs)
    }

    private fun startDataRefresh(intervalMs: Long) {
        refreshJob?.cancel()
        updateRefreshInterval(intervalMs)

        refreshJob = viewModelScope.launch {
            var consecutiveFailures = 0
            while (isActive) {
                val success = refreshOnce()
                consecutiveFailures = if (success) 0 else (consecutiveFailures + 1).coerceAtMost(3)

                val retryMultiplier = 1L shl consecutiveFailures
                val nextDelay = if (success) {
                    refreshIntervalMs
                } else {
                    (refreshIntervalMs * retryMultiplier).coerceAtMost(MAX_RETRY_INTERVAL_MS)
                }
                delay(nextDelay)
            }
        }
        Log.i(TAG, "自动刷新已启动，间隔: ${refreshIntervalMs}ms")
    }

    private fun updateRefreshInterval(intervalMs: Long) {
        refreshIntervalMs = intervalMs.coerceAtLeast(MIN_REFRESH_INTERVAL_MS)
        _historyCapacity.value = (TRAFFIC_WINDOW_MS / refreshIntervalMs).toInt()
            .coerceIn(MIN_HISTORY_SIZE, MAX_HISTORY_SIZE)
    }

    /**
     * Ping 和连接跟踪走独立连接、独立协程，避免约 1 秒的探测阻塞主刷新。
     */
    private fun startRoutingProbe() {
        probeJob?.cancel()
        probeJob = viewModelScope.launch {
            while (isActive) {
                val metrics = repository.probeRoutingMetrics()
                routingMetrics = metrics
                _systemInfo.update { it.copy(routingMetrics = metrics) }
                metrics.latencyMs?.let { latency ->
                    _latencyHistory.update { (it + latency).takeLast(PROBE_HISTORY_SIZE) }
                }
                metrics.activeConnections?.let { count ->
                    _connectionHistory.update { (it + count).takeLast(PROBE_HISTORY_SIZE) }
                }
                delay(ROUTING_PROBE_INTERVAL_MS)
            }
        }
    }

    /**
     * 停止自动刷新
     */
    fun stopAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = null
        probeJob?.cancel()
        probeJob = null
        _isLoading.value = false
    }

    fun stopMonitoring() {
        stopAutoRefresh()
        repository.closeConnection()
        _isConnected.value = false
    }


    /**
     * 释放资源
     */
    override fun onCleared() {
        stopMonitoring()
        super.onCleared()
    }

    /**
     *  设置未知错误信息
     */

    fun unkonwError() {
        _errorMessage.value = "未知错误"
    }

    /**
     *设置连接错误信息
     */
    fun connectionError() {
        _errorMessage.value = "无法连接到RouterOS设备,请检查网络和连接配置"
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
