package com.screenguard.timer;

import android.accessibilityservice.AccessibilityService;
import android.provider.Settings;
import android.content.Context;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * 无障碍锁屏兜底：iQOO/OriginOS 上设备管理器的 lockNow() 常触发异常，
 * 改用系统无障碍接口 GLOBAL_ACTION_LOCK_SCREEN 锁屏，更可靠。
 * 需在系统设置开启本应用的「无障碍 / 辅助功能」。
 */
public class AccessLockService extends AccessibilityService {

    private static boolean enabled = false;
    private static AccessLockService instance;

    public static boolean isEnabled() {
        return enabled;
    }

    /** 执行锁屏，成功返回 true */
    public static boolean lock() {
        if (instance == null) return false;
        try {
            return instance.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        } catch (Exception e) {
            return false;
        }
    }

    /** 打开系统"无障碍"设置页，让用户开启本应用 */
    public static void openSettings(Context context) {
        try {
            context.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        enabled = true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不处理任何事件，仅用于锁屏
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        instance = null;
        enabled = false;
        super.onDestroy();
    }
}
