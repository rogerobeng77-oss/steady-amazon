package com.steady.app.data

/**
 * Steady's own text-size setting.
 *
 * It exists because Fire TV does not have one. Fire OS 7 is Android 9 and Amazon's
 * accessibility documentation for Fire TV covers VoiceView, audio description and
 * captions — and nothing else. Google tells Android TV developers to respect the
 * system's text scaling; Fire TV is not Android TV and there is no such setting to
 * respect. the shared Fire TV craft reference is blunt about what follows: for an app whose
 * audience is older, an in-app text size control is worth more than any other
 * accessibility work on the list.
 *
 * Three steps, not a slider. A slider on a remote is a held direction and a guess.
 *
 * [LARGEST] is capped well short of 1.5x on purpose. Past roughly 38sp, making text
 * bigger costs more than it buys: lines shorten, paragraphs turn into towers, and the
 * screen stops fitting. Beyond that the layout has to change instead, which is what
 * [dropsDecoration] is for — at the larger steps Home drops its wordmark and its dose
 * dots rather than shrinking anything, keeping the tier line, the count in words and the
 * one button.
 *
 * **The multipliers are set by where they arrive, not by a fixed ratio.** They were 1.18
 * and 1.35 when the base scale bottomed out at 24sp; the base scale now starts at 26sp
 * and its instruction step is 30sp (see `SteadyTheme`), so the same multipliers would
 * have overshot — and did. At 1.35 the ready step lost the fifth movement off the bottom
 * of its running order and sliced the last line of "press the remote when you are ready
 * to move on" in half, on the one screen in the whole set a reviewer singled out as
 * properly composed.
 *
 * So the ceiling is what is fixed: Largest puts body text at about 38sp and meta at about
 * 33sp, which is where this scale's predecessor arrived and what the layouts below are
 * built for. A higher floor needs a shorter climb to reach the same place.
 */
enum class TextSize(val scale: Float, val label: String) {
    NORMAL(1.0f, "Normal"),
    LARGER(1.12f, "Larger"),
    LARGEST(1.26f, "Largest"),
    ;

    /** True where the layout should shed decoration rather than scale it. */
    val dropsDecoration: Boolean get() = this != NORMAL
}
