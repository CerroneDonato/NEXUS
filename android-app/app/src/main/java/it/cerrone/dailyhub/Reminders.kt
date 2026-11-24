package it.cerrone.dailyhub

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.cerrone.dailyhub.widget.NexusWidgets
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Silent, visual-only nudges: a notification a few minutes before each event (set in
 * Impostazioni) and a "Buongiorno" summary shortly after the phone alarm rings. Nothing is spoken.
 */
object Reminders {
    private const val CHANNEL_EVENTS = "reminders"
    private const val CHANNEL_BRIEFING = "briefing"
    private const val BRIEFING_CODE = 1
    private const val BRIEFING_DELAY_MILLIS = 90_000L
    private const val BRIEFING_MAX_AHEAD_MILLIS = 20 * 60 * 60 * 1000L

    const val ACTION_EVENT = "it.cerrone.dailyhub.REMINDER_EVENT"
    const val ACTION_BRIEFING = "it.cerrone.dailyhub.REMINDER_BRIEFING"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"
    const val EXTRA_CODE = "code"

    /** Re-plans every alarm from the latest snapshot; stale ones are cancelled. */
    fun schedule(context: Context, snapshot: TodaySnapshot) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = Prefs(context)
        val now = OffsetDateTime.now()

        val plan = reminderPlan(snapshot.events, now, prefs.reminderLeadMinutes)
        val codes = plan.map { codeFor(it.key) }.toSet()
        (prefs.reminderCodes - codes).forEach { code ->
            existing(context, code, ACTION_EVENT)?.let { alarms.cancel(it); it.cancel() }
        }
        plan.forEach { reminder ->
            val code = codeFor(reminder.key)
            val intent = Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_EVENT)
                .putExtra(EXTRA_TITLE, reminder.title)
                .putExtra(EXTRA_TEXT, reminder.text)
                .putExtra(EXTRA_CODE, code)
            setAt(alarms, reminder.atMillis, broadcast(context, code, intent))
        }
        prefs.reminderCodes = codes

        scheduleBriefing(context, alarms, prefs.morningBriefing)
    }

    private fun scheduleBriefing(context: Context, alarms: AlarmManager, enabled: Boolean) {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_BRIEFING)
        val trigger = AlarmSync.nextAlarmMillis(context)
        val nowMillis = System.currentTimeMillis()
        val hour = trigger?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).hour }
        if (!enabled || trigger == null || hour !in 4..11 || trigger - nowMillis > BRIEFING_MAX_AHEAD_MILLIS) {
            existing(context, BRIEFING_CODE, ACTION_BRIEFING)?.let { alarms.cancel(it); it.cancel() }
            return
        }
        setAt(alarms, trigger + BRIEFING_DELAY_MILLIS, broadcast(context, BRIEFING_CODE, intent))
    }

    private fun setAt(alarms: AlarmManager, atMillis: Long, operation: PendingIntent) {
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        }
    }

    private fun codeFor(key: String): Int = (key.hashCode() and 0x7FFFFFFF).let { if (it == BRIEFING_CODE) it + 1 else it }

    private fun broadcast(context: Context, code: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun existing(context: Context, code: Int, action: String): PendingIntent? =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )

    private fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_EVENTS, tr("Promemoria impegni", "Event reminders"), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = tr("Un avviso prima di ogni impegno", "A notice before every event") },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_BRIEFING, tr("Buongiorno", "Good morning"), NotificationManager.IMPORTANCE_LOW)
                .apply { description = tr("Riepilogo silenzioso dopo la sveglia", "Silent summary after the alarm") },
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun notifyEvent(context: Context, code: Int, title: String, text: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_notify_event)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setTimeoutAfter(Prefs(context).reminderLeadMinutes * 60_000)
            .build()
        runCatching { manager.notify(code, notification) }
    }

    fun notifyBriefing(context: Context, snapshot: TodaySnapshot) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannels(context)
        val (title, lines) = briefingText(snapshot, OffsetDateTime.now())
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        lines.forEach(style::addLine)
        val notification = NotificationCompat.Builder(context, CHANNEL_BRIEFING)
            .setSmallIcon(R.drawable.ic_notify_sun)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull().orEmpty())
            .setStyle(style)
            .setSilent(true)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(BRIEFING_CODE, notification) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Reminders.ACTION_EVENT -> Reminders.notifyEvent(
                context,
                intent.getIntExtra(Reminders.EXTRA_CODE, 2),
                intent.getStringExtra(Reminders.EXTRA_TITLE).orEmpty(),
                intent.getStringExtra(Reminders.EXTRA_TEXT).orEmpty(),
            )
            Reminders.ACTION_BRIEFING -> BriefingWorker.enqueue(context)
        }
    }
}

/** Fetches a fresh snapshot after the alarm, falling back to the cached one, and posts the morning summary. */
class BriefingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val snapshot = try {
            val json = LocalToday.refresh(applicationContext)
            NexusWidgets.updateAll(applicationContext)
            parseToday(json)
        } catch (e: Exception) {
            if (runAttemptCount < 2) return Result.retry()
            prefs.lastSnapshotJson?.let { runCatching { parseToday(it) }.getOrNull() }
                ?.takeIf { it.date == LocalDate.now() }
        } ?: return Result.success()

        if (prefs.lastBriefingDate == snapshot.date.toString()) return Result.success()
        prefs.lastBriefingDate = snapshot.date.toString()
        Reminders.notifyBriefing(applicationContext, snapshot)
        return Result.success()
    }

    companion object {
        private const val NAME = "morning-briefing"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<BriefingWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
