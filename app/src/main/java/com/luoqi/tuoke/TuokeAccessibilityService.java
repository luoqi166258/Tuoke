package com.luoqi.tuoke;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 拓客无障碍服务：提供自动化所需的基础原子能力。
 * 所有业务动作（采集/私信/关注等）统一经由本服务提供的原子能力完成。
 */
public class TuokeAccessibilityService extends AccessibilityService {

    private static volatile TuokeAccessibilityService INSTANCE;
    private static final List<String> LOGS = Collections.synchronizedList(new ArrayList<String>());
    private static final int MAX_LOGS = 200;

    private volatile boolean aborted = false;

    public static TuokeAccessibilityService get() { return INSTANCE; }

    public static boolean isReady() { return INSTANCE != null; }

    public static void log(String s) {
        if (s == null) return;
        LOGS.add(s);
        while (LOGS.size() > MAX_LOGS) LOGS.remove(0);
    }

    public static List<String> logs() { return new ArrayList<>(LOGS); }

    public static void clearLogs() { LOGS.clear(); }

    public static int sdkInt() { return Build.VERSION.SDK_INT; }

    public void resetAbort() { aborted = false; }

    public void abort() { aborted = true; log("已请求中止任务"); }

    public boolean isAborted() { return aborted; }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        INSTANCE = this;
        log("无障碍服务已连接");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        INSTANCE = null;
        log("无障碍服务已断开");
        return super.onUnbind(intent);
    }

    /** 当前前台应用包名。 */
    public String foregroundPackage() {
        try {
            AccessibilityNodeInfo r = root();
            if (r == null) return "";
            CharSequence p = r.getPackageName();
            return p == null ? "" : p.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** 获取活动窗口根节点（多窗口兜底）。 */
    public AccessibilityNodeInfo root() {
        try {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            if (r != null) return r;
            List<android.view.accessibility.AccessibilityWindowInfo> ws = getWindows();
            if (ws != null) {
                for (android.view.accessibility.AccessibilityWindowInfo w : ws) {
                    if (w == null) continue;
                    AccessibilityNodeInfo n = w.getRoot();
                    if (n != null) return n;
                }
            }
        } catch (Throwable t) { }
        return null;
    }

    /** 按可见文本查找节点。 */
    public AccessibilityNodeInfo findByText(String text) {
        if (text == null) return null;
        AccessibilityNodeInfo r = root();
        if (r == null) return null;
        try {
            List<AccessibilityNodeInfo> list = r.findAccessibilityNodeInfosByText(text);
            if (list != null) {
                for (AccessibilityNodeInfo n : list) {
                    if (n != null && n.isVisibleToUser()) return n;
                }
                if (!list.isEmpty()) return list.get(0);
            }
        } catch (Throwable t) { }
        return null;
    }

    /** 按 viewId 查找节点。 */
    public AccessibilityNodeInfo findById(String viewId) {
        if (viewId == null) return null;
        AccessibilityNodeInfo r = root();
        if (r == null) return null;
        try {
            List<AccessibilityNodeInfo> list = r.findAccessibilityNodeInfosByViewId(viewId);
            if (list != null) {
                for (AccessibilityNodeInfo n : list) {
                    if (n != null && n.isVisibleToUser()) return n;
                }
                if (!list.isEmpty()) return list.get(0);
            }
        } catch (Throwable t) { }
        return null;
    }

    /** 向上寻找最近的可点击祖先。 */
    public AccessibilityNodeInfo clickableAncestor(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo cur = n;
        int depth = 0;
        while (cur != null && depth < 12) {
            if (cur.isClickable()) return cur;
            cur = cur.getParent();
            depth++;
        }
        return n;
    }

    /** 点击节点（自动找可点击祖先）。 */
    public boolean clickNode(AccessibilityNodeInfo n) {
        if (n == null) return false;
        AccessibilityNodeInfo t = clickableAncestor(n);
        try {
            if (t != null && t.isClickable()) return t.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (n.isClickable()) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } catch (Throwable e) { }
        return false;
    }

    /** 按文本点击。 */
    public boolean clickText(String text) {
        AccessibilityNodeInfo n = findByText(text);
        boolean ok = clickNode(n);
        if (ok) log("点击文本：" + text);
        return ok;
    }

    /** 按 viewId 点击。 */
    public boolean clickId(String viewId) {
        AccessibilityNodeInfo n = findById(viewId);
        boolean ok = clickNode(n);
        if (ok) log("点击控件：" + viewId);
        return ok;
    }

    /** 向输入框写入文本。 */
    public boolean setText(AccessibilityNodeInfo n, String text) {
        if (n == null) return false;
        try {
            if (!n.isEditable()) {
                AccessibilityNodeInfo p = n.getParent();
                if (p != null && p.isEditable()) n = p;
            }
            android.os.Bundle b = new android.os.Bundle();
            b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text == null ? "" : text);
            return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
        } catch (Throwable t) { }
        return false;
    }

    /** 全局返回。 */
    public boolean back() { return performGlobalAction(GLOBAL_ACTION_BACK); }

    /** 回到桌面。 */
    public boolean home() { return performGlobalAction(GLOBAL_ACTION_HOME); }

    /** 向前滚动。 */
    public boolean scrollForward(AccessibilityNodeInfo n) {
        if (n == null) return false;
        try {
            return n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        } catch (Throwable t) { }
        return false;
    }

    /** 找到最深的可滚动节点。 */
    public AccessibilityNodeInfo deepestScrollable(AccessibilityNodeInfo root) {
        if (root == null) return null;
        AccessibilityNodeInfo found = null;
        try {
            List<AccessibilityNodeInfo> queue = new ArrayList<>();
            queue.add(root);
            while (!queue.isEmpty()) {
                AccessibilityNodeInfo cur = queue.remove(0);
                if (cur == null) continue;
                if (cur.isScrollable() && cur.isVisibleToUser()) found = cur;
                for (int i = 0; i < cur.getChildCount(); i++) {
                    AccessibilityNodeInfo c = cur.getChild(i);
                    if (c != null) queue.add(c);
                }
            }
        } catch (Throwable t) { }
        return found;
    }

    /** 递归收集节点下的文本。 */
    public void collectTexts(AccessibilityNodeInfo n, List<String> out) {
        if (n == null || out == null) return;
        try {
            CharSequence t = n.getText();
            if (t != null && t.length() > 0) out.add(t.toString().trim());
            CharSequence d = n.getContentDescription();
            if (d != null && d.length() > 0) out.add(d.toString().trim());
            for (int i = 0; i < n.getChildCount(); i++) {
                collectTexts(n.getChild(i), out);
            }
        } catch (Throwable e) { }
    }
}
