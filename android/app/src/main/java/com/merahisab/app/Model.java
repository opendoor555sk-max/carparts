package com.merahisab.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Data model + all hisab calculations. Same JSON shape as the web version so backups work both ways. */
public final class Model {
    private Model() {}

    public static final class TypeInfo {
        public final String key, label;
        public final boolean party;
        public final int cash, bal;
        public final String kind;
        TypeInfo(String key, String label, boolean party, int cash, int bal, String kind) {
            this.key = key; this.label = label; this.party = party; this.cash = cash; this.bal = bal; this.kind = kind;
        }
    }

    public static final String[] TYPE_ORDER = {"gave", "got", "took", "paid", "income", "expense"};
    private static final Map<String, TypeInfo> TYPES = new LinkedHashMap<>();
    static {
        TYPES.put("gave", new TypeInfo("gave", "ઉધાર વેચાણ", true, 0, +1, "customer"));
        TYPES.put("got", new TypeInfo("got", "પૈસા મળ્યા", true, +1, -1, "customer"));
        TYPES.put("took", new TypeInfo("took", "ઉધાર ખરીદી", true, 0, -1, "creditor"));
        TYPES.put("paid", new TypeInfo("paid", "પૈસા ચૂકવ્યા", true, -1, +1, "creditor"));
        TYPES.put("income", new TypeInfo("income", "આવક", false, +1, 0, "customer"));
        TYPES.put("expense", new TypeInfo("expense", "ખર્ચ", false, -1, 0, "creditor"));
    }
    public static TypeInfo type(String k) { return TYPES.get(k); }

    public static final class Party {
        public String id = "", name = "", phone = "", kind = "";
        public long created, upd;
    }

    public static final class Txn {
        public String id = "", type = "income", partyId = null, note = "", mode = "", cat = "", said = "", due = "", date = "";
        public double amount;
        public long ts, upd;
    }

    public static final class Product {
        public String id = "", name = "", unit = "";
        public double rate;
    }

    public static final class Settings {
        public String owner = "", shop = "", phone = "", addr = "", lang = "gu-IN", theme = "system", upiId = "", upiName = "",
                pinHash = "", bio = "", lastBackup = "", deviceId = "";
        public double openCash, openBank;
        public boolean productMode = false, speak = true;
    }

    /** A spoken entry waiting in the "In-Review" list until the user confirms it. */
    public static final class Pending {
        public String id = "", said = "", type = "gave", name = "", partyId = null, note = "", date = "", cat = "";
        public double amount;
        public long ts;
    }

    /** One reminder that was sent (history list). */
    public static final class RemLog {
        public String partyId = "", name = "";
        public double amount;
        public long ts;
    }

    /** One spoken sentence kept for voice testing: what was heard, what the app understood, and the user's verdict. */
    public static final class VLog {
        public String heard = "", alts = "", res = "", fix = "";
        public int ok; // 0 not rated, 1 correct, -1 wrong
        public long ts;
    }

    /** A spelling the speech engine produced that the app learned to map to a real khata name. */
    public static final class Learn {
        public String from = "", to = "";
        public long ts;
    }

    public static final class Totals { public double gave, took, inc, exp, got, paid; }
    public static final class Due { public double recv, pay; }
    public static final class Cash { public double cash, bank; }

    public static final class Db {
        public List<Party> parties = new ArrayList<>();
        public List<Txn> txns = new ArrayList<>();
        public List<Product> products = new ArrayList<>();
        public List<Pending> review = new ArrayList<>();
        public List<RemLog> remLog = new ArrayList<>();
        public List<VLog> vlog = new ArrayList<>();
        public List<Learn> learn = new ArrayList<>();
        public List<String[]> aliasPairs() {
            List<String[]> l = new ArrayList<>();
            for (Learn x : learn) l.add(new String[]{x.from, x.to});
            return l;
        }
        public Settings settings = new Settings();
        public long saved;

        public Party party(String id) {
            if (id == null) return null;
            for (Party p : parties) if (p.id.equals(id)) return p;
            return null;
        }

        public Product product(String id) {
            if (id == null) return null;
            for (Product p : products) if (p.id.equals(id)) return p;
            return null;
        }

        public Party findByName(String name) {
            String n = Parser.normText(name);
            for (Party p : parties) if (Parser.normText(p.name).equals(n)) return p;
            return null;
        }

