package it.cerrone.dailyhub

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class WeatherInfo(
    val city: String,
    val temperature: Double?,
    val description: String,
    val min: Double?,
    val max: Double?,
    val precipitationProbability: Int?,
    val icon: String = "unknown",
    val isDay: Boolean = true,
    val daily: List<DayForecast> = emptyList(),
)

data class DayForecast(
    val date: LocalDate,
    val min: Double,
    val max: Double,
    val precipitationProbability: Int?,
    val description: String,
    val icon: String,
)

/** Calendar words follow the app language, not the phone's. */
private val textLocale: java.util.Locale get() = if (Lang.isEnglish) java.util.Locale.ENGLISH else java.util.Locale.ITALIAN

/** "Oggi" for today, otherwise the short weekday without the trailing dot: "Ven". */
fun dayLabel(date: LocalDate, today: LocalDate): String =
    if (date == today) tr("Oggi", "Today")
    else DateTimeFormatter.ofPattern("EEE", textLocale).format(date).removeSuffix(".").replaceFirstChar { it.uppercase() }

private val MONTHS_SHORT = listOf("gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic")
private val MONTHS_SHORT_EN = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** Compact day and month, same words as the dashboard: "13 nov". */
fun dayMonthLabel(date: LocalDate): String =
    "${date.dayOfMonth} ${(if (Lang.isEnglish) MONTHS_SHORT_EN else MONTHS_SHORT)[date.monthValue - 1]}"

private val WEATHER_EN = mapOf(
    "Sereno" to "Clear", "Prevalentemente sereno" to "Mostly clear", "Parzialmente nuvoloso" to "Partly cloudy",
    "Coperto" to "Overcast", "Nebbia" to "Fog", "Nebbia con brina" to "Rime fog",
    "Pioviggine leggera" to "Light drizzle", "Pioviggine" to "Drizzle", "Pioviggine intensa" to "Heavy drizzle",
    "Pioviggine gelata" to "Freezing drizzle", "Pioviggine gelata intensa" to "Heavy freezing drizzle",
    "Pioggia leggera" to "Light rain", "Pioggia" to "Rain", "Pioggia forte" to "Heavy rain",
    "Pioggia gelata" to "Freezing rain", "Pioggia gelata forte" to "Heavy freezing rain",
    "Neve leggera" to "Light snow", "Neve" to "Snow", "Neve forte" to "Heavy snow", "Nevischio" to "Sleet",
    "Rovesci leggeri" to "Light showers", "Rovesci" to "Showers", "Rovesci violenti" to "Violent showers",
    "Rovesci di neve" to "Snow showers", "Rovesci di neve forti" to "Heavy snow showers",
    "Temporale" to "Thunderstorm", "Temporale con grandine" to "Thunderstorm with hail",
    "Temporale con grandine forte" to "Thunderstorm with heavy hail",
    "Meteo non disponibile" to "Weather unavailable",
)

/** Weather descriptions arrive in Italian (hub and phone); in English they are translated for display. */
fun weatherText(italian: String): String = if (Lang.isEnglish) WEATHER_EN[italian] ?: italian else italian

private val TEMP_STOPS = listOf(
    -5.0 to 0x7CAAFF, 5.0 to 0x7CC7FF, 12.0 to 0x6FE3C4, 19.0 to 0xFFD37A, 27.0 to 0xFF965A,
)

/** Opaque ARGB colour for a temperature, from cold blue to warm orange; matches the dashboard. */
fun tempArgb(celsius: Double): Int {
    fun opaque(rgb: Int) = (0xFF shl 24) or rgb
    if (celsius <= TEMP_STOPS.first().first) return opaque(TEMP_STOPS.first().second)
    for (i in 1 until TEMP_STOPS.size) {
        val (t1, c1) = TEMP_STOPS[i]
        val (t0, c0) = TEMP_STOPS[i - 1]
        if (celsius <= t1) {
            val k = (celsius - t0) / (t1 - t0)
            fun channel(shift: Int): Int {
                val a = (c0 shr shift) and 0xFF
                val b = (c1 shr shift) and 0xFF
                return Math.round(a + (b - a) * k).toInt() shl shift
            }
            return opaque(channel(16) or channel(8) or channel(0))
        }
    }
    return opaque(TEMP_STOPS.last().second)
}

