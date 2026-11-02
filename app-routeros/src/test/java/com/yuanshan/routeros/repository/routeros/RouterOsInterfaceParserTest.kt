package com.yuanshan.routeros.repository.routeros

import com.yuanshan.routeros.repository.parseIpAddresses
import com.yuanshan.routeros.repository.parsePppoeNetworks
import com.yuanshan.routeros.repository.parseTemperature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterOsInterfaceParserTest {

    @Test
    fun ipAddresses_stripsPrefixAndKeepsFirstPerInterface() {
        val result = parseIpAddresses(
            listOf(
                mapOf("interface" to "pppoe-out1", "address" to "100.64.1.2/32"),
                mapOf("interface" to "pppoe-out1", "address" to "100.64.1.3/32"),
                mapOf("interface" to "bridge", "address" to "192.168.8.1/24"),
                mapOf("address" to "10.0.0.1/24"),
            )
        )

        assertEquals(mapOf("pppoe-out1" to "100.64.1.2", "bridge" to "192.168.8.1"), result)
    }

    @Test
    fun pppoeNetworks_mapsCountersAndIgnoresNonPppoeOrNamelessRows() {
        val result = parsePppoeNetworks(
            interfaces = listOf(
                mapOf(
                    "name" to "pppoe-out1",
                    "type" to "pppoe-out",
                    "rx-byte" to "1024",
                    "tx-byte" to "2048",
                    "running" to "true",
                ),
                mapOf("name" to "ether1", "type" to "ether", "running" to "true"),
                mapOf("type" to "pppoe-out"),
            ),
            ipByInterface = mapOf("pppoe-out1" to "100.64.1.2"),
        )

        assertEquals(1, result.size)
        val network = result.single()
        assertEquals("pppoe-out1", network.interfaceName)
        assertEquals("100.64.1.2", network.ipAddress)
        assertEquals(1_024L, network.rxBytes)
        assertEquals(2_048L, network.txBytes)
        assertTrue(network.running)
    }

    @Test
    fun pppoeNetworks_malformedCountersFallBackToZero() {
        val network = parsePppoeNetworks(
            interfaces = listOf(mapOf("name" to "pppoe-out1", "rx-byte" to "n/a")),
            ipByInterface = emptyMap(),
        ).single()

        assertEquals(0L, network.rxBytes)
        assertEquals("", network.ipAddress)
    }

    @Test
    fun temperature_supportsV6FieldsAndV7NameValueRows() {
        assertEquals(47f, parseTemperature(listOf(mapOf("cpu-temperature" to "47"))))
        assertEquals(
            52f,
            parseTemperature(
                listOf(
                    mapOf("name" to "voltage", "value" to "24.1"),
                    mapOf("name" to "cpu-temperature", "value" to "52"),
                )
            ),
        )
        assertEquals(0f, parseTemperature(emptyList()))
    }
}
