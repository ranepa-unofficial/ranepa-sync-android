package com.qteb.ranepa.sync.sync

import android.content.Context
import com.qteb.ranepa.sync.core.Clock
import com.qteb.ranepa.sync.core.Keys
import com.qteb.ranepa.sync.core.Store
import com.qteb.ranepa.sync.core.addingDays
import com.qteb.ranepa.sync.core.dateOnly
import com.qteb.ranepa.sync.core.isoWeekday
import com.qteb.ranepa.sync.core.day
import com.qteb.ranepa.sync.core.monday
import com.qteb.ranepa.sync.core.month
import com.qteb.ranepa.sync.core.moscowStamp
import com.qteb.ranepa.sync.core.sameDay
import com.qteb.ranepa.sync.lk.FetchResult
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

/**
 * Демо-режим: Наташа Ростова и выдуманное расписание без обращений к ЛК.
 * Нужен для проверки магазинами приложений (у проверяющего нет аккаунта в ЛК),
 * скриншотов и проверки без интернета. Включается долгим нажатием на логотип
 * на стартовом экране.
 */
object Demo {
    const val GROUP = "ГМУ-ОБ25-03"
    const val PROGRAM = "Государственное и муниципальное управление"
    const val NAME = "Ростова Наталья Ильинична"

    /** Пары в ЛК: 09:00–10:20, 10:40–12:00, … */
    private val slots = listOf(9 to 0, 10 to 40, 12 to 20, 14 to 0, 15 to 40)
    private const val PAIR_LENGTH = 80 * 60L

    /** «Ускоренный день»: пары и перемены в минутах, а не в часах. */
    const val FAST_LENGTH = 4 * 60L
    const val FAST_BREAK = 2 * 60L

    private const val LECTURE = "Лекция"
    private const val SEMINAR = "Практическое занятие"
    private const val LAB = "Лабораторная работа"

    private const val REMOTE = "СДО (дистанционно)"
    private val rooms = listOf(
        "5 - 201 (44) П+ПК (Вернадского, 82 - корпус 5)",
        "1 - 1112 (20) ТВ+ПК Блок 1B (Вернадского, 84 - корпус 1)",
        "1 - 2262 (24) П+ПК Блок 2F (Вернадского, 84 - корпус 1)",
        "1 - 2317 (24) ТВ+ПК Блок 2H (Вернадского, 84 - корпус 1)",
        "Стадион (Вернадского, 82)",
    )

    /** Предмет, преподаватель, почта. Люди и адреса выдуманы. */
    private val subjects = listOf(
        Triple("Теория управления", "Соколова Мария Андреевна", "sokolova-ma"),
        Triple("Экономическая теория", "Лебедев Игорь Павлович", "lebedev-ip"),
        // Без почты: так тоже бывает, и карточка выглядит чище на скриншотах.
        Triple("Иностранный язык (английский)", "Морозова Ксения Олеговна", ""),
        Triple("Элективные дисциплины по физической культуре и спорту", "Васильев Денис Сергеевич", "vasilev-ds"),
        Triple("Математика", "Ершова Наталья Викторовна", "ershova-nv"),
        Triple("Основы российской государственности", "Кузнецов Алексей Михайлович", "kuznetsov-am"),
        Triple("Информационные технологии в управлении", "Тихонов Павел Андреевич", "tikhonov-pa"),
        Triple("Правоведение", "Белова Ольга Николаевна", "belova-on"),
    )

    private data class Slot(val slot: Int, val subject: Int, val type: String, val room: Int)

    /** Неделя: день (1 — понедельник) → пары. Аудитория -1 — дистанционно. */
    private val week = mapOf(
        1 to listOf(Slot(0, 5, LECTURE, -1), Slot(1, 1, LECTURE, 0), Slot(2, 1, SEMINAR, 2)),
        2 to listOf(Slot(0, 0, LECTURE, 0), Slot(1, 2, SEMINAR, 3), Slot(2, 3, SEMINAR, 4), Slot(3, 6, LAB, 1)),
        3 to listOf(Slot(1, 4, LECTURE, 0), Slot(2, 4, SEMINAR, 2), Slot(3, 7, SEMINAR, 3)),
        4 to listOf(Slot(0, 7, LECTURE, -1), Slot(1, 0, SEMINAR, 1), Slot(2, 2, SEMINAR, 3)),
        5 to listOf(Slot(0, 6, LECTURE, 0), Slot(1, 1, SEMINAR, 2), Slot(2, 3, SEMINAR, 4)),
        6 to listOf(Slot(0, 5, SEMINAR, -1), Slot(1, 4, SEMINAR, 1)),
    )

