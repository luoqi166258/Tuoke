package com.luoqi.tuoke;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 轻量 UI 工厂：统一卡片、标题、按钮、间距的风格。
 * 全部用代码构建，零布局依赖，避免和现有布局文件耦合。
 */
final class UiKit {
    static final int C_PRIMARY = 0xFF00C853;
    static final int C_BG = 0xFFF5F5F5;
    static final int C_CARD = 0xFFFFFFFF;
    static final int C_TITLE = 0xFF222222;
    static final int C_SUB = 0xFF888888;
    static final int C_LINE = 0xFFEEEEEE;

    private UiKit() {}

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** 垂直页面容器（带内边距）。 */
    static LinearLayout page(Context c) {
        LinearLayout ll = new LinearLayout(c);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        int p = dp(c, 14);
        ll.setPadding(p, p, p, p);
        ll.setBackgroundColor(C_BG);
        return ll;
    }

    /** 圆角卡片。 */
    static LinearLayout card(Context c) {
        LinearLayout ll = new LinearLayout(c);
        ll.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_CARD);
        bg.setCornerRadius(dp(c, 12));
        ll.setBackground(bg);
        int p = dp(c, 16);
        ll.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 12);
        ll.setLayoutParams(lp);
        return ll;
    }

    static TextView title(Context c, String s) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(15);
        tv.setTextColor(C_TITLE);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        return tv;
    }

    static TextView sub(Context c, String s) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(12);
        tv.setTextColor(C_SUB);
        tv.setLineSpacing(dp(c, 2), 1f);
        return tv;
    }

    /** 实心按钮。 */
    static TextView button(Context c, String s, int color, View.OnClickListener click) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(15);
        tv.setTextColor(0xFFFFFFFF);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(c, 10));
        tv.setBackground(bg);
        tv.setPadding(dp(c, 12), dp(c, 13), dp(c, 12), dp(c, 13));
        tv.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(lp);
        return tv;
    }

    /** 描边按钮（次要动作）。 */
    static TextView ghost(Context c, String s, View.OnClickListener click) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(14);
        tv.setTextColor(C_PRIMARY);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dp(c, 10));
        bg.setStroke(dp(c, 1), C_PRIMARY);
        tv.setBackground(bg);
        tv.setPadding(dp(c, 10), dp(c, 12), dp(c, 10), dp(c, 12));
        tv.setOnClickListener(click);
        return tv;
    }

    static View space(Context c, int h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, h)));
        return v;
    }

    /** 撑满宽度的横向等分容器。 */
    static LinearLayout hbox(Context c) {
        LinearLayout ll = new LinearLayout(c);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return ll;
    }

    static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }
}
