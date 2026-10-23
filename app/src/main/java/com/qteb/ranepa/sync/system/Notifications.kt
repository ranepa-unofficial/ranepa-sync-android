package com.qteb.ranepa.sync.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.qteb.ranepa.sync.MainActivity
import com.qteb.ranepa.sync.R
import com.qteb.ranepa.sync.core.Clock
import com.qteb.ranepa.sync.core.ExamSession
import com.qteb.ranepa.sync.core.Lesson
import com.qteb.ranepa.sync.core.StudyPlan
import com.qteb.ranepa.sync.core.addingDays
import com.qteb.ranepa.sync.core.at
import com.qteb.ranepa.sync.core.capitalizedFirst
import com.qteb.ranepa.sync.core.durationText
import com.qteb.ranepa.sync.core.hm
import java.time.Duration
import java.time.Instant

/** Напоминания о парах и служебные уведомления. */
object Notifications {
    const val CHANNEL_LESSONS = "lessons"
    const val CHANNEL_EXAMS = "exams"
    const val CHANNEL_CHANGES = "changes"
    const val CHANNEL_SESSION = "session"
    const val CHANNEL_LIVE = "live"

    /** Начало пары в намерении: нажатие открывает её карточку. */
    const val EXTRA_LESSON = "lesson"
    const val EXTRA_TITLE = "title"
    const val EXTRA_BODY = "body"
    const val EXTRA_CHANNEL = "channel"

    const val ACTION_REMINDER = "com.qteb.ranepa.sync.REMINDER"
    const val ACTION_KEEP_ALIVE = "com.qteb.ranepa.sync.KEEP_ALIVE"

    /** Тег показанных напоминаний: по нему убираем те, чья пара уже началась. */
    private const val TAG_LESSON = "lesson"
    private const val ID_CHANGES = 1
    private const val ID_SESSION = 2
    private const val ID_KEEP_ALIVE = 3
    private const val ID_TEST = 4

    /** Коды будильников: напоминания о парах, о сессии, «открой приложение». */
    private const val CODE_LESSONS = 1000
    private const val MAX_SCHEDULED = 52
    private const val CODE_EXAMS = 2000
    private const val MAX_EXAMS = 8
    private const val CODE_KEEP_ALIVE = 3000

    /** Через сколько после последнего обновления напомнить открыть приложение,
     * если срок сессии неизвестен. Refresh-токен без «Запомнить меня» живёт
     * 24 часа — нужен запас. */
    val keepAliveAfter: Duration = Duration.ofHours(20)
    /** За сколько до конца сессии ЛК напомнить, если её срок известен. */
    val keepAliveBefore: Duration = Duration.ofHours(4)

    /** Накануне зачёта или экзамена в это время — напоминание. */
    private const val EXAM_EVENING_HOUR = 19

    fun createChannels(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannels(listOf(
            NotificationChannel(CHANNEL_LESSONS, "Напоминания о парах", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_LIVE, "Текущая пара", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Идущая или ближайшая пара в строке состояния и на экране блокировки"
            },
            NotificationChannel(CHANNEL_EXAMS, "Экзамены и зачёты", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Вечером накануне каждого экзамена и зачёта"
            },
            NotificationChannel(CHANNEL_CHANGES, "Изменения в расписании", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Переносы, отмены, новые пары и другая аудитория"
            },
            NotificationChannel(CHANNEL_SESSION, "Вход в ЛК", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Когда сессия личного кабинета истекает"
            },
        ))
    }

    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Название, под ним «Лекция · аудитория», затем когда. */
    fun reminderText(lesson: Lesson, minutes: Int): Pair<String, String> =
        lesson.subject to listOf(
            listOf(lesson.kind.capitalizedFirst(), lesson.placeLabel).filter { it.isNotEmpty() }.joinToString(" · "),
            "Через ${durationText(minutes * 60L)}, в ${hm(lesson.start)}",
        ).joinToString("\n")

