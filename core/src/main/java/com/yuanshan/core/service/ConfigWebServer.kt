package com.yuanshan.core.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.BatteryManager
import android.util.Base64
import android.util.Log
import androidx.annotation.DrawableRes
import com.google.gson.Gson
import com.yuanshan.core.broadcast.MainBroadcastController
import com.yuanshan.core.config.CommonConfig
import fi.iki.elonen.NanoHTTPD
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Web 配置项。`type = "password"` 的字段视为敏感字段：
 * 页面和 `/api/config` 永远不回传其值，提交空值表示保持不变。
 */
data class ConfigField(
    val key: String,
    val label: String,
    val value: Any,
    val type: String = "text",
    val options: List<ConfigOption> = emptyList()
)

data class ConfigOption(
    val value: String,
    val label: String
)

class ConfigWebServer(
    private val context: Context,
    port: Int,
    private val title: String,
    @DrawableRes private val logoResId: Int,
    private val fieldsProvider: () -> List<ConfigField>,
    private val updateConfig: (Map<String, Any>) -> Unit
) : NanoHTTPD(port) {

    companion object {
        const val CUSTOM_WALLPAPER_FILENAME = "custom_wallpaper.jpg"
        const val ACCESS_CODE_FIELD_KEY = "webAccessCode"
        private const val ASSET_DESKTOP_TEMPLATE = "web/config_desktop.html"
        private const val ASSET_MOBILE_TEMPLATE = "web/config_mobile.html"
        private const val ASSET_LOGIN_TEMPLATE = "web/login.html"
        private const val TAG = "ConfigWebServer"
        private const val SESSION_COOKIE = "monitor_session"
        private const val SESSION_TTL_MS = 12 * 60 * 60 * 1000L
        private const val MAX_SESSIONS = 16
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val BASE_LOCKOUT_MS = 30_000L
        private const val MAX_LOCKOUT_MS = 15 * 60_000L
        private const val SECRET_FIELD_TYPE = "password"

        fun getCustomWallpaperFile(context: Context): File =
            File(context.filesDir, CUSTOM_WALLPAPER_FILENAME)
    }

    private val gson = Gson()
    private val commonConfig = CommonConfig(context)
    private val secureRandom = SecureRandom()

    // 会话令牌 -> 过期时间；进程重启后需重新输入访问码。
    private val sessions = ConcurrentHashMap<String, Long>()
    private val loginLock = Any()
    private var failedAttempts = 0
    private var lockoutCount = 0
    private var lockedUntilMs = 0L

    fun startSafely() {
        // 提前生成访问码，确保屏幕和 Web 服务读到同一个值。
        commonConfig.webAccessCode
        runCatching { start() }
            .onFailure { Log.e(TAG, "启动配置服务失败: $listeningPort", it) }
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.POST && !isSameOrigin(session)) {
            return jsonError(Response.Status.FORBIDDEN, "跨站请求已拒绝")
        }

        when {
            session.uri == "/api/login" && session.method == Method.POST -> return login(session)
            session.uri == "/api/logout" && session.method == Method.POST -> return logout(session)
        }

        if (!isAuthenticated(session)) {
            return if (session.uri == "/" && session.method == Method.GET) {
                htmlResponse(renderLoginPage())
            } else {
                jsonError(Response.Status.UNAUTHORIZED, "需要访问码")
            }
        }

        return when {
            session.uri == "/" && session.method == Method.GET -> {
                val userAgent = session.headers["user-agent"] ?: session.headers["User-Agent"] ?: ""
                val isMobile = userAgent.contains("Android", ignoreCase = true) ||
                        userAgent.contains("iPhone", ignoreCase = true) ||
                        userAgent.contains("iPad", ignoreCase = true) ||
                        userAgent.contains("Mobile", ignoreCase = true)
                val html = if (isMobile) renderMobilePage() else renderDesktopPage()
                htmlResponse(html)
            }

            session.uri == "/api/config" && session.method == Method.GET ->
                jsonResponse(
                    allFields().associate { it.key to if (it.isSecret) "" else it.value }
                )

            session.uri == "/api/config" && session.method == Method.POST ->
                update(session)

            session.uri == "/api/status" -> jsonResponse(getDeviceStatus())

            session.uri == "/api/wallpaper" && session.method == Method.GET ->
                serveWallpaper()

            session.uri == "/api/wallpaper" && session.method == Method.POST ->
                handleWallpaperAction(session)

            session.uri == "/api/wallpaper" && session.method == Method.DELETE ->
                deleteWallpaper()

            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Not found"
            )
        }
    }

    private val ConfigField.isSecret: Boolean
        get() = type == SECRET_FIELD_TYPE

    private fun allFields(): List<ConfigField> =
        fieldsProvider() + ConfigField(
            key = ACCESS_CODE_FIELD_KEY,
            label = "Web 配置访问码（6-32 位，留空不修改）",
            value = "",
            type = SECRET_FIELD_TYPE
        )

    private fun login(session: IHTTPSession): Response {
        val payload = try {
            parseJsonBody(session)
        } catch (e: Exception) {
            return jsonError(Response.Status.BAD_REQUEST, "请求格式错误")
        }
        val submitted = payload["code"]?.toString().orEmpty()

        synchronized(loginLock) {
            val now = System.currentTimeMillis()
            if (now < lockedUntilMs) {
                val seconds = (lockedUntilMs - now + 999) / 1000
                return jsonError(Response.Status.TOO_MANY_REQUESTS, "尝试次数过多，请 $seconds 秒后再试")
            }

            if (!constantTimeEquals(submitted, commonConfig.webAccessCode)) {
                failedAttempts++
                if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                    failedAttempts = 0
                    // 每次锁定时长翻倍，限制在线暴力猜测。
                    val lockMs = (BASE_LOCKOUT_MS shl lockoutCount.coerceAtMost(5))
                        .coerceAtMost(MAX_LOCKOUT_MS)
                    lockoutCount++
                    lockedUntilMs = now + lockMs
                }
                return jsonError(Response.Status.UNAUTHORIZED, "访问码错误")
            }

            failedAttempts = 0
            lockoutCount = 0
        }

        val token = createSession()
        return jsonResponse(mapOf("success" to true)).apply {
            addHeader(
                "Set-Cookie",
                "$SESSION_COOKIE=$token; Path=/; Max-Age=${SESSION_TTL_MS / 1000}; HttpOnly; SameSite=Strict"
            )
        }
    }

    private fun logout(session: IHTTPSession): Response {
        sessionToken(session)?.let { sessions.remove(it) }
        return jsonResponse(mapOf("success" to true)).apply {
            addHeader("Set-Cookie", "$SESSION_COOKIE=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict")
        }
    }

    private fun createSession(): String {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { it.value <= now }
        if (sessions.size >= MAX_SESSIONS) {
            sessions.entries.minByOrNull { it.value }?.let { sessions.remove(it.key) }
        }
        val bytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        sessions[token] = now + SESSION_TTL_MS
        return token
    }

    private fun sessionToken(session: IHTTPSession): String? =
        session.cookies?.read(SESSION_COOKIE)?.takeIf { it.isNotBlank() }

    private fun isAuthenticated(session: IHTTPSession): Boolean {
        val token = sessionToken(session) ?: return false
        val expiresAt = sessions[token] ?: return false
        if (expiresAt <= System.currentTimeMillis()) {
            sessions.remove(token)
            return false
        }
        return true
    }

    /** 修改访问码后，只保留当前会话，其余已登录端需要重新验证。 */
    private fun revokeOtherSessions(session: IHTTPSession) {
        val current = sessionToken(session)
        sessions.keys.removeIf { it != current }
    }

    /** 浏览器带 Origin 时必须与 Host 一致，配合 SameSite Cookie 防止跨站提交。 */
    private fun isSameOrigin(session: IHTTPSession): Boolean {
        val origin = session.headers["origin"] ?: return true
        val host = session.headers["host"] ?: return false
        val originHost = runCatching {
            val uri = URI(origin)
            if (uri.port == -1) uri.host else "${uri.host}:${uri.port}"
        }.getOrNull() ?: return false
        return originHost.equals(host, ignoreCase = true)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    private fun parseJsonBody(session: IHTTPSession): Map<String, Any> {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val body = files["postData"].orEmpty()
        @Suppress("UNCHECKED_CAST")
        return gson.fromJson(body, Map::class.java) as? Map<String, Any> ?: emptyMap()
    }

    private fun serveWallpaper(): Response {
        val file = getCustomWallpaperFile(context)
        if (!file.exists() || file.length() <= 0L) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No custom wallpaper")
        }
        return try {
            newFixedLengthResponse(
                Response.Status.OK,
                "image/jpeg",
                FileInputStream(file),
                file.length()
            )
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "Error")
        }
    }

    private fun handleWallpaperAction(session: IHTTPSession): Response {
        return try {
            val payload = parseJsonBody(session)
            val action = payload["action"]?.toString().orEmpty()
            if (action == "clear") {
                return deleteWallpaper()
            }

            val rawDataUrl = payload["imageBase64"]?.toString().orEmpty()
            val base64Part = rawDataUrl.substringAfter("base64,", rawDataUrl).trim()
            if (base64Part.isEmpty()) {
                return jsonResponse(mapOf("success" to false, "message" to "未接收到有效的图片数据"))
            }

            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return jsonResponse(mapOf("success" to false, "message" to "无法解析图片格式，请上传 JPG/PNG/WebP 图片"))

            val maxDim = 1920
            val finalBitmap = if (decoded.width > maxDim || decoded.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / decoded.width, maxDim.toFloat() / decoded.height)
                val targetW = (decoded.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (decoded.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            val outFile = getCustomWallpaperFile(context)
            FileOutputStream(outFile).use { fos ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
            }
            finalBitmap.recycle()

            notifyConfigRefreshed()
            jsonResponse(mapOf("success" to true, "hasCustomWallpaper" to true))
        } catch (e: Exception) {
            Log.e(TAG, "上传自定义背景失败", e)
            newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                gson.toJson(mapOf("success" to false, "message" to (e.message ?: "上传失败")))
            )
        }
    }

    private fun deleteWallpaper(): Response {
        return try {
            val file = getCustomWallpaperFile(context)
            if (file.exists()) {
                file.delete()
            }
            notifyConfigRefreshed()
            jsonResponse(mapOf("success" to true, "hasCustomWallpaper" to false))
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                gson.toJson(mapOf("success" to false, "message" to (e.message ?: "清除失败")))
            )
        }
    }

    private fun notifyConfigRefreshed() {
        context.sendBroadcast(
            Intent(MainBroadcastController.ACTION_REFRESH_CONFIG).apply {
                setPackage(context.packageName)
            }
        )
    }

    private fun update(session: IHTTPSession): Response {
        return try {
            val secretKeys = allFields().filter { it.isSecret }.map { it.key }.toSet()
            // 敏感字段提交空值表示“保持不变”，因为页面从不回显旧值。
            val values = parseJsonBody(session).filterNot { (key, value) ->
                key in secretKeys && value.toString().isEmpty()
            }

            val newAccessCode = values[ACCESS_CODE_FIELD_KEY]?.toString()
            if (newAccessCode != null && !CommonConfig.isValidAccessCode(newAccessCode)) {
                return jsonError(
                    Response.Status.BAD_REQUEST,
                    "访问码需为 ${CommonConfig.MIN_ACCESS_CODE_LENGTH}-${CommonConfig.MAX_ACCESS_CODE_LENGTH} 位且不含空格"
                )
            }

            updateConfig(values - ACCESS_CODE_FIELD_KEY)
            if (newAccessCode != null) {
                commonConfig.updateWebAccessCode(newAccessCode)
                revokeOtherSessions(session)
            }

            notifyConfigRefreshed()
            jsonResponse(mapOf("success" to true))
        } catch (e: Exception) {
            Log.w(TAG, "保存配置失败: ${e.javaClass.simpleName}")
            jsonError(Response.Status.BAD_REQUEST, "保存失败：请求格式错误")
        }
    }

    private fun jsonResponse(value: Any): Response =
        newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            gson.toJson(value)
        ).withSecurityHeaders()

    private fun jsonError(status: Response.Status, message: String): Response =
        newFixedLengthResponse(
            status,
            "application/json",
            gson.toJson(mapOf("success" to false, "message" to message))
        ).withSecurityHeaders()

    private fun htmlResponse(html: String): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
            .withSecurityHeaders()

    private fun Response.withSecurityHeaders(): Response = apply {
        addHeader("Cache-Control", "no-store")
        addHeader("X-Frame-Options", "DENY")
        addHeader("X-Content-Type-Options", "nosniff")
    }

    private fun getDeviceStatus(): Map<String, Any?> {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) {
            (level * 100f / scale).toInt().coerceIn(0, 100)
        } else {
            null
        }
        val batteryStatus = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

        val wallpaperFile = getCustomWallpaperFile(context)
        return linkedMapOf(
            "running" to isAlive,
            "port" to listeningPort,
            "batteryPercent" to batteryPercent,
            "charging" to (batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                    (batteryStatus == BatteryManager.BATTERY_STATUS_FULL && plugged != 0)),
            "batteryAvailable" to (batteryPercent != null),
            "hasCustomWallpaper" to (wallpaperFile.exists() && wallpaperFile.length() > 0L)
        )
    }

    private data class SvgPath(
        val d: String,
        val fillColor: String?,
        val strokeColor: String?,
        val strokeWidth: String?,
        val strokeLineCap: String?,
        val strokeLineJoin: String?
    )

    private data class SvgData(
        val viewportWidth: String,
        val viewportHeight: String,
        val paths: List<SvgPath>
    )

    @SuppressLint("ResourceType")
    private fun getLogoSvgData(): SvgData {
        val paths = mutableListOf<SvgPath>()
        var viewWidth = "512"
        var viewHeight = "512"
        try {
            val parser = context.resources.getXml(logoResId)
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    if (parser.name == "vector") {
                        for (i in 0 until parser.attributeCount) {
                            val attrName = parser.getAttributeName(i)
                            if (attrName == "viewportWidth") {
                                viewWidth = parser.getAttributeValue(i)
                            } else if (attrName == "viewportHeight") {
                                viewHeight = parser.getAttributeValue(i)
                            }
                        }
                    } else if (parser.name == "path") {
                        var d = ""
                        var fillColor: String? = null
                        var strokeColor: String? = null
                        var strokeWidth: String? = null
                        var strokeLineCap: String? = null
                        var strokeLineJoin: String? = null

                        for (i in 0 until parser.attributeCount) {
                            val attrName = parser.getAttributeName(i)
                            val attrVal = parser.getAttributeValue(i)
                            when (attrName) {
                                "pathData" -> d = attrVal
                                "fillColor" -> fillColor = attrVal
                                "strokeColor" -> strokeColor = attrVal
                                "strokeWidth" -> strokeWidth = attrVal
                                "strokeLineCap" -> strokeLineCap = attrVal
                                "strokeLineJoin" -> strokeLineJoin = attrVal
                            }
                        }
                        if (d.isNotEmpty()) {
                            paths.add(SvgPath(d, fillColor, strokeColor, strokeWidth, strokeLineCap, strokeLineJoin))
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse logo vector drawable", e)
        }
        if (paths.isEmpty()) {
            paths.add(SvgPath("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 17h-2v-2h2v2zm0-4h-2V7h2v8z", "currentColor", null, null, null, null))
        }
        return SvgData(viewWidth, viewHeight, paths)
    }

    private fun buildLogoSvgHtml(): String {
        val svgData = getLogoSvgData()
        val pathsHtml = svgData.paths.joinToString("\n") { path ->
            val rawFill = when {
                path.fillColor == null || path.fillColor == "#00000000" -> "none"
                path.fillColor.startsWith("#") -> path.fillColor
                else -> "currentColor"
            }
            val fill = convertArgbToRgba(rawFill)

            val rawStroke = when {
                path.strokeColor == null || path.strokeColor == "#00000000" -> "none"
                path.strokeColor.startsWith("#") -> path.strokeColor
                else -> "currentColor"
            }
            val stroke = convertArgbToRgba(rawStroke)

            val strokeWidthAttr = if (path.strokeWidth != null) " stroke-width=\"${path.strokeWidth}\"" else ""
            val strokeLineCapAttr = if (path.strokeLineCap != null) " stroke-linecap=\"${path.strokeLineCap}\"" else ""
            val strokeLineJoinAttr = if (path.strokeLineJoin != null) " stroke-linejoin=\"${path.strokeLineJoin}\"" else ""

            "<path d=\"${path.d}\" fill=\"$fill\" stroke=\"$stroke\"$strokeWidthAttr$strokeLineCapAttr$strokeLineJoinAttr />"
        }
        return "<svg viewBox=\"0 0 ${svgData.viewportWidth} ${svgData.viewportHeight}\" class=\"app-logo\">$pathsHtml</svg>"
    }

    private data class Section(
        val title: String,
        val icon: String,
        val fields: List<ConfigField>,
        val description: String = "",
        val showWallpaper: Boolean = false,
    )

    /** PC 端细分的配置分类；[mobileGroup] 为手机端合并后的分组（底部 tab 空间有限）。 */
    private enum class ConfigTab(
        val title: String,
        val description: String,
        val iconType: String,
        val mobileGroup: String,
        val keys: Set<String>,
        val showWallpaper: Boolean = false,
    ) {
        CONNECTION(
            "设备连接", "RouterOS API 地址与登录凭据，修改后会重新连接设备。", "conn", "连接设置",
            setOf("host", "port", "username", "password", "version", "community", "authPassword",
                "privPassword", "url", "token", "tokenHeaderType"),
        ),
        MONITORING(
            "采集与探测", "数据刷新频率与链路质量探测目标，修改后无需重新连接。", "mon", "监控设置",
            setOf("pingTarget", "refreshInterval"),
        ),
        BANDWIDTH(
            "签约带宽", "用于计算上下行占用率，填 0 表示不显示占用率。", "bandwidth", "监控设置",
            setOf("wanDownMbps", "wanUpMbps"),
        ),
        BRIGHTNESS(
            "屏幕亮度", "日间亮度与夜间定时调暗，夜间时段支持跨零点。", "brightness", "显示",
            setOf("screenBrightness", "brightnessScheduleEnabled", "nightBrightness",
                "nightBrightnessStart", "nightBrightnessEnd"),
        ),
        APPEARANCE(
            "外观", "自定义背景壁纸、卡片玻璃透明度与文字颜色。", "appearance", "显示",
            setOf("cardTransparency", "primaryTextColor", "secondaryTextColor"), showWallpaper = true,
        ),
        SECURITY(
            "安全", "Web 配置访问码，修改后其他已登录的浏览器需要重新验证。", "security", "安全",
            setOf(ACCESS_CODE_FIELD_KEY),
        );

        companion object {
            /** 未登记的字段归入设备连接，保证新增字段不会丢失。 */
            fun of(key: String): ConfigTab = entries.firstOrNull { key in it.keys } ?: CONNECTION
        }
    }

    /** PC 端：每个分类一个 tab；外观 tab 即使没有字段也要展示壁纸设置。 */
    private fun getDesktopSections(): List<Section> {
        val byTab = allFields().groupBy { ConfigTab.of(it.key) }
        return ConfigTab.entries.mapNotNull { tab ->
            val fields = byTab[tab].orEmpty()
            if (fields.isEmpty() && !tab.showWallpaper) return@mapNotNull null
            Section(tab.title, getSectionIconSvg(tab.iconType), fields, tab.description, tab.showWallpaper)
        }
    }

    /** 手机端：按 [ConfigTab.mobileGroup] 合并为少量分组。 */
    private fun getSections(): List<Section> {
        val byTab = allFields().groupBy { ConfigTab.of(it.key) }
        return ConfigTab.entries.groupBy { it.mobileGroup }.mapNotNull { (group, tabs) ->
            val fields = tabs.flatMap { byTab[it].orEmpty() }
            val showWallpaper = tabs.any { it.showWallpaper }
            if (fields.isEmpty() && !showWallpaper) return@mapNotNull null
            Section(group, getSectionIconSvg(tabs.first().iconType), fields, showWallpaper = showWallpaper)
        }
    }

    private fun getSectionIconSvg(type: String): String = when (type) {
        "conn" -> """<svg class="section-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"></path><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"></path></svg>"""
        "mon" -> """<svg class="section-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"></polyline></svg>"""
        "bandwidth" -> """<svg class="section-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M12 14l4-4"></path><path d="M3.34 19a10 10 0 1 1 17.32 0"></path></svg>"""
        "brightness" -> """<svg class="section-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="4"></circle><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"></path></svg>"""
        "appearance" -> """<svg class="section-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><rect x="3" y="3" width="18" height="18" rx="2"></rect><circle cx="9" cy="9" r="2"></circle><path d="M21 15l-5-5L5 21"></path></svg>"""
        "security" -> """<svg class="section-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path></svg>"""
        else -> """<svg class="section-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect><line x1="8" y1="21" x2="16" y2="21"></line><line x1="12" y1="17" x2="12" y2="21"></line></svg>"""
    }

    private fun getFieldIconSvg(key: String): String = when (key) {
        "host", "url" -> """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="2" width="20" height="8" rx="2" ry="2"></rect><rect x="2" y="14" width="20" height="8" rx="2" ry="2"></rect><line x1="6" y1="6" x2="6.01" y2="6"></line><line x1="6" y1="18" x2="6.01" y2="18"></line></svg>"""
        "port" -> """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="4" y1="9" x2="20" y2="9"></line><line x1="4" y1="15" x2="20" y2="15"></line><line x1="9" y1="4" x2="9" y2="20"></line><line x1="15" y1="4" x2="15" y2="20"></line></svg>"""
        "username" -> """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>"""
        "password", "community", "authPassword", "privPassword", "token", ACCESS_CODE_FIELD_KEY -> """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path></svg>"""
        "pingTarget" -> """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><circle cx="12" cy="12" r="6"></circle><circle cx="12" cy="12" r="2"></circle></svg>"""
        "refreshInterval" -> """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>"""
        "screenBrightness", "brightnessScheduleEnabled", "nightBrightness" -> """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="5"></circle><line x1="12" y1="1" x2="12" y2="3"></line><line x1="12" y1="21" x2="12" y2="23"></line><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line><line x1="1" y1="12" x2="3" y2="12"></line><line x1="21" y1="12" x2="23" y2="12"></line><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line></svg>"""
        "nightBrightnessStart", "nightBrightnessEnd" -> """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"></circle><path d="M12 7v5l3 2"></path></svg>"""
        "dashboardStyle", "tokenHeaderType", "version", "primaryTextColor", "secondaryTextColor" -> """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 14.7255 3.09032 17.1962 4.85857 19C5.03444 19.1759 5.0975 19.4328 5.01955 19.6787C4.693 20.7083 5.462 21.7587 6.53805 21.7587C6.73715 21.7587 6.93175 21.6881 7.08643 21.5583C8.42398 20.4359 10.1332 20 12 22Z"></path><circle cx="7.5" cy="10.5" r="1.5"></circle><circle cx="11.5" cy="7.5" r="1.5"></circle><circle cx="16.5" cy="9.5" r="1.5"></circle></svg>"""
        else -> """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="16"></line><line x1="8" y1="12" x2="16" y2="12"></line></svg>"""
    }

    private fun renderControl(field: ConfigField): String {
        return if (field.type == "color") {
            val customColor = CommonConfig.normalizeHexColor(field.value.toString()).orEmpty()
            val pickerColor = customColor.ifEmpty {
                if (field.key == "primaryTextColor") "#F5F5F7" else "#AEAEB2"
            }
            "<div class=\"color-control\">" +
                "<input class=\"color-picker\" type=\"color\" value=\"$pickerColor\" " +
                    "aria-label=\"${escapeHtml(field.label)}\" " +
                    "oninput=\"this.nextElementSibling.value=this.value.toUpperCase()\">" +
                "<input class=\"color-hex\" data-key=\"${escapeHtml(field.key)}\" type=\"text\" " +
                    "value=\"$customColor\" placeholder=\"自动\" maxlength=\"7\" spellcheck=\"false\" " +
                    "oninput=\"if(/^#[0-9a-fA-F]{6}$/.test(this.value)){this.previousElementSibling.value=this.value}\">" +
                "<button class=\"color-auto\" type=\"button\" " +
                    "onclick=\"this.previousElementSibling.value=''\">自动</button>" +
            "</div>"
        } else if (field.type == "select") {
            val options = field.options.joinToString("") { option ->
                val selected = if (option.value == field.value.toString()) " selected" else ""
                "<option value=\"${escapeHtml(option.value)}\"$selected>${escapeHtml(option.label)}</option>"
            }
            "<select data-key=\"${escapeHtml(field.key)}\">$options</select>"
        } else if (field.isSecret) {
            // 不把已保存的密钥写进 HTML，留空提交即保持原值。
            "<input data-key=\"${escapeHtml(field.key)}\" type=\"password\" value=\"\" " +
                "placeholder=\"已隐藏，留空保持不变\" autocomplete=\"new-password\">"
        } else {
            val escapedValue = escapeHtml(field.value.toString())
            "<input data-key=\"${escapeHtml(field.key)}\" type=\"${escapeHtml(field.type)}\" value=\"$escapedValue\">"
        }
    }

    private fun renderFieldGroup(field: ConfigField): String =
        "<div class=\"field-group\">" +
            "<label class=\"field-label\">${escapeHtml(field.label)}</label>" +
            "<div class=\"input-wrapper\">" +
                "<span class=\"input-icon\">${getFieldIconSvg(field.key)}</span>" +
                renderControl(field) +
            "</div>" +
        "</div>"

    private fun renderWallpaperControlsHtml(): String =
        "<div class=\"wallpaper-box\">" +
            "<div class=\"wallpaper-header\">" +
                "<div>" +
                    "<div class=\"field-label\" style=\"margin-bottom:2px;\">自定义背景图片</div>" +
                    "<div class=\"wallpaper-hint\">支持上传 JPG / PNG / WebP 横屏壁纸（自动压缩适配并立即生效）</div>" +
                "</div>" +
                "<div class=\"wallpaper-actions\">" +
                    "<label class=\"btn-upload\">" +
                        "<input type=\"file\" id=\"wallpaperInput\" accept=\"image/*\" style=\"display:none;\" onchange=\"handleWallpaperSelect(this)\">" +
                        "选择图片上传" +
                    "</label>" +
                    "<button type=\"button\" id=\"clearWallpaperBtn\" class=\"btn-clear\" onclick=\"clearCustomWallpaper()\" style=\"display:none;\">恢复默认背景</button>" +
                "</div>" +
            "</div>" +
            "<div id=\"wallpaperPreviewWrap\" class=\"wallpaper-preview-wrap\" style=\"display:none;\">" +
                "<img id=\"wallpaperPreviewImg\" class=\"wallpaper-preview-img\" alt=\"当前自定义背景\">" +
            "</div>" +
        "</div>"

    private fun loadAssetTemplate(assetPath: String): String =
        context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun renderLoginPage(): String =
        loadAssetTemplate(ASSET_LOGIN_TEMPLATE)
            .replace("{{TITLE}}", escapeHtml(title))
            .replace("{{LOGO_SVG}}", buildLogoSvgHtml())

    private fun renderDesktopPage(): String {
        val sections = getDesktopSections()
        val navHtml = sections.mapIndexed { index, section ->
            val activeClass = if (index == 0) " active" else ""
            "<a class=\"nav-item$activeClass\" data-tab=\"$index\" href=\"#tab-$index\">" +
                section.icon + escapeHtml(section.title) +
            "</a>"
        }.joinToString("\n")
        val sectionsHtml = sections.mapIndexed { index, section ->
            val activeClass = if (index == 0) " active" else ""
            val fieldsHtml = section.fields.joinToString("\n") { renderFieldGroup(it) }
            val gridHtml = if (fieldsHtml.isEmpty()) "" else "<div class=\"fields-grid\">$fieldsHtml</div>"
            val extraHtml = if (section.showWallpaper) renderWallpaperControlsHtml() else ""
            "<section class=\"tab-panel$activeClass\" id=\"tab-$index\" " +
                "data-title=\"${escapeHtml(section.title)}\" data-desc=\"${escapeHtml(section.description)}\">" +
                "<div class=\"config-card\">" +
                    gridHtml +
                    extraHtml +
                "</div>" +
            "</section>"
        }.joinToString("\n")
        val first = sections.firstOrNull()

        return loadAssetTemplate(ASSET_DESKTOP_TEMPLATE)
            .replace("{{TITLE}}", escapeHtml(title))
            .replace("{{LOGO_SVG}}", buildLogoSvgHtml())
            .replace("{{NAV_HTML}}", navHtml)
            .replace("{{PAGE_TITLE}}", escapeHtml(first?.title ?: "设置"))
            .replace("{{PAGE_DESC}}", escapeHtml(first?.description.orEmpty()))
            .replace("{{SECTIONS_HTML}}", sectionsHtml)
    }

    private fun renderMobilePage(): String {
        val sections = getSections()
        val sectionsHtml = sections.mapIndexed { index, section ->
            val activeClass = if (index == 0) " active" else ""
            val fieldsHtml = section.fields.joinToString("\n") { renderFieldGroup(it) }
            val extraHtml = if (section.showWallpaper) renderWallpaperControlsHtml() else ""
            "<div class=\"form-section$activeClass\" id=\"section-$index\">" +
                "<h2 class=\"section-title\">${section.icon}${escapeHtml(section.title)}</h2>" +
                fieldsHtml +
                extraHtml +
            "</div>"
        }.joinToString("\n")

        val tabButtonsHtml = sections.mapIndexed { index, section ->
            val activeClass = if (index == 0) " active" else ""
            "<button class=\"tab-btn$activeClass\" onclick=\"switchTab($index)\">${escapeHtml(section.title)}</button>"
        }.joinToString("\n")

        return loadAssetTemplate(ASSET_MOBILE_TEMPLATE)
            .replace("{{TITLE}}", escapeHtml(title))
            .replace("{{LOGO_SVG}}", buildLogoSvgHtml())
            .replace("{{SECTIONS_HTML}}", sectionsHtml)
            .replace("{{TAB_BUTTONS_HTML}}", tabButtonsHtml)
    }

    private fun convertArgbToRgba(hex: String): String {
        if (!hex.startsWith("#")) return hex
        val cleanHex = hex.substring(1)
        return when (cleanHex.length) {
            8 -> {
                val a = cleanHex.substring(0, 2)
                val r = cleanHex.substring(2, 4)
                val g = cleanHex.substring(4, 6)
                val b = cleanHex.substring(6, 8)
                "#$r$g$b$a"
            }
            else -> hex
        }
    }

    private fun escapeHtml(value: String): String =
        value.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
