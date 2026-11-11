package it.cerrone.dailyhub.widget

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import it.cerrone.dailyhub.EventInfo
import it.cerrone.dailyhub.MainActivity
import it.cerrone.dailyhub.Prefs
import it.cerrone.dailyhub.R
import it.cerrone.dailyhub.RefreshWorker
import it.cerrone.dailyhub.Sky
import it.cerrone.dailyhub.TaskInfo
import it.cerrone.dailyhub.TodaySnapshot
import it.cerrone.dailyhub.WeatherInfo
import it.cerrone.dailyhub.focusEvent
import it.cerrone.dailyhub.formatTime
import it.cerrone.dailyhub.parseToday
import it.cerrone.dailyhub.skyFor
import java.text.DateFormat
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.Date
import kotlin.math.roundToInt

private val White = ColorProvider(Color(0xFFFFFFFF))
private val Soft = ColorProvider(Color(0xD9FFFFFF))
private val Faint = ColorProvider(Color(0x8CFFFFFF))
private val Warn = ColorProvider(Color(0xFFFFB199))

private const val MAX_EVENTS = 3
private const val MAX_TASKS = 4

class HubWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = Prefs(context)
        val snapshot = prefs.lastSnapshotJson?.let { runCatching { parseToday(it) }.getOrNull() }
        val updated = prefs.lastUpdateMillis
        val error = prefs.lastError
        val configured = prefs.isConfigured
        val now = OffsetDateTime.now()
        provideContent {
            WidgetContent(snapshot, updated, error, configured, now)
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

@DrawableRes
private fun backgroundFor(sky: Sky): Int = when (sky) {
    Sky.CLEAR_DAY -> R.drawable.bg_clear_day
    Sky.CLEAR_NIGHT -> R.drawable.bg_clear_night
    Sky.CLOUDY_DAY -> R.drawable.bg_cloudy_day
    Sky.CLOUDY_NIGHT -> R.drawable.bg_cloudy_night
    Sky.RAIN -> R.drawable.bg_rain
    Sky.STORM -> R.drawable.bg_storm
    Sky.SNOW -> R.drawable.bg_snow
    Sky.FOG -> R.drawable.bg_fog
}

private fun accentFor(sky: Sky): ColorProvider = ColorProvider(
    when (sky) {
        Sky.CLEAR_DAY -> Color(0xFFFFD36B)
        Sky.CLEAR_NIGHT -> Color(0xFFB9C4FF)
        Sky.CLOUDY_DAY, Sky.CLOUDY_NIGHT -> Color(0xFFCFE3FF)
        Sky.RAIN -> Color(0xFF8FD0FF)
        Sky.STORM -> Color(0xFFD2B4FF)
        Sky.SNOW, Sky.FOG -> Color(0xFFFFFFFF)
    },
)

@DrawableRes
private fun weatherIconFor(icon: String, isDay: Boolean): Int = when (icon) {
    "clear" -> if (isDay) R.drawable.ic_wx_clear_day else R.drawable.ic_wx_clear_night
    "partly-cloudy" -> if (isDay) R.drawable.ic_wx_partly_day else R.drawable.ic_wx_partly_night
    "fog" -> R.drawable.ic_wx_fog
    "drizzle", "rain" -> R.drawable.ic_wx_rain
    "storm" -> R.drawable.ic_wx_storm
    "snow" -> R.drawable.ic_wx_snow
    else -> R.drawable.ic_wx_cloudy
}

@Composable
private fun WidgetContent(snapshot: TodaySnapshot?, updated: Long, error: String?, configured: Boolean, now: OffsetDateTime) {
    val weather = snapshot?.weather
    val daytime = LocalTime.now().hour in 7..17
    val sky = skyFor(weather?.icon, weather?.isDay ?: daytime)
    val accent = accentFor(sky)
    val compact = LocalSize.current.height < 190.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(backgroundFor(sky)))
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Header(weather, snapshot?.weatherError, updated, accent)
        Spacer(GlanceModifier.height(10.dp))
        when {
            !configured -> Text("Apri l'app e configura indirizzo e token dell'hub", style = TextStyle(color = Soft, fontSize = 13.sp))
            snapshot == null -> Text(error ?: "In attesa del primo aggiornamento...", style = TextStyle(color = Soft, fontSize = 13.sp))
            else -> Body(snapshot, now, accent, compact)
        }
        if (snapshot != null && error != null) {
            Text("Offline: $error", style = TextStyle(color = Warn, fontSize = 11.sp), maxLines = 1)
        }
    }
}

