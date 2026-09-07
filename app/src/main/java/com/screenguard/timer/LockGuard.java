package com.screenguard.timer;

import android.content.Context;

import java.util.Calendar;

/**
 * 娱乐超限锁机守卫。
 * 正常模式：当天娱乐累计达到"今日允许娱乐时间"后触发锁机，锁机结束进入"惩罚模式"。
 * 惩罚模式：每次娱乐固定 20 分钟额度（可剩余续计），中途息屏暂停（不扣时），到点/用尽再次锁机；
 *           学习不计入；次日娱乐额度未超时自动恢复正常模式。
 * 锁机期间由 AccessLockService 拦截非白名单应用。
 */
public final class LockGuard {

    public static final String KEY_LOCK_UNTIL = "lock_until";
    public static final String KEY_LOCK_DURATION_MIN = "lock_dur_min";
    public static final String KEY_FUN_THRESHOLD_MIN = "fun_threshold";
    public static final String KEY_PUNISH = "punish_mode";
    public static final String KEY_PUNISH_REMAIN = "punish_remain_ms";
    public static final int DEFAULT_LOCK_MIN = 20;                     // 默认锁机 20 分钟（记录页可改 2-120）
    public static final int DEFAULT_FUN_THRESHOLD_MIN = 120;           // 默认每日允许娱乐 120 分钟
    public static final long PUNISH_DURATION_MS = 20 * 60_000L;        // 惩罚模式每次娱乐固定 20 分钟
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
        prefs(c).edit().putLong(KEY_LOCK_UNTIL,
                System.currentTimeMillis() + getLockDurationMs(c)).apply();
    }

    public static long getLockDurationMs(Context c) {
        int min = prefs(c).getInt(KEY_LOCK_DURATION_MIN, DEFAULT_LOCK_MIN);
        return min * 60_000L;
    }

    public static int getFunThresholdMin(Context c) {
        return prefs(c).getInt(KEY_FUN_THRESHOLD_MIN, DEFAULT_FUN_THRESHOLD_MIN);
    }

    public static void clearLock(Context c) {
        prefs(c).edit().putLong(KEY_LOCK_UNTIL, 0).apply();
    }

    // -------- 惩罚模式 --------

    public static boolean isPunish(Context c) {
        return prefs(c).getBoolean(KEY_PUNISH, false);
    }

    public static void setPunish(Context c, boolean v) {
        prefs(c).edit().putBoolean(KEY_PUNISH, v).apply();
    }

    public static long getPunishRemain(Context c) {
        return prefs(c).getLong(KEY_PUNISH_REMAIN, PUNISH_DURATION_MS);
    }

    public static void setPunishRemain(Context c, long ms) {
        prefs(c).edit().putLong(KEY_PUNISH_REMAIN, Math.max(0, ms)).apply();
    }

    /** 锁机结束后进入惩罚模式并重置 20 分钟额度 */
    public static void enterPunish(Context c) {
        setPunish(c, true);
        prefs(c).edit().putLong(KEY_PUNISH_REMAIN, PUNISH_DURATION_MS).apply();
    }

    private static long todayFunMs(Context c) {
        StatDb db = new StatDb(c.getApplicationContext());
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long ds = cal.getTimeInMillis();
        StatDb.DayStat st = db.dayStats(ds, ds + DAY_MS);
        db.close();
        return st.funMs;
    }

    /** 娱乐累计超阈值则触发锁机（在每次计时结束后调用） */
    public static void maybeEnterLock(Context c) {
        if (!ScreenGuardService.isEnabled(c) || ScreenGuardService.isSuspended(c) || isLocked(c)) {
            return;
        }
        try {
            long funMs = todayFunMs(c);
            if (funMs < getFunThresholdMin(c) * 60_000L) {
                // 额度未超：恢复正常模式（次日/学习后）
                if (isPunish(c)) {
                    setPunish(c, false);
                    prefs(c).edit().putLong(KEY_PUNISH_REMAIN, PUNISH_DURATION_MS).apply();
                }
                return;
            }
            // 正常模式首次超限 -> 触发锁机（锁机结束由 LockOverlay 进入惩罚模式）
            if (!isPunish(c)) {
                setLock(c);
                LockOverlay.show(c.getApplicationContext());
            }
            // 惩罚模式下的再次锁定由"惩罚计时到点"处理，这里不重复
        } catch (Exception ignored) {
        }
    }

    private static android.content.SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