/** Rain chance worth showing next to an icon, as in weather apps: hidden below 20%. */
fun rainLabel(probability: Int?): String? = probability?.takeIf { it >= 20 }?.let { "$it%" }

data class EventInfo(
    val title: String,
    val start: String?,
    val allDay: Boolean,
    val end: String? = null,
    val location: String? = null,
) {
    val timeLabel: String
        get() = if (allDay || start == null) tr("Tutto il giorno", "All day") else formatTime(start)
}

/** One day of the coming week, as returned by `/api/today` in `week`. */
data class DayAgenda(val date: LocalDate, val events: List<EventInfo>)

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

/**
 * Compact countdown until a later event: "Tra 1 h 46 min" ("In 1 h 46 min" in English).
 * Null once the event has started, or when the start cannot be read.
 */
fun countdownLabel(startIso: String?, now: OffsetDateTime): String? {
    val start = parseInstant(startIso) ?: return null
    if (!start.isAfter(now)) return null
    val total = kotlin.math.round(java.time.Duration.between(now, start).toMillis() / 60000.0).toInt().coerceAtLeast(0)
    val lead = tr("Tra", "In")
    if (total < 1) return tr("Tra ora", "In a moment")
    if (total < 60) return "$lead $total min"
    val hours = total / 60
    val minutes = total % 60
    return if (minutes == 0) "$lead $hours h" else "$lead $hours h $minutes min"
}

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

/** One line of the agenda widget. [day] is set only on the first event of each day. */
data class AgendaRow(val day: String?, val event: EventInfo, val ongoing: Boolean)

/** Events from now to six days ahead, in order; today's finished events are left out. */
fun weekAgendaRows(snapshot: TodaySnapshot, now: OffsetDateTime): List<AgendaRow> {
    val today = snapshot.date
    val days = snapshot.week.ifEmpty { listOf(DayAgenda(today, snapshot.events)) }
    val rows = mutableListOf<AgendaRow>()
    for (day in days.sortedBy { it.date }) {
        if (day.date < today || day.date > today.plusDays(6)) continue
        val upcoming = day.events.filter { event ->
            day.date != today || event.allDay || (parseInstant(event.end) ?: parseInstant(event.start))?.isAfter(now) != false
        }
        upcoming.forEachIndexed { index, event ->
            val start = parseInstant(event.start)
            val ongoing = day.date == today && !event.allDay && start != null && !start.isAfter(now)
            rows += AgendaRow(if (index == 0) dayLabel(day.date, today) else null, event, ongoing)
        }
    }
    return rows
}

/** Whole lines given to the agenda and to the task list; [tasks] 0 hides the task section. */
data class AgendaSplit(val events: Int, val tasks: Int)

/**
 * Splits [lines] between the week's events and the tasks, so no row is cut in half.
 * An empty list still takes one line for its message; the task title takes one line.
 * Events get about half first, tasks what they need, and events take whatever is left.
 */
fun agendaSplit(lines: Int, events: Int, tasks: Int): AgendaSplit {
    if (lines <= 0) return AgendaSplit(0, 0)
    val eventNeed = maxOf(events, 1)
    val taskNeed = maxOf(tasks, 1)
    if (lines < 3) return AgendaSplit(minOf(eventNeed, lines), 0)
    val budget = lines - 1
    var shownEvents = minOf(eventNeed, maxOf(1, (budget + 1) / 2))
    val shownTasks = minOf(taskNeed, budget - shownEvents)
    shownEvents += minOf(eventNeed - shownEvents, budget - shownEvents - shownTasks)
    return AgendaSplit(shownEvents, shownTasks)
}

/** A quiet notification to post at [atMillis]; [key] identifies the event across refreshes. */
data class Reminder(val key: String, val title: String, val text: String, val atMillis: Long)

const val REMINDER_LEAD_MINUTES = 15L

/** One reminder per timed event, [leadMinutes] before it starts, only if that moment is still ahead. */
fun reminderPlan(
    events: List<EventInfo>,
    now: OffsetDateTime,
    leadMinutes: Long = REMINDER_LEAD_MINUTES,
): List<Reminder> = events.mapNotNull { event ->
    if (event.allDay) return@mapNotNull null
    val start = parseInstant(event.start) ?: return@mapNotNull null
    val at = start.minusMinutes(leadMinutes)
    if (!at.isAfter(now)) return@mapNotNull null
    val text = buildString {
        append(tr("Tra $leadMinutes minuti · ", "In $leadMinutes minutes · ")).append(formatTime(event.start!!))
        event.end?.let { append(" – ").append(formatTime(it)) }
        event.location?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
    }
    Reminder("${event.title}|${event.start}", event.title, text, at.toInstant().toEpochMilli())
}

