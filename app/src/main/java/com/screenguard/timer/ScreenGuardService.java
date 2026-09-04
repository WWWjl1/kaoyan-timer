package com.screenguard.timer;

import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;

/**
 * 核心服务：监听亮屏/息屏事件 + 倒计时 + 常驻通知。
 *
 * 状态流转：
 *   IDLE    —— 监测中。每次亮屏解锁后弹出选时长界面；
 *   COUNTING—— 倒计时中。中途息屏 -> 结束本轮(未完成)回 IDLE；
 *   ALERT   —— 到点，悬浮窗图片+震动已弹出。点图片 -> 锁屏 回 IDLE。
 */
public class ScreenGuardService extends Service {

    public static final String ACTION_START_COUNTDOWN = "com.screenguard.timer.action.START_COUNTDOWN";
    public static final String EXTRA_MINUTES = "minutes";
    public static final String EXTRA_PURPOSE = "purpose";

    public static final String PREF_NAME = "sg_pref";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_LAST_MINUTES = "last_minutes";
    public static final String KEY_LAST_STUDY = "last_study";
    public static final String KEY_LAST_FUN = "last_fun";

    public static final int STATE_IDLE = 0;
    public static final int STATE_COUNTING = 1;
    public static final int STATE_ALERT = 2;

    // 供 MainActivity 读取当前状态（同进程）
    public static volatile int state = STATE_IDLE;
    public static volatile long countdownEndMs = 0L;
    public static volatile boolean pickerVisible = false;

    private static final String CHANNEL_ID = "monitor";
    private static final int NOTIF_ID = 1;

    /** 自检闹钟间隔：即使服务被系统杀掉，最多这么久后自动复活（抗国产系统杀后台） */
    private static final long SELF_CHECK_INTERVAL_MS = 20 * 60_000L;

    private static volatile ScreenGuardService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingLaunch;   // 延迟弹出选时长界面
    private Runnable tick;            // 倒计时每秒滴答

