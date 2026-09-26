package com.kabadimarket.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.RemoveShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kabadimarket.app.Nav
import com.kabadimarket.app.Route
import com.kabadimarket.app.data.Api
import com.kabadimarket.app.data.ApiException
import com.kabadimarket.app.data.Branding
import com.kabadimarket.app.data.Feedback
import com.kabadimarket.app.data.Gps
import com.kabadimarket.app.data.I18n
import com.kabadimarket.app.data.Printer
import com.kabadimarket.app.data.Share
import com.kabadimarket.app.data.User
import com.kabadimarket.app.data.arr
import com.kabadimarket.app.data.extractPartNumber
import com.kabadimarket.app.data.int
import com.kabadimarket.app.data.num
import com.kabadimarket.app.data.obj
import com.kabadimarket.app.data.objects
import com.kabadimarket.app.data.str
import com.kabadimarket.app.data.strings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

val COMPANIES = listOf(
    "All", "Maruti Suzuki", "Hyundai", "Tata", "Mahindra", "Kia", "Toyota", "Honda", "Nissan",
    "Renault", "Ford", "Volkswagen", "Skoda", "MG", "Datsun", "Chevrolet",
)
val CONDITIONS = listOf("Working", "Testing", "Repairable", "Damaged", "Incomplete", "Scrap", "Unknown")

/** Asks for location permission once and gives back the GPS text ("lat,lng"). */
@Composable
fun rememberGps(decimals: Int = 6, sep: String = ","): String {
    val context = LocalContext.current
    var gps by rememberSaveable { mutableStateOf("") }
    var granted by remember { mutableStateOf(Gps.hasPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        granted = res.values.any { it }
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Gps.PERMISSIONS) }
    LaunchedEffect(granted) {
        if (granted && gps.isEmpty()) gps = Gps.format(Gps.current(context), decimals, sep)
    }
    return gps
}

// ============================================================
//  SCAN (Search / Sell / Requirement entry — camera + manual)
// ============================================================

