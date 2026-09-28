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
    private const val CHUNK = 3000          // base64 characters per message (server limit is 4 KB)
    private const val PREF = "kabadi_fb"

    /** user side: returns true when every piece reached the server */
    fun send(name: String, mobile: String, dev: String, text: String, audio: ByteArray?, ms: Long): Boolean {
        val id = java.lang.Long.toString(System.currentTimeMillis(), 36) + dev
        val b64 = if (audio == null || audio.isEmpty()) "" else Base64.encodeToString(audio, Base64.NO_WRAP)
        val parts = if (b64.isEmpty()) listOf("") else b64.chunked(CHUNK)
        parts.forEachIndexed { i, p ->
            val o = JSONObject().put("id", id).put("i", i).put("k", parts.size).put("a", p)
            if (i == 0) o.put("n", name).put("m", mobile).put("d", dev).put("t", text).put("ms", ms)
            var ok = Relay.post(TOPIC, o)
            if (!ok) { Thread.sleep(1500); ok = Relay.post(TOPIC, o) }
            if (!ok) return false
        }
        return true
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

    fun delete(ctx: Context, id: String) {
        file(ctx, id).delete()
        saveList(ctx, list(ctx).filter { it.optString("id") != id })
    }

    /** admin side: joins the pieces of complete messages; returns how many new ones arrived (-1 = no internet) */
    fun collect(ctx: Context): Int {
        val all = Relay.poll(TOPIC) ?: return -1
        val have = list(ctx).toMutableList()
        val known = have.map { it.optString("id") }.toHashSet()
        var added = 0
        all.groupBy { it.second.optString("id") }.forEach { (id, msgs) ->
            if (id.isEmpty() || id in known) return@forEach
            val parts = msgs.map { it.second }.distinctBy { it.optInt("i") }
            val k = parts.firstOrNull()?.optInt("k") ?: return@forEach
            if (k <= 0 || parts.size < k) return@forEach           // still arriving
            val head = parts.firstOrNull { it.optInt("i") == 0 } ?: return@forEach
            val b64 = parts.sortedBy { it.optInt("i") }.joinToString("") { it.optString("a") }
            val hasVoice = b64.isNotEmpty()
            if (hasVoice) try { file(ctx, id).writeBytes(Base64.decode(b64, Base64.NO_WRAP)) } catch (_: Exception) { return@forEach }
            val at = msgs.minOf { it.first.optLong("time") } * 1000
            have.add(JSONObject().put("id", id).put("n", head.optString("n")).put("m", head.optString("m")).put("d", head.optString("d"))
                .put("t", head.optString("t")).put("ms", head.optLong("ms")).put("at", at).put("voice", hasVoice).put("heard", false))
            known.add(id); added++
        }
        if (added > 0) saveList(ctx, have)
        return added
    }
}
