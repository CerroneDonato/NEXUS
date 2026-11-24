package it.cerrone.dailyhub

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import it.cerrone.dailyhub.widget.HubWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * Sport mode: keeps the microphone open (foreground service with a notification) and answers
 * sentences that start with "Lilith", reading the reply into the earbuds.
 */
class SportService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val filter = WakeFilter()
    private val conversationId = "sport-" + UUID.randomUUID().toString().take(12)

    private lateinit var prefs: Prefs
    private lateinit var audio: AudioManager
    private lateinit var voice: VoiceInput
    private lateinit var speaker: Speaker
    private var wakeLock: PowerManager.WakeLock? = null
    private var focus: AudioFocusRequest? = null

    /** Offline listening; null while the model loads or when sport mode falls back to Google's recognizer. */
    private var ears: VoskEars? = null

    private var running = false
    private var busy = false
    private var speaking = false
    private var failures = 0

    /** True when the earbuds' microphone is in use, false when the phone's is; null before the first routing. */
    private var onHeadset: Boolean? = null

    /** True once the listening engine (Vosk or the Google fallback) has been chosen. */
    private var engineReady = false

    private val listenNow = Runnable {
        if (running && engineReady && !busy && !speaking) {
            applyRoute()
            // Vosk keeps one microphone stream open: resuming is enough. Google needs a new recognition each time.
            val offline = ears
            if (offline != null) offline.resume() else voice.start()
        }
    }
    private val speechWatchdog = Runnable {
        // The speech engine never reported the end: do not stay deaf.
        speaking = false
        scheduleListen(300)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY
        prefs = Prefs(this)
        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification(IDLE_TEXT),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
        } catch (e: RuntimeException) {
            // Missing microphone permission or a start the system does not allow.
            stopSelf()
            return START_NOT_STICKY
        }

        audio = getSystemService(AudioManager::class.java)
        voice = VoiceInput(
            this,
            onPartial = {},
            onLevel = {},
            onFinal = ::onHeard,
            onError = ::onVoiceError,
        )
        speaker = Speaker(this, ::onSpeaking)

        running = true
        SportState.active = true
        HubWidget.refresh(this)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NEXUS:sport")
            .apply { acquire(MAX_RUN_MILLIS) }
        applyRoute()
        scope.launch { chooseEngine() }
        return START_NOT_STICKY
    }

    /**
     * Prefers offline Vosk (music keeps playing). The first time the small Italian model is downloaded;
     * if that or the engine fails, falls back to Google's recognizer, which works but may pause music.
     */
    private suspend fun chooseEngine() {
        if (!VoskModel.isReady(this)) {
            notify(tr("Scarico il modello vocale (una volta sola)...", "Downloading the voice model (one time only)..."))
            var shown = -10
            try {
                VoskModel.ensure(this) { percent ->
                    if (percent >= shown + 10) {
                        shown = percent
                        main.post { if (running) notify(tr("Scarico il modello vocale: $percent%", "Downloading the voice model: $percent%")) }
                    }
                }
            } catch (e: IOException) {
                notify(tr("Modello vocale non scaricato: uso il riconoscimento di Google.", "Voice model not downloaded: using Google's recognition."))
            }
        }
        if (!running) return
        if (VoskModel.isReady(this)) {
            val model = withContext(Dispatchers.Default) {
                runCatching { org.vosk.Model(VoskModel.dir(this@SportService).path) }.getOrNull()
            }
            if (!running) {
                model?.close()
                return
            }
            if (model != null) {
                val offline = VoskEars(model, onText = ::onHeard, onError = ::onEarsError)
                ears = offline
                offline.start() // on failure it reports through onEarsError, which clears [ears]
            }
        }
        if (ears == null) {
            if (!voice.isAvailable) {
                stopWithNotice(tr("Riconoscimento vocale non disponibile su questo telefono.", "Speech recognition is not available on this phone."))
                return
            }
            if (VoskModel.isReady(this)) notify(tr("Ascolto offline non avviato: uso il riconoscimento di Google.", "Offline listening did not start: using Google's recognition."))
        } else {
            notify(IDLE_TEXT)
        }
        engineReady = true
        scheduleListen(0)
    }

    /** The offline engine broke while running: keep going with Google's recognizer instead of going deaf. */
    private fun onEarsError(message: String) {
        if (!running) return
        ears?.destroy()
        ears = null
        if (voice.isAvailable) {
            notify(tr("Ascolto offline interrotto: uso il riconoscimento di Google.", "Offline listening stopped: using Google's recognition."))
            scheduleListen(500)
        } else {
            stopWithNotice(message)
        }
    }

    override fun onDestroy() {
        running = false
        SportState.active = false
        HubWidget.refresh(this)
        main.removeCallbacksAndMessages(null)
        ears?.destroy()
        ears = null
        if (::voice.isInitialized) voice.destroy()
        if (::speaker.isInitialized) speaker.shutdown()
        if (::audio.isInitialized) {
            abandonFocus()
            releaseHeadset()
        }
        wakeLock?.takeIf { it.isHeld }?.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun scheduleListen(delayMillis: Long) {
        main.removeCallbacks(listenNow)
        main.postDelayed(listenNow, delayMillis)
    }

    private fun onHeard(text: String) {
        failures = 0
        when (val wake = filter.hear(text, SystemClock.elapsedRealtime())) {
            Wake.Ignore -> scheduleListen(200)
            Wake.Listening -> say(tr("Dimmi.", "Yes?"))
            is Wake.Ask -> ask(wake.question)
        }
    }

    private fun onVoiceError(message: String?) {
        // Silence and a recognizer that simply ended are normal in a continuous loop.
        if (message == null || message == VoiceInput.NO_SPEECH) {
            failures = 0
            scheduleListen(400)
            return
        }
        failures++
        if (failures >= MAX_FAILURES) {
            stopWithNotice(message)
        } else {
            scheduleListen((failures * 2_000L).coerceAtMost(10_000L))
        }
    }

    private fun ask(question: String) {
        busy = true
        ears?.pause()
        notify(tr("Sto pensando...", "Thinking..."))
        scope.launch {
            try {
                val result = PhoneAssistant(this@SportService).chat(conversationId, sportQuestion(question))
                if (result.actions.isNotEmpty()) RefreshWorker.refreshNow(this@SportService)
                say(result.reply)
            } catch (e: Exception) {
                say(e.message ?: tr("Non riesco a rispondere adesso.", "I can't answer right now."))
            } finally {
                busy = false
                notify(IDLE_TEXT)
                if (!speaking) scheduleListen(300)
            }
        }
    }

    private fun say(text: String) {
        speaking = true
        ears?.pause()
        main.removeCallbacks(speechWatchdog)
        main.postDelayed(speechWatchdog, SPEECH_LIMIT_MILLIS)
        speaker.speak(text)
    }

    private fun onSpeaking(isSpeaking: Boolean) {
        if (isSpeaking) {
            requestFocus()
            return
        }
        main.removeCallbacks(speechWatchdog)
        speaking = false
        abandonFocus()
        scheduleListen(500)
    }

    private fun stopWithNotice(message: String) {
        running = false
        stopForeground(STOP_FOREGROUND_DETACH)
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(message, ongoing = false))
        stopSelf()
    }

    /**
     * Picks the microphone from the "Con la musica" setting. A Bluetooth headset microphone (HFP/SCO) interrupts
     * the music in the earbuds, so with music on the phone microphone listens and the earbuds only play.
     */
    private fun applyRoute() {
        val wantHeadset = !prefs.sportPhoneMic
        speaker.duckMusic(!wantHeadset)
        if (onHeadset == wantHeadset) return
        if (wantHeadset) routeToHeadset() else releaseHeadset()
        onHeadset = wantHeadset
    }

    /** Duck the music while Lilith talks. */
    private fun requestFocus() {
        if (focus != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(
                        if (prefs.sportPhoneMic) AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
                        else AudioAttributes.USAGE_ASSISTANT,
                    )
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .build()
        focus = request
        audio.requestAudioFocus(request)
    }

    private fun abandonFocus() {
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
    }

    /** Use the earbuds' microphone when a Bluetooth headset is connected; otherwise the phone's. */
    @Suppress("DEPRECATION")
    private fun routeToHeadset() {
        val canUseBluetooth = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (!canUseBluetooth) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val headset = audio.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
            if (headset != null) audio.setCommunicationDevice(headset)
        } else if (audio.isBluetoothScoAvailableOffCall) {
            audio.startBluetoothSco()
            audio.isBluetoothScoOn = true
        }
    }

    @Suppress("DEPRECATION")
    private fun releaseHeadset() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audio.clearCommunicationDevice()
        } else {
            audio.isBluetoothScoOn = false
            audio.stopBluetoothSco()
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, tr("Modalità sport", "Sport mode"), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notification(text: String, ongoing: Boolean = true): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, SportService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(if (ongoing) tr("Lilith ti ascolta", "Lilith is listening") else tr("Modalità sport fermata", "Sport mode stopped"))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (ongoing) builder.addAction(0, tr("Ferma", "Stop"), stop)
        return builder.build()
    }

    private fun notify(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    companion object {
        private const val ACTION_STOP = "it.cerrone.dailyhub.SPORT_STOP"
        private const val CHANNEL_ID = "sport"
        private const val NOTIFICATION_ID = 42
        private val IDLE_TEXT: String get() = tr("Di' «Lilith» e fai la tua domanda", "Say 'Lilith' and ask your question")
        private const val MAX_FAILURES = 6
        private const val SPEECH_LIMIT_MILLIS = 45_000L
        private const val MAX_RUN_MILLIS = 4 * 60 * 60 * 1000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, SportService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SportService::class.java))
        }
    }
}
