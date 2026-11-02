package com.yuanshan.routeros.viewmodel

/**
 * 根据累计字节计数器估算速率（字节/秒）。
 *
 * RouterOS 的接口计数器大约每秒更新一次，与 1 秒轮询叠加时相邻两次采样的差值
 * 会在 0 和两倍之间跳动。这里用至少 [windowMs] 的滑动窗口计算平均速率来消除抖动。
 */
internal class CounterRateEstimator(private val windowMs: Long = 3_000L) {

    private data class Sample(val timeMs: Long, val rx: Long, val tx: Long)

    private val samples = ArrayDeque<Sample>()

    /** 加入一次采样；样本不足或计数器重置后的首个样本返回 null。 */
    fun add(timeMs: Long, rxBytes: Long, txBytes: Long): Pair<Long, Long>? {
        val last = samples.lastOrNull()
        // 计数器回退（PPPoE 重拨、接口重建）或时钟异常时重新开始
        if (last != null && (rxBytes < last.rx || txBytes < last.tx || timeMs <= last.timeMs)) {
            samples.clear()
        }
        samples.addLast(Sample(timeMs, rxBytes, txBytes))

        // 保留一个不晚于窗口起点的锚点样本，使计算跨度不小于 windowMs
        while (samples.size > 2 && samples[1].timeMs <= timeMs - windowMs) {
            samples.removeFirst()
        }
        if (samples.size < 2) return null

        val first = samples.first()
        val seconds = (timeMs - first.timeMs) / 1000.0
        return ((rxBytes - first.rx) / seconds).toLong() to ((txBytes - first.tx) / seconds).toLong()
    }
}
