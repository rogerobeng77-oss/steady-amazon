package com.steady.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wording is a safety property here, not a style preference, so it is tested like
 * one.
 *
 * A reminder in a falls-prevention programme has a specific way of failing: the person
 * reads it as a reprimand, feels judged in their own living room, and stops opening the
 * app. An app nobody opens prevents nothing. So the vocabulary below is checked against
 * every sentence [CatchUpNotice] is capable of producing, across the whole range of
 * gaps it can describe — not against a handful of examples, which is the mistake
 * the shared guard-wording standard's §4 names.
 */
class CatchUpNoticeWordingTest {

    /** Words that turn a fact into a verdict, an instruction, or an alarm. */
    private val forbidden = listOf(
        "missed", "miss", "failed", "fail", "behind", "should", "must", "need to",
        "neglect", "lapse", "skipped", "skip", "broken", "streak", "overdue", "late",
        "warning", "worry", "worried", "concerned", "risk", "danger", "unsteady",
        "fall", "falls", "sorry", "remember to", "don't forget", "why",
    )

    /** Every notice the app can construct, across the whole range of gaps and counts. */
    private fun everyNotice(): List<CatchUpNotice> = buildList {
        for (reason in CatchUpReason.entries) {
            for (days in 0..60) {
                for (sessions in 0..3) {
                    add(
                        CatchUpNotice(
                            reason = reason,
                            daysSinceLastSession = days,
                            lastSessionEpochDay = 20_000L,
                            sessionsInLastElapsedWeek = sessions,
                            prescribedPerWeek = 3,
                        ),
                    )
                }
            }
        }
    }

    /** Every sentence the app can put in front of the person through this path. */
    private fun everyString(): List<String> = everyNotice().flatMap {
        listOf(it.headline, it.subline, it.notificationTitle, it.notificationBody)
    }

    @Test
    fun `no sentence the app can produce scolds anybody`() {
        val offenders = everyString().flatMap { text ->
            val lower = text.lowercase()
            forbidden.filter { term -> Regex("\\b${Regex.escape(term)}\\b").containsMatchIn(lower) }
                .map { term -> "\"$text\" contains \"$term\"" }
        }.distinct()
        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun `the sentence carrying the gap never says "you"`() {
        // The distinction this test holds is the whole tone of the feature. "The last
        // session was 6 days ago" is a fact about a record; "you have not exercised in
        // 6 days" is the same fact rewritten as an accusation. The *offer* is allowed
        // to be in the second person — "Today's is ready when you are" is addressed to
        // somebody, which is the point of an offer — so this checks the two strings
        // that carry the gap and not the two that carry the invitation.
        val offenders = gapStrings().filter { text ->
            Regex("\\b(you|your|you've|you're|yourself)\\b").containsMatchIn(text.lowercase())
        }.distinct()
        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }

    private fun gapStrings(): List<String> = everyNotice().flatMap {
        listOf(it.headline, it.notificationTitle)
    }

    @Test
    fun `no sentence carries an exclamation mark or a question`() {
        val offenders = everyString().filter { it.contains('!') || it.contains('?') }
        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun `every sentence is short enough to read across a room`() {
        val tooLong = everyString().filter { it.length > 70 }
        assertTrue(tooLong.joinToString("\n"), tooLong.isEmpty())
    }

    @Test
    fun `no headline outgrows the band Home reserves for it`() {
        // Home caps the notice at 600dp and drops the offer line at the 1.35x
        // accessibility text size, which leaves room for one line of about 38
        // characters. A headline longer than that wraps to two and the second line
        // lands on the exercise row's heading, which is how this was found. 999 days is
        // well past any gap a real programme survives.
        val worst = everyNotice() + CatchUpNotice(CatchUpReason.QUIET_STRETCH, 999, 20_000L, 0, 3)
        val tooWide = worst.map { it.headline }.filter { it.length > 38 }.distinct()
        assertTrue(tooWide.joinToString("\n"), tooWide.isEmpty())
    }

    @Test
    fun `the quiet stretch line states the gap in days`() {
        val notice = CatchUpNotice(
            reason = CatchUpReason.QUIET_STRETCH,
            daysSinceLastSession = 6,
            lastSessionEpochDay = 20_000L,
            sessionsInLastElapsedWeek = 1,
            prescribedPerWeek = 3,
        )
        assertEquals("The last session was 6 days ago.", notice.headline)
        assertEquals("6 days since the last session", notice.notificationTitle)
    }

    @Test
    fun `one day is singular`() {
        val notice = CatchUpNotice(
            reason = CatchUpReason.QUIET_STRETCH,
            daysSinceLastSession = 1,
            lastSessionEpochDay = 20_000L,
            sessionsInLastElapsedWeek = 1,
            prescribedPerWeek = 3,
        )
        assertEquals("The last session was 1 day ago.", notice.headline)
    }

    @Test
    fun `the short week line states the count and the dose, with no adjective`() {
        val notice = CatchUpNotice(
            reason = CatchUpReason.SHORT_WEEK,
            daysSinceLastSession = 2,
            lastSessionEpochDay = 20_000L,
            sessionsInLastElapsedWeek = 1,
            prescribedPerWeek = 3,
        )
        assertEquals("Last week had 1 of 3 sessions.", notice.headline)
    }

    @Test
    fun `both reasons make the same offer`() {
        val quiet = CatchUpNotice(CatchUpReason.QUIET_STRETCH, 6, 20_000L, 1, 3)
        val short = CatchUpNotice(CatchUpReason.SHORT_WEEK, 2, 20_000L, 1, 3)
        assertEquals("Today's session is ready when you are.", quiet.subline)
        assertEquals(quiet.subline, short.subline)
        // And the notification makes it in the same words, not a synonym of them.
        assertEquals(quiet.subline, quiet.notificationBody)
    }
}
