package com.steady.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Turns raw completion dates into the two numbers a person and their family actually
 * look at: a weekly streak, and this week's session count against the prescribed dose.
 * Nothing here is a sensor reading; it is arithmetic over dates the person's own remote
 * presses recorded. */
object AdherenceTracker {

    /** Consecutive weeks with at least one completed session, counted backward from
     * [asOfDate]. A week still in progress that has not had a session yet does not break
     * the streak by itself; only a week that fully elapsed with zero sessions does.
     *
     * A week in [excludedWeeks] (see [PauseTracker]) is skipped rather than treated as
     * empty: a pause the person declared is not a gap in the streak, it is time the
     * streak simply was not being asked to cover. */
    fun currentStreakWeeks(
        programStart: LocalDate,
        completedDates: List<LocalDate>,
        asOfDate: LocalDate,
        excludedWeeks: Set<Int> = emptySet(),
    ): Int {
        val currentWeek = WeekCalendar.weekIndex(programStart, asOfDate)
        var weekIdx = if (
            currentWeek in excludedWeeks ||
            WeekCalendar.sessionsInWeek(programStart, completedDates, currentWeek) >= 1
        ) {
            currentWeek
        } else {
            currentWeek - 1
        }

        var streak = 0
        while (weekIdx >= 0) {
            if (weekIdx in excludedWeeks) {
                weekIdx--
                continue
            }
            if (WeekCalendar.sessionsInWeek(programStart, completedDates, weekIdx) >= 1) {
                streak++
                weekIdx--
            } else {
                break
            }
        }
        return streak
    }

    fun sessionsThisWeek(
        programStart: LocalDate,
        completedDates: List<LocalDate>,
        asOfDate: LocalDate,
    ): Int {
        val currentWeek = WeekCalendar.weekIndex(programStart, asOfDate)
        return WeekCalendar.sessionsInWeek(programStart, completedDates, currentWeek)
    }

    /**
     * Days between the most recent completed session and [asOfDate]. Null when nothing
     * has ever been completed, which is a different state from "a long time ago" and is
     * treated as one everywhere it is read.
     *
     * This is the only new number the missed-session work adds, and it is the same kind
     * of number as everything else in this file: subtraction over dates that a remote
     * press wrote. Dates after [asOfDate] are ignored rather than trusted, so a device
     * whose clock moved backwards cannot produce a negative gap.
     */
    fun daysSinceLastSession(
        completedDates: List<LocalDate>,
        asOfDate: LocalDate,
    ): Int? {
        val last = completedDates.filter { !it.isAfter(asOfDate) }.maxOrNull() ?: return null
        return ChronoUnit.DAYS.between(last, asOfDate).toInt()
    }

    /**
     * Sessions in the most recent week that has fully elapsed and was not excluded by a
     * pause. Null means there is no such week yet: a programme in its first week, or one
     * that has been paused for its whole history so far.
     *
     * Deliberately the same walk [ProgramEngine] uses to pick the week its tier rule
     * looks at, so the week the family update reports on and the week that moved the
     * tier are always the same week.
     */
    fun sessionsInLastElapsedWeek(
        programStart: LocalDate,
        completedDates: List<LocalDate>,
        asOfDate: LocalDate,
        excludedWeeks: Set<Int> = emptySet(),
    ): Int? {
        var week = WeekCalendar.weekIndex(programStart, asOfDate) - 1
        while (week >= 0) {
            if (week !in excludedWeeks) {
                return WeekCalendar.sessionsInWeek(programStart, completedDates, week)
            }
            week--
        }
        return null
    }

    fun isThisWeekStrong(
        programStart: LocalDate,
        completedDates: List<LocalDate>,
        asOfDate: LocalDate,
        prescribedPerWeek: Int = ProgramEngine.DEFAULT_PRESCRIBED_SESSIONS_PER_WEEK,
    ): Boolean {
        val count = sessionsThisWeek(programStart, completedDates, asOfDate)
        return count >= WeekCalendar.strongWeekThreshold(prescribedPerWeek)
    }
}
