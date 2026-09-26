package com.steady.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.steady.app.R
import com.steady.app.data.TextSize
import com.steady.app.domain.Exercise
import com.steady.app.domain.SessionFeedback
import com.steady.app.ui.components.ExerciseTile
import com.steady.app.ui.components.FeelingTile
import com.steady.app.ui.components.HeroBand
import com.steady.app.ui.components.STEADY_SAFE_X
import com.steady.app.ui.components.STEADY_SAFE_Y
import com.steady.app.ui.components.SteadyButton
import com.steady.app.ui.components.SessionFrame
import com.steady.app.ui.components.SteadyPage
import com.steady.app.ui.components.TileState
import com.steady.app.ui.theme.SteadyColors
import kotlinx.coroutines.delay

/**
 * Today's session, from standing up to sitting back down, as one screen that changes
 * rather than three screens that replace each other.
 *
 * Ready, each movement, and the question at the end are all drawn in [SessionFrame]:
 * artwork in the same place running off the same edge, words in the same column at the
 * same measure, and the same strip along the bottom. Only the contents move. Before this
 * they were three unrelated layouts — a centred stack, a two-column split, and another
 * centred stack — and the session read as three small apps rather than one sequence.
 *
 * Every advance is a remote press, never a countdown that forces the person forward and
 * never a camera checking whether they kept up. The timer is a pace suggestion shown as a
 * bar, not a gate: the button is always focused and always pressable immediately.
 */
@Composable
fun SessionScreen(
    exercises: List<Exercise>,
    /** Seconds from the first movement appearing to the last one being finished, how many
     * movements were actually stepped through, and how many were on the list. */
    onSessionCompleted: (secondsElapsed: Int, movementsDone: Int, movementsPlanned: Int) -> Unit,
    onFeedback: (SessionFeedback) -> Unit,
    onReturnHome: () -> Unit,
    textSize: TextSize,
    startIndex: Int = 0,
) {
    // -1 is the ready step, exercises.size is the finished step. One integer for the
    // whole sequence, so "where am I" has exactly one answer.
    val firstIndex = if (startIndex in exercises.indices) (startIndex - 1).coerceAtLeast(0) else 0
    var index by remember { mutableIntStateOf(if (startIndex in exercises.indices) startIndex - 1 else -1) }

    // Wall-clock, started when the first movement is put on the screen rather than when
    // the app is opened. How long somebody stood in front of the television reading the
    // ready step is not how long they exercised for, and the history screen says
    // "12 minutes" about the exercise.
    var startedAtMillis by remember { mutableStateOf<Long?>(null) }
    val beginNow = { if (startedAtMillis == null) startedAtMillis = System.currentTimeMillis() }
    LaunchedEffect(Unit) { if (index >= 0) beginNow() }

    when {
        index < 0 -> ReadyStep(
            exercises = exercises,
            textSize = textSize,
            onBegin = {
                beginNow()
                index = 0
            },
        )

        index < exercises.size -> ExerciseStep(
            exercises = exercises,
            index = index,
            textSize = textSize,
            onAdvance = { index++ },
        )

        else -> {
            var recorded by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (!recorded) {
                    recorded = true
                    val started = startedAtMillis ?: System.currentTimeMillis()
                    onSessionCompleted(
                        ((System.currentTimeMillis() - started) / 1000L).toInt().coerceAtLeast(0),
                        exercises.size - firstIndex,
                        exercises.size,
                    )
                }
            }
            FeedbackStep(
                textSize = textSize,
                onFeedback = { feedback ->
                    onFeedback(feedback)
                    onReturnHome()
                },
            )
        }
    }
}

/**
 * 208dp, not 220dp and not 176dp, and the number is load-bearing on a 540dp-tall canvas.
 *
 * 176dp fitted all five tiles inside the panel and ellipsised two of the names. 220dp
 * fixed the names and made each tile 124dp tall, which pushed the bottom strip up until
 * "Done, finish session" was drawn over "Movement 5 of 5". The tile has to hold both a
 * name and a height, and the name is set in the label step, so the width is a function of
 * that step: it was 200dp while the label was 20sp and it is 208dp now the label is 26sp.
 * With the 12dp the band keeps at each end that is 184dp of label, which takes
 * "Sit-to-stand, hands on chair" on two lines, and 117dp of tile height, which leaves the
 * words column clear.
 */
private val STRIP_TILE = 208.dp

