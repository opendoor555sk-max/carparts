package com.kabadimarket.app.ui

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabadimarket.app.data.Gps

private fun locationOn(ctx: Context): Boolean {
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
    return try {
        if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
        else lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    } catch (_: Exception) {
        false
    }
}

private fun openSafely(ctx: Context, intent: Intent) {
    try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
}

/**
 * Same strict GPS rule as the old app: the app works only while phone Location is ON
 * and location permission is allowed. Re-checks every time the app comes back.
 */
@Composable
fun LocationGate(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    // 0 ok, 1 location off, 2 permission denied (can ask), 3 blocked (settings only)
    var status by remember { mutableStateOf(if (locationOn(ctx) && Gps.hasPermission(ctx)) 0 else -1) }
    var asked by remember { mutableStateOf(false) }

    fun check(askAgain: Boolean, launch: (() -> Unit)?) {
        status = when {
            !locationOn(ctx) -> 1
            Gps.hasPermission(ctx) -> 0
            askAgain && launch != null -> { launch(); status.coerceAtLeast(2) }
            else -> if (status == 3) 3 else 2
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        asked = true
        val canAskAgain = (ctx as? android.app.Activity)?.let {
            androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(it, android.Manifest.permission.ACCESS_FINE_LOCATION)
        } ?: true
        status = when {
            res.values.any { it } || Gps.hasPermission(ctx) -> if (locationOn(ctx)) 0 else 1
            canAskAgain -> 2
            else -> 3
        }
    }
    val ask: () -> Unit = { permLauncher.launch(Gps.PERMISSIONS) }

    LaunchedEffect(Unit) { check(true, ask) }

    @Suppress("DEPRECATION")
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) check(false, null)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    // The app stays underneath (so open screens and drafts are kept); the gate covers it.
    Box(Modifier.fillMaxSize()) {
        if (status != -1) content()
        if (status == -1) Column(Modifier.fillMaxSize().background(C.Bg)) {}
        else if (status != 0) GateCover(status, ctx, ask) { check(true, ask) }
    }
}

@Composable
private fun GateCover(status: Int, ctx: Context, ask: () -> Unit, retry: () -> Unit) {

    val title: String
    val msg: String
    val button: String
    val action: () -> Unit
    when (status) {
        1 -> {
            title = ux("GPS / લોકેશન ચાલુ કરો", "GPS / लोकेशन चालू करें", "Turn On GPS / Location")
            msg = ux(
                "આ એપ ફોનનું લોકેશન (GPS) ચાલુ હોય ત્યારે જ ચાલે છે. ચાલુ કરીને પાછા આવો.",
                "यह ऐप फ़ोन का लोकेशन (GPS) चालू होने पर ही चलता है। चालू करके वापस आएं।",
                "This app works only when your phone Location (GPS) is ON. Turn it on and come back.",
            )
            button = ux("લોકેશન સેટિંગ ખોલો", "लोकेशन सेटिंग खोलें", "Open Location Settings")
            action = { openSafely(ctx, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        }
        2 -> {
            title = ux("લોકેશન પરવાનગી જરૂરી છે", "लोकेशन अनुमति ज़रूरी है", "Location Permission Required")
            msg = ux(
                "લોકેશન પરવાનગી વગર આ એપ ચાલશે નહીં. કૃપા કરીને મંજૂરી આપો.",
                "लोकेशन अनुमति के बिना यह ऐप नहीं चलेगा। कृपया अनुमति दें।",
                "This app cannot run without location access. Please allow location.",
            )
            button = ux("લોકેશન મંજૂર કરો", "लोकेशन अनुमति दें", "Allow Location")
            action = ask
        }
        else -> {
            title = ux("લોકેશન પરવાનગી બંધ છે", "लोकेशन अनुमति बंद है", "Location Permission Blocked")
            msg = ux(
                "સેટિંગમાં જઈ આ એપ માટે Location → Allow કરો, પછી પાછા આવો.",
                "सेटिंग में जाकर इस ऐप के लिए Location → Allow करें, फिर वापस आएं।",
                "Open Settings, allow Location for this app, then come back.",
            )
            button = ux("સેટિંગ ખોલો", "सेटिंग खोलें", "Open Settings")
            action = { openSafely(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))) }
        }
    }
    Column(
        Modifier.fillMaxSize().background(C.Bg)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } } // block touches to the app below
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = C.Brand, modifier = Modifier.size(64.dp).entrance())
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(msg, fontSize = 15.sp, color = C.Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        BigButton(button, onClick = action, modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.LocationOn)
        Spacer(Modifier.height(8.dp))
        Text(
            ux("મેં ચાલુ કર્યું — ફરી તપાસો", "मैंने चालू किया — फिर जांचें", "I have enabled it — Retry"),
            color = C.Brand, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(12.dp).pressable(retry),
        )
    }
}
