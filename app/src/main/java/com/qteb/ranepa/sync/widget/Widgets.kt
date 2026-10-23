package com.qteb.ranepa.sync.widget

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColor
import com.qteb.ranepa.sync.MainActivity
import com.qteb.ranepa.sync.core.Clock
import com.qteb.ranepa.sync.core.KindColors
import com.qteb.ranepa.sync.core.Lesson
import com.qteb.ranepa.sync.core.Store
import com.qteb.ranepa.sync.core.addingDays
import com.qteb.ranepa.sync.core.dateOnly
import com.qteb.ranepa.sync.core.day
import com.qteb.ranepa.sync.core.hm
import com.qteb.ranepa.sync.core.month
import com.qteb.ranepa.sync.core.monthsGenitive
import com.qteb.ranepa.sync.core.isoWeekday
import com.qteb.ranepa.sync.core.weekdaysLong
import com.qteb.ranepa.sync.system.AlarmReceiver
import com.qteb.ranepa.sync.system.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/** Виджеты получают пары из кэша приложения: сами в ЛК они не ходят. */
object Widgets {
    const val ACTION_TICK = "com.qteb.ranepa.sync.WIDGET_TICK"
    private const val CODE_TICK = 5000
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun update(context: Context) {
        val app = context.applicationContext
        scope.launch {
            NextLessonWidget().updateAll(app)
            LessonsWidget().updateAll(app)
            scheduleTick(app)
        }
    }

    /**
     * «55 мин» система сама не отсчитывает: пока идёт пара или до неё меньше
     * трёх часов, виджеты перерисовываются раз в минуту, иначе — на ближайшей
     * границе пары. Будильник не будит телефон: виджет видно только на экране.
     */
    private suspend fun scheduleTick(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        val count = manager.getGlanceIds(NextLessonWidget::class.java).size + manager.getGlanceIds(LessonsWidget::class.java).size
        val intent = android.app.PendingIntent.getBroadcast(
            context, CODE_TICK, Intent(context, AlarmReceiver::class.java).setAction(ACTION_TICK),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val alarms = context.getSystemService<AlarmManager>() ?: return
        alarms.cancel(intent)
        if (count == 0) return
        val now = Clock.now
        val state = WidgetState(Store(context).cachedLessons(), now)
        val next = if (state.needsMinuteTicks) {
            Instant.ofEpochSecond((now.epochSecond / 60 + 1) * 60)
        } else {
            val marks = state.lessons.filter { !it.cancelled }
                .flatMap { listOf(it.start.minus(countdownWindow), it.start, it.end) }
                .filter { it > now }
            (marks + now.dateOnly.addingDays(1)).min()
        }
        alarms.setWindow(AlarmManager.RTC, next.minus(Clock.shift).toEpochMilli(), 15_000, intent)
    }
}

private fun color(pair: Pair<Long, Long>) = ColorProvider(day = Color(pair.first), night = Color(pair.second))

private val accent = color(KindColors.accent)
private val red = ColorProvider(day = Color(0xFFD70015), night = Color(0xFFFF6961))

private fun openIntent(context: Context, lesson: Lesson?): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        lesson?.let { putExtra(Notifications.EXTRA_LESSON, it.start.minus(Clock.shift).epochSecond) }
    }

@Composable
private fun WidgetFrame(context: Context, lesson: Lesson?, content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(14.dp)
            .clickable(actionStartActivity(openIntent(context, lesson))),
    ) { content() }
}

private fun text(size: Int, weight: FontWeight = FontWeight.Normal, color: GlanceColor, strike: Boolean = false) =
    TextStyle(fontSize = size.sp, fontWeight = weight, color = color,
        textDecoration = if (strike) TextDecoration.LineThrough else null)

@Composable
private fun EmptyLessons(loaded: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Text("Расписание", style = text(13, FontWeight.Bold, accent))
        Text(if (loaded) "Ближайших пар нет" else "Открой приложение, чтобы загрузить пары",
            style = text(12, color = GlanceTheme.colors.onSurfaceVariant))
    }
}

