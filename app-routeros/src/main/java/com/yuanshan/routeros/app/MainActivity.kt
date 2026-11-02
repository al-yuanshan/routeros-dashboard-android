package com.yuanshan.routeros.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.yuanshan.core.BaseMonitorActivity
import com.yuanshan.core.config.CommonConfig
import com.yuanshan.core.service.ConfigWebServer
import com.yuanshan.core.utils.FormatUtils
import com.yuanshan.routeros.config.RouterOSConfig
import com.yuanshan.routeros.ui.DashboardWallpaper
import com.yuanshan.routeros.ui.LuminanceMap
import com.yuanshan.routeros.model.RouterOsSystemInfo
import com.yuanshan.routeros.ui.QualityState
import com.yuanshan.routeros.ui.RouterOsCompactAppleScreen
import com.yuanshan.routeros.ui.RouterOsDashboardState
import com.yuanshan.routeros.ui.SystemState
import com.yuanshan.routeros.ui.TrafficState
import com.yuanshan.routeros.ui.WanState
import com.yuanshan.routeros.viewmodel.RouterOsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

class MainActivity : BaseMonitorActivity() {

    private companion object {
        // 启动后短暂展示访问码，便于在已连接状态下找回 Web 配置入口。
        const val ACCESS_CODE_VISIBLE_AFTER_START_MS = 3 * 60_000L
        // 超过 3 个刷新周期（至少 10 秒）没有新数据即视为过期。
        const val MIN_STALE_THRESHOLD_MS = 10_000L
        const val STALE_CONTENT_ALPHA = 0.55f
        const val MBPS_TO_BYTES_PER_SECOND = 125_000L
        // 壁纸只需覆盖 1280×720 屏幕，按此上限降采样解码，避免大图在 2GB 设备上 OOM。
        const val WALLPAPER_MAX_EDGE_PX = 1280
        const val LUMINANCE_SAMPLE_SIZE = 24
        // 毛玻璃底图：缩到长边 320px 再多次盒式模糊；分辨率翻倍、半径同步翻倍，模糊程度不变但边缘更干净。
        const val BLUR_MAX_EDGE_PX = 320
        const val BLUR_RADIUS_PX = 6
        const val BLUR_PASSES = 3
        const val GLASS_SATURATION = 1.15f
    }

    private val viewModel: RouterOsViewModel by viewModels()
    private lateinit var config: RouterOSConfig
    private val startedAtMs = SystemClock.elapsedRealtime()

