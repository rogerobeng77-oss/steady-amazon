package com.steady.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** Shared week arithmetic used by [ProgramEngine], [AdherenceTracker] and the caregiver
 * summary. A "week" here is simply seven-day blocks counted from the day the program was
 * started on this television; it deliberately does not depend on the device locale's
 * first-day-of-week setting, since that has nothing to do with when this person started. */
object WeekCalendar {
    fun weekIndex(programStart: LocalDate, date: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(programStart, date)
        return Math.floorDiv(days, 7L).toInt()
    }

    fun sessionsInWeek(
        programStart: LocalDate,
        completedDates: List<LocalDate>,
        weekIndex: Int,
    ): Int = completedDates.count { weekIndex(programStart, it) == weekIndex }

    /** The minimum sessions in a week counted as a "strong" week. Hua et al. (Arch
     * Gerontol Geriatr 2026) found 75%-plus adherence independently associated with a much
     * larger fall-rate reduction than the pooled effect across all adherence levels. */
    fun strongWeekThreshold(prescribedPerWeek: Int): Int =
        ceil(prescribedPerWeek * 0.75).toInt()
}
