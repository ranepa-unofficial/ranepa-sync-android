package com.qteb.ranepa.sync.core

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** План обучения из ЛК: предметы всех семестров с формой контроля и даты сессий. */
@Serializable
data class StudyPlan(val items: List<Item>, val sessions: List<Session>) {
    @Serializable
    data class Item(
        val name: String,
        /** «Зачет», «Экзамен», «Зачет с оценкой»…; null — код не из справочника. */
        val control: String? = null,
        val hours: Int = 0,
        val ze: Int = 0,
        val course: Int = 0,
        /** «1-й семестр». */
        val semester: String = "",
        val elective: Boolean = false,
        val practice: Boolean = false,
        val courseWork: Boolean = false,
        val facultative: Boolean = false,
        val selected: Boolean = true,
    ) {
        val semesterNumber: Int get() = semester.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        /** «СОЦИОГУМАНИТАРНАЯ ЧАСТЬ» — заголовок модуля, не предмет. */
        val isHeader: Boolean get() = name == name.uppercase() && name.any { it.isLetter() }
        /** «Экзамен», «Зачёт», «Диф. зачёт», «Курсовая» — коротко для пометки. */
        val form: String? get() = control?.let(::shortForm)
        val isExam: Boolean get() = control?.contains("экзамен", ignoreCase = true) ?: false
        val isCredit: Boolean get() = !isExam && (control?.contains("зач", ignoreCase = true) ?: false)
    }

    @Serializable
    data class Session(
        val course: String = "",
        val semester: String = "",
        /** «11.01.2027». */
        val start: String = "",
        val end: String = "",
    ) {
        val semesterNumber: Int get() = semester.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        val startDate: Instant? get() = date(start)
        /** Конец дня последнего дня сессии. */
        val endDate: Instant? get() = date(end)?.addingDays(1)

        private fun date(s: String): Instant? = runCatching {
            LocalDate.parse(s, DateTimeFormatter.ofPattern("dd.MM.yyyy")).atStartOfDay(zone).toInstant()
        }.getOrNull()
    }

    /** Предметы семестра без заголовков модулей и невыбранных дисциплин по выбору. */
    fun subjects(semester: Int): List<Item> {
        val seen = mutableSetOf<String>()
        return items.filter { it.semesterNumber == semester && !it.isHeader && it.selected && seen.add(it.name) }
    }

    val semesters: List<Int> get() = items.map { it.semesterNumber }.toSet().filter { it > 0 }.sorted()

    /** Сессии семестра (бывает две — у последнего курса), по порядку. */
    fun sessions(semester: Int): List<Session> =
        sessions.filter { it.semesterNumber == semester }.sortedBy { it.startDate ?: Instant.MIN }

    /** Текущий семестр — тот, чья сессия ещё не закончилась. */
    fun currentSemester(now: Instant): Int? =
        sessions.filter { (it.endDate ?: Instant.MIN) > now }
            .minByOrNull { it.endDate ?: Instant.MAX }?.semesterNumber

    /** «1-й курс» для семестра. */
    fun course(semester: Int): String? = sessions(semester).firstOrNull()?.course

    /** Предмет из плана по названию в расписании. */
    fun item(name: String, semester: Int?): Item? {
        val pool = if (semester != null) items.filter { it.semesterNumber == semester } else items
        return pool.firstOrNull { it.name == name } ?: pool.firstOrNull { it.name.lowercase() == name.lowercase() }
    }
}

private fun shortForm(control: String): String {
    val c = control.lowercase()
    return when {
        c.contains("экзамен") -> "Экзамен"
        c.contains("с оценкой") || c.contains("диф") -> "Диф. зачёт"
        c.contains("зачет") || c.contains("зачёт") -> "Зачёт"
        c.contains("курсов") -> "Курсовая"
        else -> control
    }
}