@Composable
fun ScanScreen(user: User, nav: Nav, mode: String) {
    val gps = rememberGps()
    var manual by rememberSaveable { mutableStateOf("") }
    var scanning by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val color = when (mode) {
        "sell" -> C.Brand
        "requirement" -> C.Amber
        else -> C.Muted
    }
    val modeTitle = t("module.$mode")

    fun proceed(raw: String) {
        val pn = extractPartNumber(raw)
        if (pn.isBlank()) return
        when (mode) {
            "sell" -> nav.replace(Route.Sell(pn))
            "requirement" -> nav.replace(Route.RequirementNew(pn, gps))
            else -> nav.replace(Route.Part(pn, gps))
        }
    }

    Screen {
        TopBar("$modeTitle — ${t("scan.scan")}", "${t("common.company")}: All", onBack = { nav.back() })
        if (user.isSuperAdmin) {
            Row(Modifier.fillMaxWidth().background(C.Card).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = if (gps.isNotBlank()) C.Green else C.Muted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (gps.isNotBlank()) "GPS: $gps" else t("scan.gettingGps"), color = C.Muted, fontSize = 12.sp)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CameraScanner(Modifier.fillMaxSize(), active = scanning) { code ->
                if (!scanning) return@CameraScanner
                scanning = false
                Feedback.success(context)
                proceed(code)
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                ScanBracket(color, Modifier.size(width = 260.dp, height = 170.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    t("scan.holdHint"), color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Column(Modifier.background(C.Card).padding(16.dp).imePadding()) {
            SectionTitle(t("scan.manualLabel"))
            Field(manual, { manual = it }, t("common.partNumber"), placeholder = "e.g. 39100-2B000", caps = true, imeAction = androidx.compose.ui.text.input.ImeAction.Go, onIme = { proceed(manual) })
            BigButton("$modeTitle — ${t("scan.continue")}", icon = Icons.AutoMirrored.Filled.ArrowForward, enabled = manual.isNotBlank(), onClick = { proceed(manual) })
        }
    }
}

// ============================================================
//  PART DETAIL (same sections as old app)
// ============================================================

private enum class EditMode { AiApprove, EditPart, NewPart }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PartScreen(user: User, nav: Nav, partNumber: String, gps: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val canViewDetails = user.can("view_part_details")
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var part by remember { mutableStateOf<JSONObject?>(null) }
    var restricted by remember { mutableStateOf<JSONObject?>(null) }
    var ai by remember { mutableStateOf<JSONObject?>(null) }
    var aiLoading by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var qrMm by rememberSaveable { mutableIntStateOf(30) }
    var pendingUnit by remember { mutableStateOf<String?>(null) }
    var deletingUnit by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf<EditMode?>(null) }

    LaunchedEffect(reload) {
        try {
            if (!canViewDetails) {
                restricted = Api.getObj("/inventory/location-check", mapOf("part_number" to partNumber))
                return@LaunchedEffect
            }
            val res = Api.getObj("/search", mapOf("q" to partNumber, "gps" to gps))
            data = res
            part = if (res.obj("part") != null) Api.getObj("/parts/${Api.seg(partNumber)}") else null
            try {
                val list = Api.getArr("/ai/research", mapOf("part_number" to partNumber)).objects()
                if (list.isNotEmpty()) ai = list[0]
            } catch (_: Exception) {
            }
        } catch (e: ApiException) {
            Toast.error(e.message ?: t("common.loadFailed"))
        } finally {
            loading = false
        }
    }

    fun adjust(delta: Int) {
        scope.launch {
            try {
                val res = Api.post("/stock/adjust", JSONObject().put("part_number", partNumber).put("delta", delta)) as JSONObject
                if (res.optBoolean("limit_reached")) {
                    Feedback.error(context)
                    Toast.error("${t("buy.stopBuying")} $partNumber — ${t("buy.limitReached")}")
                } else Feedback.success(context)
                reload++
            } catch (e: ApiException) {
                Toast.error(e.message ?: t("common.failed"))
            }
        }
    }

    fun runAi() {
        aiLoading = true
        scope.launch {
            try {
                val company = data?.obj("part")?.str("company")?.ifBlank { null } ?: "All"
                ai = Api.post("/ai/research", JSONObject().put("part_number", partNumber).put("company", company)) as JSONObject
                Toast.success(t("partDetail.aiComplete"))
            } catch (e: ApiException) {
                Toast.error(e.message ?: t("partDetail.aiFailed"))
            } finally {
                aiLoading = false
            }
        }
    }

    if (loading) {
        Screen {
            TopBar(t("partDetail.part"), onBack = { nav.back() })
            Loading(t("partDetail.loadingPart"))
        }
        return
    }

    // Staff without "view part details": only in-stock yes/no + location.
    if (!canViewDetails) {
        val exists = (restricted?.int("units_total") ?: 0) > 0
        val loc = formatAssignedLocation(restricted?.obj("assigned_location"))
        Screen {
            TopBar(t("partDetail.part"), onBack = { nav.back() })
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(Modifier.entrance(0)) {
                    SectionTitle(t("common.partNumber"))
                    Text(partNumber, color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    Badge(
                        if (exists) t("partDetail.inStockYes") else t("partDetail.notFoundInStore"),
                        if (exists) C.Green else C.Red, if (exists) C.GreenFaint else C.RedFaint,
                    )
                }
                Card(Modifier.entrance(1)) {
                    SectionTitle(t("partDetail.locationTitle"))
                    Text(loc.ifBlank { t("common.noLocation") }, color = if (loc.isBlank()) C.Muted else C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    val d = data
    val status = d?.str("status") ?: ""
    val p: JSONObject? = part ?: d?.obj("part")
    val limit = part?.obj("limit") ?: d?.obj("limit")
    val units = part?.arr("units")?.objects().orEmpty()

    Screen {
        TopBar(if (status.isNotBlank()) I18n.status(status) else t("partDetail.part"), t("partDetail.partMaster"), onBack = { nav.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Hero
            Card(Modifier.entrance(0)) {
                SectionTitle(t("common.partNumber"))
                Text(partNumber, color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(status)
                    if (p != null) StatusChip(p.str("verification_status"))
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Text("${t("partDetail.inStock")}: ", color = C.Text2, fontSize = 15.sp)
                    Text("${d?.int("stock_count") ?: 0}", color = C.Brand, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Text(" ${t("common.units")}", color = C.Text2, fontSize = 15.sp)
                }
            }

            // Barcode + QR
            Card(Modifier.entrance(1)) {
                SectionTitle(t("partDetail.barcodeQr"))
                BarcodeView(partNumber, height = 64)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    QrView(partNumber, Modifier.size((qrMm * 4).dp.coerceAtMost(220.dp)))
                    Text(partNumber, color = C.Muted, fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(t("partDetail.qrSize").uppercase(), color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    SmallIconBox(Icons.Filled.Remove, C.Amber) { qrMm = (qrMm - 2).coerceAtLeast(12) }
                    Text("$qrMm mm", color = C.Text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
                    SmallIconBox(Icons.Filled.Add, C.Green) { qrMm = (qrMm + 2).coerceAtMost(50) }
                }
                Spacer(Modifier.height(10.dp))
                BigButton(t("partDetail.printBarcodeLabel"), icon = Icons.Filled.Print, outlined = true, onClick = {
                    Printer.print(context, Printer.barcodeLabelHtml(Branding.from(user), partNumber, p?.str("company"), qrMm), "Barcode Label")
                })
            }

            // Details / new part
            if (p != null) {
                Card(Modifier.entrance(2)) {
                    SectionTitle(t("partDetail.details"))
                    InfoRow(t("common.name"), p.str("name"))
                    InfoRow(t("common.company"), p.str("company"))
                    InfoRow(t("common.category"), p.str("category"))
                    InfoRow(t("buy.variant"), p.str("variant"))
                    InfoRow(t("partDetail.year"), p.str("year"))
                    InfoRow(t("partDetail.oldNo"), p.str("old_number"))
                    InfoRow(t("partDetail.newNo"), p.str("new_number"))
                    InfoRow(t("partDetail.stickerColor"), p.str("sticker_color"))
                    InfoRow(t("partDetail.compatible"), p.arr("compatible_vehicles").strings().joinToString(", "))
                    if (p.str("technical_info").isNotBlank()) {
                        Text(t("partDetail.technicalInfo"), color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                        Text(p.str("technical_info"), color = C.Text2, fontSize = 14.sp)
                    }
                    Text("${t("partDetail.source")}: ${p.str("source").ifBlank { t("partDetail.manual") }}", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    if (user.can("manage_parts")) {
                        Spacer(Modifier.height(10.dp))
                        BigButton(t("partDetail.editDetails"), icon = Icons.Filled.Edit, outlined = true, onClick = { editMode = EditMode.EditPart })
                    }
                }
            } else {
                val cat = d?.obj("catalog")
                Card(Modifier.entrance(2)) {
                    SectionTitle(if (cat != null) t("partDetail.newToStore") else t("partDetail.newPart"))
                    if (cat != null) {
                        InfoNote(t("partDetail.foundInCatalog"), Icons.Filled.Public)
                        Spacer(Modifier.height(8.dp))
                        InfoRow(t("common.name"), cat.str("name"))
                        InfoRow(t("common.company"), cat.str("company"))
                        InfoRow(t("common.category"), cat.str("category"))
                        InfoRow(t("buy.variant"), cat.str("variant"))
                        InfoRow(t("partDetail.compatible"), cat.arr("compatible_vehicles").strings().joinToString(", "))
                        Text(t("partDetail.addToStoreHint"), color = C.Muted, fontSize = 13.sp)
                    } else {
                        Text(t("partDetail.notInLibrary"), color = C.Muted, fontSize = 13.sp)
                    }
                    if (user.can("manage_parts")) {
                        Spacer(Modifier.height(10.dp))
                        BigButton(
                            if (cat != null) t("partDetail.addToMyStore") else t("partDetail.addSaveNewPart"),
                            icon = Icons.Filled.Add, onClick = { editMode = EditMode.NewPart },
                        )
                    }
                }
            }

            // Purchase limit
            if (limit != null) {
                Card(Modifier.entrance(3)) {
                    SectionTitle(t("buy.purchaseLimit"))
                    LimitBar(limit.int("existing_stock"), if (limit.isNull("allowed_limit")) null else limit.int("allowed_limit"))
                    if (limit.str("status") == "STOP") {
                        Spacer(Modifier.height(8.dp))
                        DangerBanner(t("partDetail.limitReachedDoNotBuy"))
                    }
                }
            }

            // AI research
            Card(Modifier.entrance(4).animateContentSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = C.Brand, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("partDetail.aiResearch").uppercase(), color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(8.dp))
                val a = ai
                if (a == null) {
                    Text(t("partDetail.aiResearchHint"), color = C.Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    BigButton(t("partDetail.runAiResearch"), icon = Icons.Filled.Search, outlined = true, loading = aiLoading, onClick = { runAi() })
                } else {
                    val r = a.obj("result") ?: JSONObject()
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip(a.str("verification"))
                        StatusChip(a.str("approval_status"))
                        if (a.optBoolean("conflict")) Badge("⚠ ${t("partDetail.infoConflict")}", C.OnAmberFaint, C.AmberFaint)
                        if (a.optBoolean("grounded")) Badge("🌐 ${t("partDetail.liveWebSources")}", Color.White, C.Green)
                    }
                    Spacer(Modifier.height(8.dp))
                    val conf = a.int("confidence")
                    Text("${t("partDetail.confidence")}: $conf%", color = C.Text2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(
                        progress = { conf / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).padding(vertical = 2.dp),
                        color = if (conf >= 70) C.Green else if (conf >= 40) C.Amber else C.Red,
                        trackColor = C.Surface3,
                    )
                    InfoRow(t("common.name"), r.str("name"))
                    InfoRow(t("common.category"), r.str("category"))
                    InfoRow(t("partDetail.vehicles"), r.arr("compatible_vehicles").strings().joinToString(", "))
                    InfoRow(t("buy.variant"), r.str("variant"))
                    InfoRow(t("partDetail.year"), r.str("year").ifBlank { r.str("model_years") })
                    InfoRow(t("partDetail.crossRef"), r.arr("cross_reference").strings().joinToString(", "))
                    if (r.str("status") == "NOT_FOUND") Badge("? ${t("partDetail.aiNotFound")}", C.OnAmberFaint, C.AmberFaint)
                    if (a.optBoolean("from_database")) Badge("✓ ${t("partDetail.fromVerifiedLibrary")}", C.OnGreenFaint, C.GreenFaint)
                    if (r.str("technical_info").isNotBlank()) Text(r.str("technical_info"), color = C.Text2, fontSize = 13.sp)
                    val sources = a.arr("sources").strings()
                    if (sources.isNotEmpty()) {
                        Text(t("partDetail.sources"), color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                        sources.forEach { Text("• $it", color = C.Brand, fontSize = 12.sp) }
                    }
                    if (r.str("notes").isNotBlank()) Text(r.str("notes"), color = C.Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    if (a.str("approval_status") == "Pending" && user.can("ai_approve")) {
                        BigButton(t("partDetail.reviewEditApprove"), icon = Icons.Filled.Edit, onClick = { editMode = EditMode.AiApprove })
                        Spacer(Modifier.height(8.dp))
                        BigButton(t("common.reject"), icon = Icons.Filled.Close, color = C.Red, onClick = {
                            scope.launch {
                                try {
                                    Api.post("/ai/research/${Api.seg(a.str("id"))}/reject")
                                    Toast.show(t("partDetail.rejected"))
                                    reload++
                                } catch (e: ApiException) {
                                    Toast.error(e.message ?: t("partDetail.rejectFailed"))
                                }
                            }
                        })
                    } else if (a.str("approval_status") == "Pending") {
                        Badge("⏱ ${t("partDetail.awaitingApproval")}", C.OnAmberFaint, C.AmberFaint)
                    }
                    Spacer(Modifier.height(6.dp))
                    GhostButton(t("partDetail.rerunAi"), Icons.Filled.AutoAwesome, loading = aiLoading) { runAi() }
                }
            }

            // Stock units
            if (units.isNotEmpty()) {
                Card(Modifier.entrance(5)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("${t("partDetail.stockUnits")} (${units.size})", Modifier.weight(1f))
                        if (user.isStoreAdmin) {
                            SmallIconBox(Icons.Filled.Remove, C.Amber) { adjust(-1) }
                            Text("${units.size}", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 10.dp))
                            SmallIconBox(Icons.Filled.Add, C.Green) { adjust(1) }
                        }
                    }
                    units.forEach { u ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                            StatusChip(u.str("condition"))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(oldLocationText(u.obj("location")).ifBlank { t("common.noLocation") }, color = C.Text2, fontSize = 13.sp)
                                val g = u.obj("location")?.str("gps").orEmpty()
                                if (g.isNotBlank()) Text("📍 $g", color = C.Green, fontSize = 11.sp)
                                val photos = u.arr("photos").strings()
                                if (photos.isNotEmpty()) {
                                    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        photos.forEach { ph -> RemoteImage(ph, Modifier.size(56.dp).background(C.Surface3, RoundedCornerShape(6.dp))) }
                                    }
                                }
                            }
                            if (user.isStoreAdmin) {
                                Icon(Icons.Filled.Delete, contentDescription = null, tint = C.Red, modifier = Modifier.size(20.dp).pressable { pendingUnit = u.str("id") })
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Sticky actions
        Column(Modifier.background(C.Card).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (user.can("buy")) BigButton(t("buy.title"), icon = Icons.Filled.Download, outlined = true, modifier = Modifier.weight(1f), onClick = { nav.open(Route.Buy(partNumber)) })
                if (user.can("sell")) BigButton(t("sell.title"), icon = Icons.Filled.Payments, modifier = Modifier.weight(1f), onClick = { nav.open(Route.Sell(partNumber)) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (user.can("requirement")) {
                    Box(Modifier.weight(1f)) { GhostButton(t("module.requirement"), Icons.Filled.AddCircle) { nav.open(Route.RequirementNew(partNumber, gps)) } }
                }
                if (p != null) {
                    Box(Modifier.weight(1f)) {
                        GhostButton(t("partDetail.saveKnown"), Icons.Filled.Bookmark) {
                            scope.launch {
                                try {
                                    Api.post("/known-parts", JSONObject().put("part_number", partNumber).put("company", d?.obj("part")?.str("company")?.ifBlank { null } ?: "All"))
                                    Toast.success(t("partDetail.savedKnown"))
                                    reload++
                                } catch (e: ApiException) {
                                    Toast.error(e.message ?: t("common.failed"))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (pendingUnit != null) {
        ConfirmDialog(
            title = t("partDetail.deleteUnitTitle"), message = t("partDetail.deleteUnitMsg"),
            confirmText = t("common.delete"), danger = true, loading = deletingUnit,
            onConfirm = {
                deletingUnit = true
                scope.launch {
                    try {
                        Api.delete("/stock/unit/${Api.seg(pendingUnit ?: "")}")
                        Feedback.error(context)
                        Toast.success(t("partDetail.unitDeleted"))
                        pendingUnit = null
                        reload++
                    } catch (e: ApiException) {
                        Toast.error(e.message ?: t("common.failed"))
                    } finally {
                        deletingUnit = false
                    }
                }
            },
            onCancel = { pendingUnit = null },
        )
    }

    editMode?.let { mode ->
        PartEditDialog(mode, partNumber, part, d, ai, onClose = { editMode = null }, onSaved = { editMode = null; reload++ })
    }
}

@Composable
private fun PartEditDialog(
    mode: EditMode,
    partNumber: String,
    part: JSONObject?,
    data: JSONObject?,
    ai: JSONObject?,
    onClose: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val src: JSONObject? = when (mode) {
        EditMode.AiApprove -> ai?.obj("result")
        EditMode.EditPart -> part
        EditMode.NewPart -> data?.obj("catalog")
    }
    var name by remember { mutableStateOf(src?.str("name") ?: "") }
    var category by remember { mutableStateOf(src?.str("category") ?: "") }
    var company by remember {
        mutableStateOf(
            src?.str("company")?.ifBlank { null }
                ?: (if (mode == EditMode.AiApprove) ai?.str("company") else data?.obj("part")?.str("company"))?.ifBlank { null }
                ?: "All",
        )
    }
    var vehicles by remember { mutableStateOf(src?.arr("compatible_vehicles")?.strings()?.joinToString(", ") ?: "") }
    var variant by remember { mutableStateOf(src?.str("variant") ?: "") }
    var year by remember { mutableStateOf(src?.str("year") ?: "") }
    var tech by remember { mutableStateOf(if (mode == EditMode.NewPart) "" else src?.str("technical_info") ?: "") }
    var saving by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(20.dp), color = C.Card) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()).imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (mode) {
                            EditMode.AiApprove -> t("partDetail.reviewApprove")
                            EditMode.EditPart -> t("partDetail.editDetails")
                            EditMode.NewPart -> t("partDetail.addPartDetails")
                        },
                        fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Filled.Close, contentDescription = null, tint = C.Text, modifier = Modifier.pressable(onClose))
                }
                Text(partNumber, color = C.Brand, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
                if (mode == EditMode.AiApprove) {
                    InfoNote(t("partDetail.aiSuggestionHint"))
                    Spacer(Modifier.height(8.dp))
                }
                Field(name, { name = it }, t("common.name").uppercase())
                Field(category, { category = it }, t("common.category").uppercase())
                Field(company, { company = it }, t("common.company").uppercase())
                Field(vehicles, { vehicles = it }, t("buy.compatibleVehicles").uppercase(), placeholder = "Hyundai Creta, Kia Seltos")
                Field(variant, { variant = it }, t("buy.variant").uppercase())
                Field(year, { year = it }, t("partDetail.year").uppercase())
                Field(tech, { tech = it }, t("partDetail.technicalInfo").uppercase(), multiline = true)
                BigButton(
                    when (mode) {
                        EditMode.AiApprove -> t("partDetail.approveSaveVerified")
                        EditMode.EditPart -> t("common.saveChanges")
                        EditMode.NewPart -> t("partDetail.savePart")
                    },
                    icon = Icons.Filled.Check, loading = saving,
                    onClick = {
                        saving = true
                        val payload = JSONObject()
                            .put("name", name).put("category", category).put("company", company)
                            .put("compatible_vehicles", org.json.JSONArray(vehicles.split(",").map { it.trim() }.filter { it.isNotEmpty() }))
                            .put("variant", variant).put("year", year).put("technical_info", tech)
                        scope.launch {
                            try {
                                when (mode) {
                                    EditMode.AiApprove -> {
                                        Api.post("/ai/research/${Api.seg(ai?.str("id") ?: "")}/approve", payload)
                                        Toast.success(t("partDetail.editedSavedVerified"))
                                    }
                                    EditMode.EditPart -> {
                                        Api.patch("/parts/${Api.seg(partNumber)}", payload)
                                        Toast.success(t("partDetail.detailsUpdated"))
                                    }
                                    EditMode.NewPart -> {
                                        Api.post("/parts", payload.put("part_number", partNumber).put("source", "Manual"))
                                        Toast.success(t("partDetail.newPartSaved"))
                                    }
                                }
                                onSaved()
                            } catch (e: ApiException) {
                                Toast.error(e.message ?: t("common.saveFailed"))
                            } finally {
                                saving = false
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun SmallIconBox(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).border(1.dp, C.Line, RoundedCornerShape(8.dp)).background(C.Bg, RoundedCornerShape(8.dp)).pressable(onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) }
}

@Composable
fun DangerBanner(text: String) {
    Row(
        Modifier.fillMaxWidth().background(C.Red, RoundedCornerShape(10.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PanTool, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

// ============================================================
//  SELL
// ============================================================

@Composable
fun SellScreen(user: User, nav: Nav, partNumber: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var part by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    var selectedUnit by rememberSaveable { mutableStateOf<String?>(null) }
    var price by rememberSaveable { mutableStateOf("") }
    var buyer by rememberSaveable { mutableStateOf("") }
    var customerQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var customer by remember { mutableStateOf<JSONObject?>(null) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val full = Api.getObj("/parts/${Api.seg(partNumber)}")
            part = full
            if (selectedUnit == null) selectedUnit = full.arr("units").objects().firstOrNull()?.str("id")
        } catch (_: ApiException) {
            part = null
        } finally {
            loading = false
        }
    }

    LaunchedEffect(customerQuery) {
        results = emptyList()
        if (customerQuery.isBlank()) return@LaunchedEffect
        delay(300)
        searching = true
        try {
            results = Api.getArr("/customers", mapOf("q" to customerQuery.trim())).objects()
        } catch (_: ApiException) {
        } finally {
            searching = false
        }
    }

    if (loading) {
        Screen {
            TopBar(t("sell.title"), onBack = { nav.back() })
            Loading()
        }
        return
    }
    val units = part?.arr("units")?.objects().orEmpty()

    Screen {
        TopBar(t("sell.title").uppercase(), partNumber, onBack = { nav.back() })
        if (units.isEmpty()) {
            EmptyState(Icons.Outlined.RemoveShoppingCart, t("sell.noStock"), t("sell.noStockSub")) {
                BigButton(t("common.back"), outlined = true, onClick = { nav.back() }, modifier = Modifier.width(180.dp))
            }
            return@Screen
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.entrance(0)) {
                Row {
                    SectionTitle(t("sell.availableStock"), Modifier.weight(1f))
                    Text("${units.size} ${t("common.units")}", color = C.Brand, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                units.forEach { u ->
                    val id = u.str("id")
                    val sel = selectedUnit == id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(if (sel) C.BrandFaint else C.Card, RoundedCornerShape(10.dp))
                            .border(1.dp, if (sel) C.Brand else C.Line, RoundedCornerShape(10.dp))
                            .pressable { selectedUnit = id }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (sel) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked, contentDescription = null, tint = if (sel) C.Brand else C.Muted, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(oldLocationText(u.obj("location")).ifBlank { t("common.noLocation") }, color = C.Text2, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        StatusChip(u.str("condition"))
                    }
                }
            }
            if (user.can("view_price")) {
                Card(Modifier.entrance(1)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(t("sell.salePrice"), Modifier.weight(1f))
                        Row(Modifier.background(C.BrandFaint, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = C.Brand, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(t("common.adminOnly"), color = C.Brand, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Field(price, { price = it }, "₹", placeholder = "₹ 0", number = true)
                }
            }
            Card(Modifier.entrance(2)) {
                SectionTitle(t("sell.buyerOptional"))
                Field(buyer, { buyer = it }, t("sell.buyerName"))
            }
            Card(Modifier.entrance(3).animateContentSize()) {
                SectionTitle(t("sell.creditCardTitle"))
                val c = customer
                if (c != null) {
                    Row(
                        Modifier.fillMaxWidth().background(C.BrandFaint, RoundedCornerShape(10.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = C.Brand)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.str("name"), color = C.Text, fontWeight = FontWeight.Bold)
                            Text("${c.str("phone")} • ${t("sell.currentBalance")}: ${money(c.num("balance") ?: 0.0)}", color = C.Muted, fontSize = 12.sp)
                        }
                        Icon(Icons.Filled.Cancel, contentDescription = null, tint = C.Red, modifier = Modifier.pressable { customer = null; customerQuery = "" })
                    }
                } else {
                    Field(customerQuery, { customerQuery = it }, t("sell.searchCustomer"))
                    if (searching) Loading(t("common.searching"))
                    results.forEach { r ->
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp).border(1.dp, C.Line, RoundedCornerShape(10.dp))
                                .pressable { customer = r; results = emptyList() }.padding(12.dp),
                        ) {
                            Text(r.str("name"), color = C.Text, fontWeight = FontWeight.Bold)
                            Text("${r.str("phone")} • ${t("common.balance")}: ${money(r.num("balance") ?: 0.0)}", color = C.Muted, fontSize = 12.sp)
                        }
                    }
                }
                Text(if (customer != null) t("sell.creditHintOn") else t("sell.creditHintOff"), color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Column(Modifier.background(C.Card).padding(12.dp)) {
            BigButton(t("common.printBill"), icon = Icons.Filled.Print, outlined = true, onClick = {
                val u = units.firstOrNull { it.str("id") == selectedUnit } ?: units.first()
                Printer.print(
                    context,
                    Printer.receiptHtml(
                        Branding.from(user), "SELL",
                        Printer.Receipt(
                            partNumber, part?.str("name"), u.str("condition"), price.ifBlank { null }, buyer,
                            u.obj("location"), user.name,
                        ),
                    ),
                    "Sale Receipt",
                )
            })
            Spacer(Modifier.height(8.dp))
            BigButton(t("sell.confirmSell"), icon = Icons.Filled.Payments, loading = submitting, onClick = {
                val c = customer
                if (c != null && price.isBlank()) {
                    Toast.error(t("sell.errCreditPrice")); return@BigButton
                }
                submitting = true
                scope.launch {
                    try {
                        val body = JSONObject().put("part_number", partNumber)
                            .put("unit_id", selectedUnit ?: JSONObject.NULL)
                            .put("price", price.trim().toDoubleOrNull() ?: JSONObject.NULL)
                            .put("buyer", buyer)
                            .put("customer_id", c?.str("id") ?: JSONObject.NULL)
                        val res = Api.post("/sell", body) as JSONObject
                        Feedback.success(context)
                        Toast.success(
                            if (c != null) "${t("sell.soldOnCredit")} — ${c.str("name")}${t("sell.balanceIsNow")} ${money(res.num("credit_balance"))}"
                            else t("sell.soldReduced"),
                        )
                        val invId = res.obj("invoice")?.str("id").orEmpty()
                        if (invId.isNotBlank()) nav.replace(Route.Invoice(invId)) else nav.replace(Route.Part(partNumber, ""))
                    } catch (e: ApiException) {
                        Feedback.error(context)
                        Toast.error(e.message ?: t("sell.errFailed"))
                    } finally {
                        submitting = false
                    }
                }
            })
        }
    }
}

// ============================================================
//  INVOICE
// ============================================================

@Composable
fun InvoiceScreen(user: User, nav: Nav, invoiceId: String) {
    val context = LocalContext.current
    var inv by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var sharing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            inv = Api.getObj("/invoices/${Api.seg(invoiceId)}")
        } catch (e: ApiException) {
            Toast.error(e.message ?: t("invoice.loadFailed"))
        } finally {
            loading = false
        }
    }
    val i = inv
    if (loading || i == null) {
        Screen {
            TopBar(t("invoice.title"), onBack = { nav.back() })
            if (loading) Loading() else EmptyState(Icons.Outlined.Receipt, t("invoice.notFound"))
        }
        return
    }
    val rate = ((i.num("gst_rate") ?: 0.18) * 100).toInt()
    Screen {
        TopBar(i.str("invoice_number"), dateTime(i.str("at")), onBack = { nav.back() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (i.str("customer_name").isNotBlank()) {
                Card(Modifier.entrance(0)) {
                    SectionTitle(t("customers.detailTitle"))
                    Text(i.str("customer_name"), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            Card(Modifier.entrance(1)) {
                SectionTitle(t("invoice.item"))
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(i.str("part_number"), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        if (i.str("description").isNotBlank()) Text(i.str("description"), color = C.Muted, fontSize = 13.sp)
                    }
                    Text(money(i.num("price")), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            Card(Modifier.entrance(2)) {
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text(t("invoice.taxableAmount"), color = C.Muted, modifier = Modifier.weight(1f))
                    Text(money(i.num("price")), color = C.Text, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text("${t("invoice.gst")} ($rate%)", color = C.Muted, modifier = Modifier.weight(1f))
                    Text(money(i.num("gst_amount")), color = C.Text, fontWeight = FontWeight.Bold)
                }
                androidx.compose.material3.HorizontalDivider(color = C.Line, modifier = Modifier.padding(vertical = 6.dp))
                Row {
                    Text(t("invoice.total"), color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Text(money(i.num("total")), color = C.Brand, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            if (i.isNull("price")) Text(t("invoice.noPriceHint"), color = C.Muted, fontSize = 13.sp)
            BigButton(t("invoice.shareAsPdf"), icon = Icons.Filled.Share, loading = sharing, onClick = {
                sharing = true
                val html = Printer.invoiceHtml(Branding.from(user), i)
                Printer.sharePdf(context, html, "Invoice_${i.str("invoice_number").replace("/", "-")}.pdf") { ok ->
                    sharing = false
                    if (!ok) Printer.print(context, html, "Invoice ${i.str("invoice_number")}")
                }
            })
            BigButton(t("invoice.shareOnWhatsApp"), icon = Icons.Filled.Chat, outlined = true, color = C.WhatsApp, onClick = {
                Share.whatsApp(context, Printer.invoiceWhatsAppText(Branding.from(user), i))
            })
        }
    }
}

// ============================================================
//  REQUIREMENTS (Needs) + NEW REQUIREMENT
// ============================================================

private val REQ_STATUSES = listOf("All", "Pending", "Purchased", "Completed", "Cancelled")
private val REQ_NEXT = mapOf("Pending" to "Purchased", "Purchased" to "Completed", "Completed" to "Pending")

private fun prColor(p: String) = when (p) {
    "High" -> C.Red
    "Medium" -> C.Amber
    else -> C.Muted
}

@Composable
fun RequirementsScreen(user: User, nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by rememberSaveable { mutableStateOf("All") }
    var reqs by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(status, reload) {
        try {
            reqs = Api.getArr("/requirements", mapOf("status" to status.takeIf { it != "All" })).objects()
        } catch (_: ApiException) {
        } finally {
            loading = false
        }
    }

    Screen {
        TopBar(t("requirements.title"), t("requirements.subtitle"), onBack = { nav.back() }, actions = {
            if (reqs.isNotEmpty()) {
                Icon(Icons.Filled.Print, contentDescription = null, tint = C.Brand, modifier = Modifier.padding(end = 12.dp).pressable {
                    Printer.print(context, Printer.requirementsHtml(Branding.from(user), reqs), "Requirements")
                })
            }
            Box(Modifier.size(36.dp).background(C.Brand, CircleShape).pressable { nav.open(Route.Scan("requirement")) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
            }
        })
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, top = 12.dp)) {
            REQ_STATUSES.forEach { s -> Chip(if (s == "All") t("common.all") else I18n.status(s), status == s) { status = s } }
        }
        when {
            loading -> Loading()
            reqs.isEmpty() -> EmptyState(Icons.Outlined.Checklist, t("requirements.empty"), t("requirements.emptySub"))
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(reqs, key = { _, r -> r.str("id") }) { i, r ->
                    Row(
                        Modifier.fillMaxWidth().entrance(i).background(C.Card, RoundedCornerShape(12.dp)).border(1.dp, C.Line, RoundedCornerShape(12.dp)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(r.str("part_number"), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.size(8.dp).background(prColor(r.str("priority")), CircleShape))
                                Spacer(Modifier.width(4.dp))
                                Text(t("common.priority.${r.str("priority")}"), color = prColor(r.str("priority")), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            if (r.str("name").isNotBlank()) Text(r.str("name"), color = C.Text2, fontSize = 14.sp)
                            Text("${t("common.quantity")}: ${r.int("quantity")} • ${t("storeDetail.inStock")}: ${r.int("stock_count")}", color = C.Muted, fontSize = 12.sp)
                            if (user.isAdmin && (r.str("by_contact").isNotBlank() || r.str("gps").isNotBlank())) {
                                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (r.str("by_contact").isNotBlank()) {
                                        TrackChip(Icons.Filled.Call, r.str("by_contact"), C.Green) {
                                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${r.str("by_contact")}")))
                                        }
                                    }
                                    if (r.str("gps").isNotBlank()) {
                                        TrackChip(Icons.Filled.LocationOn, t("requirements.viewOnMap"), C.Brand) {
                                            Share.openUrl(context, "https://maps.google.com/?q=${r.str("gps")}")
                                        }
                                    }
                                }
                            }
                        }
                        Box(Modifier.pressable {
                            val next = REQ_NEXT[r.str("status")] ?: "Pending"
                            scope.launch {
                                try {
                                    Api.patch("/requirements/${Api.seg(r.str("id"))}", JSONObject().put("status", next))
                                    Toast.success("${t("limits.status")}: ${I18n.status(next)}")
                                    reload++
                                } catch (e: ApiException) {
                                    Toast.error(e.message ?: t("common.failed"))
                                }
                            }
                        }) { StatusChip(r.str("status")) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.border(1.dp, C.Line, RoundedCornerShape(50)).pressable(onClick).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = C.Text2, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RequirementNewScreen(nav: Nav, initialPn: String, gpsParam: String) {
    val scope = rememberCoroutineScope()
    val liveGps = if (gpsParam.isBlank()) rememberGps() else gpsParam
    var pn by rememberSaveable { mutableStateOf(initialPn) }
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf("Medium") }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var note by rememberSaveable { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    Screen {
        TopBar(t("reqNew.title"), t("reqNew.subtitle"), onBack = { nav.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp)) {
            Card(Modifier.entrance()) {
                Field(pn, { pn = it }, t("common.partNumber").uppercase(), placeholder = "e.g. 39100-2B000", caps = true)
                Field(name, { name = it }, t("common.name").uppercase(), placeholder = t("common.partName"))
                Field(category, { category = it }, t("common.category").uppercase(), placeholder = t("common.category"))
                Field(quantity, { quantity = it.filter(Char::isDigit) }, t("common.quantity").uppercase(), number = true)
                Field(note, { note = it }, t("common.note").uppercase(), placeholder = t("common.optional"), multiline = true)
                SectionTitle(t("reqNew.priority"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("High", "Medium", "Low").forEach { pr ->
                        val sel = priority == pr
                        Box(
                            Modifier.background(if (sel) prColor(pr) else C.Bg, RoundedCornerShape(10.dp))
                                .border(1.dp, if (sel) prColor(pr) else C.Line, RoundedCornerShape(10.dp))
                                .pressable { priority = pr }.padding(horizontal = 18.dp, vertical = 10.dp),
                        ) { Text(t("common.priority.$pr"), color = if (sel) Color.White else C.Text2, fontWeight = FontWeight.ExtraBold) }
                    }
                }
            }
        }
        Column(Modifier.background(C.Card).padding(12.dp)) {
            BigButton(t("reqNew.submit"), icon = Icons.Filled.AddCircle, loading = submitting, onClick = {
                if (pn.isBlank()) {
                    Toast.error(t("common.errPartNumberRequired")); return@BigButton
                }
                submitting = true
                scope.launch {
                    try {
                        Api.post(
                            "/requirements",
                            JSONObject().put("part_number", pn.trim()).put("company", "All").put("name", name).put("category", category)
                                .put("priority", priority).put("quantity", quantity.toIntOrNull() ?: 1).put("note", note).put("gps", liveGps),
                        )
                        Toast.success(t("reqNew.added"))
                        nav.replace(Route.Requirements)
                    } catch (e: ApiException) {
                        Toast.error(e.message ?: t("common.failed"))
                    } finally {
                        submitting = false
                    }
                }
            })
        }
    }
}