    /** Сдвиг сегодняшних пар в секундах: вторая идёт уже 25 минут, поэтому
     * карточка «Сейчас» видна в любое время. Кратен 5 минутам и не выходит за сутки. */
    fun todayShift(now: Instant): Long {
        val today = now.dateOnly
        val secondStart = today.plusSeconds(10 * 3600 + 40 * 60)
        var shift = (now.epochSecond - secondStart.epochSecond) - 25 * 60
        shift = (shift / 300.0).roundToLong() * 300
        // Отступ в 5 минут от полуночи с обеих сторон.
        val dayStart = today.plusSeconds(5 * 60)
        val firstStart = today.plusSeconds(9 * 3600 + shift)
        if (firstStart < dayStart) shift += dayStart.epochSecond - firstStart.epochSecond
        val lastEnd = today.plusSeconds(14 * 3600 + PAIR_LENGTH + shift)
        val dayEnd = today.plusSeconds(23 * 3600 + 55 * 60)
        if (lastEnd > dayEnd) shift -= lastEnd.epochSecond - dayEnd.epochSecond
        return shift
    }

    /**
     * Расписание на [FETCH_WEEKS] недель вокруг [now]. Сегодня — со сдвигом
     * [shift] (в воскресенье — расписание вторника). Нечётная [revision] меняет
     * аудиторию первой пары завтра — так видно уведомление об изменении.
     * [fastFrom] — «ускоренный день»: сегодня пары по 4 минуты.
     */
    fun result(now: Instant, revision: Int = 0, shift: Long? = null, fastFrom: Instant? = null): FetchResult {
        val today = now.dateOnly
        val monday = now.monday
        val todayShift = shift ?: todayShift(now)
        var nextDay = today.addingDays(1)
        if (nextDay.isoWeekday == 7) nextDay = nextDay.addingDays(1)

        val days = buildJsonArray {
            for (i in 0 until 7 * FETCH_WEEKS) {
                val day = monday.addingDays(i)
                val isToday = day.sameDay(today)
                val plan = if (isToday && day.isoWeekday == 7) week.getValue(2) else week[day.isoWeekday].orEmpty()
                val weekIndex = i / 7
                addJsonObject {
                    put("date", moscowStamp(day))
                    putJsonArray("discs") {
                        plan.forEachIndexed { n, (slot, subject, type, room) ->
                            val (h, m) = slots[slot]
                            var start = day.plusSeconds(h * 3600L + m * 60L)
                            var length = PAIR_LENGTH
                            if (isToday && fastFrom != null) {
                                start = fastFrom.plusSeconds(n * (FAST_LENGTH + FAST_BREAK))
                                length = FAST_LENGTH
                            } else if (isToday) {
                                start = start.plusSeconds(todayShift)
                            }
                            // Зачёт — по правоведению: по математике в плане экзамен.
                            val credit = day.isoWeekday == 6 && n == 1 && weekIndex == FETCH_WEEKS - 2
                            val (name, lecturer, email) = subjects[if (credit) 7 else subject]
                            var place = if (room < 0) REMOTE else rooms[room]
                            if (revision % 2 == 1 && day.sameDay(nextDay) && n == 0) place = rooms[(room + 2) % 4]
                            // Сессия по субботам: консультация, зачёт и экзамен. Зачёт —
                            // не дальше месяца, чтобы карточка сессии была видна всегда.
                            val session = day.isoWeekday == 6 && n == 1
                            val kind = when {
                                session && weekIndex == FETCH_WEEKS - 3 -> "Консультация"
                                session && weekIndex == FETCH_WEEKS - 2 -> "Зачёт"
                                session && weekIndex == FETCH_WEEKS - 1 -> "Экзамен"
                                else -> type
                            }
                            addJsonObject {
                                put("start_date", moscowStamp(start))
                                put("end_date", moscowStamp(start.plusSeconds(length)))
                                put("disc", name)
                                put("type", kind)
                                put("room", place)
                                put("lecturer", lecturer)
                                put("sam_email", if (email.isEmpty()) "" else "$email@ranepa.example")
                                putJsonObject("groups") {
                                    put("0", GROUP)
                                    if (type == LECTURE) put("1", "ГМУ-ОБ25-04")
                                }
                                put("lms_link", if (room < 0) "https://lms.ranepa.ru/" else "")
                                put("comment", if (type == LAB) "Нужен ноутбук" else "")
                                // Одна отменённая пара послезавтра.
                                put("status", if (day.sameDay(nextDay.addingDays(1)) && n == plan.size - 1) "Отменено" else "")
                            }
                        }
                    }
                }
            }
        }
        return FetchResult(days = days, group = GROUP, program = PROGRAM, fullName = NAME, plan = plan(monday),
            progress = progress(now, revision))
    }

