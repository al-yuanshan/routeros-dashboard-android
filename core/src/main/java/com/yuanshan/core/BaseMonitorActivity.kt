package com.yuanshan.core

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.view.WindowCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.yuanshan.core.broadcast.MainBroadcastController
import com.yuanshan.core.config.BrightnessSchedule
import com.yuanshan.core.config.CommonConfig
import com.yuanshan.core.ui.composable.BurnInProtection
import com.yuanshan.core.ui.theme.MonitorTheme
import java.util.Calendar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

abstract class BaseMonitorActivity : ComponentActivity() {

    protected lateinit var commonConfig: CommonConfig
        private set

    private lateinit var broadcastController: MainBroadcastController
    private val configRevision = mutableIntStateOf(0)
    private var lastAppliedBrightnessPercent: Int? = null
    private var brightnessScheduleJob: Job? = null

    protected abstract fun configureMonitoring()
    protected abstract fun stopMonitoring()

    @Composable
    protected abstract fun MonitorContent()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setupFullscreenWindow()

        commonConfig = CommonConfig(this)

        broadcastController = MainBroadcastController(
            context = this,
            onConfigRefreshed = {
                configRevision.intValue++
                applyScheduledBrightness()
                configureMonitoring()
            }
        )

        setContent {
            configRevision.intValue
            MonitorTheme {
                // 深色满屏底色：任何子界面未覆盖到的区域都不会露出白色窗口背景。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ComposeColor(0xFF07080B))
                ) {
                    BurnInProtection {
                        MonitorContent()
                    }
                }
            }
        }

        applyScheduledBrightness()
        applyFullscreenMode()
    }

    override fun onStart() {
        super.onStart()
        broadcastController.registerAll()
        startBrightnessSchedule()
        configureMonitoring()
    }

    override fun onStop() {
        broadcastController.unregisterAll()
        brightnessScheduleJob?.cancel()
        brightnessScheduleJob = null
        stopMonitoring()
        super.onStop()
    }

    override fun onDestroy() {
        broadcastController.unregisterAll()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullscreenMode()
    }

    private fun applyScreenBrightness(brightnessPercent: Int) {
        val safePercent = brightnessPercent.coerceIn(1, 100)
        if (lastAppliedBrightnessPercent == safePercent) return
        lastAppliedBrightnessPercent = safePercent

        window.attributes = window.attributes.apply {
            screenBrightness = safePercent / 100f
        }
    }

    private fun startBrightnessSchedule() {
        brightnessScheduleJob?.cancel()
        brightnessScheduleJob = lifecycleScope.launch {
            while (isActive) {
                applyScheduledBrightness()
                delay(BRIGHTNESS_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun applyScheduledBrightness() {
        val now = Calendar.getInstance()
        val brightness = BrightnessSchedule.brightnessAt(
            daytimeBrightness = commonConfig.screenBrightness,
            nightBrightness = commonConfig.nightBrightness,
            enabled = commonConfig.brightnessScheduleEnabled,
            start = commonConfig.nightBrightnessStart,
            end = commonConfig.nightBrightnessEnd,
            currentHour = now.get(Calendar.HOUR_OF_DAY),
            currentMinute = now.get(Calendar.MINUTE),
        )
        applyScreenBrightness(brightness)
    }

    @Suppress("DEPRECATION")
    private fun setupFullscreenWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    private fun applyFullscreenMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    private companion object {
        const val BRIGHTNESS_CHECK_INTERVAL_MS = 30_000L
    }
}
