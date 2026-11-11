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
    val icon: String = "unknown",
    val isDay: Boolean = true,
)

data class EventInfo(
    val title: String,
    val start: String?,
    val allDay: Boolean,
    val end: String? = null,
    val location: String? = null,
) {
    val timeLabel: String
        get() = if (allDay || start == null) "Tutto il giorno" else formatTime(start)
}

/** Widget background family, derived from the server's weather icon key. */
enum class Sky { CLEAR_DAY, CLEAR_NIGHT, CLOUDY_DAY, CLOUDY_NIGHT, RAIN, STORM, SNOW, FOG }

fun skyFor(icon: String?, isDay: Boolean): Sky = when (icon) {
    "clear", "partly-cloudy" -> if (isDay) Sky.CLEAR_DAY else Sky.CLEAR_NIGHT
    "drizzle", "rain" -> Sky.RAIN
    "storm" -> Sky.STORM
    "snow" -> Sky.SNOW
    "fog" -> Sky.FOG
    else -> if (isDay) Sky.CLOUDY_DAY else Sky.CLOUDY_NIGHT
}

data class FocusEvent(val event: EventInfo, val ongoing: Boolean)

private fun parseInstant(iso: String?): OffsetDateTime? = iso?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

/** The event happening now, otherwise the next one starting later today. All-day events are ignored. */
fun focusEvent(events: List<EventInfo>, now: OffsetDateTime): FocusEvent? {
    for (event in events) {
        if (event.allDay) continue
        val start = parseInstant(event.start) ?: continue
        val end = parseInstant(event.end) ?: start
        if (end.isAfter(now)) return FocusEvent(event, ongoing = !start.isAfter(now))
    }
    return null
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

data class AssistantInfo(val name: String, val enabled: Boolean)

data class ChatResult(val reply: String, val actions: List<String>)

fun parseAssistantInfo(json: String): AssistantInfo {
    val root = JSONObject(json)
    return AssistantInfo(root.optString("name", "Lilith"), root.optBoolean("enabled"))
}

fun parseChatResult(json: String): ChatResult {
    val root = JSONObject(json)
    val actions = root.optJSONArray("actions")
    return ChatResult(
        reply = root.optString("reply"),
        actions = if (actions == null) emptyList() else (0 until actions.length()).map { actions.getString(it) },
    )
}

/** FastAPI error bodies look like {"detail": "..."}; validation errors use a list instead of a string. */
fun errorDetail(body: String): String? =
    runCatching { JSONObject(body).opt("detail") as? String }.getOrNull()?.takeIf { it.isNotBlank() }

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
            icon = current.str("icon") ?: "unknown",
            isDay = current.optBoolean("isDay", true),
        )
    }
    return TodaySnapshot(
        date = LocalDate.parse(root.getString("date")),
        weather = weather,
        weatherError = root.str("weatherError"),
        events = root.optJSONArray("events").mapObjects { e ->
            EventInfo(e.optString("title"), e.str("start"), e.optBoolean("allDay"), e.str("end"), e.str("location"))
        },
        calendarError = root.str("calendarError"),
        tasks = root.optJSONArray("tasks").mapObjects(::parseTask),
        nextAlarm = root.optJSONObject("alarm")?.str("nextAlarm"),
    )
}
