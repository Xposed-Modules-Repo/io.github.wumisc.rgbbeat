package io.github.wumisc.rgbbeat.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wumisc.rgbbeat.R
import io.github.wumisc.rgbbeat.ui.art.ArtKind
import io.github.wumisc.rgbbeat.ui.art.AuraBackdrop
import io.github.wumisc.rgbbeat.ui.art.SceneSurface
import io.github.wumisc.rgbbeat.ui.components.GhostButton
import io.github.wumisc.rgbbeat.ui.components.SectionLabel
import io.github.wumisc.rgbbeat.ui.components.StatPill
import io.github.wumisc.rgbbeat.ui.glass.GlassPanel
import io.github.wumisc.rgbbeat.ui.live.LiveBeat
import io.github.wumisc.rgbbeat.ui.theme.LocalPalette
import io.github.wumisc.rgbbeat.ui.util.SystemBits

@Composable
fun AboutScreen(settings: io.github.wumisc.rgbbeat.ui.data.UiSettings, live: LiveBeat, scene: ArtKind) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val version = ModalVersion(context)

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
        Text("SIGNATURE", color = palette.gold, fontSize = 10.sp, letterSpacing = 3.sp)
        Text("关于", color = palette.ink, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Spacer(Modifier.height(16.dp))

        GlassPanel(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), backdrop = backdrop()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(palette.neon, palette.neonAlt)))
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x22FFFFFF))
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("RGBBeat", color = palette.ink, fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(3.dp))
                    Row {
                        StatPill("版本", "v$version", palette.gold)
                        Spacer(Modifier.width(6.dp))
                        StatPill("协议", "AGPL-3.0", palette.neonAlt)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "RGBBeat 是一款基于 LSPosed 的乘车晕车缓解与音乐律动增强模块。" +
                    "它自动判断你是否在车上——打开打车/导航应用，或传感器检测到持续运动时，" +
                    "点亮屏幕边缘的柔光，并给出提示；听歌时叠加节拍光效、精准震动与高潮礼物特效，" +
                    "让耳朵听到的内容，在视觉与触觉上也炸裂一次。",
                color = palette.ink.copy(alpha = 0.85f),
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x12FFFFFF))
                    .padding(14.dp)
            ) {
                Column {
                    Text("「我希望我们能够改变一下这个世界，哪怕只有一点点，诸君共勉」", color = palette.gold, fontSize = 12.5.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("作者：@酷安锦无名", color = palette.inkDim, fontSize = 11.5.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("支持作者")
        Spacer(Modifier.height(10.dp))
        GlassPanel(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), backdrop = backdrop()) {
            Text("觉得好用，请作者喝杯奶茶", color = palette.inkDim, fontSize = 11.5.sp)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    R.drawable.donate_wechat to "微信",
                    R.drawable.donate_alipay to "支付宝",
                ).forEach { (res, label) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0x0DFFFFFF))
                        ) {
                            Image(
                                painter = painterResource(res),
                                contentDescription = label + "捐赠码",
                                modifier = Modifier.fillMaxSize().padding(6.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(label, color = palette.inkDim, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("开源与出处")
        Spacer(Modifier.height(10.dp))
        GlassPanel(Modifier.fillMaxWidth(), backdrop = backdrop()) {
            Row(Modifier.fillMaxWidth()) {
                GhostButton("GitHub 仓库", {
                    SystemBits.openUrl(context, "https://github.com/Xposed-Modules-Repo/io.github.wumisc.rgbbeat")
                }, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                GhostButton("LSPosed", {
                    SystemBits.openUrl(context, "https://github.com/LSPosed/LSPosed")
                }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                GhostButton("分享给朋友", {
                    SystemBits.shareText(
                        context,
                        "分享 RGBBeat",
                        "RGBBeat：乘车晕车缓解 + 音乐律动增强（LSPosed 模块）\n" +
                            "https://github.com/Xposed-Modules-Repo/io.github.wumisc.rgbbeat",
                    )
                }, Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(
            "致敬 LSPosed / Xposed 及整个开源社区\n纯本地运行 · 无联网 · 无广告",
            color = palette.inkFaint,
            fontSize = 10.5.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun ModalVersion(context: android.content.Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "3.0.0"
} catch (_: Throwable) {
    "3.0.0"
}
