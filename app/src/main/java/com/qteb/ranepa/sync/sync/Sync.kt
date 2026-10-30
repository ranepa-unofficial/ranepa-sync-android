package com.qteb.ranepa.sync.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.core.graphics.scale
import com.qteb.ranepa.sync.core.Clock
import com.qteb.ranepa.sync.core.Keys
import com.qteb.ranepa.sync.core.Lesson
import com.qteb.ranepa.sync.core.Store
import com.qteb.ranepa.sync.core.addingDays
import com.qteb.ranepa.sync.core.dateOnly
import com.qteb.ranepa.sync.core.describeChanges
import com.qteb.ranepa.sync.core.hour
import com.qteb.ranepa.sync.core.isoWeekday
import com.qteb.ranepa.sync.core.localDayStart
import com.qteb.ranepa.sync.core.monday
import com.qteb.ranepa.sync.core.parseLessons
import com.qteb.ranepa.sync.core.saveProgress
import com.qteb.ranepa.sync.core.sameDay
import com.qteb.ranepa.sync.core.str
import com.qteb.ranepa.sync.lk.FetchResult
import com.qteb.ranepa.sync.lk.LkError
import com.qteb.ranepa.sync.lk.LkWebView
import com.qteb.ranepa.sync.system.CalendarExport
import com.qteb.ranepa.sync.system.LiveUpdates
import com.qteb.ranepa.sync.system.Notifications
import com.qteb.ranepa.sync.widget.Widgets
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.Instant
import kotlin.math.min

/** Полная загрузка: месяц вперёд, то есть текущая неделя и ещё 4. Делается первым обновлением за день. */
const val FETCH_WEEKS = 5

/** Загрузка до конца семестра — при входе и раз в неделю. ЛК отдаёт
 * расписание вместе с сессией; 20 недель хватает на любой семестр. */
const val SEMESTER_WEEKS = 20

/** Столько пустых недель подряд — значит, семестр кончился. Меньше нельзя:
 * перед сессией бывают каникулы на пару недель. */
const val EMPTY_WEEKS_TO_STOP = 4

enum class SyncTrigger {
    BACKGROUND, OPEN, MANUAL,
    /** Кнопка в отладке: без проверок интервалов. */
    DEBUG,
}

enum class SkipReason {
    /** Пауза после ошибки сервера. */
    BACKOFF,
    /** С прошлого обновления прошло слишком мало времени. */
    TOO_SOON,
    /** Обновлять нечего: каникулы или данные свежие. */
    NOT_NEEDED,
    /** Сессия ЛК истекла — сначала войти заново. */
    NEED_LOGIN,
}

data class SyncResult(
    /** Изменения в ближайшие дни по сравнению с прошлой загрузкой. */
    val changes: List<String> = emptyList(),
    val sideEffectError: String? = null,
    /** Почему в ЛК не ходили; null — обновление было. */
    val skipped: SkipReason? = null,
)

/** Когда и сколько загружать из ЛК. */
object Sync {
    /** При открытии не пытаемся чаще, даже если прошлая попытка не удалась. */
    private val openRetryAfter = Duration.ofMinutes(15)
    /** «Потянуть вниз» чаще этого не делает запросов. */
    private val manualCooldown = Duration.ofMinutes(2)
    /** Допуск к интервалу фона: система запускает задачу с разбросом. */
    private val intervalSlack = Duration.ofMinutes(15)
    /** Пауза после ответа 429/5xx: 8 → 16 → 24 часа. */
    private val backoffBase = Duration.ofHours(4)
    private val backoffMax = Duration.ofHours(24)
    /** Фото весит под мегабайт — качаем его при входе и потом раз в неделю. */
    private val photoMaxAge = Duration.ofDays(7)

    /** На каникулах (пар в ближайшие 2 недели нет) — не чаще раза в 4 часа:
     * обновлять нечего, но сессию ЛК продлевать надо. */
    val holidayInterval: Duration = Duration.ofHours(4)

