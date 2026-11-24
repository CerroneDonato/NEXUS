package it.cerrone.dailyhub.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import it.cerrone.dailyhub.AlarmSync
import it.cerrone.dailyhub.EventInfo
import it.cerrone.dailyhub.MainActivity
import it.cerrone.dailyhub.Prefs
import it.cerrone.dailyhub.R
import it.cerrone.dailyhub.tr
import it.cerrone.dailyhub.RefreshWorker
import it.cerrone.dailyhub.TaskInfo
import it.cerrone.dailyhub.TodaySnapshot
import it.cerrone.dailyhub.dueLabel
import it.cerrone.dailyhub.focusEvent
import it.cerrone.dailyhub.formatTime
import it.cerrone.dailyhub.italianDate
import it.cerrone.dailyhub.parseToday
import it.cerrone.dailyhub.skyAccent
import it.cerrone.dailyhub.skyOf
import it.cerrone.dailyhub.widgetRows
import java.text.DateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.Date

/*
 * Heights in dp used to decide how many whole lines fit. They are slightly generous
 * so the last line never ends up cut by the launcher.
 */
private val PAD_V = 14.dp
private val HEADER_H = 42.dp
private val FOCUS_CARD_H = 72.dp
private val LINE_H = 23.dp
private val OFFLINE_H = 16.dp
private val GAP_H = 10.dp

/**
 * Agenda and tasks of today. Keeps the original class name so the widget already on the home screen
 * becomes this one after the update instead of disappearing.
 */
class HubWidget : GlanceAppWidget() {
    /** The launcher picks the largest layout that fits, so the content always matches the real size. */
    override val sizeMode: SizeMode = SizeMode.Responsive(
        listOf(110.dp, 180.dp, 250.dp, 320.dp, 400.dp, 480.dp, 560.dp, 660.dp)
            .flatMap { h -> listOf(DpSize(200.dp, h), DpSize(300.dp, h)) }
            .toSet(),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = Prefs(context)
        val snapshot = prefs.lastSnapshotJson?.let { runCatching { parseToday(it) }.getOrNull() }
        val state = AgendaState(
            updated = prefs.lastUpdateMillis,
            error = prefs.lastError,
            configured = true,
            alarm = AlarmSync.nextAlarmIso(context),
        )
        val now = OffsetDateTime.now()
        provideContent {
            AgendaContent(snapshot, state, now)
        }
    }

    companion object {
        /** Redraws every NEXUS widget, for state that changes outside a refresh (sport mode, alarm). */
        fun refresh(context: Context) = NexusWidgets.refresh(context)
    }
}

private data class AgendaState(
    val updated: Long,
    val error: String?,
    val configured: Boolean,
    val alarm: String?,
)

class HubWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HubWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedulePeriodic(context)
        RefreshWorker.refreshNow(context)
    }
}

@Composable
private fun AgendaContent(snapshot: TodaySnapshot?, state: AgendaState, now: OffsetDateTime) {
    val accent = ColorProvider(skyAccent(skyOf(snapshot, LocalTime.now().hour)))
    val size = LocalSize.current
    val offline = snapshot != null && state.error != null
    val focus = snapshot?.takeIf { it.calendarError == null }?.let { focusEvent(it.events, now) }

    var space = size.height - PAD_V * 2 - HEADER_H - GAP_H
    if (offline) space -= OFFLINE_H
    val focusAsCard = focus != null && space >= FOCUS_CARD_H + LINE_H * 2
    space -= if (focusAsCard) FOCUS_CARD_H else LINE_H
    val lines = (space.value / LINE_H.value).toInt().coerceAtLeast(0)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.bg_widget))
            .padding(horizontal = 16.dp, vertical = PAD_V)
            .clickable(actionStartActivity(Intent(LocalContext.current, MainActivity::class.java))),
    ) {
        AgendaHeader(snapshot?.date ?: LocalDate.now(), state, accent)
        Spacer(GlanceModifier.height(GAP_H))
        Column(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
            when {
                !state.configured -> Text(tr("Apri l'app e configura indirizzo e token dell'hub", "Open the app and set the hub address and token"), style = TextStyle(color = Soft, fontSize = 13.sp), maxLines = 2)
                snapshot == null -> Text(state.error ?: tr("In attesa del primo aggiornamento...", "Waiting for the first update..."), style = TextStyle(color = Soft, fontSize = 13.sp), maxLines = 2)
                else -> Body(snapshot, focus?.event, focus?.ongoing == true, focusAsCard, lines, accent)
            }
        }
        if (offline) {
            Text(tr("Offline: ${state.error}", "Offline: ${state.error}"), style = TextStyle(color = Warn, fontSize = 11.sp), maxLines = 1)
        }
    }
}

