package com.qteb.ranepa.sync.ui

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Intent
import android.net.Uri
import com.qteb.ranepa.sync.AppState
import com.qteb.ranepa.sync.LoginBounced
import com.qteb.ranepa.sync.LoginFailure
import com.qteb.ranepa.sync.LoginTimeout
import com.qteb.ranepa.sync.R
import com.qteb.ranepa.sync.core.Clock
import com.qteb.ranepa.sync.core.monday
import com.qteb.ranepa.sync.lk.LkWebView
import com.qteb.ranepa.sync.lk.ScheduleTemplate
import com.qteb.ranepa.sync.sync.EMPTY_WEEKS_TO_STOP
import com.qteb.ranepa.sync.sync.SEMESTER_WEEKS
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/** Стартовый экран до входа: что это за приложение и куда уходит пароль. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(state: AppState, onLogin: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.weight(1f).widthIn(max = 520.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painterResource(R.drawable.logo), null,
                Modifier.size(96.dp).clip(RoundedCornerShape(30.dp))
                    // Долгое нажатие: демо без ЛК для проверки магазинами приложений и скриншотов.
                    .combinedClickable(onClick = {}, onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        state.startDemo()
                    }),
            )
            Text("Расписание студента", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp, bottom = 36.dp))
            Feature(Icons.Outlined.CalendarMonth, "Расписание", "Пары до конца семестра, без интернета")
            Feature(Icons.Outlined.LibraryBooks, "Предметы", "Преподаватели, аудитории и все пары по предмету")
            Feature(Icons.Outlined.School, "Сессия", "Экзамены и зачёты по плану обучения")
            Feature(Icons.Outlined.Lock, "Вход на сайте ЛК", "Логин и пароль приложение не видит")
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 12.dp))
        }
        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp).height(56.dp)) {
            Text("Войти через ЛК", style = MaterialTheme.typography.titleMedium)
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Неофициальное приложение · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(Links.REPO))) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("Открытый код", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Вход идёт на настоящей странице ЛК: пароль вводится на сайте, приложение
 * его не видит и не хранит. [relogin] — повторный вход поверх расписания.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(state: AppState, relogin: Boolean, onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lk = remember { LkWebView(context) }
    var pageLoading by remember { mutableStateOf(true) }
    var sawLoginForm by remember { mutableStateOf(false) }
    var fetching by remember { mutableStateOf(false) }
    var requested by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<LoginFailure?>(null) }
    var timeout by remember { mutableStateOf<Job?>(null) }

    /** Возвращает на стартовый экран, а он покажет, что случилось. */
    fun fail(error: Throwable) {
        timeout?.cancel()
        if (failed) return
        failed = true
        fetching = false
        if (relogin) failure = LoginFailure.of(error) else {
            state.loginFailure = LoginFailure.of(error)
            onBack()
        }
    }

    fun showLoading() {
        if (fetching) return
        fetching = true
        timeout?.cancel()
        timeout = scope.launch {
            delay(60_000)
            if (!requested) fail(LoginTimeout())
        }
    }

    fun onTemplate(template: ScheduleTemplate) {
        if (requested) return
        requested = true
        showLoading()
        scope.launch {
            try {
                val monday: Instant = Clock.now.monday
                val result = lk.fetch(template, monday, SEMESTER_WEEKS, stopAfterEmpty = EMPTY_WEEKS_TO_STOP,
                    withPhoto = true, withPlan = true)
                timeout?.cancel()
                state.completeLogin(result, monday)
                onDone()
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    DisposableEffect(lk) {
        lk.onLoading = { pageLoading = it }
        lk.onUrl = { url ->
            if (LkWebView.isLoginPage(url)) {
                sawLoginForm = true
                if (fetching && !requested) fail(LoginBounced())
            } else if (sawLoginForm) {
                // Ушли с формы входа — значит, вход прошёл. Сайт дальше грузится под
                // экраном загрузки: нам нужен только его запрос расписания.
                showLoading()
            }
        }
        lk.onFailure = { fail(it) }
        lk.onTemplate = { onTemplate(it) }
        lk.load()
        onDispose {
            timeout?.cancel()
            lk.destroy()
        }
    }
    BackHandler(enabled = !fetching && lk.webView.canGoBack()) { lk.webView.goBack() }

    Scaffold(topBar = {
        if (!fetching) BackBar("Вход в ЛК", onBack)
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(factory = { lk.webView }, modifier = Modifier.fillMaxSize())
            if (pageLoading && !fetching) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (fetching) {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Загружаю расписание…", style = MaterialTheme.typography.titleMedium)
                    Text("Обычно это занимает несколько секунд", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    failure?.let { f ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { failure = null; onBack() },
            title = { Text(f.title) },
            text = { Text(f.message) },
            confirmButton = { TextButton(onClick = { failure = null; onBack() }) { Text("OK") } },
        )
    }
}
