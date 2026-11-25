package it.cerrone.dailyhub

import android.content.Context
import it.cerrone.dailyhub.widget.NexusWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * When the phone talks to the PC, beyond the 15-minute background sync:
 *  - [nudge] right after a task or chat change, so the PC sees it within a second or two;
 *  - [watch] while the app is on screen, every 15 seconds, so changes made on the PC show up on their own.
 * Both are plain short HTTP calls: no foreground service, no wake lock, nothing runs with the screen off.
 */
object SyncTrigger {
    private const val DEBOUNCE_MS = 1_000L
    private const val LIVE_MS = 15_000L
    private const val MAX_BACKOFF_MS = 120_000L
    private const val MANUAL_LIMIT_MS = 25_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var timer: Job? = null

    /** Syncs about a second from now; several quick changes share one exchange. A failure hands over to WorkManager. */
    fun nudge(context: Context) {
        val app = context.applicationContext
        if (!Prefs(app).canSync) return
        synchronized(this) {
            timer?.cancel()
            timer = scope.launch {
                delay(DEBOUNCE_MS)
                // Once the exchange starts it runs to the end, even if another change arrives meanwhile.
                withContext(NonCancellable) { exchange(app) }
            }
        }
    }

    private suspend fun exchange(app: Context) {
        val ok = try {
            SyncClient(app).sync().ok
        } catch (e: Exception) {
            false
        }
        if (!ok) SyncWorker.syncSoon(app)
    }

    /**
     * The widget sync button: exchange with the PC like "Sincronizza ora", then reread calendar and weather,
     * redraw every widget and tell an open app to reload. Bounded so the widget broadcast never times out;
     * if the PC does not answer in time WorkManager finishes the job.
     */
    suspend fun manual(context: Context) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        prefs.manualSyncStarted = System.currentTimeMillis()
        NexusWidgets.updateAll(app)
        val ok = try {
            withTimeout(MANUAL_LIMIT_MS) { if (prefs.canSync) SyncClient(app).sync().ok else true }
        } catch (e: TimeoutCancellationException) {
            false
        } catch (e: Exception) {
            false
        }
        try {
            val json = LocalToday.refresh(app)
            runCatching { Reminders.schedule(app, parseToday(json)) }
        } catch (e: Exception) {
            prefs.lastError = e.message ?: tr("Aggiornamento non riuscito", "Update failed")
        }
        prefs.manualSyncStarted = 0L
        SyncSignal.applied.update { it + 1 }
        NexusWidgets.updateAll(app)
        if (!ok) SyncWorker.syncSoon(app)
    }

    fun isManualSyncRunning(prefs: Prefs): Boolean =
        System.currentTimeMillis() - prefs.manualSyncStarted < MANUAL_LIMIT_MS + 5_000L

    /** Runs while the screen is visible (call it inside repeatOnLifecycle). Backs off while the PC cannot be reached. */
    suspend fun watch(context: Context) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        var wait = LIVE_MS
        // Coming back to the app after a while syncs at once; right after another sync it waits its turn.
        delay((LIVE_MS - (System.currentTimeMillis() - SyncClient.lastFinished)).coerceIn(0L, LIVE_MS))
        while (true) {
            if (prefs.canSync) {
                val ok = try {
                    SyncClient(app).sync().ok
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
                wait = if (ok) LIVE_MS else minOf(wait * 2, MAX_BACKOFF_MS)
            } else {
                wait = LIVE_MS
            }
            delay(wait)
        }
    }
}
