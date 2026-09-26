package com.steady.app.data

/**
 * The one place model-written prose is still allowed into Steady, and the check it has
 * to pass.
 *
 * The weekly caregiver update is the only remaining free-text path in this app. It is
 * allowed to stay free text for a reason that does not apply anywhere else here: it
 * describes a record the rules already produced, it is read by a family member two
 * hundred miles away, and nobody moves their body because of it. Everything on the
 * exercise path now returns an index into this app's own sentences instead (see
 * [com.steady.app.domain.AdjustmentPhrasings]).
 *
 * "Free text" is not "unchecked", though. The failure this guard exists for is the one
 * that is easy to write and hard to notice: a warm, fluent, entirely invented clinical
 * observation.
 *
 *     "Mum only managed one session and seemed unsteady on Thursday — it may be worth
 *      talking to her GP about her falls risk."
 *
 * Every clause after the first is fabricated. Nothing in [SummaryFacts] says anything
 * about Thursday, about steadiness, or about a GP. A guard that only checked for an
 * empty string would send it.
 *
 * Three rules, in the order they are cheapest to fail:
 *
 * 1. **Shape.** A weekly text message, not an essay: 30 to 400 characters, at most five
 *    sentences, no links and no contact details.
 * 2. **Numbers.** Every number in the text, written as digits or as an English word,
 *    must be one of the numbers the app handed the model. A summary is a restatement of
 *    a count; a number that was not in the input was invented.
 * 3. **Vocabulary.** No clinical noun, no instruction, no alarm. The denylist is
 *    deliberately blunt — it will occasionally reject a harmless sentence, and that
 *    costs nothing, because the deterministic summary underneath it is already correct
 *    and already the default path.
 *
 * On rejection the caller uses [CaregiverSummaryBuilder][com.steady.app.domain.CaregiverSummaryBuilder]'s
 * text and tags it as standard wording. The rejected string is **not** shown, not
 * persisted and not put behind a disclosure control: a rejection rendered on screen is
 * a second output path around the guard. It goes to the log, where a developer is and
 * the family member is not.
 */
object SummaryGuard {

    const val MIN_CHARS = 30
    const val MAX_CHARS = 400
    const val MAX_SENTENCES = 5

    /** Why a candidate was refused. Names the rule and the trigger, never carries the text. */
    data class Rejection(val rule: String, val detail: String) {
        override fun toString(): String = "$rule ($detail)"
    }

    /**
     * Clinical framing, instructions, and alarm. Matched on word boundaries against the
     * lower-cased text, so "doctor" trips and "documented" does not.
     *
     * "fall", "falls" and "balance" are deliberately absent: this is a falls-prevention
     * programme and the app's own wording uses them. What is banned is turning the
     * programme into a diagnosis, a referral, or a warning about a person.
     */
    private val DENIED = listOf(
        // clinical actors and settings
        "doctor", "doctors", "gp", "physician", "nurse", "physio", "physiotherapist",
        "hospital", "clinic", "surgery", "a&e", "paramedic", "consultant", "specialist",
        // medicine
        "medication", "medications", "medicine", "medicines", "pill", "pills", "tablet",
        "tablets", "dose", "dosage", "prescribe", "prescribed", "prescription",
        // diagnosis and deterioration
        "diagnose", "diagnosed", "diagnosis", "symptom", "symptoms", "condition",
        "frail", "frailty", "dementia", "arthritis", "osteoporosis", "stroke",
        "deteriorating", "declining", "decline", "declined", "worsening", "unsteady", "unstable",
        "wobbly", "dizzy", "dizziness", "injured", "injury", "fracture", "fractured",
        "hurt", "pain", "painful", "bruise", "bruised",
        // alarm and judgement
        "concerned", "concerning", "worried", "worrying", "urgent", "urgently",
        "alarming", "danger", "dangerous", "risk", "risky", "emergency", "warning",
        "failed", "failing", "non-compliant", "noncompliant", "neglect", "struggling",
        // instructions to anybody
        "should", "must", "need to", "needs to", "ought to", "make sure", "be sure to",
        "try to", "stop taking", "check on", "intervene", "supervise",
        "call me", "phone number",
    )

