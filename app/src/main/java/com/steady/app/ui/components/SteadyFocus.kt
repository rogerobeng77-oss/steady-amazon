package com.steady.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.steady.app.ui.theme.SteadyColors

/**
 * The one focus treatment in this app.
 *
 * **Focus fills. It does not outline.** That sentence is the whole of it, and it is the
 * single correction this redesign exists to make. Photographs of a real Fire TV
 * (a set of reference photographs of a real Fire TV, shots 05 to 07) show the selected item as a solid pill
 * with the ground colour showing through its text, while everything unselected has no
 * container at all. Our screens drew a ring around the focused item instead, which is
 * the clearest tell that a screen is a mock-up: at three metres a ring is a thin bright
 * rectangle competing with every other edge on the panel, and a filled shape is a block
 * of colour that the eye finds before it has read anything.
 *
 * Three independent channels carry focus, because at three metres any one of them can
 * fail — and the ring that used to be the first of them is now the last resort:
 *
 * 1. **Fill.** The focused control takes an ember container and bark text. Nothing that
 *    is not focused wears ember anywhere in the app, so gold means "the remote is
 *    pointing here" and never anything else.
 * 2. **Scale.** [FOCUS_SCALE], applied as a draw transform so no neighbour reflows. The
 *    footprint is reserved in both states, so nothing shifts when focus arrives.
 * 3. **Luminance.** The focused fill is the brightest thing on the screen, and anything
 *    that was dimmed while unfocused — artwork under a scrim, in particular — comes back
 *    to full strength.
 *
 * [steadyFocusEdge] adds a 6dp light edge, and exists for exactly one case: artwork
 * tiles, which already have a fill of their own, so the fill channel is unavailable. Six
 * is the floor from internal research notes on Fire TV / Fire OS platform facts — a 2dp ring is 2.6 arcminutes at three
 * metres and is not there. It is never used on a control that can fill instead.
 *
 * Deliberately not used: tonal elevation and drop shadow. Material 3's dark surface ramp
 * separates adjacent levels by as little as 1.02:1, which is one flat grey at three
 * metres in a lit room.
 *
 * Transitions run in [FOCUS_MILLIS], comfortably under the ~50 ms D-pad key repeat, so
 * the indicator never visibly lags a held direction. When the system animator scale is 0
 * the change happens instantly instead: the indicator still arrives, it just does not
 * travel.
 */
const val FOCUS_MILLIS = 120
val FOCUS_EDGE: Dp = 6.dp
const val FOCUS_SCALE = 1.08f

/** False when the person has turned system animations off. */
@Composable
fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        }.getOrDefault(true)
    }
}

/**
 * The geometry channel on its own: the focused control grows where it stands.
 *
 * [focused] comes from a `Modifier.onFocusChanged` upstream in the same chain, not from
 * a `MutableInteractionSource`. That is deliberate: an interaction source only reports
 * focus if whatever owns it chooses to emit the interaction, and Material's `Button`
 * does not do so reliably when focus arrives from the system's own default assignment
 * rather than from an explicit request. The symptom was a button that answered the
 * select key while drawing itself unfocused — the worst possible state on a television,
 * because the remote works and the screen will not say where it is pointing. Asking the
 * focus system directly cannot drift from the truth that way.
 */
@Composable
fun Modifier.steadyFocusScale(focused: Boolean): Modifier {
    val animated = animationsEnabled()
    val scale by animateFloatAsState(
        targetValue = if (focused) FOCUS_SCALE else 1f,
        animationSpec = tween(durationMillis = if (animated) FOCUS_MILLIS else 0),
        label = "steadyFocusScale",
    )
    return this.scale(scale)
}

/**
 * Scale plus a 6dp light edge, for artwork tiles only — a picture cannot change colour
 * to say it has focus without ceasing to be the picture. The edge's footprint is
 * reserved in both states so the row does not twitch as focus moves along it.
 */
@Composable
fun Modifier.steadyFocusEdge(focused: Boolean, shape: Shape): Modifier =
    this
        .steadyFocusScale(focused)
        .border(
            width = FOCUS_EDGE,
            color = if (focused) SteadyColors.Linen else Color.Transparent,
            shape = shape,
        )
        .padding(FOCUS_EDGE)
