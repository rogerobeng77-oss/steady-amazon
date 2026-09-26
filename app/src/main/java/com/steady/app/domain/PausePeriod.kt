package com.steady.app.domain

import java.time.LocalDate

/** Why the person is not doing sessions right now. Deliberately just two choices, both
 * framed as facts about their life, not about their performance. */
enum class PauseReason {
    AWAY,
    UNWELL,
}

/** A single stretch of time excluded from adherence accounting: a completed pause
 * (`endEpochDay` set) or the pause currently in progress (`endEpochDay` null, meaning
 * "still going, up to today"). This is the fix for the app's one genuinely harmful
 * default: without it, a single fully-elapsed week with zero sessions drops a
 * standing-tier person back to chair-supported, and the most likely reason for an empty
 * week in this population is a hospital stay, an illness, or a trip to see family. A
 * rule that treats a real-life interruption exactly like a lapse in motivation punishes
 * the person for the event this app exists to prevent. */
data class PausePeriod(
    val startEpochDay: Long,
    val endEpochDay: Long?,
    val reason: PauseReason,
) {
    fun isActiveOn(date: LocalDate): Boolean {
        val day = date.toEpochDay()
        val end = endEpochDay ?: day
        return day in startEpochDay..end
    }
}

/** Turns a set of pause periods into the week indices [ProgramEngine] and
 * [AdherenceTracker] must treat as excluded: neither strong nor a lapse, and never able
 * to break a streak or trigger a tier regression on their own. */
object PauseTracker {
    fun excludedWeekIndices(
        programStart: LocalDate,
        pausePeriods: List<PausePeriod>,
        asOfDate: LocalDate,
    ): Set<Int> {
        val weeks = mutableSetOf<Int>()
        for (period in pausePeriods) {
            val start = LocalDate.ofEpochDay(period.startEpochDay)
            val end = period.endEpochDay?.let(LocalDate::ofEpochDay) ?: asOfDate
            var cursor = start
            while (!cursor.isAfter(end)) {
                weeks += WeekCalendar.weekIndex(programStart, cursor)
                cursor = cursor.plusWeeks(1)
            }
            // make sure the boundary week (end) is always included even if the loop step
            // jumped past it
            weeks += WeekCalendar.weekIndex(programStart, end)
        }
        return weeks
    }
}
