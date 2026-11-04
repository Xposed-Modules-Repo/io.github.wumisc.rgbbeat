package io.github.wumisc.rgbbeat.core;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.net.Uri;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import io.github.wumisc.rgbbeat.R;

/**
 * 高潮礼物特效：全屏视频 + 黑底透明 + 震动 + 音效。
 *
 * 用 TextureView 代替 VideoView，这样可以做颜色键控：
 * 把视频里的黑色像素变成透明，只保留亮的特效图案，
 * 像抖音礼物那样飘在原来的画面上面，不是盖住整个屏幕。
 */
public final class HighlightEffectsRenderer {

    private static final String TAG = "RGBBeat/Highlight";

    public static final int EFFECT_ORBIT = 0;
    public static final int EFFECT_OCEAN = 1;
    public static final int EFFECT_PHOENIX = 2;
    public static final int EFFECT_COUNT = 3;

    private final Context ctx;
    private final BeatState state;
    private final VibrationEngine vib;
    private TextureView textureView;
    private MediaPlayer mediaPlayer;
    private int currentEffect = -1;
    private boolean isPlaying = false;

    public HighlightEffectsRenderer(Context ctx, BeatState state) {
        this.ctx = ctx;
        this.state = state;
        this.vib = new VibrationEngine(ctx);
    }

    /**
     * 绑定到根布局，TextureView 会加到这个容器里
     */
    public void attach(ViewGroup rootView) {
        textureView = new TextureView(ctx);
        textureView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        textureView.setVisibility(View.GONE);

        // 设置 SurfaceTexture 可用监听
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                // Surface 准备好了，如果有待播放的视频就播
                if (pendingPlay) {
                    playPendingVideo();
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                releasePlayer();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
        });

        rootView.addView(textureView);
    }

    private boolean pendingPlay = false;
    private int pendingResId = 0;

    /**
     * 触发高潮特效：切换到下一个视频并播放
     */
    public void triggerHighlight() {
        if (isPlaying) {
            currentEffect = (currentEffect + 1) % EFFECT_COUNT;
        } else {
            currentEffect = (currentEffect + 1) % EFFECT_COUNT;
        }

        // 礼物特效震动
        vib.onMotion(1.0f, true, 200);

        // 礼物音效：已去掉（用户反馈音效太差）

        playEffect(currentEffect);
    }

    private void playEffect(int effectIndex) {
        int resId;
        switch (effectIndex) {
            case EFFECT_ORBIT:
                resId = R.raw.highlight_orbit;
                break;
            case EFFECT_OCEAN:
                resId = R.raw.highlight_ocean;
                break;
            case EFFECT_PHOENIX:
                resId = R.raw.highlight_phoenix;
                break;
            default:
                return;
        }

        pendingResId = resId;

        // 如果 Surface 已经准备好了，直接播
        if (textureView.isAvailable()) {
            playPendingVideo();
        } else {
            pendingPlay = true;
        }
    }

    private void playPendingVideo() {
        try {
            releasePlayer();

            mediaPlayer = new MediaPlayer();
            Uri uri = Uri.parse("android.resource://" + ctx.getPackageName() + "/" + pendingResId);
            mediaPlayer.setDataSource(ctx, uri);

            SurfaceTexture st = textureView.getSurfaceTexture();
            Surface surface = new Surface(st);
            mediaPlayer.setSurface(surface);

            mediaPlayer.setLooping(false);
            mediaPlayer.setVolume(0, 0); // 静音，不影响音乐

            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                isPlaying = true;
                textureView.setVisibility(View.VISIBLE);
                DiagLog.d(TAG, "播放礼物特效");
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                stop();
            });

            mediaPlayer.prepareAsync();
            pendingPlay = false;
        } catch (Throwable t) {
            DiagLog.w(TAG, "播放特效失败: " + t);
            stop();
        }
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Throwable ignored) {}
            mediaPlayer = null;
        }
    }

    public void stop() {
        releasePlayer();
        if (textureView != null) {
            textureView.setVisibility(View.GONE);
        }
        isPlaying = false;
        currentEffect = -1;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public boolean shouldSkipNormalDraw() {
        return isPlaying;
    }
}
