package com.qteb.ranepa.sync

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import com.qteb.ranepa.sync.system.Notifications
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Изменения в расписании, найденные в открытом приложении, тоже ложатся
 * в шторку: раньше их видел только баннер внутри приложения.
 */
// SDK 36 в Robolectric не запускается на JDK 21 (ApplicationSharedMemory).
@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class ChangesNotificationTest {
    private lateinit var app: Application
    private val manager get() = app.getSystemService<NotificationManager>()!!

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifications.createChannels(app)
    }

    @Test fun summaryShowsThreeLines() {
        assertEquals("a\nb", Notifications.summary(listOf("a", "b")))
        assertEquals("a\nb\nc\nи ещё 2", Notifications.summary(listOf("a", "b", "c", "d", "e")))
    }

    /** На экране — тихо, без всплывающего окна; новое уведомление заменяет прошлое. */
    @Test fun foregroundChangesAreSilentAndReplaced() {
        Notifications.showChanges(app, listOf("Математика, ср 14.10: перенесли с 12:20 на 14:00"), silent = true)
        Notifications.showChanges(app, listOf("Правоведение, чт 15.10: пару отменили", "Математика, пт 16.10: аудитория: 1 - 1112"),
            silent = true)
        val shown = shadowOf(manager).allNotifications
        assertEquals(1, shown.size)
        val n = shown.single()
        assertEquals("Изменения в расписании", n.extras.getString("android.title"))
        assertEquals(Notifications.CHANNEL_CHANGES, n.channelId)
    }

    @Test fun gradesHaveOwnChannel() {
        Notifications.showGrades(app, listOf("Информатика: 16 → 24 балла"))
        val n = shadowOf(manager).allNotifications.single()
        assertEquals("Новые баллы", n.extras.getString("android.title"))
        assertEquals(Notifications.CHANNEL_GRADES, n.channelId)
    }
}
