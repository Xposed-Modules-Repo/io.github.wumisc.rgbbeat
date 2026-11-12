package io.github.wumisc.rgbbeat.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import io.github.wumisc.rgbbeat.ui.theme.RgbPalette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 程序化场景引擎。
 *
 * 之所以不用图片素材：动态背景要做到"跟着节拍呼吸"，位图做不到逐帧重绘的丝光、
 * 轮廓光与粒子；程序化绘制还能把 APK 从几十 MB 的视频素材里解放出来。
 *
 * 四种场景对应产品叙事：
 *  - SILK   丝光织物（深色=黑丝，浅色=白丝）—— 首页氛围底
 *  - MUSCLE 美系肌肉男模轮廊（深色）—— 关于页
 *  - SUIT   中山装男模轮廓（浅色）—— 关于页
 *  - DUO    丝光 / 力量 对撞分屏 —— 功能页
 */
enum class ArtKind { SILK, MUSCLE, SUIT, DUO }

private val Transparent = Color(0x00000000)

fun sceneForHome(dark: Boolean): ArtKind = ArtKind.SILK

fun sceneForAbout(dark: Boolean): ArtKind = if (dark) ArtKind.MUSCLE else ArtKind.SUIT

fun sceneForEffects(): ArtKind = ArtKind.DUO

/** 背景画布：所有场景共用同一时间轴与节拍脉冲 */
fun DrawScope.drawScene(
    kind: ArtKind,
    palette: RgbPalette,
    t: Float,
    pulse: Float,
    energy: Float,
    breath: Float,
    hue: Float,
    motionX: Float,
) {
    when (kind) {
        ArtKind.SILK -> drawSilk(palette, t, pulse, energy, breath, hue)
        ArtKind.MUSCLE -> drawMuscle(palette, t, pulse, energy, breath, hue)
        ArtKind.SUIT -> drawSuit(palette, t, pulse, energy, breath)
        ArtKind.DUO -> drawDuo(palette, t, pulse, energy, breath, hue)
    }
}

// ---------------------------------------------------------------- 通用部件

private fun DrawScope.vignette(p: RgbPalette, strength: Float = 0.9f) {
    val c = Offset(size.width * 0.5f, size.height * 0.46f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Transparent, Transparent, p.baseBottom.copy(alpha = strength)),
            center = c,
            radius = size.maxDimension * 0.78f,
        ),
        radius = size.maxDimension * 0.78f,
        center = c,
    )
}

