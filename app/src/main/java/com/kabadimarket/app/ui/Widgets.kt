package com.kabadimarket.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.kabadimarket.app.data.Api
import com.kabadimarket.app.data.Codes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.Executors

// ============================================================
//  Live camera barcode scanner (inside the screen, like the old app)
// ============================================================

/**
 * Shows the camera and calls [onCode] for every barcode / QR / DataMatrix it sees.
 * [active] = false pauses reporting (camera keeps running).
 */
@Composable
fun CameraScanner(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    onCode: (String) -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var askedOnce by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        askedOnce = true
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier.background(Color.Black)) {
        if (granted) {
            CameraPreview(active, onCode)
        } else {
            Column(
                Modifier.fillMaxSize().background(C.Surface3).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = C.Brand, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(8.dp))
                Text(t("scan.cameraNeeded"), color = C.Text, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(t("scan.cameraAllow"), color = C.Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                if (askedOnce) {
                    BigButton(t("scan.openSettings"), icon = Icons.Filled.Settings, outlined = true, onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }, modifier = Modifier.padding(horizontal = 24.dp))
                }
                BigButton(t("scan.allowCamera"), icon = Icons.Filled.CameraAlt, onClick = {
                    launcher.launch(Manifest.permission.CAMERA)
                }, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(active: Boolean, onCode: (String) -> Unit) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val currentActive by rememberUpdatedState(active)
    val currentOnCode by rememberUpdatedState(onCode)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val main = remember { Handler(Looper.getMainLooper()) }
    val scanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build())
    }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null || !currentActive) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                    scanner.process(image)
                        .addOnSuccessListener { list ->
                            val value = list.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                            if (value != null && currentActive) main.post { currentOnCode(value.trim()) }
                        }
                        .addOnCompleteListener { proxy.close() }
                }
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            try {
                provider?.unbindAll()
            } catch (_: Exception) {
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            scanner.close()
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/** The scan window corners drawn over the camera. */
@Composable
fun ScanBracket(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val len = size.minDimension * 0.18f
        val w = 4.dp.toPx()
        val corners = listOf(
            Offset(0f, 0f) to Pair(Offset(len, 0f), Offset(0f, len)),
            Offset(size.width, 0f) to Pair(Offset(size.width - len, 0f), Offset(size.width, len)),
            Offset(0f, size.height) to Pair(Offset(len, size.height), Offset(0f, size.height - len)),
            Offset(size.width, size.height) to Pair(Offset(size.width - len, size.height), Offset(size.width, size.height - len)),
        )
        corners.forEach { (c, ends) ->
            drawLine(color, c, ends.first, w)
            drawLine(color, c, ends.second, w)
        }
    }
}

// ============================================================
//  Photos from the server
// ============================================================

private object ImageCache {
    val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
}

