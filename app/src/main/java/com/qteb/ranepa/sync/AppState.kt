package com.qteb.ranepa.sync

import android.app.Application
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import com.qteb.ranepa.sync.core.Clock
import com.qteb.ranepa.sync.core.ExamSession
import com.qteb.ranepa.sync.core.Keys
import com.qteb.ranepa.sync.core.Lesson
import com.qteb.ranepa.sync.core.Progress
import com.qteb.ranepa.sync.core.StudyPlan
import com.qteb.ranepa.sync.core.progress
import com.qteb.ranepa.sync.core.Store
import com.qteb.ranepa.sync.core.daysSince
import com.qteb.ranepa.sync.core.monday
import com.qteb.ranepa.sync.core.sameDay
import com.qteb.ranepa.sync.lk.FetchResult
import com.qteb.ranepa.sync.lk.LkError
import com.qteb.ranepa.sync.lk.LkWebView
import com.qteb.ranepa.sync.sync.Background
import com.qteb.ranepa.sync.sync.Demo
import com.qteb.ranepa.sync.sync.FETCH_WEEKS
import com.qteb.ranepa.sync.sync.SkipReason
import com.qteb.ranepa.sync.sync.Sync
import com.qteb.ranepa.sync.sync.SyncTrigger
import com.qteb.ranepa.sync.system.Analytics
import com.qteb.ranepa.sync.system.AppVisibility
import com.qteb.ranepa.sync.system.CalendarExport
import com.qteb.ranepa.sync.system.LiveUpdates
import com.qteb.ranepa.sync.system.Notifications
import com.qteb.ranepa.sync.system.UpdateRules
import com.qteb.ranepa.sync.system.Updates
import com.qteb.ranepa.sync.ui.From
import com.qteb.ranepa.sync.widget.Widgets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

enum class RootTab { SCHEDULE, SUBJECTS, SESSION }

/** Почему не удалось войти — для окна на стартовом экране. */
data class LoginFailure(val title: String, val message: String) {
    companion object {
        fun of(error: Throwable): LoginFailure = when (error) {
            is LkError.ServerBusy -> LoginFailure("Личный кабинет недоступен", "Не удалось получить данные из ЛК. Попробуй войти позже.")
            is LoginTimeout -> LoginFailure("Личный кабинет не отвечает", "Попробуй войти позже.")
            is LoginBounced -> LoginFailure("Не удалось войти", "Попробуй ещё раз.")
            else -> LoginFailure("Не удалось войти", error.message ?: "Неизвестная ошибка")
        }
    }
}

class LoginTimeout : Exception()
class LoginBounced : Exception()

/** Состояние приложения: расписание из кэша, настройки и действия с ними. */
class AppState(application: Application) : AndroidViewModel(application) {
    private val context get() = getApplication<Application>()
    private val d = Store(application)

    var lessons by mutableStateOf<List<Lesson>>(emptyList()); private set
    var fetchedAt by mutableStateOf<Instant?>(null); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    /** Сессия ЛК истекла: расписание из кэша, на главном экране — карточка. */
    var sessionExpired by mutableStateOf(false); private set
    /** «Позже» на карточке: до следующего открытия приложения. */
    var sessionCardHidden by mutableStateOf(false)
    /** Вход не удался: стартовый экран показывает это окном. */
    var loginFailure by mutableStateOf<LoginFailure?>(null)
    /** Изменения, найденные последним ручным обновлением: главный экран показывает их баннером. */
    var pendingChanges by mutableStateOf<List<String>>(emptyList())
    /** Разрешены ли уведомления; null — ещё не знаем. */
    var notificationsEnabled by mutableStateOf<Boolean?>(null); private set
    /** Нажали на напоминание, Live Update или виджет: главный экран листает к дню пары и открывает её карточку. */
    var pendingLesson by mutableStateOf<Instant?>(null)
    /** Плитка «Расписание» в быстрых настройках: главный экран листает к сегодня. */
    var pendingToday by mutableStateOf(false)
    /** Открытая карточка пары — общая для всех экранов. */
    var sheetLesson by mutableStateOf<Lesson?>(null)
    /** С какого экрана открыта карточка: ссылку на него же в ней не показываем. */
    var sheetFrom by mutableStateOf<From?>(null); private set

