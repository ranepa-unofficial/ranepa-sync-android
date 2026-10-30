package com.qteb.ranepa.sync.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qteb.ranepa.sync.AppState
import com.qteb.ranepa.sync.core.Clock
import com.qteb.ranepa.sync.core.Lesson
import com.qteb.ranepa.sync.core.StudyPlan
import com.qteb.ranepa.sync.core.Subject
import com.qteb.ranepa.sync.core.capitalizedFirst
import com.qteb.ranepa.sync.core.dateOnly
import com.qteb.ranepa.sync.core.dateRange
import com.qteb.ranepa.sync.core.kindPlural
import com.qteb.ranepa.sync.core.month
import com.qteb.ranepa.sync.core.monthsNominative
import com.qteb.ranepa.sync.core.plural
import com.qteb.ranepa.sync.core.shortWhen
import com.qteb.ranepa.sync.core.year
import java.time.Instant

/** Строка списка предметов: предмет из плана и/или расписания. */
private data class SubjectEntry(val name: String, val form: String?, val subject: Subject, val points: Int? = null)

private fun entries(state: AppState, now: Instant): List<SubjectEntry> {
    val base = baseEntries(state, now)
    val progress = state.progress ?: return base
    val list = base.map { it.copy(points = progress.pointsFor(it.name)) }.toMutableList()
    // Предметы с баллами, которых нет ни в плане, ни в расписании.
    for (s in progress.subjects) {
        if (list.none { it.name.lowercase() == s.name.lowercase() }) list += SubjectEntry(s.name, null, Subject(s.name, emptyList(), now), s.points)
    }
    return list
}

private fun baseEntries(state: AppState, now: Instant): List<SubjectEntry> {
    val fromSchedule = Subject.all(state.lessons, now)
    val plan = state.plan
    val semester = plan?.currentSemester(now) ?: return fromSchedule.map { SubjectEntry(it.name, null, it) }
    fun subject(name: String) = fromSchedule.firstOrNull { it.name.lowercase() == name.lowercase() } ?: Subject(name, emptyList(), now)
    val list = plan.subjects(semester).map { SubjectEntry(it.name, it.form, subject(it.name)) }.toMutableList()
    // Предметы из расписания, которых нет в плане семестра.
    for (s in fromSchedule) if (list.none { it.name.lowercase() == s.name.lowercase() }) list += SubjectEntry(s.name, null, s)
    // Сначала те, у которых скоро пара; без пар — в конце.
    return list.sortedWith(compareBy({ it.subject.next?.start ?: Instant.MAX }, { it.name }))
}

/** Предметы семестра — из плана обучения (с формой контроля), иначе из расписания. */
@Composable
fun SubjectsScreen(state: AppState, onSearch: () -> Unit, onAccount: () -> Unit) {
    val routes = LocalRoutes.current
    val now = state.minute.let { Clock.now }
    val list = entries(state, now)
    val plan = state.plan
    val semester = plan?.currentSemester(now)
    val subtitle = semester?.let { s -> plan.course(s)?.let { "$it · $s-й семестр" } }
    Column(Modifier.fillMaxSize()) {
        TabHeader("Предметы", subtitle, state, onSearch, onAccount)
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Outlined.LibraryBooks, "Предметов пока нет", "Они появятся, когда загрузится расписание.")
            }
            return
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listBottom) {
            state.progress?.takeIf { it.showsCard() }?.let { progress ->
                item(key = "grades") {
                    val context = LocalContext.current
                    Box(Modifier.padding(top = 12.dp)) {
                        GradesCard(progress) {
                            com.qteb.ranepa.sync.system.Analytics.screen(context, "grades")
                            routes.grades()
                        }
                    }
                }
            }
            section("Предметы семестра · ${list.size}", list, key = { it.name }) { e -> SubjectRow(e) { routes.subject(e.name) } }
            if (plan != null && plan.items.isNotEmpty()) {
                item { Box(Modifier.height(16.dp)) }
                section(null, listOf(plan)) {
                    NavRow("План обучения", "${it.semesters.size} ${plural(it.semesters.size, "семестр", "семестра", "семестров")}") { routes.plan() }
                }
            }
        }
    }
}

