package com.qteb.ranepa.sync

import com.qteb.ranepa.sync.system.UpdateRules
import com.qteb.ranepa.sync.system.UpdateRules.Channel
import com.qteb.ranepa.sync.system.UpdateRules.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Duration
import java.time.Instant

class UpdateRulesTest {
    @Test fun channelByInstaller() {
        assertEquals(Channel.PLAY, Channel.fromInstaller("com.android.vending"))
        assertEquals(Channel.RUSTORE, Channel.fromInstaller("ru.vk.store"))
        assertEquals(Channel.GITHUB, Channel.fromInstaller(null))
        assertEquals(Channel.GITHUB, Channel.fromInstaller("com.google.android.packageinstaller"))
        assertEquals(Channel.GITHUB, Channel.fromInstaller("com.android.shell"))
    }

    @Test fun versionOrder() {
        assertTrue(UpdateRules.compareVersions("1.0.1", "1.1.0") < 0)
        assertTrue(UpdateRules.compareVersions("1.9", "1.10") < 0)
        assertEquals(0, UpdateRules.compareVersions("1.1", "1.1.0"))
    }

    /** Манифест из корня репозитория должен разбираться. */
    @Test fun repoManifestParses() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "update.json").exists() }
        val m = UpdateRules.parseManifest(File(root, "update.json").readText())
        assertNotNull(m?.github)
        assertNotNull(m?.minimum)
    }

    @Test fun brokenManifestIsIgnored() {
        assertNull(UpdateRules.parseManifest("<html>404</html>"))
        assertNull(UpdateRules.parseManifest("""{"github": {"latest": "1.1.0"}}"""))
    }

    @Test fun githubStatus() {
        val m = UpdateRules.parseManifest("""
            {"github": {"latest": "1.1.0", "latestCode": 5, "notes": "Баллы БРС", "url": "https://example.org"},
             "minimum": {"version": "1.0.0", "code": 3}}
        """)!!
        assertEquals(Status.None, UpdateRules.githubStatus(m, versionCode = 5))
        assertEquals(Status.Available("1.1.0", "Баллы БРС"), UpdateRules.githubStatus(m, versionCode = 4))
        assertEquals(Status.Required, UpdateRules.githubStatus(m, versionCode = 2))
        // Минимум общий для всех каналов.
        assertTrue(UpdateRules.required(m, versionCode = 2))
        assertFalse(UpdateRules.required(m, versionCode = 3))
    }

    @Test fun downloadEveryThreeHours() {
        val now = Instant.parse("2026-10-30T09:00:00Z")
        assertTrue(UpdateRules.downloadDue(null, now))
        assertFalse(UpdateRules.downloadDue(now.minus(Duration.ofMinutes(170)), now))
        assertTrue(UpdateRules.downloadDue(now.minus(Duration.ofHours(3)), now))
        // Отметка из будущего — переводили часы.
        assertTrue(UpdateRules.downloadDue(now.plus(Duration.ofDays(1)), now))
    }

    /** «Позже» — на 3 дня; о более новой версии — сразу. */
    @Test fun laterHidesSheetForThreeDays() {
        val now = Instant.parse("2026-10-30T09:00:00Z")
        assertTrue(UpdateRules.offerSheet("1.1.0", null, null, now))
        assertFalse(UpdateRules.offerSheet("1.1.0", "1.1.0", now.minus(Duration.ofDays(2)), now))
        assertTrue(UpdateRules.offerSheet("1.1.0", "1.1.0", now.minus(Duration.ofDays(3)), now))
        assertTrue(UpdateRules.offerSheet("1.2.0", "1.1.0", now.minus(Duration.ofHours(1)), now))
    }
}
