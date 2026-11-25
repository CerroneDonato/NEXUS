package it.cerrone.dailyhub

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Calendar part of the home snapshot: the PC's Google calendars, or the last ones saved when the PC does not answer. */
data class CalendarPart(
    val events: JSONArray,
    val week: JSONArray?,
    val error: String?,
    val note: String?,
)

/** Calendar events come from the PC, which reads the Google calendars. The phone keeps the last week it received. */
object HubCalendar {
    /** Reads `/api/today` from the PC: its week and the error it reports, if any. */
    fun fromHub(hub: JSONObject, today: LocalDate): CalendarPart {
        val error = text(hub, "calendarError")
        if (error != null) return CalendarPart(JSONArray(), null, error, null)
        val week = hub.optJSONArray("week") ?: return unavailable()
        return CalendarPart(eventsOn(week, today), week, null, null)
    }

    /** The PC does not answer: the last week saved, from today on, with a note that it is not up to date. */
    fun fromLastSnapshot(previous: JSONObject?, today: LocalDate): CalendarPart {
        val saved = previous?.optJSONArray("week") ?: return unavailable()
        val week = JSONArray()
        for (i in 0 until saved.length()) {
            val day = saved.optJSONObject(i) ?: continue
            val date = dateOf(day) ?: continue
            if (!date.isBefore(today)) week.put(day)
        }
        if (week.length() == 0) return unavailable()
        val note = tr("Dati del PC non aggiornati: mostro gli ultimi ricevuti", "PC data not up to date: showing the last received")
        return CalendarPart(eventsOn(week, today), week, null, note)
    }

    /** Before the PC is linked: nothing to show. */
    fun notConnected(): CalendarPart =
        CalendarPart(JSONArray(), null, tr("Collega il PC per vedere il calendario", "Connect the PC to see the calendar"), null)

    /** Events of one day for Lilith and the quick answers, from the week kept in the snapshot. */
    fun onDay(snapshot: JSONObject?, day: LocalDate): JSONObject {
        if (snapshot == null) return JSONObject().put("error", unavailableText())
        val week = snapshot.optJSONArray("week")
            ?: return JSONObject().put("error", text(snapshot, "calendarError") ?: unavailableText())
        val found = (0 until week.length()).mapNotNull { week.optJSONObject(it) }.firstOrNull { dateOf(it) == day }
            ?: return JSONObject().put("error", tr("Il calendario del PC copre solo i prossimi 7 giorni", "The PC calendar covers only the next 7 days"))
        return JSONObject().put("date", day.toString()).put("events", found.optJSONArray("events") ?: JSONArray())
    }

    private fun unavailable(): CalendarPart = CalendarPart(JSONArray(), null, unavailableText(), null)

    private fun unavailableText(): String = tr("Il PC non risponde", "The PC is not responding")

    private fun eventsOn(week: JSONArray, day: LocalDate): JSONArray {
        for (i in 0 until week.length()) {
            val entry = week.optJSONObject(i) ?: continue
            if (dateOf(entry) == day) return entry.optJSONArray("events") ?: JSONArray()
        }
        return JSONArray()
    }

    private fun dateOf(day: JSONObject): LocalDate? = runCatching { LocalDate.parse(day.getString("date")) }.getOrNull()

    /** A string field, or null when it is missing, JSON null or blank (optString would give "null"). */
    private fun text(obj: JSONObject, key: String): String? =
        if (obj.isNull(key)) null else obj.optString(key).takeIf { it.isNotBlank() }
}
