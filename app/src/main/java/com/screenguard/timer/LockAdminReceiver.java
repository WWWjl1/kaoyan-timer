package com.screenguard.timer;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * 设备管理员：只用于到点后自动锁屏（lockNow），不索取任何受限策略。
 */
public class LockAdminReceiver extends DeviceAdminReceiver {

    @Override
    public void onEnabled(Context context, Intent intent) {
        Toast.makeText(context, "锁屏管理已激活", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Toast.makeText(context, "锁屏管理已停用，到点后将无法自动锁屏", Toast.LENGTH_LONG).show();
    }
}
