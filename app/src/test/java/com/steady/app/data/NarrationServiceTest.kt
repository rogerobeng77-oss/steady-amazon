package com.steady.app.data

import com.steady.app.domain.AdjustmentKind
import com.steady.app.domain.AdjustmentPhrasings
import com.steady.app.domain.Tier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A stand-in for a real network call: no HTTP, no timeout, just return values the
 * test controls. This is the stub the brief asks for: "a test that passes with the
 * call stubbed." */
private class StubNarrator(
    private val summaryResult: String? = null,
    private val choiceResult: Int? = null,
) : Narrator {
    override suspend fun narrateSummary(facts: SummaryFacts): String? = summaryResult
    override suspend fun chooseAdjustmentPhrasing(request: PhrasingRequest): Int? = choiceResult
}

private val facts = SummaryFacts(
    familyMemberName = "Marcus",
    weekNumber = 1,
    sessionsCompletedThisWeek = 1,
    prescribedPerWeek = 3,
    streakWeeks = 1,
    tier = Tier.CHAIR_SUPPORTED,
)

/** Long enough to clear [SummaryGuard.MIN_CHARS], using only numbers that are in [facts]. */
private const val GOOD_SUMMARY =
    "Hi Marcus, 1 of 3 sessions done this week on the seated routine. Nice steady going."

class NarrationServiceTest {

    @Test
    fun `uses the AI text and tags it AI_WRITTEN when the model answers and the guard passes`() = runBlocking {
        val service = NarrationService(StubNarrator(summaryResult = GOOD_SUMMARY))
        val result = service.summary(facts) { "fallback text" }
        assertEquals(GOOD_SUMMARY, result.text)
        assertEquals(TextSource.AI_WRITTEN, result.source)
    }

    @Test
    fun `falls back to the deterministic text and tags it RULES when the model returns null`() = runBlocking {
        val service = NarrationService(StubNarrator(summaryResult = null))
        val result = service.summary(facts) { "fallback text" }
        assertEquals("fallback text", result.text)
        assertEquals(TextSource.RULES, result.source)
    }

    @Test
    fun `falls back when the model returns a blank string`() = runBlocking {
        val service = NarrationService(StubNarrator(summaryResult = "   "))
        val result = service.summary(facts) { "fallback text" }
        assertEquals("fallback text", result.text)
        assertEquals(TextSource.RULES, result.source)
    }

    @Test
    fun `falls back when the model throws`() = runBlocking {
        val throwing = object : Narrator {
            override suspend fun narrateSummary(facts: SummaryFacts): String = throw RuntimeException("boom")
            override suspend fun chooseAdjustmentPhrasing(request: PhrasingRequest): Int = throw RuntimeException("boom")
        }
        val result = NarrationService(throwing).summary(facts) { "fallback text" }
        assertEquals("fallback text", result.text)
        assertEquals(TextSource.RULES, result.source)
    }

    /** The A2/B1 defect: fluent, warm, and entirely invented. Nothing in [facts] mentions
     * a Thursday, steadiness, or a GP. */
    @Test
    fun `the invented clinical summary is refused and never reaches the family member`() = runBlocking {
        val invented = "Hi Marcus, Mum managed 1 session and seemed unsteady on Thursday, " +
            "so it may be worth talking to her GP about her falls risk."
        val service = NarrationService(StubNarrator(summaryResult = invented))
        val result = service.summary(facts) { "fallback text" }
        assertEquals("fallback text", result.text)
        assertEquals(TextSource.RULES, result.source)
        assertNotEquals(invented, result.text)
    }

    @Test
    fun `a summary carrying a number nobody gave the model is refused`() = runBlocking {
        val service = NarrationService(
            StubNarrator(summaryResult = "Hi Marcus, 7 of 3 sessions done this week on the seated routine."),
        )
        assertEquals(TextSource.RULES, NarrationService(StubNarrator()).summary(facts) { "f" }.source)
        assertEquals("fallback text", service.summary(facts) { "fallback text" }.text)
    }

    @Test
    fun `the adjustment explanation is always one of Steady's own sentences`() = runBlocking {
        val options = AdjustmentPhrasings.options(AdjustmentKind.SHORTER)
        // Every answer a model could give, valid and not.
        val answers = listOf(null, -1, 0, 1, 2, 3, 99, Int.MAX_VALUE, Int.MIN_VALUE)
        answers.forEach { answer ->
            val service = NarrationService(StubNarrator(choiceResult = answer))
            val result = service.adjustmentExplanation(AdjustmentKind.SHORTER, "durations shortened")
            assertTrue("model answer $answer produced text outside the fixed set", result.text in options)
        }
    }

    @Test
    fun `a valid choice is tagged AI_CHOSEN, never AI_WRITTEN`() = runBlocking {
        val service = NarrationService(StubNarrator(choiceResult = 1))
        val result = service.adjustmentExplanation(AdjustmentKind.SHORTER, "durations shortened")
        assertEquals(AdjustmentPhrasings.options(AdjustmentKind.SHORTER)[1], result.text)
        assertEquals(TextSource.AI_CHOSEN, result.source)
    }

    @Test
    fun `an out-of-range choice falls back to the default sentence tagged RULES`() = runBlocking {
        val service = NarrationService(StubNarrator(choiceResult = 42))
        val result = service.adjustmentExplanation(AdjustmentKind.SHORTER, "durations shortened")
        assertEquals(AdjustmentPhrasings.default(AdjustmentKind.SHORTER), result.text)
        assertEquals(TextSource.RULES, result.source)
    }

    @Test
    fun `a throwing narrator still yields one of Steady's own sentences`() = runBlocking {
        val throwing = object : Narrator {
            override suspend fun narrateSummary(facts: SummaryFacts): String = throw RuntimeException("boom")
            override suspend fun chooseAdjustmentPhrasing(request: PhrasingRequest): Int = throw RuntimeException("boom")
        }
        val result = NarrationService(throwing).adjustmentExplanation(AdjustmentKind.WELCOME_BACK, "eased in")
        assertEquals(AdjustmentPhrasings.default(AdjustmentKind.WELCOME_BACK), result.text)
        assertEquals(TextSource.RULES, result.source)
    }
}
