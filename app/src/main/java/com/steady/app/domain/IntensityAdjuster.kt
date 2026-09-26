package com.steady.app.domain

import kotlin.math.roundToInt

/** The result of adjusting a session for how the last one felt: the exercises to show,
 * a plain-English description of what changed (used as the deterministic fallback and
 * as the only input a narrator model is ever given), and whether anything changed at
 * all. */
data class SessionAdjustment(
    val exercises: List<Exercise>,
    val description: String,
    val changed: Boolean,
    /** Which of [AdjustmentPhrasings]' fixed sentence sets describes this session. The
     * only thing about an adjustment a model is ever told, and the only thing it can
     * choose within. */
    val kind: AdjustmentKind,
)

/** This is the rule that makes "that was too much" change tomorrow's session instead of
 * just sitting in a log nobody reads. It is deliberately small and deliberately boring:
 * three cases, hard floors and ceilings, and it can never do the one thing that would
 * make it unsafe, which is move a person between tiers. Tier progression stays entirely
 * owned by [ProgramEngine]'s two-consecutive-strong-week rule; this only ever tunes the
 * dose within whichever tier ProgramEngine already chose. A narration model is allowed
 * to describe what this function decided. It is never allowed to decide it. */
object IntensityAdjuster {
    private const val MIN_DURATION_SECONDS = 15
    private const val MAX_DURATION_SECONDS = 90
    private const val REDUCE_FACTOR = 0.7
    private const val INCREASE_FACTOR = 1.15
    private const val MIN_EXERCISES_TO_TRIM = 5

    fun adjust(baseExercises: List<Exercise>, lastFeedback: SessionFeedback?): SessionAdjustment {
        return when (lastFeedback) {
            SessionFeedback.TOO_MUCH -> shortenedSession(
                baseExercises,
                descriptionSuffix = "durations shortened by about 30 percent",
                kind = AdjustmentKind.SHORTER,
            )

            SessionFeedback.TOO_EASY -> {
                val lengthened = baseExercises.map { it.lengthenedBy(INCREASE_FACTOR) }
                SessionAdjustment(
                    exercises = lengthened,
                    description = "durations increased by about 15 percent, within the usual safe range",
                    changed = true,
                    kind = AdjustmentKind.LONGER,
                )
            }

            SessionFeedback.JUST_RIGHT, null -> SessionAdjustment(
                exercises = baseExercises,
                description = "unchanged from the standard dose for this tier",
                changed = false,
                kind = AdjustmentKind.UNCHANGED,
            )
        }
    }

    /** The deterministic sentence shown when a narration model is unreachable. Plain,
     * calm, and derived from nothing but the feedback itself, so it is always available
     * with no network and no possibility of contradicting what the rules actually did. */
    fun fallbackExplanation(feedback: SessionFeedback): String =
        AdjustmentPhrasings.default(kindFor(feedback))

    /** Which fixed sentence set a given piece of feedback maps to. */
    fun kindFor(feedback: SessionFeedback): AdjustmentKind = when (feedback) {
        SessionFeedback.TOO_MUCH -> AdjustmentKind.SHORTER
        SessionFeedback.TOO_EASY -> AdjustmentKind.LONGER
        SessionFeedback.JUST_RIGHT -> AdjustmentKind.UNCHANGED
    }

    /** The first session after a pause ends. [ProgramEngine] already held the tier
     * steady across the pause rather than dropping it (see [PauseTracker]); this is the
     * other half of "hold their level rather than dropping it, and offer something
     * shorter on return instead of resuming where they stopped": the tier is exactly
     * what it was, but today's dose is eased back in, the same shape as a "too much"
     * adjustment, on the reasonable assumption that time off is time off regardless of
     * why. It only ever applies on the single day a pause is resumed. */
    fun welcomeBackAdjustment(baseExercises: List<Exercise>): SessionAdjustment = shortenedSession(
        baseExercises,
        descriptionSuffix = "first session back after a pause, kept shorter on purpose",
        kind = AdjustmentKind.WELCOME_BACK,
    )

    fun fallbackWelcomeBackExplanation(): String =
        AdjustmentPhrasings.default(AdjustmentKind.WELCOME_BACK)

    private fun shortenedSession(
        baseExercises: List<Exercise>,
        descriptionSuffix: String,
        kind: AdjustmentKind,
    ): SessionAdjustment {
        val shortened = baseExercises.map { it.shortenedBy(REDUCE_FACTOR) }
        val trimmed = if (shortened.size >= MIN_EXERCISES_TO_TRIM) shortened.dropLast(1) else shortened
        val description = if (trimmed.size < shortened.size) {
            "$descriptionSuffix and one exercise removed"
        } else {
            descriptionSuffix
        }
        return SessionAdjustment(exercises = trimmed, description = description, changed = true, kind = kind)
    }

    private fun Exercise.shortenedBy(factor: Double): Exercise {
        val adjusted = (durationSeconds * factor).roundToInt().coerceAtLeast(MIN_DURATION_SECONDS)
        return copy(durationSeconds = adjusted)
    }

    private fun Exercise.lengthenedBy(factor: Double): Exercise {
        val adjusted = (durationSeconds * factor).roundToInt().coerceAtMost(MAX_DURATION_SECONDS)
        return copy(durationSeconds = adjusted)
    }
}
