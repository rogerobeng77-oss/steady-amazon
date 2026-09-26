package com.steady.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The evaluator takes `asOfDate` rather than reading a clock, which is the whole point:
 * a fortnight of somebody's programme runs here in under a millisecond, and the same
 * function runs on the television. Nothing in this file waits for anything.
 */
class CatchUpEvaluatorTest {

    private val start: LocalDate = LocalDate.of(2026, 3, 2) // a Monday
    private fun day(n: Int) = start.plusDays(n.toLong())

    // --- the four silences -------------------------------------------------------

    @Test
    fun `says nothing before setup`() {
        val notice = CatchUpEvaluator.evaluate(
            programStart = null,
            completedDates = emptyList(),
            asOfDate = day(30),
        )
        assertNull(notice)
    }

    @Test
    fun `says nothing while paused, however long the gap`() {
        val notice = CatchUpEvaluator.evaluate(
            programStart = start,
            completedDates = listOf(day(0)),
            asOfDate = day(40),
            isPaused = true,
        )
        assertNull(notice)
    }

    @Test
    fun `says nothing to somebody who has never started`() {
        // Home already reads "0 of 3 sessions done this week" above a Start button.
        // Repeating that is a nag carrying no new information.
        val notice = CatchUpEvaluator.evaluate(
            programStart = start,
            completedDates = emptyList(),
            asOfDate = day(12),
        )
        assertNull(notice)
    }

    @Test
    fun `says nothing about a week the person declared as a pause`() {
        val pause = PausePeriod(day(7).toEpochDay(), day(13).toEpochDay(), PauseReason.UNWELL)
        val excluded = PauseTracker.excludedWeekIndices(start, listOf(pause), day(10))
        val notice = CatchUpEvaluator.evaluate(
            programStart = start,
            completedDates = listOf(day(0)),
            asOfDate = day(10),
            excludedWeeks = excluded,
        )
        assertNull(notice)
    }

    // --- the quiet stretch -------------------------------------------------------

    @Test
    fun `one or two quiet days are the programme working normally`() {
        // Three sessions a week is roughly every other day. Neither of these is a gap.
        assertNull(
            CatchUpEvaluator.evaluate(start, listOf(day(0)), asOfDate = day(1)),
        )
        assertNull(
            CatchUpEvaluator.evaluate(start, listOf(day(0)), asOfDate = day(2)),
        )
    }

    @Test
    fun `the third quiet day is the first one worth mentioning`() {
        val notice = CatchUpEvaluator.evaluate(start, listOf(day(0)), asOfDate = day(3))
        assertNotNull(notice)
        assertEquals(CatchUpReason.QUIET_STRETCH, notice!!.reason)
        assertEquals(3, notice.daysSinceLastSession)
        assertEquals(day(0).toEpochDay(), notice.lastSessionEpochDay)
    }

    @Test
    fun `walking the clock forward turns silence into a notice on exactly one day`() {
        val history = listOf(day(0))
        val firstDayWithANotice = (0..10).first { offset ->
            CatchUpEvaluator.evaluate(start, history, asOfDate = day(offset)) != null
        }
        assertEquals(CatchUpEvaluator.QUIET_DAYS, firstDayWithANotice)
    }

    @Test
    fun `the gap keeps counting up for as long as it is real`() {
        val notice = CatchUpEvaluator.evaluate(start, listOf(day(0)), asOfDate = day(9))
        assertEquals(9, notice!!.daysSinceLastSession)
    }

    @Test
    fun `a session today clears the notice entirely`() {
        val history = listOf(day(0), day(9))
        assertNull(CatchUpEvaluator.evaluate(start, history, asOfDate = day(9)))
    }

    @Test
    fun `a clock that moved backwards cannot produce a gap from the future`() {
        // Dates after asOfDate are ignored rather than trusted.
        val history = listOf(day(0), day(20))
        val notice = CatchUpEvaluator.evaluate(start, history, asOfDate = day(5))
        assertEquals(5, notice!!.daysSinceLastSession)
    }

    // --- the short week ----------------------------------------------------------

    @Test
    fun `a finished week below the dose is raised once the new week has not started`() {
        // Week 0: one session out of three. Day 7 is the first day of week 1 and the
        // gap is only two days, so the quiet-stretch rule has not fired.
        val history = listOf(day(5))
        val notice = CatchUpEvaluator.evaluate(start, history, asOfDate = day(7))
        assertNotNull(notice)
        assertEquals(CatchUpReason.SHORT_WEEK, notice!!.reason)
        assertEquals(1, notice.sessionsInLastElapsedWeek)
        assertEquals(3, notice.prescribedPerWeek)
    }

    @Test
    fun `a finished week at full dose says nothing`() {
        val history = listOf(day(1), day(3), day(5))
        assertNull(CatchUpEvaluator.evaluate(start, history, asOfDate = day(6)))
        assertNull(CatchUpEvaluator.evaluate(start, history, asOfDate = day(7)))
    }

    @Test
    fun `a short week is not mentioned to somebody who has already restarted`() {
        // Week 0 was thin, but there is a session in week 1 already. Telling them now
        // is scorekeeping.
        val history = listOf(day(5), day(7))
        assertNull(CatchUpEvaluator.evaluate(start, history, asOfDate = day(8)))
    }

    @Test
    fun `the first week of a programme has no elapsed week to report on`() {
        val history = listOf(day(0))
        // Day 2: gap of two, week 0 still in progress. Nothing to say.
        assertNull(CatchUpEvaluator.evaluate(start, history, asOfDate = day(2)))
    }

    @Test
    fun `a quiet stretch outranks a short week when both are true`() {
        // Week 0 had one session; it is now day 10 with nothing since day 2.
        val history = listOf(day(2))
        val notice = CatchUpEvaluator.evaluate(start, history, asOfDate = day(10))
        assertEquals(CatchUpReason.QUIET_STRETCH, notice!!.reason)
        // The short week is still carried, so the caregiver summary can use it.
        assertEquals(1, notice.sessionsInLastElapsedWeek)
    }

    @Test
    fun `a pause week is skipped when looking back for the last elapsed week`() {
        // Week 0: three sessions. Week 1: declared pause, nothing. Week 2 day 14: the
        // week the rule looks back at is week 0, which was fine.
        val pause = PausePeriod(day(7).toEpochDay(), day(13).toEpochDay(), PauseReason.AWAY)
        val excluded = PauseTracker.excludedWeekIndices(start, listOf(pause), day(14))
        val history = listOf(day(1), day(3), day(5))
        val notice = CatchUpEvaluator.evaluate(
            programStart = start,
            completedDates = history,
            asOfDate = day(14),
            excludedWeeks = excluded,
        )
        // Nine days since the last session, so this is a quiet stretch and not a short
        // week — but the week it reports on is week 0's three, never the paused week's
        // zero.
        assertEquals(CatchUpReason.QUIET_STRETCH, notice!!.reason)
        assertEquals(3, notice.sessionsInLastElapsedWeek)
    }
}
