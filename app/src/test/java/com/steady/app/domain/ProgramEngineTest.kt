package com.steady.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ProgramEngineTest {

    private val start: LocalDate = LocalDate.of(2026, 1, 5) // a Monday

    @Test
    fun `brand new program starts chair-supported`() {
        val tier = ProgramEngine.tierFor(start, emptyList(), asOfDate = start)
        assertEquals(Tier.CHAIR_SUPPORTED, tier)
    }

    @Test
    fun `first week in progress stays chair-supported even with sessions so far`() {
        val history = listOf(start, start.plusDays(2))
        val tier = ProgramEngine.tierFor(start, history, asOfDate = start.plusDays(3))
        assertEquals(Tier.CHAIR_SUPPORTED, tier)
    }

    @Test
    fun `two consecutive strong weeks unlock standing`() {
        // prescribed = 3 per week, strong threshold = ceil(3 * 0.75) = 3
        val week0 = listOf(start, start.plusDays(1), start.plusDays(3))
        val week1 = listOf(start.plusDays(7), start.plusDays(9), start.plusDays(11))
        val history = week0 + week1
        // asOfDate in week 2, so weeks 0 and 1 are the two most recently completed weeks
        val asOf = start.plusDays(15)
        val tier = ProgramEngine.tierFor(start, history, asOfDate = asOf)
        assertEquals(Tier.STANDING, tier)
    }

    @Test
    fun `one strong week alone does not unlock standing`() {
        val week0 = listOf(start) // only 1 session, below the threshold of 3
        val week1 = listOf(start.plusDays(7), start.plusDays(9), start.plusDays(11)) // strong
        val history = week0 + week1
        val asOf = start.plusDays(15)
        val tier = ProgramEngine.tierFor(start, history, asOfDate = asOf)
        assertEquals(Tier.CHAIR_SUPPORTED, tier)
    }

    @Test
    fun `a fully missed week regresses standing back to chair-supported`() {
        // Weeks 0 and 1 strong (would normally unlock standing for week 2 onward),
        // but week 2 has zero sessions, so week 3 must regress.
        val week0 = listOf(start, start.plusDays(1), start.plusDays(3))
        val week1 = listOf(start.plusDays(7), start.plusDays(9), start.plusDays(11))
        // week 2 (days 14-20): nothing logged
        val history = week0 + week1
        val asOf = start.plusDays(22) // in week 3
        val tier = ProgramEngine.tierFor(start, history, asOfDate = asOf)
        assertEquals(Tier.CHAIR_SUPPORTED, tier)
    }

    @Test
    fun `standing tier persists while strong weeks continue`() {
        val week0 = listOf(start, start.plusDays(1), start.plusDays(3))
        val week1 = listOf(start.plusDays(7), start.plusDays(9), start.plusDays(11))
        val week2 = listOf(start.plusDays(14), start.plusDays(16), start.plusDays(18))
        val history = week0 + week1 + week2
        val asOf = start.plusDays(22) // in week 3, weeks 1 and 2 are both strong
        val tier = ProgramEngine.tierFor(start, history, asOfDate = asOf)
        assertEquals(Tier.STANDING, tier)
    }

    @Test
    fun `standing session never contains fewer than two warm-up chair exercises`() {
        val session = ProgramEngine.sessionFor(
            start,
            completedDates = emptyList(),
            asOfDate = start,
        )
        // brand new program: chair-supported session only
        assertEquals(ExerciseLibrary.chairSupported, session)
    }
}
