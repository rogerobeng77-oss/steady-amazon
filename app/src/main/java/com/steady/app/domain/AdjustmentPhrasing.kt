package com.steady.app.domain

/** What the rules did to today's session. The model is never told this in prose it can
 * paraphrase; it is told which kind applies and is offered this app's own sentences for
 * it. */
enum class AdjustmentKind {
    SHORTER,
    LONGER,
    UNCHANGED,
    WELCOME_BACK,
}

/**
 * The complete set of sentences Steady will ever show under "Start Today's Session".
 *
 * Every one of them was written here, by a person, and reviewed against the two things
 * that make this app's audience different: they are at risk of falling, and they are
 * standing in their own living room doing what the television says. So none of these
 * sentences tells anyone how to move, none of them mentions a chair, a leg, a hospital
 * or a doctor, and none of them contains a number.
 *
 * A model may pick which of these to show. It may not write one. That is a structural
 * property, not a rule a guard enforces after the fact: [Narrator.chooseAdjustmentPhrasing]
 * returns an `Int`, so there is no string on the wire for a model to put words into.
 * The worst a compromised, confused or hallucinating model can do to this screen is
 * choose the second-best sentence out of three that all say the same true thing.
 *
 * Index 0 of every list is the deterministic default, used whenever no model answered,
 * so the no-network path and the model path differ only in which of these appears.
 */
object AdjustmentPhrasings {

    private val shorter = listOf(
        "Today's session is a little shorter, since the last one felt like a lot.",
        "A slightly shorter session today, after you said the last one felt like a lot.",
        "Today's is trimmed down a bit, because the last one felt like a lot.",
    )

    private val longer = listOf(
        "Today's session runs a little longer, since the last one felt easy.",
        "A slightly longer session today, after you said the last one felt easy.",
        "Today's runs on a bit more, because the last one felt easy.",
    )

    private val unchanged = listOf(
        "Today's session is the usual length.",
        "Today's session is the same as usual.",
        "No change today: this is the usual session.",
    )

    private val welcomeBack = listOf(
        "Welcome back. Today's session is a little shorter to ease back in, at the same level as before.",
        "Good to see you again. Today's is a little shorter to ease back in, at the level you were on.",
        "Welcome back. Nothing has been lost: today is a shorter one at the same level as before.",
    )

    fun options(kind: AdjustmentKind): List<String> = when (kind) {
        AdjustmentKind.SHORTER -> shorter
        AdjustmentKind.LONGER -> longer
        AdjustmentKind.UNCHANGED -> unchanged
        AdjustmentKind.WELCOME_BACK -> welcomeBack
    }

    /** The sentence shown when nothing but the rules have run. */
    fun default(kind: AdjustmentKind): String = options(kind).first()

    /**
     * Resolve a model's choice. Anything that is not a valid index into this app's own
     * list — out of range, negative, or absent — collapses to the default rather than
     * failing, so a malformed answer is indistinguishable from no answer at all.
     */
    fun resolve(kind: AdjustmentKind, choice: Int?): String {
        val options = options(kind)
        val index = choice ?: return options.first()
        return options.getOrNull(index) ?: options.first()
    }

    fun isValidChoice(kind: AdjustmentKind, choice: Int?): Boolean =
        choice != null && choice in options(kind).indices
}
