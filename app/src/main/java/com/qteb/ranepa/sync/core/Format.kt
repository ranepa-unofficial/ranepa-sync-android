package com.qteb.ranepa.sync.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

val weekdaysShort = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
val weekdaysLong = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
val monthsNominative = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль",
    "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
val monthsGenitive = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля",
    "августа", "сентября", "октября", "ноября", "декабря")
private val weekdaysAccusative = listOf("в понедельник", "во вторник", "в среду", "в четверг",
    "в пятницу", "в субботу", "в воскресенье")

/** Часовой пояс телефона: пары показываются в нём. */
val zone: ZoneId get() = ZoneId.systemDefault()

/**
 * Часы приложения. Для скриншотов их можно перевести: `DEMO_AT 2026-10-13T11:05`
 * в настройках отладки, дальше они идут как обычно.
 */
object Clock {
    @Volatile var shift: Duration = Duration.ZERO
        private set

    /** Сборка для скриншотов: демо выглядит как обычный аккаунт. */
    val screenshotMode: Boolean get() = !shift.isZero

    val now: Instant get() = Instant.now().plus(shift)

    /** «2026-10-13T11:05» — перевести часы; null — вернуть настоящие. */
    fun setDemoAt(value: String?) {
        shift = value?.let {
            runCatching { LocalDateTime.parse(it).atZone(zone).toInstant() }.getOrNull()
        }?.let { Duration.between(Instant.now(), it) } ?: Duration.ZERO
    }
}

private fun Instant.local(): ZonedDateTime = atZone(zone)

/** Начало дня. */
val Instant.dateOnly: Instant get() = local().truncatedTo(ChronoUnit.DAYS).toInstant()

/** 1 — понедельник, 7 — воскресенье. */
val Instant.isoWeekday: Int get() = local().dayOfWeek.value

val Instant.monday: Instant get() = dateOnly.addingDays(1 - isoWeekday)

val Instant.day: Int get() = local().dayOfMonth
val Instant.month: Int get() = local().monthValue
val Instant.year: Int get() = local().year
val Instant.hour: Int get() = local().hour
val Instant.minute: Int get() = local().minute

fun Instant.addingDays(days: Int): Instant = local().plusDays(days.toLong()).toInstant()

fun Instant.addingSeconds(seconds: Long): Instant = plusSeconds(seconds)

fun Instant.sameDay(other: Instant): Boolean = local().toLocalDate() == other.local().toLocalDate()

/** Целых дней от начала [other] до начала этого дня. */
fun Instant.daysSince(other: Instant): Int =
    ChronoUnit.DAYS.between(other.local().toLocalDate(), local().toLocalDate()).toInt()

/** Секунды между моментами: положительные, если этот позже. */
fun Instant.secondsSince(other: Instant): Long = Duration.between(other, this).seconds

/** Момент того же дня в [hour]:[minute]. */
fun Instant.at(hour: Int, minute: Int = 0): Instant =
    local().truncatedTo(ChronoUnit.DAYS).withHour(hour).withMinute(minute).toInstant()

val Instant.isWeekend: Boolean get() = local().dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

private val hmFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** «09:00». */
fun hm(t: Instant): String = t.local().format(hmFormatter)

/** «Среда, 14 октября». */
fun dayTitle(d: Instant): String = "${weekdaysLong[d.isoWeekday - 1]}, ${d.day} ${monthsGenitive[d.month - 1]}"

/** «Сегодня», «Завтра», «Вчера» или день недели. */
fun relativeDayName(d: Instant, now: Instant = Clock.now): String = when (d.daysSince(now)) {
    0 -> "Сегодня"
    1 -> "Завтра"
    -1 -> "Вчера"
    else -> weekdaysLong[d.isoWeekday - 1]
}

/** «40 мин», «1 ч», «1 ч 40 мин». Минуты округляются вниз. */
fun durationText(seconds: Long): String {
    val total = maxOf(0L, seconds / 60)
    val h = total / 60
    val m = total % 60
    if (h == 0L) return "$m мин"
    return if (m == 0L) "$h ч" else "$h ч $m мин"
}

/** «сегодня в 12:20», «завтра в 09:00», «в среду 21 октября в 09:00». */
fun whenLabel(t: Instant, now: Instant = Clock.now): String {
    val day = when (t.daysSince(now)) {
        0 -> "сегодня"
        1 -> "завтра"
        else -> "${weekdaysAccusative[t.isoWeekday - 1]} ${t.day} ${monthsGenitive[t.month - 1]}"
    }
    return "$day в ${hm(t)}"
}

/** «12.10». */
fun shortDate(d: Instant): String = "%d.%02d".format(d.day, d.month)

/** День в списках: «Сегодня», «Завтра», на этой неделе — «Пт», дальше — «Ср, 14 октября». */
fun dayName(t: Instant, now: Instant = Clock.now): String = when (t.daysSince(now)) {
    0 -> "Сегодня"
    1 -> "Завтра"
    in 2..6 -> weekdaysShort[t.isoWeekday - 1]
    else -> "${weekdaysShort[t.isoWeekday - 1]}, ${t.day} ${monthsGenitive[t.month - 1]}"
}

/** «Сегодня, 09:00», «Ср, 14 октября, 10:40». */
fun shortWhen(t: Instant, now: Instant = Clock.now): String = "${dayName(t, now)}, ${hm(t)}"

fun String.capitalizedFirst(): String = if (isEmpty()) this else substring(0, 1).uppercase() + substring(1)

fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    if (m10 == 1 && m100 != 11) return one
    if (m10 in 2..4 && m100 !in 12..14) return few
    return many
}

/** «сегодня», «завтра», «через 13 дней». */
fun daysUntil(date: Instant, now: Instant): String = when (val n = date.daysSince(now)) {
    in Int.MIN_VALUE..0 -> "сегодня"
    1 -> "завтра"
    else -> "через $n ${plural(n, "день", "дня", "дней")}"
}

/** «11–30 января», «28 декабря – 26 января». */
fun dateRange(a: Instant, b: Instant): String {
    if (a.sameDay(b)) return "${a.day} ${monthsGenitive[a.month - 1]}"
    if (a.month == b.month) return "${a.day}–${b.day} ${monthsGenitive[b.month - 1]}"
    return "${a.day} ${monthsGenitive[a.month - 1]} – ${b.day} ${monthsGenitive[b.month - 1]}"
}
