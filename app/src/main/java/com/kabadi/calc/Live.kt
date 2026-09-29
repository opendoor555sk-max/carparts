package com.kabadi.calc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Instant delivery: a small always-on connection to the relay (ntfy stream) for this phone's own topic.
 * The moment somebody sends a hisab / a line for this number, the server pushes it (1-2 seconds) and the app
 * fetches it. Needs a small permanent notification (Android rule for anything that runs all the time).
 */
object Live {
    /** set by the visible screen: called (on any thread) when something new arrived */
    @Volatile var onNew: (() -> Unit)? = null
    private const val PREF = "kabadi_share"
    fun enabled(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("live", true)
    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean("live", on).apply()
        if (on) start(ctx) else stop(ctx)
    }
    fun start(ctx: Context) {
        try {
            if (!enabled(ctx) || Share.myMobile(ctx).length != 10) return
            val i = Intent(ctx, LiveService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        } catch (_: Exception) {}
    }
    fun stop(ctx: Context) { try { ctx.stopService(Intent(ctx, LiveService::class.java)) } catch (_: Exception) {} }
}

class LiveService : Service() {
    @Volatile private var run = false
    private var th: Thread? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("live", L.t("live_t"), NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getActivity(this, 17, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "live") else @Suppress("DEPRECATION") Notification.Builder(this)
        b.setSmallIcon(R.drawable.ic_launcher).setContentTitle("🟢 " + L.t("live_t")).setContentText(L.t("live_d")).setContentIntent(open).setOngoing(true)
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(21, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(21, b.build())
        } catch (_: Exception) { stopSelf(); return START_NOT_STICKY }
        if (!run) { run = true; th = Thread { loop() }.also { it.isDaemon = true; it.start() } }
        return START_STICKY
    }

    override fun onDestroy() { run = false; th?.interrupt(); super.onDestroy() }

    private fun loop() {
        var wait = 2000L
        while (run) {
            val me = Share.myMobile(this)
            if (me.length != 10 || !Live.enabled(this)) { stopSelf(); return }
            val topic = Share.topic(me)
            try {
                // first catch up on what we missed, then listen for new messages
                fetch()
                val c = URL("https://ntfy.sh/$topic/json").openConnection() as HttpURLConnection
                c.connectTimeout = 15000; c.readTimeout = 120000
                if (c.responseCode == 200) {
                    wait = 2000L
                    val r = c.inputStream.bufferedReader()
                    while (run) {
                        val line = r.readLine() ?: break
                        if (Share.myMobile(this) != me) break // number changed: reconnect on the new topic
                        val ev = try { JSONObject(line).optString("event") } catch (_: Exception) { "" }
                        if (ev == "message") {
                            // the message may come in several parts: a moment, then fetch once
                            try { Thread.sleep(400) } catch (_: InterruptedException) {}
                            fetch()
                        }
                    }
                }
                c.disconnect()
            } catch (_: Exception) {}
            if (!run) return
            try { Thread.sleep(wait) } catch (_: InterruptedException) {}
            wait = Math.min(wait * 2, 60000L)
        }
    }

    private fun fetch() {
        val n = try { Reminders.fetchShared(this, Live.onNew == null) } catch (_: Exception) { 0 }
        if (n > 0) Live.onNew?.invoke()
    }
}
