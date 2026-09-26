package com.steady.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.steady.app.R
import com.steady.app.data.TextSize
import com.steady.app.domain.PauseReason
import com.steady.app.ui.components.HeroBand
import com.steady.app.ui.components.STEADY_SAFE_X
import com.steady.app.ui.components.STEADY_SAFE_Y
import com.steady.app.ui.components.SteadyButton
import com.steady.app.ui.components.SteadyButtonTone
import com.steady.app.ui.components.SteadyPage
import com.steady.app.ui.theme.SteadyColors

/**
 * Two reasons, no typing, and a way out. The two are the only ones that matter for the
 * rule this screen exists to trigger: the paused weeks are excluded from adherence
 * entirely, so they cannot break the run of weeks and cannot step the routine back.
 *
 * Built in the same shape as every other screen rather than as a centred stack on bare
 * ground. It was the only screen in the app that was centre-aligned, and the only one
 * with no artwork on it at all — which on a television reads as a system dialog that
 * has interrupted the app, not as a page of it. A person reaches this screen in one
 * press from Home and should not feel they have left.
 */
@Composable
fun PauseScreen(
    textSize: TextSize,
    onConfirm: (PauseReason) -> Unit,
    onCancel: () -> Unit,
) {
    SteadyPage {
        HeroBand(
            photo = R.drawable.photo_home_hero,
            contentDescription = "An outdoor exercise class for older adults in a park",
            textSideWeight = if (textSize.dropsDecoration) 0.74f else 0.60f,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(312.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                // The measure is on the sentence, not on the column. Capping the column
                // capped the button row inside it too, and "I'm away" plus "I'm unwell"
                // need 526dp between them at the normal text size — so the second reason
                // rendered as "I'm u…", which is not a thing anybody can answer.
                .padding(start = STEADY_SAFE_X, top = STEADY_SAFE_Y + 8.dp, end = STEADY_SAFE_X),
        ) {
            Text(
                text = "Taking a break?",
                style = MaterialTheme.typography.headlineLarge,
                color = SteadyColors.Linen,
            )
            // Word for word the promise Home makes while the pause is running, opening
            // on the same clause. Before this the two screens described the same
            // guarantee in two different sentences — one of them using "streak", a word
            // the notice wording deliberately avoids — and somebody reading both had to
            // work out whether the difference meant anything.
            Text(
                text = "The programme is held exactly where it is. Your weeks in a row are " +
                    "kept, and the routine does not step back while you are away.",
                style = MaterialTheme.typography.bodyMedium,
                color = SteadyColors.LinenDim,
                modifier = Modifier
                    .widthIn(max = if (textSize.dropsDecoration) 640.dp else 470.dp)
                    .padding(top = 10.dp, bottom = 22.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SteadyButton(
                    text = "I'm away",
                    onClick = { onConfirm(PauseReason.AWAY) },
                    autoFocus = true,
                )
                SteadyButton(
                    text = "I'm unwell",
                    onClick = { onConfirm(PauseReason.UNWELL) },
                )
            }
            SteadyButton(
                text = "Never mind",
                onClick = onCancel,
                tone = SteadyButtonTone.SECONDARY,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
