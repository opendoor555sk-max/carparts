package com.kabadimarket.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabadimarket.app.Nav
import com.kabadimarket.app.Route
import com.kabadimarket.app.data.Api
import com.kabadimarket.app.data.ApiException
import com.kabadimarket.app.data.I18n
import com.kabadimarket.app.data.Share
import com.kabadimarket.app.data.User
import com.kabadimarket.app.data.arr
import com.kabadimarket.app.data.int
import com.kabadimarket.app.data.num
import com.kabadimarket.app.data.obj
import com.kabadimarket.app.data.objects
import com.kabadimarket.app.data.scanBarcode
import com.kabadimarket.app.data.str
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.abs

// ============================================================
//  Section not rebuilt yet
// ============================================================

@Composable
fun SoonScreen(nav: Nav, titleKey: String) {
    val title = I18n.t(titleKey).let { if (it == titleKey) I18n.x(titleKey) else it }
    Screen {
        TopBar(title, onBack = { nav.back() })
        EmptyState(Icons.Filled.Construction, I18n.x("soon.title"), I18n.x("soon.sub")) {
            BigButton(t("common.back"), onClick = { nav.back() }, modifier = Modifier.width(180.dp))
        }
    }
}

// ============================================================
//  TOOLS (same 8 items as old app)
// ============================================================

