package com.luoqi.tuoke;

import android.app.Activity;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 功能页：任务操作台。
 * 结构：平台选择 → 动作选择 → 参数（关键词/条数）→ 开始 → 日志。
 */
final class FuncPage {
    private FuncPage() {}

    private static Platform platform = Platform.DOUYIN;
    private static TaskRunner.Action action = TaskRunner.Action.COLLECT;

    static View build(final Activity a) {
        ScrollView sv = new ScrollView(a);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        sv.setBackgroundColor(UiKit.C_BG);

        LinearLayout root = UiKit.page(a);
        sv.addView(root);

        // ── 1. 平台选择 ──
        LinearLayout pCard = UiKit.card(a);
        pCard.addView(UiKit.title(a, "选择平台"));
        pCard.addView(UiKit.space(a, 10));
        LinearLayout pRow = UiKit.hbox(a);
        for (final Platform p : Platform.values()) {
            TextView chip = chip(a, p.label, p == platform);
            chip.setLayoutParams(UiKit.weight());
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) chip.getLayoutParams();
            lp.rightMargin = UiKit.dp(a, 6);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    platform = p;
                    rebuild(a, (LinearLayout) pRow.getParent(), null);
                }
            });
            pRow.addView(chip);
        }
        pCard.addView(pRow);
        pCard.addView(UiKit.space(a, 8));
        pCard.addView(UiKit.sub(a, "当前：" + platform.label
                + (platform.installed(a) ? "（已安装）" : "（未安装，无法执行）")));
        root.addView(pCard);

        // ── 2. 动作选择 ──
        LinearLayout aCard = UiKit.card(a);
        aCard.addView(UiKit.title(a, "选择动作"));
        aCard.addView(UiKit.space(a, 10));
        LinearLayout aRow1 = UiKit.hbox(a);
        LinearLayout aRow2 = UiKit.hbox(a);
        TaskRunner.Action[] acts = TaskRunner.Action.values();
        for (int i = 0; i < acts.length; i++) {
            final TaskRunner.Action act = acts[i];
            TextView chip = chip(a, act.label, act == action);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    action = act;
                    rebuild(a, root, null);
                }
            });
            LinearLayout.LayoutParams lp = UiKit.weight();
            lp.rightMargin = UiKit.dp(a, 6);
            chip.setLayoutParams(lp);
            if (i < 2) aRow1.addView(chip); else aRow2.addView(chip);
        }
        aCard.addView(aRow1);
        aCard.addView(UiKit.space(a, 8));
        aCard.addView(aRow2);
        root.addView(aCard);

        // ── 3. 参数 ──
        LinearLayout kCard = UiKit.card(a);
        kCard.addView(UiKit.title(a, "任务参数"));
        kCard.addView(UiKit.space(a, 10));
        final EditText etKeyword = new EditText(a);
        etKeyword.setHint("输入关键词（如：祛痘 / 同城 / 美甲）");
        etKeyword.setTextSize(14);
        etKeyword.setSingleLine(true);
        etKeyword.setInputType(InputType.TYPE_CLASS_TEXT);
        etKeyword.setPadding(UiKit.dp(a, 12), UiKit.dp(a, 12),
                UiKit.dp(a, 12), UiKit.dp(a, 12));
        etKeyword.setBackgroundResource(android.R.drawable.editbox_background_normal);
        kCard.addView(etKeyword);
        kCard.addView(UiKit.space(a, 10));

        final EditText etCount = new EditText(a);
        etCount.setHint("目标条数（默认 20）");
        etCount.setText("20");
        etCount.setTextSize(14);
        etCount.setSingleLine(true);
        etCount.setInputType(InputType.TYPE_CLASS_NUMBER);
        etCount.setPadding(UiKit.dp(a, 12), UiKit.dp(a, 12),
                UiKit.dp(a, 12), UiKit.dp(a, 12));
        etCount.setBackgroundResource(android.R.drawable.editbox_background_normal);
        kCard.addView(etCount);
        kCard.addView(UiKit.space(a, 6));
        kCard.addView(UiKit.sub(a, "关键词用于采集/评论/私信定位目标用户；"
                + "条数为本次任务的上限。"));
        root.addView(kCard);

        // ── 3.5 采集结果 ──
        LinearLayout rCard = UiKit.card(a);
        rCard.addView(UiKit.title(a, "采集结果"));
        rCard.addView(UiKit.space(a, 10));
        final TextView tvResult = new TextView(a);
        tvResult.setTextSize(13);
        tvResult.setTextColor(0xFF999999);
        tvResult.setLineSpacing(UiKit.dp(a, 3), 1f);
        tvResult.setText("暂无采集结果，执行采集任务后在此查看");
        rCard.addView(tvResult);
        root.addView(rCard);
        // ── 4. 日志 ──
        LinearLayout lCard = UiKit.card(a);
        lCard.addView(UiKit.title(a, "任务日志"));
        lCard.addView(UiKit.space(a, 10));
        final TextView tvLog = new TextView(a);
        tvLog.setTextSize(12);
        tvLog.setTextColor(0xFF444444);
        tvLog.setLineSpacing(UiKit.dp(a, 3), 1f);
        tvLog.setTypeface(Typeface.MONOSPACE);
        refreshLog(tvLog);
        lCard.addView(tvLog);
        root.addView(lCard);

        // ── 5. 开始按钮（放底部） ──
        TextView start = UiKit.button(a, "开始" + action.label, UiKit.C_PRIMARY,
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        String kw = etKeyword.getText().toString().trim();
                        int cnt = 20;
                        try { cnt = Integer.parseInt(etCount.getText().toString().trim()); }
                        catch (Exception ignored) {}
                        if (cnt <= 0) cnt = 20;
                        if ((action == TaskRunner.Action.COLLECT
                                || action == TaskRunner.Action.COMMENT
                                || action == TaskRunner.Action.DM) && kw.isEmpty()) {
                            TaskRunner.toast(a, "请先输入关键词");
                            return;
                        }
                        TuokeAccessibilityService.clearLogs();
                        final int finalCnt = cnt;
                        final String finalKw = kw;
                        TaskRunner.run(a, platform, action, kw, cnt, new TaskRunner.Cb() {
                            @Override public void onLog(String line) {
                                refreshLog(tvLog);
                            }
                            @Override public void onItems(java.util.List<String> items) {
                                if (items == null || items.isEmpty()) {
                                    tvResult.setText("未采集到结果");
                                    tvResult.setTextColor(0xFF999999);
                                    return;
                                }
                                StringBuilder sb = new StringBuilder();
                                for (int i = 0; i < items.size(); i++) {
                                    sb.append(i + 1).append(". ").append(items.get(i)).append('\n');
                                }
                                tvResult.setTextColor(0xFF333333);
                                tvResult.setText(sb.toString().trim());
                            }
                            @Override public void onDone(boolean ok, String msg) {
                                refreshLog(tvLog);
                                TaskRunner.toast(a, msg);
                            }
                        });
                        refreshLog(tvLog);
                    }
                });
        root.addView(start);

        return sv;
    }

    /** 重建页面（切换平台/动作后刷新选中态）。 */
    private static void rebuild(Activity a, LinearLayout oldParent, String unused) {
        View v = oldParent.getRootView();
        // 直接复用 MainActivity 的刷新入口
        if (a instanceof MainActivity) {
            ((MainActivity) a).refreshCurrentTab();
        }
    }

    private static void refreshLog(TextView tv) {
        java.util.List<String> logs = TuokeAccessibilityService.logs();
        if (logs.isEmpty()) {
            tv.setText("暂无日志，执行任务后在此查看");
            tv.setTextColor(0xFF999999);
            return;
        }
        tv.setTextColor(0xFF444444);
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, logs.size() - 30);
        for (int i = start; i < logs.size(); i++) {
            sb.append(logs.get(i)).append('\n');
        }
        tv.setText(sb.toString().trim());
    }

    /** 标签式选择控件。 */
    private static TextView chip(Activity a, String text, boolean selected) {
        TextView tv = new TextView(a);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(UiKit.dp(a, 6), UiKit.dp(a, 10),
                UiKit.dp(a, 6), UiKit.dp(a, 10));
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(UiKit.dp(a, 8));
        if (selected) {
            bg.setColor(0x1A00C853);
            bg.setStroke(UiKit.dp(a, 1), UiKit.C_PRIMARY);
            tv.setTextColor(UiKit.C_PRIMARY);
            tv.setTypeface(null, Typeface.BOLD);
        } else {
            bg.setColor(0xFFF2F2F2);
            tv.setTextColor(0xFF666666);
        }
        tv.setBackground(bg);
        return tv;
    }
}