    /**
     * Matched anywhere, not on a word boundary, because these do not sit at one:
     * "http" is inside "https", and an address has no space before its "@".
     */
    private val DENIED_SUBSTRINGS = listOf("http", "www.", "://", "@", ".com", ".co.uk")

    /** Number words the guard understands, so "three sessions" is checked like "3". */
    private val NUMBER_WORDS = mapOf(
        "zero" to 0, "none" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12,
    )

    private val DIGIT_RUN = Regex("""\d+""")
    private val WORD = Regex("""[a-z']+""")
    private val SENTENCE_END = Regex("""[.!?]+""")

    /**
     * @return null when the text may be shown, or the [Rejection] that stopped it.
     */
    fun check(text: String, facts: SummaryFacts): Rejection? {
        val trimmed = text.trim()
        if (trimmed.length < MIN_CHARS) return Rejection("length", "under $MIN_CHARS characters")
        if (trimmed.length > MAX_CHARS) return Rejection("length", "over $MAX_CHARS characters")

        val sentences = SENTENCE_END.split(trimmed).count { it.isNotBlank() }
        if (sentences > MAX_SENTENCES) return Rejection("shape", "$sentences sentences")

        val lower = trimmed.lowercase()

        val allowedNumbers = allowedNumbers(facts)
        DIGIT_RUN.findAll(lower).forEach { match ->
            val value = match.value.toIntOrNull() ?: return Rejection("number", "unreadable digits")
            if (value !in allowedNumbers) return Rejection("number", "$value is not one of the facts")
        }
        WORD.findAll(lower).forEach { match ->
            val value = NUMBER_WORDS[match.value]
            if (value != null && value !in allowedNumbers) {
                return Rejection("number", "\"${match.value}\" is not one of the facts")
            }
        }

        DENIED_SUBSTRINGS.forEach { term ->
            if (lower.contains(term)) return Rejection("contact", term)
        }
        DENIED.forEach { term ->
            if (containsTerm(lower, term)) return Rejection("vocabulary", term)
        }

        return null
    }

    /**
     * Every number the model was given, plus 0 and 1 unconditionally. Those two are in
     * the input's shape rather than its values ("no sessions", "the first week"), and
     * excluding them would reject the app's own deterministic wording.
     *
     * [SummaryFacts.daysSinceLastSession] joins the set **only when it is non-null**,
     * which is the same as saying "only when a session has ever been completed and the
     * app is therefore about to use that number itself". Leaving it out would have the
     * guard reject Steady's own sentence about the gap and quietly fall back to a
     * summary with the gap removed from it, which is the one failure worse than saying
     * nothing: a family member told a week was fine because the check refused the
     * evidence.
     *
     * It widens the set by at most one value, it is arithmetic over dates that only a
     * remote press writes, and it changes none of the other three rules. The residual
     * is the one this set has always had: the check is positional-blind, so an allowed
     * number is allowed in any sentence position. That is why the deterministic builder
     * stays the default path rather than the fallback of last resort.
     */
    private fun allowedNumbers(facts: SummaryFacts): Set<Int> = setOfNotNull(
        0,
        1,
        facts.weekNumber,
        facts.sessionsCompletedThisWeek,
        facts.prescribedPerWeek,
        facts.streakWeeks,
        facts.daysSinceLastSession,
    )

    /**
     * Word-boundary containment for plain terms; plain containment for anything holding
     * a character that cannot sit inside a word ("a&e", "www.", "@", "http").
     */
    private fun containsTerm(haystack: String, term: String): Boolean {
        if (term.any { !it.isLetterOrDigit() && it != ' ' && it != '-' }) {
            return haystack.contains(term)
        }
        var from = 0
        while (true) {
            val at = haystack.indexOf(term, from)
            if (at < 0) return false
            val before = if (at == 0) ' ' else haystack[at - 1]
            val afterIndex = at + term.length
            val after = if (afterIndex >= haystack.length) ' ' else haystack[afterIndex]
            if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
            from = at + 1
        }
    }
}
