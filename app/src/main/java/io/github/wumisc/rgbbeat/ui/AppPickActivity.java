package io.github.wumisc.rgbbeat.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import io.github.wumisc.rgbbeat.R;
import io.github.wumisc.rgbbeat.core.Prefs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 应用选择界面，支持两种模式（通过 intent extra "mode" 区分）：
 *  - trigger   触发应用：选中应用在前台打开时强制开启光效（防晕车标配）
 *  - blacklist 音乐识别黑名单：选中应用在前台时抑制音乐光效（视频/直播等带背景音乐的应用）
 */
public final class AppPickActivity extends AppCompatActivity {

    private static final String EXTRA_MODE = "mode";
    private static final String MODE_TRIGGER = "trigger";
    private static final String MODE_BLACKLIST = "blacklist";

    private static class AppItem {
        String pkg;
        CharSequence label;
        Drawable icon;
    }

    private Prefs prefs;
    private String mode = MODE_TRIGGER;
    private final List<AppItem> apps = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private AppAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) mode = MODE_TRIGGER;
        prefs = new Prefs(this);
        selected.clear();
        if (MODE_BLACKLIST.equals(mode)) selected.addAll(prefs.musicBlacklist());
        else selected.addAll(prefs.triggerApps());
        setContentView(R.layout.activity_app_pick);

        loadApps();

        TextView tvTitle = findViewById(R.id.tv_title);
        if (tvTitle != null) {
            tvTitle.setText(MODE_BLACKLIST.equals(mode)
                    ? "音乐识别黑名单" : "选择触发应用");
        }

        ListView lv = findViewById(R.id.app_list);
        adapter = new AppAdapter();
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((parent, view, pos, id) -> {
            AppItem a = apps.get(pos);
            if (MODE_TRIGGER.equals(mode) && Prefs.isCarsickApp(a.pkg)) {
                Toast.makeText(this, "系统推荐应用，打开即自动识别，无需勾选", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selected.contains(a.pkg)) selected.remove(a.pkg);
            else selected.add(a.pkg);
            adapter.notifyDataSetChanged();
        });

        Button save = findViewById(R.id.btn_save);
        save.setOnClickListener(v -> {
            if (MODE_BLACKLIST.equals(mode)) {
                prefs.saveMusicBlacklist(selected);
                Toast.makeText(this, "已保存 " + selected.size() + " 个黑名单应用", Toast.LENGTH_SHORT).show();
            } else {
                // 勾选保存 = 开启触发总开关（AppTriggerService 需该开关为 true 才生效）
                prefs.sp().edit().putBoolean("app_trigger_enabled", true).apply();
                prefs.saveTriggerApps(selected);
                Toast.makeText(this, "已保存 " + selected.size() + " 个触发应用", Toast.LENGTH_SHORT).show();
            }
            finish();
        });
    }

    private void loadApps() {
        apps.clear();
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
        for (ResolveInfo ri : list) {
            try {
                String pkg = ri.activityInfo.packageName;
                AppItem a = new AppItem();
                a.pkg = pkg;
                a.label = ri.loadLabel(pm);
                a.icon = ri.loadIcon(pm);
                apps.add(a);
            } catch (Throwable ignored) { }
        }
        // 同包名多入口去重，并按名称排序
        Set<String> seen = new HashSet<>();
        List<AppItem> uniq = new ArrayList<>();
        for (AppItem a : apps) if (seen.add(a.pkg)) uniq.add(a);
        apps.clear();
        apps.addAll(uniq);
        Collections.sort(apps, (x, y) ->
                String.valueOf(x.label).compareToIgnoreCase(String.valueOf(y.label)));
    }

    private final class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int i) { return apps.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View cv, ViewGroup parent) {
            if (cv == null) {
                cv = LayoutInflater.from(AppPickActivity.this)
                        .inflate(R.layout.item_app_pick, parent, false);
            }
            AppItem a = apps.get(i);
            ((ImageView) cv.findViewById(R.id.app_icon)).setImageDrawable(a.icon);
            ((TextView) cv.findViewById(R.id.app_label)).setText(a.label);
            ((TextView) cv.findViewById(R.id.app_pkg)).setText(a.pkg);
            boolean rec = MODE_TRIGGER.equals(mode) && Prefs.isCarsickApp(a.pkg);
            ((TextView) cv.findViewById(R.id.app_recommend))
                    .setVisibility(rec ? View.VISIBLE : View.GONE);
            CheckBox cb = cv.findViewById(R.id.app_check);
            cb.setClickable(false);
            cb.setEnabled(!rec);                       // 触发模式下系统推荐应用锁定为启用
            cb.setChecked(rec || selected.contains(a.pkg));
            return cv;
        }
    }
}
