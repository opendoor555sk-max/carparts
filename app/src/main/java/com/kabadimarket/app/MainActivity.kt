package com.kabadimarket.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kabadimarket.app.data.Api
import com.kabadimarket.app.data.I18n
import com.kabadimarket.app.data.Session
import com.kabadimarket.app.data.User
import com.kabadimarket.app.ui.AppTheme
import com.kabadimarket.app.ui.BuyScreen
import com.kabadimarket.app.ui.C
import com.kabadimarket.app.ui.ChangePasswordScreen
import com.kabadimarket.app.ui.CustomerDetailScreen
import com.kabadimarket.app.ui.CustomerNewScreen
import com.kabadimarket.app.ui.CustomersScreen
import com.kabadimarket.app.ui.InventoryScreen
import com.kabadimarket.app.ui.LimitsScreen
import com.kabadimarket.app.ui.LoginScreen
import com.kabadimarket.app.ui.PartScreen
import com.kabadimarket.app.ui.PartsListScreen
import com.kabadimarket.app.ui.SellScreen
import com.kabadimarket.app.ui.SoonScreen
import com.kabadimarket.app.ui.SplashScreen
import com.kabadimarket.app.ui.TabsScreen
import com.kabadimarket.app.ui.ToastHost
import com.kabadimarket.app.ui.ToolsScreen
import com.kabadimarket.app.ui.VendorDetailScreen
import com.kabadimarket.app.ui.VendorNewScreen
import com.kabadimarket.app.ui.VendorsScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(applicationContext)
        I18n.init(applicationContext)
        setContent {
            AppTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(C.Bg)
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    App()
                    ToastHost()
                }
            }
        }
    }
}

/** All the screens of the app (same screens as the old app). */
sealed interface Route {
    data object Tabs : Route
    data object Inventory : Route
    data class Part(val partNumber: String) : Route
    data class Buy(val partNumber: String = "") : Route
    data class Sell(val partNumber: String = "", val unitId: String? = null) : Route
    data object Limits : Route
    data object Tools : Route
    data object Customers : Route
    data object CustomerNew : Route
    data class Customer(val id: String) : Route
    data object Vendors : Route
    data object VendorNew : Route
    data class Vendor(val id: String) : Route
    data class PartsList(val category: String) : Route
    data object ChangePassword : Route

    /** A section of the old app that is not rebuilt yet. */
    data class Soon(val title: String) : Route
}

class Entry(val id: Int, val route: Route)

/** Screen history. open() goes forward (slides in from the right), back() goes back. */
class Nav {
    private var nextId = 1
    val stack = mutableStateListOf(Entry(0, Route.Tabs))
    var forward by mutableStateOf(true)
        private set

    /** Selected bottom tab: 0 Home, 1 Reports, 2 Catalog, 3 Admin */
    var tab by mutableIntStateOf(0)

    val top: Entry get() = stack[stack.lastIndex]

    fun open(route: Route) {
        forward = true
        stack.add(Entry(nextId++, route))
    }

    /** Replace the current screen (e.g. after saving a new customer, open its page). */
    fun replace(route: Route) {
        forward = true
        stack[stack.lastIndex] = Entry(nextId++, route)
    }

    fun back() {
        forward = false
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Go back to a screen of the given kind, if it is in the history. */
    fun backTo(match: (Route) -> Boolean) {
        forward = false
        while (stack.size > 1 && !match(top.route)) stack.removeAt(stack.lastIndex)
    }

    fun home() {
        forward = false
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        tab = 0
    }
}

@Composable
fun App() {
    var user by remember { mutableStateOf(Session.user) }
    var splash by rememberSaveable { mutableStateOf(true) }
    val nav = remember { Nav() }

    LaunchedEffect(Unit) {
        delay(1700)
        splash = false
    }

    DisposableEffect(Unit) {
        Api.onUnauthorized = {
            Session.clear()
            user = null
        }
        onDispose { Api.onUnauthorized = null }
    }

    val logout = {
        Session.clear()
        nav.home()
        user = null
    }

    val stage = when {
        splash -> 0
        user == null -> 1
        else -> 2
    }
    Crossfade(targetState = stage, animationSpec = tween(450), label = "stage") { s ->
        when (s) {
            0 -> SplashScreen()
            1 -> LoginScreen(onLoggedIn = {
                nav.home()
                user = it
            })
            else -> user?.let { u -> MainArea(u, nav, logout) }
        }
    }
}

@Composable
private fun MainArea(u: User, nav: Nav, logout: () -> Unit) {
    BackHandler(enabled = nav.stack.size > 1) { nav.back() }
    BackHandler(enabled = nav.stack.size == 1 && nav.tab != 0) { nav.tab = 0 }

    // Keeps each screen's typed text / filters when you come back to it.
    val holder = rememberSaveableStateHolder()
    AnimatedContent(
        targetState = nav.top,
        contentKey = { it.id },
        transitionSpec = {
            if (nav.forward) {
                (slideInHorizontally(tween(320)) { it } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(320)) { -it / 4 } + fadeOut(tween(240)))
            } else {
                (slideInHorizontally(tween(320)) { -it / 4 } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(320)) { it } + fadeOut(tween(240)))
            }
        },
        label = "screens",
    ) { entry ->
        holder.SaveableStateProvider(entry.id) {
            Screen(u, nav, entry.route, logout)
        }
    }
}

@Composable
private fun Screen(u: User, nav: Nav, r: Route, logout: () -> Unit) {
    when (r) {
        Route.Tabs -> TabsScreen(u, nav, logout)
        Route.Inventory -> InventoryScreen(u, nav)
        is Route.Part -> PartScreen(u, nav, r.partNumber)
        is Route.Buy -> BuyScreen(u, nav, r.partNumber)
        is Route.Sell -> SellScreen(u, nav, r.partNumber, r.unitId)
        Route.Limits -> LimitsScreen(nav)
        Route.Tools -> ToolsScreen(u, nav)
        Route.Customers -> CustomersScreen(nav)
        Route.CustomerNew -> CustomerNewScreen(nav)
        is Route.Customer -> CustomerDetailScreen(nav, r.id)
        Route.Vendors -> VendorsScreen(nav)
        Route.VendorNew -> VendorNewScreen(nav)
        is Route.Vendor -> VendorDetailScreen(u, nav, r.id)
        is Route.PartsList -> PartsListScreen(u, nav, r.category)
        Route.ChangePassword -> ChangePasswordScreen(nav)
        is Route.Soon -> SoonScreen(nav, r.title)
    }
}
