package com.qteb.ranepa.sync.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qteb.ranepa.sync.AppState
import com.qteb.ranepa.sync.core.Clock
import com.qteb.ranepa.sync.core.Progress
import com.qteb.ranepa.sync.core.ProgressHistory
import com.qteb.ranepa.sync.core.Store
import com.qteb.ranepa.sync.core.day
import com.qteb.ranepa.sync.core.daysSince
import com.qteb.ranepa.sync.core.hm
import com.qteb.ranepa.sync.core.month
import com.qteb.ranepa.sync.core.monthsGenitive
import com.qteb.ranepa.sync.core.percent
import com.qteb.ranepa.sync.core.plural
import com.qteb.ranepa.sync.ui.theme.LocalDark
import java.time.Instant

/** Зелёный посещаемости и роста баллов: в светлой теме темнее, чтобы читался. */
@Composable
fun gradesGreen(): Color = if (LocalDark.current) Color(0xFF30D158) else Color(0xFF248A3D)

/** Полоска баллов БРС, 0–100, в акцентном цвете. */
@Composable
fun PointsBar(points: Int, modifier: Modifier = Modifier, height: Dp = 4.dp, color: Color = MaterialTheme.colorScheme.primary) {
    val fraction = points.coerceIn(0, 100) / 100f
    Box(modifier.height(height).clip(RoundedCornerShape(height / 2)).background(color.copy(alpha = 0.16f))) {
        if (points > 0) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(height / 2)).background(color))
        }
    }
}

/** Строка под предметом в списке: полоска гибкой ширины и число. Тесно — сжимается полоска, а не число. */
@Composable
fun PointsLine(points: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(top = 4.dp).clearAndSetSemantics { contentDescription = "$points из 100 баллов" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PointsBar(points, Modifier.weight(1f, fill = false).widthIn(min = 40.dp, max = 150.dp).fillMaxWidth())
        Text("$points", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Показывать ли карточку на «Предметах»: есть средний балл или текущий месяц. */
fun Progress.showsCard(): Boolean = averagePoints != null || currentMonth != null

/** Карточка наверху «Предметов»: средний балл БРС и посещаемость. */
@Composable
fun GradesCard(progress: Progress, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min).padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            val average = progress.averagePoints
            val month = progress.currentMonth
            if (average != null) {
                val n = progress.subjects.size
                GradesColumn("Средний балл БРС", "$average", "из 100", "по $n ${plural(n, "предмету", "предметам", "предметам")}",
                    Modifier.weight(1f))
            }
            if (average != null && month != null) {
                VerticalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (month != null) {
                GradesColumn("Посещаемость", "${percent(month)}%", null, "${month.name.lowercase()}: ${month.fact} из ${month.plan}",
                    Modifier.weight(1f))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GradesColumn(title: String, value: String, unit: String?, note: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (unit != null) {
                Text(unit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** «сегодня в 08:40», «вчера в 21:10», «14 октября». */
fun updatedLabel(t: Instant, now: Instant = Clock.now): String = when (now.daysSince(t)) {
    0 -> "сегодня в ${hm(t)}"
    1 -> "вчера в ${hm(t)}"
    else -> "${t.day} ${monthsGenitive[t.month - 1]}"
}

/** Чип «+4 за неделю»: зелёный при росте, серый при падении; при 0 или без данных — нет. */
@Composable
fun WeekDeltaChip(delta: Int?) {
    if (delta == null || delta == 0) return
    val up = delta > 0
    val color = if (up) gradesGreen() else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        "${if (up) "+" else ""}$delta за неделю",
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier.clip(RoundedCornerShape(13.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Секция «Успеваемость» на экране предмета: баллы, изменение за неделю, полоска. */
fun LazyListScope.subjectGradesSection(name: String, progress: Progress?, weekDelta: Int?) {
    val points = progress?.pointsFor(name) ?: return
    section("Успеваемость", listOf(points), footer = "Баллы БРС из личного кабинета, обновлено ${updatedLabel(progress.fetchedAt)}") {
        SubjectPoints(it, weekDelta)
    }
}

@Composable
fun SubjectPoints(points: Int, weekDelta: Int?) {
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 18.dp)
        .semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$points", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("из 100 баллов", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(top = 8.dp))
            WeekDeltaChip(weekDelta)
        }
        PointsBar(points, Modifier.fillMaxWidth(), height = 8.dp)
    }
}

/** Успеваемость: посещаемость по месяцам, средний балл, уведомления. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(state: AppState, onBack: () -> Unit) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { state.checkNotifications() }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scroll.nestedScrollConnection), topBar = { BackBar("Успеваемость", onBack, scroll) }) { padding ->
        GradesContent(
            progress = state.progress,
            notifications = state.gradeNotifications,
            onNotifications = { on ->
                state.changeGradeNotifications(on)
                if (on && Build.VERSION.SDK_INT >= 33 && !com.qteb.ranepa.sync.system.Notifications.allowed(context)) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp),
        )
    }
}

/** Содержимое экрана «Успеваемость» — отдельно, чтобы снимать скриншот-тестами. */
@Composable
fun GradesContent(progress: Progress?, notifications: Boolean, onNotifications: (Boolean) -> Unit, contentPadding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        if (progress != null && progress.months.isNotEmpty()) {
            val current = progress.months.indexOfLast { it.fact > 0 }
            section("Посещаемость", progress.months.withIndex().toList(), key = { "m${it.index}" },
                footer = "Посещённые занятия из запланированных, как в личном кабинете") { (i, m) -> MonthRow(m, ahead = i > current) }
        }
        val values = listOfNotNull(
            progress?.averageBrs?.let { "По 100-балльной шкале" to it },
            progress?.rating?.let { "По 5-балльной шкале" to it },
        )
        section("Средний балл", values.ifEmpty { listOf("" to "") }) { (title, value) ->
            if (title.isEmpty()) {
                Text("Появится после первой сессии", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp))
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        section("Уведомления", listOf(notifications),
            footer = "Приложение проверяет баллы раз в день и при обновлении расписания жестом «потянуть вниз».") { on ->
            Row(Modifier.fillMaxWidth().clickable { onNotifications(!on) }.padding(start = 18.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Новые баллы", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = on, onCheckedChange = onNotifications)
            }
        }
    }
}

@Composable
private fun MonthRow(m: Progress.Month, ahead: Boolean) {
    val textColor = if (ahead) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)
            .clearAndSetSemantics { contentDescription = if (ahead) "${m.name}, впереди" else "${m.name}: ${m.fact} из ${m.plan}" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(m.name, style = MaterialTheme.typography.bodyLarge, color = textColor, modifier = Modifier.width(92.dp))
        PointsBar(if (ahead) 0 else percent(m), Modifier.weight(1f), height = 6.dp, color = gradesGreen())
        Text(if (ahead) "впереди" else "${m.fact} из ${m.plan}", style = MaterialTheme.typography.bodyLarge, color = textColor,
            modifier = Modifier.widthIn(min = 70.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

/** «+4 за неделю» по сохранённой истории. */
fun weekDelta(context: android.content.Context, name: String): Int? = ProgressHistory.weekDelta(name, Clock.now, Store(context))
