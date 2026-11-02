package com.yuanshan.routeros.config

import com.yuanshan.core.config.BrightnessSchedule
import org.junit.Assert.assertEquals
import org.junit.Test

class BrightnessScheduleTest {
    @Test
    fun crossMidnightSchedule_usesNightBrightnessWithinRange() {
        assertEquals(20, brightnessAt(23, 30))
        assertEquals(20, brightnessAt(6, 59))
        assertEquals(100, brightnessAt(7, 0))
        assertEquals(100, brightnessAt(21, 59))
    }

    @Test
    fun disabledSchedule_alwaysUsesDaytimeBrightness() {
        assertEquals(
            100,
            BrightnessSchedule.brightnessAt(100, 20, false, "22:00", "07:00", 23, 0),
        )
    }

    @Test
    fun sameStartAndEnd_doesNotDimAllDay() {
        assertEquals(
            100,
            BrightnessSchedule.brightnessAt(100, 20, true, "22:00", "22:00", 23, 0),
        )
    }

    private fun brightnessAt(hour: Int, minute: Int): Int =
        BrightnessSchedule.brightnessAt(100, 20, true, "22:00", "07:00", hour, minute)
}
