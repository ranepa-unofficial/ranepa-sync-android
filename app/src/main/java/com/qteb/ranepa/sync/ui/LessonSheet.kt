package com.qteb.ranepa.sync.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.qteb.ranepa.sync.core.Lesson
import com.qteb.ranepa.sync.core.capitalizedFirst
import com.qteb.ranepa.sync.core.dayTitle
import com.qteb.ranepa.sync.core.hm
import com.qteb.ranepa.sync.system.Campus
import com.qteb.ranepa.sync.system.MapApp
import com.qteb.ranepa.sync.system.Places
import com.qteb.ranepa.sync.system.Point
import com.qteb.ranepa.sync.ui.theme.LocalDark
import com.qteb.ranepa.sync.ui.theme.kindText
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.TilesOverlay

/** Откуда открыта карточка: ссылку на этот же экран не показываем. */
sealed interface From {
    data class Subject(val name: String) : From
    data class Lecturer(val name: String) : From
}

/** Скопировать строку долгим нажатием, как контекстное меню «Скопировать» в iOS. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.copyable(text: String, onClick: (() -> Unit)? = null): Modifier {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return combinedClickable(
        enabled = true,
        onClick = { onClick?.invoke() },
        onLongClick = {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(text, text))) }
            Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
        },
    )
}

/**
 * Подробности пары: тип, название, когда; аудитория с картой корпуса,
 * преподаватель, группы, ссылка на предмет, комментарий и курс в СДО.
 * Открывается листом снизу, на широком экране — рядом с лентой.
 */
@Composable
fun LessonSheetContent(lesson: Lesson, onClose: () -> Unit, inPane: Boolean = false, from: From? = null) {
    val routes = LocalRoutes.current
    val context = LocalContext.current
    val address = Places.address(lesson.room)
    var point by remember(lesson.id) { mutableStateOf(address?.let { Places.cachedLocation(context, it) }) }
    var chooseMaps by remember { mutableStateOf(false) }
    LaunchedEffect(lesson.id) {
        if (address != null && point == null && !lesson.isRemote) point = Places.locate(context, address)
    }

    fun openPlace() {
        val a = address ?: return
        MapApp.preferred(context)?.let { return open(context, it.intent(point, lesson.mapTitle, Places.query(a))) }
        val apps = MapApp.installedApps(context)
        when (apps.size) {
            0 -> open(context, MapApp.systemIntent(point, lesson.mapTitle, Places.query(a)))
            1 -> open(context, apps[0].intent(point, lesson.mapTitle, Places.query(a)))
            else -> chooseMaps = true
        }
    }

    // Снизу — отступ под полоску жестов: последняя строка не уходит под неё.
    Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Row(Modifier.padding(start = 8.dp, top = if (inPane) 20.dp else 0.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val color = kindText(lesson.kind)
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(kindIcon(lesson.kind), null, tint = color, modifier = Modifier.size(16.dp))
                    Text(if (lesson.kind.isEmpty()) "Пара" else lesson.kind.capitalizedFirst(),
                        style = MaterialTheme.typography.labelLarge, color = color)
                }
                Text(lesson.subject, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                    textDecoration = if (lesson.cancelled) TextDecoration.LineThrough else null,
                    modifier = Modifier.copyable(lesson.subject))
                Text("${dayTitle(lesson.start)}, ${hm(lesson.start)}–${hm(lesson.end)}",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (lesson.cancelled) Text("Отменена", color = MaterialTheme.colorScheme.error)
            }
            if (inPane) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Закрыть") }
            }
        }

        val rows = buildList<@Composable () -> Unit> {
            if (lesson.isRemote) {
                add { InfoRow(Icons.Outlined.Videocam, "Дистанционно") }
            } else if (lesson.room.isNotEmpty()) {
                point?.let { p -> add { CampusMap(p, address, lesson, onClick = ::openPlace) } }
                add {
                    InfoRow(Icons.Outlined.Place, lesson.mapTitle, address, copy = lesson.room,
                        trailing = if (address != null) Icons.AutoMirrored.Filled.OpenInNew else null,
                        onClick = if (address != null) ::openPlace else null)
                }
            }
            if (lesson.lecturer.isNotEmpty()) {
                add {
                    if (from == From.Lecturer(lesson.lecturer)) InfoRow(Icons.Outlined.Person, lesson.lecturer)
                    else InfoRow(Icons.Outlined.Person, lesson.lecturer, trailing = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        onClick = { routes.lecturer(lesson.lecturer) })
                }
            }
            if (lesson.groups.isNotEmpty()) add { InfoRow(Icons.Outlined.Groups, lesson.groups.joinToString(", ")) }
        }
        if (rows.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                Column {
                    rows.forEachIndexed { i, row ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh)
                        row()
                    }
                }
            }
        }

        if (from != From.Subject(lesson.subject)) {
            FilledTonalButton(onClick = { routes.subject(lesson.subject) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(56.dp)) {
                Icon(Icons.Outlined.LibraryBooks, null, modifier = Modifier.size(20.dp))
                Text("Все пары и экзамен по предмету", modifier = Modifier.padding(start = 10.dp))
            }
        }
        if (lesson.comment.isNotEmpty()) {
            SectionHeader("Комментарий")
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(24.dp)) {
                InfoRow(Icons.Outlined.Notes, lesson.comment)
            }
        }
        if (lesson.lmsLink.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                InfoRow(Icons.Outlined.MenuBook, "Открыть курс в СДО", copy = lesson.lmsLink,
                    trailing = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(lesson.lmsLink))) })
            }
        }
    }

    if (chooseMaps && address != null) {
        MapsDialog(context, address, point, lesson, onDismiss = { chooseMaps = false })
    }
}

