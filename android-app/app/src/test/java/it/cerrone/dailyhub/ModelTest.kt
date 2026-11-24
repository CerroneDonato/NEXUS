package it.cerrone.dailyhub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

class ModelTest {
    private val sample = """
        {"date":"2026-11-10","weather":{"city":"Milano","current":{"temperature":9.4,"description":"Coperto",
         "icon":"rain","isDay":false},
         "today":{"min":6.1,"max":14.2,"precipitationProbability":70}},"weatherError":null,
         "events":[{"title":"Compleanno","start":"2026-11-10","end":"2026-11-11","allDay":true,"location":null},
                   {"title":"Riunione","start":"2026-11-10T10:00:00+01:00","end":"2026-11-10T11:00:00+01:00",
                    "allDay":false,"location":"Sala Duomo"},
                   {"title":"Palestra","start":"2026-11-10T18:30:00+01:00","end":"2026-11-10T19:30:00+01:00",
                    "allDay":false,"location":null}],
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
        assertEquals("rain", snapshot.weather?.icon)
        assertEquals(false, snapshot.weather?.isDay)
        assertEquals(3, snapshot.events.size)
        assertEquals("Tutto il giorno", snapshot.events[0].timeLabel)
        assertEquals("Sala Duomo", snapshot.events[1].location)
        assertNull(snapshot.events[2].location)
        assertTrue(snapshot.tasks[0].overdue)
        assertNull(snapshot.tasks[1].due)
        assertNull(snapshot.weatherError)
        assertEquals("2026-11-11T07:00:00+01:00", snapshot.nextAlarm)
    }

    @Test
    fun skyFor_followsWeatherAndDaylight() {
        assertEquals(Sky.CLEAR_DAY, skyFor("clear", isDay = true))
        assertEquals(Sky.CLEAR_NIGHT, skyFor("partly-cloudy", isDay = false))
        assertEquals(Sky.RAIN, skyFor("drizzle", isDay = true))
        assertEquals(Sky.STORM, skyFor("storm", isDay = false))
        assertEquals(Sky.CLOUDY_NIGHT, skyFor(null, isDay = false))
    }

    @Test
    fun focusEvent_prefersOngoingThenNextAndSkipsAllDay() {
        val events = parseToday(sample).events

        val during = focusEvent(events, OffsetDateTime.parse("2026-11-10T10:30:00+01:00"))
        assertEquals("Riunione", during?.event?.title)
        assertTrue(during!!.ongoing)

        val before = focusEvent(events, OffsetDateTime.parse("2026-11-10T08:00:00+01:00"))
        assertEquals("Riunione", before?.event?.title)
        assertFalse(before!!.ongoing)

        val afternoon = focusEvent(events, OffsetDateTime.parse("2026-11-10T12:00:00+01:00"))
        assertEquals("Palestra", afternoon?.event?.title)

        assertNull(focusEvent(events, OffsetDateTime.parse("2026-11-10T21:00:00+01:00")))
    }

    @Test
    fun parseChatResult_readsReplyAndActions() {
        val result = parseChatResult("""{"reply":"Fatto.","actions":["Task creato: Latte"]}""")

        assertEquals("Fatto.", result.reply)
        assertEquals(listOf("Task creato: Latte"), result.actions)
        assertEquals(AssistantInfo("Lilith", false), parseAssistantInfo("""{"name":"Lilith","enabled":false}"""))
    }

    @Test
    fun errorDetail_onlyReturnsStringDetails() {
        assertEquals("Assistente non configurato", errorDetail("""{"detail":"Assistente non configurato"}"""))
        assertNull(errorDetail("""{"detail":[{"msg":"too long"}]}"""))
        assertNull(errorDetail("<html>"))
    }

    @Test
    fun dueLabel_coversOverdueTodayTomorrowAndLater() {
        val today = LocalDate.of(2026, 11, 11)
        fun task(due: LocalDate?, overdue: Boolean = false) = TaskInfo(1, "t", due, overdue, false)

        assertNull(dueLabel(task(null), today))
        assertEquals("Scaduto", dueLabel(task(today.minusDays(2), overdue = true), today))
        assertEquals("Oggi", dueLabel(task(today), today))
        assertEquals("Domani", dueLabel(task(today.plusDays(1)), today))
        assertEquals("20/11", dueLabel(task(LocalDate.of(2026, 11, 20)), today))
    }

    @Test
    fun greetingAndDate_areItalian() {
        assertEquals("Buongiorno", greetingFor(7))
        assertEquals("Buon pomeriggio", greetingFor(16))
        assertEquals("Buonasera", greetingFor(21))
        assertEquals("Buonanotte", greetingFor(2))
        assertEquals("Mercoledì 11 novembre", italianDate(LocalDate.of(2026, 11, 11)))
    }

    @Test
    fun speakable_turnsListsIntoPauses() {
        val reply = "Oggi hai 2 impegni:\n- 15:30 Review\n- 18:30 Palestra\nIl prossimo è la palestra."

        assertEquals("Oggi hai 2 impegni: 15:30 Review. 18:30 Palestra. Il prossimo è la palestra.", speakable(reply))
    }

    @Test
    fun parseToday_readsTheWeekAndSkipsIncompleteDays() {
        val json = """
            {"date":"2026-11-12","weather":{"city":"Milano","current":{"temperature":12.0},"today":{},
             "daily":[{"date":"2026-11-12","min":7.5,"max":15.2,"precipitationProbability":10,"description":"Sereno","icon":"clear"},
                      {"date":"2026-11-13","min":null,"max":14.0,"precipitationProbability":40,"description":"Pioggia","icon":"rain"},
                      {"date":"2026-11-14","min":6.0,"max":11.0,"precipitationProbability":80,"description":"Pioggia","icon":"rain"}]},
             "events":[],"tasks":[]}
        """.trimIndent()

        val days = parseToday(json).weather!!.daily

        assertEquals(listOf(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 14)), days.map { it.date })
        assertEquals(80, days[1].precipitationProbability)
        assertEquals("rain", days[1].icon)
        assertTrue(parseToday(sample).weather!!.daily.isEmpty())
    }

    @Test
    fun dayAndRainLabels_followWeatherAppConventions() {
        val today = LocalDate.of(2026, 11, 12)

        assertEquals("Oggi", dayLabel(today, today))
        assertEquals("Ven", dayLabel(today.plusDays(1), today))
        assertEquals("13 nov", dayMonthLabel(today.plusDays(1)))
        assertNull(rainLabel(10))
        assertNull(rainLabel(null))
        assertEquals("20%", rainLabel(20))
    }

    @Test
    fun tempArgb_goesFromColdBlueToWarmOrange() {
        assertEquals(0xFF7CAAFF.toInt(), tempArgb(-10.0))
        assertEquals(0xFF6FE3C4.toInt(), tempArgb(12.0))
        assertEquals(0xFFFF965A.toInt(), tempArgb(35.0))
    }

    @Test
    fun reminderPlan_onlyTimedEventsWithTheLeadStillAhead() {
        val events = parseToday(sample).events
        val now = OffsetDateTime.parse("2026-11-10T09:50:00+01:00")

        val plan = reminderPlan(events, now)

        assertEquals(listOf("Palestra"), plan.map { it.title })
        assertEquals(OffsetDateTime.parse("2026-11-10T18:15:00+01:00").toInstant().toEpochMilli(), plan[0].atMillis)
        assertTrue(plan[0].text.startsWith("Tra 15 minuti · "))
        assertEquals(1, reminderPlan(events, OffsetDateTime.parse("2026-11-10T09:30:00+01:00")).count { it.title == "Riunione" })
        assertTrue(reminderPlan(events, OffsetDateTime.parse("2026-11-10T09:30:00+01:00"))
            .first { it.title == "Riunione" }.text.endsWith(" · Sala Duomo"))
    }

    @Test
    fun reminderPlan_usesTheLeadChosenInSettings() {
        val events = parseToday(sample).events
        val plan = reminderPlan(events, OffsetDateTime.parse("2026-11-10T09:50:00+01:00"), leadMinutes = 30)

        val gym = plan.first { it.title == "Palestra" }
        assertEquals(OffsetDateTime.parse("2026-11-10T18:00:00+01:00").toInstant().toEpochMilli(), gym.atMillis)
        assertTrue(gym.text.startsWith("Tra 30 minuti · "))
    }

    @Test
    fun briefingText_summarisesWeatherAgendaAndTasks() {
        val snapshot = parseToday(sample)

        val (title, lines) = briefingText(snapshot, OffsetDateTime.parse("2026-11-10T07:01:00+01:00"))

        assertEquals("Buongiorno · 9° coperto", title)
        assertEquals("6° / 14° · pioggia al 70%, meglio l'ombrello", lines[0])
        assertTrue(lines[1].endsWith(" Riunione · altri 1"))
        assertEquals("Task: 1 scaduto", lines[2])
    }

    @Test
    fun isClockApp_acceptsClocksAndRejectsOtherApps() {
        assertTrue(isClockApp("com.oneplus.deskclock"))
        assertTrue(isClockApp("com.coloros.alarmclock"))
        assertTrue(isClockApp("com.google.android.deskclock"))
        assertTrue(isClockApp(null))
        assertFalse(isClockApp("com.google.android.calendar"))
        assertFalse(isClockApp("com.google.android.apps.wellbeing"))
    }

    @Test
    fun widgetRows_neverPlansMoreLinesThanFit() {
        assertEquals(WidgetRows(0, 0, showTasks = false), widgetRows(1, events = 3, tasks = 5))
        assertEquals(WidgetRows(0, 1, showTasks = true), widgetRows(2, events = 3, tasks = 5))
        assertEquals(WidgetRows(0, 2, showTasks = true), widgetRows(3, events = 2, tasks = 4))
        assertEquals(WidgetRows(2, 2, showTasks = true), widgetRows(6, events = 3, tasks = 5))
        assertEquals(WidgetRows(1, 3, showTasks = true), widgetRows(12, events = 1, tasks = 3))
        assertEquals(WidgetRows(3, 4, showTasks = true), widgetRows(20, events = 3, tasks = 4))
    }

    @Test
    fun toIso_includesSecondsAndOffset() {
        val iso = AlarmSync.toIso(1_794_376_800_000L, ZoneId.of("Europe/Rome"))

        assertTrue(iso.matches(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\+0[12]:00""")))
    }

    @Test
    fun countdownLabel_isCompactItalianUntilTheEventStarts() {
        val now = OffsetDateTime.parse("2026-11-10T08:14:00+01:00")

        assertEquals("Tra 1 h 46 min", countdownLabel("2026-11-10T10:00:00+01:00", now))
        assertEquals("Tra 45 min", countdownLabel("2026-11-10T08:59:00+01:00", now))
        assertEquals("Tra 2 h", countdownLabel("2026-11-10T10:14:00+01:00", now))
        assertNull(countdownLabel("2026-11-10T08:14:00+01:00", now))
        assertNull(countdownLabel(null, now))
    }

    @Test
    fun parseToday_readsTheWeekAgendaAndSkipsBrokenDays() {
        val json = """
            {"date":"2026-11-12","events":[],"tasks":[],
             "week":[{"date":"2026-11-12","events":[{"title":"Review","start":"2026-11-12T15:30:00+01:00","end":"2026-11-12T16:30:00+01:00","allDay":false,"location":"Sala Duomo"}]},
                     {"date":"non-una-data","events":[]},
                     {"date":"2026-11-14","events":[{"title":"Compleanno","start":"2026-11-14","allDay":true,"location":null}]},
                     {"date":"2026-11-15","events":[]}]}
        """.trimIndent()

        val days = parseToday(json).week

        assertEquals(listOf(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 15)), days.map { it.date })
        assertEquals("Sala Duomo", days[0].events[0].location)
        assertFalse(days[0].events[0].allDay)
        assertTrue(days[0].events[0].timeLabel.matches(Regex("""\d{2}:\d{2}""")))
        assertEquals("Tutto il giorno", days[1].events[0].timeLabel)
        assertTrue(days[2].events.isEmpty())
        assertTrue(parseToday(sample).week.isEmpty())
    }

    @Test
    fun alarmKey_isTheLocalTimeOfDay() {
        val rome = ZoneId.of("Europe/Rome")
        val eight = OffsetDateTime.parse("2026-11-14T08:00:00+01:00").toInstant().toEpochMilli()
        val nextDay = OffsetDateTime.parse("2026-11-15T08:00:30+01:00").toInstant().toEpochMilli()

        assertEquals("08:00", alarmKey(eight, rome))
        assertEquals("08:00", alarmKey(nextDay, rome))
        assertEquals("07:00", alarmKey(eight, ZoneId.of("UTC")))
    }

    @Test
    fun parsesChatHistory() {
        val json = """[{"id":"android-1","title":"Che tempo fa?","startedAt":"2026-11-13T09:00:00+01:00",
            "lastAt":"2026-11-13T09:05:00+01:00","messages":4,"source":"phone"},
            {"id":"","title":"x","lastAt":"","messages":0,"source":"pc"}]"""

        val items = parseHistory(json)

        assertEquals(1, items.size)
        assertEquals(ChatSummary("android-1", "Che tempo fa?", "2026-11-13T09:05:00+01:00", 4, "phone"), items[0])
        assertEquals("Telefono", sourceLabel("phone"))
        assertEquals("Sport", sourceLabel("sport"))
        assertEquals("PC", sourceLabel("altro"))
    }

    @Test
    fun parsesStoredMessages() {
        val json = """{"id":"c","messages":[
            {"at":"a","role":"user","text":"Ciao","actions":[]},
            {"at":"b","role":"bot","text":"Fatto","actions":["Task creato: Latte"]}]}"""

        val messages = parseStoredMessages(json)

        assertEquals(listOf(true, false), messages.map { it.fromUser })
        assertEquals("Fatto", messages[1].text)
        assertEquals(listOf("Task creato: Latte"), messages[1].actions)
        assertTrue(parseStoredMessages("""{"id":"c"}""").isEmpty())
    }

    @Test
    fun historyDatesAreRelativeToToday() {
        val now = OffsetDateTime.parse("2026-11-13T15:00:00+01:00")

        assertEquals("09:05", historyWhenLabel("2026-11-13T09:05:00+01:00", now))
        assertEquals("Ieri", historyWhenLabel("2026-11-12T23:50:00+01:00", now))
        assertEquals("9 nov", historyWhenLabel("2026-11-09T10:00:00+01:00", now))
        assertEquals("", historyWhenLabel("non-una-data", now))
    }
}
