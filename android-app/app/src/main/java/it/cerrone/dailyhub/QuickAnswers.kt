package it.cerrone.dailyhub

import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.math.roundToInt

/**
 * Same everyday answers as the PC (hub-server/app/quick.py): weather, agenda, tasks,
 * the day as a whole, the alarm. Anything that changes data, or names another day, goes to Gemini.
 */
object QuickAnswers {
    private const val MAX_WORDS = 10
    private val action = setOf(
        "aggiungi", "aggiungere", "crea", "creare", "segna", "completa", "completato", "finito", "fatto",
        "sposta", "ricorda", "ricordami", "cancella", "elimina", "modifica", "rinomina", "togli", "metti",
        "imposta", "scrivi", "manda", "chiama", "rimanda", "annulla",
    )
    private val otherDay = setOf(
        "lunedì", "lunedi", "martedì", "martedi", "mercoledì", "mercoledi", "giovedì", "giovedi",
        "venerdì", "venerdi", "sabato", "domenica", "settimana", "weekend", "dopodomani", "ieri",
        "prossimo", "prossima", "prossimi", "prossime", "mese",
    )
    private val weatherWords = setOf("meteo", "piove", "pioverà", "pioggia", "temperatura", "gradi", "nevica", "neve")
    private val agendaWords = setOf("impegni", "impegno", "agenda", "appuntamenti", "appuntamento", "eventi", "riunioni", "calendario")
    private val taskWords = setOf("task", "todo", "scadenza", "scadenze")
    private val dayWords = setOf("programma", "fare")
    private val weekdays = listOf("lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato", "domenica")
    private val word = Regex("[a-zàèéìòù0-9]+")
    private val leadingNote = Regex("^\\s*\\([^)]*\\)\\s*")

    fun answer(message: String, tools: PhoneTools, now: OffsetDateTime): String? {
        val words = word.findAll(leadingNote.replace(message.lowercase(), "")).map { it.value }.toList()
        if (words.isEmpty() || words.size > MAX_WORDS) return null
        val vocabulary = words.toSet()
        if (vocabulary.intersect(action).isNotEmpty() || vocabulary.intersect(otherDay).isNotEmpty() || words.any { it.all(Char::isDigit) }) {
            return null
        }
        val today = now.toLocalDate()
        val tomorrow = "domani" in vocabulary
        val day = if (tomorrow) today.plusDays(1) else today
        if ("ombrello" in vocabulary) return umbrella(tools, day, tomorrow)
        if (vocabulary.intersect(weatherWords).isNotEmpty() || ("tempo" in vocabulary && vocabulary.intersect(setOf("che", "fa", "farà")).isNotEmpty())) {
            return weather(tools, day, tomorrow)
        }
        if (vocabulary.intersect(taskWords).isNotEmpty()) return tasks(tools, today)
        if (vocabulary.intersect(agendaWords).isNotEmpty()) return agenda(tools, day, tomorrow)
        if (vocabulary.intersect(dayWords).isNotEmpty() || hasPair(words, "cosa", "ho")) return dayOverview(tools, day, tomorrow, today)
        if ("sveglia" in vocabulary) return alarm(tools, today)
        return null
    }

    private fun hasPair(words: List<String>, first: String, second: String) =
        words.zipWithNext().any { (a, b) -> a == first && b == second }

    private fun forecast(weather: JSONObject, day: LocalDate, tomorrow: Boolean): JSONObject? {
        if (!tomorrow) return weather.optJSONObject("today")
        val daily = weather.optJSONArray("daily") ?: return null
        for (index in 0 until daily.length()) {
            val item = daily.optJSONObject(index) ?: continue
            if (item.optString("date") == day.toString()) return item
        }
        return null
    }

    private fun weather(tools: PhoneTools, day: LocalDate, tomorrow: Boolean): String? {
        val data = tools.call("get_weather", JSONObject()).data
        if (data.has("error")) return null
        val dayForecast = forecast(data, day, tomorrow)
        if (tomorrow) {
            if (dayForecast == null || dayForecast.isNull("min") || dayForecast.isNull("max")) return null
            val rain = if (dayForecast.isNull("precipitationProbability")) null else dayForecast.optInt("precipitationProbability")
            val reply = "Domani ${dayForecast.optString("description").lowercase()}, tra ${deg(dayForecast.getDouble("min"))}° e ${deg(dayForecast.getDouble("max"))}°"
            return if (rain == null) "$reply." else "$reply, pioggia al $rain%."
        }
        val current = data.optJSONObject("current") ?: return null
        if (current.isNull("temperature")) return null
        var reply = "Adesso ${deg(current.getDouble("temperature"))}°, ${current.optString("description").lowercase()}."
        if (dayForecast != null && !dayForecast.isNull("min") && !dayForecast.isNull("max")) {
            reply += " Oggi tra ${deg(dayForecast.getDouble("min"))}° e ${deg(dayForecast.getDouble("max"))}°"
            val rain = if (dayForecast.isNull("precipitationProbability")) null else dayForecast.optInt("precipitationProbability")
            reply += if (rain == null) "." else ", pioggia al $rain%."
        }
        return reply
    }

