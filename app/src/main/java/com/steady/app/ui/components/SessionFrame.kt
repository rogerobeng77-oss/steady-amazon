package com.steady.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.steady.app.data.TextSize
import com.steady.app.ui.theme.SteadyColors

/**
 * The shape all three steps share.
 *
 * Artwork sits top-right and runs off the right edge of the panel with no border and no
 * card behind it — the hero bleeds into the strip beneath rather than sitting in a box
 * above it (a set of reference photographs of a real Fire TV). Words are in a single left column
 * capped at the 470dp measure this app uses for body text, roughly forty characters at
 * 28sp. The strip along the bottom is the same geometry in every step, which is what
 * makes the three read as one thing.
 */
@Composable
fun SessionFrame(
    @DrawableRes art: Int,
    artDescription: String,
    /** Null on the movement step, where the counter lives in the words column instead —
     * see [ExerciseStep]. */
    stripLabel: String?,
    textSize: TextSize,
    /**
     * Whether the strip may be dropped at the larger text sizes.
     *
     * True for the previews of upcoming movements, which repeat what the artwork above
     * already shows. **False on the feedback step**, where the strip is the three
     * feeling buttons and dropping it would leave the person no way to answer the only
     * question the app ever asks them.
     */
    stripIsDecoration: Boolean = true,
    /**
     * How much of the screen's leftover height goes *below* the strip rather than above
     * it. Zero — the default — pins the strip to the bottom, which is right for the
     * movement steps, where the strip is a row of tiles running off the edge and the
     * words above it are long.
     *
     * The feedback step is the one that needs otherwise. Its words are three lines and
     * its strip is three cards, so at the normal text size there is about 100dp spare,
     * and pinned to the bottom that 100dp opened as a band of empty ground between the
     * foot of the photograph and the cards. Space *below* content reads as a screen that
     * has finished; the same space *between* two things reads as something that failed to
     * arrive. It is expressed as a weight rather than a fixed lift so that at the
     * accessibility sizes, where there is no spare height at all, it silently does
     * nothing instead of pushing the answer cards off the bottom.
     */
    spaceBelowStrip: Float = 0f,
    left: @Composable ColumnScope.() -> Unit,
    strip: @Composable () -> Unit,
) {
    SteadyPage {
        HeroBand(
            photo = art,
            contentDescription = artDescription,
            // Matched to the words column below, which is wider at the accessibility
            // sizes. The default 0.52 left the last word or two of every wrapped line
            // drawn straight onto the photograph. Both numbers are (start + measure) as a
            // fraction of 960dp, rounded up, so they move whenever the measure does.
            textSideWeight = if (textSize.dropsDecoration) 0.74f else 0.63f,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(312.dp),
        )

        // One column, top to bottom, with the give in the middle — not two children of a
        // Box anchored to opposite edges.
        //
        // Anchored, the words grew down and the strip grew up and at 1.35x they met:
        // "Done, next exercise" was drawn *underneath* the tiles with a few letters
        // showing between them, and "Suggested pace. Take as long as you like." landed
        // on top of "Movement 1 of 5". Each was patched by hand with a size-dependent
        // width or a moved caption, which is a fix per collision rather than a fix.
        //
        // As a column with a weighted spacer between the two, the gap is whatever is
        // left over and overlap is not a state this screen can reach. There is no end
        // padding, so the strip still runs off the right edge the way a Fire TV row
        // should.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = STEADY_SAFE_X,
                    top = STEADY_SAFE_Y + 8.dp,
                    // The full safe inset, not 8dp less than it. At 1.35x the feedback
                    // step's three answer cards reach the bottom of this column, so the
                    // padding is the only thing between the focused card and the edge of
                    // the panel — and at 24dp it was under the 30dp vertical floor that
                    // 5% of 540dp gives (internal research notes on Fire TV / Fire OS platform facts). Amazon's overscan
                    // rule singles out the focused item as the thing that must be fully
                    // inside the inner 90%, and the focused item was the one hanging over
                    // the line.
                    bottom = STEADY_SAFE_Y,
                ),
        ) {
            Column(
                modifier = Modifier
                    // A measure is a count of characters, not a width, so it has to move
                    // when the type does. 470dp was about forty characters when body text
                    // was 28sp; at 30sp it is thirty-five, and the three lines this column
                    // holds all gained a line — "Five movements, about 5 minutes." broke
                    // after the comma, and the note under Begin went to three. The column
                    // then overflowed the page and the only thing under it that could give
                    // was the strip, which was squeezed from 112dp to 81dp and ellipsised
                    // every movement name in the running order to one line.
                    //
                    // 540dp restores the forty characters. It ends at 596dp of a 960dp
                    // panel, which is why the scrim above moved with it.
                    //
                    // Wider still at the accessibility sizes: "Done, next exercise" grows
                    // with the type and was ellipsising to "Done, next exerc…" inside a
                    // 470dp column — the primary control of the screen, unreadable, at the
                    // text size this app's audience uses.
                    .widthIn(max = if (textSize.dropsDecoration) 640.dp else 540.dp),
                content = left,
            )

            Spacer(Modifier.weight(1f))

            // At the larger text sizes a strip that is only a preview gives way, for the
            // same reason Home sheds its wordmark and its dose dots: it repeats movements
            // the big artwork above is already showing one of. A strip that is the only
            // copy of something — the three feeling buttons, the running order on the
            // ready step — passes `stripIsDecoration = false` and always stays.
            if (stripLabel != null) {
                Text(
                    text = stripLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = SteadyColors.LinenDim,
                    // Flush with the heading above it and with the first tile below it.
                    // It used to carry 6dp of start padding, which put the one label on
                    // the screen 6dp to the right of every other left edge — a jog in the
                    // margin that is small enough to look like a mistake and large enough
                    // to see, and the only thing separating "How did that feel?" from the
                    // sentence over it was that margin.
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            if (!(stripIsDecoration && textSize.dropsDecoration)) strip()

            if (spaceBelowStrip > 0f) Spacer(Modifier.weight(spaceBelowStrip))
        }
    }
}

/**
 * Wider than the 176dp that let five tiles sit inside the panel exactly.
 *
 * At 176dp the label band had 148dp to work with and "Sit-to-stand, hands on chair" and
 * "Seated side reach" both ellipsised. Those names are not decoration: "hands on chair"
 * and "hand on chair" are the support instruction, in a falls-prevention programme, so
 * shortening the name to make it fit would be trading a safety qualifier for a layout.
 *
 * The row runs off the right edge instead, which is what Home already does and what a
 * Fire TV row is supposed to do — it is also the honest signal that there is more to the
 * right, where a row ending flush with the safe area reads as a complete list.
 */
