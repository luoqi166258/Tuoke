package com.luoqi.tuoke;

import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 抖音采集流程（无障碍原子能力编排）。
 * 只做编排，不碰节点细节，节点能力全在 TuokeAccessibilityService。
 */
public final class DouyinFlow {

    private DouyinFlow() { }

    public static final String PKG = "com.ss.android.ugc.aweme";
    private static final Random RND = new Random();

    private static final String[] SEARCH_HINTS = { "搜索", "search", "搜一搜" };
    private static final String[] USER_TAB_HINTS = { "用户", "用户tab", "Users" };

    /** 搜索框候选 viewId（不同抖音版本差异较大，逐个尝试）。 */
    private static final String[] SEARCH_INPUT_IDS = {
            PKG + ":id/search_input",
            PKG + ":id/search_src_text",
            PKG + ":id/et_search_kw",
            PKG + ":id/search_edit_text",
            PKG + ":id/edit_text"
    };
    /** 搜索入口候选 viewId。 */
    private static final String[] SEARCH_BAR_IDS = {
            PKG + ":id/search_bar",
            PKG + ":id/search_icon",
            PKG + ":id/search_button",
            PKG + ":id/top_search"
    };

    /** 采集回调。 */
    public interface Result {
        void onCollected(int collected, List<String> items);
        void onError(String msg);
    }

    /**
     * 执行一次抖音关键词采集。
     * @param svc     无障碍服务实例
     * @param keyword 关键词
     * @param count   目标条数
     * @param r       回调
     */
    public static void collect(TuokeAccessibilityService svc, String keyword, int count, Result r) {
        if (svc == null) { r.onError("无障碍服务未就绪"); return; }
        if (keyword == null || keyword.trim().isEmpty()) { r.onError("关键词为空"); return; }
        if (count <= 0) count = 20;
        svc.resetAbort();
        try {
            // 1. 等抖音前台
            if (!waitFront(svc, 6000)) { r.onError("未检测到抖音前台界面"); return; }
            // 2. 打开搜索页
            if (!openSearch(svc)) { r.onError("未找到搜索入口"); return; }
            // 3. 写入关键词并提交
            if (!typeKeyword(svc, keyword)) { r.onError("搜索框写入失败"); return; }
            sleep(1200);
            // 4. 切到用户 tab
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
            List<String> out = new ArrayList<>(items.subList(0, total));
            TuokeAccessibilityService.log("采集完成：共 " + total + " 条");
            r.onCollected(total, out);
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

    /** 打开搜索页（文本优先，viewId 多候选兜底）。 */
    private static boolean openSearch(TuokeAccessibilityService svc) {
        for (String hint : SEARCH_HINTS) {
            if (svc.clickText(hint)) {
                TuokeAccessibilityService.log("已进入搜索页(text=" + hint + ")");
                return true;
            }
        }
        for (String id : SEARCH_BAR_IDS) {
            if (svc.clickId(id)) {
                TuokeAccessibilityService.log("已进入搜索页(id=" + id + ")");
                return true;
            }
        }
        return false;
    }

    /** 写入关键词并回车（多候选输入框 + 点击重试）。 */
    private static boolean typeKeyword(TuokeAccessibilityService svc, String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) return false;
        sleep(900);
        AccessibilityNodeInfo box = findInput(svc);
        if (box == null) {
            TuokeAccessibilityService.log("未找到输入框，尝试先点搜索条");
            openSearch(svc);
            sleep(700);
            box = findInput(svc);
        }
        if (box == null) box = svc.findByText("搜索");
        if (box == null) return false;
        boolean ok = svc.setText(box, kw);
        if (!ok) {
            TuokeAccessibilityService.log("输入框写入失败，点击后重试");
            svc.clickNode(box);
            sleep(400);
            AccessibilityNodeInfo again = findInput(svc);
            if (again != null) ok = svc.setText(again, kw);
        }
        if (ok) {
            sleep(300);
            if (!svc.clickText("搜索")) svc.clickText("搜一搜");
            TuokeAccessibilityService.log("已提交关键词：" + kw);
        }
        return ok;
    }

    /** 遍历候选 viewId 找输入框。 */
    private static AccessibilityNodeInfo findInput(TuokeAccessibilityService svc) {
        for (String id : SEARCH_INPUT_IDS) {
            AccessibilityNodeInfo n = svc.findById(id);
            if (n != null) return n;
        }
        return null;
    }

    /** 切到用户 tab。 */
    private static void switchToUserTab(TuokeAccessibilityService svc) {
        for (String t : USER_TAB_HINTS) {
            if (svc.clickText(t)) {
                TuokeAccessibilityService.log("已切换到用户 tab");
                return;
            }
        }
        TuokeAccessibilityService.log("未找到用户 tab，按综合结果继续");
    }

    /** 从当前页采集可见文本，去重后追加。 */
    private static void harvest(TuokeAccessibilityService svc, List<String> out, Set<String> seen) {
        AccessibilityNodeInfo root = svc.root();
        if (root == null) return;
        List<String> raw = new ArrayList<>();
        svc.collectTexts(root, raw);
        for (String s : raw) {
            if (s == null) continue;
            String t = s.replaceAll("\s+", " ").trim();
            if (t.length() < 2 || t.length() > 40) continue;
            if (isNoise(t)) continue;
            if (isNumeric(t)) continue;
            if (seen.add(t)) out.add(t);
        }
    }

    /** 过滤界面噪音文案。 */
    private static boolean isNoise(String t) {
        String[] noise = { "搜索", "取消", "首页", "朋友", "消息", "我", "关注",
                "推荐", "直播", "同城", "综合", "视频", "用户", "商品",
                "音乐", "地点", "筛选", "更多", "全部", "点赞", "评论", "分享" };
        for (String n : noise) {
            if (n.equals(t)) return true;
        }
        if (t.startsWith("@")) return true;
        return false;
    }

    /** 纯数字/纯符号文本过滤。 */
    private static boolean isNumeric(String t) {
        boolean hasLetter = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isLetter(c) || Character.isIdeographic(c)) {
                hasLetter = true;
                break;
            }
        }
        return !hasLetter;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
