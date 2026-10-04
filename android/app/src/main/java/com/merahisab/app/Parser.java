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

    private static final List<String> EXP = L("spent", "spend", "spending", "expenses", "kharch", "kharcha", "ખર્ચ", "ખર્ચો", "खर्च", "खर्चा", "expense");
    private static final List<String> GOT = L("mile", "mila", "mili", "aaya", "aaye", "aayi", "jama", "jamaa", "wapas", "vapas", "received", "receive", "prapt", "collected", "returned",
            "મળ્યા", "મળ્યો", "મળ્યું", "મળી", "આવ્યા", "આવ્યો", "આવ્યું", "જમા", "પાછા", "પરત",
            "मिले", "मिला", "मिली", "आए", "आया", "आई", "जमा", "वापस");
    private static final List<String> TOOK = L("took", "taken", "bought", "purchased", "borrowed", "liya", "liye", "lidha", "lidhu", "khareeda", "kharida", "kharid", "kharidi",
            "લીધા", "લીધો", "લીધું", "લીધી", "ખરીદ્યા", "ખરીદ્યો", "ખરીદ્યું", "ખરીદી",
            "लिया", "लिए", "ली", "खरीदा", "खरीदे", "खरीद");
    private static final List<String> PAID = L("chukavya", "chukaya", "chukaye", "chukav", "paid", "bhugtan",
            "ચૂકવ્યા", "ચૂકવ્યો", "ચૂકવી", "ચુકવ્યા", "ભરપાઈ", "चुकाया", "चुकाए", "चुकाई", "भुगतान");
    private static final List<String> GIVE = L("gave", "given", "give", "lent", "diya", "diye", "di", "dia", "aapya", "aapyo", "apya", "sold", "becha", "beche",
            "આપ્યા", "આપ્યો", "આપ્યું", "આપી", "દીધા", "દીધો", "દીધું", "વેચ્યા", "વેચ્યો", "વેચ્યું",
            "दिया", "दिए", "दी", "बेचा", "बेचे");
    private static final List<String> GOODS = L("goods", "credit", "rent", "maal", "mal", "bhada", "bhade", "bhadu", "udhar", "udhaar", "uthar", "udhari", "pedal",
            "માલ", "ભાડે", "ભાડું", "ભાડા", "ઉધાર", "उधार", "माल", "भाड़ा", "भाड़े", "किराया");
    private static final List<String> MONEY = L("paisa", "paise", "payment", "rakam", "rupiya", "rupaye", "rupees", "rs",
            "પૈસા", "રૂપિયા", "રકમ", "પેમેન્ટ", "पैसा", "पैसे", "रुपये", "रुपया", "रुपए", "पेमेंट");
    private static final List<String> HISAB = L("balance", "account", "ledger", "hisab", "hisaab", "hishab", "khata", "khaata", "statement", "detail", "baki", "baaki",
            "હિસાબ", "ખાતું", "ખાતા", "ડિટેલ", "બાકી", "हिसाब", "खाता", "डिटेल", "बाकी");
    private static final List<String> SHOW = L("open", "khol", "kholo", "kholiye", "kholna", "khulo", "tell", "display", "check", "batao", "bata", "bataiye", "dikhao", "dikha", "show", "kitna", "kitne", "jano", "janvu",
            "ખોલો", "ખોલ", "ખોલવું", "खोलो", "खोल", "खोलिए", "બતાવો", "બતાવ", "બોલો", "કહો", "કેટલા", "કેટલું", "જણાવો", "बताओ", "बताइए", "बता", "दिखाओ", "कितना", "कितने");
    private static final List<String> MULT = L("thousand", "hundred", "hajar", "hazar", "hazaar", "હજાર", "हजार", "sau", "સો", "सौ", "lakh", "laakh", "લાખ", "लाख");
    private static final List<String> TIME = L("aaj", "kal", "આજે", "આજ", "आज", "કાલે", "કાલ", "ગઈકાલે", "कल", "mahina", "mahine", "મહિનો", "મહિના", "महीने", "महीना", "is", "iss", "આ", "इस");
    private static final List<String> FILL = L("mujhe", "mene", "maine", "me", "mein", "ma", "ek", "koi", "sab", "saara", "sara", "બધા", "બધું", "सब", "सारा", "ka", "ki", "ke", "ko", "se", "ne", "ni", "no", "na", "nu", "ne", "hai", "hain", "he", "chhe", "che", "hu", "hoon", "mera", "mara", "meri", "mari", "please", "plz", "jara", "zara", "है", "हैं", "छे", "છે", "मेरा", "मेरी", "મારું", "મારા", "જરા");
    private static final List<List<String>> ANYKW = L2(EXP, GOT, TOOK, PAID, GIVE, GOODS, MONEY, HISAB, SHOW, MULT, TIME);

    @SafeVarargs
    private static List<List<String>> L2(List<String>... a) { return Arrays.asList(a); }

    private static final List<String> PARTICLES = L("ko", "se", "ne", "ka", "ki", "ke", "ni", "no", "nu", "ને", "થી", "નો", "ની", "ના", "નું", "પાસે", "પાસેથી", "को", "से", "ने", "का", "की", "के", "पास");
    private static final List<String> YESTERDAY = L("yesterday", "kal", "કાલે", "કાલ", "ગઈકાલે", "कल");
    private static final List<String> TODAY = L("today", "aaj", "આજે", "આજ", "आज");
    private static final List<String> YES_WORDS = L("haan", "han", "ha", "haa", "sahi", "save", "yes", "ok", "okay", "yeah", "yep", "correct", "હા", "સાચું", "સેવ", "हाँ", "हां", "हा", "सही", "सेव");
    private static final List<String> NO_WORDS = L("nope", "na", "nahi", "nahin", "no", "cancel", "ના", "નહીં", "નથી", "ન", "नहीं", "ना", "नही", "गलत");
    private static final List<String> ENG_FWD = L("to", "from", "for");
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
        add(1, "one"); add(2, "two"); add(3, "three"); add(4, "four"); add(5, "five"); add(6, "six"); add(7, "seven"); add(8, "eight"); add(9, "nine");
        add(10, "ten"); add(15, "fifteen"); add(20, "twenty"); add(30, "thirty"); add(40, "forty"); add(50, "fifty"); add(60, "sixty"); add(70, "seventy"); add(80, "eighty"); add(90, "ninety");
        for (String w : L("hajar", "hazar", "hazaar", "thousand", "હજાર", "हजार")) MULTVAL.put(w, 1000);
        for (String w : L("sau", "hundred", "સો", "सौ")) MULTVAL.put(w, 100);
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

    /** Replace words the app has learned (heard spelling -> real khata name) before parsing. aliases = {from, to}. */
    public static String applyAliases(String text, List<String[]> aliases) {
        if (text == null || aliases == null) return text;
        for (String[] al : aliases) {
            if (al[0].isEmpty() || al[0].equalsIgnoreCase(al[1])) continue;
            text = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(al[0]), java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE)
                    .matcher(text).replaceAll(java.util.regex.Matcher.quoteReplacement(al[1]));
        }
        return text;
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


    /** Name for a query with no particle ("khata kholo kasam bhai"): the leftover non-keyword words. */
    private static String fallbackName(List<String> tokens) {
        List<String> cand = new ArrayList<>();
        for (String t : tokens) if (!isNameStop(t)) cand.add(t);
        if (cand.isEmpty() || cand.size() > 3) return null;
        return String.join(" ", cand);
    }

    private static final String IND_FROM = "કખગઘઙચછજઝઞટઠડઢણતથદધનપફબભમયરલળવશષસહ" + "कखगघङचछजझञटठडढणतथदधनपफबभमयरलळवशषसह";
    private static final String IND_MAP_TO = "kkggnccjjnttddnttddnppbbm0rllvsss0";

    /** Rough sound-skeleton of a name, same for Gujarati, Devanagari and Latin spellings (kasam = kasam = ksm). */
    public static String phon(String name) {
        String s = normText(name).replace(" ", "");
        StringBuilder b = new StringBuilder();
        String lat = s.replace("sh", "s").replace("ch", "C").replace("kh", "k").replace("gh", "g").replace("th", "t").replace("dh", "d")
                .replace("ph", "p").replace("bh", "b").replace("jh", "j");
        for (int i = 0; i < lat.length(); i++) {
            char c = lat.charAt(i);
            if (c == 'ં' || c == 'ं') { b.append('n'); continue; }
            int k = IND_FROM.indexOf(c);
            if (k >= 0) { char m = IND_MAP_TO.charAt(k % IND_MAP_TO.length()); b.append(m == 'c' ? 'C' : m); continue; }
            if (c >= 'a' && c <= 'z') {
                switch (c) {
                    case 'c': case 'q': b.append('k'); break;
                    case 'z': b.append('j'); break;
                    case 'f': b.append('p'); break;
                    case 'w': b.append('v'); break;
                    case 'x': b.append("ks"); break;
                    case 'a': case 'e': case 'i': case 'o': case 'u': case 'y': case 'h': break;
                    default: b.append(c);
                }
            } else if (c == 'C') b.append('C');
        }
        StringBuilder o = new StringBuilder();
        for (int i = 0; i < b.length(); i++) if (i == 0 || b.charAt(i) != b.charAt(i - 1)) o.append(b.charAt(i));
        return o.toString().replace("0", "");
    }

    /** Parties whose sound-skeleton matches the spoken name (works across scripts). */
    public static List<Model.Party> phonMatches(String spoken, List<Model.Party> parties) {
        List<Model.Party> out = new ArrayList<>();
        String q = phon(spoken);
        if (q.length() < 2) return out;
        int best = 99;
        List<Model.Party> hits = new ArrayList<>();
        for (Model.Party p : parties) {
            String pp = phon(p.name);
            if (pp.isEmpty()) continue;
            int d = lev(pp, q);
            int lim = Math.min(pp.length(), q.length()) >= 4 ? 1 : 0;
            if (d > lim) continue;
            if (d < best) { best = d; hits.clear(); }
            if (d == best) hits.add(p);
        }
        return hits;
    }

    private static boolean isNameStop(String t) {
        return numVal(t) != null || isAnyKw(t) || isIn(t, FILL) || PARTICLES.contains(t) || ENG_FWD.contains(t) || t.equals("a") || t.equals("the") || t.equals("of") || t.equals("rs") || t.equals("rupees") || t.equals("rupee") || t.equals("and") || YESTERDAY.contains(t) || TODAY.contains(t) || t.equals("on") || t.equals("is") || t.equals("what") || t.equals("show");
    }

    private static String extractName(List<String> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            if (!ENG_FWD.contains(tokens.get(i))) continue;
            List<String> cand = new ArrayList<>();
            for (int j = i + 1; j < tokens.size() && cand.size() < 3; j++) {
                String w = tokens.get(j);
                if (w.endsWith("'s") && w.length() > 2) w = w.substring(0, w.length() - 2);
                if (isNameStop(w)) break;
                cand.add(w);
            }
            if (!cand.isEmpty()) return String.join(" ", cand);
        }
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
            List<Model.Party> ph = phonMatches(candName, parties);
            if (ph.size() == 1) return ph.get(0);
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
            String cn = candName;
            if (cn == null) cn = fallbackName(tokens);
            Model.Party qp = party != null ? party : matchParty(tokens, parties, cn);
            if (qp != null || cn != null) {
                r.what = "party"; r.partyId = qp != null ? qp.id : null; r.name = qp != null ? qp.name : prettyName(cn);
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
            if (nameToks.contains(t) || PARTICLES.contains(t) || isIn(t, FILL) || ENG_FWD.contains(t) || YESTERDAY.contains(t) || TODAY.contains(t)) continue;
            if (t.equals("a") || t.equals("the") || t.equals("of") || t.equals("on") || t.equals("and") || t.equals("rs") || t.equals("rupees") || t.equals("rupee")) continue;
            for (List<String> k : kws) if (isIn(t, k)) continue outer;
            if (UDHAR.contains(t)) continue;
            out.add(t);
        }
        return String.join(" ", out);
    }
}
