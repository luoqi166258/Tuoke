package com.luoqi.tuoke;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 泡椒云网络验证对接（自研实现，非第三方改包）。
 *
 * 接口约定（泡椒云标准版）：
 *   POST http://api.paojiaoyun.com/v1/card/login     卡密登录
 *   POST http://api.paojiaoyun.com/v1/card/heartbeat 心跳
 *   POST http://api.paojiaoyun.com/v1/card/logout    解绑/退出
 *
 * 关键点：queryCard / heartbeat / logout 都必须带 card 参数，
 * 所以登录成功必须把 card 明文落盘（SharedPreferences），后续复用。
 */
public class PjYunAuth {

    private static final String BASE = "http://api.paojiaoyun.com";
    private static final String PATH_LOGIN = "/v1/card/login";
    private static final String PATH_HEARTBEAT = "/v1/card/heartbeat";
    private static final String PATH_LOGOUT = "/v1/card/logout";

    public static final String PREF = "tuoke_prefs";
    public static final String K_CARD = "card";
    public static final String K_TOKEN = "token";

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // ---------------- 对外回调 ----------------
    public interface Callback {
        void onOk(JSONObject data);
        void onFail(String msg);
    }

    // ---------------- 凭据解密 ----------------
    /** 把 BuildConfig 里被切片/反转/Base64 的凭据还原为明文。 */
    private static String joinParts(String[] parts) {
        if (parts == null || parts.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            sb.append(new String(Base64.decode(p, Base64.DEFAULT), StandardCharsets.UTF_8));
        }
        return sb.reverse().toString();
    }

    public static String appKey() {
        return joinParts(BuildConfig.PJ_KEY_PARTS);
    }

    public static String appSecret() {
        return joinParts(BuildConfig.PJ_SECRET_PARTS);
    }

    // ---------------- 本地卡密存储 ----------------
    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void saveCard(Context ctx, String card, String token) {
        prefs(ctx).edit().putString(K_CARD, card).putString(K_TOKEN, token).apply();
    }

    public static String cardFromPrefs(Context ctx) {
        if (ctx == null) return "";
        String c = prefs(ctx).getString(K_CARD, "");
        if (c == null || c.isEmpty()) c = prefs(ctx).getString("cardKey", "");
        return c == null ? "" : c;
    }

    public static String tokenFromPrefs(Context ctx) {
        if (ctx == null) return "";
        String t = prefs(ctx).getString(K_TOKEN, "");
        return t == null ? "" : t;
    }

    public static void clearCard(Context ctx) {
        prefs(ctx).edit().remove(K_CARD).remove(K_TOKEN).apply();
    }

    // ---------------- 网络请求 ----------------
    private static JSONObject post(String path, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE + path).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        String raw = sb.toString();
        JSONObject obj = new JSONObject(raw);
        obj.put("__http", code);
        return obj;
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static void runAsync(final Callback cb, final Runnable job) {
        POOL.execute(() -> {
            try {
                job.run();
            } catch (Exception e) {
                final String msg = e.getMessage() == null ? "网络异常" : e.getMessage();
                MAIN.post(() -> cb.onFail(msg));
            }
        });
    }

    // ---------------- 业务接口 ----------------

    /** 卡密登录。 */
    public static void login(Context ctx, final String card, final Callback cb) {
        final Context app = ctx.getApplicationContext();
        runAsync(cb, () -> {
            String body = "app_key=" + enc(appKey())
                    + "&app_secret=" + enc(appSecret())
                    + "&card=" + enc(card);
            final JSONObject r = post(PATH_LOGIN, body);
            MAIN.post(() -> {
                int http = r.optInt("__http", -1);
                int ret = r.optInt("code", r.optInt("status", -1));
                if (http == 200 && (ret == 0 || ret == 200)) {
                    String token = r.optString("token", "");
                    saveCard(app, card, token);
                    cb.onOk(r);
                } else {
                    String msg = r.optString("msg", r.optString("message", ""));
                    if (msg.isEmpty()) msg = "登录失败(" + http + "/" + ret + ")";
                    cb.onFail(msg);
                }
            });
        });
    }

    /** 心跳（保活 + 校验卡密仍有效）。 */
    public static void heartbeat(Context ctx, final Callback cb) {
        final Context app = ctx.getApplicationContext();
        runAsync(cb, () -> {
            String card = cardFromPrefs(app);
            if (card.isEmpty()) { MAIN.post(() -> cb.onFail("卡密为空")); return; }
            String body = "app_key=" + enc(appKey())
                    + "&app_secret=" + enc(appSecret())
                    + "&card=" + enc(card)
                    + "&token=" + enc(tokenFromPrefs(app));
            final JSONObject r = post(PATH_HEARTBEAT, body);
            MAIN.post(() -> {
                int ret = r.optInt("code", r.optInt("status", -1));
                if (ret == 0 || ret == 200) cb.onOk(r);
                else cb.onFail(r.optString("msg", "心跳失败"));
            });
        });
    }

    /** 解绑（把卡密从当前设备解绑）。 */
    public static void logout(Context ctx, final Callback cb) {
        final Context app = ctx.getApplicationContext();
        runAsync(cb, () -> {
            String card = cardFromPrefs(app);
            String body = "app_key=" + enc(appKey())
                    + "&app_secret=" + enc(appSecret())
                    + "&card=" + enc(card)
                    + "&token=" + enc(tokenFromPrefs(app));
            final JSONObject r = post(PATH_LOGOUT, body);
            MAIN.post(() -> {
                clearCard(app);
                int ret = r.optInt("code", r.optInt("status", -1));
                if (ret == 0 || ret == 200) cb.onOk(r);
                else cb.onFail(r.optString("msg", "解绑完成"));
            });
        });
    }
}