@Composable
fun ToolsScreen(user: User, nav: Nav) {
    data class Tool(val title: String, val sub: String, val icon: ImageVector, val perm: String?, val go: () -> Unit)
    val tools = listOf(
        Tool("admin.toolAiApprovals", "admin.linkAiApprovalsSub", Icons.Filled.AutoAwesome, "ai_approve") { nav.open(Route.Soon("admin.toolAiApprovals")) },
        Tool("admin.toolSearchSetup", "admin.linkGoogleSearchSub", Icons.Filled.Key, "search") { nav.open(Route.Soon("admin.toolSearchSetup")) },
        Tool("admin.toolBuyLimit", "admin.linkPurchaseLimitsSub", Icons.Filled.Speed, "manage_limits") { nav.open(Route.Limits) },
        Tool("admin.toolDemand", "admin.linkDemandSearchSub", Icons.Filled.TrendingUp, "view_stats") { nav.open(Route.Soon("admin.toolDemand")) },
        Tool("admin.toolStockVerify", "admin.linkStockVerifySub", Icons.Filled.Assignment, null) { nav.open(Route.Soon("admin.toolStockVerify")) },
        Tool("admin.toolUnlinked", "admin.linkUnlinkedStockSub", Icons.Filled.Warning, null) { nav.open(Route.Soon("admin.toolUnlinked")) },
        Tool("admin.toolPsHistory", "admin.toolBulkDelete", Icons.Filled.Receipt, null) { nav.open(Route.Soon("admin.toolPsHistory")) },
        Tool("admin.toolSearchLogs", "admin.linkSearchLogsSub", Icons.Filled.History, null) { nav.open(Route.Soon("admin.toolSearchLogs")) },
    ).filter { if (it.perm != null) user.can(it.perm) else user.isAdmin }

    Screen {
        TopBar(t("admin.toolsTitle"), t("admin.linkToolsSub"), onBack = { nav.back() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tools.forEachIndexed { i, tool ->
                LinkRow(tool.icon, t(tool.title), t(tool.sub), modifier = Modifier.entrance(i)) { tool.go() }
            }
        }
    }
}

// ============================================================
//  PURCHASE LIMITS (same as old limits.tsx)
// ============================================================

@Composable
fun LimitsScreen(nav: Nav) {
    val scope = rememberCoroutineScope()
    var globalEnabled by rememberSaveable { mutableStateOf(false) }
    var globalDefault by rememberSaveable { mutableStateOf("") }
    var savingGlobal by remember { mutableStateOf(false) }

    var partNumber by rememberSaveable { mutableStateOf("") }
    var partLimit by rememberSaveable { mutableStateOf("") }
    var partEnabled by rememberSaveable { mutableStateOf(true) }
    var savingPart by remember { mutableStateOf(false) }
    var computed by remember { mutableStateOf<JSONObject?>(null) }

    var lowPn by rememberSaveable { mutableStateOf("") }
    var lowThreshold by rememberSaveable { mutableStateOf("") }
    var lowEnabled by rememberSaveable { mutableStateOf(true) }
    var savingLow by remember { mutableStateOf(false) }
    var lowComputed by remember { mutableStateOf<JSONObject?>(null) }

    LaunchedEffect(Unit) {
        try {
            val g = Api.getObj("/limits/global")
            globalEnabled = g.optBoolean("global_enabled")
            globalDefault = if (g.isNull("global_default")) "" else g.optInt("global_default").toString()
        } catch (_: ApiException) {
        }
    }

    Screen {
        TopBar(t("limits.title"), t("limits.subtitle"), onBack = { nav.back() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoNote(t("limits.infoText"))

            Card(Modifier.entrance(0)) {
                SectionTitle(t("limits.globalDefault"))
                ToggleRow(t("limits.enableGlobal"), globalEnabled) { globalEnabled = it }
                Field(globalDefault, { globalDefault = it.filter(Char::isDigit) }, t("limits.defaultMax"), placeholder = "e.g. 5", number = true)
                BigButton(t("limits.saveGlobal"), icon = Icons.Filled.Save, loading = savingGlobal, onClick = {
                    savingGlobal = true
                    scope.launch {
                        try {
                            val body = JSONObject().put("global_enabled", globalEnabled)
                                .put("global_default", globalDefault.toIntOrNull() ?: JSONObject.NULL)
                            Api.post("/limits/global", body)
                            Toast.success(t("limits.globalSaved"))
                        } catch (e: ApiException) {
                            Toast.error(e.message ?: t("common.failed"))
                        } finally {
                            savingGlobal = false
                        }
                    }
                })
            }

            Card(Modifier.entrance(1)) {
                SectionTitle(t("limits.perPartLimit"))
                PartNumberField(partNumber, { partNumber = it })
                Field(partLimit, { partLimit = it.filter(Char::isDigit) }, t("limits.limitBlank"), placeholder = "e.g. 3", number = true)
                ToggleRow(t("limits.enableThisLimit"), partEnabled) { partEnabled = it }
                BigButton(t("limits.savePartLimit"), icon = Icons.Filled.Save, outlined = true, loading = savingPart, onClick = {
                    if (partNumber.isBlank()) {
                        Toast.error(t("common.errPartNumberRequired")); return@BigButton
                    }
                    savingPart = true
                    scope.launch {
                        try {
                            val body = JSONObject().put("part_number", partNumber.trim())
                                .put("limit", partLimit.toIntOrNull() ?: JSONObject.NULL)
                                .put("enabled", partEnabled)
                            computed = Api.post("/limits/part", body) as? JSONObject
                            Toast.success(t("limits.partSaved"))
                        } catch (e: ApiException) {
                            Toast.error(e.message ?: t("common.saveFailed"))
                        } finally {
                            savingPart = false
                        }
                    }
                })
                computed?.let { c ->
                    Spacer(Modifier.height(12.dp))
                    LimitBar(c.int("existing_stock"), if (c.isNull("allowed_limit")) null else c.int("allowed_limit"))
                    Text("${t("limits.status")}: ${I18n.status(c.str("status"))}", color = C.Text2, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                }
            }

            Card(Modifier.entrance(2)) {
                SectionTitle(t("limits.lowStockAlert"))
                PartNumberField(lowPn, { lowPn = it })
                Field(lowThreshold, { lowThreshold = it.filter(Char::isDigit) }, t("limits.alertWhen"), placeholder = "e.g. 2", number = true)
                ToggleRow(t("limits.enableThisAlert"), lowEnabled) { lowEnabled = it }
                BigButton(t("limits.saveLowStockAlert"), icon = Icons.Filled.Error, outlined = true, loading = savingLow, onClick = {
                    if (lowPn.isBlank()) {
                        Toast.error(t("common.errPartNumberRequired")); return@BigButton
                    }
                    savingLow = true
                    scope.launch {
                        try {
                            val body = JSONObject().put("part_number", lowPn.trim())
                                .put("threshold", lowThreshold.toIntOrNull() ?: JSONObject.NULL)
                                .put("enabled", lowEnabled)
                            lowComputed = Api.post("/limits/low-stock", body) as? JSONObject
                            Toast.success(t("limits.lowStockSaved"))
                        } catch (e: ApiException) {
                            Toast.error(e.message ?: t("common.saveFailed"))
                        } finally {
                            savingLow = false
                        }
                    }
                })
                lowComputed?.let { c ->
                    val low = c.optBoolean("low")
                    val text = buildString {
                        append("${c.int("stock_count")} ${t("storeDetail.inStock").lowercase()}")
                        if (!c.isNull("low_stock_threshold")) append(" • ${t("limits.alertAt")} ≤ ${c.int("low_stock_threshold")}")
                        if (low) append(" • ${t("limits.lowStockNow")}")
                    }
                    Text(text, color = if (low) C.Red else C.Text2, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Animated bar: how much of the allowed stock is already used. */
@Composable
fun LimitBar(existing: Int, allowed: Int?) {
    val ratio = if (allowed == null || allowed <= 0) 0f else (existing.toFloat() / allowed).coerceIn(0f, 1f)
    val anim by animateFloatAsState(ratio, tween(800), label = "limit")
    val color = when {
        allowed == null -> C.Muted
        existing >= allowed -> C.Red
        ratio >= 0.8f -> C.Amber
        else -> C.Green
    }
    Column {
        Row {
            Text("${t("common.stock")}: $existing", color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (allowed == null) "∞" else "/ $allowed", color = C.Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { anim },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            color = color,
            trackColor = C.Surface3,
        )
    }
}

// ============================================================
//  CUSTOMERS — Grahak Khata
// ============================================================

@Composable
fun CustomersScreen(nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by rememberSaveable { mutableStateOf("") }
    var list by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(q) {
        delay(300)
        try {
            list = Api.getArr("/customers", mapOf("q" to q.trim())).objects()
        } catch (e: ApiException) {
            Toast.error(e.message ?: t("common.loadFailed"))
        } finally {
            loading = false
        }
    }

    fun lookupByPhone(phone: String) {
        scope.launch {
            try {
                val matches = Api.getArr("/customers", mapOf("phone" to phone)).objects()
                if (matches.isNotEmpty()) nav.open(Route.Customer(matches[0].str("id")))
                else Toast.show("${t("customers.noCustomerFound")} $phone")
            } catch (e: ApiException) {
                Toast.error(e.message ?: t("customers.lookupFailed"))
            }
        }
    }

    Screen {
        TopBar(t("customers.title"), t("customers.subtitle"), onBack = { nav.back() })
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.Top) {
            Field(q, { q = it }, t("customers.searchPlaceholder"), modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            SquareIconButton(Icons.Filled.QrCodeScanner) { scanBarcode(context) { lookupByPhone(it.trim()) } }
            Spacer(Modifier.width(8.dp))
            SquareIconButton(Icons.Filled.PersonAdd) { nav.open(Route.CustomerNew) }
        }
        when {
            loading -> Loading()
            list.isEmpty() -> EmptyState(
                Icons.Outlined.People, t("customers.empty"),
                if (q.isNotBlank()) "${t("common.nothingFoundFor")} \"$q\"" else t("customers.emptySub"),
            ) { BigButton(t("customers.add"), icon = Icons.Filled.PersonAdd, onClick = { nav.open(Route.CustomerNew) }, modifier = Modifier.width(220.dp)) }
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(list, key = { _, c -> c.str("id") }) { i, c ->
                    val bal = c.num("balance") ?: 0.0
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .entrance(i)
                            .background(C.Card, RoundedCornerShape(12.dp))
                            .border(1.dp, C.Line, RoundedCornerShape(12.dp))
                            .pressable { nav.open(Route.Customer(c.str("id"))) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(Icons.Filled.Person)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.str("name"), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(c.str("phone"), color = C.Muted, fontSize = 13.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                when {
                                    bal > 0 -> t("customers.owes")
                                    bal < 0 -> t("customers.advance")
                                    else -> t("customers.settled")
                                },
                                color = C.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            )
                            Text(money(abs(bal)), color = balanceColor(bal), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = C.Muted)
                    }
                }
            }
        }
    }
}

private fun balanceColor(bal: Double): Color = when {
    bal > 0 -> C.Red
    bal < 0 -> C.Green
    else -> C.Muted
}

@Composable
private fun Avatar(icon: ImageVector) {
    Box(Modifier.size(42.dp).background(C.BrandFaint, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = C.Brand, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SquareIconButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(top = 8.dp)
            .size(56.dp)
            .background(C.Brand, RoundedCornerShape(12.dp))
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp)) }
}

@Composable
fun CustomerNewScreen(nav: Nav) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    Screen {
        TopBar(t("customers.newTitle"), t("customers.subtitle"), onBack = { nav.back() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp)) {
            Card(Modifier.entrance()) {
                Field(name, { name = it }, t("common.name"), placeholder = t("customers.namePlaceholder"))
                Field(phone, { phone = it }, t("common.phone"), placeholder = t("customers.phonePlaceholder"), phone = true)
                Field(address, { address = it }, t("common.addressOptional"), placeholder = t("common.address"), multiline = true)
            }
            Spacer(Modifier.height(12.dp))
            BigButton(t("customers.save"), icon = Icons.Filled.CheckCircle, loading = saving, onClick = {
                if (name.isBlank() || phone.isBlank()) {
                    Toast.error(t("customers.errRequired")); return@BigButton
                }
                saving = true
                scope.launch {
                    try {
                        val c = Api.post(
                            "/customers",
                            JSONObject().put("name", name.trim()).put("phone", phone.trim()).put("address", address.trim()),
                        ) as JSONObject
                        Toast.success(t("customers.added"))
                        nav.replace(Route.Customer(c.str("id")))
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

@Composable
fun CustomerDetailScreen(nav: Nav, customerId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var customer by remember { mutableStateOf<JSONObject?>(null) }
    var entries by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var balance by remember { mutableStateOf(0.0) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }

    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var savingEdit by remember { mutableStateOf(false) }

    var showPay by remember { mutableStateOf(false) }
    var payAmount by remember { mutableStateOf("") }
    var payNote by remember { mutableStateOf("") }
    var paying by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        try {
            val res = Api.getObj("/customers/${Api.seg(customerId)}/ledger")
            val c = res.obj("customer")
            customer = c
            entries = res.arr("entries").objects()
            balance = res.num("balance") ?: 0.0
            if (c != null) {
                name = c.str("name"); phone = c.str("phone"); address = c.str("address")
            }
        } catch (e: ApiException) {
            Toast.error(e.message ?: t("customers.loadFailed"))
        } finally {
            loading = false
        }
    }

    val c = customer
    if (loading || c == null) {
        Screen {
            TopBar(t("customers.detailTitle"), onBack = { nav.back() })
            if (loading) Loading() else EmptyState(Icons.Outlined.PersonOff, t("customers.notFound"))
        }
        return
    }

    Screen {
        TopBar(c.str("name"), c.str("phone"), onBack = { nav.back() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Balance
            Card(Modifier.entrance(0)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(t("customers.balance"), Modifier.weight(1f))
                    GhostButton(if (editing) t("ui.cancel") else t("common.edit"), if (editing) Icons.Filled.Close else Icons.Filled.Edit) { editing = !editing }
                }
                val animBal by animateFloatAsState(abs(balance).toFloat(), tween(900), label = "bal")
                Text(
                    money(animBal.toDouble()),
                    color = when {
                        balance > 0 -> C.Red
                        balance < 0 -> C.Green
                        else -> C.Text
                    },
                    fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    when {
                        balance > 0 -> t("customers.owedToStore")
                        balance < 0 -> t("customers.storeOwes")
                        else -> t("customers.settledNoDues")
                    },
                    color = C.Muted, fontSize = 13.sp,
                )
            }

            // Edit / address
            AnimatedVisibility(editing, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Card {
                    SectionTitle(t("customers.editCustomer"))
                    Field(name, { name = it }, t("common.name"), placeholder = t("customers.namePlaceholder"))
                    Field(phone, { phone = it }, t("common.phone"), placeholder = t("common.phoneNumber"), phone = true)
                    Field(address, { address = it }, t("common.address"), multiline = true)
                    BigButton(t("common.saveChanges"), icon = Icons.Filled.CheckCircle, loading = savingEdit, onClick = {
                        if (name.isBlank() || phone.isBlank()) {
                            Toast.error(t("customers.errRequired")); return@BigButton
                        }
                        savingEdit = true
                        scope.launch {
                            try {
                                Api.patch(
                                    "/customers/${Api.seg(customerId)}",
                                    JSONObject().put("name", name.trim()).put("phone", phone.trim()).put("address", address.trim()),
                                )
                                Toast.success(t("customers.updated"))
                                editing = false
                                reload++
                            } catch (e: ApiException) {
                                Toast.error(e.message ?: t("common.updateFailed"))
                            } finally {
                                savingEdit = false
                            }
                        }
                    })
                }
            }
            if (!editing && c.str("address").isNotBlank()) {
                Card {
                    SectionTitle(t("common.address"))
                    Text(c.str("address"), color = C.Text2, fontSize = 14.sp)
                }
            }

            // Record payment
            Card(Modifier.entrance(1).animateContentSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(t("customers.recordPayment"), Modifier.weight(1f))
                    if (!showPay) {
                        GhostButton(t("customers.recordPayment"), Icons.Filled.Payments, filled = true) { showPay = true }
                    }
                }
                if (showPay) {
                    Field(payAmount, { payAmount = it }, t("customers.amountReceived"), placeholder = "₹ 0", number = true)
                    Field(payNote, { payNote = it }, t("common.noteOptional"), placeholder = "e.g. Cash, UPI")
                    BigButton(t("customers.confirmPayment"), icon = Icons.Filled.CheckCircle, loading = paying, onClick = {
                        val amt = payAmount.trim().toDoubleOrNull()
                        if (amt == null || amt <= 0) {
                            Toast.error(t("customers.errPaymentAmount")); return@BigButton
                        }
                        paying = true
                        scope.launch {
                            try {
                                Api.post(
                                    "/customers/${Api.seg(customerId)}/payments",
                                    JSONObject().put("amount", amt).put("note", payNote.trim()),
                                )
                                Toast.success(t("customers.paymentRecorded"))
                                payAmount = ""; payNote = ""; showPay = false
                                reload++
                            } catch (e: ApiException) {
                                Toast.error(e.message ?: t("customers.paymentFailed"))
                            } finally {
                                paying = false
                            }
                        }
                    })
                }
            }

            // Ledger
            Card(Modifier.entrance(2)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(t("customers.transactionHistory"), Modifier.weight(1f))
                    if (entries.isNotEmpty()) {
                        GhostButton(t("common.export"), Icons.Filled.Download, loading = exporting) {
                            exporting = true
                            scope.launch {
                                try {
                                    val bytes = Api.download("/customers/${Api.seg(customerId)}/ledger/excel")
                                    Share.file(context, bytes, "ledger_${c.str("name").replace(Regex("\\s+"), "_")}.xlsx", Share.XLSX)
                                } catch (e: ApiException) {
                                    Toast.error(e.message ?: t("common.exportFailed"))
                                } finally {
                                    exporting = false
                                }
                            }
                        }
                    }
                }
                if (entries.isEmpty()) {
                    Text(t("customers.noTransactions"), color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
                }
                entries.forEach { e ->
                    val sale = e.str("type") == "sale"
                    val color = if (sale) C.Red else C.Green
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Icon(if (sale) Icons.Filled.ArrowCircleUp else Icons.Filled.ArrowCircleDown, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            val pn = e.str("part_number")
                            Text(
                                if (sale) t("customers.sale") + (if (pn.isNotBlank()) " — $pn" else "") else t("customers.paymentReceived"),
                                color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            )
                            if (e.str("note").isNotBlank()) Text(e.str("note"), color = C.Text2, fontSize = 12.sp)
                            Text("${dateTime(e.str("at"))} • ${e.str("by")}", color = C.Muted, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text((if (sale) "+" else "−") + money(e.num("amount") ?: 0.0), color = color, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                            Text("${t("customers.bal")}: ${money(e.num("running_balance") ?: 0.0)}", color = C.Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Small text button with an icon (old app's "ghost" button). */
@Composable
fun GhostButton(text: String, icon: ImageVector, filled: Boolean = false, loading: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .background(if (filled) C.Brand else Color.Transparent, RoundedCornerShape(10.dp))
            .pressable { if (!loading) onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = if (filled) Color.White else C.Brand)
        } else {
            Icon(icon, contentDescription = null, tint = if (filled) Color.White else C.Brand, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(text, color = if (filled) Color.White else C.Brand, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// ============================================================
//  VENDORS — supplier directory
// ============================================================

@Composable
fun VendorsScreen(nav: Nav) {
    var q by rememberSaveable { mutableStateOf("") }
    var list by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(q) {
        delay(300)
        try {
            list = Api.getArr("/vendors", mapOf("q" to q.trim())).objects()
        } catch (e: ApiException) {
            Toast.error(e.message ?: t("common.loadFailed"))
        } finally {
            loading = false
        }
    }

    Screen {
        TopBar(t("vendors.title"), t("vendors.subtitle"), onBack = { nav.back() })
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.Top) {
            Field(q, { q = it }, t("customers.searchPlaceholder"), modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            SquareIconButton(Icons.Filled.Add) { nav.open(Route.VendorNew) }
        }
        when {
            loading -> Loading()
            list.isEmpty() -> EmptyState(
                Icons.Outlined.WorkOutline, t("vendors.empty"),
                if (q.isNotBlank()) "${t("common.nothingFoundFor")} \"$q\"" else t("vendors.emptySub"),
            ) { BigButton(t("vendors.add"), icon = Icons.Filled.AddCircle, onClick = { nav.open(Route.VendorNew) }, modifier = Modifier.width(220.dp)) }
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(list, key = { _, v -> v.str("id") }) { i, v ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .entrance(i)
                            .background(C.Card, RoundedCornerShape(12.dp))
                            .border(1.dp, C.Line, RoundedCornerShape(12.dp))
                            .pressable { nav.open(Route.Vendor(v.str("id"))) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(Icons.Filled.Work)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.str("name"), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(v.str("phone"), color = C.Muted, fontSize = 13.sp)
                            if (v.str("notes").isNotBlank()) Text(v.str("notes"), color = C.Muted, fontSize = 12.sp, maxLines = 1)
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = C.Muted)
                    }
                }
            }
        }
    }
}

@Composable
fun VendorNewScreen(nav: Nav) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    Screen {
        TopBar(t("vendors.newTitle"), t("vendors.subtitle"), onBack = { nav.back() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp)) {
            Card(Modifier.entrance()) {
                Field(name, { name = it }, t("common.name"), placeholder = t("vendors.namePlaceholder"))
                Field(phone, { phone = it }, t("common.phone"), placeholder = t("customers.phonePlaceholder"), phone = true)
                Field(address, { address = it }, t("common.addressOptional"), placeholder = t("common.address"), multiline = true)
                Field(notes, { notes = it }, t("vendors.notesOptional"), placeholder = "e.g. Electrical parts, OEM Maruti", multiline = true)
            }
            Spacer(Modifier.height(12.dp))
            BigButton(t("vendors.save"), icon = Icons.Filled.CheckCircle, loading = saving, onClick = {
                if (name.isBlank() || phone.isBlank()) {
                    Toast.error(t("customers.errRequired")); return@BigButton
                }
                saving = true
                scope.launch {
                    try {
                        val v = Api.post(
                            "/vendors",
                            JSONObject().put("name", name.trim()).put("phone", phone.trim())
                                .put("address", address.trim()).put("notes", notes.trim()),
                        ) as JSONObject
                        Toast.success(t("vendors.added"))
                        nav.replace(Route.Vendor(v.str("id")))
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

@Composable
fun VendorDetailScreen(user: User, nav: Nav, vendorId: String) {
    val scope = rememberCoroutineScope()
    var vendor by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        try {
            val v = Api.getObj("/vendors/${Api.seg(vendorId)}")
            vendor = v
            name = v.str("name"); phone = v.str("phone"); address = v.str("address"); notes = v.str("notes")
        } catch (e: ApiException) {
            Toast.error(e.message ?: t("vendors.loadFailed"))
            vendor = null
        } finally {
            loading = false
        }
    }

    val v = vendor
    if (loading || v == null) {
        Screen {
            TopBar(t("vendors.detailTitle"), onBack = { nav.back() })
            if (loading) Loading() else EmptyState(Icons.Outlined.WorkOutline, t("vendors.notFound"))
        }
        return
    }

    Screen {
        TopBar(v.str("name"), v.str("phone"), onBack = { nav.back() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.entrance().animateContentSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(t("vendors.details"), Modifier.weight(1f))
                    GhostButton(if (editing) t("ui.cancel") else t("common.edit"), if (editing) Icons.Filled.Close else Icons.Filled.Edit) { editing = !editing }
                }
                if (editing) {
                    Field(name, { name = it }, t("common.name"), placeholder = t("vendors.namePlaceholder"))
                    Field(phone, { phone = it }, t("common.phone"), placeholder = t("common.phoneNumber"), phone = true)
                    Field(address, { address = it }, t("common.address"), multiline = true)
                    Field(notes, { notes = it }, t("vendors.notes"), placeholder = "e.g. Electrical parts, OEM Maruti", multiline = true)
                    BigButton(t("common.saveChanges"), icon = Icons.Filled.CheckCircle, loading = saving, onClick = {
                        if (name.isBlank() || phone.isBlank()) {
                            Toast.error(t("customers.errRequired")); return@BigButton
                        }
                        saving = true
                        scope.launch {
                            try {
                                Api.patch(
                                    "/vendors/${Api.seg(vendorId)}",
                                    JSONObject().put("name", name.trim()).put("phone", phone.trim())
                                        .put("address", address.trim()).put("notes", notes.trim()),
                                )
                                Toast.success(t("vendors.updated"))
                                editing = false
                                reload++
                            } catch (e: ApiException) {
                                Toast.error(e.message ?: t("common.updateFailed"))
                            } finally {
                                saving = false
                            }
                        }
                    })
                } else {
                    if (v.str("address").isNotBlank()) DetailBlock(t("common.address"), v.str("address"))
                    if (v.str("notes").isNotBlank()) DetailBlock(t("vendors.notes"), v.str("notes"))
                    if (v.str("address").isBlank() && v.str("notes").isBlank()) {
                        Text(t("vendors.noAddressNotes"), color = C.Muted, fontSize = 14.sp)
                    }
                }
            }
            if (user.isAdmin) {
                BigButton(t("vendors.delete"), icon = Icons.Filled.Delete, color = C.Red, onClick = { confirmDelete = true })
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = t("vendors.deleteConfirmTitle"),
            message = "${v.str("name")} ${t("vendors.deleteConfirmMsg")}",
            confirmText = t("common.delete"),
            danger = true,
            loading = deleting,
            onConfirm = {
                deleting = true
                scope.launch {
                    try {
                        Api.delete("/vendors/${Api.seg(vendorId)}")
                        Toast.success(t("vendors.deleted"))
                        confirmDelete = false
                        nav.back()
                    } catch (e: ApiException) {
                        Toast.error(e.message ?: t("vendors.deleteFailed"))
                        confirmDelete = false
                    } finally {
                        deleting = false
                    }
                }
            },
            onCancel = { confirmDelete = false },
        )
    }
}

@Composable
private fun DetailBlock(label: String, value: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(value, color = C.Text, fontSize = 15.sp)
    }
}

// ============================================================
//  PARTS LIST (from Catalog → category)
// ============================================================

@Composable
fun PartsListScreen(user: User, nav: Nav, category: String) {
    var parts by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val canViewDetails = user.can("view_part_details")

    LaunchedEffect(reload) {
        loading = true
        error = null
        try {
            parts = Api.getArr("/parts", mapOf("category" to category)).objects()
        } catch (e: ApiException) {
            error = e.message
        } finally {
            loading = false
        }
    }

    Screen {
        TopBar(category.ifBlank { t("parts.title") }, t("parts.subtitle"), onBack = { nav.back() })
        when {
            loading -> Loading()
            error != null -> LoadError(error ?: "") { reload++ }
            parts.isEmpty() -> EmptyState(Icons.Outlined.Description, t("parts.empty"), t("parts.emptySub"))
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(parts, key = { _, p -> p.str("id") }) { i, p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .entrance(i)
                            .background(C.Card, RoundedCornerShape(12.dp))
                            .border(1.dp, C.Line, RoundedCornerShape(12.dp))
                            .pressable { nav.open(Route.Part(p.str("part_number"))) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.str("part_number"), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            if (canViewDetails) {
                                if (p.str("name").isNotBlank()) Text(p.str("name"), color = C.Text2, fontSize = 14.sp)
                                Text("${p.str("company")} • ${t("common.stock")}: ${p.int("stock_count")}", color = C.Muted, fontSize = 12.sp)
                            }
                        }
                        if (canViewDetails) {
                            StatusChip(p.str("verification_status"))
                        } else {
                            val exists = p.optBoolean("exists")
                            Badge(
                                if (exists) t("partDetail.inStockYes") else t("partDetail.notFoundInStore"),
                                if (exists) C.Green else C.Red,
                                if (exists) C.GreenFaint else C.RedFaint,
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = C.Muted)
                    }
                }
            }
        }
    }
}

// ============================================================
//  CHANGE MY PASSWORD
// ============================================================

@Composable
fun ChangePasswordScreen(nav: Nav) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    Screen {
        TopBar(t("cp.title"), onBack = { nav.back() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp)) {
            InfoNote(t("cp.info"))
            Spacer(Modifier.height(12.dp))
            Card(Modifier.entrance()) {
                Field(current, { current = it }, t("cp.current"), password = true)
                Field(next, { next = it }, t("cp.new"), placeholder = t("cp.newPlaceholder"), password = true)
                Field(confirm, { confirm = it }, t("cp.reenter"), placeholder = t("cp.confirmPlaceholder"), password = true)
            }
            Spacer(Modifier.height(12.dp))
            BigButton(t("cp.submit"), icon = Icons.Filled.Key, loading = saving, onClick = {
                when {
                    current.isBlank() -> { Toast.error(t("cp.errCurrent")); return@BigButton }
                    next.length < 6 -> { Toast.error(t("cp.errLen")); return@BigButton }
                    next != confirm -> { Toast.error(t("cp.errMismatch")); return@BigButton }
                    next == current -> { Toast.error(t("cp.errSame")); return@BigButton }
                }
                saving = true
                scope.launch {
                    try {
                        Api.post("/auth/change-password", JSONObject().put("current_password", current).put("new_password", next))
                        Toast.success(t("cp.success"))
                        nav.back()
                    } catch (e: ApiException) {
                        Toast.error(e.message ?: t("cp.errFailed"))
                    } finally {
                        saving = false
                    }
                }
            })
        }
    }
}
