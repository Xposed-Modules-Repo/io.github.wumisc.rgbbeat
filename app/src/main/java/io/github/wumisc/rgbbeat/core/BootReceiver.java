package io.github.wumisc.rgbbeat.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机自启：开机完成后，若用户在设置中开启了"开机自启"则拉起光效服务。
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (new Prefs(context).autoStart()) {
            try {
                BeatService.start(context);
            } catch (Exception ignored) { }
        }
    }
}
