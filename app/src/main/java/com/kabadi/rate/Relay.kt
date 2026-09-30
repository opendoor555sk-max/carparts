package com.kabadi.rate

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** ntfy.sh relay (free, no account). Messages stay 12 hours. Call from a background thread. */
object Relay {
    private const val HOST = "https://ntfy.sh/"

    fun post(topic: String, body: JSONObject): Boolean = try {
        val c = URL(HOST + topic).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 10000; c.readTimeout = 10000
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val ok = c.responseCode in 200..299
        c.disconnect(); ok
    } catch (_: Exception) { false }

    /** messages of the last 12 hours (oldest first); null = no internet */
    fun poll(topic: String): List<Pair<JSONObject, JSONObject>>? = try {
        val c = URL(HOST + topic + "/json?poll=1&since=12h").openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000
        val out = mutableListOf<Pair<JSONObject, JSONObject>>()
        if (c.responseCode == 200) c.inputStream.bufferedReader().forEachLine { line ->
            try {
                val o = JSONObject(line)
                if (o.optString("event") == "message") out.add(o to JSONObject(o.optString("message")))
            } catch (_: Exception) {}
        }
        c.disconnect(); out
    } catch (_: Exception) { null }
}
