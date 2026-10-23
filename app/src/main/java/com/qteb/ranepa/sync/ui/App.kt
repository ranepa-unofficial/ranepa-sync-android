package com.qteb.ranepa.sync.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.qteb.ranepa.sync.AppState
import com.qteb.ranepa.sync.RootTab
import com.qteb.ranepa.sync.core.Clock
import com.qteb.ranepa.sync.core.Store
import com.qteb.ranepa.sync.sync.SyncTrigger

/** Переходы, доступные любому экрану и карточке пары. */
class Routes(private val nav: NavHostController?, private val state: AppState?) {
    fun subject(name: String) = go("subject/${Uri.encode(name)}")
    fun lecturer(name: String) = go("lecturer/${Uri.encode(name)}")
    fun plan() = go("plan")

    private fun go(route: String) {
        // Из карточки пары: лист закрывается, экран открывается поверх вкладки.
        state?.sheetLesson = null
        nav?.navigate(route) { launchSingleTop = true }
    }
}

val LocalRoutes = staticCompositionLocalOf { Routes(null, null) }

@Composable
fun App(state: AppState) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (state.loggedIn) state.resumed() else state.checkNotifications() }
    if (state.loggedIn) MainFlow(state) else AuthFlow(state)

    state.loginFailure?.let { failure ->
        AlertDialog(
            onDismissRequest = { state.loginFailure = null },
            title = { Text(failure.title) },
            text = { Text(failure.message) },
            confirmButton = { TextButton(onClick = { state.loginFailure = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun AuthFlow(state: AppState) {
    val nav = rememberNavController()
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = "onboarding") {
            composable("onboarding") { OnboardingScreen(state) { nav.navigate("login") } }
            composable("login") { LoginScreen(state, relogin = false, onDone = {}, onBack = { nav.popBackStack() }) }
        }
    }
}

private data class TabItem(val tab: RootTab, val title: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    TabItem(RootTab.SCHEDULE, "Расписание", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    TabItem(RootTab.SUBJECTS, "Предметы", Icons.Outlined.LibraryBooks, Icons.Filled.LibraryBooks),
    TabItem(RootTab.SESSION, "Сессия", Icons.Outlined.School, Icons.Filled.School),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainFlow(state: AppState) {
    val nav = rememberNavController()
    val routes = remember(nav) { Routes(nav, state) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Разрешение нужно и для уведомлений об изменениях, не только для напоминаний. Спрашиваем один раз.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { state.checkNotifications() }
    LaunchedEffect(Unit) {
        val d = Store(context)
        if (Build.VERSION.SDK_INT >= 33 && !d.bool("askedNotifications") && !Clock.screenshotMode) {
            d.setBool("askedNotifications", true)
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        state.refresh(SyncTrigger.OPEN)
        state.showLiveUpdate()
    }

    CompositionLocalProvider(LocalRoutes provides routes) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= wideWidth
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                NavHost(nav, startDestination = "tabs") {
                    composable("tabs") { Tabs(state, wide, nav) }
                    composable("subject/{name}") { SubjectScreen(state, it.arguments?.getString("name").orEmpty()) { nav.popBackStack() } }
                    composable("lecturer/{name}") { LecturerScreen(state, it.arguments?.getString("name").orEmpty()) { nav.popBackStack() } }
                    composable("plan") { PlanScreen(state) { nav.popBackStack() } }
                    composable("search") { SearchScreen(state) { nav.popBackStack() } }
                    composable("account") { AccountScreen(state, onBack = { nav.popBackStack() }, onAbout = { nav.navigate("about") }) }
                    composable("about") {
                        AboutScreen(onBack = { nav.popBackStack() }, onLegend = { nav.navigate("legend") }, onPrivacy = { nav.navigate("privacy") })
                    }
                    composable("legend") { LegendScreen { nav.popBackStack() } }
                    composable("privacy") { PrivacyScreen { nav.popBackStack() } }
                    composable("relogin") {
                        LoginScreen(state, relogin = true, onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
                    }
                }
            }

            // Карточка пары листом снизу. На широком экране расписания она рядом с лентой.
            val lesson = state.sheetLesson
            val inPane = wide && state.tab == RootTab.SCHEDULE && nav.currentDestination?.route == "tabs" && state.sheetFrom == null
            if (lesson != null && !inPane) {
                val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
                ModalBottomSheet(
                    onDismissRequest = { state.sheetLesson = null },
                    sheetState = sheet,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    LessonSheetContent(lesson, onClose = { state.sheetLesson = null }, from = state.sheetFrom)
                }
            }
        }
    }
}

@Composable
private fun Tabs(state: AppState, wide: Boolean, nav: NavHostController) {
    val now = Clock.now
    val session = state.session
    // Во время сессии на значке — сколько осталось сдавать.
    val badge = session?.takeIf { it.isRunning(now) }?.remaining(now) ?: 0
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            tabs.forEach { item ->
                val selected = state.tab == item.tab
                item(
                    selected = selected,
                    onClick = { state.tab = item.tab },
                    icon = {
                        if (item.tab == RootTab.SESSION && badge > 0) {
                            BadgedBox(badge = { Badge { Text("$badge") } }) { Icon(if (selected) item.selectedIcon else item.icon, null) }
                        } else {
                            Icon(if (selected) item.selectedIcon else item.icon, null)
                        }
                    },
                    label = { Text(item.title) },
                )
            }
        },
    ) {
        val onSearch = { nav.navigate("search") { launchSingleTop = true } }
        val onAccount = { nav.navigate("account") { launchSingleTop = true } }
        // Вкладки без своей панели: шапка начинается под строкой состояния.
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            when (state.tab) {
                RootTab.SCHEDULE -> HomeScreen(state, wide, onSearch, onAccount, onRelogin = { nav.navigate("relogin") })
                RootTab.SUBJECTS -> SubjectsScreen(state, onSearch, onAccount)
                RootTab.SESSION -> SessionScreen(state, onSearch, onAccount)
            }
        }
    }
}
