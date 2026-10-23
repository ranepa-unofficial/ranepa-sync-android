package com.qteb.ranepa.sync

import com.qteb.ranepa.sync.core.ExamSession
import com.qteb.ranepa.sync.core.StudyPlan
import com.qteb.ranepa.sync.core.daysUntil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExamSessionTest {
    @Before fun zone() = moscowTests()

    private fun l(day: Int, month: Int, subject: String, kind: String, cancelled: Boolean = false) =
        lesson(date(if (month == 1) 2027 else 2026, month, day, 10), subject, kind, cancelled = cancelled)

    private fun item(name: String, control: String?, semester: String = "1-й семестр") =
        StudyPlan.Item(name = name, control = control, hours = 72, ze = 2, course = 1, semester = semester)

    private val now get() = date(2026, 12, 20, 12)

    private val plan = StudyPlan(
        items = listOf(
            item("ФУНДАМЕНТАЛЬНАЯ ЧАСТЬ", "Экзамен"),
            item("Высшая математика", "Экзамен"),
            item("Информатика", "Зачет"),
            item("История России", "Зачет"),
            item("Макроэкономика", "Экзамен", semester = "2-й семестр"),
        ),
        sessions = listOf(
            StudyPlan.Session("1-й курс", "1-й семестр", "11.01.2027", "30.01.2027"),
            StudyPlan.Session("1-й курс", "2-й семестр", "14.06.2027", "03.07.2027"),
        ),
    )

    @Test fun planDefinesSession() {
        val s = ExamSession.of(listOf(
            l(18, 1, "Информатика", "зачёт"),
            l(20, 1, "Высшая математика", "консультация"),
            l(21, 1, "Высшая математика", "экзамен"),
        ), plan, now)!!
        assertEquals(listOf("Высшая математика"), s.exams.map { it.subject })
        assertNotNull(s.exams[0].consultation)
        // Зачёт по истории — без даты, но в списке.
        assertEquals(listOf("Информатика", "История России"), s.credits.map { it.subject })
        assertNull(s.credits[1].event)
        assertEquals("экзамен и 2 зачёта", s.summary)
        assertEquals("11–30 января", s.dates)
    }

    @Test fun currentSemester() {
        assertEquals(1, plan.currentSemester(now))
        assertEquals(2, plan.currentSemester(date(2027, 2, 10, 12)))
        assertEquals(listOf("Высшая математика", "Информатика", "История России"), plan.subjects(1).map { it.name })
    }

    @Test fun withoutPlan() {
        assertNull(ExamSession.of(listOf(l(18, 1, "Информатика", "лекция")), null, now))
        assertNull(ExamSession.of(listOf(l(18, 1, "Информатика", "зачёт", cancelled = true)), null, now))
        val s = ExamSession.of(listOf(l(18, 1, "Информатика", "зачёт")), null, now)!!
        assertTrue(s.showsCard(now))
        assertFalse(s.showsCard(date(2026, 12, 1, 12)))
    }

    @Test fun words() {
        assertEquals("завтра", daysUntil(date(2026, 12, 21, 9), now))
        assertEquals("через 2 дня", daysUntil(date(2026, 12, 22, 9), now))
        assertEquals("через 13 дней", daysUntil(date(2027, 1, 2, 9), now))
        assertEquals("через 21 день", daysUntil(date(2027, 1, 10, 9), now))
    }
}
