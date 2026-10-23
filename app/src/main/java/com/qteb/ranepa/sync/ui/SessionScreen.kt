package com.qteb.ranepa.sync.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qteb.ranepa.sync.AppState
import com.qteb.ranepa.sync.core.Clock
import com.qteb.ranepa.sync.core.ExamSession
import com.qteb.ranepa.sync.core.daysUntil
import com.qteb.ranepa.sync.core.shortWhen
import java.time.Instant

/** Вкладка «Сессия»: ближайшая сессия по плану — экзамены и зачёты строками пар, сданное — внизу. */
@Composable
fun SessionScreen(state: AppState, onSearch: () -> Unit, onAccount: () -> Unit) {
    val routes = LocalRoutes.current
    val now = state.minute.let { Clock.now }
    val session = state.session
    Column(Modifier.fillMaxSize()) {
        TabHeader("Сессия", session?.let { subtitle(it, now) }, state, onSearch, onAccount)
        if (session == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Outlined.School, "Сессии пока нет", "Даты появятся, когда загрузится план обучения.")
            }
            return
        }
        val exams = session.exams.filter { !session.isDone(it, now) }
        val credits = session.credits.filter { !session.isDone(it, now) }
        val done = session.all.filter { session.isDone(it, now) }
        val row: @Composable (ExamSession.Entry) -> Unit = { e ->
            val event = e.event
            if (event != null) {
                LessonListRow(event, note = consultationNote(e, now)) { state.openLesson(event) }
            } else {
                NoDateRow(e) { routes.subject(e.subject) }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listBottom) {
            if (exams.isNotEmpty()) section("${if (exams.size == 1) "Экзамен" else "Экзамены"} · ${exams.size}", exams, key = { it.id }, row = row)
            if (credits.isNotEmpty()) section("${if (credits.size == 1) "Зачёт" else "Зачёты"} · ${credits.size}", credits, key = { it.id }, row = row)
            if (done.isNotEmpty()) section("Прошли", done, key = { "done-${it.id}" }, row = row)
            item { SectionFooter("Вечером накануне каждого экзамена и зачёта с датой придёт напоминание.") }
        }
    }
}

/** «11–30 января · через 89 дней», «11–30 января · идёт, осталось 8 из 9». */
private fun subtitle(session: ExamSession, now: Instant): String {
    val whenText = if (session.isRunning(now)) "идёт, осталось ${session.remaining(now)} из ${session.all.size}"
        else session.firstDate?.let { daysUntil(it, now) } ?: ""
    return listOf(session.dates, whenText).filter { it.isNotEmpty() }.joinToString(" · ")
}

private fun consultationNote(e: ExamSession.Entry, now: Instant): String? {
    val c = e.consultation ?: return null
    if (c.end <= now) return null
    return "Консультация — ${shortWhen(c.start)}${if (c.isRemote) ", дистанционно" else ", ${c.shortLocation}"}"
}

/** Без даты — предмет и форма контроля; нажатие открывает предмет. */
@Composable
private fun NoDateRow(e: ExamSession.Entry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).height(IntrinsicSize.Min).padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        KindStripe("экзамен", muted = true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Дата не назначена", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            Text(e.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(e.form, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
