package com.steady.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.steady.app.ui.theme.SteadyColors

/**
 * The ground every screen stands on: one wash, warm at the top and deepening toward the
 * bottom, painted edge to edge behind everything.
 *
 * It exists so that artwork at the top of a screen has somewhere to go. The reference
 * photographs (a set of reference photographs of a real Fire TV, shot 05) show a hero whose colour carries
 * down through the navigation strip and into the row of tiles under it, with no line
 * anywhere marking a boundary; our screens were a flat grey with boxes drawn on it. One
 * gradient behind the whole page, and artwork that fades into it rather than stopping at
 * an edge, is most of the difference.
 *
 * Deliberately shallow — 0xFF362818 to 0xFF17110D is under a stop and a half of
 * luminance. A gradient a viewer can name is decoration; this one is only there to stop
 * the eye finding a horizon where there should not be one. Eight-bit panels band across
 * a large area, which is the other reason to keep the range short.
 */
val STEADY_SAFE_X: Dp = 56.dp
val STEADY_SAFE_Y: Dp = 32.dp

private val STEADY_GROUND_TOP = Color(0xFF362818)

/**
 * The wash's own colour a given fraction of the way down the page.
 *
 * A band of artwork has to end on the colour that is underneath it, or its lower edge
 * becomes a horizontal line across the screen — which is the one thing the whole
 * gradient exists to prevent. Painting every band's fade to [SteadyColors.Bark] was
 * right only for a band ending at 44% of the height, where the wash happens to be Bark;
 * a shorter band stopped on a wash that is still warmer than that and left a visible
 * step. [HeroBand] takes its own fade colour from here.
 */
fun steadyGroundAt(fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return if (f <= 0.44f) {
        lerp(STEADY_GROUND_TOP, SteadyColors.Bark, f / 0.44f)
    } else {
        lerp(SteadyColors.Bark, SteadyColors.BarkDeep, (f - 0.44f) / 0.56f)
    }
}

@Composable
fun SteadyPage(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to STEADY_GROUND_TOP,
                    0.44f to SteadyColors.Bark,
                    1f to SteadyColors.BarkDeep,
                ),
            ),
        content = content,
    )
}

/**
 * Dissolves the first 28dp of a scrolling region instead of cutting it off square.
 *
 * [active] is the scroll position, not a constant: a region that has not moved has
 * nothing above it to dissolve, and fading it anyway dims the first row of a list for no
 * reason — which on this app's one list is also the row the remote is pointing at.
 *
 * A scrolling list under a fixed heading slices whatever passes beneath it through the
 * middle of a letter, which reads as a rendering fault rather than as "there is more
 * above". Drawn with [BlendMode.DstIn] over the content's own alpha, so it takes the
 * content away rather than painting a colour over it — the page wash behind stays
 * exactly as it is.
 */
fun Modifier.fadingTopEdge(active: Boolean, height: Dp = 28.dp): Modifier =
    if (!active) this else this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                1f to Color.Black,
                endY = height.toPx(),
            ),
            blendMode = BlendMode.DstIn,
        )
    }
