package com.qteb.ranepa.sync.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import java.time.Instant

/**
 * Будильники для напоминаний и Live Update. Точные — если система разрешила
 * (Android 14+ спрашивает пользователя), иначе неточные: в режиме сна
 * Android может сдвинуть их на несколько минут.
 */
object Alarms {
    fun canBeExact(context: Context): Boolean {
        val manager = context.getSystemService<AlarmManager>() ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
    }

    fun set(context: Context, at: Instant, operation: PendingIntent) {
        val manager = context.getSystemService<AlarmManager>() ?: return
        val millis = at.toEpochMilli()
        if (canBeExact(context)) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
        }
    }

    fun cancel(context: Context, operation: PendingIntent) {
        context.getSystemService<AlarmManager>()?.cancel(operation)
        operation.cancel()
    }

    /** Намерение для [AlarmReceiver]: [code] отличает будильники друг от друга. */
    fun broadcast(context: Context, action: String, code: Int, extras: Intent.() -> Unit = {}): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(action).apply(extras)
        return PendingIntent.getBroadcast(
            context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Уже поставленный будильник или null — чтобы снять его. */
    fun existing(context: Context, action: String, code: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
    }
}
