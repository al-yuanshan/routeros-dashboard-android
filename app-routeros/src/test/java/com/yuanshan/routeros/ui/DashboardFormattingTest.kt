package com.yuanshan.routeros.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardFormattingTest {

    @Test
    fun niceCeiling_roundsUpToOneTwoFive() {
        assertEquals(1.0, niceCeiling(0.0), 0.0)
        assertEquals(10_000.0, niceCeiling(10_000.0), 0.0)
        assertEquals(20_000.0, niceCeiling(10_001.0), 0.0)
        assertEquals(50_000.0, niceCeiling(22_200.0), 0.0)
        assertEquals(1_000_000.0, niceCeiling(612_000.0), 0.0)
    }

    @Test
    fun formatRate_keepsThreeSignificantDigits() {
        assertEquals("0" to "B/s", formatRate(0L))
        assertEquals("2.51" to "KB/s", formatRate(2_510L))
        assertEquals("15.8" to "KB/s", formatRate(15_830L))
        assertEquals("999" to "KB/s", formatRate(999_400L))
        assertEquals("1.00" to "MB/s", formatRate(999_600L))
        assertEquals("0" to "B/s", formatRate(-5L))
    }

    @Test
    fun formatSize_usesBinaryUnits() {
        assertEquals("2.00 GB", formatSize(2_147_483_648L))
        assertEquals("92.9 GB", formatSize(99_750_000_000L))
        assertEquals("0 B", formatSize(0L))
        assertEquals("2 GB", formatCompactSize(2_147_483_648L))
        assertEquals("22 MB", formatCompactSize(23_068_672L))
    }

    @Test
    fun alertLevels_respectThresholds() {
        assertEquals(AlertLevel.NORMAL, latencyLevel(31, 0))
        assertEquals(AlertLevel.WARNING, latencyLevel(31, 1))
        assertEquals(AlertLevel.WARNING, latencyLevel(80, 0))
        assertEquals(AlertLevel.CRITICAL, latencyLevel(150, 0))
        assertEquals(AlertLevel.NORMAL, latencyLevel(null, 50))
        assertEquals(AlertLevel.WARNING, usageLevel(70))
        assertEquals(AlertLevel.CRITICAL, usageLevel(85))
        assertEquals(AlertLevel.NORMAL, temperatureLevel(null))
    }
}
