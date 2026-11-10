package it.cerrone.dailyhub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ModelTest {
    private val sample = """
        {"date":"2026-11-10","weather":{"city":"Milano","current":{"temperature":9.4,"description":"Coperto"},
         "today":{"min":6.1,"max":14.2,"precipitationProbability":70}},"weatherError":null,
         "events":[{"title":"Riunione","start":"2026-11-10T10:00:00+01:00","allDay":false},
                   {"title":"Compleanno","start":"2026-11-10","allDay":true}],
         "calendarError":null,
         "tasks":[{"id":1,"title":"Latte","due":"2026-11-09","done":false,"overdue":true},
                  {"id":2,"title":"Palestra","due":null,"done":false,"overdue":false}],
         "alarm":{"nextAlarm":"2026-11-11T07:00:00+01:00","wakeAt":null,"scheduled":false}}
    """.trimIndent()

    @Test
    fun parseToday_mapsAllSections() {
        val snapshot = parseToday(sample)

        assertEquals(LocalDate.of(2026, 11, 10), snapshot.date)
        assertEquals("Milano", snapshot.weather?.city)
        assertEquals(70, snapshot.weather?.precipitationProbability)
        assertEquals(2, snapshot.events.size)
        assertEquals("Tutto il giorno", snapshot.events[1].timeLabel)
        assertTrue(snapshot.tasks[0].overdue)
        assertNull(snapshot.tasks[1].due)
        assertNull(snapshot.weatherError)
        assertEquals("2026-11-11T07:00:00+01:00", snapshot.nextAlarm)
    }

    @Test
    fun toIso_includesSecondsAndOffset() {
        val iso = AlarmSync.toIso(1_794_376_800_000L, ZoneId.of("Europe/Rome"))

        assertTrue(iso.matches(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\+0[12]:00""")))
    }
}
