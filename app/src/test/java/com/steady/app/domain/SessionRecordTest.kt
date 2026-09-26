package com.steady.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SessionRecordTest {

    private val day = LocalDate.of(2026, 9, 22)

    @Test
    fun `a record survives a round trip through storage`() {
        val record = SessionRecord(day, secondsElapsed = 731, movementsDone = 5, movementsPlanned = 5, feedback = SessionFeedback.JUST_RIGHT)
        assertEquals(record, SessionRecord.decode(record.encode()))
    }

    @Test
    fun `a session nobody answered the question about round trips as unanswered`() {
        val record = SessionRecord(day, 400, 4, 5, feedback = null)
        assertEquals(record, SessionRecord.decode(record.encode()))
        assertNull(SessionRecord.decode(record.encode())!!.feedback)
    }

    @Test
    fun `unreadable stored values are dropped rather than thrown`() {
        // Each of these has been a real shape at some point: a value written by an older
        // build, a truncated write, a feedback constant that has since been renamed.
        listOf(
            "",
            "20718|600|5",
            "not-a-day|600|5|5|JUST_RIGHT",
            "20718|600|5|5|MIDDLING",
            "20718|-60|5|5|JUST_RIGHT",
        ).forEach { encoded ->
            val decoded = SessionRecord.decode(encoded)
            if (encoded == "20718|600|5|5|MIDDLING") {
                // A feedback word we no longer know is not a reason to lose the session.
                assertNull("feedback in $encoded", decoded?.feedback)
                assertEquals(5, decoded?.movementsDone)
            } else {
                assertNull("decoded $encoded", decoded)
            }
        }
    }

    @Test
    fun `history is newest first and keeps days that have no detail`() {
        val dates = listOf(
            LocalDate.of(2026, 9, 18),
            LocalDate.of(2026, 9, 20),
            LocalDate.of(2026, 9, 22),
        )
        val records = listOf(SessionRecord(LocalDate.of(2026, 9, 22), 600, 5, 5, SessionFeedback.TOO_EASY))

        val rows = SessionHistory.rows(dates, records)

        assertEquals(listOf(dates[2], dates[1], dates[0]), rows.map { it.date })
        assertEquals(SessionFeedback.TOO_EASY, rows[0].detail?.feedback)
        assertNull(rows[1].detail)
        assertNull(rows[2].detail)
    }

    @Test
    fun `a record for a day that never counted as a session does not appear`() {
        // The dates are what the programme is computed from. A stray record must not be
        // able to put a row into the record of what counted.
        val rows = SessionHistory.rows(
            completedDates = listOf(LocalDate.of(2026, 9, 22)),
            records = listOf(
                SessionRecord(LocalDate.of(2026, 9, 22), 600, 5, 5, null),
                SessionRecord(LocalDate.of(2026, 9, 21), 600, 5, 5, null),
            ),
        )
        assertEquals(1, rows.size)
        assertEquals(LocalDate.of(2026, 9, 22), rows[0].date)
    }

    @Test
    fun `a day recorded twice is one row`() {
        val rows = SessionHistory.rows(
            completedDates = listOf(day, day),
            records = emptyList(),
        )
        assertEquals(1, rows.size)
    }

    @Test
    fun `durations are rounded to minutes and never to zero`() {
        assertEquals("under a minute", SessionPhrasing.durationLabel(0))
        assertEquals("under a minute", SessionPhrasing.durationLabel(44))
        assertEquals("1 minute", SessionPhrasing.durationLabel(45))
        assertEquals("1 minute", SessionPhrasing.durationLabel(89))
        assertEquals("2 minutes", SessionPhrasing.durationLabel(90))
        assertEquals("12 minutes", SessionPhrasing.durationLabel(731))
    }

    @Test
    fun `the history says what the feedback screen said`() {
        assertEquals("Too easy", SessionPhrasing.feltLabel(SessionFeedback.TOO_EASY))
        assertEquals("Just right", SessionPhrasing.feltLabel(SessionFeedback.JUST_RIGHT))
        assertEquals("Too much", SessionPhrasing.feltLabel(SessionFeedback.TOO_MUCH))
        assertEquals("Not answered", SessionPhrasing.feltLabel(null))
    }

    @Test
    fun `movements are figures, so a column of them can be read down`() {
        assertEquals("5 of 5", SessionPhrasing.movementsLabel(5, 5))
        assertEquals("3 of 5", SessionPhrasing.movementsLabel(3, 5))
    }

    @Test
    fun `the answer attaches to the session it was given about`() {
        val record = SessionRecord(day, 600, 5, 5, feedback = null)
        assertEquals(SessionFeedback.TOO_MUCH, record.withFeedback(SessionFeedback.TOO_MUCH).feedback)
        assertEquals(600, record.withFeedback(SessionFeedback.TOO_MUCH).secondsElapsed)
    }
}
