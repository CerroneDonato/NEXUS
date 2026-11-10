package it.cerrone.dailyhub

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

data class WeatherInfo(
    val city: String,
    val temperature: Double?,
    val description: String,
    val min: Double?,
    val max: Double?,
    val precipitationProbability: Int?,
)

data class EventInfo(val title: String, val start: String?, val allDay: Boolean) {
    val timeLabel: String
        get() = if (allDay || start == null) "Tutto il giorno" else formatTime(start)
}

data class TaskInfo(val id: Long, val title: String, val due: LocalDate?, val overdue: Boolean, val done: Boolean)

data class TodaySnapshot(
    val date: LocalDate,
    val weather: WeatherInfo?,
    val weatherError: String?,
    val events: List<EventInfo>,
    val calendarError: String?,
    val tasks: List<TaskInfo>,
    val nextAlarm: String?,
)

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun formatTime(isoDateTime: String): String =
    runCatching { OffsetDateTime.parse(isoDateTime).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(TIME_FORMAT) }
        .getOrDefault(isoDateTime)

/** org.json returns the string "null" for JSON nulls with optString; this does not. */
fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key)

fun JSONObject.dbl(key: String): Double? = if (isNull(key)) null else optDouble(key)

private inline fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).map { transform(getJSONObject(it)) }
}

fun parseTask(obj: JSONObject) = TaskInfo(
    id = obj.getLong("id"),
    title = obj.getString("title"),
    due = obj.str("due")?.let(LocalDate::parse),
    overdue = obj.optBoolean("overdue"),
    done = obj.optBoolean("done"),
)

fun parseTasks(json: String): List<TaskInfo> = JSONArray(json).mapObjects(::parseTask)

fun parseToday(json: String): TodaySnapshot {
    val root = JSONObject(json)
    val weather = root.optJSONObject("weather")?.let { w ->
        val current = w.optJSONObject("current") ?: JSONObject()
        val today = w.optJSONObject("today") ?: JSONObject()
        WeatherInfo(
            city = w.optString("city", "Milano"),
            temperature = current.dbl("temperature"),
            description = current.str("description") ?: "",
            min = today.dbl("min"),
            max = today.dbl("max"),
            precipitationProbability = today.dbl("precipitationProbability")?.toInt(),
        )
    }
    return TodaySnapshot(
        date = LocalDate.parse(root.getString("date")),
        weather = weather,
        weatherError = root.str("weatherError"),
        events = root.optJSONArray("events").mapObjects { e ->
            EventInfo(e.optString("title"), e.str("start"), e.optBoolean("allDay"))
        },
        calendarError = root.str("calendarError"),
        tasks = root.optJSONArray("tasks").mapObjects(::parseTask),
        nextAlarm = root.optJSONObject("alarm")?.str("nextAlarm"),
    )
}
