package com.yuanshan.core.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

class MainBroadcastController(
    private val context: Context,
    private val onConfigRefreshed: () -> Unit
) {
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_REFRESH_CONFIG) onConfigRefreshed()
        }
    }

    fun registerAll() {
        if (registered) return
        val filter = IntentFilter(ACTION_REFRESH_CONFIG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        registered = true
    }

    fun unregisterAll() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }

    companion object {
        const val ACTION_REFRESH_CONFIG = "com.yuanshan.core.REFRESH_CONFIG"
    }
}