/** "OGGI" and the date on the left; next alarm and last update (tap to refresh) on the right. */
@Composable
private fun AgendaHeader(date: LocalDate, state: AgendaState, accent: ColorProvider) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text("OGGI", style = TextStyle(color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold))
            Text(italianDate(date), style = TextStyle(color = White, fontSize = 17.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>())) {
            if (state.alarm != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(R.drawable.ic_alarm), contentDescription = tr("Sveglia", "Alarm"), modifier = GlanceModifier.size(12.dp))
                    Text(" ${formatTime(state.alarm)}", style = TextStyle(color = Soft, fontSize = 12.sp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(R.drawable.ic_refresh), contentDescription = tr("Aggiorna", "Refresh"), modifier = GlanceModifier.size(11.dp))
                if (state.updated > 0) {
                    Text(
                        " " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.updated)),
                        style = TextStyle(color = Faint, fontSize = 11.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Body(
    snapshot: TodaySnapshot,
    focus: EventInfo?,
    ongoing: Boolean,
    focusAsCard: Boolean,
    lines: Int,
    accent: ColorProvider,
) {
    when {
        snapshot.calendarError != null -> Text(tr("Calendario non collegato", "Calendar not connected"), style = TextStyle(color = Faint, fontSize = 12.sp), maxLines = 1)
        focus != null && focusAsCard -> FocusCard(focus, ongoing, accent)
        focus != null -> EventLine(focus, highlight = accent)
        else -> Text(
            if (snapshot.events.isEmpty()) tr("Agenda libera oggi", "Nothing on the agenda today") else tr("Impegni di oggi completati", "Today's events are over"),
            style = TextStyle(color = Soft, fontSize = 13.sp),
            maxLines = 1,
        )
    }

    val others = snapshot.events.filter { it != focus }
    val rows = widgetRows(lines, others.size, snapshot.tasks.size)
    if (rows.events > 0) {
        SectionTitle(tr("Agenda", "Agenda"), accent)
        others.take(rows.events).forEach { EventLine(it) }
    }
    if (rows.showTasks) {
        SectionTitle(if (snapshot.tasks.isEmpty()) tr("Task", "Tasks") else tr("Task · ${snapshot.tasks.size}", "Tasks · ${snapshot.tasks.size}"), accent)
        if (snapshot.tasks.isEmpty()) {
            Text(tr("Tutto fatto", "All done"), style = TextStyle(color = Faint, fontSize = 13.sp), maxLines = 1)
        }
        snapshot.tasks.take(rows.tasks).forEach { TaskLine(it, snapshot) }
    }
}

@Composable
private fun FocusCard(event: EventInfo, ongoing: Boolean, accent: ColorProvider) {
    val range = buildString {
        append(event.timeLabel)
        event.end?.let { append(" – ").append(formatTime(it)) }
        event.location?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
    }
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(R.drawable.card_glass))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(
            if (ongoing) tr("IN CORSO", "NOW") else tr("PROSSIMO", "NEXT"),
            style = TextStyle(color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold),
        )
        Text(event.title, style = TextStyle(color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(range, style = TextStyle(color = Soft, fontSize = 12.sp), maxLines = 1)
    }
}

@Composable
private fun SectionTitle(title: String, accent: ColorProvider) {
    Text(
        title.uppercase(),
        style = TextStyle(color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold),
        maxLines = 1,
        modifier = GlanceModifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun EventLine(event: EventInfo, highlight: ColorProvider? = null) {
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (event.allDay) tr("Giorno", "Day") else event.timeLabel,
            style = TextStyle(color = highlight ?: Soft, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.width(52.dp),
        )
        Text(event.title, style = TextStyle(color = White, fontSize = 13.sp), maxLines = 1)
    }
}

@Composable
private fun TaskLine(task: TaskInfo, snapshot: TodaySnapshot) {
    val due = dueLabel(task, snapshot.date)
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("○  ", style = TextStyle(color = if (task.overdue) Warn else Faint, fontSize = 13.sp))
        Text(
            task.title,
            style = TextStyle(color = White, fontSize = 13.sp),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        if (due != null) {
            Text(
                due,
                style = TextStyle(color = if (task.overdue) Warn else Soft, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                modifier = GlanceModifier
                    .background(ImageProvider(if (task.overdue) R.drawable.chip_warn else R.drawable.chip_soft))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}
