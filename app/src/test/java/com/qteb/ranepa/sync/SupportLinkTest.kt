package com.qteb.ranepa.sync

import com.qteb.ranepa.sync.system.SupportLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Формат параметра start общий с iOS: бот разбирает его по строгим правилам. */
class SupportLinkTest {
    private val allowed = Regex("^[a-z0-9_-]{1,64}$")

    private fun start(versionName: String = "1.0.0", versionCode: Long = 1, os: String = "16", model: String = "Pixel 9") =
        SupportLink.startParameter(versionName, versionCode, os, model)

    @Test fun example() {
        assertEquals("app-1-0-0-1_android-16_pixel-9", start())
    }

    @Test fun dotsCaseAndSymbols() {
        assertEquals("app-1-0-0-1_android-8-1-0_sm-s921b", start(os = "8.1.0", model = "SM-S921B"))
        assertEquals("app-1-0-0-1_android-16_pixel-9-pro-xl", start(model = "Pixel 9 Pro XL"))
        assertEquals("app-1-0-0-1_android-16_sdk-gphone64-arm64-emulator", start(model = "sdk_gphone64_arm64-emulator"))
        // Кириллица и другие не-ASCII символы — разделители, повторы схлопываются.
        assertEquals("app-1-0-0-1_android-16_redmi-12", start(model = "  Редми «Redmi» №12!  "))
    }

    /** Длинная модель укорачивается, начало app-…_android-… остаётся целым. */
    @Test fun onlyModelIsShortened() {
        val result = start(versionName = "12.34.56", versionCode = 789, os = "16.1.2",
            model = "Very Long Manufacturer Model Name With Many Words And Numbers 1234567890")
        assertTrue(result.length <= SupportLink.MAX_LENGTH)
        assertTrue(result, result.startsWith("app-12-34-56-789_android-16-1-2_very-long"))
        assertFalse(result.endsWith("-"))
        assertTrue(result, allowed.matches(result))
    }

    /** Обрезка посреди слова не оставляет дефис на конце. */
    @Test fun noTrailingDash() {
        val head = "app-1-0-0-1_android-16_"
        val model = "a".repeat(SupportLink.MAX_LENGTH - head.length - 1) + " bbb"
        val result = start(model = model)
        assertEquals(SupportLink.MAX_LENGTH - 1, result.length)
        assertFalse(result.endsWith("-"))
    }

    /** Места на модель нет — без третьей части и без «_». */
    @Test fun noRoomForModel() {
        val versionName = "1." + "2".repeat(36)
        val result = start(versionName = versionName, versionCode = 1234567, os = "16.1")
        assertEquals("app-1-${"2".repeat(36)}-1234567_android-16-1", result)
        assertFalse(result.endsWith("_"))
        assertTrue(allowed.matches(result))
    }

    @Test fun emptyModel() {
        assertEquals("app-1-0-0-1_android-16", start(model = "Модель"))
    }
}
