package com.kabadimarket.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabadimarket.app.Nav
import com.kabadimarket.app.data.Api
import com.kabadimarket.app.data.ApiException
import com.kabadimarket.app.data.Branding
import com.kabadimarket.app.data.Feedback
import com.kabadimarket.app.data.Printer
import com.kabadimarket.app.data.User
import com.kabadimarket.app.data.arr
import com.kabadimarket.app.data.extractPartNumber
import com.kabadimarket.app.data.int
import com.kabadimarket.app.data.num
import com.kabadimarket.app.data.obj
import com.kabadimarket.app.data.objects
import com.kabadimarket.app.data.str
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Small colored status pill (old app's statusPill: color + 13% background). */
@Composable
private fun StatusPill(text: String, color: Color) {
    Box(Modifier.background(color.copy(alpha = 0.13f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// ============================================================
//  QUOTATIONS + PURCHASE ORDERS (same layout, different words)
// ============================================================

private class DraftItem {
    var partNumber by mutableStateOf("")
    var quantity by mutableStateOf("1")
    var price by mutableStateOf("")
}

private data class OrderBookConfig(
    val keyPrefix: String, // "quotations" or "purchaseOrders"
    val endpoint: String,
    val partyField: String, // customer_name / vendor_name
    val numberField: String, // quote_number / po_number
    val partyLabel: String,
    val partyPlaceholder: String,
    val newTitle: String,
    val emptyKey: String,
    val emptyIcon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
fun QuotationsScreen(user: User, nav: Nav) = OrderBookScreen(
    user, nav,
    OrderBookConfig(
        "quotations", "/quotations", "customer_name", "quote_number",
        "quotations.customerName", "quotations.customerPlaceholder", "quotations.newQuote", "quotations.noQuotes", Icons.Outlined.Description,
    ),
)

@Composable
fun PurchaseOrdersScreen(user: User, nav: Nav) = OrderBookScreen(
    user, nav,
    OrderBookConfig(
        "purchaseOrders", "/purchase-orders", "vendor_name", "po_number",
        "purchaseOrders.vendorName", "purchaseOrders.vendorPlaceholder", "purchaseOrders.newOrder", "purchaseOrders.noOrders", Icons.Filled.Assignment,
    ),
)

@Composable
private fun OrderBookScreen(user: User, nav: Nav, cfg: OrderBookConfig) {
    val scope = rememberCoroutineScope()
    val k = cfg.keyPrefix
    var list by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var party by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val items = remember { mutableStateListOf(DraftItem()) }
    var saving by remember { mutableStateOf(false) }
    val isQuote = k == "quotations"

    LaunchedEffect(reload) {
        try {
            list = Api.getArr(cfg.endpoint).objects()
        } catch (e: ApiException) {
            Toast.error(e.message ?: t("$k.loadFailed"))
        } finally {
            loading = false
        }
    }

    fun setStatus(id: String, status: String) {
        scope.launch {
            try {
                Api.patch("${cfg.endpoint}/${Api.seg(id)}/status", JSONObject().put("status", status))
                Toast.success(t("$k.updated"))
                reload++
            } catch (e: ApiException) {
                Toast.error(e.message ?: t("$k.updateFailed"))
            }
        }
    }

    fun statusColor(s: String) = when (s) {
        "accepted", "converted", "received" -> C.Green
        "rejected", "cancelled" -> C.Red
        else -> C.Amber
    }

    Screen {
        TopBar(t("$k.title"), onBack = { nav.back() })
        if (loading) {
            Loading(); return@Screen
        }
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.entrance(0).animateContentSize()) {
                SectionTitle(t(cfg.newTitle))
                Field(party, { party = it }, t(cfg.partyLabel), placeholder = t(cfg.partyPlaceholder))
                items.forEachIndexed { idx, it ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Field(it.partNumber, { v -> it.partNumber = v }, t("$k.partNumber"), caps = true, modifier = Modifier.weight(2f))
                        Field(it.quantity, { v -> it.quantity = v.filter(Char::isDigit) }, t("$k.qty"), number = true, modifier = Modifier.weight(1f))
                        Field(it.price, { v -> it.price = v }, t("$k.price"), number = true, modifier = Modifier.weight(1f))
                        if (items.size > 1) {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = C.Red, modifier = Modifier.size(22.dp).pressable { items.removeAt(idx) })
                        }
                    }
                }
                BigButton(t("$k.addItem"), icon = Icons.Filled.AddCircle, outlined = true, onClick = { items.add(DraftItem()) })
                Spacer(Modifier.height(10.dp))
                Field(note, { note = it }, t("common.noteOptional"))
                BigButton(t("$k.save"), icon = Icons.Filled.CheckCircle, loading = saving, onClick = {
                    val clean = items.map {
                        JSONObject().put("part_number", it.partNumber.trim()).put("name", "")
                            .put("quantity", it.quantity.toIntOrNull()?.takeIf { q -> q > 0 } ?: 1)
                            .put("price", it.price.trim().toDoubleOrNull() ?: 0.0)
                    }.filter { it.str("part_number").isNotBlank() }
                    if (clean.isEmpty()) {
                        Toast.error(t("$k.errItems")); return@BigButton
                    }
                    saving = true
                    scope.launch {
                        try {
                            Api.post(cfg.endpoint, JSONObject().put(cfg.partyField, party.trim()).put("items", JSONArray(clean)).put("note", note.trim()))
                            Toast.success(t("$k.created"))
                            party = ""; note = ""
                            items.clear(); items.add(DraftItem())
                            reload++
                        } catch (e: ApiException) {
                            Toast.error(e.message ?: t("$k.createFailed"))
                        } finally {
                            saving = false
                        }
                    }
                })
            }
            Card(Modifier.entrance(1)) {
                SectionTitle(t("$k.history"))
                if (list.isEmpty()) EmptyState(cfg.emptyIcon, t(cfg.emptyKey))
                list.forEach { q ->
                    val status = q.str("status")
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).border(1.dp, C.Line, RoundedCornerShape(10.dp)).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(q.str(cfg.numberField), color = C.Text, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            StatusPill(t("$k.status.$status"), statusColor(status))
                        }
                        if (q.str(cfg.partyField).isNotBlank()) Text(q.str(cfg.partyField), color = C.Text2, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        q.arr("items").objects().forEach { it ->
                            val nm = it.str("name")
                            Text("• ${it.str("part_number")} ${if (nm.isNotBlank()) "($nm)" else ""} × ${it.int("quantity")} @ ₹${Printer.fmtNum(it.num("price") ?: 0.0)}", color = C.Text2, fontSize = 13.sp)
                        }
                        Text("${t("$k.total")}: ${money(q.num("total") ?: 0.0)}", color = C.Brand, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
                        Text("${dateTime(q.str("at"))} • ${q.str("by")}", color = C.Muted, fontSize = 11.sp)
                        if (status == "pending") {
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (isQuote) {
                                    BigButton(t("quotations.accept"), icon = Icons.Filled.DoneAll, modifier = Modifier.weight(1f), onClick = { setStatus(q.str("id"), "accepted") })
                                    BigButton(t("quotations.reject"), icon = Icons.Filled.Cancel, color = C.Red, modifier = Modifier.weight(1f), onClick = { setStatus(q.str("id"), "rejected") })
                                } else {
                                    BigButton(t("purchaseOrders.markReceived"), icon = Icons.Filled.DoneAll, modifier = Modifier.weight(1f), onClick = { setStatus(q.str("id"), "received") })
                                    BigButton(t("purchaseOrders.cancel"), icon = Icons.Filled.Cancel, color = C.Red, modifier = Modifier.weight(1f), onClick = { setStatus(q.str("id"), "cancelled") })
                                }
                            }
                        }
                        if (isQuote && status == "accepted") {
                            Spacer(Modifier.height(8.dp))
                            BigButton(t("quotations.markConverted"), icon = Icons.Filled.SwapHoriz, onClick = { setStatus(q.str("id"), "converted") })
                        }
                        if (user.isAdmin) {
                            GhostButton(t("common.delete"), Icons.Filled.Delete) {
                                scope.launch {
                                    try {
                                        Api.delete("${cfg.endpoint}/${Api.seg(q.str("id"))}")
                                        Toast.success(t("$k.deleted"))
                                        reload++
                                    } catch (e: ApiException) {
                                        Toast.error(e.message ?: t("$k.deleteFailed"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ============================================================
//  STOCK HOLD (Reservations)
// ============================================================

@Composable
fun ReservationsScreen(user: User, nav: Nav) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var pn by rememberSaveable { mutableStateOf("") }
    var customer by rememberSaveable { mutableStateOf("") }
    var qty by rememberSaveable { mutableStateOf("1") }
    var note by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        try {
            rows = Api.getArr("/reservations").objects()
        } catch (e: ApiException) {
            Toast.error(e.message ?: t("reservations.loadFailed"))
        } finally {
            loading = false
        }
    }

    fun setStatus(id: String, status: String) {
        scope.launch {
            try {
                Api.patch("/reservations/${Api.seg(id)}/status", JSONObject().put("status", status))
                Toast.success(t("reservations.updated"))
                reload++
            } catch (e: ApiException) {
                Toast.error(e.message ?: t("reservations.updateFailed"))
            }
        }
    }

    Screen {
        TopBar(t("reservations.title"), onBack = { nav.back() })
        if (loading) {
            Loading(); return@Screen
        }
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.entrance(0)) {
                SectionTitle(t("reservations.newReservation"))
                PartNumberField(pn, { pn = it }, label = t("reservations.partNumber"))
                Field(customer, { customer = it }, t("reservations.customerName"), placeholder = t("reservations.customerPlaceholder"))
                Field(qty, { qty = it.filter(Char::isDigit) }, t("reservations.qty"), number = true)
                Field(note, { note = it }, t("common.noteOptional"))
                BigButton(t("reservations.save"), icon = Icons.Filled.Lock, loading = saving, onClick = {
                    val q = qty.toIntOrNull() ?: 0
                    if (pn.isBlank() || q <= 0) {
                        Toast.error(t("reservations.errPart")); return@BigButton
                    }
                    saving = true
                    scope.launch {
                        try {
                            Api.post("/reservations", JSONObject().put("part_number", pn.trim()).put("customer_name", customer.trim()).put("quantity", q).put("note", note.trim()))
                            Toast.success(t("reservations.created"))
                            pn = ""; customer = ""; qty = "1"; note = ""
                            reload++
                        } catch (e: ApiException) {
                            Toast.error(e.message ?: t("reservations.createFailed"))
                        } finally {
                            saving = false
                        }
                    }
                })
            }
            Card(Modifier.entrance(1)) {
                SectionTitle(t("reservations.history"))
                if (rows.isEmpty()) EmptyState(Icons.Outlined.Lock, t("reservations.noReservations"))
                rows.forEach { r ->
                    val status = r.str("status")
                    val color = when (status) {
                        "fulfilled" -> C.Green
                        "cancelled" -> C.Red
                        else -> C.Amber
                    }
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).border(1.dp, C.Line, RoundedCornerShape(10.dp)).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${r.str("part_number")} × ${r.int("quantity")}", color = C.Text, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                            StatusPill(t("reservations.status.$status"), color)
                        }
                        if (r.str("customer_name").isNotBlank()) Text(r.str("customer_name"), color = C.Text2, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (r.str("note").isNotBlank()) Text(r.str("note"), color = C.Text2, fontSize = 13.sp)
                        Text("${dateTime(r.str("at"))} • ${r.str("by")}", color = C.Muted, fontSize = 11.sp)
                        if (status == "active") {
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BigButton(t("reservations.fulfill"), icon = Icons.Filled.DoneAll, modifier = Modifier.weight(1f), onClick = { setStatus(r.str("id"), "fulfilled") })
                                BigButton(t("reservations.cancel"), icon = Icons.Filled.Cancel, color = C.Red, modifier = Modifier.weight(1f), onClick = { setStatus(r.str("id"), "cancelled") })
                            }
                        }
                        if (user.isAdmin) {
                            GhostButton(t("common.delete"), Icons.Filled.Delete) {
                                scope.launch {
                                    try {
                                        Api.delete("/reservations/${Api.seg(r.str("id"))}")
                                        Toast.success(t("reservations.deleted"))
                                        reload++
                                    } catch (e: ApiException) {
                                        Toast.error(e.message ?: t("reservations.deleteFailed"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ============================================================
//  STOCK TRANSFER (store → store, platform owner only)
// ============================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StockTransferScreen(nav: Nav) {
    val scope = rememberCoroutineScope()
    var stores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var transfers by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var fromStore by rememberSaveable { mutableStateOf<String?>(null) }
    var toStore by rememberSaveable { mutableStateOf<String?>(null) }
    var pn by rememberSaveable { mutableStateOf("") }
    var qty by rememberSaveable { mutableStateOf("1") }
    var note by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        try {
            stores = Api.getArr("/admin/stores").objects()
            transfers = Api.getArr("/stock-transfer").objects()
        } catch (e: ApiException) {
            Toast.error(e.message ?: t("stockTransfer.loadFailed"))
        } finally {
            loading = false
        }
    }
    fun storeName(id: String) = stores.firstOrNull { it.str("id") == id }?.str("name") ?: id

    Screen {
        TopBar(t("stockTransfer.title"), onBack = { nav.back() })
        if (loading) {
            Loading(); return@Screen
        }
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.entrance(0)) {
                SectionTitle(t("stockTransfer.newTransfer"))
                Text(t("stockTransfer.fromStore"), color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                FlowRow(Modifier.padding(vertical = 6.dp)) { stores.forEach { s -> Chip(s.str("name"), fromStore == s.str("id")) { fromStore = s.str("id") } } }
                Text(t("stockTransfer.toStore"), color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                FlowRow(Modifier.padding(vertical = 6.dp)) { stores.forEach { s -> Chip(s.str("name"), toStore == s.str("id")) { toStore = s.str("id") } } }
                PartNumberField(pn, { pn = it }, label = t("stockTransfer.partNumber"))
                Field(qty, { qty = it.filter(Char::isDigit) }, t("stockTransfer.qty"), number = true)
                Field(note, { note = it }, t("common.noteOptional"))
                BigButton(t("stockTransfer.save"), icon = Icons.Filled.SwapHoriz, loading = saving, onClick = {
                    val f = fromStore
                    val tt = toStore
                    val q = qty.toIntOrNull() ?: 0
                    when {
                        f == null || tt == null -> { Toast.error(t("stockTransfer.errStores")); return@BigButton }
                        f == tt -> { Toast.error(t("stockTransfer.errSame")); return@BigButton }
                        pn.isBlank() || q <= 0 -> { Toast.error(t("stockTransfer.errPart")); return@BigButton }
                    }
                    saving = true
                    scope.launch {
                        try {
                            Api.post(
                                "/stock-transfer",
                                JSONObject().put("from_store_id", f).put("to_store_id", tt).put("part_number", pn.trim()).put("quantity", q).put("note", note.trim()),
                            )
                            Toast.success(t("stockTransfer.done"))
                            pn = ""; qty = "1"; note = ""
                            reload++
                        } catch (e: ApiException) {
                            Toast.error(e.message ?: t("stockTransfer.failed"))
                        } finally {
                            saving = false
                        }
                    }
                })
            }
            Card(Modifier.entrance(1)) {
                SectionTitle(t("stockTransfer.history"))
                if (transfers.isEmpty()) EmptyState(Icons.Outlined.SwapHoriz, t("stockTransfer.noTransfers"))
                transfers.forEach { tr ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text("${tr.str("part_number")} × ${tr.int("quantity")}", color = C.Text, fontWeight = FontWeight.ExtraBold)
                        Text("${storeName(tr.str("from_store_id"))} → ${storeName(tr.str("to_store_id"))}", color = C.Brand, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (tr.str("note").isNotBlank()) Text(tr.str("note"), color = C.Text2, fontSize = 12.sp)
                        Text("${dateTime(tr.str("at"))} • ${tr.str("by")}", color = C.Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ============================================================
//  STOCK VERIFY (count physical stock)
// ============================================================

@Composable
fun StockVerifyScreen(nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    val counts = remember { mutableStateMapOf<String, Int>() }
    var loading by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<JSONObject?>(null) }

    LaunchedEffect(Unit) {
        try {
            items = Api.getObj("/stock/verification").arr("items").objects()
            items.forEach { counts[it.str("part_number")] = 0 }
        } catch (e: ApiException) {
            Toast.error(e.message ?: t("common.loadFailed"))
        } finally {
            loading = false
        }
    }

    Screen {
        TopBar(t("stockVerify.title"), t("stockVerify.subtitle"), onBack = { nav.back() })
        when {
            loading -> Loading()
            items.isEmpty() -> EmptyState(Icons.Outlined.Inventory2, t("sell.noStock"), t("stockVerify.addFromBuy"))
            else -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoNote(t("stockVerify.infoText"), Icons.Filled.Assignment)
                    val r = report
                    if (r != null) {
                        val disc = r.arr("discrepancies").objects()
                        Card(Modifier.entrance()) {
                            SectionTitle(t("stockVerify.report"))
                            Row(Modifier.fillMaxWidth()) {
                                VerifyBox(r.int("total_parts").toString(), t("stockVerify.totalParts"), C.Text, Modifier.weight(1f))
                                VerifyBox(r.int("ok_count").toString(), t("stockVerify.ok"), C.Green, Modifier.weight(1f))
                                VerifyBox(disc.size.toString(), t("stockVerify.difference"), C.Red, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                            if (disc.isEmpty()) Text("✅ ${t("stockVerify.allMatched")}", color = C.Green, fontWeight = FontWeight.Bold)
                            disc.forEach { d ->
                                val diff = d.int("diff")
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(d.str("part_number"), color = C.Text, fontWeight = FontWeight.ExtraBold)
                                        if (d.str("part_name").isNotBlank()) Text(d.str("part_name"), color = C.Text2, fontSize = 13.sp)
                                        Text("${t("stockVerify.system")}: ${d.int("expected")}  •  ${t("stockVerify.counted")}: ${d.int("counted")}  •  ${if (diff > 0) "+$diff" else "$diff"}", color = C.Muted, fontSize = 12.sp)
                                    }
                                    StatusChip(if (d.str("status") == "MISSING") "Cancelled" else "Pending")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            BigButton(t("stockVerify.countAgain"), outlined = true, onClick = { report = null })
                        }
                    } else {
                        items.forEachIndexed { i, it ->
                            val pn = it.str("part_number")
                            val expected = it.int("expected")
                            Card(Modifier.entrance(i)) {
                                Text(pn, color = C.Text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                if (it.str("part_name").isNotBlank()) Text(it.str("part_name"), color = C.Text2, fontSize = 13.sp)
                                Text("${t("stockVerify.systemStock")}: $expected", color = C.Muted, fontSize = 12.sp)
                                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    SmallIconBox(Icons.Filled.Remove, C.Text) { counts[pn] = ((counts[pn] ?: 0) - 1).coerceAtLeast(0) }
                                    Text("${counts[pn] ?: 0}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.padding(horizontal = 16.dp))
                                    SmallIconBox(Icons.Filled.Add, C.Text) { counts[pn] = (counts[pn] ?: 0) + 1 }
                                    Spacer(Modifier.width(12.dp))
                                    Box(Modifier.background(C.GreenFaint, RoundedCornerShape(8.dp)).pressable { counts[pn] = expected }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text("= $expected", color = C.OnGreenFaint, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                if (report == null) {
                    Column(Modifier.background(C.Card).padding(12.dp)) {
                        BigButton(t("stockVerify.verifyView"), icon = Icons.Filled.DoneAll, loading = submitting, onClick = {
                            submitting = true
                            scope.launch {
                                try {
                                    val arr = JSONArray(items.map { JSONObject().put("part_number", it.str("part_number")).put("counted", counts[it.str("part_number")] ?: 0) })
                                    report = Api.post("/stock/verify", JSONObject().put("counts", arr)) as? JSONObject ?: JSONObject()
                                    Feedback.success(context)
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
        }
    }
}

@Composable
private fun VerifyBox(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = C.Muted, fontSize = 12.sp)
    }
}

// ============================================================
//  STORE ARRANGEMENT (put bought stock on racks)
// ============================================================

@Composable
fun StoreArrangementScreen(user: User, nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var manual by rememberSaveable { mutableStateOf("") }
    var part by remember { mutableStateOf<JSONObject?>(null) }
    var loadingPart by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var loc by remember { mutableStateOf(AssignedLocation(storeName = if (user.isSuperAdmin) null else user.storeName.ifBlank { null })) }
    val confirmedMap = remember { mutableStateMapOf<String, Int>() }

    fun loadPart(raw: String) {
        val pn = extractPartNumber(raw)
        if (pn.isBlank()) return
        loadingPart = true
        scope.launch {
            try {
                part = Api.getObj("/parts/${Api.seg(pn)}")
            } catch (e: ApiException) {
                part = null
                Toast.error(if (e.status == 404) "${t("storeArrangement.partNotFoundPre")} \"$pn\" ${t("storeArrangement.partNotFoundPost")}" else e.message ?: "")
            } finally {
                loadingPart = false
            }
        }
    }

    Screen {
        TopBar(t("storeArrangement.title"), t("storeArrangement.subtitle"), onBack = { nav.back() })
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PartNumberField(
                manual, { manual = it }, label = t("storeArrangement.scanOrType"),
                onSubmit = { loadPart(manual) },
                onScanned = { code -> manual = ""; Feedback.success(context); loadPart(code) },
            )
            val p = part
            when {
                loadingPart -> Loading(t("storeArrangement.lookingUp"))
                p == null -> EmptyState(Icons.Outlined.Inventory2, t("storeArrangement.scanToBegin"), t("storeArrangement.scanToBeginSub"))
                else -> {
                    val units = p.arr("units").objects()
                    val pn = p.str("part_number")
                    val target = units.firstOrNull { AssignedLocation.from(it.obj("assigned_location")) == null } ?: units.lastOrNull()
                    val arranged = units.count { AssignedLocation.from(it.obj("assigned_location")) != null }
                    val expected = p.int("stock_count")
                    val confirmed = confirmedMap[pn] ?: 0
                    val mismatch = expected > 0 && confirmed != expected
                    Card(Modifier.entrance()) {
                        Text(pn, color = C.Text, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                        if (p.str("name").isNotBlank()) Text(p.str("name"), color = C.Text2)
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            VerifyBox(expected.toString(), t("storeArrangement.expected"), C.Text, Modifier.weight(1f))
                            VerifyBox(arranged.toString(), t("storeArrangement.arranged"), C.Green, Modifier.weight(1f))
                            VerifyBox(confirmed.toString(), t("storeArrangement.confirmedSession"), if (mismatch) C.Amber else C.Green, Modifier.weight(1f))
                        }
                        when {
                            expected == 0 -> ArrangeBanner(Icons.Filled.Error, t("storeArrangement.noStockOnFile"), C.AmberFaint, C.OnAmberFaint)
                            mismatch -> ArrangeBanner(
                                Icons.Filled.Warning,
                                if (confirmed < expected) "${t("storeArrangement.shortByPre")} ${expected - confirmed} ${t("storeArrangement.shortBySuffix")}"
                                else "⚠️ ${confirmed - expected} ${t("storeArrangement.extraConfirmSuffix")}",
                                C.AmberFaint, C.OnAmberFaint,
                            )
                            else -> ArrangeBanner(Icons.Filled.CheckCircle, "${t("storeArrangement.allConfirmedPre")} $expected ${t("storeArrangement.allConfirmedSuffix")}", C.Green, Color.White)
                        }
                    }
                    Card {
                        SectionTitle(t("storeArrangement.assignLocation"))
                        LocationPicker(loc, { loc = it }, showStoreName = user.isSuperAdmin)
                    }
                    BigButton(
                        if (saving) t("storeArrangement.saving") else t("storeArrangement.saveLocation"),
                        icon = Icons.Filled.Save, loading = saving, enabled = target != null,
                        onClick = {
                            val u = target ?: return@BigButton
                            if (loc.isEmpty) {
                                Toast.error(t("storeArrangement.pickLocationField")); return@BigButton
                            }
                            saving = true
                            scope.launch {
                                try {
                                    Api.patch("/stock/unit/${Api.seg(u.str("id"))}", JSONObject().put("assigned_location", loc.toJson()))
                                    Feedback.success(context)
                                    confirmedMap[pn] = (confirmedMap[pn] ?: 0) + 1
                                    Toast.success(t("storeArrangement.locationSaved"))
                                    part = Api.getObj("/parts/${Api.seg(pn)}")
                                } catch (e: ApiException) {
                                    Feedback.error(context)
                                    Toast.error(e.message ?: t("storeArrangement.saveLocationFailed"))
                                } finally {
                                    saving = false
                                }
                            }
                        },
                    )
                    BigButton(t("storeArrangement.printSticker"), icon = Icons.Filled.Print, outlined = true, enabled = !loc.isEmpty, onClick = {
                        Printer.print(context, Printer.locationStickerHtml(Branding.from(user), pn, formatAssignedLocation(loc), p.str("name")), "Location Sticker")
                    })
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ArrangeBanner(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, bg: Color, fg: Color) {
    Row(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
