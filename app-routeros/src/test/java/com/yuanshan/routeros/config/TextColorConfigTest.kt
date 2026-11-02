package com.yuanshan.routeros.config

import com.yuanshan.core.config.CommonConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextColorConfigTest {
    @Test
    fun normalizeHexColor_acceptsRgbAndCanonicalizesCase() {
        assertEquals("#1A2B3C", CommonConfig.normalizeHexColor("  #1a2b3c "))
    }

    @Test
    fun normalizeHexColor_rejectsInvalidOrTransparentValues() {
        assertNull(CommonConfig.normalizeHexColor("#123"))
        assertNull(CommonConfig.normalizeHexColor("#80112233"))
        assertNull(CommonConfig.normalizeHexColor("blue"))
        assertNull(CommonConfig.normalizeHexColor(""))
    }
}
