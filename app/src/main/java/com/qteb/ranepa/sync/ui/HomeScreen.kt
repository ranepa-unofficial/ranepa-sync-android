package com.qteb.ranepa.sync.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.qteb.ranepa.sync.AppState
import com.qteb.ranepa.sync.RootTab
import com.qteb.ranepa.sync.core.Clock
import com.qteb.ranepa.sync.core.ExamSession
import com.qteb.ranepa.sync.core.Lesson
import com.qteb.ranepa.sync.core.addingDays
import com.qteb.ranepa.sync.core.capitalizedFirst
import com.qteb.ranepa.sync.core.day
import com.qteb.ranepa.sync.core.dayTitle
import com.qteb.ranepa.sync.core.daysSince
import com.qteb.ranepa.sync.core.daysUntil
import com.qteb.ranepa.sync.core.durationText
import com.qteb.ranepa.sync.core.hm
import com.qteb.ranepa.sync.core.isoWeekday
import com.qteb.ranepa.sync.core.month
import com.qteb.ranepa.sync.core.monthsNominative
import com.qteb.ranepa.sync.core.sameDay
import com.qteb.ranepa.sync.core.secondsSince
import com.qteb.ranepa.sync.core.weekdaysShort
import com.qteb.ranepa.sync.core.whenLabel
import com.qteb.ranepa.sync.core.year
import com.qteb.ranepa.sync.sync.SyncTrigger
import com.qteb.ranepa.sync.ui.theme.nowRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.abs

/** Ширина, с которой рядом с лентой показывается карточка пары (планшеты, складные). */
val wideWidth = 840.dp

/**
 * Главный экран: месяц, поиск и аватар в одну строку, под ними полоса недель,
 * ниже лента пар выбранного дня. На широком экране справа — выбранная пара.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: AppState, wide: Boolean, onSearch: () -> Unit, onAccount: () -> Unit, onRelogin: () -> Unit) {
    val first = state.firstDay
    val count = state.dayCount
    val todayIndex = Clock.now.daysSince(first).coerceIn(0, count - 1)
    val days = rememberPagerState(initialPage = todayIndex) { count }
    val weeks = rememberPagerState(initialPage = todayIndex / 7) { count / 7 }
    val scope = rememberCoroutineScope()
    var banner by remember { mutableStateOf<Pair<String, String>?>(null) }
    val selected = first.addingDays(days.currentPage)

    fun index(day: Instant) = day.daysSince(first).coerceIn(0, count - 1)

    /** Как в Календаре: к дальнему дню — один сдвиг страницы, без пролистывания промежуточных. */
    fun goTo(day: Instant) {
        val target = index(day)
        scope.launch {
            val current = days.currentPage
            if (abs(target - current) > 1) days.scrollToPage(if (target > current) target - 1 else target + 1)
            days.animateScrollToPage(target)
        }
    }

    // Полоса недель догоняет выбранный день, а свайп недели переносит на тот же день недели.
    LaunchedEffect(days) {
        snapshotFlow { days.currentPage }.collect { page ->
            if (weeks.currentPage != page / 7 && !weeks.isScrollInProgress) weeks.animateScrollToPage(page / 7)
        }
    }
    LaunchedEffect(weeks) {
        snapshotFlow { weeks.settledPage }.collect { week ->
            val page = days.currentPage
            if (page / 7 != week && !days.isScrollInProgress) days.scrollToPage(week * 7 + page % 7)
        }
    }

    LaunchedEffect(state.pendingLesson, state.lessons) {
        val start = state.pendingLesson ?: return@LaunchedEffect
        val lesson = state.lessons.firstOrNull { abs(it.start.epochSecond - start.epochSecond) < 60 } ?: return@LaunchedEffect
        state.pendingLesson = null
        state.tab = RootTab.SCHEDULE
        goTo(lesson.start)
        state.openLesson(lesson)
    }
    LaunchedEffect(state.pendingToday) {
        if (!state.pendingToday) return@LaunchedEffect
        state.pendingToday = false
        state.sheetLesson = null
        goTo(Clock.now)
    }
    LaunchedEffect(state.pendingChanges) {
        val changes = state.pendingChanges
        if (changes.isEmpty()) return@LaunchedEffect
        state.pendingChanges = emptyList()
        banner = (if (changes.size == 1) "Изменение в расписании" else "Изменения в расписании") to changes.joinToString("\n")
        delay(8000)
        banner = null
    }
    LaunchedEffect(state.error, state.loading) {
        val error = state.error ?: return@LaunchedEffect
        if (state.loading) return@LaunchedEffect
        banner = "Не удалось обновить" to error
        delay(5000)
        if (banner?.second == error) banner = null
    }

    val list: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            HomeHeader(state, selected, first, weeks, onToday = { goTo(Clock.now) }, onSelect = ::goTo,
                onSearch = onSearch, onAccount = onAccount)
            if (state.sessionExpired && !state.sessionCardHidden && !state.demo) {
                SessionExpiredCard(onLogin = onRelogin, onLater = { state.sessionCardHidden = true })
            }
            HorizontalPager(days, modifier = Modifier.fillMaxSize(), key = { it }, beyondViewportPageCount = 1) { page ->
                DayPage(state, first.addingDays(page), wide)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                list(Modifier.width(440.dp).fillMaxHeight())
                LessonPane(state, Modifier.weight(1f).fillMaxHeight().padding(top = 16.dp, end = 16.dp, bottom = 16.dp))
            }
        } else {
            list(Modifier.fillMaxSize())
        }
        AnimatedVisibility(
            visible = banner != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            banner?.let { (title, message) -> BannerCard(title, message) { banner = null } }
        }
    }
}