@Composable
private fun SubjectRow(entry: SubjectEntry, onClick: () -> Unit) {
    val subject = entry.subject
    val kind = subject.kinds.maxByOrNull { it.upcoming }?.kind
    val subtitle = when {
        subject.lessons.isEmpty() -> "Пар в расписании нет"
        subject.next == null -> "Пар больше нет"
        else -> "${shortWhen(subject.next!!.start)} · впереди ${subject.upcoming.size}"
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).height(IntrinsicSize.Min).padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        KindStripe(kind ?: "", muted = kind == null)
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                color = if (subject.lessons.isEmpty()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
            entry.points?.let { PointsLine(it) }
        }
        entry.form?.let { FormBadge(it) }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Строка-переход: название, значение и стрелка. */
@Composable
fun NavRow(title: String, value: String? = null, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun consultationNote(c: Lesson?): String? {
    c ?: return null
    if (c.end <= Clock.now) return null
    return "Консультация — ${shortWhen(c.start)}${if (c.isRemote) ", дистанционно" else ", ${c.shortLocation}"}"
}

/** Предмет: следующая пара, экзамен, преподаватели, где обычно проходит, все пары по месяцам. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectScreen(state: AppState, name: String, onBack: () -> Unit) {
    val routes = LocalRoutes.current
    val context = LocalContext.current
    val now = state.minute.let { Clock.now }
    val subject = Subject(name, state.lessons.filter { it.subject == name }, now)
    val open: (Lesson) -> Unit = { state.openLesson(it, From.Subject(name)) }
    var expanded by remember(name) { mutableStateOf(setOf<Int>()) }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()

    /** «Зачёт · 72 ч · 2 з. е. · 1-й семестр» — из плана обучения. */
    val planLine = state.plan?.let { plan ->
        val current = plan.currentSemester(now)
        val item = plan.item(name, current) ?: plan.item(name, null) ?: return@let null
        buildList {
            item.form?.let(::add)
            if (item.hours > 0) add("${item.hours} ч")
            if (item.ze > 0) add("${item.ze} з. е.")
            if (item.semesterNumber != current) add(item.semester)
        }.joinToString(" · ")
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { BackBar("", onBack, scroll) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp)) {
            item { ScreenTitle(name, planLine, if (subject.lessons.isEmpty()) "Пар в расписании нет" else subject.upcomingSummary) }
            subjectGradesSection(name, state.progress, weekDelta(context, name))
            subject.next?.let { next ->
                section("Следующая", listOf(next)) { LessonListRow(it, showSubject = false) { open(it) } }
            }
            subject.exam?.let { exam ->
                section(exam.kind.capitalizedFirst(), listOf(exam)) {
                    LessonListRow(it, showSubject = false, note = consultationNote(subject.consultation)) { open(it) }
                }
            }
            if (subject.lecturers.isNotEmpty()) {
                section(if (subject.lecturers.size == 1) "Преподаватель" else "Преподаватели", subject.lecturers, key = { it.name }) { l ->
                    LecturerRow(l, onClick = { routes.lecturer(l.name) }, onMail = {
                        open(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${l.email}?subject=${Uri.encode(name)}")))
                    })
                }
            }
            if (subject.kinds.isNotEmpty()) {
                val rows: List<Any> = subject.kinds + listOfNotNull(subject.lmsLink)
                section("Занятия", rows) { row ->
                    when (row) {
                        is Subject.KindSummary -> KindRow(row)
                        is String -> NavRow("Курс в СДО") { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(row))) }
                    }
                }
            }
            val upcoming = subject.lessons.filter { it.end > now.dateOnly }
            val groups = upcoming.groupBy { it.start.year * 100 + it.start.month }.toSortedMap()
            groups.entries.forEachIndexed { index, (key, items) ->
                val isOpen = index == 0 || key in expanded
                val title = "${monthsNominative[key % 100 - 1]} · ${items.size} ${plural(items.size, "пара", "пары", "пар")}"
                if (isOpen) {
                    section(title, items, key = { it.id }) { LessonListRow(it, showSubject = false) { open(it) } }
                } else {
                    item(key = "m$key") { SectionHeader(title) }
                    item(key = "show$key") {
                        TextButton(onClick = { expanded = expanded + key }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Показать") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LecturerRow(l: Subject.Lecturer, onClick: () -> Unit, onMail: () -> Unit) {
    val kinds = l.kinds.mapIndexed { i, k -> if (i == 0) k else k.lowercase() }.joinToString(" и ")
    val subtitle = (listOf(kinds).filter { it.isNotEmpty() } + if (l.upcoming > 0) listOf("${l.upcoming} впереди") else emptyList())
        .joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(l.name, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (l.email.isNotEmpty()) {
            FilledTonalIconButton(onClick = onMail) { Icon(Icons.Outlined.Email, contentDescription = "Написать ${l.name}") }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun KindRow(k: Subject.KindSummary) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KindStripe(k.kind)
        Column {
            Text(kindPlural(k.kind), style = MaterialTheme.typography.bodyLarge)
            Text(listOfNotNull(if (k.upcoming > 0) "${k.upcoming} впереди" else "все прошли", k.usualRoom?.let { "обычно $it" })
                .joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Преподаватель: почта, его предметы и ближайшие пары. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LecturerScreen(state: AppState, name: String, onBack: () -> Unit) {
    val routes = LocalRoutes.current
    val context = LocalContext.current
    val now = state.minute.let { Clock.now }
    val own = state.lessons.filter { it.lecturer == name }
    val email = own.firstOrNull { it.email.isNotEmpty() }?.email ?: ""
    val subjects = Subject.all(own, now)
    val upcoming = own.filter { !it.cancelled && it.end > now }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { BackBar("", onBack, scroll) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp)) {
            item {
                ScreenTitle(name, if (upcoming.isEmpty()) "Пар больше нет"
                    else "Впереди ${upcoming.size} ${plural(upcoming.size, "пара", "пары", "пар")}")
            }
            if (email.isNotEmpty()) {
                item { Box(Modifier.height(12.dp)) }
                section(null, listOf(email)) {
                    Row(
                        Modifier.fillMaxWidth().copyable(email) {
                            open(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")))
                        }.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Email, null, tint = MaterialTheme.colorScheme.primary)
                        Text(email, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            section(if (subjects.size == 1) "Предмет" else "Предметы", subjects, key = { it.name }) { s ->
                NavRow(s.name, subtitle = s.kinds.joinToString(", ") { kindPlural(it.kind) }) { routes.subject(s.name) }
            }
            if (upcoming.isNotEmpty()) {
                section("Ближайшие пары", upcoming.take(10), key = { it.id }) {
                    LessonListRow(it) { state.openLesson(it, From.Lecturer(name)) }
                }
            }
        }
    }
}

/** План обучения: все семестры с предметами, объёмом и формой контроля. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(state: AppState, onBack: () -> Unit) {
    val routes = LocalRoutes.current
    val now = Clock.now
    val plan = state.plan
    val current = plan?.currentSemester(now)
    var expanded by remember { mutableStateOf(setOf<Int>()) }
    val preview = 4
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { BackBar("План обучения", onBack, scroll) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp)) {
            state.program?.let { item { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp)) } }
            for (semester in plan?.semesters.orEmpty()) {
                val items = plan!!.subjects(semester)
                val shown = when {
                    semester in expanded -> items
                    semester == current -> items.take(preview)
                    else -> emptyList()
                }
                val title = header(semester, plan, current, now)
                if (shown.isEmpty()) item(key = "h$semester") { SectionHeader(title) }
                else section(title, shown, key = { "$semester-${it.name}" }) { i -> PlanRow(i) { routes.subject(i.name) } }
                if (shown.size < items.size) {
                    item(key = "more$semester") {
                        TextButton(onClick = { expanded = expanded + semester }, modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(if (shown.isEmpty()) "Показать ${items.size} ${plural(items.size, "предмет", "предмета", "предметов")}"
                                else "Показать ещё ${items.size - shown.size}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanRow(item: StudyPlan.Item, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(item.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (item.ze > 0) Text("${item.ze} з. е.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        item.form?.let { FormBadge(it) }
    }
}

/** «1-й семестр · сейчас · сессия 11–30 января». */
private fun header(semester: Int, plan: StudyPlan, current: Int?, now: Instant): String {
    val parts = mutableListOf("$semester-й семестр")
    if (semester == current) parts += "сейчас"
    val dates = plan.sessions(semester).mapNotNull { s ->
        val a = s.startDate ?: return@mapNotNull null
        val b = s.endDate?.minusSeconds(86400) ?: return@mapNotNull null
        val range = dateRange(a, b)
        if (a.year == now.year || (a.year == now.year + 1 && a.month < now.month)) range else "$range ${a.year}"
    }
    if (dates.isNotEmpty()) parts += "сессия " + dates.joinToString(", ")
    return parts.joinToString(" · ")
}
