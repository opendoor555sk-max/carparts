package com.nirmaan.calc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Saves preferences + memories so they survive closing the app. */
object Store {
    private const val FILE = "nirmaan_calc"
    private const val KEY = "state"

    private fun qj(q: Q): JSONObject? {
        if (!q.v.isFinite()) return null
        return JSONObject().put("t", q.t.toString()).put("v", q.v).put("d", q.d)
            .put("u", q.u).put("f", q.f).put("dms", q.dms)
    }

    private fun jq(o: JSONObject) = Q(
        o.getString("t")[0], o.getDouble("v"), o.optInt("d"), o.optString("u"),
        o.optString("f", "frac"), o.optBoolean("dms")
    )

    private fun mj(m: Mem): JSONObject {
        val o = JSONObject().put("v", m.v).put("ts", m.ts).put("u", m.u).put("f", m.f)
        m.ang?.let { o.put("ang", it) }
        m.len?.let { o.put("len", it) }
        return o
    }

    private fun jm(o: JSONObject) = Mem(
        o.optDouble("v", 0.0), o.optInt("ts"), o.optString("u"), o.optString("f"),
        if (o.has("ang")) o.getDouble("ang") else null,
        if (o.has("len")) o.getDouble("len") else null
    )

    fun save(ctx: Context, e: Engine) {
        try {
            val o = JSONObject()
            o.put("res", e.res).put("metric", e.metric).put("trig", e.trig).put("light", e.light).put("TS", e.TS)
            e.triU?.let { o.put("triU", it) }
            val m = JSONObject(); e.M.forEach { (k, q) -> qj(q)?.let { m.put(k, it) } }; o.put("M", m)
            val t = JSONObject(); e.T.forEach { (k, v) -> if (v.v.isFinite()) t.put(k, mj(v)) }; o.put("T", t)
            val g = JSONObject(); e.G.forEach { (k, v) -> if (v.v.isFinite()) g.put(k, mj(v)) }; o.put("G", g)
            val tp = JSONArray(); e.tape.takeLast(100).forEach { tp.put(it) }; o.put("tape", tp)
            ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun load(ctx: Context, e: Engine) {
        try {
            val s = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null) ?: return
            val o = JSONObject(s)
            e.res = o.optInt("res", 16)
            e.metric = o.optBoolean("metric")
            e.trig = o.optBoolean("trig")
            e.light = o.optBoolean("light")
            e.TS = o.optInt("TS")
            e.triU = if (o.has("triU")) o.getString("triU") else null
            o.optJSONObject("M")?.let { m -> m.keys().forEach { k -> e.M[k] = jq(m.getJSONObject(k)) } }
            o.optJSONObject("T")?.let { m -> m.keys().forEach { k -> e.T[k] = jm(m.getJSONObject(k)) } }
            o.optJSONObject("G")?.let { m -> m.keys().forEach { k -> e.G[k] = jm(m.getJSONObject(k)) } }
            o.optJSONArray("tape")?.let { a -> for (i in 0 until a.length()) e.tape.add(a.getString(i)) }
        } catch (_: Exception) {
        }
    }
}
