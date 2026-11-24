package it.cerrone.dailyhub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SportTest {
    @Test
    fun nameFirst_isTheQuestion() {
        assertEquals(Wake.Ask("che tempo fa domani"), WakeFilter().hear("Lilith che tempo fa domani", 0))
        assertEquals(Wake.Ask("cosa ho oggi?"), WakeFilter().hear("Lilith, cosa ho oggi?", 0))
    }

    @Test
    fun fillerWordsAndSpellingsAreAccepted() {
        assertEquals(Wake.Ask("che ore sono"), WakeFilter().hear("ehi Lilith, che ore sono", 0))
        assertEquals(Wake.Ask("che ore sono"), WakeFilter().hear("Ok Lilit che ore sono", 0))
        assertEquals(Wake.Ask("e il meteo"), WakeFilter().hear("ciao Lili: e il meteo", 0))
    }

    @Test
    fun nameAlone_thenFollowUpWithinTime() {
        val filter = WakeFilter(followUpMillis = 12_000)
        assertEquals(Wake.Listening, filter.hear("Lilith", 1_000))
        assertEquals(Wake.Ask("che tempo fa"), filter.hear("che tempo fa", 4_000))
        assertEquals(Wake.Ignore, filter.hear("che tempo fa", 5_000))
    }

    @Test
    fun followUpExpires() {
        val filter = WakeFilter(followUpMillis = 12_000)
        assertEquals(Wake.Listening, filter.hear("Lilith", 0))
        assertEquals(Wake.Ignore, filter.hear("che tempo fa", 20_000))
    }

    @Test
    fun otherSentencesAreIgnored() {
        val filter = WakeFilter()
        assertEquals(Wake.Ignore, filter.hear("che tempo fa", 0))
        assertEquals(Wake.Ignore, filter.hear("sto parlando con Liliana", 0))
        assertEquals(Wake.Ignore, filter.hear("   ", 0))
        assertEquals(Wake.Ignore, filter.hear("ho visto Lilith ieri", 0))
    }

    @Test
    fun sportQuestion_asksForOneShortSentence() {
        val text = sportQuestion("che tempo fa")
        assertTrue(text.endsWith("che tempo fa"))
        assertTrue(text.contains("una sola frase breve"))
    }
}
