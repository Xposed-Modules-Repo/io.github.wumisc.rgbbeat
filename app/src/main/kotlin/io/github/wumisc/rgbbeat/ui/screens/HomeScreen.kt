package io.github.wumisc.rgbbeat.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wumisc.rgbbeat.ui.art.ArtKind
import io.github.wumisc.rgbbeat.ui.art.AuraBackdrop
import io.github.wumisc.rgbbeat.ui.art.SceneSurface
import io.github.wumisc.rgbbeat.ui.art.hueColor
import io.github.wumisc.rgbbeat.ui.components.EnergyMeter
import io.github.wumisc.rgbbeat.ui.components.GhostButton
import io.github.wumisc.rgbbeat.ui.components.NeonSwitch
import io.github.wumisc.rgbbeat.ui.components.SectionLabel
import io.github.wumisc.rgbbeat.ui.components.SettingRow
import io.github.wumisc.rgbbeat.ui.components.StatPill
import io.github.wumisc.rgbbeat.ui.data.UiSettings
import io.github.wumisc.rgbbeat.ui.glass.GlassPanel
import io.github.wumisc.rgbbeat.ui.live.LiveBeat
import io.github.wumisc.rgbbeat.ui.theme.LocalPalette
import io.github.wumisc.rgbbeat.ui.util.SystemBits
import kotlin.math.cos
import kotlin.math.sin

/**
 * 首页：整页就是一件"乐器"。
 * 中心是共鸣球（PulseOrb）——它既是电源开关，也是把音频能量可视化的心脏：
 * 外围 64 根频谱柱随能量伸缩、随色相变色，命中节拍时整球外扩一圈冲击波。
 */
