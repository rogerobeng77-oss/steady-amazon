package com.steady.app.domain

/** Builds the plain-language weekly update sent to the one named family member. This is
 * mocked at the boundary, honestly: Steady has no email or SMS integration, so "sending"
 * writes this text to local history and marks it sent. The text itself is real, not a
 * placeholder, and is the thing under test here.
 *
 * Two rules the wording follows on purpose:
 * 1. No clinical or alarming language. A week with no sessions is reported as a fact, not
 *    a failure ("missed", "failed", "non-compliant" do not appear anywhere in this file).
 * 2. No session content, only the count and the tier name. The family member does not
 *    need to know which exercises were done to know whether their person is keeping this
 *    up. */
object CaregiverSummaryBuilder {

    fun build(
        familyMemberName: String,
        weekNumber: Int,
        sessionsCompletedThisWeek: Int,
        prescribedPerWeek: Int,
        streakWeeks: Int,
        tier: Tier,
        /** Days between the most recent completed session and the day this update is
         * being written, or null when nothing has ever been completed. Computed by
         * [AdherenceTracker.daysSinceLastSession] from the recorded dates; the sentence
         * it produces is written below rather than anywhere a model can reach. */
        daysSinceLastSession: Int? = null,
    ): String {
        val name = familyMemberName.trim().ifEmpty { "there" }
        // "1 of 3 sessions", not "1 of 3 session". In "N of M", the noun agrees with M,
        // the set being counted from, not with N — the same way "one of three people" is
        // not "one of three person". It was agreeing with the wrong number, so the one
        // message a family member ever reads about somebody's week opened with a grammar
        // mistake, and Home two presses away already said "1 of 3 sessions done this
        // week" correctly on the same data.
        val sessionWord = if (prescribedPerWeek == 1) "session" else "sessions"
        val tierPhrase = when (tier) {
            Tier.CHAIR_SUPPORTED -> "the seated strength and balance routine"
            Tier.STANDING -> "the standing balance routine"
        }

        val activityLine = if (sessionsCompletedThisWeek == 0) {
            "No sessions were logged this week."
        } else {
            "$sessionsCompletedThisWeek of $prescribedPerWeek $sessionWord done this week, on $tierPhrase."
        }

        // The sentence this update could not carry until now. A count of sessions in a
        // week is silent about *when* in the week they were, so "2 of 3" and "2 of 3,
        // both nine days ago" read identically to the person two hundred miles away —
        // and the second one is the one worth knowing. Only shown once the gap is longer
        // than the programme's own rhythm, so a normal rest day never becomes an item.
        val dayWord = if (daysSinceLastSession == 1) "day" else "days"
        val gapLine = daysSinceLastSession
            ?.takeIf { it >= CatchUpEvaluator.QUIET_DAYS }
            ?.let { "The last one was $it $dayWord ago." }

        // Past tense when the week being reported is empty. "This makes 2 weeks in a
        // row" reads as credit for the week in the same message that just said nothing
        // was logged in it — technically true, because a week in progress does not break
        // a streak, and still the wrong thing to tell somebody who is reading this to
        // find out how their mother is getting on. The gap sentence above made it
        // visible; it was always there.
        val streakLine = when {
            sessionsCompletedThisWeek == 0 && streakWeeks >= 2 ->
                "The $streakWeeks weeks before this one each had at least one session."
            sessionsCompletedThisWeek == 0 && streakWeeks == 1 ->
                "The week before this one had a session in it."
            streakWeeks >= 2 -> "This makes $streakWeeks weeks in a row with at least one session."
            streakWeeks == 1 -> "This is the first week in a new streak."
            else -> "There is no active streak right now."
        }

        // Kept for the empty week only, and last, so the message ends on the offer
        // rather than on the gap. Rule 1 at the top of this file is what makes this a
        // fact and not a verdict, and it survives the addition of [gapLine]: a week with
        // nothing in it is still reported as a week with nothing in it.
        val closingLine = if (sessionsCompletedThisWeek == 0) {
            "That happens, and the next one is ready whenever it works."
        } else {
            null
        }

        // At most five sentences, which is also [SummaryGuard]'s shape limit: greeting,
        // activity, gap, streak, closing. The empty-week reassurance used to be three
        // sentences of its own and there was no room for anything else.
        return listOfNotNull(
            "Hi $name, here is this week's update from Steady (week $weekNumber).",
            activityLine,
            gapLine,
            streakLine,
            closingLine,
        ).joinToString(" ")
    }
}
