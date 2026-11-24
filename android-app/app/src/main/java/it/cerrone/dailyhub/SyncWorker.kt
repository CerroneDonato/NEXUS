package it.cerrone.dailyhub

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.cerrone.dailyhub.widget.NexusWidgets
import java.util.concurrent.TimeUnit

/** Tries a sync while the phone is on Wi-Fi. If the PC is off, nothing changes. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (!prefs.canSync) return Result.success()
        val ok = SyncClient(applicationContext).sync().ok
        val json = LocalToday.refresh(applicationContext)
        runCatching { Reminders.schedule(applicationContext, parseToday(json)) }
        NexusWidgets.updateAll(applicationContext)
        // A change made on the phone that could not reach the PC is retried with a growing delay.
        return if (!ok && inputData.getBoolean(KEY_RETRY, false) && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
    }

    companion object {
        private const val PERIODIC = "nexus-sync-periodic"
        private const val NOW = "nexus-sync-now"
        private const val SOON = "nexus-sync-soon"
        private const val KEY_RETRY = "retry"
        private const val MAX_RETRIES = 8
        private val wifi = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(wifi)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(wifi).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }

        /** After a direct sync failed (PC asleep, Wi-Fi switching): try again later, 30 s then 1 min, 2 min... */
        fun syncSoon(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(wifi)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(Data.Builder().putBoolean(KEY_RETRY, true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(SOON, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