    private StatDb db;
    private NotificationManager nm;
    private long roundId = -1;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                screenOn();
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                screenOff();
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                userPresent();
            }
        }
    };

    // ---------------------------------------------------------------- 生命周期

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        db = new StatDb(this);
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createChannel();
        startForeground(NOTIF_ID, buildNotification(getString(R.string.notif_monitoring)));
        registerScreenReceiver();
        scheduleSelfCheck(this);
        // 服务被系统重启后，若屏幕正亮着且未锁定，补一次弹窗（避免错过解锁事件）
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                tryLaunchIfScreenOn();
            }
        }, 1800);
    }

    /** 若屏幕当前亮着且未锁定、状态空闲，主动弹出选时长（弥补被杀后错过事件） */
    private void tryLaunchIfScreenOn() {
        if (state != STATE_IDLE || PickerOverlay.isShowing() || !isEnabled(this) || !canDrawOverlays()) {
            return;
        }
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        boolean interactive = pm != null && pm.isInteractive();
        boolean locked = km != null && km.isKeyguardLocked();
        if (interactive && !locked) {
            launchPicker();
        }
    }

    /** 注册亮屏/息屏/解锁监听（Android 8+ 必须在运行时动态注册才能收到） */
    private void registerScreenReceiver() {
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            registerReceiver(screenReceiver, filter);
        } catch (Exception ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_START_COUNTDOWN.equals(intent.getAction())) {
            int minutes = intent.getIntExtra(EXTRA_MINUTES, 25);
            String purpose = intent.getStringExtra(EXTRA_PURPOSE);
            if (purpose == null) purpose = "fun";
            startCountdown(minutes, purpose);
        }
        // 被杀后系统会尝试重启服务
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        instance = null;
        cancelPendingLaunch();
        cancelTick();
        try {
            unregisterReceiver(screenReceiver);
        } catch (Exception ignored) {
        }
        OverlayManager.dismiss(getApplicationContext(), false);
        if (db != null) db.close();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------------------------------------------------------------- 事件处理

    private void screenOn() {
        if (state == STATE_IDLE && !PickerOverlay.isShowing() && isEnabled(this) && canDrawOverlays()) {
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            boolean locked = km != null && km.isKeyguardLocked();
            if (!locked) {
                // 无锁屏/已解锁的亮屏，稍等片刻确认不是误触亮屏
                scheduleLaunch(1800);
            }
            // 有锁屏：等用户解锁后由 USER_PRESENT 触发
        }
    }

    private void userPresent() {
        if (state == STATE_ALERT) {
            // 之前到点提醒没来得及点就被息屏了，解锁后把提醒图片重新弹出来
            OverlayManager.show(this);
        } else if (state == STATE_IDLE && !PickerOverlay.isShowing() && isEnabled(this) && canDrawOverlays()) {
            // 稍延后弹出，避开解锁转场瞬间
            scheduleLaunch(700);
        }
    }

    private void screenOff() {
        cancelPendingLaunch();
        if (state == STATE_COUNTING) {
            // 中途息屏 = 本轮结束
            db.closeRound(roundId, System.currentTimeMillis());
            roundId = -1;
            state = STATE_IDLE;
            cancelTick();
            updateNotification(getString(R.string.notif_monitoring));
        } else if (state == STATE_ALERT) {
            // 屏幕关了，悬浮窗自动收起；状态保留 ALERT，解锁后重新弹出
            OverlayManager.dismiss(getApplicationContext(), false);
        } else if (state == STATE_IDLE && PickerOverlay.isShowing()) {
            // 息屏时把选时长悬浮窗收起来，下次亮屏再弹
            PickerOverlay.dismiss(this);
        }
    }

    private void scheduleLaunch(long delayMs) {
        cancelPendingLaunch();
        pendingLaunch = new Runnable() {
            @Override
            public void run() {
                if (state != STATE_IDLE || PickerOverlay.isShowing() || !isEnabled(ScreenGuardService.this) || !canDrawOverlays()) {
                    return;
                }
                KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
                boolean locked = km != null && km.isKeyguardLocked();
                if (!locked) {
                    launchPicker();
                }
                // 若此刻又锁上了，等 USER_PRESENT 再触发
            }
        };
        handler.postDelayed(pendingLaunch, delayMs);
    }

    private void cancelPendingLaunch() {
        if (pendingLaunch != null) {
            handler.removeCallbacks(pendingLaunch);
            pendingLaunch = null;
        }
    }

    private void launchPicker() {
        // 用悬浮窗展示选时长界面：不受「后台启动 Activity」限制，iQOO 上也不会一闪而过
        PickerOverlay.show(this);
    }

    // ---------------------------------------------------------------- 倒计时

    private void startCountdown(int minutes, String purpose) {
        cancelTick();
        cancelPendingLaunch();

        long now = System.currentTimeMillis();
        state = STATE_COUNTING;
        countdownEndMs = now + minutes * 60_000L;
        roundId = db.openRound(now, minutes, purpose);

        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .edit().putInt(KEY_LAST_MINUTES, minutes).apply();

        updateNotification("已开始：" + minutes + " 分钟，到点会提醒你");

        tick = new Runnable() {
            @Override
            public void run() {
                if (state != STATE_COUNTING) return;
                long remain = countdownEndMs - System.currentTimeMillis();
                if (remain <= 0) {
                    timeUp();
                } else {
                    long sec = remain / 1000;
                    if (sec % 60 == 0) {
                        updateNotification("剩余 " + (sec / 60) + " 分钟");
                    }
                    handler.postDelayed(this, 500);
                }
            }
        };
        handler.post(tick);
    }

    private void cancelTick() {
        if (tick != null) {
            handler.removeCallbacks(tick);
            tick = null;
        }
    }

    private void timeUp() {
        state = STATE_ALERT;
        cancelTick();
        db.closeRound(roundId, System.currentTimeMillis());
        roundId = -1;
        updateNotification(getString(R.string.notif_timeup));
        OverlayManager.show(this);
    }

    // ---------------------------------------------------------------- 通知

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, getString(R.string.notif_channel_monitor),
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification(String text) {
        Intent pi = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 0, pi,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateNotification(String text) {
        if (nm != null) {
            try {
                nm.notify(NOTIF_ID, buildNotification(text));
            } catch (Exception ignored) {
            }
        }
    }

    // ---------------------------------------------------------------- 静态工具

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, true);
    }

    public static void startMonitor(Context context) {
        try {
            context.startForegroundService(new Intent(context, ScreenGuardService.class));
        } catch (Exception ignored) {
        }
    }

    public static void stopMonitor(Context context) {
        context.stopService(new Intent(context, ScreenGuardService.class));
    }

    /**
     * 安排一次自检闹钟（系统级调度，App 进程被杀也会到点触发）。
     * 到点后 SelfCheckReceiver 会：重新安排下一次 + 把服务拉起来。
     * 这样就算 iQOO/小米等系统把后台杀了，最多 20 分钟也能自动复活。
     */
    public static void scheduleSelfCheck(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(ALARM_SERVICE);
            if (am == null) return;
            long triggerAt = SystemClock.elapsedRealtime() + SELF_CHECK_INTERVAL_MS;
            PendingIntent pi = PendingIntent.getBroadcast(context, 0,
                    new Intent(context, SelfCheckReceiver.class),
                    PendingIntent.FLAG_IMMUTABLE);
            // setWindow：非精确闹钟，无需额外权限，doze 下也会被调度
            am.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, 60_000L, pi);
        } catch (Exception ignored) {
        }
    }

    private boolean canDrawOverlays() {
        return Settings.canDrawOverlays(this);
    }

    /** 悬浮窗点击锁屏后，把通知恢复成「监测中」 */
    public static void notifyMonitoring(Context context) {
        ScreenGuardService svc = instance;
        if (svc != null && svc.nm != null) {
            svc.updateNotification(context.getString(R.string.notif_monitoring));
        }
    }
}
