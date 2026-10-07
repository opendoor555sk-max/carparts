package com.merahisab.app;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Small view toolkit + theme colours. Everything is built in code (no XML layouts, no AndroidX). */
public final class Ui {
    private Ui() {}

    public static int BG, SURFACE, SURFACE2, LINE, TEXT, MUTED, PURPLE, ACCENT, LILAC, GREEN, RED, AMBER, BLUE, FLD, SOFT, TOP, ONLILAC, DNGBG, OV;
    public static boolean dark = true;
    private static float density = 3f;

    public static void init(Context c) { density = c.getResources().getDisplayMetrics().density; }

    public static final int T1 = Color.parseColor("#14B8A6"), T2 = Color.parseColor("#0F766E");

    public static void setDark(boolean d) {
        dark = d;
        PURPLE = T2;
        if (d) {
            ONLILAC = Color.parseColor("#04302B");
            BG = Color.parseColor("#0A1415"); SURFACE = Color.parseColor("#12201F"); SURFACE2 = Color.parseColor("#1A2D2C");
            LINE = Color.parseColor("#25403D"); TEXT = Color.parseColor("#EAF6F4"); MUTED = Color.parseColor("#8FAAA6");
            ACCENT = Color.parseColor("#5EEAD4"); LILAC = Color.parseColor("#5EEAD4"); GREEN = Color.parseColor("#34D399");
            RED = Color.parseColor("#FB7185"); AMBER = Color.parseColor("#FBBF24"); BLUE = Color.parseColor("#60A5FA");
            FLD = Color.parseColor("#15282A"); SOFT = Color.parseColor("#173A37"); TOP = Color.parseColor("#0B4F49");
            DNGBG = Color.parseColor("#3A1620"); OV = Color.parseColor("#99000000");
        } else {
            ONLILAC = Color.parseColor("#04302B");
            BG = Color.parseColor("#F1F5F9"); SURFACE = Color.parseColor("#FFFFFF"); SURFACE2 = Color.parseColor("#EEF2F6");
            LINE = Color.parseColor("#E2E8F0"); TEXT = Color.parseColor("#0F172A"); MUTED = Color.parseColor("#64748B");
            ACCENT = Color.parseColor("#0D9488"); LILAC = Color.parseColor("#99F6E4"); GREEN = Color.parseColor("#16A34A");
            RED = Color.parseColor("#DC2626"); AMBER = Color.parseColor("#D97706"); BLUE = Color.parseColor("#2563EB");
            FLD = Color.parseColor("#F8FAFC"); SOFT = Color.parseColor("#CCFBF1"); TOP = Color.parseColor("#0F766E");
            DNGBG = Color.parseColor("#FEE2E2"); OV = Color.parseColor("#731E293B");
        }
    }

    public static int dp(float v) { return Math.round(v * density); }

