package io.github.wumisc.rgbbeat.render;

import io.github.wumisc.rgbbeat.data.*;
import io.github.wumisc.rgbbeat.engine.*;
import io.github.wumisc.rgbbeat.render.*;
import io.github.wumisc.rgbbeat.service.*;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.IOException;

import io.github.wumisc.rgbbeat.R;

/**
 * 高潮礼物特效：星际轨道 / 心海流光 / 鎏金凤凰（res/raw 下三段 mp4）。
 *
 * 渲染链路：MediaPlayer → EffectVideoView 的 OES 外部纹理 → GL 色键抠背景 →
 * 按素材原始宽高比等比居中绘制到透明 Surface。
 *
 * 修复要点：
 *  · 原实现把横屏 mp4 铺到 MATCH_PARENT 的 TextureView，被拉伸/裁切 → 只能看到局部；
 *    现在按视频原始比例 FIT_CENTER 居中，完整呈现、不裁切、不变形。
 *  · 原实现没有 alpha，靠 setAlpha(0.82) 硬盖一层黑底 → 整屏灰蒙蒙、挡住界面；
 *    现在用 GL 片元着色器把暗部变成全透明，只叠加亮部流光。
 *  · 原实现 TextureView 初始 GONE，拿不到 SurfaceTexture，播放请求永远挂起；
 *    现在 GL View 始终 VISIBLE，Surface 由 onGlReady 回调驱动，未就绪时挂起再补播。
 *  · stop() 不再重置 currentEffect，否则每次都从第 0 段开始，另外两段永远轮不到。
 *  · 用 AssetFileDescriptor 打开 raw 资源，避免部分 ROM 上 android.resource:// URI 解析失败；
 *    监听 onError 并复位状态，Surface / MediaPlayer 成对释放，避免解码失败后卡死或泄漏。
 */
public final class HighlightEffectsRenderer {

    private static final String TAG = "RGBBeat/Highlight";

    public static final int EFFECT_ORBIT = 0;
    public static final int EFFECT_OCEAN = 1;
    public static final int EFFECT_PHOENIX = 2;
    public static final int EFFECT_COUNT = 3;

    private static final int[] EFFECT_RES = {
            R.raw.highlight_orbit,
            R.raw.highlight_ocean,
            R.raw.highlight_phoenix
    };

    private final Context ctx;
    private final Prefs prefs;
    private final VibrationEngine vib;
    private final Handler main = new Handler(Looper.getMainLooper());

    private EffectVideoView glView;
    private MediaPlayer player;
    private int currentEffect = -1;
    private boolean playing;        // 准备中或播放中：防止重入
    private boolean startedPlaying; // 真的拿到首帧开始播了（区别于「卡在 prepare」）
    private boolean pendingPlay;    // GL Surface 尚未就绪，等 onGlReady
    private int pendingResId;
    private boolean attached;

    /** prepare 超时看门狗：解码器卡住时强制复位，否则 playing 永久为 true，特效再也不触发 */
    private static final long PREPARE_TIMEOUT_MS = 6000;
    private final Runnable prepareWatchdog = () -> {
        if (playing && !startedPlaying) {
            DiagLog.w(TAG, "prepare timeout " + PREPARE_TIMEOUT_MS + "ms, reset state");
            stop();
        }
    };

    /**
     * 兜底硬停：即使 onCompletion 没回调（部分 ROM 对 raw 资源有异常），也要把特效层清掉。
     * 叠加层一旦留在最上层就是事故，宁可早停也不能滞留。
     */
    private final Runnable hardStop = () -> {
        if (playing) {
            DiagLog.w(TAG, "hard stop after duration, clear effect layer");
            stop();
        }
    };

    public HighlightEffectsRenderer(Context ctx, Prefs prefs) {
        this.ctx = ctx;
        this.prefs = prefs;
        this.vib = new VibrationEngine(ctx);
    }

    /**
     * 绑定到悬浮窗根布局。GL 层按默认 z 序放进窗口（addView index=0，位于光效层之下），
     * 窗口本身是 FLAG_NOT_TOUCHABLE，所以触摸照常穿透到下层应用。
     * 必须在主线程调用。
     */
    public void attach(ViewGroup rootView) {
        if (attached || rootView == null) return;

        glView = new EffectVideoView(ctx);
        glView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        glView.setReadyCallback(surface -> main.post(() -> {
            if (pendingPlay) startPlayback(surface);
        }));
        rootView.addView(glView, 0);
        attached = true;
    }

    /** 触发一次高潮特效：按设置自动轮播或播放用户指定的那一段 */
    public void triggerHighlight() {
        playEffect(EffectPicker.resolve(prefs.highlightEffect(), currentEffect));
    }

    /** 手动预览。effectIndex < 0 表示按设置自动选择下一段，0..2 表示指定特效 */
    public void preview(int effectIndex) {
        if (effectIndex < 0) triggerHighlight();
        else playEffect(effectIndex);
    }

