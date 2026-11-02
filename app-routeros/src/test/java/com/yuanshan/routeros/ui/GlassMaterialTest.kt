package com.yuanshan.routeros.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassMaterialTest {

    private val darkText = 0.637f
    private val lightText = 0.0423f

    @Test
    fun darkWallpaper_keepsThinDarkGlass() {
        val bg = RegionLuminance(mean = 0.03f, low = 0.01f, high = 0.06f)
        assertEquals(GlassTone.Dark, chooseGlassTone(bg))
        assertEquals(0.16f, glassTintAlpha(GlassTone.Dark, bg), 0.001f)
    }

    @Test
    fun brightWallpaper_switchesToThinLightGlass() {
        val bg = RegionLuminance(mean = 0.75f, low = 0.6f, high = 0.9f)
        assertEquals(GlassTone.Light, chooseGlassTone(bg))
        assertEquals(0.14f, glassTintAlpha(GlassTone.Light, bg), 0.001f)
    }

    @Test
    fun chosenTone_reachesTargetContrastWhenWithinLimits() {
        for (high in listOf(0.05f, 0.12f, 0.2f, 0.35f, 0.5f, 0.8f, 1f)) {
            val bg = RegionLuminance(mean = high * 0.8f, low = high * 0.6f, high = high)
            val tone = chooseGlassTone(bg)
            val required = requiredTintAlpha(tone, bg)
            if (required > 0.58f) continue
            val alpha = glassTintAlpha(tone, bg)
            val ratio = if (tone == GlassTone.Dark) {
                contrastRatio(darkText, tintedLuminance(tone, bg.high, alpha))
            } else {
                contrastRatio(lightText, tintedLuminance(tone, bg.low, alpha))
            }
            assertTrue("high=$high tone=$tone ratio=$ratio", ratio >= GLASS_TARGET_CONTRAST - 0.01f)
        }
    }

    @Test
    fun toneStickiness_preventsFlipNearThreshold() {
        val bg = RegionLuminance(mean = 0.18f, low = 0.147f, high = 0.21f)
        val fromDark = chooseGlassTone(bg, GlassTone.Dark)
        val fromLight = chooseGlassTone(bg, GlassTone.Light)
        // 临界区内保持当前色调
        assertEquals(GlassTone.Dark, fromDark)
        assertEquals(GlassTone.Light, fromLight)
    }

    @Test
    fun regionStats_mapsCropCoordinates() {
        // 4×2 图：左半黑、右半白；根布局同比例 400×200
        val map = LuminanceMap(4, 2, floatArrayOf(0f, 0f, 1f, 1f, 0f, 0f, 1f, 1f))
        val left = map.regionStats(400f, 200f, 0f, 0f, 200f, 200f)
        val right = map.regionStats(400f, 200f, 200f, 0f, 400f, 200f)
        assertNotNull(left)
        assertEquals(0f, left!!.high, 0.001f)
        assertEquals(1f, right!!.low, 0.001f)
    }

    @Test
    fun wallpaperScrim_staysLight() {
        assertEquals(0.04f, wallpaperScrimAlpha(0f), 0.001f)
        assertEquals(0.16f, wallpaperScrimAlpha(2f), 0.001f)
    }

    @Test
    fun userTransparency_mapsToTintAlpha() {
        assertEquals(1f, tintAlphaForTransparency(0), 0.001f)
        assertEquals(0.3f, tintAlphaForTransparency(70), 0.001f)
        assertEquals(0f, tintAlphaForTransparency(150), 0.001f)
        assertEquals(1f, tintAlphaForTransparency(-5), 0.001f)
    }
}
