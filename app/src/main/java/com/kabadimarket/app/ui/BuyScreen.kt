package com.kabadimarket.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.kabadimarket.app.Nav
import com.kabadimarket.app.Route
import com.kabadimarket.app.data.Api
import com.kabadimarket.app.data.ApiException
import com.kabadimarket.app.data.Branding
import com.kabadimarket.app.data.Feedback
import com.kabadimarket.app.data.I18n
import com.kabadimarket.app.data.Printer
import com.kabadimarket.app.data.User
import com.kabadimarket.app.data.arr
import com.kabadimarket.app.data.extractPartNumber
import com.kabadimarket.app.data.int
import com.kabadimarket.app.data.obj
import com.kabadimarket.app.data.str
import com.kabadimarket.app.data.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** One scanned part in the Buy draft (nothing is saved until "Confirm"). */
private class DraftLine(val pn: String, company: String) {
    var qty by mutableStateOf(1)
    var expanded by mutableStateOf(false)
    var condition by mutableStateOf("Working")
    var name by mutableStateOf("")
    var category by mutableStateOf("")
    var vehicles by mutableStateOf("")
    var variant by mutableStateOf("")
    var company by mutableStateOf(company)
    var rack by mutableStateOf("")
    var shelf by mutableStateOf("")
    var box by mutableStateOf("")
    var position by mutableStateOf("")
    var price by mutableStateOf("")
    val photos = mutableStateListOf<String>()
    var override by mutableStateOf(false)
    var info by mutableStateOf<JSONObject?>(null)
    var infoLoading by mutableStateOf(true)
    var searching by mutableStateOf(false)
    var uploading by mutableStateOf(false)
}

