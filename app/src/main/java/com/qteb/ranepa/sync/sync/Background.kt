package com.qteb.ranepa.sync.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.qteb.ranepa.sync.core.Clock
import com.qteb.ranepa.sync.core.Keys
import com.qteb.ranepa.sync.core.Store
import com.qteb.ranepa.sync.core.moscowDate
import com.qteb.ranepa.sync.core.moscowStamp
import com.qteb.ranepa.sync.core.str
import com.qteb.ranepa.sync.lk.LkError
import com.qteb.ranepa.sync.system.LiveUpdates
import com.qteb.ranepa.sync.system.Notifications
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Фоновое обновление: в будни днём — раз в час, Пн–Сб не реже раза в 4 часа,
 * в воскресенье — раз в 12 (`Sync.interval`). WorkManager запускает задачу
 * не раньше заданного времени и только с интернетом. Каждое обновление
 * продлевает сессию ЛК.
 */
object Background {
    private const val WORK = "refresh"

    /**
     * Ставит следующий запуск. Из приложения — [ExistingWorkPolicy.KEEP]: уже
     * запланированный не трогаем. После входа — REPLACE. Из самой задачи —
     * APPEND_OR_REPLACE: REPLACE остановил бы её посреди обновления.
     */
    fun schedule(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
        val now = Instant.now()
        val delay = Duration.between(now, Sync.nextRun(now)).coerceAtLeast(Duration.ofMinutes(1))
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInitialDelay(delay.seconds, TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, policy, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    /** Запуск системой. Итог пишется в настройки — его видно в разделе «Отладка». */
    suspend fun run(context: Context, debug: Boolean = false) {
        val d = Store(context)
        if (!d.loggedIn || d.demo) return
        val outcome = try {
            val result = Sync.run(context, if (debug) SyncTrigger.DEBUG else SyncTrigger.BACKGROUND)
            Notifications.showChanges(context, result.changes)
            when (result.skipped) {
                null -> if (result.changes.isEmpty()) "без изменений" else "изменений: ${result.changes.size}"
                SkipReason.BACKOFF -> "пауза после ошибки ЛК"
                SkipReason.TOO_SOON -> "рано, пропущено"
                SkipReason.NOT_NEEDED -> "нет пар, пропущено"
                SkipReason.NEED_LOGIN -> "нужно войти в ЛК"
            }
        } catch (e: LkError.NeedLogin) {
            // Остаёмся в приложении с сохранённым расписанием, на главном
            // экране — просьба войти заново.
            d.setBool(Keys.SESSION_EXPIRED, true)
            Notifications.cancelKeepAlive(context)
            if (!d.bool(Keys.SESSION_EXPIRED_NOTIFIED)) {
                Notifications.showSessionExpired(context)
                d.setBool(Keys.SESSION_EXPIRED_NOTIFIED, true)
            }
            "сессия истекла"
        } catch (e: Exception) {
            "ошибка: ${e.message}"
        }
        // Live Update — по сохранённому расписанию, даже если ЛК не ответил.
        LiveUpdates.update(context, d.cachedLessons())
        d.setTime(Keys.LAST_BACKGROUND_RUN, Instant.now())
        d.setString(Keys.LAST_BACKGROUND_RESULT, outcome)
    }

    /** Отладка: сдвигает в кэше ближайшую будущую пару на час раньше, чтобы
     * следующее обновление нашло «перенос» и прислало уведомление. */
    fun debugShiftNextLesson(context: Context) {
        val d = Store(context)
        val now = Clock.now
        val days = d.cachedDays().toMutableList()
        for ((i, day) in days.withIndex()) {
            val dayObject = day as? JsonObject ?: continue
            val discs = (dayObject["discs"] as? JsonArray)?.toMutableList() ?: continue
            for ((j, disc) in discs.withIndex()) {
                val discObject = disc as? JsonObject ?: continue
                val start = moscowDate(discObject["start_date"].str()) ?: continue
                val end = moscowDate(discObject["end_date"].str()) ?: continue
                if (start <= now) continue
                discs[j] = JsonObject(discObject + mapOf(
                    "start_date" to JsonPrimitive(moscowStamp(start.minusSeconds(3600))),
                    "end_date" to JsonPrimitive(moscowStamp(end.minusSeconds(3600))),
                ))
                days[i] = JsonObject(dayObject + ("discs" to JsonArray(discs)))
                d.saveDays(JsonArray(days))
                return
            }
        }
    }
}

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Следующий запуск — сразу: задача могла не успеть.
        if (Store(applicationContext).loggedIn) {
            Background.schedule(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        Background.run(applicationContext)
        return Result.success()
    }
}
