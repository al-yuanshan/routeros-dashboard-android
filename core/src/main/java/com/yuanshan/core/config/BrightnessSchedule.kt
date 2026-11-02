package com.yuanshan.core.config

/** 解析 HH:mm，并判断当前分钟是否落在夜间时段；支持 22:00-07:00 这类跨日区间。 */
object BrightnessSchedule {
    fun brightnessAt(
        daytimeBrightness: Int,
        nightBrightness: Int,
        enabled: Boolean,
        start: String,
        end: String,
        currentHour: Int,
        currentMinute: Int,
    ): Int {
        if (!enabled) return daytimeBrightness
        val startMinute = parseMinutes(start) ?: return daytimeBrightness
        val endMinute = parseMinutes(end) ?: return daytimeBrightness
        val now = currentHour * 60 + currentMinute
        val isNight = when {
            startMinute == endMinute -> false
            startMinute < endMinute -> now in startMinute until endMinute
            else -> now >= startMinute || now < endMinute
        }
        return if (isNight) nightBrightness else daytimeBrightness
    }

    private fun parseMinutes(value: String): Int? {
        if (!CommonConfig.isValidTime(value)) return null
        val (hour, minute) = value.split(':').map(String::toInt)
        return hour * 60 + minute
    }
}
