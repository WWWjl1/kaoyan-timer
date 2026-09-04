package com.screenguard.timer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 自检闹钟接收器：到点自动续订下一次闹钟，并把可能被系统杀掉的服务重新拉起。
 * 闹钟由系统调度（AlarmManager），不依赖 App 进程存活，因此能抵抗后台清理。
 */
public class SelfCheckReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // 先续订下一次自检（保证链条不断）
        ScreenGuardService.scheduleSelfCheck(context);
        // 再确保监测服务在运行（幂等：已在跑则无副作用）
        if (ScreenGuardService.isEnabled(context)) {
            ScreenGuardService.startMonitor(context);
        }
    }
}
