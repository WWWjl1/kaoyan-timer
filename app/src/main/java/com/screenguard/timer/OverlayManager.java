package com.screenguard.timer;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

/**
 * 到点提醒悬浮窗：全屏半透明 + 图片(用户图/占位图) + 震动。
 * 点击任意处 -> 收起 -> 请求设备管理员锁屏。
 */
public final class OverlayManager {

    private static View overlayView;
    private static WindowManager windowManager;
    private static Vibrator vibrator;

    private OverlayManager() {
    }

    public static boolean isShowing() {
        return overlayView != null;
    }

    public static boolean canShow(Context context) {
        return Settings.canDrawOverlays(context);
    }

    public static void show(Context context) {
        if (overlayView != null) return;            // 已在显示
        if (!canShow(context)) return;              // 没给悬浮窗权限
        try {
            Context app = context.getApplicationContext();
            windowManager = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            overlayView = LayoutInflater.from(app).inflate(R.layout.overlay_alert, null);

            ImageView image = overlayView.findViewById(R.id.overlay_image);
            Bitmap userBmp = ReminderImageStore.load(app);
            if (userBmp != null) {
                image.setImageBitmap(userBmp);
            } else {
                image.setImageResource(R.drawable.reminder_placeholder);
            }

            // 点屏幕任意处：收起 + 锁屏
            overlayView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss(app, true);
                }
            });

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT);
            windowManager.addView(overlayView, lp);

            vibrate(app);
        } catch (Exception e) {
            overlayView = null;
        }
    }

    /** doLock=true：点击收起并锁屏；false：仅收起（如屏幕被系统关掉） */
    public static void dismiss(Context context, boolean doLock) {
        try {
            if (overlayView != null && windowManager != null) {
                windowManager.removeView(overlayView);
            }
        } catch (Exception ignored) {
        }
        overlayView = null;
        stopVibrate();

        if (doLock) {
            ScreenGuardService.state = ScreenGuardService.STATE_IDLE;
            ScreenGuardService.notifyMonitoring(context);
            requestLock(context);
        }
    }

    private static void vibrate(Context context) {
        try {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                // 长震动循环，直到用户点击图片（dismiss 时取消）
                long[] pattern = {0, 700, 350, 700, 350, 1400};
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            }
        } catch (Exception ignored) {
        }
    }

    private static void stopVibrate() {
        try {
            if (vibrator != null) vibrator.cancel();
        } catch (Exception ignored) {
        }
        vibrator = null;
    }

    private static void requestLock(Context context) {
        DevicePolicyManager dpm =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName cn = new ComponentName(context, LockAdminReceiver.class);
        if (dpm != null && dpm.isAdminActive(cn)) {
            try {
                dpm.lockNow();
                return;
            } catch (Exception ignored) {
            }
        }
        Toast.makeText(context, "未激活「锁屏管理」：请回 App 点「去激活锁屏管理」完成激活；若你刚覆盖安装过，请先卸载旧版再用本版重新激活", Toast.LENGTH_LONG).show();
    }
}