        public double partyBal(String pid) {
            double b = 0;
            for (Txn t : txns) if (pid.equals(t.partyId)) b += TYPES.get(t.type).bal * t.amount;
            return b;
        }

        public String kindOf(Party p) {
            if (p.kind != null && !p.kind.isEmpty()) return p.kind;
            return partyBal(p.id) < 0 ? "creditor" : "customer";
        }

        public Cash cashBank() {
            Cash c = new Cash();
            c.cash = settings.openCash;
            c.bank = settings.openBank;
            for (Txn t : txns) {
                double e = TYPES.get(t.type).cash * t.amount;
                if (e == 0 || "writeoff".equals(t.mode)) continue;
                if ("upi".equals(t.mode)) c.bank += e; else c.cash += e;
            }
            return c;
        }

        public Totals monthTotals(String key) {
            Totals o = new Totals();
            for (Txn t : txns) {
                if (t.date.length() < 7 || !t.date.substring(0, 7).equals(key)) continue;
                boolean wo = "writeoff".equals(t.mode);
                switch (t.type) {
                    case "gave": o.gave += t.amount; break;
                    case "took": o.took += t.amount; break;
                    case "got": o.got += t.amount; if (!wo) o.inc += t.amount; break;
                    case "income": o.inc += t.amount; break;
                    case "paid": o.paid += t.amount; if (!wo) o.exp += t.amount; break;
                    case "expense": o.exp += t.amount; break;
                    default:
                }
            }
            return o;
        }

        public Totals dayTotals(String day) {
            Totals o = new Totals();
            for (Txn t : txns) {
                if (!day.equals(t.date)) continue;
                boolean wo = "writeoff".equals(t.mode);
                switch (t.type) {
                    case "gave": o.gave += t.amount; break;
                    case "took": o.took += t.amount; break;
                    case "got": o.got += t.amount; if (!wo) o.inc += t.amount; break;
                    case "income": o.inc += t.amount; break;
                    case "paid": o.paid += t.amount; if (!wo) o.exp += t.amount; break;
                    case "expense": o.exp += t.amount; break;
                    default:
                }
            }
            return o;
        }

        public Due totalsDue() {
            Due d = new Due();
            for (Party p : parties) {
                double b = partyBal(p.id);
                if (b > 0) d.recv += b; else if (b < 0) d.pay += -b;
            }
            return d;
        }

        /** Oldest credit entries are settled first; the rest with a due date before today is overdue. */
        public Due overdueFor(String pid, String today) {
            List<Txn> tx = new ArrayList<>();
            for (Txn t : txns) if (pid.equals(t.partyId)) tx.add(t);
            Collections.sort(tx, new Comparator<Txn>() {
                @Override public int compare(Txn a, Txn b) {
                    int c = a.date.compareTo(b.date);
                    return c != 0 ? c : Long.compare(a.ts, b.ts);
                }
            });
            Due res = new Due();
            String[][] pairs = {{"gave", "got", "recv"}, {"took", "paid", "pay"}};
            for (String[] c : pairs) {
                double settle = 0;
                for (Txn t : tx) if (t.type.equals(c[1])) settle += t.amount;
                for (Txn t : tx) {
                    if (!t.type.equals(c[0])) continue;
                    double rem = t.amount;
                    double use = Math.min(rem, settle);
                    rem -= use;
                    settle -= use;
                    if (rem > 0 && t.due != null && !t.due.isEmpty() && t.due.compareTo(today) < 0) {
                        if (c[2].equals("recv")) res.recv += rem; else res.pay += rem;
                    }
                }
            }
            return res;
        }

        public Due overdueTotals(String today) {
            Due o = new Due();
            for (Party p : parties) {
                Due x = overdueFor(p.id, today);
                o.recv += x.recv;
                o.pay += x.pay;
            }
            return o;
        }

        public List<Txn> sorted() {
            List<Txn> l = new ArrayList<>(txns);
            Collections.sort(l, SORT_NEWEST);
            return l;
        }

        public List<Txn> partyEntries(String pid) {
            List<Txn> l = new ArrayList<>();
            for (Txn t : txns) if (pid.equals(t.partyId)) l.add(t);
            Collections.sort(l, SORT_NEWEST);
            return l;
        }

