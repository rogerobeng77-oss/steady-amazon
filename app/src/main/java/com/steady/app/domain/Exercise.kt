package com.steady.app.domain

/** The two support levels a session can be built from. Progression between them is
 * conservative and automatic; the person using the app never chooses a tier directly. */
enum class Tier {
    CHAIR_SUPPORTED,
    STANDING,
}

data class Exercise(
    val id: String,
    val name: String,
    val cue: String,
    val durationSeconds: Int,
    val tier: Tier,
)

/** A fixed, small exercise library. Chair-supported exercises are always safe to repeat;
 * standing exercises are only ever shown once the person has demonstrated two consecutive
 * strong weeks (see [ProgramEngine]). Nothing here depends on a camera, a wearable, or any
 * sensor: durations are fixed and self-paced, and the person advances by pressing the
 * remote, not by being watched. */
object ExerciseLibrary {
    val chairSupported = listOf(
        Exercise("cs_march", "Seated marching", "Sit tall, lift one knee at a time, hands resting on the chair.", 45, Tier.CHAIR_SUPPORTED),
        Exercise("cs_ankle", "Ankle pumps", "Point your toes, then pull them back up. Keeps circulation moving.", 30, Tier.CHAIR_SUPPORTED),
        Exercise("cs_arm_raise", "Seated arm raises", "Raise both arms slowly to shoulder height, then lower.", 40, Tier.CHAIR_SUPPORTED),
        Exercise("cs_sit_to_stand", "Sit-to-stand, hands on chair", "Push up to standing using the chair arms, then sit back down slowly.", 45, Tier.CHAIR_SUPPORTED),
        Exercise("cs_side_bend", "Seated side reach", "Reach one arm gently toward the floor beside the chair, then the other side.", 30, Tier.CHAIR_SUPPORTED),
    )

    val standing = listOf(
        Exercise("st_heel_toe", "Heel-to-toe stand", "Stand behind the chair for support. Place one foot directly in front of the other.", 30, Tier.STANDING),
        Exercise("st_single_leg", "One-leg stand, hand on chair", "Hold the chair back. Lift one foot slightly off the floor.", 20, Tier.STANDING),
        Exercise("st_march", "Standing march", "Hold the chair back. March in place, knees lifting gently.", 40, Tier.STANDING),
        Exercise("st_side_step", "Side steps with support", "Hold the chair. Step sideways, then bring feet back together.", 30, Tier.STANDING),
        Exercise("st_calf_raise", "Standing calf raises", "Hold the chair back. Rise onto your toes, then lower slowly.", 30, Tier.STANDING),
    )

    fun forTier(tier: Tier): List<Exercise> = when (tier) {
        Tier.CHAIR_SUPPORTED -> chairSupported
        Tier.STANDING -> chairSupported.take(2) + standing
    }
}
