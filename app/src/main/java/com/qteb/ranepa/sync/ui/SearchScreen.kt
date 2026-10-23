package com.qteb.ranepa.sync.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.qteb.ranepa.sync.AppState
import com.qteb.ranepa.sync.core.Clock
import com.qteb.ranepa.sync.core.Subject

/** Поиск по семестру: предмет, преподаватель или аудитория. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(state: AppState, onBack: () -> Unit) {
    val routes = LocalRoutes.current
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Предмет, преподаватель, аудитория") },
            leadingIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") } },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "Очистить") }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
        )
        val q = query.trim().lowercase()
        val now = Clock.now
        if (q.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Outlined.Search, "Поиск по семестру",
                    "Ищи по названию предмета, фамилии преподавателя или номеру аудитории.")
            }
            return@Column
        }
        val subjects = Subject.all(state.lessons, now).filter { it.name.lowercase().contains(q) }
        val lecturers = state.lessons.filter { it.lecturer.lowercase().contains(q) }.groupBy { it.lecturer }
        val lessons = state.lessons.filter { l ->
            !l.cancelled && l.end > now && (l.subject.lowercase().contains(q) || l.lecturer.lowercase().contains(q) ||
                l.room.lowercase().contains(q))
        }
        if (subjects.isEmpty() && lecturers.isEmpty() && lessons.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Outlined.SearchOff, "Ничего не нашлось", "По запросу «${query.trim()}» нет ни предметов, ни преподавателей, ни пар.")
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listBottom) {
            if (subjects.isNotEmpty()) section("Предметы", subjects, key = { "s-${it.name}" }) { s ->
                NavRow(s.name, subtitle = s.upcomingSummary) { routes.subject(s.name) }
            }
            if (lecturers.isNotEmpty()) section("Преподаватели", lecturers.keys.sorted(), key = { "l-$it" }) { name ->
                NavRow(name, subtitle = lecturers.getValue(name).map { it.subject }.toSet().sorted().joinToString(", ")) {
                    routes.lecturer(name)
                }
            }
            if (lessons.isNotEmpty()) section("Ближайшие пары", lessons.take(30), key = { "p-${it.id}" }) {
                LessonListRow(it) { state.openLesson(it) }
            }
        }
    }
}
