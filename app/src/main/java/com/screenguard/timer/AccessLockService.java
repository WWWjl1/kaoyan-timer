package com.screenguard.timer;

import android.accessibilityservice.AccessibilityService;
import android.provider.Settings;
import android.content.Context;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * 无障碍服务：两个用途
 * 1) 锁屏兜底：设备管理器 lockNow() 在 iQOO 上常失效，用 GLOBAL_ACTION_LOCK_SCREEN 兜底；
 * 2) 娱乐超限锁机：监听前台 App，锁机状态下非白名单应用（电话/豆包/不背单词/扇贝考研 之外）被拉回并显示锁机横幅。
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

    /** 打开系统"无障碍"设置页 */
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
        // 未处于锁机状态：什么都不做
        if (!LockGuard.isLocked(this)) return;
        CharSequence pkg = event.getPackageName();
        if (isWhitelisted(pkg)) return;
        // 非白名单应用出现在前台：显示锁机横幅 + 拉回桌面
        LockOverlay.show(this);
        try {
            performGlobalAction(GLOBAL_ACTION_HOME);
        } catch (Exception ignored) {
        }
    }

    /** 锁机期间允许的包名：系统/电话/桌面/设置 + 三个学习 App */
    private boolean isWhitelisted(CharSequence pkg) {
        if (pkg == null) return false;
        String p = pkg.toString();
        if (p.equals("com.screenguard.timer")) return true;               // 本 App
        if (p.startsWith("com.android")) return true;                       // 系统（电话/桌面/设置/系统UI 多为 com.android.*）
        if (p.contains("dialer") || p.contains("contacts")
                || p.contains("launcher") || p.contains("settings")
                || p.contains("phone") || p.contains("keyboard")) return true;
        // 各厂商系统 App（避免误拦 iQOO/vivo 的拨号等）
        if (p.startsWith("com.vivo") || p.startsWith("com.bbk")
                || p.startsWith("com.oplus") || p.startsWith("com.oneplus")
                || p.startsWith("com.iqoo") || p.startsWith("com.coloros")
                || p.startsWith("com.huawei") || p.startsWith("com.xiaomi")
                || p.startsWith("com.samsung") || p.startsWith("com.oppo")) return true;
        // 用户自定义白名单（可在 App「白名单」页增删）
        java.util.Set<String> set = getSharedPreferences(ScreenGuardService.PREF_NAME, MODE_PRIVATE)
                .getStringSet(ScreenGuardService.KEY_WHITELIST, null);
        if (set != null && set.contains(p)) return true;
        return false;
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
