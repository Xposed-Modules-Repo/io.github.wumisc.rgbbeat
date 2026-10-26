package io.github.wumisc.rgbbeat.ui;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import io.github.wumisc.rgbbeat.R;

/** 关于界面：模块简介、作者署名、捐赠二维码、版本与许可证 */
public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        TextView tvVersion = findViewById(R.id.tvVersion);
        tvVersion.setText("版本 v" + versionName());
    }

    public void onBack(android.view.View v) {
        finish();
    }

    private String versionName() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "1.8";
        }
    }
}
