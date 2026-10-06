package com.merahisab.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Tiny client for the Supabase server (PostgREST RPC). Blocking: call from a background thread. */
final class Api {
    private Api() {}
    private static String url = "", key = "";

    static void load(Context c) {
        url = ""; key = "";
        try {
            SharedPreferences p = c.getSharedPreferences("mh", Context.MODE_PRIVATE);
            url = p.getString("srvUrl", ""); key = p.getString("srvKey", "");
        } catch (Exception e) { /* ignore */ }
        if (url.isEmpty() || key.isEmpty()) {
            try {
                InputStream in = c.getAssets().open("server.txt");
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] b = new byte[1024]; int n;
                while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                in.close();
                parse(new String(bo.toByteArray(), StandardCharsets.UTF_8));
            } catch (Exception e) { /* no server file */ }
        }
    }

    /** Accepts "URL KEY" separated by spaces / new line / |. Returns true if both parts look right. */
    static boolean parse(String text) {
        String[] t = text.trim().split("[\\s|,]+");
        String u = "", k = "";
        for (String x : t) {
            if (x.startsWith("http")) u = x; else if (x.length() > 20) k = x;
        }
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (u.isEmpty() || k.isEmpty()) return false;
        url = u; key = k;
        return true;
    }

    static void save(Context c, String text) {
        if (text.trim().isEmpty()) {
            c.getSharedPreferences("mh", Context.MODE_PRIVATE).edit().remove("srvUrl").remove("srvKey").apply();
            load(c);
            return;
        }
        if (parse(text)) c.getSharedPreferences("mh", Context.MODE_PRIVATE).edit().putString("srvUrl", url).putString("srvKey", key).apply();
    }

    static boolean configured() { return !url.isEmpty() && !key.isEmpty(); }

    /** Calls a server function. Never throws: returns {ok:false, err:"net"} when offline. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> call(String fn, Map<String, Object> args) {
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("ok", false); bad.put("err", "net");
        if (!configured()) return bad;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url + "/rest/v1/rpc/" + fn).openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(40000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("apikey", key);
            if (key.startsWith("eyJ")) c.setRequestProperty("Authorization", "Bearer " + key);
            OutputStream o = c.getOutputStream();
            o.write(Json.stringify(args).getBytes(StandardCharsets.UTF_8));
            o.close();
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192]; int n;
            while (in != null && (n = in.read(b)) > 0) bo.write(b, 0, n);
            Object r = Json.parse(new String(bo.toByteArray(), StandardCharsets.UTF_8));
            if (code < 400 && r instanceof Map) return (Map<String, Object>) r;
            bad.put("err", "server");
            return bad;
        } catch (Exception e) {
            return bad;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    static String s(Map<String, Object> m, String k) { Object v = m.get(k); return v == null ? "" : String.valueOf(v); }
    static boolean ok(Map<String, Object> m) { return Boolean.TRUE.equals(m.get("ok")); }
}
