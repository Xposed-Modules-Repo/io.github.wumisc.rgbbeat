package io.github.wumisc.rgbbeat.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.wumisc.rgbbeat.data.CrashGuard
import io.github.wumisc.rgbbeat.data.DiagLog
import io.github.wumisc.rgbbeat.data.Prefs
import io.github.wumisc.rgbbeat.service.BeatService
import io.github.wumisc.rgbbeat.ui.art.ArtKind
import io.github.wumisc.rgbbeat.ui.art.SceneSurface
import io.github.wumisc.rgbbeat.ui.art.hueColor
import io.github.wumisc.rgbbeat.ui.data.UiSettings
import io.github.wumisc.rgbbeat.ui.glass.LiquidGlassNav
import io.github.wumisc.rgbbeat.ui.glass.NavIcon
import io.github.wumisc.rgbbeat.ui.glass.NavItem
import io.github.wumisc.rgbbeat.ui.live.rememberLiveBeat
import io.github.wumisc.rgbbeat.ui.screens.AboutScreen
import io.github.wumisc.rgbbeat.ui.screens.AppPickerSheet
import io.github.wumisc.rgbbeat.ui.screens.EffectsScreen
import io.github.wumisc.rgbbeat.ui.screens.HomeScreen
import io.github.wumisc.rgbbeat.ui.theme.LocalPalette
import io.github.wumisc.rgbbeat.ui.theme.RgbTheme
import io.github.wumisc.rgbbeat.ui.util.SystemBits
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 唯一的界面入口。
 *
 * 架构迁移：旧版用 XML + AppCompatActivity + PreferenceFragment 拼了三个页面，
 * 现在整屏交给 Compose —— 背景是程序化场景（可逐帧响应节拍），内容是三页 Pager，
 * 底部是液态玻璃导航，服务状态经 LiveBeat 桥接进 UI。
 */
class MainActivity : ComponentActivity() {

    private lateinit var settings: UiSettings
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiagLog.init(this)
        CrashGuard.install(this)
        Prefs.migrateLegacy(this)
        settings = UiSettings(this)

        // 窗口层统一关闭系统的"强制深色"二次压暗，否则自定义配色会被系统强行调灰
        if (Build.VERSION.SDK_INT >= 29) {
            window.decorView.isForceDarkAllowed = false
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        enableEdgeToEdge()

        setContent {
            RgbTheme {
                AppRoot(settings = settings, activity = this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        settings.refreshServiceState()
        requestPermissionsIfNeeded()
        // 与旧版保持一致：开启"开机自启"的用户打开 App 时自动拉起服务
        try {
            if (!BeatService.isRunningNow() && settings.autoStart.value) {
                BeatService.start(this)
            }
        } catch (_: Throwable) {
        }
    }

    private fun requestPermissionsIfNeeded() {
        val need = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            need += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            need += Manifest.permission.POST_NOTIFICATIONS
        }
        if (need.isNotEmpty()) permissionLauncher.launch(need.toTypedArray())
    }
}

@Composable
private fun AppRoot(settings: UiSettings, activity: ComponentActivity) {
    val palette = LocalPalette.current
    val live = rememberLiveBeat()
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var pickerMode by remember { mutableStateOf<String?>(null) }

    val dark = palette.dark
    val scenes = listOf(
        io.github.wumisc.rgbbeat.ui.art.sceneForHome(dark),
        io.github.wumisc.rgbbeat.ui.art.sceneForEffects(),
        io.github.wumisc.rgbbeat.ui.art.sceneForAbout(dark),
    )

    // 服务状态 1.2s 轮询：避免与服务实例不同步（服务可能被系统杀掉）
    LaunchedEffect(Unit) {
        while (true) {
            settings.refreshServiceState()
            delay(1200)
        }
    }

    // 页面偏移：背景场景按距离做交叉淡入，滑动时两页的场景是"融"过去的
    val pagePos by animateFloatAsState(pager.currentPage + pager.currentPageOffsetFraction, label = "pagePos")

    Box(Modifier.fillMaxSize().background(palette.baseBottom)) {
        // ---- 背景场景层 ----
        scenes.forEachIndexed { index, kind ->
            val alpha = (1f - abs(pagePos - index)).coerceIn(0f, 1f)
            if (alpha > 0.01f) {
                SceneSurface(
                    kind = kind,
                    palette = palette,
                    t = live.clock,
                    pulse = live.pulse,
                    energy = live.energy,
                    breath = live.breath,
                    hue = live.hue,
                    motionX = live.motionX,
                    modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
                )
            }
        }

        // ---- 节拍冲击波 + 上下压暗，保证文字永远读得清 ----
        Canvas(Modifier.fillMaxSize()) {
            if (live.pulse > 0.02f) {
                val cy = size.height * 0.42f
                val cx = size.width * 0.5f
                val maxR = size.width * 0.95f
                val r = maxR * (1f - live.pulse)
                drawCircle(
                    hueColor(live.hue).copy(alpha = 0.30f * live.pulse),
                    radius = r.coerceAtLeast(1f),
                    center = Offset(cx, cy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(2f + 8f * live.pulse),
                )
            }
            drawRect(
                Brush.verticalGradient(
                    listOf(palette.baseTop.copy(alpha = 0.72f), Color.Transparent, Color.Transparent),
                    endY = size.height * 0.34f,
                )
            )
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Transparent, palette.baseBottom.copy(alpha = 0.88f)),
                    startY = size.height * 0.62f,
                )
            )
        }

        // ---- 内容页 ----
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                when (page) {
                    0 -> HomeScreen(
                        settings = settings,
                        live = live,
                        scene = scenes[0],
                        onToggleService = {
                            SystemBits.toggleService(activity)
                            settings.refreshServiceState()
                        },
                        onOpenPicker = { pickerMode = it },
                    )
                    1 -> EffectsScreen(
                        settings = settings,
                        live = live,
                        scene = scenes[1],
                        onOpenPicker = { pickerMode = it },
                    )
                    else -> AboutScreen(settings = settings, live = live, scene = scenes[2])
                }
            }
        }

        // ---- 液态玻璃导航 ----
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 10.dp)
        ) {
            LiquidGlassNav(
                pagerState = pager,
                items = listOf(
                    NavItem("首页", NavIcon.HOME),
                    NavItem("特效", NavIcon.WAVE),
                    NavItem("关于", NavIcon.SPARK),
                ),
                live = live,
                onSelect = { idx -> scope.launch { pager.animateScrollToPage(idx) } },
            )
        }
    }

    pickerMode?.let { mode ->
        AppPickerSheet(mode = mode, settings = settings, onDismiss = { pickerMode = null })
    }
}
