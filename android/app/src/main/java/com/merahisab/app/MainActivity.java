package com.merahisab.app;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.CancellationSignal;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class MainActivity extends Activity {
    interface SpeechCb { void partial(String t); void fin(String t); void end(boolean got); void error(String kind); }
    interface BoolCb { void done(boolean ok); }

    private static final int REQ_MIC = 11, REQ_RESTORE = 12;

    // ---- app state ----
    final Model.Db db = new Model.Db();
    Sheets sheets;
    Screens screens;
    String tab = "home", partyId = null, sub = null, period = "day", day, month, filter = "all", fparty = "";
    String kq = "", kf = "all", sort = "bal", rday, rmonth, rkind = "all", rparty = "", drange = "month", rhome = "a", balMode = "full", setOpen = "";

    Sync sync;
    private View loginView;
    private boolean lgReqDone = false;
    private String lgBusy = "";
    private String lgName = "", lgPhone = "", lgShop = "", lgMsg = "", lgWa = "", lgOtp = "";
    private final Handler pollH = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            sync.run(null);
            if (sync.isAdmin()) new Thread(new Runnable() { @Override public void run() {
                final int before = Notifier.pending;
                final int n = Notifier.check(MainActivity.this, true);
                if (n > before) runOnUiThread(new Runnable() { @Override public void run() { toast("નવી OTP વિનંતિ: " + n + " (સેટિંગ્સ → એડમિન પેનલ)"); } });
            } }).start();
            pollH.postDelayed(this, 30000);
        }
    };
    private long lastUpdCheck = 0;

    private FrameLayout root;
    private ScrollView scroll;
    private LinearLayout navBar;
    private View lockView, setupView;
    private String setupCat = "";
    private long stoppedAt = 0;
    private boolean unlocked = false;

    // ---- pin state ----
    private String pinMode = "unlock", pinBuf = "", pinFirst = "";
    private Runnable pinDone;
    private TextView pinSub, pinMsg;
    private LinearLayout pinDots;

    // ---- speech / tts / bio ----
    private SpeechRecognizer recognizer;
    private SpeechCb speechCb;
    String lastAlts = "";
    private boolean listening = false, gotText = false;
    private String pendingLangStart = null;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private BoolCb bioCb;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Api.ver = versionName();
        Ui.init(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        day = Fmt.today(); month = day.substring(0, 7); rday = day; rmonth = month;
        loadData();
        Api.load(this);
        sync = new Sync(this);
        if (!db.settings.setupDone && (!db.parties.isEmpty() || !db.txns.isEmpty() || !db.settings.owner.isEmpty() || !db.settings.pinHash.isEmpty())) {
            db.settings.setupDone = true; // existing user: no first-run wizard
            save();
        }
        sheets = new Sheets(this);
        screens = new Screens(this);
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override public void onInit(int st) { ttsReady = (st == TextToSpeech.SUCCESS); }
        });
        rebuild();
        if (!db.settings.pinHash.isEmpty()) showPin("unlock", null);
    }

    // ================= first-run setup =================
    private void closeSetup() {
        if (setupView != null) { root.removeView(setupView); setupView = null; }
    }

    private TextView setupHero(LinearLayout l, String title, String sub, final Runnable back) {
        LinearLayout h = Ui.v(this);
        h.setBackground(Ui.grad(Color.parseColor("#C79BFF"), Color.parseColor("#7B2FBE"), 28));
        h.setGravity(Gravity.CENTER);
        Ui.pad(h, 20, 30, 20, 30);
        TextView t = Ui.t(this, title, 22, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        h.addView(t);
        TextView s = Ui.t(this, sub, 14, Color.parseColor("#EBDDFF"), false);
        s.setGravity(Gravity.CENTER);
        Ui.pad(s, 0, 6, 0, 0);
        h.addView(s);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(12), Ui.dp(36), Ui.dp(12), Ui.dp(18));
        l.addView(h, lp);
        return t;
    }

    private View setupOption(String title, String sub, boolean on, Runnable r) {
        LinearLayout row = Ui.v(this);
        row.setBackground(Ui.rr(on ? Ui.SOFT : Ui.SURFACE2, on ? Ui.ACCENT : Ui.LINE, 16));
        Ui.pad(row, 16, 14, 16, 14);
        row.addView(Ui.t(this, (on ? "◉  " : "○  ") + title, 17, Ui.TEXT, true));
        if (sub != null) { TextView s = Ui.t(this, sub, 13, Ui.MUTED, false); Ui.pad(s, 26, 2, 0, 0); row.addView(s); }
        Ui.tap(row, r);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(14), Ui.dp(6), Ui.dp(14), Ui.dp(6));
        row.setLayoutParams(lp);
        return row;
    }

    /** First-run wizard: language, name, what you do, (service category), business name, PIN. */
    void showSetup(final int step) {
        closeSetup();
        I18n.set(db.settings.lang);
        final LinearLayout l = Ui.v(this);
        l.setBackgroundColor(Ui.BG);
        l.setClickable(true);
        final Model.Settings st = db.settings;
        if (step == 0) {
            setupHero(l, "Mera Hisab", "ભાષા પસંદ કરો", null);
            final String[][] langs = {{"gu-IN", "ગુજરાતી", "Gujarati"}, {"hi-IN", "हिन्दी", "Hindi"}, {"en-IN", "English", "English"}};
            for (final String[] g : langs) {
                l.addView(setupOption(g[1], g[2], g[0].equals(st.lang), new Runnable() { @Override public void run() { st.lang = g[0]; I18n.set(g[0]); showSetup(0); } }));
            }
            l.addView(Ui.btn(this, "આગળ વધો", "primary", new Runnable() { @Override public void run() { save(); showSetup(1); } }));
        } else if (step == 1) {
            setupHero(l, "તમારું નામ", "Mera Hisab માં સ્વાગત છે", null);
            final EditText nm = Ui.fld(this, "તમારું નામ", st.owner, Ui.IN_PLAIN);
            l.addView(nm);
            l.addView(Ui.btn(this, "આગળ વધો", "primary", new Runnable() {
                @Override public void run() {
                    String n = nm.getText().toString().trim();
                    if (n.isEmpty()) { toast("તમારું નામ લખો"); return; }
                    st.owner = n; save(); showSetup(2);
                }
            }));
            l.addView(Ui.btn(this, "← પાછા", "ghost", new Runnable() { @Override public void run() { showSetup(0); } }));
        } else if (step == 2) {
            setupHero(l, "તમે શું કરો છો?", "તમારા માટે સૌથી યોગ્ય પ્રોફાઇલ પસંદ કરો", null);
            l.addView(setupOption("દુકાન / સ્ટોર", "દુકાન, ગ્રાહકો અને સ્ટોક મેનેજ કરો", st.biz.equals("store"), new Runnable() { @Override public void run() { st.biz = "store"; showSetup(2); } }));
            l.addView(setupOption("સર્વિસ પ્રોવાઈડર", "કામ, સર્વિસ, પાર્ટ અને ઉધાર-રોકડ", st.biz.equals("service"), new Runnable() { @Override public void run() { st.biz = "service"; showSetup(2); } }));
            l.addView(Ui.btn(this, "આગળ", "primary", new Runnable() {
                @Override public void run() { save(); showSetup(st.biz.equals("service") ? 3 : 4); }
            }));
            l.addView(Ui.btn(this, "← પાછા", "ghost", new Runnable() { @Override public void run() { showSetup(1); } }));
        } else if (step == 3) {
            setupHero(l, "વ્યવસાય શ્રેણી પસંદ કરો", "તમારા કામની શ્રેણી ચૂંટો", null);
            String[] cats = {"દૂધની સેવા", "ટિફિન સેવા", "લોન્ડ્રી / ધોલાઈ", "ચાની દુકાન", "છાપું / મેગેઝિન", "પાણીની સેવા", "અન્ય સેવા"};
            for (final String c1 : cats) {
                l.addView(setupOption(c1, null, c1.equals(st.bizCat), new Runnable() { @Override public void run() { st.bizCat = c1; showSetup(3); } }));
            }
            l.addView(Ui.btn(this, "આગળ", "primary", new Runnable() {
                @Override public void run() {
                    if (st.bizCat.isEmpty()) st.bizCat = "અન્ય સેવા";
                    save(); showSetup(4);
                }
            }));
            l.addView(Ui.btn(this, "← પાછા", "ghost", new Runnable() { @Override public void run() { showSetup(2); } }));
        } else if (step == 4) {
            setupHero(l, "દુકાન / ધંધાનું નામ", "શરૂ કરવા માટે તેને નામ આપો", null);
            final EditText sh = Ui.fld(this, "દુકાન / ધંધાનું નામ", st.shop, Ui.IN_PLAIN);
            l.addView(sh);
            l.addView(Ui.btn(this, "શરૂ કરો", "primary", new Runnable() {
                @Override public void run() {
                    String n = sh.getText().toString().trim();
                    if (n.isEmpty()) { toast("ધંધાનું નામ લખો"); return; }
                    st.shop = n; save(); showSetup(5);
                }
            }));
            l.addView(Ui.btn(this, "← પાછા", "ghost", new Runnable() { @Override public void run() { showSetup(st.biz.equals("service") ? 3 : 2); } }));
        } else {
            setupHero(l, "તમારી એપ સુરક્ષિત કરો", "4-અંકનો PIN સેટ કરો અથવા છોડો", null);
            l.addView(Ui.btn(this, "PIN સેટ કરો", "primary", new Runnable() {
                @Override public void run() {
                    showPin("set1", new Runnable() { @Override public void run() { askBiometric(); } });
                }
            }));
            l.addView(Ui.btn(this, "છોડો (Skip)", "ghost", new Runnable() { @Override public void run() { finishSetup(); } }));
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setupView = sv;
        root.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (lockView != null) { root.removeView(lockView); root.addView(lockView); }
    }

    private void askBiometric() {
        if (!bioAvailable()) { finishSetup(); return; }
        new android.app.AlertDialog.Builder(this)
                .setTitle(I18n.tr("બાયોમેટ્રિક્સ સક્રિય કરો?"))
                .setMessage(I18n.tr("શું તમે એપને ઝડપથી અનલોક કરવા માટે ફિંગરપ્રિન્ટ અથવા ફેસ આઈડી વાપરવા માંગો છો?"))
                .setCancelable(false)
                .setNegativeButton(I18n.tr("ના"), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) { finishSetup(); }
                })
                .setPositiveButton(I18n.tr("હા"), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        bioAuth(I18n.tr("ફિંગરપ્રિન્ટ લૉક ચાલુ કરો"), new BoolCb() {
                            @Override public void done(boolean ok) { if (ok) { db.settings.bio = "native"; save(); } finishSetup(); }
                        });
                    }
                }).show();
    }

    private void finishSetup() {
        db.settings.setupDone = true;
        save();
        closeSetup();
        render();
    }

    // ================= data =================
    private File dataFile() { return new File(getFilesDir(), "data.json"); }

    private void loadData() {
        I18n.set(db.settings.lang);
        try {
            File f = dataFile();
            if (f.exists()) {
                InputStream in = new java.io.FileInputStream(f);
                String s = new String(readAll(in), StandardCharsets.UTF_8);
                db.loadJson(s);
            }
        } catch (Exception e) { /* start empty */ }
        if (db.settings.deviceId.isEmpty()) db.settings.deviceId = uid();
    }

    void save() {
        saveLocalOnly();
        if (sync != null) sync.kick();
    }

    void saveLocalOnly() {
        db.saved = System.currentTimeMillis();
        try {
            File t = new File(getFilesDir(), "data.json.tmp");
            FileOutputStream o = new FileOutputStream(t);
            o.write(db.toJson().getBytes(StandardCharsets.UTF_8));
            o.close();
            File f = dataFile();
            if (f.exists()) f.delete();
            t.renameTo(f);
        } catch (Exception e) { toast("સેવ થઈ શક્યું નહીં"); }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toByteArray();
    }

    String uid() {
        return Long.toString(System.currentTimeMillis(), 36) + Integer.toString(new Random().nextInt(36 * 36 * 36 * 36), 36);
    }

    Model.Party newParty(String name, String phone, String kind) {
        Model.Party p = new Model.Party();
        p.id = uid(); p.name = name; p.phone = phone; p.kind = kind;
        p.created = System.currentTimeMillis(); p.upd = p.created;
        db.parties.add(p);
        return p;
    }

    void toast(String m) { Toast.makeText(this, I18n.tr(m), Toast.LENGTH_SHORT).show(); }

    String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "1.0"; }
    }

    long versionCode() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode(); } catch (Exception e) { return 1; }
    }

    // ================= theme + layout =================
    void applyTheme() {
        String t = db.settings.theme;
        boolean dark;
        if (t.equals("dark")) dark = true;
        else if (t.equals("light")) dark = false;
        else dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        Ui.setDark(dark);
        getWindow().setStatusBarColor(Ui.TOP);
        View dv = getWindow().getDecorView();
        dv.setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    /** Rebuild frame + nav (used at start and after a theme change). */
    void rebuild() {
        applyTheme();
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        LinearLayout main = Ui.v(this);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        main.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        navBar = Ui.h(this);
        navBar.setBackgroundColor(Ui.BG);
        main.addView(navBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(62)));
        root.addView(main, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        lockView = null;
        setupView = null;
        render();
        loginView = null;
        if (needLogin()) showLogin(); else if (!db.settings.setupDone) showSetup(0);
        if (!db.settings.pinHash.isEmpty() && !unlocked) showPin("unlock", null);
    }

    void go(String t) { tab = t; partyId = null; sub = null; render(); scroll.scrollTo(0, 0); }

    void openParty(String id) { sheets.close(); partyId = id; tab = "khata"; render(); scroll.scrollTo(0, 0); }

    void navPeriod(int d) {
        if (period.equals("day")) day = Fmt.addDays(day, d);
        else if (period.equals("month")) month = Fmt.monthShift(month, d);
        render();
    }

    void render() {
        I18n.set(db.settings.lang);
        if (scroll == null) return;
        LinearLayout v;
        if (partyId != null) v = screens.party(partyId);
        else if (tab.equals("home")) v = screens.home();
        else if (tab.equals("txn")) v = screens.txn();
        else if (tab.equals("rep")) v = screens.reports();
        else if (tab.equals("khata")) v = screens.khata();
        else v = screens.more();
        scroll.removeAllViews();
        v.setPadding(0, 0, 0, Ui.dp(24));
        scroll.addView(v);
        drawNav();
    }

    private void drawNav() {
        navBar.removeAllViews();
        navBar.setBackgroundColor(Ui.BG);
        String[][] items = {{"home", "હોમ", "⌂"}, {"txn", "વ્યવહારો", "▤"}, {"rep", "રિપોર્ટ્સ", "▥"}, {"khata", "ખાતા", "👥"}, {"more", "વધુ", "⋯"}};
        for (final String[] it : items) {
            boolean on = it[0].equals(tab) && partyId == null || (it[0].equals("khata") && partyId != null);
            LinearLayout col = Ui.v(this);
            col.setGravity(Gravity.CENTER);
            TextView g = Ui.t(this, it[2], 20, on ? Ui.ACCENT : Ui.MUTED, true);
            g.setGravity(Gravity.CENTER);
            if (on) { g.setBackground(Ui.rr(Ui.SOFT, 0, 14)); Ui.pad(g, 16, 1, 16, 1); }
            col.addView(g);
            TextView l = Ui.t(this, it[1], 11, on ? Ui.ACCENT : Ui.MUTED, on);
            l.setGravity(Gravity.CENTER);
            col.addView(l);
            Ui.tap(col, new Runnable() { @Override public void run() { go(it[0]); } });
            navBar.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
    }

    @Override
    public void onBackPressed() {
        if (lockView != null || setupView != null) return;
        if (partyId != null) { partyId = null; render(); return; }
        if (sub != null) { sub = null; render(); return; }
        if (!tab.equals("home")) { go("home"); return; }
        finish();
    }

    @Override
    protected void onStop() {
        stoppedAt = System.currentTimeMillis();
        stopListen();
        pollH.removeCallbacks(poll);
        super.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (sync != null && sync.loggedIn()) { pollH.removeCallbacks(poll); pollH.postDelayed(poll, 1200); }
        if (System.currentTimeMillis() - lastUpdCheck > 6L * 3600000L && loginView == null && lockView == null) {
            lastUpdCheck = System.currentTimeMillis();
            Updater.check(this, true);
        }
        if (stoppedAt > 0 && !db.settings.pinHash.isEmpty() && lockView == null && System.currentTimeMillis() - stoppedAt > 30000) {
            sheets.close();
            unlocked = false;
            showPin("unlock", null);
        }
    }

    @Override
    protected void onDestroy() {
        stopListen();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }

    // ================= helpers: text / links =================
    String waLink(String phone, String text) {
        String n = phone == null ? "" : phone.replaceAll("\\D", "");
        if (n.length() == 10) n = "91" + n;
        try { return "https://wa.me/" + n + "?text=" + URLEncoder.encode(text, "UTF-8").replace("+", "%20"); }
        catch (Exception e) { return "https://wa.me/" + n; }
    }

    String upiLink(double amount, String note) {
        Model.Settings s = db.settings;
        String name = !s.upiName.isEmpty() ? s.upiName : (!s.shop.isEmpty() ? s.shop : (!s.owner.isEmpty() ? s.owner : "Mera Hisab"));
        try {
            return "upi://pay?pa=" + URLEncoder.encode(s.upiId, "UTF-8") + "&pn=" + URLEncoder.encode(name, "UTF-8").replace("+", "%20")
                    + (amount > 0 ? "&am=" + URLEncoder.encode(String.valueOf(Fmt.round2(amount)), "UTF-8") : "")
                    + "&cu=INR&tn=" + URLEncoder.encode(note, "UTF-8").replace("+", "%20");
        } catch (Exception e) { return "upi://pay?pa=" + s.upiId; }
    }

    String payMsg(Model.Party p, double amt) {
        Model.Settings s = db.settings;
        return "નમસ્તે " + p.name + ",\nતમારા " + Fmt.money(amt) + " બાકી છે. કૃપા કરીને ચૂકવણી કરશો.\n\nUPI ID: " + s.upiId
                + "\nચૂકવણી Link: " + upiLink(amt, "Hisab " + p.name) + (s.shop.isEmpty() ? "" : "\n\n— " + s.shop);
    }

    String statementText(Model.Party p) {
        java.util.List<Model.Txn> rows = new ArrayList<>(db.partyEntries(p.id));
        java.util.Collections.reverse(rows);
        double b = db.partyBal(p.id);
        StringBuilder s = new StringBuilder("*" + p.name + " નો હિસાબ*" + (db.settings.shop.isEmpty() ? "" : " (" + db.settings.shop + ")") + "\n\n");
        for (Model.Txn t : rows) {
            String nt = !t.note.isEmpty() ? t.note : t.said;
            s.append(Fmt.fmtDate(t.date)).append(" · ").append(Model.type(t.type).label).append(" ").append(Fmt.money(t.amount));
            if (!nt.isEmpty()) s.append(" (").append(nt).append(")");
            s.append("\n");
        }
        s.append("\n").append(b > 0 ? "કુલ બાકી (તમારે આપવાના): " : (b < 0 ? "કુલ બાકી (અમારે આપવાના): " : "હિસાબ બરાબર ")).append(b != 0 ? Fmt.money(Math.abs(b)) : "");
        return s.toString();
    }

    boolean isEnglish() { return db.settings.lang.equals("en-IN"); }

    String partySummary(Model.Party p) {
        if (isEnglish()) return partySummaryEn(p);
        double b = db.partyBal(p.id), gave = 0, got = 0, took = 0, paid = 0;
        for (Model.Txn t : db.txns) {
            if (!p.id.equals(t.partyId)) continue;
            if (t.type.equals("gave")) gave += t.amount; else if (t.type.equals("got")) got += t.amount;
            else if (t.type.equals("took")) took += t.amount; else if (t.type.equals("paid")) paid += t.amount;
        }
        StringBuilder m = new StringBuilder(p.name + " નો હિસાબ. ");
        if (gave > 0) m.append("કુલ ઉધાર આપ્યું ").append(Fmt.plain(gave)).append(" રૂપિયા. ");
        if (got > 0) m.append("પૈસા મળ્યા ").append(Fmt.plain(got)).append(" રૂપિયા. ");
        if (took > 0) m.append("ઉધાર લીધું ").append(Fmt.plain(took)).append(" રૂપિયા. ");
        if (paid > 0) m.append("ચૂકવ્યા ").append(Fmt.plain(paid)).append(" રૂપિયા. ");
        m.append(b > 0 ? "કુલ બાકી " + Fmt.plain(b) + " રૂપિયા લેવાના છે." : (b < 0 ? "કુલ " + Fmt.plain(-b) + " રૂપિયા દેવાના છે." : "હિસાબ બરાબર છે."));
        return m.toString();
    }

    private String partySummaryEn(Model.Party p) {
        double b = db.partyBal(p.id), gave = 0, got = 0, took = 0, paid = 0;
        for (Model.Txn x : db.txns) {
            if (!p.id.equals(x.partyId)) continue;
            if (x.type.equals("gave")) gave += x.amount; else if (x.type.equals("got")) got += x.amount;
            else if (x.type.equals("took")) took += x.amount; else if (x.type.equals("paid")) paid += x.amount;
        }
        StringBuilder m = new StringBuilder("Account of " + p.name + ". ");
        if (gave > 0) m.append("Total given on credit ").append(Fmt.plain(gave)).append(" rupees. ");
        if (got > 0) m.append("Received ").append(Fmt.plain(got)).append(" rupees. ");
        if (took > 0) m.append("Taken on credit ").append(Fmt.plain(took)).append(" rupees. ");
        if (paid > 0) m.append("Paid ").append(Fmt.plain(paid)).append(" rupees. ");
        m.append(b > 0 ? "You will receive " + Fmt.plain(b) + " rupees." : (b < 0 ? "You have to pay " + Fmt.plain(-b) + " rupees." : "The account is settled."));
        return m.toString();
    }

    String backupTimeText() {
        String lb = db.settings.lastBackup;
        if (lb.isEmpty()) return "હજી બેકઅપ લીધું નથી";
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(lb.length() > 19 ? lb.substring(0, 19) : lb);
            return "છેલ્લું બેકઅપ: " + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(d);
        } catch (Exception e) { return "છેલ્લું બેકઅપ લીધું છે"; }
    }

    boolean backupFresh() {
        String lb = db.settings.lastBackup;
        if (lb.isEmpty()) return false;
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(lb.length() > 19 ? lb.substring(0, 19) : lb);
            return System.currentTimeMillis() - d.getTime() < 7L * 86400000L;
        } catch (Exception e) { return false; }
    }

    // ================= external =================
    void openUrl(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(i);
        } catch (Exception e) { toast("ખોલી શકાયું નહીં"); }
    }

    void shareText(String text) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(i, "Share"));
        } catch (Exception e) { toast("શેર થઈ શક્યું નહીં"); }
    }

    // ================= backup / restore =================
    void doBackup(boolean share) {
        String stamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date());
        String body = db.backupJson(stamp);
        String name = "MeraHisab-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date()) + ".json";
        Uri u = null;
        try {
            ContentResolver cr = getContentResolver();
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download");
            u = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (u != null) {
                OutputStream o = cr.openOutputStream(u);
                o.write(body.getBytes(StandardCharsets.UTF_8));
                o.close();
            }
        } catch (Exception e) { u = null; }
        if (u == null) { toast("બેકઅપ બન્યું નહીં"); return; }
        db.settings.lastBackup = stamp;
        save();
        render();
        if (share) {
            try {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_STREAM, u);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "Mera Hisab Backup"));
            } catch (Exception e) { toast("શેર થઈ શક્યું નહીં"); }
        } else toast("બેકઅપ Downloads ફોલ્ડરમાં સેવ થયું");
    }

    void pickRestoreFile() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_RESTORE);
        } catch (Exception e) { toast("ફાઇલ પસંદ થઈ શકી નહીં"); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_RESTORE && res == RESULT_OK && data != null && data.getData() != null) {
            try {
                InputStream in = getContentResolver().openInputStream(data.getData());
                String text = new String(readAll(in), StandardCharsets.UTF_8);
                final Model.Db tmp = new Model.Db();
                if (!tmp.loadJson(text)) { toast("આ ફાઇલ સાચી નથી"); return; }
                sheets.confirm(tmp.parties.size() + " ખાતા અને " + tmp.txns.size() + " નોંધ પાછી લાવવી છે? હાલનો ડેટા બદલાઈ જશે.", "હા, પાછું લાવો", new Runnable() {
                    @Override public void run() {
                        String keepPin = db.settings.pinHash, keepBio = db.settings.bio, keepDev = db.settings.deviceId;
                        db.parties = tmp.parties; db.txns = tmp.txns; db.products = tmp.products; db.settings = tmp.settings;
                        db.settings.pinHash = keepPin; db.settings.bio = keepBio;
                        db.settings.deviceId = keepDev.isEmpty() ? uid() : keepDev;
                        save();
                        rebuild();
                        toast("બેકઅપ પાછું આવ્યું");
                    }
                });
            } catch (Exception e) { toast("આ ફાઇલ સાચી નથી"); }
            return;
        }
        super.onActivityResult(req, res, data);
    }

    // ================= update (download + install inside the app) =================
    void checkUpdate() { Updater.check(this, false); }

    // ================= text to speech =================
    void speak(String text) {
        if (!db.settings.speak || !ttsReady) return;
        try {
            tts.setLanguage(Locale.forLanguageTag(db.settings.lang));
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mh");
        } catch (Exception e) { /* ignore */ }
    }

    // ================= speech recognition =================
    String speechError(String kind) {
        switch (kind) {
            case "perm": return "માઇકની પરવાનગી આપો, પછી ફરી દબાવો";
            case "nospeech": return "અવાજ સંભળાયો નહીં, ફરી દબાવીને બોલો";
            case "network": return "ઇન્ટરનેટ ચાલુ કરો (બોલવા માટે જરૂરી)";
            case "audio": return "માઇક મળ્યું નહીં";
            case "busy": return "માઇક વ્યસ્ત છે, થોડીવાર પછી ફરી";
            default: return "આ ફોનમાં બોલવાની સુવિધા નથી, લખીને મોકલો";
        }
    }

    boolean isListening() { return listening; }

    boolean listen(SpeechCb cb) {
        stopListen();
        speechCb = cb;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingLangStart = db.settings.lang;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return false;
        }
        return beginSpeech();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        if (req == REQ_MIC) {
            boolean ok = grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED;
            if (ok && pendingLangStart != null && speechCb != null) beginSpeech();
            else if (speechCb != null) speechCb.error("perm");
            pendingLangStart = null;
        }
    }

    private boolean beginSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            if (speechCb != null) speechCb.error("unsupported");
            return false;
        }
        destroyRecognizer();
        gotText = false;
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(android.os.Bundle p) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float v) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onEvent(int t, android.os.Bundle b) {}

            @Override public void onPartialResults(android.os.Bundle p) {
                ArrayList<String> l = p.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (l != null && !l.isEmpty() && speechCb != null) speechCb.partial(l.get(0));
            }

            @Override public void onResults(android.os.Bundle r) {
                ArrayList<String> l = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                SpeechCb cb = speechCb;
                listening = false;
                if (l != null && !l.isEmpty()) {
                    gotText = true;
                    // several guesses from the speech engine: pick the one the app understands best
                    int bestI = 0, bestS = -1;
                    StringBuilder alts = new StringBuilder();
                    for (int k = 0; k < l.size(); k++) {
                        String c = l.get(k).trim();
                        if (k > 0) alts.append(" | ").append(c);
                        Parser.Result pr = Parser.parseCommand(Parser.applyAliases(c, db.aliasPairs()), db.parties);
                        int sc = 0;
                        if ("entry".equals(pr.kind)) sc = 2 + (pr.amount != null && pr.amount > 0 ? 1 : 0) + (pr.partyId != null ? 1 : 0);
                        else if ("query".equals(pr.kind)) sc = 1 + (pr.partyId != null ? 2 : 0);
                        if (sc > bestS) { bestS = sc; bestI = k; }
                    }
                    lastAlts = alts.toString();
                    if (cb != null) cb.fin(l.get(bestI).trim());
                }
                if (cb != null) cb.end(gotText);
            }

            @Override public void onError(int e) {
                String k;
                switch (e) {
                    case SpeechRecognizer.ERROR_NO_MATCH:
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: k = "nospeech"; break;
                    case SpeechRecognizer.ERROR_NETWORK:
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: k = "network"; break;
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: k = "perm"; break;
                    case SpeechRecognizer.ERROR_AUDIO: k = "audio"; break;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: k = "busy"; break;
                    default: k = "unsupported";
                }
                SpeechCb cb = speechCb;
                listening = false;
                if (cb != null) { cb.error(k); cb.end(false); }
            }
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, db.settings.lang);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        listening = true;
        recognizer.startListening(i);
        return true;
    }

    void stopListen() {
        listening = false;
        speechCb = null;
        destroyRecognizer();
    }

    private void destroyRecognizer() {
        if (recognizer != null) {
            try { recognizer.cancel(); recognizer.destroy(); } catch (Exception e) { /* ignore */ }
            recognizer = null;
        }
    }

    // ================= biometric =================
    boolean bioAvailable() {
        try {
            BiometricManager m = (BiometricManager) getSystemService(BIOMETRIC_SERVICE);
            return m != null && m.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Exception e) { return false; }
    }

    void bioAuth(String title, final BoolCb cb) {
        try {
            BiometricPrompt p = new BiometricPrompt.Builder(this)
                    .setTitle(title)
                    .setNegativeButton("રદ કરો", getMainExecutor(), new android.content.DialogInterface.OnClickListener() {
                        @Override public void onClick(android.content.DialogInterface d, int w) { cb.done(false); }
                    })
                    .build();
            p.authenticate(new CancellationSignal(), getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) { cb.done(true); }
                @Override public void onAuthenticationError(int code, CharSequence msg) { cb.done(false); }
            });
        } catch (Exception e) { cb.done(false); }
    }

    // ================= PIN lock =================
    String hashPin(String pin) {
        String s = "merahisab|" + db.settings.deviceId + "|" + pin;
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format(Locale.US, "%02x", x));
            return b.toString();
        } catch (Exception e) { return "x" + s.hashCode(); }
    }

    void showPin(String mode, Runnable done) {
        pinMode = mode; pinBuf = ""; pinFirst = ""; pinDone = done;
        if (lockView != null) root.removeView(lockView);
        final LinearLayout l = Ui.v(this);
        l.setBackgroundColor(Ui.BG);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setClickable(true);
        Ui.pad(l, 24, 60, 24, 24);
        TextView ic = Ui.t(this, "🔒", 34, Color.WHITE, true);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(Ui.grad(Color.parseColor("#8B3FE0"), Ui.PURPLE, 36));
        l.addView(ic, new LinearLayout.LayoutParams(Ui.dp(72), Ui.dp(72)));
        TextView ttl = Ui.t(this, "Mera Hisab", 24, Ui.TEXT, true);
        ttl.setGravity(Gravity.CENTER);
        Ui.pad(ttl, 0, 14, 0, 2);
        l.addView(ttl);
        pinSub = Ui.t(this, "", 14, Ui.MUTED, false);
        pinSub.setGravity(Gravity.CENTER);
        l.addView(pinSub);
        pinDots = Ui.h(this);
        pinDots.setGravity(Gravity.CENTER);
        for (int i = 0; i < 4; i++) {
            View d = new View(this);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Ui.dp(16), Ui.dp(16));
            dp.setMargins(Ui.dp(8), Ui.dp(22), Ui.dp(8), Ui.dp(10));
            pinDots.addView(d, dp);
        }
        l.addView(pinDots);
        pinMsg = Ui.t(this, "", 13, Ui.RED, false);
        pinMsg.setGravity(Gravity.CENTER);
        pinMsg.setMinHeight(Ui.dp(20));
        l.addView(pinMsg);
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "OK"};
        for (int r = 0; r < 4; r++) {
            LinearLayout row = Ui.h(this);
            row.setGravity(Gravity.CENTER);
            for (int k = 0; k < 3; k++) {
                final String key = keys[r * 3 + k];
                TextView b = Ui.t(this, key, 22, Ui.TEXT, true);
                b.setGravity(Gravity.CENTER);
                b.setBackground(Ui.rr(Ui.SURFACE2, Ui.LINE, 34));
                Ui.tap(b, new Runnable() { @Override public void run() { pinKey(key); } });
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.dp(68), Ui.dp(68));
                bp.setMargins(Ui.dp(10), Ui.dp(6), Ui.dp(10), Ui.dp(6));
                row.addView(b, bp);
            }
            l.addView(row);
        }
        if (mode.equals("unlock")) {
            TextView fg = Ui.t(this, "PIN ભૂલ્યા?", 14, Ui.ACCENT, true);
            Ui.pad(fg, 10, 14, 10, 6);
            Ui.tap(fg, new Runnable() {
                @Override public void run() {
                    sheets.confirm("પિન ભૂલી ગયા? આગળ વધશો તો બધો ડેટા કાઢી નાખીને પિન હટાવાશે. (બેકઅપ ફાઇલ હોય તો પછી પાછી લાવી શકાય.)", "હા, ડેટા કાઢીને રીસેટ કરો", new Runnable() {
                        @Override public void run() {
                            db.parties.clear(); db.txns.clear(); db.products.clear();
                            db.settings.pinHash = ""; db.settings.bio = "";
                            save(); hidePin(); unlocked = true; render(); toast("એપ રીસેટ થઈ");
                        }
                    });
                }
            });
            l.addView(fg);
            if (!db.settings.bio.isEmpty() && bioAvailable()) {
                TextView bio = Ui.t(this, "☝ ફિંગરપ્રિન્ટથી ખોલો", 14, Ui.ACCENT, true);
                Ui.pad(bio, 10, 6, 10, 6);
                Ui.tap(bio, new Runnable() { @Override public void run() { lockBio(); } });
                l.addView(bio);
            }
        } else {
            TextView cn = Ui.t(this, "રદ કરો", 14, Ui.ACCENT, true);
            Ui.pad(cn, 10, 14, 10, 6);
            Ui.tap(cn, new Runnable() { @Override public void run() { hidePin(); } });
            l.addView(cn);
        }
        lockView = l;
        root.addView(l, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pinUi(null, false);
        if (mode.equals("unlock") && !db.settings.bio.isEmpty() && bioAvailable()) {
            l.postDelayed(new Runnable() { @Override public void run() { if (lockView != null && pinMode.equals("unlock")) lockBio(); } }, 350);
        }
    }

    private void lockBio() {
        bioAuth("Mera Hisab ખોલો", new BoolCb() {
            @Override public void done(boolean ok) { if (ok && lockView != null && pinMode.equals("unlock")) { Runnable d = pinDone; hidePin(); unlocked = true; if (d != null) d.run(); } }
        });
    }

    private void pinUi(String msg, boolean err) {
        String sub;
        switch (pinMode) {
            case "unlock": sub = "અનલૉક કરવા સિક્યુરિટી પિન દાખલ કરો"; break;
            case "verify": sub = "હાલનો પિન દાખલ કરો"; break;
            case "set1": sub = "નવો 4 અંકનો પિન દાખલ કરો"; break;
            default: sub = "ફરી એ જ પિન દાખલ કરો";
        }
        pinSub.setText(sub);
        pinMsg.setText(msg == null ? "" : msg);
        for (int i = 0; i < pinDots.getChildCount(); i++) {
            pinDots.getChildAt(i).setBackground(Ui.rr(i < pinBuf.length() ? Ui.ACCENT : 0, err ? Ui.RED : Ui.ACCENT, 8));
        }
    }

    void hidePin() {
        if (lockView != null) { root.removeView(lockView); lockView = null; }
    }

    private void pinKey(String v) {
        if (lockView == null) return;
        if (v.equals("C")) { pinBuf = ""; pinUi(null, false); return; }
        if (v.equals("OK")) { if (pinBuf.length() == 4) pinSubmit(); else pinUi("4 અંકનો પિન જોઈએ", true); return; }
        if (pinBuf.length() >= 4) return;
        pinBuf += v;
        pinUi(null, false);
        if (pinBuf.length() == 4) pinSubmit();
    }

    private void pinSubmit() {
        String b = pinBuf;
        if (pinMode.equals("unlock") || pinMode.equals("verify")) {
            if (hashPin(b).equals(db.settings.pinHash)) {
                Runnable d = pinDone;
                hidePin();
                unlocked = true;
                if (d != null) d.run();
                return;
            }
            pinBuf = "";
            pinUi("ખોટો પિન, ફરી પ્રયત્ન કરો", true);
            return;
        }
        if (pinMode.equals("set1")) { pinFirst = b; pinBuf = ""; pinMode = "set2"; pinUi(null, false); return; }
        if (!b.equals(pinFirst)) { pinMode = "set1"; pinBuf = ""; pinFirst = ""; pinUi("પિન મેળ ખાતો નથી, ફરી શરૂ કરો", true); return; }
        db.settings.pinHash = hashPin(b);
        save();
        Runnable d = pinDone;
        hidePin();
        unlocked = true;
        toast("પિન સેટ થઈ ગયો");
        if (d != null) d.run(); else render();
    }

    // ================= login / logout =================
    boolean skipLogin() { return getSharedPreferences("mh", MODE_PRIVATE).getBoolean("skipLogin", false); }
    void setSkipLogin(boolean v) { getSharedPreferences("mh", MODE_PRIVATE).edit().putBoolean("skipLogin", v).apply(); }
    boolean needLogin() { return Api.configured() && !sync.loggedIn() && !skipLogin(); }
    boolean isStaff() { return sync != null && sync.isStaff(); }
    String who() { return sync != null && sync.loggedIn() ? sync.name : db.settings.owner; }

    private void closeLogin() {
        if (loginView != null) { root.removeView(loginView); loginView = null; }
    }

    void wipeLocal() {
        db.parties = new ArrayList<>(); db.txns = new ArrayList<>(); db.products = new ArrayList<>(); db.review = new ArrayList<>();
        db.remLog = new ArrayList<>(); db.vlog = new ArrayList<>(); db.learn = new ArrayList<>();
        Model.Settings o = db.settings, n = new Model.Settings();
        n.lang = o.lang; n.theme = o.theme; n.pinHash = o.pinHash; n.bio = o.bio; n.deviceId = o.deviceId; n.speak = o.speak;
        db.settings = n;
        saveLocalOnly();
    }

    private String loginErr(String e) {
        switch (e) {
            case "phone": return "૧૦ અંકનો સાચો મોબાઇલ નંબર લખો";
            case "name": return "તમારું નામ લખો";
            case "blocked": return "તમારો એક્સેસ બંધ છે. એડમિનનો સંપર્ક કરો";
            case "wrong": return "OTP ખોટો છે, ફરી લખો";
            case "nootp": return "હજી OTP બન્યો નથી. પહેલાં “OTP માંગો” દબાવો";
            case "locked": return "ઘણી ખોટી કોશિશ થઈ. નવો OTP માંગો";
            case "noaccess": return "આ નંબરને એક્સેસ નથી. પહેલાં “OTP માંગો” દબાવો";
            case "net": return "ઇન્ટરનેટ ચાલુ કરો";
            default: return "કંઈક ગડબડ થઈ, ફરી પ્રયત્ન કરો";
        }
    }

    void showLogin() {
        closeLogin();
        I18n.set(db.settings.lang);
        final LinearLayout l = Ui.v(this);
        l.setBackgroundColor(Ui.BG);
        l.setClickable(true);
        setupHero(l, "Mera Hisab", "લૉગિન કરો", null);
        LinearLayout lr = Ui.h(this);
        Ui.pad(lr, 14, 0, 14, 4);
        final String[][] langs = {{"gu-IN", "ગુજરાતી"}, {"hi-IN", "हिन्दी"}, {"en-IN", "English"}};
        for (final String[] g : langs) {
            lr.addView(Ui.chip(this, g[1], g[0].equals(db.settings.lang), new Runnable() {
                @Override public void run() { db.settings.lang = g[0]; I18n.set(g[0]); saveLocalOnly(); showLogin(); }
            }));
        }
        l.addView(lr);
        final EditText nm = Ui.fld(this, "તમારું નામ", lgName, Ui.IN_TEXT);
        final EditText ph = Ui.fld(this, "મોબાઇલ નંબર", lgPhone, Ui.IN_PHONE);
        final EditText sh = Ui.fld(this, "દુકાન / ધંધાનું નામ (નવા ખાતા માટે)", lgShop, Ui.IN_TEXT);
        final EditText otp = Ui.fld(this, "OTP", lgOtp, Ui.IN_NUM);
        for (EditText e : new EditText[]{nm, ph, sh, otp}) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) e.getLayoutParams();
            lp.setMargins(Ui.dp(14), Ui.dp(6), Ui.dp(14), 0);
        }
        l.addView(nm); l.addView(ph); l.addView(sh);
        boolean reqBusy = lgBusy.equals("req");
        l.addView(Ui.btn(this, reqBusy ? "⏳  OTP માંગી રહ્યા છીએ..." : (lgReqDone ? "✔  OTP માંગ્યો (ફરી માંગો)" : "OTP માંગો"), reqBusy ? "ghost" : (lgReqDone ? "green" : "primary"), new Runnable() {
            @Override public void run() {
                if (!lgBusy.isEmpty()) return;
                lgName = nm.getText().toString().trim(); lgPhone = ph.getText().toString().trim(); lgShop = sh.getText().toString().trim();
                lgOtp = otp.getText().toString().trim();
                lgBusy = "req"; lgReqDone = false; lgMsg = ""; lgWa = ""; showLogin();
                final Map<String, Object> q = Api.args("p_name", lgName, "p_phone", lgPhone, "p_shop", lgShop);
                new Thread(new Runnable() {
                    @Override public void run() {
                        final Map<String, Object> r = Api.call("mh_request", q);
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                lgBusy = "";
                                if (!Api.ok(r)) { lgMsg = loginErr(Api.s(r, "err")) + (Api.s(r, "detail").isEmpty() ? "" : "\n(" + Api.s(r, "detail") + ")"); lgWa = ""; showLogin(); return; }
                                lgReqDone = true;
                                String to;
                                if (Api.s(r, "status").equals("pending")) {
                                    lgMsg = "તમારી વિનંતિ એડમિનને મોકલાઈ. એડમિન OTP આપે ત્યારે નીચે લખીને લૉગિન કરો.";
                                    to = Api.s(r, "admin_phone");
                                } else if (Api.s(r, "role").equals("staff")) {
                                    lgMsg = "તમારા માલિક પાસેથી OTP લો અને નીચે લખીને લૉગિન કરો.";
                                    to = Api.s(r, "owner_phone");
                                } else {
                                    lgMsg = "એડમિન પાસેથી OTP લો અને નીચે લખીને લૉગિન કરો.";
                                    to = Api.s(r, "admin_phone");
                                }
                                lgWa = to.isEmpty() ? "" : waLink(to, "Mera Hisab login OTP joiye che.\nNaam: " + lgName + "\nNumber: " + lgPhone);
                                showLogin();
                            }
                        });
                    }
                }).start();
            }
        }));
        if (!lgMsg.isEmpty()) {
            TextView m = Ui.t(this, lgMsg, 14, Ui.TEXT, false);
            m.setBackground(Ui.rr(Ui.SOFT, Ui.LILAC, 14));
            Ui.pad(m, 14, 10, 14, 10);
            LinearLayout.LayoutParams mp = Ui.fillW();
            mp.setMargins(Ui.dp(14), Ui.dp(10), Ui.dp(14), 0);
            l.addView(m, mp);
        }
        if (!lgWa.isEmpty()) {
            l.addView(Ui.btn(this, "WhatsApp પર OTP માટે મેસેજ કરો", "green", new Runnable() { @Override public void run() { openUrl(lgWa); } }));
        }
        l.addView(Ui.label(this, "OTP"));
        l.addView(otp);
        boolean loginBusy = lgBusy.equals("login");
        l.addView(Ui.btn(this, loginBusy ? "⏳  લૉગિન થઈ રહ્યું છે..." : "લૉગિન કરો", loginBusy ? "ghost" : "primary", new Runnable() {
            @Override public void run() {
                if (!lgBusy.isEmpty()) return;
                lgName = nm.getText().toString().trim(); lgPhone = ph.getText().toString().trim(); lgShop = sh.getText().toString().trim();
                lgOtp = otp.getText().toString().trim();
                if (lgPhone.isEmpty() || lgOtp.isEmpty()) { toast("નંબર અને OTP લખો"); return; }
                lgBusy = "login"; lgMsg = ""; showLogin();
                final Map<String, Object> q = Api.args("p_phone", lgPhone, "p_otp", lgOtp, "p_device", Build.MODEL == null ? "" : Build.MODEL, "p_ver", versionName());
                new Thread(new Runnable() {
                    @Override public void run() {
                        final Map<String, Object> r = Api.call("mh_login", q);
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                lgBusy = "";
                                if (!Api.ok(r)) { lgMsg = loginErr(Api.s(r, "err")) + (Api.s(r, "detail").isEmpty() ? "" : "\n(" + Api.s(r, "detail") + ")"); showLogin(); return; }
                                lgReqDone = false;
                                String prev = sync.bizId;
                                sync.setSession(r);
                                if (sync.isStaff() || (!prev.isEmpty() && !prev.equals(sync.bizId))) wipeLocal();
                                lgOtp = ""; lgMsg = ""; lgWa = "";
                                afterLogin();
                            }
                        });
                    }
                }).start();
            }
        }));
        TextView pv = Ui.t(this, "એડમિન સપોર્ટ માટે તમારો હિસાબ જોઈ શકે છે.", 12, Ui.MUTED, false);
        pv.setGravity(Gravity.CENTER);
        Ui.pad(pv, 14, 8, 14, 4);
        l.addView(pv);
        l.addView(Ui.btn(this, "હમણાં લૉગિન વગર વાપરો", "ghost", new Runnable() { @Override public void run() { setSkipLogin(true); rebuild(); } }));
        TextView sv = Ui.t(this, "⚙  સર્વર સેટઅપ", 13, Ui.MUTED, true);
        sv.setGravity(Gravity.CENTER);
        Ui.pad(sv, 10, 18, 10, 18);
        Ui.tap(sv, new Runnable() { @Override public void run() { sheets.serverSheet(); } });
        l.addView(sv);
        ScrollView scv = new ScrollView(this);
        scv.setBackgroundColor(Ui.BG);
        scv.setFillViewport(true);
        scv.addView(l);
        loginView = scv;
        root.addView(scv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (lockView != null) { root.removeView(lockView); root.addView(lockView); }
    }

    /** After a good login: bring the business data down (and push this phone's old data if it is the owner's), then continue. */
    void afterLogin() {
        toast("ડેટા સિંક થઈ રહ્યો છે...");
        sync.run(new Sync.Done() {
            @Override public void done(boolean ok, String err) {
                Model.Settings st = db.settings;
                if (!st.setupDone) {
                    if (sync.isStaff()) { st.setupDone = true; st.owner = sync.name; }
                    else if (!db.parties.isEmpty() || !db.txns.isEmpty() || !st.shop.isEmpty()) st.setupDone = true;
                    else { st.owner = sync.name; if (!sync.bizName.endsWith(" ni dukan")) st.shop = sync.bizName; }
                }
                if (st.owner.isEmpty()) st.owner = sync.name;
                save();
                rebuild();
                if (!ok) toast("સિંક ન થયું, ઇન્ટરનેટ તપાસો");
            }
        });
    }

    void onSessionExpired() {
        sync.clear();
        wipeLocal();
        lgMsg = "તમારું સેશન પૂરું થયું અથવા એક્સેસ બંધ છે. ફરી લૉગિન કરો";
        rebuild();
    }

    void logout() {
        sheets.confirm("લૉગઆઉટ કરવું છે? આ ફોનમાંથી ડેટા દૂર થશે (સર્વર પર સુરક્ષિત રહેશે).", "હા, લૉગઆઉટ", new Runnable() {
            @Override public void run() {
                toast("સિંક થઈ રહ્યું છે...");
                sync.run(new Sync.Done() {
                    @Override public void done(boolean ok, String err) {
                        if (!ok && !"auth".equals(err)) {
                            sheets.confirm("ઇન્ટરનેટ નથી. છેલ્લા ફેરફારો સર્વર પર નથી પહોંચ્યા. તો પણ લૉગઆઉટ કરવું છે?", "હા, તો પણ", new Runnable() { @Override public void run() { finishLogout(); } });
                            return;
                        }
                        if (ok) finishLogout();
                    }
                });
            }
        });
    }

    private void finishLogout() {
        final String tok = sync.token;
        new Thread(new Runnable() { @Override public void run() { Api.call("mh_logout", Api.args("p_tok", tok)); } }).start();
        sync.clear();
        wipeLocal();
        lgMsg = ""; lgWa = "";
        rebuild();
    }
}
