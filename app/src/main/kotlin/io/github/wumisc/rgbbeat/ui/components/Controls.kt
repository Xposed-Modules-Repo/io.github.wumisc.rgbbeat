package io.github.wumisc.rgbbeat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wumisc.rgbbeat.ui.theme.LocalPalette
import io.github.wumisc.rgbbeat.ui.theme.RgbPalette
import kotlin.math.roundToInt

/** 分区标题：一条霓虹短线 + 大字标题，夜店灯牌感 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, palette: RgbPalette = LocalPalette.current) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 16.dp, height = 2.dp)) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(palette.neon, palette.neonAlt)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = palette.ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
        )
    }
}

/** 玻璃内的设置行：标题 + 说明 + 右侧控件 */
@Composable
fun SettingRow(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val palette = LocalPalette.current
    Row(
        modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
            if (description != null) {
                Spacer(Modifier.height(3.dp))
                Text(description, color = palette.inkDim, fontSize = 11.5.sp, lineHeight = 16.sp)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** 液态开关：轨道渐变 + 带辉光的滑块 */
@Composable
fun NeonSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    palette: RgbPalette = LocalPalette.current,
    onCheckedChange: (Boolean) -> Unit,
) {
    val trackColor by animateColorAsState(
        if (checked) palette.neon else palette.inkDim.copy(alpha = 0.22f),
        label = "track",
    )
    val thumbOffset by animateDpAsState(
        if (checked) 22.dp else 2.dp,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "thumb",
    )
    Box(
        modifier
            .width(48.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(
                if (checked) Brush.horizontalGradient(listOf(palette.neon.copy(alpha = 0.9f), palette.neonAlt.copy(alpha = 0.65f)))
                else Brush.horizontalGradient(listOf(Color(0x22FFFFFF), Color(0x11FFFFFF)))
            )
            .border(1.dp, palette.glassBorder.copy(alpha = 0.5f), RoundedCornerShape(percent = 50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(thumbOffset.roundToPx(), 0) }
                .size(24.dp)
                .graphicsLayer {
                    if (checked) {
                        shadowElevation = 6f
                        spotShadowColor = trackColor
                    }
                }
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(listOf(Color.White, Color(0xFFE8E4EE)))
                )
                .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
        )
        if (checked) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(palette.neon.copy(alpha = 0.35f), Color.Transparent),
                        Offset(size.width * 0.75f, size.height * 0.5f), size.height * 0.9f,
                    ),
                    size.height * 0.9f, Offset(size.width * 0.75f, size.height * 0.5f),
                )
            }
        }
    }
}

/** 分段控制器：玻璃里的液体指示块，切换时横向滑动 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    palette: RgbPalette = LocalPalette.current,
) {
    val anim by animateFloatAsState(selectedIndex.toFloat(), spring(dampingRatio = 0.8f), label = "seg")
    Box(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x14FFFFFF))
            .border(1.dp, palette.glassBorder.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth / options.size
            Box(
                Modifier
                    .width(w)
                    .fillMaxHeight()
                    .padding(3.dp)
                    .offset { androidx.compose.ui.unit.IntOffset((w * anim).roundToPx(), 0) }
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        Brush.linearGradient(listOf(palette.neon.copy(alpha = 0.85f), palette.neonAlt.copy(alpha = 0.6f)))
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(11.dp))
            )
            Row(Modifier.fillMaxSize()) {
                options.forEachIndexed { i, label ->
                    val on = i == selectedIndex
                    Box(
                        Modifier
                            .width(w)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onSelect(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (on) palette.onColor(palette.neon) else palette.inkDim,
                            fontSize = 12.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 霓虹滑杆：按下即跳值、拖动实时跟随，松手才落盘（避免每帧写 SharedPreferences）。
 * 轨道分两段：已选区间是霓虹渐变，剩余是低透明白。
 */
