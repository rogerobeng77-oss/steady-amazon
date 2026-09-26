package com.steady.app.domain

import java.time.LocalDate

/** Decides which tier today's session should be built from. This is the "conservative
 * automatic progression" the spec promises: the person never picks a difficulty, and the
 * rule only ever looks at fully-elapsed weeks, never the week in progress.
 *
 * Progression up: two consecutive strong weeks (see [WeekCalendar.strongWeekThreshold])
 * move a chair-supported person to standing work.
 *
 * Regression down: a single fully-elapsed week with zero completed sessions drops a
 * standing-tier person back to chair-supported, on the same logic a physiotherapist would
 * use after a break: reintroduce load gradually rather than assume nothing changed.
 *
 * [excludedWeeks] (see [PauseTracker]) are skipped entirely rather than treated as a
 * zero-session week: a week the person marked as "away" or "unwell" is not evidence they
 * stopped trying, and must never trigger the same regression a real lapse would.
 *
 * This function is pure and stateless: it recomputes the tier from the full session
 * history every time rather than storing a tier as separate mutable state, so there is no
 * way for the stored tier and the history to disagree. */
object ProgramEngine {
    const val DEFAULT_PRESCRIBED_SESSIONS_PER_WEEK = 3

    fun tierFor(
        programStart: LocalDate,
        completedDates: List<LocalDate>,
        asOfDate: LocalDate,
        prescribedPerWeek: Int = DEFAULT_PRESCRIBED_SESSIONS_PER_WEEK,
        excludedWeeks: Set<Int> = emptySet(),
    ): Tier {
        val currentWeek = WeekCalendar.weekIndex(programStart, asOfDate)

        val lastCompletedWeek = mostRecentNonExcludedWeek(currentWeek - 1, excludedWeeks)
            ?: return Tier.CHAIR_SUPPORTED

        val lastWeekCount = WeekCalendar.sessionsInWeek(programStart, completedDates, lastCompletedWeek)
        // A fully missed week (that was not an excluded pause) is a safety regression
        // regardless of older history.
        if (lastWeekCount == 0) return Tier.CHAIR_SUPPORTED

        val secondLastWeek = mostRecentNonExcludedWeek(lastCompletedWeek - 1, excludedWeeks)
            ?: return Tier.CHAIR_SUPPORTED

        val threshold = WeekCalendar.strongWeekThreshold(prescribedPerWeek)
        val secondLastWeekCount = WeekCalendar.sessionsInWeek(programStart, completedDates, secondLastWeek)
        val twoConsecutiveStrongWeeks = lastWeekCount >= threshold && secondLastWeekCount >= threshold

        return if (twoConsecutiveStrongWeeks) Tier.STANDING else Tier.CHAIR_SUPPORTED
    }

    fun sessionFor(
        programStart: LocalDate,
        completedDates: List<LocalDate>,
        asOfDate: LocalDate,
        prescribedPerWeek: Int = DEFAULT_PRESCRIBED_SESSIONS_PER_WEEK,
        excludedWeeks: Set<Int> = emptySet(),
    ): List<Exercise> {
        val tier = tierFor(programStart, completedDates, asOfDate, prescribedPerWeek, excludedWeeks)
        return ExerciseLibrary.forTier(tier)
    }

    /** Walks backward from [startWeek], skipping any index in [excludedWeeks], and
     * returns the first week found at zero or above. Null means the walk ran off the
     * start of the programme without finding one (a brand-new programme, or one that has
     * been paused for its entire history so far). */
    private fun mostRecentNonExcludedWeek(startWeek: Int, excludedWeeks: Set<Int>): Int? {
        var week = startWeek
        while (week >= 0) {
            if (week !in excludedWeeks) return week
            week--
        }
        return null
    }
}
