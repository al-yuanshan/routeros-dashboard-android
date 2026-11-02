package com.yuanshan.routeros.config

import android.content.Context
import android.content.SharedPreferences

/**
 * RouterOS 配置管理类
 */
class RouterOSConfig(private val context: Context) {

    // SharedPreferences 实例
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "routeros_config",
        Context.MODE_PRIVATE
    )

    private val TAG = "RouterOSConfig"

    // 配置键和默认值
    companion object {
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_PING_TARGET = "ping_target"
        const val KEY_REFRESH_INTERVAL = "refresh_interval"

        const val DEFAULT_HOST = "192.168.8.1"
        const val DEFAULT_PORT = 8728  // RouterOS API 默认端口
        const val DEFAULT_USERNAME = "admin"
        // 不内置任何凭据，首次使用时通过 Web 配置页填写。
        const val DEFAULT_PASSWORD = ""
        const val DEFAULT_PING_TARGET = "223.5.5.5"
        const val DEFAULT_REFRESH_INTERVAL = 1_000L
        const val MIN_REFRESH_INTERVAL = 100L

        const val KEY_WAN_DOWN_MBPS = "wan_down_mbps"
        const val KEY_WAN_UP_MBPS = "wan_up_mbps"
        const val MAX_BANDWIDTH_MBPS = 100_000

        // 旧版多主题时代的键，仅用于清理
        private const val LEGACY_KEY_DASHBOARD_STYLE = "dashboard_style"
    }

    init {
        if (prefs.contains(LEGACY_KEY_DASHBOARD_STYLE)) {
            prefs.edit().remove(LEGACY_KEY_DASHBOARD_STYLE).apply()
        }
    }

    // 主机名或 IP 地址
    var host: String
        get() = prefs.getString(KEY_HOST, DEFAULT_HOST) ?: DEFAULT_HOST
        set(value) = prefs.edit().putString(KEY_HOST, value.trim()).apply()

    // 端口号
    var port: Int
        get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_PORT, value.coerceIn(1, 65_535)).apply()

    // 用户名
    var username: String
        get() = prefs.getString(KEY_USERNAME, DEFAULT_USERNAME) ?: DEFAULT_USERNAME
        set(value) = prefs.edit().putString(KEY_USERNAME, value.trim()).apply()

    // 密码
    var password: String
        get() = prefs.getString(KEY_PASSWORD, DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    // 由 RouterOS 主动探测的公网目标
    var pingTarget: String
        get() = prefs.getString(KEY_PING_TARGET, DEFAULT_PING_TARGET)
            ?.trim()
            ?.ifBlank { DEFAULT_PING_TARGET }
            ?: DEFAULT_PING_TARGET
        set(value) = prefs.edit()
            .putString(KEY_PING_TARGET, value.trim().ifBlank { DEFAULT_PING_TARGET })
            .apply()

    // 刷新间隔
    var routerosRefreshInterval: Long
        get() = prefs.getLong(KEY_REFRESH_INTERVAL, DEFAULT_REFRESH_INTERVAL)
            .coerceAtLeast(MIN_REFRESH_INTERVAL)
        set(value) = prefs.edit()
            .putLong(KEY_REFRESH_INTERVAL, value.coerceAtLeast(MIN_REFRESH_INTERVAL))
            .apply()

    // 宽带签约带宽（Mbps），用于显示占用率；0 表示不显示
    var wanDownMbps: Int
        get() = prefs.getInt(KEY_WAN_DOWN_MBPS, 0).coerceIn(0, MAX_BANDWIDTH_MBPS)
        set(value) = prefs.edit()
            .putInt(KEY_WAN_DOWN_MBPS, value.coerceIn(0, MAX_BANDWIDTH_MBPS))
            .apply()

    var wanUpMbps: Int
        get() = prefs.getInt(KEY_WAN_UP_MBPS, 0).coerceIn(0, MAX_BANDWIDTH_MBPS)
        set(value) = prefs.edit()
            .putInt(KEY_WAN_UP_MBPS, value.coerceIn(0, MAX_BANDWIDTH_MBPS))
            .apply()
}
