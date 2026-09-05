package com.screenguard.timer;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * 统计库：每开一轮计时记一条（带目的 学习/娱乐）。
 * 只关心「实际使用时长」，不再记录次数/是否完成。
 */
public class StatDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "stats.db";
    private static final int DB_VERSION = 2;
    private static final String TABLE = "rounds";
    private static final String COL_ID = "_id";
    private static final String COL_START = "start_ms";
    private static final String COL_END = "end_ms";
    private static final String COL_MIN = "minutes";
    private static final String COL_PURPOSE = "purpose";

    public StatDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                COL_START + " INTEGER NOT NULL," +
                COL_END + " INTEGER NOT NULL DEFAULT 0," +
                COL_MIN + " INTEGER NOT NULL DEFAULT 0," +
                COL_PURPOSE + " TEXT NOT NULL DEFAULT '')");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    public long openRound(long startMs, int minutes, String purpose) {
        ContentValues cv = new ContentValues();
        cv.put(COL_START, startMs);
        cv.put(COL_MIN, minutes);
        cv.put(COL_END, 0);
        cv.put(COL_PURPOSE, purpose);
        return getWritableDatabase().insert(TABLE, null, cv);
    }

    public void closeRound(long id, long endMs) {
        if (id < 0) return;
        ContentValues cv = new ContentValues();
        cv.put(COL_END, endMs);
        getWritableDatabase().update(TABLE, cv, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    /** 把异常残留的"未结束"记录（end_ms=0）结清到 lastActiveMs（被杀/息屏后不再累计到当前） */
    public void closeStaleRounds(long lastActiveMs) {
        getWritableDatabase().execSQL(
                "UPDATE " + TABLE + " SET " + COL_END + " = max(" + COL_START + ", ?) WHERE " + COL_END + " = 0",
                new Object[]{lastActiveMs});
    }

    /** 删除早于 cutoff 的记录（7 天前直接清除） */
    public void pruneOlderThan(long cutoffMs) {
        getWritableDatabase().delete(TABLE, COL_START + "<?", new String[]{String.valueOf(cutoffMs)});
    }

    public static class DayStat {
        public long studyMs = 0;
        public long funMs = 0;
    }

    /** 统计 [dayStart, dayEnd) 之间开始的轮次，按目的分别累计实际时长 */
    public DayStat dayStats(long dayStart, long dayEnd) {
        DayStat s = new DayStat();
        long now = System.currentTimeMillis();
        Cursor c = null;
        try {
            c = getReadableDatabase().query(TABLE,
                    new String[]{COL_START, COL_END, COL_PURPOSE},
                    COL_START + ">=? AND " + COL_START + "<?",
                    new String[]{String.valueOf(dayStart), String.valueOf(dayEnd)},
                    null, null, COL_START + " ASC");
            while (c.moveToNext()) {
                long st = c.getLong(0);
                long en = c.getLong(1);
                boolean study = "study".equals(c.getString(2));
                long realEnd = (en > 0) ? en : now;
                if (realEnd < st) realEnd = st;
                long ms = realEnd - st;
                if (study) s.studyMs += ms;
                else s.funMs += ms;
            }
        } finally {
            if (c != null) c.close();
        }
        return s;
    }

    /** 把某一天的时长按小时切分到 24 个桶（studyMs/funMs 为 long[24]，单位毫秒） */
    public void fillHourBuckets(long dayStart, long[] studyMs, long[] funMs) {
        long dayEnd = dayStart + 24 * 3600_000L;
        long now = System.currentTimeMillis();
        Cursor c = null;
        try {
            c = getReadableDatabase().query(TABLE,
                    new String[]{COL_START, COL_END, COL_PURPOSE},
                    COL_START + ">=? AND " + COL_START + "<?",
                    new String[]{String.valueOf(dayStart), String.valueOf(dayEnd)},
                    null, null, null);
            while (c.moveToNext()) {
                long st = c.getLong(0);
                long en = c.getLong(1);
                boolean study = "study".equals(c.getString(2));
                long realEnd = (en > 0) ? en : now;
                if (realEnd < st) realEnd = st;
                long lo = Math.max(st, dayStart);
                long hi = Math.min(realEnd, dayEnd);
                if (hi <= lo) continue;
                for (int h = 0; h < 24; h++) {
                    long hs = dayStart + h * 3600_000L;
                    long he = hs + 3600_000L;
                    long inter = Math.min(hi, he) - Math.max(lo, hs);
                    if (inter > 0) {
                        if (study) studyMs[h] += inter;
                        else funMs[h] += inter;
                    }
                }
            }
        } finally {
            if (c != null) c.close();
        }
    }
}
