package com.screenguard.timer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 主界面：三 Tab（今日 / 记录 / 权限）切换。
 * 今日：状态 + 今日使用时长；记录：24 小时柱状图 + 近 7 天；权限：引导 + 换图。
 */
public class MainActivity extends Activity {

    private static final int REQ_OPEN_IMAGE = 100;
    private static final long DAY_MS = 24 * 3600_000L;
    private static final int C_ACTIVE = Color.parseColor("#3F51B5");
    private static final int C_INACTIVE = Color.parseColor("#757575");

    private StatDb db;
    private Switch enableSwitch;
    private TextView statusText;
    private TextView todayTotal, todayStudy, todayFun;
    private LinearLayout weekTable;
    private TextView imageStatus;
    private HourBarView hourBar;
    private Button btnOverlay, btnDevice, btnBattery, btnNotif, btnChange, btnAccessibility, btnExit;
    private NumberPicker lockDurPicker;

    private View pageToday, pageRecord, pagePerms, pageWhitelist;
    private TextView tabToday, tabRecord, tabPerms, tabWhitelist;
    private LinearLayout whitelistContainer;
    private Button btnAddWhitelist;

    private boolean refreshing = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateStatusText();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        db = new StatDb(this);

        if (ScreenGuardService.isEnabled(this)) {
            // 手动打开 App = 之前退出的恢复点
            ScreenGuardService.setSuspended(this, false);
            ScreenGuardService.startMonitor(this);
        }

        enableSwitch = findViewById(R.id.enable_switch);
        statusText = findViewById(R.id.status_text);
        todayTotal = findViewById(R.id.today_total);
        todayStudy = findViewById(R.id.today_study);
        todayFun = findViewById(R.id.today_fun);
        weekTable = findViewById(R.id.week_table);
        hourBar = findViewById(R.id.hour_bar);
        imageStatus = findViewById(R.id.image_status);
        btnOverlay = findViewById(R.id.btn_overlay);
        btnDevice = findViewById(R.id.btn_deviceadmin);
        btnBattery = findViewById(R.id.btn_battery);
        btnNotif = findViewById(R.id.btn_notif);
        btnAccessibility = findViewById(R.id.btn_accessibility);
        btnExit = findViewById(R.id.btn_exit);
        btnChange = findViewById(R.id.btn_change_image);
        lockDurPicker = findViewById(R.id.lock_dur_picker);
        pageWhitelist = findViewById(R.id.page_whitelist);
        whitelistContainer = findViewById(R.id.whitelist_container);
        btnAddWhitelist = findViewById(R.id.btn_add_whitelist);

        pageToday = findViewById(R.id.page_today);
        pageRecord = findViewById(R.id.page_record);
        pagePerms = findViewById(R.id.page_perms);
        tabToday = findViewById(R.id.tab_today);
        tabRecord = findViewById(R.id.tab_record);
        tabPerms = findViewById(R.id.tab_perms);
        tabWhitelist = findViewById(R.id.tab_whitelist);

        tabToday.setOnClickListener(v -> showTab(0));
        tabRecord.setOnClickListener(v -> showTab(1));
        tabWhitelist.setOnClickListener(v -> showTab(2));
        tabPerms.setOnClickListener(v -> showTab(3));
        showTab(0);

        enableSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (refreshing) return;
                getSharedPreferences(ScreenGuardService.PREF_NAME, MODE_PRIVATE)
                        .edit().putBoolean(ScreenGuardService.KEY_ENABLED, isChecked).apply();
                if (isChecked) {
                    ScreenGuardService.startMonitor(MainActivity.this);
                    Toast.makeText(MainActivity.this, "已开启：锁屏后再次亮屏会弹出用途+时长选择", Toast.LENGTH_LONG).show();
                } else {
                    ScreenGuardService.stopMonitor(MainActivity.this);
                    Toast.makeText(MainActivity.this, "已停用", Toast.LENGTH_SHORT).show();
                }
                refresh();
            }
        });

        btnOverlay.setOnClickListener(v -> openOverlaySetting());
        btnDevice.setOnClickListener(v -> openDeviceAdminSetting());
        btnBattery.setOnClickListener(v -> openBatterySetting());
        btnNotif.setOnClickListener(v -> requestNotificationPermission());
        btnAccessibility.setOnClickListener(v -> openAccessibilitySetting());
        btnExit.setOnClickListener(v -> onExitApp());
        btnChange.setOnClickListener(v -> openImagePicker());
        btnAddWhitelist.setOnClickListener(v -> onAddWhitelist());

        initLockDurationPicker();
        ensureDefaultWhitelist();
        renderWhitelist();
    }

    /** 切换 Tab 显示 */
    private void showTab(int idx) {
        pageToday.setVisibility(idx == 0 ? View.VISIBLE : View.GONE);
        pageRecord.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
        pageWhitelist.setVisibility(idx == 2 ? View.VISIBLE : View.GONE);
        pagePerms.setVisibility(idx == 3 ? View.VISIBLE : View.GONE);
        setTab(tabToday, idx == 0);
        setTab(tabRecord, idx == 1);
        setTab(tabWhitelist, idx == 2);
        setTab(tabPerms, idx == 3);
        refresh();
    }

    private void setTab(TextView tv, boolean active) {
        tv.setTextColor(active ? C_ACTIVE : C_INACTIVE);
        tv.setTypeface(null, active ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    /** 实时更新状态文字（每秒） */
    private void updateStatusText() {
        boolean enabled = ScreenGuardService.isEnabled(this);
        if (!enabled) {
            statusText.setText("已停用（打开右上角开关）");
        } else if (ScreenGuardService.state == ScreenGuardService.STATE_COUNTING) {
            long remain = ScreenGuardService.countdownEndMs - System.currentTimeMillis();
            long sec = Math.max(0, remain / 1000);
            statusText.setText("计时中 · 剩余 " + (sec / 60) + " 分 " + (sec % 60) + " 秒");
        } else if (ScreenGuardService.state == ScreenGuardService.STATE_ALERT) {
            statusText.setText("到点提醒中：点悬浮窗图片即锁屏");
        } else {
            statusText.setText("监测中 · 锁屏后再亮屏会弹出用途+时长选择");
        }
    }

    // ---------------------------------------------------------------- 刷新界面

    private void refresh() {
        refreshing = true;
        try {
            boolean enabled = ScreenGuardService.isEnabled(this);
            enableSwitch.setChecked(enabled);
            updateStatusText();

            long dayStart = startOfToday();
            // 只保留最近 7 天（今天 + 前 6 天），更早的删除
            db.pruneOlderThan(dayStart - 6 * DAY_MS);
            // 若监测服务不在运行，可能残留"未结束"记录，结清到最近活跃时刻（不把息屏时间算进去）
            if (!ScreenGuardService.isRunning()) {
                long lastActive = getSharedPreferences(ScreenGuardService.PREF_NAME, MODE_PRIVATE)
                        .getLong(ScreenGuardService.KEY_LAST_ACTIVE, System.currentTimeMillis());
                db.closeStaleRounds(lastActive);
            }

            StatDb.DayStat ds = db.dayStats(dayStart, dayStart + DAY_MS);
            int total = Math.round((ds.studyMs + ds.funMs) / 60000f);
            todayTotal.setText(String.valueOf(total));
            todayStudy.setText(String.valueOf(Math.round(ds.studyMs / 60000f)));
            todayFun.setText(String.valueOf(Math.round(ds.funMs / 60000f)));

            long[] study = new long[24];
            long[] fun = new long[24];
            db.fillHourBuckets(dayStart, study, fun);
            hourBar.setData(study, fun);

            renderWeekTable(weekTable);

            boolean overlay = Settings.canDrawOverlays(this);
            setPermButton(btnOverlay, overlay, overlay ? "已开启" : "去开启");

            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            boolean admin = dpm != null && dpm.isAdminActive(
                    new ComponentName(this, LockAdminReceiver.class));
            setPermButton(btnDevice, admin, admin ? "已激活" : "去激活");

            boolean acc = AccessLockService.isEnabled();
            setPermButton(btnAccessibility, acc, acc ? "已开启" : "去开启");

            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            boolean battery = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
            setPermButton(btnBattery, battery, battery ? "已开启" : "去开启");

            boolean notif = hasNotificationPermission();
            setPermButton(btnNotif, notif, notif ? "已开启" : "去开启");

            if (ReminderImageStore.exists(this)) {
                imageStatus.setText("当前：你选择的图片（点更换可换）");
            } else {
                imageStatus.setText("当前：默认占位图（点更换可从相册选择）");
            }
        } finally {
            refreshing = false;
        }
    }

    private void setPermButton(Button b, boolean granted, String label) {
        b.setText(label);
        b.setEnabled(!granted);
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    // ---------------------------------------------------------------- 近 7 天

    private void renderWeekTable(LinearLayout container) {
        container.removeAllViews();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long today = cal.getTimeInMillis();
        String[] weeks = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        float density = getResources().getDisplayMetrics().density;
        for (int offset = 6; offset >= 0; offset--) {
            long start = today - offset * DAY_MS;
            StatDb.DayStat ds = db.dayStats(start, start + DAY_MS);
            int study = Math.round(ds.studyMs / 60000f);
            int fun = Math.round(ds.funMs / 60000f);
            String dayLabel;
            if (offset == 0) dayLabel = "今天";
            else if (offset == 1) dayLabel = "昨天";
            else {
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(start);
                dayLabel = (c.get(Calendar.MONTH) + 1) + "月" + c.get(Calendar.DAY_OF_MONTH) + "日 "
                        + weeks[c.get(Calendar.DAY_OF_WEEK) - 1];
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, (int) (8 * density), 0, (int) (8 * density));
            row.addView(cell(dayLabel, 0xFF212121, Gravity.START | Gravity.CENTER_VERTICAL, 1.6f));
            row.addView(cell(String.valueOf(study), 0xFF2196F3, Gravity.CENTER, 1f));
            row.addView(cell(String.valueOf(fun), 0xFFFF9800, Gravity.CENTER, 1f));
            row.addView(cell(String.valueOf(study + fun), 0xFF212121, Gravity.CENTER, 1f));
            container.addView(row);
        }
    }

    private TextView cell(String text, int color, int gravity, float weight) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(color);
        tv.setGravity(gravity);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight));
        return tv;
    }

    private long startOfToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    // ---------------------------------------------------------------- 权限引导

    private void openOverlaySetting() {
        if (Settings.canDrawOverlays(this)) return;
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "请在系统设置-应用-考研自律钟-悬浮窗里开启", Toast.LENGTH_LONG).show();
        }
    }

    private void openDeviceAdminSetting() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        ComponentName cn = new ComponentName(this, LockAdminReceiver.class);
        if (dpm != null && dpm.isAdminActive(cn)) return;
        try {
            Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn);
            i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "用于到点提醒后自动锁屏，不会管理你的任何数据");
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开激活界面，请手动在系统设置中查找", Toast.LENGTH_LONG).show();
        }
    }

    private void openBatterySetting() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) return;
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "请在系统设置-电池-后台限制里允许运行", Toast.LENGTH_LONG).show();
        }
    }

    private void requestNotificationPermission() {
        if (hasNotificationPermission()) return;
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    private void openAccessibilitySetting() {
        if (AccessLockService.isEnabled()) return;
        AccessLockService.openSettings(this);
    }

    private void onExitApp() {
        ScreenGuardService.setSuspended(this, true);
        ScreenGuardService.stopMonitor(this);
        Toast.makeText(this, "已退出：自动检测已暂停，下次打开 App 才恢复", Toast.LENGTH_LONG).show();
        refresh();
    }

    /** 锁机时长设置：2–120 分钟，10 分钟一梯度（2,10,20,...,120），改动即保存 */
    private void initLockDurationPicker() {
        int[] vals = {2, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120};
        String[] labels = new String[vals.length];
        for (int i = 0; i < vals.length; i++) labels[i] = vals[i] + " 分钟";
        lockDurPicker.setMinValue(0);
        lockDurPicker.setMaxValue(vals.length - 1);
        lockDurPicker.setDisplayedValues(labels);
        lockDurPicker.setWrapSelectorWheel(false);
        int cur = getSharedPreferences(ScreenGuardService.PREF_NAME, MODE_PRIVATE)
                .getInt(LockGuard.KEY_LOCK_DURATION_MIN, LockGuard.DEFAULT_LOCK_MIN);
        int idx = 0;
        for (int i = 0; i < vals.length; i++) {
            if (vals[i] <= cur) idx = i;
        }
        lockDurPicker.setValue(idx);
        lockDurPicker.setOnValueChangedListener((picker, oldV, newV) ->
                getSharedPreferences(ScreenGuardService.PREF_NAME, MODE_PRIVATE)
                        .edit().putInt(LockGuard.KEY_LOCK_DURATION_MIN, vals[newV]).apply());
    }

    // ---------------------------------------------------------------- 锁机白名单

    private void ensureDefaultWhitelist() {
        if (getSharedPreferences(ScreenGuardService.PREF_NAME, MODE_PRIVATE)
                .getStringSet(ScreenGuardService.KEY_WHITELIST, null) == null) {
            Set<String> def = new HashSet<>();
            def.add("com.larus.nova");                 // 豆包
            def.add("cn.com.langeasy.LangEasyLexis");  // 不背单词
            def.add("com.shanbay.kaoyan");             // 扇贝考研
            getSharedPreferences(ScreenGuardService.PREF_NAME, MODE_PRIVATE)
                    .edit().putStringSet(ScreenGuardService.KEY_WHITELIST, def).apply();
        }
    }

    private Set<String> whitelistSet() {
        return getSharedPreferences(ScreenGuardService.PREF_NAME, MODE_PRIVATE)
                .getStringSet(ScreenGuardService.KEY_WHITELIST, new HashSet<String>());
    }

    private void renderWhitelist() {
        whitelistContainer.removeAllViews();
        List<String> pkgs = new java.util.ArrayList<>(whitelistSet());
        if (pkgs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有放行的软件，点上方「＋ 添加白名单软件」选择");
            empty.setTextColor(0xFF757575);
            empty.setTextSize(13);
            whitelistContainer.addView(empty);
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        for (final String pkg : pkgs) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, (int) (10 * density), 0, (int) (10 * density));

            TextView name = new TextView(this);
            name.setText(appLabel(pkg));
            name.setTextColor(0xFF212121);
            name.setTextSize(14);
            name.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(name);

            Button del = new Button(this);
            del.setText("移除");
            del.setTextSize(13);
            del.setTextColor(0xFFFFFFFF);
            del.setBackgroundResource(R.drawable.bg_btn_primary);
            del.setOnClickListener(v -> {
                removeFromWhitelist(pkg);
                renderWhitelist();
            });
            row.addView(del);
            whitelistContainer.addView(row);
        }
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() + "  (" + pkg + ")";
        } catch (Exception e) {
            return pkg;
        }
    }

    private void removeFromWhitelist(String pkg) {
        Set<String> s = new HashSet<>(whitelistSet());
        s.remove(pkg);
        getSharedPreferences(ScreenGuardService.PREF_NAME, MODE_PRIVATE)
                .edit().putStringSet(ScreenGuardService.KEY_WHITELIST, s).apply();
    }

    private void onAddWhitelist() {
        final List<ResolveInfo> apps = getPackageManager()
                .queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        if (apps.isEmpty()) {
            Toast.makeText(this, "未找到可添加的应用", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            CharSequence lb = apps.get(i).loadLabel(getPackageManager());
            labels[i] = lb != null ? lb.toString() : apps.get(i).activityInfo.packageName;
        }
        new AlertDialog.Builder(this)
                .setTitle("选择要放行的软件")
                .setItems(labels, (d, which) -> {
                    String pkg = apps.get(which).activityInfo.packageName;
                    if (whitelistSet().contains(pkg)) {
                        Toast.makeText(this, "已在白名单中", Toast.LENGTH_SHORT).show();
                    } else {
                        Set<String> s = new HashSet<>(whitelistSet());
                        s.add(pkg);
                        getSharedPreferences(ScreenGuardService.PREF_NAME, MODE_PRIVATE)
                                .edit().putStringSet(ScreenGuardService.KEY_WHITELIST, s).apply();
                        Toast.makeText(this, "已添加：" + appLabel(pkg), Toast.LENGTH_SHORT).show();
                    }
                    renderWhitelist();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------------------------------------------------------------- 更换提醒图片

    private void openImagePicker() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_OPEN_IMAGE);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开相册", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OPEN_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            boolean ok = ReminderImageStore.save(this, data.getData());
            Toast.makeText(this, ok ? "提醒图片已更换" : "图片保存失败，请换一张试试", Toast.LENGTH_SHORT).show();
            refresh();
        }
    }
}
