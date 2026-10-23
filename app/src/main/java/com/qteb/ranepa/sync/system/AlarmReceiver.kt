package com.qteb.ranepa.sync.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qteb.ranepa.sync.core.Store
import com.qteb.ranepa.sync.sync.Background
import com.qteb.ranepa.sync.sync.Sync
import com.qteb.ranepa.sync.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/** Будильники: напоминание, «открой приложение», граница пары для Live Update. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Notifications.ACTION_REMINDER -> Notifications.showReminder(
                context,
                channel = intent.getStringExtra(Notifications.EXTRA_CHANNEL) ?: Notifications.CHANNEL_LESSONS,
                title = intent.getStringExtra(Notifications.EXTRA_TITLE) ?: return,
                body = intent.getStringExtra(Notifications.EXTRA_BODY) ?: "",
                start = intent.getLongExtra(Notifications.EXTRA_LESSON, 0).takeIf { it > 0 }?.let(Instant::ofEpochSecond),
            )
            Notifications.ACTION_KEEP_ALIVE -> Notifications.showKeepAlive(context)
            Widgets.ACTION_TICK -> Widgets.update(context)
            LiveUpdates.ACTION -> {
                LiveUpdates.update(context, Store(context).cachedLessons(),
                    alert = intent.getBooleanExtra(LiveUpdates.EXTRA_ALERT, false))
                Widgets.update(context)
            }
        }
    }
}

/**
 * После перезагрузки, смены времени или обновления приложения будильники
 * пропадают — ставим их заново по сохранённому расписанию.
 */
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val d = Store(context)
        if (!d.loggedIn) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            try {
                Sync.applySideEffects(context, d.cachedLessons())
                if (!d.demo) Background.schedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
