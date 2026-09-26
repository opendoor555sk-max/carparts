package com.kabadimarket.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.ContextCompat
import com.kabadimarket.app.R
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.Locale
import kotlin.coroutines.resume

/** Phone GPS (same use as old app: search/requirement GPS + company-phone location). */
object Gps {
    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Current location or null (no permission / GPS off / timeout). */
    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context, timeoutMs: Long = 8000): Location? {
        if (!hasPermission(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val recent = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (recent != null && System.currentTimeMillis() - recent.time < 2 * 60 * 1000) return recent
        val provider = providers.firstOrNull() ?: return recent
        val fresh = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                    }

                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                try {
                    lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
            }
        }
        return fresh ?: recent
    }

    fun format(l: Location?, decimals: Int = 6, sep: String = ","): String =
        if (l == null) "" else String.format(Locale.US, "%.${decimals}f$sep%.${decimals}f", l.latitude, l.longitude)

    /** Company-phone location ping (old app: every 10 minutes while logged in). */
    suspend fun ping(ctx: Context) {
        val l = current(ctx) ?: return
        try {
            Api.post("/device/ping-location", JSONObject().put("lat", l.latitude).put("lng", l.longitude))
        } catch (_: Exception) {
        }
    }
}

/** Vibration + sound feedback (old app used haptics + a warning sound). */
object Feedback {
    private fun vibrate(ctx: Context, pattern: LongArray) {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            else @Suppress("DEPRECATION") v.vibrate(pattern, -1)
        } catch (_: Exception) {
        }
    }

    fun success(ctx: Context) = vibrate(ctx, longArrayOf(0, 35, 40, 60))
    fun tap(ctx: Context) = vibrate(ctx, longArrayOf(0, 20))
    fun error(ctx: Context) = vibrate(ctx, longArrayOf(0, 80, 60, 120, 60, 120))

    /** The loud "limit reached" warning sound from the old app. */
    fun limitSound(ctx: Context) {
        try {
            MediaPlayer.create(ctx, R.raw.limit_reached)?.apply {
                setOnCompletionListener { it.release() }
                start()
            }
        } catch (_: Exception) {
        }
    }
}

/** Same part-number extraction from long sticker barcodes as the old app. */
fun extractPartNumber(raw: String): String {
    val original = raw.trim()
    if (original.isEmpty() || original.length < 16) return original
    val s = original.uppercase().replace(Regex("[^0-9A-Z]"), "")
    Regex("P([0-9][0-9A-Z]{9})(?=[A-Z]|$)").find(s)?.let { return it.groupValues[1] }
    Regex("([0-9][0-9A-Z]{4}[A-Z][0-9A-Z]{4})").find(s)?.let { return it.groupValues[1] }
    return original
}
