package it.cerrone.dailyhub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class VoskTest {
    @Test
    fun voskText_readsTheRecognizedWords() {
        assertEquals("lilit che tempo fa", voskText("""{"text" : "lilit che tempo fa"}"""))
        assertEquals("", voskText("""{"text" : ""}"""))
        assertEquals("", voskText("""{"partial" : "ciao"}"""))
        assertEquals("", voskText("non è json"))
        assertEquals("", voskText(null))
    }

    @Test
    fun wakeFilter_acceptsHowVoskSpellsTheName() {
        // Vosk returns lower case text without punctuation.
        assertEquals(Wake.Ask("che tempo fa"), WakeFilter().hear("lilit che tempo fa", 0))
        assertEquals(Wake.Ask("cosa ho oggi"), WakeFilter().hear("lily cosa ho oggi", 0))
        assertEquals(Wake.Ask("che ore sono"), WakeFilter().hear("li lit che ore sono", 0))
        assertEquals(Wake.Listening, WakeFilter().hear("lilla", 0))
        assertEquals(Wake.Ignore, WakeFilter().hear("liliana ha chiamato", 0))
    }

    @Test
    fun zipTarget_staysInsideTheFolder() {
        val root = File("build/tmp/zip-root")

        assertNotNull(zipTarget(root, "model/am/final.mdl"))
        assertNotNull(zipTarget(root, "model/"))
        assertNull(zipTarget(root, "../evil.txt"))
        assertNull(zipTarget(root, "model/../../evil.txt"))
    }
}
