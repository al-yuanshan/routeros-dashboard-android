package com.yuanshan.routeros.ui

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/**
 * 模糊壁纸逐像素的相对亮度（线性空间，0～1）。
 * 卡片按自己在屏幕上的位置取样，从而知道“背后那一块”有多亮。
 */
class LuminanceMap(val width: Int, val height: Int, private val values: FloatArray) {
    init {
        require(width > 0 && height > 0 && values.size == width * height)
    }

    /**
     * 统计屏幕区域背后的亮度。坐标为根布局像素，映射方式与 ContentScale.Crop 一致。
     */
    fun regionStats(
        rootWidth: Float,
        rootHeight: Float,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): RegionLuminance? {
        if (rootWidth <= 0f || rootHeight <= 0f || right <= left || bottom <= top) return null
        val scale = maxOf(rootWidth / width, rootHeight / height)
        val offsetX = (width - rootWidth / scale) / 2f
        val offsetY = (height - rootHeight / scale) / 2f
        val x0 = floor(offsetX + left / scale).toInt().coerceIn(0, width - 1)
        val x1 = (ceil(offsetX + right / scale).toInt() - 1).coerceIn(x0, width - 1)
        val y0 = floor(offsetY + top / scale).toInt().coerceIn(0, height - 1)
        val y1 = (ceil(offsetY + bottom / scale).toInt() - 1).coerceIn(y0, height - 1)

        val samples = FloatArray((x1 - x0 + 1) * (y1 - y0 + 1))
        var index = 0
        var sum = 0f
        for (y in y0..y1) {
            for (x in x0..x1) {
                val value = values[y * width + x]
                samples[index++] = value
                sum += value
            }
        }
        samples.sort()
        return RegionLuminance(
            mean = sum / samples.size,
            low = samples[((samples.size - 1) * 0.1f).toInt()],
            high = samples[((samples.size - 1) * 0.9f).toInt()],
        )
    }

    companion object {
        private val SRGB_TO_LINEAR = FloatArray(256) { srgbToLinear(it) }

        /** 由 ARGB 像素生成亮度图。 */
        fun fromPixels(width: Int, height: Int, pixels: IntArray): LuminanceMap =
            LuminanceMap(width, height, FloatArray(pixels.size) { i -> relativeLuminance(pixels[i]) })

        fun relativeLuminance(argb: Int): Float =
            0.2126f * SRGB_TO_LINEAR[(argb shr 16) and 0xFF] +
                0.7152f * SRGB_TO_LINEAR[(argb shr 8) and 0xFF] +
                0.0722f * SRGB_TO_LINEAR[argb and 0xFF]

        private fun srgbToLinear(channel: Int): Float {
            val c = channel / 255f
            return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        }
    }
}

/** 区域亮度：平均值、最暗 10% 与最亮 10% 分位。 */
data class RegionLuminance(val mean: Float, val low: Float, val high: Float)

/** 玻璃材质色调：深色玻璃配浅色字，浅色玻璃配深色字。 */
enum class GlassTone { Dark, Light }

// 以下亮度均为相对亮度：深色玻璃 #12141A、浅色玻璃 #E6E8EE（中性灰白，避免乳白发雾），
// 以及两种色调下最浅的常用文字（深色 #D1D1D6、浅色 #3A3A3C）。
private const val DARK_TINT_LUMINANCE = 0.0068f
private const val LIGHT_TINT_LUMINANCE = 0.807f
private const val DARK_TONE_TEXT_LUMINANCE = 0.637f
private const val LIGHT_TONE_TEXT_LUMINANCE = 0.0423f

/** 次级文字需达到的对比度（WCAG AA 正文标准）。 */
internal const val GLASS_TARGET_CONTRAST = 4.5f

private const val DARK_TINT_MIN = 0.16f
private const val DARK_TINT_MAX = 0.62f
private const val LIGHT_TINT_MIN = 0.14f
private const val LIGHT_TINT_MAX = 0.58f
private const val HEADER_SCRIM_MIN = 0.15f
private const val HEADER_SCRIM_MAX = 0.55f

