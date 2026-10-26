package io.github.wumisc.rgbbeat.ui;

import android.Manifest;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.materialswitch.MaterialSwitch;
import io.github.wumisc.rgbbeat.R;
import io.github.wumisc.rgbbeat.core.BeatService;
import io.github.wumisc.rgbbeat.core.DiagLog;
import io.github.wumisc.rgbbeat.core.Prefs;

public class MainActivity extends AppCompatActivity {

    private Prefs prefs;
    private TextView tvStatus;
    private TextView tvInfo;
    private MaterialSwitch swService;
    private MaterialSwitch swCarsick;
    private MaterialSwitch swMotionTrigger;
    private MaterialSwitch swToast;
    private MaterialSwitch swMusicAuto;
    private RadioGroup rgData;
    private boolean updating = false;
    /** 服务状态同步监视：服务可能被无障碍/自动启动异步拉起，需定时刷新 UI 显示"运行中" */
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable serviceMonitor = new Runnable() {
        @Override
        public void run() {
            // 仅当服务已运行但界面仍显示未运行（或状态不同步）时刷新
            if (BeatService.instance() != null) refresh();
            uiHandler.postDelayed(this, 1500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        DiagLog.init(this);
        prefs = new Prefs(this);

        tvStatus = findViewById(R.id.tvStatus);
        tvInfo = findViewById(R.id.tvInfo);
        swService = findViewById(R.id.swService);
        swCarsick = findViewById(R.id.swCarsick);
        swMotionTrigger = findViewById(R.id.swMotionTrigger);
        swToast = findViewById(R.id.swToast);
        swMusicAuto = findViewById(R.id.swMusicAuto);
        rgData = findViewById(R.id.rgData);

        // 服务总开关（替代原大按钮）
        swService.setOnCheckedChangeListener((b, checked) -> {
            if (updating) return;
            if (checked) {
                if (!canDrawOverlays()) {
                    Toast.makeText(this, R.string.toast_permission_denied, Toast.LENGTH_SHORT).show();
                    grantOverlay();
                    refresh();
                    return;
                }
                ensureRuntimePermissions();
                BeatService.start(this);
                Toast.makeText(this, R.string.toast_started, Toast.LENGTH_SHORT).show();
            } else {
                BeatService.stop(this);
                Toast.makeText(this, R.string.toast_stopped, Toast.LENGTH_SHORT).show();
            }
            // 服务启停是异步的：延后同步真实运行状态，避免开关瞬时弹回/不同步
            swService.postDelayed(this::refresh, 800);
        });

        // 晕车场景配置
        swCarsick.setChecked(prefs.carsickEnabled());
        swMotionTrigger.setChecked(prefs.motionTriggerEnabled());
        swToast.setChecked(prefs.carsickToast());
        swCarsick.setOnCheckedChangeListener((b, checked) -> applyBool("carsick_enabled", checked));
        swMotionTrigger.setOnCheckedChangeListener((b, checked) -> applyBool("motion_trigger_enabled", checked));
        swToast.setOnCheckedChangeListener((b, checked) -> applyBool("carsick_toast", checked));

        // 音乐自动触发：有音乐播放开光效，音乐停止关光效
        swMusicAuto.setChecked(prefs.musicAutoEnabled());
        swMusicAuto.setOnCheckedChangeListener((b, checked) -> {
            prefs.setMusicAutoEnabled(checked);
            BeatService svc = BeatService.instance();
            if (svc != null) svc.applyPrefs();
        });

        // 光效颜色 DIY：随节奏自动彩虹 或 自定义主色
        buildColorPicker();

        // 数据源模式（融合 / 音乐 / 晕车）
        String dm = prefs.dataMode();
        if ("motion".equals(dm)) rgData.check(R.id.rbMotion);
        else if ("music".equals(dm)) rgData.check(R.id.rbMusic);
        else rgData.check(R.id.rbFusion);
        rgData.setOnCheckedChangeListener((g, id) -> {
            if (updating) return;
            String v;
            if (id == R.id.rbMotion) v = "motion";
            else if (id == R.id.rbMusic) v = "music";
            else v = "fusion";
            prefs.sp().edit().putString("data_mode", v).apply();
            BeatService svc = BeatService.instance();
            if (svc != null) svc.applyPrefs();
        });

        findViewById(R.id.btnAbout).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));
        findViewById(R.id.btnRecommend).setOnClickListener(v ->
                startActivity(new Intent(this, AppPickActivity.class)));
        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnGrantOverlay).setOnClickListener(v -> grantOverlay());
        findViewById(R.id.btnGrantNotif).setOnClickListener(v -> requestNotifPermission());
        findViewById(R.id.btnDiagLog).setOnClickListener(v -> showDiagLog());
    }

    private void applyBool(String key, boolean val) {
        prefs.sp().edit().putBoolean(key, val).apply();
        BeatService svc = BeatService.instance();
        if (svc != null) svc.applyPrefs();
    }

    // ---- 光效颜色 DIY：随节奏自动彩虹 或 用户指定主色 ----
    private static final float[] COLOR_HUES = {-1f, 280f, 190f, 230f, 320f, 30f, 120f, 0f};
    private static final String[] COLOR_LABELS = {"随节奏", "紫", "青", "蓝", "粉", "橙", "绿", "红"};

    private void buildColorPicker() {
        LinearLayout group = findViewById(R.id.colorGroup);
        group.removeAllViews();
        float cur = prefs.lightColorHue();
        for (int i = 0; i < COLOR_HUES.length; i++) {
            final float h = COLOR_HUES[i];
            View dot = new View(this);
            int size = (int) (getResources().getDisplayMetrics().density * 30);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins((int) (getResources().getDisplayMetrics().density * 3), 0,
                    (int) (getResources().getDisplayMetrics().density * 3), 0);
            dot.setLayoutParams(lp);
            dot.setContentDescription(COLOR_LABELS[i]);
            dot.setBackground(colorDotDrawable(h, cur == h));
            dot.setOnClickListener(v -> {
                prefs.setLightColorHue(h);
                BeatService svc = BeatService.instance();
                if (svc != null) svc.applyPrefs();
                buildColorPicker(); // 刷新选中态
            });
            group.addView(dot);
        }
    }

    /** 色块背景：自动=彩虹扫色圆，自定义=该色相圆；选中加白描边 */
    private GradientDrawable colorDotDrawable(float hue, boolean selected) {
        GradientDrawable g;
        if (hue < 0) {
            g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.HSVToColor(new float[]{0f, 0.9f, 1f}),
                            Color.HSVToColor(new float[]{280f, 0.9f, 1f}),
                            Color.HSVToColor(new float[]{200f, 0.9f, 1f})});
        } else {
            g = new GradientDrawable();
            g.setColor(Color.HSVToColor(new float[]{hue, 0.85f, 1f}));
        }
        g.setCornerRadius(getResources().getDisplayMetrics().density * 15);
        g.setStroke((int) (getResources().getDisplayMetrics().density * (selected ? 3 : 1)),
                selected ? 0xFFFFFFFF : 0x33FFFFFF);
        return g;
    }

    /** 查看调试日志（无需 adb）：读取日志文件，弹窗显示并支持分享 */
    private void showDiagLog() {
        String log = DiagLog.readLast(300);
        TextView tv = new TextView(this);
        tv.setText(log);
        tv.setTextSize(11f);
        tv.setTextIsSelectable(true);
        tv.setTextColor(getColor(R.color.text_primary));
        tv.setPadding(28, 16, 28, 16);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        sv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (getResources().getDisplayMetrics().density * 380)));
        new AlertDialog.Builder(this)
                .setTitle("调试日志\n" + DiagLog.path())
                .setView(sv)
                .setPositiveButton("分享", (d, w) -> shareLog(log))
                .setNegativeButton("关闭", null)
                .show();
    }

    private void shareLog(String log) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, log);
        startActivity(Intent.createChooser(i, "分享调试日志"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        ensureRuntimePermissions();
        uiHandler.removeCallbacks(serviceMonitor);
        uiHandler.post(serviceMonitor);
        // 自动功能开启时（音乐自动触发 / 开机自启），打开 App 即自动拉起光效服务，
        // 实现"放音乐就自动开光效"而无需手动点服务开关
        try {
            if (BeatService.instance() == null && (prefs.musicAutoEnabled() || prefs.autoStart())) {
                BeatService.start(this);
            }
        } catch (Throwable ignored) { }
    }

    @Override
    protected void onPause() {
        super.onPause();
        uiHandler.removeCallbacks(serviceMonitor);
    }

    private void refresh() {
        updating = true;
        boolean running = BeatService.instance() != null;
        swService.setChecked(running);
        tvStatus.setText(running ? R.string.service_status_running : R.string.service_status_stopped);
        tvStatus.setTextColor(getColor(running ? R.color.status_running : R.color.status_stopped));
        if (running) {
            String[] mv = getResources().getStringArray(R.array.data_mode_values);
            String[] ml = getResources().getStringArray(R.array.data_mode_labels);
            tvInfo.setText("模式：" + labelOf(mv, ml, prefs.dataMode()) + " · 晕车检测：" + (prefs.carsickEnabled() ? "开" : "关"));
        } else {
            tvInfo.setText(R.string.info_idle);
        }
        updating = false;
    }

    private static String labelOf(String[] values, String[] labels, String v) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(v)) return labels[i];
        }
        return v;
    }

    private boolean canDrawOverlays() {
        return Settings.canDrawOverlays(this);
    }

    private void grantOverlay() {
        if (canDrawOverlays()) {
            Toast.makeText(this, "悬浮窗权限已授予", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    private void requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 102);
        } else {
            Toast.makeText(this, "通知权限已授予", Toast.LENGTH_SHORT).show();
        }
    }

    private void ensureRuntimePermissions() {
        String[] needed;
        if (Build.VERSION.SDK_INT >= 33) {
            needed = new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS};
        } else {
            needed = new String[]{Manifest.permission.RECORD_AUDIO};
        }
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String p : needed) {
            if (ContextCompat.checkSelfPermission(this, p)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) missing.add(p);
        }
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), 101);
        }
    }
}
