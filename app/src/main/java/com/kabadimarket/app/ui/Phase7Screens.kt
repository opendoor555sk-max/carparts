package com.kabadimarket.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kabadimarket.app.Nav
import com.kabadimarket.app.Route
import com.kabadimarket.app.data.Api
import com.kabadimarket.app.data.ApiException
import com.kabadimarket.app.data.Branding
import com.kabadimarket.app.data.Feedback
import com.kabadimarket.app.data.I18n
import com.kabadimarket.app.data.Printer
import com.kabadimarket.app.data.Session
import com.kabadimarket.app.data.Share
import com.kabadimarket.app.data.User
import com.kabadimarket.app.data.arr
import com.kabadimarket.app.data.int
import com.kabadimarket.app.data.num
import com.kabadimarket.app.data.obj
import com.kabadimarket.app.data.objects
import com.kabadimarket.app.data.str
import com.kabadimarket.app.data.strings
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun RowCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(C.Card, RoundedCornerShape(12.dp)).border(1.dp, C.Line, RoundedCornerShape(12.dp)).padding(14.dp),
        content = content,
    )
}

@Composable
private fun Pill(text: String, fg: Color, bg: Color) = Badge(text, fg, bg)

// ============================================================
//  ACTIVITY LOG
// ============================================================

@Composable
fun AuditLogScreen(nav: Nav) {
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        try {
            rows = Api.getArr("/audit-log").objects()
        } catch (e: ApiException) {
            Toast.error(e.message ?: t("auditLog.loadFailed"))
        } finally {
            loading = false
        }
    }
    Screen {
        TopBar(t("auditLog.title"), onBack = { nav.back() })
        when {
            loading -> Loading()
            rows.isEmpty() -> EmptyState(Icons.Outlined.History, t("auditLog.noEntries"))
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(rows, key = { idx, r -> r.str("id") + "#" + idx }) { i, r ->
                    val a = r.str("action")
                    val icon = when {
                        a.startsWith("cash_book") -> Icons.Filled.Inventory2
                        a.startsWith("quotation") -> Icons.Filled.Description
                        a.startsWith("purchase_order") -> Icons.Filled.Description
                        a.startsWith("reservation") -> Icons.Filled.Lock
                        a.startsWith("stock_transfer") -> Icons.Filled.Link
                        else -> Icons.Filled.History
                    }
                    Row(Modifier.fillMaxWidth().entrance(i).background(C.Card, RoundedCornerShape(12.dp)).border(1.dp, C.Line, RoundedCornerShape(12.dp)).padding(12.dp)) {
                        Icon(icon, contentDescription = null, tint = C.Brand, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(a, color = C.Text, fontWeight = FontWeight.Bold)
                            if (r.str("details").isNotBlank()) Text(r.str("details"), color = C.Text2, fontSize = 13.sp)
                            Text("${dateTime(r.str("at"))} • ${r.str("by")}", color = C.Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
//  AI APPROVALS
// ============================================================

@Composable
fun AiApprovalsScreen(user: User, nav: Nav) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        try {
            items = Api.getArr("/ai/research", mapOf("status" to "Pending")).objects()
        } catch (_: ApiException) {
        } finally {
            loading = false
        }
    }
    fun act(id: String, approve: Boolean) {
        scope.launch {
            try {
                Api.post("/ai/research/${Api.seg(id)}/${if (approve) "approve" else "reject"}")
                if (approve) Toast.success(t("aiApprovals.approvedVerified")) else Toast.show(t("partDetail.rejected"))
                reload++
            } catch (e: ApiException) {
                Toast.error(e.message ?: t("common.failed"))
            }
        }
    }
    Screen {
        TopBar(t("aiApprovals.title"), t("aiApprovals.subtitle"), onBack = { nav.back() })
        when {
            loading -> Loading()
            items.isEmpty() -> EmptyState(Icons.Outlined.AutoAwesome, t("aiApprovals.empty"), t("aiApprovals.emptySub"))
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(items, key = { idx, it -> it.str("id") + "#" + idx }) { i, it ->
                    val r = it.obj("result") ?: JSONObject()
                    val conf = it.int("confidence")
                    Card(Modifier.entrance(i)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(it.str("part_number"), color = C.Text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            StatusChip(it.str("verification"))
                        }
                        Text("${t("partDetail.confidence")}: $conf%", color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                        LinearProgressIndicator(
                            progress = { conf / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = if (conf >= 70) C.Green else if (conf >= 40) C.Amber else C.Red, trackColor = C.Surface3,
                        )
                        Text("${r.str("name").ifBlank { "—" }} • ${r.str("category").ifBlank { "—" }}", color = C.Text, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                        Text(r.arr("compatible_vehicles").strings().joinToString(", "), color = C.Muted, fontSize = 12.sp)
                        if (it.optBoolean("conflict")) Badge("⚠ ${t("aiApprovals.conflictNote")}", C.OnAmberFaint, C.AmberFaint)
                        if (user.can("ai_approve")) {
                            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BigButton(t("aiApprovals.approve"), icon = Icons.Filled.Check, modifier = Modifier.weight(1f), onClick = { act(it.str("id"), true) })
                                BigButton(t("common.reject"), icon = Icons.Filled.Close, color = C.Red, modifier = Modifier.weight(1f), onClick = { act(it.str("id"), false) })
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
//  ALL STORES + STORE DETAIL (platform owner)
// ============================================================

@Composable
fun StoresScreen(nav: Nav) {
    var stores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        try {
            stores = Api.getArr("/admin/stores").objects()
        } catch (_: ApiException) {
        } finally {
            loading = false
        }
    }
    Screen {
        TopBar(t("stores.title"), t("stores.subtitle"), onBack = { nav.back() })
        when {
            loading -> Loading()
            stores.isEmpty() -> EmptyState(Icons.Outlined.Storefront, t("stores.empty"), t("stores.emptySub"))
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(stores, key = { idx, s -> s.str("id") + "#" + idx }) { i, s ->
                    RowCard(Modifier.entrance(i).pressable { nav.open(Route.StoreDetail(s.str("id"), s.str("name"))) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).background(C.BrandFaint, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Storefront, contentDescription = null, tint = C.Brand)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.str("name"), color = C.Text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                val o = s.obj("owner")
                                Text("${o?.str("name")?.ifBlank { "-" } ?: "-"} (${o?.str("username")?.ifBlank { "-" } ?: "-"})", color = C.Muted, fontSize = 12.sp)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                            MiniStat("${s.int("users")}", t("stores.users"), Modifier.weight(1f))
                            MiniStat("${s.int("parts")}", t("parts.title"), Modifier.weight(1f))
                            MiniStat("${s.int("in_stock")}", t("storeDetail.inStock"), Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = C.Text, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text(label, color = C.Muted, fontSize = 11.sp)
    }
}

@Composable
fun StoreDetailScreen(nav: Nav, storeId: String, storeName: String) {
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf("inventory") }
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var busyId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tab, reload) {
        loading = true
        stats = try { Api.getObj("/stats", mapOf("store_id" to storeId)) } catch (_: ApiException) { null }
        rows = try {
            when (tab) {
                "staff" -> Api.getArr("/owner/users", mapOf("store_id" to storeId)).objects()
                "inventory" -> Api.getArr("/inventory", mapOf("store_id" to storeId)).objects()
                else -> Api.getArr("/transactions", mapOf("store_id" to storeId, "type" to tab)).objects()
            }
        } catch (_: ApiException) {
            emptyList()
        }
        loading = false
    }

    Screen {
        TopBar(storeName.ifBlank { t("storeDetail.store") }, t("storeDetail.adminView"), onBack = { nav.back() })
        stats?.let { s ->
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    t("parts.title") to s.int("total_parts"), t("storeDetail.inStock") to s.int("in_stock_units"),
                    t("storeDetail.sold") to s.int("sold_units"), t("storeDetail.buys") to s.int("total_buys"), t("storeDetail.sells") to s.int("total_sells"),
                ).forEach { (l, v) ->
                    Column(Modifier.background(C.Card, RoundedCornerShape(10.dp)).border(1.dp, C.Line, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text("$v", color = C.Brand, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text(l, color = C.Muted, fontSize = 11.sp)
                    }
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, top = 8.dp)) {
            listOf("inventory" to t("tabs.inventory"), "buy" to t("storeDetail.purchases"), "sell" to t("storeDetail.sales"), "staff" to t("ownerPanel.tabStaff"))
                .forEach { (k, l) -> Chip(l, tab == k) { tab = k } }
        }
        when {
            loading -> Loading()
            tab == "staff" && rows.isEmpty() -> EmptyState(Icons.Outlined.People, t("ownerPanel.noStaff"))
            rows.isEmpty() -> EmptyState(Icons.Outlined.Description, t("storeDetail.nothingHere"), t("storeDetail.nothingHereSub"))
            tab == "staff" -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(rows, key = { idx, u -> u.str("id") + "#" + idx }) { i, u ->
                    StaffCard(u, i, busyId == u.str("id"), showStore = false) {
                        busyId = u.str("id")
                        scope.launch {
                            try {
                                Api.post("/owner/users/${Api.seg(u.str("id"))}/${if (u.optBoolean("disabled")) "reactivate" else "deactivate"}")
                                reload++
                            } catch (e: ApiException) {
                                Toast.error(e.message ?: t("common.failed"))
                            } finally {
                                busyId = null
                            }
                        }
                    }
                }
            }
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(rows) { i, r ->
                    Row(Modifier.fillMaxWidth().entrance(i.coerceAtMost(12)).background(C.Card, RoundedCornerShape(10.dp)).border(1.dp, C.Line, RoundedCornerShape(10.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.str("part_number"), color = C.Text, fontWeight = FontWeight.ExtraBold)
                            if (r.str("part_name").isNotBlank()) Text(r.str("part_name"), color = C.Text2, fontSize = 13.sp)
                            val date = r.str("at").ifBlank { r.str("created_at") }
                            Text(listOf(r.str("company"), r.str("category"), if (date.isNotBlank()) shortDate(date) else "").filter { it.isNotBlank() }.joinToString("  •  "), color = C.Muted, fontSize = 12.sp)
                        }
                        if (r.str("condition").isNotBlank()) StatusChip(r.str("condition"))
                        r.num("price")?.let { Text("Rs.${Printer.fmtNum(it)}", color = C.Brand, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffCard(u: JSONObject, index: Int, busy: Boolean, showStore: Boolean, onToggle: () -> Unit) {
    val disabled = u.optBoolean("disabled")
    RowCard(Modifier.entrance(index)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(u.str("name"), color = C.Text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Pill(if (disabled) t("ownerPanel.staffDisabled") else t("ownerPanel.staffActive"), if (disabled) C.Red else C.Green, if (disabled) C.RedFaint else C.GreenFaint)
        }
        Text("@${u.str("username")} · ${u.str("role")}", color = C.Muted, fontSize = 12.sp)
        if (showStore) Text("${t("ownerPanel.staffStore")}: ${u.str("store_name").ifBlank { "—" }}", color = C.Muted, fontSize = 12.sp)
        Text("${t("users.addedBy")}: ${u.obj("created_by")?.str("name")?.ifBlank { null } ?: t("users.addedByUnknown")}", color = C.Muted, fontSize = 12.sp)
        if (u.str("role") != "super_admin") {
            Spacer(Modifier.height(8.dp))
            BigButton(
                if (disabled) t("ownerPanel.reactivate") else t("ownerPanel.deactivate"),
                icon = if (disabled) Icons.Filled.CheckCircle else Icons.Filled.Block,
                color = if (disabled) C.Brand else C.Red, outlined = disabled, loading = busy, onClick = onToggle,
            )
        }
    }
}

// ============================================================
//  SEARCH SETUP (Google key)
// ============================================================

@Composable
fun SearchSetupScreen(nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf("") }
    var cx by remember { mutableStateOf("") }
    var hasKey by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            val s = Api.getObj("/auth/settings")
            cx = s.str("google_cx")
            hasKey = s.optBoolean("has_google_key")
        } catch (_: ApiException) {
        }
    }
    Screen {
        TopBar(t("settings.title"), t("settings.subtitle"), onBack = { nav.back() })
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoNote(t("settings.infoText"), Icons.Filled.Key)
            Card(Modifier.entrance(0)) {
                SectionTitle(t("settings.yourCredentials"))
                Field(apiKey, { apiKey = it }, (if (hasKey) t("settings.apiKeySaved") else t("settings.googleApiKey")).uppercase(),
                    placeholder = if (hasKey) t("settings.alreadySet") else "AIza...", password = true)
                Field(cx, { cx = it }, t("settings.searchEngineId").uppercase(), placeholder = "e.g. a1b2c3d4e5f6g7h8i")
                BigButton(t("settings.saveSettings"), icon = Icons.Filled.Save, loading = saving, onClick = {
                    if (apiKey.isBlank() && !hasKey) {
                        Toast.error(t("settings.enterApiKey")); return@BigButton
                    }
                    saving = true
                    scope.launch {
                        try {
                            val body = JSONObject().put("google_cx", cx.trim())
                            if (apiKey.isNotBlank()) body.put("google_api_key", apiKey.trim())
                            val r = Api.post("/auth/settings", body) as? JSONObject ?: JSONObject()
                            hasKey = r.optBoolean("has_google_key")
                            apiKey = ""
                            try { Session.updateUser(Api.getObj("/auth/me")) } catch (_: Exception) {}
                            Toast.success(t("settings.saved"))
                        } catch (e: ApiException) {
                            Toast.error(e.message ?: t("common.failed"))
                        } finally {
                            saving = false
                        }
                    }
                })
                if (hasKey) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = C.Green, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t("settings.keyConfigured"), color = C.Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
            Card(Modifier.entrance(1)) {
                SectionTitle(t("settings.howToGetKey"))
                listOf("settings.step1", "settings.step2", "settings.step3", "settings.step4").forEachIndexed { i, k ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(24.dp).background(C.Brand, CircleShape), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(t(k), color = C.Text2, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                BigButton(t("settings.openConsole"), icon = Icons.AutoMirrored.Filled.OpenInNew, outlined = true, onClick = {
                    Share.openUrl(context, "https://console.cloud.google.com/apis/library/customsearch.googleapis.com")
                })
            }
        }
    }
}

// ============================================================
//  UNLINKED STOCK
// ============================================================

@Composable
fun UnlinkedStockScreen(nav: Nav) {
    var units by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        try {
            units = Api.getArr("/inventory/unlinked-stock").objects()
        } catch (e: ApiException) {
            Toast.error(e.message ?: t("common.loadFailed"))
        } finally {
            loading = false
        }
    }
    Screen {
        TopBar(t("unlinkedStock.title"), t("unlinkedStock.subtitle"), onBack = { nav.back() })
        when {
            loading -> Loading(t("unlinkedStock.checking"))
            units.isEmpty() -> EmptyState(Icons.Outlined.DoneAll, t("unlinkedStock.allClear"), t("unlinkedStock.allClearSub"))
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().background(C.AmberFaint, RoundedCornerShape(10.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = C.Amber)
                        Spacer(Modifier.width(8.dp))
                        Text("${units.size} ${t("unlinkedStock.foundNoTrace")}", color = C.OnAmberFaint, fontWeight = FontWeight.Bold)
                    }
                }
                itemsIndexed(units, key = { idx, u -> u.str("id") + "#" + idx }) { i, u ->
                    RowCard(Modifier.entrance(i)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(u.str("part_number"), color = C.Text, fontWeight = FontWeight.ExtraBold)
                                Text(
                                    "${u.str("condition")} • ${t("unlinkedStock.added")} ${shortDate(u.str("created_at"))}" +
                                        (if (u.str("added_by").isNotBlank()) " ${t("unlinkedStock.by")} ${u.str("added_by")}" else ""),
                                    color = C.Muted, fontSize = 12.sp,
                                )
                                val al = formatAssignedLocation(u.obj("assigned_location"))
                                if (al.isNotBlank()) Text("📍 $al", color = C.Muted, fontSize = 12.sp)
                            }
                            Row(
                                Modifier.background(C.Brand, RoundedCornerShape(8.dp)).pressable { nav.open(Route.Buy(u.str("part_number"))) }.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Link, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(t("unlinkedStock.createBuyEntry"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
//  SEARCH LOGS (store admin) — also reused by Owner Panel
// ============================================================

@Composable
private fun SearchLogList(endpoint: String, showStore: Boolean) {
    var logs by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(endpoint) {
        try {
            val res = Api.getObj(endpoint, mapOf("page" to "1", "page_size" to "50"))
            logs = res.arr("items").objects()
            total = res.int("total")
            page = 1
        } catch (e: ApiException) {
            Toast.error(e.message ?: t("common.loadFailed"))
        } finally {
            loading = false
        }
    }
    when {
        loading -> Loading()
        logs.isEmpty() -> EmptyState(Icons.Outlined.History, t("searchLogs.empty"))
        else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(logs, key = { idx, l -> l.str("id") + "#" + idx }) { i, l ->
                RowCard(Modifier.entrance(i.coerceAtMost(12))) {
                    Row {
                        Text(l.str("user_name"), color = C.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(dateTime(l.str("created_at")), color = C.Muted, fontSize = 11.sp)
                    }
                    if (showStore) Text("${t("ownerPanel.staffStore")}: ${l.str("store_name").ifBlank { "—" }}", color = C.Muted, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = C.Brand, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(l.str("part_number_searched"), color = C.Brand, fontWeight = FontWeight.ExtraBold)
                    }
                    val g = l.obj("gps_coord")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = C.Muted, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (g != null) String.format(Locale.US, "%.5f, %.5f", g.optDouble("lat"), g.optDouble("lng")) else t("searchLogs.noLocation"),
                            color = C.Muted, fontSize = 12.sp,
                        )
                    }
                }
            }
            if (logs.size < total) {
                item {
                    BigButton(if (loadingMore) t("common.loading") else t("searchLogs.loadMore"), icon = Icons.Filled.ExpandMore, outlined = true, loading = loadingMore, onClick = {
                        loadingMore = true
                        scope.launch {
                            try {
                                val next = page + 1
                                val res = Api.getObj(endpoint, mapOf("page" to "$next", "page_size" to "50"))
                                logs = (logs + res.arr("items").objects()).distinctBy { it.str("id") }
                                total = res.int("total")
                                page = next
                            } catch (e: ApiException) {
                                Toast.error(e.message ?: t("common.failed"))
                            } finally {
                                loadingMore = false
                            }
                        }
                    })
                }
            }
        }
    }
}

@Composable
fun SearchLogsScreen(nav: Nav) {
    Screen {
        TopBar(t("searchLogs.title"), t("searchLogs.subtitle"), onBack = { nav.back() })
        SearchLogList("/admin/search-logs", showStore = false)
    }
}

// ============================================================
//  STORE PROFILE / BRANDING
// ============================================================

@Composable
fun StoreProfileScreen(nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var gst by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf("") }
    var logoPath by remember { mutableStateOf("") }

    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                logoPath = Api.uploadImage(bytes, "logo.jpg")
                Toast.success(t("storeProfile.logoUploaded"))
            } catch (e: Exception) {
                Toast.error(t("storeProfile.logoUploadFailed"))
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            val s = Api.getObj("/store/profile")
            name = s.str("name"); gst = s.str("gst"); phone = s.str("phone"); address = s.str("address"); bank = s.str("bank")
            logoPath = s.str("logo_path")
        } catch (e: ApiException) {
            Toast.error(e.message ?: t("common.loadFailed"))
        } finally {
            loading = false
        }
    }

    Screen {
        TopBar(if (loading) t("storeProfile.title") else t("storeProfile.fullTitle"), onBack = { nav.back() })
        if (loading) {
            Loading(); return@Screen
        }
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.entrance(0)) {
                SectionTitle(t("storeProfile.storeLogo"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (logoPath.isNotBlank()) {
                        RemoteImage(logoPath, Modifier.size(72.dp).background(C.Surface3, RoundedCornerShape(10.dp)))
                    } else {
                        Box(Modifier.size(72.dp).background(C.Surface3, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Image, contentDescription = null, tint = C.Muted, modifier = Modifier.size(28.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    BigButton(t("storeProfile.chooseLogo"), icon = Icons.Filled.CloudUpload, outlined = true, modifier = Modifier.weight(1f), onClick = {
                        pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                }
                Text(t("storeProfile.logoHint"), color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Card(Modifier.entrance(1)) {
                Field(name, { name = it }, t("storeProfile.storeName"))
                Field(gst, { gst = it }, t("storeProfile.gstNumber"), placeholder = "e.g. 24ABCDE1234F1Z5", caps = true)
                Field(phone, { phone = it }, t("common.phone"), placeholder = "e.g. +91 98xxxxxxxx", phone = true)
                Field(address, { address = it }, t("common.address"), placeholder = t("storeProfile.shopAddress"))
                Field(bank, { bank = it }, t("storeProfile.bankDetails"), placeholder = t("storeProfile.bankPlaceholder"))
            }
            BigButton(t("storeProfile.saveProfile"), icon = Icons.Filled.Save, loading = saving, onClick = {
                saving = true
                scope.launch {
                    try {
                        Api.post(
                            "/store/profile",
                            JSONObject().put("name", name.trim()).put("gst", gst.trim()).put("phone", phone.trim())
                                .put("address", address.trim()).put("bank", bank.trim()).put("logo_path", logoPath),
                        )
                        try { Session.updateUser(Api.getObj("/auth/me")) } catch (_: Exception) {}
                        Toast.success(t("storeProfile.saved"))
                        nav.back()
                    } catch (e: ApiException) {
                        Toast.error(e.message ?: t("common.saveFailed"))
                    } finally {
                        saving = false
                    }
                }
            })
        }
    }
}

// ============================================================
//  PURCHASE / SALE HISTORY (bulk delete)
// ============================================================

@Composable
fun HistoryScreen(user: User, nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf("buy") }
    var txns by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var deleting by remember { mutableStateOf(false) }
    var confirmOpen by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(tab, reload) {
        loading = true
        selected.clear()
        try {
            txns = Api.getArr("/transactions", mapOf("type" to tab)).objects()
        } catch (e: ApiException) {
            Toast.error(e.message ?: t("common.loadFailed"))
        } finally {
            loading = false
        }
    }
    val ids = selected.filterValues { it }.keys.toList()
    val allSelected = txns.isNotEmpty() && ids.size == txns.size

    Screen {
        TopBar(t("history.title"), t("history.subtitle"), onBack = { nav.back() }, actions = {
            if (txns.isNotEmpty()) {
                Icon(Icons.Filled.Print, contentDescription = null, tint = C.Brand, modifier = Modifier.pressable {
                    val title = if (tab == "buy") t("history.purchaseHistory") else t("history.saleHistory")
                    Printer.print(context, Printer.reportHtml(Branding.from(user), title, txns, true), title)
                })
                Spacer(Modifier.width(14.dp))
                Text(if (allSelected) t("history.clear") else t("common.all"), color = C.Brand, fontWeight = FontWeight.Bold, modifier = Modifier.pressable {
                    if (allSelected) selected.clear() else txns.forEach { selected[it.str("id")] = true }
                })
            }
        })
        Row(Modifier.padding(start = 16.dp, top = 12.dp)) {
            Chip(t("buy.title"), tab == "buy") { tab = "buy" }
            Chip(t("sell.title"), tab == "sell") { tab = "sell" }
        }
        when {
            loading -> Loading()
            txns.isEmpty() -> EmptyState(Icons.Outlined.Receipt, t("history.noEntries"), if (tab == "buy") t("history.noPurchases") else t("history.noSales"))
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(txns, key = { idx, x -> x.str("id") + "#" + idx }) { i, x ->
                    val on = selected[x.str("id")] == true
                    Row(
                        Modifier.fillMaxWidth().entrance(i.coerceAtMost(12))
                            .background(if (on) C.BrandFaint else C.Card, RoundedCornerShape(12.dp))
                            .border(1.dp, if (on) C.Brand else C.Line, RoundedCornerShape(12.dp))
                            .pressable { selected[x.str("id")] = !on }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (on) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, contentDescription = null, tint = if (on) C.Brand else C.Muted)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(x.str("part_number"), color = C.Text, fontWeight = FontWeight.ExtraBold)
                            if (x.str("part_name").isNotBlank()) Text(x.str("part_name"), color = C.Text2, fontSize = 13.sp)
                            Text(
                                listOf(
                                    if (x.str("by").isNotBlank()) "${t("unlinkedStock.by")} ${x.str("by")}" else "",
                                    if (x.str("at").isNotBlank()) shortDate(x.str("at")) else "",
                                    x.str("buyer"),
                                ).filter { it.isNotBlank() }.joinToString("  •  "),
                                color = C.Muted, fontSize = 12.sp,
                            )
                        }
                        x.num("price")?.takeIf { it != 0.0 }?.let { Text("₹${Printer.fmtNum(it)}", color = C.Brand, fontWeight = FontWeight.ExtraBold) }
                    }
                }
            }
        }
        if (ids.isNotEmpty()) {
            Column(Modifier.background(C.Card).padding(12.dp)) {
                BigButton(
                    if (deleting) t("history.deletingEllipsis") else "${t("common.delete")} ${ids.size} ${t("history.entries")}",
                    icon = Icons.Filled.Delete, color = C.Red, loading = deleting, onClick = { confirmOpen = true },
                )
            }
        }
    }
    if (confirmOpen) {
        ConfirmDialog(
            title = "${t("common.delete")} ${ids.size} ${t("history.entries")}?",
            message = if (tab == "buy") t("history.deleteConfirmMsgBuy") else t("history.deleteConfirmMsgSell"),
            confirmText = t("common.delete"), danger = true, loading = deleting,
            onConfirm = {
                deleting = true
                scope.launch {
                    try {
                        val res = Api.post("/transactions/delete", JSONObject().put("ids", JSONArray(ids)).put("remove_stock", true)) as? JSONObject ?: JSONObject()
                        Feedback.error(context)
                        Toast.success("${res.int("deleted")} ${t("history.entriesPlusStock")} ${res.int("removed_units")} ${t("history.stockDeleted")}")
                        confirmOpen = false
                        reload++
                    } catch (e: ApiException) {
                        Toast.error(e.message ?: t("common.failed"))
                    } finally {
                        deleting = false
                    }
                }
            },
            onCancel = { confirmOpen = false },
        )
    }
}

// ============================================================
//  BACKUP & RESTORE
// ============================================================

@Composable
fun BackupScreen(nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<JSONObject?>(null) }
    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = "read"
        scope.launch {
            // Big backups are read in the background so the screen doesn't freeze.
            val parsed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                    val o = JSONObject(text)
                    o.optJSONObject("collections") ?: o
                } catch (e: Throwable) {
                    null
                }
            }
            busy = null
            if (parsed == null) Toast.error(t("backup.errorReadingFile")) else pending = parsed
        }
    }

    Screen {
        TopBar(t("backup.title"), t("backup.subtitle"), onBack = { nav.back() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoNote("${t("backup.infoText1")} ${t("backup.secureCloud")}. ${t("backup.infoText2")}", Icons.Filled.Shield, C.Green, C.GreenFaint, C.OnGreenFaint)
            Card(Modifier.entrance(0)) {
                SectionTitle(t("backup.exportBackup"))
                Text(t("backup.exportSub"), color = C.Muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                BigButton(t("backup.excelBackup"), icon = Icons.Filled.GridOn, loading = busy == "excel", onClick = {
                    busy = "excel"
                    scope.launch {
                        try {
                            Share.file(context, Api.download("/backup/excel"), "kabadi_backup_$stamp.xlsx", Share.XLSX)
                            Toast.success(t("backup.excelReady"))
                        } catch (e: Exception) {
                            Toast.error(e.message ?: t("backup.excelExportFailed"))
                        } finally {
                            busy = null
                        }
                    }
                })
                Spacer(Modifier.height(8.dp))
                BigButton(t("backup.fullBackup"), icon = Icons.Filled.Download, outlined = true, loading = busy == "json", onClick = {
                    busy = "json"
                    scope.launch {
                        try {
                            val data = Api.get("/backup/export")
                            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                (if (data is JSONObject) data.toString(2) else data.toString()).toByteArray()
                            }
                            Share.file(context, bytes, "kabadi_backup_$stamp.json", "application/json")
                            Toast.success(t("backup.jsonReady"))
                        } catch (e: Throwable) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            Toast.error(e.message ?: t("common.exportFailed"))
                        } finally {
                            busy = null
                        }
                    }
                })
            }
            Card(Modifier.entrance(1)) {
                SectionTitle(t("backup.importRestore"))
                Text(t("backup.importSub"), color = C.Muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                BigButton(t("backup.pickAndRestore"), icon = Icons.Filled.CloudUpload, outlined = true, loading = busy == "import" || busy == "read", onClick = {
                    // Any file type: backups shared via WhatsApp / Drive often lose the ".json" type.
                    pickFile.launch("*/*")
                })
            }
        }
    }
    pending?.let { cols ->
        ConfirmDialog(
            title = t("backup.restoreTitle"), message = t("backup.restoreMsg"), confirmText = t("backup.restore"),
            loading = busy == "import",
            onConfirm = {
                busy = "import"
                scope.launch {
                    try {
                        val res = Api.post("/backup/import", JSONObject().put("collections", cols)) as? JSONObject ?: JSONObject()
                        val imp = res.optJSONObject("imported") ?: JSONObject()
                        var total = 0
                        imp.keys().forEach { k -> total += imp.optInt(k) }
                        Toast.success("$total ${t("backup.recordsRestored")}")
                        pending = null
                    } catch (e: ApiException) {
                        Toast.error(e.message ?: t("backup.importFailed"))
                    } finally {
                        busy = null
                    }
                }
            },
            onCancel = { pending = null },
        )
    }
}

// ============================================================
//  DEMAND (search demand)
// ============================================================

@Composable
fun DemandScreen(nav: Nav) {
    var demand by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var history by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var range by rememberSaveable { mutableStateOf("all") }
    var company by rememberSaveable { mutableStateOf("All") }
    var category by rememberSaveable { mutableStateOf("All") }
    var fromDate by rememberSaveable { mutableStateOf(demandToday()) }
    var toDate by rememberSaveable { mutableStateOf(demandToday()) }
    var apply by remember { mutableIntStateOf(0) }
    var companies by remember { mutableStateOf(listOf("All")) }
    var categories by remember { mutableStateOf(listOf("All")) }

    LaunchedEffect(Unit) {
        try {
            val co = Api.getArr("/companies").strings()
            companies = if ("All" in co) co else listOf("All") + co
        } catch (_: ApiException) {
        }
        try {
            categories = listOf("All") + Api.getObj("/categories").arr("groups").objects().flatMap { it.arr("items").strings() }
        } catch (_: ApiException) {
        }
    }
    LaunchedEffect(range, company, category, apply) {
        loading = true
        try {
            val cal = java.util.Calendar.getInstance()
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val today = fmt.format(cal.time)
            val (f, tt) = when (range) {
                "today" -> today to today
                "month" -> fmt.format(java.util.Calendar.getInstance().apply { set(java.util.Calendar.DAY_OF_MONTH, 1) }.time) to today
                "year" -> fmt.format(java.util.Calendar.getInstance().apply { set(java.util.Calendar.DAY_OF_YEAR, 1) }.time) to today
                "custom" -> fromDate to toDate
                else -> null to null
            }
            demand = Api.getArr("/demand").objects()
            history = Api.getArr(
                "/search-history",
                mapOf("date_from" to f, "date_to" to tt, "company" to company.takeIf { it != "All" }, "category" to category.takeIf { it != "All" }),
            ).objects()
        } catch (_: ApiException) {
        } finally {
            loading = false
        }
    }

    Screen {
        TopBar(t("demand.title"), t("demand.subtitle"), onBack = { nav.back() })
        Column(Modifier.background(C.Card).padding(bottom = 8.dp)) {
            Text(t("report.date"), color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp)) {
                listOf("all" to "common.all", "month" to "common.thisMonth", "year" to "report.thisYear", "today" to "common.today", "custom" to "common.custom")
                    .forEach { (k, l) -> Chip(t(l), range == k) { range = k } }
            }
            if (range == "custom") {
                val context = LocalContext.current
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    DemandDate(fromDate) { fromDate = it }
                    Text(t("common.to"), color = C.Muted, modifier = Modifier.padding(horizontal = 8.dp))
                    DemandDate(toDate) { toDate = it }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.background(C.Brand, RoundedCornerShape(8.dp)).pressable { apply++ }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(t("common.apply"), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    @Suppress("UNUSED_VARIABLE") val c = context
                }
            }
            Text(t("report.companyLabel"), color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp)) {
                companies.forEach { c -> Chip(if (c == "All") t("common.all") else c, company == c) { company = c } }
            }
            Text(t("report.categoryLabel"), color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp)) {
                categories.forEach { c -> Chip(if (c == "All") t("common.all") else c, category == c) { category = c } }
            }
        }
        when {
            loading -> Loading()
            history.isEmpty() -> EmptyState(Icons.Outlined.TrendingUp, t("demand.noSearchData"), t("report.tryAnother"))
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (demand.isNotEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().background(C.RedFaint, RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Whatshot, contentDescription = null, tint = C.Red, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(t("demand.highDemandHeader"), color = C.OnRedFaint, fontWeight = FontWeight.ExtraBold)
                            }
                            demand.forEach { d ->
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                    Text(d.str("part_number"), color = C.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text("${d.int("count")}${t("demand.searchedCountSuffix")}", color = C.Red, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                itemsIndexed(history, key = { i, h -> "${h.str("store_id")}|${h.str("part_number")}|$i" }) { i, h ->
                    Row(Modifier.fillMaxWidth().entrance(i.coerceAtMost(12)).background(C.Card, RoundedCornerShape(12.dp)).border(1.dp, C.Line, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(h.str("part_number"), color = C.Text, fontWeight = FontWeight.ExtraBold)
                            if (h.str("part_name").isNotBlank()) Text(h.str("part_name"), color = C.Text2, fontSize = 13.sp)
                            Text(
                                "${t("demand.searchedPrefix")} ${h.int("count")}× • ${t("demand.lastColon")} ${h.str("last_status")}" +
                                    (if (h.str("company").isNotBlank() && h.str("company") != "All") "  •  ${h.str("company")}" else ""),
                                color = C.Muted, fontSize = 12.sp,
                            )
                        }
                        StatusChip(h.str("last_status"))
                    }
                }
            }
        }
    }
}

private fun demandToday(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

@Composable
private fun DemandDate(value: String, onPick: (String) -> Unit) {
    val context = LocalContext.current
    Box(
        Modifier.border(1.dp, C.Brand, RoundedCornerShape(8.dp)).pressable {
            val c = java.util.Calendar.getInstance()
            runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value) }.getOrNull()?.let { c.time = it }
            android.app.DatePickerDialog(context, { _, y, m, d -> onPick(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)) },
                c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }.padding(horizontal = 10.dp, vertical = 8.dp),
    ) { Text("📅 $value", color = C.Brand, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

// ============================================================
//  GPS LOCATIONS (platform owner)
// ============================================================

@Composable
fun AdminGpsScreen(nav: Nav) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf("activity") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var points by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var devices by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(mode) {
        loading = true
        try {
            if (mode == "live") devices = Api.getArr("/owner/device-locations").objects()
            else points = Api.getArr("/admin/gps-locations").objects()
        } catch (e: ApiException) {
            Toast.error(e.message ?: t("common.loadFailed"))
        } finally {
            loading = false
        }
    }
    fun openMap(lat: Double, lng: Double, label: String) =
        Share.openUrl(context, "https://maps.google.com/?q=$lat,$lng(${Uri.encode(label)})")
    fun ago(iso: String): String {
        val p = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val d = runCatching { p.parse(iso.take(19)) }.getOrNull() ?: return iso
        val mins = ((System.currentTimeMillis() - d.time) / 60000).coerceAtLeast(0)
        return when {
            mins < 1 -> t("adminGps.justNow")
            mins < 60 -> "$mins ${t("adminGps.minsAgo")}"
            mins < 60 * 24 -> "${Math.round(mins / 60.0)} ${t("adminGps.hoursAgo")}"
            else -> shortDate(iso)
        }
    }

    Screen {
        TopBar(t("adminGps.title"), t("adminGps.subtitle"), onBack = { nav.back() })
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("activity" to t("adminGps.modeActivity"), "live" to t("adminGps.modeLive")).forEach { (k, l) ->
                Box(
                    Modifier.weight(1f).background(if (mode == k) C.Brand else C.Card, RoundedCornerShape(10.dp)).border(1.dp, if (mode == k) C.Brand else C.Line, RoundedCornerShape(10.dp))
                        .pressable { mode = k }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(l, color = if (mode == k) Color.White else C.Text2, fontWeight = FontWeight.Bold) }
            }
        }
        if (mode == "activity") {
            Row(Modifier.padding(start = 16.dp)) {
                listOf("All" to t("common.all"), "Requirement" to t("module.requirement"), "Purchase" to t("adminGps.purchase")).forEach { (k, l) ->
                    Chip(l, filter == k) { filter = k }
                }
            }
        } else {
            Text("ℹ ${t("adminGps.liveHint")}", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        }
        when {
            loading -> Loading(t("adminGps.loadingPoints"))
            mode == "live" -> if (devices.isEmpty()) EmptyState(Icons.Outlined.PhoneAndroid, t("adminGps.liveEmpty")) else
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(devices, key = { idx, d -> d.str("user_id") + "#" + idx }) { i, d ->
                        GpsRow(Icons.Filled.PhoneAndroid, C.Brand, d.str("name"), "@${d.str("username")} · ${d.str("store_name")}",
                            String.format(Locale.US, "%.5f, %.5f", d.optDouble("lat"), d.optDouble("lng")), ago(d.str("at")), i) {
                            openMap(d.optDouble("lat"), d.optDouble("lng"), "${d.str("name")} (${d.str("store_name")})")
                        }
                    }
                }
            else -> {
                val data = if (filter == "All") points else points.filter { it.str("type") == filter }
                if (data.isEmpty()) EmptyState(Icons.Outlined.LocationOn, t("adminGps.empty")) else
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(data) { i, p ->
                            val isReq = p.str("type") == "Requirement"
                            GpsRow(
                                if (isReq) Icons.Filled.Warning else Icons.Filled.Download, if (isReq) C.Amber else C.Green,
                                p.str("part_number").ifBlank { "—" },
                                "${if (isReq) t("module.requirement") else t("adminGps.purchase")} · ${p.str("store_name")} · ${p.str("by").ifBlank { "—" }}",
                                p.str("gps"), dateTime(p.str("at")), i,
                            ) { openMap(p.optDouble("lat"), p.optDouble("lng"), "${p.str("part_number")} (${p.str("store_name")})") }
                        }
                    }
            }
        }
    }
}

@Composable
private fun GpsRow(icon: ImageVector, color: Color, title: String, meta: String, gps: String, time: String, index: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().entrance(index.coerceAtMost(12)).background(C.Card, RoundedCornerShape(12.dp)).border(1.dp, C.Line, RoundedCornerShape(12.dp)).pressable(onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(meta, color = C.Muted, fontSize = 12.sp)
            Text("📍 $gps", color = C.Green, fontSize = 12.sp)
            Text(time, color = C.Muted, fontSize = 11.sp)
        }
        Icon(Icons.Filled.Map, contentDescription = null, tint = C.Brand)
    }
}

// ============================================================
//  MANAGE USERS (store admin)
// ============================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UsersScreen(nav: Nav) {
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var allPerms by remember { mutableStateOf<List<String>>(emptyList()) }
    var defaultPerms by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    val revealed = remember { mutableStateMapOf<String, String>() }
    var createOpen by remember { mutableStateOf(false) }
    var editUser by remember { mutableStateOf<JSONObject?>(null) }
    var removeUser by remember { mutableStateOf<JSONObject?>(null) }
    var removing by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        try {
            users = Api.getArr("/admin/users").objects()
            val p = Api.getObj("/permissions")
            allPerms = p.arr("all").strings()
            defaultPerms = p.arr("staff_default").strings()
        } catch (e: ApiException) {
            Toast.error(e.message ?: t("common.failed"))
        } finally {
            loading = false
        }
    }
    fun patch(id: String, body: JSONObject) {
        scope.launch {
            try {
                Api.patch("/admin/users/${Api.seg(id)}", body)
                reload++
            } catch (e: ApiException) {
                Toast.error(e.message ?: t("common.failed"))
            }
        }
    }

    Screen {
        TopBar(t("users.title"), t("users.subtitle"), onBack = { nav.back() }, actions = {
            Box(Modifier.size(36.dp).background(C.Brand, CircleShape).pressable { createOpen = true }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        })
        if (loading) {
            Loading(); return@Screen
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            users.forEachIndexed { i, u ->
                val id = u.str("id")
                val perms = u.arr("permissions").strings()
                Card(Modifier.entrance(i)) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(u.str("name"), color = C.Text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            Text("@${u.str("username")}", color = C.Muted, fontSize = 13.sp)
                            Text("${t("users.addedBy")}: ${u.obj("created_by")?.str("name")?.ifBlank { null } ?: t("users.addedByUnknown")}", color = C.Muted, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val v = u.optBoolean("verified", true)
                            Pill(if (v) t("users.verified") else t("users.pending"), if (v) C.Green else C.Amber, if (v) C.GreenFaint else C.AmberFaint)
                            val d = u.optBoolean("disabled")
                            Pill(if (d) t("users.disabled") else t("users.active"), if (d) C.Red else C.Green, if (d) C.RedFaint else C.GreenFaint)
                        }
                    }
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).background(C.Bg, RoundedCornerShape(8.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Key, contentDescription = null, tint = C.Muted, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            SelectionContainer { Text(revealed[id] ?: "••••••••", color = C.Text, fontWeight = FontWeight.Bold) }
                        }
                        GhostButton(if (revealed[id] != null) t("users.hide") else t("users.view"), if (revealed[id] != null) Icons.Filled.VisibilityOff else Icons.Filled.Visibility) {
                            if (revealed[id] != null) revealed.remove(id) else scope.launch {
                                try {
                                    revealed[id] = Api.getObj("/admin/users/${Api.seg(id)}/password").str("password")
                                } catch (e: ApiException) {
                                    Toast.error(e.message ?: t("users.passwordNotFound"))
                                }
                            }
                        }
                        GhostButton(t("users.change"), Icons.Filled.Edit) { editUser = u }
                    }
                    if (u.str("role") != "admin") {
                        if (!u.optBoolean("verified", true)) {
                            Spacer(Modifier.height(8.dp))
                            BigButton(t("users.verifyButton"), icon = Icons.Filled.CheckCircle, loading = verifying == id, onClick = {
                                verifying = id
                                scope.launch {
                                    try {
                                        Api.post("/admin/users/${Api.seg(id)}/verify")
                                        Toast.success(t("users.staffVerifiedToast"))
                                        reload++
                                    } catch (e: ApiException) {
                                        Toast.error(e.message ?: t("common.failed"))
                                    } finally {
                                        verifying = null
                                    }
                                }
                            })
                        }
                        Text(t("users.permissionsTapToggle"), color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            allPerms.forEach { p ->
                                PermChip(p, p in perms) {
                                    val next = if (p in perms) perms - p else perms + p
                                    patch(id, JSONObject().put("permissions", JSONArray(next)))
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t("users.disabled"), color = C.Text2, modifier = Modifier.weight(1f))
                            Switch(
                                checked = u.optBoolean("disabled"),
                                onCheckedChange = { patch(id, JSONObject().put("disabled", !u.optBoolean("disabled"))) },
                                colors = SwitchDefaults.colors(checkedTrackColor = C.Red),
                            )
                        }
                        Row(Modifier.pressable { removeUser = u }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = C.Red, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(t("users.removeUser"), color = C.Red, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(t("users.mainAdminNote"), color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }

    if (createOpen) {
        CreateStaffDialog(allPerms, defaultPerms, onClose = { createOpen = false }, onCreated = { createOpen = false; reload++ })
    }
    editUser?.let { u ->
        EditUserDialog(u, onClose = { editUser = null }, onSaved = { revealed.remove(u.str("id")); editUser = null; reload++ })
    }
    removeUser?.let { u ->
        ConfirmDialog(
            title = t("users.removeThisUser"),
            message = "${u.str("name")} (@${u.str("username")}) ${t("users.removeConfirmSuffix")}",
            confirmText = t("users.remove"), danger = true, loading = removing,
            onConfirm = {
                removing = true
                scope.launch {
                    try {
                        Api.delete("/admin/users/${Api.seg(u.str("id"))}")
                        Toast.success("${u.str("name")} ${t("users.removedSuffix")}")
                        removeUser = null
                        reload++
                    } catch (e: ApiException) {
                        Toast.error(e.message ?: t("common.failed"))
                    } finally {
                        removing = false
                    }
                }
            },
            onCancel = { removeUser = null },
        )
    }
}

@Composable
private fun PermChip(p: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.background(if (on) C.BrandFaint else C.Card, RoundedCornerShape(8.dp)).border(1.dp, if (on) C.Brand else C.Line, RoundedCornerShape(8.dp))
            .pressable(onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    ) { Text(p, color = if (on) C.Brand else C.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateStaffDialog(allPerms: List<String>, defaults: List<String>, onClose: () -> Unit, onCreated: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var perms by remember { mutableStateOf(defaults.toSet()) }
    var creating by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(20.dp), color = C.Card) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()).imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("users.newStaff"), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.pressable(onClose))
                }
                Spacer(Modifier.height(8.dp))
                Field(name, { name = it }, t("common.name"))
                Field(username, { username = it }, t("users.username"))
                Field(password, { password = it }, t("users.password"), password = true)
                Text(t("users.permissions"), color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    allPerms.forEach { p -> PermChip(p, p in perms) { perms = if (p in perms) perms - p else perms + p } }
                }
                Spacer(Modifier.height(14.dp))
                BigButton(t("users.createStaff"), icon = Icons.Filled.Check, loading = creating, onClick = {
                    if (name.isBlank() || username.isBlank() || password.isEmpty()) {
                        Toast.error(t("users.fillAllFields")); return@BigButton
                    }
                    creating = true
                    scope.launch {
                        try {
                            Api.post(
                                "/admin/users",
                                JSONObject().put("name", name).put("username", username).put("password", password)
                                    .put("role", "staff").put("permissions", JSONArray(perms.toList())),
                            )
                            Toast.success(t("users.staffCreated"))
                            onCreated()
                        } catch (e: ApiException) {
                            Toast.error(e.message ?: t("common.failed"))
                        } finally {
                            creating = false
                        }
                    }
                })
            }
        }
    }
}

@Composable
private fun EditUserDialog(u: JSONObject, onClose: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(u.str("name")) }
    var username by remember { mutableStateOf(u.str("username")) }
    var password by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(20.dp), color = C.Card) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()).imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("users.changeUsernamePassword"), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.pressable(onClose))
                }
                Text("${u.str("name")} (@${u.str("username")})", color = C.Brand, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                Field(name, { name = it }, t("common.name"))
                Field(username, { username = it }, t("users.usernameLoginName"))
                Field(password, { password = it }, t("users.newPasswordLabel"), placeholder = t("users.newPasswordPlaceholder"))
                BigButton(t("common.saveChanges"), icon = Icons.Filled.Save, loading = saving, onClick = {
                    val body = JSONObject()
                    if (name.isNotBlank() && name.trim() != u.str("name")) body.put("name", name.trim())
                    if (username.isNotBlank() && username.trim() != u.str("username")) body.put("username", username.trim())
                    if (password.isNotEmpty()) {
                        if (password.length < 6) {
                            Toast.error(t("users.passwordMinLength")); return@BigButton
                        }
                        body.put("password", password)
                    }
                    if (body.length() == 0) {
                        onClose(); return@BigButton
                    }
                    saving = true
                    scope.launch {
                        try {
                            Api.patch("/admin/users/${Api.seg(u.str("id"))}", body)
                            Toast.success(t("users.updated"))
                            onSaved()
                        } catch (e: ApiException) {
                            Toast.error(e.message ?: t("common.failed"))
                        } finally {
                            saving = false
                        }
                    }
                })
            }
        }
    }
}

// ============================================================
//  OWNER PANEL (platform owner only)
// ============================================================

@Composable
fun OwnerPanelScreen(user: User, nav: Nav) {
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf("stores") }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var stores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var requests by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var staff by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<JSONObject?>(null) }
    var deleteConfirm by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    val otps = remember { mutableStateMapOf<String, Pair<String, String>>() }

    LaunchedEffect(Unit) { if (!user.isOwner) nav.back() }
    LaunchedEffect(tab, reload) {
        if (!user.isOwner || tab == "logs") {
            loading = false; return@LaunchedEffect
        }
        loading = true
        try {
            when (tab) {
                "stores" -> stores = Api.getArr("/owner/stores").objects()
                "requests" -> requests = Api.getArr("/owner/store-requests").objects()
                "staff" -> staff = Api.getArr("/owner/users").objects()
            }
        } catch (e: ApiException) {
            Toast.error(e.message ?: t("common.loadFailed"))
        } finally {
            loading = false
        }
    }
    fun run(id: String, block: suspend () -> Unit) {
        busyId = id
        scope.launch {
            try {
                block()
                reload++
            } catch (e: ApiException) {
                Toast.error(e.message ?: t("common.failed"))
            } finally {
                busyId = null
            }
        }
    }

    Screen {
        TopBar(t("ownerPanel.title"), t("ownerPanel.subtitle"), onBack = { nav.back() })
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("stores" to t("ownerPanel.tabStores"), "requests" to t("ownerPanel.tabRequests"), "staff" to t("ownerPanel.tabStaff"), "logs" to t("ownerPanel.tabLogs")).forEach { (k, l) ->
                Box(
                    Modifier.weight(1f).background(if (tab == k) C.Brand else C.Card, RoundedCornerShape(10.dp)).border(1.dp, if (tab == k) C.Brand else C.Line, RoundedCornerShape(10.dp))
                        .pressable { tab = k }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(l, color = if (tab == k) Color.White else C.Text2, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1) }
            }
        }
        when {
            tab == "logs" -> SearchLogList("/owner/search-logs", showStore = true)
            loading -> Loading()
            tab == "stores" -> if (stores.isEmpty()) EmptyState(Icons.Outlined.Business, t("ownerPanel.noStores")) else
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(stores, key = { idx, s -> s.str("id") + "#" + idx }) { i, s ->
                        val locked = s.str("status") == "locked"
                        RowCard(Modifier.entrance(i).pressable { nav.open(Route.StoreDetail(s.str("id"), s.str("name"))) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.str("name"), color = C.Text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                Pill(if (locked) t("ownerPanel.locked") else t("ownerPanel.active"), if (locked) C.Red else C.Green, if (locked) C.RedFaint else C.GreenFaint)
                            }
                            if (s.str("admin_contact").isNotBlank()) Text("${t("ownerPanel.adminContact")}: ${s.str("admin_contact")}", color = C.Muted, fontSize = 12.sp)
                            Text("👥 ${s.int("user_count")} ${t("ownerPanel.users")}    📦 ${s.int("part_count")} ${t("ownerPanel.parts")}", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BigButton(
                                    if (locked) t("ownerPanel.unlock") else t("ownerPanel.lock"), icon = if (locked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                                    outlined = true, loading = busyId == s.str("id"), modifier = Modifier.weight(1f),
                                    onClick = {
                                        run(s.str("id")) {
                                            Api.post("/owner/stores/${Api.seg(s.str("id"))}/${if (locked) "unlock" else "lock"}")
                                            Toast.success(if (locked) t("ownerPanel.unlockedToast") else t("ownerPanel.lockedToast"))
                                        }
                                    },
                                )
                                BigButton(t("ownerPanel.delete"), icon = Icons.Filled.Delete, color = C.Red, modifier = Modifier.weight(1f), onClick = { deleteTarget = s; deleteConfirm = "" })
                            }
                        }
                    }
                }
            tab == "requests" -> if (requests.isEmpty()) EmptyState(Icons.Outlined.MailOutline, t("ownerPanel.noRequests")) else
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(requests, key = { idx, r -> r.str("id") + "#" + idx }) { i, r ->
                        val st = r.str("status")
                        val (fg, bg, label) = when (st) {
                            "otp_generated" -> Triple(C.Brand, C.BrandFaint, t("ownerPanel.reqOtpGenerated"))
                            "verified" -> Triple(C.Green, C.GreenFaint, t("ownerPanel.reqVerified"))
                            "expired" -> Triple(C.Red, C.RedFaint, t("ownerPanel.reqExpired"))
                            else -> Triple(C.Muted, C.Surface3, t("ownerPanel.reqPending"))
                        }
                        RowCard(Modifier.entrance(i)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(r.str("name"), color = C.Text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                Pill(label, fg, bg)
                            }
                            Text(r.str("mobile"), color = C.Muted, fontSize = 13.sp)
                            otps[r.str("id")]?.let { (otp, exp) ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).background(C.BrandFaint, RoundedCornerShape(10.dp)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(t("ownerPanel.otpLabel"), color = C.OnBrandFaint, fontSize = 12.sp)
                                    SelectionContainer { Text(otp, color = C.Brand, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp) }
                                    Text(t("ownerPanel.otpCopyHint"), color = C.OnBrandFaint, fontSize = 11.sp, textAlign = TextAlign.Center)
                                    Text("${t("ownerPanel.otpExpiresLabel")}: ${dateTime(exp)}", color = C.Muted, fontSize = 11.sp)
                                }
                            }
                            if (st == "verified") {
                                Text(t("ownerPanel.reqCompleted"), color = C.Muted, fontSize = 12.sp)
                            } else {
                                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BigButton(
                                        if (st == "pending") t("ownerPanel.generateOtp") else t("ownerPanel.regenerateOtp"), icon = Icons.Filled.Key,
                                        loading = busyId == r.str("id") + "otp", modifier = Modifier.weight(1f),
                                        onClick = {
                                            run(r.str("id") + "otp") {
                                                val res = Api.post("/owner/store-requests/${Api.seg(r.str("id"))}/generate-otp") as? JSONObject ?: JSONObject()
                                                otps[r.str("id")] = res.str("otp") to res.str("expires_at")
                                            }
                                        },
                                    )
                                    BigButton(
                                        t("ownerPanel.reqDelete"), icon = Icons.Filled.Delete, color = C.Red, loading = busyId == r.str("id") + "del", modifier = Modifier.weight(1f),
                                        onClick = {
                                            run(r.str("id") + "del") {
                                                Api.delete("/owner/store-requests/${Api.seg(r.str("id"))}")
                                                Toast.success(t("ownerPanel.reqDeletedToast"))
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            else -> if (staff.isEmpty()) EmptyState(Icons.Outlined.People, t("ownerPanel.noStaff")) else
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text("ℹ ${t("ownerPanel.staffHint")}", color = C.Muted, fontSize = 12.sp) }
                    itemsIndexed(staff, key = { idx, u -> u.str("id") + "#" + idx }) { i, u ->
                        StaffCard(u, i, busyId == u.str("id"), showStore = true) {
                            run(u.str("id")) {
                                val dis = u.optBoolean("disabled")
                                Api.post("/owner/users/${Api.seg(u.str("id"))}/${if (dis) "reactivate" else "deactivate"}")
                                Toast.success(if (dis) t("ownerPanel.reactivatedToast") else t("ownerPanel.deactivatedToast"))
                            }
                        }
                    }
                }
        }
    }

    deleteTarget?.let { s ->
        Dialog(onDismissRequest = { deleteTarget = null }) {
            Surface(shape = RoundedCornerShape(20.dp), color = C.Card) {
                Column(Modifier.padding(16.dp)) {
                    Text(t("ownerPanel.deleteConfirmTitle"), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = C.Red)
                    Text(t("ownerPanel.deleteConfirmMsg").replace("{name}", s.str("name")), color = C.Text2, modifier = Modifier.padding(vertical = 8.dp))
                    Field(deleteConfirm, { deleteConfirm = it }, s.str("name"), placeholder = s.str("name"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigButton(t("ui.cancel"), outlined = true, modifier = Modifier.weight(1f), onClick = { deleteTarget = null })
                        BigButton(
                            t("ownerPanel.delete"), color = C.Red, loading = deleting, enabled = deleteConfirm.trim() == s.str("name"),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                deleting = true
                                scope.launch {
                                    try {
                                        Api.delete("/owner/stores/${Api.seg(s.str("id"))}", body = JSONObject().put("confirm_name", s.str("name")))
                                        Toast.success(t("ownerPanel.deletedToast"))
                                        deleteTarget = null
                                        reload++
                                    } catch (e: ApiException) {
                                        Toast.error(e.message ?: t("common.failed"))
                                    } finally {
                                        deleting = false
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
//  SIGN UP (create a new store) and STORE LOCKED
// ============================================================

@Composable
fun SignUpScreen(onBackToLogin: () -> Unit, onDone: (User) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf("form") }
    var storeName by rememberSaveable { mutableStateOf("") }
    var mobile by rememberSaveable { mutableStateOf("") }
    var requestId by rememberSaveable { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var tempPassword by rememberSaveable { mutableStateOf("") }
    // Login is kept here and saved only on "Continue", so the one-time password stays on screen.
    var pendingToken by rememberSaveable { mutableStateOf("") }
    var pendingUser by rememberSaveable { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    fun whatsapp() = Share.waMe(context, "New store request: ${storeName.trim()}, ${mobile.trim()}", User.OWNER_CONTACT)

    Column(
        Modifier.fillMaxSize().background(C.Bg).verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(76.dp).background(C.BrandFaint, RoundedCornerShape(20.dp)).entrance(), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Storefront, contentDescription = null, tint = C.Brand, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(t("signup.title"), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
        Text(t("signup.subtitle"), fontSize = 13.sp, color = C.Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().entrance(1)) {
            when (step) {
                "form" -> {
                    Field(storeName, { storeName = it }, t("signup.storeName").uppercase(), placeholder = t("signup.storeNamePlaceholder"))
                    Field(mobile, { mobile = it }, t("signup.contact").uppercase(), placeholder = "e.g. +91 98xxxxxxxx", phone = true)
                    if (err.isNotBlank()) ErrorBox(err)
                    BigButton(t("signup.sendRequest"), icon = Icons.Filled.Chat, loading = loading, onClick = {
                        err = ""
                        if (storeName.isBlank()) { err = t("signup.errStoreNameRequired"); return@BigButton }
                        if (mobile.isBlank()) { err = t("signup.errContact"); return@BigButton }
                        loading = true
                        scope.launch {
                            try {
                                val req = Api.post("/store-requests", JSONObject().put("name", storeName.trim()).put("mobile", mobile.trim())) as? JSONObject ?: JSONObject()
                                requestId = req.str("id")
                                whatsapp()
                                step = "otp"
                            } catch (e: ApiException) {
                                err = e.message ?: t("signup.errFailed")
                            } finally {
                                loading = false
                            }
                        }
                    })
                }
                "otp" -> {
                    Text("$storeName · $mobile", color = C.Text, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Text(t("signup.otpHelper"), color = C.Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    Field(otp, { otp = it.filter(Char::isDigit).take(6) }, t("signup.otpLabel").uppercase(), placeholder = t("signup.otpPlaceholder"), number = true)
                    if (err.isNotBlank()) ErrorBox(err)
                    BigButton(t("signup.verifySubmit"), icon = Icons.Filled.CheckCircle, loading = loading, onClick = {
                        err = ""
                        if (otp.isBlank()) { err = t("signup.errOtpRequired"); return@BigButton }
                        loading = true
                        scope.launch {
                            try {
                                val res = Api.post("/store-requests/${Api.seg(requestId)}/verify-otp", JSONObject().put("otp", otp.trim())) as? JSONObject ?: JSONObject()
                                val uj = res.optJSONObject("user")
                                val token = res.str("access_token")
                                if (uj == null || token.isBlank()) throw ApiException(0, t("signup.errOtpFailed"))
                                pendingToken = token
                                pendingUser = uj.toString()
                                username = uj.str("username")
                                tempPassword = res.str("temp_password")
                                step = "done"
                            } catch (e: Exception) {
                                err = e.message ?: t("signup.errOtpFailed")
                            } finally {
                                loading = false
                            }
                        }
                    })
                    Text("💬 ${t("signup.resendWhatsapp")}", color = C.Brand, fontWeight = FontWeight.ExtraBold, modifier = Modifier.fillMaxWidth().padding(top = 14.dp).pressable {
                        whatsapp(); Toast.show(t("signup.otpResent"))
                    }, textAlign = TextAlign.Center)
                    Text(t("signup.editDetails"), color = C.Muted, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).pressable { step = "form"; err = ""; otp = "" }, textAlign = TextAlign.Center)
                }
                else -> {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = C.Green, modifier = Modifier.size(44.dp).align(Alignment.CenterHorizontally))
                    Text(t("signup.storeReady"), color = C.Text, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Text(t("signup.tempCredsHelper"), color = C.Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    Card {
                        Row { Text(t("signup.usernameLabel"), color = C.Muted, modifier = Modifier.weight(1f)); SelectionContainer { Text(username, fontWeight = FontWeight.ExtraBold) } }
                        HorizontalDivider(color = C.Line, modifier = Modifier.padding(vertical = 8.dp))
                        Row { Text(t("login.password"), color = C.Muted, modifier = Modifier.weight(1f)); SelectionContainer { Text(tempPassword, fontWeight = FontWeight.ExtraBold) } }
                    }
                    Text(t("signup.copyHint"), color = C.Amber, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(14.dp))
                    BigButton(t("signup.continueToApp"), icon = Icons.AutoMirrored.Filled.ArrowForward, onClick = {
                        runCatching { JSONObject(pendingUser) }.getOrNull()?.let { uj ->
                            Session.save(pendingToken, uj)
                            onDone(User.from(uj))
                        }
                    })
                }
            }
            if (step != "done") {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp).pressable(onBackToLogin), horizontalArrangement = Arrangement.Center) {
                    Text("${t("signup.haveAccount")} ", color = C.Muted)
                    Text(t("login.signIn"), color = C.Brand, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun StoreLockedScreen(message: String, contact: String, onBackToLogin: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(C.Bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(110.dp).background(C.RedFaint, CircleShape).entrance(), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = C.Red, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(t("storeLocked.title"), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
        Text(message.ifBlank { t("storeLocked.defaultMessage") }, color = C.Text2, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp))
        if (contact.isNotBlank()) {
            BigButton("${t("storeLocked.call")} $contact", icon = Icons.Filled.Call, onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$contact"))) }
            })
            Spacer(Modifier.height(10.dp))
            BigButton(t("storeLocked.whatsapp"), icon = Icons.Filled.Chat, outlined = true, color = C.WhatsApp, onClick = {
                Share.waMe(context, t("storeLocked.whatsappText"), contact)
            })
        }
        Spacer(Modifier.height(10.dp))
        GhostButton(t("storeLocked.backToLogin"), Icons.AutoMirrored.Filled.Logout, onClick = onBackToLogin)
    }
}