    private fun since(d: Store, key: String, now: Instant): Duration {
        // Отметка из будущего (часы переводили назад) — считаем давней,
        // иначе обновления встанут до этой даты.
        val t = d.time(key) ?: return Duration.ofDays(365)
        val gap = Duration.between(t, now)
        return if (gap.isNegative) Duration.ofDays(365) else gap
    }

    /** Обновление стоит на паузе после ошибки сервера. */
    fun paused(d: Store, now: Instant = Clock.now): Boolean = d.time(Keys.BACKOFF_UNTIL)?.let { it > now } ?: false

    /**
     * Как часто фон ходит в ЛК в это время. Чаще всего — в учебное время,
     * когда пары переносят и отменяют. Реже всего — ночью (раз в 4 часа)
     * и в воскресенье днём (раз в 12): каждое обновление продлевает сессию
     * ЛК, а она живёт сутки, так что совсем пропускать нельзя.
     */
    fun interval(now: Instant): Duration {
        val hour = now.hour
        if (now.isoWeekday == 7) {
            // Вечером чаще: вдруг отменят первую пару понедельника.
            return Duration.ofHours(if (hour >= 18) 3 else 12)
        }
        if (hour < 6) return Duration.ofHours(4)
        // Вечером — реже, но изменения на завтра ловим.
        if (hour >= 21) return Duration.ofHours(3)
        return Duration.ofHours(if (now.isoWeekday == 6) 2 else 1)
    }

    /** Когда фону стоит проснуться: первый момент, когда с [now] прошло не
     * меньше интервала, действующего в тот момент. */
    fun nextRun(now: Instant): Instant {
        var t = now
        repeat(13 * 4) {
            t = t.plus(Duration.ofMinutes(15))
            if (Duration.between(now, t) >= interval(t)) return t
        }
        return t
    }

    /** Почему не надо идти в ЛК, или null — надо. Отдельно от загрузки, чтобы
     * проверять правила тестами. */
    fun skipReason(d: Store, trigger: SyncTrigger, now: Instant): SkipReason? {
        d.time(Keys.BACKOFF_UNTIL)?.let { if (it > now) return SkipReason.BACKOFF }
        // Без сессии страница ЛК всё равно уйдёт на вход: сами не ходим,
        // а «потянуть вниз» пробует — вдруг вошли снова.
        if (d.bool(Keys.SESSION_EXPIRED) && (trigger == SyncTrigger.BACKGROUND || trigger == SyncTrigger.OPEN)) {
            return SkipReason.NEED_LOGIN
        }
        when (trigger) {
            SyncTrigger.BACKGROUND -> {
                val interval = if (lessonsSoon(d, now)) interval(now) else maxOf(interval(now), holidayInterval)
                if (since(d, Keys.LAST_ATTEMPT_AT, now) < interval.minus(intervalSlack)) return SkipReason.TOO_SOON
            }
            SyncTrigger.OPEN -> {
                // Свежими считаем данные моложе интервала фона, но не старше 3 часов.
                val stale = minOf(interval(now), Duration.ofHours(3))
                if (since(d, Keys.FETCHED_AT, now) < stale || since(d, Keys.LAST_ATTEMPT_AT, now) < openRetryAfter) {
                    return SkipReason.NOT_NEEDED
                }
            }
            SyncTrigger.MANUAL -> if (since(d, Keys.FETCHED_AT, now) < manualCooldown) return SkipReason.TOO_SOON
            SyncTrigger.DEBUG -> Unit
        }
        return null
    }

    /** Первое обновление за день — полное. */
    fun isFullDue(d: Store, now: Instant): Boolean {
        val last = d.time(Keys.LAST_FULL_AT) ?: return true
        return !last.sameDay(now)
    }

    /** Раз в неделю — весь семестр: дальние недели тоже меняются, но редко. */
    fun isSemesterDue(d: Store, now: Instant): Boolean = since(d, Keys.LAST_SEMESTER_AT, now) >= Duration.ofDays(7)

