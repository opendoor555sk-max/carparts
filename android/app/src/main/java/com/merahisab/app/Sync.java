package com.merahisab.app;

import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Login session + two-way sync of the local data with the server (one business, many users). */
final class Sync {
    interface Done { void done(boolean ok, String err); }

    private static final String[][] LISTS = {{"parties", "party"}, {"txns", "txn"}, {"products", "product"}, {"review", "review"}, {"learn", "learn"}};
    private static final String[] SHARED = {"shop", "phone", "addr", "biz", "bizCat", "upiId", "upiName", "productMode", "openCash", "openBank"};

    private final MainActivity a;
    private final File file;
    private final Handler h = new Handler(Looper.getMainLooper());
    String token = "", role = "", name = "", phone = "", bizId = "", bizName = "";
    boolean admin = false;
    long since = 0, lastOk = 0;
    private final Map<String, String> synced = new HashMap<>();
    private boolean running = false, again = false;
    private final List<Done> waiting = new ArrayList<>();
    String lastErr = "";
    private final Runnable tick = new Runnable() { @Override public void run() { run(null); } };

    Sync(MainActivity a) {
        this.a = a;
        this.file = new File(a.getFilesDir(), "sync.json");
        load();
    }

    boolean loggedIn() { return !token.isEmpty(); }
    boolean isOwner() { return !loggedIn() || role.equals("owner"); }
    boolean isStaff() { return loggedIn() && role.equals("staff"); }
    boolean isAdmin() { return loggedIn() && admin; }

    @SuppressWarnings("unchecked")
    private void load() {
        try {
            if (!file.exists()) return;
            java.io.InputStream in = new java.io.FileInputStream(file);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            in.close();
            Map<String, Object> m = (Map<String, Object>) Json.parse(new String(bo.toByteArray(), StandardCharsets.UTF_8));
            token = str(m, "token"); role = str(m, "role"); name = str(m, "name"); phone = str(m, "phone");
            bizId = str(m, "biz"); bizName = str(m, "bizName"); admin = Boolean.TRUE.equals(m.get("admin"));
            since = (long) num(m.get("since")); lastOk = (long) num(m.get("lastOk"));
            Object sy = m.get("synced");
            if (sy instanceof Map) for (Map.Entry<String, Object> e : ((Map<String, Object>) sy).entrySet()) synced.put(e.getKey(), String.valueOf(e.getValue()));
        } catch (Exception e) { /* start clean */ }
    }

