package com.screenguard.timer;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;

/**
 * 选时长的悬浮窗（替代 Activity）。
 * 两步：先选目的（学习/娱乐），再选时长——
 *   学习：0–60 分钟，5 分钟梯度；娱乐：0–30 分钟，2 分钟梯度。
 * 强制：只有点「开始计时」能关闭，点其它地方无效；「0 分钟」= 跳过本轮。
 */
public final class PickerOverlay {

    private static final String STUDY = "study";
    private static final String FUN = "fun";
    private static View view;
    private static WindowManager wm;

    private PickerOverlay() {
    }

    public static boolean isShowing() {
        return view != null;
    }

    public static boolean canShow(Context context) {
        return Settings.canDrawOverlays(context);
    }

    // 学习 5..60 步5（不含0）；娱乐 2..30 步2（不含0）
    private static int[] buildMinutes(boolean study) {
        if (study) {
            int n = 12; // 5,10,...,60
            int[] a = new int[n];
            for (int i = 0; i < n; i++) a[i] = (i + 1) * 5;
            return a;
        } else {
            int n = 15; // 2,4,...,30
            int[] a = new int[n];
            for (int i = 0; i < n; i++) a[i] = (i + 1) * 2;
            return a;
        }
    }

    public static void show(Context context) {
        if (view != null) return;
        if (!canShow(context)) return;
        try {
            Context app = context.getApplicationContext();
            wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            view = LayoutInflater.from(app).inflate(R.layout.overlay_picker, null);

            final Context ctx = context;
            final TextView title = view.findViewById(R.id.section_title);
            final View purposeSection = view.findViewById(R.id.purpose_section);
            final View timerSection = view.findViewById(R.id.timer_section);
            final NumberPicker picker = view.findViewById(R.id.time_picker);
            final Button start = view.findViewById(R.id.btn_start);
            final TextView back = view.findViewById(R.id.btn_back);

            Button studyBtn = view.findViewById(R.id.purpose_study);
            Button funBtn = view.findViewById(R.id.purpose_fun);
            studyBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    applyMode(ctx, title, purposeSection, timerSection, picker, true);
                }
            });
            funBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    applyMode(ctx, title, purposeSection, timerSection, picker, false);
                }
            });

            start.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    int minutes = minutesCurrent[picker.getValue()];
                    String purpose = currentStudy ? STUDY : FUN;
                    ctx.getSharedPreferences(ScreenGuardService.PREF_NAME, Context.MODE_PRIVATE)
                            .edit().putInt(currentStudy ? ScreenGuardService.KEY_LAST_STUDY
                                    : ScreenGuardService.KEY_LAST_FUN, minutes).apply();
                    Intent svc = new Intent(ctx, ScreenGuardService.class)
                            .setAction(ScreenGuardService.ACTION_START_COUNTDOWN)
                            .putExtra(ScreenGuardService.EXTRA_MINUTES, minutes)
                            .putExtra(ScreenGuardService.EXTRA_PURPOSE, purpose);
                    try {
                        ctx.startService(svc);
                    } catch (Exception ignored) {
                    }
                    dismiss(ctx);
                }
            });

            back.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    title.setText("这次想做什么？");
                    purposeSection.setVisibility(View.VISIBLE);
                    timerSection.setVisibility(View.GONE);
                }
            });

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.CENTER;
            wm.addView(view, lp);

            ScreenGuardService.pickerVisible = true;
        } catch (Exception e) {
            view = null;
        }
    }

    // 记录当前模式，供按钮读取
    private static boolean currentStudy = true;
    private static int[] minutesCurrent = buildMinutes(true);

    private static void applyMode(Context ctx, TextView title, View purposeSection,
                                  View timerSection, NumberPicker picker, boolean study) {
        currentStudy = study;
        minutesCurrent = buildMinutes(study);
        String[] labels = new String[minutesCurrent.length];
        for (int i = 0; i < minutesCurrent.length; i++) {
            labels[i] = minutesCurrent[i] + " 分钟";
        }
        picker.setMinValue(0);
        picker.setMaxValue(minutesCurrent.length - 1);
        picker.setDisplayedValues(labels);
        picker.setWrapSelectorWheel(false);

        // 默认定位到上次选的时长（按目的分开记）
        int def = study ? 30 : 20;
        int last = ctx.getSharedPreferences(ScreenGuardService.PREF_NAME, Context.MODE_PRIVATE)
                .getInt(study ? ScreenGuardService.KEY_LAST_STUDY : ScreenGuardService.KEY_LAST_FUN, def);
        int idx = 0;
        for (int i = 0; i < minutesCurrent.length; i++) {
            if (minutesCurrent[i] <= last) idx = i;
        }
        picker.setValue(idx);

        title.setText(study ? "学习了多长时间？" : "娱乐了多长时间？");
        purposeSection.setVisibility(View.GONE);
        timerSection.setVisibility(View.VISIBLE);
    }

    public static void dismiss(Context context) {
        try {
            if (view != null && wm != null) {
                wm.removeView(view);
            }
        } catch (Exception ignored) {
        }
        view = null;
        ScreenGuardService.pickerVisible = false;
    }
}
