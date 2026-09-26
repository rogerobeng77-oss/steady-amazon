package com.steady.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.steady.app.data.TextSize
import com.steady.app.ui.components.STEADY_SAFE_X
import com.steady.app.ui.components.STEADY_SAFE_Y
import com.steady.app.ui.components.SteadyPage
import com.steady.app.ui.components.fadingTopEdge
import com.steady.app.ui.components.steadyGroundAt
import com.steady.app.ui.components.SteadyButton
import com.steady.app.ui.components.SteadyButtonTone
import com.steady.app.ui.components.SteadyTextField
import com.steady.app.ui.theme.SteadyColors

/**
 * How far the last control stops short of the bottom of the screen: clear of the 84dp
 * fade, and clear of the 5% overscan internal research notes on Fire TV / Fire OS platform facts says a television is
 * entitled to crop.
 */
private val SCROLL_BOTTOM_INSET = 96.dp

/** The setup screen only ever runs once, so it is also the only place a wrong name gets
 * caught: a mistyped name, a nickname the family member does not use, or simply
 * deciding the update should go to someone else. This screen exists because that is a
 * real day-two need, not a hypothetical one -- it is exactly the situation setup itself
 * is built to avoid the person doing the exercises ever having to fix. */
@OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)
@Composable
fun SettingsScreen(
    currentFamilyMemberName: String,
    totalSessionsCompleted: Int,
    textSize: TextSize,
    lastSummarySentAt: String?,
    lastSummaryText: String?,
    /** The day the scheduled check last ran, or null if it has not yet. */
    lastCheckedOnLabel: String?,
    onTextSizeChange: (TextSize) -> Unit,
    onSendSummary: () -> Unit,
    onSave: (String) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember(currentFamilyMemberName) { mutableStateOf(currentFamilyMemberName) }
    val scrollState = rememberScrollState()

    SteadyPage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // No bottom padding, deliberately, and it is the fix for the defect this
                // screen was named for.
                //
                // With `vertical = 32.dp` the scrolling region stopped 32dp short of the
                // screen and its last line was cut square by that edge — while the fade
                // meant to soften the cut was drawn at the bottom of the *page*, 32dp
                // lower down, over the margin rather than over the content. So the
                // caregiver card was sliced through the middle of its second line with
                // nothing to say why, which is exactly what a screen looks like when it
                // has failed to render. The region now runs to the bottom edge, the fade
                // sits over where the cutting happens, and the content keeps its distance
                // from the edge through [SCROLL_BOTTOM_INSET] instead — which is inset
                // the scroll can travel through, not a margin it stops at.
                .padding(start = STEADY_SAFE_X, end = STEADY_SAFE_X, top = STEADY_SAFE_Y),
        ) {
            // The heading and the two facts under it stay put; only the settings
            // themselves scroll.
            //
            // With the whole screen inside one scroller, the name field claims focus on
            // arrival, Compose scrolls to bring it into view, and the first thing a
            // person saw on opening Settings was the bottom half of the word "Settings"
            // with its top sliced off. A title that can scroll away is a title that will.
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                color = SteadyColors.Linen,
            )
            Text(
                text = "$totalSessionsCompleted session${if (totalSessionsCompleted == 1) "" else "s"} completed in total.",
                style = MaterialTheme.typography.bodyMedium,
                color = SteadyColors.LinenDim,
                modifier = Modifier.padding(top = 8.dp),
            )
            // Steady checks once a day whether a stretch has gone by, whether or not
            // anybody opened it. Saying so here, with the date, is the difference
            // between a claim in a README and something a person can read off their own
            // television — and it is also the only place the app admits it is watching
            // at all, which somebody being watched is entitled to see.
            Text(
                text = lastCheckedOnLabel
                    ?.let { "Steady last checked on $it." }
                    ?: "Steady checks once a day. It has not run yet.",
                style = MaterialTheme.typography.labelLarge,
                color = SteadyColors.LinenDim,
                modifier = Modifier.padding(top = 4.dp, bottom = 22.dp),
            )

            // The settings themselves scroll, because at the 1.35x text size they run
            // off the bottom and take the text-size control with them: "Text size" was
            // the last thing visible and its three buttons, Save and Back were all below
            // the fold. The one setting that exists because Fire TV has no system
            // text-size control was, at its own largest step, unreachable — so a person
            // could set Largest and then not set it back.
            //
            // The scroll modifier is on a node with no Arrangement.Center:
            // FRICTION.md row 9 is about what happens when those two meet, which
            // is that content is laid out outside the visible screen and silently
            // disappears.
            Column(
                modifier = Modifier
                    .weight(1f)
                    // The settings dissolve as they pass under the fixed header rather
                    // than being chopped through the middle of a letter by its lower
                    // edge. Scrolled down to "Send now", the text-size row was sliced in
                    // half horizontally and read as a rendering fault.
                    .fadingTopEdge(active = scrollState.value > 0)
                    .verticalScroll(scrollState)
                    // Inside the scroller, so it is distance the content can travel
                    // through rather than a margin the viewport stops at. Scrolled to the
                    // end, Save and Back come to rest clear of both the fade and the 5%
                    // the television may not show at all.
                    .padding(bottom = SCROLL_BOTTOM_INSET),
            ) {
            // Fire TV has no system text-size setting and this app's audience is the one
            // that most needs one, so Steady ships its own: two presses from Home, three
            // steps rather than a slider (a slider on a remote is a held direction and a
            // guess), persisted, and applied to the whole scale at once. At the larger
            // steps Home sheds its wordmark and its dose dots instead of shrinking them,
            // because past about 32sp the layout has to change rather than scale.
            Text(
                text = "Text size",
                style = MaterialTheme.typography.bodyMedium,
                color = SteadyColors.LinenDim,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextSize.entries.forEach { size ->
                    SteadyButton(
                        text = if (size == textSize) "${size.label} \u00b7 on" else size.label,
                        onClick = { onTextSizeChange(size) },
                        tone = if (size == textSize) SteadyButtonTone.PRIMARY else SteadyButtonTone.SECONDARY,
                        // The screen opens with the remote pointing at the size already
                        // in use. It has to point somewhere — a frame with nothing
                        // focused is a frame where VoiceView says nothing and the D-pad
                        // does nothing — and this is the only control here that does
                        // nothing at all if pressed by accident. It is also at the top,
                        // so focus does not scroll the screen before the person has
                        // read it, which is what auto-focusing the name field did.
                        autoFocus = size == textSize,
                    )
                }
            }


            Text(
                text = "Family member who gets the weekly update",
                style = MaterialTheme.typography.bodyMedium,
                color = SteadyColors.LinenDim,
                modifier = Modifier.padding(top = 28.dp, bottom = 10.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SteadyTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Family member's first name",
                    placeholder = "Press to type",
                    modifier = Modifier.width(420.dp),
                )
                // Sending lives here rather than on Home, beside the name it is
                // addressed to and beside the words it will contain.
                SteadyButton(
                    text = "Send now",
                    onClick = onSendSummary,
                    tone = SteadyButtonTone.SECONDARY,
                )
            }
            // The person this update is about can read what was said about them. Writing
            // a description of somebody's health behaviour, marking it sent to their
            // daughter, and giving them no way to see it is the kind of thing that is
            // only defensible until somebody asks.
            // What was actually sent, set apart on the raised ground rather than left as
            // one more dim line in a column of labels. This is the paragraph somebody's
            // daughter received about their week, and on a screen of settings it was
            // reading as a caption for the field above it. No maxLines: showing a person
            // two lines of the message that was sent about them, then an ellipsis, is
            // showing them a summary of their own summary.
            Surface(
                color = SteadyColors.BarkRaised,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(top = 14.dp).widthIn(max = 720.dp),
            ) {
                Text(
                    text = lastSummaryText ?: if (lastSummarySentAt == null) {
                        "Nothing has been sent yet. Press Send now to send this week's update."
                    } else {
                        "Sent $lastSummarySentAt."
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (lastSummaryText == null) SteadyColors.LinenDim else SteadyColors.Linen,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }

            Row(
                modifier = Modifier.padding(top = 26.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SteadyButton(
                    text = "Save",
                    onClick = { onSave(name) },
                )
                SteadyButton(
                    text = "Back",
                    onClick = onBack,
                    tone = SteadyButtonTone.SECONDARY,
                )
            }
            }
        }

        // The settings run past the bottom of the panel at every text size, so the
        // bottom of the panel dissolves into the ground rather than slicing a button in
        // half against a hard edge. A cut that looks like a fade reads as "there is more
        // here"; a cut that looks like a cut reads as a broken screen.
        //
        // Tall enough to reach well above the 5% a television may crop, so that whatever
        // is being cut is already gone before the cut happens. At 44dp it covered the
        // margin under the scrolling region and none of the region itself.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(84.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to steadyGroundAt(1f),
                    ),
                ),
        )
    }
}
