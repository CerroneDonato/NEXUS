package it.cerrone.dailyhub

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarAccountTest {
    @Test
    fun keepsOnlyGoogleAccountsOnceAndSorted() {
        val rows = listOf(
            "b@example.com" to "com.google",
            "a@example.com" to "com.google",
            "b@example.com" to "com.google",
            "Samsung" to "com.osp.app",
            "work@corp.example" to "com.exchange",
            "" to "com.google",
        )
        assertEquals(listOf("a@example.com", "b@example.com"), googleAccountNames(rows))
    }

    @Test
    fun noGoogleAccountGivesEmptyList() {
        assertEquals(emptyList<String>(), googleAccountNames(listOf("work@corp.example" to "com.exchange")))
    }
}