fun open(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(context, "Не нашлось приложения, чтобы открыть", Toast.LENGTH_SHORT).show() }
}

/** Строка карточки: значок, текст с подписью, справа — куда ведёт. */
@Composable
private fun InfoRow(
    icon: ImageVector, title: String, subtitle: String? = null, copy: String = title,
    trailing: ImageVector? = null, onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().copyable(copy, onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) Icon(trailing, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/** Корпус на карте OpenStreetMap. Карта не листается: нажатие открывает приложение карт. */
@Composable
private fun CampusMap(point: Point, address: String?, lesson: Lesson, onClick: () -> Unit) {
    val dark = LocalDark.current
    val highlight = MaterialTheme.colorScheme.primary
    val building = address?.let(Campus::building)
    val label = when {
        address?.contains("стадион") == true -> "Стадион"
        address != null -> Regex("""корпус\s*(\d+)""").find(address)?.groupValues?.get(1)?.let { "Корпус $it" } ?: lesson.shortLocation
        else -> lesson.shortLocation
    }
    // MapView рисует и за своими границами — обрезаем по блоку.
    Box(Modifier.fillMaxWidth().height(150.dp).clipToBounds().copyable(lesson.room, onClick)) {
        AndroidView(
            factory = { ctx ->
                Configuration.getInstance().userAgentValue = ctx.packageName
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(false)
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    isClickable = false
                    setOnTouchListener { _, _ -> true }
                }
            },
            update = { map ->
                map.controller.setZoom(if (building != null) 17.6 else 17.0)
                map.controller.setCenter(GeoPoint(point.lat, point.lon))
                if (dark) map.overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
                map.overlays.clear()
                if (building != null) {
                    map.overlays += Polygon(map).apply {
                        points = building.outline.map { GeoPoint(it.lat, it.lon) }
                        fillPaint.color = highlight.copy(alpha = 0.18f).toArgb()
                        outlinePaint.color = highlight.copy(alpha = 0.8f).toArgb()
                        outlinePaint.strokeWidth = 4f
                        setOnClickListener { _, _, _ -> false }
                    }
                }
                map.invalidate()
            },
            modifier = Modifier.matchParentSize(),
        )
        Text(
            label, style = MaterialTheme.typography.labelLarge, color = highlight,
            modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)).padding(horizontal = 10.dp, vertical = 3.dp),
        )
        Text(
            "© OpenStreetMap", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)).padding(horizontal = 4.dp),
        )
    }
}

/** Карты, установленные на телефоне: какой открыть корпус, или скопировать адрес. */
@Composable
private fun MapsDialog(context: Context, address: String, point: Point?, lesson: Lesson, onDismiss: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(address) },
        text = {
            Column {
                MapApp.installedApps(context).forEach { app ->
                    TextButton(onClick = {
                        onDismiss()
                        open(context, app.intent(point, lesson.mapTitle, Places.query(address)))
                    }, modifier = Modifier.fillMaxWidth()) { Text(app.title, modifier = Modifier.fillMaxWidth()) }
                }
                TextButton(onClick = {
                    onDismiss()
                    val text = Places.query(address)
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(text, text))) }
                }, modifier = Modifier.fillMaxWidth()) { Text("Скопировать адрес", modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
