package com.kabadimarket.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabadimarket.app.Nav
import com.kabadimarket.app.data.Api
import com.kabadimarket.app.data.ApiException
import com.kabadimarket.app.data.User
import com.kabadimarket.app.data.arr
import com.kabadimarket.app.data.int
import com.kabadimarket.app.data.num
import com.kabadimarket.app.data.obj
import com.kabadimarket.app.data.objects
import com.kabadimarket.app.data.str
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val CASH = "Cash sale (no khata)"

/** Sell = remove one piece from stock and make a GST bill. */
@Composable
fun SellScreen(@Suppress("UNUSED_PARAMETER") user: User, nav: Nav, initialPartNumber: String, initialUnitId: String?) {
    var pn by rememberSaveable { mutableStateOf(initialPartNumber) }
    var unitId by rememberSaveable { mutableStateOf(initialUnitId) }
    var price by rememberSaveable { mutableStateOf("") }
    var buyer by rememberSaveable { mutableStateOf("") }
    var customerName by rememberSaveable { mutableStateOf(CASH) }

    var part by remember { mutableStateOf<JSONObject?>(null) }
    var partError by remember { mutableStateOf<String?>(null) }
    var loadingPart by remember { mutableStateOf(false) }
    var customers by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        customers = try {
            Api.getArr("/customers").objects()
        } catch (_: ApiException) {
            emptyList()
        }
    }

    // Load the part and its stock pieces whenever the part number changes.
    LaunchedEffect(pn) {
        part = null
        partError = null
        val q = pn.trim()
        if (q.isEmpty()) return@LaunchedEffect
        delay(600)
        loadingPart = true
        try {
            val p = Api.getObj("/parts/" + Api.seg(q))
            part = p
            val units = p.arr("units").objects()
            if (units.none { it.str("id") == unitId }) unitId = units.firstOrNull()?.str("id")
        } catch (e: ApiException) {
            partError = if (e.status == 404) "Part not found in your store." else e.message
        } finally {
            loadingPart = false
        }
    }

    fun submit() {
        val p = part ?: return
        val priceValue = price.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        if (price.isNotBlank() && priceValue == null) {
            error = "Price is not a valid number"
            return
        }
        val customer = customers.firstOrNull { it.str("name") == customerName }
        if (customer != null && priceValue == null) {
            error = "Enter the price for a khata (credit) sale"
            return
        }
        val body = JSONObject()
            .put("part_number", p.str("part_number"))
            .put("buyer", buyer.trim())
        unitId?.let { body.put("unit_id", it) }
        if (priceValue != null) body.put("price", priceValue)
        if (customer != null) body.put("customer_id", customer.str("id"))
        saving = true
        error = null
        scope.launch {
            try {
                done = Api.post("/sell", body) as JSONObject
            } catch (e: ApiException) {
                error = e.message
            } finally {
                saving = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(t("module.sell"), subtitle = "Sale", onBack = { nav.back() })

        val result = done
        if (result != null) {
            SellDone(
                result,
                onAnother = {
                    pn = ""; unitId = null; price = ""; buyer = ""; customerName = CASH
                    done = null; error = null
                },
                onHome = { nav.home() },
            )
            return@Column
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
        ) {
            Card {
                SectionTitle("Part")
                PartNumberField(pn, { pn = it; unitId = null })
                if (loadingPart) Loading("Finding stock…")
                ErrorBox(partError)
                part?.let { p ->
                    val nm = p.str("name")
                    if (nm.isNotBlank()) Text(nm, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = C.Text)
                }
            }

            val units = part?.arr("units")?.objects().orEmpty()
            if (part != null) {
                Spacer(Modifier.height(12.dp))
                Card {
                    SectionTitle("Choose the piece (${units.size} in stock)")
                    if (units.isEmpty()) {
                        Text("No stock available — cannot sell.", color = C.Red, fontSize = 14.sp)
                    }
                    units.forEachIndexed { i, u ->
                        if (i > 0) HorizontalDivider(color = C.Line)
                        val id = u.str("id")
                        val cond = u.str("condition").ifBlank { "Unknown" }
                        val (fg, bg) = conditionColors(cond)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { unitId = id }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = unitId == id, onClick = { unitId = id })
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Badge(cond, fg, bg)
                                    Spacer(Modifier.padding(start = 8.dp))
                                    Text(shortDate(u.str("created_at")), fontSize = 13.sp, color = C.Muted)
                                }
                                val loc = locationText(u.obj("assigned_location"))
                                if (loc.isNotBlank()) Text("📍 $loc", fontSize = 12.sp, color = C.Muted)
                            }
                        }
                    }
                }
            }

            if (units.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Card {
                    SectionTitle("Bill")
                    Field(price, { price = it }, "Selling price (₹, before GST)", number = true)
                    val pv = price.trim().toDoubleOrNull()
                    if (pv != null) {
                        val gst = Math.round(pv * 18.0) / 100.0
                        Text(
                            "GST 18%: ${money(gst)}   ·   Total: ${money(pv + gst)}",
                            fontSize = 14.sp, color = C.Brand, fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                    Picker("Customer (khata)", customerName, listOf(CASH) + customers.map { it.str("name") }) {
                        customerName = it
                    }
                    customers.firstOrNull { it.str("name") == customerName }?.let { c ->
                        val bal = c.num("balance") ?: 0.0
                        Text(
                            "Current balance: ${money(bal)}",
                            fontSize = 13.sp, color = if (bal > 0) C.Amber else C.Muted,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Field(buyer, { buyer = it }, "Buyer note — optional (e.g. mechanic name)")
                }
                ErrorBox(error)
                Spacer(Modifier.height(12.dp))
                BigButton("🧾 Sell & make bill", onClick = { submit() }, loading = saving, enabled = unitId != null)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SellDone(r: JSONObject, onAnother: () -> Unit, onHome: () -> Unit) {
    val inv = r.obj("invoice")
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Text("✅", fontSize = 56.sp)
        Text("Sold", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.Green)
        Text("Stock left: ${r.int("remaining_stock")}", fontSize = 14.sp, color = C.Muted)
        Spacer(Modifier.height(16.dp))
        if (inv != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(C.Card, RoundedCornerShape(14.dp))
                    .border(1.dp, C.Line, RoundedCornerShape(14.dp))
                    .padding(16.dp),
            ) {
                Text(inv.str("store_name").ifBlank { "Invoice" }, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.Text)
                if (inv.str("store_gst").isNotBlank()) Text("GSTIN: ${inv.str("store_gst")}", fontSize = 12.sp, color = C.Muted)
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = C.Line)
                Spacer(Modifier.height(8.dp))
                InfoRow("Invoice no.", inv.str("invoice_number"))
                InfoRow("Date", shortDate(inv.str("at")))
                InfoRow("Customer", inv.str("customer_name"))
                InfoRow("Part", inv.str("part_number"))
                InfoRow("Item", inv.str("description"))
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = C.Line)
                Spacer(Modifier.height(8.dp))
                InfoRow("Price", money(inv.num("price")))
                InfoRow("GST 18%", money(inv.num("gst_amount")))
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("Total", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.Text, modifier = Modifier.weight(1f))
                    Text(money(inv.num("total")), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.Brand)
                }
            }
        }
        r.num("credit_balance")?.let {
            Spacer(Modifier.height(8.dp))
            Text("Customer khata balance now: ${money(it)}", fontSize = 14.sp, color = C.Amber)
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Sell another", onClick = onAnother, modifier = Modifier.weight(1f))
            BigButton("Home", onClick = onHome, color = C.Gray, modifier = Modifier.weight(1f))
        }
    }
}
