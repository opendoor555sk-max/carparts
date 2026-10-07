package com.merahisab.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Admin only: tells the admin when someone asks for an OTP (checks the server, shows a phone notification). */
final class Notifier {
    static volatile int pending = 0;
    private static int lastShown = 0;
    private static final int JOB_ID = 4711;

    /** Asks the server how many OTP requests wait. Call from a background thread. Returns count, or -1 if not admin / failed. */
    @SuppressWarnings("unchecked")
    static int check(Context c, boolean notify) {
        try {
            File f = new File(c.getFilesDir(), "sync.json");
            if (!f.exists()) return -1;
            FileInputStream in = new FileInputStream(f);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            in.close();
            Map<String, Object> m = (Map<String, Object>) Json.parse(new String(bo.toByteArray(), StandardCharsets.UTF_8));
            String tok = m.get("token") == null ? "" : String.valueOf(m.get("token"));
            if (tok.isEmpty() || !Boolean.TRUE.equals(m.get("admin"))) return -1;
            Api.load(c);
            if (!Api.configured()) return -1;
            Map<String, Object> r = Api.call("mh_admin_pending", Api.args("p_tok", tok));
            if (!Api.ok(r)) return -1;
            int cnt = (int) Double.parseDouble(Api.s(r, "n"));
            pending = cnt;
            if (cnt == 0) lastShown = 0;
            else if (notify && cnt != lastShown) {
                lastShown = cnt;
                post(c, "Mera Hisab: " + cnt + " નવી OTP વિનંતિ", Api.s(r, "names").isEmpty() ? "એડમિન પેનલ ખોલો" : Api.s(r, "names"));
            }
            return cnt;
        } catch (Exception e) { return -1; }
    }

    static void post(Context c, String title, String text) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel("otpreq", "OTP requests", NotificationManager.IMPORTANCE_HIGH));
            Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(c, "otpreq").setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle(title).setContentText(text).setContentIntent(pi).setAutoCancel(true).build();
            nm.notify(77, n);
        } catch (Exception e) { /* notifications may be blocked */ }
    }

    static void schedule(Context c) {
        try {
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == JOB_ID) return;
            js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, PendingJob.class))
                    .setPeriodic(15 * 60 * 1000L).setPersisted(true)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build());
        } catch (Exception e) { /* ignore */ }
    }

    static void cancel(Context c) {
        try { ((JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE)).cancel(JOB_ID); } catch (Exception e) { /* ignore */ }
    }

    public static class PendingJob extends JobService {
        @Override public boolean onStartJob(final JobParameters p) {
            new Thread(new Runnable() {
                @Override public void run() {
                    int r = check(getApplicationContext(), true);
                    if (r < 0) cancel(getApplicationContext()); // not admin any more / logged out
                    jobFinished(p, false);
                }
            }).start();
            return true;
        }
        @Override public boolean onStopJob(JobParameters p) { return true; }
    }
}
