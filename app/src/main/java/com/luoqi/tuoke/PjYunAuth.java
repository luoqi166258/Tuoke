package com.luoqi.tuoke;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;

/**
 * 泡椒云网络验证对接（严格对齐官方文档 http://docs.paojiaoyun.com/）。
 *
 * 接口：
 *   POST http://api.paojiaoyun.com/v1/card/login            卡密登录
 *   POST http://api.paojiaoyun.com/v1/card/heartbeat        心跳
 *   POST http://api.paojiaoyun.com/v1/card/logout           退出登录
 *   POST http://api.paojiaoyun.com/v1/card/unbind_device    解绑设备
 *
 * 请求格式：application/x-www-form-urlencoded（表单，不是 JSON）
 * 公共字段：app_key / card / device_id / nonce / timestamp / sign
 *   - nonce     <=36 位随机串（这里用 UUID 去掉横线，32 位）
 *   - timestamp 秒级 10 位整数，1 分钟内有效
 *   - sign      md5( method + host + path + sorted(k=v 用 & 拼接) + app_secret )
 *               host 为裸域名 api.paojiaoyun.com，拼接时不含 sign、不 urlencode
 *
 * 关键纪律：
 *   1. app_secret 只参与签名计算，绝不放进请求体。
 *   2. 签名用「原始值」，传输用 urlencode 后的值，最后把 sign 追加到表单末尾。
 *   3. 登录成功后 token 在 result.token，card 明文落盘供心跳/退出复用。
 */
public final class PjYunAuth {

    /** 裸域名，用于签名拼接。 */
    private static final String HOST = "api.paojiaoyun.com";
    /** 基址，用于实际发请求。 */
    private static final String BASE = "http://" + HOST;

    private static final String PATH_LOGIN  = "/v1/card/login";
    private static final String PATH_BEAT   = "/v1/card/heartbeat";
    private static final String PATH_LOGOUT = "/v1/card/logout";
    private static final String PATH_UNBIND = "/v1/card/unbind_device";

    private static final String SP_NAME  = "pj_auth";
    private static final String K_CARD   = "card";
    private static final String K_TOKEN  = "token";
    private static final String K_DEVICE = "device_id";

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private PjYunAuth() {}

    // ======================= 回调 =======================

    public interface Callback {
        /** result 为服务端返回的 result 对象（含 token / expires / hg 等）。 */
        void onOk(JSONObject result);

        void onFail(String message);
    }

    // ======================= 对外接口 =======================

    /** 卡密登录。成功后自动落盘 card 与 token。 */
    public static void login(Context ctx, String card, final Callback cb) {
        final Context app = ctx.getApplicationContext();
        final String c = card == null ? "" : card.trim();
        runAsync(cb, () -> {
            String nonce = newNonce();
            String ts = nowSeconds();
            String dev = deviceId(app);

            List<String[]> kv = new ArrayList<>();
            kv.add(new String[]{"app_key", appKey()});
            kv.add(new String[]{"card", c});
            kv.add(new String[]{"device_id", dev});
            kv.add(new String[]{"nonce", nonce});
            kv.add(new String[]{"timestamp", ts});

            String sign = sign("POST", PATH_LOGIN, kv);
            String body = buildBody(kv) + "&sign=" + enc(sign);

            JSONObject r = post(PATH_LOGIN, body);
            MAIN.post(() -> {
                String err = r.optString("__error", "");
                if (!err.isEmpty()) { cb.onFail(err); return; }

                int code = r.optInt("code", -1);
                if (code != 0) { cb.onFail(errMsg(r)); return; }

                JSONObject res = r.optJSONObject("result");
                if (res == null) { cb.onFail("返回数据异常：缺少 result"); return; }

                save(app, K_CARD, c);
                save(app, K_TOKEN, res.optString("token", ""));
                cb.onOk(res);
            });
        });
    }

    /** 心跳。interval 秒建议取服务端返回的 hg（默认 600）。 */
    public static void heartbeat(Context ctx, final Callback cb) {
        final Context app = ctx.getApplicationContext();
        runAsync(cb, () -> {
            String card = load(app, K_CARD);
            String token = load(app, K_TOKEN);
            String nonce = newNonce();
            String ts = nowSeconds();

            List<String[]> kv = new ArrayList<>();
            kv.add(new String[]{"app_key", appKey()});
            kv.add(new String[]{"card", card});
            kv.add(new String[]{"nonce", nonce});
            kv.add(new String[]{"timestamp", ts});
            kv.add(new String[]{"token", token});

            String sign = sign("POST", PATH_BEAT, kv);
            String body = buildBody(kv) + "&sign=" + enc(sign);

            JSONObject r = post(PATH_BEAT, body);
            MAIN.post(() -> {
                String err = r.optString("__error", "");
                if (!err.isEmpty()) { cb.onFail(err); return; }
                int code = r.optInt("code", -1);
                if (code != 0) { cb.onFail(errMsg(r)); return; }
                JSONObject res = r.optJSONObject("result");
                cb.onOk(res == null ? new JSONObject() : res);
            });
        });
    }

