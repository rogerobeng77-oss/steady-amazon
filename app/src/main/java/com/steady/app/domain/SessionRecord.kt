package com.steady.app.domain

import java.time.LocalDate

/**
 * One finished session, as much of it as the app watched happen.
 *
 * Before this existed, a completed session was a date and nothing else, and the history
 * screen could therefore only ever be a column of dates — which is why it read as a list
 * that had failed to load. The three facts here are the three a person or a family member
 * actually asks about a session afterwards: how long was she at it, how did she find it,
 * and did she get through the whole thing.
 *
 * None of it is inferred. [secondsElapsed] is wall-clock time from the press of "Begin" to
 * the press that finishes the last movement; [movementsDone] counts the movements actually
 * stepped through, which is fewer than [movementsPlanned] when somebody started partway in
 * from a tile on Home. [feedback] is written a moment later, when the answer to "How did
 * that feel?" arrives, and is null until then — a session the person walked away from
 * without answering is a real thing and is shown as one rather than given a default.
 */
data class SessionRecord(
    val date: LocalDate,
    val secondsElapsed: Int,
    val movementsDone: Int,
    val movementsPlanned: Int,
    val feedback: SessionFeedback?,
) {
    fun withFeedback(answer: SessionFeedback): SessionRecord = copy(feedback = answer)

    /** Pipe-separated rather than JSON: this goes into a DataStore string set, there is
     * one writer and one reader, and a parser small enough to read in full cannot drift
     * from the thing it parses. */
    fun encode(): String =
        "${date.toEpochDay()}|$secondsElapsed|$movementsDone|$movementsPlanned|${feedback?.name.orEmpty()}"

    companion object {
        /** Returns null for anything it does not recognise rather than throwing. A stored
         * record that cannot be read is a row missing from a history screen; a stored
         * record that throws is an app that will not open. */
        fun decode(encoded: String): SessionRecord? {
            val parts = encoded.split("|")
            if (parts.size != 5) return null
            val epochDay = parts[0].toLongOrNull() ?: return null
            val seconds = parts[1].toIntOrNull() ?: return null
            val done = parts[2].toIntOrNull() ?: return null
            val planned = parts[3].toIntOrNull() ?: return null
            if (seconds < 0 || done < 0 || planned < 0) return null
            val feedback = parts[4].takeIf(String::isNotEmpty)?.let { raw ->
                runCatching { SessionFeedback.valueOf(raw) }.getOrNull()
            }
            return SessionRecord(LocalDate.ofEpochDay(epochDay), seconds, done, planned, feedback)
        }
    }
}

/** A row of the history screen: the day, and whatever detail was kept about it. */
data class SessionHistoryEntry(val date: LocalDate, val detail: SessionRecord?)

object SessionHistory {
    /**
     * Newest first, one row per day a session was completed.
     *
     * The dates lead and the detail follows, never the other way round: the dates are what
     * the whole programme is computed from, so a day that has a record but is somehow
     * missing from the date set is a day that did not count towards anything and must not
     * appear in a record of what counted. A day with no detail still gets its row.
     */
    fun rows(
        completedDates: List<LocalDate>,
        records: List<SessionRecord>,
    ): List<SessionHistoryEntry> {
        val byDate = records.associateBy { it.date }
        return completedDates.distinct().sortedDescending().map { date ->
            SessionHistoryEntry(date, byDate[date])
        }
    }
}

/**
 * The words the history screen puts in its columns.
 *
 * Kept out of the screen so they can be tested as text, and because the same phrasing has
 * to survive being read by VoiceView in a row where the numbers are also drawn as marks.
 */
object SessionPhrasing {

    /**
     * "12 minutes". Rounded to the nearest minute and never to zero: a session that took
     * forty seconds is "under a minute", not "0 minutes", because zero reads as a failure
     * to record rather than as a short session.
     */
    fun durationLabel(seconds: Int): String {
        if (seconds < 45) return "under a minute"
        val minutes = Math.round(seconds / 60.0).toInt().coerceAtLeast(1)
        return "$minutes minute${if (minutes == 1) "" else "s"}"
    }

    /** The person's own answer, in the words they chose it by. The feedback screen says
     * "Too easy", so the history says "Too easy" — one vocabulary, start to finish. */
    fun feltLabel(feedback: SessionFeedback?): String = when (feedback) {
        SessionFeedback.TOO_EASY -> "Too easy"
        SessionFeedback.JUST_RIGHT -> "Just right"
        SessionFeedback.TOO_MUCH -> "Too much"
        null -> "Not answered"
    }

    /**
     * "5 of 5", in figures.
     *
     * Everywhere else this app spells its numbers — "Five movements, about 5 minutes." —
     * because it is talking. A history column is not talking, it is a record, and figures
     * in a column can be compared down the column at a glance while words cannot.
     */
    fun movementsLabel(done: Int, planned: Int): String = "$done of $planned"
}