/** Баннер сверху: изменения в расписании или ошибка обновления. */
@Composable
private fun BannerCard(title: String, message: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 6.dp,
        modifier = Modifier.padding(16.dp).fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (title.startsWith("Не удалось")) Icons.Outlined.ErrorOutline else Icons.Outlined.EventNote, null,
                tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun HomeHeader(
    state: AppState, selected: Instant, first: Instant, weeks: PagerState,
    onToday: () -> Unit, onSelect: (Instant) -> Unit, onSearch: () -> Unit, onAccount: () -> Unit,
) {
    val now = Clock.now
    val month = monthsNominative[selected.month - 1]
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (selected.year == now.year) month else "$month ${selected.year}",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            AnimatedVisibility(!selected.sameDay(now)) {
                FilledTonalButton(onClick = onToday, modifier = Modifier.padding(end = 4.dp)) { Text("Сегодня") }
            }
            SearchButton(onSearch)
            AccountButton(state, onAccount)
        }
        HorizontalPager(weeks, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) { w ->
            WeekRow(state, first.addingDays(w * 7), selected, onSelect)
        }
    }
}

/** Неделя: семь дней, выходные на общей подложке — видно, где кончается неделя. */
@Composable
private fun WeekRow(state: AppState, monday: Instant, selected: Instant, onSelect: (Instant) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.weight(5f)) {
            for (i in 0 until 5) DayCell(state, monday.addingDays(i), selected, onSelect, Modifier.weight(1f))
        }
        Row(Modifier.weight(2f).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            for (i in 5 until 7) DayCell(state, monday.addingDays(i), selected, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DayCell(state: AppState, day: Instant, selected: Instant, onSelect: (Instant) -> Unit, modifier: Modifier) {
    val now = Clock.now
    val isToday = day.sameDay(now)
    val isSelected = day.sameDay(selected)
    val weekend = day.isoWeekday >= 6
    val hasLessons = state.lessonsOn(day).any { !it.cancelled }
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onSelect(day) }
            .padding(vertical = 6.dp)
            .semantics {
                contentDescription = "${if (isToday) "Сегодня, " else ""}${dayTitle(day)}${if (hasLessons) "" else ", пар нет"}"
                this.selected = isSelected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(weekdaysShort[day.isoWeekday - 1], style = MaterialTheme.typography.labelMedium,
            color = if (isToday) scheme.primary else scheme.onSurfaceVariant)
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(
                when {
                    isSelected && isToday -> scheme.primary
                    isSelected -> scheme.onSurface
                    else -> Color.Transparent
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${day.day}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    isSelected && isToday -> scheme.onPrimary
                    isSelected -> scheme.surface
                    isToday -> scheme.primary
                    weekend -> scheme.outline
                    else -> scheme.onSurface
                },
            )
        }
        Box(Modifier.size(4.dp).clip(CircleShape).background(if (hasLessons) scheme.outline else Color.Transparent))
    }
}

