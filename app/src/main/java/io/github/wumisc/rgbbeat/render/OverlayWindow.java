package io.github.wumisc.rgbbeat.render;

import io.github.wumisc.rgbbeat.data.*;
import io.github.wumisc.rgbbeat.engine.*;
import io.github.wumisc.rgbbeat.render.*;
import io.github.wumisc.rgbbeat.service.*;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * 悬浮窗管理：创建全屏透明 FrameLayout 覆盖层。
 * 根布局是 FrameLayout，里面叠两层（按 z 序，后加的在上）：
 *  1. 底层：EffectVideoView — 高潮礼物特效（TextureView + EGL 色键，无独立图层）
 *  2. 顶层：LightOverlayView — 绘制边缘光效/粒子（透明 Canvas，可与礼物特效叠加）
 *
 * - FLAG_NOT_TOUCHABLE / NOT_TOUCH_MODAL：触摸完全穿透，不拦截任何操作。
 *   （注：GL 层绝不能用 setZOrderOnTop(true)，那会把 Surface 提到窗口之外的顶层，
 *    部分 ROM 上会导致整机触摸失效。）
 * - FLAG_LAYOUT_IN_SCREEN + LAYOUT_NO_LIMITS：窗口覆盖到状态栏。
 */
public final class OverlayWindow {

    private final WindowManager wm;
    private final Context ctx;
    private final BeatState state;
    private FrameLayout rootLayout;
    private LightOverlayView view;
    private KeyboardWatcher watcher;
    private LightRenderer renderer;

    public OverlayWindow(Context context, BeatState state) {
        this.ctx = context;
        this.wm = context.getSystemService(WindowManager.class);
        this.state = state;
    }

    /** 显示覆盖层并绑定渲染器，返回覆盖 View */
    public LightOverlayView show(LightRenderer renderer) {
        if (view != null) return view;
        this.renderer = renderer;

        // 根布局 FrameLayout，用来叠视频层
        rootLayout = new FrameLayout(ctx);

        // 顶层：光效绘制 View（透明 Canvas）
        view = new LightOverlayView(ctx, renderer);
        rootLayout.addView(view);

        // 底层：礼物特效层（TextureView，随窗口合成，不产生独立图层），attach 内部 addView(index=0)
        renderer.attachHighlightView(rootLayout);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE   // 触摸穿透，不挡操作
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL // 双保险：不独占触摸
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN // 覆盖到状态栏
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, // 铺满含导航栏
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.setTitle("RGBBeat");

        // 权限可能在中途被系统/用户回收，直接 addView 会抛 BadTokenException 把进程带走
        if (!CrashGuard.canOverlay(ctx)) {
            DiagLog.w("RGBBeat/Overlay", "overlay permission lost, skip attach");
            rootLayout = null;
            view = null;
            return null;
        }
        try {
            wm.addView(rootLayout, lp);
        } catch (Throwable t) {
            DiagLog.w("RGBBeat/Overlay", "addView failed: " + t);
            try { wm.removeView(rootLayout); } catch (Throwable ignored) { }
            rootLayout = null;
            view = null;
            return null;
        }

        try {
            watcher = new KeyboardWatcher(ctx, view, state);
            watcher.attach();
        } catch (Throwable t) {
            DiagLog.w("RGBBeat/Overlay", "ime watcher attach failed: " + t);
            watcher = null;
        }
        return view;
    }

    /** 只切换可见性，不销毁窗口（避免切应用瞬间重建导致的闪断）；首显自动创建 */
    public void setVisible(boolean visible) {
        if (visible) {
            if (rootLayout == null) return;
        }
        if (rootLayout != null) {
            rootLayout.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    public void hide() {
        if (watcher != null) {
            watcher.detach();
            watcher = null;
        }
        // 释放礼物视频的 MediaPlayer / Surface，避免服务停止后资源泄漏
        if (renderer != null) {
            try { renderer.releaseHighlight(); } catch (Throwable ignored) { }
            renderer = null;
        }
        if (rootLayout != null) {
            try { wm.removeView(rootLayout); } catch (Exception ignored) { }
            rootLayout = null;
            view = null;
        }
    }
}
