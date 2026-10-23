package com.qteb.ranepa.sync.core

import java.time.Instant

private val sessionKinds = setOf("экзамен", "зачёт", "консультация")

/** Предмет семестра: все его пары, преподаватели, где обычно проходит. */
class Subject(
    val name: String,
    /** Все пары по времени, вместе с прошедшими и отменёнными. */
    val lessons: List<Lesson>,
    val now: Instant,
) {
    data class Lecturer(
        val name: String,
        val email: String,
        /** «Лекции», «Семинары» — что ведёт. */
        val kinds: List<String>,
        val upcoming: Int,
    )

    /** Занятия одного типа: сколько впереди и в какой аудитории обычно. */
    data class KindSummary(
        val kind: String,
        val upcoming: Int,
        /** Аудитория, где прошло больше всего таких пар; null — дистанционно или аудитории разные. */
        val usualRoom: String?,
    )

    val upcoming: List<Lesson> get() = lessons.filter { !it.cancelled && it.end > now }
    val next: Lesson? get() = upcoming.firstOrNull()
    val exam: Lesson? get() = upcoming.firstOrNull { it.kind == "экзамен" || it.kind == "зачёт" }
    val consultation: Lesson?
        get() {
            val exam = exam ?: return null
            return upcoming.lastOrNull { it.kind == "консультация" && it.start < exam.start }
        }

    /** Курс в СДО: ссылка с любой дистанционной пары. */
    val lmsLink: String? get() = lessons.firstOrNull { it.lmsLink.isNotEmpty() }?.lmsLink

    /** Типы занятий (кроме сессии) в порядке: лекции, семинары, остальное. */
    val kinds: List<KindSummary>
        get() {
            val regular = lessons.filter { it.kind !in sessionKinds && !it.cancelled }
            val order = listOf("лекция", "семинар", "лабораторная", "физкультура")
            val grouped = regular.groupBy { it.kind }
            return grouped.keys
                .sortedWith(compareBy({ order.indexOf(it).let { i -> if (i < 0) 9 else i } }, { it }))
                .map { kind ->
                    val all = grouped.getValue(kind)
                    KindSummary(kind, all.count { it.end > now }, usualRoom(all))
                }
        }

    val lecturers: List<Lecturer>
        get() {
            val withName = lessons.filter { it.lecturer.isNotEmpty() && !it.cancelled }
            return withName.map { it.lecturer }.distinct().map { name ->
                val own = withName.filter { it.lecturer == name }
                val kinds = own.map { it.kind }.filter { it !in sessionKinds }.toSet().sorted().map(::kindPlural)
                Lecturer(name, own.firstOrNull { it.email.isNotEmpty() }?.email ?: "", kinds, own.count { it.end > now })
            }
        }

    /** «Впереди 24 пары: 11 лекций, 11 семинаров, консультация и экзамен». */
    val upcomingSummary: String
        get() {
            val list = upcoming
            if (list.isEmpty()) return "Пар больше нет"
            val parts = kinds.filter { it.upcoming > 0 }.map { "${it.upcoming} ${kindCount(it.kind, it.upcoming)}" }.toMutableList()
            val consults = list.count { it.kind == "консультация" }
            if (consults > 0) parts += if (consults == 1) "консультация" else "$consults консультации"
            exam?.let { parts += it.kind }
            val total = "Впереди ${list.size} ${plural(list.size, "пара", "пары", "пар")}"
            // Один тип занятий — расшифровка ничего не добавляет.
            if (parts.size <= 1) return total
            return "$total: ${parts.dropLast(1).joinToString(", ")} и ${parts.last()}"
        }

    override fun equals(other: Any?) = other is Subject && other.name == name
    override fun hashCode() = name.hashCode()

    companion object {
        fun all(lessons: List<Lesson>, now: Instant): List<Subject> =
            lessons.groupBy { it.subject }
                .map { (name, own) -> Subject(name, own.sortedBy { it.start }, now) }
                // Сначала те, у которых скоро пара; закончившиеся — в конце.
                .sortedWith(compareBy({ it.next?.start ?: Instant.MAX }, { it.name }))

        /** Аудитория, где больше половины пар; иначе null. */
        private fun usualRoom(lessons: List<Lesson>): String? {
            val rooms = lessons.filter { !it.isRemote && it.room.isNotEmpty() && !it.room.startsWith("Уточняется") }
                .map { it.shortLocation }
            val (room, count) = rooms.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return null
            return if (count * 2 > rooms.size && count >= 2) room else null
        }
    }
}

/** «лекция» → «Лекции». */
fun kindPlural(kind: String): String = when (kind) {
    "лекция" -> "Лекции"
    "семинар" -> "Семинары"
    "лабораторная" -> "Лабораторные"
    "физкультура" -> "Физкультура"
    else -> kind.capitalizedFirst()
}

/** «11 лекций», «2 семинара». */
private fun kindCount(kind: String, n: Int): String = when (kind) {
    "лекция" -> plural(n, "лекция", "лекции", "лекций")
    "семинар" -> plural(n, "семинар", "семинара", "семинаров")
    "лабораторная" -> plural(n, "лабораторная", "лабораторные", "лабораторных")
    "физкультура" -> plural(n, "занятие", "занятия", "занятий")
    else -> kind
}
