package io.github.wumisc.rgbbeat.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wumisc.rgbbeat.ui.components.GhostButton
import io.github.wumisc.rgbbeat.ui.data.UiSettings
import io.github.wumisc.rgbbeat.ui.glass.GlassPanel
import io.github.wumisc.rgbbeat.ui.theme.LocalPalette
import io.github.wumisc.rgbbeat.ui.util.AppEntry
import io.github.wumisc.rgbbeat.ui.util.SystemBits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.HashSet

/**
 * 应用选择面板（替代旧 AppPickActivity 的小窗）。
 * 两种模式共用一套列表：触发应用 / 音乐识别黑名单。
 */
@Composable
fun AppPickerSheet(
    mode: String,
    settings: UiSettings,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val isBlacklist = mode == "blacklist"
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    val selected = remember {
        mutableStateListOf<String>().apply {
            addAll(
                if (isBlacklist) settings.prefs.musicBlacklist() else settings.prefs.triggerApps()
            )
        }
    }

    LaunchedEffect(mode) {
        apps = withContext(Dispatchers.IO) {
            try {
                SystemBits.loadLaunchableApps(context)
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        GlassPanel(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .fillMaxHeight(0.78f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            tintTop = if (palette.dark) Color(0xCC151019) else Color(0xF2FFFFFF),
        ) {
            Text(
                if (isBlacklist) "音乐识别黑名单" else "选择触发应用",
                color = palette.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (isBlacklist) "黑名单内的应用在前台时不触发音乐光效（视频/直播等）"
                else "勾选后，这些应用在前台时会强制开启光效。打车/导航已内置，无需勾选。",
                color = palette.inkDim, fontSize = 11.sp, lineHeight = 16.sp,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x14FFFFFF))
                    .border(1.dp, palette.glassBorder.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = palette.ink, fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("搜索应用名或包名", color = palette.inkFaint, fontSize = 13.sp)
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(10.dp))

            val filtered = remember(apps, query) {
                if (query.isBlank()) apps
                else apps.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(filtered, key = { it.pkg }) { app ->
                    val checked = selected.contains(app.pkg)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (app.recommended && !isBlacklist) return@clickable
                                if (checked) selected.remove(app.pkg) else selected.add(app.pkg)
                            }
                            .padding(vertical = 9.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val icon = app.icon
                        if (icon != null) {
                            Image(
                                bitmap = icon.asImageBitmap(),
                                contentDescription = app.label,
                                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)),
                            )
                        } else {
                            Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(Color(0x22FFFFFF)))
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(app.label, color = palette.ink, fontSize = 13.5.sp, maxLines = 1, modifier = Modifier.weight(1f, false))
                                if (app.recommended && !isBlacklist) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "内置",
                                        color = palette.gold,
                                        fontSize = 9.5.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(palette.gold.copy(alpha = 0.16f))
                                            .padding(horizontal = 5.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(app.pkg, color = palette.inkFaint, fontSize = 10.sp, maxLines = 1)
                        }
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (checked) palette.neon.copy(alpha = 0.9f) else Color(0x14FFFFFF))
                                .border(
                                    1.dp,
                                    if (checked) Color.White.copy(alpha = 0.5f) else palette.glassBorder.copy(alpha = 0.4f),
                                    CircleShape,
                                ),
                        ) {
                            if (checked) {
                                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                    drawLine(
                                        Color.White,
                                        androidx.compose.ui.geometry.Offset(size.width * 0.26f, size.height * 0.52f),
                                        androidx.compose.ui.geometry.Offset(size.width * 0.44f, size.height * 0.70f),
                                        2f,
                                    )
                                    drawLine(
                                        Color.White,
                                        androidx.compose.ui.geometry.Offset(size.width * 0.44f, size.height * 0.70f),
                                        androidx.compose.ui.geometry.Offset(size.width * 0.75f, size.height * 0.30f),
                                        2f,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                GhostButton("取消", { onDismiss() }, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                GhostButton("保存 (${selected.size})", {
                    val set: Set<String> = HashSet(selected)
                    if (isBlacklist) settings.prefs.saveMusicBlacklist(set)
                    else settings.prefs.saveTriggerApps(Collections.unmodifiableSet(set))
                    settings.sync()
                    onDismiss()
                }, Modifier.weight(1f), solid = true)
            }
        }
    }
}
