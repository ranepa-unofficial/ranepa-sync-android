package com.qteb.ranepa.sync

import com.qteb.ranepa.sync.core.Lesson
import com.qteb.ranepa.sync.system.LiveUpdatePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** Когда Live Update виден и когда появляется по расписанию. */
class LiveUpdatePlanTest {
    @Before fun zone() = moscowTests()

    private fun pair(day: Int, hour: Int, minute: Int = 0): Lesson =
        lesson(date(2026, 10, day, hour, minute), subject = "Пара $day $hour:$minute")

    /** Вторник 13-го: пары в 9:00 и 10:40; среда 14-го: 12:20. */
    private val week by lazy { listOf(pair(13, 9), pair(13, 10, 40), pair(14, 12, 20)) }

    private fun plan(now: Instant, running: Boolean = false, minutes: Int = 15) = LiveUpdatePlan(week, now, running, minutes)

    /** Ночью сегодняшние пары не показываем — только появление вместе с напоминанием. */
    @Test fun nightSchedulesWithReminder() {
        val p = plan(date(2026, 10, 13, 1))
        assertFalse(p.startNow)
        assertEquals(date(2026, 10, 13, 8, 45), p.scheduled?.at)
        assertEquals(week[0], p.scheduled?.state?.lesson)
        assertEquals(week[1], p.scheduled?.state?.next)
    }

    @Test fun startsTwoHoursBefore() {
        assertFalse(plan(date(2026, 10, 13, 6, 59)).startNow)
        val p = plan(date(2026, 10, 13, 7))
        assertTrue(p.startNow)
        assertFalse(p.today?.isCurrent ?: true)
        // Сегодняшний уже показывается — по расписанию следующий день.
        assertEquals(date(2026, 10, 14, 12, 5), p.scheduled?.at)
    }

    @Test fun duringLesson() {
        val p = plan(date(2026, 10, 13, 9, 30), running = true)
        assertFalse(p.startNow)
        assertEquals(week[0], p.today?.lesson)
        assertTrue(p.today?.isCurrent ?: false)
        assertEquals(week[1], p.today?.next)
    }

    /** После последней пары дня уведомление не нужно. */
    @Test fun endsAfterLastLesson() {
        val p = plan(date(2026, 10, 13, 12, 1), running = true)
        assertNull(p.today)
        assertFalse(p.startNow)
        assertEquals(date(2026, 10, 14, 12, 5), p.scheduled?.at)
    }

    /** Длинное окно: уже видимое уведомление остаётся до конца дня. */
    @Test fun longBreakKeepsRunning() {
        val lessons = listOf(pair(13, 9), pair(13, 16))
        val now = date(2026, 10, 13, 11)
        val p = LiveUpdatePlan(lessons, now, running = true, reminderMinutes = 15)
        assertEquals(lessons[1], p.today?.lesson)
        assertFalse(p.startNow)
        // А новое в окне не показываем: до пары больше 2 часов.
        assertFalse(LiveUpdatePlan(lessons, now, running = false, reminderMinutes = 15).startNow)
    }

    /** Напоминания выключены — без появления по расписанию. */
    @Test fun noScheduleWithoutReminders() {
        assertNull(plan(date(2026, 10, 13, 1), minutes = 0).scheduled)
    }

    /** Сегодня уже идёт — по расписанию только следующий день. */
    @Test fun scheduleSkipsStartedDay() {
        val p = plan(date(2026, 10, 13, 8, 50), running = true)
        assertEquals(week[2], p.scheduled?.state?.lesson)
    }
}
