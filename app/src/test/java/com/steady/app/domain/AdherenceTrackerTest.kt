package com.steady.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AdherenceTrackerTest {

    private val start: LocalDate = LocalDate.of(2026, 1, 5)

    @Test
    fun `no history means no streak`() {
        val streak = AdherenceTracker.currentStreakWeeks(start, emptyList(), asOfDate = start)
        assertEquals(0, streak)
    }

    @Test
    fun `a week in progress with no session yet does not break a prior streak`() {
        // Week 0 has a session, week 1 (in progress, asOfDate) has none yet.
        val history = listOf(start.plusDays(1))
        val asOf = start.plusDays(9) // in week 1
        val streak = AdherenceTracker.currentStreakWeeks(start, history, asOfDate = asOf)
        assertEquals(1, streak)
    }

    @Test
    fun `a gap week breaks the streak`() {
        // Week 0 has a session, week 1 has nothing, week 2 (asOfDate) has a session.
        val history = listOf(start.plusDays(1), start.plusDays(15))
        val asOf = start.plusDays(15)
        val streak = AdherenceTracker.currentStreakWeeks(start, history, asOfDate = asOf)
        assertEquals(1, streak)
    }

    @Test
    fun `three consecutive weeks with sessions give a streak of three`() {
        val history = listOf(start, start.plusDays(8), start.plusDays(16))
        val asOf = start.plusDays(16)
        val streak = AdherenceTracker.currentStreakWeeks(start, history, asOfDate = asOf)
        assertEquals(3, streak)
    }

    @Test
    fun `sessions this week counts only the current seven-day block`() {
        val history = listOf(start, start.plusDays(1), start.plusDays(8))
        val count = AdherenceTracker.sessionsThisWeek(start, history, asOfDate = start.plusDays(2))
        assertEquals(2, count)
    }

    @Test
    fun `a week meets strong threshold only at 75 percent or better of the prescribed dose`() {
        val strongWeek = listOf(start, start.plusDays(1), start.plusDays(2)) // 3 of 3
        assertTrue(
            AdherenceTracker.isThisWeekStrong(start, strongWeek, asOfDate = start, prescribedPerWeek = 3),
        )

        val weakWeek = listOf(start, start.plusDays(1)) // 2 of 3, 66%
        assertFalse(
            AdherenceTracker.isThisWeekStrong(start, weakWeek, asOfDate = start, prescribedPerWeek = 3),
        )
    }

    // --- the two numbers the missed-session work needed ------------------------------

    @Test
    fun `nothing completed means no gap to measure, which is not the same as a long gap`() {
        assertNull(AdherenceTracker.daysSinceLastSession(emptyList(), asOfDate = start.plusDays(30)))
    }

    @Test
    fun `a session today is a gap of zero`() {
        val today = start.plusDays(4)
        assertEquals(0, AdherenceTracker.daysSinceLastSession(listOf(start, today), asOfDate = today))
    }

    @Test
    fun `the gap is measured from the most recent session, not the first`() {
        val history = listOf(start, start.plusDays(3), start.plusDays(10))
        assertEquals(5, AdherenceTracker.daysSinceLastSession(history, asOfDate = start.plusDays(15)))
    }

    @Test
    fun `dates after today are ignored rather than trusted`() {
        // A device whose clock moved backwards must not produce a negative gap.
        val history = listOf(start, start.plusDays(40))
        assertEquals(6, AdherenceTracker.daysSinceLastSession(history, asOfDate = start.plusDays(6)))
    }

    @Test
    fun `there is no elapsed week to look at during the first week`() {
        assertNull(
            AdherenceTracker.sessionsInLastElapsedWeek(start, listOf(start), asOfDate = start.plusDays(3)),
        )
    }

    @Test
    fun `the last elapsed week is the one that just finished`() {
        val history = listOf(start.plusDays(1), start.plusDays(3), start.plusDays(9))
        assertEquals(
            2,
            AdherenceTracker.sessionsInLastElapsedWeek(start, history, asOfDate = start.plusDays(9)),
        )
    }

    @Test
    fun `a paused week is skipped when looking back for the last elapsed week`() {
        // Week 0 had two sessions, week 1 was a declared pause, and today is in week 2.
        val history = listOf(start.plusDays(1), start.plusDays(3))
        assertEquals(
            2,
            AdherenceTracker.sessionsInLastElapsedWeek(
                start,
                history,
                asOfDate = start.plusDays(15),
                excludedWeeks = setOf(1),
            ),
        )
    }

    @Test
    fun `a programme paused for its whole history has no elapsed week to report`() {
        assertNull(
            AdherenceTracker.sessionsInLastElapsedWeek(
                start,
                listOf(start),
                asOfDate = start.plusDays(15),
                excludedWeeks = setOf(0, 1),
            ),
        )
    }
}