/** Title and lines of the silent morning notification. */
fun briefingText(snapshot: TodaySnapshot, now: OffsetDateTime): Pair<String, List<String>> {
    val weather = snapshot.weather
    val title = buildString {
        append(greetingFor(now.hour))
        weather?.temperature?.let { append(" · ${it.roundToInt()}° ") }
        weather?.description?.takeIf { it.isNotBlank() }?.let { append(it.lowercase()) }
    }.trim()
    val lines = buildList {
        if (weather != null) {
            val rain = weather.precipitationProbability
            val range = "${weather.min?.roundToInt() ?: "--"}° / ${weather.max?.roundToInt() ?: "--"}°"
            add(
                if (rain != null && rain >= 50) tr("$range · pioggia al $rain%, meglio l'ombrello", "$range · $rain% chance of rain, take an umbrella")
                else tr("$range · pioggia al ${rain ?: "--"}%", "$range · ${rain ?: "--"}% chance of rain"),
            )
        }
        if (snapshot.calendarError == null) {
            val focus = focusEvent(snapshot.events, now)
            val allDay = snapshot.events.firstOrNull { it.allDay }
            when {
                focus != null -> {
                    val more = snapshot.events.count { !it.allDay && parseInstant(it.end ?: it.start)?.isAfter(now) == true } - 1
                    add("${focus.event.timeLabel} ${focus.event.title}" + if (more > 0) " · " + tr("altri $more", "$more more") else "")
                }
                allDay != null -> add(tr("Tutto il giorno: ${allDay.title}", "All day: ${allDay.title}"))
                else -> add(tr("Agenda libera", "Nothing on the agenda"))
            }
        }
        val dueToday = snapshot.tasks.count { it.due == snapshot.date }
        val overdue = snapshot.tasks.count { it.overdue }
        val bits = listOfNotNull(
            dueToday.takeIf { it > 0 }?.let { tr("$it in scadenza oggi", "$it due today") },
            overdue.takeIf { it > 0 }?.let { tr("$it ${if (it == 1) "scaduto" else "scaduti"}", "$it overdue") },
        )
        add(if (bits.isEmpty()) tr("Nessun task in scadenza", "No tasks due") else "Task: ${bits.joinToString(" · ")}")
    }
    return title to lines
}

data class TaskInfo(
    val id: Long,
    val title: String,
    val due: LocalDate?,
    val overdue: Boolean,
    val done: Boolean,
    val uid: String = "",
)

/** Short due chip text: "Scaduto", "Oggi", "Domani" or day/month. */
fun dueLabel(task: TaskInfo, today: LocalDate): String? = when {
    task.due == null -> null
    task.overdue -> tr("Scaduto", "Overdue")
    task.due == today -> tr("Oggi", "Today")
    task.due == today.plusDays(1) -> tr("Domani", "Tomorrow")
    else -> "${task.due.dayOfMonth}/${task.due.monthValue}"
}

fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> tr("Buongiorno", "Good morning")
    in 12..17 -> tr("Buon pomeriggio", "Good afternoon")
    in 18..22 -> tr("Buonasera", "Good evening")
    else -> tr("Buonanotte", "Good night")
}

/** "Mercoledì 11 novembre" ("Wednesday 11 November" in English). */
fun italianDate(date: LocalDate): String =
    DateTimeFormatter.ofPattern("EEEE d MMMM", textLocale).format(date).replaceFirstChar { it.uppercase() }

/** Reply text prepared for text-to-speech: list markers dropped, line breaks become pauses. */
fun speakable(text: String): String =
    text.replace(Regex("(?m)^\\s*[-•*]\\s*"), "")
        .replace(Regex("([^.:;!?\\s])\\s*\\n+"), "\$1. ")
        .replace(Regex("\\s*\\n+"), " ")
        .trim()

