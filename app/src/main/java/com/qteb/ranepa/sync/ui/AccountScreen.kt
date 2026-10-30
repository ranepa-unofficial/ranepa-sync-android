package com.qteb.ranepa.sync.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.AccessAlarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qteb.ranepa.sync.AppState
import com.qteb.ranepa.sync.BuildConfig
import com.qteb.ranepa.sync.R
import com.qteb.ranepa.sync.RanepaApplication
import com.qteb.ranepa.sync.core.Clock
import com.qteb.ranepa.sync.core.Store
import com.qteb.ranepa.sync.core.hm
import com.qteb.ranepa.sync.core.sameDay
import com.qteb.ranepa.sync.core.shortDate
import com.qteb.ranepa.sync.sync.Background
import com.qteb.ranepa.sync.system.Alarms
import com.qteb.ranepa.sync.system.CalendarExport
import com.qteb.ranepa.sync.system.MapApp
import com.qteb.ranepa.sync.system.Notifications
import com.qteb.ranepa.sync.system.Analytics
import com.qteb.ranepa.sync.system.SupportLink
import com.qteb.ranepa.sync.system.UpdateRules
import com.qteb.ranepa.sync.system.Updates
import com.qteb.ranepa.sync.core.Keys
import kotlinx.coroutines.launch

private val reminderOptions = listOf(0, 5, 10, 15, 30, 60)
private val themes = listOf("Как в системе", "Светлое", "Тёмное")

