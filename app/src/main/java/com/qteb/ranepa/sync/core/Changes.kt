package com.qteb.ranepa.sync.core

import java.time.Duration
import java.time.Instant

/**
 * За сколько дней вперёд сообщаем об изменениях: дальние недели в ЛК
 * часто правят, и уведомления о них — шум.
 */
val changesHorizon: Duration = Duration.ofDays(7)

/**
 * Сравнивает старое и новое расписание и описывает изменения в ближайшие
 * дни строками вида «Микроэкономика, ср 22.10: перенесли с 12:20 на 14:00».
 * Пары сопоставляются по дню, предмету и типу занятия.
 */
fun describeChanges(before: List<Lesson>, after: List<Lesson>, now: Instant): List<String> {
    val horizon = now.plus(changesHorizon)
    fun relevant(l: Lesson) = l.start > now && l.start < horizon
    fun key(l: Lesson) = "${l.start.dateOnly.epochSecond}|${l.subject}|${l.kind}"

    val old = before.groupBy(::key)
    val fresh = after.groupBy(::key)
    val changes = mutableListOf<Pair<Instant, String>>()
    fun add(l: Lesson, what: String) {
        val day = weekdaysShort[l.start.isoWeekday - 1].lowercase()
        changes += l.start to "${l.subject}, $day ${"%02d.%02d".format(l.start.day, l.start.month)}: $what"
    }

    for (k in old.keys + fresh.keys) {
        val a = old[k].orEmpty()
        val b = fresh[k].orEmpty()
        for (i in 0 until maxOf(a.size, b.size)) {
            val was = a.getOrNull(i)
            val cur = b.getOrNull(i)
            if (was != null && cur != null) {
                if (!relevant(was) && !relevant(cur)) continue
                when {
                    was.cancelled != cur.cancelled -> add(cur, if (cur.cancelled) "пару отменили" else "пару вернули")
                    was.start != cur.start || was.end != cur.end -> add(cur, "перенесли с ${hm(was.start)} на ${hm(cur.start)}")
                    was.room != cur.room -> add(cur, "аудитория: ${cur.shortLocation}")
                }
            } else if (cur != null && relevant(cur) && !cur.cancelled) {
                add(cur, "добавили пару в ${hm(cur.start)}")
            } else if (was != null && relevant(was) && !was.cancelled) {
                add(was, "пару в ${hm(was.start)} убрали")
            }
        }
    }
    return changes.sortedBy { it.first }.map { it.second }
}
