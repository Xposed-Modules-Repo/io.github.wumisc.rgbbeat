package io.github.wumisc.rgbbeat.ui.data

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.wumisc.rgbbeat.data.Prefs
import io.github.wumisc.rgbbeat.render.EffectKeyMode
import io.github.wumisc.rgbbeat.render.EffectPicker
import io.github.wumisc.rgbbeat.render.EffectTint
import io.github.wumisc.rgbbeat.service.BeatService

/**
 * UI 侧设置状态。
 *
 * 单一职责：把 SharedPreferences 的键值提升成 Compose 可观察状态，并在每次写入后
 * 调用 BeatService.applyPrefs() 让运行中的服务即时生效。
 * 键名集中在下方常量里，避免界面代码里散落裸字符串导致的"改了不生效"。
 */
class UiSettings(private val context: Context) {

    val prefs = Prefs(context)

    // ---- 布尔项 ----
    val vibration = boolState(KEY_VIBRATION, true)
    val anim = boolState(KEY_ANIM, true)
    val particle = boolState(KEY_PARTICLE, false)
    val highlight = boolState(KEY_HIGHLIGHT, true)
    val musicAuto = boolState(KEY_MUSIC_AUTO, false)
    val carsick = boolState(KEY_CARSICK, true)
    val motionTrigger = boolState(KEY_MOTION_TRIGGER, true)
    val carsickToast = boolState(KEY_CARSICK_TOAST, true)
    val autoStart = boolState(KEY_AUTO_START, true)
    val triggerEnabled = boolState(KEY_TRIGGER_ENABLED, false)

    // ---- 枚举项 ----
    var dataMode by mutableStateOf(prefs.dataMode())
        private set
    var lightMode by mutableStateOf(prefs.mode())
        private set
    var audioSource by mutableStateOf(prefs.audioSource())
        private set
    var particleQuality by mutableStateOf(prefs.particleQuality())
        private set
    var highlightEffect by mutableStateOf(prefs.highlightEffect())
        private set
    var keyMode by mutableStateOf(prefs.highlightKeyMode())
        private set
    var tint by mutableStateOf(prefs.highlightTint())
        private set

    // ---- 数值项 ----
    var brightness by mutableIntStateOf(prefs.brightness())
        private set
    var speed by mutableIntStateOf(prefs.speed().toInt())
        private set
    var sensitivity by mutableIntStateOf((prefs.sensitivity() * 10).toInt())
        private set
    var cooldown by mutableIntStateOf(prefs.cooldownMs().toInt())
        private set
    var vibrationIntensity by mutableIntStateOf(prefs.vibrationIntensity())
        private set
    var lightHue by mutableFloatStateOf(prefs.lightColorHue())
        private set

    /** 服务是否在跑（1.5s 轮询刷新，避免与服务实例不同步） */
    var serviceOn by mutableStateOf(BeatService.isRunningNow())
        private set

    fun refreshServiceState() {
        serviceOn = BeatService.isRunningNow()
    }

    fun setBool(state: MutableState<Boolean>, key: String, value: Boolean) {
        state.value = value
        prefs.sp().edit().putBoolean(key, value).apply()
        sync()
    }

    fun updateDataMode(v: String) {
        dataMode = v
        setString(KEY_DATA_MODE, v)
    }

    fun updateLightMode(v: String) {
        lightMode = v
        setString(KEY_MODE, v)
    }

    fun updateAudioSource(v: String) {
        audioSource = v
        setString(KEY_AUDIO_SOURCE, v)
    }

    fun updateParticleQuality(v: String) {
        particleQuality = v
        setString(KEY_PARTICLE_QUALITY, v)
    }

    fun updateHighlightEffect(v: String) {
        highlightEffect = EffectPicker.normalize(v)
        setString(KEY_HIGHLIGHT_EFFECT, highlightEffect)
    }

    fun updateKeyMode(v: String) {
        keyMode = EffectKeyMode.normalize(v)
        setString(KEY_HIGHLIGHT_KEY, keyMode)
    }

    fun updateTint(v: String) {
        tint = EffectTint.normalize(v)
        setString(KEY_HIGHLIGHT_TINT, tint)
    }

    fun updateBrightness(v: Int) { brightness = v; putInt(KEY_BRIGHTNESS, v) }
    fun updateSpeed(v: Int) { speed = v; putInt(KEY_SPEED, v) }
    fun updateSensitivity(v: Int) { sensitivity = v; putInt(KEY_SENSITIVITY, v) }
    fun updateCooldown(v: Int) { cooldown = v; putInt(KEY_COOLDOWN, v) }
    fun updateVibrationIntensity(v: Int) { vibrationIntensity = v; putInt(KEY_VIBRATION_INTENSITY, v) }

    fun updateLightHue(v: Float) {
        lightHue = v
        prefs.setLightColorHue(v)
        sync()
    }

    /** 预览礼物特效（服务未启动时由调用方提示） */
    fun previewHighlight() {
        BeatService.instance()?.previewHighlight(-1)
    }

    private fun boolState(key: String, def: Boolean): MutableState<Boolean> =
        mutableStateOf(prefs.sp().getBoolean(key, def))

    private fun setString(key: String, v: String) {
        prefs.sp().edit().putString(key, v).apply()
        sync()
    }

    private fun putInt(key: String, v: Int) {
        prefs.sp().edit().putInt(key, v).apply()
        sync()
    }

    fun sync() {
        try {
            BeatService.instance()?.applyPrefs()
        } catch (_: Throwable) {
        }
    }

    companion object {
        const val KEY_VIBRATION = "vibration_enabled"
        const val KEY_ANIM = "anim_enabled"
        const val KEY_PARTICLE = "particle_mode"
        const val KEY_HIGHLIGHT = "highlight_enabled"
        const val KEY_MUSIC_AUTO = "music_auto_enabled"
        const val KEY_CARSICK = "carsick_enabled"
        const val KEY_MOTION_TRIGGER = "motion_trigger_enabled"
        const val KEY_CARSICK_TOAST = "carsick_toast"
        const val KEY_AUTO_START = "auto_start"
        const val KEY_TRIGGER_ENABLED = "app_trigger_enabled"
        const val KEY_DATA_MODE = "data_mode"
        const val KEY_MODE = "mode"
        const val KEY_AUDIO_SOURCE = "audio_source"
        const val KEY_PARTICLE_QUALITY = "particle_quality"
        const val KEY_HIGHLIGHT_EFFECT = "highlight_effect"
        const val KEY_HIGHLIGHT_KEY = "highlight_key_mode"
        const val KEY_HIGHLIGHT_TINT = "highlight_tint"
        const val KEY_BRIGHTNESS = "brightness"
        const val KEY_SPEED = "speed"
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_COOLDOWN = "cooldown"
        const val KEY_VIBRATION_INTENSITY = "vibration_intensity"
        const val KEY_LIGHT_HUE = "light_color_hue"
    }
}
