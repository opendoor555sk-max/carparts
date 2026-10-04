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
import android.os.Bundle;
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
    String kq = "", kf = "all", sort = "bal", rday, rmonth, rkind = "all", balMode = "full", setOpen = "biz";

    private FrameLayout root;
    private ScrollView scroll;
    private LinearLayout navBar;
    private View lockView;
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
    private boolean listening = false, gotText = false;
    private String pendingLangStart = null;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private BoolCb bioCb;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        day = Fmt.today(); month = day.substring(0, 7); rday = day; rmonth = month;
        loadData();
        sheets = new Sheets(this);
        screens = new Screens(this);
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override public void onInit(int st) { ttsReady = (st == TextToSpeech.SUCCESS); }
        });
        rebuild();
        if (!db.settings.pinHash.isEmpty()) showPin("unlock", null);
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
        render();
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
        if (lockView != null) return;
        if (partyId != null) { partyId = null; render(); return; }
        if (sub != null) { sub = null; render(); return; }
        if (!tab.equals("home")) { go("home"); return; }
        finish();
    }

    @Override
    protected void onStop() {
        stoppedAt = System.currentTimeMillis();
        stopListen();
        super.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
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

    // ================= update check (via GitHub) =================
    void checkUpdate() {
        toast("તપાસી રહ્યા છીએ...");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final String repo = new String(readAll(getAssets().open("repo.txt")), StandardCharsets.UTF_8).trim();
                    if (repo.isEmpty() || repo.equals("placeholder")) throw new Exception("no repo");
                    final String base = "https://github.com/" + repo + "/releases/download/mera-latest/";
                    HttpURLConnection c = (HttpURLConnection) new URL(base + "version.txt?t=" + System.currentTimeMillis()).openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(15000);
                    c.setUseCaches(false);
                    c.setInstanceFollowRedirects(true);
                    if (c.getResponseCode() != 200) throw new Exception("http");
                    String s = new String(readAll(c.getInputStream()), StandardCharsets.UTF_8).trim();
                    c.disconnect();
                    final long remote = Long.parseLong(s);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (remote > versionCode()) {
                                sheets.confirm("નવું વર્ઝન મળ્યું છે. હમણાં ડાઉનલોડ કરવું છે?", "હા, ડાઉનલોડ કરો", new Runnable() { @Override public void run() { openUrl(base + "Mera-Hisab.apk"); } });
                            } else toast("તમારી એપ નવીનતમ છે");
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(new Runnable() { @Override public void run() { toast("અપડેટ ચકાસી શકાયું નહીં, ઇન્ટરનેટ તપાસો"); } });
                }
            }
        }).start();
    }

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
                    if (cb != null) cb.fin(l.get(0).trim());
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
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
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
}