    public static GradientDrawable rr(int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    public static GradientDrawable grad(int a, int b, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b});
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    /** Teal header / hero gradient (left-right). */
    public static GradientDrawable tealBar(float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{T2, Color.parseColor("#0D9488"), T1});
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    public static GradientDrawable tealBarBottom(float rDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{T2, Color.parseColor("#0D9488"), T1});
        float r = dp(rDp);
        g.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        return g;
    }

    public static TextView t(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(I18n.tr(s));
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setIncludeFontPadding(true);
        return v;
    }

    public static LinearLayout v(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout h(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    public static LinearLayout.LayoutParams fillW() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    public static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams p, int l, int t, int r, int b) {
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    public static View space(Context c, int hDp) {
        View s = new View(c);
        s.setLayoutParams(lp(1, dp(hDp)));
        return s;
    }

    public static void pad(View v, int l, int t, int r, int b) { v.setPadding(dp(l), dp(t), dp(r), dp(b)); }

    public static void tap(View v, final Runnable r) {
        v.setClickable(true);
        v.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { r.run(); }
        });
    }

    /** Primary / ghost / danger / green button. */
    public static TextView btn(Context c, String text, String style, Runnable r) {
        TextView b = t(c, text, 16, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        int minH = dp(50);
        b.setMinHeight(minH);
        pad(b, 16, 10, 16, 10);
        switch (style) {
            case "ghost": b.setTextColor(ACCENT); b.setBackground(rr(0, LINE, 14)); break;
            case "danger": b.setTextColor(RED); b.setBackground(rr(DNGBG, RED, 14)); break;
            case "green": b.setTextColor(Color.WHITE); b.setBackground(rr(GREEN, 0, 14)); if (!dark) b.setTextColor(Color.WHITE); break;
            default: b.setTextColor(Color.WHITE); b.setBackground(grad(T1, T2, 16));
        }
        LinearLayout.LayoutParams p = fillW();
        p.setMargins(0, dp(10), 0, 0);
        b.setLayoutParams(p);
        b.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, android.view.MotionEvent e) {
                int a = e.getAction();
                if (a == android.view.MotionEvent.ACTION_DOWN) v.setAlpha(0.45f);
                else if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL) v.setAlpha(1f);
                return false;
            }
        });
        tap(b, r);
        return b;
    }

    public static TextView chip(Context c, String text, boolean on, Runnable r) {
        TextView b = t(c, text, 14, on ? ONLILAC : TEXT, true);
        b.setGravity(Gravity.CENTER);
        pad(b, 14, 8, 14, 8);
        b.setBackground(on ? rr(LILAC, LILAC, 20) : rr(SURFACE2, LINE, 20));
        LinearLayout.LayoutParams p = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, dp(8), 0);
        b.setLayoutParams(p);
        tap(b, r);
        return b;
    }

    public static EditText fld(Context c, String hint, String text, int inputType) {
        EditText e = new EditText(c);
        e.setHint(I18n.tr(hint));
        e.setText(text);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setBackground(rr(FLD, LINE, 14));
        pad(e, 14, 12, 14, 12);
        LinearLayout.LayoutParams p = fillW();
        p.setMargins(0, dp(6), 0, 0);
        e.setLayoutParams(p);
        return e;
    }

    public static final int IN_TEXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS;
    public static final int IN_PLAIN = InputType.TYPE_CLASS_TEXT;
    public static final int IN_NUM = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
    public static final int IN_PHONE = InputType.TYPE_CLASS_PHONE;

    public static TextView label(Context c, String s) {
        TextView l = t(c, s, 13, MUTED, true);
        LinearLayout.LayoutParams p = fillW();
        p.setMargins(dp(2), dp(14), 0, 0);
        l.setLayoutParams(p);
        return l;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = v(c);
        l.setBackground(rr(SURFACE, dark ? LINE : 0, 20));
        l.setElevation(dp(dark ? 0 : 2));
        pad(l, 14, 12, 14, 12);
        return l;
    }

    public static void onText(EditText e, final Runnable r) {
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { r.run(); }
        });
    }

    public static int tone(String tone) {
        switch (tone) {
            case "g": return GREEN;
            case "r": return RED;
            case "b": return BLUE;
            default: return AMBER;
        }
    }

    public static String toneOf(String type) {
        switch (type) {
            case "gave": return "a";
            case "got": return "g";
            case "took": return "b";
            case "paid": return "r";
            case "income": return "g";
            default: return "r";
        }
    }

    /** Bottom sheet dialog with scrollable body. */
    public static final class Sheet {
        public final Dialog dlg;
        public final LinearLayout body;

        public Sheet(Context c) {
            dlg = new Dialog(c);
            dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout root = v(c);
            root.setBackground(topRounded(SURFACE, 24));
            pad(root, 16, 10, 16, 16);
            View grab = new View(c);
            grab.setBackground(rr(LINE, 0, 3));
            LinearLayout.LayoutParams gp = lp(dp(44), dp(5));
            gp.gravity = Gravity.CENTER_HORIZONTAL;
            gp.setMargins(0, 0, 0, dp(10));
            grab.setLayoutParams(gp);
            root.addView(grab);
            ScrollView sv = new ScrollView(c);
            body = v(c);
            sv.addView(body);
            root.addView(sv, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            dlg.setContentView(root);
            Window w = dlg.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                w.setGravity(Gravity.BOTTOM);
                w.setDimAmount(0.55f);
                w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
            }
        }

        public Sheet title(String s) {
            TextView t = Ui.t(dlg.getContext(), s, 19, TEXT, true);
            t.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams p = fillW();
            p.setMargins(0, 0, 0, dp(10));
            t.setLayoutParams(p);
            body.addView(t);
            return this;
        }

        public Sheet add(View v) { body.addView(v); return this; }

        public void show() { dlg.show(); }

        public void dismiss() { try { dlg.dismiss(); } catch (Exception e) { /* ignore */ } }
    }

    public static GradientDrawable topRounded(int fill, float rDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        float r = dp(rDp);
        g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        return g;
    }
}
