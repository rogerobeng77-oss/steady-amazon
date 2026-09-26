package com.steady.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntensityAdjusterTest {

    private val base = ExerciseLibrary.chairSupported

    @Test
    fun `just right feedback leaves the session unchanged`() {
        val result = IntensityAdjuster.adjust(base, SessionFeedback.JUST_RIGHT)
        assertEquals(base, result.exercises)
        assertFalse(result.changed)
    }

    @Test
    fun `no feedback yet leaves the session unchanged`() {
        val result = IntensityAdjuster.adjust(base, null)
        assertEquals(base, result.exercises)
        assertFalse(result.changed)
    }

    @Test
    fun `too much feedback shortens every exercise`() {
        val result = IntensityAdjuster.adjust(base, SessionFeedback.TOO_MUCH)
        assertTrue(result.changed)
        result.exercises.forEachIndexed { i, adjusted ->
            assertTrue(
                "adjusted duration ${adjusted.durationSeconds} should be <= original ${base[i].durationSeconds}",
                adjusted.durationSeconds <= base[i].durationSeconds,
            )
        }
    }

    @Test
    fun `too much feedback never drops duration below the safety floor`() {
        val tiny = listOf(base[0].copy(durationSeconds = 16))
        val result = IntensityAdjuster.adjust(tiny, SessionFeedback.TOO_MUCH)
        assertTrue(result.exercises.all { it.durationSeconds >= 15 })
    }

    @Test
    fun `too much feedback drops the last exercise only when there are enough to spare`() {
        val fiveExercises = base // chairSupported has 5
        val result = IntensityAdjuster.adjust(fiveExercises, SessionFeedback.TOO_MUCH)
        assertEquals(fiveExercises.size - 1, result.exercises.size)

        val threeExercises = base.take(3)
        val smallResult = IntensityAdjuster.adjust(threeExercises, SessionFeedback.TOO_MUCH)
        assertEquals(threeExercises.size, smallResult.exercises.size)
    }

    @Test
    fun `too easy feedback lengthens every exercise but never past the safety ceiling`() {
        val result = IntensityAdjuster.adjust(base, SessionFeedback.TOO_EASY)
        assertTrue(result.changed)
        result.exercises.forEachIndexed { i, adjusted ->
            assertTrue(adjusted.durationSeconds >= base[i].durationSeconds)
            assertTrue(adjusted.durationSeconds <= 90)
        }
    }

    @Test
    fun `too easy feedback never changes exercise identity or tier, only duration`() {
        val result = IntensityAdjuster.adjust(base, SessionFeedback.TOO_EASY)
        assertEquals(base.map { it.id }, result.exercises.map { it.id })
        assertTrue(result.exercises.all { it.tier == Tier.CHAIR_SUPPORTED })
    }

    @Test
    fun `too much feedback never changes exercise identity or tier, only duration and count`() {
        val result = IntensityAdjuster.adjust(base, SessionFeedback.TOO_MUCH)
        assertTrue(base.map { it.id }.containsAll(result.exercises.map { it.id }))
        assertTrue(result.exercises.all { it.tier == Tier.CHAIR_SUPPORTED })
    }

    @Test
    fun `fallback explanations are distinct per feedback and never blank`() {
        val explanations = SessionFeedback.entries.map { IntensityAdjuster.fallbackExplanation(it) }
        assertEquals(explanations.size, explanations.toSet().size)
        assertTrue(explanations.all { it.isNotBlank() })
    }

    @Test
    fun `welcome back adjustment shortens the session the same way too much does`() {
        val result = IntensityAdjuster.welcomeBackAdjustment(base)
        assertTrue(result.changed)
        result.exercises.forEachIndexed { i, adjusted ->
            assertTrue(adjusted.durationSeconds <= base[i].durationSeconds)
        }
        assertEquals(base.size - 1, result.exercises.size)
    }

    @Test
    fun `welcome back adjustment never changes tier or exercise identity`() {
        val result = IntensityAdjuster.welcomeBackAdjustment(base)
        assertTrue(base.map { it.id }.containsAll(result.exercises.map { it.id }))
        assertTrue(result.exercises.all { it.tier == Tier.CHAIR_SUPPORTED })
    }
}
