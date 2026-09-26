package com.kabadimarket.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.kabadimarket.app.data.Session
import com.kabadimarket.app.data.Share
import com.kabadimarket.app.data.User
import com.kabadimarket.app.data.arr
import com.kabadimarket.app.data.int
import com.kabadimarket.app.data.num
import com.kabadimarket.app.data.objects
import com.kabadimarket.app.data.str
import com.kabadimarket.app.data.strings
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================
//  Bottom tabs: Home · Reports · Catalog · Admin (same as old app)
// ============================================================

private data class TabItem(val key: String, val icon: ImageVector)

private val TABS = listOf(
    TabItem("tabs.home", Icons.Filled.GridView),
    TabItem("tabs.reports", Icons.Filled.BarChart),
    TabItem("tabs.catalog", Icons.Filled.Collections),
    TabItem("tabs.admin", Icons.Filled.Shield),
)

@Composable
fun TabsScreen(user: User, nav: Nav, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().background(C.Bg)) {
        AnimatedContent(
            targetState = nav.tab,
            transitionSpec = {
                (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.96f)) togetherWith fadeOut(tween(160))
            },
            modifier = Modifier.weight(1f),
            label = "tabs",
        ) { tab ->
            when (tab) {
                0 -> HomeTab(user, nav, onLogout)
                1 -> ReportsTab(user, nav, onLogout)
                2 -> CatalogTab(nav, onLogout)
                else -> AdminTab(user, nav, onLogout)
            }
        }
        HorizontalDivider(color = C.Line)
        NavigationBar(containerColor = C.Card, tonalElevation = 0.dp) {
            TABS.forEachIndexed { i, tab ->
                val selected = nav.tab == i
                val scale by animateFloatAsState(
                    if (selected) 1.18f else 1f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "tabIcon",
                )
                NavigationBarItem(
                    selected = selected,
                    onClick = { nav.tab = i },
                    icon = {
                        Icon(tab.icon, contentDescription = null, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
                    },
                    label = { Text(t(tab.key), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = C.Brand,
                        selectedTextColor = C.Brand,
                        indicatorColor = C.BrandFaint,
                        unselectedIconColor = C.Muted,
                        unselectedTextColor = C.Muted,
                    ),
                )
            }
        }
    }
}

// ============================================================
//  HOME — module grid (same modules, same order as old app)
// ============================================================

private data class Module(
    val key: String,
    val sub: String,
    val icon: ImageVector,
    val color: Color,
    val perm: String? = null,
    val wide: Boolean = false,
    val superAdminOnly: Boolean = false,
    val adminOnly: Boolean = false,
    val route: (Nav) -> Unit,
)

private fun soon(nav: Nav, key: String) = nav.open(Route.Soon(key))