    // 自定义壁纸在后台解码，避免在组合阶段读写磁盘
    private var wallpaper by mutableStateOf<DashboardWallpaper?>(null)
    private var wallpaperStamp = -1L
    // 卡片透明度（-1 为自动），配置刷新时更新以触发重组
    private var cardTransparency by mutableIntStateOf(CommonConfig.CARD_TRANSPARENCY_AUTO)
    private var primaryTextColor by mutableStateOf<ComposeColor?>(null)
    private var secondaryTextColor by mutableStateOf<ComposeColor?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        config = RouterOSConfig(this)
        super.onCreate(savedInstanceState)
    }

    override fun configureMonitoring() {
        viewModel.configure(
            host = config.host,
            port = config.port,
            username = config.username,
            password = config.password,
            pingTarget = config.pingTarget,
            refreshIntervalMs = config.routerosRefreshInterval
        )
        cardTransparency = commonConfig.cardTransparency
        primaryTextColor = commonConfig.primaryTextColor?.let(::parseComposeColor)
        secondaryTextColor = commonConfig.secondaryTextColor?.let(::parseComposeColor)
        reloadWallpaper()
    }

    override fun stopMonitoring() {
        viewModel.stopMonitoring()
    }

    private fun reloadWallpaper() {
        lifecycleScope.launch {
            val file = ConfigWebServer.getCustomWallpaperFile(this@MainActivity)
            val stamp = withContext(Dispatchers.IO) {
                if (file.exists() && file.length() > 0L) file.lastModified() else 0L
            }
            if (stamp == wallpaperStamp) return@launch
            wallpaperStamp = stamp
            wallpaper = if (stamp > 0L) {
                withContext(Dispatchers.IO) { runCatching { decodeWallpaper(file) }.getOrNull() }
            } else {
                null
            }
        }
    }

    @Composable
    override fun MonitorContent() {
        val systemInfo by viewModel.systemInfo.collectAsStateWithLifecycle()
        val connected by viewModel.isConnected.collectAsStateWithLifecycle()
        val error by viewModel.errorMessage.collectAsStateWithLifecycle()
        val histories by viewModel.speedHistory.collectAsStateWithLifecycle()
        val speeds by viewModel.currentSpeed.collectAsStateWithLifecycle()
        val capacity by viewModel.historyCapacity.collectAsStateWithLifecycle()
        val latencyHistory by viewModel.latencyHistory.collectAsStateWithLifecycle()
        val connectionHistory by viewModel.connectionHistory.collectAsStateWithLifecycle()
        val lastSuccessAt by viewModel.lastSuccessAtMs.collectAsStateWithLifecycle()

        // 刷新失败时各 Flow 不再变化，用 1 秒时钟驱动“数据 N 秒前”和访问码的显隐。
        var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(1_000L)
                now = SystemClock.elapsedRealtime()
            }
        }
        val staleThresholdMs = maxOf(
            viewModel.effectiveRefreshIntervalMs * 3,
            MIN_STALE_THRESHOLD_MS,
        )
        // derivedStateOf 让每秒的时钟只在提示文字变化时才触发重组。
        val staleHint by remember(lastSuccessAt, staleThresholdMs) {
            derivedStateOf {
                val ageMs = lastSuccessAt?.let { (now - it).coerceAtLeast(0L) }
                if (ageMs != null && ageMs > staleThresholdMs) "数据 ${formatAge(ageMs)}前" else null
            }
        }
        val recentlyStarted by remember {
            derivedStateOf { now - startedAtMs < ACCESS_CODE_VISIBLE_AFTER_START_MS }
        }

        val network = systemInfo.networks.firstOrNull()
        val history = histories[network?.interfaceName]
        val speed = speeds[network?.interfaceName] ?: (0L to 0L)

        val state = RouterOsDashboardState(
            deviceName = deviceName(systemInfo),
            version = FormatUtils.formatVersion(systemInfo.version).let {
                if (it.firstOrNull()?.isDigit() == true) "v$it" else it
            },
            connected = connected,
            errorMessage = error,
            uptime = if (connected && systemInfo.uptime.isNotBlank()) {
                FormatUtils.formatUptime(systemInfo.uptime)
            } else {
                "--"
            },
            temperatureC = systemInfo.cpu.temperature.takeIf { it > 0f },
            accessHint = if (!connected || recentlyStarted) {
                "访问码 ${commonConfig.webAccessCode}"
            } else {
                null
            },
            staleHint = staleHint,
            traffic = TrafficState(
                downloadBps = speed.first,
                uploadBps = speed.second,
                downloadTotalBytes = network?.rxBytes ?: 0L,
                uploadTotalBytes = network?.txBytes ?: 0L,
                downloadHistory = history?.first.orEmpty(),
                uploadHistory = history?.second.orEmpty(),
                historyCapacity = capacity,
                windowLabel = "近 ${(capacity * viewModel.effectiveRefreshIntervalMs / 60_000L).coerceAtLeast(1)} 分钟",
                downloadCapBps = config.wanDownMbps.takeIf { it > 0 }?.let { it * MBPS_TO_BYTES_PER_SECOND },
                uploadCapBps = config.wanUpMbps.takeIf { it > 0 }?.let { it * MBPS_TO_BYTES_PER_SECOND },
            ),
            quality = QualityState(
                latencyMs = systemInfo.routingMetrics.latencyMs,
                packetLossPercent = systemInfo.routingMetrics.packetLossPercent,
                pingTarget = config.pingTarget,
                latencyHistory = latencyHistory,
                activeConnections = systemInfo.routingMetrics.activeConnections,
                maxConnections = systemInfo.routingMetrics.maxConnections,
                connectionHistory = connectionHistory,
            ),
            system = SystemState(
                cpuPercent = systemInfo.cpu.cpuLoad.roundToInt().coerceIn(0, 100),
                cpuCores = systemInfo.cpu.count,
                ramPercent = systemInfo.memory.usagePercent.roundToInt().coerceIn(0, 100),
                ramUsedBytes = systemInfo.memory.usedMemory,
                ramTotalBytes = systemInfo.memory.totalMemory,
                diskPercent = systemInfo.disk.usagePercent.roundToInt().coerceIn(0, 100),
                diskUsedBytes = systemInfo.disk.usedSpace,
                diskTotalBytes = systemInfo.disk.totalSpace,
            ),
            wan = WanState(
                interfaceName = network?.interfaceName,
                ipAddress = network?.ipAddress,
                running = connected && network?.running == true,
            ),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (staleHint != null) STALE_CONTENT_ALPHA else 1f)
        ) {
            RouterOsCompactAppleScreen(
                state = state,
                wallpaper = wallpaper,
                cardTransparencyPercent = cardTransparency.takeIf { it >= 0 },
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
            )
        }
    }

    private fun decodeWallpaper(file: File): DashboardWallpaper? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= WALLPAPER_MAX_EDGE_PX) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return null
        val blurred = blurredCopy(bitmap)
        val blurredPixels = IntArray(blurred.width * blurred.height)
        blurred.getPixels(blurredPixels, 0, blurred.width, 0, 0, blurred.width, blurred.height)
        return DashboardWallpaper(
            image = bitmap.asImageBitmap(),
            blurred = blurred.asImageBitmap(),
            luminance = averageLuminance(bitmap),
            luminanceMap = LuminanceMap.fromPixels(blurred.width, blurred.height, blurredPixels),
        )
    }

    /** 生成与原图同比例的模糊小图；多次盒式模糊近似高斯模糊。 */
    private fun blurredCopy(bitmap: Bitmap): Bitmap {
        val scale = BLUR_MAX_EDGE_PX.toFloat() / maxOf(bitmap.width, bitmap.height)
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        val buffer = IntArray(pixels.size)
        repeat(BLUR_PASSES) {
            boxBlur(pixels, buffer, width, height, horizontal = true)
            boxBlur(buffer, pixels, width, height, horizontal = false)
        }
        val blurred = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        blurred.setPixels(pixels, 0, width, 0, 0, width, height)
        if (small !== bitmap) small.recycle()
        // 模糊会让颜色发灰，适度提高饱和度，玻璃里透出的壁纸颜色更鲜活。
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(GLASS_SATURATION) })
        }
        Canvas(result).drawBitmap(blurred, 0f, 0f, paint)
        blurred.recycle()
        return result
    }

    private fun boxBlur(src: IntArray, dst: IntArray, width: Int, height: Int, horizontal: Boolean) {
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        for (line in 0 until lines) {
            for (i in 0 until length) {
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                for (k in -BLUR_RADIUS_PX..BLUR_RADIUS_PX) {
                    val j = (i + k).coerceIn(0, length - 1)
                    val pixel = if (horizontal) src[line * width + j] else src[j * width + line]
                    r += Color.red(pixel)
                    g += Color.green(pixel)
                    b += Color.blue(pixel)
                    count++
                }
                val out = Color.rgb(r / count, g / count, b / count)
                if (horizontal) dst[line * width + i] = out else dst[i * width + line] = out
            }
        }
    }

    /** 缩到极小尺寸后求平均相对亮度，足以判断整体明暗。 */
    private fun averageLuminance(bitmap: Bitmap): Float {
        val small = Bitmap.createScaledBitmap(bitmap, LUMINANCE_SAMPLE_SIZE, LUMINANCE_SAMPLE_SIZE, true)
        val pixels = IntArray(LUMINANCE_SAMPLE_SIZE * LUMINANCE_SAMPLE_SIZE)
        small.getPixels(pixels, 0, LUMINANCE_SAMPLE_SIZE, 0, 0, LUMINANCE_SAMPLE_SIZE, LUMINANCE_SAMPLE_SIZE)
        if (small !== bitmap) small.recycle()
        val total = pixels.sumOf { pixel ->
            (0.2126 * Color.red(pixel) + 0.7152 * Color.green(pixel) + 0.0722 * Color.blue(pixel)) / 255.0
        }
        return (total / pixels.size).toFloat()
    }

    private fun deviceName(info: RouterOsSystemInfo): String =
        info.identity.ifBlank { info.boardName }.ifBlank { "RouterOS" }

    private fun formatAge(ageMs: Long): String {
        val seconds = ageMs / 1_000L
        return when {
            seconds < 60 -> "$seconds 秒"
            seconds < 3_600 -> "${seconds / 60} 分钟"
            else -> "${seconds / 3_600} 小时"
        }
    }

    private fun parseComposeColor(value: String): ComposeColor? =
        runCatching { ComposeColor(Color.parseColor(value)) }.getOrNull()
}
