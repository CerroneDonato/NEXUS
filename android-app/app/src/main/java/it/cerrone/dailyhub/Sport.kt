package it.cerrone.dailyhub

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What the sport-mode microphone made of one heard sentence. */
sealed interface Wake {
    data object Ignore : Wake

    /** Only the name was said: answer "Dimmi." and take the next sentence as the question. */
    data object Listening : Wake

    data class Ask(val question: String) : Wake
}

/**
 * Decides which heard sentences are for Lilith. A sentence counts when it starts with her name,
 * with common spellings the recognizer produces ("Lilit", "Lili"), optionally after "ehi", "ciao"...
 */
class WakeFilter(private val followUpMillis: Long = 12_000) {
    private var awaitingUntil = 0L

    fun hear(text: String, nowMillis: Long): Wake {
        val heard = text.trim()
        if (heard.isEmpty()) return Wake.Ignore
        val match = WAKE.matchEntire(heard)
        if (match != null) {
            val rest = match.groupValues[1].trim()
            if (rest.isEmpty()) {
                awaitingUntil = nowMillis + followUpMillis
                return Wake.Listening
            }
            awaitingUntil = 0L
            return Wake.Ask(rest)
        }
        if (nowMillis < awaitingUntil) {
            awaitingUntil = 0L
            return Wake.Ask(heard)
        }
        return Wake.Ignore
    }

    private companion object {
        val WAKE = Regex(
            """^\s*(?:(?:ehi|ehilà|hey|ok|ciao|senti)[\s,]+)?(?:lilith|lillith|lilit|lilli|lilly|lily|lilla|lilia|lilì|lili|li\s+lit)(?![\p{L}\p{N}])[\s,.:;!?-]*(.*)$""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
    }
}

/** The question sent to the hub: short answers suit earbuds. */
fun sportQuestion(question: String): String =
    "(Mi sto allenando e ascolto dagli auricolari: rispondi in una sola frase breve, senza elenchi.) $question"

/** True while the sport service runs; the home switch reads it. */
object SportState {
    var active by mutableStateOf(false)
}
