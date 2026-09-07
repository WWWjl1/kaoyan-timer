package com.screenguard.timer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

/**
 * 强制锁机界面：全屏显示用户设置的提醒图片，打开非白名单软件时弹出；
 * 输入正确口令（记录页设置的"退出口令"）才能退出。无震动。
 */
public final class ForceOverlay {

    private static View view;
    private static WindowManager wm;

    private ForceOverlay() {
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
            view = LayoutInflater.from(app).inflate(R.layout.overlay_force, null);

            ImageView img = view.findViewById(R.id.force_image);
            Bitmap bmp = ReminderImageStore.load(app);
            if (bmp != null) img.setImageBitmap(bmp);
            else img.setImageResource(R.drawable.reminder_placeholder);

            Button btn = view.findViewById(R.id.force_btn);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    tryUnlock(app);
                }
            });

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.CENTER;
            wm.addView(view, lp);
        } catch (Exception e) {
            view = null;
        }
    }

    private static void tryUnlock(Context app) {
        EditText et = view.findViewById(R.id.force_input);
        String pass = LockGuard.getForcePass(app);
        String input = et.getText() != null ? et.getText().toString().trim() : "";
        if (input.length() > 0 && input.equals(pass)) {
            LockGuard.setForce(app, false);
            dismiss(app);
        } else {
            Toast.makeText(app, "口令不对，无法退出", Toast.LENGTH_SHORT).show();
        }
    }

    public static void dismiss(Context context) {
        try {
            if (view != null && wm != null) wm.removeView(view);
        } catch (Exception ignored) {
        }
        view = null;
    }
}
