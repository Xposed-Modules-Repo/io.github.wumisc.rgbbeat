package io.github.wumisc.rgbbeat.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import io.github.wumisc.rgbbeat.R;
import io.github.wumisc.rgbbeat.core.BeatService;
import io.github.wumisc.rgbbeat.core.Prefs;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs.migrateLegacy(this); // 把旧 "<包名>_preferences" 里的用户设置迁过来
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        private final SharedPreferences.OnSharedPreferenceChangeListener listener =
                (sp, key) -> {
                    BeatService svc = BeatService.instance();
                    if (svc != null) svc.applyPrefs();
                };

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            // 关键修复：PreferenceManager 默认写 "<包名>_preferences"，而 Prefs / 首页
            // 读写的是 "rgbbeat"。不统一的话，设置页改的任何值（含礼物特效开关）服务都读不到。
            getPreferenceManager().setSharedPreferencesName(Prefs.NAME);
            getPreferenceManager().setSharedPreferencesMode(Context.MODE_PRIVATE);
            setPreferencesFromResource(R.xml.preferences, rootKey);

            // "选择触发应用" → 打开应用选择界面
            Preference pick = findPreference("pick_apps");
            if (pick != null) {
                pick.setOnPreferenceClickListener(pref -> {
                    startActivity(new Intent(getActivity(), AppPickActivity.class)
                            .putExtra("mode", "trigger"));
                    return true;
                });
            }

            // "音乐识别黑名单" → 打开黑名单应用选择界面
            Preference blacklist = findPreference("pick_music_blacklist");
            if (blacklist != null) {
                blacklist.setOnPreferenceClickListener(pref -> {
                    startActivity(new Intent(getActivity(), AppPickActivity.class)
                            .putExtra("mode", "blacklist"));
                    return true;
                });
            }

            // "立即预览特效" → 手动播一次，用于确认视频真的能显示
            Preference preview = findPreference("preview_highlight");
            if (preview != null) {
                preview.setOnPreferenceClickListener(pref -> {
                    BeatService svc = BeatService.instance();
                    if (svc == null) {
                        Toast.makeText(getContext(), R.string.toast_preview_need_service,
                                Toast.LENGTH_SHORT).show();
                    } else {
                        svc.previewHighlight(-1);
                    }
                    return true;
                });
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(listener);
        }

        @Override
        public void onPause() {
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(listener);
            super.onPause();
        }
    }
}
