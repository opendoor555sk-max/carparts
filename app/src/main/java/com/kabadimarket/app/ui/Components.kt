package com.kabadimarket.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabadimarket.app.R
import com.kabadimarket.app.data.I18n
import com.kabadimarket.app.data.scanBarcode
import kotlinx.coroutines.delay

// ---------------- Colors (same "Trusted Blue" theme as the old app) ----------------
object C {
    val Brand = Color(0xFF2563EB)
    val BrandDark = Color(0xFF1D4ED8)
    val BrandFaint = Color(0xFFDBEAFE)
    val OnBrandFaint = Color(0xFF1E40AF)
    val Bg = Color(0xFFF4F6F9)
    val Card = Color(0xFFFFFFFF)
    val Surface3 = Color(0xFFECEFF3)
    val Text = Color(0xFF1E293B)
    val Text2 = Color(0xFF334155)
    val Muted = Color(0xFF64748B)
    val Line = Color(0xFFE2E8F0)
    val LineStrong = Color(0xFFCBD5E1)
    val Divider = Color(0xFFEDF1F5)
    val Green = Color(0xFF16A34A)
    val GreenFaint = Color(0xFFDCFCE7)
    val OnGreenFaint = Color(0xFF15803D)
    val Amber = Color(0xFFD97706)
    val AmberFaint = Color(0xFFFEF3C7)
    val OnAmberFaint = Color(0xFF92400E)
    val Red = Color(0xFFDC2626)
    val RedFaint = Color(0xFFFEE2E2)
    val OnRedFaint = Color(0xFF991B1B)
    val Gray = Color(0xFF475569)
    val GrayFaint = Color(0xFFECEFF3)
    val Orange = Color(0xFFF97316)
    val WhatsApp = Color(0xFF25D366)
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

/** Short way to get a translated text: t("home.modules") */
fun t(key: String): String = I18n.t(key)

// ---------------- Animations ----------------

/** Pops in (fade + slide up) a little after the previous item — for lists and grids. */
fun Modifier.entrance(index: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(12) * 45).toLong())
        progress.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow))
    }
    graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 40f
    }
}

/** Card/button that shrinks a little while pressed, with a bounce back. */
fun Modifier.pressable(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

/**
 * The shop logo with animation: pops in with a bounce, an orange ring spins around it
 * and it gently "breathes".
 */
@Composable
fun AnimatedLogo(size: Dp = 150.dp) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
    }
    val infinite = rememberInfiniteTransition(label = "logo")
    val spin by infinite.animateFloat(
        0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "spin",
    )
    val breathe by infinite.animateFloat(
        1f, 1.04f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "breathe",
    )
    val glow by infinite.animateFloat(
        0.25f, 0.6f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "glow",
    )
    Box(
        Modifier
            .size(size + 28.dp)
            .graphicsLayer {
                scaleX = appear.value
                scaleY = appear.value
                alpha = appear.value.coerceIn(0f, 1f)
                rotationZ = (1f - appear.value) * -90f
            },
        contentAlignment = Alignment.Center,
    ) {
        // Soft glow behind the logo
        Canvas(Modifier.size(size + 28.dp)) {
            drawCircle(
                Brush.radialGradient(listOf(C.Orange.copy(alpha = glow), Color.Transparent)),
                radius = this.size.minDimension / 2f,
            )
        }
        // Spinning orange + blue ring
        Canvas(Modifier.size(size + 14.dp).rotate(spin)) {
            val stroke = 5.dp.toPx()
            drawArc(
                brush = Brush.sweepGradient(listOf(Color.Transparent, C.Orange, C.Brand, Color.Transparent)),
                startAngle = 0f,
                sweepAngle = 300f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = null,
            modifier = Modifier.size(size).scale(breathe).clip(CircleShape),
        )
    }
}

// ---------------- Toast (animated message at the top) ----------------

object Toast {
    enum class Kind { Success, Error, Info }
    data class Msg(val text: String, val kind: Kind, val id: Long = System.nanoTime())

    var current by mutableStateOf<Msg?>(null)
        private set

    fun show(text: String, kind: Kind = Kind.Info) {
        current = Msg(text, kind)
    }

    fun success(text: String) = show(text, Kind.Success)
    fun error(text: String) = show(text, Kind.Error)
    fun hide() {
        current = null
    }
}

