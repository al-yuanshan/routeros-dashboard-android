package com.yuanshan.core.ui.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// 以中心为起点绕一圈的 3x3 偏移序列（单位：步长）。
private val SHIFT_PATTERN = listOf(
    0 to 0, 1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1,
)

private const val STEP_DP = 2

private val LocalBurnInIndex = staticCompositionLocalOf<State<Int>?> { null }

/**
 * 常亮屏防烧屏：每隔 [intervalMs] 切换一次平移位置。
 *
 * 只提供位移状态，不移动整个界面：背景/壁纸必须保持满屏静止，
 * 否则平移后屏幕边缘会露出窗口底色（白条）。需要平移的前景用 [burnInShift] 声明。
 */
@Composable
fun BurnInProtection(
    modifier: Modifier = Modifier,
    intervalMs: Long = 3 * 60_000L,
    content: @Composable () -> Unit,
) {
    val index = remember { mutableIntStateOf(0) }
    LaunchedEffect(intervalMs) {
        while (true) {
            delay(intervalMs)
            index.intValue = (index.intValue + 1) % SHIFT_PATTERN.size
        }
    }

    CompositionLocalProvider(LocalBurnInIndex provides index) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
        }
    }
}

/** 按防烧屏节奏平移前景（只影响布局阶段，不触发重组）。应放在已留有边距的内容上。 */
@Composable
fun Modifier.burnInShift(): Modifier {
    val index = LocalBurnInIndex.current ?: return this
    return offset {
        val step = STEP_DP.dp.roundToPx()
        val (dx, dy) = SHIFT_PATTERN[index.value]
        IntOffset(dx * step, dy * step)
    }
}
