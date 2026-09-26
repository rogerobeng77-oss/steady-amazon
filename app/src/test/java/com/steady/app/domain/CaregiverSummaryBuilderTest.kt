package com.steady.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaregiverSummaryBuilderTest {

    @Test
    fun `summary names the family member and the week`() {
        val summary = CaregiverSummaryBuilder.build(
            familyMemberName = "Marcus",
            weekNumber = 4,
            sessionsCompletedThisWeek = 3,
            prescribedPerWeek = 3,
            streakWeeks = 4,
            tier = Tier.CHAIR_SUPPORTED,
        )
        assertTrue(summary.contains("Marcus"))
        assertTrue(summary.contains("week 4"))
        assertTrue(summary.contains("3 of 3 sessions"))
    }

    /** "N of M sessions" agrees with M, not with N. This test used to assert the
     * opposite — that one completed session produced "1 of 3 session" — which locked a
     * grammar mistake into the only sentence a family member ever reads. The singular
     * belongs to a one-session-a-week prescription, which is what the second case here
     * checks. */
    @Test
    fun `the noun agrees with the prescribed count, not the completed one`() {
        val summary = CaregiverSummaryBuilder.build(
            familyMemberName = "Aisha",
            weekNumber = 1,
            sessionsCompletedThisWeek = 1,
            prescribedPerWeek = 3,
            streakWeeks = 1,
            tier = Tier.CHAIR_SUPPORTED,
        )
        assertTrue(summary.contains("1 of 3 sessions done this week"))

        val weekly = CaregiverSummaryBuilder.build(
            familyMemberName = "Aisha",
            weekNumber = 1,
            sessionsCompletedThisWeek = 1,
            prescribedPerWeek = 1,
            streakWeeks = 1,
            tier = Tier.CHAIR_SUPPORTED,
        )
        assertTrue(weekly.contains("1 of 1 session done this week"))
        assertFalse(weekly.contains("1 of 1 sessions"))
    }

    @Test
    fun `a zero-session week is reported plainly, never as a failure`() {
        val summary = CaregiverSummaryBuilder.build(
            familyMemberName = "Priya",
            weekNumber = 6,
            sessionsCompletedThisWeek = 0,
            prescribedPerWeek = 3,
            streakWeeks = 0,
            tier = Tier.CHAIR_SUPPORTED,
        )
        val bannedWords = listOf("missed", "failed", "fail", "non-compliant", "noncompliant", "behind")
        bannedWords.forEach { word ->
            assertFalse("summary must not shame with the word '$word'", summary.lowercase().contains(word))
        }
        assertTrue(summary.contains("No sessions were logged"))
    }

    @Test
    fun `blank family member name falls back to a neutral greeting`() {
        val summary = CaregiverSummaryBuilder.build(
            familyMemberName = "   ",
            weekNumber = 2,
            sessionsCompletedThisWeek = 2,
            prescribedPerWeek = 3,
            streakWeeks = 2,
            tier = Tier.STANDING,
        )
        assertTrue(summary.startsWith("Hi there,"))
        assertTrue(summary.contains("standing balance routine"))
    }

    @Test
    fun `a streak of one is not described as weeks plural`() {
        val summary = CaregiverSummaryBuilder.build(
            familyMemberName = "Dee",
            weekNumber = 1,
            sessionsCompletedThisWeek = 3,
            prescribedPerWeek = 3,
            streakWeeks = 1,
            tier = Tier.CHAIR_SUPPORTED,
        )
        assertTrue(summary.contains("first week in a new streak"))
    }

    // --- the fact this update could not carry until the app could notice it ----------

    @Test
    fun `a gap longer than the programme's rhythm is reported, as a fact about the record`() {
        val summary = CaregiverSummaryBuilder.build(
            familyMemberName = "Marcus",
            weekNumber = 4,
            sessionsCompletedThisWeek = 0,
            prescribedPerWeek = 3,
            streakWeeks = 0,
            tier = Tier.CHAIR_SUPPORTED,
            daysSinceLastSession = 9,
        )
        assertTrue(summary.contains("The last one was 9 days ago."))
        // Still no verdict attached to it.
        listOf("missed", "failed", "behind", "should", "worried", "concerned").forEach { word ->
            assertFalse("gap line must not shame with '$word'", summary.lowercase().contains(word))
        }
    }

    @Test
    fun `a normal rest day is not turned into an item`() {
        val summary = CaregiverSummaryBuilder.build(
            familyMemberName = "Marcus",
            weekNumber = 4,
            sessionsCompletedThisWeek = 2,
            prescribedPerWeek = 3,
            streakWeeks = 2,
            tier = Tier.CHAIR_SUPPORTED,
            daysSinceLastSession = 1,
        )
        assertFalse(summary.contains("The last one was"))
    }

    @Test
    fun `a programme with nothing recorded yet reports no gap at all`() {
        val summary = CaregiverSummaryBuilder.build(
            familyMemberName = "Marcus",
            weekNumber = 1,
            sessionsCompletedThisWeek = 0,
            prescribedPerWeek = 3,
            streakWeeks = 0,
            tier = Tier.CHAIR_SUPPORTED,
            daysSinceLastSession = null,
        )
        assertFalse(summary.contains("The last one was"))
        assertTrue(summary.contains("No sessions were logged this week."))
    }

    @Test
    fun `the gap can sit alongside a week that did have sessions in it`() {
        // Two sessions early in the week and nothing for six days is a different week
        // from two sessions yesterday, and the count alone cannot tell them apart.
        val summary = CaregiverSummaryBuilder.build(
            familyMemberName = "Marcus",
            weekNumber = 4,
            sessionsCompletedThisWeek = 2,
            prescribedPerWeek = 3,
            streakWeeks = 2,
            tier = Tier.CHAIR_SUPPORTED,
            daysSinceLastSession = 6,
        )
        assertTrue(summary.contains("2 of 3 sessions done this week"))
        assertTrue(summary.contains("The last one was 6 days ago."))
    }

    @Test
    fun `one day is singular in the gap line too`() {
        val summary = CaregiverSummaryBuilder.build(
            familyMemberName = "Marcus",
            weekNumber = 4,
            sessionsCompletedThisWeek = 1,
            prescribedPerWeek = 3,
            streakWeeks = 1,
            tier = Tier.CHAIR_SUPPORTED,
            daysSinceLastSession = 1,
        )
        // Below the threshold, so it is absent entirely — but if the threshold ever
        // moves, the pluralisation is already right.
        assertFalse(summary.contains("1 days ago"))
    }

    @Test
    fun `the longest summary the builder can produce stays inside the guard's shape`() {
        // Greeting, activity, gap, streak, closing. Five sentences is SummaryGuard's
        // limit and the empty-week wording used to spend three of them on its own.
        val summary = CaregiverSummaryBuilder.build(
            familyMemberName = "Katherine",
            weekNumber = 12,
            sessionsCompletedThisWeek = 0,
            prescribedPerWeek = 3,
            streakWeeks = 0,
            tier = Tier.STANDING,
            daysSinceLastSession = 11,
        )
        val sentences = Regex("[.!?]+").split(summary).count { it.isNotBlank() }
        assertTrue("$sentences sentences: $summary", sentences <= 5)
        assertTrue("${summary.length} characters", summary.length <= 400)
    }

    @Test
    fun `an empty week is not given credit for the streak it did not add to`() {
        val summary = CaregiverSummaryBuilder.build(
            familyMemberName = "Marcus",
            weekNumber = 3,
            sessionsCompletedThisWeek = 0,
            prescribedPerWeek = 3,
            streakWeeks = 2,
            tier = Tier.CHAIR_SUPPORTED,
            daysSinceLastSession = 10,
        )
        assertTrue(summary.contains("The 2 weeks before this one each had at least one session."))
        assertFalse("an empty week must not read as 2 weeks in a row", summary.contains("This makes"))
    }

    @Test
    fun `a week with sessions in it still reads as a streak in progress`() {
        val summary = CaregiverSummaryBuilder.build(
            familyMemberName = "Marcus",
            weekNumber = 3,
            sessionsCompletedThisWeek = 2,
            prescribedPerWeek = 3,
            streakWeeks = 2,
            tier = Tier.CHAIR_SUPPORTED,
        )
        assertTrue(summary.contains("This makes 2 weeks in a row"))
    }
}
