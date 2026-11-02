package com.yuanshan.routeros.ui

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanshan.routeros.ui.theme.AppleLightGlassColors
import com.yuanshan.routeros.ui.theme.AppleDarkGlassColors
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.geometry.Rect
import com.yuanshan.core.R
import com.yuanshan.core.ui.composable.burnInShift
import com.yuanshan.core.ui.theme.DashboardDisplayFontFamily
import com.yuanshan.routeros.ui.theme.AppleRouterOsColors
import com.yuanshan.routeros.ui.theme.RouterOsTheme
import com.yuanshan.routeros.ui.theme.RouterOsThemeProvider
import kotlin.math.roundToInt

/**
 * Apple 风格 RouterOS 仪表盘（640×360dp 起，横屏、无触控）。
 *
 * 设计原则：
 * - 布局：顶部状态栏；中部左「网络吞吐（数字 + 5 分钟曲线）」、右「链路质量（延迟 + 连接数）」；
 *   底部系统资源与 WAN 信息条。
 * - 颜色只表达含义：上下行用固定的蓝/紫区分，其余指标平时为中性色，超阈值才变橙/红。
 * - 所有曲线都标注刻度或统计值，不展示无法解读的比例条。
 */
@Composable
fun RouterOsCompactAppleScreen(
    state: RouterOsDashboardState,
    modifier: Modifier = Modifier,
    wallpaper: DashboardWallpaper? = null,
    /** 用户指定的卡片透明度（0～90）；null 表示按壁纸亮度自动计算。 */
    cardTransparencyPercent: Int? = null,
    primaryTextColor: Color? = null,
    secondaryTextColor: Color? = null,
) {
    val baseColors = AppleRouterOsColors.withTextOverrides(primaryTextColor, secondaryTextColor)
    RouterOsThemeProvider(colors = baseColors) {
        val backdrop = remember(wallpaper) { wallpaper?.let(::GlassBackdrop) }
        CompositionLocalProvider(
            LocalGlassBackdrop provides backdrop,
            LocalCardTransparency provides cardTransparencyPercent,
            LocalPrimaryTextColor provides primaryTextColor,
            LocalSecondaryTextColor provides secondaryTextColor,
        ) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(RouterOsTheme.colors.bgRadialEdge)
                    .onGloballyPositioned { backdrop?.rootCoordinates = it }
            ) {
                DashboardBackground(wallpaper)

                // 背景与壁纸保持满屏静止，只平移前景内容（防烧屏），边缘不会露底。
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .burnInShift(),
                    verticalArrangement = Arrangement.spacedBy(CardGap)
                ) {
                    // 每一排按背后壁纸的明暗统一选择深色或浅色玻璃。
                    GlassToneGroup(headerScrim = true) {
                        AppleHeaderBar(state)
                    }

                    GlassToneGroup(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(CardGap)
                    ) {
                        AppleTrafficCard(
                            traffic = state.traffic,
                            modifier = Modifier
                                .weight(1.08f)
                                .fillMaxHeight()
                        )
                        AppleQualityCard(
                            quality = state.quality,
                            modifier = Modifier
                                .weight(0.92f)
                                .fillMaxHeight()
                        )
                    }
                    }

                    GlassToneGroup(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp),
                    ) {
                        AppleSystemStrip(
                            system = state.system,
                            wan = state.wan,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

/** 毛玻璃所需信息：模糊壁纸、仪表盘根布局坐标（用于换算卡片在屏幕中的位置）。 */
private class GlassBackdrop(val wallpaper: DashboardWallpaper) {
    var rootCoordinates: LayoutCoordinates? = null

    /** 元素在根布局中的位置；根布局未就绪时返回 null。 */
    fun boundsOf(coordinates: LayoutCoordinates): Rect? {
        val root = rootCoordinates ?: return null
        if (!root.isAttached || !coordinates.isAttached) return null
        val offset = root.localPositionOf(coordinates, Offset.Zero)
        return Rect(offset, coordinates.size.toSize())
    }

    fun luminanceOf(bounds: Rect): RegionLuminance? {
        val root = rootCoordinates?.size ?: return null
        return wallpaper.luminanceMap.regionStats(
            rootWidth = root.width.toFloat(),
            rootHeight = root.height.toFloat(),
            left = bounds.left,
            top = bounds.top,
            right = bounds.right,
            bottom = bounds.bottom,
        )
    }
}

private val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }
private val LocalGlassTone = staticCompositionLocalOf { GlassTone.Dark }
private val LocalCardTransparency = staticCompositionLocalOf<Int?> { null }
private val LocalPrimaryTextColor = staticCompositionLocalOf<Color?> { null }
private val LocalSecondaryTextColor = staticCompositionLocalOf<Color?> { null }

/**
 * 一排内容共用一种玻璃色调：按这一排背后的壁纸亮度，选择遮挡最少即可保证对比度的深/浅玻璃，
 * 并同步切换文字配色与文字光晕。[headerScrim] 为没有卡片的状态栏补一层按需的渐变托底。
 */
@Composable
private fun GlassToneGroup(
    modifier: Modifier = Modifier,
    headerScrim: Boolean = false,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalGlassBackdrop.current
    if (backdrop == null) {
        Box(modifier = modifier) { content() }
        return
    }
    var tone by remember(backdrop) { mutableStateOf(GlassTone.Dark) }
    var bounds by remember(backdrop) { mutableStateOf<Rect?>(null) }
    val luminance = remember(backdrop, bounds) { bounds?.let(backdrop::luminanceOf) }
    val scrimAlpha = if (headerScrim && luminance != null) headerScrimAlpha(tone, luminance) else 0f
    val scrimColor = glassTintColor(tone)

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                val newBounds = backdrop.boundsOf(coordinates) ?: return@onGloballyPositioned
                if (newBounds == bounds) return@onGloballyPositioned
                bounds = newBounds
                backdrop.luminanceOf(newBounds)?.let { tone = chooseGlassTone(it, tone) }
            }
            .drawBehind {
                val area = bounds ?: return@drawBehind
                val root = backdrop.rootCoordinates?.size ?: return@drawBehind
                if (scrimAlpha <= 0f) return@drawBehind
                // 托底铺满整个屏幕宽度并延伸到顶部边缘，下沿渐隐，不形成色块边界。
                val fadeHeight = 24.dp.toPx()
                val totalHeight = area.bottom + fadeHeight
                val color = scrimColor.copy(alpha = scrimAlpha)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to color,
                        (area.bottom / totalHeight) to color,
                        1f to Color.Transparent,
                        startY = -area.top,
                        endY = -area.top + totalHeight,
                    ),
                    topLeft = Offset(-area.left, -area.top),
                    size = Size(root.width.toFloat(), totalHeight),
                )
            }
    ) {
        GlassToneProvider(tone) { content() }
    }
}