    fun openLesson(lesson: Lesson, from: From? = null) {
        sheetFrom = from
        sheetLesson = lesson
    }

    var loggedIn by mutableStateOf(false); private set
    var demo by mutableStateOf(false); private set
    var reminderMinutes by mutableStateOf(15); private set
    var calendarExport by mutableStateOf(false); private set
    var liveUpdate by mutableStateOf(true); private set
    var themeMode by mutableStateOf(0); private set
    var fullName by mutableStateOf<String?>(null); private set
    var group by mutableStateOf<String?>(null); private set
    var program by mutableStateOf<String?>(null); private set
    var photo by mutableStateOf<ImageBitmap?>(null); private set
    /** План обучения из ЛК; null — ещё не загружался. */
    var plan by mutableStateOf<StudyPlan?>(null); private set
    /** Баллы БРС и посещаемость из ЛК; null — ещё не загружались. */
    var progress by mutableStateOf<Progress?>(null); private set
    var gradeNotifications by mutableStateOf(false); private set
    var tab by mutableStateOf(RootTab.SCHEDULE)
    /** Эта версия больше не работает — экран обновления поверх всего. */
    var updateRequired by mutableStateOf(false); private set
    /** Новая версия: строка в аккаунте; [updateUnseen] — точка на аватаре. */
    var updateOffer by mutableStateOf<Updates.Offer?>(null); private set
    var updateUnseen by mutableStateOf(false); private set
    /** Шторка «Вышла версия»: показывается один раз, после «Позже» — через 3 дня. */
    var updateSheet by mutableStateOf<Updates.Offer?>(null); private set
    val updateChannel: UpdateRules.Channel get() = Updates.channel(context)
    val debugUpdateManifest: String? get() = d.string(Keys.UPDATE_DEBUG_MANIFEST)
    /** Счётчик минут: экраны перерисовывают «идёт сейчас» и «ещё N мин». */
    var minute by mutableStateOf(Clock.now); private set

    val lastBackgroundRun: Instant? get() = d.time(Keys.LAST_BACKGROUND_RUN)
    val lastBackgroundResult: String? get() = d.string(Keys.LAST_BACKGROUND_RESULT)

    /** Сессия текущего семестра: план + события расписания. */
    val session: ExamSession? get() = ExamSession.of(lessons, plan, Clock.now)

    /** Первый день загруженного диапазона — понедельник текущей недели. */
    val firstDay: Instant get() = Clock.now.monday

    /** Дней в ленте: до конца последней недели с парами, но не меньше месяца. */
    val dayCount: Int
        get() {
            val weeks = lessons.maxOfOrNull { it.start }?.let { it.monday.daysSince(firstDay) / 7 + 1 } ?: 0
            return maxOf(FETCH_WEEKS, weeks) * 7
        }

    init {
        loadCache()
        viewModelScope.launch {
            while (true) {
                delay(60_000 - (System.currentTimeMillis() % 60_000))
                minute = Clock.now
            }
        }
    }

    fun lessonsOn(day: Instant): List<Lesson> = lessons.filter { it.start.sameDay(day) }

    /** Ближайшая пара после [after] (не отменённая). */
    fun next(after: Instant): Lesson? = lessons.firstOrNull { !it.cancelled && it.start > after }

