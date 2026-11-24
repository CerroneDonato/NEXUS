package it.cerrone.dailyhub

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class ToolOut(val data: JSONObject, val action: String? = null)

/** The same tools Lilith has on the PC, run against the phone's own data. */
class PhoneTools(private val context: Context) {
    private val db = LocalDb.get(context)
    private val prefs = Prefs(context)

    fun call(name: String, args: JSONObject): ToolOut = try {
        when (name) {
            "get_weather" -> weather()
            "list_events" -> events(args.optString("date"))
            "list_tasks" -> tasks(args.optBoolean("include_done"))
            "create_task" -> create(args.optString("title"), text(args, "due"))
            "complete_task" -> complete(args.optString("task_id"))
            "update_task" -> update(args)
            "get_alarm" -> ToolOut(JSONObject().put("nextAlarm", AlarmSync.nextAlarmIso(context) ?: JSONObject.NULL))
            else -> ToolOut(JSONObject().put("error", "Strumento sconosciuto: $name"))
        }
    } catch (e: Exception) {
        ToolOut(JSONObject().put("error", e.message ?: tr("Servizio non disponibile in questo momento", "Service not available right now")))
    }

    private fun weather(): ToolOut {
        val snapshot = prefs.lastSnapshotJson?.let { runCatching { JSONObject(it).optJSONObject("weather") }.getOrNull() }
        val weather = snapshot ?: runCatching { WeatherClient.load(prefs) }.getOrNull()
            ?: return ToolOut(JSONObject().put("error", "Meteo non disponibile"))
        return ToolOut(
            JSONObject()
                .put("city", weather.opt("city"))
                .put("current", weather.opt("current"))
                .put("today", weather.opt("today"))
                .put("daily", weather.opt("daily") ?: JSONArray())
        )
    }

    private fun events(date: String): ToolOut {
        val day = runCatching { LocalDate.parse(date) }.getOrElse { return ToolOut(JSONObject().put("error", "Data non valida")) }
        val result = PhoneCalendar.onDay(context, day)
        if (result.has("error")) return ToolOut(result)
        return ToolOut(result)
    }

    private fun tasks(includeDone: Boolean): ToolOut {
        val array = JSONArray()
        for (task in db.tasks(includeDone, LocalDate.now())) {
            array.put(
                JSONObject()
                    .put("id", task.uid)
                    .put("title", task.title)
                    .put("due", task.due?.toString() ?: JSONObject.NULL)
                    .put("done", task.done)
                    .put("overdue", task.overdue)
            )
        }
        return ToolOut(JSONObject().put("tasks", array))
    }

    private fun create(title: String, due: String?): ToolOut {
        if (title.isBlank()) return ToolOut(JSONObject().put("error", "Manca il titolo"))
        val parsed = due?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
        db.addTask(title, parsed)
        val suffix = if (parsed != null) " (entro $parsed)" else ""
        return ToolOut(
            JSONObject().put("created", JSONObject().put("title", title.trim())),
            action = tr("Task creato: ${title.trim()}$suffix", "Task created: ${title.trim()}$suffix"),
        )
    }

    private fun complete(uid: String): ToolOut {
        val task = db.completeByUid(uid) ?: return ToolOut(JSONObject().put("error", "Task non trovato"))
        return ToolOut(JSONObject().put("completed", JSONObject().put("id", task.uid).put("title", task.title)), tr("Task completato: ${task.title}", "Task completed: ${task.title}"))
    }

    /** A string argument, or null when it is missing, JSON null or blank (optString would give "null"). */
    private fun text(args: JSONObject, key: String): String? =
        if (args.isNull(key)) null else args.optString(key).trim().ifBlank { null }

    private fun update(args: JSONObject): ToolOut {
        val uid = args.optString("task_id")
        val title = text(args, "title")
        val hasDue = args.has("due")
        val due = text(args, "due")?.take(10)
        if (title == null && !hasDue) return ToolOut(JSONObject().put("error", "Nessuna modifica indicata"))
        if (due != null && runCatching { LocalDate.parse(due) }.isFailure) {
            return ToolOut(JSONObject().put("error", "Data non valida"))
        }
        val task = db.updateByUid(uid, title, due, clearDue = hasDue && due == null)
            ?: return ToolOut(JSONObject().put("error", "Task non trovato"))
        return ToolOut(JSONObject().put("updated", JSONObject().put("title", task.title)), tr("Task modificato: ${task.title}", "Task updated: ${task.title}"))
    }
}
