package com.steady.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.steady.app.R
import com.steady.app.domain.Exercise
import com.steady.app.ui.theme.SteadyColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Every exercise is a photograph of somebody actually doing it.
 *
 * Not a diagram, and not a drawing. The person using Steady is in their seventies or
 * eighties, alone in a living room, trying to copy a movement off a television three
 * metres away — and a photograph of a real body is easier to copy than a pictogram,
 * because the thing that makes a sit-to-stand safe is where the weight is, which a line
 * drawing cannot show. Here the picture is the instruction, not the decoration.
 *
 * They are also photographs of people this app is *for*. Every one shows adults in their
 * sixties, seventies and eighties, in ordinary clothes, in a park or a public hall. A
 * twenty-five-year-old athlete demonstrating a calf raise quietly tells this viewer that
 * the app is aimed at somebody else.
 *
 * Provenance is in ATTRIBUTION.md, generated from what Wikimedia Commons returned rather
 * than from a claim on a landing page, and the fetch step drops anything whose licence
 * forbids commercial use or derivative works before downloading it.
 */
@DrawableRes
fun artworkFor(exerciseId: String): Int = when (exerciseId) {
    "cs_march" -> R.drawable.photo_cs_march
    "cs_ankle" -> R.drawable.photo_cs_ankle
    "cs_arm_raise" -> R.drawable.photo_cs_arm_raise
    "cs_sit_to_stand" -> R.drawable.photo_cs_sit_to_stand
    "cs_side_bend" -> R.drawable.photo_cs_side_bend
    "st_heel_toe" -> R.drawable.photo_st_heel_toe
    "st_single_leg" -> R.drawable.photo_st_single_leg
    "st_march" -> R.drawable.photo_st_march
    "st_side_step" -> R.drawable.photo_st_side_step
    "st_calf_raise" -> R.drawable.photo_st_calf_raise
    // An id with no photograph of its own opens on the whole class rather than on an
    // empty tile.
    else -> R.drawable.photo_session_ready
}

/**
 * The band of photograph across the top of a screen.
 *
 * Full width, hard against the top and both side edges, with no card, no border and no
 * panel. Its lower edge fades into the page and the row of tiles sits on that fade, so
 * there is no line anywhere saying where the picture stops — which is the third thing
 * the reference photographs do that our screens did not (a set of reference photographs of a real Fire TV).
 *
 * The left of the image darkens toward the ground colour so that the words laid over it
 * have something to sit on. That gradient is a reading aid, not a mood: it is opaque
 * where the text is and gone by the time it reaches the subject.
 */
@Composable
fun HeroBand(
    @DrawableRes photo: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    /** Where the caller's text column ends, as a fraction of the band's width. The
     * scrim is solid to here and the photograph emerges over the 0.20 after it. Getting
     * this smaller than the real text column is what makes line-ends disappear. */
    textSideWeight: Float = 0.52f,
    /** The band's height as a fraction of the page's, so its lower edge can fade into
     * the exact colour the page wash is at that depth rather than into a flat Bark that
     * is only correct at 44% down. A short band faded to Bark drew a horizontal line
     * across the screen, which is the one thing the wash exists to prevent. */
    heightFraction: Float = 0.58f,
) {
    val fadeTo = steadyGroundAt(heightFraction)
    Box(modifier = modifier) {
        Image(
            painter = painterResource(photo),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            // Faces and raised arms live in the upper half of these frames, so a centred
            // crop of a wide band takes the legs and leaves the heads outside it.
            alignment = BiasAlignment(0f, -0.25f),
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    // The scrim holds its strength all the way to [textSideWeight] and
                    // only then falls away, rather than arriving at fully transparent
                    // exactly there.
                    //
                    // It used to reach zero at [textSideWeight], which meant any text
                    // column ending even slightly past that point had the *ends of its
                    // lines* drawn straight onto the photograph. On the session screen
                    // that cost the last two words of "Have a sturdy chair within
                    // reach." and of "Push up to standing using the chair arms, then sit
                    // back down slowly." — a safety instruction and a movement
                    // instruction, in a falls-prevention programme, read at three metres.
                    // Callers now set [textSideWeight] to where their text actually ends
                    // and the photograph starts emerging after that, not before.
                    Brush.horizontalGradient(
                        0f to fadeTo.copy(alpha = 0.97f),
                        textSideWeight * 0.55f to fadeTo.copy(alpha = 0.90f),
                        textSideWeight to fadeTo.copy(alpha = 0.82f),
                        (textSideWeight + 0.20f).coerceAtMost(1f) to Color.Transparent,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    // The top of the band is darkened too, because the quiet controls in
                    // the corner sit on it and a park photograph has bright sky exactly
                    // where they land.
                    Brush.verticalGradient(
                        0f to steadyGroundAt(0f).copy(alpha = 0.78f),
                        0.26f to Color.Transparent,
                        0.70f to Color.Transparent,
                        1f to fadeTo,
                    ),
                ),
        )
    }
}

private val TILE_RADIUS = RoundedCornerShape(14.dp)

