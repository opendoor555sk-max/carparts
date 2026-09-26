package com.kabadimarket.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabadimarket.app.data.Api
import com.kabadimarket.app.data.ApiException
import com.kabadimarket.app.data.I18n
import com.kabadimarket.app.data.Session
import com.kabadimarket.app.data.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Opening screen: animated logo + name. */
@Composable
fun SplashScreen() {
    val textIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(450)
        textIn.animateTo(1f, tween(600))
    }
    Column(
        Modifier.fillMaxSize().background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedLogo(180.dp)
        Spacer(Modifier.height(24.dp))
        Text(
            "Auto Parts Store",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = C.Text,
            modifier = Modifier.graphicsLayer {
                alpha = textIn.value
                translationY = (1f - textIn.value) * 30f
            },
        )
        Text(
            "Kabadi Market",
            fontSize = 14.sp,
            color = C.Orange,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer { alpha = textIn.value },
        )
    }
}

@Composable
fun LoginScreen(onLoggedIn: (User) -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var slow by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val lang = I18n.lang // re-draw on language change

    LaunchedEffect(loading) {
        slow = false
        if (loading) {
            delay(5000)
            slow = true
        }
    }

    fun doLogin() {
        if (username.isBlank() || password.isBlank()) {
            error = t("login.errRequired")
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                val res = Api.post(
                    "/auth/login",
                    JSONObject().put("username", username.trim()).put("password", password),
                ) as JSONObject
                val userJson = res.getJSONObject("user")
                Session.save(res.getString("access_token"), userJson)
                onLoggedIn(User.from(userJson))
            } catch (e: ApiException) {
                error = if (e.code == "staff_pending_approval") t("login.pendingApproval") else e.message
            } catch (e: Exception) {
                error = t("login.errFailed")
            } finally {
                loading = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        AnimatedLogo(130.dp)
        Spacer(Modifier.height(10.dp))
        Text("Auto Parts Store", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
        Spacer(Modifier.height(28.dp))

        Column(Modifier.fillMaxWidth().entrance(2)) {
            Field(username, { username = it }, t("login.username").uppercase(), placeholder = t("login.username"))
            Field(
                password, { password = it }, t("login.password").uppercase(),
                placeholder = t("login.password"),
                password = !showPw, imeAction = ImeAction.Go, onIme = { doLogin() },
                trailing = {
                    IconButton(onClick = { showPw = !showPw }) {
                        Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null, tint = C.Muted)
                    }
                },
            )
            error?.let {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .background(C.Red, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(it, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(4.dp))
            BigButton(t("login.signIn"), onClick = { doLogin() }, loading = loading, icon = Icons.AutoMirrored.Filled.Login)
            if (slow) {
                Text(
                    I18n.x("wakeup"),
                    color = C.Amber,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            BigButton(
                t("login.createStore"),
                onClick = { Toast.show(I18n.x("soon.title")) },
                outlined = true,
                icon = Icons.Filled.Storefront,
            )
        }

        Spacer(Modifier.height(20.dp))
        // Language choice right on the login screen
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            I18n.LANGUAGES.forEach { (code, label) ->
                Chip(label, lang == code) { I18n.setLanguage(code) }
            }
        }
        Text(t("login.footer"), color = C.Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 16.dp))
    }
}
