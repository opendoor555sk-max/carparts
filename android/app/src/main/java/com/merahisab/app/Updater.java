package com.merahisab.app;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** In-app update: one button -> download inside the app -> installer opens (one tap on Android's own Install). */
final class Updater {
    private Updater() {}
    private static final String ACTION = "com.merahisab.app.INSTALL_RESULT";
    private static boolean busy = false, regd = false;
    private static Ui.Sheet progress;
    private static TextView progText;

    private static String base(Context c) throws Exception {
        InputStream in = c.getAssets().open("repo.txt");
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[512]; int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        in.close();
        String repo = new String(bo.toByteArray(), StandardCharsets.UTF_8).trim();
        if (repo.isEmpty() || repo.equals("placeholder")) throw new Exception("no repo");
        return "https://github.com/" + repo + "/releases/download/mera-latest/";
    }

    /** quiet = launch-time check: stays silent unless a new version exists. */
    static void check(final MainActivity a, final boolean quiet) {
        if (busy) return;
        if (!quiet) a.toast("તપાસી રહ્યા છીએ...");
        new Thread(() -> {
            try {
                String base = base(a);
                HttpURLConnection c = (HttpURLConnection) new URL(base + "version.txt?t=" + System.currentTimeMillis()).openConnection();
                c.setConnectTimeout(10000); c.setReadTimeout(15000); c.setUseCaches(false); c.setInstanceFollowRedirects(true);
                if (c.getResponseCode() != 200) throw new Exception("http");
                InputStream in = c.getInputStream();
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] b = new byte[256]; int n;
                while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                c.disconnect();
                final long remote = Long.parseLong(new String(bo.toByteArray(), StandardCharsets.UTF_8).trim());
                a.runOnUiThread(() -> {
                    if (remote > a.versionCode()) a.sheets.updatePrompt(remote);
                    else if (!quiet) a.toast("તમારી એપ નવીનતમ છે");
                });
            } catch (Exception e) {
                if (!quiet) a.runOnUiThread(() -> a.toast("અપડેટ ચકાસી શકાયું નહીં, ઇન્ટરનેટ તપાસો"));
            }
        }).start();
    }

    private static void say(final MainActivity a, final String s) {
        a.runOnUiThread(() -> { if (progText != null) progText.setText(I18n.tr(s)); });
    }

    private static void endProgress(MainActivity a) {
        busy = false;
        if (progress != null) { progress.dismiss(); progress = null; progText = null; }
    }

    /** The one-button flow. */
    static void install(final MainActivity a) {
        if (busy) return;
        if (!a.getPackageManager().canRequestPackageInstalls()) {
            a.sheets.close();
            a.toast("એક વખત આ પરવાનગી ચાલુ કરો, પછી પાછા આવી ફરી અપડેટ દબાવો");
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
            } catch (Exception e) { a.toast("સેટિંગ્સ ખુલી નહીં"); }
            return;
        }
        busy = true;
        register(a);
        a.sheets.close();
        progress = new Ui.Sheet(a);
        progress.title(I18n.tr("અપડેટ થઈ રહ્યું છે"));
        progText = Ui.t(a, I18n.tr("ડાઉનલોડ શરૂ થઈ રહ્યું છે..."), 16, Ui.TEXT, false);
        progText.setGravity(Gravity.CENTER);
        Ui.pad(progText, 8, 14, 8, 14);
        progress.add(progText);
        progress.dlg.setCancelable(false);
        progress.show();
        new Thread(() -> {
            try {
                String url = base(a) + "Mera-Hisab.apk?t=" + System.currentTimeMillis();
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15000); c.setReadTimeout(30000); c.setInstanceFollowRedirects(true);
                if (c.getResponseCode() != 200) throw new Exception("http " + c.getResponseCode());
                long total = c.getContentLengthLong();
                PackageInstaller pi = a.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                sp.setAppPackageName(a.getPackageName());
                if (Build.VERSION.SDK_INT >= 31) sp.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                int id = pi.createSession(sp);
                PackageInstaller.Session s = pi.openSession(id);
                InputStream in = c.getInputStream();
                OutputStream out = s.openWrite("mera.apk", 0, total > 0 ? total : -1);
                byte[] buf = new byte[65536];
                long done = 0, last = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    if (done - last > 150000) {
                        last = done;
                        say(a, I18n.tr("ડાઉનલોડ થઈ રહ્યું છે...") + " " + (total > 0 ? (done * 100 / total) + "%" : (done / 1048576) + " MB"));
                    }
                }
                s.fsync(out);
                out.close();
                in.close();
                c.disconnect();
                say(a, "ઇન્સ્ટોલ થઈ રહ્યું છે...");
                Intent it = new Intent(ACTION).setPackage(a.getPackageName());
                PendingIntent pend = PendingIntent.getBroadcast(a, id, it, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                s.commit(pend.getIntentSender());
                s.close();
            } catch (Exception e) {
                a.runOnUiThread(() -> { endProgress(a); a.toast("અપડેટ ડાઉનલોડ ન થયું, ઇન્ટરનેટ તપાસી ફરી પ્રયત્ન કરો"); });
            }
        }).start();
    }

    private static void register(final MainActivity a) {
        if (regd) return;
        regd = true;
        BroadcastReceiver r = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) {
                int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
                if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirm != null) {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try { a.startActivity(confirm); } catch (Exception e) { endProgress(a); a.toast("ઇન્સ્ટોલ ખુલ્યું નહીં"); }
                    }
                } else if (st == PackageInstaller.STATUS_SUCCESS) {
                    endProgress(a);
                } else {
                    endProgress(a);
                    a.toast("અપડેટ ઇન્સ્ટોલ ન થયું, ફરી પ્રયત્ન કરો");
                }
            }
        };
        IntentFilter f = new IntentFilter(ACTION);
        if (Build.VERSION.SDK_INT >= 33) a.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
        else a.registerReceiver(r, f);
    }
}
