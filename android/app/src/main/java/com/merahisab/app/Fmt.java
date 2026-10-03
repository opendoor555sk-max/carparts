package com.merahisab.app;

import java.time.LocalDate;
import java.util.Locale;

/** Dates and money formatting (Gujarati labels, Indian digit grouping). */
public final class Fmt {
    private Fmt() {}

    public static final String[] MONTHS = {"જાન્યુઆરી", "ફેબ્રુઆરી", "માર્ચ", "એપ્રિલ", "મે", "જૂન", "જુલાઈ", "ઑગસ્ટ", "સપ્ટેમ્બર", "ઑક્ટોબર", "નવેમ્બર", "ડિસેમ્બર"};
    public static final String[] WDAYS = {"રવિ", "સોમ", "મંગળ", "બુધ", "ગુરુ", "શુક્ર", "શનિ"};
    public static final String[] CATS = {"અન્ય", "પેટ્રોલ/ડીઝલ", "ભાડું", "પગાર", "ચા-નાસ્તો", "વીજળી", "રિપેરિંગ", "પરિવહન"};

    public static String today() { return LocalDate.now().toString(); }

    public static boolean validIso(String s) {
        if (s == null || !s.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        try { LocalDate.parse(s); return true; } catch (Exception e) { return false; }
    }

    public static String addDays(String iso, int n) { return LocalDate.parse(iso).plusDays(n).toString(); }

    public static String fmtDate(String iso) {
        if (iso == null || iso.length() < 10) return "";
        return iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(0, 4);
    }

    public static String weekday(String iso) {
        int dow = LocalDate.parse(iso).getDayOfWeek().getValue() % 7; // Mon=1..Sun=7 -> Sun=0
        return WDAYS[dow];
    }

    public static String longDate(String iso) {
        LocalDate d = LocalDate.parse(iso);
        return weekday(iso) + ", " + d.getDayOfMonth() + " " + MONTHS[d.getMonthValue() - 1] + " " + d.getYear();
    }

    public static String shortDate(String iso) {
        LocalDate d = LocalDate.parse(iso);
        String m = MONTHS[d.getMonthValue() - 1];
        if (m.length() > 5) m = m.substring(0, 5);
        return d.getDayOfMonth() + " " + m + ", " + d.getYear();
    }

    public static String monthShift(String key, int n) {
        String[] p = key.split("-");
        LocalDate d = LocalDate.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]), 1).plusMonths(n);
        return String.format(Locale.US, "%04d-%02d", d.getYear(), d.getMonthValue());
    }

    public static String monthLabel(String key) {
        String[] p = key.split("-");
        return MONTHS[Integer.parseInt(p[1]) - 1] + " " + p[0];
    }

    public static double round2(double n) { return Math.round(n * 100.0) / 100.0; }

    public static String plain(double n) {
        n = round2(n);
        boolean neg = n < 0;
        if (neg) n = -n;
        long whole = (long) Math.floor(n);
        long cents = Math.round((n - whole) * 100);
        if (cents >= 100) { whole++; cents -= 100; }
        String ws = Long.toString(whole);
        String out;
        if (ws.length() <= 3) out = ws;
        else {
            String last3 = ws.substring(ws.length() - 3);
            String rest = ws.substring(0, ws.length() - 3);
            StringBuilder b = new StringBuilder();
            while (rest.length() > 2) {
                b.insert(0, "," + rest.substring(rest.length() - 2));
                rest = rest.substring(0, rest.length() - 2);
            }
            out = rest + b + "," + last3;
        }
        if (cents > 0) {
            String c = String.format(Locale.US, "%02d", cents);
            if (c.endsWith("0")) c = c.substring(0, 1);
            out += "." + c;
        }
        return (neg ? "-" : "") + out;
    }

    public static String money(double n) { return "₹" + plain(n); }

    public static String greeting(int hour) { return hour < 12 ? "શુભ સવાર" : (hour < 17 ? "શુભ બપોર" : "શુભ સાંજ"); }

    public static String initial(String name) {
        String t = name == null ? "" : name.trim();
        if (t.isEmpty()) return "?";
        int cp = t.codePointAt(0);
        return new String(Character.toChars(cp)).toUpperCase(Locale.ROOT);
    }
}