private val MODULES = listOf(
    Module("search", "Find part", Icons.Filled.Search, C.Muted, "search") { it.open(Route.Scan("search")) },
    Module("buy", "Purchase", Icons.Filled.Download, C.Green, "buy") { it.open(Route.Buy()) },
    Module("sell", "Sale", Icons.Filled.Payments, C.Brand, "sell") { it.open(Route.Scan("sell")) },
    Module("requirement", "Inquiry / Need", Icons.Filled.AddCircle, C.Amber, "requirement") { it.open(Route.Scan("requirement")) },
    Module("customers", "Grahak Khata", Icons.Filled.People, C.Brand, "sell") { it.open(Route.Customers) },
    Module("vendors", "Supplier records", Icons.Filled.Work, C.Green, "buy") { it.open(Route.Vendors) },
    Module("damaged-returns", "Returns & spoilage", Icons.AutoMirrored.Filled.Undo, C.Red, "sell") { it.open(Route.DamagedReturns) },
    Module("cash-book", "Rokad no hisab", Icons.Filled.Wallet, C.Amber, "sell") { it.open(Route.CashBook) },
    Module("purchase-orders", "Vendor ne order aapo", Icons.Filled.ListAlt, C.Green, "buy") { soon(it, "module.purchase-orders") },
    Module("quotations", "Grahak ne bhav aapo", Icons.Filled.Description, C.Brand, "sell") { soon(it, "module.quotations") },
    Module("stock-transfer", "Store thi store stock mokalo", Icons.Filled.SwapHoriz, C.Muted, superAdminOnly = true) { soon(it, "module.stock-transfer") },
    Module("reservations", "Grahak mate stock rakho", Icons.Filled.Lock, C.Amber, "sell") { soon(it, "module.reservations") },
    Module("audit-log", "Kone shu badalyu", Icons.Filled.History, C.Muted, adminOnly = true) { soon(it, "module.audit-log") },
    Module("tools", "Admin utilities", Icons.Filled.Build, C.Brand) { it.open(Route.Tools) },
    Module("arrange", "Place bought stock", Icons.Filled.LocationOn, C.Muted, "buy", wide = true) { soon(it, "module.arrange") },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeTab(user: User, nav: Nav, onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lowStock by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var sharing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Refresh the user's permissions quietly (an admin may have changed them).
        try {
            val me = Api.getObj("/auth/me")
            if (me.has("id")) Session.updateUser(me)
        } catch (_: Exception) {
        }
        lowStock = try {
            Api.getArr("/inventory/low-stock").objects()
        } catch (_: Exception) {
            emptyList()
        }
    }

    val visible = MODULES.filter {
        (it.perm == null || user.can(it.perm)) &&
            (!it.superAdminOnly || user.isSuperAdmin) &&
            (!it.adminOnly || user.isAdmin)
    }

    Column(Modifier.fillMaxSize()) {
        // Top bar: Welcome, name · Online · Sign out
        Column(Modifier.fillMaxWidth().background(C.Card).padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(I18n.x("welcome"), color = C.Muted, fontSize = 12.sp)
                    Text(user.name, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
                OnlinePill()
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                SignOutButton(onLogout)
            }
        }
        HorizontalDivider(color = C.Divider)

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (lowStock.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .entrance()
                        .background(C.Red, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.weight(1f).pressable { nav.open(Route.Inventory) }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Error, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${lowStock.size} ${I18n.x("lowStock")}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                    if (sharing) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(
                            Icons.Filled.Chat, contentDescription = "WhatsApp", tint = Color.White,
                            modifier = Modifier.size(22.dp).pressable {
                                sharing = true
                                scope.launch {
                                    try {
                                        val rows = Api.getArr("/inventory/low-stock").objects()
                                        if (rows.isEmpty()) Toast.show(t("home.noLowStock"))
                                        else Share.whatsApp(context, lowStockText(user, rows))
                                    } catch (e: ApiException) {
                                        Toast.error(e.message ?: t("common.failed"))
                                    } finally {
                                        sharing = false
                                    }
                                }
                            },
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.height(16.dp))
            }

            SectionTitle(t("home.modules"))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 2,
            ) {
                visible.filter { !it.wide }.forEachIndexed { i, m ->
                    ModuleTile(m, i, Modifier.weight(1f)) { m.route(nav) }
                }
            }
            visible.filter { it.wide }.forEach { m ->
                Spacer(Modifier.height(12.dp))
                ModuleTile(m, visible.size, Modifier.fillMaxWidth()) { m.route(nav) }
            }

            Spacer(Modifier.height(24.dp))
            InfoNote(t("home.hint"))

            if (user.isAdmin) {
                Spacer(Modifier.height(24.dp))
                SectionTitle(I18n.x("sticker.printing"))
                LinkRow(Icons.Filled.DocumentScanner, I18n.x("sticker.title"), I18n.x("sticker.sub")) {
                    nav.open(Route.Soon("sticker.title"))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ModuleTile(m: Module, index: Int, modifier: Modifier, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        Box(
            Modifier
                .size(48.dp)
                .border(1.5.dp, m.color, RoundedCornerShape(12.dp))
                .background(C.Bg, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(m.icon, contentDescription = null, tint = m.color, modifier = Modifier.size(26.dp)) }
    }
    val base = modifier
        .entrance(index)
        .background(C.Card, RoundedCornerShape(12.dp))
        .border(1.dp, C.Line, RoundedCornerShape(12.dp))
        .pressable(onClick)
        .padding(16.dp)
    if (m.wide) {
        Row(base.heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically) {
            content()
            Spacer(Modifier.width(12.dp))
            Column {
                Text(t("module.${m.key}"), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                Text(m.sub, color = C.Muted, fontSize = 14.sp)
            }
        }
    } else {
        Column(base.heightIn(min = 118.dp), verticalArrangement = Arrangement.SpaceBetween) {
            content()
            Spacer(Modifier.height(12.dp))
            Text(t("module.${m.key}"), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
            Text(m.sub, color = C.Muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun OnlinePill() {
    val pulse by rememberInfiniteTransition(label = "online").animateFloat(
        0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dot",
    )
    Row(
        Modifier
            .border(1.dp, C.Line, RoundedCornerShape(50))
            .background(C.Card, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).graphicsLayer { alpha = pulse }.background(C.Green, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(I18n.x("online"), color = C.Text2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private fun today(): String = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date())

private fun lowStockText(user: User, rows: List<JSONObject>): String {
    val lines = mutableListOf("*Low Stock Alert — ${user.storeName.ifBlank { "Auto Parts Store" }}*", today(), "")
    rows.forEachIndexed { i, r ->
        val name = r.str("name")
        lines.add("${i + 1}. ${r.str("part_number")}${if (name.isNotBlank()) " - $name" else ""} — ${r.int("stock_count")} left (alert ≤ ${r.int("low_stock_threshold")})")
    }
    lines.add("")
    lines.add("Total: ${rows.size} part(s) low on stock.")
    return lines.joinToString("\n")
}

// ============================================================
//  REPORTS tab
// ============================================================

@Composable
private fun ReportsTab(user: User, nav: Nav, onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }

    data class L(val key: String, val title: String, val sub: String, val icon: ImageVector, val color: Color, val adminOnly: Boolean, val go: () -> Unit)
    val links = listOf(
        L("profit", "profitReport.title", "home.reportProfitSub", Icons.Filled.TrendingUp, C.Amber, true) { nav.open(Route.ProfitReport) },
        L("stock", "report.stockReport", "home.reportStockSub", Icons.Filled.Inventory2, C.Muted, false) { nav.open(Route.Report("stock")) },
        L("buy", "report.purchases", "home.reportBuySub", Icons.Filled.Download, C.Green, true) { nav.open(Route.Report("buy")) },
        L("sell", "report.sales", "home.reportSellSub", Icons.Filled.Payments, C.Brand, true) { nav.open(Route.Report("sell")) },
        L("inventory", "tabs.inventory", "home.reportInventorySub", Icons.Filled.Inventory, C.Brand, false) { nav.open(Route.Inventory) },
        L("requirements", "tabs.needs", "home.reportRequirementsSub", Icons.Filled.Checklist, C.Amber, false) { nav.open(Route.Requirements) },
    ).filter { !it.adminOnly || user.isAdmin }

    Column(Modifier.fillMaxSize()) {
        TopBar(t("tabs.reports"), t("home.viewAllReportsSub"), actions = { SignOutButton(onLogout) })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            links.forEachIndexed { i, l ->
                LinkRow(l.icon, t(l.title), t(l.sub), l.color, Modifier.entrance(i)) { l.go() }
            }
            if (user.isAdmin) {
                val spinner: (@Composable () -> Unit)? =
                    if (sharing) { { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) } } else null
                LinkRow(
                    Icons.Filled.Chat, t("home.shareDailySales"), t("home.shareDailySalesSub"), C.WhatsApp,
                    Modifier.entrance(links.size),
                    trailing = spinner,
                ) {
                    if (sharing) return@LinkRow
                    sharing = true
                    scope.launch {
                        try {
                            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                            val report = Api.getObj("/reports/profit", mapOf("date_from" to day, "date_to" to day))
                            val s = report.optJSONObject("summary") ?: JSONObject()
                            val lines = mutableListOf(
                                "*Daily Sales Summary — ${user.storeName.ifBlank { "Auto Parts Store" }}*",
                                today(), "",
                                "Units sold: ${s.int("units_sold")}",
                                "Revenue: ${moneyText(s.num("total_revenue"))}",
                                "Cost: ${moneyText(s.num("total_cost"))}",
                                "*Profit: ${moneyText(s.num("total_profit"))}*",
                            )
                            if (s.int("units_with_unknown_cost") > 0) lines.add("(${s.int("units_with_unknown_cost")} unit(s) had no recorded cost)")
                            Share.whatsApp(context, lines.joinToString("\n"))
                        } catch (e: ApiException) {
                            Toast.error(e.message ?: t("common.failed"))
                        } finally {
                            sharing = false
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ============================================================
//  CATALOG tab — Category Master (tap a group to open it)
// ============================================================

private fun groupIcon(group: String): ImageVector = when (group) {
    "Control Modules" -> Icons.Filled.Memory
    "Sensors" -> Icons.Filled.Sensors
    "Motors & Actuators" -> Icons.Filled.Settings
    "Switches & Electrical" -> Icons.Filled.Bolt
    "Interior / Electronic" -> Icons.Filled.SmartDisplay
    else -> Icons.Filled.Inventory2
}

@Composable
private fun CatalogTab(nav: Nav, onLogout: () -> Unit) {
    var groups by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val open = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(reload) {
        loading = true
        error = null
        try {
            val data = Api.getObj("/categories")
            groups = data.arr("groups").objects()
            total = data.int("total")
            if (open.isEmpty()) groups.firstOrNull()?.let { open[it.str("group")] = true }
        } catch (e: ApiException) {
            error = e.message
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            t("categories.title"),
            if (groups.isNotEmpty()) "$total ${t("categories.items")} • ${groups.size} ${t("categories.groups")}" else null,
            actions = { SignOutButton(onLogout) },
        )
        when {
            loading -> Loading()
            error != null -> LoadError(error ?: "") { reload++ }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                groups.forEachIndexed { gi, g ->
                    val name = g.str("group")
                    val items = g.arr("items").strings()
                    val isOpen = open[name] == true
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .entrance(gi)
                            .background(C.Card, RoundedCornerShape(12.dp))
                            .border(1.dp, C.Line, RoundedCornerShape(12.dp))
                            .animateContentSize(),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().pressable { open[name] = !isOpen }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(40.dp).background(C.BrandFaint, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                Icon(groupIcon(name), contentDescription = null, tint = C.Brand, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                                Text("${items.size} ${t("categories.items")}", color = C.Muted, fontSize = 12.sp)
                            }
                            Icon(if (isOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = C.Muted)
                        }
                        if (isOpen) {
                            items.forEachIndexed { i, item ->
                                HorizontalDivider(color = C.Divider)
                                Row(
                                    Modifier.fillMaxWidth().pressable { nav.open(Route.PartsList(item)) }.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(item, color = C.Text2, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = C.Muted, modifier = Modifier.size(16.dp))
                                }
                                if (i == items.lastIndex) Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ============================================================
//  ADMIN tab
// ============================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminTab(user: User, nav: Nav, onLogout: () -> Unit) {
    val context = LocalContext.current
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    val lang = I18n.lang

    LaunchedEffect(Unit) {
        try {
            if (user.can("view_stats")) stats = Api.getObj("/stats")
        } catch (_: ApiException) {
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            t("admin.title"),
            if (user.storeName.isNotBlank()) "${user.name} · ${user.storeName}" else user.name,
            actions = { SignOutButton(onLogout) },
        )
        if (loading) {
            Loading()
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            stats?.let { s ->
                SectionTitle(t("admin.statistics"))
                data class Stat(val label: String, val value: Int, val color: Color, val icon: ImageVector, val go: () -> Unit)
                val cards = buildList {
                    add(Stat(t("admin.statParts"), s.int("total_parts"), C.Brand, Icons.Filled.Description) { nav.open(Route.Report("stock")) })
                    add(Stat(t("admin.statInStock"), s.int("in_stock_units"), C.Green, Icons.Filled.Inventory2) { nav.open(Route.Inventory) })
                    if (user.isAdmin) add(Stat(t("admin.statSold"), s.int("sold_units"), C.Muted, Icons.Filled.Payments) { nav.open(Route.Report("sell")) })
                    add(Stat(t("admin.statPendingNeeds"), s.int("pending_requirements"), C.Amber, Icons.Filled.Checklist) { nav.open(Route.Requirements) })
                    add(Stat(t("admin.statAiPending"), s.int("pending_ai"), C.Amber, Icons.Filled.AutoAwesome) { soon(nav, "admin.toolAiApprovals") })
                    add(Stat(t("admin.statVerified"), s.int("verified_parts"), C.Green, Icons.Filled.Verified) { nav.open(Route.Report("stock")) })
                }
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = 3,
                ) {
                    cards.forEachIndexed { i, c ->
                        Column(
                            Modifier
                                .weight(1f)
                                .entrance(i)
                                .background(C.Card, RoundedCornerShape(12.dp))
                                .border(1.dp, C.Line, RoundedCornerShape(12.dp))
                                .pressable(c.go)
                                .padding(12.dp),
                        ) {
                            Icon(c.icon, contentDescription = null, tint = c.color, modifier = Modifier.size(20.dp))
                            CountUpText(c.value, c.color)
                            Text(c.label, color = C.Muted, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            SectionTitle(t("admin.management"))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (user.isSuperAdmin) {
                    LinkRow(Icons.Filled.Business, t("stores.title"), t("admin.linkAllStoresSub")) { soon(nav, "stores.title") }
                    LinkRow(Icons.Filled.LocationOn, t("admin.linkGpsTitle"), t("admin.linkGpsSub"), C.Green) { soon(nav, "admin.linkGpsTitle") }
                }
                if (user.isOwner) {
                    LinkRow(Icons.Filled.Public, t("ownerPanel.title"), t("admin.linkOwnerPanelSub"), C.Red) { soon(nav, "ownerPanel.title") }
                }
                if (user.isAdmin) {
                    LinkRow(
                        Icons.Filled.Description, t("admin.linkBlueprintTitle"), t("admin.linkBlueprintSub"), C.Muted,
                        trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = C.Muted) },
                    ) {
                        Share.openUrl(context, "https://github.com/opendoor555sk-max/carparts/blob/conflict_040926_0622/PROJECT_BLUEPRINT.md")
                    }
                }
                if (user.can("manage_users")) {
                    LinkRow(Icons.Filled.People, t("users.title"), t("admin.linkManageUsersSub")) { soon(nav, "users.title") }
                }
                if (user.isStoreAdmin) {
                    LinkRow(Icons.Filled.CloudDownload, t("backup.title"), t("admin.linkBackupSub")) { soon(nav, "backup.title") }
                }
            }
            Spacer(Modifier.height(12.dp))
            InfoNote(t("admin.locationNotice"), Icons.Filled.LocationOn, C.Muted, C.Surface3, C.Text2)

            Spacer(Modifier.height(24.dp))
            SectionTitle(t("admin.account"))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(C.Card, RoundedCornerShape(12.dp))
                    .border(1.dp, C.Line, RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).border(1.5.dp, C.Brand, RoundedCornerShape(8.dp)).background(C.Bg, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Language, contentDescription = null, tint = C.Brand) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(t("admin.language"), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Text(t("admin.languageSub"), color = C.Muted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                FlowRow {
                    I18n.LANGUAGES.forEach { (code, label) -> Chip(label, lang == code) { I18n.setLanguage(code) } }
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (user.isStoreAdmin) {
                    LinkRow(Icons.Filled.Storefront, t("admin.linkStoreProfileTitle"), t("admin.linkStoreProfileSub")) { soon(nav, "admin.linkStoreProfileTitle") }
                }
                LinkRow(Icons.Filled.Key, t("admin.linkMyPasswordTitle"), t("admin.linkMyPasswordSub")) { nav.open(Route.ChangePassword) }
            }
            if (!user.isStoreAdmin) {
                Spacer(Modifier.height(12.dp))
                InfoNote(t("admin.staffNote"), Icons.Filled.Info, C.Muted, C.Surface3, C.Text2)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** A number that counts up from 0 when it appears. */
@Composable
private fun CountUpText(value: Int, color: Color) {
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(value) { anim.animateTo(value.toFloat(), tween(900)) }
    Text(anim.value.toInt().toString(), color = color, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(vertical = 2.dp))
}
