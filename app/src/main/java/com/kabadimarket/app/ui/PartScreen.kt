package com.kabadimarket.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabadimarket.app.Nav
import com.kabadimarket.app.Route
import com.kabadimarket.app.data.Api
import com.kabadimarket.app.data.ApiException
import com.kabadimarket.app.data.User
import com.kabadimarket.app.data.arr
import com.kabadimarket.app.data.int
import com.kabadimarket.app.data.obj
import com.kabadimarket.app.data.objects
import com.kabadimarket.app.data.str
import com.kabadimarket.app.data.strings
import org.json.JSONObject

/** Search result + full details of one part number. */
@Composable
fun PartScreen(user: User, nav: Nav, initialPartNumber: String) {
    var input by rememberSaveable { mutableStateOf(initialPartNumber) }
    var partNumber by rememberSaveable { mutableStateOf(initialPartNumber.trim()) }
    var reload by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var detail by remember { mutableStateOf<JSONObject?>(null) }

    // Opened from the SEARCH module with no part number: open the camera straight away (like the old app).
    val context = androidx.compose.ui.platform.LocalContext.current
    var autoScanned by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (initialPartNumber.isBlank() && !autoScanned) {
            autoScanned = true
            com.kabadimarket.app.data.scanBarcode(context) { code -> input = code; partNumber = code.trim() }
        }
    }

    LaunchedEffect(partNumber, reload) {
        if (partNumber.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        detail = null
        try {
            val r = Api.getObj("/search", mapOf("q" to partNumber))
            result = r
            val part = r.obj("part")
            if (part != null && user.can("view_part_details")) {
                detail = try {
                    Api.getObj("/parts/" + Api.seg(part.str("part_number").ifBlank { partNumber }))
                } catch (_: ApiException) {
                    null
                }
            }
        } catch (e: ApiException) {
            result = null
            error = e.message
        } finally {
            loading = false
        }
    }

    fun search() {
        val pn = input.trim()
        if (pn.isEmpty()) return
        if (pn == partNumber) reload++ else partNumber = pn
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(t("module.search"), subtitle = partNumber.ifBlank { null }, onBack = { nav.back() })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            PartNumberField(
                value = input,
                onChange = { input = it },
                onSubmit = { search() },
                onScanned = { code -> input = code; partNumber = code.trim() },
            )

            when {
                loading -> Loading(t("common.searching"))
                error != null -> ErrorBox(error)
                result != null -> PartResult(user, nav, result!!, detail)
                else -> Text("Type or scan a part number.", color = C.Muted)
            }
        }
    }
}

