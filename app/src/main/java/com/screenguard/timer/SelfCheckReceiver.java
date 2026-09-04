package com.screenguard.timer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 自检闹钟接收器：每 2 分钟检测一次，若监测服务不在运行就重新拉起。
 * 用户主动「退出 App」后会暂停检测，直到下次手动打开 App 才恢复。
 */
public class SelfCheckReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // 总开关关闭，或用户主动退出 -> 暂停自动检测
        if (!ScreenGuardService.isEnabled(context) || ScreenGuardService.isSuspended(context)) {
            return;
        }
        // 先续订下一次自检（保证链条不断）
        ScreenGuardService.scheduleSelfCheck(context);
        // 若服务不在运行则重启
        if (!ScreenGuardService.isRunning()) {
            ScreenGuardService.startMonitor(context);
        }
    }
}