    /** Открыть приложение на паре, начинающейся в [start]. */
    fun openLesson(context: Context, start: Instant?, code: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (start != null) intent.putExtra(EXTRA_LESSON, start.epochSecond)
        return PendingIntent.getActivity(
            context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun builder(context: Context, channel: String, title: String, body: String) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle(title)
            .setContentText(body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)

    @Suppress("MissingPermission")
    private fun post(context: Context, tag: String?, id: Int, notification: android.app.Notification) {
        if (!allowed(context)) return
        NotificationManagerCompat.from(context).notify(tag, id, notification)
    }

    /** Показывает напоминание — вызывается будильником. */
    fun showReminder(context: Context, channel: String, title: String, body: String, start: Instant?) {
        val id = ((start?.epochSecond ?: Instant.now().epochSecond) / 60).toInt()
        val notification = builder(context, channel, title, body)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openLesson(context, start, id))
            // Напоминание о паре само исчезает, когда она начнётся.
            .apply { if (start != null && channel == CHANNEL_LESSONS) setTimeoutAfter(Duration.between(Instant.now(), start).toMillis().coerceAtLeast(1)) }
            .build()
        post(context, TAG_LESSON, id, notification)
    }

    /** Перепланирует напоминания: [minutes] до начала каждой пары, 0 — выключено. */
    fun scheduleReminders(context: Context, lessons: List<Lesson>, minutes: Int) {
        for (i in 0 until MAX_SCHEDULED) {
            Alarms.existing(context, ACTION_REMINDER, CODE_LESSONS + i)?.let { Alarms.cancel(context, it) }
        }
        removeStale(context)
        if (minutes <= 0) return
        val now = Instant.now()
        val shift = Clock.shift
        // О первой паре дня напомнит появление Live Update.
        val viaLive = LiveUpdates.scheduledLesson(context)
        lessons.asSequence()
            .filter { !it.cancelled && it.start != viaLive }
            .map { it to it.start.minus(shift).minusSeconds(minutes * 60L) }
            .filter { it.second > now }
            .take(MAX_SCHEDULED)
            .forEachIndexed { i, (lesson, at) ->
                val (title, body) = reminderText(lesson, minutes)
                Alarms.set(context, at, Alarms.broadcast(context, ACTION_REMINDER, CODE_LESSONS + i) {
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_BODY, body)
                    putExtra(EXTRA_CHANNEL, CHANNEL_LESSONS)
                    putExtra(EXTRA_LESSON, lesson.start.minus(shift).epochSecond)
                })
            }
    }

    /** Вечером накануне каждого зачёта и экзамена. Не зависит от напоминаний
     * о парах: их можно выключить, а про экзамен знать нужно. */
    fun scheduleExamReminders(context: Context, lessons: List<Lesson>, plan: StudyPlan?) {
        for (i in 0 until MAX_EXAMS) {
            Alarms.existing(context, ACTION_REMINDER, CODE_EXAMS + i)?.let { Alarms.cancel(context, it) }
        }
        val now = Instant.now()
        val shift = Clock.shift
        val session = ExamSession.of(lessons, plan, now.plus(shift)) ?: return
        session.all.mapNotNull { it.event }.take(MAX_EXAMS).forEachIndexed { i, exam ->
            // По настоящим часам: в сборке для скриншотов время сдвинуто.
            val start = exam.start.minus(shift)
            val at = start.addingDays(-1).at(EXAM_EVENING_HOUR)
            if (at <= now) return@forEachIndexed
            Alarms.set(context, at, Alarms.broadcast(context, ACTION_REMINDER, CODE_EXAMS + i) {
                putExtra(EXTRA_TITLE, "Завтра ${exam.kind}: ${exam.subject}")
                putExtra(EXTRA_BODY, "${hm(exam.start)} · ${if (exam.isRemote) "дистанционно" else exam.placeLabel}")
                putExtra(EXTRA_CHANNEL, CHANNEL_EXAMS)
                putExtra(EXTRA_LESSON, start.epochSecond)
            })
        }
    }

