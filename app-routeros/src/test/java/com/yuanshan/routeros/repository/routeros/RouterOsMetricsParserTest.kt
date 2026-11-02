package com.yuanshan.routeros.repository.routeros

import com.yuanshan.routeros.repository.parseConnectionTracking
import com.yuanshan.routeros.repository.parsePingMetrics
import com.yuanshan.routeros.repository.parseRouterOsDurationMs
import org.junit.Assert.assertEquals
import org.junit.Test

class RouterOsMetricsParserTest {

    @Test
    fun connectionTracking_readsCurrentAndMaximumEntries() {
        val result = parseConnectionTracking(
            listOf(
                mapOf(
                    "total-entries" to "1284",
                    "max-entries" to "32768"
                )
            )
        )

        assertEquals(1_284L, result.first)
        assertEquals(32_768L, result.second)
    }

    @Test
    fun pingMetrics_prefersRouterOsSummary() {
        val result = parsePingMetrics(
            rows = listOf(
                mapOf("time" to "17ms"),
                mapOf("time" to "18ms"),
                mapOf(
                    "sent" to "3",
                    "received" to "3",
                    "packet-loss" to "0%",
                    "avg-rtt" to "18ms"
                )
            ),
            expectedCount = 3
        )

        assertEquals(18, result.latencyMs)
        assertEquals(0, result.packetLossPercent)
    }

    @Test
    fun pingMetrics_calculatesFallbackFromReplies() {
        val result = parsePingMetrics(
            rows = listOf(
                mapOf("time" to "12ms"),
                mapOf("time" to "18ms"),
                mapOf("status" to "timeout")
            ),
            expectedCount = 3
        )

        assertEquals(15, result.latencyMs)
        assertEquals(33, result.packetLossPercent)
    }

    @Test
    fun durationParser_supportsSubMillisecondAndCompoundValues() {
        assertEquals(0, parseRouterOsDurationMs("453us"))
        assertEquals(1_250, parseRouterOsDurationMs("1s250ms"))
    }
}
