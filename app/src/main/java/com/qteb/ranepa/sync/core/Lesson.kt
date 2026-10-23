package com.qteb.ranepa.sync.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Пара из ЛК. Время — момент; сайт отдаёт московское время без зоны. */
data class Lesson(
    val start: Instant,
    val end: Instant,
    val subject: String,
    /** «лекция», «семинар», … */
    val kind: String,
    val room: String,
    val lecturer: String,
    val email: String,
    val groups: List<String>,
    val lmsLink: String,
    val comment: String,
    val cancelled: Boolean,
) {
    val id: String get() = "${start.epochSecond}|$subject|$kind"

    val title: String get() = if (kind.isEmpty()) subject else "$subject ($kind)"
    val isRemote: Boolean get() = room.startsWith("СДО")
    val location: String get() = if (isRemote) "Дистанционно" else room

    /** «1 - 1112 (20) ТВ+ПК Блок 1В (Вернадского, 84 …)» → «1 - 1112». */
    val shortLocation: String
        get() {
            if (isRemote) return "Дистанционно"
            val i = room.indexOf(" (")
            return if (i >= 0) room.substring(0, i) else room
        }

    /** «1 - 2262, корпус 1» или «Дистанционно» — для уведомлений. */
    val placeLabel: String
        get() {
            if (isRemote) return "Дистанционно"
            val match = Regex("""корпус\s*(\d+)""").find(room) ?: return shortLocation
            return "$shortLocation, корпус ${match.groupValues[1]}"
        }

    /** «1 - 2262, блок 2F» — подпись точки на карте. */
    val mapTitle: String
        get() {
            val block = Regex("""Блок\s+(\S+)""").find(room) ?: return shortLocation
            return "$shortLocation, блок ${block.groupValues[1]}"
        }

    /** Описание события в календаре. */
    val details: String
        get() = listOf(lecturer, email, groups.joinToString(", "), lmsLink, comment)
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    fun isOngoing(now: Instant): Boolean = !cancelled && start <= now && now < end
}

/** Тип занятия из ЛК → короткое название. Физкультура в ЛК — обычное
 * практическое занятие, её узнаём по названию предмета. */
fun kindOf(type: String, subject: String): String {
    val t = type.lowercase()
    if (subject.lowercase().contains("физической культур")) return "физкультура"
    return when {
        t.contains("лекц") -> "лекция"
        t.contains("практ") || t.contains("семин") -> "семинар"
        t.contains("лаборатор") -> "лабораторная"
        t.contains("консульт") -> "консультация"
        t.contains("экзамен") -> "экзамен"
        t.contains("зач") -> "зачёт"
        else -> t
    }
}

private val moscow = ZoneId.of("Europe/Moscow")
private val stampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

/** «2026-10-12T09:00:00» — московское время без смещения. */
fun moscowDate(value: String): Instant? = runCatching {
    LocalDateTime.parse(value.take(19), stampFormatter).atZone(moscow).toInstant()
}.getOrNull()

/** Момент → строка в формате ЛК (московское время). */
fun moscowStamp(date: Instant): String = date.atZone(moscow).format(stampFormatter)

/** День из ЛК («2026-10-12T00:00:00»), прочитанный как местная дата. */
fun localDayStart(value: String): Instant? = runCatching {
    LocalDateTime.parse(value.take(19), stampFormatter).atZone(zone).toInstant()
}.getOrNull()

internal fun JsonElement?.str(): String = when (this) {
    is JsonPrimitive -> contentOrNull?.trim() ?: ""
    else -> ""
}

/** Разбирает сырые дни из n-api/schedule: [{"date": …, "discs": […]}, …]. */
fun parseLessons(days: JsonArray): List<Lesson> {
    val lessons = mutableListOf<Lesson>()
    for (day in days) {
        val discs = (day as? JsonObject)?.get("discs") as? JsonArray ?: continue
        for (disc in discs) {
            if (disc !is JsonObject) continue
            val start = moscowDate(disc["start_date"].str()) ?: continue
            val end = moscowDate(disc["end_date"].str()) ?: continue
            val subject = disc["disc"].str()
            // Группы приходят словарём {"0": …, "1": …}.
            val groups = (disc["groups"] as? JsonObject).orEmpty()
                .entries.sortedBy { it.key.toIntOrNull() ?: 0 }
                .map { it.value.str() }
            lessons += Lesson(
                start = start, end = end, subject = subject,
                kind = kindOf(disc["type"].str(), subject),
                room = disc["room"].str(), lecturer = disc["lecturer"].str(),
                email = disc["sam_email"].str(), groups = groups,
                lmsLink = disc["lms_link"].str(), comment = disc["comment"].str(),
                cancelled = disc["status"].str().lowercase().contains("отмен"),
            )
        }
    }
    return lessons.sortedBy { it.start }
}
