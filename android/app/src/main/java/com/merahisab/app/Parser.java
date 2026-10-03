package com.merahisab.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Hindi / Gujarati / Hinglish voice command parser (port of the web version). */
public final class Parser {
    private Parser() {}

    public static final class Result {
        public String kind;       // entry | query | unknown | empty
        public String type;       // entry type
        public Double amount;
        public String name;
        public String partyId;
        public int dateOffset;
        public String what;       // party | expense | all
        public String scope;      // day | yesterday | month
    }

    private static final String GDIG = "૦૧૨૩૪૫૬૭૮૯", DDIG = "०१२३४५६७८९";

    private static List<String> L(String... a) { return Arrays.asList(a); }

    private static final List<String> EXP = L("kharch", "kharcha", "ખર્ચ", "ખર્ચો", "खर्च", "खर्चा", "expense");
    private static final List<String> GOT = L("mile", "mila", "mili", "aaya", "aaye", "aayi", "jama", "jamaa", "wapas", "vapas", "received", "receive", "prapt",
            "મળ્યા", "મળ્યો", "મળ્યું", "મળી", "આવ્યા", "આવ્યો", "આવ્યું", "જમા", "પાછા", "પરત",
            "मिले", "मिला", "मिली", "आए", "आया", "आई", "जमा", "वापस");
    private static final List<String> TOOK = L("liya", "liye", "lidha", "lidhu", "khareeda", "kharida", "kharid", "kharidi",
            "લીધા", "લીધો", "લીધું", "લીધી", "ખરીદ્યા", "ખરીદ્યો", "ખરીદ્યું", "ખરીદી",
            "लिया", "लिए", "ली", "खरीदा", "खरीदे", "खरीद");
    private static final List<String> PAID = L("chukavya", "chukaya", "chukaye", "chukav", "paid", "bhugtan",
            "ચૂકવ્યા", "ચૂકવ્યો", "ચૂકવી", "ચુકવ્યા", "ભરપાઈ", "चुकाया", "चुकाए", "चुकाई", "भुगतान");
    private static final List<String> GIVE = L("diya", "diye", "di", "dia", "aapya", "aapyo", "apya", "sold", "becha", "beche",
            "આપ્યા", "આપ્યો", "આપ્યું", "આપી", "દીધા", "દીધો", "દીધું", "વેચ્યા", "વેચ્યો", "વેચ્યું",
            "दिया", "दिए", "दी", "बेचा", "बेचे");
    private static final List<String> GOODS = L("maal", "mal", "bhada", "bhade", "bhadu", "udhar", "udhaar", "uthar", "udhari", "pedal",
            "માલ", "ભાડે", "ભાડું", "ભાડા", "ઉધાર", "उधार", "माल", "भाड़ा", "भाड़े", "किराया");
    private static final List<String> MONEY = L("paisa", "paise", "payment", "rakam", "rupiya", "rupaye", "rupees", "rs",
            "પૈસા", "રૂપિયા", "રકમ", "પેમેન્ટ", "पैसा", "पैसे", "रुपये", "रुपया", "रुपए", "पेमेंट");
    private static final List<String> HISAB = L("hisab", "hisaab", "hishab", "khata", "khaata", "statement", "detail", "baki", "baaki",
            "હિસાબ", "ખાતું", "ખાતા", "ડિટેલ", "બાકી", "हिसाब", "खाता", "डिटेल", "बाकी");
    private static final List<String> SHOW = L("batao", "bata", "bataiye", "dikhao", "dikha", "show", "kitna", "kitne", "jano", "janvu",
            "બતાવો", "બતાવ", "બોલો", "કહો", "કેટલા", "કેટલું", "જણાવો", "बताओ", "बताइए", "बता", "दिखाओ", "कितना", "कितने");
    private static final List<String> MULT = L("hajar", "hazar", "hazaar", "હજાર", "हजार", "sau", "સો", "सौ", "lakh", "laakh", "લાખ", "लाख");
    private static final List<String> TIME = L("aaj", "kal", "આજે", "આજ", "आज", "કાલે", "કાલ", "ગઈકાલે", "कल", "mahina", "mahine", "મહિનો", "મહિના", "महीने", "महीना", "is", "iss", "આ", "इस");
    private static final List<String> FILL = L("mujhe", "mene", "maine", "me", "mein", "ma", "ek", "koi", "sab", "saara", "sara", "બધા", "બધું", "सब", "सारा", "ka", "ki", "ke", "ko", "se", "ne", "ni", "no", "na", "nu", "ne");
    private static final List<List<String>> ANYKW = L2(EXP, GOT, TOOK, PAID, GIVE, GOODS, MONEY, HISAB, SHOW, MULT, TIME);

