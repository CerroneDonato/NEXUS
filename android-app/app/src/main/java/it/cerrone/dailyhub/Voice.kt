package it.cerrone.dailyhub

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Push-to-talk recognition in Italian with the phone's built-in recognizer. Use from the main thread. */
class VoiceInput(
    context: Context,
    private val onPartial: (String) -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String?) -> Unit,
) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun start() {
        destroy()
        val current = SpeechRecognizer.createSpeechRecognizer(appContext)
        recognizer = current
        current.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = onLevel(0f)
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onRmsChanged(rmsdB: Float) = onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))

            override fun onPartialResults(partialResults: Bundle?) {
                firstResult(partialResults)?.let(onPartial)
            }

            override fun onResults(results: Bundle?) {
                val text = firstResult(results).orEmpty().trim()
                destroy()
                if (text.isEmpty()) this@VoiceInput.onError(NO_SPEECH) else onFinal(text)
            }

            override fun onError(error: Int) {
                destroy()
                this@VoiceInput.onError(messageFor(error))
            }
        })
        current.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Lang.speechTag)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName),
        )
    }

    /** Ends listening; the words heard so far are delivered to onFinal. */
    fun stop() {
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    companion object {
        /** Reported when nothing was heard; the sport loop treats it as normal silence. */
        val NO_SPEECH: String get() = tr("Non ho sentito nulla, riprova.", "I didn't hear anything, try again.")

        private fun messageFor(error: Int): String? = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> NO_SPEECH
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> tr("Serve il permesso del microfono.", "The microphone permission is needed.")
            SpeechRecognizer.ERROR_AUDIO -> tr("Microfono non disponibile.", "Microphone not available.")
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                tr("Riconoscimento vocale non raggiungibile: controlla la connessione.", "Speech recognition unreachable: check the connection.")
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> tr("Il riconoscimento vocale è occupato, riprova.", "Speech recognition is busy, try again.")
            SpeechRecognizer.ERROR_CLIENT -> null
            else -> tr("Errore del riconoscimento vocale ($error).", "Speech recognition error ($error).")
        }
    }
}

/** Reads Lilith's replies aloud with the phone's text-to-speech engine. */
class Speaker(context: Context, private val onSpeaking: (Boolean) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var pending: String? = null
    private var counter = 0
    private var duck = false
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts.language = Lang.locale
                applyAttributes()
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = notify(true)
                    override fun onDone(utteranceId: String?) = notify(false)
                    override fun onStop(utteranceId: String?, interrupted: Boolean) = notify(false)

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = notify(false)
                })
                pending?.let(::speak)
            }
            pending = null
        }
    }

    /** With [on], speech is flagged as guidance so music keeps playing and only lowers its volume while Lilith talks. */
    fun duckMusic(on: Boolean) {
        if (duck == on) return
        duck = on
        if (ready) applyAttributes()
    }

    private fun applyAttributes() {
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(if (duck) AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE else AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
    }

    fun speak(text: String) {
        if (!ready) {
            pending = text
            return
        }
        tts.speak(speakable(text), TextToSpeech.QUEUE_FLUSH, null, "lilith-${counter++}")
    }

    fun stop() {
        pending = null
        if (ready) tts.stop()
        onSpeaking(false)
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    private fun notify(speaking: Boolean) {
        main.post { onSpeaking(speaking) }
    }
}
