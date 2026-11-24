package it.cerrone.dailyhub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeTest {
    private val moment = "2026-11-19T10:00:00+01:00"

    private fun task(title: String, updatedAt: String = moment, deleted: Boolean = false) = TaskRecord(
        uid = "u", title = title, due = null, done = false, createdAt = moment,
        completedAt = null, updatedAt = updatedAt, deleted = deleted,
    )

    @Test
    fun tiePrefersThePc() {
        assertFalse(preferIncoming(moment, moment, incomingFromPc = false))
        assertTrue(preferIncoming(moment, moment, incomingFromPc = true))
        assertEquals("pc", mergeTask(task("pc"), task("phone"), incomingFromPc = false).title)
        assertEquals("pc", mergeTask(task("phone"), task("pc"), incomingFromPc = true).title)
    }

    @Test
    fun newerEditWins() {
        val later = "2026-11-19T11:00:00+01:00"
        assertEquals("phone", mergeTask(task("pc"), task("phone", later), incomingFromPc = false).title)
        assertTrue(mergeTask(task("alive"), task("gone", later, deleted = true), incomingFromPc = false).deleted)
        assertTrue(mergeTask(task("gone", later, deleted = true), task("alive", moment), incomingFromPc = false).deleted)
    }

    @Test
    fun aMissingTaskTakesTheIncomingOne() {
        assertEquals("phone", mergeTask(null, task("phone"), incomingFromPc = false).title)
    }

    @Test
    fun messagesAreAppendOnly() {
        val first = MessageRecord("m", "c", moment, "user", "prima", moment, deleted = false)
        val second = first.copy(text = "dopo")
        assertEquals("prima", mergeMessage(first, second).text)
        assertEquals("prima", mergeMessage(null, first).text)
    }
}
