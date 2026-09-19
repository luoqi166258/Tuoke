package com.luoqi.tuoke;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.luoqi.tuoke.databinding.ActivityMainBinding;

/**
 * 主界面：顶栏 + 内容容器 + 底部四 Tab（首页/功能/课堂/我的）。
 *
 * 结构对齐目标 App 形态，各 Tab 目前为占位页，
 * 后续按平台（抖音/快手/小红书…）逐个接入自动化模块。
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding b;
    private final Handler hb = new Handler(Looper.getMainLooper());
    private int curTab = 0;

    // 心跳间隔：官方返回 hg=600（秒），这里对齐 10 分钟
    private static final long HB_INTERVAL = 600_000L;
    private final Runnable hbTask = new Runnable() {
        @Override
        public void run() {
            PjYunAuth.heartbeat(MainActivity.this, new PjYunAuth.Callback() {
                @Override
                public void onOk(org.json.JSONObject data) {
                    // 心跳正常，继续下一次
                }

                @Override
                public void onFail(String msg) {
                    toast("卡密失效：" + msg);
                    // 回登录页
                    PjYunAuth.clearCard(MainActivity.this);
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
                    finish();
                }
            });
            hb.postDelayed(this, HB_INTERVAL);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        String card = PjYunAuth.cardFromPrefs(this);
        b.tvCardInfo.setText(card.isEmpty() ? "未登录" : "卡密：" + mask(card));

        b.tabHome.setOnClickListener(v -> switchTab(0));
        b.tabFunc.setOnClickListener(v -> switchTab(1));
        b.tabClass.setOnClickListener(v -> switchTab(2));
        b.tabMine.setOnClickListener(v -> switchTab(3));

        switchTab(0);
        hb.postDelayed(hbTask, HB_INTERVAL);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hb.removeCallbacks(hbTask);
    }

    private void switchTab(int idx) {
        curTab = idx;
        b.container.removeAllViews();

        String title;
        switch (idx) {
            case 0: title = "首页"; break;
            case 1: title = "功能"; break;
            case 2: title = "课堂"; break;
            default: title = "我的"; break;
        }
        b.tvTitle.setText(title);

        // 各 Tab 分发到独立页面构建器
        View page;
        switch (idx) {
            case 0: page = HomePage.build(this, () -> switchTab(1)); break;
            case 1: page = FuncPage.build(this); break;
            case 2: page = ClassPage.build(this); break;
            default: page = buildMinePage(); break;
        }
        b.container.addView(page);

        setTabSelected(idx);
    }

    /** 供子页面（如功能页执行完任务后）刷新当前 Tab 内容。 */
    public void refreshCurrentTab() {
        switchTab(curTab);
    }

    /** 先给一个占位页，后续各平台功能模块替换进来。 */
    private View buildPage(String title) {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setGravity(android.view.Gravity.CENTER);
        ll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        TextView tv = new TextView(this);
        tv.setText(title + "功能模块\n（待接入）");
        tv.setTextSize(16);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setTextColor(0xFF666666);
        ll.addView(tv);
        return ll;
    }

    /** 官方交流群链接（用户指定）。 */
    private static final String GROUP_URL = "https://qm.qq.com/q/jk2u1tVLzy";

    /**
     * 「我的」页：账号信息 + 加入交流群入口。
     */
    private View buildMinePage() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        int pad = dp(20);
        ll.setPadding(pad, pad, pad, pad);

        // 账号卡片
        TextView tvCard = new TextView(this);
        String card = PjYunAuth.cardFromPrefs(this);
        tvCard.setText(card.isEmpty() ? "未登录" : "卡密：" + mask(card));
        tvCard.setTextSize(15);
        tvCard.setTextColor(0xFF333333);
        tvCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        tvCard.setBackgroundColor(0xFFF5F5F5);
        ll.addView(tvCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ll.addView(space(dp(16)));

        // 加入交流群
        ll.addView(buildRow("加入交流群", "点击加入官方 QQ 群", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openGroup();
            }
        }));

        return ll;
    }

    /** 一行可点击条目：标题 + 副标题。 */
    private View buildRow(String title, String sub, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(16), dp(16), dp(16));
        row.setBackgroundColor(0xFFFFFFFF);
        row.setOnClickListener(click);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView tvT = new TextView(this);
        tvT.setText(title);
        tvT.setTextSize(16);
        tvT.setTextColor(0xFF222222);
        col.addView(tvT);
        if (sub != null) {
            TextView tvS = new TextView(this);
            tvS.setText(sub);
            tvS.setTextSize(12);
            tvS.setTextColor(0xFF999999);
            col.addView(tvS);
        }
        row.addView(col, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(22);
        arrow.setTextColor(0xFFBBBBBB);
        row.addView(arrow);

        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    /**
     * 打开 QQ 加群页。优先用 QQ 内置包尝试拉起，
     * 任何异常都回落到浏览器打开，保证链接一定可用。
     */
    private void openGroup() {
        try {
            Intent it = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(GROUP_URL));
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(it);
        } catch (Exception e) {
            // 极端情况下连浏览器都没有
            toast("无法打开链接：" + GROUP_URL);
        }
    }

    private View space(int px) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px));
        return v;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void setTabSelected(int idx) {
        TextView[] tabs = {b.tabHome, b.tabFunc, b.tabClass, b.tabMine};
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setSelected(i == idx);
            tabs[i].setTextColor(i == idx ? 0xFF00C853 : 0xFF888888);
        }
    }

    private String mask(String card) {
        if (card.length() <= 6) return card;
        return card.substring(0, 3) + "****" + card.substring(card.length() - 3);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}