@Composable
fun HomeScreen(
    settings: UiSettings,
    live: LiveBeat,
    scene: ArtKind,
    onToggleService: () -> Unit,
    onOpenPicker: (String) -> Unit,
) {
    val palette = LocalPalette.current
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 110.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = "RGBBEAT",
            style = TextStyle(
                brush = Brush.horizontalGradient(listOf(palette.neon, palette.neonAlt, palette.gold)),
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
            ),
        )
        Text(
            text = "乘车晕车缓解 · 音乐律动共鸣",
            color = palette.inkDim,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
        )

        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PulseOrb(
                running = settings.serviceOn,
                live = live,
                size = 236.dp,
                onClick = onToggleService,
            )
        }
        Text(
            text = if (settings.serviceOn) "运行中 · 点按球体停止" else "未运行 · 点按球体启动",
            color = palette.inkDim,
            fontSize = 11.sp,
            letterSpacing = 1.2.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            StatPill("状态", if (settings.serviceOn) "运行中" else "已停止", if (settings.serviceOn) palette.ok else palette.inkDim)
            Spacer(Modifier.width(8.dp))
            StatPill("BPM", if (settings.serviceOn && live.bpm > 20f) live.bpm.toInt().toString() else "--", palette.neonAlt)
            Spacer(Modifier.width(8.dp))
            StatPill(
                "能量",
                (live.energy * 100).toInt().toString() + "%",
                hueColor(live.hue),
            )
            if (live.vehicle) {
                Spacer(Modifier.width(8.dp))
                StatPill("乘车", "检测中", palette.gold)
            }
        }

        Spacer(Modifier.height(12.dp))
        EnergyMeter(live.energy, live.pulse, Modifier.fillMaxWidth().padding(horizontal = 6.dp))

        // ---- 正在播放：把耳朵里的内容搬到视觉中心 ----
        if (!live.title.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                backdrop = { AuraBackdrop(palette, live.hue, live.pulse, Modifier.fillMaxSize()) },
            ) {
                Text("NOW PLAYING", color = palette.neonAlt, fontSize = 9.5.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(6.dp))
                Text(live.title ?: "", color = palette.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                val artist = live.artist
                if (!artist.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(artist, color = palette.inkDim, fontSize = 11.5.sp, maxLines = 1)
                }
                val lyric = live.lyric
                if (!lyric.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        lyric,
                        color = palette.ink.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("QUICK CONTROL")
        Spacer(Modifier.height(10.dp))
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            backdrop = { AuraBackdrop(palette, live.hue, live.pulse, Modifier.fillMaxSize()) },
        ) {
            SettingRow("节拍震动") {
                NeonSwitch(settings.vibration.value) { settings.setBool(settings.vibration, UiSettings.KEY_VIBRATION, it) }
            }
            SettingRow("烟花 / 礼花动效") {
                NeonSwitch(settings.anim.value) { settings.setBool(settings.anim, UiSettings.KEY_ANIM, it) }
            }
            SettingRow("3D 粒子氛围") {
                NeonSwitch(settings.particle.value) { settings.setBool(settings.particle, UiSettings.KEY_PARTICLE, it) }
            }
            SettingRow("高潮礼物特效") {
                NeonSwitch(settings.highlight.value) { settings.setBool(settings.highlight, UiSettings.KEY_HIGHLIGHT, it) }
            }
        }

        // ---- 权限缺口：只在缺的时候出现，不占版面 ----
        val missingOverlay = !SystemBits.canDrawOverlays(context)
        val missingNotif = !SystemBits.hasNotificationPermission(context)
        val missingA11y = !SystemBits.isAccessibilityEnabled(context)
        if (missingOverlay || missingNotif || missingA11y) {
            Spacer(Modifier.height(18.dp))
            SectionLabel("待授权")
            Spacer(Modifier.height(10.dp))
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                backdrop = { AuraBackdrop(palette, live.hue, live.pulse, Modifier.fillMaxSize()) },
            ) {
                if (missingOverlay) {
                    SettingRow("悬浮窗权限", "光效覆盖层的必要条件") {
                        GhostButton("去授权", { SystemBits.openOverlaySettings(context) }, solid = true)
                    }
                }
                if (missingNotif) {
                    SettingRow("通知权限", "前台服务常驻通知") {
                        GhostButton("去授权", { SystemBits.openNotificationSettings(context) }, solid = true)
                    }
                }
                if (missingA11y) {
                    SettingRow("无障碍服务", "识别打车/导航应用，自动进入晕车缓解") {
                        GhostButton("去开启", { SystemBits.openAccessibilitySettings(context) }, solid = true)
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth()) {
            GhostButton("触发应用", { onOpenPicker("trigger") }, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            GhostButton("音乐黑名单", { onOpenPicker("blacklist") }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "提示：光效走系统悬浮窗，覆盖在所有应用之上；触摸完全穿透，不影响操作。",
            color = palette.inkFaint,
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
        )
    }
}

/**
 * 共鸣球：电源 + 实时频谱。
 * 三层结构：外环冲击波（节拍）、64 根径向频谱柱（能量）、内芯（电源图标）。
 */
@Composable
fun PulseOrb(
    running: Boolean,
    live: LiveBeat,
    size: Dp,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val accent = hueColor(live.hue)
    Box(
        Modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = this.size.width / 2
            val cy = this.size.height / 2
            val r = this.size.minDimension / 2 - 6f
            val pulse = live.pulse
            val energy = live.energy

            // 外层辉光
            drawCircle(
                Brush.radialGradient(
                    listOf(accent.copy(alpha = (if (running) 0.30f else 0.10f) + 0.18f * pulse), Color.Transparent),
                    Offset(cx, cy), r * 1.02f,
                ),
                r * 1.02f, Offset(cx, cy),
            )

            // 节拍冲击波：每拍向外扩一圈
            if (pulse > 0.02f) {
                val ringR = r * (1f - pulse * 0.55f)
                drawCircle(accent.copy(alpha = 0.35f * pulse), ringR, Offset(cx, cy), style = Stroke(2f + 6f * pulse))
            }

            // 频谱柱：64 根，按能量从底部展开
            val bars = 64
            for (i in 0 until bars) {
                val angle = (i.toFloat() / bars) * 360f + live.clock * 8f
                val phase = sin((i / bars.toFloat()) * 6.28f * 3f + live.clock * 1.6f)
                val len = (r * 0.10f) * (0.4f + energy * 2.2f + pulse * 0.9f) * (0.7f + 0.5f * phase)
                val inner = r * 0.62f
                val rad = Math.toRadians(angle.toDouble())
                val sx = cx + (inner * cos(rad)).toFloat()
                val sy = cy + (inner * sin(rad)).toFloat()
                val ex = cx + ((inner + len) * cos(rad)).toFloat()
                val ey = cy + ((inner + len) * sin(rad)).toFloat()
                drawLine(
                    color = if (i % 2 == 0) accent.copy(alpha = 0.65f) else palette.neonAlt.copy(alpha = 0.55f),
                    start = Offset(sx, sy), end = Offset(ex, ey),
                    strokeWidth = 2.2f, cap = StrokeCap.Round,
                )
            }

            // 内芯玻璃球
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x33FFFFFF), Color(0x11FFFFFF), Color.Transparent),
                    Offset(cx - r * 0.12f, cy - r * 0.15f), r * 0.56f,
                ),
                r * 0.56f, Offset(cx, cy),
            )
            drawCircle(
                color = palette.glassBorder.copy(alpha = 0.6f),
                radius = r * 0.56f, center = Offset(cx, cy),
                style = Stroke(1.2f),
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.08f), Color.Transparent),
                    Offset(cx, cy), r * 0.5f,
                ),
                r * 0.5f, Offset(cx, cy),
            )

            // 电源图标
            if (running) {
                val half = r * 0.085f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.92f),
                    topLeft = Offset(cx - half, cy - half),
                    size = Size(half * 2, half * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
                )
                rotate(live.clock * 40f, pivot = Offset(cx, cy)) {
                    drawArc(
                        color = palette.neon.copy(alpha = 0.55f),
                        startAngle = 0f, sweepAngle = 120f, useCenter = false,
                        topLeft = Offset(cx - r * 0.30f, cy - r * 0.30f),
                        size = Size(r * 0.6f, r * 0.6f),
                        style = Stroke(1.5f),
                    )
                }
            } else {
                val path = Path().apply {
                    moveTo(cx - r * 0.05f, cy - r * 0.075f)
                    lineTo(cx + r * 0.075f, cy)
                    lineTo(cx - r * 0.05f, cy + r * 0.075f)
                    close()
                }
                drawPath(path, Color.White.copy(alpha = 0.9f))
            }
        }
    }
}
