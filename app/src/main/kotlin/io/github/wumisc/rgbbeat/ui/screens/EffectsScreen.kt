package io.github.wumisc.rgbbeat.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wumisc.rgbbeat.data.DiagLog
import io.github.wumisc.rgbbeat.render.EffectKeyMode
import io.github.wumisc.rgbbeat.render.EffectPicker
import io.github.wumisc.rgbbeat.render.EffectTint
import io.github.wumisc.rgbbeat.ui.art.ArtKind
import io.github.wumisc.rgbbeat.ui.art.AuraBackdrop
import io.github.wumisc.rgbbeat.ui.art.SceneSurface
import io.github.wumisc.rgbbeat.ui.components.Chip
import io.github.wumisc.rgbbeat.ui.components.ColorDotRow
import io.github.wumisc.rgbbeat.ui.components.GhostButton
import io.github.wumisc.rgbbeat.ui.components.NeonSlider
import io.github.wumisc.rgbbeat.ui.components.NeonSwitch
import io.github.wumisc.rgbbeat.ui.components.SectionLabel
import io.github.wumisc.rgbbeat.ui.components.SegmentedControl
import io.github.wumisc.rgbbeat.ui.components.SettingRow
import io.github.wumisc.rgbbeat.ui.data.UiSettings
import io.github.wumisc.rgbbeat.ui.glass.GlassPanel
import io.github.wumisc.rgbbeat.ui.live.LiveBeat
import io.github.wumisc.rgbbeat.ui.theme.LocalPalette
import io.github.wumisc.rgbbeat.ui.util.SystemBits