/** 深色玻璃配白字更锐利，明显优先；只有背景确实很亮时才切换浅色。当前色调再加粘性，避免防烧屏平移时来回切换。 */
private const val DARK_TONE_BIAS = 0.20f
private const val TONE_STICKINESS = 0.12f

internal fun contrastRatio(a: Float, b: Float): Float =
    (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)

/** 叠加 [alpha] 的着色后，背景的相对亮度。 */
internal fun tintedLuminance(tone: GlassTone, background: Float, alpha: Float): Float {
    val tint = if (tone == GlassTone.Dark) DARK_TINT_LUMINANCE else LIGHT_TINT_LUMINANCE
    return tint * alpha + background * (1f - alpha)
}

/**
 * 让该色调的次级文字在最不利的背景像素上达到 [GLASS_TARGET_CONTRAST] 所需的着色强度（未限幅）。
 * 深色玻璃看最亮的 10%，浅色玻璃看最暗的 10%。
 */
internal fun requiredTintAlpha(tone: GlassTone, background: RegionLuminance): Float = when (tone) {
    GlassTone.Dark -> {
        val maxBackground = (DARK_TONE_TEXT_LUMINANCE + 0.05f) / GLASS_TARGET_CONTRAST - 0.05f
        val bg = background.high
        if (bg <= maxBackground) 0f else (bg - maxBackground) / (bg - DARK_TINT_LUMINANCE)
    }
    GlassTone.Light -> {
        val minBackground = GLASS_TARGET_CONTRAST * (LIGHT_TONE_TEXT_LUMINANCE + 0.05f) - 0.05f
        val bg = background.low
        if (bg >= minBackground) 0f else (minBackground - bg) / (LIGHT_TINT_LUMINANCE - bg)
    }
}.coerceIn(0f, 1f)

/** 卡片玻璃着色：够用即止，尽量透出壁纸。 */
internal fun glassTintAlpha(tone: GlassTone, background: RegionLuminance): Float =
    when (tone) {
        GlassTone.Dark -> requiredTintAlpha(tone, background).coerceIn(DARK_TINT_MIN, DARK_TINT_MAX)
        GlassTone.Light -> requiredTintAlpha(tone, background).coerceIn(LIGHT_TINT_MIN, LIGHT_TINT_MAX)
    }

/**
 * 顶部状态栏没有卡片，用渐变托底。模糊亮度图会抹掉细线条等高频细节，
 * 因此保留一个很淡的下限，防止文字压在细碎纹理上。
 */
internal fun headerScrimAlpha(tone: GlassTone, background: RegionLuminance): Float =
    requiredTintAlpha(tone, background).coerceIn(HEADER_SCRIM_MIN, HEADER_SCRIM_MAX)

/** 选择需要更少着色的色调，即对壁纸遮挡最少的那一种。 */
internal fun chooseGlassTone(background: RegionLuminance, current: GlassTone = GlassTone.Dark): GlassTone {
    val darkCost = requiredTintAlpha(GlassTone.Dark, background) - DARK_TONE_BIAS -
        if (current == GlassTone.Dark) TONE_STICKINESS else 0f
    val lightCost = requiredTintAlpha(GlassTone.Light, background) -
        if (current == GlassTone.Light) TONE_STICKINESS else 0f
    return if (lightCost < darkCost) GlassTone.Light else GlassTone.Dark
}

/** 用户设定的透明度百分比（0 不透明～100 全透明）转换为着色强度。 */
internal fun tintAlphaForTransparency(percent: Int): Float =
    1f - percent.coerceIn(0, 100) / 100f

/** 背景整体只做轻微压暗，主要依靠局部玻璃保证可读，范围 0.04～0.16。 */
internal fun wallpaperScrimAlpha(luminance: Float): Float =
    0.04f + 0.12f * luminance.coerceIn(0f, 1f)
