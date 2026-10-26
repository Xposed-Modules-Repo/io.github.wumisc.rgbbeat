package io.github.wumisc.rgbbeat.core;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 软键盘监听：感知当前前台应用的 IME 高度，驱动"键盘环绕"光效。
 * - Android 11+（API 30）：反射调用 WindowManager.CurrentImeRequestedInsetsCallback，
 *   这是 overlay 窗口正确获取全局键盘 insets 的官方方式（getWindowVisibleDisplayFrame
 *   在 overlay 上收不到键盘，旧实现完全检测不到）。编译环境精简 stub 缺该符号，用反射绕过。
 * - Android 8~10（<30）或反射失败：回退到布局差值估算。
 */
public final class KeyboardWatcher implements ViewTreeObserver.OnGlobalLayoutListener {

    private static final int KEYBOARD_THRESHOLD = 160; // px，小于此视为导航栏等非键盘占用
    private static final String CB_IFACE = "android.view.WindowManager$CurrentImeRequestedInsetsCallback";

    private final BeatState state;
    private final WindowManager wm;
    private final View root;
    private Object imeCb;

    public KeyboardWatcher(Context ctx, View root, BeatState state) {
        this.state = state;
        this.root = root;
        this.wm = ctx.getSystemService(WindowManager.class);
    }

    public void attach() {
        if (Build.VERSION.SDK_INT >= 30 && bindImeCallbackViaReflection()) {
            return;
        }
        // 回退：布局差值估算
        root.getViewTreeObserver().addOnGlobalLayoutListener(this);
    }

    /** 反射绑定 IME insets 回调（API 30+，编译环境缺符号故用反射） */
    private boolean bindImeCallbackViaReflection() {
        try {
            Class<?> wmCls = android.view.WindowManager.class;
            Class<?> cbIface = Class.forName(CB_IFACE);
            Object cb = Proxy.newProxyInstance(cbIface.getClassLoader(),
                    new Class<?>[]{cbIface},
                    (proxy, method, args) -> {
                        if ("onInsetsChanged".equals(method.getName()) && args != null && args.length > 0
                                && args[0] instanceof WindowInsets) {
                            WindowInsets insets = (WindowInsets) args[0];
                            int ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
                            boolean visible = ime > KEYBOARD_THRESHOLD;
                            state.keyboardVisible = visible;
                            state.keyboardHeightPx = visible ? ime : 0;
                        }
                        return null; // onImeRequested(boolean) 无需处理
                    });
            Method setter = wmCls.getMethod("setCurrentImeRequestedInsetsCallback", cbIface, Handler.class);
            setter.invoke(wm, cb, root.getHandler());
            imeCb = cb;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public void detach() {
        if (imeCb != null && Build.VERSION.SDK_INT >= 30) {
            try {
                Class<?> wmCls = android.view.WindowManager.class;
                Class<?> cbIface = Class.forName(CB_IFACE);
                Method setter = wmCls.getMethod("setCurrentImeRequestedInsetsCallback", cbIface, Handler.class);
                setter.invoke(wm, null, null);
            } catch (Throwable ignored) { }
            imeCb = null;
        }
        root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
    }

    /** <API 30 回退：布局差值估算键盘高度 */
    @Override
    public void onGlobalLayout() {
        Rect visible = new Rect();
        root.getWindowVisibleDisplayFrame(visible);
        int screenH = root.getHeight();
        int bottomInset = Math.max(0, screenH - visible.bottom);
        boolean visibleKb = bottomInset > KEYBOARD_THRESHOLD;
        state.keyboardVisible = visibleKb;
        state.keyboardHeightPx = visibleKb ? bottomInset : 0;
    }
}
