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
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import it.cerrone.dailyhub.DayForecast
import it.cerrone.dailyhub.MainActivity
import it.cerrone.dailyhub.Prefs
import it.cerrone.dailyhub.R
import it.cerrone.dailyhub.tr
import it.cerrone.dailyhub.RefreshWorker
import it.cerrone.dailyhub.TodaySnapshot
import it.cerrone.dailyhub.WeatherInfo
import it.cerrone.dailyhub.dayLabel
import it.cerrone.dailyhub.parseToday
import it.cerrone.dailyhub.skyAccent
import it.cerrone.dailyhub.skyOf
import it.cerrone.dailyhub.weatherIcon
import java.time.LocalTime
import kotlin.math.roundToInt

/** Current weather; the next days appear when the widget is wide and tall enough. */
class WeatherWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp),
            DpSize(180.dp, 110.dp),
            DpSize(250.dp, 110.dp),
            DpSize(250.dp, 180.dp),
            DpSize(320.dp, 180.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = Prefs(context).lastSnapshotJson?.let { runCatching { parseToday(it) }.getOrNull() }
        provideContent { WeatherContent(snapshot) }
    }
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedulePeriodic(context)
        RefreshWorker.refreshNow(context)
    }
}

@Composable
private fun WeatherContent(snapshot: TodaySnapshot?) {
    val sky = skyOf(snapshot, LocalTime.now().hour)
    val accent = ColorProvider(skyAccent(sky))
    val size = LocalSize.current
    val weather = snapshot?.weather
    val days = snapshot?.weather?.daily.orEmpty()
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(backgroundFor(sky)))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .clickable(actionStartActivity(Intent(LocalContext.current, MainActivity::class.java))),
    ) {
        when {
            weather == null -> {
                Text("NEXUS", style = TextStyle(color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                Text(snapshot?.weatherError ?: tr("In attesa del meteo...", "Waiting for the weather..."), style = TextStyle(color = Soft, fontSize = 13.sp), maxLines = 3)
            }
            size.width < 180.dp -> Compact(weather, accent)
            else -> {
                Wide(weather, accent)
                if (size.height >= 180.dp && days.size > 1 && snapshot != null) {
                    Spacer(GlanceModifier.defaultWeight())
                    DayStrip(days.take(if (size.width >= 300.dp) 5 else 4), snapshot.date)
                }
            }
        }
    }
}

/** Square 2x2: city, temperature, icon and range stacked like the iPhone weather widget. */
@Composable
private fun ColumnScope.Compact(weather: WeatherInfo, accent: ColorProvider) {
    Text(weather.city.uppercase(), style = TextStyle(color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    Text("${weather.temperature?.roundToInt() ?: "--"}°", style = TextStyle(color = White, fontSize = 40.sp))
    Spacer(GlanceModifier.defaultWeight())
    Image(ImageProvider(weatherIcon(weather.icon, weather.isDay)), contentDescription = weather.description, modifier = GlanceModifier.size(26.dp))
    Text(weather.description, style = TextStyle(color = Soft, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    Text("${weather.min?.roundToInt() ?: "--"}° / ${weather.max?.roundToInt() ?: "--"}°", style = TextStyle(color = Faint, fontSize = 12.sp))
}

@Composable
private fun Wide(weather: WeatherInfo, accent: ColorProvider) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(weatherIcon(weather.icon, weather.isDay)),
            contentDescription = weather.description,
            modifier = GlanceModifier.size(56.dp),
        )
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text("${weather.temperature?.roundToInt() ?: "--"}°", style = TextStyle(color = White, fontSize = 42.sp))
            Text(weather.description, style = TextStyle(color = Soft, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(weather.city.uppercase(), style = TextStyle(color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            Text(
                "${weather.min?.roundToInt() ?: "--"}° / ${weather.max?.roundToInt() ?: "--"}°",
                style = TextStyle(color = Soft, fontSize = 13.sp),
            )
            Text(tr("Pioggia ${weather.precipitationProbability ?: "--"}%", "Rain ${weather.precipitationProbability ?: "--"}%"), style = TextStyle(color = Faint, fontSize = 11.sp))
        }
    }
}

/** The next days side by side: weekday, icon, high and low. */
@Composable
private fun DayStrip(days: List<DayForecast>, today: java.time.LocalDate) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(R.drawable.card_glass))
            .padding(horizontal = 6.dp, vertical = 7.dp),
    ) {
        days.forEach { day ->
            Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dayLabel(day.date, today), style = TextStyle(color = Soft, fontSize = 11.sp, fontWeight = FontWeight.Medium))
                Image(ImageProvider(weatherIcon(day.icon, true)), contentDescription = day.description, modifier = GlanceModifier.size(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${day.max.roundToInt()}°", style = TextStyle(color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    Text(" ${day.min.roundToInt()}°", style = TextStyle(color = Faint, fontSize = 12.sp))
                }
            }
        }
    }
}
