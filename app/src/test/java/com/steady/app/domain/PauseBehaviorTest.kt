package com.steady.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** The pause feature exists because a single fully-empty week otherwise demotes a
 * standing-tier person, and the most likely reason for an empty week in this population
 * is a hospital stay, an illness, or a trip to see family. These tests exist to prove
 * that a declared pause cannot do what an ordinary lapse does: it must never drop a
 * tier, and it must never break a streak. */
class PauseBehaviorTest {

    private val start: LocalDate = LocalDate.of(2026, 1, 5)

    @Test
    fun `an excluded week never triggers the zero-session regression`() {
        // Weeks 0 and 1 strong enough to be on standing; week 2 is a declared pause with
        // zero sessions, which would ordinarily regress the tier back to chair-supported.
        val week0 = listOf(start, start.plusDays(1), start.plusDays(3))
        val week1 = listOf(start.plusDays(7), start.plusDays(9), start.plusDays(11))
        val history = week0 + week1
        val pausedWeekIndex = 2
        val asOf = start.plusDays(22) // in week 3

        val tier = ProgramEngine.tierFor(
            start,
            history,
            asOfDate = asOf,
            excludedWeeks = setOf(pausedWeekIndex),
        )
        assertEquals(Tier.STANDING, tier)
    }

    @Test
    fun `without the exclusion the same history would have regressed`() {
        val week0 = listOf(start, start.plusDays(1), start.plusDays(3))
        val week1 = listOf(start.plusDays(7), start.plusDays(9), start.plusDays(11))
        val history = week0 + week1
        val asOf = start.plusDays(22)

        val tier = ProgramEngine.tierFor(start, history, asOfDate = asOf)
        assertEquals(Tier.CHAIR_SUPPORTED, tier)
    }

    @Test
    fun `a real lapse right after a pause still regresses normally`() {
        // Week 0 and 1 strong, week 2 paused (excluded), week 3 genuinely empty (not
        // excluded): the regression rule must still fire for a real lapse.
        val week0 = listOf(start, start.plusDays(1), start.plusDays(3))
        val week1 = listOf(start.plusDays(7), start.plusDays(9), start.plusDays(11))
        val history = week0 + week1
        val asOf = start.plusDays(29) // in week 4, week 3 fully elapsed with nothing logged

        val tier = ProgramEngine.tierFor(
            start,
            history,
            asOfDate = asOf,
            excludedWeeks = setOf(2),
        )
        assertEquals(Tier.CHAIR_SUPPORTED, tier)
    }

    @Test
    fun `a streak survives a pause spanning multiple weeks`() {
        val week0 = listOf(start)
        val week1 = listOf(start.plusDays(7))
        val week2 = listOf(start.plusDays(14))
        val history = week0 + week1 + week2
        // weeks 3 and 4 paused, asOfDate in week 5 with nothing logged yet today
        val asOf = start.plusDays(35)

        val streak = AdherenceTracker.currentStreakWeeks(
            start,
            history,
            asOfDate = asOf,
            excludedWeeks = setOf(3, 4),
        )
        assertEquals(3, streak)
    }

    @Test
    fun `PauseTracker computes every week index an ongoing pause has touched`() {
        val pauseStart = start.plusDays(14) // week 2
        val period = PausePeriod(pauseStart.toEpochDay(), endEpochDay = null, reason = PauseReason.UNWELL)
        val asOf = start.plusDays(29) // week 4, pause still ongoing

        val excluded = PauseTracker.excludedWeekIndices(start, listOf(period), asOf)
        assertEquals(setOf(2, 3, 4), excluded)
    }

    @Test
    fun `PauseTracker computes the week indices of a closed pause`() {
        val pauseStart = start.plusDays(7) // week 1
        val pauseEnd = start.plusDays(20) // week 2
        val period = PausePeriod(pauseStart.toEpochDay(), pauseEnd.toEpochDay(), PauseReason.AWAY)

        val excluded = PauseTracker.excludedWeekIndices(start, listOf(period), asOfDate = start.plusDays(60))
        assertEquals(setOf(1, 2), excluded)
    }
}
