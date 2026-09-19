package com.luoqi.tuoke;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 任务调度器：各类自动化动作的统一入口。
 *
 * 负责：
 *  1. 前置校验（无障碍是否开启、目标平台是否安装）
 *  2. 拉起目标平台
 *  3. 交给平台专用流程执行真实采集/互动
 *  4. 记录统计与日志
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
        TuokeAccessibilityService.log(platform.label + " · " + action.label + " 已排队");

        if (!launched) {
            TaskStore.recordRun(ctx, false, 0);
            cb.onDone(false, platform.label + " 启动失败");
            return;
        }

        // 4. 交给平台专用流程执行
        switch (platform) {
            case DOUYIN:
                dispatchDouyin(ctx, action, keyword, count, cb);
                break;
            default:
                // 其余平台暂未接入真实流程，保留调度闭环
                MAIN.postDelayed(() -> {
                    TaskStore.recordRun(ctx, true, 0);
                    String msg = platform.label + " · " + action.label + " 任务已排队（该平台流程待接入）";
                    cb.onLog("[" + now() + "] " + msg);
                    cb.onDone(true, msg);
                }, 1200);
                break;
        }
    }

    /** 抖音平台任务分发。 */
    private static void dispatchDouyin(Context ctx, Action action, String keyword,
                                       int count, Cb cb) {
        TuokeAccessibilityService svc = TuokeAccessibilityService.get();
        if (svc == null) {
            TaskStore.recordRun(ctx, false, 0);
            cb.onDone(false, "无障碍服务实例不可用");
            return;
        }
        if (action != Action.COLLECT) {
            // 私信/关注/评论/点赞后续接入，先记录排队
            MAIN.postDelayed(() -> {
                TaskStore.recordRun(ctx, true, 0);
                String msg = "抖音 · " + action.label + " 已排队（流程开发中）";
                cb.onLog("[" + now() + "] " + msg);
                cb.onDone(true, msg);
            }, 1200);
            return;
        }
        // 采集：走抖音真实采集流程
        cb.onLog("[" + now() + "] 开始抖音采集：关键词 " + (keyword == null ? "" : keyword)
                + " · 目标 " + count + " 条");
        DouyinFlow.collect(svc, keyword, count, new DouyinFlow.Result() {
            @Override
            public void onCollected(int collected, List<String> items) {
                MAIN.post(() -> {
                    TaskStore.recordRun(ctx, true, collected);
                    String msg = "抖音采集完成：共 " + collected + " 条";
                    cb.onLog("[" + now() + "] " + msg);
                    cb.onDone(true, msg);
                });
            }

            @Override
            public void onError(String err) {
                MAIN.post(() -> {
                    TaskStore.recordRun(ctx, false, 0);
                    cb.onLog("[" + now() + "] 采集失败：" + err);
                    cb.onDone(false, err);
                });
            }
        });
    }

    private static String now() {
        return new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date());
    }

    public static void toast(Context c, String s) {
        MAIN.post(() -> Toast.makeText(c.getApplicationContext(), s, Toast.LENGTH_SHORT).show());
    }
}