/**
 * One exercise, as a tile: a 16:9 photograph reaching all four edges, with its name in
 * the bottom of the frame rather than in a caption strip under it.
 *
 * Unfocused, the name sits on a gradient that runs from nothing to the ground colour, so
 * the photograph is never dimmed as a whole — only the strip the words need is darkened.
 * Focused, that strip becomes a solid ember bar with dark text, the tile grows, and the
 * slight warm veil over the photograph lifts.
 *
 * There is no outline in either state. A real Fire TV does not stroke the item the
 * remote is pointing at; it fills it (a set of reference photographs of a real Fire TV, read off
 * shots 05 to 07). An outline was the single clearest tell that our screens were a
 * mock-up, and it is the one thing this component must never grow back.
 */
@Composable
fun ExerciseTile(
    exercise: Exercise,
    onSelect: (() -> Unit)?,
    modifier: Modifier = Modifier,
    artWidth: Dp = 264.dp,
    autoFocus: Boolean = false,
    state: TileState = TileState.UPCOMING,
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var requested by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val spoken = buildString {
        append(exercise.name)
        when (state) {
            TileState.DONE -> append(", done")
            TileState.CURRENT -> append(", doing this one now")
            TileState.UPCOMING -> Unit
        }
        if (onSelect != null) append(". Press to start here.")
    }

    PhotoTile(
        photo = artworkFor(exercise.id),
        label = exercise.name,
        spoken = spoken,
        focused = focused,
        highlighted = state == TileState.CURRENT,
        receded = state == TileState.DONE,
        artWidth = artWidth,
        modifier = modifier
            .let { if (onSelect != null) it.focusRequester(focusRequester) else it }
            .onGloballyPositioned {
                if (autoFocus && onSelect != null && !requested) {
                    requested = true
                    scope.launch {
                        delay(48)
                        runCatching { focusRequester.requestFocus() }
                    }
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .let {
                if (onSelect == null) {
                    it
                } else {
                    it.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSelect,
                    )
                }
            },
    )
}

/** Where an exercise sits in today's run, so the strip reads as progress and not as a menu. */
enum class TileState { DONE, CURRENT, UPCOMING }

@Composable
private fun PhotoTile(
    @DrawableRes photo: Int,
    label: String,
    spoken: String,
    focused: Boolean,
    highlighted: Boolean,
    receded: Boolean,
    artWidth: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .steadyFocusScale(focused)
            .width(artWidth)
            .height(artWidth * 9 / 16)
            .clip(TILE_RADIUS)
            .background(SteadyColors.BarkDeep)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
    ) {
        Image(
            painter = painterResource(photo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // A warm veil, not a dimmer. It seats a green-and-blue park photograph in a bark
        // and ember interface, and it lifts entirely when the remote points here, which
        // is the luminance channel doing its share of the focus work.
        val veil = when {
            focused || highlighted -> 0f
            receded -> 0.55f
            else -> 0.26f
        }
        if (veil > 0f) {
            Box(Modifier.matchParentSize().background(SteadyColors.Bark.copy(alpha = veil)))
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                // heightIn, not height. A fixed 46% of the tile height fitted exactly
                // one line of labelLarge at 1.0x, so every two-line name ellipsised on
                // line one and the session strip read "Seated ma…", "Ankle pum…",
                // "Sit-to-stan…". At the 1.35x text size it got as far as "Seat…". The
                // band now grows to whatever the name needs and the photograph above it
                // gives up the room, which is the right trade: the label is the part
                // that has to be read.
                .heightIn(min = artWidth * 9 / 16 * 0.46f)
                .background(
                    if (focused) {
                        Brush.verticalGradient(
                            listOf(SteadyColors.EmberFocused, SteadyColors.EmberFocused),
                        )
                    } else {
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.35f to SteadyColors.BarkDeep.copy(alpha = 0.72f),
                            1f to SteadyColors.BarkDeep.copy(alpha = 0.95f),
                        )
                    },
                ),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) SteadyColors.Bark else SteadyColors.Linen,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // 12dp, not 14dp. The label step went from 20sp to 26sp with the rest of
                // the scale and "Sit-to-stand, hands on chair" started arriving as
                // "Sit-to-stand, hands on ch…" — which is the trade the tile width was
                // widened to 200dp to avoid in the first place, since "hands on chair" is
                // the support instruction and not a subtitle. Four dp of padding is worth
                // four characters here and is worth nothing anywhere else.
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * The three answers to "how did that feel", built to the same tile as the exercises.
 *
 * These stay drawings on purpose, and they are the only drawings left in the app. They
 * are not pictures of anything — they are a gauge: a line at the level today's session
 * was pitched at, and a bar showing where it landed, short of the line, level with it, or
 * well past it. No photograph can say "that was harder than it should have been".
 */
@Composable
fun FeelingTile(
    @DrawableRes art: Int,
    label: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    artWidth: Dp = 264.dp,
    autoFocus: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var requested by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .onGloballyPositioned {
                if (autoFocus && !requested) {
                    requested = true
                    scope.launch {
                        delay(48)
                        runCatching { focusRequester.requestFocus() }
                    }
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .steadyFocusScale(focused)
            .width(artWidth)
            .height(artWidth * 9 / 16)
            .clip(TILE_RADIUS)
            .background(if (focused) SteadyColors.EmberFocused else SteadyColors.BarkLift)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect,
            )
            .semantics(mergeDescendants = true) { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(art),
            contentDescription = null,
            colorFilter = if (focused) {
                androidx.compose.ui.graphics.ColorFilter.tint(SteadyColors.Bark)
            } else {
                null
            },
            modifier = Modifier.size(artWidth * 0.34f).padding(bottom = 26.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (focused) SteadyColors.Bark else SteadyColors.Linen,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        )
    }
}
