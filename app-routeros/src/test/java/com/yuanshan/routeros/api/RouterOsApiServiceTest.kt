package com.yuanshan.routeros.api

import me.legrange.mikrotik.ApiConnectionException
import me.legrange.mikrotik.MikrotikApiException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterOsApiServiceTest {

    @Test
    fun connectionFailure_detectsWrappedTimeout() {
        val timeout = MikrotikApiException(
            "Command timed out after 10000 ms",
            ApiConnectionException("Command timed out after 10000 ms"),
        )

        assertTrue(timeout.isRouterOsConnectionFailure())
    }

    @Test
    fun connectionFailure_ignoresUnrelatedCommandError() {
        assertFalse(MikrotikApiException("RouterOS rejected command").isRouterOsConnectionFailure())
    }
}
