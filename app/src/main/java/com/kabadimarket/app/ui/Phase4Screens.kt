package com.kabadimarket.app.ui

import android.app.DatePickerDialog
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.kabadimarket.app.Nav
import com.kabadimarket.app.data.Api
import com.kabadimarket.app.data.ApiException
import com.kabadimarket.app.data.Branding
import com.kabadimarket.app.data.I18n
import com.kabadimarket.app.data.Printer
import com.kabadimarket.app.data.Share
import com.kabadimarket.app.data.User
import com.kabadimarket.app.data.arr
import com.kabadimarket.app.data.int
import com.kabadimarket.app.data.num
import com.kabadimarket.app.data.obj
import com.kabadimarket.app.data.objects
import com.kabadimarket.app.data.str
import com.kabadimarket.app.data.strings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ============================================================
//  CASH BOOK (Rokad no hisab)
// ============================================================

@Composable
fun CashBookScreen(user: User, nav: Nav) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var balance by remember { mutableDoubleStateOf(0.0) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var type by rememberSaveable { mutableStateOf("in") }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        try {
            val res = Api.getObj("/cash-book")
            entries = res.arr("entries").objects()
            balance = res.num("balance") ?: 0.0
        } catch (e: ApiException) {
            Toast.error(e.message ?: t("cashBook.loadFailed"))
        } finally {
            loading = false
        }
    }

    Screen {
        TopBar(t("cashBook.title"), onBack = { nav.back() })
        if (loading) {
            Loading()
            return@Screen
        }
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.entrance(0)) {
                SectionTitle(t("cashBook.cashInHand"))
                val anim by animateFloatAsState(balance.toFloat(), tween(900), label = "cash")
                Text(money(anim.toDouble()), color = if (balance < 0) C.Red else C.Green, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            }
            Card(Modifier.entrance(1)) {
                SectionTitle(t("cashBook.addEntry"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton(t("cashBook.cashIn"), icon = Icons.Filled.ArrowCircleDown, color = C.Brand, outlined = type != "in",
                        modifier = Modifier.weight(1f), onClick = { type = "in" })
                    BigButton(t("cashBook.cashOut"), icon = Icons.Filled.ArrowCircleUp, color = C.Red, outlined = type != "out",
                        modifier = Modifier.weight(1f), onClick = { type = "out" })
                }
                Spacer(Modifier.height(10.dp))
                Field(amount, { amount = it }, t("cashBook.amount"), placeholder = "₹ 0", number = true)
                Field(note, { note = it }, t("common.noteOptional"), placeholder = t("cashBook.notePlaceholder"))
                BigButton(t("cashBook.save"), icon = Icons.Filled.CheckCircle, loading = saving, onClick = {
                    val amt = amount.trim().toDoubleOrNull()
                    if (amt == null || amt <= 0) {
                        Toast.error(t("cashBook.errAmount")); return@BigButton
                    }
                    saving = true
                    scope.launch {
                        try {
                            Api.post("/cash-book", JSONObject().put("type", type).put("amount", amt).put("note", note.trim()))
                            Toast.success(t("cashBook.added"))
                            amount = ""; note = ""
                            reload++
                        } catch (e: ApiException) {
                            Toast.error(e.message ?: t("cashBook.addFailed"))
                        } finally {
                            saving = false
                        }
                    }
                })
            }
            Card(Modifier.entrance(2)) {
                SectionTitle(t("cashBook.history"))
                if (entries.isEmpty()) EmptyState(Icons.Outlined.Wallet, t("cashBook.noEntries"))
                entries.forEach { e ->
                    val isIn = e.str("type") == "in"
                    val color = if (isIn) C.Green else C.Red
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Icon(if (isIn) Icons.Filled.ArrowCircleDown else Icons.Filled.ArrowCircleUp, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (isIn) t("cashBook.cashIn") else t("cashBook.cashOut"), color = C.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (e.str("note").isNotBlank()) Text(e.str("note"), color = C.Text2, fontSize = 12.sp)
                            Text("${dateTime(e.str("at"))} • ${e.str("by")}", color = C.Muted, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text((if (isIn) "+" else "−") + money(e.num("amount") ?: 0.0), color = color, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            Text("${t("cashBook.bal")}: ${money(e.num("running_balance") ?: 0.0)}", color = C.Muted, fontSize = 11.sp)
                            if (user.isAdmin) {
                                GhostButton(t("common.delete"), Icons.Filled.Delete) {
                                    scope.launch {
                                        try {
                                            Api.delete("/cash-book/${Api.seg(e.str("id"))}")
                                            Toast.success(t("cashBook.deleted"))
                                            reload++
                                        } catch (ex: ApiException) {
                                            Toast.error(ex.message ?: t("cashBook.deleteFailed"))
                                        }
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
//  DAMAGED / RETURNS
// ============================================================

private data class ReturnMeta(val icon: ImageVector, val color: Color)

private fun returnMeta(type: String) = when (type) {
    "customer_return" -> ReturnMeta(Icons.AutoMirrored.Filled.Undo, C.Brand)
    "damaged_stock" -> ReturnMeta(Icons.Filled.Warning, C.Red)
    else -> ReturnMeta(Icons.Filled.KeyboardReturn, C.Muted)
}

@Composable
fun DamagedReturnsScreen(nav: Nav) {
    var records by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var modal by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        try {
            records = Api.getArr("/returns").objects()
        } catch (_: ApiException) {
        } finally {
            loading = false
        }
    }

    Screen {
        TopBar(t("damagedReturns.title"), t("damagedReturns.subtitle"), onBack = { nav.back() })
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "customer_return" to t("damagedReturns.customerReturnShort"),
                "damaged_stock" to t("damagedReturns.markDamagedShort"),
                "vendor_return" to t("damagedReturns.vendorReturnShort"),
            ).forEachIndexed { i, (type, label) ->
                val m = returnMeta(type)
                Column(
                    Modifier.weight(1f).entrance(i).background(C.Card, RoundedCornerShape(12.dp)).border(1.dp, C.Line, RoundedCornerShape(12.dp))
                        .pressable { modal = type }.padding(vertical = 14.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(m.icon, contentDescription = null, tint = m.color, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(label, color = C.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
        }
        when {
            loading -> Loading()
            records.isEmpty() -> EmptyState(Icons.AutoMirrored.Outlined.Undo, t("damagedReturns.noRecords"), t("damagedReturns.noRecordsSub"))
            else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(records, key = { _, r -> r.str("id") }) { i, r ->
                    val m = returnMeta(r.str("type"))
                    Row(
                        Modifier.fillMaxWidth().entrance(i).background(C.Card, RoundedCornerShape(12.dp)).border(1.dp, C.Line, RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(38.dp).border(1.5.dp, m.color, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(m.icon, contentDescription = null, tint = m.color, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(r.str("part_number"), color = C.Text, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                Text(t("damagedReturns.type.${r.str("type")}"), color = m.color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(dateTime(r.str("at")) + (if (r.str("condition").isNotBlank()) " • ${r.str("condition")}" else ""), color = C.Muted, fontSize = 12.sp)
                            if (r.str("note").isNotBlank()) Text(r.str("note"), color = C.Text2, fontSize = 12.sp, maxLines = 2)
                        }
                    }
                }
            }
        }
    }

    modal?.let { type ->
        ReturnDialog(type, onClose = { modal = null }, onSaved = { modal = null; reload++ })
    }
}

/** Search box that finds customers or vendors (old app's usePicker). */
@Composable
private fun PickerField(endpoint: String, label: String, onPicked: (JSONObject?) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var picked by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (picked || query.isBlank()) {
            results = emptyList(); return@LaunchedEffect
        }
        delay(300)
        results = try {
            Api.getArr(endpoint, mapOf("q" to query.trim())).objects()
        } catch (_: ApiException) {
            emptyList()
        }
    }
    Field(query, { query = it; picked = false; onPicked(null) }, label, placeholder = t("customers.searchPlaceholder"))
    results.take(5).forEach { r ->
        Text(
            "${r.str("name")} · ${r.str("phone")}", color = C.Text, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).background(C.Bg, RoundedCornerShape(8.dp))
                .pressable { picked = true; query = r.str("name"); results = emptyList(); onPicked(r) }.padding(10.dp),
        )
    }
}

@Composable
private fun ReturnDialog(type: String, onClose: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pn by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("good") }
    var customer by remember { mutableStateOf<JSONObject?>(null) }
    var vendor by remember { mutableStateOf<JSONObject?>(null) }
    var saving by remember { mutableStateOf(false) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        androidx.compose.material3.Surface(shape = RoundedCornerShape(20.dp), color = C.Card) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()).imePadding()) {
                Text(t("damagedReturns.type.$type"), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = C.Text, modifier = Modifier.padding(bottom = 10.dp))
                PartNumberField(pn, { pn = it })
                when (type) {
                    "customer_return" -> {
                        Text(t("buy.condition"), color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.padding(vertical = 6.dp)) {
                            Chip(t("damagedReturns.goodReusable"), condition == "good") { condition = "good" }
                            Chip(I18n.status("Damaged"), condition == "damaged") { condition = "damaged" }
                        }
                        PickerField("/customers", t("damagedReturns.customerOptional")) { customer = it }
                        Field(note, { note = it }, t("common.noteOptional"), placeholder = t("damagedReturns.conditionNote"), multiline = true)
                    }
                    "damaged_stock" -> Field(note, { note = it }, t("common.noteOptional"), placeholder = t("damagedReturns.whatHappened"), multiline = true)
                    else -> {
                        PickerField("/vendors", t("damagedReturns.vendorOptional")) { vendor = it }
                        Field(note, { note = it }, t("damagedReturns.reasonOptional"), placeholder = t("damagedReturns.whyGoingBack"), multiline = true)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    BigButton(t("ui.cancel"), outlined = true, modifier = Modifier.weight(1f), onClick = onClose)
                    BigButton(t("damagedReturns.save"), loading = saving, modifier = Modifier.weight(1f), onClick = {
                        if (pn.isBlank()) {
                            Toast.error(t("common.errPartNumberRequired")); return@BigButton
                        }
                        saving = true
                        scope.launch {
                            try {
                                when (type) {
                                    "customer_return" -> {
                                        Api.post(
                                            "/returns/customer",
                                            JSONObject().put("part_number", pn.trim()).put("condition", condition)
                                                .put("customer_id", customer?.str("id") ?: JSONObject.NULL).put("note", note.trim()),
                                        )
                                        Toast.success(if (condition == "good") t("damagedReturns.returnGood") else t("damagedReturns.returnDamaged"))
                                    }
                                    "damaged_stock" -> {
                                        Api.post("/returns/damaged", JSONObject().put("part_number", pn.trim()).put("note", note.trim()))
                                        Toast.success(t("damagedReturns.markedDamaged"))
                                    }
                                    else -> {
                                        Api.post(
                                            "/returns/vendor",
                                            JSONObject().put("part_number", pn.trim()).put("vendor_id", vendor?.str("id") ?: JSONObject.NULL).put("reason", note.trim()),
                                        )
                                        Toast.success(t("damagedReturns.vendorReturnRecorded"))
                                    }
                                }
                                onSaved()
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
    }
}

// ============================================================
//  Date range helpers (shared by Reports and Profit)
// ============================================================

private val isoFmt get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private fun iso(c: Calendar): String = isoFmt.format(c.time)
private fun todayIso(): String = iso(Calendar.getInstance())

private fun resolveRange(range: String, fromDate: String, toDate: String): Pair<String?, String?> {
    val now = Calendar.getInstance()
    return when (range) {
        "today" -> todayIso() to todayIso()
        "week" -> {
            val start = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY) }
            iso(start) to todayIso()
        }
        "month" -> iso(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }) to todayIso()
        "year" -> iso(Calendar.getInstance().apply { set(Calendar.DAY_OF_YEAR, 1) }) to iso(now)
        "custom" -> Pair(fromDate, toDate)
        else -> null to null
    }
}

@Composable
private fun DateButton(value: String, onPick: (String) -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.border(1.dp, C.Brand, RoundedCornerShape(8.dp)).pressable {
            val c = Calendar.getInstance()
            runCatching { isoFmt.parse(value) }.getOrNull()?.let { c.time = it }
            DatePickerDialog(context, { _, y, m, d ->
                onPick(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = C.Brand, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(value, color = C.Brand, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun RangeFilter(
    ranges: List<Pair<String, String>>,
    range: String,
    onRange: (String) -> Unit,
    fromDate: String,
    toDate: String,
    onFrom: (String) -> Unit,
    onTo: (String) -> Unit,
    onApply: () -> Unit,
    label: String,
) {
    Text(label, color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp)) {
        ranges.forEach { (key, lbl) -> Chip(t(lbl), range == key) { onRange(key) } }
    }
    if (range == "custom") {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            DateButton(fromDate, onFrom)
            Text(t("common.to"), color = C.Muted, modifier = Modifier.padding(horizontal = 8.dp))
            DateButton(toDate, onTo)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.background(C.Brand, RoundedCornerShape(8.dp)).pressable(onApply).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(t("common.apply"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

// ============================================================
//  REPORTS: Stock / Purchases / Sales
// ============================================================

@Composable
fun ReportScreen(user: User, nav: Nav, mode: String) {
    val context = LocalContext.current
    val showPrice = user.can("view_price")
    val title = t(
        when (mode) {
            "sell" -> "report.sales"
            "stock" -> "report.stockReport"
            else -> "report.purchases"
        },
    )
    var items by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var range by rememberSaveable { mutableStateOf("all") }
    var company by rememberSaveable { mutableStateOf("All") }
    var category by rememberSaveable { mutableStateOf("All") }
    var fromDate by rememberSaveable { mutableStateOf(todayIso()) }
    var toDate by rememberSaveable { mutableStateOf(todayIso()) }
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
        if (range == "custom" && apply == 0) return@LaunchedEffect
        loading = true
        try {
            val (f, tt) = resolveRange(range, fromDate, toDate)
            val q = mapOf("date_from" to f, "date_to" to tt, "company" to company.takeIf { it != "All" }, "category" to category.takeIf { it != "All" })
            items = if (mode == "stock") Api.getArr("/inventory", q).objects()
            else Api.getArr("/transactions", q + ("type" to mode)).objects()
        } catch (e: ApiException) {
            Toast.error(e.message ?: t("common.loadFailed"))
        } finally {
            loading = false
        }
    }

    val rows = remember(items) {
        val out = mutableListOf<Triple<String, String, JSONObject?>>() // kind, label, item
        items.groupBy { it.str("company").ifBlank { "All" } }.toSortedMap().forEach { (co, byCo) ->
            out.add(Triple("company", co, null))
            byCo.groupBy { it.str("category").ifBlank { t("report.uncategorized") } }.toSortedMap().forEach { (cat, list) ->
                out.add(Triple("category", "$cat|${list.size}", null))
                list.forEach { out.add(Triple("item", "", it)) }
            }
        }
        out
    }
    val total = items.sumOf { it.num("price") ?: 0.0 }

    Screen {
        TopBar(title, user.storeName, onBack = { nav.back() }, actions = {
            if (items.isNotEmpty()) {
                Icon(Icons.Filled.Print, contentDescription = null, tint = C.Brand, modifier = Modifier.pressable {
                    Printer.print(context, Printer.reportHtml(Branding.from(user), title, items, showPrice && mode != "stock"), title)
                })
            }
        })
        Column(Modifier.background(C.Card).padding(bottom = 8.dp)) {
            RangeFilter(
                listOf("all" to "common.all", "month" to "common.thisMonth", "year" to "report.thisYear", "today" to "common.today", "custom" to "common.custom"),
                range, { range = it }, fromDate, toDate, { fromDate = it }, { toDate = it }, { apply++ }, t("report.date"),
            )
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
            items.isEmpty() -> EmptyState(Icons.Outlined.Description, t("report.nothingFound"), t("report.tryAnother"))
            else -> {
                Row(Modifier.fillMaxWidth().background(C.BrandFaint).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("${items.size} ${t("report.itemsSuffix")}", color = C.OnBrandFaint, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (showPrice && mode != "stock") Text("${t("report.totalPrefix")}${Printer.fmtNum(total)}", color = C.OnBrandFaint, fontWeight = FontWeight.ExtraBold)
                }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(rows) { i, (kind, label, it0) ->
                        when (kind) {
                            "company" -> Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Business, contentDescription = null, tint = C.Brand, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(label, color = C.Text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            }
                            "category" -> {
                                val (cat, count) = label.split("|").let { it[0] to it.getOrElse(1) { "" } }
                                Text("$cat ($count)", color = C.Muted, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                            else -> {
                                val x = it0 ?: return@itemsIndexed
                                Row(
                                    Modifier.fillMaxWidth().entrance(i.coerceAtMost(12)).background(C.Card, RoundedCornerShape(10.dp)).border(1.dp, C.Line, RoundedCornerShape(10.dp)).padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(x.str("part_number"), color = C.Text, fontWeight = FontWeight.ExtraBold)
                                        if (x.str("part_name").isNotBlank()) Text(x.str("part_name"), color = C.Text2, fontSize = 13.sp)
                                        val date = x.str("at").ifBlank { x.str("created_at") }
                                        Text(shortDate(date) + (if (x.str("buyer").isNotBlank()) "  •  ${x.str("buyer")}" else ""), color = C.Muted, fontSize = 12.sp)
                                    }
                                    if (x.str("condition").isNotBlank()) StatusChip(x.str("condition"))
                                    val p = x.num("price")
                                    if (showPrice && p != null) Text("Rs.${Printer.fmtNum(p)}", color = C.Brand, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
//  PROFIT / MARGIN
// ============================================================

@Composable
fun ProfitReportScreen(nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var range by rememberSaveable { mutableStateOf("month") }
    var fromDate by rememberSaveable { mutableStateOf(todayIso()) }
    var toDate by rememberSaveable { mutableStateOf(todayIso()) }
    var partFilter by rememberSaveable { mutableStateOf("") }
    var report by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var exporting by remember { mutableStateOf(false) }
    var apply by remember { mutableIntStateOf(0) }

    fun params(): Map<String, String?> {
        val (f, tt) = resolveRange(range, fromDate, toDate)
        return mapOf("date_from" to f, "date_to" to tt, "part_number" to partFilter.trim())
    }

    LaunchedEffect(range, apply) {
        if (range == "custom" && apply == 0) return@LaunchedEffect
        loading = true
        try {
            report = Api.getObj("/reports/profit", params())
        } catch (e: ApiException) {
            Toast.error(e.message ?: t("common.loadFailed"))
            report = null
        } finally {
            loading = false
        }
    }

    Screen {
        TopBar(t("profitReport.title"), t("profitReport.subtitle"), onBack = { nav.back() }, actions = {
            if (exporting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Filled.Download, contentDescription = null, tint = C.Brand, modifier = Modifier.pressable {
                exporting = true
                scope.launch {
                    try {
                        Share.file(context, Api.download("/reports/profit/excel", params()), "profit_report.xlsx", Share.XLSX)
                    } catch (e: ApiException) {
                        Toast.error(e.message ?: t("common.exportFailed"))
                    } finally {
                        exporting = false
                    }
                }
            })
        })
        Column(Modifier.background(C.Card).padding(bottom = 8.dp)) {
            RangeFilter(
                listOf("today" to "common.today", "week" to "common.thisWeek", "month" to "common.thisMonth", "all" to "common.all", "custom" to "common.custom"),
                range, { range = it }, fromDate, toDate, { fromDate = it }, { toDate = it }, { apply++ }, t("profitReport.dateRange"),
            )
            Text(t("profitReport.partNumberOptional"), color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
            Box(Modifier.padding(horizontal = 16.dp)) {
                Field(partFilter, { partFilter = it }, t("profitReport.filterToPart"), caps = true,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search, onIme = { apply++ })
            }
        }
        val r = report
        val s = r?.obj("summary")
        when {
            loading -> Loading()
            s == null || s.int("units_sold") == 0 -> EmptyState(Icons.Outlined.TrendingUp, t("profitReport.noSales"), t("profitReport.tryWiderRange"))
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Card(Modifier.entrance(0)) {
                        SectionTitle(t("profitReport.summary"))
                        Row(Modifier.fillMaxWidth()) {
                            SummaryStat(t("profitReport.revenue"), s.num("total_revenue") ?: 0.0, C.Text, Modifier.weight(1f))
                            SummaryStat(t("profitReport.cost"), s.num("total_cost") ?: 0.0, C.Text, Modifier.weight(1f))
                            val profit = s.num("total_profit") ?: 0.0
                            SummaryStat(t("profitReport.profit"), profit, if (profit >= 0) C.Green else C.Red, Modifier.weight(1f), big = true)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("${s.int("units_sold")} ${t("profitReport.unitsSoldSuffix")}", color = C.Text2, fontSize = 13.sp)
                        if (s.int("units_with_unknown_cost") > 0) {
                            Text("⚠ ${s.int("units_with_unknown_cost")} ${t("profitReport.noCostWarningSuffix")}", color = C.Amber, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SectionTitle(t("profitReport.byPartHeader"))
                }
                itemsIndexed(r.arr("by_part").objects(), key = { _, p -> p.str("part_number") }) { i, p ->
                    val unknownAll = p.int("unknown_cost_units") == p.int("units_sold")
                    val profit = p.num("profit") ?: 0.0
                    Row(
                        Modifier.fillMaxWidth().entrance(i + 1).background(C.Card, RoundedCornerShape(12.dp)).border(1.dp, C.Line, RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.str("part_number"), color = C.Text, fontWeight = FontWeight.ExtraBold)
                            if (p.str("part_name").isNotBlank()) Text(p.str("part_name"), color = C.Text2, fontSize = 13.sp)
                            Text(
                                "${p.int("units_sold")} ${t("profitReport.soldSuffix")} • ${t("profitReport.rev")} ${money(p.num("revenue") ?: 0.0)} • ${t("profitReport.cost")} " +
                                    (if (unknownAll) t("profitReport.unknownCost") else money(p.num("cost") ?: 0.0)),
                                color = C.Muted, fontSize = 12.sp,
                            )
                            if (p.int("unknown_cost_units") > 0) Text("${p.int("unknown_cost_units")} ${t("profitReport.unknownCostUnitsSuffix")}", color = C.Amber, fontSize = 11.sp)
                        }
                        Text(if (unknownAll) "—" else money(profit), color = if (profit >= 0) C.Green else C.Red, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: Double, color: Color, modifier: Modifier, big: Boolean = false) {
    val anim by animateFloatAsState(value.toFloat(), tween(900), label = "stat")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = C.Muted, fontSize = 12.sp)
        Text(money(anim.toDouble()), color = color, fontSize = if (big) 18.sp else 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}
