package io.github.wumisc.rgbbeat.core;

/**
 * 跨线程共享的实时状态。AudioCapture/BeatDetector 写入，LightRenderer/VibrationEngine 读取。
 * 全部为 volatile，保证可见性。
 */
public final class BeatState {

    /** 归一化能量 0~1（越大越强） */
    public volatile float energy = 0f;
    /** 当前帧是否命中节拍 */
    public volatile boolean beat = false;
    /** 色相相位 0~360，随时间与音乐漂移 */
    public volatile float hue = 0f;
    /** 跑马灯沿边缘流动进度 0~1（顺时针一圈） */
    public volatile float marqueeT = 0f;
    /** 脉动强度 0~1 */
    public volatile float intensity = 0f;
    /** 估算 BPM */
    public volatile float bpm = 0f;

    /** 主导音高强度 0~1：由音符（A~E 等）高低映射，高音更强；驱动灯光/震动强度随旋律音符变化 */
    public volatile float pitchLevel = 0.5f;
    /** 主导音高对应的强调色相 0~360：高音出现时屏幕边缘亮起该色（人声/旋律强调色） */
    public volatile float pitchHue = 0f;

    /** 数据源模式：music（音乐律动）/ motion（晕车缓解·车辆运动） */
    public volatile String dataMode = "music";

    // ---- 晕车缓解·车辆运动状态（MotionSense 写入，LightRenderer/VibrationEngine 读取）----
    /** 车辆运动强度 0~1（加速度合成幅度归一化） */
    public volatile float motionEnergy = 0f;
    /** 横向偏移 -1~1：转弯/变道（手机 x 轴） */
    public volatile float motionX = 0f;
    /** 纵向偏移 -1~1：加减速（手机 z 轴，去重力后） */
    public volatile float motionY = 0f;
    /** 颠簸/摇晃 0~1：加速度高频抖动 */
    public volatile float motionJitter = 0f;
    /** 检测到车辆持续运动（自动开启光效）；静止超时自动关 */
    public volatile boolean vehicleActive = false;

    /** 当前前台应用包名（无障碍服务写入，供音乐黑名单/触发判断使用） */
    public volatile String foregroundPkg = null;

    /** 软键盘是否可见 */
    public volatile boolean keyboardVisible = false;
    /** 软键盘高度 px */
    public volatile int keyboardHeightPx = 0;

    /** 光效模式：edge / keyboard / auto */
    public volatile String mode = "auto";

    // ---- 当前播放媒体信息（MediaSession 轮询写入，悬浮窗顶部显示）----
    /** 当前播放歌曲名（无则 null） */
    public volatile String title = null;
    /** 当前播放歌手（无则 null） */
    public volatile String artist = null;
    /** 歌词可用（LyricInfo 写入的 lyricInfo 已解析出当前行） */
    public volatile boolean lyricReady = false;
    /** 歌词上一行（弱化显示） */
    public volatile String lyricPrev = null;
    /** 歌词当前行 */
    public volatile String lyricNow = null;
    /** 歌词下一行（弱化显示） */
    public volatile String lyricNext = null;
}