@Composable
private fun PartResult(user: User, nav: Nav, r: JSONObject, detail: JSONObject?) {
    val status = r.str("status")
    val stock = r.int("stock_count")
    val pn = r.obj("part")?.str("part_number")?.ifBlank { null } ?: r.str("part_number")
    val info: JSONObject? = detail ?: r.obj("part") ?: r.obj("known") ?: r.obj("catalog")
    val (fg, bg) = statusColors(status)

    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pn, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.Text)
                Spacer(Modifier.height(4.dp))
                Badge(com.kabadimarket.app.data.I18n.status(status), fg, bg)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stock.toString(), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = if (stock > 0) C.Green else C.Muted)
                Text("in stock", fontSize = 12.sp, color = C.Muted)
            }
        }
        val statusHelp = when (status) {
            "IN STOCK" -> "This part is available in your store."
            "REQUIREMENT" -> "A customer has asked for this part."
            "KNOWN PART" -> "Known part, but no stock right now."
            "IN CATALOG" -> "Seen in the shared catalog, not in your store yet."
            "NEW PART" -> "Never seen before."
            else -> ""
        }
        if (statusHelp.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(statusHelp, fontSize = 13.sp, color = C.Muted)
        }
    }

    // Purchase limit (don't buy more than allowed)
    r.obj("limit")?.let { limit ->
        if (limit.optBoolean("limit_enabled")) {
            Spacer(Modifier.height(12.dp))
            val ls = limit.str("status")
            val (lf, lb) = statusColors(ls)
            Card {
                SectionTitle("Purchase limit")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Allowed ${limit.int("allowed_limit")} · In stock ${limit.int("existing_stock")} · Left ${limit.int("remaining")}",
                        fontSize = 14.sp, color = C.Text, modifier = Modifier.weight(1f),
                    )
                    Badge(if (ls == "STOP") "DO NOT BUY" else ls, lf, lb)
                }
            }
        }
    }

    // Requirement from a customer
    r.obj("requirement")?.let { req ->
        Spacer(Modifier.height(12.dp))
        Card {
            SectionTitle("Customer requirement")
            InfoRow("Status", req.str("status"))
            InfoRow("Priority", req.str("priority"))
            InfoRow("Quantity", req.int("quantity").takeIf { it > 0 }?.toString() ?: "")
            InfoRow("Note", req.str("note"))
            InfoRow("Asked by", req.str("by").ifBlank { req.str("created_by") })
        }
    }

    // Part details
    if (info != null) {
        Spacer(Modifier.height(12.dp))
        Card {
            SectionTitle("Details")
            InfoRow("Name", info.str("name"))
            InfoRow("Company", info.str("company").takeIf { it != "All" } ?: "")
            InfoRow("Category", info.str("category"))
            InfoRow("Variant", info.str("variant"))
            InfoRow("Year", info.str("year"))
            InfoRow("Vehicles", info.arr("compatible_vehicles").strings().joinToString(", "))
            InfoRow("Old number", info.str("old_number"))
            InfoRow("New number", info.str("new_number"))
            InfoRow("Verified", info.str("verification_status"))
            InfoRow("Info", info.str("technical_info"))
        }
    }

    // Actions
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (user.can("buy")) {
            BigButton("📥 Buy", onClick = { nav.open(Route.Buy(pn)) }, color = C.Green, modifier = Modifier.weight(1f))
        }
        if (user.can("sell") && stock > 0) {
            BigButton("🧾 Sell", onClick = { nav.open(Route.Sell(pn)) }, modifier = Modifier.weight(1f))
        }
    }

    // Stock units (each physical piece)
    val units = detail?.arr("units")?.objects().orEmpty()
    if (units.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        SectionTitle("Stock pieces (${units.size})")
        Card {
            units.forEachIndexed { i, u ->
                if (i > 0) HorizontalDivider(color = C.Line, modifier = Modifier.padding(vertical = 8.dp))
                UnitRow(u, canSell = user.can("sell")) { nav.open(Route.Sell(pn, u.str("id"))) }
            }
        }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
fun UnitRow(u: JSONObject, canSell: Boolean, onSell: () -> Unit) {
    val cond = u.str("condition").ifBlank { "Unknown" }
    val (fg, bg) = conditionColors(cond)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(cond, fg, bg)
                Spacer(Modifier.padding(start = 8.dp))
                Text(shortDate(u.str("created_at")), fontSize = 13.sp, color = C.Muted)
            }
            val loc = locationText(u.obj("assigned_location"))
            if (loc.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("📍 $loc", fontSize = 13.sp, color = C.Text)
            }
            val by = u.str("added_by")
            if (by.isNotBlank()) Text("Added by $by", fontSize = 12.sp, color = C.Muted)
        }
        if (canSell) {
            TextButton(onClick = onSell) { Text("Sell", fontWeight = FontWeight.Bold) }
        }
    }
}

/** Store · Wall · Rack (or Open floor + carton) · Shelf */
fun locationText(a: JSONObject?): String {
    if (a == null) return ""
    val parts = mutableListOf<String>()
    a.str("store_name").takeIf { it.isNotBlank() }?.let { parts.add(it) }
    a.str("wall").takeIf { it.isNotBlank() }?.let { parts.add("$it wall") }
    if (a.optBoolean("is_open_floor")) {
        parts.add("Open floor")
        a.str("carton_number").takeIf { it.isNotBlank() }?.let { parts.add("Carton $it") }
    } else {
        a.str("rack_name").takeIf { it.isNotBlank() }?.let { parts.add("Rack $it") }
    }
    a.str("shelf_level").takeIf { it.isNotBlank() }?.let { parts.add("$it shelf") }
    return parts.joinToString(" · ")
}
