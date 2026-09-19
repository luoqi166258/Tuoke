package com.luoqi.tuoke;

import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 抖音真实采集流程：搜索关键词 -> 进入用户列表 -> 滚动采集用户 -> 记录结果。
 * 全程基于无障碍原子能力，含随机延迟与中止检查。
 */
public class DouyinFlow {

    private static final String PKG = "com.ss.android.ugc.aweme";
    private static final Random RND = new Random();

    private static final String[] SEARCH_HINTS = { "搜索", "search", "搜一搜" };
    private static final String[] USER_TAB_HINTS = { "用户", "用户tab", "Users" };

    /** 结果回调：返回采集到的用户/内容文本列表。 */
    public interface Result {
        void onCollected(int collected, List<String> items);
        void onError(String msg);
    }

    /** 执行采集。count 为目标条数，keyword 为搜索词。 */
    public static void collect(TuokeAccessibilityService svc, String keyword, int count, Result r) {
        if (svc == null) {
            r.onError("无障碍服务未就绪");
            return;
        }
        svc.resetAbort();
        try {
            // 1. 确认在前台
            TuokeAccessibilityService.log("抖音采集：等待前台就绪");
            if (!waitFront(svc, 6000)) {
                r.onError("未检测到抖音前台界面");
                return;
            }

            // 2. 进入搜索
            if (!openSearch(svc)) {
                r.onError("未找到搜索入口");
                return;
            }

            // 3. 输入关键词并提交
            if (!typeKeyword(svc, keyword)) {
                r.onError("搜索框写入失败");
                return;
            }

            // 4. 切到「用户」tab
            sleep(1200);
            switchToUserTab(svc);
            sleep(1500);

            // 5. 滚动采集
            List<String> items = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            int idle = 0;
            int maxScroll = Math.max(count * 3, 15);
            for (int i = 0; i < maxScroll; i++) {
                if (svc.isAborted()) {
                    TuokeAccessibilityService.log("采集已中止");
                    break;
                }
                int before = items.size();
                harvest(svc, items, seen);
                if (items.size() >= count) break;
                if (items.size() == before) {
                    idle++;
                    if (idle >= 4) {
                        TuokeAccessibilityService.log("连续无新增，停止滚动");
                        break;
                    }
                } else {
                    idle = 0;
                }
                AccessibilityNodeInfo scroll = svc.deepestScrollable(svc.root());
                if (scroll == null) break;
                svc.scrollForward(scroll);
                sleep(700 + RND.nextInt(600));
            }

            int total = Math.min(items.size(), count);
            TuokeAccessibilityService.log("采集完成：共 " + total + " 条");
            r.onCollected(total, items);
        } catch (Throwable t) {
            r.onError("采集异常：" + t.getMessage());
        }
    }

    /** 等待抖音进入前台。 */
    private static boolean waitFront(TuokeAccessibilityService svc, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (svc.isAborted()) return false;
            String p = svc.foregroundPackage();
            if (p != null && p.contains("aweme")) return true;
            sleep(300);
        }
        return false;
    }

    /** 打开搜索页。 */
    private static boolean openSearch(TuokeAccessibilityService svc) {
        for (String hint : SEARCH_HINTS) {
            if (svc.clickText(hint)) {
                TuokeAccessibilityService.log("已进入搜索页");
                return true;
            }
        }
        // 兜底：常见搜索框 viewId
        if (svc.clickId(PKG + ":id/search_bar")) {
            TuokeAccessibilityService.log("已进入搜索页(viewId)");
            return true;
        }
        return false;
    }

    /** 写入关键词并回车。 */
    private static boolean typeKeyword(TuokeAccessibilityService svc, String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) return false;
        sleep(900);
        AccessibilityNodeInfo box = svc.findById(PKG + ":id/search_input");
        if (box == null) box = svc.findByText("搜索");
        if (box == null) return false;
        boolean ok = svc.setText(box, kw);
        if (!ok) {
            TuokeAccessibilityService.log("输入框写入失败，尝试点击后重试");
            svc.clickNode(box);
            sleep(400);
            ok = svc.setText(svc.findById(PKG + ":id/search_input"), kw);
        }
        if (ok) {
            sleep(300);
            svc.clickText("搜索");
            TuokeAccessibilityService.log("已提交关键词：" + kw);
        }
        return ok;
    }

    /** 切到用户 tab。 */
    private static void switchToUserTab(TuokeAccessibilityService svc) {
        for (String t : USER_TAB_HINTS) {
            if (svc.clickText(t)) {
                TuokeAccessibilityService.log("已切换到用户 tab");
                return;
            }
        }
    }

    /** 从当前页采集可见文本，去重后追加。 */
    private static void harvest(TuokeAccessibilityService svc, List<String> out, Set<String> seen) {
        AccessibilityNodeInfo root = svc.root();
        if (root == null) return;
        List<String> raw = new ArrayList<>();
        svc.collectTexts(root, raw);
        for (String s : raw) {
            if (s == null) continue;
            String t = s.trim();
            if (t.length() < 2 || t.length() > 40) continue;
            if (isNoise(t)) continue;
            if (seen.add(t)) out.add(t);
        }
    }

    /** 过滤界面噪音文案。 */
    private static boolean isNoise(String t) {
        String[] noise = { "搜索", "取消", "首页", "朋友", "消息", "我", "关注", "推荐", "直播", "同城" };
        for (String n : noise) {
            if (n.equals(t)) return true;
        }
        return false;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
