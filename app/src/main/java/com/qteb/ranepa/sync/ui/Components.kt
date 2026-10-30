package com.qteb.ranepa.sync.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qteb.ranepa.sync.AppState
import com.qteb.ranepa.sync.R
import com.qteb.ranepa.sync.core.Clock
import com.qteb.ranepa.sync.core.Lesson
import com.qteb.ranepa.sync.core.capitalizedFirst
import com.qteb.ranepa.sync.core.dayName
import com.qteb.ranepa.sync.core.hm
import com.qteb.ranepa.sync.ui.theme.kindStripe
import com.qteb.ranepa.sync.ui.theme.kindText

/** Иконка типа пары — как символы в iOS-версии. */
fun kindIcon(kind: String): ImageVector = when (kind) {
    "лекция" -> Icons.Outlined.RecordVoiceOver
    "семинар" -> Icons.Outlined.ChatBubbleOutline
    "физкультура" -> Icons.Outlined.DirectionsRun
    "лабораторная" -> Icons.Outlined.Science
    "консультация" -> Icons.Outlined.HelpOutline
    "экзамен", "зачёт" -> Icons.Outlined.Verified
    else -> Icons.Outlined.MenuBook
}

/** «Лекция · 5 - 201»: тип цветом типа, остальное приглушённо. */
@Composable
fun LessonDetails(l: Lesson) {
    val rest = listOf(
        if (l.location.isEmpty()) "" else if (l.isRemote) "дистанционно" else l.shortLocation,
        if (l.cancelled) "отменена" else "",
    ).filter { it.isNotEmpty() }.joinToString(" · ")
    val kindColor = kindText(l.kind)
    Text(
        buildAnnotatedString {
            if (l.kind.isNotEmpty()) {
                withStyle(SpanStyle(color = kindColor, fontWeight = FontWeight.Medium)) { append(l.kind.capitalizedFirst()) }
                if (rest.isNotEmpty()) append(" · ")
            }
            append(rest)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Цветная полоска типа. У идущей пары заполняется сверху вниз по ходу пары. */
@Composable
fun KindStripe(kind: String, modifier: Modifier = Modifier, progress: Float? = null, muted: Boolean = false) {
    val color = kindStripe(kind, muted)
    Box(
        modifier
            .width(if (progress == null) 4.dp else 5.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(3.dp))
            .background(if (progress == null) color else color.copy(alpha = 0.25f)),
    ) {
        if (progress != null) {
            Box(
                Modifier.fillMaxWidth().fillMaxHeight(progress.coerceIn(0.05f, 1f))
                    .clip(RoundedCornerShape(3.dp)).background(color),
            )
        }
    }
}

/** Фото из ЛК, а если его нет — инициалы «ИФ», в демо — схематичный портрет. */
@Composable
fun Avatar(state: AppState, size: Dp) {
    val modifier = Modifier.size(size).clip(CircleShape)
    val photo = state.photo
    when {
        photo != null -> Image(photo, null, modifier, contentScale = ContentScale.Crop)
        state.demo -> Image(painterResource(R.drawable.demo_avatar), null, modifier, contentScale = ContentScale.Crop)
        else -> {
            val parts = (state.fullName ?: "").split(" ").filter { it.isNotEmpty() }
            val initials = if (parts.size >= 2) "${parts[1].take(1)}${parts[0].take(1)}" else ""
            Box(modifier.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                if (initials.isEmpty()) {
                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(size * 0.55f))
                } else {
                    Text(initials, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Фото в шапке — открывает аккаунт и настройки. */
@Composable
fun AccountButton(state: AppState, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick)
            .semantics { contentDescription = "Аккаунт и настройки" },
        contentAlignment = Alignment.Center,
    ) {
        // Красная точка — вышла новая версия, а строку в аккаунте ещё не видели.
        if (state.updateUnseen) {
            androidx.compose.material3.BadgedBox(badge = { androidx.compose.material3.Badge() }) { Avatar(state, 36.dp) }
        } else {
            Avatar(state, 36.dp)
        }
    }
}

/** Форма групп в списках, как в Material 3 Expressive: крайние строки скруглены сильнее. */
fun groupShape(index: Int, count: Int, outer: Dp = 24.dp, inner: Dp = 4.dp): Shape = RoundedCornerShape(
    topStart = if (index == 0) outer else inner, topEnd = if (index == 0) outer else inner,
    bottomStart = if (index == count - 1) outer else inner, bottomEnd = if (index == count - 1) outer else inner,
)

/** Секция списка: подпись цветом акцента и строки одной группой. */
fun <T> LazyListScope.section(
    title: String?,
    items: List<T>,
    key: ((T) -> Any)? = null,
    footer: String? = null,
    row: @Composable (T) -> Unit,
) {
    if (title != null) item(key = "h:$title") { SectionHeader(title) }
    items.forEachIndexed { i, item ->
        item(key = key?.let { "r:${it(item)}" }) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = groupShape(i, items.size),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = if (i == 0) 0.dp else 3.dp),
            ) { row(item) }
        }
    }
    if (footer != null) item { SectionFooter(footer) }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun SectionFooter(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp),
    )
}

/**
 * Пара в списке — везде одинаково: полоска цвета типа, когда, предмет,
 * тип и аудитория. Прошедшая — бледнее.
 */
@Composable
fun LessonListRow(
    lesson: Lesson,
    showDay: Boolean = true,
    showSubject: Boolean = true,
    note: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val past = lesson.end < Clock.now
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .height(IntrinsicSize.Min)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(if (past || lesson.cancelled) 0.5f else 1f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KindStripe(lesson.kind)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (showDay) "${dayName(lesson.start)}, ${hm(lesson.start)}–${hm(lesson.end)}" else "${hm(lesson.start)}–${hm(lesson.end)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showSubject) {
                Text(lesson.subject, style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (lesson.cancelled) TextDecoration.LineThrough else null)
            }
            LessonDetails(lesson)
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** «Экзамен» — цветом сессии, остальное серым. */
@Composable
fun FormBadge(form: String) {
    val exam = form.startsWith("Экз")
    Text(
        form,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (exam) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (exam) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Шапка вкладки: крупный заголовок, поиск и аватар в одной строке, под ними подзаголовок. */
@Composable
fun TabHeader(
    title: String,
    subtitle: String?,
    state: AppState,
    onSearch: () -> Unit,
    onAccount: () -> Unit,
    titleContent: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (titleContent != null) titleContent() else {
                    Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            SearchButton(onSearch)
            AccountButton(state, onAccount)
        }
        if (!subtitle.isNullOrEmpty()) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SearchButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Default.Search, contentDescription = "Поиск",
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Верхняя панель вложенного экрана: стрелка «Назад» и заголовок. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackBar(title: String, onBack: () -> Unit, scroll: TopAppBarScrollBehavior? = null) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        scrollBehavior = scroll,
    )
}

/** Крупный заголовок экрана в начале списка: длинные названия переносятся, а не обрезаются. */
@Composable
fun ScreenTitle(title: String, vararg lines: String?) {
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        lines.filterNotNull().filter { it.isNotEmpty() }.forEach {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Пустое состояние: значок, заголовок и пояснение по центру. */
@Composable
fun EmptyState(icon: ImageVector, title: String, text: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (text != null) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

/** Отступ под системной панелью навигации внизу списков. */
val listBottom = PaddingValues(bottom = 24.dp)
