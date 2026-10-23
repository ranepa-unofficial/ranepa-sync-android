package com.qteb.ranepa.sync

import com.qteb.ranepa.sync.core.ExamSession
import com.qteb.ranepa.sync.core.Lesson
import com.qteb.ranepa.sync.core.StudyPlan
import com.qteb.ranepa.sync.core.addingDays
import com.qteb.ranepa.sync.core.describeChanges
import com.qteb.ranepa.sync.core.hm
import com.qteb.ranepa.sync.core.json
import com.qteb.ranepa.sync.core.monday
import com.qteb.ranepa.sync.core.parseLessons
import com.qteb.ranepa.sync.core.sameDay
import com.qteb.ranepa.sync.sync.Demo
import com.qteb.ranepa.sync.sync.FETCH_WEEKS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class DemoTest {
    @Before fun zone() = moscowTests()

    private fun lessons(now: Instant, revision: Int = 0): List<Lesson> = parseLessons(Demo.result(now, revision).days)

    /** План в демо: сессия с экзаменом и зачётами видна, карточка — в пределах месяца. */
    @Test fun demoPlanSession() {
        val now = date(2026, 10, 13, 11, 5)
        val result = Demo.result(now)
        val plan = json.decodeFromString<StudyPlan>(result.plan!!)
        assertEquals(1, plan.currentSemester(now))
        val session = ExamSession.of(parseLessons(result.days), plan, now)!!
        assertEquals(listOf("Математика", "Экономическая теория"), session.exams.map { it.subject })
        assertNotNull(session.exams[0].event)
        assertEquals(5, session.credits.size)
        assertTrue(session.showsCard(now))
    }

    @Test fun tuesdayMorningRegularGrid() {
        val now = date(2026, 10, 13, 11, 5)
        val today = parseLessons(Demo.result(now, shift = 0).days).filter { it.start.sameDay(now) }
        assertEquals(listOf("09:00", "10:40", "12:20", "14:00"), today.map { hm(it.start) })
        assertEquals(1, today.count { it.start <= now && now < it.end })
    }

    @Test fun todayAlwaysHasLessonsWithinDay() {
        for (hour in listOf(0, 3, 8, 15, 21, 23)) {
            // Воскресенье: берётся расписание вторника.
            val now = date(2026, 10, 18, hour, 50)
            val today = lessons(now).filter { it.start.sameDay(now) || it.end.sameDay(now) }
            assertFalse("$hour:50", today.isEmpty())
            for (l in today) assertTrue(l.start.sameDay(now) && l.end.sameDay(now))
        }
    }

    @Test fun monthWithCancellationConsultationAndCredit() {
        val now = date(2026, 10, 13, 11, 5)
        val all = lessons(now)
        assertTrue(all.first().start > now.monday)
        assertTrue(all.last().start < now.monday.addingDays(7 * FETCH_WEEKS))
        assertEquals(1, all.count { it.cancelled })
        assertTrue(all.map { it.kind }.toSet().containsAll(
            setOf("лекция", "семинар", "лабораторная", "физкультура", "консультация", "зачёт")))
    }

    @Test fun oddRevisionChangesTomorrowRoom() {
        val now = date(2026, 10, 13, 11, 5)
        val tomorrow = now.addingDays(1)
        fun first(revision: Int) = lessons(now, revision).first { it.start.sameDay(tomorrow) }
        assertNotEquals(first(0).room, first(1).room)
        assertEquals(first(0).room, first(2).room)
    }

    @Test fun changesDescribeMovedLesson() {
        val now = date(2026, 10, 13, 11, 5)
        val changes = describeChanges(lessons(now), lessons(now, revision = 1), now)
        assertEquals(1, changes.size)
        assertTrue(changes[0], changes[0].contains("аудитория:"))
    }
}
