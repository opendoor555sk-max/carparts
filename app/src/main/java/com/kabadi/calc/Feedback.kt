package com.kabadi.calc

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * "Tell your problem": a user records his voice (and / or types) on the home page, it goes to the admin.
 * Sent through ntfy.sh in small pieces (the server keeps them 12 hours); the admin phone collects
 * them every ~15 minutes and keeps them for good, so he can read and listen in the admin screen.
 * Network calls: call from a background thread.
 */
object Feedback {
    val TOPIC = "kmh-f-" + Otp.topic("feedback")
    private const val CHUNK = 3500          // base64 characters per message (server limit is 4 KB)
    private const val PREF = "kabadi_fb"

    /**
     * user side: returns true when every piece reached the server.
     * A copy is kept on the phone, so if the internet is bad the rest is sent later, by itself (resendPending).
     */
    fun send(ctx: Context, name: String, mobile: String, dev: String, text: String, audio: ByteArray?, ms: Long): Boolean {
        val id = java.lang.Long.toString(System.currentTimeMillis(), 36) + dev
        val b64 = if (audio == null || audio.isEmpty()) "" else Base64.encodeToString(audio, Base64.NO_WRAP)
        val f = File(outDir(ctx), "$id.json")
        f.writeText(JSONObject().put("id", id).put("n", name).put("m", mobile).put("d", dev).put("t", text).put("ms", ms)
            .put("a", b64).put("sent", 0).put("t0", System.currentTimeMillis()).toString())
        return flush(f)
    }

    private fun outDir(ctx: Context) = File(ctx.filesDir, "fbout").apply { mkdirs() }

    private fun postRetry(o: JSONObject): Boolean {
        for (n in 0 until 3) { if (Relay.post(TOPIC, o)) return true; Thread.sleep(1500L * (n + 1)) }
        return false
    }

    /** sends the pieces not sent yet; false = stopped (no internet), the rest waits on the phone */
    private fun flush(f: File): Boolean {
        val j = try { JSONObject(f.readText()) } catch (_: Exception) { f.delete(); return true }
        val id = j.optString("id"); val b64 = j.optString("a")
        val parts = if (b64.isEmpty()) listOf("") else b64.chunked(CHUNK)
        var from = j.optInt("sent")
        // the server forgets after 12 h: after 10 h send everything again
        if (System.currentTimeMillis() - j.optLong("t0") > 10L * 3600_000L) { from = 0; j.put("t0", System.currentTimeMillis()) }
        for (i in from until parts.size) {
            val o = JSONObject().put("id", id).put("i", i).put("k", parts.size).put("a", parts[i])
            if (i == 0) o.put("n", j.optString("n")).put("m", j.optString("m")).put("d", j.optString("d")).put("t", j.optString("t")).put("ms", j.optLong("ms"))
            if (!postRetry(o)) { j.put("sent", i); f.writeText(j.toString()); return false }
        }
        f.delete()
        return true
    }

    /** user side, on app start: finish messages that were not sent completely */
    fun resendPending(ctx: Context) {
        val now = System.currentTimeMillis()
        outDir(ctx).listFiles()?.filter { it.name.endsWith(".json") }?.forEach { f ->
            if (now - f.lastModified() > 7L * 86400000L) f.delete() else flush(f)
        }
    }

    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private fun dir(ctx: Context) = File(ctx.filesDir, "fb").apply { mkdirs() }
    fun file(ctx: Context, id: String) = File(dir(ctx), "$id.3gp")

    /** admin side: every message kept on this phone (newest first) */
    fun list(ctx: Context): List<JSONObject> {
        val a = try { JSONArray(sp(ctx).getString("list", "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until a.length()).map { a.getJSONObject(it) }.sortedByDescending { it.optLong("at") }
    }

    private fun saveList(ctx: Context, l: List<JSONObject>) {
        sp(ctx).edit().putString("list", JSONArray(l).toString()).apply()
    }

    fun unheard(ctx: Context) = list(ctx).count { !it.optBoolean("heard") }

    fun markHeard(ctx: Context, id: String) {
        val l = list(ctx)
        l.firstOrNull { it.optString("id") == id }?.put("heard", true)
        saveList(ctx, l)
    }

    /** ids the admin deleted: the server still holds them for 12 h, so they must never come back */
    private fun deleted(ctx: Context): MutableSet<String> = sp(ctx).getStringSet("deleted", emptySet())!!.toMutableSet()

    fun delete(ctx: Context, id: String) {
        file(ctx, id).delete()
        val d = deleted(ctx); d.add(id)
        // keep only the newest 500 ids
        sp(ctx).edit().putStringSet("deleted", if (d.size > 500) d.toList().takeLast(500).toSet() else d).apply()
        saveList(ctx, list(ctx).filter { it.optString("id") != id })
    }

    /**
     * admin side: joins the pieces of complete messages; returns how many new ones arrived (-1 = no internet).
     * A message whose voice is still arriving (or lost) is listed at once with the text, marked "partial".
     */
    fun collect(ctx: Context): Int {
        val all = Relay.poll(TOPIC) ?: return -1
        val have = list(ctx).toMutableList()
        val gone = deleted(ctx)
        var added = 0; var changed = false
        all.groupBy { it.second.optString("id") }.forEach { (id, msgs) ->
            if (id.isEmpty() || id in gone) return@forEach
            val old = have.firstOrNull { it.optString("id") == id }
            if (old != null && !old.optBoolean("partial")) return@forEach
            val byI = msgs.map { it.second }.filter { it.has("k") }.distinctBy { it.optInt("i") }.associateBy { it.optInt("i") }
            val k = byI.values.firstOrNull()?.optInt("k") ?: return@forEach
            val head = byI[0] ?: return@forEach
            if (k <= 0) return@forEach
            val at = msgs.minOf { it.first.optLong("time") } * 1000
            if (!(0 until k).all { byI.containsKey(it) }) {
                if (old == null) {
                    have.add(JSONObject().put("id", id).put("n", head.optString("n")).put("m", head.optString("m")).put("d", head.optString("d"))
                        .put("t", head.optString("t")).put("ms", head.optLong("ms")).put("at", at).put("voice", false).put("heard", false)
                        .put("partial", true).put("got", byI.size).put("of", k))
                    added++; changed = true
                } else if (old.optInt("got") != byI.size) { old.put("got", byI.size); changed = true }
                return@forEach
            }
            val b64 = (0 until k).joinToString("") { byI[it]!!.optString("a") }
            val hasVoice = b64.isNotEmpty()
            if (hasVoice) try { file(ctx, id).writeBytes(Base64.decode(b64, Base64.NO_WRAP)) } catch (_: Exception) { return@forEach }
            if (old != null) have.remove(old)
            have.add(JSONObject().put("id", id).put("n", head.optString("n")).put("m", head.optString("m")).put("d", head.optString("d"))
                .put("t", head.optString("t")).put("ms", head.optLong("ms")).put("at", at).put("voice", hasVoice).put("heard", false))
            added++; changed = true
        }
        if (changed) saveList(ctx, have)
        return added
    }
}
