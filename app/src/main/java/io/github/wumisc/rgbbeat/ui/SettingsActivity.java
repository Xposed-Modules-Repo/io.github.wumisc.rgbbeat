package io.github.wumisc.rgbbeat.ui;

import android.content.SharedPreferences;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import io.github.wumisc.rgbbeat.R;
import io.github.wumisc.rgbbeat.core.BeatService;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
