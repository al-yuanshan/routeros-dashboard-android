package com.yuanshan.core.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 渐变背景组件 - 为日间模式提供美观的背景
 */
@Composable
fun GradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFE3F2FD), // 浅蓝色
                        Color(0xFFF3E5F5), // 浅紫色
                        Color(0xFFFCE4EC), // 浅粉色
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
    ) {
        content()
    }
}

/**
 * 动态网格渐变背景 - 科技感网格样式
 */
@Composable
fun TechGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFE8EAF6), // 浅靛蓝
                        Color(0xFFC5CAE9), // 中靛蓝
                        Color(0xFF9FA8DA), // 深靛蓝
                    ),
                    center = Offset(0.5f, 0.5f),
                    radius = 1500f
                )
            )
    ) {
        content()
    }
}

/**
 * 清新渐变背景 - 清爽的颜色过渡
 */
@Composable
fun FreshGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFE0F7FA), // 青色极浅
                        Color(0xFFB2EBF2), // 青色浅
                        Color(0xFFE1F5FE), // 淡蓝
                        Color(0xFFE8F5E9), // 淡绿
                    )
                )
            )
    ) {
        content()
    }
}

/**
 * 温暖渐变背景 - 温暖舒适的颜色
 */
@Composable
fun WarmGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFF3E0), // 橙色极浅
                        Color(0xFFFFE0B2), // 橙色浅
                        Color(0xFFFFF9C4), // 黄色浅
                        Color(0xFFFFF8E1), // 琥珀色浅
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
    ) {
        content()
    }
}

/**
 * 专业渐变背景 - 专业稳重的深色调
 */
@Composable
fun ProfessionalGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFF5F5F5), // 灰色极浅
                        Color(0xFFECEFF1), // 蓝灰极浅
                        Color(0xFFE0E0E0), // 灰色浅
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(0f, Float.POSITIVE_INFINITY)
                )
            )
    ) {
        content()
    }
}
