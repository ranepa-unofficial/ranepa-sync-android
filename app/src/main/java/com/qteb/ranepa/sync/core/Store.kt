package com.qteb.ranepa.sync.core

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import java.time.Instant

/** Ключи настроек и кэша — те же, что в iOS-версии. */
object Keys {
    const val LOGGED_IN = "loggedIn"
    const val DAYS = "days"
    const val GROUP = "group"
    const val FETCHED_AT = "fetchedAt"
    const val RANGE_START = "rangeStart"
    const val REMINDER_MINUTES = "reminderMinutes"
    const val CALENDAR_EXPORT = "calendarExport"
    const val SESSION_EXPIRED_NOTIFIED = "sessionExpiredNotified"
    /** Сессия ЛК истекла: расписание показываем из кэша и просим войти заново. */
    const val SESSION_EXPIRED = "sessionExpired"
    const val LAST_BACKGROUND_RUN = "lastBackgroundRun"
    const val LAST_BACKGROUND_RESULT = "lastBackgroundResult"
    const val FULL_NAME = "fullName"
    const val PROGRAM = "program"
    const val PHOTO = "photo"
    const val PHOTO_FETCHED_AT = "photoFetchedAt"
    const val LAST_FULL_AT = "lastFullAt"
    /** Когда последний раз загружали весь семестр. */
    const val LAST_SEMESTER_AT = "lastSemesterAt"
    const val LAST_ATTEMPT_AT = "lastAttemptAt"
    const val BACKOFF_UNTIL = "backoffUntil"
    const val BACKOFF_FAILURES = "backoffFailures"
    const val LIVE_UPDATE = "liveActivity"
    const val THEME_MODE = "themeMode"
    const val MAP_APP = "mapApp"
    const val DEMO = "demo"
    const val DEMO_REVISION = "demoRevision"
    const val DEMO_SHIFT = "demoShift"
    const val DEMO_FAST_FROM = "demoFastFrom"
    /** Когда истекает сессия ЛК (refresh-токен), по настоящим часам. */
    const val SESSION_UNTIL = "sessionUntil"
    /** План обучения из ЛК (JSON StudyPlan). */
    const val PLAN = "plan"
    /** Точки корпусов, найденные геокодером: адрес → широта и долгота. */
    const val PLACES = "places"
    /** Последний день, за который отправлены настройки, — «2026-10-26». */
    const val ANALYTICS_DAY = "analyticsDay"
    /** Текущая успеваемость (JSON Progress) и баллы по дням для «за неделю». */
    const val PROGRESS = "progress"
    const val PROGRESS_HISTORY = "progressHistory"
    /** Уведомлять о новых баллах БРС. */
    const val GRADE_NOTIFICATIONS = "gradeNotifications"
    /** Манифест обновлений (update.json), когда его загрузили и какую новую
     * версию уже видели в аккаунте. При выходе не удаляются. */
    const val UPDATE_MANIFEST = "updateManifest"
    const val UPDATE_CHECKED_AT = "updateCheckedAt"
    const val UPDATE_SEEN = "updateSeen"
    /** О какой версии и когда последний раз показывали шторку «Вышла версия». */
    const val UPDATE_SHEET_VERSION = "updateSheetVersion"
    const val UPDATE_SHEET_SHOWN_AT = "updateSheetShownAt"
    /** Новая версия из последней проверки: точка на аватаре до следующей проверки. */
    const val UPDATE_OFFER = "updateOffer"
    /** Только в отладке: канал установки и манифест вместо настоящих. */
    const val UPDATE_DEBUG_CHANNEL = "updateDebugChannel"
    const val UPDATE_DEBUG_MANIFEST = "updateDebugManifest"

    /** Всё, что относится к сессии и удаляется при выходе. */
    val session = listOf(
        LOGGED_IN, DAYS, GROUP, FETCHED_AT, RANGE_START, SESSION_EXPIRED_NOTIFIED, SESSION_EXPIRED,
        LAST_BACKGROUND_RUN, LAST_BACKGROUND_RESULT, FULL_NAME, PROGRAM, PHOTO,
        PHOTO_FETCHED_AT, LAST_FULL_AT, LAST_SEMESTER_AT, LAST_ATTEMPT_AT, BACKOFF_UNTIL, BACKOFF_FAILURES,
        DEMO, DEMO_REVISION, DEMO_SHIFT, DEMO_FAST_FROM, SESSION_UNTIL, PLAN, PROGRESS, PROGRESS_HISTORY,
    )
}

/** Напоминание о паре по умолчанию, пока пользователь не выбрал своё. */
const val DEFAULT_REMINDER_MINUTES = 15

val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

/** Настройки и кэш приложения. Моменты хранятся в миллисекундах. */
class Store(val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("ranepa", Context.MODE_PRIVATE))

    fun has(key: String) = prefs.contains(key)

    fun time(key: String): Instant? = if (prefs.contains(key)) Instant.ofEpochMilli(prefs.getLong(key, 0)) else null

    fun setTime(key: String, value: Instant?) = prefs.edit {
        if (value == null) remove(key) else putLong(key, value.toEpochMilli())
    }

    fun bool(key: String, default: Boolean = false) = prefs.getBoolean(key, default)
    fun setBool(key: String, value: Boolean) = prefs.edit { putBoolean(key, value) }

    fun int(key: String, default: Int = 0) = prefs.getInt(key, default)
    fun setInt(key: String, value: Int) = prefs.edit { putInt(key, value) }

    fun string(key: String): String? = prefs.getString(key, null)
    fun setString(key: String, value: String?) = prefs.edit {
        if (value == null) remove(key) else putString(key, value)
    }

    fun remove(vararg keys: String) = prefs.edit { keys.forEach(::remove) }

    val reminderMinutes: Int get() = int(Keys.REMINDER_MINUTES, DEFAULT_REMINDER_MINUTES)
    val liveUpdate: Boolean get() = bool(Keys.LIVE_UPDATE, true)
    val demo: Boolean get() = bool(Keys.DEMO)
    val loggedIn: Boolean get() = bool(Keys.LOGGED_IN)

    /** Сырые дни из кэша. */
    fun cachedDays(): JsonArray = string(Keys.DAYS)
        ?.let { runCatching { json.parseToJsonElement(it) as? JsonArray }.getOrNull() }
        ?: JsonArray(emptyList())

    fun cachedLessons(): List<Lesson> = parseLessons(cachedDays())

    fun saveDays(days: JsonArray) = setString(Keys.DAYS, days.toString())

    fun plan(): StudyPlan? = string(Keys.PLAN)?.let { runCatching { json.decodeFromString<StudyPlan>(it) }.getOrNull() }
}