    /** 退出登录，并清理本地凭据。 */
    public static void logout(Context ctx, final Callback cb) {
        final Context app = ctx.getApplicationContext();
        runAsync(cb, () -> {
            String card = load(app, K_CARD);
            String token = load(app, K_TOKEN);
            String nonce = newNonce();
            String ts = nowSeconds();

            List<String[]> kv = new ArrayList<>();
            kv.add(new String[]{"app_key", appKey()});
            kv.add(new String[]{"card", card});
            kv.add(new String[]{"nonce", nonce});
            kv.add(new String[]{"timestamp", ts});
            kv.add(new String[]{"token", token});

            String sign = sign("POST", PATH_LOGOUT, kv);
            String body = buildBody(kv) + "&sign=" + enc(sign);

            JSONObject r = post(PATH_LOGOUT, body);
            MAIN.post(() -> {
                clear(app);
                String err = r.optString("__error", "");
                if (!err.isEmpty()) { cb.onFail("已本地清理（" + err + "）"); return; }
                int code = r.optInt("code", -1);
                if (code == 0) cb.onOk(r.optJSONObject("result"));
                else cb.onFail(errMsg(r));
            });
        });
    }

    /** 解绑设备（服务端解绑，成功后清空本地 token）。 */
    public static void unbind(Context ctx, final Callback cb) {
        final Context app = ctx.getApplicationContext();
        runAsync(cb, () -> {
            String card = load(app, K_CARD);
            String token = load(app, K_TOKEN);
            String dev = deviceId(app);
            String nonce = newNonce();
            String ts = nowSeconds();

            List<String[]> kv = new ArrayList<>();
            kv.add(new String[]{"app_key", appKey()});
            kv.add(new String[]{"card", card});
            kv.add(new String[]{"device_id", dev});
            kv.add(new String[]{"nonce", nonce});
            kv.add(new String[]{"timestamp", ts});
            kv.add(new String[]{"token", token});

            String sign = sign("POST", PATH_UNBIND, kv);
            String body = buildBody(kv) + "&sign=" + enc(sign);

            JSONObject r = post(PATH_UNBIND, body);
            MAIN.post(() -> {
                String err = r.optString("__error", "");
                if (!err.isEmpty()) { cb.onFail(err); return; }
                int code = r.optInt("code", -1);
                if (code == 0) {
                    save(app, K_TOKEN, "");
                    cb.onOk(r.optJSONObject("result"));
                } else {
                    cb.onFail(errMsg(r));
                }
            });
        });
    }

    // ======================= 签名与请求 =======================

    /**
     * 计算签名：md5( method + host + path + sorted_k=v拼接(&) + app_secret )
     * 拼接不含 sign、不 urlencode。
     */
    private static String sign(String method, String path, List<String[]> kv) {
        List<String[]> sorted = new ArrayList<>(kv);
        Collections.sort(sorted, (a, b) -> a[0].compareTo(b[0]));

        StringBuilder sb = new StringBuilder();
        sb.append(method.toUpperCase(Locale.ROOT));
        sb.append(HOST);
        sb.append(path);
        int n = 0;
        for (String[] p : sorted) {
            if ("sign".equals(p[0])) continue;
            if (n++ > 0) sb.append('&');
            sb.append(p[0]).append('=').append(p[1] == null ? "" : p[1]);
        }
        sb.append(appSecret());
        return md5(sb.toString());
    }