private fun DrawScope.scanLines(p: RgbPalette, t: Float, alphaScale: Float = 1f) {
    // 极细密水平线：模拟夜店灯带与织物经纬，同时给画面"胶片颗粒感"
    val gap = size.height / 96f
    var y = 0f
    while (y < size.height) {
        val a = (0.02f + 0.03f * (0.5f + 0.5f * sin(y * 0.06f + t * 2.2f))) * alphaScale
        drawLine(
            color = p.ink.copy(alpha = a),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
        y += gap
    }
}

/** 短裤/裤袜的腿部剪影：从髋部收到踝部，带一点膝部内收，避免呆板直筒 */
private fun legPath(
    cx: Float,
    top: Float,
    bottom: Float,
    topW: Float,
    botW: Float,
    dir: Float,
    knee: Float,
): Path {
    val h = bottom - top
    val p = Path()
    p.moveTo(cx - topW * 0.5f * dir, top)
    p.cubicTo(
        cx - topW * 0.62f * dir, top + h * 0.28f,
        cx - topW * 0.52f * dir - knee, top + h * 0.58f,
        cx - botW * 0.5f * dir - knee * 0.4f, bottom,
    )
    p.lineTo(cx + botW * 0.5f * dir - knee * 0.4f, bottom)
    p.cubicTo(
        cx + topW * 0.52f * dir - knee, top + h * 0.58f,
        cx + topW * 0.62f * dir, top + h * 0.28f,
        cx + topW * 0.5f * dir, top,
    )
    p.close()
    return p
}

// ---------------------------------------------------------------- 丝光场景

private fun DrawScope.drawSilk(
    p: RgbPalette,
    t: Float,
    pulse: Float,
    energy: Float,
    breath: Float,
    hue: Float,
) {
    val w = size.width
    val h = size.height
    val accent = hueColor(hue, if (p.dark) 0.85f else 0.55f, if (p.dark) 1f else 0.7f)

    drawRect(Brush.verticalGradient(listOf(p.baseTop, p.baseBottom)))

    // 两侧环境光：左冷右暖，形成舞台边光
    drawCircle(
        Brush.radialGradient(
            listOf(accent.copy(alpha = if (p.dark) 0.22f else 0.16f), Transparent),
            Offset(w * 0.88f, h * 0.14f), h * 0.62f,
        ),
        h * 0.62f, Offset(w * 0.88f, h * 0.14f),
    )
    drawCircle(
        Brush.radialGradient(
            listOf(p.neonAlt.copy(alpha = if (p.dark) 0.18f else 0.10f), Transparent),
            Offset(w * 0.1f, h * 0.42f), h * 0.5f,
        ),
        h * 0.5f, Offset(w * 0.1f, h * 0.42f),
    )

    val top = h * 0.18f
    val bottom = h * 1.06f
    val hipW = w * 0.30f
    val ankleW = w * 0.10f
    val cx = w * 0.5f
    val knee = w * 0.03f
    val leftLeg = legPath(cx - hipW * 0.46f, top, bottom, hipW, ankleW, 1f, knee)
    val rightLeg = legPath(cx + hipW * 0.46f, top, bottom, hipW, ankleW, 1f, -knee)

    // 织物本体：深色用近黑带紫，浅色用珍珠白带暖灰
    val bodyTop = if (p.dark) Color(0xFF211523) else Color(0xFFFDFBF8)
    val bodyBottom = if (p.dark) Color(0xFF050207) else Color(0xFFD9CFC4)
    for (leg in listOf(leftLeg, rightLeg)) {
        drawPath(
            leg,
            Brush.verticalGradient(listOf(bodyTop, bodyBottom), top, bottom),
        )
    }

    // 丝袜纹理：经纬两层。纵向细丝 + 横向浅织纹，clip 到腿部内，做出"半透明透光"观感
    clipPath(leftLeg) { silkTexture(p, t, pulse, top, bottom, breath) }
    clipPath(rightLeg) { silkTexture(p, t, pulse, top, bottom, breath) }

    // 轮廓光：先宽后窄两层，模拟边缘被霓虹灯打亮
    for (leg in listOf(leftLeg, rightLeg)) {
        drawPath(leg, accent.copy(alpha = 0.10f + 0.28f * pulse), style = Stroke(width = 14f, cap = StrokeCap.Round))
        drawPath(leg, accent.copy(alpha = 0.35f + 0.45f * pulse), style = Stroke(width = 2.5f, cap = StrokeCap.Round))
        drawPath(leg, Color.White.copy(alpha = if (p.dark) 0.10f else 0.55f), style = Stroke(width = 1f))
    }

    // 丝光扫过：一条沿 x 轴缓慢平移的高光带，节拍命中时更亮
    val sweepX = (w * (-0.4f) + (t * 0.06f / 8f).mod(1f) * w * 2.2f)
    val sheenColor = if (p.dark) Color.White.copy(alpha = 0.13f + 0.25f * pulse) else Color.White.copy(alpha = 0.5f)
    clipPath(leftLeg) { drawSheen(sweepX, sheenColor, top, bottom) }
    clipPath(rightLeg) { drawSheen(sweepX, sheenColor, top, bottom) }

    // 节拍涟漪：从髋部向下荡开的横向亮环
    if (pulse > 0.02f) {
        val ringY = top + (1f - pulse) * h * 0.8f
        drawPath(
            leftLeg,
            Brush.verticalGradient(
                listOf(Transparent, accent.copy(alpha = 0.22f * pulse), Transparent),
                ringY - h * 0.12f, ringY + h * 0.12f,
            ),
            blendMode = BlendMode.Plus,
        )
        drawPath(
            rightLeg,
            Brush.verticalGradient(
                listOf(Transparent, accent.copy(alpha = 0.22f * pulse), Transparent),
                ringY - h * 0.12f, ringY + h * 0.12f,
            ),
            blendMode = BlendMode.Plus,
        )
    }

    // 底部舞台雾
    drawRect(
        Brush.verticalGradient(
            listOf(Transparent, if (p.dark) Color(0xFF060109) else Color(0xFFE7DED4)),
            h * 0.62f, h,
        ),
    )
    scanLines(p, t, if (p.dark) 0.7f else 0.35f)
    vignette(p, if (p.dark) 0.85f else 0.55f)
}

private fun DrawScope.silkTexture(p: RgbPalette, t: Float, pulse: Float, top: Float, bottom: Float, breath: Float) {
    val threads = 58
    val fiber = if (p.dark) Color(0xFFB99FC4) else Color(0xFF8B7F73)
    for (i in 0 until threads) {
        val baseX = size.width * (i / threads.toFloat())
        val phase = t * 0.85f + i * 0.42f
        val dx = sin(phase) * 6f * (1f + 0.6f * breath)
        val a = (0.05f + 0.10f * (0.5f + 0.5f * sin(phase * 1.7f))) * (0.65f + 0.55f * pulse)
        drawLine(
            color = fiber.copy(alpha = if (p.dark) a else a * 0.55f),
            start = Offset(baseX + dx, top),
            end = Offset(baseX - dx * 0.6f, bottom),
            strokeWidth = 1.1f,
        )
    }
    // 横向织纹
    var y = top
    while (y < bottom) {
        drawLine(
            color = fiber.copy(alpha = if (p.dark) 0.035f else 0.02f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
        y += size.height * 0.012f
    }
}

private fun DrawScope.drawSheen(x: Float, color: Color, top: Float, bottom: Float) {
    val band = size.width * 0.5f
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(Transparent, color, Transparent),
            x - band * 0.5f, x + band * 0.5f,
        ),
        topLeft = Offset(0f, top),
        size = Size(size.width, bottom - top),
        blendMode = BlendMode.Plus,
    )
}

// ---------------------------------------------------------------- 力量（肌肉）场景

private fun DrawScope.drawMuscle(
    p: RgbPalette,
    t: Float,
    pulse: Float,
    energy: Float,
    breath: Float,
    hue: Float,
) {
    val w = size.width
    val h = size.height
    val rim = if (p.dark) Color(0xFFFF7A52) else p.gold
    val accent = hueColor(hue, 0.9f, 1f)

    drawRect(Brush.verticalGradient(listOf(Color(0xFF1B050C), Color(0xFF040003))))
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0x33FF2D55), Transparent),
            Offset(w * 0.5f, h * 0.3f), h * 0.7f,
        ),
        h * 0.7f, Offset(w * 0.5f, h * 0.3f),
    )

    // 躯干：宽肩 → 收腰的 V 字剪影
    val cx = w * 0.5f
    val shoulderY = h * 0.2f
    val shoulderW = w * 0.62f
    val waistY = h * 0.78f
    val waistW = w * 0.26f
    val torso = Path().apply {
        moveTo(cx - shoulderW * 0.5f, shoulderY + h * 0.03f)
        cubicTo(cx - shoulderW * 0.62f, shoulderY + h * 0.16f, cx - waistW * 0.72f, waistY - h * 0.12f, cx - waistW * 0.5f, waistY)
        lineTo(cx + waistW * 0.5f, waistY)
        cubicTo(cx + waistW * 0.72f, waistY - h * 0.12f, cx + shoulderW * 0.62f, shoulderY + h * 0.16f, cx + shoulderW * 0.5f, shoulderY + h * 0.03f)
        close()
    }

    withTransform({
        val s = 1f + 0.012f * breath
        scale(s, s, pivot = Offset(cx, waistY))
    }) {
        drawPath(
            torso,
            Brush.verticalGradient(
                listOf(Color(0xFF2B0A12), Color(0xFF0A0205), Color(0xFF040003)),
                shoulderY, waistY,
            ),
        )
        // 胸/腹肌轮廓：几条弧线 + 中线，随着脉冲亮起
        val lineAlpha = (0.10f + 0.35f * pulse)
        val contour = Color(0xFFFF9C7A)
        // 胸线
        drawLine(contour.copy(alpha = lineAlpha), Offset(cx - w * 0.16f, h * 0.33f), Offset(cx + w * 0.16f, h * 0.33f), 3f, StrokeCap.Round)
        drawLine(contour.copy(alpha = lineAlpha * 0.8f), Offset(cx - w * 0.14f, h * 0.37f), Offset(cx - w * 0.02f, h * 0.42f), 2.5f)
        drawLine(contour.copy(alpha = lineAlpha * 0.8f), Offset(cx + w * 0.14f, h * 0.37f), Offset(cx + w * 0.02f, h * 0.42f), 2.5f)
        // 腹肌
        var i = 0
        while (i < 3) {
            val y = h * (0.47f + i * 0.075f)
            val half = w * (0.105f - i * 0.012f)
            drawLine(contour.copy(alpha = lineAlpha * 0.75f), Offset(cx - half, y), Offset(cx - w * 0.012f, y), 2f)
            drawLine(contour.copy(alpha = lineAlpha * 0.75f), Offset(cx + w * 0.012f, y), Offset(cx + half, y), 2f)
            i++
        }
        // 中线
        drawLine(Color.White.copy(alpha = 0.05f + 0.12f * pulse), Offset(cx, h * 0.34f), Offset(cx, h * 0.74f), 1.5f)

        // 肩部高光
        val specA = 0.18f + 0.35f * pulse
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = specA), Transparent),
                Offset(cx - w * 0.22f, shoulderY + h * 0.03f), w * 0.2f,
            ),
            w * 0.2f, Offset(cx - w * 0.22f, shoulderY + h * 0.03f),
        )
    }

    // 轮廓光
    drawPath(torso, rim.copy(alpha = 0.25f + 0.4f * pulse), style = Stroke(18f, cap = StrokeCap.Round))
    drawPath(torso, (if (p.dark) Color(0xFFFFD9C2) else p.gold).copy(alpha = 0.5f + 0.4f * pulse), style = Stroke(2.5f, cap = StrokeCap.Round))

    // 节拍闪光
    if (pulse > 0.05f) {
        drawRect(accent.copy(alpha = 0.05f * pulse), blendMode = BlendMode.Plus)
        drawCircle(
            Brush.radialGradient(listOf(accent.copy(alpha = 0.22f * pulse), Transparent), Offset(w * 0.5f, h * 0.45f), h * 0.55f),
            h * 0.55f, Offset(w * 0.5f, h * 0.45f),
            blendMode = BlendMode.Plus,
        )
    }
    scanLines(p, t, 0.45f)
    vignette(p, 0.9f)
}

