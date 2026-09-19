package com.luoqi.tuoke;

import android.app.Activity;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 首页：数据总览 + 运行环境状态 + 快速入口。
 * 数据来源 TaskStore，环境状态来源 TuokeAccessibilityService。
 */
final class HomePage {
    private HomePage() {}

    static View build(final Activity a, final Runnable goFunc) {
        LinearLayout root = UiKit.page(a);

        // ── 数据总览卡片 ──
        LinearLayout card = UiKit.card(a);
        card.addView(UiKit.title(a, "今日数据"));
        card.addView(UiKit.space(a, 10));

        LinearLayout nums = UiKit.hbox(a);
        nums.addView(statCol(a, String.valueOf(TaskStore.todayRun(a)), "执行任务"));
        nums.addView(statCol(a, String.valueOf(TaskStore.todayOk(a)), "成功"));
        nums.addView(statCol(a, String.valueOf(TaskStore.todayCollect(a)), "采集数"));
        card.addView(nums);
        card.addView(UiKit.space(a, 8));
        card.addView(UiKit.sub(a, "今日成功率：" + TaskStore.successRate(a) + "%"));
        root.addView(card);

        // ── 运行环境卡片 ──
        LinearLayout env = UiKit.card(a);
        env.addView(UiKit.title(a, "运行环境"));
        env.addView(UiKit.space(a, 8));
        final boolean accReady = TuokeAccessibilityService.isReady();
        TextView accLine = UiKit.sub(a, "无障碍服务：" + (accReady ? "已开启" : "未开启（必须开启才能执行任务）"));
        accLine.setTextColor(accReady ? UiKit.C_PRIMARY : 0xFFE53935);
        env.addView(accLine);
        env.addView(UiKit.space(a, 10));
        env.addView(UiKit.ghost(a, accReady ? "无障碍已就绪" : "去开启无障碍服务",
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        try {
                            Intent it = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                            a.startActivity(it);
                        } catch (Exception e) {
                            TaskRunner.toast(a, "无法打开系统无障碍设置");
                        }
                    }
                }));
        env.addView(UiKit.space(a, 8));
        env.addView(UiKit.sub(a, "提示：部分系统会在后台杀掉无障碍服务，"
                + "建议在系统设置中把拓客工具加入后台白名单。"));
        root.addView(env);

        // ── 快速入口 ──
        LinearLayout quick = UiKit.card(a);
        quick.addView(UiKit.title(a, "快速开始"));
        quick.addView(UiKit.space(a, 10));
        quick.addView(UiKit.button(a, "前往功能页设置任务", UiKit.C_PRIMARY,
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        if (goFunc != null) goFunc.run();
                    }
                }));
        root.addView(quick);

        return root;
    }

    /** 单个数字统计列。 */
    private static LinearLayout statCol(Activity a, String num, String label) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(android.view.Gravity.CENTER);
        col.setLayoutParams(UiKit.weight());

        TextView tvNum = new TextView(a);
        tvNum.setText(num);
        tvNum.setTextSize(24);
        tvNum.setTextColor(UiKit.C_PRIMARY);
        tvNum.setTypeface(null, android.graphics.Typeface.BOLD);
        tvNum.setGravity(android.view.Gravity.CENTER);
        col.addView(tvNum);

        TextView tvLbl = new TextView(a);
        tvLbl.setText(label);
        tvLbl.setTextSize(12);
        tvLbl.setTextColor(UiKit.C_SUB);
        col.addView(tvLbl);
        return col;
    }
}