        // ---------- JSON ----------
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            List<Object> ps = new ArrayList<>();
            for (Party p : parties) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("id", p.id); x.put("name", p.name); x.put("phone", p.phone); x.put("kind", p.kind);
                x.put("created", (double) p.created); x.put("upd", (double) p.upd);
                ps.add(x);
            }
            List<Object> ts = new ArrayList<>();
            for (Txn t : txns) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("id", t.id); x.put("ts", (double) t.ts); x.put("upd", (double) t.upd); x.put("date", t.date);
                x.put("type", t.type); x.put("partyId", t.partyId); x.put("amount", t.amount); x.put("note", t.note);
                x.put("mode", t.mode); x.put("cat", t.cat); x.put("said", t.said); x.put("due", t.due);
                ts.add(x);
            }
            List<Object> pr = new ArrayList<>();
            for (Product p : products) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("id", p.id); x.put("name", p.name); x.put("rate", p.rate); x.put("unit", p.unit);
                pr.add(x);
            }
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("owner", settings.owner); s.put("shop", settings.shop); s.put("phone", settings.phone); s.put("addr", settings.addr);
            s.put("lang", settings.lang); s.put("theme", settings.theme); s.put("openCash", settings.openCash); s.put("openBank", settings.openBank);
            s.put("upiId", settings.upiId); s.put("upiName", settings.upiName); s.put("productMode", settings.productMode);
            s.put("speak", settings.speak); s.put("pinHash", settings.pinHash); s.put("bio", settings.bio);
            s.put("lastBackup", settings.lastBackup); s.put("deviceId", settings.deviceId);
            List<Object> rv = new ArrayList<>();
            for (Pending q : review) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("id", q.id); x.put("said", q.said); x.put("type", q.type); x.put("name", q.name); x.put("partyId", q.partyId);
                x.put("note", q.note); x.put("date", q.date); x.put("cat", q.cat); x.put("amount", q.amount); x.put("ts", (double) q.ts);
                rv.add(x);
            }
            List<Object> rl = new ArrayList<>();
            for (RemLog q : remLog) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("partyId", q.partyId); x.put("name", q.name); x.put("amount", q.amount); x.put("ts", (double) q.ts);
                rl.add(x);
            }
            List<Object> vl = new ArrayList<>();
            for (VLog q : vlog) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("heard", q.heard); x.put("alts", q.alts); x.put("res", q.res); x.put("fix", q.fix); x.put("ok", (double) q.ok); x.put("ts", (double) q.ts);
                vl.add(x);
            }
            List<Object> ln = new ArrayList<>();
            for (Learn q : learn) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("from", q.from); x.put("to", q.to); x.put("ts", (double) q.ts);
                ln.add(x);
            }
            m.put("review", rv); m.put("remlog", rl); m.put("vlog", vl); m.put("learn", ln);
            m.put("parties", ps); m.put("txns", ts); m.put("products", pr); m.put("settings", s); m.put("saved", (double) saved);
            return m;
        }

        @SuppressWarnings("unchecked")
        public void fromMap(Map<String, Object> o) {
            parties = new ArrayList<>();
            txns = new ArrayList<>();
            products = new ArrayList<>();
            review = new ArrayList<>();
            remLog = new ArrayList<>();
            vlog = new ArrayList<>();
            learn = new ArrayList<>();
            settings = new Settings();
            Object lno = o.get("learn");
            if (lno instanceof List) for (Object x : (List<Object>) lno) {
                Map<String, Object> m = (Map<String, Object>) x;
                Learn q = new Learn();
                q.from = str(m, "from"); q.to = str(m, "to"); q.ts = (long) num(m, "ts");
                if (!q.from.isEmpty() && !q.to.isEmpty()) learn.add(q);
            }
            Object vlo = o.get("vlog");
            if (vlo instanceof List) for (Object x : (List<Object>) vlo) {
                Map<String, Object> m = (Map<String, Object>) x;
                VLog q = new VLog();
                q.heard = str(m, "heard"); q.alts = str(m, "alts"); q.res = str(m, "res"); q.fix = str(m, "fix"); q.ok = (int) num(m, "ok"); q.ts = (long) num(m, "ts");
                vlog.add(q);
            }
            Object rvo = o.get("review");
            if (rvo instanceof List) for (Object x : (List<Object>) rvo) {
                Map<String, Object> m = (Map<String, Object>) x;
                Pending q = new Pending();
                q.id = str(m, "id"); q.said = str(m, "said"); q.type = str(m, "type"); q.name = str(m, "name");
                q.partyId = m.get("partyId") instanceof String ? (String) m.get("partyId") : null;
                q.note = str(m, "note"); q.date = str(m, "date"); q.cat = str(m, "cat"); q.amount = num(m, "amount"); q.ts = (long) num(m, "ts");
                if (TYPES.containsKey(q.type)) review.add(q);
            }
            Object rlo = o.get("remlog");
            if (rlo instanceof List) for (Object x : (List<Object>) rlo) {
                Map<String, Object> m = (Map<String, Object>) x;
                RemLog q = new RemLog();
                q.partyId = str(m, "partyId"); q.name = str(m, "name"); q.amount = num(m, "amount"); q.ts = (long) num(m, "ts");
                remLog.add(q);
            }
            Object po = o.get("parties");
            if (po instanceof List) for (Object x : (List<Object>) po) {
                Map<String, Object> m = (Map<String, Object>) x;
                Party p = new Party();
                p.id = str(m, "id"); p.name = str(m, "name"); p.phone = str(m, "phone"); p.kind = str(m, "kind");
                p.created = (long) num(m, "created"); p.upd = (long) num(m, "upd");
                parties.add(p);
            }
            Object to = o.get("txns");
            if (to instanceof List) for (Object x : (List<Object>) to) {
                Map<String, Object> m = (Map<String, Object>) x;
                Txn t = new Txn();
                t.id = str(m, "id"); t.ts = (long) num(m, "ts"); t.upd = (long) num(m, "upd"); t.date = str(m, "date");
                t.type = str(m, "type"); t.partyId = m.get("partyId") instanceof String ? (String) m.get("partyId") : null;
                t.amount = num(m, "amount"); t.note = str(m, "note"); t.mode = str(m, "mode"); t.cat = str(m, "cat");
                t.said = str(m, "said"); t.due = str(m, "due");
                if (!TYPES.containsKey(t.type)) continue;
                txns.add(t);
            }
            Object ro = o.get("products");
            if (ro instanceof List) for (Object x : (List<Object>) ro) {
                Map<String, Object> m = (Map<String, Object>) x;
                Product p = new Product();
                p.id = str(m, "id"); p.name = str(m, "name"); p.rate = num(m, "rate"); p.unit = str(m, "unit");
                products.add(p);
            }
            Object so = o.get("settings");
            if (so instanceof Map) {
                Map<String, Object> m = (Map<String, Object>) so;
                Settings s = settings;
                s.owner = str(m, "owner"); s.shop = str(m, "shop"); s.phone = str(m, "phone"); s.addr = str(m, "addr");
                if (!str(m, "lang").isEmpty()) s.lang = str(m, "lang");
                if (!str(m, "theme").isEmpty()) s.theme = str(m, "theme");
                s.openCash = num(m, "openCash"); s.openBank = num(m, "openBank");
                s.upiId = str(m, "upiId"); s.upiName = str(m, "upiName");
                s.productMode = Boolean.TRUE.equals(m.get("productMode"));
                s.speak = !Boolean.FALSE.equals(m.get("speak"));
                s.pinHash = str(m, "pinHash"); s.bio = str(m, "bio"); s.lastBackup = str(m, "lastBackup"); s.deviceId = str(m, "deviceId");
            }
            saved = (long) num(o, "saved");
        }

        @SuppressWarnings("unchecked")
        public boolean loadJson(String text) {
            try {
                Object o = Json.parse(text);
                if (!(o instanceof Map)) return false;
                Map<String, Object> m = (Map<String, Object>) o;
                // accept backup wrapper {app, v, data}
                if (m.get("data") instanceof Map && !m.containsKey("txns")) m = (Map<String, Object>) m.get("data");
                if (!(m.get("txns") instanceof List) && !(m.get("parties") instanceof List)) return false;
                fromMap(m);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        public String toJson() { return Json.stringify(toMap()); }

        public String backupJson(String nowIso) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("app", "merahisab"); m.put("v", 1.0); m.put("saved", nowIso); m.put("data", toMap());
            return Json.stringify(m);
        }
    }

    public static final Comparator<Txn> SORT_NEWEST = new Comparator<Txn>() {
        @Override public int compare(Txn a, Txn b) {
            if (a.date.equals(b.date)) return Long.compare(b.ts, a.ts);
            return a.date.compareTo(b.date) < 0 ? 1 : -1;
        }
    };

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof String ? (String) v : "";
    }

    private static double num(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) { try { return Double.parseDouble((String) v); } catch (Exception e) { return 0; } }
        return 0;
    }
}