@Composable
private fun GlassToneProvider(tone: GlassTone, content: @Composable () -> Unit) {
    val colors = (if (tone == GlassTone.Light) AppleLightGlassColors else AppleDarkGlassColors)
        .withTextOverrides(LocalPrimaryTextColor.current, LocalSecondaryTextColor.current)
    // 浅色玻璃上深色字直接绘制，任何光晕都会让字边发虚；
    // 深色玻璃只保留紧贴字形的极淡阴影。
    val halo = if (tone == GlassTone.Light) {
        null
    } else {
        Shadow(color = Color(0x73000000), offset = Offset(0f, 0.5f), blurRadius = 1.5f)
    }
    RouterOsThemeProvider(colors = colors) {
        CompositionLocalProvider(
            LocalGlassTone provides tone,
            LocalTextStyle provides LocalTextStyle.current.copy(shadow = halo),
        ) {
            content()
        }
    }
}

private fun com.yuanshan.routeros.ui.theme.RouterOsColors.withTextOverrides(
    primary: Color?,
    secondary: Color?,
) = copy(
    textPrimary = primary ?: textPrimary,
    logoTint = primary ?: logoTint,
    logoBadgeText = primary ?: logoBadgeText,
    tagText = primary ?: tagText,
    textSecondary = secondary ?: textSecondary,
    textMuted = secondary ?: textMuted,
    textTitle = secondary ?: textTitle,
)

private fun glassTintColor(tone: GlassTone): Color =
    if (tone == GlassTone.Light) GlassTintLight else GlassTint

@Composable
private fun AlertLevel.color(normal: Color): Color = when (this) {
    AlertLevel.NORMAL -> normal
    AlertLevel.WARNING -> RouterOsTheme.colors.warningAccent
    AlertLevel.CRITICAL -> RouterOsTheme.colors.statusOffline
}

/** 卡片、底栏等所有间距统一使用同一个值。 */
private val CardGap = 10.dp
private val CardShape = RoundedCornerShape(16.dp)

/** 卡片统一底色：比背景亮一档，不依赖背景内容即可分辨轮廓。 */
private val CardSurfaceSolid = Color(0xFF1A1D24)
/** 毛玻璃着色基色（深/浅），透明度按卡片背后的亮度自适应。 */
private val GlassTint = Color(0xFF12141A)
private val GlassTintLight = Color(0xFFE6E8EE)
/** 四边一致的细边框，避免渐变边框在底边消失。 */
private val CardBorder = Color(0x26FFFFFF)
private val ScrimColor = Color(0xFF05070B)

