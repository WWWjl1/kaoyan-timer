package com.screenguard.timer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机自启：重启手机后自动恢复计时监测。
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (ScreenGuardService.isEnabled(context)) {
                // 先埋下自检闹钟，再拉起监测服务
                ScreenGuardService.scheduleSelfCheck(context);
                ScreenGuardService.startMonitor(context);
            }
        }
    }
}
