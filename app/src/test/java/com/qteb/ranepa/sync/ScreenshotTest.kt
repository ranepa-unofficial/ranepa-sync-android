package com.qteb.ranepa.sync

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.qteb.ranepa.sync.core.Clock
import com.qteb.ranepa.sync.core.Progress
import com.qteb.ranepa.sync.sync.Demo
import com.qteb.ranepa.sync.system.UpdateRules.Channel
import com.qteb.ranepa.sync.system.Updates
import com.qteb.ranepa.sync.ui.GradesCard
import com.qteb.ranepa.sync.ui.GradesContent
import com.qteb.ranepa.sync.ui.RequiredUpdateScreen
import com.qteb.ranepa.sync.ui.UpdateSheetContent
import com.qteb.ranepa.sync.ui.subjectGradesSection
import com.qteb.ranepa.sync.ui.theme.RanepaTheme
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/**
 * Скриншоты новых экранов в светлой и тёмной теме. Эталоны — в
 * `app/src/test/screenshots`: `gradle recordRoborazziDebug` перезаписывает их,
 * `gradle verifyRoborazziDebug` сравнивает.
 */
// SDK 36 в Robolectric не запускается на JDK 21 (ApplicationSharedMemory).
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class ScreenshotTest {
    private lateinit var progress: Progress

    /** Часы и пояс фиксированы: подпись «обновлено сегодня в 12:00» одна и та же везде. */
    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
        Clock.setDemoAt("2026-10-30T12:00")
        progress = Progress.parse(Demo.progress(Clock.now), Clock.now)
    }

    @After
    fun tearDown() = Clock.setDemoAt(null)

    private val offer = Updates.Offer(Channel.RUSTORE, 5, "1.1.0",
        "Баллы БРС и посещаемость на вкладке «Предметы», уведомление о новых баллах.")

    private fun shot(name: String, content: @Composable () -> Unit) {
        for ((mode, theme) in listOf(1 to "light", 2 to "dark")) {
            captureRoboImage("src/test/screenshots/${name}_$theme.png") {
                RanepaTheme(mode = mode, dynamic = false) {
                    Surface(color = MaterialTheme.colorScheme.surface) { content() }
                }
            }
        }
    }

    @Test fun gradesCard() = shot("grades_card") {
        Column(Modifier.padding(vertical = 16.dp)) { GradesCard(progress) {} }
    }

    @Test fun subjectGrades() = shot("subject_grades") {
        LazyColumn(contentPadding = PaddingValues(vertical = 16.dp)) {
            subjectGradesSection(progress.subjects.first().name, progress, weekDelta = 4)
        }
    }

    @Test fun gradesScreen() = shot("grades_screen") {
        GradesContent(progress, notifications = true, onNotifications = {}, contentPadding = PaddingValues(vertical = 16.dp))
    }

    @Test fun updateSheet() = shot("update_sheet") {
        Column(Modifier.padding(top = 24.dp)) { UpdateSheetContent(offer, "1.0.1", onUpdate = {}, onLater = {}) }
    }

    @Test fun updateRequired() = shot("update_required") {
        RequiredUpdateScreen(Channel.GITHUB, "1.0.1", onUpdate = {})
    }
}
