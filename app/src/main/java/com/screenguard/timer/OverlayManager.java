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
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;

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

            // 显示今日已用时长
            TextView used = overlayView.findViewById(R.id.overlay_used);
            if (used != null) {
                used.setText("今日已用 " + usedMinutes(app) + " 分钟");
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

    /** doLock=true：点击尝试锁屏；false：仅收起（如屏幕被系统关掉） */
    public static void dismiss(Context context, boolean doLock) {
        if (doLock) {
            boolean locked = requestLock(context);
            stopVibrate();
            // 无论如何都收干净（结束本轮提醒），避免下次亮屏再弹提醒而非重新选时长
            removeViewSafely();
            ScreenGuardService.state = ScreenGuardService.STATE_IDLE;
            ScreenGuardService.notifyMonitoring(context);
            if (!locked) {
                Toast.makeText(context, "自动锁屏失败：请按一下电源键锁屏", Toast.LENGTH_LONG).show();
            }
            return;
        }
        removeViewSafely();
        stopVibrate();
    }

    private static void removeViewSafely() {
        try {
            if (overlayView != null && windowManager != null) {
                windowManager.removeView(overlayView);
            }
        } catch (Exception ignored) {
        }
        overlayView = null;
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

    private static boolean requestLock(Context context) {
        DevicePolicyManager dpm =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName cn = new ComponentName(context, LockAdminReceiver.class);
        if (dpm != null && dpm.isAdminActive(cn)) {
            try {
                dpm.lockNow();
                return true;
            } catch (Exception ignored) {
                // iQOO/OriginOS 上 lockNow 常触发异常，改走无障碍锁屏
            }
        }
        // 无障碍锁屏兜底（国产机更可靠）
        if (AccessLockService.isEnabled() && AccessLockService.lock()) {
            return true;
        }
        return false;
    }

    private static int usedMinutes(Context c) {
        try {
            StatDb sdb = new StatDb(c);
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long ds = cal.getTimeInMillis();
            StatDb.DayStat st = sdb.dayStats(ds, ds + 24 * 3600_000L);
            int m = Math.round((st.studyMs + st.funMs) / 60000f);
            sdb.close();
            return m;
        } catch (Exception e) {
            return 0;
        }
    }
}
