package com.yuanshan.core.config

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom

/**
 * 通用配置管理类
 */
class CommonConfig(private val context: Context) {

    // SharedPreferences 实例
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "common_config",
        Context.MODE_PRIVATE
    )

    private val TAG = "CommonConfig"

    // 配置键和默认值
    companion object {
        private const val KEY_SCREEN_BRIGHTNESS = "screen_brightness"
        private const val KEY_BRIGHTNESS_SCHEDULE_ENABLED = "brightness_schedule_enabled"
        private const val KEY_NIGHT_BRIGHTNESS = "night_brightness"
        private const val KEY_NIGHT_BRIGHTNESS_START = "night_brightness_start"
        private const val KEY_NIGHT_BRIGHTNESS_END = "night_brightness_end"
        private const val KEY_ENABLE_WEB_SERVER = "enable_web_server"
        private const val KEY_CARD_TRANSPARENCY = "card_transparency"
        private const val KEY_PRIMARY_TEXT_COLOR = "primary_text_color"
        private const val KEY_SECONDARY_TEXT_COLOR = "secondary_text_color"
        private const val KEY_WEB_ACCESS_CODE = "web_access_code"

        private const val DEFAULT_SCREEN_BRIGHTNESS = 100  // 默认最亮
        private const val DEFAULT_NIGHT_BRIGHTNESS = 20
        private const val DEFAULT_NIGHT_BRIGHTNESS_START = "22:00"
        private const val DEFAULT_NIGHT_BRIGHTNESS_END = "07:00"
        private const val DEFAULT_ENABLE_WEB_SERVER = true  // 默认启用 Web 服务

        /** 卡片透明度取值：AUTO 表示按壁纸亮度自动计算；手动取值 0（不透明）～MAX。 */
        const val CARD_TRANSPARENCY_AUTO = -1
        const val CARD_TRANSPARENCY_MAX = 90

        const val WEB_CONFIG_PORT = 8081
        const val GENERATED_ACCESS_CODE_LENGTH = 6
        const val MIN_ACCESS_CODE_LENGTH = 6
        const val MAX_ACCESS_CODE_LENGTH = 32

        private val accessCodeLock = Any()
        private val secureRandom = SecureRandom()

        fun isValidAccessCode(value: String): Boolean =
            value.length in MIN_ACCESS_CODE_LENGTH..MAX_ACCESS_CODE_LENGTH &&
                value.none { it.isWhitespace() || it.isISOControl() }

        fun isValidTime(value: String): Boolean =
            value.matches(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d"))

        /** 将 Web 端颜色统一为 #RRGGBB；空值表示继续使用自适应配色。 */
        fun normalizeHexColor(value: String?): String? = value
            ?.trim()
            ?.takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) }
            ?.uppercase()

        private fun generateAccessCode(): String =
            buildString {
                repeat(GENERATED_ACCESS_CODE_LENGTH) { append(secureRandom.nextInt(10)) }
            }
    }

    var screenBrightness: Int
        get() = prefs.getInt(KEY_SCREEN_BRIGHTNESS, DEFAULT_SCREEN_BRIGHTNESS)
        set(value) = prefs.edit().putInt(KEY_SCREEN_BRIGHTNESS, value.coerceIn(0, 100)).apply()

    var brightnessScheduleEnabled: Boolean
        get() = prefs.getBoolean(KEY_BRIGHTNESS_SCHEDULE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BRIGHTNESS_SCHEDULE_ENABLED, value).apply()

    var nightBrightness: Int
        get() = prefs.getInt(KEY_NIGHT_BRIGHTNESS, DEFAULT_NIGHT_BRIGHTNESS)
        set(value) = prefs.edit().putInt(KEY_NIGHT_BRIGHTNESS, value.coerceIn(1, 100)).apply()

    var nightBrightnessStart: String
        get() = prefs.getString(KEY_NIGHT_BRIGHTNESS_START, DEFAULT_NIGHT_BRIGHTNESS_START)
            ?.takeIf(::isValidTime) ?: DEFAULT_NIGHT_BRIGHTNESS_START
        set(value) {
            if (isValidTime(value)) prefs.edit().putString(KEY_NIGHT_BRIGHTNESS_START, value).apply()
        }

    var nightBrightnessEnd: String
        get() = prefs.getString(KEY_NIGHT_BRIGHTNESS_END, DEFAULT_NIGHT_BRIGHTNESS_END)
            ?.takeIf(::isValidTime) ?: DEFAULT_NIGHT_BRIGHTNESS_END
        set(value) {
            if (isValidTime(value)) prefs.edit().putString(KEY_NIGHT_BRIGHTNESS_END, value).apply()
        }

    /** 壁纸模式下卡片的透明度百分比；[CARD_TRANSPARENCY_AUTO] 为自动。 */
    var cardTransparency: Int
        get() = prefs.getInt(KEY_CARD_TRANSPARENCY, CARD_TRANSPARENCY_AUTO)
            .let { if (it < 0) CARD_TRANSPARENCY_AUTO else it.coerceAtMost(CARD_TRANSPARENCY_MAX) }
        set(value) {
            val safe = if (value < 0) CARD_TRANSPARENCY_AUTO else value.coerceAtMost(CARD_TRANSPARENCY_MAX)
            prefs.edit().putInt(KEY_CARD_TRANSPARENCY, safe).apply()
        }

    var primaryTextColor: String?
        get() = normalizeHexColor(prefs.getString(KEY_PRIMARY_TEXT_COLOR, null))
        set(value) = updateOptionalColor(KEY_PRIMARY_TEXT_COLOR, value)

    var secondaryTextColor: String?
        get() = normalizeHexColor(prefs.getString(KEY_SECONDARY_TEXT_COLOR, null))
        set(value) = updateOptionalColor(KEY_SECONDARY_TEXT_COLOR, value)

    var enableWebServer: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_WEB_SERVER, DEFAULT_ENABLE_WEB_SERVER)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_WEB_SERVER, value).apply()

    /**
     * Web 配置服务访问码。首次读取时随机生成并持久化，设备屏幕会在未连接时展示它。
     */
    val webAccessCode: String
        get() = synchronized(accessCodeLock) {
            prefs.getString(KEY_WEB_ACCESS_CODE, null)
                ?.takeIf { isValidAccessCode(it) }
                ?: generateAccessCode().also {
                    prefs.edit().putString(KEY_WEB_ACCESS_CODE, it).commit()
                }
        }

    /** 更新访问码；格式不合法时返回 false 且不做修改。 */
    fun updateWebAccessCode(value: String): Boolean {
        if (!isValidAccessCode(value)) return false
        synchronized(accessCodeLock) {
            prefs.edit().putString(KEY_WEB_ACCESS_CODE, value).commit()
        }
        return true
    }

    private fun updateOptionalColor(key: String, value: String?) {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            prefs.edit().remove(key).apply()
            return
        }
        normalizeHexColor(trimmed)?.let { prefs.edit().putString(key, it).apply() }
    }
}