/** Крупно — сколько осталось или время; ниже предмет и аудитория. */
@Composable
private fun LeadBlock(state: WidgetState, lesson: Lesson, modifier: GlanceModifier = GlanceModifier) {
    val t = state.leadText(lesson)
    Column(modifier = modifier.fillMaxHeight()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(lesson.kind.replaceFirstChar { it.uppercase() }, style = text(12, FontWeight.Bold, color(KindColors.text(lesson.kind))),
                maxLines = 1, modifier = GlanceModifier.defaultWeight())
            Text(t.trailing, style = text(12, FontWeight.Medium, GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        Text(t.caption, style = text(13, FontWeight.Bold, accent))
        Text(t.value, style = text(30, FontWeight.Bold, GlanceTheme.colors.onSurface), maxLines = 1)
        Text(lesson.subject, style = text(13, FontWeight.Bold, GlanceTheme.colors.onSurface), maxLines = 2)
        Text(lesson.shortLocation, style = text(12, color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
    }
}

@Composable
private fun Stripe(lesson: Lesson) {
    Box(
        modifier = GlanceModifier.width(3.dp).fillMaxHeight().cornerRadius(2.dp)
            .background(if (lesson.cancelled) ColorProvider(Color(0x80999999), Color(0x80999999)) else color(KindColors.stripe(lesson.kind))),
    ) {}
}

@Composable
private fun CompactRow(lesson: Lesson) {
    Row(modifier = GlanceModifier.fillMaxWidth().height(36.dp)) {
        Stripe(lesson)
        Spacer(modifier = GlanceModifier.width(7.dp))
        Column {
            Text(lesson.subject, maxLines = 1, style = text(13, FontWeight.Bold,
                if (lesson.cancelled) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface, lesson.cancelled))
            Text(if (lesson.cancelled) "${hm(lesson.start)} · отменена" else "${hm(lesson.start)}–${hm(lesson.end)} · ${lesson.shortLocation}",
                maxLines = 1, style = text(11, color = GlanceTheme.colors.onSurfaceVariant))
        }
    }
}

@Composable
private fun BigRow(state: WidgetState, lesson: Lesson) {
    val ongoing = !lesson.cancelled && state.isOngoing(lesson)
    Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(modifier = GlanceModifier.width(42.dp), horizontalAlignment = Alignment.End) {
            Text(hm(lesson.start), style = text(13, FontWeight.Bold, GlanceTheme.colors.onSurface))
            Text(hm(lesson.end), style = text(12, color = GlanceTheme.colors.onSurfaceVariant))
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        Stripe(lesson)
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(lesson.subject, maxLines = 2, style = text(14, FontWeight.Bold,
                if (lesson.cancelled) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface, lesson.cancelled))
            Text(lesson.shortLocation, maxLines = 1, style = text(12, color = GlanceTheme.colors.onSurfaceVariant))
            when {
                lesson.cancelled -> Text("Отменена", style = text(12, FontWeight.Bold, red))
                ongoing -> Text("ещё ${durationLabel(java.time.Duration.between(state.now, lesson.end).seconds)}",
                    style = text(12, FontWeight.Bold, accent))
            }
        }
    }
}

/** «Следующая пара»: сколько осталось до конца пары или до начала следующей. */
class NextLessonWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = WidgetState(Store(context).cachedLessons(), Clock.now)
        provideContent {
            GlanceTheme {
                WidgetFrame(context, state.lead) {
                    val lead = state.lead
                    if (lead != null) LeadBlock(state, lead) else EmptyLessons(state.loaded)
                }
            }
        }
    }
}

/** «Пары»: средний — как «Следующая пара» и что потом, большой — пары на сегодня и завтра. */
class LessonsWidget : GlanceAppWidget() {
    private val medium = DpSize(250.dp, 110.dp)
    private val large = DpSize(250.dp, 260.dp)
    override val sizeMode = SizeMode.Responsive(setOf(medium, large))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = WidgetState(Store(context).cachedLessons(), Clock.now)
        provideContent {
            GlanceTheme {
                WidgetFrame(context, state.lead) {
                    val lead = state.lead
                    when {
                        lead == null -> EmptyLessons(state.loaded)
                        LocalSize.current.height >= large.height -> Large(state, lead)
                        else -> Medium(state, lead)
                    }
                }
            }
        }
    }

    @Composable
    private fun Medium(state: WidgetState, lead: Lesson) {
        val days = state.days(lead)
        val rest = days.firstOrNull()?.second?.filter { it.start > lead.start }.orEmpty()
        val later = if (rest.isEmpty()) days.getOrNull(1) else null
        Row(modifier = GlanceModifier.fillMaxSize()) {
            LeadBlock(state, lead, GlanceModifier.width(140.dp))
            Spacer(modifier = GlanceModifier.width(16.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                when {
                    rest.isNotEmpty() -> {
                        Text("Потом", style = text(13, FontWeight.Bold, GlanceTheme.colors.onSurfaceVariant))
                        Spacer(modifier = GlanceModifier.height(6.dp))
                        rest.take(2).forEach { CompactRow(it) }
                    }
                    later != null -> {
                        Text(state.dayName(later.first), style = text(13, FontWeight.Bold, GlanceTheme.colors.onSurfaceVariant))
                        Spacer(modifier = GlanceModifier.height(6.dp))
                        later.second.take(2).forEach { CompactRow(it) }
                    }
                    else -> Text("Больше пар нет", style = text(13, color = GlanceTheme.colors.onSurfaceVariant))
                }
            }
        }
    }

    @Composable
    private fun Large(state: WidgetState, lead: Lesson) {
        // Строк столько, сколько влезает: заголовок дня и около 56 dp на пару.
        var budget = ((LocalSize.current.height.value - 28) / 56).toInt().coerceAtLeast(2)
        Column(modifier = GlanceModifier.fillMaxSize()) {
            for ((day, lessons) in state.days(lead)) {
                if (budget < 2) break
                val name = state.dayName(day)
                val date = if (name == "Сегодня" || name == "Завтра") {
                    "${weekdaysLong[day.isoWeekday - 1].lowercase()}, ${day.day} ${monthsGenitive[day.month - 1]}"
                } else "${day.day} ${monthsGenitive[day.month - 1]}"
                Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = text(15, FontWeight.Bold, accent), modifier = GlanceModifier.defaultWeight())
                    Text(date, style = text(12, color = GlanceTheme.colors.onSurfaceVariant))
                }
                budget -= 1
                for (l in lessons) {
                    if (budget <= 0) break
                    BigRow(state, l)
                    budget -= 1
                }
            }
        }
    }
}

class NextLessonReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextLessonWidget()
}

class LessonsReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LessonsWidget()
}
