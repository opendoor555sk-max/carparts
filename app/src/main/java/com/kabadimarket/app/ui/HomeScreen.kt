package com.kabadimarket.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabadimarket.app.Nav
import com.kabadimarket.app.Route
import com.kabadimarket.app.data.Api
import com.kabadimarket.app.data.Session
import com.kabadimarket.app.data.User
import com.kabadimarket.app.data.int
import com.kabadimarket.app.data.scanBarcode
import org.json.JSONObject

@Composable
fun HomeScreen(user: User, nav: Nav, onLogout: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var askLogout by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        // Refresh the user's permissions quietly (an admin may have changed them).
        try {
            val me = Api.getObj("/auth/me")
            if (me.has("id")) Session.updateUser(me)
        } catch (_: Exception) {
        }
        if (user.can("view_stats")) {
            stats = try {
                Api.getObj("/stats")
            } catch (_: Exception) {
                null
            }
        }
    }

    fun search() {
        val pn = query.trim()
        if (pn.isNotEmpty()) nav.open(Route.Part(pn))
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = user.storeName.ifBlank { "Kabadi Market" },
            subtitle = "${user.name} · ${user.role}",
            actions = {
                IconButton(onClick = { askLogout = true }) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = Color.White)
                }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Card {
                SectionTitle("Find a part")
                PartNumberField(
                    value = query,
                    onChange = { query = it },
                    onSubmit = { search() },
                    onScanned = { code -> nav.open(Route.Part(code.trim())) },
                )
                BigButton("Search", onClick = { search() }, enabled = query.isNotBlank())
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle("Work")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (user.can("buy")) {
                    Tile("📥", "Buy", "Add stock", C.Green, Modifier.weight(1f)) { nav.open(Route.Buy()) }
                }
                if (user.can("sell")) {
                    Tile("🧾", "Sell", "Make bill", C.Brand, Modifier.weight(1f)) { nav.open(Route.Sell()) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("📦", "Inventory", "All stock", C.Amber, Modifier.weight(1f)) { nav.open(Route.Inventory) }
                Tile("🔍", "Scan", "Scan & search", C.BrandDark, Modifier.weight(1f)) {
                    scanBarcode(context) { code -> nav.open(Route.Part(code)) }
                }
            }

            stats?.let { s ->
                Spacer(Modifier.height(20.dp))
                SectionTitle("Today's picture")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("In stock", s.int("in_stock_units"), Modifier.weight(1f))
                    Stat("Parts", s.int("total_parts"), Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("Total buys", s.int("total_buys"), Modifier.weight(1f))
                    Stat("Total sells", s.int("total_sells"), Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "More sections (customers, reports, labels…) are coming in the next update.",
                color = C.Muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (askLogout) {
        AlertDialog(
            onDismissRequest = { askLogout = false },
            title = { Text("Logout?") },
            text = { Text("You will need your username and password to login again.") },
            confirmButton = { TextButton(onClick = { askLogout = false; onLogout() }) { Text("Logout") } },
            dismissButton = { TextButton(onClick = { askLogout = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Tile(emoji: String, title: String, sub: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .background(C.Card, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(
            emoji,
            fontSize = 26.sp,
            modifier = Modifier
                .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .padding(8.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.Text)
        Text(sub, fontSize = 13.sp, color = C.Muted)
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier) {
    Column(
        modifier
            .background(C.Card, RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(value.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.Brand)
        Text(label, fontSize = 13.sp, color = C.Muted)
    }
}
