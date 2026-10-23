package com.qteb.ranepa.sync.system

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.qteb.ranepa.sync.core.Lesson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.TimeZone

/**
 * Отдельный календарь «РАНХиГС» на телефоне. Он локальный и принадлежит только
 * приложению, поэтому проще всего удалить события в диапазоне и создать заново.
 */
object CalendarExport {
    const val NAME = "РАНХиГС"
    private const val ACCOUNT = "Расписание студента"
    private const val COLOR = 0xFF8E1B2E.toInt()

    val permissions = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    fun allowed(context: Context): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun asSyncAdapter(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    private fun findCalendar(context: Context): Long? {
        context.contentResolver.query(
            Calendars.CONTENT_URI, arrayOf(Calendars._ID),
            "${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.ACCOUNT_TYPE} = ?",
            arrayOf(ACCOUNT, CalendarContract.ACCOUNT_TYPE_LOCAL), null,
        )?.use { if (it.moveToFirst()) return it.getLong(0) }
        return null
    }

    private fun ownCalendar(context: Context): Long {
        findCalendar(context)?.let { return it }
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, ACCOUNT)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, NAME)
            put(Calendars.CALENDAR_DISPLAY_NAME, NAME)
            put(Calendars.CALENDAR_COLOR, COLOR)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, ACCOUNT)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }
        val uri = context.contentResolver.insert(asSyncAdapter(Calendars.CONTENT_URI), values)
            ?: error("календарь не создался")
        return ContentUris.parseId(uri)
    }

    /** «1 - 2317 · проспект Вернадского, 84, корпус 1». */
    private fun place(l: Lesson): String {
        if (l.isRemote) return l.location
        val address = Places.address(l.room) ?: return l.location
        return "${l.shortLocation} · ${Places.query(address).removePrefix("Москва, ")}"
    }

    /** Пересобирает календарь за [from]..[to]. Возвращает текст ошибки или null. */
    suspend fun export(context: Context, lessons: List<Lesson>, from: Instant, to: Instant, reminderMinutes: Int): String? =
        withContext(Dispatchers.IO) {
            if (!allowed(context)) return@withContext "Нет доступа к календарю: разреши его в настройках телефона"
            try {
                val resolver = context.contentResolver
                val calendar = ownCalendar(context)
                resolver.delete(
                    asSyncAdapter(Events.CONTENT_URI),
                    "${Events.CALENDAR_ID} = ? AND ${Events.DTSTART} >= ? AND ${Events.DTSTART} < ?",
                    arrayOf("$calendar", "${from.toEpochMilli()}", "${to.toEpochMilli()}"),
                )
                for (l in lessons) {
                    if (l.cancelled || l.start < from || l.start >= to) continue
                    val event = resolver.insert(asSyncAdapter(Events.CONTENT_URI), ContentValues().apply {
                        put(Events.CALENDAR_ID, calendar)
                        put(Events.TITLE, l.title)
                        put(Events.DTSTART, l.start.toEpochMilli())
                        put(Events.DTEND, l.end.toEpochMilli())
                        put(Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                        put(Events.EVENT_LOCATION, place(l))
                        put(Events.DESCRIPTION, l.details)
                        put(Events.HAS_ALARM, if (reminderMinutes > 0) 1 else 0)
                    }) ?: continue
                    if (reminderMinutes > 0) {
                        resolver.insert(asSyncAdapter(Reminders.CONTENT_URI), ContentValues().apply {
                            put(Reminders.EVENT_ID, ContentUris.parseId(event))
                            put(Reminders.MINUTES, reminderMinutes)
                            put(Reminders.METHOD, Reminders.METHOD_ALERT)
                        })
                    }
                }
                null
            } catch (e: Exception) {
                "Не удалось выгрузить в календарь: ${e.message}"
            }
        }

    suspend fun remove(context: Context) = withContext(Dispatchers.IO) {
        if (!allowed(context)) return@withContext
        val id = findCalendar(context) ?: return@withContext
        runCatching {
            context.contentResolver.delete(ContentUris.withAppendedId(asSyncAdapter(Calendars.CONTENT_URI), id), null, null)
        }
    }
}
