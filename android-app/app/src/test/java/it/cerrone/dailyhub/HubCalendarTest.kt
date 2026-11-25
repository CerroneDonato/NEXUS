package it.cerrone.dailyhub

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HubCalendarTest {
    private val today: LocalDate = LocalDate.of(2026, 11, 25)

    private fun event(title: String) = JSONObject().put("title", title).put("allDay", false)

    private fun day(date: LocalDate, vararg titles: String): JSONObject {
        val events = JSONArray()
        titles.forEach { events.put(event(it)) }
        return JSONObject().put("date", date.toString()).put("events", events)
    }

    private fun weekFrom(start: LocalDate, count: Int, titles: Map<LocalDate, List<String>> = emptyMap()): JSONArray {
        val week = JSONArray()
        for (i in 0 until count) {
            val date = start.plusDays(i.toLong())
            week.put(day(date, *titles[date].orEmpty().toTypedArray()))
        }
        return week
    }

    @Test
    fun takesTodayEventsAndWeekFromThePc() {
        val hub = JSONObject()
            .put("calendarError", JSONObject.NULL)
            .put("week", weekFrom(today, 7, mapOf(today to listOf("Standup"), today.plusDays(2) to listOf("Dentista"))))
        val part = HubCalendar.fromHub(hub, today)

        assertNull(part.error)
        assertNull(part.note)
        assertEquals(1, part.events.length())
        assertEquals("Standup", part.events.getJSONObject(0).getString("title"))
        assertEquals(7, part.week!!.length())
    }

    @Test
    fun passesTheErrorOfThePcAndHasNoWeek() {
        val hub = JSONObject().put("calendarError", "Google Calendar non collegato sul PC").put("week", JSONObject.NULL)
        val part = HubCalendar.fromHub(hub, today)

        assertEquals("Google Calendar non collegato sul PC", part.error)
        assertNull(part.week)
        assertEquals(0, part.events.length())
    }

    @Test
    fun withoutAWeekFromThePcTheCalendarIsUnavailable() {
        val part = HubCalendar.fromHub(JSONObject().put("calendarError", JSONObject.NULL), today)

        assertNotNull(part.error)
        assertNull(part.week)
    }

    @Test
    fun pcDownKeepsTheLastWeekFromTodayOnWithANote() {
        val yesterday = today.minusDays(1)
        val previous = JSONObject().put(
            "week",
            weekFrom(yesterday, 8, mapOf(yesterday to listOf("Ieri"), today to listOf("Oggi"), today.plusDays(3) to listOf("Dopo"))),
        )
        val part = HubCalendar.fromLastSnapshot(previous, today)

        assertNull(part.error)
        assertNotNull(part.note)
        assertEquals(7, part.week!!.length())
        assertEquals("Oggi", part.events.getJSONObject(0).getString("title"))
    }

    @Test
    fun pcDownWithoutSavedWeekSaysItIsUnavailable() {
        val part = HubCalendar.fromLastSnapshot(null, today)

        assertNotNull(part.error)
        assertNull(part.week)
        assertNull(part.note)
    }

    @Test
    fun pcDownWithOnlyPastDaysSavedIsUnavailable() {
        val previous = JSONObject().put("week", weekFrom(today.minusDays(10), 3))
        val part = HubCalendar.fromLastSnapshot(previous, today)

        assertNotNull(part.error)
        assertNull(part.week)
    }

    @Test
    fun notConnectedAsksToLinkThePc() {
        val part = HubCalendar.notConnected()

        assertNotNull(part.error)
        assertNull(part.week)
    }

    @Test
    fun onDayReturnsTheEventsOfADayInTheWeek() {
        val snapshot = JSONObject().put("week", weekFrom(today, 7, mapOf(today.plusDays(4) to listOf("Esame"))))
        val result = HubCalendar.onDay(snapshot, today.plusDays(4))

        assertFalse(result.has("error"))
        assertEquals(today.plusDays(4).toString(), result.getString("date"))
        assertEquals("Esame", result.getJSONArray("events").getJSONObject(0).getString("title"))
    }

    @Test
    fun onDayOutsideTheWeekGivesAnError() {
        val snapshot = JSONObject().put("week", weekFrom(today, 7))
        val result = HubCalendar.onDay(snapshot, today.plusDays(30))

        assertTrue(result.has("error"))
    }

    @Test
    fun onDayWithoutSnapshotGivesAnError() {
        assertTrue(HubCalendar.onDay(null, today).has("error"))
    }
}
