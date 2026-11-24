package it.cerrone.dailyhub

import org.junit.Assert.assertEquals
import org.junit.Test

class HubAddressTest {
    @Test
    fun blankStaysBlankSoThePhoneCanSearch() {
        assertEquals("", hubAddress("  "))
    }

    @Test
    fun aBareIpGetsSchemeAndPort() {
        assertEquals("http://192.168.1.20:8765", hubAddress("192.168.1.20"))
        assertEquals("http://192.168.1.20:8765", hubAddress("http://192.168.1.20"))
        assertEquals("http://192.168.1.20:9000", hubAddress("192.168.1.20:9000"))
    }
}
