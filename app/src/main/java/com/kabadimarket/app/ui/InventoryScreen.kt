package com.kabadimarket.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabadimarket.app.Nav
import com.kabadimarket.app.Route
import com.kabadimarket.app.data.Api
import com.kabadimarket.app.data.ApiException
import com.kabadimarket.app.data.Branding
import com.kabadimarket.app.data.Feedback
import com.kabadimarket.app.data.I18n
import com.kabadimarket.app.data.Printer
import com.kabadimarket.app.data.Share
import com.kabadimarket.app.data.User
import com.kabadimarket.app.data.extractPartNumber
import com.kabadimarket.app.data.int
import com.kabadimarket.app.data.obj
import com.kabadimarket.app.data.objects
import com.kabadimarket.app.data.scanBarcode
import com.kabadimarket.app.data.str
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(user: User, nav: Nav, onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var units by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var cond by rememberSaveable { mutableStateOf("All") }
    var pnFilter by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<JSONObject?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var lowStock by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var reload by remember { mutableIntStateOf(0) }
    var locCheck by remember { mutableStateOf<JSONObject?>(null) }
    var checkingLoc by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var currentLoc by remember { mutableStateOf(AssignedLocation(storeName = if (user.isSuperAdmin) null else user.storeName.ifBlank { null })) }

    fun params(): Map<String, String?> = mapOf("condition" to cond.takeIf { it != "All" }, "q" to pnFilter.trim())

    LaunchedEffect(cond, pnFilter, reload) {
        delay(250)
        try {
            units = Api.getArr("/inventory", params()).objects()
        } catch (_: ApiException) {
        } finally {
            loading = false
            refreshing = false
        }
        try {
            lowStock = Api.getArr("/inventory/low-stock").objects().associate { it.str("part_number") to it.int("low_stock_threshold") }
        } catch (_: ApiException) {
        }
    }

    fun checkLocation(raw: String) {
        val pn = raw.trim()
        if (pn.isEmpty()) {
            locCheck = null; return
        }
        checkingLoc = true
        scope.launch {
            try {
                locCheck = Api.getObj("/inventory/location-check", mapOf("part_number" to pn) + currentLoc.toQuery())
            } catch (_: ApiException) {
                locCheck = null
            } finally {
                checkingLoc = false
            }
        }
    }

    fun adjust(pn: String, delta: Int) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val res = Api.post("/stock/adjust", JSONObject().put("part_number", pn).put("delta", delta)) as JSONObject
                if (res.optBoolean("limit_reached")) {
                    Feedback.error(context)
                    Toast.error("${t("buy.stopBuying")} $pn — ${t("buy.limitReached")}")
                } else Feedback.success(context)
                reload++
            } catch (e: ApiException) {
                Toast.error(e.message ?: t("common.failed"))
            } finally {
                busy = false
            }
        }
    }

    Screen {
        TopBar(t("inventory.title"), t("inventory.subtitle"), onBack = { nav.back() }, actions = {
            if (units.isNotEmpty()) {
                if (exporting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Download, contentDescription = "Excel", tint = C.Brand, modifier = Modifier.pressable {
                    exporting = true
                    scope.launch {
                        try {
                            Share.file(context, Api.download("/inventory/excel", params()), "inventory.xlsx", Share.XLSX)
                        } catch (e: ApiException) {
                            Toast.error(e.message ?: t("common.exportFailed"))
                        } finally {
                            exporting = false
                        }
                    }
                })
                Spacer(Modifier.width(14.dp))
                Icon(Icons.Filled.Print, contentDescription = "Print", tint = C.Brand, modifier = Modifier.pressable {
                    Printer.print(context, Printer.inventoryHtml(Branding.from(user), units), "Inventory Report")
                })
                Spacer(Modifier.width(12.dp))
            }
            SignOutButton(onLogout)
        })

        // Part number filter + scan
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).height(48.dp).background(C.Card, RoundedCornerShape(10.dp)).border(1.dp, C.Line, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (pnFilter.isEmpty()) Text(t("inventory.filterByPartNumber"), color = C.Muted, fontSize = 15.sp)
                    BasicTextField(
                        value = pnFilter,
                        onValueChange = { pnFilter = it; locCheck = null },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = C.Text),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { checkLocation(pnFilter) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (pnFilter.isNotEmpty()) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = C.Muted, modifier = Modifier.size(18.dp).pressable { pnFilter = ""; locCheck = null })
                }
            }
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier.height(48.dp).background(C.Brand, RoundedCornerShape(10.dp)).pressable {
                    scanBarcode(context) { code ->
                        val pn = extractPartNumber(code)
                        pnFilter = pn
                        Toast.success("${t("inventory.filteringBy")} $pn")
                        checkLocation(pn)
                    }
                }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(t("inventory.scan"), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // Current location (where you're checking from)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).pressable { pickerOpen = !pickerOpen },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = C.Muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(formatAssignedLocation(currentLoc).ifBlank { t("inventory.currentLocationHint") }, color = C.Muted, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Icon(if (pickerOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = C.Muted, modifier = Modifier.size(18.dp))
        }
        AnimatedVisibility(pickerOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(horizontal = 16.dp).heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                LocationPicker(currentLoc, { currentLoc = it }, showStoreName = user.isSuperAdmin)
                BigButton(t("inventory.checkLocation"), icon = Icons.Filled.Search, onClick = {
                    checkLocation(locCheck?.str("part_number")?.ifBlank { null } ?: pnFilter)
                })
                Spacer(Modifier.height(8.dp))
            }
        }

        // Location check result
        val lc = locCheck
        when {
            checkingLoc -> LocBanner(Icons.Filled.Info, t("inventory.checkingLocation"), C.Surface3, C.Text2)
            lc != null && (lc.optBoolean("location_mismatch") || (lc.optJSONArray("inconsistent_locations")?.length() ?: 0) > 1) -> {
                val text = if (lc.optBoolean("location_mismatch")) {
                    "${t("inventory.wrongLocation")} ${formatAssignedLocation(lc.obj("assigned_location")).ifBlank { t("inventory.unknown") }}"
                } else {
                    val arr = lc.optJSONArray("inconsistent_locations")
                    "${t("inventory.inconsistentLocations")} " + (0 until (arr?.length() ?: 0)).joinToString(", ") { formatAssignedLocation(arr?.optJSONObject(it)) }
                }
                LocBanner(Icons.Filled.Warning, text, C.Red, Color.White)
            }
            lc != null && lc.obj("assigned_location") != null ->
                LocBanner(Icons.Filled.CheckCircle, "${t("inventory.correctLocation")} ${formatAssignedLocation(lc.obj("assigned_location"))}", C.Green, Color.White)
            lc != null -> LocBanner(Icons.Filled.Info, "${lc.str("part_number")} ${t("inventory.noAssignedLocationFor")}", C.Surface3, C.Text2)
        }

        Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, top = 4.dp)) {
            (listOf("All") + CONDITIONS).forEach { c -> Chip(if (c == "All") t("common.all") else I18n.status(c), cond == c) { cond = c } }
        }

        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; reload++ }, modifier = Modifier.weight(1f)) {
            when {
                loading -> Loading()
                units.isEmpty() -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    EmptyState(
                        Icons.Outlined.Inventory2,
                        if (pnFilter.isNotBlank()) t("inventory.noMatch") else t("inventory.noStock"),
                        if (pnFilter.isNotBlank()) "${t("common.nothingFoundFor")} \"$pnFilter\"" else t("inventory.addFromBuy"),
                    )
                }
                else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(units, key = { _, u -> u.str("id") }) { i, u ->
                        val pn = u.str("part_number")
                        Column(Modifier.fillMaxWidth().entrance(i).background(C.Card, RoundedCornerShape(12.dp)).border(1.dp, C.Line, RoundedCornerShape(12.dp))) {
                            Row(Modifier.fillMaxWidth().pressable { nav.open(Route.Part(pn, "")) }.padding(14.dp), verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    Text(pn, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                                    if (u.str("part_name").isNotBlank()) Text(u.str("part_name"), color = C.Text2, fontSize = 14.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = C.Muted, modifier = Modifier.size(13.dp))
                                        Text(oldLocationText(u.obj("location")).ifBlank { t("common.noLocation") }, color = C.Muted, fontSize = 12.sp)
                                    }
                                    val al = u.obj("assigned_location")
                                    if (al != null && formatAssignedLocation(al).isNotBlank()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Sell, contentDescription = null, tint = C.Green, modifier = Modifier.size(12.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text(formatAssignedLocation(al), color = C.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Box(Modifier.padding(top = 4.dp).background(C.AmberFaint, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                            Text("⏳ ${t("inventory.locationPending")}", color = C.OnAmberFaint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    lowStock[pn]?.let { th ->
                                        Row(Modifier.padding(top = 4.dp).background(C.Red, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Error, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text("${t("inventory.lowStock")} (${t("inventory.alertAt")} ≤ $th)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                StatusChip(u.str("condition"))
                            }
                            if (user.isStoreAdmin) {
                                HorizontalDivider(color = C.Divider)
                                Row(Modifier.fillMaxWidth()) {
                                    AdminAction(Icons.Filled.Remove, t("inventory.reduce"), C.Amber, Modifier.weight(1f)) { adjust(pn, -1) }
                                    AdminAction(Icons.Filled.Add, t("inventory.add"), C.Green, Modifier.weight(1f)) { adjust(pn, 1) }
                                    AdminAction(Icons.Filled.Delete, t("common.delete"), C.Red, Modifier.weight(1f)) { pendingDelete = u }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { u ->
        ConfirmDialog(
            title = t("inventory.deleteUnitTitle"),
            message = "${u.str("part_number")} ${t("inventory.unitDeleteMsg")}",
            confirmText = t("common.delete"), danger = true, loading = busy,
            onConfirm = {
                busy = true
                scope.launch {
                    try {
                        Api.delete("/stock/unit/${Api.seg(u.str("id"))}")
                        Feedback.error(context)
                        Toast.success(t("inventory.unitDeleted"))
                        pendingDelete = null
                        reload++
                    } catch (e: ApiException) {
                        Toast.error(e.message ?: t("common.failed"))
                    } finally {
                        busy = false
                    }
                }
            },
            onCancel = { pendingDelete = null },
        )
    }
}

@Composable
private fun LocBanner(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, bg: Color, fg: Color) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).background(bg, RoundedCornerShape(10.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AdminAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.pressable(onClick).padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
