package com.luoqi.tuoke;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * 目标平台定义：包名 / 显示名 / 主题色。
 *
 * 每个平台对应一个安装包，自动化任务通过无障碍服务操作这些 App。
 */
public enum Platform {

    DOUYIN("抖音", "com.ss.android.ugc.aweme", 0xFF1B1B1B),
    KUAISHOU("快手", "com.smile.gifmaker", 0xFFFF6600),
    XHS("小红书", "com.xingin.xhs", 0xFFFF2442),
    SHIPINHAO("视频号", "com.tencent.mm", 0xFF07C160);

    public final String label;
    public final String pkg;
    public final int color;

    Platform(String label, String pkg, int color) {
        this.label = label;
        this.pkg = pkg;
        this.color = color;
    }

    /** 该平台是否已安装。 */
    public boolean installed(Context c) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** 尝试打开该平台 App（未安装返回 false）。 */
    public boolean launch(Context c) {
        Intent it = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (it == null) return false;
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(it);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static Platform of(String name) {
        for (Platform p : values()) if (p.name().equals(name)) return p;
        return DOUYIN;
    }
}