/** Пары одного дня: карточка сессии (сегодня), лента с перерывами. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPage(state: AppState, day: Instant, wide: Boolean) {
    // Чтение minute перерисовывает страницу раз в минуту: «идёт сейчас» и «ещё N мин».
    val now = state.minute.let { Clock.now }
    val lessons = state.lessonsOn(day)
    val isToday = day.sameDay(now)
    val open: (Lesson) -> Unit = { state.openLesson(it) }
    PullToRefreshBox(isRefreshing = state.loading, onRefresh = { state.refresh(SyncTrigger.MANUAL) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
            val session = state.session
            if (isToday && session != null && session.showsCard(now)) {
                item(key = "exam") { ExamCard(session) { state.tab = RootTab.SESSION } }
            }
            if (lessons.isEmpty()) {
                item(key = "empty") {
                    val next = state.next(if (isToday) now else day.addingDays(1))
                    EmptyState(Icons.Outlined.Bedtime, if (isToday) "Сегодня пар нет" else "Пар нет",
                        next?.let { "Следующая ${whenLabel(it.start)}" }, Modifier.padding(top = 40.dp))
                }
            } else {
                val active = lessons.filter { !it.cancelled }
                fun left(until: Instant) = durationText(until.secondsSince(now) + 59)
                if (isToday && active.isNotEmpty() && now < active.first().start) {
                    item(key = "now-before") { NowLine(now, "Первая пара через ${left(active.first().start)}") }
                }
                lessons.forEachIndexed { i, lesson ->
                    if (i > 0) {
                        val prev = lessons[i - 1]
                        val gap = lesson.start.secondsSince(prev.end)
                        item(key = "gap-${lesson.id}") {
                            if (isToday && prev.end <= now && now < lesson.start && !prev.cancelled && !lesson.cancelled) {
                                NowLine(now, "${if (gap >= 3600) "Окно" else "Перерыв"} · ещё ${left(lesson.start)}")
                            } else {
                                GapRow(gap)
                            }
                        }
                    }
                    item(key = lesson.id) {
                        TimelineCard(lesson, now, selected = wide && state.sheetLesson?.id == lesson.id,
                            remaining = if (isToday && lesson.isOngoing(now)) "ещё ${left(lesson.end)}" else null) { open(lesson) }
                    }
                }
                val last = active.lastOrNull()
                if (isToday && last != null && now >= last.end) {
                    item(key = "now-after") {
                        val next = state.next(last.end)
                        NowLine(now, "Пары закончились", next?.let { "Следующая ${whenLabel(it.start)} — ${it.subject}" })
                    }
                }
            }
        }
    }
}

/** Красная линия «сейчас»: время на плашке, линия до края и подпись. */
@Composable
private fun NowLine(now: Instant, caption: String, detail: String? = null) {
    val red = nowRed()
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(hm(now), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.clip(CircleShape).background(red).padding(horizontal = 6.dp, vertical = 2.dp))
            Box(Modifier.weight(1f).height(1.5.dp).background(red))
        }
        Text(caption, style = MaterialTheme.typography.labelLarge, color = red, modifier = Modifier.padding(start = 64.dp, top = 2.dp))
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 64.dp, bottom = 4.dp))
        }
    }
}