    private fun umbrella(tools: PhoneTools, day: LocalDate, tomorrow: Boolean): String? {
        val data = tools.call("get_weather", JSONObject()).data
        if (data.has("error")) return null
        val dayForecast = forecast(data, day, tomorrow) ?: return null
        if (dayForecast.isNull("precipitationProbability")) return null
        val rain = dayForecast.optInt("precipitationProbability")
        val whenLabel = if (tomorrow) "domani" else "oggi"
        return when {
            rain >= 50 -> "Sì, meglio prenderlo: pioggia al $rain% $whenLabel."
            rain >= 20 -> "Forse: pioggia al $rain% $whenLabel. Uno piccolo in borsa basta."
            else -> "No, pioggia al $rain% $whenLabel."
        }
    }

    private fun agenda(tools: PhoneTools, day: LocalDate, tomorrow: Boolean): String {
        val result = tools.call("list_events", JSONObject().put("date", day.toString())).data
        if (result.has("error")) return "${result.getString("error")}."
        val events = result.optJSONArray("events")
        val label = if (tomorrow) "Domani" else "Oggi"
        if (events == null || events.length() == 0) return "$label non hai impegni."
        val noun = if (events.length() == 1) "impegno" else "impegni"
        val lines = mutableListOf("$label hai ${events.length()} $noun:")
        for (index in 0 until events.length()) {
            val event = events.getJSONObject(index)
            val start = event.optString("start")
            val whenLabel = if (event.optBoolean("allDay") || start.length < 16) "Tutto il giorno" else start.substring(11, 16)
            lines += "- $whenLabel ${event.optString("title").ifBlank { "Senza titolo" }}"
        }
        return lines.joinToString("\n")
    }

    private fun dayOverview(tools: PhoneTools, day: LocalDate, tomorrow: Boolean, today: LocalDate): String {
        val agendaText = agenda(tools, day, tomorrow)
        var tasks = tools.call("list_tasks", JSONObject()).data.optJSONArray("tasks") ?: return agendaText
        if (tomorrow) {
            val kept = org.json.JSONArray()
            for (index in 0 until tasks.length()) {
                val task = tasks.getJSONObject(index)
                val due = task.optString("due")
                if (due.isNotBlank() && due <= day.toString()) kept.put(task)
            }
            tasks = kept
        }
        if (tasks.length() == 0) {
            val ending = if (tomorrow) " entro domani." else "."
            return "$agendaText\nNessun task da chiudere$ending"
        }
        val header = if (tomorrow) {
            "Task da chiudere entro domani (${tasks.length()}):"
        } else {
            "E ${tasks.length()} task ${if (tasks.length() == 1) "aperto" else "aperti"}:"
        }
        return "$agendaText\n${taskLines(tasks, today, header)}"
    }

    private fun tasks(tools: PhoneTools, today: LocalDate): String {
        val tasks = tools.call("list_tasks", JSONObject()).data.optJSONArray("tasks")
        if (tasks == null || tasks.length() == 0) return "Non hai task aperti."
        val noun = if (tasks.length() == 1) "task aperto" else "task aperti"
        return taskLines(tasks, today, "Hai ${tasks.length()} $noun:")
    }

    private fun taskLines(tasks: org.json.JSONArray, today: LocalDate, header: String): String {
        val lines = mutableListOf(header)
        for (index in 0 until tasks.length()) {
            val task = tasks.getJSONObject(index)
            val due = if (task.isNull("due")) null else task.optString("due").ifBlank { null }
            val note = when {
                task.optBoolean("overdue") -> " (scaduto)"
                due == today.toString() -> " (oggi)"
                due != null -> {
                    val parsed = LocalDate.parse(due)
                    " (entro il ${parsed.dayOfMonth}/${parsed.monthValue})"
                }
                else -> ""
            }
            lines += "- ${task.optString("title")}$note"
        }
        return lines.joinToString("\n")
    }

    private fun alarm(tools: PhoneTools, today: LocalDate): String {
        val iso = tools.call("get_alarm", JSONObject()).data.optString("nextAlarm")
        if (iso.isBlank() || iso == "null") return "Non c'è nessuna sveglia impostata sul telefono."
        val at = OffsetDateTime.parse(iso)
        val whenLabel = when (at.toLocalDate()) {
            today -> "oggi"
            today.plusDays(1) -> "domani"
            else -> "${weekdays[at.dayOfWeek.value - 1]} ${at.dayOfMonth}"
        }
        val hh = at.hour.toString().padStart(2, '0')
        val mm = at.minute.toString().padStart(2, '0')
        return "La prossima sveglia è $whenLabel alle $hh:$mm."
    }

    private fun deg(value: Double) = value.roundToInt()
}
