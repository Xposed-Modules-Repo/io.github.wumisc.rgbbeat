package io.github.wumisc.rgbbeat.core;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;

/**
 * 常驻前台服务：持有音频/车辆运动采集 → 节拍/运动语义 → 光效渲染 + 马达震动的完整链路，
 * 并创建全屏悬浮窗。设置变更经 applyPrefs() 实时同步。
 * dataMode=music：音频频谱驱动（音乐律动）；dataMode=motion：加速度计驱动（晕车缓解，车辆运动自动开启）。
 */
public class BeatService extends Service {

    private static final String TAG = "RGBBeat/Service";
    private static final String CHANNEL_ID = "rgbbeat";
    private static final int NOTIF_ID = 1;

    private static BeatService sInstance;

    /** 主线程 Handler：悬浮窗/View 的创建、显示、隐藏必须在主线程执行，
     *  否则在麦克风/传感器采集线程里直接操作会抛 "Can't create handler ... not called Looper.prepare()"。 */
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private BeatState state;
    private Prefs prefs;
    private AudioCapture capture;
    private BeatDetector detector;
    private MotionSense motion;
    private VibrationEngine vib;
    private OverlayWindow overlay;
    private LightRenderer renderer;
    private boolean started;
    private String lastSource = "";
    private boolean lastVehicleActive = false; // 晕车模式自动开关沿变检测
    private boolean triggerActive = false;     // 指定应用在前台：强制开启
    private boolean carsickActive = false;     // 当前处于乘车（晕车）环境
    private long lastCarsickToastMs = 0;       // 晕车 Toast 节流
    private long lastStateLogMs = 0; // 周期状态日志节流
    private android.os.PowerManager.WakeLock wakeLock; // 后台持续渲染保活

    // ---- 音乐自动触发：有音乐播放时开启光效，音乐停止后关闭（省电）----
    private boolean musicActive = false;       // 当前是否检测到音乐在播放
    private long lastMusicMs = 0;              // 最近一次检测到音乐的时间
    private long musicSince = 0;               // 本次连续有音频的起始时间（进入滞回）
    private static final long MUSIC_ENTER_MS = 600;     // 连续有音频 600ms 才确认进入（防噪声抖动）
    private static final long MUSIC_HOLD_MS = 4000;     // 音乐停止 4s 后才判定退出
    private static final int MUSIC_HIT_WINDOW_MS = 3000; // 节拍命中统计窗口（节奏密度）
    private static final int MUSIC_HIT_MIN = 2;          // 窗口内最少命中数=有稳定节奏（过滤视频对白/稀疏声音）
    private final long[] musicHitTimes = new long[16];   // 最近节拍命中时间戳（环形）
    private int musicHitIdx = 0;

    // ---- 媒体信息/歌词（LyricInfo 优先，MediaMetadata 兜底）----
    private String lastSongKey = "";          // 歌曲标识（歌名|歌手），变化时重置歌词进度
    private long lyricStartElapsed = 0;       // 当前歌曲开始观测时间（播放位置不可得时推算用）
    private String lastLyricKey = "";         // 已解析的 LRC 缓存标识
    private final java.util.ArrayList<LyricInfoReader.LyricLine> parsedLines = new java.util.ArrayList<>();
    private long lastMusicToastMs = 0;        // 音乐识别 Toast 节流

    // ---- 悬浮窗开关去抖：on→off 延迟 200ms 确认，切应用瞬间的抖动不闪断 ----
    private boolean overlayWanted = false;
    private final Runnable turnOnRun = () -> {
        try {
            LightOverlayView ov = overlay.show(renderer);
            renderer.attach(ov);
            overlay.setVisible(true);
            renderer.start();
            DiagLog.d(TAG, "overlay on: window=" + (ov != null)
                    + " render=" + (renderer != null && renderer.isRunning()));
        } catch (Throwable t) {
            DiagLog.w(TAG, "ensureOverlayOn err: " + t);
        }
    };
    private final Runnable turnOffRun = () -> {
        if (overlayWanted) return; // 期间重新需要光效，取消关闭
        try {
            if (renderer != null) renderer.stop();
            if (overlay != null) overlay.setVisible(false); // 窗口保留，仅隐藏，避免重建闪断
        } catch (Throwable t) {
            DiagLog.w(TAG, "ensureOverlayOff err: " + t);
        }
    };

