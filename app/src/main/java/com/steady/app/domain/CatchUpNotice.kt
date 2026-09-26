package com.steady.app.domain

import java.time.LocalDate

/** Why the app has something to say about a gap. Two reasons, both arithmetic over
 * recorded dates, neither of them a judgement about the person. */
enum class CatchUpReason {
    /** Nothing has been recorded for [CatchUpEvaluator.QUIET_DAYS] days or more. */
    QUIET_STRETCH,

    /** A week finished, was not paused, and ended below the prescribed dose — and this
     * week has not started yet. */
    SHORT_WEEK,
}

/**
 * The one thing Steady is now able to notice: that a session did not happen.
 *
 * Every field here is a count of days or of sessions taken from
 * `completed_session_dates`, which only a remote press ever writes to. Nothing on this
 * object is a sensor reading, an inference, or a model's opinion, and the sentences it
 * carries are written in this file rather than generated, for the same reason
 * [AdjustmentPhrasings] exists: the person reading them is being told something about
 * their own health behaviour, and the app should be able to say exactly which sentences
 * it is capable of producing.
 *
 * The wording rules, which [CatchUpNoticeWordingTest] enforces:
 *
 * - It states a fact about the record, never about the person. "The last session was 6
 *   days ago", not "you have not exercised for 6 days". A gap in a list of dates is the
 *   only thing the app actually knows.
 * - No "missed", "failed", "behind", "should", "streak". A reminder that reads as a
 *   reprimand is how somebody in their seventies stops opening an app, and the app that
 *   gets uninstalled prevents no falls at all.
 * - One fact, one offer, and a way out. Nothing else.
 */
data class CatchUpNotice(
    val reason: CatchUpReason,
    val daysSinceLastSession: Int,
    val lastSessionEpochDay: Long,
    val sessionsInLastElapsedWeek: Int,
    val prescribedPerWeek: Int,
) {
    /** The fact, in the app's own voice. Digits rather than words, matching the
     * "0 of 3 sessions done this week" already on the same screen. */
    val headline: String
        get() = when (reason) {
            CatchUpReason.QUIET_STRETCH ->
                "The last session was $daysSinceLastSession ${dayWord(daysSinceLastSession)} ago."
            // "Last week had 2 of 3 sessions", not "Last week finished with 2 of 3
            // sessions". Nine characters shorter, and those nine characters are the
            // difference between one line and two at the 1.35x accessibility text size,
            // where a second line ran into the exercise row on the real TV canvas. It
            // also reads closer to the "1 of 3 sessions done this week" already on the
            // same screen.
            CatchUpReason.SHORT_WEEK ->
                "Last week had $sessionsInLastElapsedWeek of $prescribedPerWeek sessions."
        }

    /** The offer. Identical for both reasons on purpose: whatever the app noticed, the
     * thing being offered is the same four minutes it always offers. "Today's session"
     * in full rather than "Today's" — the elision is fine on a phone held at arm's
     * length and clipped at three metres. */
    val subline: String get() = "Today's session is ready when you are."

    /** Fire TV's Notification Center shows the title first and a row has to read on its
     * own in a list, so the fact goes in the title rather than the app's name. */
    val notificationTitle: String get() = when (reason) {
        CatchUpReason.QUIET_STRETCH ->
            "$daysSinceLastSession ${dayWord(daysSinceLastSession)} since the last session"
        CatchUpReason.SHORT_WEEK ->
            "Last week had $sessionsInLastElapsedWeek of $prescribedPerWeek sessions"
    }

    /** Word for word the same as [subline], deliberately. Two surfaces making the same
     * offer in two different phrasings is the app talking to itself, and the person
     * who reads both has to work out whether the difference meant anything. */
    val notificationBody: String get() = subline

    private fun dayWord(days: Int) = if (days == 1) "day" else "days"
}

/**
 * Decides whether there is anything to say, from the same recorded dates every other
 * number on the Home screen is derived from.
 *
 * Pure, and takes [asOfDate] rather than reading a clock, so a test can walk a programme
 * forward a day at a time and the scheduled worker and the Home screen can share one
 * implementation. The four silences below are the design, not edge cases.
 */
object CatchUpEvaluator {

    /**
     * Three sessions a week is roughly every other day, so one quiet day is the
     * programme working normally and two is a weekend. Three is the first number that
     * is genuinely off the pattern, and it is the earliest point at which saying
     * something is informative rather than impatient.
     */
    const val QUIET_DAYS = 3

    fun evaluate(
        programStart: LocalDate?,
        completedDates: List<LocalDate>,
        asOfDate: LocalDate,
        excludedWeeks: Set<Int> = emptySet(),
        isPaused: Boolean = false,
        prescribedPerWeek: Int = ProgramEngine.DEFAULT_PRESCRIBED_SESSIONS_PER_WEEK,
    ): CatchUpNotice? {
        // 1. Nothing has been set up yet.
        if (programStart == null) return null

        // 2. A declared pause. The person has already told the app they are away or
        //    unwell; noticing it back at them is the single most patronising thing this
        //    feature could do.
        if (isPaused) return null

        // 3. Never started. Home already reads "0 of 3 sessions done this week" above a
        //    button marked Start. A second line saying the same thing is a nag carrying
        //    no new information.
        val lastSession = completedDates.filter { !it.isAfter(asOfDate) }.maxOrNull() ?: return null

        // 4. This week is inside a pause the person declared, including one they have
        //    only just come back from.
        val currentWeek = WeekCalendar.weekIndex(programStart, asOfDate)
        if (currentWeek in excludedWeeks) return null

        val daysSince = AdherenceTracker.daysSinceLastSession(completedDates, asOfDate) ?: return null

        if (daysSince >= QUIET_DAYS) {
            return CatchUpNotice(
                reason = CatchUpReason.QUIET_STRETCH,
                daysSinceLastSession = daysSince,
                lastSessionEpochDay = lastSession.toEpochDay(),
                sessionsInLastElapsedWeek = AdherenceTracker
                    .sessionsInLastElapsedWeek(programStart, completedDates, asOfDate, excludedWeeks) ?: 0,
                prescribedPerWeek = prescribedPerWeek,
            )
        }

        // A short week is only worth mentioning to somebody who has not restarted yet.
        // Telling a person who trained yesterday that last week was thin is scorekeeping,
        // and scorekeeping is what this app is trying not to be.
        val thisWeek = AdherenceTracker.sessionsThisWeek(programStart, completedDates, asOfDate)
        if (thisWeek > 0) return null

        val lastElapsed = AdherenceTracker
            .sessionsInLastElapsedWeek(programStart, completedDates, asOfDate, excludedWeeks)
            ?: return null
        if (lastElapsed >= prescribedPerWeek) return null

        return CatchUpNotice(
            reason = CatchUpReason.SHORT_WEEK,
            daysSinceLastSession = daysSince,
            lastSessionEpochDay = lastSession.toEpochDay(),
            sessionsInLastElapsedWeek = lastElapsed,
            prescribedPerWeek = prescribedPerWeek,
        )
    }
}