    @SafeVarargs
    private static List<List<String>> L2(List<String>... a) { return Arrays.asList(a); }

    private static final List<String> PARTICLES = L("ko", "se", "ne", "ka", "ki", "ke", "ni", "no", "nu", "ને", "થી", "નો", "ની", "ના", "નું", "પાસે", "પાસેથી", "को", "से", "ने", "का", "की", "के", "पास");
    private static final List<String> YESTERDAY = L("kal", "કાલે", "કાલ", "ગઈકાલે", "कल");
    private static final List<String> TODAY = L("aaj", "આજે", "આજ", "आज");
    private static final List<String> YES_WORDS = L("haan", "han", "ha", "haa", "sahi", "save", "yes", "ok", "okay", "હા", "સાચું", "સેવ", "हाँ", "हां", "हा", "सही", "सेव");
    private static final List<String> NO_WORDS = L("na", "nahi", "nahin", "no", "cancel", "ના", "નહીં", "નથી", "ન", "नहीं", "ना", "नही", "गलत");
    private static final String[] SUFFIXES = {"ને", "થી", "નો", "ની", "ના", "નું"};

    private static final Map<String, Integer> NUMWORDS = new HashMap<>();
    private static final Map<String, Integer> MULTVAL = new HashMap<>();

    private static void add(int v, String... ws) { for (String w : ws) NUMWORDS.put(w, v); }

    static {
        add(1, "ek", "એક", "एक"); add(2, "do", "be", "બે", "दो"); add(3, "teen", "tran", "ત્રણ", "तीन");
        add(4, "chaar", "char", "ચાર", "चार"); add(5, "paanch", "panch", "પાંચ", "पांच", "पाँच");
        add(6, "chhe", "che", "chah", "છ", "छह", "छः"); add(7, "saat", "sat", "સાત", "सात");
        add(8, "aath", "ath", "આઠ", "आठ"); add(9, "nau", "nav", "નવ", "नौ");
        add(10, "das", "dus", "દસ", "दस"); add(15, "pandrah", "pandar", "પંદર", "पंद्रह");
        add(20, "bees", "vees", "વીસ", "बीस"); add(25, "pachchis", "pachis", "પચીસ", "पच्चीस");
        add(30, "tees", "tris", "ત્રીસ", "तीस"); add(40, "chalis", "chaalis", "ચાલીસ", "चालीस");
        add(50, "pachas", "pachaas", "પચાસ", "पचास"); add(60, "saath", "sath", "સાઠ", "साठ");
        add(70, "sattar", "sitter", "સિત્તેર", "सत्तर"); add(80, "assi", "ashi", "એંસી", "अस्सी");
        add(90, "nabbe", "nevu", "નેવું", "नब्बे");
        for (String w : L("hajar", "hazar", "hazaar", "હજાર", "हजार")) MULTVAL.put(w, 1000);
        for (String w : L("sau", "સો", "सौ")) MULTVAL.put(w, 100);
        for (String w : L("lakh", "laakh", "લાખ", "लाख")) MULTVAL.put(w, 100000);
    }

    private static boolean kwMatch(String t, String k) { return t.equals(k) || (k.length() >= 4 && t.startsWith(k)); }

    private static boolean isIn(String t, List<String> list) {
        for (String k : list) if (kwMatch(t, k)) return true;
        return false;
    }

    private static boolean hasKw(List<String> tokens, List<String> list) {
        for (String t : tokens) if (isIn(t, list)) return true;
        return false;
    }

    private static boolean isNum(String t) { return t.matches("\\d+(\\.\\d+)?"); }

    private static boolean isAnyKw(String t) {
        for (List<String> l : ANYKW) if (isIn(t, l)) return true;
        return false;
    }