    public static BeatService instance() { return sInstance; }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!started) {
            try {
                startForeground(NOTIF_ID, buildNotification());
            } catch (Throwable t) {
                // Android 14+ 前台服务类型/通知限制下 startForeground 可能抛异常，
                // 兜底后继续尝试初始化，绝不因此崩溃
                DiagLog.w(TAG, "startForeground failed: " + t);
            }
            try {
                init();
                started = true;
            } catch (Throwable t) {
                // 初始化任一步骤失败（如权限/渲染/音频异常）都不崩进程
                DiagLog.w(TAG, "service init error: " + t);
                stopSelf();
            }
        }
        return START_STICKY;
    }

    private void init() {
        DiagLog.init(this); // 调试日志落盘（无需 adb，App 内可查看）
        prefs = new Prefs(this);
        state = new BeatState();
        state.mode = prefs.mode();
        state.dataMode = prefs.dataMode();

        // 后台持续渲染保活：partial wakelock 让渲染线程在切后台/锁屏时不被 CPU 睡眠中断，
        // 保证光效/强调色持续流动（而非仅前台/使用 RGBBeat 时才强调）
        try {
            android.os.PowerManager pm = getSystemService(android.os.PowerManager.class);
            wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "RGBBeat:render");
            wakeLock.acquire();
        } catch (Throwable t) {
            DiagLog.w(TAG, "wakelock acquire failed: " + t);
        }

        overlay = new OverlayWindow(this, state);
        renderer = new LightRenderer(state, prefs);
        vib = new VibrationEngine(this);
        refreshMediaInfo(); // 首次读取当前播放媒体信息

        if (isFusion()) {
            // 融合模式：音乐律动 + 晕车缓解并行，听音乐也能叠加晕车光效
            motion = new MotionSense(this, this::onMotion);
            motion.start();
            detector = new BeatDetector(state, prefs.sensitivity(), prefs.cooldownMs());
            lastSource = prefs.audioSource();
            capture = new AudioCapture(this::onFrame);
            capture.start(lastSource);
            refreshOverlay(); // 融合：音乐自动触发 或 车辆运动 命中才显示
        } else if (isMotion()) {
            // 晕车缓解模式：加速度计驱动，车辆运动时自动开启光效
            motion = new MotionSense(this, this::onMotion);
            motion.start();
        } else {
            // 音乐律动模式：音频频谱驱动，音乐自动触发命中才显示光效
            detector = new BeatDetector(state, prefs.sensitivity(), prefs.cooldownMs());
            lastSource = prefs.audioSource();
            capture = new AudioCapture(this::onFrame);
            capture.start(lastSource);
            refreshOverlay();
        }
    }

    private boolean isMotion() {
        return "motion".equals(prefs.dataMode());
    }

    private boolean isFusion() {
        return "fusion".equals(prefs.dataMode());
    }

    /** 综合决定悬浮窗开关：指定应用强制开 > 音乐模式随音乐播放 > 晕车模式随车辆运动。
     *  悬浮窗操作切到主线程执行；on→off 延迟 200ms 确认，切应用等瞬时抖动不会闪断光效。 */
    private void refreshOverlay() {
        if (renderer == null) return;
        boolean musicPart = isFusion() || "music".equals(state.dataMode); // 涉及音乐的模式
        boolean motionPart = isFusion() || isMotion();                    // 涉及运动的模式
        boolean musicOn = musicPart && (!prefs.musicAutoEnabled() || musicActive);
        boolean motionOn = motionPart && state.vehicleActive;
        boolean on = triggerActive || musicOn || motionOn;
        if (on == overlayWanted) return; // 状态无变化，不触碰悬浮窗
        overlayWanted = on;
        DiagLog.d(TAG, "overlay want on=" + on + " trigger=" + triggerActive
                + " music=" + (musicPart && musicOn) + " motion=" + (motionPart && motionOn));
        mainHandler.removeCallbacks(turnOnRun);
        mainHandler.removeCallbacks(turnOffRun);
        if (on) {
            mainHandler.post(turnOnRun);            // 需要光效：立即开
        } else {
            mainHandler.postDelayed(turnOffRun, 200); // 停止：延迟确认，避免短暂抖动闪断
        }
    }

    /** 无障碍触发服务回调：指定应用进入/离开前台时强制开启/回落光效，并更新晕车环境判定 */
    public void setTriggerActive(boolean active, String pkg) {
        if (triggerActive == active) return;
        triggerActive = active;
        DiagLog.d(TAG, "triggerActive -> " + active + " pkg=" + pkg);
        refreshOverlay();
        updateCarsick();
    }

    /** 综合判定是否处于乘车（晕车）环境：前台打车/导航 App 或 车辆运动 */
    private void updateCarsick() {
        if (prefs == null || !prefs.carsickEnabled()) return;
        boolean now = triggerActive || (lastVehicleActive && prefs.motionTriggerEnabled());
        if (now && !carsickActive) {
            carsickActive = true;
            DiagLog.d(TAG, "carsick environment entered");
            if (prefs.carsickToast()) toastCarsick();
        } else if (!now && carsickActive) {
            carsickActive = false;
            DiagLog.d(TAG, "carsick environment left");
        }
    }

    /** 进入乘车环境的 Toast 提示（节流：1 分钟内只提示一次） */
    private void toastCarsick() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastCarsickToastMs < 60000) return;
        lastCarsickToastMs = now;
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        main.post(() -> {
            try {
                android.widget.Toast.makeText(this, "已进入乘车环境 · 晕车缓解已开启", android.widget.Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) { }
        });
    }

    /** 车辆运动帧回调（传感器线程）：更新状态 + 随运动轻微震动 + 自动开/关光效 */
    private void onMotion(float energy, float x, float y, float jitter, boolean active) {
        if (state == null) return;
        state.motionEnergy = energy;
        state.motionX = x;
        state.motionY = y;
        state.motionJitter = jitter;
        state.vehicleActive = active; // 供 refreshOverlay 判定运动触发

        // 随运动轻微震动（颠簸/加减速有感知但不刺激）
        if (vib != null && energy > 0.2f) {
            vib.onMotion(energy, prefs.vibrationEnabled(), prefs.vibrationIntensity());
        }

        // 自动开启/关闭光效：车辆运动激活显示，静止超时隐藏；并更新晕车环境判定
        if (active != lastVehicleActive) {
            lastVehicleActive = active;
            refreshOverlay();
            updateCarsick();
            DiagLog.d(TAG, "vehicleActive -> " + active);
        }

        // 周期状态日志：判断传感器→光效链路是否在走（每 2s 一条）
        long now = SystemClock.elapsedRealtime();
        if (now - lastStateLogMs >= 2000) {
            lastStateLogMs = now;
            DiagLog.d(TAG, "motion energy=" + String.format("%.2f", energy)
                    + " x=" + String.format("%.2f", x)
                    + " y=" + String.format("%.2f", y)
                    + " jitter=" + String.format("%.2f", jitter)
                    + " active=" + active);
        }
    }

    /** 音频帧回调（采集线程）：energy=能量, pitch=主导音高强度, pitchHue=主导音高强调色相 */
    private void onFrame(float energy, float pitch, float pitchHue, long nowMs) {
        if (detector != null) detector.feed(energy, nowMs);
        state.pitchLevel = pitch;
        state.pitchHue = pitchHue;
        updateMusicActive(nowMs);
        if (state.beat) {
            // 记录节拍命中时间，供音乐识别判定（有节奏的音乐才有稳定命中）
            musicHitTimes[musicHitIdx % musicHitTimes.length] = nowMs;
            musicHitIdx++;
            // 节拍命中诊断：便于排查"震动不工作"是未命中节拍还是马达路径问题
            DiagLog.d(TAG, "beat hit energy=" + state.energy + " pitch=" + pitch
                    + " intensity=" + prefs.vibrationIntensity());
            if (vib != null) {
                vib.onBeat(state.energy, pitch, prefs.vibrationEnabled(), prefs.vibrationIntensity());
            }
        }
        // 周期状态日志：判断音频→节拍链路是否在走（每 2s 一条）
        if (nowMs - lastStateLogMs >= 2000) {
            lastStateLogMs = nowMs;
            refreshMediaInfo(); // 同步刷新当前播放歌名/歌手
            DiagLog.d(TAG, "state rawEnergy=" + String.format("%.3f", energy)
                    + " energy=" + String.format("%.3f", state.energy)
                    + " beat=" + state.beat + " bpm=" + String.format("%.0f", state.bpm)
                    + " pitch=" + String.format("%.2f", pitch)
                    + " hue=" + String.format("%.0f", pitchHue) + " mode=" + state.mode
                    + " music=" + musicActive);
        }
    }

    /**
     * 轮询系统 MediaSession 读取当前播放信息：LyricInfo 模块写入的 lyricInfo JSON 优先
     * （歌名/歌手/歌词），无则退回普通 MediaMetadata 歌名/歌手（全反射，兼容裁剪 SDK）。
     * 歌词行按播放进度定位，写入 state.lyricPrev/Now/Next 供悬浮窗顶部轮换显示。
     */
    private void refreshMediaInfo() {
        try {
            android.media.session.MediaSessionManager msm =
                    getSystemService(android.media.session.MediaSessionManager.class);
            if (msm == null) return;
            String t = null, a = null;
            LyricInfoReader.LyricInfo li = null;
            android.media.session.MediaController chosen = null;
            for (android.media.session.MediaController c : msm.getActiveSessions(null)) {
                li = LyricInfoReader.read(c);
                if (li != null && li.usable()) {
                    t = li.songName.isEmpty() ? fallbackTitle(c) : li.songName;
                    a = li.artist;
                    chosen = c;
                    break;
                }
                String[] ta = LyricInfoReader.readTitleArtist(c);
                if (ta != null) {
                    t = ta[0];
                    a = ta[1];
                    chosen = c;
                    break;
                }
            }
            if (t == null || t.isEmpty()) {
                state.title = null;
                state.artist = null;
                state.lyricReady = false;
                state.lyricPrev = state.lyricNow = state.lyricNext = null;
                return;
            }
            // 歌曲变化：重置歌词进度起点与已解析缓存
            String key = t + "|" + (a != null ? a : "");
            if (!key.equals(lastSongKey)) {
                lastSongKey = key;
                lyricStartElapsed = SystemClock.elapsedRealtime();
                lastLyricKey = "";
                parsedLines.clear();
            }
            if (!t.equals(state.title) || !java.util.Objects.equals(a, state.artist)) {
                state.title = t;
                state.artist = a;
                DiagLog.d(TAG, "media: " + t + " - " + a);
            }
            // 歌词：LyricInfo 有 LRC 时解析并按播放进度定位当前行
            if (li != null && li.lyric != null && !li.lyric.isEmpty()) {
                long pos = chosen != null ? LyricInfoReader.readPositionMs(chosen) : -1;
                if (pos < 0) pos = SystemClock.elapsedRealtime() - lyricStartElapsed; // 推算
                updateLyricLine(li.lyric, pos);
            } else {
                state.lyricReady = false;
                state.lyricPrev = state.lyricNow = state.lyricNext = null;
            }
        } catch (Throwable ignored) { }
    }

    private String fallbackTitle(android.media.session.MediaController c) {
        String[] ta = LyricInfoReader.readTitleArtist(c);
        return ta != null ? ta[0] : "";
    }

    /** 解析/缓存 LRC 并按播放位置更新当前/上一行/下一行歌词（变化时才写入状态） */
    private void updateLyricLine(String lrc, long posMs) {
        if (!lrc.equals(lastLyricKey)) {
            lastLyricKey = lrc;
            parsedLines.clear();
            parsedLines.addAll(LyricInfoReader.parseLrc(lrc));
        }
        if (parsedLines.isEmpty()) {
            state.lyricReady = false;
            state.lyricPrev = state.lyricNow = state.lyricNext = null;
            return;
        }
        int idx = LyricInfoReader.findLine(parsedLines, posMs);
        String now = idx >= 0 ? parsedLines.get(idx).text : null;
        String prev = idx > 0 ? parsedLines.get(idx - 1).text : null;
        String next = (idx >= 0 && idx + 1 < parsedLines.size()) ? parsedLines.get(idx + 1).text : null;
        if (!java.util.Objects.equals(state.lyricNow, now)
                || !java.util.Objects.equals(state.lyricPrev, prev)
                || !java.util.Objects.equals(state.lyricNext, next)) {
            state.lyricPrev = prev;
            state.lyricNow = now;
            state.lyricNext = next;
            state.lyricReady = now != null;
            if (now != null) DiagLog.d(TAG, "lyric: " + now);
        }
    }

    /**
     * 音乐自动触发判定：以"节拍命中频率"识别是否在放音乐（有节奏的音乐才有稳定命中，
     * 麦克风能量基线很低不可靠），开启光效；音乐停止 4s 后自动关闭。
     * 开关未开启时恒为 false（不干预其它触发）。
     */
    private void updateMusicActive(long nowMs) {
        if (state == null || prefs == null) return;
        if (!prefs.musicAutoEnabled()) {
            if (musicActive) musicActive = false;
            musicSince = 0;
            return;
        }
        // 音乐识别黑名单：当前前台应用在黑名单内（视频/直播等）则不触发音乐光效
        String fg = state.foregroundPkg;
        if (fg != null && prefs.musicBlacklist().contains(fg)) {
            if (musicActive) {
                musicActive = false;
                state.title = null;
                state.artist = null;
                state.lyricReady = false;
                state.lyricPrev = state.lyricNow = state.lyricNext = null;
                lastMusicMs = 0;
                refreshOverlay();
                DiagLog.d(TAG, "musicActive -> false (blacklist " + fg + ")");
            }
            musicSince = 0;
            return;
        }
        boolean playing = recentBeatHits(nowMs) >= MUSIC_HIT_MIN;
        if (playing) {
            if (musicSince == 0) musicSince = nowMs;
            lastMusicMs = nowMs;
        } else {
            musicSince = 0; // 命中稀疏，重新累计
        }
        boolean want = musicActive;
        if (!musicActive) {
            // 进入滞回：连续有节拍 600ms 才确认"有音乐"，避免麦克风噪声抖动误进/误出
            want = musicSince != 0 && (nowMs - musicSince) >= MUSIC_ENTER_MS;
        } else {
            want = (nowMs - lastMusicMs) <= MUSIC_HOLD_MS;
        }
        if (want != musicActive) {
            musicActive = want;
            if (!want) {
                // 音乐停止：清掉顶部歌名/歌词，避免暂停后残留
                state.title = null;
                state.artist = null;
                state.lyricReady = false;
                state.lyricPrev = state.lyricNow = state.lyricNext = null;
                lastMusicMs = 0;
            } else {
                refreshMediaInfo(); // 刚确认有音乐：立刻抓一次歌名/歌手/歌词
                toastMusic();       // 区分触发源：识别到音乐就提示"识别到音乐"，而非统一"乘车环境"
            }
            refreshOverlay();
            DiagLog.d(TAG, "musicActive -> " + musicActive);
        }
    }

    /** 更新当前前台应用包名（无障碍服务写入），供音乐黑名单判定使用 */
    public void setForegroundPkg(String pkg) {
        if (state != null) state.foregroundPkg = pkg;
    }

    /** 最近 MUSIC_HIT_WINDOW_MS 毫秒内的节拍命中次数 */
    private int recentBeatHits(long nowMs) {
        int n = 0;
        for (long t : musicHitTimes) {
            if (t > 0 && nowMs - t <= MUSIC_HIT_WINDOW_MS) n++;
        }
        return n;
    }

    /** 识别到音乐的 Toast 提示（节流：1 分钟内只提示一次） */
    private void toastMusic() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastMusicToastMs < 60000) return;
        lastMusicToastMs = now;
        mainHandler.post(() -> {
            try {
                android.widget.Toast.makeText(this, "已识别到音乐 · 光效已开启", android.widget.Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) { }
        });
    }

    /**
     * 任务被移除（用户上滑清除 App / HyperOS 后台清理）时，前台服务常被连带杀掉，
     * 导致"退出软件后光效停止"。若服务仍在运行（用户并未主动点停止），则自愈重启，
     * 让光效与震动在脱离 App 后持续工作。
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        if (!started) return; // 用户已点停止：不再拉起
        try {
            startForegroundService(new Intent(this, BeatService.class));
            DiagLog.d(TAG, "task removed, self-heal restart");
        } catch (Throwable t) {
            DiagLog.w(TAG, "self-heal restart failed: " + t);
        }
    }

    /** 设置变更后实时同步（SettingsActivity 调用） */
    public void applyPrefs() {
        if (prefs == null || state == null) return;
        state.mode = prefs.mode();
        // 数据源模式变化：音乐 ↔ 晕车切换数据源与显示
        String wantMode = prefs.dataMode();
        if (!wantMode.equals(state.dataMode)) {
            state.dataMode = wantMode;
            swapDataSource();
            return;
        }
        // 音乐模式音频来源变化时才重启采集，避免频繁切换
        String want = prefs.audioSource();
        if (capture != null && !want.equals(lastSource)) {
            lastSource = want;
            new Thread(() -> capture.start(want)).start();
        }
    }

    /** 切换数据源（融合 / 音乐 / 晕车），并同步悬浮窗显示状态 */
    private void swapDataSource() {
        musicActive = false; // 重置音乐态，等待新数据源重新判定
        if (motion != null) { motion.stop(); motion = null; }
        if (capture != null) { capture.stop(); capture = null; }
        if (isFusion()) {
            lastVehicleActive = false;
            motion = new MotionSense(this, this::onMotion);
            motion.start();
            detector = new BeatDetector(state, prefs.sensitivity(), prefs.cooldownMs());
            lastSource = prefs.audioSource();
            capture = new AudioCapture(this::onFrame);
            capture.start(lastSource);
            refreshOverlay();
            DiagLog.d(TAG, "switched to fusion(音乐+晕车) mode");
        } else if (isMotion()) {
            lastVehicleActive = false;
            motion = new MotionSense(this, this::onMotion);
            motion.start();
            refreshOverlay(); // 触发应用在前台则强制开，否则等车辆运动自动开启
            DiagLog.d(TAG, "switched to motion(晕车) mode");
        } else {
            detector = new BeatDetector(state, prefs.sensitivity(), prefs.cooldownMs());
            lastSource = prefs.audioSource();
            capture = new AudioCapture(this::onFrame);
            capture.start(lastSource);
            refreshOverlay();
            DiagLog.d(TAG, "switched to music(音乐) mode");
        }
    }

    private Notification buildNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                "RGBBeat 光效服务", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("音乐律动 RGB 光效运行中");
        nm.createNotificationChannel(ch);

        Notification.Builder b = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("RGBBeat")
                .setContentText("音乐律动 RGB 光效运行中")
                .setOngoing(true);
        return b.build();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        sInstance = null;
        if (wakeLock != null) {
            try { if (wakeLock.isHeld()) wakeLock.release(); } catch (Throwable ignored) { }
            wakeLock = null;
        }
        if (renderer != null) renderer.stop();
        if (motion != null) motion.stop();
        if (capture != null) capture.stop();
        if (overlay != null) overlay.hide();
        if (vib != null) vib.release();
        started = false;
        super.onDestroy();
    }

    public static void start(Context c) {
        c.startForegroundService(new Intent(c, BeatService.class));
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, BeatService.class));
    }
}