@Composable
fun BoxScope.ToastHost() {
    val msg = Toast.current
    var shown by remember { mutableStateOf<Toast.Msg?>(null) }
    LaunchedEffect(msg) {
        if (msg != null) {
            shown = msg
            delay(2800)
            if (Toast.current == msg) Toast.hide()
        }
    }
    AnimatedVisibility(
        visible = msg != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp),
    ) {
        val m = shown ?: return@AnimatedVisibility
        val (bg, icon) = when (m.kind) {
            Toast.Kind.Success -> C.Green to Icons.Filled.CheckCircle
            Toast.Kind.Error -> C.Red to Icons.Filled.Error
            Toast.Kind.Info -> C.Brand to Icons.Filled.Info
        }
        Surface(shape = RoundedCornerShape(14.dp), color = bg, shadowElevation = 8.dp, modifier = Modifier.clickable { Toast.hide() }) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(m.text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------- Layout pieces ----------------

/** Screen header — white bar with back arrow, same as the old app. */
@Composable
fun TopBar(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().background(C.Card)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = t("common.back"), tint = C.Text, modifier = Modifier.size(30.dp))
                }
            } else {
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, color = C.Muted, fontSize = 12.sp, maxLines = 1)
                }
            }
            actions()
        }
        HorizontalDivider(color = C.Divider)
    }
}

/** Small red "Sign Out" pill with a confirm box, like the old app. */
@Composable
fun SignOutButton(onSignOut: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Row(
        Modifier
            .border(1.dp, C.RedFaint, RoundedCornerShape(50))
            .background(C.Card, RoundedCornerShape(50))
            .pressable { confirm = true }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = C.Red, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(t("ui.signOut"), color = C.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
    if (confirm) {
        ConfirmDialog(
            title = t("ui.signOutTitle"),
            message = t("ui.signOutMessage"),
            confirmText = t("ui.signOut"),
            danger = true,
            onConfirm = { confirm = false; onSignOut() },
            onCancel = { confirm = false },
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = t("ui.confirm"),
    danger: Boolean = false,
    loading: Boolean = false,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !loading) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(confirmText, color = if (danger) C.Red else C.Brand, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(t("ui.cancel")) } },
    )
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = C.Card,
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, C.Line),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = C.Muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        modifier = modifier.padding(bottom = 8.dp),
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

/** Status chip with the same colors as the old app's statusColor(). */
@Composable
fun StatusChip(status: String) {
    if (status.isBlank()) return
    val (fg, bg) = statusColors(status)
    Badge(I18n.status(status), fg, bg)
}

fun statusColors(status: String): Pair<Color, Color> = when (status) {
    "IN STOCK", "Working", "Verified", "Approved", "Completed", "OK TO BUY", "BUY — REQUIRED", "OK" -> C.OnGreenFaint to C.GreenFaint
    "REQUIREMENT", "Pending", "WARNING", "BUY WITH CAUTION", "Requires Verification", "Testing" -> C.OnAmberFaint to C.AmberFaint
    "NEW PART", "STOP", "DO NOT BUY", "Rejected", "Cancelled", "Damaged", "Scrap" -> C.OnRedFaint to C.RedFaint
    else -> C.Gray to C.Surface3
}

fun conditionColors(condition: String): Pair<Color, Color> = statusColors(condition)

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg by androidx.compose.animation.animateColorAsState(if (selected) C.Brand else C.Surface3, label = "chip")
    val fg by androidx.compose.animation.animateColorAsState(if (selected) Color.White else C.Text, label = "chipText")
    Box(
        Modifier
            .padding(end = 8.dp, bottom = 8.dp)
            .background(bg, RoundedCornerShape(50))
            .pressable(onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, color = fg, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(options: List<String>, selected: String, label: (String) -> String = { it }, onSelect: (String) -> Unit) {
    FlowRow(Modifier.fillMaxWidth()) {
        options.forEach { o -> Chip(label(o), o == selected) { onSelect(o) } }
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    number: Boolean = false,
    phone: Boolean = false,
    caps: Boolean = false,
    password: Boolean = false,
    multiline: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onIme: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = C.Muted) } },
        singleLine = !multiline,
        minLines = if (multiline) 2 else 1,
        modifier = modifier.fillMaxWidth().padding(bottom = 10.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                password -> KeyboardType.Password
                phone -> KeyboardType.Phone
                number -> KeyboardType.Decimal
                else -> KeyboardType.Text
            },
            capitalization = when {
                caps -> KeyboardCapitalization.Characters
                password || number || phone -> KeyboardCapitalization.None
                else -> KeyboardCapitalization.Sentences
            },
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onDone = { onIme?.invoke() },
            onSearch = { onIme?.invoke() },
            onGo = { onIme?.invoke() },
        ),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = trailing,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = C.Card,
            focusedContainerColor = C.Card,
            unfocusedBorderColor = C.Line,
        ),
    )
}