@Composable
private fun DashboardBackground(wallpaper: DashboardWallpaper?) {
    if (wallpaper != null) {
        Image(
            bitmap = wallpaper.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // 只做轻微整体压暗，尽量保留壁纸原貌；可读性由各排玻璃局部保证。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScrimColor.copy(alpha = wallpaperScrimAlpha(wallpaper.luminance)))
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0F1116), Color(0xFF07080B))))
        )
    }
}

/** 圆角卡片底座：所有卡片共用同一底色、边框和圆角；有壁纸时为毛玻璃。 */
@Composable
private fun AppleBentoSurface(
    modifier: Modifier = Modifier,
    horizontalPadding: Int = 14,
    verticalPadding: Int = 11,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalGlassBackdrop.current
    val surfaceModifier = if (backdrop == null) {
        Modifier
            .background(CardSurfaceSolid)
            .border(width = 1.dp, color = CardBorder, shape = CardShape)
    } else {
        Modifier
            .glassBackground(backdrop, LocalGlassTone.current)
            .border(width = 1.dp, brush = glassBorderBrush(LocalGlassTone.current), shape = CardShape)
    }
    Box(
        modifier = modifier
            .clip(CardShape)
            .then(surfaceModifier)
            .padding(horizontal = horizontalPadding.dp, vertical = verticalPadding.dp)
    ) {
        content()
    }
}

/**
 * 毛玻璃底：在卡片内绘制与背景完全对齐的模糊壁纸，再叠一层“够用即止”的着色和顶部高光。
 * 着色强度按卡片背后的实际亮度计算，保证文字对比度的同时尽量透出壁纸。
 * 不依赖 RenderEffect，因此 Android 7 也能获得一致效果。
 */
private fun Modifier.glassBackground(backdrop: GlassBackdrop, tone: GlassTone): Modifier = composed {
    var bounds by remember(backdrop) { mutableStateOf<Rect?>(null) }
    val userTransparency = LocalCardTransparency.current
    val tintAlpha = remember(backdrop, bounds, tone, userTransparency) {
        // 用户指定时直接使用（深/浅色调仍自动选择）；否则按背后亮度计算够用的着色。
        userTransparency?.let(::tintAlphaForTransparency) ?: run {
            val luminance = bounds?.let(backdrop::luminanceOf)
            if (luminance == null) 0.4f else glassTintAlpha(tone, luminance)
        }
    }
    val tint = glassTintColor(tone).copy(alpha = tintAlpha)
    val sheen = if (tone == GlassTone.Light) 0.08f else 0.05f
    this
        .onGloballyPositioned { coordinates ->
            val newBounds = backdrop.boundsOf(coordinates) ?: return@onGloballyPositioned
            if (newBounds != bounds) bounds = newBounds
        }
        .drawBehind {
            val area = bounds ?: return@drawBehind
            val rootSize = backdrop.rootCoordinates?.size ?: return@drawBehind
            translate(-area.left, -area.top) {
                drawImageCropped(backdrop.wallpaper.blurred, rootSize)
            }
            drawRect(tint)
            // 顶部一抹柔和高光，模拟玻璃受光，增加通透感。
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = sheen),
                    0.45f to Color.Transparent,
                ),
            )
        }
}

/** 玻璃边缘：上沿亮、下沿淡但不消失，勾出厚度感。 */
private fun glassBorderBrush(tone: GlassTone): Brush =
    if (tone == GlassTone.Light) {
        Brush.verticalGradient(listOf(Color(0x99FFFFFF), Color(0x40FFFFFF)))
    } else {
        Brush.verticalGradient(listOf(Color(0x4DFFFFFF), Color(0x1AFFFFFF)))
    }

/** 等同 ContentScale.Crop：保持比例铺满 [target]，与背景原图的裁切方式一致。 */
private fun DrawScope.drawImageCropped(image: ImageBitmap, target: IntSize) {
    if (target.width <= 0 || target.height <= 0) return
    val scale = maxOf(
        target.width / image.width.toFloat(),
        target.height / image.height.toFloat(),
    )
    val srcWidth = (target.width / scale).roundToInt().coerceIn(1, image.width)
    val srcHeight = (target.height / scale).roundToInt().coerceIn(1, image.height)
    drawImage(
        image = image,
        srcOffset = IntOffset((image.width - srcWidth) / 2, (image.height - srcHeight) / 2),
        srcSize = IntSize(srcWidth, srcHeight),
        dstSize = target,
        filterQuality = FilterQuality.Low,
    )
}