    private void playEffect(int effectIndex) {
        if (!attached || glView == null || effectIndex < 0 || effectIndex >= EFFECT_COUNT) {
            DiagLog.w(TAG, "skip play: attached=" + attached + " index=" + effectIndex
                    + " (悬浮窗未创建时无法预览)");
            return;
        }
        currentEffect = effectIndex;   // 注意：stop() 不重置它，保证三段都能轮到
        pendingResId = EFFECT_RES[effectIndex];
        refreshVisual();               // 每次播放都按最新的抠背景模式与染色，无需重启服务

        // 礼物震动遵循用户的总开关与强度，不再硬编码
        if (prefs.vibrationEnabled()) {
            vib.onBeat(1f, 1f, true, prefs.vibrationIntensity());
        }

        Surface surface = glView.isGlReady() ? glView.getVideoSurface() : null;
        if (surface == null) {
            pendingPlay = true;
            DiagLog.d(TAG, "waiting GL surface for effect " + effectIndex);
            return;
        }
        startPlayback(surface);
    }

    private void startPlayback(Surface surface) {
        if (!attached || glView == null || surface == null) return;
        try {
            releasePlayer();

            MediaPlayer mp = new MediaPlayer();
            mp.setSurface(surface);
            AssetFileDescriptor afd = ctx.getResources().openRawResourceFd(pendingResId);
            if (afd == null) throw new IOException("openRawResourceFd failed: " + pendingResId);
            try {
                // 用 fd + offset + length，比 android.resource:// URI 兼容性更好
                mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            } finally {
                afd.close();
            }

            mp.setLooping(false);
            mp.setVolume(0f, 0f);   // 静音，不影响正在播放的音乐
            try {
                // 帧完整写进 SurfaceTexture，缩放/居中全部由 GL 顶点控制
                mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            } catch (Throwable ignored) { }

            mp.setOnVideoSizeChangedListener((p, w, h) -> {
                if (glView != null) glView.setVideoSize(w, h);
            });
            mp.setOnPreparedListener(p -> {
                if (glView != null) {
                    glView.setVideoSize(p.getVideoWidth(), p.getVideoHeight());
                    glView.setShowing(true);
                }
                p.start();
                playing = true;
                startedPlaying = true;
                main.removeCallbacks(prepareWatchdog);
                // 兜底：到点强制清层，避免叠加层滞留
                int dur = 0;
                try { dur = p.getDuration(); } catch (Throwable ignored) { }
                main.removeCallbacks(hardStop);
                main.postDelayed(hardStop, Math.max(4000, dur + 1500));
                DiagLog.d(TAG, "playing effect " + currentEffect + " dur=" + dur);
            });
            mp.setOnCompletionListener(p -> {
                DiagLog.d(TAG, "effect finished " + currentEffect);
                stop();
            });
            mp.setOnErrorListener((p, what, extra) -> {
                // 常见于设备不支持该编码 / 素材损坏，必须复位而不是卡住
                DiagLog.w(TAG, "MediaPlayer error what=" + what + " extra=" + extra
                        + " (素材编码或解码器不兼容)");
                stop();
                return true;
            });

            player = mp;
            playing = true;         // 准备阶段即占用，冷却期内不重复触发
            startedPlaying = false;
            pendingPlay = false;
            mp.prepareAsync();
            main.removeCallbacks(prepareWatchdog);
            main.postDelayed(prepareWatchdog, PREPARE_TIMEOUT_MS);
        } catch (Throwable t) {
            DiagLog.w(TAG, "play effect failed: " + t);
            stop();
        }
    }

    /** 设置页改了「背景抠除模式 / 特效颜色」后立即重绘，正在播放的画面也会跟着变 */
    public void refreshVisual() {
        if (glView != null) {
            glView.setKeyMode(EffectKeyMode.toShaderMode(prefs.highlightKeyMode()));
            glView.setTint(EffectTint.resolve(prefs.highlightTint()));
        }
    }

    /** 停止播放并复位：清屏为全透明，不再遮挡任何界面。currentEffect 保留用于轮播 */
    public void stop() {
        main.removeCallbacks(prepareWatchdog);
        main.removeCallbacks(hardStop);
        releasePlayer();
        pendingPlay = false;
        playing = false;
        startedPlaying = false;
        if (glView != null) glView.setShowing(false);
    }

    /** 悬浮窗销毁 / 服务停止时彻底释放 */
    public void release() {
        stop();
        try { vib.release(); } catch (Throwable ignored) { }
        if (glView != null) {
            try { glView.release(); } catch (Throwable ignored) { }
            glView = null;
        }
        attached = false;
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.stop(); } catch (Throwable ignored) { }
            try { player.release(); } catch (Throwable ignored) { }
            player = null;
        }
    }

    /** 准备中或播放中 */
    public boolean isPlaying() { return playing; }

    public int currentEffect() { return currentEffect; }
}