@Composable
fun NeonSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
    format: (Float) -> String = { it.roundToInt().toString() },
    steps: Int = 0,
    palette: RgbPalette = LocalPalette.current,
) {
    var local by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { if (kotlin.math.abs(local - value) > 0.51f) local = value }
    val onChangeState = rememberUpdatedState(onChange)
    val onFinishState = rememberUpdatedState(onFinished)

    val span = range.endInclusive - range.start
    val fraction = ((local - range.start) / span).coerceIn(0f, 1f)

    fun pick(x: Float, width: Float) {
        var f = (x / width).coerceIn(0f, 1f)
        var v = range.start + f * span
        if (steps > 0) {
            val stepSize = span / steps
            v = (kotlin.math.round((v - range.start) / stepSize) * stepSize + range.start)
            f = ((v - range.start) / span).coerceIn(0f, 1f)
        }
        local = v
        onChangeState.value(v)
    }

    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = palette.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(format(local), color = palette.neon, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        var trackWidth by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        Box(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .onSizeChanged { trackWidth = it.width }
                .pointerInput(range, steps) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        pick(down.position.x, size.width.toFloat())
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull()
                            if (ch != null) {
                                pick(ch.position.x, size.width.toFloat())
                                ch.consume()
                            }
                        } while (event.changes.any { it.pressed })
                        onFinishState.value(local)
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                val r = size.height / 2
                drawRoundRect(Color(0x22FFFFFF), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
                val fullW = size.width * fraction
                drawRoundRect(
                    Brush.horizontalGradient(listOf(palette.neonAlt, palette.neon)),
                    size = Size(fullW.coerceAtLeast(8f), size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(palette.neon.copy(alpha = 0.45f), Color.Transparent),
                        Offset(fullW, r), 26f,
                    ),
                    26f, Offset(fullW, r),
                )
            }
            Box(
                Modifier
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            (fraction * trackWidth - with(density) { 11.dp.toPx() }).roundToInt(),
                            0,
                        )
                    }
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFEDE9F2))))
                    .border(1.5.dp, palette.neon.copy(alpha = 0.55f), CircleShape)
            )
        }
    }
}

/** 选择胶囊 */
@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    palette: RgbPalette = LocalPalette.current,
) {
    val bg by animateColorAsState(
        if (selected) palette.neon.copy(alpha = 0.22f) else Color(0x0DFFFFFF),
        label = "chipBg",
    )
    val border by animateColorAsState(
        if (selected) palette.neon.copy(alpha = 0.85f) else palette.glassBorder.copy(alpha = 0.45f),
        label = "chipBorder",
    )
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = if (selected) palette.ink else palette.inkDim,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** 光效色板：8 个固定色相 + 一个"随节奏"彩虹 */
@Composable
fun ColorDotRow(selectedHue: Float, modifier: Modifier = Modifier, onPick: (Float) -> Unit) {
    val palette = LocalPalette.current
    val hues = listOf(-1f, 300f, 190f, 230f, 330f, 30f, 130f, 0f)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        hues.forEach { h ->
            val isSel = kotlin.math.abs(selectedHue - h) < 1f
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (h < 0) Brush.sweepGradient(
                            listOf(
                                Color(0xFFFF2D9B), Color(0xFFFFD166), Color(0xFF45F08B),
                                Color(0xFF23E8FF), Color(0xFF8A5CFF), Color(0xFFFF2D9B),
                            )
                        ) else Brush.linearGradient(
                            listOf(
                                io.github.wumisc.rgbbeat.ui.art.hueColor(h, 0.9f, 1f),
                                io.github.wumisc.rgbbeat.ui.art.hueColor(h, 0.9f, 0.65f),
                            )
                        )
                    )
                    .border(
                        width = if (isSel) 2.5.dp else 1.dp,
                        color = if (isSel) palette.ink else Color.White.copy(alpha = 0.25f),
                        shape = CircleShape,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onPick(h) },
            )
        }
    }
}

/** 小数据胶囊 */
@Composable
fun StatPill(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x14000000))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(6.dp))
        Text(label, color = palette.inkDim, fontSize = 10.5.sp)
        Spacer(Modifier.width(5.dp))
        Text(value, color = palette.ink, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 幽灵按钮：玻璃描边，按下无涟漪，靠颜色变化反馈 */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    solid: Boolean = false,
    palette: RgbPalette = LocalPalette.current,
) {
    val tint = accent ?: palette.neon
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (solid) tint.copy(alpha = 0.22f) else Color(0x0DFFFFFF))
            .border(1.dp, tint.copy(alpha = if (solid) 0.85f else 0.4f), RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (solid) palette.ink else palette.inkDim, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
    }
}

/** 细能量条：实时显示节拍能量 */
@Composable
fun EnergyMeter(energy: Float, pulse: Float, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val bars = 22
    Canvas(modifier.fillMaxWidth().height(26.dp)) {
        val gap = size.width / bars
        for (i in 0 until bars) {
            val level = (energy * 1.35f - i * 0.045f + pulse * 0.25f).coerceIn(0f, 1f)
            val h = size.height * (0.18f + 0.82f * level)
            val x = gap * i + gap * 0.28f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(palette.neonAlt.copy(alpha = 0.9f), palette.neon.copy(alpha = 0.9f)),
                    startY = size.height - h, endY = size.height,
                ),
                topLeft = Offset(x, size.height - h),
                size = Size(gap * 0.44f, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(gap * 0.22f, gap * 0.22f),
            )
        }
    }
}
