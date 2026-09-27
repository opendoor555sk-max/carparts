package com.kabadi.calc

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/** Every day at 10 AM: notification for credits whose muddat is today or already over. */
object Reminders {
    private const val CH = "udhaar_muddat"

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(ctx, 11, Intent(ctx, DueReceiver::class.java).setAction("com.kabadi.calc.DUE"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, c.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
        // admin phone: look for new OTP requests about every 15 minutes
        val rq = PendingIntent.getBroadcast(ctx, 13, Intent(ctx, DueReceiver::class.java).setAction("com.kabadi.calc.REQ"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (Account.isAdmin(ctx)) am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60_000, AlarmManager.INTERVAL_FIFTEEN_MINUTES, rq)
        else am.cancel(rq)
    }

    fun endOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
    }.timeInMillis

    fun check(ctx: Context) {
        Store.load(ctx)
        val due = openDues(Store.hisabs).filter { it.due != null && it.due <= endOfToday() }
        if (due.isEmpty()) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, L.t("khata"), NotificationManager.IMPORTANCE_HIGH))
        val lena = due.filter { it.lena }
        val title = L.t("n_title") + " (" + due.size + ")"
        val text = due.take(6).joinToString("\n") { d ->
            (if (d.lena) "⬇ " else "⬆ ") + (d.l.cName.ifBlank { d.h.party }) + " • " + d.l.name + " • " + money(d.left)
        }
        val open = PendingIntent.getActivity(ctx, 12, Intent(ctx, MainActivity::class.java).putExtra("khata", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CH) else @Suppress("DEPRECATION") Notification.Builder(ctx)
        b.setSmallIcon(R.drawable.ic_launcher).setContentTitle(title)
            .setContentText(L.t("lena_baaki").substringBefore(" (") + ": " + money(lena.sumOf { it.left }))
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true)
        try { nm.notify(7, b.build()) } catch (_: Exception) {}
    }
}

class DueReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        when (i.action) {
            Intent.ACTION_BOOT_COMPLETED -> Reminders.schedule(ctx)
            "com.kabadi.calc.REQ" -> { val r = goAsync(); Thread { try { Relay.notifyAdmin(ctx) } finally { r.finish() } }.start() }
            else -> Reminders.check(ctx)
        }
    }
}