/** Shows an uploaded photo (by its server path). */
@Composable
fun RemoteImage(path: String, modifier: Modifier = Modifier, maxSize: Int = 480) {
    var bmp by remember(path) { mutableStateOf(ImageCache.cache.get(path)) }
    LaunchedEffect(path) {
        if (bmp != null) return@LaunchedEffect
        bmp = withContext(Dispatchers.IO) {
            try {
                val bytes = Api.fetchUrl(Api.fileUrl(path))
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                var sample = 1
                while (opts.outWidth / (sample * 2) >= maxSize && opts.outHeight / (sample * 2) >= maxSize) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.also { ImageCache.cache.put(path, it) }
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(modifier.background(C.Surface3), contentAlignment = Alignment.Center) {
        bmp?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

// ============================================================
//  Barcode + QR pictures
// ============================================================

@Composable
fun BarcodeView(value: String, modifier: Modifier = Modifier, height: Int = 64) {
    val widths = remember(value) { Codes.code128Widths(value) }
    val total = remember(widths) { widths.sum() }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(height.dp).background(Color.White)) {
            val unit = size.width / (total + 20f)
            var x = unit * 10
            var bar = true
            widths.forEach { w ->
                if (bar) drawRect(Color.Black, Offset(x, 0f), Size(w * unit, size.height))
                x += w * unit
                bar = !bar
            }
        }
        Text(Codes.code128Text(value), fontSize = 13.sp, color = Color.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

@Composable
fun QrView(value: String, modifier: Modifier = Modifier) {
    val matrix = remember(value) { runCatching { Codes.qrMatrix(value) }.getOrNull() }
    Canvas(modifier.background(Color.White)) {
        val m = matrix ?: return@Canvas
        val n = m.size + 2
        val cell = size.minDimension / n
        for (r in m.indices) for (c in m[r].indices) {
            if (m[r][c]) drawRect(Color.Black, Offset((c + 1) * cell, (r + 1) * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

// ============================================================
//  Structured shelf/rack location (old app's LocationPicker)
// ============================================================

data class AssignedLocation(
    val storeName: String? = null,
    val wall: String? = null,
    val rackName: String? = null,
    val isOpenFloor: Boolean = false,
    val cartonNumber: String? = null,
    val shelfLevel: String? = null,
) {
    fun toQuery(prefix: String = "current_"): Map<String, String?> = buildMap {
        storeName?.takeIf { it.isNotBlank() }?.let { put("${prefix}store_name", it) }
        wall?.let { put("${prefix}wall", it) }
        put("${prefix}is_open_floor", if (isOpenFloor) "true" else "false")
        if (isOpenFloor) {
            cartonNumber?.takeIf { it.isNotBlank() }?.let { put("${prefix}carton_number", it) }
        } else {
            rackName?.takeIf { it.isNotBlank() }?.let { put("${prefix}rack_name", it) }
            shelfLevel?.let { put("${prefix}shelf_level", it) }
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("store_name", storeName ?: JSONObject.NULL)
        .put("wall", wall ?: JSONObject.NULL)
        .put("rack_name", rackName ?: JSONObject.NULL)
        .put("is_open_floor", isOpenFloor)
        .put("carton_number", cartonNumber ?: JSONObject.NULL)
        .put("shelf_level", shelfLevel ?: JSONObject.NULL)

    val isEmpty: Boolean
        get() = storeName.isNullOrBlank() && wall == null && rackName.isNullOrBlank() && cartonNumber.isNullOrBlank() && shelfLevel == null

    companion object {
        fun from(j: JSONObject?): AssignedLocation? = j?.let {
            AssignedLocation(
                storeName = it.optString("store_name").takeIf { s -> !it.isNull("store_name") && s.isNotBlank() },
                wall = it.optString("wall").takeIf { s -> !it.isNull("wall") && s.isNotBlank() },
                rackName = it.optString("rack_name").takeIf { s -> !it.isNull("rack_name") && s.isNotBlank() },
                isOpenFloor = it.optBoolean("is_open_floor"),
                cartonNumber = it.optString("carton_number").takeIf { s -> !it.isNull("carton_number") && s.isNotBlank() },
                shelfLevel = it.optString("shelf_level").takeIf { s -> !it.isNull("shelf_level") && s.isNotBlank() },
            )
        }
    }
}

/** Same text as old app's formatAssignedLocation(). */
fun formatAssignedLocation(loc: AssignedLocation?): String {
    if (loc == null) return ""
    val parts = mutableListOf<String>()
    loc.storeName?.takeIf { it.isNotBlank() }?.let { parts.add("Store $it") }
    loc.wall?.let { parts.add(it) }
    if (loc.isOpenFloor) {
        parts.add(if (!loc.cartonNumber.isNullOrBlank()) "Open Floor · Carton ${loc.cartonNumber}" else "Open Floor")
    } else if (!loc.rackName.isNullOrBlank()) {
        parts.add("Rack ${loc.rackName}")
    }
    if (!loc.isOpenFloor && loc.shelfLevel != null) parts.add(loc.shelfLevel)
    return parts.joinToString(" · ")
}

fun formatAssignedLocation(j: JSONObject?): String = formatAssignedLocation(AssignedLocation.from(j))

/** Old "Rack → Shelf → Box → Position" text of a stock unit. */
fun oldLocationText(l: JSONObject?): String =
    if (l == null) "" else listOf("rack", "shelf", "box", "position").map { if (l.isNull(it)) "" else l.optString(it) }
        .filter { it.isNotBlank() }.joinToString(" → ")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocationPicker(value: AssignedLocation, onChange: (AssignedLocation) -> Unit, showStoreName: Boolean = true) {
    @Composable
    fun Opt(text: String, selected: Boolean, onClick: () -> Unit) {
        Box(
            Modifier
                .padding(end = 8.dp, bottom = 8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) C.Brand else C.Card)
                .border(1.dp, if (selected) C.Brand else C.Line, RoundedCornerShape(6.dp))
                .pressable(onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) { Text(text, color = if (selected) Color.White else C.Text2, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
    }

    Column {
        if (showStoreName) Field(value.storeName ?: "", { onChange(value.copy(storeName = it)) }, "Store name")
        Text("Wall", color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        FlowRow {
            listOf("Front", "Back", "Left", "Right").forEach { w ->
                Opt(w, value.wall == w) { onChange(value.copy(wall = if (value.wall == w) null else w)) }
            }
        }
        Text("Placement", color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        FlowRow {
            Opt("Rack", !value.isOpenFloor) { onChange(value.copy(isOpenFloor = false, cartonNumber = null)) }
            Opt("Open Floor", value.isOpenFloor) { onChange(value.copy(isOpenFloor = true, rackName = null, shelfLevel = null)) }
        }
        if (value.isOpenFloor) {
            Field(value.cartonNumber ?: "", { onChange(value.copy(cartonNumber = it.filter(Char::isDigit))) }, "Carton number", number = true)
        } else {
            Field(value.rackName ?: "", { onChange(value.copy(rackName = it)) }, "Rack name — e.g. A-3", caps = true)
            Text("Shelf", color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
            FlowRow {
                listOf("Top", "Middle", "Bottom").forEach { s ->
                    Opt(s, value.shelfLevel == s) { onChange(value.copy(shelfLevel = if (value.shelfLevel == s) null else s)) }
                }
            }
        }
    }
}