// ---------------------------------------------------------------- 中山装场景

private fun DrawScope.drawSuit(
    p: RgbPalette,
    t: Float,
    pulse: Float,
    energy: Float,
    breath: Float,
) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(p.baseTop, p.baseBottom)))

    // 暖光与冷光分区，避免浅色画面发灰
    drawCircle(Brush.radialGradient(listOf(p.gold.copy(alpha = 0.16f), Transparent), Offset(w * 0.15f, h * 0.2f), h * 0.6f), h * 0.6f, Offset(w * 0.15f, h * 0.2f))
    drawCircle(Brush.radialGradient(listOf(Color(0x223C6FF0), Transparent), Offset(w * 0.9f, h * 0.5f), h * 0.5f), h * 0.5f, Offset(w * 0.9f, h * 0.5f))

    val cx = w * 0.5f
    val shoulderY = h * 0.17f
    val shoulderW = w * 0.66f
    val waistW = w * 0.46f
    val bottomY = h * 1.02f
    val bodyTop = Color(0xFF1A1D24)
    val bodyBottom = Color(0xFF0A0C11)

    val body = Path().apply {
        moveTo(cx - shoulderW * 0.5f, shoulderY)
        lineTo(cx + shoulderW * 0.5f, shoulderY)
        lineTo(cx + waistW * 0.5f, bottomY)
        lineTo(cx - waistW * 0.5f, bottomY)
        close()
    }
    withTransform({ scale(1f + 0.006f * breath, 1f, pivot = Offset(cx, bottomY)) }) {
        drawPath(body, Brush.verticalGradient(listOf(bodyTop, bodyBottom), shoulderY, bottomY))
    }

    // 立领：中山装最标志性的闭合领口
    val collar = Path().apply {
        moveTo(cx - w * 0.085f, shoulderY - h * 0.035f)
        lineTo(cx + w * 0.085f, shoulderY - h * 0.035f)
        lineTo(cx + w * 0.10f, shoulderY + h * 0.055f)
        lineTo(cx - w * 0.10f, shoulderY + h * 0.055f)
        close()
    }
    drawPath(collar, Color(0xFF232730))
    drawPath(collar, p.gold.copy(alpha = 0.55f), style = Stroke(1.5f))

    // 门襟与五颗扣：中轴一条细线 + 金扣
    drawLine(Color.White.copy(alpha = 0.10f), Offset(cx, shoulderY + h * 0.055f), Offset(cx, bottomY), 1.2f)
    for (i in 0 until 5) {
        val y = shoulderY + h * (0.09f + i * 0.105f)
        val glow = 0.6f + 0.4f * pulse
        drawCircle(p.gold.copy(alpha = 0.25f * glow), radius = 7f, center = Offset(cx, y))
        drawCircle(p.gold, radius = 3.2f, center = Offset(cx, y))
    }

    // 四个口袋
    val pocket = p.gold.copy(alpha = 0.30f)
    for (sx in listOf(-1f, 1f)) {
        drawRect(
            color = pocket,
            topLeft = Offset(cx + sx * w * 0.16f - w * 0.07f, shoulderY + h * 0.20f),
            size = Size(w * 0.14f, h * 0.055f),
            style = Stroke(1.2f),
        )
        drawRect(
            color = pocket,
            topLeft = Offset(cx + sx * w * 0.16f - w * 0.075f, shoulderY + h * 0.52f),
            size = Size(w * 0.15f, h * 0.05f),
            style = Stroke(1.2f),
        )
    }

    // 袖缝
    for (sx in listOf(-1f, 1f)) {
        drawLine(
            Color.White.copy(alpha = 0.06f),
            Offset(cx + sx * w * 0.26f, shoulderY + h * 0.02f),
            Offset(cx + sx * w * 0.20f, shoulderY + h * 0.30f),
            1.5f,
        )
    }

    // 金色扫光（节拍时沿肩线掠过一次）
    val sweep = ((t * 0.05f) / 4f).mod(1f)
    drawRect(
        Brush.horizontalGradient(
            listOf(Transparent, p.gold.copy(alpha = 0.16f + 0.18f * pulse), Transparent),
            w * (sweep - 0.3f), w * (sweep + 0.3f),
        ),
        blendMode = BlendMode.Plus,
    )
    scanLines(p, t, 0.3f)
    vignette(p, 0.45f)
}

