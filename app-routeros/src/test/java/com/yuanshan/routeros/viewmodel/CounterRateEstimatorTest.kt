package com.yuanshan.routeros.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CounterRateEstimatorTest {

    @Test
    fun firstSample_returnsNull() {
        assertNull(CounterRateEstimator().add(0L, 1_000L, 1_000L))
    }

    @Test
    fun jitteryOneSecondCounters_areSmoothedOverWindow() {
        val estimator = CounterRateEstimator(windowMs = 3_000L)
        // 计数器每两秒才跳一次 2000 字节，真实速率 1000 B/s
        val counters = listOf(0L, 0L, 2_000L, 2_000L, 4_000L, 4_000L, 6_000L)
        val rates = counters.mapIndexed { second, bytes ->
            estimator.add(second * 1_000L, bytes, 0L)?.first
        }

        // 窗口填满后速率稳定在 1000 附近，不再 0 / 2000 交替
        rates.drop(3).forEach { rate ->
            assertEquals(1_000.0, rate!!.toDouble(), 340.0)
        }
    }

    @Test
    fun counterReset_restartsWithoutNegativeRate() {
        val estimator = CounterRateEstimator()
        estimator.add(0L, 10_000L, 10_000L)
        estimator.add(1_000L, 20_000L, 20_000L)

        assertNull(estimator.add(2_000L, 500L, 500L))
        assertEquals(500L to 500L, estimator.add(3_000L, 1_000L, 1_000L))
    }

    @Test
    fun slowPolling_usesLatestInterval() {
        val estimator = CounterRateEstimator(windowMs = 3_000L)
        estimator.add(0L, 0L, 0L)
        estimator.add(5_000L, 5_000L, 0L)

        assertEquals(2_000L to 0L, estimator.add(10_000L, 15_000L, 0L))
    }
}
