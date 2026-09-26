package com.steady.app.domain

/**
 * How often Steady is allowed to say anything, ever.
 *
 * This is deliberately a separate object from [CatchUpEvaluator], and deliberately pure.
 * "Is there a gap" and "may we mention it" are different questions with different
 * failure modes: getting the first one wrong means the app is blind, and getting the
 * second one wrong means the app is a nag. Only the second one makes people uninstall.
 *
 * Both answers are functions of values already on disk, so both can be proved in a unit
 * test instead of observed on a television over nine days.
 */
object AdherenceNoticePolicy {

    /**
     * Whether Home shows the line.
     *
     * One press of "Not today" clears it for the rest of that day. It comes back
     * tomorrow if the gap is still real, because a gap that is still there is still
     * true — but it never comes back twice in one evening, which is the shape of
     * dismissal a person actually expects from a television.
     */
    fun showOnHome(
        notice: CatchUpNotice?,
        dismissedOnEpochDay: Long?,
        todayEpochDay: Long,
    ): Boolean {
        if (notice == null) return false
        return dismissedOnEpochDay != todayEpochDay
    }

    /**
     * Whether the scheduled evaluation posts a notification.
     *
     * At most one per quiet stretch, for the whole life of the stretch. A stretch is
     * identified by the date of the session it runs from, so somebody who stops for six
     * weeks is told once and then left alone, and the counter only rearms when a real
     * session is recorded and the stretch therefore becomes a different one.
     *
     * Dismissing the Home line also counts: a person who has already seen the fact on
     * screen and pressed "Not today" does not then need it repeated in a list under
     * Settings.
     */
    fun shouldRaiseNotification(
        notice: CatchUpNotice?,
        notifiedForSessionEpochDay: Long?,
        dismissedOnEpochDay: Long?,
        todayEpochDay: Long,
    ): Boolean {
        if (notice == null) return false
        if (notice.lastSessionEpochDay == notifiedForSessionEpochDay) return false
        if (dismissedOnEpochDay == todayEpochDay) return false
        return true
    }
}