@Composable
private fun Header(weather: WeatherInfo?, weatherError: String?, updated: Long, accent: ColorProvider) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (weather != null) {
            Image(
                provider = ImageProvider(weatherIconFor(weather.icon, weather.isDay)),
                contentDescription = weather.description,
                modifier = GlanceModifier.size(54.dp),
            )
            Spacer(GlanceModifier.width(10.dp))
        }
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = weather?.temperature?.let { "${it.roundToInt()}°" } ?: "NEXUS",
                style = TextStyle(color = White, fontSize = 40.sp, fontWeight = FontWeight.Normal),
            )
            Text(
                text = weather?.description ?: weatherError ?: "",
                style = TextStyle(color = if (weather != null) Soft else Warn, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (weather?.city ?: "NEXUS").uppercase(),
                style = TextStyle(color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
            if (weather != null) {
                Text(
                    "${weather.min?.roundToInt() ?: "--"}° / ${weather.max?.roundToInt() ?: "--"}°",
                    style = TextStyle(color = Soft, fontSize = 13.sp),
                )
                Text("Pioggia ${weather.precipitationProbability ?: "--"}%", style = TextStyle(color = Faint, fontSize = 11.sp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = GlanceModifier.padding(top = 4.dp).clickable(actionRunCallback<RefreshAction>()),
            ) {
                Image(ImageProvider(R.drawable.ic_refresh), contentDescription = "Aggiorna", modifier = GlanceModifier.size(12.dp))
                if (updated > 0) {
                    Text(
                        " " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(updated)),
                        style = TextStyle(color = Faint, fontSize = 11.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Body(snapshot: TodaySnapshot, now: OffsetDateTime, accent: ColorProvider, compact: Boolean) {
    val focus = focusEvent(snapshot.events, now)
    LazyColumn {
        if (snapshot.calendarError != null) {
            item { Text("Calendario non collegato", style = TextStyle(color = Faint, fontSize = 12.sp)) }
        } else if (focus != null) {
            item { FocusCard(focus.event, focus.ongoing, accent) }
        } else {
            item { Text(if (snapshot.events.isEmpty()) "Agenda libera oggi" else "Impegni di oggi completati", style = TextStyle(color = Soft, fontSize = 13.sp)) }
        }

        if (!compact) {
            val others = snapshot.events.filter { it != focus?.event }.take(MAX_EVENTS)
            if (others.isNotEmpty()) {
                item { SectionTitle("Agenda", accent) }
                others.forEach { event -> item { EventLine(event) } }
            }

            item { SectionTitle(if (snapshot.tasks.isEmpty()) "Task" else "Task · ${snapshot.tasks.size}", accent) }
            if (snapshot.tasks.isEmpty()) {
                item { Text("Tutto fatto", style = TextStyle(color = Faint, fontSize = 13.sp)) }
            }
            snapshot.tasks.take(MAX_TASKS).forEach { task -> item { TaskLine(task, snapshot) } }
            if (snapshot.tasks.size > MAX_TASKS) {
                item { Text("+${snapshot.tasks.size - MAX_TASKS} altri", style = TextStyle(color = Faint, fontSize = 12.sp)) }
            }
        }

        snapshot.nextAlarm?.let { alarm ->
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.padding(top = 8.dp)) {
                    Image(ImageProvider(R.drawable.ic_alarm), contentDescription = "Sveglia", modifier = GlanceModifier.size(13.dp))
                    Text("  Sveglia ${formatTime(alarm)}", style = TextStyle(color = Faint, fontSize = 11.sp))
                }
            }
        }
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
            if (ongoing) "IN CORSO" else "PROSSIMO",
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
        modifier = GlanceModifier.padding(top = 10.dp, bottom = 3.dp),
    )
}

@Composable
private fun EventLine(event: EventInfo) {
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (event.allDay) "Giorno" else event.timeLabel,
            style = TextStyle(color = Soft, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.width(52.dp),
        )
        Text(event.title, style = TextStyle(color = White, fontSize = 13.sp), maxLines = 1)
    }
}

@Composable
private fun TaskLine(task: TaskInfo, snapshot: TodaySnapshot) {
    val due = when {
        task.due == null -> null
        task.overdue -> "Scaduto"
        task.due == snapshot.date -> "Oggi"
        else -> "${task.due.dayOfMonth}/${task.due.monthValue}"
    }
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
                modifier = GlanceModifier
                    .background(ImageProvider(if (task.overdue) R.drawable.chip_warn else R.drawable.chip_soft))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}
