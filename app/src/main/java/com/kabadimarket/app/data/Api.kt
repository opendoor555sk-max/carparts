package com.kabadimarket.app.data

import android.os.Handler
import android.os.Looper
import com.kabadimarket.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Error from the server (or network), with a message that can be shown to the user. */
class ApiException(
    val status: Int,
    message: String,
    val code: String? = null,
    val detail: JSONObject? = null,
) : Exception(message)

/**
 * Talks to the existing Kabadi Market server (the same one the old app uses),
 * so all data, stock and users stay exactly the same.
 */
object Api {
    private val BASE = BuildConfig.API_BASE
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    // The free server "sleeps" when unused and can take ~1 minute to wake up.
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile
    var token: String? = null

    /** Called when the server says the login is no longer valid. */
    var onUnauthorized: (() -> Unit)? = null

    suspend fun get(path: String, query: Map<String, String?> = emptyMap()): Any =
        call("GET", path, query, null)

    suspend fun post(path: String, body: JSONObject = JSONObject(), query: Map<String, String?> = emptyMap()): Any =
        call("POST", path, query, body)

    suspend fun patch(path: String, body: JSONObject, query: Map<String, String?> = emptyMap()): Any =
        call("PATCH", path, query, body)

    suspend fun delete(path: String, query: Map<String, String?> = emptyMap()): Any =
        call("DELETE", path, query, null)

    suspend fun getObj(path: String, query: Map<String, String?> = emptyMap()): JSONObject =
        get(path, query) as? JSONObject ?: JSONObject()

    suspend fun getArr(path: String, query: Map<String, String?> = emptyMap()): JSONArray =
        get(path, query) as? JSONArray ?: JSONArray()

    /** Downloads a file (e.g. an Excel export) as raw bytes. */
    suspend fun download(path: String, query: Map<String, String?> = emptyMap()): ByteArray =
        withContext(Dispatchers.IO) {
            val req = baseRequest(path, query).get().build()
            try {
                client.newCall(req).execute().use { resp ->
                    val bytes = resp.body?.bytes() ?: ByteArray(0)
                    if (!resp.isSuccessful) throw handleError(resp.code, String(bytes, Charsets.UTF_8))
                    bytes
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: IOException) {
                throw networkError(e)
            }
        }

    /** Encodes one piece of a URL path, e.g. a part number or id. */
    fun seg(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun baseRequest(path: String, query: Map<String, String?>): Request.Builder {
        val url = (BASE + path).toHttpUrl().newBuilder().apply {
            query.forEach { (k, v) -> if (!v.isNullOrBlank()) addQueryParameter(k, v) }
        }.build()
        return Request.Builder().url(url).header("Accept", "application/json").apply {
            token?.let { header("Authorization", "Bearer $it") }
        }
    }

    private suspend fun call(
        method: String,
        path: String,
        query: Map<String, String?>,
        body: JSONObject?,
    ): Any = withContext(Dispatchers.IO) {
        val reqBody = body?.toString()?.toRequestBody(JSON_TYPE)
        val req = baseRequest(path, query).method(method, reqBody).build()
        try {
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) throw handleError(resp.code, text)
                parseJson(text)
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw networkError(e)
        }
    }

    private fun networkError(e: IOException): ApiException =
        if (e is SocketTimeoutException) {
            ApiException(0, I18n.x("err.slow"))
        } else {
            ApiException(0, I18n.x("err.offline"))
        }

    private fun handleError(status: Int, text: String): ApiException {
        val err = parseError(status, text)
        if (status == 401 && token != null) {
            Handler(Looper.getMainLooper()).post { onUnauthorized?.invoke() }
        }
        return err
    }

    private fun parseJson(text: String): Any {
        val t = text.trim()
        return when {
            t.startsWith("{") -> JSONObject(t)
            t.startsWith("[") -> JSONArray(t)
            else -> JSONObject()
        }
    }

    private fun parseError(status: Int, text: String): ApiException {
        val fallback = when (status) {
            401 -> I18n.x("err.401")
            403 -> I18n.x("err.403")
            404 -> I18n.x("err.404")
            503 -> I18n.x("err.503")
            in 500..599 -> I18n.x("err.500") + " ($status)"
            else -> "Error $status"
        }
        return try {
            val json = JSONObject(text)
            when (val d = json.opt("detail")) {
                is String -> ApiException(status, d.ifBlank { fallback })
                is JSONObject -> ApiException(
                    status,
                    d.str("message").ifBlank { fallback },
                    d.str("code").ifBlank { null },
                    d,
                )
                is JSONArray -> {
                    val first = d.optJSONObject(0)
                    ApiException(status, first?.str("msg")?.ifBlank { null } ?: fallback)
                }
                else -> ApiException(status, fallback)
            }
        } catch (e: Exception) {
            ApiException(status, fallback)
        }
    }
}

// ---- Small JSON helpers (treat JSON null and missing the same way) ----

fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")

fun JSONObject.num(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

fun JSONObject.int(key: String): Int = if (isNull(key)) 0 else optInt(key, 0)

fun JSONObject.obj(key: String): JSONObject? = if (isNull(key)) null else optJSONObject(key)

fun JSONObject.arr(key: String): JSONArray = if (isNull(key)) JSONArray() else (optJSONArray(key) ?: JSONArray())

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull { i ->
    if (isNull(i)) null else optString(i, "").takeIf { it.isNotBlank() }
}
