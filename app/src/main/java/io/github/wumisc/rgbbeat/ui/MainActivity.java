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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.materialswitch.MaterialSwitch;
import io.github.wumisc.rgbbeat.R;
import io.github.wumisc.rgbbeat.core.BeatService;
import io.github.wumisc.rgbbeat.core.DiagLog;
import io.github.wumisc.rgbbeat.core.Prefs;

public class MainActivity extends AppCompatActivity {

    private Prefs prefs;
    private TextView tvStatus, tvInfo, tvAboutVersion;
    private MaterialSwitch swCarsick, swMotionTrigger, swToast, swMusicAuto, swVibration, swAnim, swParticle;
    private TextView chipParticleHigh, chipParticleEco;
    private RadioGroup rgData;
    private View pageHome, pageFx, pageAbout;
    private View tabHome, tabFx, tabAbout;
    private View swService;
    private boolean updating = false;
    private int currentTab = 0;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable serviceMonitor = new Runnable() {
        @Override
        public void run() {
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

        // 页面
        pageHome = findViewById(R.id.page_home);
        pageFx = findViewById(R.id.page_fx);
        pageAbout = findViewById(R.id.page_about);
        tabHome = findViewById(R.id.tab_home);
        tabFx = findViewById(R.id.tab_fx);
        tabAbout = findViewById(R.id.tab_about);

        // 状态
        tvStatus = findViewById(R.id.tvStatus);
        tvInfo = findViewById(R.id.tvInfo);
        tvAboutVersion = findViewById(R.id.tvAboutVersion);
        tvAboutVersion.setText("RGBBeat v3.0");

        // 开关
        swService = findViewById(R.id.swService);
        swCarsick = findViewById(R.id.swCarsick);
        swMotionTrigger = findViewById(R.id.swMotionTrigger);
        swToast = findViewById(R.id.swToast);
        swMusicAuto = findViewById(R.id.swMusicAuto);
        swVibration = findViewById(R.id.swVibration);
        swAnim = findViewById(R.id.swAnim);
        swParticle = findViewById(R.id.swParticle);
        chipParticleHigh = findViewById(R.id.chipParticleHigh);
        chipParticleEco = findViewById(R.id.chipParticleEco);
        rgData = findViewById(R.id.rgData);

        // Tab 切换
        tabHome.setOnClickListener(v -> switchTab(0));
        tabFx.setOnClickListener(v -> switchTab(1));
        tabAbout.setOnClickListener(v -> switchTab(2));

        // 服务开关（圆形按钮）
        swService.setOnClickListener(v -> toggleService());

        // 震动开关
        swVibration.setChecked(prefs.vibrationEnabled());
        swVibration.setOnCheckedChangeListener((b, c) -> {
            prefs.sp().edit().putBoolean("vibration_enabled", c).apply();
            BeatService svc = BeatService.instance();
            if (svc != null) svc.applyPrefs();
        });

        // 动效开关
        swAnim.setChecked(prefs.animEnabled());
        swAnim.setOnCheckedChangeListener((b, c) -> {
            prefs.sp().edit().putBoolean("anim_enabled", c).apply();
            BeatService svc = BeatService.instance();
            if (svc != null) svc.applyPrefs();
        });

        // 3D 粒子氛围引擎
        swParticle.setChecked(prefs.particleEnabled());
        swParticle.setOnCheckedChangeListener((b, c) -> {
            prefs.setParticleEnabled(c);
            BeatService svc = BeatService.instance();
            if (svc != null) svc.applyPrefs();
        });
        refreshParticleChips();
        chipParticleHigh.setOnClickListener(v -> {
            prefs.setParticleQuality("high");
            BeatService svc = BeatService.instance();
            if (svc != null) svc.applyPrefs();
            refreshParticleChips();
        });
        chipParticleEco.setOnClickListener(v -> {
            prefs.setParticleQuality("eco");
            BeatService svc = BeatService.instance();
            if (svc != null) svc.applyPrefs();
            refreshParticleChips();
        });

        // 晕车
        swCarsick.setChecked(prefs.carsickEnabled());
        swMotionTrigger.setChecked(prefs.motionTriggerEnabled());
        swToast.setChecked(prefs.carsickToast());
        swCarsick.setOnCheckedChangeListener((b, c) -> applyBool("carsick_enabled", c));
        swMotionTrigger.setOnCheckedChangeListener((b, c) -> applyBool("motion_trigger_enabled", c));
        swToast.setOnCheckedChangeListener((b, c) -> applyBool("carsick_toast", c));

        // 音乐自动
        swMusicAuto.setChecked(prefs.musicAutoEnabled());
        swMusicAuto.setOnCheckedChangeListener((b, c) -> {
            prefs.setMusicAutoEnabled(c);
            BeatService svc = BeatService.instance();
            if (svc != null) svc.applyPrefs();
        });

        // 颜色
        buildColorPicker();

        // 数据源
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

        // 关于页按钮
        findViewById(R.id.btnRecommend).setOnClickListener(v ->
                startActivity(new Intent(this, AppPickActivity.class)));
        findViewById(R.id.btnGrantOverlay).setOnClickListener(v -> grantOverlay());
        findViewById(R.id.btnGrantNotif).setOnClickListener(v -> requestNotifPermission());
        findViewById(R.id.btnDiagLog).setOnClickListener(v -> showDiagLog());
    }

    private void switchTab(int idx) {
        currentTab = idx;
        pageHome.setVisibility(idx == 0 ? View.VISIBLE : View.GONE);
        pageFx.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
        pageAbout.setVisibility(idx == 2 ? View.VISIBLE : View.GONE);
        updateTabStyle(tabHome, idx == 0);
        updateTabStyle(tabFx, idx == 1);
        updateTabStyle(tabAbout, idx == 2);
    }

    private void updateTabStyle(View tab, boolean active) {
        TextView tv = (TextView) ((LinearLayout) tab).getChildAt(1);
        ImageView iv = (ImageView) ((LinearLayout) tab).getChildAt(0);
        int color = active ? getColor(R.color.primary) : getColor(R.color.text_hint);
        tv.setTextColor(color);
        iv.setColorFilter(color);
    }

    private void toggleService() {
        boolean running = BeatService.instance() != null;
        if (running) {
            BeatService.stop(this);
            Toast.makeText(this, R.string.toast_stopped, Toast.LENGTH_SHORT).show();
        } else {
            if (!canDrawOverlays()) {
                Toast.makeText(this, R.string.toast_permission_denied, Toast.LENGTH_SHORT).show();
                grantOverlay();
                return;
            }
            ensureRuntimePermissions();
            BeatService.start(this);
            Toast.makeText(this, R.string.toast_started, Toast.LENGTH_SHORT).show();
        }
        swService.postDelayed(this::refresh, 800);
    }

    private void applyBool(String key, boolean val) {
        prefs.sp().edit().putBoolean(key, val).apply();
        BeatService svc = BeatService.instance();
        if (svc != null) svc.applyPrefs();
    }

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
                buildColorPicker();
            });
            group.addView(dot);
        }
    }

    private void refreshParticleChips() {
        boolean high = !"eco".equals(prefs.particleQuality());
        chipParticleHigh.setBackground(chipBg(high));
        chipParticleEco.setBackground(chipBg(!high));
        chipParticleHigh.setTextColor(getColor(high ? R.color.primary : R.color.text_hint));
        chipParticleEco.setTextColor(getColor(!high ? R.color.primary : R.color.text_hint));
    }

    private GradientDrawable chipBg(boolean selected) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(getColor(selected ? R.color.primary : android.R.color.transparent));
        g.setCornerRadius(getResources().getDisplayMetrics().density * 10);
        g.setStroke((int) (getResources().getDisplayMetrics().density * 1),
                getColor(selected ? R.color.primary : R.color.text_hint));
        return g;
    }

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
        tvStatus.setText(running ? R.string.service_status_running : R.string.service_status_stopped);
        tvStatus.setTextColor(getColor(running ? R.color.status_running : R.color.status_stopped));
        // 服务按钮颜色：运行=绿色，停止=蓝色
        ((com.google.android.material.card.MaterialCardView) swService)
                .setCardBackgroundColor(getColor(running ? R.color.status_running : R.color.primary));
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
            needed = new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS};
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
