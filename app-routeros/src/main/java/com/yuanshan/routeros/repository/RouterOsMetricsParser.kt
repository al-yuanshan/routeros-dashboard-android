package com.yuanshan.routeros.repository

import com.yuanshan.routeros.model.NetworkModel
import com.yuanshan.routeros.model.RouterOsRoutingMetrics
import kotlin.math.roundToInt

private val durationPart = Regex("""(\d+(?:\.\d+)?)(ns|us|µs|ms|s)""")

/** 接口名 -> 第一个 IPv4/IPv6 地址（去掉前缀长度）。 */
internal fun parseIpAddresses(rows: List<Map<String, String>>): Map<String, String> =
    rows.mapNotNull { row ->
        val iface = row["interface"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val address = row["address"]?.substringBefore("/")?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        iface to address
    }.distinctBy { it.first }.toMap()

internal fun parsePppoeNetworks(
    interfaces: List<Map<String, String>>,
    ipByInterface: Map<String, String>,
): List<NetworkModel> =
    interfaces
        .filter { it["type"]?.lowercase()?.contains("pppoe") ?: true }
        .mapNotNull { iface ->
            val name = iface["name"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            NetworkModel(
                interfaceName = name,
                ipAddress = ipByInterface[name].orEmpty(),
                rxBytes = iface["rx-byte"]?.toLongOrNull() ?: 0L,
                txBytes = iface["tx-byte"]?.toLongOrNull() ?: 0L,
                running = iface["running"]?.toBoolean() ?: false,
            )
        }

/** RouterOS v6 直接给出 temperature 字段，v7 以 name/value 行的形式返回。 */
internal fun parseTemperature(rows: List<Map<String, String>>): Float {
    rows.firstOrNull()?.let { row ->
        (row["cpu-temperature"] ?: row["temperature"])?.toFloatOrNull()?.let { return it }
    }
    val byName = rows.associate { it["name"].orEmpty() to it["value"] }
    return (byName["cpu-temperature"] ?: byName["temperature"] ?: byName["board-temperature1"])
        ?.toFloatOrNull()
        ?: 0f
}

internal fun parseConnectionTracking(
    rows: List<Map<String, String>>
): Pair<Long?, Long?> {
    val values = rows.firstOrNull().orEmpty()
    return values["total-entries"]?.toLongOrNull() to
        values["max-entries"]?.toLongOrNull()
}

internal fun parsePingMetrics(
    rows: List<Map<String, String>>,
    expectedCount: Int
): RouterOsRoutingMetrics {
    val summary = rows.lastOrNull {
        it.containsKey("avg-rtt") ||
            it.containsKey("packet-loss") ||
            it.containsKey("sent")
    }

    val replyLatencies = rows.mapNotNull { row ->
        row["time"]?.let(::parseRouterOsDurationMs)
    }
    val latency = summary
        ?.get("avg-rtt")
        ?.let(::parseRouterOsDurationMs)
        ?: replyLatencies.takeIf { it.isNotEmpty() }?.average()?.roundToInt()

    val packetLoss = summary
        ?.get("packet-loss")
        ?.removeSuffix("%")
        ?.toDoubleOrNull()
        ?.roundToInt()
        ?: run {
            val sent = summary?.get("sent")?.toIntOrNull() ?: expectedCount
            val received = summary?.get("received")?.toIntOrNull() ?: replyLatencies.size
            if (sent > 0) {
                ((sent - received).coerceAtLeast(0) * 100.0 / sent)
                    .roundToInt()
            } else {
                null
            }
        }

    return RouterOsRoutingMetrics(
        latencyMs = latency?.coerceAtLeast(0),
        packetLossPercent = packetLoss?.coerceIn(0, 100)
    )
}

internal fun parseRouterOsDurationMs(value: String): Int? {
    val matches = durationPart.findAll(value.trim()).toList()
    if (matches.isEmpty()) return null

    val milliseconds = matches.sumOf { match ->
        val amount = match.groupValues[1].toDoubleOrNull() ?: 0.0
        when (match.groupValues[2]) {
            "s" -> amount * 1_000.0
            "ms" -> amount
            "us", "µs" -> amount / 1_000.0
            "ns" -> amount / 1_000_000.0
            else -> 0.0
        }
    }
    return milliseconds.roundToInt()
}
