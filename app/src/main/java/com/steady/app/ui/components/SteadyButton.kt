package com.steady.app.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.steady.app.ui.theme.SteadyColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How loud a control is when nothing is focused. Focus looks identical in all three;
 * only the resting state differs. */
enum class SteadyButtonTone {
    /** The one thing on the screen. A filled pill in raised bark, so it is a solid shape
     * at rest rather than an outline, and ember the moment it has focus. At most one per
     * screen. */
    PRIMARY,

    /** A real but secondary action: a plain word in linen, with no container until it is
     * focused. This is what the reference photographs show for every unselected item. */
    SECONDARY,

    /**
     * Present but not competing. A dimmer plain word, for the things a second-day user
     * needs and a first-day user should not have to see past.
     *
     * A quiet control still fills with ember and grows the moment it has focus. The rule
     * this respects is "one signature element, everything around it quiet", not "make it
     * hard to find".
     */
    QUIET,
}

/**
 * The only button in this app.
 *
 * Focus is a fill, never an outline. See [steadyFocusScale] for why, and
 * a set of reference photographs of a real Fire TV for the photographs it was read off: on a real
 * Fire TV the selected item is a solid pill with dark text and everything unselected has
 * no container at all. Ember appears on exactly one control at a time — whatever the
 * remote is pointing at — so gold on this screen never means anything except "here".
 */
@Composable
fun SteadyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    tone: SteadyButtonTone = SteadyButtonTone.PRIMARY,
    /**
     * Change this to make an [autoFocus] button claim focus again.
     *
     * It exists for one real case. A control can disappear from a screen while the D-pad
     * is pointing at it — "Not today" does exactly that, because pressing it removes the
     * notice it belongs to — and Compose then hands focus to whatever it finds nearby.
     * On Home that was "Pause" in the corner, so the very next press of the remote would
     * have paused somebody's programme when all they did was dismiss a line of text.
     * Passing the notice's presence as this key sends focus back to the button that
     * matters the moment the notice goes.
     *
     * Default [Unit], so every existing caller keeps the original behaviour: request
     * once, on first layout, and never again.
     */
    autoFocusKey: Any? = Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember(autoFocusKey) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val container = when {
        isFocused -> SteadyColors.EmberFocused
        tone == SteadyButtonTone.PRIMARY -> SteadyColors.BarkLift
        else -> Color.Transparent
    }
    val content = when {
        isFocused -> SteadyColors.Bark
        tone == SteadyButtonTone.QUIET -> SteadyColors.LinenDim
        else -> SteadyColors.Linen
    }

    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier
            .focusRequester(focusRequester)
            // Requesting focus once real layout bounds exist, rather than in a plain
            // LaunchedEffect(Unit), is most of what makes this reliably win the D-pad's
            // initial focus on a freshly attached or freshly swapped screen. The short
            // extra delay is a second safeguard: Android's own default-focus-on-attach
            // dispatch runs from a similar "after layout" hook, so the two can still
            // race, and requesting a frame later than that assignment is what wins it.
            .onGloballyPositioned {
                if (autoFocus && !hasRequestedInitialFocus) {
                    hasRequestedInitialFocus = true
                    coroutineScope.launch {
                        delay(48)
                        runCatching { focusRequester.requestFocus() }
                    }
                }
            }
            .onFocusChanged { isFocused = it.isFocused }
            .steadyFocusScale(isFocused)
            .widthIn(min = if (tone == SteadyButtonTone.PRIMARY) 260.dp else 0.dp)
            .heightIn(min = 68.dp),
        shape = RoundedCornerShape(percent = 50),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = if (tone == SteadyButtonTone.PRIMARY) 40.dp else 28.dp,
            vertical = 12.dp,
        ),
    ) {
        Text(
            text = text,
            style = if (tone == SteadyButtonTone.QUIET) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.titleLarge
            },
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
