package com.kabadimarket.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabadimarket.app.Nav
import com.kabadimarket.app.Route
import com.kabadimarket.app.data.Api
import com.kabadimarket.app.data.ApiException
import com.kabadimarket.app.data.User
import com.kabadimarket.app.data.obj
import com.kabadimarket.app.data.objects
import com.kabadimarket.app.data.str
import kotlinx.coroutines.delay
import org.json.JSONObject

val CONDITIONS = listOf("Working", "Testing", "Repairable", "Damaged", "Incomplete", "Scrap", "Unknown")

@Composable
fun InventoryScreen(@Suppress("UNUSED_PARAMETER") user: User, nav: Nav) {
    var query by rememberSaveable { mutableStateOf("") }
    var condition by rememberSaveable { mutableStateOf("All") }
    var reload by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    LaunchedEffect(query, condition, reload) {
        delay(400) // wait until the user stops typing
        loading = true
        error = null
        try {
            rows = Api.getArr(
                "/inventory",
                mapOf("q" to query.trim(), "condition" to condition.takeIf { it != "All" }),
            ).objects()
        } catch (e: ApiException) {
            error = e.message
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            t("tabs.inventory"),
            subtitle = if (loading) "Loading…" else "${rows.size} pieces in stock",
            onBack = { nav.back() },
            actions = {
                IconButton(onClick = { reload++ }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color.White)
                }
            },
        )
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            PartNumberField(value = query, onChange = { query = it }, label = "Search part number")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                (listOf("All") + CONDITIONS).forEach { c -> Chip(c, c == condition) { condition = c } }
            }
        }
        ErrorBox(error)
        if (loading && rows.isEmpty()) {
            Loading()
        } else if (!loading && rows.isEmpty() && error == null) {
            Text("No stock found.", color = C.Muted, modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                items(rows, key = { it.str("id") }) { u ->
                    InventoryRow(u) { nav.open(Route.Part(u.str("part_number"))) }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun InventoryRow(u: JSONObject, onClick: () -> Unit) {
    val cond = u.str("condition").ifBlank { "Unknown" }
    val (fg, bg) = conditionColors(cond)
    Column(
        Modifier
            .fillMaxWidth()
            .background(C.Card, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(u.str("part_number"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.Text, modifier = Modifier.weight(1f))
            Badge(cond, fg, bg)
        }
        val name = u.str("part_name")
        if (name.isNotBlank()) Text(name, fontSize = 14.sp, color = C.Text)
        val meta = listOf(u.str("company").takeIf { it != "All" } ?: "", u.str("category").takeIf { it != "Uncategorized" } ?: "")
            .filter { it.isNotBlank() }.joinToString(" · ")
        if (meta.isNotBlank()) Text(meta, fontSize = 13.sp, color = C.Muted)
        val loc = locationText(u.obj("assigned_location"))
        Row {
            if (loc.isNotBlank()) Text("📍 $loc", fontSize = 12.sp, color = C.Muted, modifier = Modifier.weight(1f))
            else Spacer(Modifier.weight(1f))
            Text(shortDate(u.str("created_at")), fontSize = 12.sp, color = C.Muted)
        }
    }
}
