package com.steady.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The anti-nag guarantee, proved rather than hoped for.
 *
 * The failure this app has to avoid is not a missed notification. It is a person in
 * their seventies who feels monitored, stops opening Steady, and is then in a
 * falls-prevention programme they are no longer doing. Every rule in
 * [AdherenceNoticePolicy] exists for that, so every rule gets a test that could fail.
 */
class AdherenceNoticePolicyTest {

    private val start = LocalDate.of(2026, 3, 2)
    private fun day(n: Int) = start.plusDays(n.toLong())

    private fun noticeAfter(days: Int, lastSessionDay: Int = 0) = CatchUpNotice(
        reason = CatchUpReason.QUIET_STRETCH,
        daysSinceLastSession = days,
        lastSessionEpochDay = day(lastSessionDay).toEpochDay(),
        sessionsInLastElapsedWeek = 1,
        prescribedPerWeek = 3,
    )

    // --- the Home line -----------------------------------------------------------

    @Test
    fun `nothing to say means nothing shown`() {
        assertFalse(
            AdherenceNoticePolicy.showOnHome(null, dismissedOnEpochDay = null, todayEpochDay = 1),
        )
    }

    @Test
    fun `a real gap is shown`() {
        assertTrue(
            AdherenceNoticePolicy.showOnHome(
                noticeAfter(6),
                dismissedOnEpochDay = null,
                todayEpochDay = day(6).toEpochDay(),
            ),
        )
    }

    @Test
    fun `not today hides it for the rest of that day`() {
        val today = day(6).toEpochDay()
        assertFalse(
            AdherenceNoticePolicy.showOnHome(noticeAfter(6), dismissedOnEpochDay = today, todayEpochDay = today),
        )
    }

    @Test
    fun `not today does not silence tomorrow, because the gap is still true tomorrow`() {
        val dismissed = day(6).toEpochDay()
        assertTrue(
            AdherenceNoticePolicy.showOnHome(
                noticeAfter(7),
                dismissedOnEpochDay = dismissed,
                todayEpochDay = day(7).toEpochDay(),
            ),
        )
    }

    // --- the notification --------------------------------------------------------

    @Test
    fun `no gap raises nothing`() {
        assertFalse(
            AdherenceNoticePolicy.shouldRaiseNotification(
                notice = null,
                notifiedForSessionEpochDay = null,
                dismissedOnEpochDay = null,
                todayEpochDay = day(3).toEpochDay(),
            ),
        )
    }

    @Test
    fun `the first day of a gap raises one`() {
        assertTrue(
            AdherenceNoticePolicy.shouldRaiseNotification(
                notice = noticeAfter(3),
                notifiedForSessionEpochDay = null,
                dismissedOnEpochDay = null,
                todayEpochDay = day(3).toEpochDay(),
            ),
        )
    }

    @Test
    fun `a person who stops for a month is told once, not thirty times`() {
        var notifiedFor: Long? = null
        var raised = 0
        // Thirty consecutive daily runs of the worker, gap growing all the while.
        for (offset in 3..32) {
            val notice = noticeAfter(offset)
            if (AdherenceNoticePolicy.shouldRaiseNotification(
                    notice = notice,
                    notifiedForSessionEpochDay = notifiedFor,
                    dismissedOnEpochDay = null,
                    todayEpochDay = day(offset).toEpochDay(),
                )
            ) {
                raised++
                notifiedFor = notice.lastSessionEpochDay
            }
        }
        assertTrue("thirty days of silence produced $raised notifications", raised == 1)
    }

    @Test
    fun `a real session rearms it, so the next gap is also worth one note`() {
        val firstStretch = noticeAfter(5, lastSessionDay = 0)
        val notifiedFor = firstStretch.lastSessionEpochDay

        // They come back on day 8, then go quiet again. The stretch now runs from a
        // different session, so it is a different stretch.
        val secondStretch = noticeAfter(4, lastSessionDay = 8)
        assertTrue(
            AdherenceNoticePolicy.shouldRaiseNotification(
                notice = secondStretch,
                notifiedForSessionEpochDay = notifiedFor,
                dismissedOnEpochDay = null,
                todayEpochDay = day(12).toEpochDay(),
            ),
        )
    }

    @Test
    fun `somebody who already pressed not today is not told again in a list`() {
        val today = day(6).toEpochDay()
        assertFalse(
            AdherenceNoticePolicy.shouldRaiseNotification(
                notice = noticeAfter(6),
                notifiedForSessionEpochDay = null,
                dismissedOnEpochDay = today,
                todayEpochDay = today,
            ),
        )
    }

    @Test
    fun `a second run on the same day raises nothing further`() {
        val notice = noticeAfter(4)
        // First run records the marker; the second run of the same day sees it.
        assertFalse(
            AdherenceNoticePolicy.shouldRaiseNotification(
                notice = notice,
                notifiedForSessionEpochDay = notice.lastSessionEpochDay,
                dismissedOnEpochDay = null,
                todayEpochDay = day(4).toEpochDay(),
            ),
        )
    }
}
