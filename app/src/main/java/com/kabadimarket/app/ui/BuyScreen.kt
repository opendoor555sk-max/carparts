package com.kabadimarket.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.kabadimarket.app.Route
import com.kabadimarket.app.data.Api
import com.kabadimarket.app.data.ApiException
import com.kabadimarket.app.data.User
import com.kabadimarket.app.data.int
import com.kabadimarket.app.data.obj
import com.kabadimarket.app.data.str
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

val COMPANIES = listOf(
    "All", "Maruti Suzuki", "Hyundai", "Tata", "Mahindra", "Kia", "Toyota", "Honda",
    "Nissan", "Renault", "Ford", "Volkswagen", "Skoda", "MG", "Datsun", "Chevrolet",
    "Fiat", "Jeep", "Citroen", "Isuzu",
)
private val WALLS = listOf("Front", "Back", "Left", "Right")
private val SHELVES = listOf("Top", "Middle", "Bottom")

/** Buy = add one piece to stock. */
@Composable
fun BuyScreen(user: User, nav: Nav, initialPartNumber: String) {
    var pn by rememberSaveable { mutableStateOf(initialPartNumber) }
    var name by rememberSaveable { mutableStateOf("") }
    var company by rememberSaveable { mutableStateOf("All") }
    var category by rememberSaveable { mutableStateOf("") }
    var condition by rememberSaveable { mutableStateOf("Working") }
    var price by rememberSaveable { mutableStateOf("") }
    var barcode by rememberSaveable { mutableStateOf("") }
    var wall by rememberSaveable { mutableStateOf("") }
    var rack by rememberSaveable { mutableStateOf("") }
    var shelf by rememberSaveable { mutableStateOf("") }

    var lookup by remember { mutableStateOf<JSONObject?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var limitHit by remember { mutableStateOf<JSONObject?>(null) }
    var done by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()

    // When a part number is typed/scanned, look it up and fill in what we know.
    LaunchedEffect(pn) {
        lookup = null
        val q = pn.trim()
        if (q.length < 3) return@LaunchedEffect
        delay(700)
        try {
            val r = Api.getObj("/search", mapOf("q" to q))
            lookup = r
            val known = r.obj("part") ?: r.obj("known") ?: r.obj("catalog")
            if (known != null) {
                if (name.isBlank()) name = known.str("name")
                if (company == "All" && known.str("company") in COMPANIES) company = known.str("company").ifBlank { "All" }
                if (category.isBlank()) category = known.str("category")
            }
        } catch (_: ApiException) {
        }
    }

    fun reset() {
        pn = ""; name = ""; company = "All"; category = ""; condition = "Working"
        price = ""; barcode = ""; error = null; done = null; lookup = null
        // Rack / wall / shelf are kept: usually the next piece goes to the same place.
    }

    fun submit(override: Boolean) {
        val partNumber = pn.trim()
        if (partNumber.isEmpty()) {
            error = "Enter or scan the part number"
            return
        }
        val priceValue = price.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        if (price.isNotBlank() && priceValue == null) {
            error = "Price is not a valid number"
            return
        }
        val body = JSONObject()
            .put("part_number", partNumber)
            .put("company", company)
            .put("name", name.trim())
            .put("category", category.trim())
            .put("condition", condition)
            .put("barcode", barcode.trim())
            .put("override", override)
        if (priceValue != null) body.put("price", priceValue)
        if (wall.isNotBlank() || rack.isNotBlank() || shelf.isNotBlank()) {
            body.put(
                "assigned_location",
                JSONObject()
                    .put("wall", wall.ifBlank { JSONObject.NULL })
                    .put("rack_name", rack.trim().ifBlank { JSONObject.NULL })
                    .put("shelf_level", shelf.ifBlank { JSONObject.NULL })
                    .put("is_open_floor", false),
            )
        }
        saving = true
        error = null
        scope.launch {
            try {
                done = Api.post("/buy", body) as JSONObject
            } catch (e: ApiException) {
                if (e.code == "LIMIT_REACHED") limitHit = e.detail?.obj("limit") ?: JSONObject()
                else error = e.message
            } finally {
                saving = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(t("module.buy"), subtitle = "Purchase", onBack = { nav.back() })

        val result = done
        if (result != null) {
            BuyDone(
                result,
                onAnother = { reset() },
                onView = { nav.replace(Route.Part(result.obj("unit")?.str("part_number") ?: pn.trim())) },
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
                PartNumberField(pn, { pn = it })
                lookup?.let { r ->
                    val status = r.str("status")
                    val (fg, bg) = statusColors(status)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                        Badge(status, fg, bg)
                        Spacer(Modifier.padding(start = 8.dp))
                        Text("In stock: ${r.int("stock_count")}", fontSize = 13.sp, color = C.Muted)
                    }
                    r.obj("limit")?.takeIf { it.optBoolean("limit_enabled") }?.let { l ->
                        val ls = l.str("status")
                        val (lf, lb) = statusColors(ls)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                            Badge(if (ls == "STOP") "DO NOT BUY" else "Limit $ls", lf, lb)
                            Spacer(Modifier.padding(start = 8.dp))
                            Text("Left to buy: ${l.int("remaining")} of ${l.int("allowed_limit")}", fontSize = 13.sp, color = C.Muted)
                        }
                    }
                }
                Field(name, { name = it }, "Part name")
                Picker("Company", company, COMPANIES) { company = it }
                Field(category, { category = it }, "Category (e.g. Headlight, ECU)")
            }

            Spacer(Modifier.height(12.dp))
            Card {
                SectionTitle("Condition")
                ChipGroup(CONDITIONS, condition) { condition = it }
            }

            Spacer(Modifier.height(12.dp))
            Card {
                SectionTitle("Price & barcode")
                Field(price, { price = it }, "Buying price (₹) — optional", number = true)
                PartNumberField(barcode, { barcode = it }, label = "Barcode — optional")
            }

            Spacer(Modifier.height(12.dp))
            Card {
                SectionTitle("Where is it kept?")
                Text("Wall", fontSize = 13.sp, color = C.Muted)
                ChipGroup(WALLS, wall) { wall = if (wall == it) "" else it }
                Field(rack, { rack = it }, "Rack name / number", caps = true)
                Text("Shelf", fontSize = 13.sp, color = C.Muted)
                ChipGroup(SHELVES, shelf) { shelf = if (shelf == it) "" else it }
            }

            ErrorBox(error)
            Spacer(Modifier.height(12.dp))
            BigButton("📥 Add to stock", onClick = { submit(false) }, color = C.Green, loading = saving)
            Spacer(Modifier.height(32.dp))
        }
    }

    limitHit?.let { l ->
        AlertDialog(
            onDismissRequest = { limitHit = null },
            title = { Text("⛔ DO NOT BUY", color = C.Red, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Purchase limit reached for this part.\n\n" +
                        "Allowed: ${l.int("allowed_limit")}\nAlready in stock: ${l.int("existing_stock")}",
                )
            },
            confirmButton = {
                if (user.isAdmin) {
                    TextButton(onClick = { limitHit = null; submit(true) }) { Text("Buy anyway", color = C.Red) }
                }
            },
            dismissButton = { TextButton(onClick = { limitHit = null }) { Text("Don't buy") } },
        )
    }
}

@Composable
private fun BuyDone(r: JSONObject, onAnother: () -> Unit, onView: () -> Unit) {
    val unit = r.obj("unit")
    val limit = r.obj("limit")
    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Text("✅", fontSize = 56.sp)
        Text("Added to stock", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.Green)
        Spacer(Modifier.height(16.dp))
        Card {
            InfoRow("Part", unit?.str("part_number") ?: "")
            InfoRow("Condition", unit?.str("condition") ?: "")
            InfoRow("Location", locationText(unit?.obj("assigned_location")))
            limit?.let { InfoRow("Now in stock", it.int("existing_stock").toString()) }
            if (limit?.optBoolean("limit_enabled") == true) {
                InfoRow("Left to buy", limit.int("remaining").toString())
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Buy another", onClick = onAnother, color = C.Green, modifier = Modifier.weight(1f))
            BigButton("View part", onClick = onView, modifier = Modifier.weight(1f))
        }
    }
}