    /**
     * Решает, идти ли в ЛК и сколько грузить, и загружает расписание через
     * невидимый WebView. Семестр — раз в неделю, месяц — первым обновлением
     * за день, иначе текущую неделю (с пятницы — и следующую).
     */
    suspend fun run(context: Context, trigger: SyncTrigger): SyncResult {
        val d = Store(context)
        if (d.demo) return Demo.sync(context, trigger)
        // Часы переведены для скриншотов: в ЛК не ходим, иначе загрузка
        // «из будущего» перепишет настоящее расписание.
        if (Clock.screenshotMode) return SyncResult(skipped = SkipReason.NOT_NEEDED)
        val now = Clock.now
        skipReason(d, trigger, now)?.let { return SyncResult(skipped = it) }

        val semester = isSemesterDue(d, now)
        val full = semester || isFullDue(d, now)
        d.setTime(Keys.LAST_ATTEMPT_AT, now)
        val monday = now.monday
        val weeks = if (semester) SEMESTER_WEEKS else if (full) FETCH_WEEKS else if (now.isoWeekday >= 5) 2 else 1
        val result = try {
            LkWebView.refresh(
                context, monday, weeks, stopAfterEmpty = if (semester) EMPTY_WEEKS_TO_STOP else null,
                withProfile = full, withPhoto = full && photoStale(d, now), withPlan = semester,
                // Баллы — раз в день и по «потянуть вниз».
                withProgress = full || trigger == SyncTrigger.MANUAL,
            )
        } catch (e: LkError.ServerBusy) {
            startBackoff(d, e.retryAfter)
            throw e
        }
        d.remove(Keys.BACKOFF_UNTIL, Keys.BACKOFF_FAILURES)
        return save(context, result, monday, weeks, replaceAll = semester)
    }

    /** Есть ли пары в ближайшие 14 дней — если нет, это каникулы. */
    private fun lessonsSoon(d: Store, now: Instant): Boolean {
        val until = now.addingDays(14)
        return d.cachedLessons().any { it.end > now && it.start < until }
    }

    private fun startBackoff(d: Store, retryAfter: Int?) {
        val failures = d.int(Keys.BACKOFF_FAILURES) + 1
        var pause = minOf(backoffBase.multipliedBy(1L shl min(failures, 8)), backoffMax)
        pause = maxOf(pause, Duration.ofSeconds((retryAfter ?: 0).toLong()))
        d.setInt(Keys.BACKOFF_FAILURES, failures)
        d.setTime(Keys.BACKOFF_UNTIL, Clock.now.plus(pause))
    }

    private fun photoStale(d: Store, now: Instant): Boolean =
        d.time(Keys.PHOTO_FETCHED_AT)?.let { Duration.between(it, now) > photoMaxAge } ?: true