/**
 * 功能页：把原来散在 PreferenceFragment 里的开关全部收进一套玻璃卡片，
 * 每张卡片只解决一件事（工作模式 / 光效 / 震动 / 粒子 / 礼物 / 触发 / 维护）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EffectsScreen(
    settings: UiSettings,
    live: LiveBeat,
    scene: ArtKind,
    onOpenPicker: (String) -> Unit,
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    var showLog by remember { mutableStateOf(false) }

    fun backdrop(): @Composable () -> Unit = {
        AuraBackdrop(palette, live.hue, live.pulse, Modifier.fillMaxSize())
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp, bottom = 110.dp)
    ) {
        Text("CONTROL ROOM", color = palette.neon, fontSize = 10.sp, letterSpacing = 3.sp)
        Text("特效控制台", color = palette.ink, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text("所有改动即时同步到运行中的服务", color = palette.inkDim, fontSize = 11.5.sp)

        Spacer(Modifier.height(18.dp))
        SectionLabel("工作模式")
        Spacer(Modifier.height(10.dp))
        GlassPanel(Modifier.fillMaxWidth(), backdrop = backdrop()) {
            SegmentedControl(
                options = listOf("融合", "音乐", "晕车"),
                selectedIndex = when (settings.dataMode) {
                    "music" -> 1
                    "motion" -> 2
                    else -> 0
                },
                onSelect = { idx ->
                    settings.updateDataMode(when (idx) { 1 -> "music"; 2 -> "motion"; else -> "fusion" })
                },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                when (settings.dataMode) {
                    "music" -> "纯音乐律动：只吃音频频谱，安静时自动停光效。"
                    "motion" -> "纯晕车缓解：加速度计判断乘车，转弯/颠簸时亮边光。"
                    else -> "融合：音乐节拍 + 车辆运动并行，坐车听歌时双重缓解。"
                },
                color = palette.inkDim, fontSize = 11.5.sp, lineHeight = 16.sp,
            )
            Spacer(Modifier.height(6.dp))
            Row {
                NeonSwitch(settings.carsick.value) { settings.setBool(settings.carsick, UiSettings.KEY_CARSICK, it) }
                Spacer(Modifier.width(10.dp))
                Text("晕车场景自动检测", color = palette.ink, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("光效形态")
        Spacer(Modifier.height(10.dp))
        GlassPanel(Modifier.fillMaxWidth(), backdrop = backdrop()) {
            SegmentedControl(
                options = listOf("边框", "跑马灯", "键盘环绕", "自动"),
                selectedIndex = when (settings.lightMode) {
                    "edge" -> 0
                    "marquee" -> 1
                    "keyboard" -> 2
                    else -> 3
                },
                onSelect = { idx ->
                    settings.updateLightMode(listOf("edge", "marquee", "keyboard", "auto")[idx])
                },
            )
            Spacer(Modifier.height(12.dp))
            NeonSlider(
                label = "亮度",
                value = settings.brightness.toFloat(),
                range = 20f..255f,
                onChange = { },
                onFinished = { settings.updateBrightness(it.toInt()) },
                format = { it.toInt().toString() },
            )
            NeonSlider(
                label = "流动速度",
                value = settings.speed.toFloat(),
                range = 5f..120f,
                onChange = { },
                onFinished = { settings.updateSpeed(it.toInt()) },
            )
            Spacer(Modifier.height(4.dp))
            Text("主色", color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(9.dp))
            ColorDotRow(settings.lightHue) { settings.updateLightHue(it) }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("节拍引擎")
        Spacer(Modifier.height(10.dp))
        GlassPanel(Modifier.fillMaxWidth(), backdrop = backdrop()) {
            SegmentedControl(
                options = listOf("自动", "播放器频谱", "麦克风"),
                selectedIndex = when (settings.audioSource) {
                    "visualizer" -> 1
                    "mic" -> 2
                    else -> 0
                },
                onSelect = { idx -> settings.updateAudioSource(listOf("auto", "visualizer", "mic")[idx]) },
            )
            Spacer(Modifier.height(12.dp))
            NeonSlider(
                label = "灵敏度",
                value = settings.sensitivity.toFloat(),
                range = 8f..30f,
                onChange = { },
                onFinished = { settings.updateSensitivity(it.toInt()) },
                format = { String.format("%.1f", it / 10f) },
            )
            NeonSlider(
                label = "节拍冷却",
                value = settings.cooldown.toFloat(),
                range = 120f..600f,
                onChange = { },
                onFinished = { settings.updateCooldown(it.toInt()) },
                format = { it.toInt().toString() + " ms" },
            )
            Spacer(Modifier.height(6.dp))
            SettingRow("音乐自动触发", "有节奏的音乐才开光效，停止 4 秒自动关，省电") {
                NeonSwitch(settings.musicAuto.value) {
                    settings.setBool(settings.musicAuto, UiSettings.KEY_MUSIC_AUTO, it)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("振动共鸣")
        Spacer(Modifier.height(10.dp))
        GlassPanel(Modifier.fillMaxWidth(), backdrop = backdrop()) {
            SettingRow("节拍震动", "把节拍同步到手掌，听觉 + 触觉双通道") {
                NeonSwitch(settings.vibration.value) {
                    settings.setBool(settings.vibration, UiSettings.KEY_VIBRATION, it)
                }
            }
            NeonSlider(
                label = "震感强度",
                value = settings.vibrationIntensity.toFloat(),
                range = 0f..255f,
                onChange = { },
                onFinished = { settings.updateVibrationIntensity(it.toInt()) },
                format = { it.toInt().toString() },
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("粒子引擎")
        Spacer(Modifier.height(10.dp))
        GlassPanel(Modifier.fillMaxWidth(), backdrop = backdrop()) {
            SettingRow("3D 粒子氛围", "屏幕中央的玫瑰/爱心粒子随节拍炸开") {
                NeonSwitch(settings.particle.value) {
                    settings.setBool(settings.particle, UiSettings.KEY_PARTICLE, it)
                }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("高画质 3000", settings.particleQuality == "high", { settings.updateParticleQuality("high") })
                Chip("节电 700", settings.particleQuality == "eco", { settings.updateParticleQuality("eco") })
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("高潮礼物特效")
        Spacer(Modifier.height(10.dp))
        GlassPanel(Modifier.fillMaxWidth(), backdrop = backdrop()) {
            SettingRow("音乐高潮自动播放") {
                NeonSwitch(settings.highlight.value) {
                    settings.setBool(settings.highlight, UiSettings.KEY_HIGHLIGHT, it)
                }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    EffectPicker.CYCLE to "自动轮播",
                    EffectPicker.ORBIT to "星际轨道",
                    EffectPicker.OCEAN to "心海流光",
                    EffectPicker.PHOENIX to "鎏金凤凰",
                ).forEach { (value, label) ->
                    Chip(label, settings.highlightEffect == value, { settings.updateHighlightEffect(value) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("抠背景", color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    EffectKeyMode.AUTO to "自动",
                    EffectKeyMode.DARK to "纯黑底",
                    EffectKeyMode.LIGHT to "浅色底",
                    EffectKeyMode.OFF to "不抠",
                ).forEach { (value, label) ->
                    Chip(label, settings.keyMode == value, { settings.updateKeyMode(value) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("统一染色", color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    EffectTint.WHITE_PINK to "白里粉",
                    EffectTint.HEART to "心形粉",
                    EffectTint.VIOLET to "玫瑰紫",
                    EffectTint.CYAN to "极光青",
                    EffectTint.BLUE to "深海蓝",
                    EffectTint.ORANGE to "鎏金橙",
                    EffectTint.GREEN to "荧光绿",
                    EffectTint.RED to "中国红",
                    EffectTint.NONE to "原色",
                ).forEach { (value, label) ->
                    Chip(label, settings.tint == value, { settings.updateTint(value) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                GhostButton(
                    text = "立即预览",
                    onClick = {
                        if (settings.serviceOn) settings.previewHighlight()
                        else SystemBits.toast(context, "请先启动光效服务再预览")
                    },
                    modifier = Modifier.weight(1f),
                    solid = true,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("触发与常驻")
        Spacer(Modifier.height(10.dp))
        GlassPanel(Modifier.fillMaxWidth(), backdrop = backdrop()) {
            SettingRow("手动选择应用触发", "除内置打车/导航外，额外勾选的应用在前台也可触发") {
                NeonSwitch(settings.triggerEnabled.value) {
                    settings.setBool(settings.triggerEnabled, UiSettings.KEY_TRIGGER_ENABLED, it)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                GhostButton("触发应用 (${settings.prefs.triggerApps().size})", { onOpenPicker("trigger") }, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                GhostButton("黑名单 (${settings.prefs.musicBlacklist().size})", { onOpenPicker("blacklist") }, Modifier.weight(1f))
            }
            SettingRow("开机自启", "重启后自动拉起光效服务") {
                NeonSwitch(settings.autoStart.value) {
                    settings.setBool(settings.autoStart, UiSettings.KEY_AUTO_START, it)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("诊断")
        Spacer(Modifier.height(10.dp))
        GlassPanel(Modifier.fillMaxWidth(), backdrop = backdrop()) {
            Text(
                "光效没出现？先看日志确认是权限、节拍还是渲染链路的问题。",
                color = palette.inkDim, fontSize = 11.5.sp, lineHeight = 16.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row {
                GhostButton("查看日志", { showLog = true }, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                GhostButton("分享日志", {
                    SystemBits.shareText(context, "分享 RGBBeat 日志", DiagLog.readLast(300))
                }, Modifier.weight(1f))
            }
        }
    }

    if (showLog) {
        val log = remember { DiagLog.readLast(300) }
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text("调试日志", fontSize = 15.sp) },
            text = {
                Column(Modifier.height(380.dp).verticalScroll(rememberScrollState())) {
                    Text(log, fontSize = 10.sp, lineHeight = 14.sp, color = palette.ink)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    SystemBits.shareText(context, "分享 RGBBeat 日志", log)
                }) { Text("分享") }
            },
            dismissButton = {
                TextButton(onClick = { showLog = false }) { Text("关闭") }
            },
            containerColor = if (palette.dark) Color(0xFF121016) else Color(0xFFF7F4EF),
        )
    }
}