/**
 * The screen somebody stands in front of before a falls-prevention session, so the two
 * things on it that are not negotiable are the chair and the running order.
 *
 * It has two layouts, because at the accessibility text sizes it stops fitting in one.
 * At 1.35x the words alone — heading, the two facts, the button and the note about the
 * remote — are 394dp of a 540dp screen, and five photographic tiles need 200dp more.
 * Something has to give, and it must not be the list: this is the only place the person
 * is told what they are about to do.
 *
 * So at the larger sizes the class photograph gives way instead. It is the decoration on
 * this screen; the movements are the content. The band shortens to a header, the words
 * keep their column, and the running order moves to a second column beside them, where
 * it is set as five named lines rather than five pictures — the same trade Home makes
 * when it sheds its wordmark and its dose dots rather than shrinking anything.
 */
@Composable
private fun ReadyStep(exercises: List<Exercise>, textSize: TextSize, onBegin: () -> Unit) {
    if (textSize.dropsDecoration) {
        ReadyStepBeside(exercises = exercises, textSize = textSize, onBegin = onBegin)
    } else {
        ReadyStepStacked(exercises = exercises, textSize = textSize, onBegin = onBegin)
    }
}

@Composable
private fun ReadyStepStacked(
    exercises: List<Exercise>,
    textSize: TextSize,
    onBegin: () -> Unit,
) {
    SessionFrame(
        art = R.drawable.photo_session_ready,
        artDescription = "An exercise class for older adults, warming up together",
        stripLabel = "In this order",
        textSize = textSize,
        // The running order is not a preview of something shown elsewhere on this
        // screen; it is the screen's answer to "what am I about to do". Left on the
        // default it was deleted at both accessibility sizes and the label that
        // introduces it survived, so the screen read "In this order" and then stopped.
        stripIsDecoration = false,
        left = { ReadyWords(exercises = exercises, onBegin = onBegin) },
        strip = {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(end = 24.dp),
            ) {
                items(items = exercises, key = { it.id }) { exercise ->
                    ExerciseTile(
                        exercise = exercise,
                        onSelect = null,
                        artWidth = STRIP_TILE,
                        state = TileState.UPCOMING,
                    )
                }
            }
        },
    )
}

@Composable
private fun ReadyStepBeside(
    exercises: List<Exercise>,
    textSize: TextSize,
    onBegin: () -> Unit,
) {
    // Shorter than the 312dp of the other steps, because the running order beside the
    // words needs clean ground to start on. How much shorter is set by how tall the
    // words get: at 1.35x they run nearly to the bottom margin and the band has to give
    // up more, at 1.18x they do not and a taller band keeps the screen from going
    // top-heavy.
    val heroHeight = if (textSize == TextSize.LARGEST) 186.dp else 232.dp

    SteadyPage {
        HeroBand(
            photo = R.drawable.photo_session_ready,
            contentDescription = "An exercise class for older adults, warming up together",
            textSideWeight = 0.60f,
            heightFraction = heroHeight / 540.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(heroHeight),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = STEADY_SAFE_X, top = STEADY_SAFE_Y + 8.dp, end = STEADY_SAFE_X),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(modifier = Modifier.width(500.dp)) {
                ReadyWords(exercises = exercises, onBegin = onBegin)
            }
            Column(
                // Starts under the photograph rather than on it. The words column can
                // sit on the band because the scrim is built for it; a second column on
                // the bright side of the frame cannot.
                modifier = Modifier.weight(1f).padding(top = heroHeight - 46.dp),
            ) {
                Text(
                    text = "In this order",
                    style = MaterialTheme.typography.labelLarge,
                    color = SteadyColors.LinenDim,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                exercises.forEachIndexed { i, exercise ->
                    Row(modifier = Modifier.padding(bottom = 10.dp)) {
                        // Counted, because "In this order" is a claim the screen then has
                        // to keep. The numeral is the dim colour and the name is the
                        // bright one, so the eye lands on the movement and the position
                        // is there when it is wanted.
                        Text(
                            text = "${i + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = SteadyColors.LinenDim,
                            modifier = Modifier.width(34.dp),
                        )
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = SteadyColors.Linen,
                        )
                    }
                }
            }
        }
    }
}

/** The words, identical in both layouts: heading, the two facts, the button, and the
 * note about the remote under it.
 *
 * "Have a sturdy chair within reach." is its own paragraph in the brighter colour rather
 * than the opening clause of a longer one. It is the precondition for a sit-to-stand and
 * it was reading as more preamble. "Nothing here is timed — press the remote when you
 * are ready to move on." sits under the button because that is what it describes. */