    /** data:image/png;base64,… → PNG 256×256 в base64 (несколько десятков КБ). */
    private fun shrinkPhoto(dataUrl: String): String? {
        val comma = dataUrl.indexOf(',')
        if (comma < 0) return null
        // Сайт режет base64 переносами строк каждые 64 символа.
        val base64 = dataUrl.substring(comma + 1).filterNot { it.isWhitespace() }
        val bytes = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull() ?: return null
        val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val side = 256f
        val scale = side / min(image.width, image.height)
        val small = image.scale((image.width * scale).toInt(), (image.height * scale).toInt())
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Сохраняет загруженное расписание, сравнивает с прошлым и обновляет
     * напоминания, виджет и календарь. Семестр (при входе и раз в неделю)
     * заменяет всё расписание, остальные загрузки — только свои недели.
     */
    suspend fun save(
        context: Context, result: FetchResult, monday: Instant,
        weeks: Int = SEMESTER_WEEKS, replaceAll: Boolean = true,
    ): SyncResult {
        val d = Store(context)
        val full = weeks >= FETCH_WEEKS
        val hadCache = d.has(Keys.DAYS)
        val before = d.cachedLessons()
        val days = if (replaceAll) keepRecent(d, monday) + result.days else merge(d, result.days, monday, weeks)
        val after = parseLessons(JsonArray(days))

        d.saveDays(JsonArray(days))
        result.group?.let { d.setString(Keys.GROUP, it) }
        result.program?.let { d.setString(Keys.PROGRAM, it) }
        result.fullName?.takeIf { it.isNotEmpty() }?.let { d.setString(Keys.FULL_NAME, it) }
        result.photo?.let { photo ->
            // Битое фото — останутся инициалы, попробуем в следующий раз.
            d.setString(Keys.PHOTO, if (photo.isEmpty()) null else shrinkPhoto(photo))
            d.setTime(Keys.PHOTO_FETCHED_AT, Clock.now)
        }
        d.setTime(Keys.FETCHED_AT, Clock.now)
        result.sessionUntil?.let { d.setTime(Keys.SESSION_UNTIL, it) }
        result.plan?.let { d.setString(Keys.PLAN, it) }
        val gradeChanges = d.saveProgress(result.progress, Clock.now)
        if (full) d.setTime(Keys.LAST_FULL_AT, Clock.now)
        if (replaceAll) {
            d.setTime(Keys.RANGE_START, monday)
            d.setTime(Keys.LAST_SEMESTER_AT, Clock.now)
        }
        d.setBool(Keys.LOGGED_IN, true)
        d.remove(Keys.SESSION_EXPIRED_NOTIFIED, Keys.SESSION_EXPIRED)

        if (d.bool(Keys.GRADE_NOTIFICATIONS)) Notifications.showGrades(context, gradeChanges)
        return SyncResult(
            changes = if (hadCache) describeChanges(before, after, Clock.now) else emptyList(),
            sideEffectError = applySideEffects(context, after),
        )
    }

    private fun dayStart(day: Any?): Instant? = localDayStart((day as? JsonObject)?.get("date").str())

    /** Прошедшие дни за последние 60 дней: сданные зачёты и экзамены
     * остаются в сессии, а пройденные пары — на экране предмета. */
    private fun keepRecent(d: Store, monday: Instant): List<kotlinx.serialization.json.JsonElement> {
        val from = monday.addingDays(-60)
        return d.cachedDays().filter { day ->
            val date = dayStart(day) ?: return@filter false
            date >= from && date < monday
        }
    }

    /** Сохранённые дни вне загруженных недель + свежие дни. */
    private fun merge(d: Store, fresh: JsonArray, monday: Instant, weeks: Int): List<kotlinx.serialization.json.JsonElement> {
        val end = monday.addingDays(7 * weeks)
        val kept = d.cachedDays().filter { day ->
            val date = dayStart(day) ?: return@filter true
            date < monday || date >= end
        }
        return kept + fresh
    }

    /** Напоминания, виджет, Live Update и календарь строятся из расписания.
     * Возвращает текст ошибки или null. */
    suspend fun applySideEffects(context: Context, lessons: List<Lesson>): String? {
        val d = Store(context)
        val minutes = d.reminderMinutes
        LiveUpdates.update(context, lessons)
        Notifications.scheduleReminders(context, lessons, minutes)
        Notifications.scheduleExamReminders(context, lessons, d.plan())
        // В демо сессии ЛК нет — напоминать о ней незачем.
        val fetchedAt = d.time(Keys.FETCHED_AT)
        if (fetchedAt != null && !d.demo) Notifications.scheduleKeepAlive(context, fetchedAt, d.time(Keys.SESSION_UNTIL))
        Widgets.update(context)
        if (!d.bool(Keys.CALENDAR_EXPORT)) return null
        val from = d.time(Keys.RANGE_START) ?: Clock.now.monday
        val last = lessons.maxOfOrNull { it.start }?.dateOnly?.addingDays(1) ?: from
        return CalendarExport.export(context, lessons, from, maxOf(from.addingDays(7 * FETCH_WEEKS), last), minutes)
    }
}
