package com.qteb.ranepa.sync.core

import java.time.Instant

/**
 * Сессия текущего семестра. Состав и даты — из плана обучения, конкретные
 * дата и аудитория — из расписания, когда появятся. Без плана (демо, план
 * не загрузился) — только события расписания.
 */
class ExamSession private constructor(
    val exams: List<Entry>,
    val credits: List<Entry>,
    /** Даты сессии по плану. */
    val start: Instant?,
    val end: Instant?,
    private val now: Instant,
) {
    data class Entry(
        val subject: String,
        /** «Экзамен», «Зачёт», «Диф. зачёт». */
        val form: String,
        /** Событие в расписании; null — дата не назначена. */
        val event: Lesson?,
        val consultation: Lesson?,
    ) {
        val id: String get() = subject + form
    }

    companion object {
        /** Карточка на главном экране — за столько дней до начала сессии. */
        const val CARD_DAYS = 30

        /** null — сессии нет или она прошла. */
        fun of(lessons: List<Lesson>, plan: StudyPlan?, now: Instant): ExamSession? {
            val active = lessons.filter { !it.cancelled }
            val all = active.filter { it.kind == "экзамен" || it.kind == "зачёт" }
            val events = all.filter { it.end > now }
            fun consultation(exam: Lesson?): Lesson? {
                exam ?: return null
                return active.lastOrNull { it.kind == "консультация" && it.subject == exam.subject && it.start < exam.start }
            }
            // С планом — и прошедшие события: сданное остаётся в сессии.
            fun event(subject: String, exam: Boolean): Lesson? {
                val match = all.filter { it.subject.lowercase() == subject.lowercase() && (it.kind == "экзамен") == exam }
                return match.firstOrNull { it.end > now } ?: match.lastOrNull()
            }

            val start: Instant?
            val end: Instant?
            var exams: List<Entry>
            var credits: List<Entry>
            val semester = plan?.currentSemester(now)
            if (plan != null && semester != null) {
                val items = plan.subjects(semester)
                val sessions = plan.sessions(semester)
                start = sessions.firstOrNull()?.startDate
                end = sessions.lastOrNull()?.endDate
                exams = items.filter { it.isExam }.map { i ->
                    val e = event(i.name, exam = true)
                    Entry(i.name, i.form ?: "Экзамен", e, consultation(e))
                }
                credits = items.filter { it.isCredit }.map { i ->
                    Entry(i.name, i.form ?: "Зачёт", event(i.name, exam = false), null)
                }
                // События расписания, которых нет в плане, — тоже показываем.
                for (e in events) {
                    if ((exams + credits).any { it.subject.lowercase() == e.subject.lowercase() }) continue
                    val entry = Entry(e.subject, e.kind.capitalizedFirst(), e, consultation(e))
                    if (e.kind == "экзамен") exams = exams + entry else credits = credits + entry
                }
                exams = exams.sortedWith(order)
                credits = credits.sortedWith(order)
            } else {
                start = null
                end = null
                exams = events.filter { it.kind == "экзамен" }.map { Entry(it.subject, "Экзамен", it, consultation(it)) }
                credits = events.filter { it.kind == "зачёт" }.map { Entry(it.subject, "Зачёт", it, null) }
            }
            if (exams.isEmpty() && credits.isEmpty()) return null
            // Сессия прошла.
            if (end != null && end <= now && (exams + credits).all { it.event == null }) return null
            return ExamSession(exams, credits, start, end, now)
        }

        /** С датой — по времени, без даты — в конце по названию. */
        private val order = Comparator<Entry> { a, b ->
            val x = a.event
            val y = b.event
            when {
                x != null && y != null -> x.start.compareTo(y.start)
                x != null -> -1
                y != null -> 1
                else -> a.subject.compareTo(b.subject)
            }
        }
    }

    val all: List<Entry> get() = exams + credits

    fun isDone(e: Entry, now: Instant = this.now): Boolean = e.event?.let { it.end <= now } ?: false
    /** Сколько ещё сдавать: без даты или впереди. */
    fun remaining(now: Instant = this.now): Int = all.count { !isDone(it, now) }
    /** Ближайшее событие с датой, которое ещё впереди. */
    val nextEvent: Lesson? get() = all.mapNotNull { it.event }.filter { it.end > now }.minByOrNull { it.start }
    /** С чего считать «через N дней»: начало сессии или первое событие. */
    val firstDate: Instant? get() = listOfNotNull(start, nextEvent?.start).minOrNull()
    /** Последний день: конец сессии или последнее событие. */
    val lastDate: Instant?
        get() = listOfNotNull(end?.addingDays(-1), all.mapNotNull { it.event?.start }.maxOrNull()).maxOrNull()

    fun isRunning(now: Instant = this.now): Boolean = firstDate?.let { it <= now } ?: false

    /** Показывать ли карточку: до начала не больше месяца. */
    fun showsCard(now: Instant = this.now): Boolean = firstDate?.let { it.daysSince(now) <= CARD_DAYS } ?: false

    /** «2 экзамена и 7 зачётов». */
    val summary: String
        get() = listOfNotNull(
            countLabel(exams.size, "экзамен", "экзамена", "экзаменов"),
            countLabel(credits.size, "зачёт", "зачёта", "зачётов"),
        ).joinToString(" и ")

    /** «11–30 января». */
    val dates: String
        get() {
            val a = firstDate ?: return ""
            val b = lastDate ?: return ""
            return dateRange(a, b)
        }
}

/** null для нуля, «экзамен» для одного, «2 экзамена» дальше. */
private fun countLabel(n: Int, one: String, few: String, many: String): String? {
    if (n <= 0) return null
    return if (n == 1) one else "$n ${plural(n, one, few, many)}"
}
