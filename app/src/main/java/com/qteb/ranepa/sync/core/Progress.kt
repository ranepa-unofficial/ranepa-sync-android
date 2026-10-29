package com.qteb.ranepa.sync.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Текущая успеваемость из ЛК (`dashboard/progress`): баллы БРС по предметам
 * текущего семестра, посещаемость по месяцам и средний балл.
 */
@Serializable
data class Progress(
    val subjects: List<Subject>,
    val months: List<Month>,
    /** Средний балл из ЛК: по 100-балльной шкале и по 5-балльной. Строки
     * вида «78» и «4,5»; null — пока не было сессии. */
    val averageBrs: String? = null,
    val rating: String? = null,
    /** Когда загрузили, в миллисекундах. */
    val fetchedAtMillis: Long,
) {
    @Serializable
    data class Subject(
        /** Название как в расписании: «Иностранный язык». */
        val name: String,
        /** Баллы БРС, 0–100. */
        val points: Int,
    )

    @Serializable
    data class Month(
        /** «Октябрь». */
        val name: String,
        /** Сколько занятий было запланировано и сколько посещено. */
        val plan: Int,
        val fact: Int,
    )

    val fetchedAt: Instant get() = Instant.ofEpochMilli(fetchedAtMillis)

    val isEmpty: Boolean get() = subjects.isEmpty() && months.isEmpty()

    fun pointsFor(name: String): Int? = subjects.firstOrNull { it.name.lowercase() == name.lowercase() }?.points

    /** Средний балл БРС по предметам — пока ЛК своего не считает. */
    val averagePoints: Int?
        get() = if (subjects.isEmpty()) null else (subjects.sumOf { it.points }.toDouble() / subjects.size).roundToInt()

    /** Месяц посещаемости для карточки: последний, где уже что-то посещено. */
    val currentMonth: Month? get() = months.lastOrNull { it.fact > 0 }

    companion object {
        /** Ответ ЛК: `{brs: {labels, data}, visits: {items}, avg_brs, rating}`. */
        fun parse(raw: JsonObject, at: Instant): Progress {
            val brs = raw["brs"] as? JsonObject
            val labels = brs?.get("labels") as? JsonArray ?: JsonArray(emptyList())
            val values = brs?.get("data") as? JsonArray ?: JsonArray(emptyList())
            val subjects = labels.mapIndexedNotNull { i, label ->
                val name = subjectName((label as? JsonPrimitive)?.contentOrNull ?: return@mapIndexedNotNull null)
                val points = number(values.getOrNull(i)) ?: return@mapIndexedNotNull null
                if (name.isEmpty()) null else Subject(name, points)
            }
            val items = (raw["visits"] as? JsonObject)?.get("items") as? JsonArray ?: JsonArray(emptyList())
            val months = items.mapNotNull { m ->
                if (m !is JsonObject) return@mapNotNull null
                val name = (m["monthName"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                Month(name, number(m["maxValue"]) ?: return@mapNotNull null, number(m["currentValue"]) ?: return@mapNotNull null)
            }
            return Progress(subjects, months, text(raw["avg_brs"]), text(raw["rating"]), at.toEpochMilli())
        }

        /**
         * «Иностранный язык_ОАФ_1 курс_B1-B2 [1 семестр] [Иванов И.И.] [группы]»
         * → «Иностранный язык»: до первой скобки, без приписок через «_».
         */
        fun subjectName(label: String): String = label.substringBefore(" [").substringBefore("_").trim()

        /** «Информатика: 16 → 24 балла» — для уведомления. */
        fun changes(old: Progress, new: Progress): List<String> = new.subjects.mapNotNull { s ->
            val was = old.pointsFor(s.name) ?: return@mapNotNull null
            if (was == s.points) null else "${s.name}: $was → ${s.points} ${plural(s.points, "балл", "балла", "баллов")}"
        }

        /** Числа приходят и числами, и строками. */
        private fun number(value: JsonElement?): Int? {
            val p = value as? JsonPrimitive ?: return null
            if (p is JsonNull) return null
            return p.contentOrNull?.trim()?.toIntOrNull() ?: p.doubleOrNull?.toInt()
        }

        private fun text(value: JsonElement?): String? {
            val p = value as? JsonPrimitive ?: return null
            if (p is JsonNull) return null
            return p.contentOrNull?.takeIf { it.isNotBlank() }
        }
    }
}

/** Доля посещённых занятий в процентах; 0, если ничего не запланировано. */
fun percent(m: Progress.Month): Int = if (m.plan > 0) (m.fact.toDouble() / m.plan * 100).roundToInt() else 0

/** Баллы по дням за последние три недели: для «+4 за неделю». */
object ProgressHistory {
    @Serializable
    data class Point(
        /** День в формате «2026-10-30». */
        val day: String,
        val points: Int,
    )

    private const val KEEP_DAYS = 21L

    private fun load(d: Store): Map<String, List<Point>> =
        d.string(Keys.PROGRESS_HISTORY)?.let { runCatching { json.decodeFromString<Map<String, List<Point>>>(it) }.getOrNull() }
            ?: emptyMap()

    private fun dayOf(t: Instant): LocalDate = t.atZone(zone).toLocalDate()

    /** За один день — одна запись: повторная загрузка в тот же день её перезаписывает. */
    fun record(progress: Progress, d: Store) {
        val all = load(d).toMutableMap()
        val day = dayOf(progress.fetchedAt)
        val oldest = day.minusDays(KEEP_DAYS)
        for (s in progress.subjects) {
            val list = all[s.name].orEmpty().filter { LocalDate.parse(it.day).let { p -> p != day && p > oldest } } +
                Point(day.toString(), s.points)
            all[s.name] = list.sortedBy { it.day }
        }
        d.setString(Keys.PROGRESS_HISTORY, json.encodeToString(all))
    }

    /** Насколько изменились баллы за неделю; null — нет записи недельной давности. */
    fun weekDelta(name: String, now: Instant, d: Store): Int? {
        val list = load(d)[name].orEmpty()
        val weekAgo = dayOf(now).minusDays(7)
        val base = list.lastOrNull { LocalDate.parse(it.day) <= weekAgo } ?: return null
        val current = list.lastOrNull() ?: return null
        return current.points - base.points
    }
}

/** Сохранённая успеваемость. */
fun Store.progress(): Progress? =
    string(Keys.PROGRESS)?.let { runCatching { json.decodeFromString<Progress>(it) }.getOrNull() }

/**
 * Сохраняет успеваемость и пишет историю; возвращает изменившиеся баллы.
 * Пустой ответ (ЛК не отдал блок) прошлые данные не стирает.
 */
fun Store.saveProgress(raw: JsonObject?, at: Instant): List<String> {
    raw ?: return emptyList()
    val fresh = Progress.parse(raw, at)
    if (fresh.isEmpty) return emptyList()
    val old = progress()
    setString(Keys.PROGRESS, json.encodeToString(fresh))
    ProgressHistory.record(fresh, this)
    return old?.let { Progress.changes(it, fresh) } ?: emptyList()
}
