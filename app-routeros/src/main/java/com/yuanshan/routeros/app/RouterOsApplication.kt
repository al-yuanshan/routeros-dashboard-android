package com.yuanshan.routeros.app

import android.app.Application
import com.yuanshan.core.config.CommonConfig
import com.yuanshan.routeros.config.RouterOSConfig
import com.yuanshan.core.service.ConfigField
import com.yuanshan.core.service.ConfigWebServer

class RouterOsApplication : Application() {
    private var server: ConfigWebServer? = null

    override fun onCreate() {
        super.onCreate()
        val config = RouterOSConfig(this)
        val common = CommonConfig(this)
        server = ConfigWebServer(
            context = this,
            port = CommonConfig.WEB_CONFIG_PORT,
            title = "RouterOS Dashboard 配置",
            logoResId = com.yuanshan.core.R.drawable.ic_launcher_foreground,
            fieldsProvider = {
                listOf(
                    ConfigField("host", "RouterOS 地址", config.host),
                    ConfigField("port", "明文 API 端口（通常 8728）", config.port, "number"),
                    ConfigField("username", "用户名", config.username),
                    ConfigField("password", "密码", config.password, "password"),
                    ConfigField("pingTarget", "链路探测目标", config.pingTarget),
                    ConfigField(
                        "refreshInterval",
                        "采样间隔（毫秒，可自由设置，最小 100）",
                        config.routerosRefreshInterval,
                        "number"
                    ),
                    ConfigField(
                        "wanDownMbps",
                        "下行签约带宽（Mbps，0 表示不显示占用率）",
                        config.wanDownMbps,
                        "number"
                    ),
                    ConfigField(
                        "wanUpMbps",
                        "上行签约带宽（Mbps，0 表示不显示占用率）",
                        config.wanUpMbps,
                        "number"
                    ),
                    ConfigField("screenBrightness", "日间亮度（1-100）", common.screenBrightness, "number"),
                    ConfigField(
                        "brightnessScheduleEnabled",
                        "定时亮度",
                        common.brightnessScheduleEnabled,
                        "select",
                        listOf(
                            com.yuanshan.core.service.ConfigOption("false", "关闭"),
                            com.yuanshan.core.service.ConfigOption("true", "开启"),
                        )
                    ),
                    ConfigField("nightBrightness", "夜间亮度（1-100）", common.nightBrightness, "number"),
                    ConfigField("nightBrightnessStart", "夜间模式开始", common.nightBrightnessStart, "time"),
                    ConfigField("nightBrightnessEnd", "夜间模式结束", common.nightBrightnessEnd, "time"),
                    ConfigField(
                        "cardTransparency",
                        "卡片透明度（仅自定义壁纸生效，越高越通透）",
                        common.cardTransparency,
                        "select",
                        listOf(
                            com.yuanshan.core.service.ConfigOption(
                                CommonConfig.CARD_TRANSPARENCY_AUTO.toString(),
                                "自动（按壁纸保证文字清晰）",
                            ),
                        ) + (0..CommonConfig.CARD_TRANSPARENCY_MAX step 10).map { percent ->
                            com.yuanshan.core.service.ConfigOption(
                                percent.toString(),
                                when (percent) {
                                    0 -> "0%（不透明）"
                                    CommonConfig.CARD_TRANSPARENCY_MAX -> "$percent%（最通透，文字可能不清晰）"
                                    else -> "$percent%"
                                },
                            )
                        }
                    ),
                    ConfigField(
                        "primaryTextColor",
                        "主要字体颜色（留空则根据壁纸自动调整）",
                        common.primaryTextColor.orEmpty(),
                        "color",
                    ),
                    ConfigField(
                        "secondaryTextColor",
                        "次要字体颜色（留空则根据壁纸自动调整）",
                        common.secondaryTextColor.orEmpty(),
                        "color",
                    ),
                )
            },
            updateConfig = { values ->
                values["host"]?.toString()?.let { config.host = it }
                (values["port"] as? Double)?.toInt()?.let { config.port = it }
                values["username"]?.toString()?.let { config.username = it }
                values["password"]?.toString()?.let { config.password = it }
                values["pingTarget"]?.toString()?.let { config.pingTarget = it }
                (values["refreshInterval"] as? Double)?.toLong()
                    ?.let { config.routerosRefreshInterval = it }
                (values["wanDownMbps"] as? Double)?.toInt()?.let { config.wanDownMbps = it }
                (values["wanUpMbps"] as? Double)?.toInt()?.let { config.wanUpMbps = it }
                (values["screenBrightness"] as? Double)?.toInt()
                    ?.let { common.screenBrightness = it }
                values["brightnessScheduleEnabled"]?.toString()?.toBooleanStrictOrNull()
                    ?.let { common.brightnessScheduleEnabled = it }
                (values["nightBrightness"] as? Double)?.toInt()
                    ?.let { common.nightBrightness = it }
                values["nightBrightnessStart"]?.toString()?.let { common.nightBrightnessStart = it }
                values["nightBrightnessEnd"]?.toString()?.let { common.nightBrightnessEnd = it }
                // 页面下拉框提交字符串，API 也可能直接提交数字。
                values["cardTransparency"]?.toString()?.toDoubleOrNull()?.toInt()
                    ?.let { common.cardTransparency = it }
                values["primaryTextColor"]?.toString()?.let { common.primaryTextColor = it }
                values["secondaryTextColor"]?.toString()?.let { common.secondaryTextColor = it }
            }

        ).also { it.startSafely() }
    }
}
