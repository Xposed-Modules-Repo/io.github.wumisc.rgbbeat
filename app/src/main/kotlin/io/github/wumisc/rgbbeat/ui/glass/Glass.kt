package io.github.wumisc.rgbbeat.ui.glass

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wumisc.rgbbeat.ui.art.ArtKind
import io.github.wumisc.rgbbeat.ui.art.AuraBackdrop
import io.github.wumisc.rgbbeat.ui.art.SceneSurface
import io.github.wumisc.rgbbeat.ui.art.hueColor
import io.github.wumisc.rgbbeat.ui.live.LiveBeat
import io.github.wumisc.rgbbeat.ui.theme.LocalPalette
import io.github.wumisc.rgbbeat.ui.theme.RgbPalette
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val GlassShape = RoundedCornerShape(26.dp)
private val PillShape = RoundedCornerShape(percent = 50)

/**
 * 液态玻璃面板。
 *
 * 三层结构：
 *  1. 折射底：把背景场景重绘一份并高斯模糊（Android 12+ 走 RenderEffect，12 以下退化为半透明），
 *     玻璃的"透"是真实折射当前画面，而不是固定灰；
 *  2. 玻璃染色：上亮下暗的白色渐变 + 顶部高光条，模拟厚玻璃的折射厚度；
 *  3. 边缘：1dp 渐变描边，左上亮右下暗，做出圆角处的玻璃倒角。
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape,
    palette: RgbPalette = LocalPalette.current,
    blurRadius: Dp = 26.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    backdrop: (@Composable () -> Unit)? = null,
    tintTop: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val top = tintTop ?: palette.glassTop
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, palette.glassBottom)))
    ) {
        if (backdrop != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(blurRadius) else Modifier)
            ) { backdrop() }
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(top.copy(alpha = 0.92f), palette.glassBottom)))
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.42f)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            palette.glassHighlight.copy(alpha = if (palette.dark) 0.16f else 0.55f),
                            Color.Transparent,
                        )
                    )
                )
        )
        Box(
            Modifier
                .matchParentSize()
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            palette.glassBorder,
                            palette.glassBorder.copy(alpha = 0.35f),
                            palette.neon.copy(alpha = if (palette.dark) 0.18f else 0.10f),
                        )
                    ),
                    shape = shape,
                )
        )
        Column(Modifier.padding(contentPadding), content = content)
    }
}

// ------------------------------------------------------------------ 导航栏

enum class NavIcon { HOME, WAVE, SPARK }

data class NavItem(val label: String, val icon: NavIcon)

/**
 * 液态玻璃底部导航。
 *
 * 选中态不是"跳"过去，而是由 Pager 的实时滚动比例驱动：手指滑内容页时，
 * 玻璃胶囊以弹簧跟随，高速滑动时被拉伸（液体张力感），停下后回弹。
 * 选中项前景色按胶囊亮度自动切换，深色/浅色、任意色相下都不会糊。
 * 整条栏也支持横向拖动换页，拖动直接映射到 Pager 分页进度。
 */