    public static String normText(String s) {
        s = s == null ? "" : s.toLowerCase();
        s = Normalizer.normalize(s, Normalizer.Form.NFC);
        s = s.replace("़", "");
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            int g = GDIG.indexOf(c), d = DDIG.indexOf(c);
            if (g >= 0) b.append((char) ('0' + g));
            else if (d >= 0) b.append((char) ('0' + d));
            else b.append(c);
        }
        s = b.toString();
        s = s.replaceAll("(\\d),(?=\\d)", "$1");
        s = s.replace("₹", " ");
        s = s.replaceAll("(?<!\\d)\\.|\\.(?!\\d)", " ").replaceAll("[,!?;:()\"'।]", " ");
        s = s.replaceAll("(\\d)([^\\d\\s.])", "$1 $2").replaceAll("([^\\d\\s.])(\\d)", "$1 $2");
        return s.replaceAll("\\s+", " ").trim();
    }

    private static List<String> splitSuffix(String t) {
        List<String> r = new ArrayList<>();
        if (isNum(t) || isAnyKw(t) || PARTICLES.contains(t)) { r.add(t); return r; }
        for (String suf : SUFFIXES) {
            if (t.endsWith(suf) && t.length() >= suf.length() + 2) {
                r.add(t.substring(0, t.length() - suf.length()));
                r.add(suf);
                return r;
            }
        }
        r.add(t);
        return r;
    }

    public static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        for (String t : normText(text).split(" ")) {
            if (t.isEmpty()) continue;
            out.addAll(splitSuffix(t));
        }
        return out;
    }

    private static Double numVal(String t) {
        if (t == null) return null;
        if (isNum(t)) return Double.valueOf(t);
        Integer v = NUMWORDS.get(t);
        return v == null ? null : Double.valueOf(v);
    }

    public static Double parseAmount(List<String> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            Double first = numVal(tokens.get(i));
            if (first == null) continue;
            String nxt = i + 1 < tokens.size() ? tokens.get(i + 1) : "";
            boolean hasMult = MULTVAL.containsKey(nxt);
            if (!isNum(tokens.get(i)) && !hasMult && first < 10) continue;
            int pos = i, guard = 0;
            double total = 0;
            boolean prevMult = true;
            while (pos < tokens.size() && guard++ < 6) {
                Double v = numVal(tokens.get(pos));
                if (v == null || !prevMult) break;
                Integer m = pos + 1 < tokens.size() ? MULTVAL.get(tokens.get(pos + 1)) : null;
                if (m != null) { total += v * m; pos += 2; prevMult = true; }
                else { total += v; pos += 1; prevMult = false; }
            }
            if (prevMult && pos < tokens.size()) {
                Double v = numVal(tokens.get(pos));
                if (v != null && v < 100) total += v;
            }
            return total;
        }
        return null;
    }

    private static boolean isNameStop(String t) {
        return numVal(t) != null || isAnyKw(t) || isIn(t, FILL) || PARTICLES.contains(t);
    }

    private static String extractName(List<String> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            if (!PARTICLES.contains(tokens.get(i))) continue;
            List<String> cand = new ArrayList<>();
            for (int j = i - 1; j >= 0 && cand.size() < 3; j--) {
                if (isNameStop(tokens.get(j))) break;
                cand.add(0, tokens.get(j));
            }
            if (!cand.isEmpty()) return String.join(" ", cand);
        }
        return null;
    }

    public static String prettyName(String n) {
        if (n == null) return null;
        StringBuilder b = new StringBuilder();
        for (String w : n.split(" ")) {
            if (b.length() > 0) b.append(' ');
            if (!w.isEmpty() && w.charAt(0) >= 'a' && w.charAt(0) <= 'z') b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            else b.append(w);
        }
        return b.toString();
    }

    public static int lev(String a, String b) {
        int m = a.length(), n = b.length();
        if (m == 0) return n;
        if (n == 0) return m;
        int[] prev = new int[n + 1];
        for (int j = 0; j <= n; j++) prev[j] = j;
        for (int i = 1; i <= m; i++) {
            int[] cur = new int[n + 1];
            cur[0] = i;
            for (int j = 1; j <= n; j++) {
                cur[j] = Math.min(Math.min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            }
            prev = cur;
        }
        return prev[n];
    }

    private static String firstWord(String s) {
        int k = s.indexOf(' ');
        return k < 0 ? s : s.substring(0, k);
    }

    private static Model.Party matchParty(List<String> tokens, List<Model.Party> parties, String candName) {
        String joined = " " + String.join(" ", tokens) + " ";
        Model.Party best = null;
        for (Model.Party p : parties) {
            String n = normText(p.name);
            if (!n.isEmpty() && joined.contains(" " + n + " ") && (best == null || n.length() > normText(best.name).length())) best = p;
        }
        if (best != null) return best;
        if (candName != null) {
            String first = firstWord(normText(candName));
            List<Model.Party> hits = new ArrayList<>();
            for (Model.Party p : parties) if (firstWord(normText(p.name)).equals(first)) hits.add(p);
            if (hits.size() == 1) return hits.get(0);
            List<Model.Party> near = new ArrayList<>();
            for (Model.Party p : parties) {
                String pn = firstWord(normText(p.name));
                if (pn.length() >= 4 && first.length() >= 4 && lev(pn, first) <= (Math.min(pn.length(), first.length()) >= 5 ? 2 : 1)) near.add(p);
            }
            if (near.size() == 1) return near.get(0);
        }
        return null;
    }

    public static Result parseCommand(String text, List<Model.Party> parties) {
        if (parties == null) parties = new ArrayList<>();
        Result r = new Result();
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) { r.kind = "empty"; return r; }
        Double amount = parseAmount(tokens);
        String candName = extractName(tokens);
        Model.Party party = matchParty(tokens, parties, candName);
        String nameOut = party != null ? party.name : prettyName(candName);

        boolean asks = hasKw(tokens, SHOW) || hasKw(tokens, HISAB);
        if (amount == null && hasKw(tokens, SHOW) && hasKw(tokens, EXP)) {
            String scope = "month";
            boolean td = false, yd = false;
            for (String t : tokens) { if (TODAY.contains(t)) td = true; if (YESTERDAY.contains(t)) yd = true; }
            if (td) scope = "day"; else if (yd) scope = "yesterday";
            r.kind = "query"; r.what = "expense"; r.scope = scope;
            return r;
        }
        if (amount == null && asks && hasKw(tokens, HISAB)) {
            r.kind = "query";
            if (party != null || candName != null) {
                r.what = "party"; r.partyId = party != null ? party.id : null; r.name = nameOut;
            } else r.what = "all";
            return r;
        }

        String type = null;
        if (hasKw(tokens, EXP)) type = "expense";
        else if (hasKw(tokens, GOT)) type = "got";
        else if (hasKw(tokens, TOOK) && !hasKw(tokens, GIVE)) type = "took";
        else if (hasKw(tokens, PAID)) type = "paid";
        else if (hasKw(tokens, GIVE)) {
            if (hasKw(tokens, GOODS)) type = "gave";
            else if (hasKw(tokens, MONEY)) type = "paid";
            else type = "gave";
        } else if (hasKw(tokens, GOODS)) type = "gave";

        if (type == null && amount == null) { r.kind = "unknown"; return r; }
        boolean yd = false;
        for (String t : tokens) if (YESTERDAY.contains(t)) yd = true;
        r.kind = "entry";
        r.type = type != null ? type : ((party != null || candName != null) ? "gave" : "income");
        r.amount = amount;
        boolean exp = "expense".equals(type);
        r.name = exp ? null : nameOut;
        r.partyId = exp ? null : (party != null ? party.id : null);
        r.dateOffset = yd ? -1 : 0;
        return r;
    }

    public static String parseYesNo(String text) {
        List<String> t = tokenize(text);
        for (String w : t) if (NO_WORDS.contains(w)) return "no";
        for (String w : t) if (YES_WORDS.contains(w)) return "yes";
        return null;
    }

    private static final List<String> UDHAR = L("udhar", "udhaar", "uthar", "udhari", "ઉધાર", "उधार");

    /** Words left in a spoken sentence once name, amount and keywords are removed (used as the note). */
    public static String leftover(String text, String name) {
        List<String> nameToks = name != null ? tokenize(name) : new ArrayList<String>();
        List<String> out = new ArrayList<>();
        List<List<String>> kws = L2(EXP, GOT, TOOK, PAID, GIVE, SHOW, MULT, TIME, MONEY, HISAB);
        outer:
        for (String t : tokenize(text)) {
            if (numVal(t) != null) continue;
            if (nameToks.contains(t) || PARTICLES.contains(t) || isIn(t, FILL)) continue;
            for (List<String> k : kws) if (isIn(t, k)) continue outer;
            if (UDHAR.contains(t)) continue;
            out.add(t);
        }
        return String.join(" ", out);
    }
}
