package it.cerrone.dailyhub

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Events from the phone's own calendars. The same Google account the PC reads, through Android. */
object PhoneCalendar {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun week(context: Context, today: LocalDate): Pair<JSONArray, String?> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return JSONArray() to tr("Permesso del calendario non concesso", "Calendar permission not granted")
        }
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(start.toString())
            .appendPath(end.toString())
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val days = (0 until 7).associate { today.plusDays(it.toLong()) to JSONArray() }.toMutableMap()
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
            val title = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val begin = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val ending = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDay = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val location = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
            while (cursor.moveToNext()) {
                val startMillis = cursor.getLong(begin)
                val wholeDay = cursor.getInt(allDay) == 1
                val day = if (wholeDay) allDayDate(startMillis) else Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
                val bucket = days[day] ?: continue
                val endValue: Any = when {
                    cursor.isNull(ending) -> JSONObject.NULL
                    wholeDay -> allDayDate(cursor.getLong(ending)).toString()
                    else -> iso(cursor.getLong(ending))
                }
                val event = JSONObject()
                    .put("title", cursor.getString(title) ?: tr("Senza titolo", "Untitled"))
                    .put("allDay", wholeDay)
                    .put("start", if (wholeDay) day.toString() else iso(startMillis))
                    .put("end", endValue)
                    .put("location", cursor.getString(location)?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                bucket.put(event)
            }
        }
        val week = JSONArray()
        for (offset in 0 until 7) {
            val day = today.plusDays(offset.toLong())
            week.put(JSONObject().put("date", day.toString()).put("events", days[day] ?: JSONArray()))
        }
        return week to null
    }

    fun onDay(context: Context, day: LocalDate): JSONObject {
        val (week, error) = week(context, day)
        if (error != null) return JSONObject().put("error", error)
        val events = (0 until week.length())
            .map { week.getJSONObject(it) }
            .firstOrNull { it.getString("date") == day.toString() }
            ?.optJSONArray("events") ?: JSONArray()
        return JSONObject().put("date", day.toString()).put("events", events)
    }

    /** With seconds and offset, e.g. 2026-11-10T10:00:00+01:00 (OffsetDateTime.toString drops zero seconds). */
    private fun iso(millis: Long): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
        java.time.OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), zone).truncatedTo(ChronoUnit.SECONDS),
    )

    /** Android stores an all-day event as midnight UTC, so its date must be read in UTC, not in the local zone. */
    private fun allDayDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