    /** Отладка: напоминание через 5 секунд — успеть заблокировать телефон. */
    fun showTest(context: Context, lesson: Lesson, minutes: Int) {
        val (title, body) = reminderText(lesson, minutes)
        Alarms.set(context, Instant.now().plusSeconds(5), Alarms.broadcast(context, ACTION_REMINDER, ID_TEST) {
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
            putExtra(EXTRA_CHANNEL, CHANNEL_LESSONS)
            putExtra(EXTRA_LESSON, lesson.start.minus(Clock.shift).epochSecond)
        })
    }

    /** Убирает показанные напоминания о парах, которые уже начались. */
    fun removeStale(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        val nowMinute = Instant.now().epochSecond / 60
        manager.activeNotifications
            .filter { it.tag == TAG_LESSON && it.id <= nowMinute }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    /** Одно уведомление на все изменения: первые строки и «и ещё N». */
    fun showChanges(context: Context, changes: List<String>) {
        if (changes.isEmpty()) return
        val shown = 3
        val lines = changes.take(shown).toMutableList()
        if (changes.size > shown) lines += "и ещё ${changes.size - shown}"
        val title = if (changes.size == 1) "Изменение в расписании" else "Изменения в расписании"
        post(context, null, ID_CHANGES, builder(context, CHANNEL_CHANGES, title, lines.joinToString("\n"))
            .setContentIntent(openLesson(context, null, ID_CHANGES))
            .build())
    }

    fun showSessionExpired(context: Context) {
        post(context, null, ID_SESSION, builder(context, CHANNEL_SESSION, "Расписание не обновляется",
            "Сессия ЛК истекла. Открой приложение и войди заново.")
            .setContentIntent(openLesson(context, null, ID_SESSION))
            .build())
    }

    fun showKeepAlive(context: Context) {
        post(context, null, ID_KEEP_ALIVE, builder(context, CHANNEL_SESSION, "Открой приложение",
            "Расписание давно не обновлялось. Открой приложение, иначе придётся заново входить в ЛК.")
            .setContentIntent(openLesson(context, null, ID_KEEP_ALIVE))
            .build())
    }

    /**
     * «Таймер мёртвой руки»: каждое успешное обновление переносит уведомление
     * на [keepAliveAfter] вперёд (или раньше, если токен истекает раньше).
     * Если Android перестал запускать фоновое обновление, пользователь узнает
     * об этом до того, как сессия истечёт.
     */
    fun scheduleKeepAlive(context: Context, lastSync: Instant, sessionUntil: Instant?) {
        cancelKeepAlive(context)
        val at = minOf(lastSync.plus(keepAliveAfter), sessionUntil?.minus(keepAliveBefore) ?: Instant.MAX)
        if (at <= Instant.now()) return
        Alarms.set(context, at, Alarms.broadcast(context, ACTION_KEEP_ALIVE, CODE_KEEP_ALIVE))
    }

    fun cancelKeepAlive(context: Context) {
        Alarms.existing(context, ACTION_KEEP_ALIVE, CODE_KEEP_ALIVE)?.let { Alarms.cancel(context, it) }
        NotificationManagerCompat.from(context).cancel(ID_KEEP_ALIVE)
    }

    fun cancelAll(context: Context) {
        for (i in 0 until MAX_SCHEDULED) {
            Alarms.existing(context, ACTION_REMINDER, CODE_LESSONS + i)?.let { Alarms.cancel(context, it) }
        }
        for (i in 0 until MAX_EXAMS) {
            Alarms.existing(context, ACTION_REMINDER, CODE_EXAMS + i)?.let { Alarms.cancel(context, it) }
        }
        cancelKeepAlive(context)
        NotificationManagerCompat.from(context).cancelAll()
    }
}
