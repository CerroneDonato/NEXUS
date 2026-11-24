package it.cerrone.dailyhub

import android.app.Application
import java.util.Locale

/**
 * The app language. Every user-visible text is written once in Italian and once in English with
 * [tr]; which one wins depends on [Lang.code], set from the Language settings page. Choosing a
 * language recreates the screen, so nothing needs to observe it.
 */
object Lang {
    const val AUTO = "auto"
    const val IT = "it"
    const val EN = "en"

    /** Resolved language, "it" or "en". Italian until [apply] runs (keeps unit tests deterministic). */
    @Volatile
    var code: String = IT
        private set

    val isEnglish: Boolean get() = code == EN

    /** [mode] is what the user picked: "auto" follows the phone, otherwise "it" or "en". */
    fun apply(mode: String) {
        code = when (mode) {
            IT -> IT
            EN -> EN
            else -> if (Locale.getDefault().language == "it") IT else EN
        }
    }

    val locale: Locale get() = if (isEnglish) Locale.UK else Locale.ITALY

    /** Tag for speech recognition, text to speech and Gemini. */
    val speechTag: String get() = if (isEnglish) "en-GB" else "it-IT"
}

fun tr(italian: String, english: String): String = if (Lang.isEnglish) english else italian

/** Starts in every process (app, widgets, workers, services) so [Lang] is always set. */
class NexusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Lang.apply(Prefs(this).language)
    }
}