/** Shrinks a photo like the old app (quality 0.6) before upload. */
private suspend fun compressPhoto(bytes: ByteArray): ByteArray = withContext(Dispatchers.Default) {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    var sample = 1
    while (opts.outWidth / (sample * 2) >= 1600 && opts.outHeight / (sample * 2) >= 1200) sample *= 2
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return@withContext bytes
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 60, out)
    out.toByteArray()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuyScreen(user: User, nav: Nav, routePn: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gps = rememberGps(5, ", ", afterCamera = true)
    val lines = remember { mutableStateListOf<DraftLine>() }
    var manual by rememberSaveable { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var lastScan by remember { mutableStateOf("" to 0L) }
    var stopMessage by remember { mutableStateOf<String?>(null) }
    var dangerBorder by remember { mutableStateOf(false) }
    val flash = remember { Animatable(0f) }
    val dangerFlash = remember { Animatable(0f) }
    val counterScale = remember { Animatable(1f) }
    val total = lines.sumOf { it.qty }

    // ---- Photos (camera / gallery) for one line at a time ----
    var photoTarget by remember { mutableStateOf<String?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    fun uploadFor(pn: String, bytes: ByteArray) {
        val line = lines.firstOrNull { it.pn == pn } ?: return
        line.uploading = true
        scope.launch {
            try {
                val path = Api.uploadImage(compressPhoto(bytes))
                if (path.isNotBlank()) line.photos.add(path)
                Feedback.success(context)
            } catch (e: Exception) {
                Toast.error(t("buy.photoUploadFailed"))
            } finally {
                line.uploading = false
            }
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        val pn = photoTarget
        if (ok && f != null && pn != null && f.exists()) uploadFor(pn, f.readBytes())
    }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris: List<Uri> ->
        val pn = photoTarget ?: return@rememberLauncherForActivityResult
        val line = lines.firstOrNull { it.pn == pn } ?: return@rememberLauncherForActivityResult
        uris.take(6 - line.photos.size).forEach { uri ->
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { uploadFor(pn, it) }
        }
    }

    fun takePhotoFor(pn: String) {
        val line = lines.firstOrNull { it.pn == pn } ?: return
        if (line.photos.size >= 6) {
            Toast.show(t("buy.maxPhotos")); return
        }
        photoTarget = pn
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val f = File(dir, "p_${System.currentTimeMillis()}.jpg")
        cameraFile = f
        takePicture.launch(FileProvider.getUriForFile(context, context.packageName + ".files", f))
    }

    fun pickGalleryFor(pn: String) {
        val line = lines.firstOrNull { it.pn == pn } ?: return
        if (line.photos.size >= 6) {
            Toast.show(t("buy.maxPhotos")); return
        }
        photoTarget = pn
        pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // ---- Feedback animations ----
    fun scanFeedback() {
        Feedback.success(context)
        scope.launch {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(420))
        }
        scope.launch {
            counterScale.animateTo(1.6f, tween(130))
            counterScale.animateTo(1f, tween(260))
        }
    }

    fun dangerFeedback(pn: String) {
        Feedback.error(context)
        Feedback.limitSound(context)
        stopMessage = "${t("buy.stopBuying")} $pn — ${t("buy.limitReached")}"
        dangerBorder = true
        scope.launch {
            dangerFlash.animateTo(1f, tween(60))
            dangerFlash.animateTo(0.15f, tween(180))
            dangerFlash.animateTo(1f, tween(60))
            dangerFlash.animateTo(0f, tween(500))
        }
        scope.launch {
            delay(2200)
            dangerBorder = false
            stopMessage = null
        }
    }

    suspend fun allowedToAdd(pn: String, qtyBefore: Int, override: Boolean): Boolean {
        if (override) return true
        return try {
            val limit = Api.getObj("/limits/${Api.seg(pn)}")
            if (!limit.optBoolean("limit_enabled") || limit.isNull("allowed_limit")) return true
            val projected = limit.int("existing_stock") + qtyBefore + 1
            if (projected > limit.int("allowed_limit")) {
                dangerFeedback(pn)
                false
            } else true
        } catch (_: Exception) {
            true
        }
    }

    fun loadInfo(line: DraftLine) {
        line.infoLoading = true
        scope.launch {
            try {
                val res = Api.getObj("/search", mapOf("q" to line.pn))
                val src = res.obj("part") ?: res.obj("catalog")
                line.info = res
                if (src != null) {
                    if (line.name.isBlank()) line.name = src.str("name")
                    if (line.category.isBlank()) line.category = src.str("category")
                    if (line.variant.isBlank()) line.variant = src.str("variant")
                    if (line.vehicles.isBlank()) line.vehicles = src.arr("compatible_vehicles").strings().joinToString(", ")
                    val co = src.str("company")
                    if (co.isNotBlank() && co != "All" && (line.company == "All" || line.company.isBlank())) line.company = co
                }
                if (res.obj("part") == null && res.obj("catalog") != null) Toast.show("${line.pn}: ${t("buy.autoFilledCatalog")}")
            } catch (_: Exception) {
            } finally {
                line.infoLoading = false
            }
        }
    }

    fun addOne(raw: String, expand: Boolean = false) {
        val pn = extractPartNumber(raw)
        if (pn.isBlank() || busy) return
        busy = true
        scope.launch {
            val existing = lines.firstOrNull { it.pn == pn }
            if (allowedToAdd(pn, existing?.qty ?: 0, existing?.override ?: false)) {
                scanFeedback()
                if (existing != null) {
                    existing.qty += 1
                    if (expand) existing.expanded = true
                } else {
                    val fresh = DraftLine(pn, "All").also { it.expanded = expand }
                    lines.add(0, fresh)
                    loadInfo(fresh)
                }
            }
            delay(350)
            busy = false
        }
    }

    var initialDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (routePn.isNotBlank() && !initialDone) {
            initialDone = true
            addOne(routePn, expand = true)
        }
    }

    fun confirmAll() {
        if (lines.isEmpty() || confirming) return
        confirming = true
        scope.launch {
            val issues = mutableListOf<String>()
            var added = 0
            for (line in lines.toList()) {
                var ok = 0
                var stopReason = ""
                repeat(line.qty) {
                    if (stopReason.isNotEmpty()) return@repeat
                    try {
                        val loc = JSONObject().put("rack", line.rack).put("shelf", line.shelf).put("box", line.box)
                            .put("position", line.position).put("gps", gps)
                        val body = JSONObject()
                            .put("part_number", line.pn).put("company", line.company).put("name", line.name)
                            .put("category", line.category)
                            .put("compatible_vehicles", JSONArray(line.vehicles.split(",").map { it.trim() }.filter { it.isNotEmpty() }))
                            .put("variant", line.variant).put("condition", line.condition).put("location", loc)
                            .put("price", line.price.trim().toDoubleOrNull() ?: JSONObject.NULL)
                            .put("photos", JSONArray(line.photos.toList())).put("override", line.override)
                        Api.post("/buy", body)
                        ok++
                    } catch (e: ApiException) {
                        stopReason = if (e.code == "LIMIT_REACHED") t("buy.limitReachedLower") else (e.message ?: t("common.failed").lowercase())
                    }
                }
                added += ok
                val left = line.qty - ok
                if (left > 0) {
                    line.qty = left
                    issues.add("${line.pn}: ${t("buy.added")} $ok/${ok + left}${if (stopReason.isNotEmpty()) " — $stopReason" else ""}")
                } else {
                    lines.remove(line)
                }
            }
            confirming = false
            if (issues.isNotEmpty()) {
                Feedback.error(context)
                Toast.show("${t("buy.added")} $added ${t("buy.unitS")} — ${issues.size} ${t("buy.linesNeedAttention")}", if (added > 0) Toast.Kind.Info else Toast.Kind.Error)
                lines.forEach { loadInfo(it) }
            } else {
                Feedback.success(context)
                Toast.success("${t("buy.added")} $added ${t("buy.unitsToStock")}")
                nav.replace(Route.Inventory)
            }
        }
    }

    Screen {
        Box(Modifier.fillMaxSize().then(if (dangerBorder) Modifier.border(6.dp, C.Red) else Modifier)) {
            Column(Modifier.fillMaxSize()) {
                TopBar(t("buy.title"), if (user.isSuperAdmin) (if (gps.isNotBlank()) "📍 ${t("buy.gpsOk")}" else t("buy.gpsEllipsis")) else null, onBack = { nav.back() })
                AnimatedVisibility(stopMessage != null, enter = slideInVertically() + fadeIn(), exit = slideOutVertically() + fadeOut()) {
                    DangerBanner(stopMessage ?: "")
                }

                // Camera with scan bracket, green flash, red danger flash and the TOTAL counter
                Box(Modifier.fillMaxWidth().height(230.dp)) {
                    CameraScanner(Modifier.fillMaxSize()) { code ->
                        val now = System.currentTimeMillis()
                        if (code == lastScan.first && now - lastScan.second < 900) return@CameraScanner
                        lastScan = code to now
                        addOne(code)
                    }
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = flash.value }.background(C.Green.copy(alpha = 0.45f)))
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = dangerFlash.value }.background(C.Red.copy(alpha = 0.6f)))
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        ScanBracket(if (dangerBorder) C.Red else Color.White, Modifier.size(width = 220.dp, height = 120.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            t("buy.scanHint"), color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(6.dp),
                        )
                    }
                    Column(
                        Modifier.align(Alignment.TopEnd).padding(10.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(t("buy.total").uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "$total", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.graphicsLayer { scaleX = counterScale.value; scaleY = counterScale.value },
                        )
                    }
                }

                Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.Top) {
                    Field(manual, { manual = it }, t("buy.manualPlaceholder"), caps = true, modifier = Modifier.weight(1f),
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done, onIme = { addOne(manual); manual = "" })
                    Spacer(Modifier.width(8.dp))
                    SquareIconButton(Icons.Filled.Add) { addOne(manual); manual = "" }
                }
                if (user.isSuperAdmin) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = if (gps.isNotBlank()) C.Green else C.Amber, modifier = Modifier.size(16.dp))
                        Text(if (gps.isNotBlank()) "${t("buy.liveGps")}: $gps" else t("scan.gettingGps"), color = if (gps.isNotBlank()) C.Green else C.Amber, fontSize = 12.sp)
                    }
                }

                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (lines.isEmpty()) {
                        item { Text(t("buy.nothingScanned"), color = C.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
                    }
                    items(lines, key = { it.pn }) { line ->
                        DraftCard(
                            user, line,
                            onToggle = { line.expanded = !line.expanded },
                            onInc = { scope.launch { if (allowedToAdd(line.pn, line.qty, line.override)) line.qty += 1 } },
                            onDec = { line.qty = (line.qty - 1).coerceAtLeast(1) },
                            onRemove = { lines.remove(line) },
                            onAutofill = {
                                line.searching = true
                                scope.launch {
                                    try {
                                        val r = Api.post("/search/web", JSONObject().put("part_number", line.pn).put("company", line.company)) as JSONObject
                                        if (r.str("name").isNotBlank()) line.name = r.str("name")
                                        r.arr("models").strings().takeIf { it.isNotEmpty() }?.let { line.vehicles = it.joinToString(", ") }
                                        r.arr("variants").strings().takeIf { it.isNotEmpty() }?.let { line.variant = it.joinToString(", ") }
                                        Toast.success(if (r.optBoolean("cached")) t("buy.autofilledLibrary") else "${t("buy.autofilledWeb")} — ${r.int("result_count")} ${t("buy.webResults")}")
                                    } catch (e: ApiException) {
                                        if (e.code == "NO_KEY") {
                                            Toast.error(t("buy.noGoogleKey"))
                                            nav.open(Route.Soon("admin.toolSearchSetup"))
                                        } else Toast.error(e.message ?: t("buy.searchFailed"))
                                    } finally {
                                        line.searching = false
                                    }
                                }
                            },
                            onCamera = { takePhotoFor(line.pn) },
                            onGallery = { pickGalleryFor(line.pn) },
                            onPrint = {
                                Printer.print(
                                    context,
                                    Printer.receiptHtml(
                                        Branding.from(user), "BUY",
                                        Printer.Receipt(
                                            line.pn, line.name, line.condition, line.price.ifBlank { null }, null,
                                            JSONObject().put("rack", line.rack).put("shelf", line.shelf).put("box", line.box).put("position", line.position),
                                            user.name, line.qty,
                                        ),
                                    ),
                                    "Purchase Receipt",
                                )
                            },
                        )
                    }
                }
                Column(Modifier.background(C.Card).padding(12.dp)) {
                    BigButton(
                        if (confirming) t("buy.addingToStock") else "${t("buy.confirmAdd")} ($total)",
                        icon = Icons.Filled.CheckCircle, loading = confirming, enabled = lines.isNotEmpty(),
                        onClick = { confirmAll() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftCard(
    user: User,
    line: DraftLine,
    onToggle: () -> Unit,
    onInc: () -> Unit,
    onDec: () -> Unit,
    onRemove: () -> Unit,
    onAutofill: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onPrint: () -> Unit,
) {
    val limit = line.info?.obj("limit")
    val limitOn = limit?.optBoolean("limit_enabled") == true
    val isStop = limitOn && limit != null && !limit.isNull("remaining") && limit.int("remaining") <= 0
    val isWarn = limit?.str("status") == "WARNING"
    Column(
        Modifier
            .fillMaxWidth()
            .entrance()
            .background(C.Card, RoundedCornerShape(12.dp))
            .border(if (isStop && !line.override) 2.dp else 1.dp, if (isStop && !line.override) C.Red else C.Line, RoundedCornerShape(12.dp))
            .animateContentSize(),
    ) {
        Row(Modifier.fillMaxWidth().pressable(onToggle).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(line.pn, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                if (limitOn && limit != null) {
                    Text(
                        "${t("common.stock")} ${limit.int("existing_stock")} / ${t("buy.limit")} ${limit.int("allowed_limit")}",
                        color = if (isStop) C.Red else C.Muted, fontSize = 12.sp,
                    )
                } else if (line.infoLoading) {
                    Text(t("common.loading"), color = C.Muted, fontSize = 12.sp)
                }
            }
            SmallIconBox(Icons.Filled.Remove, C.Text, onDec)
            Box(Modifier.padding(horizontal = 6.dp).background(C.Brand, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("${line.qty}", color = Color.White, fontWeight = FontWeight.ExtraBold)
            }
            SmallIconBox(Icons.Filled.Add, C.Text, onInc)
            Spacer(Modifier.width(6.dp))
            SmallIconBox(Icons.Filled.Delete, C.Red, onRemove)
            Icon(if (line.expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = C.Muted, modifier = Modifier.padding(start = 4.dp))
        }
        if (line.expanded) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(t("buy.purchaseLimit"), Modifier.weight(1f))
                    line.info?.str("status")?.takeIf { it.isNotBlank() }?.let { StatusChip(it) }
                }
                LimitBar(limit?.int("existing_stock") ?: 0, if (limit == null || limit.isNull("allowed_limit")) null else limit.int("allowed_limit"))
                if (isStop && !line.override) {
                    Spacer(Modifier.height(8.dp))
                    DangerBanner("${I18n.status("DO NOT BUY")} — ${t("buy.limitReachedLower")}")
                } else if (isWarn) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().background(C.AmberFaint, RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = C.Amber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${I18n.status("WARNING")} — ${t("buy.nearLimit")}", color = C.OnAmberFaint, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(12.dp))
                SectionTitle(t("buy.condition"))
                ChipGroup(CONDITIONS, line.condition, label = { I18n.status(it) }) { line.condition = it }

                SectionTitle(t("buy.partCompat"))
                BigButton("🔍 ${t("buy.googleAutofill")}", icon = Icons.Filled.Search, outlined = true, loading = line.searching, onClick = onAutofill)
                Spacer(Modifier.height(10.dp))
                Field(line.name, { line.name = it }, t("common.name"), placeholder = t("common.partName"))
                Text(t("common.company").uppercase(), color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                    COMPANIES.forEach { c -> Chip(c, line.company == c) { line.company = c } }
                }
                Field(line.category, { line.category = it }, t("common.category"), placeholder = t("common.category"))
                Field(line.vehicles, { line.vehicles = it }, t("buy.compatibleVehicles"), placeholder = "Hyundai Creta, Kia Seltos")
                Field(line.variant, { line.variant = it }, t("buy.variant"), placeholder = "e.g. HTC Diesel")

                SectionTitle(t("buy.locationHeader"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(line.rack, { line.rack = it }, t("buy.rack"), placeholder = "R1", modifier = Modifier.weight(1f))
                    Field(line.shelf, { line.shelf = it }, t("buy.shelf"), placeholder = "S2", modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(line.box, { line.box = it }, t("buy.box"), placeholder = "B3", modifier = Modifier.weight(1f))
                    Field(line.position, { line.position = it }, t("buy.position"), placeholder = "P4", modifier = Modifier.weight(1f))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(t("buy.photosHeader"), Modifier.weight(1f))
                    Text("${line.photos.size}/6", color = C.Muted, fontSize = 12.sp)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    line.photos.forEach { path ->
                        Box(Modifier.size(72.dp)) {
                            RemoteImage(path, Modifier.fillMaxSize().background(C.Surface3, RoundedCornerShape(8.dp)))
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp).background(C.Red, CircleShape).pressable { line.photos.remove(path) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                        }
                    }
                    if (line.photos.size < 6) {
                        PhotoAddBox(if (line.uploading) Icons.Filled.HourglassTop else Icons.Filled.CameraAlt, t("buy.camera")) { if (!line.uploading) onCamera() }
                        PhotoAddBox(Icons.Filled.Image, t("buy.gallery")) { if (!line.uploading) onGallery() }
                    }
                }

                if (user.can("view_price")) {
                    Spacer(Modifier.height(12.dp))
                    SectionTitle(t("buy.purchasePrice"))
                    Field(line.price, { line.price = it }, "₹", placeholder = "₹ 0", number = true)
                }
                if (user.can("manage_limits")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t("buy.adminOverride"), color = C.Text, fontWeight = FontWeight.Bold)
                            Text(t("buy.overrideHint"), color = C.Muted, fontSize = 12.sp)
                        }
                        androidx.compose.material3.Switch(
                            checked = line.override,
                            onCheckedChange = { line.override = it },
                            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = C.Brand),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                BigButton(t("buy.printSlip"), icon = Icons.Filled.Print, outlined = true, onClick = onPrint)
            }
        }
    }
}

@Composable
private fun PhotoAddBox(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.size(72.dp).border(1.5.dp, C.Brand, RoundedCornerShape(8.dp)).background(C.BrandFaint, RoundedCornerShape(8.dp)).pressable(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = C.Brand, modifier = Modifier.size(20.dp))
        Text(label, color = C.Brand, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
