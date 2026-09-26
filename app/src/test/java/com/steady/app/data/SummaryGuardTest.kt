package com.steady.app.data

import com.steady.app.domain.CaregiverSummaryBuilder
import com.steady.app.domain.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SummaryGuardTest {

    private val facts = SummaryFacts(
        familyMemberName = "Marcus",
        weekNumber = 4,
        sessionsCompletedThisWeek = 2,
        prescribedPerWeek = 3,
        streakWeeks = 2,
        tier = Tier.CHAIR_SUPPORTED,
    )

    private fun reject(text: String) = SummaryGuard.check(text, facts)

    @Test
    fun `a plain restatement of the facts passes`() {
        assertNull(
            reject("Hi Marcus, week 4 went well: 2 of 3 sessions done on the seated routine, making 2 weeks in a row."),
        )
    }

    @Test
    fun `the app's own deterministic summary passes its own guard`() {
        val own = CaregiverSummaryBuilder.build(
            familyMemberName = "Marcus",
            weekNumber = 4,
            sessionsCompletedThisWeek = 2,
            prescribedPerWeek = 3,
            streakWeeks = 2,
            tier = Tier.CHAIR_SUPPORTED,
        )
        assertNull("Steady's own wording must not trip Steady's own guard", reject(own))
    }

    @Test
    fun `a referral to a doctor is refused`() {
        assertEquals("vocabulary", reject("Hi Marcus, 2 of 3 sessions done. It may be worth asking her GP.")?.rule)
    }

    @Test
    fun `an invented clinical observation is refused`() {
        assertNotNull(reject("Hi Marcus, she managed 2 sessions but seemed unsteady, which is a little concerning."))
    }

    @Test
    fun `a number that was never in the facts is refused`() {
        assertEquals("number", reject("Hi Marcus, 2 of 3 sessions done this week, 12 minutes each on the routine.")?.rule)
    }

    @Test
    fun `a number word that was never in the facts is refused`() {
        assertEquals("number", reject("Hi Marcus, two of three sessions done, and seven days of walking besides.")?.rule)
    }

    @Test
    fun `number words matching the facts are accepted`() {
        assertNull(reject("Hi Marcus, two of three sessions done this week on the seated routine. Steady going."))
    }

    @Test
    fun `an instruction aimed at anybody is refused`() {
        assertEquals("vocabulary", reject("Hi Marcus, 2 of 3 sessions done. You should check on her this weekend.")?.rule)
    }

    @Test
    fun `a link is refused`() {
        assertNotNull(reject("Hi Marcus, 2 of 3 sessions done this week. More at https://example.com/steady/report"))
    }

    @Test
    fun `an essay is refused on length`() {
        assertEquals("length", reject("Hi Marcus, ".repeat(60))?.rule)
    }

    @Test
    fun `a fragment is refused on length`() {
        assertEquals("length", reject("Fine.")?.rule)
    }

    @Test
    fun `a rejection never carries the text it refused`() {
        val text = "Hi Marcus, she seemed unsteady and it may be worth ringing her GP about the falls risk."
        val rejection = reject(text)!!
        assertEquals(false, rejection.toString().contains("unsteady and it may"))
    }

    // --- the one number the missed-session work added to the allowed set -------------

    private val factsWithGap = facts.copy(daysSinceLastSession = 9)

    @Test
    fun `the gap number is allowed only because the app handed it over`() {
        assertNull(
            SummaryGuard.check(
                "Hi Marcus, 2 of 3 sessions done in week 4. The last one was 9 days ago.",
                factsWithGap,
            ),
        )
    }

    @Test
    fun `the same number is refused when the app never computed a gap`() {
        // This is the property that matters. 9 is not safe because it is small or
        // because it looks like a day count; it is safe only when it came from the
        // record. With daysSinceLastSession null, it never did.
        assertEquals(
            "number",
            SummaryGuard.check(
                "Hi Marcus, 2 of 3 sessions done in week 4. The last one was 9 days ago.",
                facts,
            )?.rule,
        )
    }

    @Test
    fun `a gap the model invented is still refused when a different gap is real`() {
        assertEquals(
            "number",
            SummaryGuard.check(
                "Hi Marcus, 2 of 3 sessions done in week 4. The last one was 14 days ago.",
                factsWithGap,
            )?.rule,
        )
    }

    @Test
    fun `the gap number does not unlock the vocabulary rule`() {
        assertEquals(
            "vocabulary",
            SummaryGuard.check(
                "Hi Marcus, the last one was 9 days ago and she seems unsteady on her feet.",
                factsWithGap,
            )?.rule,
        )
    }

    @Test
    fun `an invented clinical observation hung off a real gap is still refused`() {
        assertNotNull(
            SummaryGuard.check(
                "Hi Marcus, the last one was 9 days ago. It might be worth a word with her doctor.",
                factsWithGap,
            ),
        )
    }

    @Test
    fun `the app's own gap wording passes its own guard`() {
        val own = CaregiverSummaryBuilder.build(
            familyMemberName = "Marcus",
            weekNumber = 4,
            sessionsCompletedThisWeek = 2,
            prescribedPerWeek = 3,
            streakWeeks = 2,
            tier = Tier.CHAIR_SUPPORTED,
            daysSinceLastSession = 9,
        )
        assertNull("Steady's own gap sentence must not trip Steady's own guard", SummaryGuard.check(own, factsWithGap))
    }

    @Test
    fun `the app's own empty-week wording with a long gap passes too`() {
        val own = CaregiverSummaryBuilder.build(
            familyMemberName = "Katherine",
            weekNumber = 12,
            sessionsCompletedThisWeek = 0,
            prescribedPerWeek = 3,
            streakWeeks = 0,
            tier = Tier.STANDING,
            daysSinceLastSession = 11,
        )
        val longFacts = SummaryFacts(
            familyMemberName = "Katherine",
            weekNumber = 12,
            sessionsCompletedThisWeek = 0,
            prescribedPerWeek = 3,
            streakWeeks = 0,
            tier = Tier.STANDING,
            daysSinceLastSession = 11,
        )
        assertNull(SummaryGuard.check(own, longFacts))
    }
}