@Composable
private fun CardTitle(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = RouterOsTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = title,
            color = colors.textTitle,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun Label(text: String, color: Color = RouterOsTheme.colors.textMuted, size: TextUnit = 11.sp) {
    Text(
        text = text,
        color = color,
        fontSize = size,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = DashboardDisplayFontFamily,
        maxLines = 1,
        modifier = Modifier
            .border(0.75.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 1.dp)
    )
}

/** 大号数值 + 单位。 */
@Composable
private fun BigValue(value: String, unit: String, unitColor: Color, valueSize: TextUnit = 28.sp) {
    Row {
        Text(
            modifier = Modifier.alignByBaseline(),
            text = value,
            color = RouterOsTheme.colors.textPrimary,
            fontSize = valueSize,
            fontFamily = DashboardDisplayFontFamily,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        if (unit.isNotEmpty()) {
            Text(
                text = unit,
                color = unitColor,
                fontSize = 13.sp,
                fontFamily = DashboardDisplayFontFamily,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .alignByBaseline()
                    .padding(start = 4.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 顶部状态栏
// ---------------------------------------------------------------------------

@Composable
private fun AppleHeaderBar(state: RouterOsDashboardState) {
    val colors = RouterOsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.textPrimary),
            modifier = Modifier.size(17.dp)
        )
        Text(
            text = state.deviceName,
            color = colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (state.connected) {
            Label(state.version, color = colors.textSecondary, size = 12.sp)
        } else {
            // 未连接时在版本号位置显示原因，宽度受限时省略
            Text(
                text = state.errorMessage ?: "未连接",
                color = colors.statusOffline,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        }

        state.staleHint?.let { Pill(it, colors.statusOffline) }
        state.accessHint?.let { accessHint ->
            Text(
                text = accessHint,
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontFamily = DashboardDisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }

        state.temperatureC?.let { temp ->
            HeaderMetric(
                label = "温度",
                value = "${temp.roundToInt()}°C",
                valueColor = temperatureLevel(temp).color(colors.textPrimary)
            )
        }
        HeaderMetric(label = "运行", value = state.uptime, valueColor = colors.textPrimary)
        ConnectionIndicator(connected = state.connected)
    }
}

@Composable
private fun HeaderMetric(label: String, value: String, valueColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Label(label)
        Text(
            text = value,
            color = valueColor,
            fontSize = 13.sp,
            fontFamily = DashboardDisplayFontFamily,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun ConnectionIndicator(connected: Boolean) {
    val colors = RouterOsTheme.colors
    val statusColor = if (connected) colors.statusOnline else colors.statusOffline
    val transition = rememberInfiniteTransition(label = "status_pulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(12.dp)) {
            if (connected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        // 在绘制阶段读取动画值，避免每帧重组
                        .graphicsLayer {
                            val scale = 1f + pulse
                            scaleX = scale
                            scaleY = scale
                            alpha = 0.35f + 0.65f * pulse
                        }
                        .background(statusColor.copy(alpha = 0.3f), CircleShape)
                )
            }
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(statusColor, CircleShape)
            )
        }
        Text(
            text = if (connected) "已连接" else "未连接",
            color = statusColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

// ---------------------------------------------------------------------------
// 网络吞吐卡片
// ---------------------------------------------------------------------------

@Composable
private fun AppleTrafficCard(traffic: TrafficState, modifier: Modifier = Modifier) {
    val colors = RouterOsTheme.colors
    val peak = maxOf(
        traffic.downloadHistory.maxOrNull() ?: 0L,
        traffic.uploadHistory.maxOrNull() ?: 0L,
    )
    // 至少 10 KB/s，避免空闲时噪声被放大成满屏波动
    val axisMax = niceCeiling(maxOf(peak, 10_000L).toDouble())

    AppleBentoSurface(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            CardTitle(icon = Icons.Rounded.SwapVert, title = "网络吞吐") {
                Label("${traffic.windowLabel} · 峰值 ${formatRate(peak).let { "${it.first} ${it.second}" }}")
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                RateColumn(
                    icon = Icons.Rounded.ArrowDownward,
                    label = "下行",
                    bytesPerSecond = traffic.downloadBps,
                    totalBytes = traffic.downloadTotalBytes,
                    capBps = traffic.downloadCapBps,
                    accent = colors.downloadAccent,
                    modifier = Modifier.weight(1f)
                )
                RateColumn(
                    icon = Icons.Rounded.ArrowUpward,
                    label = "上行",
                    bytesPerSecond = traffic.uploadBps,
                    totalBytes = traffic.uploadTotalBytes,
                    capBps = traffic.uploadCapBps,
                    accent = colors.uploadAccent,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            TrafficChart(
                download = traffic.downloadHistory,
                upload = traffic.uploadHistory,
                capacity = traffic.historyCapacity,
                axisMax = axisMax,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun RateColumn(
    icon: ImageVector,
    label: String,
    bytesPerSecond: Long,
    totalBytes: Long,
    capBps: Long?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = RouterOsTheme.colors
    val (value, unit) = formatRate(bytesPerSecond)
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(13.dp)
            )
            Label(label, color = colors.textSecondary, size = 12.sp)
        }
        BigValue(value = value, unit = unit, unitColor = accent)
        val detail = buildString {
            if (capBps != null && capBps > 0) {
                val percent = (bytesPerSecond * 100 / capBps).coerceIn(0, 999)
                append("占用 $percent% · ")
            }
            append("累计 ${formatSize(totalBytes)}")
        }
        Label(detail)
    }
}

/**
 * 上下行流量曲线。数据从右向左排列，历史不足时左侧留空；
 * Y 轴使用整齐刻度，并在网格线上标注数值。
 */
@Composable
private fun TrafficChart(
    download: List<Long>,
    upload: List<Long>,
    capacity: Int,
    axisMax: Double,
    modifier: Modifier = Modifier,
) {
    val colors = RouterOsTheme.colors
    val downloadColor = colors.downloadAccent
    val uploadColor = colors.uploadAccent
    val gridColor = colors.chartGridLine
    val axisColor = colors.chartBaseAxis
    val slots = maxOf(capacity, download.size, upload.size, 2)

    Row(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            listOf(0.5f, 1f).forEach { fraction ->
                val y = size.height * (1f - fraction)
                drawDashedHorizontalLine(gridColor, 0f, size.width, y, 1f, 4.dp.toPx(), 4.dp.toPx())
            }
            drawLine(axisColor, Offset(0f, size.height), Offset(size.width, size.height), 1f)

            drawSeries(download, slots, axisMax, downloadColor, fill = true)
            drawSeries(upload, slots, axisMax, uploadColor, fill = false)
        }

        // 刻度放在独立留白中，避免文字覆盖曲线。
        val (maxValue, maxUnit) = formatRate(axisMax.toLong())
        val (midValue, midUnit) = formatRate((axisMax / 2).toLong())
        Box(
            modifier = Modifier
                .width(70.dp)
                .fillMaxHeight()
                .padding(start = 6.dp)
        ) {
            AxisLabel("$maxValue $maxUnit", Modifier.align(Alignment.TopStart))
            AxisLabel("$midValue $midUnit", Modifier.align(Alignment.CenterStart))
        }
    }
}

@Composable
private fun AxisLabel(text: String, modifier: Modifier) {
    Text(
        text = text,
        color = RouterOsTheme.colors.textMuted,
        fontSize = 10.sp,
        fontFamily = DashboardDisplayFontFamily,
        maxLines = 1,
        modifier = modifier
    )
}

/**
 * 手动分段绘制水平虚线。Android 8 及以下的硬件加速不支持 drawLine 上的
 * PathEffect，会退化成实线，因此不依赖 dashPathEffect。
 */
private fun DrawScope.drawDashedHorizontalLine(
    color: Color,
    startX: Float,
    endX: Float,
    y: Float,
    strokeWidth: Float,
    dash: Float,
    gap: Float,
) {
    var x = startX
    while (x < endX) {
        drawLine(color, Offset(x, y), Offset(minOf(x + dash, endX), y), strokeWidth)
        x += dash + gap
    }
}

private fun DrawScope.drawSeries(
    values: List<Long>,
    slots: Int,
    axisMax: Double,
    color: Color,
    fill: Boolean,
) {
    if (values.size < 2) return
    val step = size.width / (slots - 1)
    val startX = size.width - step * (values.size - 1)
    val line = Path()
    values.forEachIndexed { index, value ->
        val x = startX + step * index
        val y = size.height * (1f - (value / axisMax).toFloat().coerceIn(0f, 1f))
        if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
    }
    if (fill) {
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(startX, size.height)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f)))
        )
    }
    drawPath(
        path = line,
        color = color,
        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

// ---------------------------------------------------------------------------
// 链路质量卡片
// ---------------------------------------------------------------------------

@Composable
private fun AppleQualityCard(quality: QualityState, modifier: Modifier = Modifier) {
    val colors = RouterOsTheme.colors
    val level = latencyLevel(quality.latencyMs, quality.packetLossPercent)
    val levelColor = level.color(colors.statusOnline)
    val statusText = when {
        quality.latencyMs == null -> "探测中"
        (quality.packetLossPercent ?: 0) > 0 -> "丢包 ${quality.packetLossPercent}%"
        level == AlertLevel.CRITICAL -> "高延迟"
        level == AlertLevel.WARNING -> "波动"
        else -> "良好"
    }

    AppleBentoSurface(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            CardTitle(icon = Icons.Rounded.Speed, title = "链路质量") {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(levelColor, CircleShape)
                )
                Label(statusText, color = levelColor, size = 12.sp)
            }

            LatencyQualitySection(
                quality = quality,
                levelColor = levelColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.2f),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.gridDivider)
            )

            // 表容量只参与告警判断；图表使用真实连接数范围，避免百万上限压平变化。
            val connRatio = quality.activeConnections?.let { active ->
                quality.maxConnections?.takeIf { it > 0 }?.let { (active * 100 / it).toInt() }
            } ?: 0
            val connLevel = usageLevel(connRatio, warning = 60, critical = 80)
            ConnectionQualitySection(
                quality = quality,
                lineColor = connLevel.color(colors.ramMetric),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.1f),
            )
        }
    }
}

@Composable
private fun LatencyQualitySection(
    quality: QualityState,
    levelColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = RouterOsTheme.colors
    val history = quality.latencyHistory
    val min = history.minOrNull()
    val average = history.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    val max = history.maxOrNull()
    Column(
        modifier = modifier.padding(vertical = 5.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Label("实时延迟 · ${quality.pingTarget}", color = colors.textSecondary, size = 12.sp)
                BigValue(
                    value = quality.latencyMs?.toString() ?: "--",
                    unit = "ms",
                    unitColor = levelColor,
                    valueSize = 26.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Label(
                    text = "丢包 ${quality.packetLossPercent ?: 0}%",
                    color = if ((quality.packetLossPercent ?: 0) > 0) levelColor else colors.textMuted,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (min != null && average != null && max != null) {
                        "低 $min · 均 $average · 高 $max"
                    } else {
                        "等待采样"
                    },
                    color = colors.textPrimary,
                    fontSize = 11.sp,
                    fontFamily = DashboardDisplayFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        LatencyTrendChart(
            values = history,
            color = levelColor,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun ConnectionQualitySection(
    quality: QualityState,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = RouterOsTheme.colors
    val history = quality.connectionHistory
    val first = history.firstOrNull()
    val last = history.lastOrNull()
    val delta = if (first != null && last != null) last - first else null
    val range = if (history.isNotEmpty()) {
        "${formatCount(history.min())}–${formatCount(history.max())}"
    } else {
        "等待采样"
    }
    Column(
        modifier = modifier.padding(vertical = 5.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Label("活跃连接 · 近 15 分钟", color = colors.textSecondary, size = 12.sp)
                BigValue(
                    value = quality.activeConnections?.let(::formatCount) ?: "--",
                    unit = "",
                    unitColor = colors.textSecondary,
                    valueSize = 26.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Label(
                    text = delta?.let {
                        when {
                            it > 0 -> "较起点 +${formatCount(it)}"
                            it < 0 -> "较起点 -${formatCount(-it)}"
                            else -> "保持稳定"
                        }
                    } ?: "趋势采样中",
                    color = colors.textMuted,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "范围 $range",
                    color = colors.textPrimary,
                    fontSize = 11.sp,
                    fontFamily = DashboardDisplayFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        ConnectionTrendBars(
            values = history,
            color = lineColor,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

/**
 * 延迟走势：纵轴固定从 0ms 起，避免放大微小抖动；虚线标出 80ms 告警阈值。
 * 刻度直接画在右侧留白中，保证在小高度下也完整可见。
 */
@Composable
private fun LatencyTrendChart(values: List<Int>, color: Color, modifier: Modifier = Modifier) {
    val colors = RouterOsTheme.colors
    val textMeasurer = rememberTextMeasurer()
    val axisMax = niceCeiling(maxOf(100.0, (values.maxOrNull() ?: 0) * 1.1)).toFloat()
    val labelStyle = TextStyle(
        color = colors.textMuted,
        fontSize = 9.sp,
        fontFamily = DashboardDisplayFontFamily,
    )
    val warningStyle = labelStyle.copy(color = colors.warningAccent.copy(alpha = 0.9f))

    Canvas(modifier = modifier) {
        val gutter = 40.dp.toPx()
        val labelGap = 6.dp.toPx()
        val chartWidth = size.width - gutter
        val inset = 6.dp.toPx()
        val chartTop = inset
        val chartBottom = size.height - inset
        val chartHeight = chartBottom - chartTop
        fun yOf(ms: Float) = chartBottom - chartHeight * (ms / axisMax).coerceIn(0f, 1f)

        fun label(text: String, y: Float, style: TextStyle) {
            val layout = textMeasurer.measure(text, style)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    chartWidth + labelGap,
                    (y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height),
                ),
            )
        }

        // 0ms 基线、上限网格线、80ms 告警虚线
        drawLine(colors.progressTrack, Offset(0f, chartBottom), Offset(chartWidth, chartBottom), 1.dp.toPx())
        drawDashedHorizontalLine(
            colors.chartGridLine, 0f, chartWidth, chartTop, 1f, 2.dp.toPx(), 4.dp.toPx(),
        )
        val warningY = yOf(80f)
        // 阈值说明与虚线同一行："80 ms ------"，不与曲线和右侧刻度争位置
        val warningLayout = textMeasurer.measure("80 ms", warningStyle)
        drawText(
            textLayoutResult = warningLayout,
            topLeft = Offset(0f, warningY - warningLayout.size.height / 2f),
        )
        drawDashedHorizontalLine(
            colors.warningAccent.copy(alpha = 0.6f),
            warningLayout.size.width + 4.dp.toPx(),
            chartWidth,
            warningY,
            1.dp.toPx(),
            4.dp.toPx(),
            3.dp.toPx(),
        )

        label("${axisMax.toInt()} ms", chartTop, labelStyle)
        label("0 ms", chartBottom, labelStyle)

        if (values.isEmpty()) return@Canvas
        val lastY = yOf(values.last().toFloat())
        if (values.size >= 2) {
            val step = chartWidth / (values.size - 1)
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = step * index
                val y = yOf(value.toFloat())
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        drawCircle(color = color, radius = 2.5.dp.toPx(), center = Offset(chartWidth, lastY))
    }
}

/** 连接数用范围柱状图表达密度变化；均值线提供趋势基准。 */
@Composable
private fun ConnectionTrendBars(values: List<Long>, color: Color, modifier: Modifier = Modifier) {
    val trackColor = RouterOsTheme.colors.progressTrack
    Canvas(modifier = modifier) {
        if (values.isEmpty()) {
            drawLine(trackColor, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
            return@Canvas
        }
        val min = values.min()
        val max = values.max()
        val range = (max - min).coerceAtLeast(1L).toFloat()
        val average = values.average().toFloat()
        val averageY = size.height * (1f - (average - min) / range)
        drawDashedHorizontalLine(
            trackColor, 0f, size.width, averageY, 1.dp.toPx(), 3.dp.toPx(), 3.dp.toPx(),
        )

        val step = size.width / values.size
        val stroke = (step * 0.48f).coerceIn(1.5.dp.toPx(), 4.dp.toPx())
        values.forEachIndexed { index, value ->
            val normalized = ((value - min) / range).coerceIn(0f, 1f)
            val top = size.height * (1f - (0.18f + normalized * 0.82f))
            val x = step * index + step / 2
            drawLine(
                color = color.copy(alpha = if (index == values.lastIndex) 1f else 0.55f),
                start = Offset(x, size.height),
                end = Offset(x, top),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 底部系统资源与 WAN 信息条
// ---------------------------------------------------------------------------

@Composable
private fun AppleSystemStrip(system: SystemState, wan: WanState, modifier: Modifier = Modifier) {
    // 不再用外层卡片包裹，四个指标块与上方卡片同级，避免“卡片套卡片”。
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CardGap),
    ) {
        val tile = Modifier.fillMaxHeight()
        ResourceMetric(
            title = "CPU",
            icon = Icons.Rounded.Memory,
            percent = system.cpuPercent,
            detail = "${system.cpuCores} 核心",
            modifier = tile.weight(1f),
        )
        ResourceMetric(
            percent = system.ramPercent,
            title = "内存",
            icon = Icons.Rounded.DeveloperBoard,
            detail = "${formatCompactSize(system.ramUsedBytes)} / ${formatCompactSize(system.ramTotalBytes)}",
            modifier = tile.weight(1f),
        )
        ResourceMetric(
            percent = system.diskPercent,
            title = "存储",
            icon = Icons.Rounded.Storage,
            detail = "${formatCompactSize(system.diskUsedBytes)} / ${formatCompactSize(system.diskTotalBytes)}",
            modifier = tile.weight(1f),
        )
        // 四个指标块等宽，保持底部节奏统一。
        WanMetric(wan = wan, modifier = tile.weight(1f))
    }
}

@Composable
private fun ResourceMetric(
    title: String,
    icon: ImageVector,
    percent: Int,
    detail: String,
    modifier: Modifier = Modifier,
) {
    val colors = RouterOsTheme.colors
    val accent = usageLevel(percent).color(colors.textSecondary)
    AppleBentoSurface(modifier = modifier, horizontalPadding = 12, verticalPadding = 8) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetricTitle(icon = icon, title = title)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "$percent%",
                    color = usageLevel(percent).color(colors.textPrimary),
                    fontSize = 15.sp,
                    fontFamily = DashboardDisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = detail,
                color = colors.textMuted,
                fontSize = 10.sp,
                fontFamily = DashboardDisplayFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.progressTrack),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((percent / 100f).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(accent),
                )
            }
        }
    }
}

@Composable
private fun MetricTitle(icon: ImageVector, title: String) {
    val colors = RouterOsTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(12.dp),
        )
        Label(title, color = colors.textSecondary, size = 11.sp)
    }
}

/** WAN 平时用中性色，只用小圆点表示在线；离线时整体转红。 */
@Composable
private fun WanMetric(wan: WanState, modifier: Modifier = Modifier) {
    val colors = RouterOsTheme.colors
    val dotColor = if (wan.running) colors.statusOnline else colors.statusOffline
    AppleBentoSurface(modifier = modifier, horizontalPadding = 12, verticalPadding = 8) {
        // 与资源卡片完全同构：标题行右侧为主值，第二行为同字号同颜色的明细，
        // 第三行对应进度条位置留空，保证四张卡片的文字基线对齐。
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetricTitle(icon = Icons.Rounded.Language, title = "WAN")
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Spacer(modifier = Modifier.width(4.dp))
                // 中文字形比数字更“重”，用较小字号与右上角的百分比视觉等重。
                Text(
                    text = if (wan.running) "在线" else "离线",
                    color = if (wan.running) colors.textPrimary else colors.statusOffline,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = wan.ipAddress?.takeIf { it.isNotBlank() } ?: "--",
                color = colors.textMuted,
                fontSize = 10.sp,
                fontFamily = DashboardDisplayFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// 预览：标准 640×360dp、较窄横屏、长文本与告警状态
// ---------------------------------------------------------------------------

private fun previewState(alert: Boolean = false): RouterOsDashboardState {
    val download = List(240) { i -> (400_000L + (i % 37) * 90_000L) * if (i in 180..200) 9 else 1 }
    val upload = List(240) { i -> 60_000L + (i % 23) * 12_000L }
    return RouterOsDashboardState(
        deviceName = if (alert) "Home-Gateway-HEX-S-Building-A-Floor-3" else "MikroTik",
        version = "v7.25",
        connected = true,
        uptime = "5天 3时",
        temperatureC = if (alert) 78f else 46f,
        accessHint = "访问码 559926",
        staleHint = if (alert) "数据 42 秒前" else null,
        traffic = TrafficState(
            downloadBps = download.last(),
            uploadBps = upload.last(),
            downloadTotalBytes = 98_800_000_000L,
            uploadTotalBytes = 12_480_000_000L,
            downloadHistory = download,
            uploadHistory = upload,
            historyCapacity = 300,
            downloadCapBps = 125_000_000L,
            uploadCapBps = 6_250_000L,
        ),
        quality = QualityState(
            latencyMs = if (alert) 168 else 31,
            packetLossPercent = if (alert) 7 else 0,
            pingTarget = "223.5.5.5",
            latencyHistory = List(40) { 28 + (it * 7) % 11 },
            activeConnections = if (alert) 52_000 else 195,
            maxConnections = if (alert) 65_536 else 1_048_576,
            connectionHistory = List(40) { 150L + (it * 13) % 60 },
        ),
        system = SystemState(
            cpuPercent = if (alert) 91 else 7,
            cpuCores = 4,
            ramPercent = 13,
            ramUsedBytes = 273_000_000L,
            ramTotalBytes = 1_073_741_824L,
            diskPercent = 2,
            diskUsedBytes = 23_000_000L,
            diskTotalBytes = 1_073_741_824L,
        ),
        wan = WanState(interfaceName = "pppoe-out1", ipAddress = "153.3.45.157", running = true),
    )
}

@Preview(widthDp = 640, heightDp = 360, name = "标准 640×360")
@Composable
private fun AppleScreenPreview() {
    RouterOsCompactAppleScreen(state = remember { previewState() })
}

@Preview(widthDp = 560, heightDp = 340, fontScale = 1.15f, name = "窄屏 + 大字体 + 告警")
@Composable
private fun AppleScreenNarrowAlertPreview() {
    RouterOsCompactAppleScreen(state = remember { previewState(alert = true) })
}
