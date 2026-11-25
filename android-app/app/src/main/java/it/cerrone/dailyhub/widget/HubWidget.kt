package it.cerrone.dailyhub.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
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
import it.cerrone.dailyhub.AgendaRow
import it.cerrone.dailyhub.MainActivity
import it.cerrone.dailyhub.Prefs
import it.cerrone.dailyhub.R
import it.cerrone.dailyhub.RefreshWorker
import it.cerrone.dailyhub.SyncTrigger
import it.cerrone.dailyhub.TaskInfo
import it.cerrone.dailyhub.TodaySnapshot
import it.cerrone.dailyhub.agendaSplit
import it.cerrone.dailyhub.dueLabel
import it.cerrone.dailyhub.formatTime
import it.cerrone.dailyhub.parseToday
import it.cerrone.dailyhub.skyAccent
import it.cerrone.dailyhub.skyOf
import it.cerrone.dailyhub.tr
import it.cerrone.dailyhub.weekAgendaRows
import java.text.DateFormat
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.Date

/*
 * Heights in dp used to decide how many whole lines fit. They are slightly generous
 * so the last line never ends up cut by the launcher.
 */
private val PAD_V = 12.dp
private val HEADER_H = 22.dp
private val LINE_H = 21.dp
private val OFFLINE_H = 16.dp
private val GAP_H = 4.dp

/**
 * The next seven days of the agenda and the open tasks, sized for a 3x3 cell and readable when
 * resized. Keeps the original class name so the widget already on the home screen updates in place.
 */
class HubWidget : GlanceAppWidget() {
    /**
     * One layout, drawn at the widget's real size (LocalSize). A list of 27 fixed sizes made the launcher
     * fail to load the widget ("impossibile mostrare il contenuto"), so there is no list any more.
     */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = Prefs(context)
        val snapshot = prefs.lastSnapshotJson?.let { runCatching { parseToday(it) }.getOrNull() }
        val state = AgendaState(
            synced = prefs.lastSyncAt?.let(::formatTime)
                ?: prefs.lastUpdateMillis.takeIf { it > 0 }?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) },
            syncing = SyncTrigger.isManualSyncRunning(prefs),
            error = prefs.lastError,
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

private data class AgendaState(val synced: String?, val syncing: Boolean, val error: String?)

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
    val calendarOk = snapshot != null && snapshot.calendarError == null
    val rows = if (snapshot != null && calendarOk) weekAgendaRows(snapshot, now) else emptyList()

    var space = size.height - PAD_V * 2 - HEADER_H - GAP_H
    if (offline) space -= OFFLINE_H
    val lines = (space.value / LINE_H.value).toInt().coerceAtLeast(0)
    val split = agendaSplit(lines, rows.size, snapshot?.tasks?.size ?: 0)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.bg_widget))
            .padding(horizontal = 14.dp, vertical = PAD_V)
            .clickable(actionStartActivity(Intent(LocalContext.current, MainActivity::class.java))),
    ) {
        Header(state, rows.size, split.events, size.width, accent)
        Spacer(GlanceModifier.height(GAP_H))
        Column(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
            when {
                snapshot == null -> Message(state.error ?: tr("In attesa del primo aggiornamento...", "Waiting for the first update..."))
                !calendarOk -> Message(tr("Calendario non collegato", "Calendar not connected"))
                rows.isEmpty() -> if (split.events > 0) Message(tr("Settimana libera", "Free week"))
                else -> rows.take(split.events).forEach { EventLine(it, size.width, accent) }
            }
            if (snapshot != null && split.tasks > 0) {
                TaskTitle(snapshot.tasks.size, split.tasks, accent)
                if (snapshot.tasks.isEmpty()) Message(tr("Tutto fatto", "All done"))
                snapshot.tasks.take(split.tasks).forEach { TaskLine(it, snapshot) }
            }
        }
        if (offline) {
            Text(tr("Offline: ${state.error}", "Offline: ${state.error}"), style = TextStyle(color = Warn, fontSize = 11.sp), maxLines = 1)
        }
    }
}

/** Section name and how many events are hidden on the left; the sync button on the right. */
@Composable
private fun Header(state: AgendaState, events: Int, shown: Int, width: Dp, accent: ColorProvider) {
    Row(modifier = GlanceModifier.fillMaxWidth().height(HEADER_H), verticalAlignment = Alignment.CenterVertically) {
        val hidden = (events - shown).coerceAtLeast(0)
        val title = if (width >= 200.dp) tr("Agenda · 7 giorni", "Agenda · 7 days") else tr("Agenda", "Agenda")
        Text(
            title.uppercase() + if (hidden > 0) "  +$hidden" else "",
            style = TextStyle(color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier
                .background(ImageProvider(R.drawable.chip_soft))
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .clickable(actionRunCallback<RefreshAction>()),
        ) {
            Image(ImageProvider(R.drawable.ic_refresh), contentDescription = tr("Sincronizza", "Sync"), modifier = GlanceModifier.size(11.dp))
            val label = if (state.syncing) tr("Sync…", "Sync…") else state.synced
            if (label != null) Text(" $label", style = TextStyle(color = Soft, fontSize = 11.sp), maxLines = 1)
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = TextStyle(color = Soft, fontSize = 13.sp), maxLines = 1, modifier = GlanceModifier.height(LINE_H))
}

/** Day (only on its first event), time, title. Narrow widgets keep the same columns, just tighter. */
@Composable
private fun EventLine(row: AgendaRow, width: Dp, accent: ColorProvider) {
    val wide = width >= 200.dp
    Row(modifier = GlanceModifier.fillMaxWidth().height(LINE_H), verticalAlignment = Alignment.CenterVertically) {
        Text(
            row.day.orEmpty(),
            style = TextStyle(color = if (row.day == tr("Oggi", "Today")) accent else Faint, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.width(if (wide) 40.dp else 32.dp),
        )
        Text(
            if (row.event.allDay) "—" else row.event.timeLabel,
            style = TextStyle(color = if (row.ongoing) accent else Soft, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            modifier = GlanceModifier.width(if (wide) 42.dp else 38.dp),
        )
        Text(
            row.event.title,
            style = TextStyle(color = White, fontSize = 13.sp, fontWeight = if (row.ongoing) FontWeight.Bold else FontWeight.Normal),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

@Composable
private fun TaskTitle(total: Int, shown: Int, accent: ColorProvider) {
    val hidden = (total - shown).coerceAtLeast(0)
    val title = if (total == 0) tr("Task", "Tasks") else tr("Task · $total", "Tasks · $total")
    Text(
        title.uppercase() + if (hidden > 0) "  +$hidden" else "",
        style = TextStyle(color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold),
        maxLines = 1,
        modifier = GlanceModifier.height(LINE_H).padding(top = 6.dp),
    )
}

@Composable
private fun TaskLine(task: TaskInfo, snapshot: TodaySnapshot) {
    val due = dueLabel(task, snapshot.date)
    Row(modifier = GlanceModifier.fillMaxWidth().height(LINE_H), verticalAlignment = Alignment.CenterVertically) {
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
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            )
        }
    }
}
