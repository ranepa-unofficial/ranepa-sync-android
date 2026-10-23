package com.qteb.ranepa.sync.system

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.qteb.ranepa.sync.R
import com.qteb.ranepa.sync.core.Clock
import com.qteb.ranepa.sync.core.Lesson
import com.qteb.ranepa.sync.core.Store
import com.qteb.ranepa.sync.core.capitalizedFirst
import com.qteb.ranepa.sync.core.hm
import com.qteb.ranepa.sync.core.sameDay
import java.time.Duration
import java.time.Instant

/**
 * Что Live Update должен показывать сейчас. Без побочных эффектов — для тестов.
 * [lessons] — по настоящим часам, отменённые уже убраны.
 * [running] — уведомление уже видно: держим его до конца дня.
 * [reminderMinutes] — 0, если напоминания выключены: тогда без появления
 * по расписанию, только из открытого приложения.
 */
class LiveUpdatePlan(lessons: List<Lesson>, now: Instant, running: Boolean, reminderMinutes: Int) {
    data class State(
        /** Идущая пара или ближайшая, если сейчас перемена. */
        val lesson: Lesson,
        /** Следующая после неё сегодня. */
        val next: Lesson?,
        val isCurrent: Boolean,
    )

    data class Scheduled(val state: State, val at: Instant)

    /** Пары оставшейся части дня; null — сегодня показывать нечего. */
    var today: State? = null
        private set
    /** Показать сейчас (если приложение открыто): пара идёт или скоро. */
    var startNow = false
        private set
    /** Появление вместе с напоминанием о первой паре дня. */
    var scheduled: Scheduled? = null
        private set

    companion object {
        /** Показываем, когда пара идёт или начнётся в эти 2 часа. */
        val lead: Duration = Duration.ofHours(2)
    }

    init {
        val upcoming = lessons.filter { it.end > now }.sortedBy { it.start }
        val todays = upcoming.filter { it.start.sameDay(now) }
        todays.firstOrNull()?.let { first ->
            today = State(first, todays.getOrNull(1), first.start <= now)
            startNow = !running && Duration.between(now, first.start) <= lead
        }
        val started = running || startNow
        // Сегодняшние пары ещё не начались — по расписанию появится сегодня,
        // иначе — в следующий день с парами.
        val day = if (todays.isNotEmpty() && !started) todays else {
            val later = upcoming.filter { !it.start.sameDay(now) }
            later.filter { it.start.sameDay(later.firstOrNull()?.start ?: now) }
        }
        val first = day.firstOrNull()
        if (reminderMinutes > 0 && first != null) {
            val at = first.start.minusSeconds(reminderMinutes * 60L)
            if (at > now.plusSeconds(60)) scheduled = Scheduled(State(first, day.getOrNull(1), false), at)
        }
    }
}

/**
 * Идущая или ближайшая пара в строке состояния и на экране блокировки.
 * На Android 16 и новее — Live Update: чип с обратным отсчётом в строке
 * состояния, на старых версиях — обычное постоянное уведомление.
 * Будильники обновляют его на границах пар и убирают после последней.
 */
object LiveUpdates {
    const val ACTION = "com.qteb.ranepa.sync.LIVE"
    const val EXTRA_ALERT = "alert"
    private const val ID = 100
    private const val CODE = 4000
    /** Начало пары, о которой напомнит появление Live Update, по настоящим часам. */
    private const val SCHEDULED_KEY = "liveScheduledLesson"
    /** Как часто во время пары двигается полоска прогресса. */
    private val progressStep: Duration = Duration.ofMinutes(5)

    /** Начало пары (по часам приложения), о которой напомнит появление
     * Live Update: обычное напоминание о ней не нужно. */
    fun scheduledLesson(context: Context): Instant? =
        Store(context).time(SCHEDULED_KEY)?.plus(Clock.shift)

    private fun running(context: Context): Boolean =
        context.getSystemService<NotificationManager>()?.activeNotifications?.any { it.id == ID } ?: false

