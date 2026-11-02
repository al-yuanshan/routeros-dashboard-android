package com.yuanshan.routeros.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机后自动打开监控界面。
 *
 * Android 10+ 限制后台启动 Activity，需要授予“显示在其他应用上层”权限：
 * `adb shell appops set com.yuanshan.routeros SYSTEM_ALERT_WINDOW allow`。
 * 即使启动界面失败，进程已被拉起，Web 配置服务仍可访问。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in BOOT_ACTIONS) return
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { context.startActivity(launch) }
            .onFailure { Log.w(TAG, "开机自启动界面失败: ${it.message}") }
    }

    private companion object {
        const val TAG = "BootReceiver"
        val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
        )
    }
}