    /**
     * Успеваемость в формате ЛК: метки дисциплин с приписками, как на сайте.
     * Каждое «потянуть вниз» добавляет баллы по математике — видно
     * уведомление о новых баллах.
     */
    fun progress(now: Instant, revision: Int = 0): JsonObject {
        val points = listOf(34, 41, 28, 55, 22 + 4 * revision, 37, 46, 31)
        // Семестр с сентября по январь; прошедшие месяцы посещены почти все.
        val names = listOf("Сентябрь", "Октябрь", "Ноябрь", "Декабрь", "Январь")
        val plans = listOf(63, 77, 63, 50, 5)
        val current = mapOf(9 to 0, 10 to 1, 11 to 2, 12 to 3, 1 to 4)[now.month] ?: 4
        return buildJsonObject {
            putJsonObject("brs") {
                putJsonArray("labels") {
                    subjects.forEachIndexed { i, (name, _, _) ->
                        add(JsonPrimitive(if (i == 2) "${name}_ОАФ_1 курс_B1-B2 [1 семестр] [] []" else "$name [1 семестр] [] []"))
                    }
                }
                putJsonArray("data") { points.forEach { add(JsonPrimitive(minOf(it, 100))) } }
            }
            putJsonObject("visits") {
                putJsonArray("items") {
                    names.forEachIndexed { i, name ->
                        val fact = when {
                            i < current -> plans[i] * 9 / 10
                            i == current -> plans[i] * 2 / 3 * now.day / 30
                            else -> 0
                        }
                        addJsonObject {
                            put("monthName", name)
                            put("maxValue", plans[i])
                            put("currentValue", fact)
                        }
                    }
                }
            }
            put("avg_brs", JsonNull)
            put("rating", JsonNull)
        }
    }

    /** План обучения: формы контроля по предметам и сессия — те две недели,
     * где в демо стоят консультация, зачёт и экзамен. */
    fun plan(monday: Instant): String {
        val f = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(com.qteb.ranepa.sync.core.zone)
        val start = monday.addingDays(7 * (FETCH_WEEKS - 2))
        val next = start.addingDays(7 * 22)
        fun JsonArrayBuilder.item(name: String, control: String?, hours: Int, semester: Int = 1) = addJsonObject {
            put("name", name)
            if (control != null) put("control", control)
            put("hours", hours)
            put("ze", hours / 36)
            put("course", 1)
            put("semester", "$semester-й семестр")
            put("selected", true)
        }
        val current = listOf(
            Triple("Математика", "Экзамен", 144), Triple("Экономическая теория", "Экзамен", 144),
            Triple("Теория управления", "Зачет", 72), Triple("Иностранный язык (английский)", "Зачет", 108),
            Triple("Основы российской государственности", "Зачет", 72),
            Triple("Информационные технологии в управлении", "Зачет с оценкой", 108),
            Triple("Правоведение", "Зачет", 72), Triple("Элективные дисциплины по физической культуре и спорту", null, 64),
        )
        val second = listOf(
            Triple("Математика", "Экзамен", 144), Triple("Макроэкономика", "Экзамен", 144), Triple("Философия", "Зачет", 72),
            Triple("Иностранный язык (английский)", "Зачет", 108), Triple("Менеджмент", "Зачет с оценкой", 108),
        )
        val plan = buildJsonObject {
            putJsonArray("items") {
                current.forEach { (n, c, h) -> item(n, c, h) }
                second.forEach { (n, c, h) -> item(n, c, h, semester = 2) }
            }
            putJsonArray("sessions") {
                addJsonObject {
                    put("course", "1-й курс"); put("semester", "1-й семестр")
                    put("start", f.format(start)); put("end", f.format(start.addingDays(13)))
                }
                addJsonObject {
                    put("course", "1-й курс"); put("semester", "2-й семестр")
                    put("start", f.format(next)); put("end", f.format(next.addingDays(13)))
                }
            }
        }
        return plan.toString()
    }

    /** Демо-расписание на сегодня. Сдвиг запоминается на весь день, иначе
     * каждое обновление «переносило» бы сегодняшние пары. */
    fun fetch(d: Store, revision: Int = 0): FetchResult {
        val now = Clock.now
        val day = "${now.dateOnly.epochSecond}"
        val saved = d.string(Keys.DEMO_SHIFT)?.split("|")
        val shift = when {
            // Скриншоты: обычная сетка пар, состояние дня задаёт DEMO_AT.
            Clock.screenshotMode -> 0L
            saved != null && saved.size == 2 && saved[0] == day && saved[1].toLongOrNull() != null -> saved[1].toLong() * 60
            else -> todayShift(now).also { d.setString(Keys.DEMO_SHIFT, "$day|${it / 60}") }
        }
        return result(now, revision, shift, d.time(Keys.DEMO_FAST_FROM))
    }

    /** Обновление в демо: без сети. Расписание пересобирается раз в день,
     * «потянуть вниз» каждый раз меняет одну аудиторию. */
    suspend fun sync(context: Context, trigger: SyncTrigger): SyncResult {
        val d = Store(context)
        val now = Clock.now
        val fresh = d.time(Keys.FETCHED_AT)?.sameDay(now) ?: false
        if (trigger != SyncTrigger.MANUAL && fresh) return SyncResult(skipped = SkipReason.NOT_NEEDED)
        var revision = d.int(Keys.DEMO_REVISION)
        if (trigger == SyncTrigger.MANUAL) {
            // Как настоящий запрос: жест не должен заканчиваться мгновенно.
            delay(800)
            revision += 1
            d.setInt(Keys.DEMO_REVISION, revision)
        }
        val result = Sync.save(context, fetch(d, revision), now.monday)
        // Новый день пересобирает всё — это не изменения в расписании.
        return if (trigger == SyncTrigger.MANUAL) result else SyncResult(sideEffectError = result.sideEffectError)
    }
}
