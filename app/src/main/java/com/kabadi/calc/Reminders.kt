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
        if (Account.ENABLED && Account.isAdmin(ctx)) am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60_000, AlarmManager.INTERVAL_FIFTEEN_MINUTES, rq)
        else am.cancel(rq)
    }

    private fun sp(ctx: Context) = ctx.getSharedPreferences("kabadi_calc", Context.MODE_PRIVATE)
    fun autoSms(ctx: Context) = sp(ctx).getBoolean("autoSms", false)
    fun setAutoSms(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("autoSms", on).apply()

    /** one message for one person (all his items / shares) */
    fun message(list: List<Remind>): String {
        val sb = StringBuilder()
        val f = list.first()
        sb.append(L.t("rem_hello")).append(" ").append(f.name).append(",\n")
        sb.append(Store.owner.ifBlank { L.t("app") }).append(":\n")
        val today = endOfToday()
        list.forEach { r ->
            val v = listOf(r.h.vehicleInfo(), r.h.vehicle).filter { it.isNotBlank() }.joinToString(" ")
            sb.append("• ").append(java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US).format(java.util.Date(r.h.time)))
            if (v.isNotBlank()) sb.append(" ").append(v)
            if (r.partner != null) {
                val co = r.h.companyResult()
                sb.append(" (").append(L.t("haraji")).append("): ").append(L.t("co_result")).append(" ").append(if (co >= 0) L.t("profit") else L.t("loss")).append(" ").append(money(Math.abs(co)))
                sb.append(", ").append(L.t("rem_share")).append(" ").append(plain(r.partner.share)).append("% = ")
                sb.append(if (r.amount >= 0) L.t("profit") + " " + money(r.amount) else L.t("loss_pay") + " " + money(-r.amount))
            } else sb.append(": ").append(L.ln(r.line!!)).append(" – ").append(L.t("left")).append(" ").append(money(r.amount))
            sb.append(" | ").append(L.t("due")).append(" ").append(java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US).format(java.util.Date(r.due)))
            if (r.due <= today) sb.append(" ⚠")
            sb.append("\n")
        }
        if (Store.mobile.isNotBlank()) sb.append(Store.mobile)
        return sb.toString()
    }

    /** send today's reminders by SMS (once a day per person unless [force]); returns how many */
    fun sendSms(ctx: Context, force: Boolean = false): Int {
        if (Build.VERSION.SDK_INT >= 23 && ctx.checkSelfPermission("android.permission.SEND_SMS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return 0
        val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        val done = sp(ctx).getString("smsDay_$day", "").orEmpty().split(",").toMutableSet()
        val sms = try { @Suppress("DEPRECATION") android.telephony.SmsManager.getDefault() } catch (_: Exception) { return 0 }
        var n = 0
        reminders(Store.hisabs).filter { it.mobile.length == 10 }.groupBy { it.mobile }.forEach { (mob, list) ->
            if (!force && mob in done) return@forEach
            try { sms.sendMultipartTextMessage(mob, null, sms.divideMessage(message(list)), null, null); done.add(mob); n++ } catch (_: Exception) {}
        }
        sp(ctx).edit().putString("smsDay_$day", done.joinToString(",")).apply()
        return n
    }

    /** vasuli: notification (and SMS when switched on) every day from 3 days before the credit time */
    fun checkVasuli(ctx: Context) {
        val list = reminders(Store.hisabs)
        if (list.isEmpty()) return
        val sent = if (autoSms(ctx)) sendSms(ctx) else 0
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, L.t("khata"), NotificationManager.IMPORTANCE_HIGH))
        val people = list.groupBy { it.key.ifEmpty { it.name } }
        val open = PendingIntent.getActivity(ctx, 15, Intent(ctx, MainActivity::class.java).putExtra("remind", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CH) else @Suppress("DEPRECATION") Notification.Builder(ctx)
        b.setSmallIcon(R.drawable.ic_launcher).setContentTitle("🔔 " + people.size + " " + L.t("rem_banner"))
            .setContentText(if (sent > 0) "📩 SMS: $sent" else L.t("rem_tap"))
            .setStyle(Notification.BigTextStyle().bigText(people.values.take(8).joinToString("\n") { l -> l.first().name + " • " + money(l.sumOf { Math.abs(it.amount) }) }))
            .setContentIntent(open).setAutoCancel(true)
        try { nm.notify(9, b.build()) } catch (_: Exception) {}
    }

    fun endOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
    }.timeInMillis

    fun check(ctx: Context) {
        Store.load(ctx)
        try { checkVasuli(ctx) } catch (_: Exception) {}
        val due = openDues(Store.hisabs).filter { it.due != null && it.due <= endOfToday() }
        if (due.isEmpty()) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, L.t("khata"), NotificationManager.IMPORTANCE_HIGH))
        val lena = due.filter { it.lena }
        val title = L.t("n_title") + " (" + due.size + ")"
        val text = due.take(6).joinToString("\n") { d ->
            (if (d.lena) "⬇ " else "⬆ ") + (d.l.cName.ifBlank { d.h.party }) + " • " + L.ln(d.l) + " • " + money(d.left)
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
