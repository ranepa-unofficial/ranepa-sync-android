package com.qteb.ranepa.sync

import com.qteb.ranepa.sync.core.durationText
import com.qteb.ranepa.sync.core.kindOf
import com.qteb.ranepa.sync.core.moscowDate
import com.qteb.ranepa.sync.core.moscowStamp
import com.qteb.ranepa.sync.core.plural
import com.qteb.ranepa.sync.core.whenLabel
import com.qteb.ranepa.sync.system.Campus
import com.qteb.ranepa.sync.system.Places
import com.qteb.ranepa.sync.widget.WidgetState
import com.qteb.ranepa.sync.widget.durationLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

class FormatTest {
    @Before fun zone() = moscowTests()

    @Test fun plurals() {
        assertEquals("пара", plural(1, "пара", "пары", "пар"))
        assertEquals("пары", plural(3, "пара", "пары", "пар"))
        assertEquals("пар", plural(12, "пара", "пары", "пар"))
        assertEquals("пара", plural(21, "пара", "пары", "пар"))
        assertEquals("пар", plural(111, "пара", "пары", "пар"))
    }

    @Test fun durations() {
        assertEquals("40 мин", durationText(40 * 60 + 59))
        assertEquals("1 ч", durationText(3600))
        assertEquals("1 ч 40 мин", durationText(6000))
        // В виджете — с округлением вверх: в последнюю минуту «1 мин».
        assertEquals("1 мин", durationLabel(5))
        assertEquals("55 мин", durationLabel(54 * 60 + 1))
    }

    @Test fun kinds() {
        assertEquals("лекция", kindOf("Лекция", "Математика"))
        assertEquals("семинар", kindOf("Практическое занятие", "Математика"))
        assertEquals("физкультура", kindOf("Практическое занятие", "Элективные дисциплины по физической культуре и спорту"))
        assertEquals("зачёт", kindOf("Зачёт", "Правоведение"))
    }

    @Test fun moscowTimeRoundTrip() {
        val t = moscowDate("2026-10-12T09:00:00")!!
        assertEquals("2026-10-12T09:00:00", moscowStamp(t))
        assertEquals(date(2026, 10, 12, 9), t)
    }

    @Test fun rooms() {
        val l = lesson(date(2026, 10, 13, 10, 40)).copy(room = "1 - 2262 (24) П+ПК Блок 2F (Вернадского, 84 - корпус 1)")
        assertEquals("1 - 2262", l.shortLocation)
        assertEquals("1 - 2262, корпус 1", l.placeLabel)
        assertEquals("1 - 2262, блок 2F", l.mapTitle)
        assertEquals("Вернадского, 84, корпус 1", Places.address(l.room))
        assertEquals("Москва, проспект Вернадского, 84, корпус 1", Places.query("Вернадского, 84, корпус 1"))
        assertNotNull(Campus.building("Вернадского, 84, корпус 1"))
        assertNull(Places.address("5 - 201 (44)"))
        assertEquals("Вернадского, 82, стадион кампуса", Places.address("Стадион (Вернадского, 82)"))
    }

    @Test fun whenLabels() {
        val now = date(2026, 10, 13, 11, 5)
        assertEquals("сегодня в 12:20", whenLabel(date(2026, 10, 13, 12, 20), now))
        assertEquals("завтра в 09:00", whenLabel(date(2026, 10, 14, 9), now))
        assertEquals("в среду 21 октября в 09:00", whenLabel(date(2026, 10, 21, 9), now))
    }

    @Test fun widgetLead() {
        val now = date(2026, 10, 13, 11, 5)
        val lessons = listOf(
            lesson(date(2026, 10, 13, 10, 40), "Иностранный язык", "семинар"),
            lesson(date(2026, 10, 13, 12, 20), "Физкультура", "физкультура"),
            lesson(date(2026, 10, 14, 10, 40), "Математика"),
        )
        val state = WidgetState(lessons, now)
        assertEquals("Иностранный язык", state.lead?.subject)
        assertEquals(WidgetState.LeadText("До конца", "55 мин", "до 12:00"), state.leadText(state.lead!!))
        // В перерыве — отсчёт до начала, вечером — день и время.
        val pause = WidgetState(lessons, date(2026, 10, 13, 12, 5))
        assertEquals(WidgetState.LeadText("До начала", "15 мин", "в 12:20"), pause.leadText(pause.lead!!))
        val evening = WidgetState(lessons, date(2026, 10, 13, 20))
        assertEquals(WidgetState.LeadText("Завтра", "10:40", "до 12:00"), evening.leadText(evening.lead!!))
        assertEquals(2, state.days(state.lead!!).size)
    }

    /** Копия политики в приложении совпадает с той, что в корне репозитория. */
    @Test fun privacyCopyIsCurrent() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "PRIVACY.md").exists() }
        assertEquals(File(root, "PRIVACY.md").readText(), File(root, "app/src/main/assets/PRIVACY.md").readText())
    }
}
