package it.cerrone.dailyhub

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.cerrone.dailyhub.widget.HubWidget
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Pushes the next phone alarm to the hub, then refreshes the widget snapshot. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (!prefs.isConfigured) return Result.success()
        return try {
            val client = prefs.client()
            AlarmSync.push(applicationContext, client)
            prefs.lastSnapshotJson = client.today()
            prefs.lastUpdateMillis = System.currentTimeMillis()
            prefs.lastError = null
            HubWidget().updateAll(applicationContext)
            Result.success()
        } catch (e: IOException) {
            prefs.lastError = e.message ?: "Hub non raggiungibile"
            HubWidget().updateAll(applicationContext)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PERIODIC = "hub-refresh-periodic"
        private const val NOW = "hub-refresh-now"

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
