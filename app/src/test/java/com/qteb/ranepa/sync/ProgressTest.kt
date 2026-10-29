package com.qteb.ranepa.sync

import com.qteb.ranepa.sync.core.Progress
import com.qteb.ranepa.sync.core.ProgressHistory
import com.qteb.ranepa.sync.core.Store
import com.qteb.ranepa.sync.core.addingDays
import com.qteb.ranepa.sync.core.percent
import com.qteb.ranepa.sync.core.progress
import com.qteb.ranepa.sync.core.saveProgress
import com.qteb.ranepa.sync.sync.Demo
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** Ответ `dashboard/progress` — в той же форме, что приходит из ЛК. */
class ProgressTest {
    private lateinit var d: Store
    private lateinit var day: Instant

    @Before
    fun setUp() {
        moscowTests()
        d = Store(MemoryPrefs())
        day = date(2026, 10, 30, 12)
    }

    private val labels = listOf(
        "Информатика [1 семестр] [Иванова И.И.] [ОБ-1курс 1сем.-гр.6]",
        "Иностранный язык_ОАФ_1 курс_B1-B2 [1 семестр] [] []",
        "Элективные дисциплины по физической культуре и спорту [1 семестр] [Петров П.П.] [753_юз_1_вт3_об]",
    )

    private fun raw(
        points: List<Any>,
        months: List<Triple<String, Int, Int>> = listOf(Triple("Сентябрь", 63, 41), Triple("Октябрь", 77, 57), Triple("Ноябрь", 63, 0)),
        avg: JsonElement = JsonNull,
    ): JsonObject = buildJsonObject {
        putJsonObject("brs") {
            putJsonArray("labels") { labels.take(points.size).forEach { add(JsonPrimitive(it)) } }
            putJsonArray("data") { points.forEach { add(if (it is String) JsonPrimitive(it) else JsonPrimitive(it as Int)) } }
        }
        putJsonObject("visits") {
            putJsonArray("items") {
                months.forEach { (name, plan, fact) ->
                    addJsonObject { put("monthName", name); put("maxValue", plan); put("currentValue", fact) }
                }
            }
        }
        put("avg_brs", avg)
        put("rating", JsonNull)
    }

    @Test fun subjectNames() {
        assertEquals("Иностранный язык", Progress.subjectName("Иностранный язык_ОАФ_1 курс_B1-B2 [1 семестр] [] []"))
        assertEquals("Основы российской государственности",
            Progress.subjectName("Основы российской государственности_ОАФ_1 курс [1 семестр] [] []"))
        assertEquals("Высшая математика", Progress.subjectName("Высшая математика [1 семестр] [Иванова И.И.] [гр.1, гр.2]"))
    }

    @Test fun parse() {
        // Числа приходят и строками.
        val p = Progress.parse(raw(listOf(16, "17", 0)), day)
        assertEquals(listOf("Информатика", "Иностранный язык", "Элективные дисциплины по физической культуре и спорту"),
            p.subjects.map { it.name })
        assertEquals(17, p.pointsFor("иностранный язык"))
        assertEquals(11, p.averagePoints)
        assertNull(p.averageBrs)
        assertEquals("Октябрь", p.currentMonth?.name)
        assertEquals(74, percent(p.currentMonth!!))
    }

    /** Метка без названия или без числа пропускается. */
    @Test fun skipsBrokenLabels() {
        val p = Progress.parse(buildJsonObject {
            putJsonObject("brs") {
                putJsonArray("labels") { add(JsonPrimitive(" [1 семестр] [] []")); add(JsonPrimitive("Информатика [1 семестр]")) }
                putJsonArray("data") { add(JsonPrimitive(10)); add(JsonNull) }
            }
        }, day)
        assertTrue(p.subjects.isEmpty())
    }

    @Test fun averageFromLk() {
        assertEquals("78,5", Progress.parse(raw(listOf(80), avg = JsonPrimitive("78,5")), day).averageBrs)
        assertNull(Progress.parse(raw(listOf(80), avg = JsonPrimitive("  ")), day).averageBrs)
    }

    @Test fun changesForNotification() {
        val old = Progress.parse(raw(listOf(16, 17)), day)
        val new = Progress.parse(raw(listOf(24, 17)), day)
        assertEquals(listOf("Информатика: 16 → 24 балла"), Progress.changes(old, new))
        val one = Progress.parse(raw(listOf(21)), day)
        assertEquals(listOf("Информатика: 16 → 21 балл"), Progress.changes(old, one))
    }

    /** Пустой ответ ЛК не стирает сохранённые баллы и не даёт изменений. */
    @Test fun emptyResponseKeepsCache() {
        d.saveProgress(raw(listOf(16)), day)
        val empty = buildJsonObject {
            putJsonObject("brs") { putJsonArray("labels") {}; putJsonArray("data") {} }
            putJsonObject("visits") { putJsonArray("items") {} }
        }
        assertEquals(emptyList<String>(), d.saveProgress(empty, day))
        assertEquals(16, d.progress()?.pointsFor("Информатика"))
    }

    @Test fun weekDelta() {
        d.saveProgress(raw(listOf(10)), day.addingDays(-8))
        d.saveProgress(raw(listOf(12)), day.addingDays(-3))
        d.saveProgress(raw(listOf(14)), day.minusSeconds(3600))
        // Повторная загрузка в тот же день перезаписывает запись дня.
        d.saveProgress(raw(listOf(16)), day)
        assertEquals(6, ProgressHistory.weekDelta("Информатика", day, d))
        assertNull(ProgressHistory.weekDelta("Иностранный язык", day, d))
    }

    /** Демо: баллы по всем предметам, посещаемость по месяцам семестра. */
    @Test fun demoProgress() {
        val p = Progress.parse(Demo.progress(day), day)
        assertEquals(8, p.subjects.size)
        assertEquals(28, p.pointsFor("Иностранный язык (английский)"))
        assertEquals("Октябрь", p.currentMonth?.name)
        val next = Progress.parse(Demo.progress(day, revision = 1), day)
        assertEquals(listOf("Математика: 22 → 26 баллов"), Progress.changes(p, next))
    }
}
