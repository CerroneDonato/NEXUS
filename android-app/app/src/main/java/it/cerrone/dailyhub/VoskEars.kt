package it.cerrone.dailyhub

import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/** The words of a Vosk result, which looks like {"text":"lilit che tempo fa"}. Empty when nothing was said. */
fun voskText(json: String?): String =
    runCatching { JSONObject(json.orEmpty()).optString("text") }.getOrDefault("").trim()

/**
 * Continuous offline listening for sport mode. One microphone stream stays open and every finished sentence
 * is delivered to [onText]. Unlike Android's SpeechRecognizer it never asks for audio focus, so the music in
 * the earbuds keeps playing. Callbacks arrive on the main thread. Takes ownership of [model].
 */
class VoskEars(
    private val model: Model,
    private val onText: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var recognizer: Recognizer? = null
    private var service: SpeechService? = null
    private var paused = false
    private var destroyed = false

    fun start() {
        try {
            val recognizer = Recognizer(model, SAMPLE_RATE)
            val service = SpeechService(recognizer, SAMPLE_RATE)
            this.recognizer = recognizer
            this.service = service
            service.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) = Unit
                override fun onResult(hypothesis: String?) = deliver(hypothesis)
                override fun onFinalResult(hypothesis: String?) = deliver(hypothesis)
                override fun onError(exception: Exception?) =
                    this@VoskEars.onError(exception?.message ?: tr("Ascolto offline interrotto.", "Offline listening stopped."))
                override fun onTimeout() = Unit
            })
            // The recording thread exists only after startListening.
            if (paused) service.setPause(true)
        } catch (e: Exception) {
            destroy()
            this.onError(e.message ?: tr("Ascolto offline non avviato.", "Offline listening did not start."))
        }
    }

    /** Stops hearing, e.g. while Lilith speaks so she does not answer herself. */
    fun pause() {
        paused = true
        service?.setPause(true)
    }

    fun resume() {
        paused = false
        service?.setPause(false)
    }

    /** Safe to call more than once: the native model and recognizer must be freed only one time. */
    fun destroy() {
        if (destroyed) return
        destroyed = true
        // Stop the recording thread before freeing what it reads from.
        runCatching { service?.stop() }
        runCatching { service?.shutdown() }
        runCatching { recognizer?.close() }
        runCatching { model.close() }
        service = null
        recognizer = null
    }

    private fun deliver(hypothesis: String?) {
        if (paused || destroyed) return
        val text = voskText(hypothesis)
        if (text.isNotEmpty()) onText(text)
    }

    private companion object {
        const val SAMPLE_RATE = 16000.0f
    }
}
