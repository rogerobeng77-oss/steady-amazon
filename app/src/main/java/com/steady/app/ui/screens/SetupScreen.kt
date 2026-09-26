package com.steady.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.steady.app.R
import com.steady.app.data.TextSize
import com.steady.app.ui.components.HeroBand
import com.steady.app.ui.components.SteadyPage
import com.steady.app.ui.components.SteadyButton
import com.steady.app.ui.components.SteadyButtonTone
import com.steady.app.ui.components.SteadyTextField
import com.steady.app.ui.theme.SteadyColors

/** Shown exactly once. The spec is explicit about who is meant to be sitting through
 * this screen: not the person who will do the exercises, but the family member setting
 * the television up for them. Every technology-delivered falls-prevention trial in the
 * research had to hand someone onto the platform; this screen is that hand-holding,
 * compressed to one field, done once, by someone else.
 *
 * Laid out left/right rather than stacked top/bottom: a ten-foot landscape screen is
 * wide and short, and a tall centred column is the layout most likely to push its own
 * buttons off the bottom of a real TV panel. The two halves size to their own content
 * and the pair is centred as a unit, rather than each claiming a fixed 50% of a 1920px
 * panel: a fixed split looks fine on a phone rotated sideways but leaves a canyon of
 * dead space on an actual television. */
@OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)
@Composable
fun SetupScreen(
    textSize: TextSize,
    onComplete: (familyMemberName: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val wideText = textSize.dropsDecoration

    SteadyPage {
        // The full 312dp every other screen uses, not 252dp.
        //
        // At 252dp the band stopped exactly where the content began and the screen read
        // as a photograph on top of a form, with a horizon across the middle — the one
        // thing the reference photographs never do and the one thing the page wash
        // exists to prevent. The heading now sits on the band where it does everywhere
        // else in the app, and the columns below start inside its lower fade.
        HeroBand(
            photo = R.drawable.photo_session_ready,
            contentDescription = "An exercise class for older adults, warming up together",
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(312.dp),
            textSideWeight = 0.62f,
        )
        Text(
            text = "Welcome to Steady",
            style = MaterialTheme.typography.headlineLarge,
            color = SteadyColors.Linen,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 56.dp, top = 40.dp)
                // Enough for the name to stay on one line at 1.35x, where a 440dp
                // column broke it into "Welcome to / Steady".
                .widthIn(max = 560.dp),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 56.dp, end = 56.dp, bottom = 40.dp),
            verticalAlignment = Alignment.Top,
            // The gutter closes at the accessibility sizes, and the words column gives
            // up width rather than the controls. Both grow with the type and only 848dp
            // of a 960dp canvas is inside the safe area; a words column that kept its
            // measure at 1.35x squeezed the column beside it until the field read
            // "Press to" and the primary button read "Conti…".
            horizontalArrangement = Arrangement.spacedBy(if (wideText) 48.dp else 72.dp),
        ) {
            Column(modifier = Modifier.widthIn(max = if (wideText) 380.dp else 470.dp)) {
                Text(
                    text = "A one-time step, usually done by a family member setting the TV up.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SteadyColors.LinenDim,
                )
                Text(
                    text = "Who should get the weekly update? A first name is enough.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SteadyColors.LinenDim,
                    // A paragraph break, not leading. At 8dp these two ran together as
                    // one block and the second question — the one the field below is
                    // actually asking — read as the tail of the first.
                    modifier = Modifier.padding(top = 18.dp),
                )
            }

            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SteadyTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Family member's first name",
                    placeholder = "Press to type",
                    // The first screen of the app sets its own focus, on the one thing
                    // that has to happen here. Before this, nothing was focused at all
                    // on a cold launch and the first D-pad press fell into a text field
                    // that would not let go of it.
                    autoFocus = true,
                    modifier = Modifier.widthIn(min = 400.dp, max = 460.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SteadyButton(
                        text = "Continue",
                        onClick = { onComplete(name) },
                        autoFocus = false,
                    )
                    SteadyButton(
                        text = "Skip",
                        onClick = { onComplete("") },
                        tone = SteadyButtonTone.SECONDARY,
                    )
                }
            }
        }
    }
}
