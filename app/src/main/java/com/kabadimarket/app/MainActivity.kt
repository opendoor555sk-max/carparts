package com.kabadimarket.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kabadimarket.app.data.Api
import com.kabadimarket.app.data.Session
import com.kabadimarket.app.data.User
import com.kabadimarket.app.ui.AppTheme
import com.kabadimarket.app.ui.BuyScreen
import com.kabadimarket.app.ui.C
import com.kabadimarket.app.ui.HomeScreen
import com.kabadimarket.app.ui.InventoryScreen
import com.kabadimarket.app.ui.LoginScreen
import com.kabadimarket.app.ui.PartScreen
import com.kabadimarket.app.ui.SellScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(applicationContext)
        setContent {
            AppTheme {
                Box(Modifier.fillMaxSize().background(C.Bg)) {
                    App()
                }
            }
        }
    }
}

/** All the screens of the app. */
sealed interface Route {
    data object Home : Route
    data object Inventory : Route
    data class Part(val partNumber: String) : Route
    data class Buy(val partNumber: String = "") : Route
    data class Sell(val partNumber: String = "", val unitId: String? = null) : Route
}

/** Simple screen history: open() goes forward, back() goes back. */
class Nav {
    val stack = mutableStateListOf<Route>(Route.Home)
    val current: Route get() = stack[stack.lastIndex]

    fun open(route: Route) {
        stack.add(route)
    }

    /** Replace the current screen (used after finishing a Buy/Sell). */
    fun replace(route: Route) {
        stack[stack.lastIndex] = route
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun home() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}

@Composable
fun App() {
    var user by remember { mutableStateOf(Session.user) }
    val nav = remember { Nav() }

    DisposableEffect(Unit) {
        Api.onUnauthorized = {
            Session.clear()
            user = null
        }
        onDispose { Api.onUnauthorized = null }
    }

    val u: User? = user
    if (u == null) {
        LoginScreen(onLoggedIn = {
            nav.home()
            user = it
        })
        return
    }

    BackHandler(enabled = nav.stack.size > 1) { nav.back() }

    // Keeps each screen's typed text / filters when you come back to it.
    val holder = rememberSaveableStateHolder()
    val r = nav.current
    holder.SaveableStateProvider(key = "${nav.stack.lastIndex}:$r") {
        when (r) {
            Route.Home -> HomeScreen(u, nav, onLogout = {
                Session.clear()
                user = null
            })
            Route.Inventory -> InventoryScreen(u, nav)
            is Route.Part -> PartScreen(u, nav, r.partNumber)
            is Route.Buy -> BuyScreen(u, nav, r.partNumber)
            is Route.Sell -> SellScreen(u, nav, r.partNumber, r.unitId)
        }
    }
}