    /** 按 urlencode 拼装传输体（不含 sign）。 */
    private static String buildBody(List<String[]> kv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kv.size(); i++) {
            String[] p = kv.get(i);
            if (i > 0) sb.append('&');
            sb.append(enc(p[0])).append('=').append(enc(p[1] == null ? "" : p[1]));
        }
        return sb.toString();
    }

    private static JSONObject post(String path, String body) {
        HttpURLConnection conn = null;
        JSONObject out = new JSONObject();
        try {
            URL url = new URL(BASE + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            byte[] raw = body.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(raw);
                os.flush();
            }

            int http = conn.getResponseCode();
            java.io.InputStream is = (http >= 200 && http < 400)
                    ? conn.getInputStream() : conn.getErrorStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            out = new JSONObject(sb.toString());
            out.put("__http", http);
            return out;
        } catch (Exception e) {
            try {
                out.put("__http", -1);
                out.put("__error", "网络异常：" + e.getMessage());
            } catch (Exception ignored) {}
            return out;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // ======================= 工具方法 =======================

    /** 解析服务端错误信息，优先取 errs 字段级报错。 */
    private static String errMsg(JSONObject r) {
        int code = r.optInt("code", -1);
        String map = codeMap(code);
        String msg = r.optString("message", r.optString("msg", ""));

        JSONObject errs = r.optJSONObject("errs");
        if (errs != null && errs.length() > 0) {
            StringBuilder sb = new StringBuilder();
            Iterator<String> it = errs.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (sb.length() > 0) sb.append("；");
                sb.append(k).append(": ").append(errs.optString(k));
            }
            return "[" + code + "] " + map + "（" + sb + "）";
        }
        if (!msg.isEmpty()) return "[" + code + "] " + msg + (map.isEmpty() ? "" : "（" + map + "）");
        return "[" + code + "] " + (map.isEmpty() ? "请求失败" : map);
    }

    private static String codeMap(int code) {
        switch (code) {
            case 0:     return "成功";
            case 10010: return "无效签名";
            case 10011: return "签名已过期";
            case 10014: return "重复的 nonce";
            case 10210: return "卡密已过期";
            case 10218: return "卡密不可用";
            case 10230: return "软件不存在";
            default:    return "";
        }
    }

    /** 设备 ID：首次生成 TK-<ANDROID_ID> 并固化落盘。 */
    public static String deviceId(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String id = sp.getString(K_DEVICE, "");
        if (id != null && !id.isEmpty()) return id;

        String raw = "";
        try {
            raw = Settings.Secure.getString(ctx.getContentResolver(),
                    Settings.Secure.ANDROID_ID);
        } catch (Exception ignored) {}
        if (raw == null || raw.isEmpty()) {
            raw = UUID.randomUUID().toString().replace("-", "");
        }
        id = "TK-" + raw;
        sp.edit().putString(K_DEVICE, id).apply();
        return id;
    }

    private static String newNonce() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String nowSeconds() {
        return String.valueOf(System.currentTimeMillis() / 1000L);
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static void runAsync(final Callback cb, final Runnable task) {
        POOL.execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                final String m = t.getMessage() == null ? t.toString() : t.getMessage();
                MAIN.post(() -> cb.onFail("内部异常：" + m));
            }
        });
    }

    // ======================= 本地存储 =======================

    private static void save(Context ctx, String k, String v) {
        ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit().putString(k, v).apply();
    }

    private static String load(Context ctx, String k) {
        return ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).getString(k, "");
    }

    private static void clear(Context ctx) {
        ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit()
                .remove(K_CARD).remove(K_TOKEN).apply();
    }

    /** 是否已登录（本地有 token）。 */
    public static boolean isLoggedIn(Context ctx) {
        String t = load(ctx, K_TOKEN);
        return t != null && !t.isEmpty();
    }

    // ======================= 兼容旧调用方 =======================

    /** 读取本地已保存的卡密（登录页回填 / 状态展示）。 */
    public static String cardFromPrefs(Context ctx) {
        String v = load(ctx, K_CARD);
        return v == null ? "" : v;
    }

    /** 清空本地卡密与 token（心跳失效时回登录页）。 */
    public static void clearCard(Context ctx) {
        clear(ctx);
    }

    // ======================= 凭据（BuildConfig 注入） =======================

    /** 还原被拆分混淆的 AppKey。 */
    public static String appKey() {
        try {
            return joinParts(BuildConfig.PJ_KEY_PARTS);
        } catch (Throwable t) {
            return "";
        }
    }

    /** 还原被拆分混淆的 AppSecret（只用于签名，绝不外发）。 */
    public static String appSecret() {
        try {
            return joinParts(BuildConfig.PJ_SECRET_PARTS);
        } catch (Throwable t) {
            return "";
        }
    }

    private static String joinParts(String[] parts) {
        if (parts == null || parts.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append(p == null ? "" : p);
        return new StringBuilder(sb.toString()).reverse().toString();
    }
}
