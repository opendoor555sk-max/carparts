package com.kabadimarket.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.kabadimarket.app.Nav
import com.kabadimarket.app.data.Api
import com.kabadimarket.app.data.Printer
import com.kabadimarket.app.data.Stickers
import com.kabadimarket.app.data.Stickers.Box as SBox
import com.kabadimarket.app.data.Stickers.Tpl
import com.kabadimarket.app.data.User
import com.kabadimarket.app.data.arr
import com.kabadimarket.app.data.obj
import com.kabadimarket.app.data.objects
import com.kabadimarket.app.data.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

// ---------------- Image helpers ----------------

/** Decodes a photo, turns it upright (EXIF) and resizes it to [width] px wide. */
private fun loadUpright(bytes: ByteArray, width: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= width) sample *= 2
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val rotation = try {
        when (android.media.ExifInterface(bytes.inputStream()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (_: Exception) {
        0f
    }
    val m = Matrix()
    if (rotation != 0f) m.postRotate(rotation)
    val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    if (rotated.width <= width) return rotated
    val h = (rotated.height * width.toFloat() / rotated.width).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(rotated, width, h, true)
}

private fun Bitmap.base64(format: Bitmap.CompressFormat, quality: Int): String {
    val out = ByteArrayOutputStream()
    compress(format, quality, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

private fun dataUrlBitmap(dataUrl: String): ImageBitmap? = try {
    val b64 = dataUrl.substringAfter("base64,", "")
    val bytes = Base64.decode(b64, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
} catch (_: Exception) {
    null
}

private fun fmt1(d: Double): String {
    val r = Math.round(d * 10) / 10.0
    return if (r == Math.floor(r)) r.toLong().toString() else String.format(Locale.US, "%.1f", r)
}

// ---------------- Live preview ----------------

@Composable
private fun StickerPreview(tpl: Tpl, layoutH: Double, codeSize: Double) {
    val density = LocalDensity.current
    val w: Dp = 320.dp
    val h: Dp = w / tpl.aspect.toFloat()
    val logoBmp = remember(tpl.logo?.dataUrl) { tpl.logo?.dataUrl?.let { dataUrlBitmap(it) } }
    val code = tpl.code
    val matrix = remember(code?.type, code?.value) { if (code != null && code.value.isNotEmpty()) Stickers.codeMatrix(code.type, code.value) else null }
    Box(
        Modifier
            .size(w, h)
            .background(Color.White, RoundedCornerShape(4.dp))
            .border(1.dp, C.Line, RoundedCornerShape(4.dp)),
    ) {
        val lg = tpl.logo
        if (lg != null && logoBmp != null) {
            Image(
                logoBmp, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier
                    .offset(w * (lg.box.x / 100).toFloat(), h * (lg.box.y / 100).toFloat())
                    .size(w * (lg.box.w / 100).toFloat(), h * (lg.box.h / 100).toFloat()),
            )
        }
        if (code != null && matrix != null) {
            val ratio = matrix.ratio
            val hDp = minOf(h.value - 2f, (codeSize / layoutH).toFloat() * h.value)
            val wDp = if (ratio > 1.3f) minOf(w.value * 0.6f, hDp * ratio) else hDp
            val left = (code.box.x / 100 * w.value).toFloat().coerceIn(0f, maxOf(0f, w.value - wDp))
            val top = (code.box.y / 100 * h.value).toFloat().coerceIn(0f, maxOf(0f, h.value - hDp))
            Canvas(Modifier.offset(left.dp, top.dp).size(wDp.dp, hDp.dp)) {
                val mh = matrix.h * matrix.rowScale
                val scale = minOf(size.width / matrix.w, size.height / mh)
                val ox = (size.width - matrix.w * scale) / 2
                val oy = (size.height - mh * scale) / 2
                for (r in 0 until matrix.h) {
                    var c = 0
                    while (c < matrix.w) {
                        if (matrix[r, c]) {
                            var e = c
                            while (e < matrix.w && matrix[r, e]) e++
                            drawRect(
                                Color.Black,
                                topLeft = Offset(ox + c * scale, oy + r * matrix.rowScale * scale),
                                size = Size((e - c) * scale + 0.5f, matrix.rowScale * scale + 0.5f),
                            )
                            c = e
                        } else c++
                    }
                }
            }
        }
        tpl.lines.forEach { ln ->
            val fsDp = maxOf(6f, (ln.size / 100 * h.value).toFloat())
            val fs = with(density) { fsDp.dp.toSp() }
            Text(
                ln.text,
                color = Color.Black,
                fontSize = fs,
                lineHeight = fs,
                fontWeight = if (ln.bold) FontWeight.ExtraBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.offset(w * (ln.x / 100).toFloat(), h * (ln.y / 100).toFloat()),
            )
        }
    }
}

// ---------------- Small UI bits ----------------

@Composable
private fun PadBtn(icon: ImageVector? = null, text: String? = null, bold: Boolean = false, size: Int = 44, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(3.dp)
            .size(width = (size + 4).dp, height = size.dp)
            .background(C.Card, RoundedCornerShape(8.dp))
            .border(1.dp, C.Line, RoundedCornerShape(8.dp))
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = C.Text, modifier = Modifier.size(22.dp))
        if (text != null) Text(text, color = C.Text, fontWeight = if (bold) FontWeight.Black else FontWeight.ExtraBold, fontSize = 17.sp)
    }
}

@Composable
private fun MiniBtn(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(end = 6.dp, bottom = 6.dp)
            .height(34.dp)
            .background(C.Card, RoundedCornerShape(8.dp))
            .border(1.dp, C.Line, RoundedCornerShape(8.dp))
            .pressable(onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = C.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

@Composable
private fun StLabel(text: String) {
    Text(text.uppercase(), color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
private fun SmallInput(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, decimal: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, color = C.Muted, fontSize = 13.sp) },
        singleLine = true,
        modifier = modifier.width(96.dp).padding(end = 8.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
    )
}

@Composable
private fun SavedTile(icon: ImageVector, name: String, active: Boolean = false, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    Box(Modifier.padding(end = 10.dp, top = 6.dp).width(112.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(C.Card, RoundedCornerShape(8.dp))
                .border(if (active) 2.dp else 1.dp, if (active) C.Brand else C.Line, RoundedCornerShape(8.dp))
                .pressable(onClick)
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = C.Brand, modifier = Modifier.size(20.dp))
            Text(name, color = C.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onDelete != null) {
            Icon(
                Icons.Filled.Cancel, contentDescription = null, tint = C.Red,
                modifier = Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(20.dp).background(C.Card, RoundedCornerShape(10.dp)).pressable(onDelete),
            )
        }
    }
}

// ---------------- Screen ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StickerScannerScreen(user: User, nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Only admin / super admin (the store owner) may create stickers — not staff.
    LaunchedEffect(user.id) {
        if (!user.isAdmin) {
            Toast.error(t("scanSticker.onlyAdminCanCreate"))
            nav.back()
        }
    }

    var busy by remember { mutableStateOf(false) }
    var tpl by remember { mutableStateOf<Tpl?>(null) }
    var partNumber by remember { mutableStateOf("") }
    var codeType by remember { mutableStateOf("qr") }
    var codeSize by remember { mutableStateOf(10.0) }
    var layoutCode by rememberSaveable { mutableStateOf("24L") }
    val cellMap = remember { mutableStateMapOf<Int, String>() }
    var activeId by remember { mutableStateOf("__current__") }
    var fillQty by remember { mutableStateOf("") }
    var marginTop by rememberSaveable { mutableStateOf("") }
    var marginLeft by rememberSaveable { mutableStateOf("") }
    var pageMargin by rememberSaveable { mutableStateOf("0") }
    var saved by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var logos by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var company by remember { mutableStateOf("Hyundai / Kia") }
    var rawLines by remember { mutableStateOf<List<Stickers.Raw>>(emptyList()) }
    var manualPn by remember { mutableStateOf("") }
    var formats by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var formatName by remember { mutableStateOf("") }
    var pendingFormatId by remember { mutableStateOf<String?>(null) }
    var selLines by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var nudgeStep by remember { mutableStateOf(2) }
    var companyFormats by remember { mutableStateOf<Map<String, JSONObject>>(emptyMap()) }
    val layout = Stickers.layout(layoutCode)

    fun loadSaved() {
        scope.launch {
            try { saved = Api.getArr("/sticker-templates").objects() } catch (_: Exception) {}
            try { logos = Api.getArr("/logos").objects() } catch (_: Exception) {}
            try {
                val fmts = Api.getArr("/company-formats").objects()
                companyFormats = fmts.mapNotNull { f -> f.obj("template")?.let { f.str("company") to it } }.toMap()
                formats = fmts
            } catch (_: Exception) {}
        }
    }
    LaunchedEffect(Unit) { loadSaved() }

    fun errMsg(e: Exception, fallback: String) = (e.message ?: "").ifBlank { fallback }

    // ---- Build from a scan / format ----
    fun buildTpl(rl: List<Stickers.Raw>, aspect: Double, hasCode: Boolean, ct: String, pn: String, comp: String, sizeMm: Double) {
        val lines = Stickers.buildLines(rl, aspect, hasCode, false, comp)
        tpl = Tpl(aspect, lines, if (hasCode) Stickers.Code(ct, pn, Stickers.codeBox(aspect, comp), sizeMm) else null, null, comp)
    }

    fun buildTplFromFormat(rl: List<Stickers.Raw>, fmt: JSONObject, aspect: Double, hasCode: Boolean, pn: String, comp: String) {
        val template = fmt.obj("template")
        val fmtLines = template?.arr("lines")?.objects() ?: emptyList()
        val lines = rl.mapIndexed { i, l ->
            val s = fmtLines.getOrNull(i)
            if (s != null) Stickers.Line(l.text, s.optDouble("x", 3.0), s.optDouble("y", 4.0), s.optDouble("size", 4.0), if (s.has("bold") && !s.isNull("bold")) s.optBoolean("bold") else l.bold)
            else Stickers.Line(l.text, 3.0, minOf(96.0, 88.0 + i * 3), 4.0, l.bold)
        }
        val fc = template?.obj("code")
        val fcType = fc?.str("type").orEmpty()
        val fcSize = fc?.optDouble("sizeMm", Double.NaN)?.takeIf { !it.isNaN() }
        val code = if (hasCode) Stickers.Code(fcType.ifEmpty { "qr" }, pn, fc?.obj("box")?.let { SBox.from(it, Stickers.codeBox(aspect, comp)) } ?: Stickers.codeBox(aspect, comp), fcSize ?: codeSize) else null
        val prev = tpl
        val logoBox = template?.obj("logo")?.obj("box")
        val logo = if (fc != null && logoBox != null && prev?.logo != null) prev.logo.copy(box = SBox.from(logoBox, Stickers.LOGO_START)) else prev?.logo
        tpl = Tpl(aspect, lines, code, logo, comp)
        if (fcType.isNotEmpty()) codeType = fcType
        if (fcSize != null) codeSize = fcSize
    }

    fun processImage(bytes: ByteArray) {
        busy = true
        scope.launch {
            try {
                val b64 = withContext(Dispatchers.Default) {
                    loadUpright(bytes, 800)?.base64(Bitmap.CompressFormat.JPEG, 85) ?: Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
                val res = Api.post("/scan-sticker", JSONObject().put("image_base64", b64)) as? JSONObject ?: JSONObject()
                val aspect = res.optDouble("aspect", 1.6).takeIf { !it.isNaN() && it > 0 } ?: 1.6
                val hasCode = res.obj("code") != null
                val pn = res.str("part_number")
                val rl = res.arr("lines").objects().map { Stickers.Raw(it.str("text"), it.optBoolean("bold", false)) }
                val joined = rl.joinToString(" ") { it.text.uppercase() }
                var comp = company
                if (joined.contains("HYUNDAI") || Regex("\\bKIA\\b").containsMatchIn(joined)) comp = "Hyundai / Kia"
                var ct = if (res.obj("code")?.str("type") == "barcode") "barcode" else "qr"
                if (comp in Stickers.FORMATTED) ct = "datamatrix"
                rawLines = rl
                company = comp

                val pendingFmt = pendingFormatId?.let { id -> formats.firstOrNull { it.str("id") == id } }
                if (pendingFmt != null) {
                    buildTplFromFormat(rl, pendingFmt, aspect, hasCode, pn, comp)
                    pendingFmt.obj("template")?.obj("code")?.str("type")?.takeIf { it.isNotEmpty() }?.let { ct = it }
                    partNumber = pn
                    codeType = ct
                    cellMap.clear()
                    pendingFormatId = null
                    Toast.success("${t("scanSticker.stickerGenerated")} (${t("scanSticker.formatColon")} ${pendingFmt.str("company")})")
                } else {
                    buildTpl(rl, aspect, hasCode, ct, pn, comp, codeSize)
                    val fmt = companyFormats[comp]
                    if (fmt != null) {
                        fmt.obj("code")?.let { fc ->
                            fc.str("type").takeIf { it.isNotEmpty() }?.let { ct = it }
                            fc.optDouble("sizeMm", Double.NaN).takeIf { !it.isNaN() }?.let { codeSize = it }
                        }
                        tpl = tpl?.let { Stickers.applyCompanyFormat(it, fmt) }
                    }
                    partNumber = pn
                    codeType = ct
                    cellMap.clear()
                    val suffix = when {
                        fmt != null -> " ($comp ${t("scanSticker.savedFormatSuffix")})"
                        comp in Stickers.FORMATTED -> " ($comp ${t("scanSticker.formatSuffix")})"
                        else -> ""
                    }
                    Toast.success(t("scanSticker.stickerGenerated") + suffix)
                }
            } catch (e: Exception) {
                Toast.error(errMsg(e, t("scanSticker.scanFailed")))
            } finally {
                busy = false
            }
        }
    }

    // ---- Photo pickers ----
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null && f.exists()) processImage(f.readBytes())
    }
    fun launchCamera() {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val f = File(dir, "sticker_${System.currentTimeMillis()}.jpg")
        cameraFile = f
        takePicture.launch(FileProvider.getUriForFile(context, context.packageName + ".files", f))
    }
    val cameraPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else Toast.error(t("scanSticker.cameraPermissionNeeded"))
    }
    fun takePhoto() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
        else cameraPerm.launch(Manifest.permission.CAMERA)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { processImage(it) }
    }

    // ---- Logo ----
    fun applyLogo(dataUrl: String?) {
        val prev = tpl ?: return
        val logo = dataUrl?.let { Stickers.Logo(it, prev.logo?.box ?: Stickers.LOGO_START) }
        tpl = if (prev.company != null && prev.company in Stickers.FORMATTED) prev.copy(logo = logo)
        else prev.copy(logo = logo, lines = Stickers.layoutLines(prev.lines.map { Stickers.Raw(it.text, it.bold) }, prev.aspect, prev.code != null, if (logo != null) 20.0 else 4.0))
    }
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } } ?: return@launch
                val dataUrl = withContext(Dispatchers.Default) { "data:image/png;base64," + (loadUpright(bytes, 300)?.base64(Bitmap.CompressFormat.PNG, 100) ?: "") }
                Api.post("/logos", JSONObject().put("name", "Logo").put("data_url", dataUrl))
                Toast.success(t("scanSticker.logoSaved"))
                loadSaved()
                applyLogo(dataUrl)
            } catch (e: Exception) {
                Toast.error(errMsg(e, t("scanSticker.logoSaveFailed")))
            }
        }
    }
    fun nudgeLogo(dx: Int, dy: Int) {
        val p = tpl ?: return
        val lg = p.logo ?: return
        val b = lg.box
        tpl = p.copy(logo = lg.copy(box = b.copy(x = (b.x + dx * nudgeStep).coerceIn(0.0, 99.0), y = (b.y + dy * nudgeStep).coerceIn(0.0, 99.0))))
    }
    fun resizeLogo(delta: Double) {
        val p = tpl ?: return
        val lg = p.logo ?: return
        val b = lg.box
        val ratio = b.h / b.w
        val w = (b.w + delta).coerceIn(6.0, 90.0)
        tpl = p.copy(logo = lg.copy(box = b.copy(w = w, h = maxOf(3.0, w * ratio))))
    }

    // ---- Manual create / company ----
    fun createManual() {
        val pn = manualPn.trim()
        if (pn.isEmpty()) {
            Toast.error(t("scanSticker.enterPartNumberFirst")); return
        }
        val comp = company
        var ct = if (comp in Stickers.FORMATTED) "datamatrix" else "qr"
        val rl = listOf(Stickers.Raw(pn, true))
        rawLines = rl
        buildTpl(rl, 1.6, true, ct, pn, comp, codeSize)
        val fmt = companyFormats[comp]
        if (fmt != null) {
            fmt.obj("code")?.let { fc ->
                fc.str("type").takeIf { it.isNotEmpty() }?.let { ct = it }
                fc.optDouble("sizeMm", Double.NaN).takeIf { !it.isNaN() }?.let { codeSize = it }
            }
            tpl = tpl?.let { Stickers.applyCompanyFormat(it, fmt) }
        }
        partNumber = pn
        codeType = ct
        cellMap.clear()
        manualPn = ""
        Toast.success(t("scanSticker.blankStickerCreated"))
    }

    fun applyCompany(comp: String) {
        company = comp
        val fmt = companyFormats[comp]
        val prev = tpl ?: return
        val lines = Stickers.buildLines(rawLines, prev.aspect, prev.code != null, prev.logo != null, comp)
        var code = prev.code?.copy(box = Stickers.codeBox(prev.aspect, comp))
        if (code != null) {
            val nt = if (comp in Stickers.FORMATTED) "datamatrix" else if (code.type == "datamatrix") "qr" else code.type
            code = code.copy(type = nt)
            codeType = nt
        }
        var next = prev.copy(company = comp, lines = lines, code = code)
        if (fmt != null) {
            fmt.obj("code")?.let { fc ->
                fc.optDouble("sizeMm", Double.NaN).takeIf { !it.isNaN() }?.let { codeSize = it }
                fc.str("type").takeIf { it.isNotEmpty() }?.let { codeType = it }
            }
            next = Stickers.applyCompanyFormat(next, fmt)
        }
        tpl = next
    }

    // ---- Edits ----
    fun applyPn(v: String) {
        partNumber = v
        tpl = tpl?.let { p -> p.copy(code = p.code?.copy(value = v)) }
    }
    fun setCode(ct: String) {
        codeType = ct
        tpl = tpl?.let { p -> p.copy(code = p.code?.copy(type = ct)) }
    }
    fun changeCodeSize(delta: Double) {
        val s = (Math.round((codeSize + delta) * 10) / 10.0).coerceIn(5.0, 30.0)
        codeSize = s
        tpl = tpl?.let { p -> p.copy(code = p.code?.copy(sizeMm = s)) }
    }
    fun moveCode(dx: Int, dy: Int) {
        val p = tpl ?: return
        val c = p.code ?: return
        tpl = p.copy(code = c.copy(box = c.box.copy(x = (c.box.x + dx * nudgeStep).coerceIn(0.0, 99.0), y = (c.box.y + dy * nudgeStep).coerceIn(0.0, 99.0))))
    }
    fun editLine(idx: Int, text: String) {
        tpl = tpl?.let { p -> p.copy(lines = p.lines.mapIndexed { i, l -> if (i == idx) l.copy(text = text) else l }) }
    }
    fun nudgeSel(dx: Int, dy: Int) {
        val p = tpl ?: return
        if (selLines.isEmpty()) return
        tpl = p.copy(lines = p.lines.mapIndexed { i, l -> if (i in selLines) l.copy(x = (l.x + dx * nudgeStep).coerceIn(0.0, 99.0), y = (l.y + dy * nudgeStep).coerceIn(0.0, 99.0)) else l })
    }
    fun resizeSel(delta: Double) {
        val p = tpl ?: return
        if (selLines.isEmpty()) return
        tpl = p.copy(lines = p.lines.mapIndexed { i, l -> if (i in selLines) l.copy(size = (Math.round((l.size + delta) * 10) / 10.0).coerceIn(2.0, 20.0)) else l })
    }
    fun boldSel() {
        val p = tpl ?: return
        if (selLines.isEmpty()) return
        val anyNotBold = p.lines.withIndex().any { (i, l) -> i in selLines && !l.bold }
        tpl = p.copy(lines = p.lines.mapIndexed { i, l -> if (i in selLines) l.copy(bold = anyNotBold) else l })
    }

    // ---- Saving ----
    fun formatTemplateJson(p: Tpl, fullCodeBox: Boolean): JSONObject = JSONObject()
        .put("lines", JSONArray().also { a -> p.lines.forEach { a.put(JSONObject().put("text", it.text).put("x", it.x).put("y", it.y).put("size", it.size).put("bold", it.bold)) } })
        .put("code", p.code?.let { c -> JSONObject().put("type", c.type).put("sizeMm", c.sizeMm).put("box", if (fullCodeBox) c.box.json() else JSONObject().put("y", c.box.y)) } ?: JSONObject.NULL)
        .put("logo", p.logo?.let { JSONObject().put("box", it.box.json()) } ?: JSONObject.NULL)

    fun saveTemplate() {
        val p = tpl ?: return
        scope.launch {
            try {
                Api.post(
                    "/sticker-templates",
                    JSONObject().put("name", partNumber.trim().ifEmpty { "Sticker" }).put("bg_data_url", p.json().toString())
                        .put("aspect", p.aspect).put("pn_box", JSONObject.NULL).put("part_number", partNumber).put("company", company),
                )
                Toast.success(t("scanSticker.templateSaved"))
                loadSaved()
            } catch (e: Exception) {
                Toast.error(errMsg(e, t("common.saveFailed")))
            }
        }
    }
    fun saveCompanyFormat() {
        val p = tpl ?: return
        val template = formatTemplateJson(p, fullCodeBox = false)
        scope.launch {
            try {
                Api.post("/company-formats", JSONObject().put("company", company).put("template", template))
                companyFormats = companyFormats + (company to template)
                Toast.success("${t("scanSticker.savedAsPrefix")} $company ${t("scanSticker.formatSuffix")}")
            } catch (e: Exception) {
                Toast.error(errMsg(e, t("common.saveFailed")))
            }
        }
    }
    fun saveNamedFormat() {
        val p = tpl ?: return
        val name = formatName.trim()
        if (name.isEmpty()) {
            Toast.error(t("scanSticker.enterFormatName")); return
        }
        scope.launch {
            try {
                Api.post("/company-formats", JSONObject().put("company", name).put("template", formatTemplateJson(p, fullCodeBox = true)))
                Toast.success("${t("scanSticker.savedFormatPrefix")} \"$name\"")
                formatName = ""
                loadSaved()
            } catch (e: Exception) {
                Toast.error(errMsg(e, t("common.saveFailed")))
            }
        }
    }
    fun loadFormat(f: JSONObject) {
        val p = tpl
        if (p != null && rawLines.isNotEmpty()) {
            buildTplFromFormat(rawLines, f, p.aspect, p.code != null, partNumber, company)
            pendingFormatId = null
            Toast.success("${t("scanSticker.appliedFormatPrefix")} \"${f.str("company")}\" ${t("scanSticker.formatSuffix")}")
        } else {
            pendingFormatId = f.str("id")
            Toast.show("\"${f.str("company")}\" ${t("scanSticker.willApplyNextScanSuffix")}")
        }
    }
    fun openTemplate(s: JSONObject) {
        val parsed = Tpl.parse(s.str("bg_data_url"))
        if (parsed == null) {
            Toast.error(t("scanSticker.couldNotOpenTemplate")); return
        }
        val comp = parsed.company ?: "Hyundai / Kia"
        val rawT = parsed.lines.map { Stickers.Raw(it.text, it.bold) }
        val needsZones = comp in Stickers.FORMATTED && parsed.lines.none { it.zone != null }
        val lines = if (needsZones) Stickers.buildLines(rawT, parsed.aspect, parsed.code != null, false, comp) else parsed.lines
        tpl = parsed.copy(company = comp, lines = lines)
        partNumber = s.str("part_number").ifEmpty { parsed.code?.value ?: "" }
        codeType = parsed.code?.type ?: "qr"
        codeSize = parsed.code?.sizeMm ?: 10.0
        company = comp
        rawLines = rawT
        cellMap.clear()
    }
    fun deleteTemplate(id: String) = scope.launch { try { Api.delete("/sticker-templates/$id"); loadSaved() } catch (_: Exception) {} }
    fun deleteLogo(id: String) = scope.launch { try { Api.delete("/logos/$id"); loadSaved() } catch (_: Exception) {} }

    // ---- Print composer ----
    fun tplForId(id: String): Tpl? =
        if (id == "__current__") tpl else saved.firstOrNull { it.str("id") == id }?.let { Tpl.parse(it.str("bg_data_url")) }

    fun assignCell(num: Int) {
        if (cellMap[num] == activeId) cellMap.remove(num) else cellMap[num] = activeId
    }
    fun autoFill() {
        val q = fillQty.trim().toIntOrNull() ?: 0
        if (q <= 0) {
            Toast.error(t("scanSticker.enterQuantity")); return
        }
        var placed = 0
        for (i in 1..layout.total) {
            if (placed >= q) break
            if (cellMap[i] == null) {
                cellMap[i] = activeId; placed++
            }
        }
        if (placed < q) Toast.show("${t("scanSticker.onlyPrefix")} $placed ${t("scanSticker.emptyBlocksLeftSuffix")}")
    }
    fun onPrint() {
        if (cellMap.isEmpty()) {
            Toast.error(t("scanSticker.placeAtLeastOne")); return
        }
        val cells = cellMap.mapNotNull { (k, id) -> tplForId(id)?.let { k to it } }.toMap()
        if (cells.isEmpty()) {
            Toast.error(t("scanSticker.selectedUnavailable")); return
        }
        val mt = marginTop.trim().takeIf { it.isNotEmpty() }?.let { maxOf(0.0, it.toDoubleOrNull() ?: 0.0) }
        val ml = marginLeft.trim().takeIf { it.isNotEmpty() }?.let { maxOf(0.0, it.toDoubleOrNull() ?: 0.0) }
        val pm = pageMargin.trim().toDoubleOrNull()?.let { maxOf(0.0, it) } ?: 0.0
        try {
            Printer.print(context, Stickers.composedSheetHtml(cells, layout, mt, ml, pm), "Stickers")
        } catch (e: Exception) {
            Toast.error(errMsg(e, t("storeArrangement.printFailed")))
        }
    }

    // ---------------- UI ----------------
    Screen {
        TopBar(t("scanSticker.title"), t("scanSticker.subtitle"), onBack = { nav.back() })
        val cur = tpl
        if (cur != null && !busy) {
            Column(
                Modifier.fillMaxWidth().background(C.Card).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(t("scanSticker.livePreview"), color = C.Brand, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 4.dp))
                StickerPreview(cur, layout.h, codeSize)
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                BigButton(t("scanSticker.gallery"), onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f), icon = Icons.Filled.PhotoLibrary, enabled = !busy)
                Spacer(Modifier.width(12.dp))
                BigButton(t("scanSticker.camera"), onClick = { takePhoto() }, modifier = Modifier.weight(1f), icon = Icons.Filled.PhotoCamera, enabled = !busy)
            }

            Spacer(Modifier.height(12.dp))
            Card {
                Text(t("scanSticker.orCreateByPartNumber"), color = C.Brand, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manualPn,
                        onValueChange = { manualPn = it },
                        placeholder = { Text(t("scanSticker.manualPnPlaceholder"), color = C.Muted) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrect = false, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { createManual() }),
                    )
                    Spacer(Modifier.width(8.dp))
                    GhostButton(t("scanSticker.create"), Icons.Filled.AddCircle, filled = true) { if (!busy) createManual() }
                }
            }

            if (saved.isNotEmpty()) {
                StLabel(t("scanSticker.savedStickers"))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    saved.forEach { s ->
                        SavedTile(Icons.AutoMirrored.Filled.Label, s.str("name"), onClick = { openTemplate(s) }, onDelete = { deleteTemplate(s.str("id")) })
                    }
                }
            }

            if (formats.isNotEmpty()) {
                StLabel(t("scanSticker.loadFormat"))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    formats.forEach { f ->
                        SavedTile(Icons.Filled.GridView, f.str("company"), active = pendingFormatId == f.str("id"), onClick = { loadFormat(f) })
                    }
                }
                val pf = pendingFormatId
                if (pf != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().background(C.BrandFaint, RoundedCornerShape(8.dp)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = C.Brand, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${t("scanSticker.nextScanUsesPrefix")} \"${formats.firstOrNull { it.str("id") == pf }?.str("company") ?: ""}\" ${t("scanSticker.scanNowToApply")}",
                            color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Filled.Cancel, contentDescription = null, tint = C.Red, modifier = Modifier.size(20.dp).pressable { pendingFormatId = null })
                    }
                }
            }

            if (busy) {
                Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = C.Brand)
                    Spacer(Modifier.height(12.dp))
                    Text(t("scanSticker.generatingClean"), color = C.Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }

            if (cur != null && !busy) {
                StLabel(t("scanSticker.partNumberLabel"))
                OutlinedTextField(
                    value = partNumber,
                    onValueChange = { applyPn(it) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrect = false),
                )

                StLabel(t("scanSticker.companyFormatLabel"))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    Stickers.COMPANIES.forEach { c ->
                        Chip(if (c in Stickers.FORMATTED) "$c ★" else c, company == c) { applyCompany(c) }
                    }
                }
                Text(
                    if (company in Stickers.FORMATTED) "★ $company ${t("scanSticker.formattedHintSuffix")}" else "$company: ${t("scanSticker.standardHintSuffix")}",
                    color = C.Muted, fontSize = 12.sp,
                )

                StLabel(t("scanSticker.companyLogoLabel"))
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier.padding(end = 10.dp).size(64.dp, 56.dp).border(1.dp, C.Brand, RoundedCornerShape(8.dp))
                            .pressable { pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = C.Brand)
                        Text(t("inventory.add"), color = C.Brand, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Column(
                        Modifier.padding(end = 10.dp).size(64.dp, 56.dp).border(1.dp, C.Brand, RoundedCornerShape(8.dp)).pressable { applyLogo(null) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Filled.Block, contentDescription = null, tint = C.Muted, modifier = Modifier.size(18.dp))
                        Text(t("scanSticker.none"), color = C.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    logos.forEach { lg ->
                        val url = lg.str("data_url")
                        val bmp = remember(url) { dataUrlBitmap(url) }
                        Box(Modifier.padding(end = 10.dp, top = 6.dp)) {
                            Box(
                                Modifier.size(72.dp, 56.dp).background(Color.White, RoundedCornerShape(8.dp)).border(1.dp, C.Line, RoundedCornerShape(8.dp)).pressable { applyLogo(url) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (bmp != null) Image(bmp, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
                            }
                            Icon(
                                Icons.Filled.Cancel, contentDescription = null, tint = C.Red,
                                modifier = Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(20.dp).background(C.Card, RoundedCornerShape(10.dp)).pressable { deleteLogo(lg.str("id")) },
                            )
                        }
                    }
                }
                if (cur.logo != null) {
                    Text("${t("scanSticker.logoMoveResize")} $nudgeStep)", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    FlowRow {
                        PadBtn(Icons.AutoMirrored.Filled.KeyboardArrowLeft) { nudgeLogo(-1, 0) }
                        PadBtn(Icons.Filled.KeyboardArrowUp) { nudgeLogo(0, -1) }
                        PadBtn(Icons.Filled.KeyboardArrowDown) { nudgeLogo(0, 1) }
                        PadBtn(Icons.AutoMirrored.Filled.KeyboardArrowRight) { nudgeLogo(1, 0) }
                        PadBtn(Icons.Filled.Remove) { resizeLogo(-3.0) }
                        PadBtn(Icons.Filled.Add) { resizeLogo(3.0) }
                    }
                }

                StLabel("${t("scanSticker.codeTypeLabel")} (${Stickers.CODE_TYPES.size} ${t("scanSticker.typesSuffix")})")
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    Stickers.CODE_TYPES.forEach { (key, label) -> Chip(label, codeType == key) { setCode(key) } }
                }

                Text("${t("scanSticker.codeSizeLabel")} ${fmt1(codeSize)} ${t("labels.mmSuffix")}", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PadBtn(Icons.Filled.Remove) { changeCodeSize(-1.0) }
                    Box(
                        Modifier.padding(3.dp).height(44.dp).widthIn(min = 80.dp).background(C.Bg, RoundedCornerShape(8.dp)).border(1.dp, C.Line, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("${fmt1(codeSize)} ${t("labels.mmSuffix")}", color = C.Text, fontWeight = FontWeight.ExtraBold) }
                    PadBtn(Icons.Filled.Add) { changeCodeSize(1.0) }
                }

                Text("${t("scanSticker.moveCodeLabel")} $nudgeStep)", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Row {
                    PadBtn(Icons.AutoMirrored.Filled.KeyboardArrowLeft) { moveCode(-1, 0) }
                    PadBtn(Icons.Filled.KeyboardArrowUp) { moveCode(0, -1) }
                    PadBtn(Icons.Filled.KeyboardArrowDown) { moveCode(0, 1) }
                    PadBtn(Icons.AutoMirrored.Filled.KeyboardArrowRight) { moveCode(1, 0) }
                }

                StLabel(t("scanSticker.arrangeLabel"))
                FlowRow(verticalArrangement = Arrangement.Center) {
                    Text(t("scanSticker.step"), color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp, top = 8.dp))
                    listOf(1, 2, 3, 5).forEach { s -> Chip("$s", nudgeStep == s) { nudgeStep = s } }
                    MiniBtn(t("common.all")) { selLines = cur.lines.indices.toSet() }
                    MiniBtn(t("history.clear")) { selLines = emptySet() }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PadBtn(Icons.Filled.KeyboardArrowUp) { nudgeSel(0, -1) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PadBtn(Icons.AutoMirrored.Filled.KeyboardArrowLeft) { nudgeSel(-1, 0) }
                            Box(Modifier.padding(3.dp).size(48.dp, 44.dp).background(C.BrandFaint, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                Text("${selLines.size}", color = C.Brand, fontWeight = FontWeight.ExtraBold)
                            }
                            PadBtn(Icons.AutoMirrored.Filled.KeyboardArrowRight) { nudgeSel(1, 0) }
                        }
                        PadBtn(Icons.Filled.KeyboardArrowDown) { nudgeSel(0, 1) }
                    }
                    Spacer(Modifier.width(24.dp))
                    Column {
                        PadBtn(text = "A+") { resizeSel(0.5) }
                        PadBtn(text = "A-") { resizeSel(-0.5) }
                        PadBtn(text = "B", bold = true) { boldSel() }
                    }
                }

                StLabel(t("scanSticker.textLinesLabel"))
                cur.lines.forEachIndexed { i, ln ->
                    val on = i in selLines
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp).background(if (on) C.BrandFaint else Color.Transparent, RoundedCornerShape(8.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (on) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, contentDescription = null,
                            tint = if (on) C.Brand else C.Muted,
                            modifier = Modifier.size(44.dp).pressable { selLines = if (on) selLines - i else selLines + i }.padding(10.dp),
                        )
                        OutlinedTextField(value = ln.text, onValueChange = { editLine(i, it) }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(8.dp))
                BigButton(
                    t("scanSticker.saveThisSticker") + if (company in Stickers.FORMATTED) " ($company)" else "",
                    onClick = { saveTemplate() }, outlined = true, icon = Icons.Filled.Bookmark,
                )
                Spacer(Modifier.height(8.dp))
                BigButton(
                    "${t("scanSticker.saveAsPrefix")} $company ${t("scanSticker.formatCaps")}" + if (companyFormats.containsKey(company)) " ${t("scanSticker.updateSuffix")}" else "",
                    onClick = { saveCompanyFormat() }, icon = Icons.Filled.Collections,
                )
                Text(t("scanSticker.formatExplain"), color = C.Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))

                StLabel(t("scanSticker.saveAsNamedFormatLabel"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = formatName, onValueChange = { formatName = it }, singleLine = true,
                        placeholder = { Text(t("scanSticker.formatNamePlaceholder"), color = C.Muted) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { saveNamedFormat() }),
                    )
                    Spacer(Modifier.width(8.dp))
                    GhostButton(t("scanSticker.save"), Icons.Filled.Save, filled = true) { saveNamedFormat() }
                }
                Text(t("scanSticker.namedFormatExplain"), color = C.Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))

                // ---- Print ----
                Text(t("scanSticker.printSectionLabel"), color = C.Brand, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    Stickers.SHEET_LAYOUTS.forEach { l ->
                        Chip("${l.code} (${l.total})", layoutCode == l.code) {
                            layoutCode = l.code
                            cellMap.clear()
                        }
                    }
                }
                Text("${layout.label} · ${fmt1(layout.w)} × ${fmt1(layout.h)} ${t("labels.mmSuffix")} · ${layout.rows} × ${layout.cols}", color = C.Muted, fontSize = 12.sp)

                StLabel(t("scanSticker.pickStickerLabel"))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    Chip(t("scanSticker.thisDesign"), activeId == "__current__") { activeId = "__current__" }
                    saved.forEach { s -> Chip(s.str("part_number").ifEmpty { s.str("name") }, activeId == s.str("id")) { activeId = s.str("id") } }
                }
                FlowRow(verticalArrangement = Arrangement.Center) {
                    Text(t("scanSticker.autoFill"), color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp, top = 14.dp))
                    SmallInput(fillQty, { fillQty = it.filter { c -> c.isDigit() } }, t("scanSticker.qtyPlaceholder"), decimal = false)
                    Column(Modifier.padding(top = 10.dp)) { Row { MiniBtn(t("scanSticker.fillEmpty")) { autoFill() }; MiniBtn(t("history.clear")) { cellMap.clear() } } }
                }

                StLabel("${t("scanSticker.tapBlocksLabel")} (${cellMap.size} ${t("scanSticker.placedSuffix")})")
                val cellW = minOf(320f / layout.cols, 60f)
                val cellH = maxOf(10f, cellW / (layout.w / layout.h).toFloat())
                Card {
                    Column(Modifier.align(Alignment.CenterHorizontally)) {
                        for (r in 0 until layout.rows) {
                            Row {
                                for (c in 0 until layout.cols) {
                                    val num = r * layout.cols + c + 1
                                    if (num > layout.total) continue
                                    val assigned = cellMap[num]
                                    val lbl = when {
                                        assigned == null -> "$num"
                                        assigned == "__current__" -> "◆"
                                        else -> (saved.firstOrNull { it.str("id") == assigned }?.str("part_number")?.ifEmpty { null } ?: "•").take(3)
                                    }
                                    Box(
                                        Modifier.size(cellW.dp, cellH.dp)
                                            .background(if (assigned != null) C.Brand else C.Bg)
                                            .border(0.5.dp, C.Line)
                                            .pressable { assignCell(num) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(lbl, color = if (assigned != null) Color.White else C.Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                StLabel(t("labels.paperMargin"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallInput(marginTop, { marginTop = it }, t("labels.top"))
                    SmallInput(marginLeft, { marginLeft = it }, t("labels.left"))
                    MiniBtn("0 / 0") { marginTop = "0"; marginLeft = "0" }
                }
                StLabel(t("scanSticker.pageMarginEdgeToEdge"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallInput(pageMargin, { pageMargin = it }, "0")
                    MiniBtn(t("labels.setZero")) { pageMargin = "0" }
                }
                Text(t("labels.printDialogTip"), color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))

                Spacer(Modifier.height(14.dp))
                BigButton("${t("scanSticker.printSheetLabel")} (${cellMap.size})", onClick = { onPrint() }, icon = Icons.Filled.Print)
                Spacer(Modifier.height(40.dp))
            } else if (!busy) {
                Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.DocumentScanner, contentDescription = null, tint = C.Muted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(t("scanSticker.emptyStateHint"), color = C.Muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}
