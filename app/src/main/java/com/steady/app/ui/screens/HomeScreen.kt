package com.steady.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.steady.app.R
import com.steady.app.data.TextSize
import com.steady.app.data.TextSource
import com.steady.app.domain.CatchUpNotice
import com.steady.app.domain.Exercise
import com.steady.app.domain.PauseReason
import com.steady.app.domain.Tier
import com.steady.app.ui.components.CatchUpLine
import com.steady.app.ui.components.ExerciseTile
import com.steady.app.ui.components.HeroBand
import com.steady.app.ui.components.STEADY_SAFE_X
import com.steady.app.ui.components.STEADY_SAFE_Y
import com.steady.app.ui.components.SteadyButton
import com.steady.app.ui.components.SteadyButtonTone
import com.steady.app.ui.components.SteadyPage
import com.steady.app.ui.components.WeeklyDose
import com.steady.app.ui.components.TileState
import com.steady.app.ui.theme.SteadyColors

/**
 * The screen a person sees every single time they turn to Steady.
 *
 * One thing to press, auto-focused, so a remote press the instant the screen appears
 * already starts the session. That has not changed. What has changed is that the screen
 * now shows what it is about to ask for: today's movements, as artwork, in a row that
 * runs off the right edge of the panel the way a real Fire TV row does. Somebody deciding
 * whether they have the energy for this in the next four minutes can see the answer
 * instead of reading a promise, and a tile can be pressed to begin at that movement.
 *
 * There is still no menu and no tab bar. History, Settings and Pause sit as plain words
 * in the top corner, off the default focus path, and are never where the eye lands first.
 */
