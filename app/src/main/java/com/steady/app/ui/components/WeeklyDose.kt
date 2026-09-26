package com.steady.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.steady.app.ui.theme.SteadyColors

/**
 * How much of this week's dose is done, for the person themselves rather than only for
 * the family member's card.
 *
 * The dots are 20dp, an empty one is a 3dp linen ring rather than a dark fill, and the
 * count is written out in words beside them, since the shared Fire TV craft reference is explicit
 * that nothing may be carried by colour or shape alone.
 */
@Composable
fun WeeklyDose(
    tierLabel: String,
    spokenLabel: String,
    sessionsThisWeek: Int,
    prescribedPerWeek: Int,
    showDots: Boolean,
    modifier: Modifier = Modifier,
) {
    val description = "$spokenLabel \u00b7 $sessionsThisWeek of $prescribedPerWeek sessions done this week"
    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = description }
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(
            text = tierLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = SteadyColors.Linen,
        )
        // The dots sit at the end of the count, not in a column of their own to the left
        // of it.
        //
        // Beside the text they indented this whole block 96dp while the wordmark, the
        // button and the tile row all began at the safe margin, so the one screen the
        // person opens every day had a notch bitten out of its left edge \u2014 and the notch
        // appeared and disappeared with the text size, because the dots are dropped at
        // the accessibility steps. Next to the number, they also illustrate the number
        // rather than heading the block.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$sessionsThisWeek of $prescribedPerWeek sessions done this week",
                style = MaterialTheme.typography.labelLarge,
                color = SteadyColors.LinenDim,
            )
            if (showDots) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(prescribedPerWeek) { i ->
                        val filled = i < sessionsThisWeek
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(
                                    color = if (filled) SteadyColors.Moss else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .border(
                                    border = BorderStroke(
                                        3.dp,
                                        if (filled) SteadyColors.Moss else SteadyColors.LinenDim,
                                    ),
                                    shape = CircleShape,
                                ),
                        )
                    }
                }
            }
        }
    }
}
