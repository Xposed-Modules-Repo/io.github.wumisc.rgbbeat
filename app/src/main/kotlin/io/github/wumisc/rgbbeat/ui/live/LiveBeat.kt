package io.github.wumisc.rgbbeat.ui.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import io.github.wumisc.rgbbeat.service.BeatService

/**
 * 实时律动桥：把服务侧 volatile 的 BeatState 采样成 UI 可观察的状态。
 *
 * 设计要点：
 *  - 只有这一层读取 Java 服务对象，UI 不再直接摸 BeatService；
 *  - 每帧一次采样，节拍命中用 beatSeq 单调序号做边沿检测（不会漏帧）；
 *  - 脉冲用指数衰减还原"打击感"，即使音频回调 45fps 也能得到 60fps 的顺滑律动；
 *  - energy 做一阶低通，避免频谱抖动导致画面抽搐。
 */
class LiveBeat {
    var running by mutableStateOf(false)
        private set
    var energy by mutableFloatStateOf(0f)
        private set

    /** 节拍脉冲 0..1，命中瞬间置 1，之后指数衰减（约 420ms 归零） */
    var pulse by mutableFloatStateOf(0f)
        private set

    /** 呼吸相位 0..1，连续正弦，用于背景"呼吸感" */
    var breath by mutableFloatStateOf(0f)
        private set

    /** 动画时钟（秒），所有程序化场景共用同一时间轴 */
    var clock by mutableFloatStateOf(0f)
        private set

    var hue by mutableFloatStateOf(300f)
        private set
    var bpm by mutableFloatStateOf(0f)
        private set
    var pitch by mutableFloatStateOf(0.5f)
        private set
    var motionEnergy by mutableFloatStateOf(0f)
        private set
    var motionX by mutableFloatStateOf(0f)
        private set
    var vehicle by mutableStateOf(false)
        private set

    var title by mutableStateOf<String?>(null)
        private set
    var artist by mutableStateOf<String?>(null)
        private set
    var lyric by mutableStateOf<String?>(null)
        private set

    private var lastSeq = -1L
    private var pulseAtNanos = 0L
    private var lastNanos = 0L

    fun sync(nowNanos: Long) {
        val state = BeatService.liveState()
        running = state != null
        val dt = if (lastNanos == 0L) 0.016f else ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastNanos = nowNanos
        clock = (clock + dt).mod(36_000f)
        breath = (kotlin.math.sin(clock * 1.15f) * 0.5f + 0.5f)

        if (state == null) {
            // 服务停止：能量与脉冲平滑回落，避免画面突然死掉
            energy = (energy - dt * 1.8f).coerceAtLeast(0f)
            pulse = (pulse - dt * 2.6f).coerceAtLeast(0f)
            vehicle = false
            motionEnergy = 0f
            title = null
            artist = null
            lyric = null
            lastSeq = -1L
            return
        }

        val target = state.energy.coerceIn(0f, 1f)
        energy += (target - energy) * (1f - kotlin.math.exp(-dt * 9f))

        val seq = state.beatSeq
        if (lastSeq >= 0 && seq != lastSeq) {
            pulse = 1f
            pulseAtNanos = nowNanos
        }
        lastSeq = seq
        if (pulse > 0f) {
            val age = (nowNanos - pulseAtNanos) / 1_000_000_000f
            val decay = kotlin.math.exp(-age / 0.16f)
            pulse = decay.coerceIn(0f, 1f)
        }

        hue = state.pitchHue.takeIf { it > 0f } ?: state.hue
        bpm = state.bpm
        pitch = state.pitchLevel.coerceIn(0f, 1f)
        motionEnergy = state.motionEnergy.coerceIn(0f, 1f)
        motionX = state.motionX.coerceIn(-1f, 1f)
        vehicle = state.vehicleActive
        title = state.title
        artist = state.artist
        lyric = state.lyricNow
    }
}

@Composable
fun rememberLiveBeat(): LiveBeat {
    val live = remember { LiveBeat() }
    LaunchedEffect(live) {
        while (true) {
            withFrameNanos { live.sync(it) }
        }
    }
    return live
}