data class TodaySnapshot(
    val date: LocalDate,
    val weather: WeatherInfo?,
    val weatherError: String?,
    val events: List<EventInfo>,
    val calendarError: String?,
    val tasks: List<TaskInfo>,
    val nextAlarm: String?,
    val week: List<DayAgenda> = emptyList(),
    /** Shown when the calendar is the last one received, because the PC did not answer. */
    val calendarNote: String? = null,
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
    id = obj.optLong("id"),
    title = obj.getString("title"),
    due = obj.str("due")?.let(LocalDate::parse),
    overdue = obj.optBoolean("overdue"),
    done = obj.optBoolean("done"),
    uid = obj.optString("uid"),
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

/** One saved conversation in the history list. [source] is "pc", "phone" or "sport". */
data class ChatSummary(val id: String, val title: String, val lastAt: String, val messages: Int, val source: String)

data class StoredMessage(val fromUser: Boolean, val text: String, val actions: List<String>)

fun parseHistory(json: String): List<ChatSummary> = JSONArray(json).mapObjects { o ->
    ChatSummary(o.optString("id"), o.optString("title"), o.optString("lastAt"), o.optInt("messages"), o.optString("source", "pc"))
}.filter { it.id.isNotBlank() }

fun parseStoredMessages(json: String): List<StoredMessage> {
    val messages = JSONObject(json).optJSONArray("messages") ?: return emptyList()
    return messages.mapObjects { o ->
        val actions = o.optJSONArray("actions")
        StoredMessage(
            fromUser = o.optString("role") == "user",
            text = o.optString("text"),
            actions = if (actions == null) emptyList() else (0 until actions.length()).map { actions.getString(it) },
        )
    }
}

fun sourceLabel(source: String): String = when (source) {
    "phone" -> tr("Telefono", "Phone")
    "sport" -> "Sport"
    else -> "PC"
}

/** "14:32" for today, "Ieri", otherwise "12 nov". Dates are compared in the zone of [now]. */
fun historyWhenLabel(iso: String, now: OffsetDateTime): String {
    val at = parseInstant(iso)?.withOffsetSameInstant(now.offset) ?: return ""
    val days = java.time.temporal.ChronoUnit.DAYS.between(at.toLocalDate(), now.toLocalDate())
    return when (days) {
        0L -> at.format(TIME_FORMAT)
        1L -> tr("Ieri", "Yesterday")
        else -> dayMonthLabel(at.toLocalDate())
    }
}

/** FastAPI error bodies look like {"detail": "..."}; validation errors use a list instead of a string. */
fun errorDetail(body: String): String? =
    runCatching { JSONObject(body).opt("detail") as? String }.getOrNull()?.takeIf { it.isNotBlank() }

private fun parseEvent(e: JSONObject) =
    EventInfo(e.optString("title"), e.str("start"), e.optBoolean("allDay"), e.str("end"), e.str("location"))

fun parseToday(json: String): TodaySnapshot {
    val root = JSONObject(json)
    val weather = root.optJSONObject("weather")?.let { w ->
        val current = w.optJSONObject("current") ?: JSONObject()
        val today = w.optJSONObject("today") ?: JSONObject()
        WeatherInfo(
            city = w.optString("city", "Milano"),
            temperature = current.dbl("temperature"),
            description = weatherText(current.str("description") ?: ""),
            min = today.dbl("min"),
            max = today.dbl("max"),
            precipitationProbability = today.dbl("precipitationProbability")?.toInt(),
            icon = current.str("icon") ?: "unknown",
            isDay = current.optBoolean("isDay", true),
            daily = w.optJSONArray("daily").mapObjects { d ->
                val min = d.dbl("min")
                val max = d.dbl("max")
                val date = d.str("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                if (min == null || max == null || date == null) null
                else DayForecast(date, min, max, d.dbl("precipitationProbability")?.toInt(), weatherText(d.str("description") ?: ""), d.str("icon") ?: "unknown")
            }.filterNotNull(),
        )
    }
    return TodaySnapshot(
        date = LocalDate.parse(root.getString("date")),
        weather = weather,
        weatherError = root.str("weatherError")?.let(::weatherText),
        events = root.optJSONArray("events").mapObjects(::parseEvent),
        calendarError = root.str("calendarError"),
        tasks = root.optJSONArray("tasks").mapObjects(::parseTask),
        nextAlarm = root.optJSONObject("alarm")?.str("nextAlarm"),
        week = root.optJSONArray("week").mapObjects { day ->
            val date = day.str("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (date == null) null
            else DayAgenda(date, day.optJSONArray("events").mapObjects(::parseEvent))
        }.filterNotNull(),
        calendarNote = root.str("calendarNote"),
    )
}