/** Значок строки настроек: цветной круг, как в прототипе. */
@Composable
private fun SettingIcon(icon: ImageVector, tint: Color, ink: Color) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SettingRow(
    icon: @Composable () -> Unit, title: String, value: String? = null,
    trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        icon()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

/** Выбор одного варианта в окне. */
@Composable
private fun <T> ChoiceDialog(title: String, options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .selectable(option == selected, role = Role.RadioButton) { onPick(option); onDismiss() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(label(option), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Аккаунт и настройки одним экраном, как в прототипе. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(state: AppState, onBack: () -> Unit, onAbout: () -> Unit) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        state.checkNotifications()
        if (!it) openAppNotificationSettings(context)
    }
    val calendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) state.changeCalendarExport(true) else state.error = "Нет доступа к календарю: разреши его в настройках телефона"
    }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    val notificationsOff = state.notificationsEnabled == false
    val mapApps = MapApp.installedApps(context)

    Scaffold(modifier = Modifier.nestedScroll(scroll.nestedScrollConnection), topBar = { BackBar("Аккаунт", onBack, scroll) }) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(state, 88.dp)
                    Text(state.fullName ?: "Студент", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
                    listOfNotNull(state.group, state.program).filter { it.isNotEmpty() }.forEach {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                            modifier = Modifier.copyable(it).padding(top = 2.dp))
                    }
                }
            }

            state.updateOffer?.let { offer ->
                item(key = "update-seen") { androidx.compose.runtime.LaunchedEffect(offer.key) { state.markUpdateSeen() } }
                section(null, listOf(offer)) {
                    val activity = androidx.activity.compose.LocalActivity.current
                    UpdateRow(it) { activity?.let { a -> Updates.start(a, it.channel, required = false) } }
                }
                item { Box(Modifier.height(16.dp)) }
            }

            val rows = buildList<@Composable () -> Unit> {
                if (notificationsOff) {
                    add {
                        SettingRow({ SettingIcon(Icons.Outlined.Notifications, Color(0xFFFFDAD6), Color(0xFF93000A)) },
                            "Уведомления выключены", "Нажми, чтобы разрешить",
                            trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }) {
                            if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else openAppNotificationSettings(context)
                        }
                    }
                } else {
                    add {
                        SettingRow({ SettingIcon(Icons.Outlined.Notifications, Color(0xFFFFDAD6), Color(0xFF93000A)) }, "Напоминание",
                            if (state.reminderMinutes == 0) "Выключено" else "За ${state.reminderMinutes} мин до пары") { dialog = "reminder" }
                    }
                }
                if (state.reminderMinutes > 0 && !notificationsOff && !Alarms.canBeExact(context) && Build.VERSION.SDK_INT >= 31) {
                    add {
                        SettingRow({ SettingIcon(Icons.Outlined.AccessAlarm, Color(0xFFFFDAD6), Color(0xFF93000A)) },
                            "Точное время напоминаний", "Без разрешения Android может сдвинуть их на несколько минут",
                            trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }) {
                            open(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                        }
                    }
                }
                add {
                    SettingRow({ SettingIcon(Icons.Outlined.CalendarMonth, Color(0xFFFFDCC2), Color(0xFF6E3900)) }, "Добавлять в календарь",
                        trailing = {
                            Switch(checked = state.calendarExport, onCheckedChange = { on ->
                                if (on && !CalendarExport.allowed(context)) calendar.launch(CalendarExport.permissions)
                                else state.changeCalendarExport(on)
                            })
                        })
                }
                add {
                    SettingRow({ SettingIcon(Icons.Outlined.Timer, Color(0xFFD7F0DC), Color(0xFF0E4D22)) }, "Live Update",
                        trailing = { Switch(checked = state.liveUpdate, onCheckedChange = state::changeLiveUpdate) })
                }
                if (mapApps.size > 1) {
                    add {
                        SettingRow({ SettingIcon(Icons.Outlined.Map, Color(0xFFD2EFF5), Color(0xFF004F5A)) }, "Карты",
                            MapApp.preferred(context)?.title ?: "Спрашивать") { dialog = "maps" }
                    }
                }
                add {
                    SettingRow({ SettingIcon(Icons.Outlined.DarkMode, Color(0xFFE4DEFF), Color(0xFF3A2A86)) }, "Оформление",
                        themes[state.themeMode]) { dialog = "theme" }
                }
            }
            section(null, rows) { it() }
            item { SectionFooter(footer(state)) }

            if (BuildConfig.DEBUG && !Clock.screenshotMode) debugSection(state)

            item { Box(Modifier.height(16.dp)) }
            section(null, listOf(Unit)) {
                SettingRow({
                    Image(painterResource(R.drawable.logo), null, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)))
                }, "О приложении", trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }, onClick = onAbout)
            }
            item {
                OutlinedButton(
                    onClick = { dialog = "logout" },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).height(48.dp),
                ) { Text(logoutTitle(state), color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    when (dialog) {
        "reminder" -> ChoiceDialog("Напоминание", reminderOptions, state.reminderMinutes,
            { if (it == 0) "Выключено" else "За $it мин" }, state::changeReminderMinutes) { dialog = null }
        "theme" -> ChoiceDialog("Оформление", listOf(0, 1, 2), state.themeMode, { themes[it] }, state::changeThemeMode) { dialog = null }
        "maps" -> ChoiceDialog("Открывать корпус в", listOf<MapApp?>(null) + mapApps, MapApp.preferred(context),
            { it?.title ?: "Спрашивать" }, { MapApp.setPreferred(context, it); state.mapsChanged() }) { dialog = null }
        "logout" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(logoutTitle(state)) },
            text = {
                Text(if (state.demo) "Демо-расписание, напоминания и календарь «${CalendarExport.NAME}» будут удалены."
                    else "Расписание, напоминания и календарь «${CalendarExport.NAME}» будут удалены с этого телефона.")
            },
            confirmButton = {
                TextButton(onClick = { dialog = null; onBack(); state.logout() }) {
                    Text(logoutTitle(state), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Отмена") } },
        )
    }
}

private fun logoutTitle(state: AppState) = if (state.demo && !Clock.screenshotMode) "Выйти из демо" else "Выйти из ЛК"

private fun footer(state: AppState): String {
    val live = "Live Update — текущая пара в строке состояния и на экране блокировки."
    if (state.demo && !Clock.screenshotMode) {
        return "$live Демо-режим: профиль и расписание выдуманы, приложение не обращается к ЛК. " +
            "«Выйти из демо» удалит и календарь «${CalendarExport.NAME}»."
    }
    val parts = mutableListOf(live)
    if (state.notificationsEnabled == false) parts += "Уведомления выключены — об изменениях в расписании ты не узнаешь."
    if (state.calendarExport) parts += "Пары добавляются в отдельный календарь «${CalendarExport.NAME}»."
    parts += "В будни днём расписание обновляется в фоне примерно раз в час, ночью и в выходные — реже."
    parts += state.lastBackgroundRun?.let {
        "Последнее фоновое обновление — ${if (it.sameDay(Clock.now)) "сегодня" else shortDate(it)} в ${hm(it)}."
    } ?: "В фоне ещё не обновлялось."
    return parts.joinToString(" ")
}

fun openAppNotificationSettings(context: android.content.Context) {
    open(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
}

/** Отладка: только в отладочной сборке. */
private fun androidx.compose.foundation.lazy.LazyListScope.debugSection(state: AppState) {
    data class Action(val title: String, val subtitle: String, val run: (android.content.Context) -> Unit)
    val actions = listOf(
        Action("Ускоренный день", "Демо: 4 пары по 4 мин, первая через 3 мин") { state.startFastDay() },
        Action("Напоминание о паре", "Через 5 секунд — заблокируй телефон") { context ->
            val now = Clock.now
            (state.lessons.firstOrNull { !it.cancelled && it.start > now } ?: state.lessons.lastOrNull())?.let {
                Notifications.showTest(context, it, state.reminderMinutes.coerceIn(1, 60))
            }
        },
        Action("Уведомление об изменении", "Показать сразу, как после обновления") { context ->
            Notifications.showChanges(context, listOf("Математика, ср 14.10: перенесли с 12:20 на 14:00"))
        },
        Action("Обновить Live Update", "Показать по сегодняшним парам") { state.showLiveUpdate() },
        Action("Фоновое обновление", "Как по расписанию системы, без проверок") { context ->
            kotlinx.coroutines.MainScope().launch {
                Background.run(context, debug = true)
                state.loadCache()
            }
        },
        Action("Сымитировать изменение", "Сдвигает ближайшую пару на час раньше") { context ->
            Background.debugShiftNextLesson(context)
            state.loadCache()
        },
        Action("Канал обновлений: ${debugChannel(state)}", "Как будто установлено из другого места; проверка без паузы") { context ->
            val d = Store(context)
            val channels = listOf<UpdateRules.Channel?>(null) + UpdateRules.Channel.entries
            val next = channels[(channels.indexOf(UpdateRules.Channel.parse(d.string(Keys.UPDATE_DEBUG_CHANNEL))) + 1) % channels.size]
            d.setString(Keys.UPDATE_DEBUG_CHANNEL, next?.name)
            state.checkUpdates()
        },
        Action("Тестовый манифест: ${debugManifestTitle(state)}", "update.json с новой или обязательной версией") { context ->
            val d = Store(context)
            val next = when (d.string(Keys.UPDATE_DEBUG_MANIFEST)) {
                null -> DEBUG_MANIFEST_NEW
                DEBUG_MANIFEST_NEW -> DEBUG_MANIFEST_REQUIRED
                else -> null
            }
            d.setString(Keys.UPDATE_DEBUG_MANIFEST, next)
            d.remove(Keys.UPDATE_SHEET_VERSION, Keys.UPDATE_SHEET_SHOWN_AT, Keys.UPDATE_SEEN)
            state.checkUpdates()
        },
        Action("Часы 13.10, 11:05", "Как на скриншотах; ещё раз — настоящие часы") { context ->
            val d = Store(context)
            val value = if (d.string(RanepaApplication.DEMO_AT_KEY) == null) "2026-10-13T11:05" else null
            d.setString(RanepaApplication.DEMO_AT_KEY, value)
            Clock.setDemoAt(value)
            state.loadCache()
        },
    )
    section("Отладка", actions, footer = "Последний результат: ${state.lastBackgroundResult ?: "—"}") { a ->
        val context = LocalContext.current
        Column(Modifier.fillMaxWidth().clickable { a.run(context) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(a.title, style = MaterialTheme.typography.bodyLarge)
            Text(a.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val DEBUG_MANIFEST_NEW = """{"github": {"latest": "9.9.0", "latestCode": 999, "notes": "Тестовая версия из отладки.",
    "url": "${Updates.LATEST_APK}"}, "minimum": {"version": "1.0.0", "code": 0}}"""
private const val DEBUG_MANIFEST_REQUIRED = """{"github": {"latest": "9.9.0", "latestCode": 999, "url": "${Updates.LATEST_APK}"},
    "minimum": {"version": "9.9.0", "code": 999}}"""

private fun debugChannel(state: AppState) = state.updateChannel.storeName

private fun debugManifestTitle(state: AppState) = when (state.debugUpdateManifest) {
    null -> "настоящий"
    DEBUG_MANIFEST_NEW -> "новая версия"
    else -> "обязательная"
}

object Links {
    const val REPO = "https://github.com/ranepa-unofficial/ranepa-sync-android"
    const val PRIVACY_RAW = "https://raw.githubusercontent.com/ranepa-unofficial/ranepa-sync-android/main/PRIVACY.md"
    const val PRIVACY_CDN = "https://cdn.jsdelivr.net/gh/ranepa-unofficial/ranepa-sync-android@main/PRIVACY.md"
}

/** Версия, справка, конфиденциальность и связь с разработчиком. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onLegend: () -> Unit, onPrivacy: () -> Unit) {
    val context = LocalContext.current
    val version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    Scaffold(topBar = { BackBar("О приложении", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.logo), null, Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)))
                    Text("Расписание студента", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                    Text("Версия $version", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            section(null, listOf(
                Triple(Icons.Outlined.ViewList, "Обозначения", onLegend),
                Triple(Icons.Outlined.Lock, "Конфиденциальность", onPrivacy),
            )) { (icon, title, action) ->
                SettingRow({ SettingIcon(icon, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) },
                    title, trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }, onClick = action)
            }
            item { Box(Modifier.height(16.dp)) }
            section(null, listOf("telegram", "github")) { kind ->
                if (kind == "telegram") {
                    // Бот поддержки: откроется Telegram, а без него — браузер.
                    SettingRow({ Image(painterResource(R.drawable.ic_telegram), null, Modifier.size(40.dp)) }, "Поддержка в Telegram",
                        trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(20.dp)) }) {
                        Analytics.send(context, "Support.opened")
                        open(context, Intent(Intent.ACTION_VIEW, SupportLink.uri(context)))
                    }
                } else {
                    SettingRow({
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF1F2328)), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_github), null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }, "Исходный код на GitHub", trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(20.dp)) }) {
                        open(context, Intent(Intent.ACTION_VIEW, Uri.parse(Links.REPO)))
                    }
                }
            }
            item {
                Text("Неофициальное приложение. Не связано с РАНХиГС.\nКарта: © участники OpenStreetMap.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
            }
        }
    }
}

/** Справка: что значат цвета и как управлять приложением жестами. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegendScreen(onBack: () -> Unit) {
    val kinds = listOf(
        Triple("лекция", "Лекция", null), Triple("семинар", "Семинар", "Практические занятия"),
        Triple("физкультура", "Физкультура", "Узнаётся по названию предмета"), Triple("лабораторная", "Лабораторная", null),
        Triple("консультация", "Консультация", null), Triple("экзамен", "Экзамен или зачёт", null), Triple("", "Другое", null),
    )
    val gestures = listOf(
        "Потяни вниз" to "Обновить расписание",
        "Листай влево и вправо" to "Другой день",
        "Листай полосу с датами" to "Другая неделя",
        "Нажми на пару" to "Аудитория, преподаватель и почта",
        "Удерживай строку" to "Скопировать текст",
        "Нажми на своё фото" to "Настройки и выход из ЛК",
    )
    Scaffold(topBar = { BackBar("Обозначения", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
            section("Типы пар", kinds, footer = "Цвет полоски слева от пары совпадает с цветом типа в подписи. " +
                "Те же цвета — в виджетах.") { (kind, title, subtitle) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(com.qteb.ranepa.sync.ui.theme.kindStripe(kind)),
                        contentAlignment = Alignment.Center) {
                        Icon(kindIcon(kind), null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Text(title, style = MaterialTheme.typography.bodyLarge)
                        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            section("В ленте", listOf("Бледная пара" to "Уже прошла", "Зачёркнутая пара" to "Отменена")) { (title, value) ->
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(title, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value)
                }
            }
            section("Жесты", gestures) { (title, subtitle) ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
