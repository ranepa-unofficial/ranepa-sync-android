package com.qteb.ranepa.sync

import com.qteb.ranepa.sync.lk.LkError
import com.qteb.ranepa.sync.system.Analytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

/** Значения параметров общие с iOS: дашборды TelemetryDeck строятся по ним. */
class AnalyticsTest {
    @Test fun dailyOncePerDay() {
        assertTrue(Analytics.dailyDue(null, "2026-10-26"))
        assertFalse(Analytics.dailyDue("2026-10-26", "2026-10-26"))
        assertTrue(Analytics.dailyDue("2026-10-26", "2026-10-27"))
    }

    @Test fun failureReasons() {
        assertEquals("session", Analytics.reason(LkError.NeedLogin()))
        assertEquals("server", Analytics.reason(LkError.ServerBusy(503, null)))
        assertEquals("network", Analytics.reason(LkError.Network("Нет связи с ЛК")))
        assertEquals("network", Analytics.reason(UnknownHostException()))
        assertEquals("other", Analytics.reason(LkError.Message("Сайт ответил непонятно")))
        assertEquals("other", Analytics.reason(IllegalStateException()))
    }

    @Test fun settings() {
        assertEquals(
            mapOf("reminders" to "15", "notifications" to "true", "calendar" to "false",
                "liveUpdate" to "true", "widgets" to "false", "theme" to "auto"),
            Analytics.settingsParams(15, notifications = true, calendar = false, liveUpdate = true, widgets = false, themeMode = 0),
        )
        val off = Analytics.settingsParams(0, notifications = false, calendar = true, liveUpdate = false, widgets = true, themeMode = 2)
        assertEquals("off", off["reminders"])
        assertEquals("dark", off["theme"])
        assertEquals("light", Analytics.settingsParams(5, true, true, true, true, 1)["theme"])
    }
}
