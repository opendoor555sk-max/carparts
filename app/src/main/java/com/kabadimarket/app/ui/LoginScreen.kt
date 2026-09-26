package com.kabadimarket.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabadimarket.app.R
import com.kabadimarket.app.data.Api
import com.kabadimarket.app.data.ApiException
import com.kabadimarket.app.data.Session
import com.kabadimarket.app.data.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun LoginScreen(onLoggedIn: (User) -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var slow by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // If login takes long, the free server is probably waking up — tell the user.
    LaunchedEffect(loading) {
        slow = false
        if (loading) {
            delay(5000)
            slow = true
        }
    }

    fun doLogin() {
        if (username.isBlank() || password.isBlank()) {
            error = "Enter username and password"
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
                error = e.message
            } catch (e: Exception) {
                error = "Login failed: ${e.message}"
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
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(40.dp))
        Image(
            painter = painterResource(R.mipmap.ic_launcher),
            contentDescription = null,
            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(20.dp)),
        )
        Spacer(Modifier.height(16.dp))
        Text("Kabadi Market", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = C.Text)
        Text("Auto parts stock & billing", fontSize = 14.sp, color = C.Muted)
        Spacer(Modifier.height(28.dp))

        Card {
            Field(username, { username = it }, "Username")
            Field(
                password, { password = it }, "Password",
                password = true, imeAction = ImeAction.Go, onIme = { doLogin() },
            )
            ErrorBox(error)
            Spacer(Modifier.height(6.dp))
            BigButton("Login", onClick = { doLogin() }, loading = loading)
            if (slow) {
                Text(
                    "Server is waking up… first login can take up to 1 minute.",
                    color = C.Amber,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Use the same username & password as the old app.", color = C.Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}
