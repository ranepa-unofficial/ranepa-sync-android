package com.qteb.ranepa.sync.system

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.qteb.ranepa.sync.MainActivity
import com.qteb.ranepa.sync.core.Clock
import com.qteb.ranepa.sync.core.Store
import com.qteb.ranepa.sync.core.hm
import com.qteb.ranepa.sync.widget.WidgetState

/** Плитка «Расписание» в быстрых настройках: следующая пара, нажатие открывает сегодня. */
class TodayTile : TileService() {
    override fun onStartListening() {
        val tile = qsTile ?: return
        val state = WidgetState(Store(this).cachedLessons(), Clock.now)
        val lead = state.lead
        tile.state = Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                lead == null -> "Пар нет"
                state.isOngoing(lead) -> "${lead.shortLocation} · до ${hm(lead.end)}"
                else -> "${state.dayName(lead.start)}, ${hm(lead.start)}"
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_TODAY, true)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        const val EXTRA_TODAY = "today"
    }
}
