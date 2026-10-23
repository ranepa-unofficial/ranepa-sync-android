package com.qteb.ranepa.sync.widget

import com.qteb.ranepa.sync.core.Lesson
import com.qteb.ranepa.sync.core.addingDays
import com.qteb.ranepa.sync.core.dateOnly
import com.qteb.ranepa.sync.core.daysSince
import com.qteb.ranepa.sync.core.hm
import com.qteb.ranepa.sync.core.sameDay
import com.qteb.ranepa.sync.core.weekdaysLong
import java.time.Duration
import java.time.Instant

/** До пары меньше трёх часов — показываем обратный отсчёт, иначе время. */
val countdownWindow: Duration = Duration.ofHours(3)

/** «40 мин», «1 ч», «1 ч 2 мин» — с округлением вверх, как в виджетах iOS. */
fun durationLabel(seconds: Long): String {
    val total = maxOf(0L, (seconds + 59) / 60)
    val h = total / 60
    val m = total % 60
    if (h == 0L) return "$m мин"
    return if (m == 0L) "$h ч" else "$h ч $m мин"
}

/** Что сейчас: идущая пара, следующая или ближайшая в другой день. Без побочных эффектов — для тестов. */
class WidgetState(all: List<Lesson>, val now: Instant) {
    /** Пары, которые ещё не кончились, вместе с отменёнными. */
    val lessons: List<Lesson> = all.filter { it.end > now && it.start < now.addingDays(14) }
    val loaded: Boolean = all.isNotEmpty()
    private val active = lessons.filter { !it.cancelled }
    val current: Lesson? = active.firstOrNull { it.start <= now && now < it.end }
    val next: Lesson? = active.firstOrNull { it.start > now }

    /** Пара, о которой виджет говорит в первую очередь: идущая или следующая. */
    val lead: Lesson? get() = current ?: next

    fun isOngoing(l: Lesson) = l.start <= now && now < l.end

    /** Отсчёт до начала: пара сегодня и скоро. */
    fun countsDown(l: Lesson) = l.start.sameDay(now) && Duration.between(now, l.start) <= countdownWindow

    /** «Сегодня», «Завтра», «Четверг». */
    fun dayName(date: Instant): String = when (date.daysSince(now)) {
        0 -> "Сегодня"
        1 -> "Завтра"
        else -> weekdaysLong[(date.atZone(com.qteb.ranepa.sync.core.zone).dayOfWeek.value) - 1]
    }

    data class LeadText(val caption: String, val value: String, val trailing: String)

    /** Подпись над крупным числом, само число и строка справа вверху. */
    fun leadText(l: Lesson): LeadText {
        if (isOngoing(l)) return LeadText("До конца", durationLabel(Duration.between(now, l.end).seconds), "до ${hm(l.end)}")
        if (countsDown(l)) return LeadText("До начала", durationLabel(Duration.between(now, l.start).seconds), "в ${hm(l.start)}")
        return LeadText(dayName(l.start), hm(l.start), "до ${hm(l.end)}")
    }

    /** Дни с оставшимися парами (и отменёнными), начиная с дня ведущей, не больше двух. */
    fun days(lead: Lesson): List<Pair<Instant, List<Lesson>>> {
        val dayStart = lead.start.dateOnly
        val result = mutableListOf<Pair<Instant, MutableList<Lesson>>>()
        for (l in lessons) {
            if (l.start < dayStart) continue
            val last = result.lastOrNull()
            if (last != null && last.first.sameDay(l.start)) last.second += l
            else if (result.size < 2) result += l.start to mutableListOf(l)
            else break
        }
        return result
    }

    /** Нужна ли запись каждую минуту: идёт пара или до неё меньше трёх часов. */
    val needsMinuteTicks: Boolean
        get() = active.any { it.end > now && it.start.minus(countdownWindow) < now }
}