@Composable
private fun ColumnScope.ReadyWords(exercises: List<Exercise>, onBegin: () -> Unit) {
    Text(
        text = "Today's session",
        style = MaterialTheme.typography.headlineLarge,
        color = SteadyColors.Linen,
    )
    Text(
        text = summaryLine(exercises),
        style = MaterialTheme.typography.bodyLarge,
        color = SteadyColors.LinenDim,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(
        // Non-breaking space, so that when the column is narrow enough to wrap this the
        // break falls after "chair" and not between "within" and "reach". At 1.35x in
        // the two-column layout it does wrap, and "Have a sturdy chair within / reach."
        // leaves the operative word alone on a line of its own.
        text = "Have a sturdy chair within reach.",
        style = MaterialTheme.typography.bodyLarge,
        color = SteadyColors.Linen,
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
    )
    SteadyButton(text = "Begin", onClick = onBegin, autoFocus = true)
    Text(
        text = "Nothing here is timed — press the remote when you are ready to move on.",
        style = MaterialTheme.typography.labelLarge,
        color = SteadyColors.LinenDim,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ExerciseStep(exercises: List<Exercise>, index: Int, textSize: TextSize, onAdvance: () -> Unit) {
    val exercise = exercises[index]
    var secondsElapsed by remember(index) { mutableIntStateOf(0) }
    LaunchedEffect(index) {
        secondsElapsed = 0
        while (secondsElapsed < exercise.durationSeconds) {
            delay(1000)
            secondsElapsed++
        }
    }

    SessionFrame(
        art = com.steady.app.ui.components.artworkFor(exercise.id),
        artDescription = "A photograph of people doing ${exercise.name.lowercase()}",
        // At the normal text size there is no strip label at all: "Movement 4 of 5"
        // sits under the button in the words column instead, because as a
        // bottom-anchored row it was 34dp of height competing with a words column that
        // grows whenever the movement's *name* wraps to two lines — and "Sit-to-stand,
        // hands on chair" does. The button was being drawn over the counter.
        //
        // At the accessibility sizes the tiles are dropped, and rather than leave that
        // band empty it carries the one thing the missing tiles were saying: what is
        // coming next. A sentence is not a hole.
        // At the accessibility sizes the band under the button carries both facts on one
        // line, and the counter is not also printed in the words column above it.
        //
        // Two separate lines of position — "Movement 4 of 5" in the column and "Next:
        // Seated side reach" in the band — did not fit at 1.35x on the one movement
        // whose name wraps to two lines. The band was pushed off the bottom of the
        // screen and "Next: Seated side reach" was sliced in half by the edge of the
        // panel. One line says both.
        stripLabel = if (textSize.dropsDecoration) {
            if (index == exercises.lastIndex) {
                "Movement ${index + 1} of ${exercises.size}. The last one today."
            } else {
                "Movement ${index + 1} of ${exercises.size}. Next: ${exercises[index + 1].name}."
            }
        } else {
            null
        },
        textSize = textSize,
        left = {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.headlineLarge,
                color = SteadyColors.Linen,
            )
            Text(
                text = exercise.cue,
                style = MaterialTheme.typography.bodyLarge,
                color = SteadyColors.LinenDim,
                modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
            )

            // The pace bar gives way at the largest text size, the same way the tile
            // strip already does and for the same reason: past about 40sp the layout has
            // to change rather than shrink.
            //
            // It is the right thing to lose. The bar is a suggestion and says so — the
            // button under it is focused and pressable from the first frame, and the
            // ready step has already said "nothing here is timed". The movement's name,
            // what to do, and the control that moves on are the content. With the bar and
            // its caption still on the screen at 1.35x, "Sit-to-stand, hands on chair" —
            // a two-line name over a three-line cue — pushed the button's *label* off the
            // bottom of the panel: a gold pill with nothing written in it, on the screen
            // of somebody who has chosen the largest text because they cannot read the
            // smaller ones.
            if (textSize != TextSize.LARGEST) {
                val progress = (secondsElapsed.toFloat() / exercise.durationSeconds.toFloat()).coerceIn(0f, 1f)
                // A pace suggestion, not a gate: the button below is focused and pressable
                // from the first frame. The bar is 420dp x 12dp rather than Material's phone
                // default of 240dp x 4dp, which at three metres is a hairline nobody sees,
                // and it is labelled in words underneath so nothing here is carried by a
                // coloured bar alone.
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.width(420.dp).height(12.dp),
                    color = SteadyColors.Moss,
                    trackColor = SteadyColors.BarkLift,
                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                )
                Text(
                    text = "Suggested pace. Take as long as you like.",
                    style = MaterialTheme.typography.labelLarge,
                    color = SteadyColors.LinenDim,
                    modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
                )
            } else {
                Spacer(Modifier.height(18.dp))
            }

            SteadyButton(
                text = if (index == exercises.lastIndex) "Done, finish session" else "Done, next exercise",
                onClick = onAdvance,
                autoFocus = true,
            )

            if (!textSize.dropsDecoration) {
                Text(
                    text = "Movement ${index + 1} of ${exercises.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = SteadyColors.LinenDim,
                    modifier = Modifier.padding(top = 8.dp, start = 6.dp),
                )
            }
        },
        strip = {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(end = 24.dp),
            ) {
                itemsIndexed(items = exercises, key = { _, item -> item.id }) { i, item ->
                    ExerciseTile(
                        exercise = item,
                        onSelect = null,
                        artWidth = STRIP_TILE,
                        state = when {
                            i < index -> TileState.DONE
                            i == index -> TileState.CURRENT
                            else -> TileState.UPCOMING
                        },
                    )
                }
            }
        },
    )
}

