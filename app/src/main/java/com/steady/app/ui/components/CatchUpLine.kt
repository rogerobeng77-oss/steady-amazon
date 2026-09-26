package com.steady.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.steady.app.domain.CatchUpNotice
import com.steady.app.ui.theme.SteadyColors

/**
 * Two sentences above the button that was always there.
 *
 * This is the whole of the "reminder", and its restraint is the design rather than an
 * omission. A person in their seventies exercising alone stops using an app that makes
 * them feel watched, and an app they have stopped using prevents no falls — so the line
 * states one fact about the record, offers the same four minutes it always offers, and
 * gets out of the way.
 *
 * Deliberately not done, each considered and rejected:
 *
 * - No dialog. A modal would take the remote hostage and demand an answer to a question
 *   the person did not ask.
 * - No red, no warning colour, no icon. It is styled in Ember, the same accent the
 *   adjustment line uses, because a gap in a diary is not an alert.
 * - It does not take focus. `Start Today's Session` stays auto-focused, so the first
 *   press of the remote still starts a session exactly as it did before this existed.
 * - It does not change the button's label. "Start Today's Session" is what the person
 *   has learned; relabelling it "Catch up" on the days they were away would make the one
 *   thing on this screen inconsistent at exactly the wrong moment.
 *
 * Both sentences are merged into one semantics node so VoiceView reads the fact and the
 * offer as one utterance instead of stopping between them.
 */
@Composable
fun CatchUpLine(
    notice: CatchUpNotice,
    modifier: Modifier = Modifier,
    /**
     * Whether the second line, the offer, is drawn.
     *
     * False at the larger text sizes, where two lines become four and the column runs
     * into the tile row. The fact is the line that cannot be cut; the offer is already
     * made by the button sitting directly above it, which says "Start Today's Session"
     * in the largest type on the screen.
     *
     * VoiceView still reads both either way — the screen reader is not short of room,
     * and somebody who needs 28sp type is exactly the person most likely to be listening
     * rather than reading.
     */
    showOffer: Boolean = true,
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "${notice.headline} ${notice.subline}"
        },
    ) {
        Text(
            text = notice.headline,
            style = MaterialTheme.typography.bodyLarge,
            color = SteadyColors.Ember,
        )
        if (showOffer) {
            Text(
                text = notice.subline,
                style = MaterialTheme.typography.bodyMedium,
                color = SteadyColors.LinenDim,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
