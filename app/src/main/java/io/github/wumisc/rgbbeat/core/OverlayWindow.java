package io.github.wumisc.rgbbeat.core;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.WindowManager;

/**
 * 悬浮窗管理：创建全屏透明 View 覆盖层（LightOverlayView），供 LightRenderer 绘制
 * 屏幕边缘 / 键盘区域 RGB 光效。
 * - FLAG_NOT_TOUCHABLE：触摸完全穿透，不拦截任何操作。
 * - FLAG_LAYOUT_IN_SCREEN + LAYOUT_NO_LIMITS：窗口覆盖到状态栏，光效可全包围整屏。
 */
public final class OverlayWindow {

    private final WindowManager wm;
    private final Context ctx;
    private final BeatState state;
    private LightOverlayView view;
    private KeyboardWatcher watcher;

    public OverlayWindow(Context context, BeatState state) {
        this.ctx = context;
        this.wm = context.getSystemService(WindowManager.class);
        this.state = state;
    }

    /** 显示覆盖层并绑定渲染器，返回覆盖 View */
    public LightOverlayView show(LightRenderer renderer) {
        if (view != null) return view;

        view = new LightOverlayView(ctx, renderer);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE   // 触摸穿透，不挡操作
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN // 覆盖到状态栏
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, // 铺满含导航栏
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.setTitle("RGBBeat");

        wm.addView(view, lp);

        watcher = new KeyboardWatcher(ctx, view, state);
        watcher.attach();
        return view;
    }

    /** 只切换可见性，不销毁窗口（避免切应用瞬间重建导致的闪断）；首显自动创建 */
    public void setVisible(boolean visible) {
        if (visible) {
            if (view == null) return; // 未创建过窗口，由 show() 创建
        }
        if (view != null) {
            view.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    public void hide() {
        if (watcher != null) {
            watcher.detach();
            watcher = null;
        }
        if (view != null) {
            try { wm.removeView(view); } catch (Exception ignored) { }
            view = null;
        }
    }
}
