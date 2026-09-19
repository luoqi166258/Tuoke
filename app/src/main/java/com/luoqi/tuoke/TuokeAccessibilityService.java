package com.luoqi.tuoke;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 拓客无障碍服务：自动化任务的核心执行入口。
 *
 * 职责：
 *  1. 维护服务连接状态（供界面判断是否已开启无障碍）
 *  2. 对外提供当前前台包名、节点查找、点击等基础原子能力
 *  3. 维护最近任务日志（供「首页」展示）
 *
 * 注意：本类只提供“原子能力”，具体平台（抖音/快手…）的自动化
 * 流程由各任务类调用这些原子能力实现，职责清晰、便于逐个接入。
 */
public class TuokeAccessibilityService extends AccessibilityService {

    private static volatile TuokeAccessibilityService instance;

    /** 最近任务日志（内存，最多保留 100 条）。 */
    private static final List<String> LOGS = Collections.synchronizedList(new ArrayList<>());

    public static TuokeAccessibilityService get() { return instance; }

    /** 无障碍服务是否已连接。 */
    public static boolean isReady() { return instance != null; }

    public static void log(String line) {
        synchronized (LOGS) {
            LOGS.add(0, line);
            while (LOGS.size() > 100) LOGS.remove(LOGS.size() - 1);
        }
    }

    public static List<String> logs() {
        synchronized (LOGS) {
            return new ArrayList<>(LOGS);
        }
    }

    public static void clearLogs() {
        synchronized (LOGS) { LOGS.clear(); }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        // 配置：监听包/窗口变化 + 内容变化，便于任务实时感知界面
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_VIEW_CLICKED
                | AccessibilityEvent.TYPE_VIEW_SCROLLED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        info.notificationTimeout = 100;
        setServiceInfo(info);
        log("无障碍服务已连接");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 事件级能力保留给后续任务实现（例如监听页面变化、触发下一步）
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        log("无障碍服务已断开");
        return super.onUnbind(intent);
    }

    /** 当前前台包名（拿不到返回空串）。 */
    public String foregroundPackage() {
        try {
            android.view.accessibility.AccessibilityWindowInfo w = getWindows().isEmpty()
                    ? null : getWindows().get(0);
            if (w != null && w.getRoot() != null && w.getRoot().getPackageName() != null) {
                return String.valueOf(w.getRoot().getPackageName());
            }
        } catch (Throwable ignored) {
        }
        return "";
    }
}
