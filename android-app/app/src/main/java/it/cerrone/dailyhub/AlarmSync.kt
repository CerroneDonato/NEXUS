package it.cerrone.dailyhub

import android.app.AlarmManager
import android.content.Context
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Package names of clock apps contain one of these: com.oneplus.deskclock, com.coloros.alarmclock, com.google.android.deskclock... */
private val CLOCK_HINTS = listOf("clock", "alarm")

/** Only clock apps count as a wake-up alarm; calendars, reminders and bedtime tools also use AlarmManager.setAlarmClock. */
fun isClockApp(packageName: String?): Boolean =
    packageName == null || CLOCK_HINTS.any { packageName.contains(it, ignoreCase = true) }

/** "HH:mm" of an alarm: the identity of the alarms the user chose to ignore (a daily "ghost" alarm repeats at that time). */
fun alarmKey(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(epochMillis).atZone(zone))

/** An alarm the phone reports but NEXUS does not show. [appName] is the app that set it, or null when the user ignored it. */
data class IgnoredAlarm(val iso: String, val appName: String?)

object AlarmSync {
    private fun reported(context: Context): AlarmManager.AlarmClockInfo? =
        context.getSystemService(AlarmManager::class.java)?.nextAlarmClock

    /** Trigger time of the next wake-up alarm: set by a clock app and not ignored by the user. */
    fun nextAlarmMillis(context: Context): Long? {
        val info = reported(context) ?: return null
        if (!isClockApp(info.showIntent?.creatorPackage)) return null
        if (alarmKey(info.triggerTime) in Prefs(context).ignoredAlarmTimes) return null
        return info.triggerTime
    }

    /** Next wake-up alarm as ISO-8601 with offset. */
    fun nextAlarmIso(context: Context): String? = nextAlarmMillis(context)?.let { toIso(it) }

    /** "HH:mm" of the alarm currently shown, which is what "Ignora" would hide. */
    fun shownKey(context: Context): String? = nextAlarmMillis(context)?.let { alarmKey(it) }

    /** The phone's next alarm when NEXUS hides it, either because another app set it or because the user ignored it. */
    fun ignoredAlarm(context: Context): IgnoredAlarm? {
        val info = reported(context) ?: return null
        val source = info.showIntent?.creatorPackage
        if (isClockApp(source)) {
            return if (alarmKey(info.triggerTime) in Prefs(context).ignoredAlarmTimes) IgnoredAlarm(toIso(info.triggerTime), null) else null
        }
        // Android 11+ may hide other apps' labels; the package name still tells which app it is.
        @Suppress("DEPRECATION")
        val label = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(source!!, 0)).toString()
        }.getOrNull() ?: source!!
        return IgnoredAlarm(toIso(info.triggerTime), label)
    }

    fun ignore(context: Context, key: String) {
        val prefs = Prefs(context)
        prefs.ignoredAlarmTimes = prefs.ignoredAlarmTimes + key
    }

    fun restoreAll(context: Context) {
        Prefs(context).ignoredAlarmTimes = emptySet()
    }

    fun toIso(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone).truncatedTo(ChronoUnit.SECONDS)
        )

    suspend fun push(context: Context, client: HubClient) {
        client.putAlarm(nextAlarmIso(context))
    }
}