// ---------------------------------------------------------------- 对撞分屏（功能页）

private fun DrawScope.drawDuo(
    p: RgbPalette,
    t: Float,
    pulse: Float,
    energy: Float,
    breath: Float,
    hue: Float,
) {
    val w = size.width
    val h = size.height
    // 斜切分屏：左丝光 / 右力量（浅色时换成中山装）
    val split = 0.52f
    val leftClip = Path().apply {
        moveTo(0f, 0f)
        lineTo(w * split + h * 0.08f, 0f)
        lineTo(w * split - h * 0.08f, h)
        lineTo(0f, h)
        close()
    }
    val rightClip = Path().apply {
        moveTo(w * split + h * 0.08f, 0f)
        lineTo(w, 0f)
        lineTo(w, h)
        lineTo(w * split - h * 0.08f, h)
        close()
    }

    clipPath(leftClip) {
        withTransform({ scale(1.25f, 1.25f, pivot = Offset(w * 0.28f, h * 0.5f)) }) {
            drawSilk(p, t, pulse, energy, breath, hue)
        }
    }
    clipPath(rightClip) {
        withTransform({ scale(1.25f, 1.25f, pivot = Offset(w * 0.78f, h * 0.5f)) }) {
            if (p.dark) drawMuscle(p, t, pulse, energy, breath, hue) else drawSuit(p, t, pulse, energy, breath)
        }
    }

    // 中缝霓虹：分屏接缝处的一道强光，节拍命中时炸开
    val seamA = Offset(w * split + h * 0.08f, -h * 0.1f)
    val seamB = Offset(w * split - h * 0.08f, h * 1.1f)
    val accent = hueColor(hue, 0.9f, 1f)
    drawLine(accent.copy(alpha = 0.15f + 0.5f * pulse), seamA, seamB, 26f + 30f * pulse, StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.55f + 0.4f * pulse), seamA, seamB, 2f, StrokeCap.Round)

    // 接缝上的能量节点：从上往下滚动
    val nodeY = ((t * 0.04f) / 3f).mod(1f)
    val nodeX = w * split + h * 0.08f - (h * 0.16f) * nodeY
    drawCircle(accent.copy(alpha = 0.5f + 0.5f * pulse), 18f, Offset(nodeX, h * nodeY))
    drawCircle(Color.White.copy(alpha = 0.8f), 5f, Offset(nodeX, h * nodeY))

    // 两侧各自的霓虹雾气，避免左右割裂
    drawCircle(Brush.radialGradient(listOf(p.neon.copy(alpha = 0.16f), Transparent), Offset(w * 0.2f, h * 0.85f), h * 0.45f), h * 0.45f, Offset(w * 0.2f, h * 0.85f), blendMode = BlendMode.Plus)
    drawCircle(Brush.radialGradient(listOf(p.neonAlt.copy(alpha = 0.14f), Transparent), Offset(w * 0.85f, h * 0.2f), h * 0.45f), h * 0.45f, Offset(w * 0.85f, h * 0.2f), blendMode = BlendMode.Plus)

    vignette(p, if (p.dark) 0.75f else 0.5f)
}

