package com.screenguard.timer;

import android.content.Context;

import java.util.Calendar;

/**
 * 娱乐超限锁机守卫：
 * 当天「娱乐累计时长」达到阈值后，进入锁机状态（持续 LOCK_DURATION_MS）。
 * 锁机期间由 AccessLockService 拦截非白名单应用（电话 + 豆包/不背单词/扇贝考研）。
 */
public final class LockGuard {

    public static final String KEY_LOCK_UNTIL = "lock_until";
    public static final long LOCK_DURATION_MS = 60_000L;   // 锁机时长：1 分钟（先做短一点方便测试）
    public static final int FUN_THRESHOLD_MIN = 60;          // 娱乐累计 60 分钟触发
    public static final long DAY_MS = 24 * 3600_000L;

    private static final String PREF = ScreenGuardService.PREF_NAME;

    private LockGuard() {
    }

    public static boolean isLocked(Context c) {
        return prefs(c).getLong(KEY_LOCK_UNTIL, 0) > System.currentTimeMillis();
    }

    public static long remainingMs(Context c) {
        long until = prefs(c).getLong(KEY_LOCK_UNTIL, 0);
        return Math.max(0, until - System.currentTimeMillis());
    }

    public static void setLock(Context c) {
        prefs(c).edit().putLong(KEY_LOCK_UNTIL, System.currentTimeMillis() + LOCK_DURATION_MS).apply();
    }

    public static void clearLock(Context c) {
        prefs(c).edit().putLong(KEY_LOCK_UNTIL, 0).apply();
    }

    /** 娱乐累计超阈值则进入锁机（在每次计时结束后调用） */
    public static void maybeEnterLock(Context c) {
        if (!ScreenGuardService.isEnabled(c) || ScreenGuardService.isSuspended(c) || isLocked(c)) {
            return;
        }
        try {
            StatDb db = new StatDb(c.getApplicationContext());
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long ds = cal.getTimeInMillis();
            StatDb.DayStat st = db.dayStats(ds, ds + DAY_MS);
            db.close();
            if (st.funMs >= FUN_THRESHOLD_MIN * 60_000L) {
                setLock(c);
                LockOverlay.show(c.getApplicationContext());
            }
        } catch (Exception ignored) {
        }
    }

    private static android.content.SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
