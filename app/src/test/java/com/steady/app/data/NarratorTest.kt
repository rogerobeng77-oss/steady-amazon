package com.steady.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NarratorTest {

    @Test
    fun `extracts a simple text field`() {
        val json = """{"text": "Hello there"}"""
        assertEquals("Hello there", extractTextField(json))
    }

    @Test
    fun `unescapes quotes and newlines inside the text field`() {
        val json = """{"text": "She said \"hello\"\nagain"}"""
        assertEquals("She said \"hello\"\nagain", extractTextField(json))
    }

    @Test
    fun `returns null when the field is missing`() {
        val json = """{"error": "not found"}"""
        assertNull(extractTextField(json))
    }

    @Test
    fun `returns null for blank text`() {
        val json = """{"text": "   "}"""
        assertNull(extractTextField(json))
    }

    @Test
    fun `returns null for malformed json`() {
        assertNull(extractTextField("not json at all"))
    }

    @Test
    fun `reads the choice integer`() {
        assertEquals(2, extractChoiceField("""{"choice": 2}"""))
        assertEquals(0, extractChoiceField("""{"choice":0}"""))
    }

    @Test
    fun `reads a negative or oversized choice without crashing, leaving the bound check to the caller`() {
        assertEquals(-1, extractChoiceField("""{"choice": -1}"""))
        assertEquals(9999, extractChoiceField("""{"choice": 9999}"""))
    }

    @Test
    fun `returns null when there is no choice field, including when prose is sent instead`() {
        assertNull(extractChoiceField("""{"text": "Today's session is shorter."}"""))
        assertNull(extractChoiceField("""{"choice": "two"}"""))
        assertNull(extractChoiceField("not json at all"))
    }

    @Test
    fun `fallback narrator always returns null without touching the network`() = runBlocking {
        assertNull(
            FallbackNarrator.narrateSummary(
                SummaryFacts(
                    familyMemberName = "Marcus",
                    weekNumber = 1,
                    sessionsCompletedThisWeek = 1,
                    prescribedPerWeek = 3,
                    streakWeeks = 1,
                    tier = com.steady.app.domain.Tier.CHAIR_SUPPORTED,
                ),
            ),
        )
        assertNull(
            FallbackNarrator.chooseAdjustmentPhrasing(
                PhrasingRequest(
                    kind = com.steady.app.domain.AdjustmentKind.SHORTER,
                    adjustmentDescription = "durations shortened by about 30 percent",
                    options = com.steady.app.domain.AdjustmentPhrasings.options(
                        com.steady.app.domain.AdjustmentKind.SHORTER,
                    ),
                ),
            ),
        )
    }
}