/** The only feedback capture in the app: three choices, no typing, shown once right
 * after the session ends. Picking one both records it and returns Home in a single
 * press, so getting the app's only adaptive signal costs the person nothing extra.
 *
 * The three answers stand exactly where the movements stood a moment ago, at the same
 * size and in the same strip, so the question arrives in a place the eye is already
 * looking rather than on a screen that has replaced the one being answered. */
@Composable
private fun FeedbackStep(textSize: TextSize, onFeedback: (SessionFeedback) -> Unit) {
    SessionFrame(
        art = R.drawable.photo_session_complete,
        artDescription = "An exercise class for older adults finishing with arms raised",
        stripLabel = "How did that feel?",
        textSize = textSize,
        stripIsDecoration = false,
        // Most of the spare height on this screen goes under the cards, so the question
        // sits close under the photograph of the class finishing rather than across a
        // band of empty ground from it.
        spaceBelowStrip = 1.6f,
        left = {
            Text(
                text = "That's today done.",
                style = MaterialTheme.typography.headlineLarge,
                color = SteadyColors.Linen,
            )
            Text(
                text = "Your answer sets how hard next week is. There is no wrong one.",
                style = MaterialTheme.typography.bodyLarge,
                color = SteadyColors.LinenDim,
                modifier = Modifier.padding(top = 10.dp),
            )
        },
        strip = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.focusGroup(),
            ) {
                FeelingTile(
                    art = R.drawable.art_feeling_too_easy,
                    label = "Too easy",
                    onSelect = { onFeedback(SessionFeedback.TOO_EASY) },
                    artWidth = STRIP_TILE + 40.dp,
                )
                FeelingTile(
                    art = R.drawable.art_feeling_just_right,
                    label = "Just right",
                    onSelect = { onFeedback(SessionFeedback.JUST_RIGHT) },
                    artWidth = STRIP_TILE + 40.dp,
                    autoFocus = true,
                )
                FeelingTile(
                    art = R.drawable.art_feeling_too_much,
                    label = "Too much",
                    onSelect = { onFeedback(SessionFeedback.TOO_MUCH) },
                    artWidth = STRIP_TILE + 40.dp,
                )
            }
        },
    )
}

private fun summaryLine(exercises: List<Exercise>): String {
    val count = exercises.size
    val minutes = ((exercises.sumOf { it.durationSeconds } + count * 20) / 60.0).let {
        if (it < 1.5) 1 else Math.round(it).toInt()
    }
    val word = when (count) {
        1 -> "One movement"
        2 -> "Two movements"
        3 -> "Three movements"
        4 -> "Four movements"
        5 -> "Five movements"
        6 -> "Six movements"
        7 -> "Seven movements"
        else -> "$count movements"
    }
    // "about 5 minutes." is held together with non-breaking spaces. Where the column is
    // too narrow for the whole sentence — the 1.35x layout, where it sits beside the
    // running order — the break then falls after the comma rather than between the
    // number and its unit.
    return "$word, about $minutes minute${if (minutes == 1) "" else "s"}."
}
