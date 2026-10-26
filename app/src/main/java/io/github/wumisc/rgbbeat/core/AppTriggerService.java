package io.github.wumisc.rgbbeat.core;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * 指定应用自动开启光效：监听前台窗口切换，当用户在前台打开已选中的应用
 * （打车软件、地图导航等）时，强制开启光效（晕车缓解模式标配）；离开该应用后
 * 回落到常规逻辑（音乐模式常开 / 晕车模式随车辆运动自动开关）。
 *
 * 需要用户在系统设置中开启本服务的无障碍开关（一次配置后常驻，系统级保活）。
 * 增强：
 *  · 支持连接后主动探测当前前台应用（不依赖窗口切换事件，部分 ROM 不投递该事件）；
 *  · 内置打车/导航只依赖"晕车检测"开关，手动勾选才需要"指定应用自动开启"总开关；
 *  · 每个新前台包名都打日志，方便 adb 对账。
 */
public final class AppTriggerService extends AccessibilityService {

    private static final String TAG = "RGBBeat/Trigger";

    private Prefs prefs;
    private String lastPkg = "";

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        prefs = new Prefs(this);
        DiagLog.init(this); // 确保无障碍服务日志可落盘（即使 BeatService 尚未启动）
        DiagLog.d(TAG, "accessibility connected");
        // 无障碍是系统级常驻组件：连接后自动拉起光效服务，服务被后台清理后自动恢复（配合开机自启=常驻）
        try {
            if (BeatService.instance() == null) BeatService.start(this);
        } catch (Throwable t) {
            DiagLog.w(TAG, "auto-start service err: " + t);
        }
        // 连接后延迟探测当前前台应用：很多场景是先打开打车/导航、服务后启动，
        // 此时不会有新的窗口切换事件，需主动触发一次
        new Handler(Looper.getMainLooper()).postDelayed(this::probeForeground, 300);
    }

    /** 主动读取当前有焦点的窗口应用并尝试触发（无需等待窗口切换事件） */
    private void probeForeground() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            String pkg = root != null && root.getPackageName() != null
                    ? root.getPackageName().toString() : "";
            DiagLog.d(TAG, "probe foreground pkg=" + pkg);
            if (!pkg.isEmpty()) handlePackage(pkg);
        } catch (Throwable t) {
            DiagLog.w(TAG, "probe err: " + t);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            if (prefs == null) prefs = new Prefs(this);
            // 内置打车/导航应用触发只依赖"晕车检测"开关（无需手动勾选）；
            // 手动勾选应用才需要"指定应用自动开启"总开关。两者都没开才不监听。
            if (!prefs.carsickEnabled() && !prefs.triggerEnabled()) return;
            // 只处理窗口切换/内容变化等前台包名变化信号
            int type = event.getEventType();
            if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;
            String pkg = event.getPackageName() == null ? "" : event.getPackageName().toString();
            if (pkg.isEmpty() || pkg.equals(lastPkg)) return;
            DiagLog.d(TAG, "event type=" + type + " pkg=" + pkg);
            handlePackage(pkg);
        } catch (Throwable t) {
            DiagLog.w(TAG, "accessibility event err: " + t);
        }
    }

    /** 处理一个前台包名：命中触发应用则开光效，否则回落 */
    private void handlePackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) return;
        if (pkg.equals(lastPkg)) return;
        lastPkg = pkg;
        // 同步当前前台应用到服务，供音乐识别黑名单判定（无论是否命中触发）
        BeatService svc0 = BeatService.instance();
        if (svc0 != null) svc0.setForegroundPkg(pkg);
        // 命中触发：内置打车/导航类应用（自动识别，无需手动勾选）或 用户手动选择的应用
        boolean builtin = Prefs.isCarsickApp(pkg) && prefs.carsickEnabled();
        boolean selected = prefs.triggerEnabled() && prefs.triggerApps().contains(pkg);
        boolean hit = builtin || selected;
        DiagLog.d(TAG, "foreground pkg=" + pkg + " builtin=" + builtin + " selected=" + selected);
        if (!hit) {
            // 离开触发应用：回落"指定应用强制开"状态，光效回到音乐/运动常规逻辑
            BeatService svc = BeatService.instance();
            if (svc != null) svc.setTriggerActive(false, pkg);
            return;
        }
        BeatService svc = BeatService.instance();
        if (svc == null) {
            // 命中打车/导航类应用但光效服务未运行：自动拉起服务，实现"打开打车/导航即自动开启"
            DiagLog.d(TAG, "auto-start service for " + pkg);
            BeatService.start(this);
            // 服务异步启动，instance() 此刻可能仍为 null，稍后重试触发
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                BeatService s2 = BeatService.instance();
                if (s2 != null) s2.setTriggerActive(true, pkg);
            }, 600);
            return;
        }
        svc.setTriggerActive(true, pkg);
    }

    @Override
    public void onInterrupt() {
        // 无障碍服务被中断：释放触发，回落常规逻辑
        try {
            BeatService svc = BeatService.instance();
            if (svc != null) svc.setTriggerActive(false, "");
        } catch (Throwable ignored) { }
    }
}
