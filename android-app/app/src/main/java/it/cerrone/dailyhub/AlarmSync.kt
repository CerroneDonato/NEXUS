package it.cerrone.dailyhub

import android.app.AlarmManager
import android.content.Context
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object AlarmSync {
    /** Next alarm set in any clock app (AlarmManager.setAlarmClock), as ISO-8601 with offset. */
    fun nextAlarmIso(context: Context): String? {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val info = alarmManager?.nextAlarmClock ?: return null
        return toIso(info.triggerTime)
    }

    fun toIso(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone).truncatedTo(ChronoUnit.SECONDS)
        )

    suspend fun push(context: Context, client: HubClient) {
        client.putAlarm(nextAlarmIso(context))
    }
}
