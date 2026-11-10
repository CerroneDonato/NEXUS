package it.cerrone.dailyhub.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import it.cerrone.dailyhub.MainActivity
import it.cerrone.dailyhub.Prefs
import it.cerrone.dailyhub.RefreshWorker
import it.cerrone.dailyhub.TodaySnapshot
import it.cerrone.dailyhub.formatTime
import it.cerrone.dailyhub.parseToday
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private val Background = Color(0xFF182033)
private val TextColor = ColorProvider(Color(0xFFE8ECF4))
private val Muted = ColorProvider(Color(0xFF8A95AB))
private val Accent = ColorProvider(Color(0xFF5FB3FF))
private val Warn = ColorProvider(Color(0xFFFF8A65))

private const val MAX_EVENTS = 4
private const val MAX_TASKS = 5

class HubWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = Prefs(context)
        val snapshot = prefs.lastSnapshotJson?.let { runCatching { parseToday(it) }.getOrNull() }
        val updated = prefs.lastUpdateMillis
        val error = prefs.lastError
        val configured = prefs.isConfigured
        provideContent {
            WidgetContent(snapshot, updated, error, configured)
        }
    }
}

class HubWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HubWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedulePeriodic(context)
        RefreshWorker.refreshNow(context)
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RefreshWorker.refreshNow(context)
    }
}

@Composable
private fun WidgetContent(snapshot: TodaySnapshot?, updated: Long, error: String?, configured: Boolean) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Background)
            .cornerRadius(20.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Header(snapshot, updated)
        Spacer(GlanceModifier.height(6.dp))
        when {
            !configured -> Text("Apri l'app e configura indirizzo e token dell'hub", style = TextStyle(color = Muted))
            snapshot == null -> Text(error ?: "In attesa del primo aggiornamento...", style = TextStyle(color = Muted))
            else -> Body(snapshot)
        }
        if (snapshot != null && error != null) {
            Text("Offline: $error", style = TextStyle(color = Warn, fontSize = 11.sp))
        }
    }
}

@Composable
private fun Header(snapshot: TodaySnapshot?, updated: Long) {
    val weather = snapshot?.weather
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = if (weather != null) "${weather.city} ${weather.temperature?.roundToInt() ?: "--"}°" else "Daily Hub",
                style = TextStyle(color = TextColor, fontSize = 20.sp, fontWeight = FontWeight.Bold),
            )
            if (weather != null) {
                Text(
                    text = "${weather.description} · ${weather.min?.roundToInt()}°/${weather.max?.roundToInt()}° · " +
                        "pioggia ${weather.precipitationProbability ?: "--"}%",
                    style = TextStyle(color = Muted, fontSize = 12.sp),
                )
            } else if (snapshot?.weatherError != null) {
                Text(snapshot.weatherError, style = TextStyle(color = Warn, fontSize = 12.sp))
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Aggiorna",
                style = TextStyle(color = Accent, fontSize = 12.sp),
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()),
            )
            if (updated > 0) {
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(updated)),
                    style = TextStyle(color = Muted, fontSize = 11.sp),
                )
            }
        }
    }
}

@Composable
private fun Body(snapshot: TodaySnapshot) {
    LazyColumn {
        item { SectionTitle("Oggi") }
        if (snapshot.calendarError != null) {
            item { Text(snapshot.calendarError, style = TextStyle(color = Warn, fontSize = 12.sp)) }
        } else if (snapshot.events.isEmpty()) {
            item { Text("Nessun evento", style = TextStyle(color = Muted, fontSize = 13.sp)) }
        }
        snapshot.events.take(MAX_EVENTS).forEach { event ->
            item { Line(event.timeLabel, event.title, Accent) }
        }

        item { SectionTitle("Task") }
        if (snapshot.tasks.isEmpty()) {
            item { Text("Nessun task aperto", style = TextStyle(color = Muted, fontSize = 13.sp)) }
        }
        snapshot.tasks.take(MAX_TASKS).forEach { task ->
            val dueLabel = when {
                task.due == null -> ""
                task.overdue -> "scaduto"
                task.due == snapshot.date -> "oggi"
                else -> "${task.due.dayOfMonth}/${task.due.monthValue}"
            }
            item { Line(dueLabel, task.title, if (task.overdue) Warn else Muted) }
        }
        if (snapshot.tasks.size > MAX_TASKS) {
            item { Text("+${snapshot.tasks.size - MAX_TASKS} altri", style = TextStyle(color = Muted, fontSize = 12.sp)) }
        }
        snapshot.nextAlarm?.let { alarm ->
            item {
                Text("Sveglia ${formatTime(alarm)}", style = TextStyle(color = Muted, fontSize = 11.sp),
                    modifier = GlanceModifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = TextStyle(color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        modifier = GlanceModifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun Line(label: String, text: String, labelColor: ColorProvider) {
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = TextStyle(color = labelColor, fontSize = 13.sp), modifier = GlanceModifier.width(64.dp))
        Text(text, style = TextStyle(color = TextColor, fontSize = 13.sp), maxLines = 1)
    }
}
