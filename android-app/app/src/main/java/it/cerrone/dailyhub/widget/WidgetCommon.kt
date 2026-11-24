package it.cerrone.dailyhub.widget

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import androidx.glance.unit.ColorProvider
import it.cerrone.dailyhub.R
import it.cerrone.dailyhub.RefreshWorker
import it.cerrone.dailyhub.Sky
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal val White = ColorProvider(Color(0xFFFFFFFF))
internal val Soft = ColorProvider(Color(0xD9FFFFFF))
internal val Faint = ColorProvider(Color(0x8CFFFFFF))
internal val Warn = ColorProvider(Color(0xFFFFB199))
internal val Ink = ColorProvider(Color(0xFF0A1220))

/** The three NEXUS widgets: agenda and tasks, weather, Lilith and sport mode. */
object NexusWidgets {
    suspend fun updateAll(context: Context) {
        val app = context.applicationContext
        runCatching { HubWidget().updateAll(app) }
        runCatching { WeatherWidget().updateAll(app) }
        runCatching { LilithWidget().updateAll(app) }
    }

    /** Redraws every widget from outside a coroutine, e.g. when sport mode or the alarm changes. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { updateAll(app) }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RefreshWorker.refreshNow(context)
    }
}

@DrawableRes
internal fun backgroundFor(sky: Sky): Int = when (sky) {
    Sky.CLEAR_DAY -> R.drawable.bg_clear_day
    Sky.CLEAR_NIGHT -> R.drawable.bg_clear_night
    Sky.CLOUDY_DAY -> R.drawable.bg_cloudy_day
    Sky.CLOUDY_NIGHT -> R.drawable.bg_cloudy_night
    Sky.RAIN -> R.drawable.bg_rain
    Sky.STORM -> R.drawable.bg_storm
    Sky.SNOW -> R.drawable.bg_snow
    Sky.FOG -> R.drawable.bg_fog
}
