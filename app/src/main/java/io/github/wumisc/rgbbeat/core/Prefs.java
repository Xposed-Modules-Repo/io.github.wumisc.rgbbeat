package io.github.wumisc.rgbbeat.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 偏好读取封装。所有取值均有默认值，避免空指针。
 */
public final class Prefs {

    private static final String NAME = "rgbbeat";
    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    /** 直接访问 SharedPreferences（首页等界面写入需要） */
    public SharedPreferences sp() { return sp; }

    /** 音频来源：auto / visualizer / mic */
    public String audioSource() { return sp.getString("audio_source", "auto"); }

    /** 节拍灵敏度阈值系数，越大越难触发（5~30）；默认 20 → 2.0 倍，
     *  过滤麦克风拾音下的噪声误触发（旧默认 1.0 时几乎每帧都算节拍） */
    public float sensitivity() { return sp.getInt("sensitivity", 20) / 10f; }

    /** 两次节拍最短间隔 ms（默认 280，减少连发） */
    public long cooldownMs() { return sp.getInt("cooldown", 280); }

    /** 整体亮度 20~255 */
    public int brightness() { return sp.getInt("brightness", 180); }

    /** 色轮/脉动速度 5~120 */
    public float speed() { return sp.getInt("speed", 40); }

    /** 光效模式：edge / keyboard / auto */
    public String mode() { return sp.getString("mode", "auto"); }

    /** 光效主色：色相 0~360；-1 = 跟随节奏自动彩虹（默认） */
    public float lightColorHue() { return sp.getFloat("light_color_hue", -1f); }

    public void setLightColorHue(float hue) {
        sp.edit().putFloat("light_color_hue", hue).apply();
    }

    /** 数据源模式：fusion（融合·音乐+晕车，默认）/ music（音乐律动）/ motion（晕车缓解·车辆运动） */
    public String dataMode() { return sp.getString("data_mode", "fusion"); }

    /** 3D 粒子氛围引擎开关（默认关，保持原有光效行为） */
    public boolean particleEnabled() { return sp.getBoolean("particle_mode", false); }

    public void setParticleEnabled(boolean on) { sp.edit().putBoolean("particle_mode", on).apply(); }

    /** 粒子档位：high=高画质(3000+1000) / eco=节电(700+300) */
    public String particleQuality() { return sp.getString("particle_quality", "high"); }

    public void setParticleQuality(String q) { sp.edit().putString("particle_quality", q).apply(); }

    // ---- 晕车场景自动检测（v1.8 增强）----

    /** 晕车场景自动检测总开关 */
    public boolean carsickEnabled() { return sp.getBoolean("carsick_enabled", true); }

    /** 车辆运动传感器触发晕车判定 */
    public boolean motionTriggerEnabled() { return sp.getBoolean("motion_trigger_enabled", true); }

    /** 进入晕车环境时 Toast 提示 */
    public boolean carsickToast() { return sp.getBoolean("carsick_toast", true); }

    /** 音乐自动触发：检测到音乐播放时自动开启光效，音乐停止后自动关闭。
     *  默认关闭——保证手动开启服务即显示光效（原行为）；需要省电时再手动打开。 */
    public boolean musicAutoEnabled() { return sp.getBoolean("music_auto_enabled", false); }

    public void setMusicAutoEnabled(boolean on) {
        sp.edit().putBoolean("music_auto_enabled", on).apply();
    }

    /** 音乐识别黑名单包名集合：这些应用在前台时不触发音乐光效（如视频/直播等带背景音乐的应用） */
    public Set<String> musicBlacklist() {
        Set<String> s = sp.getStringSet("music_blacklist", null);
        return s == null ? Collections.emptySet() : s;
    }

    public void saveMusicBlacklist(Set<String> apps) {
        sp.edit().putStringSet("music_blacklist", new HashSet<>(apps)).apply();
    }

    /** 内置打车/导航类应用包名（无需手动勾选，识别即触发晕车缓解） */
    public static final String[] DEFAULT_CARSICK_APPS = {
            "com.autonavi.minimap",      // 高德地图
            "com.baidu.BaiduMap",        // 百度地图
            "com.tencent.map",           // 腾讯地图
            "com.sdu.didi.psnger",       // 滴滴出行
            "com.didi.fleet.carclient",  // 滴滴司机
            "com.amap.android.rtbt",     // 高德打车/顺风车
            "com.caocao.motop",          // 曹操出行
            "com.t3go.t3go",             // T3出行
            "com.huaxiaozhu",            // 花小猪打车
            "com.meituan.car",           // 美团打车
            "com.zhugecars.com.cn",      // 首汽约车
            "com.shenzhou.chefu",        // 神州专车
    };

    /** 是否内置打车/导航类应用 */
    public static boolean isCarsickApp(String pkg) {
        if (pkg == null) return false;
        for (String s : DEFAULT_CARSICK_APPS) if (s.equals(pkg)) return true;
        return false;
    }

    public boolean vibrationEnabled() { return sp.getBoolean("vibration_enabled", true); }

    public int vibrationIntensity() { return sp.getInt("vibration_intensity", 120); }

    public boolean animEnabled() { return sp.getBoolean("anim_enabled", true); }

    public boolean autoStart() { return sp.getBoolean("auto_start", true); }

    /** 指定应用自动开启光效总开关 */
    public boolean triggerEnabled() { return sp.getBoolean("app_trigger_enabled", false); }

    /** 触发应用包名集合（打开这些应用的前台时强制开启光效） */
    public Set<String> triggerApps() {
        Set<String> s = sp.getStringSet("app_trigger_apps", null);
        return s == null ? Collections.emptySet() : s;
    }

    public void saveTriggerApps(Set<String> apps) {
        sp.edit().putStringSet("app_trigger_apps", new HashSet<>(apps)).apply();
    }
}