/** A part-number text box with a Scan button next to it. */
@Composable
fun PartNumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String = t("common.partNumber"),
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
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(56.dp)
                .background(C.Brand, RoundedCornerShape(12.dp))
                .pressable {
                    scanBarcode(context) { code ->
                        onChange(code)
                        onScanned?.invoke(code)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun Picker(label: String, value: String, options: List<String>, display: (String) -> String = { it }, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 11.sp, color = C.Muted)
                Text(if (value.isBlank()) "—" else display(value), fontSize = 15.sp, color = C.Text, maxLines = 1)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = C.Muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(display(o)) }, onClick = { onSelect(o); open = false })
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
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    outlined: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "btn")
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        interactionSource = source,
        modifier = modifier.fillMaxWidth().height(52.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(12.dp),
        colors = if (outlined) ButtonDefaults.buttonColors(containerColor = C.Card, contentColor = color)
        else ButtonDefaults.buttonColors(containerColor = color),
        border = if (outlined) androidx.compose.foundation.BorderStroke(1.5.dp, color) else null,
    ) {
        if (loading) {
            CircularProgressIndicator(color = if (outlined) color else Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = C.Text2, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = C.Brand, uncheckedTrackColor = C.Surface3),
        )
    }
}

@Composable
fun InfoNote(text: String, icon: ImageVector = Icons.Filled.Info, color: Color = C.Brand, bg: Color = C.BrandFaint, fg: Color = C.OnBrandFaint) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = fg, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

/** A tappable row with an outlined icon, title, subtitle and arrow (used in Admin, Reports, Tools). */
@Composable
fun LinkRow(
    icon: ImageVector,
    title: String,
    sub: String,
    color: Color = C.Brand,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(C.Card, RoundedCornerShape(12.dp))
            .border(1.dp, C.Line, RoundedCornerShape(12.dp))
            .pressable(onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .border(1.5.dp, color, RoundedCornerShape(8.dp))
                .background(C.Bg, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            if (sub.isNotBlank()) Text(sub, color = C.Muted, fontSize = 12.sp)
        }
        if (trailing != null) trailing() else Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = C.Muted)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String? = null, action: @Composable (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp).entrance(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).background(C.Surface3, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = C.Muted, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, color = C.Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun LoadError(message: String, onRetry: () -> Unit) {
    EmptyState(Icons.Outlined.CloudOff, t("common.loadFailed"), message) {
        BigButton(t("common.retry"), onClick = onRetry, modifier = Modifier.width(180.dp))
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
fun Loading(text: String = t("common.loading")) {
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

@Composable
fun Screen(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(C.Bg), content = content)
}

/** "Rs. 1,250.00"-style amount (old app used ₹ with 2 decimals on screens). */
fun money(v: Double?): String {
    if (v == null) return "—"
    return "₹" + String.format(java.util.Locale.US, "%.2f", v)
}

fun moneyText(v: Double?): String = if (v == null) "-" else "Rs. " + String.format(java.util.Locale.US, "%.2f", v)

/** "2026-09-26T10:15:00+00:00" → "26 Sep 2026" */
fun shortDate(iso: String): String {
    if (iso.length < 10) return iso
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val y = iso.substring(0, 4)
    val m = iso.substring(5, 7).toIntOrNull() ?: return iso
    val d = iso.substring(8, 10)
    return "$d ${months.getOrElse(m - 1) { "" }} $y"
}

/** "2026-09-26T10:15:00.123+00:00" (server time, UTC) → "26 Sep 2026, 15:45" in the phone's time zone */
fun dateTime(iso: String): String {
    if (iso.length < 19) return shortDate(iso)
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = parser.parse(iso.substring(0, 19)) ?: return shortDate(iso)
        java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.ENGLISH).format(date)
    } catch (e: Exception) {
        shortDate(iso)
    }
}
