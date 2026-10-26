package io.github.wumisc.rgbbeat.core;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.view.View;

/**
 * 全屏透明覆盖 View：替代 SurfaceView 方案的窗口载体。
 * - 普通 View + FLAG_LAYOUT_IN_SCREEN 才能把光效画到状态栏区域（SurfaceView 画不到系统栏）。
 * - FLAG_NOT_TOUCHABLE 保证触摸完全穿透，绝不挡手机操作。
 * - onDraw 里由 LightRenderer 绘制屏幕边缘 / 键盘四边光效（全包围）。
 */
public final class LightOverlayView extends View {

    private static final String TAG = "RGBBeat/Light";

    private final LightRenderer renderer;

    public LightOverlayView(Context context, LightRenderer renderer) {
        super(context);
        this.renderer = renderer;
        // 强制软件绘制：保证每帧 drawColor(CLEAR) 能真正清空为透明，且支持渐变 Shader
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setBackgroundColor(android.graphics.Color.TRANSPARENT);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        try {
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);   // 清空为全透明
            renderer.draw(canvas, getWidth(), getHeight());
        } catch (Throwable t) {
            // 绘制异常不外泄（任何线程的未捕获异常都会杀死进程）
            DiagLog.w(TAG, "overlay draw error: " + t);
        }
    }
}