    /**
     * [appOpen] — приложение открыто: показать, если пара идёт или скоро.
     * [alert] — сработал будильник появления: со звуком, как напоминание.
     */
    fun update(context: Context, lessons: List<Lesson>, appOpen: Boolean = false, alert: Boolean = false) {
        val d = Store(context)
        val enabled = d.loggedIn && d.liveUpdate
        // Таймеры уведомления идут по настоящим часам — убираем сдвиг скриншотов.
        val real = if (enabled) lessons.filter { !it.cancelled }
            .map { it.copy(start = it.start.minus(Clock.shift), end = it.end.minus(Clock.shift)) } else emptyList()
        val now = Instant.now()
        val running = running(context)
        val plan = LiveUpdatePlan(real, now, running, d.reminderMinutes)

        val state = plan.today
        val shown = when {
            state == null -> false
            running -> true
            plan.startNow && (appOpen || alert) -> true
            else -> false
        }
        if (state != null && shown) {
            show(context, state, now, alert && !running)
        } else {
            NotificationManagerCompat.from(context).cancel(ID)
        }

        // Следующее пробуждение: граница пары, пока уведомление видно, раз
        // в 5 минут во время пары (полоска прогресса) и появление вместе
        // с напоминанием.
        val boundaries = if (shown) real.filter { it.start.sameDay(now) }.flatMap { listOf(it.start, it.end) }.filter { it > now } else emptyList()
        val tick = if (shown && state?.isCurrent == true) now.plus(progressStep) else null
        val scheduledAt = plan.scheduled?.at
        val wake = (boundaries + listOfNotNull(scheduledAt, tick)).minOrNull()
        Alarms.existing(context, ACTION, CODE)?.let { Alarms.cancel(context, it) }
        if (wake != null) {
            Alarms.set(context, wake, Alarms.broadcast(context, ACTION, CODE) {
                putExtra(EXTRA_ALERT, wake == scheduledAt)
            })
        }

        // Напоминание о паре с появлением по расписанию — уже в нём.
        val scheduledLesson = plan.scheduled?.state?.lesson?.start
        if (scheduledLesson != d.time(SCHEDULED_KEY)) {
            d.setTime(SCHEDULED_KEY, scheduledLesson)
            if (enabled) Notifications.scheduleReminders(context, lessons, d.reminderMinutes)
        }
    }

    private fun show(context: Context, state: LiveUpdatePlan.State, now: Instant, alert: Boolean) {
        val lesson = state.lesson
        val details = listOf(lesson.kind.capitalizedFirst(), lesson.shortLocation).filter { it.isNotEmpty() }.joinToString(" · ")
        val title: String
        val text: String
        val target: Instant
        if (state.isCurrent) {
            title = lesson.subject
            text = "$details · до ${hm(lesson.end.plus(Clock.shift))}"
            target = lesson.end
        } else {
            title = lesson.subject
            text = "$details · в ${hm(lesson.start.plus(Clock.shift))}"
            target = lesson.start
        }
        val next = state.next?.let { "Потом: ${it.subject}, ${hm(it.start.plus(Clock.shift))} · ${it.shortLocation}" }
        val builder = NotificationCompat.Builder(context, Notifications.CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(text, next).joinToString("\n")))
            .setOngoing(true)
            .setOnlyAlertOnce(!alert)
            .setSilent(!alert)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(target.toEpochMilli())
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setContentIntent(Notifications.openLesson(context, lesson.start, ID))
            // Android 16: просим показать как Live Update — чипом в строке состояния.
            .addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
        if (state.isCurrent) {
            val total = Duration.between(lesson.start, lesson.end).seconds.toInt().coerceAtLeast(1)
            val done = Duration.between(lesson.start, now).seconds.toInt().coerceIn(0, total)
            builder.setProgress(total, done, false)
        }
        if (!Notifications.allowed(context)) return
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(ID, builder.build())
    }

    fun cancel(context: Context) {
        Alarms.existing(context, ACTION, CODE)?.let { Alarms.cancel(context, it) }
        NotificationManagerCompat.from(context).cancel(ID)
        Store(context).remove(SCHEDULED_KEY)
    }
}
