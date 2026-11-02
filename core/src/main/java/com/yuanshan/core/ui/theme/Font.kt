package com.yuanshan.core.ui.theme

import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.yuanshan.core.R

// 通用正文保持系统无衬线，确保中文覆盖完整。
val AppFontFamily = FontFamily.SansSerif
val MonoFontFamily = FontFamily.Monospace

/**
 * 监控屏标题字体。
 *
 * 使用设备内置的中黑体，避免中文标题被等宽字体拉得僵硬。
 */
val DashboardTitleFontFamily = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-medium"), weight = FontWeight.Normal),
    Font(DeviceFontFamilyName("sans-serif-medium"), weight = FontWeight.Medium),
    Font(DeviceFontFamilyName("sans-serif-black"), weight = FontWeight.Bold)
)

/**
 * 监控数字字体。
 *
 * Oxanium SemiBold 只包含拉丁字符，用于数字、英文缩写和单位。
 * 中文字符会由 Android 字体回退机制继续使用系统字体。
 */
val DashboardDisplayFontFamily = FontFamily(
    Font(
        resId = R.font.oxanium_semibold,
        weight = FontWeight.SemiBold
    )
)

val DashboardLabelFontFamily = FontFamily.SansSerif