@Composable
fun HomeScreen(
    weekNumber: Int,
    tier: Tier,
    streakWeeks: Int,
    sessionsThisWeek: Int,
    prescribedPerWeek: Int,
    todaysExercises: List<Exercise>,
    textSize: TextSize,
    adjustmentChanged: Boolean,
    adjustmentText: String?,
    adjustmentSource: TextSource?,
    familyMemberName: String,
    lastSummarySentAt: String?,
    lastSummarySource: String?,
    isPaused: Boolean,
    pauseReason: PauseReason?,
    pausedSinceLabel: String?,
    /** The gap the app noticed while nobody was looking, or null. Already filtered by
     * [com.steady.app.domain.AdherenceNoticePolicy]; this screen only draws it. */
    catchUp: CatchUpNotice?,
    onDismissCatchUp: () -> Unit,
    onStartSession: () -> Unit,
    onStartAt: (Int) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    SteadyPage {
        // The hero: a photograph running the full width of the panel, hard against the
        // top and both sides, with no card and no border. Its lower edge fades into the
        // page and the row of tiles sits on that fade, so nothing marks where the
        // picture ends.
        HeroBand(
            photo = R.drawable.photo_home_hero,
            contentDescription = "An outdoor exercise class for older adults in a park",
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(312.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    start = STEADY_SAFE_X,
                    // With the wordmark dropped at the accessibility sizes, the routine
                    // name started at the very top of the panel, on the same baseline as
                    // Pause, History and Settings and at the same size — so "Seated
                    // routine · Pause · History · Settings" read as one nav row of four
                    // items, with the only piece of actual content in it disguised as
                    // chrome. It now starts under that row.
                    top = if (textSize.dropsDecoration) STEADY_SAFE_Y + 32.dp else STEADY_SAFE_Y,
                )
                // The full 848dp between the safe margins of a 960dp canvas. 480dp
                // wrapped the offer onto a third line; 800dp still left the notice and
                // its "Not today" fighting for the same band at the 1.35x text size,
                // where both grow.
                .widthIn(max = 848.dp),
        ) {
            // At the larger text sizes the wordmark goes rather than growing. It is the
            // one piece of pure decoration on this screen, the person already knows what
            // they turned on, and 56sp x 1.35 is 76sp of nothing pushing the button that
            // matters toward the bottom of the panel. Cutting secondary content beats
            // shrinking it.
            if (!textSize.dropsDecoration) {
                Text(
                    text = "Steady",
                    style = MaterialTheme.typography.headlineLarge,
                    color = SteadyColors.Linen,
                )
            }

            if (isPaused) {
                // Both paused lines take the same measure the catch-up notice takes.
                //
                // Uncapped they used the column's full 848dp, which at the normal text
                // size put "Nothing is lost while you are away." out at 875dp — across
                // the sunlit part of the park photograph, where "lost while" all but
                // disappeared. The hero's scrim is built for a column that ends around
                // 500dp, not one that runs to the right edge of the panel.
                //
                // At the accessibility sizes the measure stays wide, because there the
                // same sentence needs three lines inside 590dp and the column would grow
                // down into the exercise row. It also lands lower and further left in
                // the frame at those sizes, over the dark trees rather than the sky.
                val pausedMeasure = if (textSize.dropsDecoration) 848.dp else 590.dp
                Text(
                    text = "Paused since $pausedSinceLabel${pauseReason?.let { " · ${it.label()}" }.orEmpty()}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = SteadyColors.Moss,
                    modifier = Modifier.widthIn(max = pausedMeasure).padding(top = 6.dp, bottom = 6.dp),
                )
                Text(
                    // The short form of the sentence the Pause screen uses, opening on the same
                    // clause so the two screens are plainly making one promise.
                    text = "The programme is held exactly where it is. Nothing is lost while you are away.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SteadyColors.LinenDim,
                    modifier = Modifier.widthIn(max = pausedMeasure).padding(bottom = 22.dp),
                )
                SteadyButton(text = "Resume", onClick = onResume, autoFocus = true)
            } else {
                WeeklyDose(
                    // At the larger steps the week number goes with the wordmark and the
                    // dose dots. Without the wordmark above it this line starts at the
                    // very top of the panel, and at 1.35x "Week 2 · Seated routine" ran
                    // straight under "Pause" in the corner nav. Cutting the most
                    // decorative part of the line is the rule this screen already
                    // follows at these sizes (TextSize.dropsDecoration); the routine
                    // name is what tells somebody what they are about to do, and
                    // VoiceView is still read the full label below.
                    tierLabel = tierLabel(tier, weekNumber, includeWeek = !textSize.dropsDecoration),
                    spokenLabel = tierLabel(tier, weekNumber, includeWeek = true),
                    sessionsThisWeek = sessionsThisWeek,
                    prescribedPerWeek = prescribedPerWeek,
                    showDots = !textSize.dropsDecoration,
                    modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
                )

                SteadyButton(
                    text = "Start Today's Session",
                    onClick = onStartSession,
                    autoFocus = true,
                    // Pressing "Not today" deletes the control the remote is pointing
                    // at, and Compose then gives focus to the nearest survivor, which on
                    // this screen was "Pause" in the corner. Sending it back here means
                    // dismissing a sentence cannot cost somebody their programme on the
                    // next press.
                    autoFocusKey = catchUp != null,
                )

                // The notice goes in the slot the streak line and the adjustment line
                // already share, *below* the button, and never above it.
                //
                // Above the button was the obvious place and it was wrong. A two-line
                // notice pushed "Start Today's Session" down by about 70dp, which on a
                // 540dp canvas put it over the tile row's heading — and it moved the one
                // control this whole app is built around, so the button a person had
                // learned the position of sat somewhere different on exactly the days
                // they had been away. Below the button, nothing above it can move, the
                // fact still sits under the thing that acts on it, and every text size
                // grows downward into the gap instead of into the row.
                //
                // Only one of the three can show. "2 weeks in a row" next to "the last
                // session was 6 days ago" is two scores for the same person on one
                // screen, and the one worth reading is the one about today.
                if (catchUp != null) {
                    // "Not today" sits beside the sentence it dismisses rather than
                    // under the primary button, for two reasons. It costs no height —
                    // the notice is two lines tall and a quiet control fits in that band
                    // — and beside the button it was being squeezed by the column's own
                    // width and rendering as "Not to…", which is worse than not having
                    // it. It stays off the default focus path; D-pad right from the
                    // notice, or down from the button, reaches it.
                    Row(
                        modifier = Modifier.padding(top = 12.dp).focusGroup(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CatchUpLine(
                            notice = catchUp,
                            // Capped, so the Row has width left for the control beside
                            // it. A Text in a Row with no weight takes everything it is
                            // offered, which had "Not today" rendering as a bare "…".
                            // 600dp of the column's 800dp, leaving 192dp for the
                            // control beside it — enough for "Not today" at the 1.35x
                            // text size, where it measures about 170dp. A Text in a Row
                            // with no weight takes everything it is offered, and without
                            // this cap "Not today" rendered as a bare ellipsis.
                            // Narrower at the accessibility sizes, because "Not today"
                            // grows too and was rendering as "Not tod…" at 1.35x. The
                            // notice is a single line there (the offer is dropped just
                            // below), so it needs less room, not more.
                            // Sized so the headline stays on one line and "Not today"
                            // still fits beside it. Both grow at 1.35x, and the two
                            // failure modes are opposite: too wide truncates the button
                            // to "Not tod…", too narrow wraps the fact onto a second
                            // line that lands on the exercise row's heading.
                            modifier = Modifier.widthIn(
                                max = if (textSize.dropsDecoration) 590.dp else 600.dp,
                            ),
                            // At the larger steps the offer goes and the fact stays.
                            // Same rule the wordmark and the dose dots already follow
                            // (see TextSize.dropsDecoration): past about 32sp the layout
                            // sheds secondary content rather than shrinking it, and
                            // between "the last session was 6 days ago" and "today's
                            // session is ready when you are", the button above already
                            // makes the second point in the largest type on the screen.
                            showOffer = !textSize.dropsDecoration,
                        )
                        SteadyButton(
                            text = "Not today",
                            onClick = onDismissCatchUp,
                            tone = SteadyButtonTone.QUIET,
                        )
                    }
                } else if (adjustmentChanged && adjustmentText != null) {
                    Text(
                        text = adjustmentText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SteadyColors.Ember,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    // Every sentence that can appear above is one Steady wrote itself
                    // (AdjustmentPhrasings); a model can only pick which. Saying so is
                    // cheap and it is the difference between "a model chose this
                    // wording" and "a model wrote this", which are not the same promise.
                    if (adjustmentSource == TextSource.AI_CHOSEN) {
                        Text(
                            text = "Steady's own wording · AI chose which one",
                            style = MaterialTheme.typography.labelLarge,
                            color = SteadyColors.LinenDim,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                } else if (streakWeeks > 0) {
                    Text(
                        text = streakLabel(streakWeeks),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SteadyColors.Moss,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = STEADY_SAFE_X - 20.dp, top = STEADY_SAFE_Y - 12.dp)
                // Keeps D-pad left/right cycling between Pause, History and Settings
                // rather than bidirectional search jumping into the hero column, which
                // sits closer once focus is near this row's edges.
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        ) {
            // Quiet, not absent. These were three outlined pills across the top of the
            // screen at heading size: the loudest thing on a screen whose entire
            // argument is that there is one thing to press. As plain words they stay
            // reachable in one press and stop competing with the button that matters,
            // and focus still fills them with ember exactly as it fills everything else.
            if (!isPaused) {
                SteadyButton(text = "Pause", onClick = onPause, tone = SteadyButtonTone.QUIET)
            }
            SteadyButton(text = "History", onClick = onOpenHistory, tone = SteadyButtonTone.QUIET)
            SteadyButton(text = "Settings", onClick = onOpenSettings, tone = SteadyButtonTone.QUIET)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(bottom = STEADY_SAFE_Y - 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = STEADY_SAFE_X + 6.dp, end = STEADY_SAFE_X, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = if (isPaused) "Waiting for you" else "Today, in this order",
                    style = MaterialTheme.typography.labelLarge,
                    color = SteadyColors.LinenDim,
                )
                if (familyMemberName.isNotBlank()) {
                    // Status only. The person this update is about can see that it went
                    // and what it said; sending it lives in Settings next to the name it
                    // is addressed to, so Home keeps one thing to press.
                    Text(
                        // At the accessibility sizes this sheds the name and the source
                        // note and keeps the status. "Margaret-Anne's update · not sent
                        // yet" is 583dp of a 520dp slot at 1.35x, so it was rendering as
                        // "Margaret-Anne's update · not se…" — somebody's family member
                        // cut off mid-word on the screen they open every day. Both
                        // dropped parts are shown in full in Settings, beside the name
                        // the update is addressed to. Same rule as the wordmark and the
                        // dose dots: shed secondary content, never shrink it.
                        text = if (textSize.dropsDecoration) {
                            statusLine(lastSummarySentAt, lastSummarySource, compact = true)
                        } else {
                            "$familyMemberName's update \u00b7 " +
                                statusLine(lastSummarySentAt, lastSummarySource)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = SteadyColors.LinenDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 520.dp),
                    )
                }
            }
            // A row that runs off the right edge rather than five tiles squeezed to fit.
            // That is how a television row behaves and it is also the honest signal that
            // there is more to the right; a row that ends flush with the safe area reads
            // as a complete list even when it is not.
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(start = STEADY_SAFE_X - 6.dp, end = 24.dp),
                modifier = Modifier.focusGroup(),
            ) {
                items(items = todaysExercises, key = { it.id }) { exercise ->
                    val position = todaysExercises.indexOf(exercise)
                    ExerciseTile(
                        exercise = exercise,
                        onSelect = if (isPaused) null else ({ onStartAt(position) }),
                        artWidth = 264.dp,
                        state = TileState.UPCOMING,
                    )
                }
            }
        }
    }
}

private fun PauseReason.label(): String = when (this) {
    PauseReason.AWAY -> "away"
    PauseReason.UNWELL -> "unwell"
}

private fun statusLine(
    lastSummarySentAt: String?,
    lastSummarySource: String?,
    compact: Boolean = false,
): String {
    if (lastSummarySentAt == null) return if (compact) "Update not sent yet" else "not sent yet"
    if (compact) return "Update last sent $lastSummarySentAt"
    val sourceNote = when (lastSummarySource) {
        "AI_WRITTEN" -> " · AI-written, checked"
        "AI_CHOSEN" -> " · AI-chosen wording"
        "RULES" -> " · standard wording"
        else -> ""
    }
    return "last sent $lastSummarySentAt$sourceNote"
}

private fun tierLabel(tier: Tier, weekNumber: Int, includeWeek: Boolean): String {
    val tierName = when (tier) {
        Tier.CHAIR_SUPPORTED -> "Seated routine"
        Tier.STANDING -> "Standing routine"
    }
    return if (includeWeek) "Week $weekNumber · $tierName" else tierName
}

private fun streakLabel(streakWeeks: Int): String {
    val weekWord = if (streakWeeks == 1) "week" else "weeks"
    return "$streakWeeks $weekWord in a row"
}
