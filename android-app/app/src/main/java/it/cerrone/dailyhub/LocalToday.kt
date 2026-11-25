package it.cerrone.dailyhub

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Builds the same snapshot the widget already reads, from the phone instead of the hub. */
object LocalToday {
    suspend fun refresh(context: Context): String = withContext(Dispatchers.IO) {
        val prefs = Prefs(context)
        val today = LocalDate.now()
        var weather: JSONObject? = null
        var weatherError: String? = null
        try {
            weather = WeatherClient.fetch(prefs)
        } catch (e: Exception) {
            weatherError = tr("Meteo non disponibile", "Weather unavailable")
            // Keep showing the last forecast we had instead of an empty card.
            weather = prefs.lastSnapshotJson?.let { runCatching { JSONObject(it).optJSONObject("weather") }.getOrNull() }
        }
        val previous = prefs.lastSnapshotJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        val calendar = calendarPart(prefs, previous, today)
        val tasks = tasksJson(context, today)
        val json = JSONObject()
            .put("date", today.toString())
            .put("weather", weather ?: JSONObject.NULL)
            .put("weatherError", weatherError ?: JSONObject.NULL)
            .put("events", calendar.events)
            .put("calendarError", calendar.error ?: JSONObject.NULL)
            .put("calendarNote", calendar.note ?: JSONObject.NULL)
            .put("tasks", tasks)
            .put("alarm", JSONObject().put("nextAlarm", AlarmSync.nextAlarmIso(context) ?: JSONObject.NULL))
            .put("week", calendar.week ?: JSONObject.NULL)
            .toString()
        prefs.lastSnapshotJson = json
        prefs.lastUpdateMillis = System.currentTimeMillis()
        prefs.lastError = null
        json
    }

    /** The calendar comes from the PC; when the PC does not answer, the last week received is kept. */
    private suspend fun calendarPart(prefs: Prefs, previous: JSONObject?, today: LocalDate): CalendarPart {
        if (!prefs.canSync) return HubCalendar.notConnected()
        return try {
            HubCalendar.fromHub(JSONObject(HubClient(prefs.hubUrl, prefs.token).today()), today)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            HubCalendar.fromLastSnapshot(previous, today)
        }
    }

    /**
     * Rewrites only the task list of the last snapshot, from the local database: instant and offline.
     * Used right after a task changes, when fetching weather and calendar again would only add delay.
     */
    suspend fun refreshTasks(context: Context): String? = withContext(Dispatchers.IO) {
        val prefs = Prefs(context)
        val stored = prefs.lastSnapshotJson ?: return@withContext null
        val json = runCatching { JSONObject(stored) }.getOrNull() ?: return@withContext null
        json.put("tasks", tasksJson(context, LocalDate.parse(json.optString("date", LocalDate.now().toString()))))
        val updated = json.toString()
        prefs.lastSnapshotJson = updated
        updated
    }

    private fun tasksJson(context: Context, today: LocalDate): JSONArray {
        val tasks = JSONArray()
        for (task in LocalDb.get(context).openTasks(today)) {
            tasks.put(
                JSONObject()
                    .put("id", task.id)
                    .put("uid", task.uid)
                    .put("title", task.title)
                    .put("due", task.due?.toString() ?: JSONObject.NULL)
                    .put("overdue", task.overdue)
                    .put("done", task.done)
            )
        }
        return tasks
    }
}
