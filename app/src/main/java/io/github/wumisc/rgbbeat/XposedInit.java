package io.github.wumisc.rgbbeat;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Xposed 入口：hook 系统 UI 进程，在 SystemUI 启动后自动拉起模块的光效服务，
 * 实现"无需手动打开 App 即开机自启"。模块本体作为普通 App 也能手动开关。
 *
 * Android 17 / HyperOS4 适配：
 *  - SystemUI 的 Application 类名可能随 ROM 变化，用候选类名探测，命中才 hook，
 *    避免写死类名导致开机自启静默失效；
 *  - 跨进程启动前台服务在系统刚启动 / 后台限制下可能被拒，失败后延迟重试一次，
 *    仍失败则交由 BootReceiver（BOOT_COMPLETED 广播）兜底，用户也可手动启动。
 */
public class XposedInit implements IXposedHookLoadPackage {

    private static final String TAG = "RGBBeatXposed";

    // SystemUI 主 Application 候选类名（含 HyperOS 可能的变体）
    private static final String[] SYSTEMUI_APP_CLASSES = {
            "com.android.systemui.SystemUIApplication",
            "com.android.systemui.SystemUIApp",
            "com.android.systemui.MiuiApplication",
            "com.android.systemui.SystemUIRoot",
    };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        // ===== 系统推荐 hook 对象（最小正确面）=====
        // RGBBeat 只 hook SystemUI 用于"开机自启拉起光效服务"，绝不注入其它进程：
        //   · 音频感知走系统 API Visualizer（全局频谱），无需 hook；
        //   · 光效是应用自己的悬浮窗覆盖层，无需 hook；
        //   · 震动走系统 Vibrator 服务，无需 hook；
        //   · 服务保活由 START_STICKY + onTaskRemoved 自愈 + 系统后台白名单解决，与 hook 无关。
        // 多余 hook 只会污染其它进程、增加崩溃/兼容风险、且无功能收益——刻意保持最小面。
        if (!"com.android.systemui".equals(lpparam.packageName)) return;

        String clz = null;
        for (String c : SYSTEMUI_APP_CLASSES) {
            if (XposedHelpers.findClassIfExists(c, lpparam.classLoader) != null) {
                clz = c;
                break;
            }
        }
        if (clz == null) {
            Log.w(TAG, "未找到 SystemUI Application 类，自启链路失效（可手动启动或依赖 BootReceiver）");
            return;
        }
        Log.i(TAG, "hook SystemUI Application: " + clz);

        // 系统推荐 hook 对象（SystemUI Application 生命周期）：
        //  attachBaseContext 是最早的应用初始化时机，onCreate 是常规时机——双时机兜底，
        //  任一时机命中都能拉起服务，大幅提高开机自启成功率（HyperOS 上 onCreate 偶发不触发）。
        try {
            XposedHelpers.findAndHookMethod(
                    clz, lpparam.classLoader, "attachBaseContext", Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                launchService((Context) param.thisObject);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable ignored) {
            // attach 不存在时不影响 onCreate 链路
        }
        try {
            XposedHelpers.findAndHookMethod(
                    clz, lpparam.classLoader, "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                launchService((Context) param.thisObject);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable ignored) {
        }
    }

    private static volatile long lastLaunch = 0; // 节流：attach 与 onCreate 双时机不重复拉起

    private static void launchService(final Context ctx) {
        long now = System.currentTimeMillis();
        if (now - lastLaunch < 5000) {
            return; // 已在 5s 内拉起过，跳过（避免 attach+onCreate 各拉一次）
        }
        lastLaunch = now;
        try {
            Intent i = new Intent().setClassName(
                    "io.github.wumisc.rgbbeat", "io.github.wumisc.rgbbeat.core.BeatService");
            ctx.startForegroundService(i);
            Log.i(TAG, "从 SystemUI 拉起 BeatService 成功");
        } catch (Throwable t) {
            // 系统刚启动 / 后台前台服务限制时可能被拒，延迟 5s 重试一次
            Log.w(TAG, "SystemUI 拉起失败，5s 后重试: " + t);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    Intent i = new Intent().setClassName(
                            "io.github.wumisc.rgbbeat", "io.github.wumisc.rgbbeat.core.BeatService");
                    ctx.startForegroundService(i);
                    Log.i(TAG, "重试拉起 BeatService 成功");
                } catch (Throwable t2) {
                    Log.w(TAG, "重试仍失败，交由 BootReceiver/手动启动: " + t2);
                }
            }, 5000);
        }
    }
}
