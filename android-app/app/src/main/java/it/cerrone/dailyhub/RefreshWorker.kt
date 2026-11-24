package it.cerrone.dailyhub

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.cerrone.dailyhub.widget.NexusWidgets
import java.util.concurrent.TimeUnit

/** Refreshes the local snapshot (weather, calendar, tasks) and re-plans the reminders. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val json = LocalToday.refresh(applicationContext)
            runCatching { Reminders.schedule(applicationContext, parseToday(json)) }
            NexusWidgets.updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Prefs(applicationContext).lastError = e.message ?: tr("Aggiornamento non riuscito", "Update failed")
            NexusWidgets.updateAll(applicationContext)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PERIODIC = "hub-refresh-periodic"
        private const val NOW = "hub-refresh-now"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
            SyncWorker.schedule(context)
        }

        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
            SyncWorker.syncNow(context)
        }
    }
}
