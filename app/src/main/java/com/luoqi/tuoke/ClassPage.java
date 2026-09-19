package com.luoqi.tuoke;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 课堂页：使用教程与注意事项。
 * 纯静态内容，不依赖布局文件。
 */
final class ClassPage {
    private ClassPage() {}

    static View build(Activity a) {
        ScrollView sv = new ScrollView(a);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        LinearLayout root = UiKit.page(a);
        sv.addView(root);

        root.addView(UiKit.title(a, "使用教程"));
        root.addView(UiKit.space(a, 6));
        root.addView(UiKit.sub(a, "按下面步骤，3 分钟上手拓客工具"));
        root.addView(UiKit.space(a, 12));

        LinearLayout c1 = UiKit.card(a);
        c1.addView(UiKit.title(a, "第 1 步 · 开启无障碍服务"));
        c1.addView(UiKit.space(a, 6));
        c1.addView(stepText(a, "拓客的自动化能力依赖系统无障碍服务。去「首页 → 运行环境」点击「前往开启」，在系统设置里找到「拓客工具」并打开开关。"));
        c1.addView(UiKit.space(a, 10));
        c1.addView(UiKit.button(a, "去开启无障碍", UiKit.C_PRIMARY, v -> {
            Intent it = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { a.startActivity(it); } catch (Exception e) {
                TaskRunner.toast(a, "无法打开设置");
            }
        }));
        root.addView(c1);

        LinearLayout c2 = UiKit.card(a);
        c2.addView(UiKit.title(a, "第 2 步 · 选平台与动作"));
        c2.addView(UiKit.space(a, 6));
        c2.addView(stepText(a, "切到「功能」页：先选对应平台（抖音/快手/小红书/视频号），再选动作（采集用户/自动私信/自动关注/自动评论/自动点赞）。"));
        c2.addView(UiKit.space(a, 6));
        c2.addView(stepText(a, "只有「采集用户」「自动评论」「自动私信」需要填关键词；其余动作可留空。"));
        root.addView(c2);

        LinearLayout c3 = UiKit.card(a);
        c3.addView(UiKit.title(a, "第 3 步 · 填参数并开始"));
        c3.addView(UiKit.space(a, 6));
        c3.addView(stepText(a, "输入关键词（如「装修」「美甲」）与执行条数（默认 20），点「开始执行」。系统会自动跳转到目标平台并进行操作。"));
        c3.addView(UiKit.space(a, 6));
        c3.addView(stepText(a, "执行中不要手动切换 App；结果会实时写入「任务日志」，完成后「首页」会累加数据。"));
        root.addView(c3);

        LinearLayout c4 = UiKit.card(a);
        c4.addView(UiKit.title(a, "第 4 步 · 防掉线与风控"));
        c4.addView(UiKit.space(a, 6));
        c4.addView(stepText(a, "1. 在系统省电/后台管理里把拓客加入白名单，防止无障碍服务被杀。"));
        c4.addView(stepText(a, "2. 每次执行条数不宜过大，建议 20~50 条分批执行。"));
        c4.addView(stepText(a, "3. 避免高峰时段频繁操作，降低帐号风险。"));
        root.addView(c4);

        LinearLayout c5 = UiKit.card(a);
        c5.addView(UiKit.title(a, "常见问题"));
        c5.addView(UiKit.space(a, 6));
        c5.addView(stepText(a, "Q：点开始没反应？"));
        c5.addView(UiKit.sub(a, "A：先确认无障碍已开启，再确认目标平台已安装。"));
        c5.addView(UiKit.space(a, 6));
        c5.addView(stepText(a, "Q：任务日志没内容？"));
        c5.addView(UiKit.sub(a, "A：说明尚未执行或无障碍未连接，重启 App 后重试。"));
        root.addView(c5);

        LinearLayout c6 = UiKit.card(a);
        c6.addView(UiKit.title(a, "关于本工具"));
        c6.addView(UiKit.space(a, 6));
        c6.addView(stepText(a, "拓客工具是一款面向商家的获客辅助工具，提供平台内容采集与互动能力。请在合规范围内使用，因使用本工具产生的后果由使用者自行承担。"));
        c6.addView(UiKit.space(a, 10));
        c6.addView(UiKit.ghost(a, "加入交流群", v -> {
            Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse("https://qm.qq.com/q/jk2u1tVLzy"));
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { a.startActivity(it); } catch (Exception e) {
                TaskRunner.toast(a, "无法打开链接");
            }
        }));
        root.addView(c6);

        root.addView(UiKit.space(a, 20));
        return sv;
    }

    /** 步骤正文（左对齐、行高舒适）。 */
    private static TextView stepText(Activity a, String s) {
        TextView tv = new TextView(a);
        tv.setText(s);
        tv.setTextSize(14);
        tv.setTextColor(0xFF444444);
        tv.setLineSpacing(UiKit.dp(a, 5), 1.0f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(lp);
        return tv;
    }
}