/** «Перерыв 20 мин» или «Окно 1 ч 40 мин» между парами. */
@Composable
private fun GapRow(seconds: Long) {
    if (seconds > 0) {
        Text("${if (seconds >= 3600) "Окно" else "Перерыв"} ${durationText(seconds)}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 64.dp, top = 6.dp, bottom = 6.dp))
    } else {
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Пара карточкой: время слева, цветная полоска типа, название, тип и аудитория,
 * преподаватель. У идущей пары полоска заполняется, ниже — остаток.
 */
@Composable
fun TimelineCard(lesson: Lesson, now: Instant, selected: Boolean = false, remaining: String? = null, onClick: () -> Unit) {
    val ongoing = lesson.isOngoing(now)
    val dimmed = lesson.cancelled || now > lesson.end
    val progress = if (ongoing) lesson.start.let { s ->
        now.secondsSince(s).toFloat() / lesson.end.secondsSince(s).coerceAtLeast(1)
    } else null
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        color = when {
            selected -> scheme.secondaryContainer
            ongoing -> scheme.surfaceContainerHigh
            else -> scheme.surfaceContainerLow
        },
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().alpha(if (dimmed) 0.55f else 1f).semantics {
            contentDescription = listOf(
                "${hm(lesson.start)}–${hm(lesson.end)}", lesson.subject, lesson.kind,
                if (lesson.isRemote) "дистанционно" else lesson.shortLocation, lesson.lecturer,
                if (lesson.cancelled) "отменена" else "", if (ongoing) "идёт сейчас" else "", remaining ?: "",
            ).filter { it.isNotEmpty() }.joinToString(", ")
        },
    ) {
        Row(Modifier.height(IntrinsicSize.Min).padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(44.dp), horizontalAlignment = Alignment.End) {
                Text(hm(lesson.start), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = if (ongoing) scheme.primary else scheme.onSurface)
                Text(hm(lesson.end), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            KindStripe(lesson.kind, progress = progress)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(lesson.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    textDecoration = if (lesson.cancelled) TextDecoration.LineThrough else null)
                LessonDetails(lesson)
                if (lesson.lecturer.isNotEmpty()) {
                    Text(lesson.lecturer, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
                if (progress != null && remaining != null) {
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f))
                        Text(remaining, style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                    }
                }
            }
        }
    }
}

/** Карточка «Сессия через 13 дней» над лентой сегодняшнего дня. */
@Composable
fun ExamCard(session: ExamSession, onClick: () -> Unit) {
    val now = Clock.now
    val title = session.firstDate?.takeIf { !session.isRunning(now) }?.let { "Сессия ${daysUntil(it, now)}" } ?: "Сессия идёт"
    val next = session.nextEvent
    val subtitle = if (session.isRunning(now) && next != null) {
        "${next.kind.capitalizedFirst()} ${whenLabel(next.start)} · ${next.subject}"
    } else listOf(session.dates, session.summary).filter { it.isNotEmpty() }.joinToString(" · ").capitalizedFirst()
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.School, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

/** Сессия ЛК истекла: расписание из кэша может быть устаревшим. */
@Composable
private fun SessionExpiredCard(onLogin: () -> Unit, onLater: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFFF9500)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.SyncProblem, null, tint = Color.White)
                }
                Column {
                    Text("Расписание может быть устаревшим", style = MaterialTheme.typography.titleMedium)
                    Text("Сессия личного кабинета истекла, обновить расписание не получается.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.padding(start = 58.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onLogin) { Text("Войти") }
                OutlinedButton(onClick = onLater) { Text("Позже") }
            }
        }
    }
}

/** Планшет и складной телефон: выбранная пара рядом с лентой. */
@Composable
private fun LessonPane(state: AppState, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(28.dp), modifier = modifier) {
        val lesson = state.sheetLesson
        if (lesson == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Outlined.EventNote, "Выбери пару", "Здесь будут аудитория на карте, преподаватель и группа.")
            }
        } else {
            LessonSheetContent(lesson, onClose = { state.sheetLesson = null }, inPane = true)
        }
    }
}
