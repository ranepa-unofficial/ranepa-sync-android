package com.qteb.ranepa.sync

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.qteb.ranepa.sync.core.Clock
import com.qteb.ranepa.sync.core.Store
import com.qteb.ranepa.sync.sync.Background
import com.qteb.ranepa.sync.system.Analytics
import com.qteb.ranepa.sync.system.AppVisibility
import com.qteb.ranepa.sync.system.Notifications
import com.qteb.ranepa.sync.system.TodayTile
import com.qteb.ranepa.sync.ui.App
import com.qteb.ranepa.sync.ui.theme.RanepaTheme
import java.time.Instant

class RanepaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Часы для скриншотов задаются в отладке и переживают перезапуск.
        if (BuildConfig.DEBUG) Clock.setDemoAt(Store(this).string(DEMO_AT_KEY))
        Notifications.createChannels(this)
        val d = Store(this)
        // Задача могла не встать (обновление приложения, очистка данных WorkManager).
        if (d.loggedIn && !d.demo) Background.schedule(this)
    }

    companion object {
        /** «2026-10-13T11:05» — перевести часы приложения для скриншотов. */
        const val DEMO_AT_KEY = "DEMO_AT"
    }
}

class MainActivity : ComponentActivity() {
    private val state: AppState by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent {
            RanepaTheme(mode = state.themeMode) {
                App(state)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.foreground = true
        Analytics.foreground(this)
    }

    override fun onStop() {
        AppVisibility.foreground = false
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Нажали на напоминание, Live Update или виджет: открыть пару. */
    private fun handle(intent: Intent?) {
        val seconds = intent?.getLongExtra(Notifications.EXTRA_LESSON, 0L) ?: 0L
        if (seconds > 0) {
            state.pendingLesson = Instant.ofEpochSecond(seconds).plus(Clock.shift)
            intent?.removeExtra(Notifications.EXTRA_LESSON)
        }
        // Плитка в быстрых настройках: расписание на сегодня.
        if (intent?.getBooleanExtra(TodayTile.EXTRA_TODAY, false) == true) {
            state.pendingToday = true
            intent.removeExtra(TodayTile.EXTRA_TODAY)
        }
    }
}
