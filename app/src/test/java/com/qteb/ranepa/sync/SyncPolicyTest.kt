package com.qteb.ranepa.sync

import com.qteb.ranepa.sync.core.Keys
import com.qteb.ranepa.sync.core.Store
import com.qteb.ranepa.sync.core.dateOnly
import com.qteb.ranepa.sync.core.isoWeekday
import com.qteb.ranepa.sync.core.moscowStamp
import com.qteb.ranepa.sync.sync.SkipReason
import com.qteb.ranepa.sync.sync.Sync
import com.qteb.ranepa.sync.sync.SyncTrigger
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Проверяем только решения «идти или нет»: все случаи ниже не доходят до ЛК. */
class SyncPolicyTest {
    private lateinit var d: Store
    /** Вторник, 13 октября 2026, полдень. */
    private lateinit var tuesday: Instant

    @Before
    fun setUp() {
        moscowTests()
        tuesday = date(2026, 10, 13, 12)
        d = Store(MemoryPrefs())
        d.setTime(Keys.LAST_FULL_AT, tuesday.minusSeconds(3600))
    }

    private fun skip(trigger: SyncTrigger, now: Instant) = Sync.skipReason(d, trigger, now)

    private fun lessonOn(day: Instant) {
        val stamp = moscowStamp(day.dateOnly).take(10)
        d.saveDays(buildJsonArray {
            addJsonObject {
                put("date", "${stamp}T00:00:00")
                putJsonArray("discs") {
                    addJsonObject {
                        put("start_date", "${stamp}T10:00:00")
                        put("end_date", "${stamp}T11:30:00")
                    }
                }
            }
        })
    }

    @Test fun backoffAfterServerError() {
        d.setTime(Keys.BACKOFF_UNTIL, tuesday.plusSeconds(8 * 3600))
        assertEquals(SkipReason.BACKOFF, skip(SyncTrigger.MANUAL, tuesday))
    }

    /** Сессия истекла: фон и открытие в ЛК не ходят, «потянуть вниз» пробует. */
    @Test fun expiredSessionSkipsAutomatic() {
        d.setBool(Keys.SESSION_EXPIRED, true)
        assertEquals(SkipReason.NEED_LOGIN, skip(SyncTrigger.BACKGROUND, tuesday))
        assertEquals(SkipReason.NEED_LOGIN, skip(SyncTrigger.OPEN, tuesday))
        assertNull(skip(SyncTrigger.MANUAL, tuesday))
    }

    /** Ночью тоже ходим — иначе сессия ЛК истечёт. */
    @Test fun nightStillRuns() {
        assertNull(skip(SyncTrigger.BACKGROUND, date(2026, 10, 13, 3)))
    }

    @Test fun backgroundInterval() {
        lessonOn(date(2026, 10, 14, 0))
        d.setTime(Keys.LAST_ATTEMPT_AT, tuesday.minusSeconds(30 * 60))
        assertEquals(SkipReason.TOO_SOON, skip(SyncTrigger.BACKGROUND, tuesday))
        // Будни днём — раз в час.
        d.setTime(Keys.LAST_ATTEMPT_AT, tuesday.minusSeconds(3600))
        assertNull(skip(SyncTrigger.BACKGROUND, tuesday))
    }

    @Test fun intervals() {
        assertEquals(Duration.ofHours(4), Sync.interval(date(2026, 10, 13, 3)))   // ночь в будни
        assertEquals(Duration.ofHours(1), Sync.interval(date(2026, 10, 13, 6)))   // будни утром
        assertEquals(Duration.ofHours(3), Sync.interval(date(2026, 10, 13, 22)))
        assertEquals(Duration.ofHours(2), Sync.interval(date(2026, 10, 17, 12)))  // суббота
        assertEquals(Duration.ofHours(4), Sync.interval(date(2026, 10, 17, 3)))   // ночь на субботу
        assertEquals(Duration.ofHours(12), Sync.interval(date(2026, 10, 18, 12))) // воскресенье днём
        assertEquals(Duration.ofHours(3), Sync.interval(date(2026, 10, 18, 19)))  // воскресенье вечером
    }

    /** Пн–Сб — не реже раза в 4 часа, в воскресенье — раз в 12. */
    @Test fun maxGaps() {
        var t = date(2026, 10, 12, 0)
        val end = date(2026, 10, 19, 0)
        while (t < end) {
            val next = Sync.nextRun(t)
            val limit = if (t.isoWeekday == 7 || next.isoWeekday == 7) Duration.ofHours(12) else Duration.ofHours(4)
            assertTrue("$t", Duration.between(t, next) <= limit)
            t = next
        }
    }

    @Test fun nextRun() {
        assertEquals(tuesday.plusSeconds(3600), Sync.nextRun(tuesday))
        // Вторник 23:30 → ночью через 4 часа.
        assertEquals(date(2026, 10, 14, 3, 30), Sync.nextRun(date(2026, 10, 13, 23, 30)))
        // Воскресенье 14:00 → не ждать 12 часов, а в 18:00.
        assertEquals(date(2026, 10, 18, 18), Sync.nextRun(date(2026, 10, 18, 14)))
    }

    @Test fun holidaysEveryFourHours() {
        d.setTime(Keys.LAST_ATTEMPT_AT, tuesday.minusSeconds(3 * 3600))
        assertEquals(SkipReason.TOO_SOON, skip(SyncTrigger.BACKGROUND, tuesday))
        d.setTime(Keys.LAST_ATTEMPT_AT, tuesday.minusSeconds(4 * 3600))
        assertNull(skip(SyncTrigger.BACKGROUND, tuesday))
    }

    @Test fun backgroundRunsWhenLessonsSoon() {
        lessonOn(date(2026, 10, 14, 0))
        assertNull(skip(SyncTrigger.BACKGROUND, tuesday))
    }

    @Test fun semesterWeekly() {
        assertTrue(Sync.isSemesterDue(d, tuesday))
        d.setTime(Keys.LAST_SEMESTER_AT, tuesday.minus(Duration.ofDays(6)))
        assertFalse(Sync.isSemesterDue(d, tuesday))
        d.setTime(Keys.LAST_SEMESTER_AT, tuesday.minus(Duration.ofDays(7)))
        assertTrue(Sync.isSemesterDue(d, tuesday))
    }

    @Test fun futureTimestampIsStale() {
        lessonOn(date(2026, 10, 14, 0))
        d.setTime(Keys.LAST_ATTEMPT_AT, date(2027, 1, 20, 11))
        d.setTime(Keys.FETCHED_AT, date(2027, 1, 20, 11))
        assertNull(skip(SyncTrigger.BACKGROUND, tuesday))
        assertNull(skip(SyncTrigger.OPEN, tuesday))
    }

    @Test fun manualRightAfterPrevious() {
        d.setTime(Keys.FETCHED_AT, tuesday.minusSeconds(30))
        assertEquals(SkipReason.TOO_SOON, skip(SyncTrigger.MANUAL, tuesday))
    }

    @Test fun openWithFreshData() {
        d.setTime(Keys.FETCHED_AT, tuesday.minusSeconds(30 * 60))
        assertEquals(SkipReason.NOT_NEEDED, skip(SyncTrigger.OPEN, tuesday))
        // Днём в будни данные старше часа обновляются при открытии.
        d.setTime(Keys.FETCHED_AT, tuesday.minusSeconds(3600))
        assertNull(skip(SyncTrigger.OPEN, tuesday))
    }
}