/** 色相 → 霓虹色（HSV，饱和度高，亮度按主题调整） */
fun hueColor(hue: Float, saturation: Float = 0.85f, value: Float = 1f): Color {
    val h = ((hue % 360f) + 360f) % 360f
    return Color.hsv(h, saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
}

/** 场景画布：可作为独立背景，也可嵌进玻璃层做"折射底" */
@androidx.compose.runtime.Composable
fun SceneSurface(
    kind: ArtKind,
    palette: RgbPalette,
    t: Float,
    pulse: Float,
    energy: Float,
    breath: Float,
    hue: Float,
    motionX: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        drawScene(kind, palette, t, pulse, energy, breath, hue, motionX)
    }
}

/**
 * 玻璃卡片的折射底：只画 3 个色雾，成本远低于整幅场景，模糊后观感一致。
 * 每块玻璃都重绘整幅场景会在 60fps 下把 GPU 打满，这里做了这一层取舍。
 */
@androidx.compose.runtime.Composable
fun AuraBackdrop(
    palette: RgbPalette,
    hue: Float,
    pulse: Float,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(palette.baseTop, palette.baseBottom)))
        val accent = tint ?: hueColor(hue, 0.8f, 1f)
        drawCircle(
            Brush.radialGradient(listOf(accent.copy(alpha = 0.40f + 0.25f * pulse), Transparent), Offset(size.width * 0.82f, size.height * 0.12f), size.height * 0.95f),
            size.height * 0.95f, Offset(size.width * 0.82f, size.height * 0.12f),
        )
        drawCircle(
            Brush.radialGradient(listOf(palette.neonAlt.copy(alpha = 0.32f), Transparent), Offset(size.width * 0.15f, size.height * 0.9f), size.height * 0.8f),
            size.height * 0.8f, Offset(size.width * 0.15f, size.height * 0.9f),
        )
        drawCircle(
            Brush.radialGradient(listOf(palette.neon.copy(alpha = 0.22f * pulse), Transparent), Offset(size.width * 0.5f, size.height * 0.5f), size.height * 0.7f),
            size.height * 0.7f, Offset(size.width * 0.5f, size.height * 0.5f),
        )
    }
}
