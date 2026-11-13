package io.github.wumisc.rgbbeat.data;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;
import android.provider.Settings;

/**
 * 进程级崩溃兜底。
 *
 * 背景：v3.0.0 在部分 ROM 上"开着开着"就被系统杀掉——悬浮窗被回收、GL 线程被驱动层
 * 打断、后台被清理，最终表现为闪退。这里做三件事：
 *  1. 把未捕获异常写入 DiagLog（App 内可查看），下次反馈有栈可查；
 *  2. 10 分钟内连续崩 3 次即进入安全模式：关掉最容易触发原生崩溃的高潮视频与 3D 粒子，
 *     保底让边缘光效和节拍震动继续工作，而不是彻底不可用；
 *  3. 非安全模式下用 AlarmManager 拉起服务（进程已死时 Handler 已不可用）。
 *
 * 注意：必须在任何渲染/服务初始化之前安装，且安装本身不能抛异常。
 */
public final class CrashGuard {

    private static final String TAG = "RGBBeat/Crash";
    private static final String K_LAST = "crash_last_ms";
    private static final String K_COUNT = "crash_count";
    private static final String K_SAFE = "safe_mode";
    private static final long WINDOW_MS = 10 * 60 * 1000L;
    private static final int SAFE_THRESHOLD = 3;

    private static volatile boolean installed = false;

    private CrashGuard() { }

    public static void install(final Context context) {
        if (installed) return;
        installed = true;
        final Context app = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                onCrash(app, thread, error);
            } catch (Throwable ignored) {
                // 兜底逻辑自身绝不能再抛异常
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    /** 是否处于安全模式（UI 可据此提示用户） */
    public static boolean isSafeMode(Context c) {
        return new Prefs(c).sp().getBoolean(K_SAFE, false);
    }

    /** 用户手动恢复：清空崩溃计数与安全模式标记 */
    public static void reset(Context c) {
        new Prefs(c).sp().edit()
                .putBoolean(K_SAFE, false)
                .putInt(K_COUNT, 0)
                .putLong(K_LAST, 0L)
                .apply();
    }

    private static void onCrash(Context ctx, Thread thread, Throwable error) {
        DiagLog.init(ctx);
        DiagLog.w(TAG, "UNCAUGHT thread=" + thread.getName() + " " + Log.getStackTraceString(error));

        SharedPreferences sp = new Prefs(ctx).sp();
        long now = SystemClock.elapsedRealtime();
        long last = sp.getLong(K_LAST, 0L);
        int count = sp.getInt(K_COUNT, 0);
        count = (now - last <= WINDOW_MS) ? count + 1 : 1;
        SharedPreferences.Editor e = sp.edit().putInt(K_COUNT, count).putLong(K_LAST, now);

        if (count >= SAFE_THRESHOLD) {
            // 安全模式：保留光效/震动，关掉最容易把进程带走的两个重负载特性
            e.putBoolean(K_SAFE, true)
                    .putBoolean("highlight_enabled", false)
                    .putBoolean("particle_mode", false);
            DiagLog.w(TAG, "enter SAFE MODE after " + count + " crashes in 10min");
            e.apply();
            return; // 安全模式不再自动拉起，避免无限重启循环
        }
        e.apply();

        // 非安全模式：进程即将退出，用闹钟在 2s 后把服务拉回来
        scheduleRestart(ctx);
    }

    private static void scheduleRestart(Context ctx) {
        try {
            Intent i = new Intent(ctx, io.github.wumisc.rgbbeat.service.BeatService.class);
            PendingIntent pi = PendingIntent.getForegroundService(
                    ctx, 0x524742, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager am = ctx.getSystemService(AlarmManager.class);
            if (am != null) {
                long at = SystemClock.elapsedRealtime() + 2000L;
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi);
                DiagLog.w(TAG, "restart scheduled in 2s");
            }
        } catch (Throwable t) {
            DiagLog.w(TAG, "schedule restart failed: " + t);
        }
    }

    /** 悬浮窗权限是否仍然有效（被系统回收后需要提示用户重新授予） */
    public static boolean canOverlay(Context c) {
        try {
            return Settings.canDrawOverlays(c);
        } catch (Throwable t) {
            return false;
        }
    }
}
