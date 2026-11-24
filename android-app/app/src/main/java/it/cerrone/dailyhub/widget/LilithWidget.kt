package it.cerrone.dailyhub.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import it.cerrone.dailyhub.MainActivity
import it.cerrone.dailyhub.Prefs
import it.cerrone.dailyhub.R
import it.cerrone.dailyhub.tr
import it.cerrone.dailyhub.SportState
import it.cerrone.dailyhub.SportToggleActivity

/** Two big buttons: talk to Lilith, and start or stop sport mode. Narrow sizes show only the icons. */
class LilithWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 50.dp), DpSize(180.dp, 50.dp), DpSize(250.dp, 50.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val configured = true
        provideContent { LilithContent(configured, SportState.active) }
    }
}

class LilithWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LilithWidget()
}

@Composable
private fun LilithContent(configured: Boolean, sport: Boolean) {
    val context = LocalContext.current
    val labels = LocalSize.current.width >= 180.dp
    val voice = Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_VOICE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val toggle = Intent(context, SportToggleActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.bg_widget))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .background(ImageProvider(R.drawable.pill_glass))
                .clickable(actionStartActivity(if (configured) voice else Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))),
        ) {
            Image(ImageProvider(R.drawable.orb), contentDescription = tr("Parla con Lilith", "Talk to Lilith"), modifier = GlanceModifier.size(26.dp))
            if (labels) Text("  Lilith", style = TextStyle(color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.width(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .background(ImageProvider(if (sport) R.drawable.pill_active else R.drawable.pill_glass))
                .clickable(actionStartActivity(toggle)),
        ) {
            Image(
                ImageProvider(R.drawable.ic_sport),
                contentDescription = if (sport) tr("Ferma modalità sport", "Stop sport mode") else tr("Avvia modalità sport", "Start sport mode"),
                modifier = GlanceModifier.size(20.dp),
                colorFilter = ColorFilter.tint(if (sport) Ink else White),
            )
            if (labels) {
                Text(
                    if (sport) tr("  Sport attivo", "  Sport on") else "  Sport",
                    style = TextStyle(color = if (sport) Ink else White, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
        }
    }
}
