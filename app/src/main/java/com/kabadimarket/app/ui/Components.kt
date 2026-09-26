package com.kabadimarket.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabadimarket.app.data.scanBarcode

// ---------------- Colors ----------------
object C {
    val Brand = Color(0xFF2563EB)
    val BrandDark = Color(0xFF1D4ED8)
    val BrandFaint = Color(0xFFDBEAFE)
    val Bg = Color(0xFFF4F6F9)
    val Card = Color(0xFFFFFFFF)
    val Text = Color(0xFF1E293B)
    val Muted = Color(0xFF64748B)
    val Line = Color(0xFFE2E8F0)
    val Green = Color(0xFF16A34A)
    val GreenFaint = Color(0xFFDCFCE7)
    val Amber = Color(0xFFD97706)
    val AmberFaint = Color(0xFFFEF3C7)
    val Red = Color(0xFFDC2626)
    val RedFaint = Color(0xFFFEE2E2)
    val Gray = Color(0xFF475569)
    val GrayFaint = Color(0xFFECEFF3)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = C.Brand,
            onPrimary = Color.White,
            secondary = C.BrandDark,
            background = C.Bg,
            surface = C.Card,
            onBackground = C.Text,
            onSurface = C.Text,
            error = C.Red,
        ),
        content = content,
    )
}

// ---------------- Layout pieces ----------------

@Composable
fun TopBar(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(C.Brand)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = Color(0xFFDBEAFE), fontSize = 13.sp, maxLines = 1)
            }
        }
        actions()
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = C.Card,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = C.Muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = C.Muted, fontSize = 14.sp, modifier = Modifier.width(110.dp))
        Text(value, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

@Composable
fun Badge(text: String, fg: Color, bg: Color) {
    Box(
        Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Colors for the part status coming from the server. */
fun statusColors(status: String): Pair<Color, Color> = when (status) {
    "IN STOCK" -> C.Green to C.GreenFaint
    "REQUIREMENT" -> C.Amber to C.AmberFaint
    "KNOWN PART" -> C.Brand to C.BrandFaint
    "STOP" -> C.Red to C.RedFaint
    "WARNING" -> C.Amber to C.AmberFaint
    else -> C.Gray to C.GrayFaint
}

fun conditionColors(condition: String): Pair<Color, Color> = when (condition) {
    "Working" -> C.Green to C.GreenFaint
    "Testing", "Repairable", "Incomplete" -> C.Amber to C.AmberFaint
    "Damaged", "Scrap" -> C.Red to C.RedFaint
    else -> C.Gray to C.GrayFaint
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(end = 8.dp, bottom = 8.dp)
            .background(if (selected) C.Brand else C.GrayFaint, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            color = if (selected) Color.White else C.Text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(Modifier.fillMaxWidth()) {
        options.forEach { o -> Chip(o, o == selected) { onSelect(o) } }
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    number: Boolean = false,
    caps: Boolean = false,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onIme: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth().padding(bottom = 10.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                password -> KeyboardType.Password
                number -> KeyboardType.Decimal
                else -> KeyboardType.Text
            },
            capitalization = when {
                caps -> KeyboardCapitalization.Characters
                password || number -> KeyboardCapitalization.None
                else -> KeyboardCapitalization.Sentences
            },
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onDone = { onIme?.invoke() },
            onSearch = { onIme?.invoke() },
            onGo = { onIme?.invoke() },
        ),
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation()
        else androidx.compose.ui.text.input.VisualTransformation.None,
        trailingIcon = trailing,
        shape = RoundedCornerShape(10.dp),
    )
}

/** A part-number text box with a Scan button next to it. */
@Composable
fun PartNumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String = "Part number",
    onSubmit: (() -> Unit)? = null,
    onScanned: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.Top) {
        Field(
            value = value,
            onChange = onChange,
            label = label,
            caps = true,
            imeAction = if (onSubmit != null) ImeAction.Search else ImeAction.Next,
            onIme = onSubmit,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = {
                scanBarcode(context) { code ->
                    onChange(code)
                    onScanned?.invoke(code)
                }
            },
            modifier = Modifier.padding(top = 8.dp).height(52.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.BrandDark),
        ) { Text("📷 Scan", fontSize = 15.sp) }
    }
}

@Composable
fun Picker(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 11.sp, color = C.Muted)
                Text(value.ifBlank { "Select" }, fontSize = 15.sp, color = C.Text, maxLines = 1)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = C.Muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(o); open = false })
            }
        }
    }
}

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = C.Brand,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ErrorBox(message: String?) {
    if (message.isNullOrBlank()) return
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(C.RedFaint, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) { Text(message, color = C.Red, fontSize = 14.sp) }
}

@Composable
fun Loading(text: String = "Loading…") {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = C.Brand)
        Spacer(Modifier.height(12.dp))
        Text(text, color = C.Muted, fontSize = 14.sp)
    }
}

fun money(v: Double?): String {
    if (v == null) return "—"
    return if (v % 1.0 == 0.0) "₹" + v.toLong().toString() else "₹" + String.format(java.util.Locale.US, "%.2f", v)
}

/** "2026-09-26T10:15:00+00:00" → "26 Sep 2026" */
fun shortDate(iso: String): String {
    if (iso.length < 10) return iso
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val y = iso.substring(0, 4)
    val m = iso.substring(5, 7).toIntOrNull() ?: return iso
    val d = iso.substring(8, 10)
    return "$d ${months.getOrElse(m - 1) { "" }} $y"
}
