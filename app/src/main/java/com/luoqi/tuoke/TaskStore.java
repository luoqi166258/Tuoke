package com.luoqi.tuoke;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 本地数据仓库：记录今日任务统计。
 * 纯本地存储（SharedPreferences），不依赖任何第三方服务，保证离线可用、CI 可编译。
 */
public final class TaskStore {

    private static final String SP = "tuoke_stats";
    private static final String K_DAY = "stat_day";
    private static final String K_RUN = "stat_run";
    private static final String K_OK = "stat_ok";
    private static final String K_COLLECT = "stat_collect";

    private TaskStore() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(SP, Context.MODE_PRIVATE);
    }

    /** 跨天自动清零。 */
    public static void ensureToday(Context c) {
        String today = today();
        SharedPreferences p = sp(c);
        if (!today.equals(p.getString(K_DAY, ""))) {
            p.edit().putString(K_DAY, today).putInt(K_RUN, 0).putInt(K_OK, 0).putInt(K_COLLECT, 0).apply();
        }
    }

    /** 记一次任务执行结果。 */
    public static void recordRun(Context c, boolean success, int collected) {
        ensureToday(c);
        SharedPreferences p = sp(c);
        p.edit()
                .putInt(K_RUN, p.getInt(K_RUN, 0) + 1)
                .putInt(K_OK, p.getInt(K_OK, 0) + (success ? 1 : 0))
                .putInt(K_COLLECT, p.getInt(K_COLLECT, 0) + Math.max(collected, 0))
                .apply();
    }

    public static int todayRun(Context c) { ensureToday(c); return sp(c).getInt(K_RUN, 0); }
    public static int todayOk(Context c) { ensureToday(c); return sp(c).getInt(K_OK, 0); }
    public static int todayCollect(Context c) { ensureToday(c); return sp(c).getInt(K_COLLECT, 0); }

    /** 成功率百分比（无任务时返回 0）。 */
    public static int successRate(Context c) {
        int run = todayRun(c);
        if (run <= 0) return 0;
        return Math.round(todayOk(c) * 100f / run);
    }

    /** 清空今日统计。 */
    public static void reset(Context c) {
        sp(c).edit().putString(K_DAY, today()).putInt(K_RUN, 0).putInt(K_OK, 0).putInt(K_COLLECT, 0).apply();
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
    }
}
