package com.luoqi.tuoke;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 任务调度器：各类自动化动作的统一入口。
 *
 * 负责：
 *  1. 前置校验（无障碍是否开启、目标平台是否安装）
 *  2. 拉起目标平台
 *  3. 记录统计与日志
 *
 * 设计说明：具体平台的元素定位/点击流程属于“任务实现”，
 * 这里先把调度与状态闭环做通，保证【功能页可点、可跑、有反馈】，
 * 后续逐平台把 findAndClick 等真实流程补充进来即可。
 */
public final class TaskRunner {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private TaskRunner() {}

    /** 任务类型。 */
    public enum Action {
        COLLECT("采集用户"),
        DM("自动私信"),
        FOLLOW("自动关注"),
        COMMENT("自动评论"),
        LIKE("自动点赞");

        public final String label;
        Action(String label) { this.label = label; }
    }

    /** 任务回调。 */
    public interface Cb {
        void onLog(String line);
        void onDone(boolean ok, String msg);
    }

    /**
     * 执行一个动作。
     *
     * @param platform 目标平台
     * @param action   动作类型
     * @param keyword  关键词（采集/评论等使用，可为空）
     * @param count    目标条数
     */
    public static void run(Context ctx, Platform platform, Action action,
                           String keyword, int count, Cb cb) {
        // 1. 无障碍检查
        if (!TuokeAccessibilityService.isReady()) {
            cb.onDone(false, "请先开启无障碍服务");
            return;
        }
        // 2. 平台安装检查
        if (!platform.installed(ctx)) {
            cb.onDone(false, platform.label + " 未安装");
            return;
        }

        String ts = new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date());
        cb.onLog("[" + ts + "] 启动：" + platform.label + " · " + action.label
                + (keyword == null || keyword.isEmpty() ? "" : " · 关键词：" + keyword)
                + " · 目标 " + count + " 条");

        // 3. 拉起目标平台
        boolean launched = platform.launch(ctx);
        cb.onLog(launched ? "已拉起 " + platform.label : "拉起 " + platform.label + " 失败");

        // 4. 交给任务实现执行（此处为调度位，真实流程逐平台接入）
        TuokeAccessibilityService.log(platform.label + " · " + action.label + " 已排队");

        MAIN.postDelayed(() -> {
            // 任务完成后记录统计
            TaskStore.recordRun(ctx, launched, 0);
            String msg = launched
                    ? platform.label + " · " + action.label + " 任务已提交"
                    : platform.label + " 启动失败";
            cb.onLog("[" + now() + "] " + msg);
            cb.onDone(launched, msg);
        }, 1200);
    }

    private static String now() {
        return new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date());
    }

    public static void toast(Context c, String s) {
        MAIN.post(() -> Toast.makeText(c.getApplicationContext(), s, Toast.LENGTH_SHORT).show());
    }
}