@Composable
fun LiquidGlassNav(
    pagerState: PagerState,
    items: List<NavItem>,
    live: LiveBeat,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit = {},
) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()

    val rawOffset = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
        .coerceIn(0f, (items.size - 1).toFloat())
    val offset by animateFloatAsState(
        targetValue = rawOffset,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow),
        label = "navOffset",
    )
    val stretch = (abs(rawOffset - offset) * 0.9f).coerceIn(0f, 0.34f)
    val indicatorTint by animateColorAsState(hueColor(live.hue, 0.8f, 1f), label = "navTint")
    val onIndicator = palette.onColor(indicatorTint)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(62.dp)
            .clip(PillShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        palette.glassTop.copy(alpha = if (palette.dark) 0.72f else 0.92f),
                        palette.glassBottom.copy(alpha = if (palette.dark) 0.62f else 0.82f),
                    )
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(listOf(palette.glassBorder, palette.glassBorder.copy(alpha = 0.3f))),
                PillShape,
            )
            .pointerInput(items.size) {
                val itemPx = (size.width / items.size).toFloat().coerceAtLeast(1f)
                var startPage = 0f
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        startPage = pagerState.currentPage + pagerState.currentPageOffsetFraction
                        drag = 0f
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        drag += amount
                        val target = (startPage - drag / itemPx).coerceIn(0f, (items.size - 1).toFloat())
                        scope.launch { pagerState.scrollToPage(target.toInt(), target - target.toInt()) }
                    },
                    onDragEnd = {
                        val target = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                            .roundToInt().coerceIn(0, items.size - 1)
                        scope.launch { pagerState.animateScrollToPage(target) }
                    },
                )
            }
    ) {
        // 折射底：同一场景低频重绘，模糊后成为玻璃里的"色雾"
        Box(
            Modifier
                .matchParentSize()
                .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(20.dp) else Modifier)
        ) {
            AuraBackdrop(palette, live.hue, live.pulse, Modifier.fillMaxSize())
        }

        val itemWidth = maxWidth / items.size

        Box(
            Modifier
                .width(itemWidth - 8.dp)
                .fillMaxHeight()
                .padding(vertical = 6.dp)
                // 用 lambda 版 offset：胶囊跟随只触发布局阶段，不触发重组，滑动时不掉帧
                .offset { IntOffset((itemWidth * offset + 4.dp).roundToPx(), 0) }
                .graphicsLayer {
                    scaleX = 1f + stretch
                    scaleY = 1f - stretch * 0.22f
                }
                .clip(PillShape)
                .background(
                    Brush.linearGradient(
                        listOf(palette.neon.copy(alpha = 0.95f), indicatorTint.copy(alpha = 0.95f))
                    )
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.5f), palette.neon.copy(alpha = 0.2f))),
                    PillShape,
                )
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.height * (0.55f + 0.12f * live.pulse)
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.38f), Color.Transparent),
                        Offset(size.width * 0.28f, size.height * 0.2f), r,
                    ),
                    r, Offset(size.width * 0.28f, size.height * 0.2f),
                )
            }
        }

        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { index, item ->
                val selected = abs(rawOffset - index) < 0.5f
                val fg = if (selected) onIndicator else palette.inkDim
                Column(
                    modifier = Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .semantics {
                            contentDescription = item.label
                            this.selected = selected
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    NavGlyph(
                        icon = item.icon,
                        color = fg,
                        progress = if (selected) 1f else 0f,
                        modifier = Modifier.size(21.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = item.label,
                        color = fg,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        letterSpacing = 0.6.sp,
                    )
                }
            }
        }
    }
}

/** 手绘导航图标：不引图标库，线条粗细随选中态过渡 */
@Composable
fun NavGlyph(icon: NavIcon, color: Color, progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = lerp(1.4f, 2.3f, progress)
        when (icon) {
            NavIcon.HOME -> {
                drawPath(
                    Path().apply {
                        moveTo(w * 0.08f, h * 0.46f)
                        lineTo(w * 0.5f, h * 0.10f)
                        lineTo(w * 0.92f, h * 0.46f)
                    },
                    color, style = Stroke(sw, cap = StrokeCap.Round),
                )
                drawPath(
                    Path().apply {
                        moveTo(w * 0.18f, h * 0.44f)
                        lineTo(w * 0.18f, h * 0.90f)
                        lineTo(w * 0.82f, h * 0.90f)
                        lineTo(w * 0.82f, h * 0.44f)
                    },
                    color, style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
            NavIcon.WAVE -> {
                listOf(0.28f, 0.52f, 0.78f, 0.46f, 0.66f).forEachIndexed { i, v ->
                    val x = w * (0.10f + i * 0.19f)
                    val half = h * v * (0.28f + 0.10f * progress)
                    drawLine(color, Offset(x, h * 0.5f - half), Offset(x, h * 0.5f + half), sw, StrokeCap.Round)
                }
            }
            NavIcon.SPARK -> {
                val cx = w * 0.5f
                val cy = h * 0.5f
                val r = w * 0.33f
                drawCircle(color, radius = r, center = Offset(cx, cy), style = Stroke(sw))
                drawLine(
                    color.copy(alpha = 0.45f + 0.55f * progress),
                    Offset(cx, cy - r * 0.6f),
                    Offset(cx, cy + r * 0.6f),
                    sw, StrokeCap.Round,
                )
                drawCircle(color, radius = sw * 0.75f, center = Offset(cx, cy - r * 0.62f))
            }
        }
    }
}