    fun loadCache() {
        lessons = d.cachedLessons()
        fetchedAt = d.time(Keys.FETCHED_AT)
        loggedIn = d.loggedIn
        sessionExpired = loggedIn && d.bool(Keys.SESSION_EXPIRED)
        demo = d.demo
        reminderMinutes = d.reminderMinutes
        calendarExport = d.bool(Keys.CALENDAR_EXPORT)
        liveUpdate = d.liveUpdate
        themeMode = d.int(Keys.THEME_MODE)
        fullName = d.string(Keys.FULL_NAME)
        group = d.string(Keys.GROUP)
        program = d.string(Keys.PROGRAM)
        plan = d.plan()
        progress = d.progress()
        gradeNotifications = d.bool(Keys.GRADE_NOTIFICATIONS)
        updateRequired = Updates.requiredFromCache(context)
        updateOffer = Updates.cachedOffer(context)
        updateUnseen = updateOffer?.let { it.key != d.string(Keys.UPDATE_SEEN) } ?: false
        photo = d.string(Keys.PHOTO)?.let { base64 ->
            runCatching {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
        if (!loggedIn && d.bool(Keys.SESSION_EXPIRED_NOTIFIED)) error = "Сессия ЛК истекла, войди заново"
        minute = Clock.now
    }

    /** Вызывается экраном входа, когда страница ЛК уже авторизована. [relogin] — вход после истёкшей сессии. */
    suspend fun completeLogin(result: FetchResult, monday: Instant, relogin: Boolean = false) {
        error = null
        val sync = Sync.save(context, result, monday)
        loadCache()
        if (demo) Analytics.stop() else if (!relogin) Analytics.send(context, "Login.completed")
        error = sync.sideEffectError
        if (!demo) Background.schedule(context, ExistingWorkPolicy.REPLACE)
        sessionCardHidden = false
    }

    /** Вход в демо долгим нажатием на логотип стартового экрана. */
    fun startDemo() = viewModelScope.launch {
        d.setBool(Keys.DEMO, true)
        completeLogin(Demo.fetch(d), Clock.now.monday)
    }

    /** Отладка: демо, где сегодня 4 пары по 4 минуты, первая — через 3 минуты. Напоминания — за 1 минуту. */
    fun startFastDay() = viewModelScope.launch {
        if (loggedIn) logoutNow()
        d.setTime(Keys.DEMO_FAST_FROM, Clock.now.plusSeconds(3 * 60))
        d.setInt(Keys.REMINDER_MINUTES, 1)
        d.setBool(Keys.DEMO, true)
        completeLogin(Demo.fetch(d), Clock.now.monday)
        showLiveUpdate()
    }

    /** Перечитывает разрешение на уведомления; включили — сразу ставим напоминания заново. */
    fun checkNotifications() {
        val allowed = Notifications.allowed(context)
        if (allowed == notificationsEnabled) return
        val wasOff = notificationsEnabled == false
        notificationsEnabled = allowed
        if (allowed && wasOff && loggedIn) resync()
    }

    /** OPEN — при открытии, если данные устарели; MANUAL — жестом «потянуть вниз». */
    fun refresh(trigger: SyncTrigger = SyncTrigger.MANUAL) = viewModelScope.launch {
        if (loading || !loggedIn) return@launch
        loading = true
        error = null
        try {
            val sync = Sync.run(context, trigger)
            // Данные только что обновлялись — запрос не нужен, но жест должен
            // выглядеть как обычное обновление.
            if (sync.skipped == SkipReason.TOO_SOON) delay(500)
            pendingChanges = sync.changes
            // Системное уведомление — и из открытого приложения: его могли свернуть,
            // пока шло обновление. На экране баннер уже виден — тогда без звука.
            Notifications.showChanges(context, sync.changes, silent = AppVisibility.foreground)
            error = sync.sideEffectError
            if (sync.skipped == SkipReason.BACKOFF && trigger == SyncTrigger.MANUAL) {
                error = LkError.ServerBusy(503, null).message
            }
        } catch (e: LkError.NeedLogin) {
            Analytics.syncFailed(context, e, trigger)
            d.setBool(Keys.SESSION_EXPIRED, true)
            sessionCardHidden = false
            Notifications.cancelKeepAlive(context)
        } catch (e: Exception) {
            Analytics.syncFailed(context, e, trigger)
            error = e.message
        } finally {
            loadCache()
            loading = false
        }
    }

    /** Приложение снова на экране: фон и будильники могли поменять данные. */
    fun resumed() {
        sessionCardHidden = false
        loadCache()
        checkNotifications()
        Notifications.removeStale(context)
        showLiveUpdate()
        refresh(SyncTrigger.OPEN)
    }

    /** Новая версия: при выходе на экран, не чаще раза в 3 часа. */
    fun checkUpdates() {
        if (Clock.screenshotMode) return
        viewModelScope.launch {
            val result = Updates.check(context) ?: return@launch
            loadCache()
            val offer = result.offer ?: return@launch
            if (!result.required && UpdateRules.offerSheet(offer.key, d.string(Keys.UPDATE_SHEET_VERSION),
                    d.time(Keys.UPDATE_SHEET_SHOWN_AT), Clock.now)) updateSheet = offer
        }
    }

    /** Шторку закрыли — «Позже», «Обновить» или жестом: о той же версии снова через 3 дня. */
    fun dismissUpdateSheet() {
        updateSheet?.let {
            d.setString(Keys.UPDATE_SHEET_VERSION, it.key)
            d.setTime(Keys.UPDATE_SHEET_SHOWN_AT, Clock.now)
        }
        updateSheet = null
    }

    /** Отладка: настоящие канал и манифест вместо подменённых. */
    fun resetDebugUpdates() {
        d.remove(Keys.UPDATE_DEBUG_CHANNEL, Keys.UPDATE_DEBUG_MANIFEST, Keys.UPDATE_OFFER, Keys.UPDATE_CHECKED_AT)
        loadCache()
        checkUpdates()
    }

    /** Строку «Доступна версия» увидели в аккаунте — точка на аватаре гаснет. */
    fun markUpdateSeen() {
        val offer = updateOffer ?: return
        d.setString(Keys.UPDATE_SEEN, offer.key)
        updateUnseen = false
    }

    private fun resync() = viewModelScope.launch { error = Sync.applySideEffects(context, lessons) }

    fun changeReminderMinutes(minutes: Int) {
        d.setInt(Keys.REMINDER_MINUTES, minutes)
        reminderMinutes = minutes
        resync()
    }

    /** Уведомления о новых баллах. Разрешение на уведомления экран спрашивает сам при включении. */
    fun changeGradeNotifications(enabled: Boolean) {
        d.setBool(Keys.GRADE_NOTIFICATIONS, enabled)
        gradeNotifications = enabled
    }

    fun changeCalendarExport(enabled: Boolean) {
        d.setBool(Keys.CALENDAR_EXPORT, enabled)
        calendarExport = enabled
        if (enabled) resync() else viewModelScope.launch { CalendarExport.remove(context) }
    }

    fun changeLiveUpdate(enabled: Boolean) {
        d.setBool(Keys.LIVE_UPDATE, enabled)
        liveUpdate = enabled
        LiveUpdates.update(context, lessons, appOpen = true)
    }

    /** Открыто приложение: показать текущую пару, если она идёт или скоро. */
    fun showLiveUpdate() {
        LiveUpdates.update(context, lessons, appOpen = true)
        if (loggedIn && !demo) Background.schedule(context)
    }

    fun changeThemeMode(mode: Int) {
        d.setInt(Keys.THEME_MODE, mode)
        themeMode = mode
    }

    fun mapsChanged() = Widgets.update(context)

    fun logout() = viewModelScope.launch { logoutNow() }

    private suspend fun logoutNow() {
        // Сессию WebView чистим до перехода на экран входа, иначе страница ЛК
        // подхватит старые токены и снова «войдёт».
        LkWebView.logout()
        Background.cancel(context)
        Notifications.cancelAll(context)
        LiveUpdates.cancel(context)
        if (calendarExport) CalendarExport.remove(context)
        d.remove(*Keys.session.toTypedArray())
        Widgets.update(context)
        error = null
        pendingChanges = emptyList()
        sheetLesson = null
        tab = RootTab.SCHEDULE
        loadCache()
    }
}