    private void persist() {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("token", token); m.put("role", role); m.put("name", name); m.put("phone", phone); m.put("biz", bizId);
            m.put("bizName", bizName); m.put("admin", admin); m.put("since", (double) since); m.put("lastOk", (double) lastOk);
            m.put("synced", new LinkedHashMap<String, Object>(synced));
            File t = new File(a.getFilesDir(), "sync.json.tmp");
            FileOutputStream o = new FileOutputStream(t);
            o.write(Json.stringify(m).getBytes(StandardCharsets.UTF_8));
            o.close();
            if (file.exists()) file.delete();
            t.renameTo(file);
        } catch (Exception e) { /* ignore */ }
    }

    /** Called after a successful login. */
    void setSession(Map<String, Object> r) {
        String newBiz = Api.s(r, "biz");
        if (!newBiz.equals(bizId)) { since = 0; synced.clear(); }
        token = Api.s(r, "token"); role = Api.s(r, "role"); name = Api.s(r, "name"); phone = Api.s(r, "phone");
        bizId = newBiz; bizName = Api.s(r, "biz_name"); admin = Boolean.TRUE.equals(r.get("is_admin"));
        persist();
    }

    void clear() {
        h.removeCallbacks(tick);
        token = ""; role = ""; name = ""; phone = ""; bizId = ""; bizName = ""; admin = false; since = 0; lastOk = 0;
        synced.clear();
        persist();
    }

    void kick() {
        if (!loggedIn()) return;
        h.removeCallbacks(tick);
        h.postDelayed(tick, 2500);
    }

    // ---------- record helpers ----------
    @SuppressWarnings("unchecked")
    private static Object canon(Object o) {
        if (o instanceof Map) {
            TreeMap<String, Object> t = new TreeMap<>();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) t.put(e.getKey(), canon(e.getValue()));
            return t;
        }
        if (o instanceof List) {
            List<Object> l = new ArrayList<>();
            for (Object x : (List<Object>) o) l.add(canon(x));
            return l;
        }
        return o;
    }

    private static String hash(Object data) {
        try {
            byte[] d = MessageDigest.getInstance("MD5").digest(Json.stringify(canon(data)).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return String.valueOf(Json.stringify(data).hashCode()); }
    }

    private static String str(Map<String, Object> m, String k) { Object v = m.get(k); return v instanceof String ? (String) v : ""; }
    private static double num(Object v) { return v instanceof Number ? ((Number) v).doubleValue() : 0; }

    private static String idOf(String kind, Map<String, Object> m) { return kind.equals("learn") ? str(m, "from") : str(m, "id"); }

    @SuppressWarnings("unchecked")
    private static Map<String, Object[]> collect(Map<String, Object> dbMap) {
        Map<String, Object[]> out = new LinkedHashMap<>();
        for (String[] l : LISTS) {
            Object lo = dbMap.get(l[0]);
            if (!(lo instanceof List)) continue;
            for (Object x : (List<Object>) lo) {
                Map<String, Object> m = (Map<String, Object>) x;
                String id = idOf(l[1], m);
                if (id.isEmpty()) continue;
                out.put(l[1] + "|" + id, new Object[]{l[1], id, m});
            }
        }
        Object so = dbMap.get("settings");
        if (so instanceof Map) {
            Map<String, Object> sm = (Map<String, Object>) so, sh = new LinkedHashMap<>();
            for (String k : SHARED) sh.put(k, sm.get(k));
            out.put("settings|settings", new Object[]{"settings", "settings", sh});
        }
        return out;
    }

    // ---------- the sync itself ----------
    void run(Done cb) { run(cb, since == 0 && synced.isEmpty()); }

    @SuppressWarnings("unchecked")
    private void run(final Done cb, final boolean pullOnly) {
        if (!loggedIn() || !Api.configured()) { if (cb != null) cb.done(true, ""); return; }
        if (running) { again = true; if (cb != null) waiting.add(cb); return; }
        running = true;
        if (cb != null) waiting.add(cb);
        final List<Object> push = new ArrayList<>();
        final Map<String, String> pushedHash = new HashMap<>();
        final Set<String> pushedDel = new HashSet<>();
        if (!pullOnly) {
            long now = System.currentTimeMillis();
            Map<String, Object[]> cur = collect(a.db.toMap());
            for (Map.Entry<String, Object[]> e : cur.entrySet()) {
                String kind = (String) e.getValue()[0];
                if (isStaff() && kind.equals("settings")) continue;
                String hs = hash(e.getValue()[2]);
                if (hs.equals(synced.get(e.getKey()))) continue;
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("kind", kind); r.put("id", e.getValue()[1]); r.put("data", e.getValue()[2]); r.put("upd", (double) now); r.put("del", false);
                push.add(r);
                pushedHash.put(e.getKey(), hs);
            }
            if (!isStaff()) for (String k : synced.keySet()) {
                if (cur.containsKey(k)) continue;
                int p = k.indexOf('|');
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("kind", k.substring(0, p)); r.put("id", k.substring(p + 1)); r.put("data", new LinkedHashMap<String, Object>());
                r.put("upd", (double) now); r.put("del", true);
                push.add(r);
                pushedDel.add(k);
            }
        }
        final String tok = token;
        final long startSince = since;
        new Thread(() -> {
            String err = null;
            List<Object> rows = new ArrayList<>();
            long mx = startSince;
            for (int i = 0; i < push.size() && err == null; i += 150) {
                List<Object> chunk = new ArrayList<>(push.subList(i, Math.min(push.size(), i + 150)));
                Map<String, Object> r = Api.call("mh_sync", Api.args("p_tok", tok, "p_since", (double) startSince, "p_push", chunk));
                if (!Api.ok(r)) err = Api.s(r, "err");
            }
            if (err == null) {
                long cur = startSince;
                boolean more;
                do {
                    Map<String, Object> r = Api.call("mh_sync", Api.args("p_tok", tok, "p_since", (double) cur, "p_push", new ArrayList<Object>()));
                    if (!Api.ok(r)) { err = Api.s(r, "err"); break; }
                    Object ro = r.get("rows");
                    if (ro instanceof List) rows.addAll((List<Object>) ro);
                    cur = (long) num(r.get("max"));
                    more = Boolean.TRUE.equals(r.get("more"));
                } while (more);
                mx = cur;
            }
            final String ferr = err;
            final long fmx = mx;
            a.runOnUiThread(() -> finish(ferr, rows, fmx, pushedHash, pushedDel, pullOnly));
        }).start();
    }

    private void finish(String err, List<Object> rows, long mx, Map<String, String> pushedHash, Set<String> pushedDel, boolean pullOnly) {
        running = false;
        if (err != null) {
            lastErr = err;
            List<Done> w = new ArrayList<>(waiting);
            waiting.clear();
            if (err.equals("auth")) { a.onSessionExpired(); }
            for (Done d : w) d.done(false, err);
            return;
        }
        boolean changed = applyRows(rows);
        for (Map.Entry<String, String> e : pushedHash.entrySet()) synced.put(e.getKey(), e.getValue());
        for (String k : pushedDel) synced.remove(k);
        since = mx;
        lastOk = System.currentTimeMillis();
        lastErr = "";
        persist();
        if (changed) { a.saveLocalOnly(); a.render(); }
        if (pullOnly) { run(null, false); return; }
        List<Done> w = new ArrayList<>(waiting);
        waiting.clear();
        for (Done d : w) d.done(true, "");
        if (again) { again = false; h.postDelayed(tick, 800); }
    }

    @SuppressWarnings("unchecked")
    private boolean applyRows(List<Object> rows) {
        if (rows.isEmpty()) return false;
        Map<String, Object> m = a.db.toMap();
        boolean changed = false;
        Set<String> touched = new HashSet<>();
        for (Object ro : rows) {
            Map<String, Object> r = (Map<String, Object>) ro;
            String kind = str(r, "kind"), id = str(r, "id");
            boolean del = Boolean.TRUE.equals(r.get("del"));
            Object d = r.get("data");
            touched.add(kind + "|" + id);
            if (kind.equals("settings")) {
                if (del || !(d instanceof Map) || !(m.get("settings") instanceof Map)) continue;
                Map<String, Object> sm = (Map<String, Object>) m.get("settings"), dm = (Map<String, Object>) d;
                for (String k : SHARED) {
                    if (!dm.containsKey(k)) continue;
                    if (!Json.stringify(canon(dm.get(k))).equals(Json.stringify(canon(sm.get(k))))) { sm.put(k, dm.get(k)); changed = true; }
                }
                continue;
            }
            String listName = null;
            for (String[] l : LISTS) if (l[1].equals(kind)) listName = l[0];
            if (listName == null || !(m.get(listName) instanceof List)) continue;
            List<Object> list = (List<Object>) m.get(listName);
            int idx = -1;
            for (int i = 0; i < list.size(); i++) if (idOf(kind, (Map<String, Object>) list.get(i)).equals(id)) { idx = i; break; }
            if (del) { if (idx >= 0) { list.remove(idx); changed = true; } continue; }
            if (!(d instanceof Map)) continue;
            if (idx < 0) { list.add(d); changed = true; }
            else if (!hash(list.get(idx)).equals(hash(d))) { list.set(idx, d); changed = true; }
        }
        if (changed) a.db.fromMap(m);
        Map<String, Object[]> cur = collect(a.db.toMap());
        for (String k : touched) {
            Object[] c = cur.get(k);
            if (c == null) synced.remove(k); else synced.put(k, hash(c[2]));
        }
        return changed;
    }

    String lastText() {
        if (lastOk == 0) return "";
        return new java.text.SimpleDateFormat("dd MMM, HH:mm", java.util.Locale.US).format(new java.util.Date(lastOk));
    }
}
