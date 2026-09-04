package com.screenguard.timer;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * 锁机提示横幅：顶部悬浮显示剩余解锁时间，不挡触摸（不影响打电话/用桌面），
 * 倒计时到 0 自动解除锁机并移除横幅。
 */
public final class LockOverlay {

    private static View view;
    private static WindowManager wm;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static Runnable tick;

    private LockOverlay() {
    }

    public static boolean isShowing() {
        return view != null;
    }

    public static void show(Context context) {
        if (view != null) return;
        if (!Settings.canDrawOverlays(context)) return;
        try {
            Context app = context.getApplicationContext();
            wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            view = LayoutInflater.from(app).inflate(R.layout.overlay_lock, null);

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP;
            wm.addView(view, lp);
            startTicker(app);
        } catch (Exception e) {
            view = null;
        }
    }

    private static void startTicker(final Context app) {
        cancelTicker();
        tick = new Runnable() {
            @Override
            public void run() {
                long remain = LockGuard.remainingMs(app);
                if (remain <= 0) {
                    dismiss(app);
                    return;
                }
                if (view != null) {
                    TextView tv = view.findViewById(R.id.lock_text);
                    if (tv != null) {
                        tv.setText("🔒 娱乐超时，锁机中… 距解锁还剩 " + (remain / 1000) + " 秒（仅可打电话/学习App）");
                    }
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(tick);
    }

    private static void cancelTicker() {
        if (tick != null) {
            handler.removeCallbacks(tick);
            tick = null;
        }
    }

    public static void dismiss(Context context) {
        cancelTicker();
        try {
            if (view != null && wm != null) {
                wm.removeView(view);
            }
        } catch (Exception ignored) {
        }
        view = null;
        LockGuard.clearLock(context);
    }
}
