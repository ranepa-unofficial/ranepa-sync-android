package com.qteb.ranepa.sync.system

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.qteb.ranepa.sync.BuildConfig
import com.qteb.ranepa.sync.core.Keys
import com.qteb.ranepa.sync.core.Store
import com.qteb.ranepa.sync.lk.LkError
import com.qteb.ranepa.sync.sync.SyncTrigger
import com.qteb.ranepa.sync.widget.LessonsReceiver
import com.qteb.ranepa.sync.widget.NextLessonReceiver
import com.telemetrydeck.sdk.TelemetryDeck
import java.io.IOException
import java.time.LocalDate

/**
 * Анонимная статистика через TelemetryDeck: сколько пользователей, как часто
 * открывают приложение и какие экраны. Схема событий общая с iOS-версией.
 * SDK запускается только на экране — фон, виджеты и будильники ничего не шлют.
 * Без App ID в сборке, в демо и с выключенным переключателем статистики нет.
 */
object Analytics {
    /** В сборке есть App ID: только тогда показываем переключатель. */
    val available: Boolean get() = BuildConfig.TELEMETRYDECK_APP_ID.isNotBlank()

    fun enabled(d: Store): Boolean = available && !d.demo && d.bool(Keys.ANALYTICS, true)

    /** Приложение на экране. Сессию и новую установку SDK отмечает сам, раз в день — настройки. */
    fun foreground(context: Context) {
        val d = Store(context)
        if (!enabled(d)) return stop()
        TelemetryDeck.start(
            context.applicationContext,
            TelemetryDeck.Builder().appID(BuildConfig.TELEMETRYDECK_APP_ID).testMode(BuildConfig.DEBUG),
        )
        val today = LocalDate.now().toString()
        if (dailyDue(d.string(Keys.ANALYTICS_DAY), today)) {
            send(context, "Settings.daily", settings(context, d))
            d.setString(Keys.ANALYTICS_DAY, today)
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        Store(context).setBool(Keys.ANALYTICS, enabled)
        if (enabled) foreground(context) else stop()
    }

    fun stop() = TelemetryDeck.stop()

    fun send(context: Context, name: String, params: Map<String, String> = emptyMap()) {
        if (!enabled(Store(context)) || TelemetryDeck.getInstance() == null) return
        TelemetryDeck.signal(name, params)
    }

    fun screen(context: Context, screen: String) = send(context, "Screen.opened", mapOf("screen" to screen))

    fun syncFailed(context: Context, error: Throwable, trigger: SyncTrigger) =
        send(context, "Sync.failed", mapOf("reason" to reason(error), "trigger" to trigger.name.lowercase()))

    /** Настройки уходят не чаще раза в календарный день по часам телефона. */
    fun dailyDue(lastDay: String?, today: String): Boolean = lastDay != today

    fun reason(error: Throwable): String = when (error) {
        is LkError.NeedLogin -> "session"
        is LkError.ServerBusy -> "server"
        is LkError.Network, is IOException -> "network"
        else -> "other"
    }

    fun settingsParams(reminderMinutes: Int, notifications: Boolean, calendar: Boolean, liveUpdate: Boolean,
                       widgets: Boolean, themeMode: Int): Map<String, String> = mapOf(
        "reminders" to if (reminderMinutes > 0) "$reminderMinutes" else "off",
        "notifications" to "$notifications",
        "calendar" to "$calendar",
        "liveUpdate" to "$liveUpdate",
        "widgets" to "$widgets",
        "theme" to when (themeMode) { 1 -> "light"; 2 -> "dark"; else -> "auto" },
    )

    private fun settings(context: Context, d: Store): Map<String, String> {
        val manager = AppWidgetManager.getInstance(context)
        val widgets = listOf(NextLessonReceiver::class.java, LessonsReceiver::class.java).any {
            manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty()
        }
        return settingsParams(d.reminderMinutes, Notifications.allowed(context), d.bool(Keys.CALENDAR_EXPORT),
            d.liveUpdate, widgets, d.int(Keys.THEME_MODE))
    }
}
