package com.kabadimarket.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabadimarket.app.BuildConfig
import com.kabadimarket.app.data.I18n
import com.kabadimarket.app.data.Updater
import kotlinx.coroutines.launch
import java.io.File

internal fun ux(gu: String, hi: String, en: String) = when (I18n.lang) {
    "gu" -> gu
    "hi" -> hi
    else -> en
}

/** Shared update state, so the Admin screen can also start a check. */
object UpdateState {
    var release by mutableStateOf<Updater.Release?>(null)
    var dismissed by mutableStateOf(false)
    var lastCheck = 0L

    /** Checks GitHub now. manual = true shows a message when already up to date. */
    suspend fun checkNow(manual: Boolean) {
        if (manual) Toast.show(ux("અપડેટ તપાસી રહ્યા છીએ…", "अपडेट जांच रहे हैं…", "Checking for update…"))
        when (val r = Updater.checkDetailed()) {
            is Updater.Result.Update -> {
                lastCheck = System.currentTimeMillis()
                release = r.release
                dismissed = false
            }
            Updater.Result.UpToDate -> {
                lastCheck = System.currentTimeMillis()
                if (manual) Toast.success(ux("તમે નવીનતમ વર્ઝન પર છો ✓ (v${BuildConfig.VERSION_NAME})", "आप नवीनतम वर्ज़न पर हैं ✓ (v${BuildConfig.VERSION_NAME})", "You are on the latest version ✓ (v${BuildConfig.VERSION_NAME})"))
            }
            // Failed: don't wait 15 min — try again next time the app opens.
            Updater.Result.Failed -> if (manual) Toast.error(ux("અપડેટ તપાસી ન શકાયું — ઇન્ટરનેટ ચાલુ કરી ફરી દબાવો", "अपडेट जांच नहीं हो सका — इंटरनेट चालू करके फिर दबाएं", "Could not check — turn on internet and try again"))
        }
    }
}

/** Checks for a new version when the app opens / comes back, and offers a one-tap update (OTA). */
@Composable
fun UpdateChecker() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var file by remember { mutableStateOf<File?>(null) }
    var failed by remember { mutableStateOf(false) }

    // Check on start, and again whenever the app comes back to the screen (max every 15 min).
    @Suppress("DEPRECATION")
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME &&
                System.currentTimeMillis() - UpdateState.lastCheck > 15 * 60 * 1000L
            ) {
                scope.launch { UpdateState.checkNow(false) }
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val r = UpdateState.release ?: return
    if (UpdateState.dismissed) return
    val anim by animateFloatAsState(progress, label = "dl")

    fun installNow(f: File) {
        if (Updater.canInstall(context)) Updater.install(context, f)
        else {
            Toast.show(ux("'આ સ્રોતમાંથી મંજૂરી આપો' ચાલુ કરો, પછી ફરી અપડેટ દબાવો", "'इस स्रोत से अनुमति दें' चालू करें, फिर अपडेट दबाएं", "Turn on 'Allow from this source', then tap Update again"))
            Updater.openInstallPermission(context)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!downloading) UpdateState.dismissed = true },
        icon = { Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = C.Brand, modifier = Modifier.size(36.dp)) },
        title = { Text(ux("નવું અપડેટ આવ્યું છે", "नया अपडेट आया है", "New update available"), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("v${BuildConfig.VERSION_NAME}  →  v2.0.${r.version}", color = C.Brand, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    ux(
                        "નવી સ્ક્રીન અને સુધારા. તમારો ડેટા સુરક્ષિત રહેશે.",
                        "नई स्क्रीन और सुधार। आपका डेटा सुरक्षित रहेगा।",
                        "New screens and fixes. Your data stays safe.",
                    ),
                    fontSize = 14.sp,
                )
                if (downloading || file != null) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { anim }, modifier = Modifier.fillMaxWidth().height(8.dp), color = C.Green, trackColor = C.Surface3)
                    Text("${(anim * 100).toInt()}%", color = C.Muted, fontSize = 12.sp)
                }
                if (failed) {
                    Spacer(Modifier.height(8.dp))
                    Text(ux("ડાઉનલોડ નિષ્ફળ — ફરી પ્રયાસ કરો", "डाउनलोड विफल — फिर कोशिश करें", "Download failed — try again"), color = C.Red, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !downloading, onClick = {
                val f = file
                if (f != null) {
                    installNow(f)
                    return@TextButton
                }
                downloading = true
                failed = false
                scope.launch {
                    val got = Updater.download(context, r) { progress = it }
                    downloading = false
                    if (got == null) failed = true else {
                        file = got
                        installNow(got)
                    }
                }
            }) {
                Text(
                    when {
                        downloading -> ux("ડાઉનલોડ થાય છે…", "डाउनलोड हो रहा है…", "Downloading…")
                        file != null -> ux("ઇન્સ્ટોલ કરો", "इंस्टॉल करें", "Install")
                        else -> ux("અપડેટ કરો", "अपडेट करें", "Update now")
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            if (!downloading) TextButton(onClick = { UpdateState.dismissed = true }) { Text(ux("પછી", "बाद में", "Later")) }
        },
    )
}
