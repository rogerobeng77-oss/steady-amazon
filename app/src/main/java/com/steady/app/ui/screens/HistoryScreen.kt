package com.steady.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.steady.app.data.TextSize
import com.steady.app.domain.SessionHistoryEntry
import com.steady.app.domain.SessionPhrasing
import com.steady.app.ui.components.STEADY_SAFE_X
import com.steady.app.ui.components.STEADY_SAFE_Y
import com.steady.app.ui.components.SteadyPage
import com.steady.app.ui.components.SteadyButton
import com.steady.app.ui.components.SteadyFocusableRow
import com.steady.app.ui.components.fadingTopEdge
import com.steady.app.ui.components.steadyGroundAt
import com.steady.app.ui.theme.SteadyColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * What actually happened, day by day, newest first.
 *
 * This screen used to be a column of dates with the weekday printed beside each one, and
 * it was the worst frame in the app: every row said Tuesday, so the second column carried
 * no information at all, and a person checking whether the exercise was really being done
 * was shown nothing about the exercise. A list with one real column per row is a list that
 * failed to load, whatever the data behind it.
 *
 * It is built as a **record with columns** rather than as a stack of list items, because
 * that is what it is for. The four fields are the four questions anybody asks about a
 * session afterwards — when, how long, how did she find it, did she get through it — and
 * they sit at fixed widths so that they line up down the whole list. A column of durations
 * that align can be read down in one movement of the eye; the same numbers set as running
 * text have to be read row by row. The header above the list names the columns, which is
 * the difference between a record and four unlabelled facts.
 *
 * The movement count is drawn as well as written: five marks, filled for the ones that
 * were done. At three metres "all of it" is a shape before it is a number. The figures
 * stay beside the marks, because an unlabelled glyph is not a fact anyone can check.
 */
@Composable
fun HistoryScreen(
    entries: List<SessionHistoryEntry>,
    textSize: TextSize,
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scrolledDown by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }

    // The year is printed only where it is not the year of the most recent session. A
    // record repeating "2026" nineteen times down a column is nineteen repetitions of the
    // one thing on the screen that has not changed, in an app whose text is already large.
    val latestYear = entries.firstOrNull()?.date?.year
    val stacked = textSize.dropsDecoration

    SteadyPage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = STEADY_SAFE_X, vertical = STEADY_SAFE_Y),
        ) {
            // The heading and the count stay put while the list moves under them. As the
            // first item of the list itself, "Session history" scrolled away on the
            // second press of the D-pad and a person three rows in had no idea what they
            // were looking at.
            Text(
                text = "Session history",
                style = MaterialTheme.typography.headlineLarge,
                color = SteadyColors.Linen,
            )
            Text(
                text = if (entries.isEmpty()) {
                    "Nothing recorded yet."
                } else {
                    "${entries.size} session${if (entries.size == 1) "" else "s"} completed since the programme started."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = SteadyColors.LinenDim,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            )

            if (entries.isEmpty()) {
                // An invitation, not a failure. It says what will put something here and
                // it leaves a control on the screen, which is the difference between an
                // empty list and a screen that looks like it did not load.
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SteadyColors.BarkRaised,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = "Every session you finish is written down here: the day, how long " +
                            "it took, how you found it, and how much of it you got through. " +
                            "Today's is the first one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SteadyColors.LinenDim,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    SteadyButton(text = "Back to Home", onClick = onBack, autoFocus = true)
                }
            } else {
                // The column header is dropped at the accessibility sizes, along with the
                // columns it names: four fields do not fit across one line at 1.35x, so
                // the row folds into two lines and each field then has to say what it is
                // on its own. "Just right" and "12 minutes" already do; the marks keep
                // their figures. Nothing is lost but the alignment.
                if (!stacked) ColumnHeader()

                // Focusable rows, and this is not decoration. Compose scrolls a lazy list
                // by moving focus into it, so a list of plain Surfaces is a screen where
                // the remote does nothing at all. It also gives VoiceView something to
                // speak, since static text is only reachable in Review Mode.
                // Rows dissolve as they pass under the fixed heading rather than being
                // sliced through the middle of a date by its lower edge.
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().fadingTopEdge(active = scrolledDown),
                ) {
                    itemsIndexed(
                        items = entries,
                        key = { _, entry -> entry.date.toEpochDay() },
                    ) { index, entry ->
                        SteadyFocusableRow(autoFocus = index == 0) { focused ->
                            HistoryRow(
                                entry = entry,
                                focused = focused,
                                showYear = entry.date.year != latestYear,
                                stacked = stacked,
                            )
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            // Kept although the remote's own Back already works, because
                            // a viewer who has scrolled to the end of their history
                            // should not have to remember which button leaves.
                            SteadyButton(text = "Back to Home", onClick = onBack)
                        }
                    }
                }
            }
        }

        // The list runs past the bottom of the panel whenever there is more than a
        // screenful, so the panel dissolves into the ground rather than slicing a row in
        // half against a hard edge. A cut that looks like a fade reads as "there is more
        // here"; a cut that looks like a cut reads as a broken screen.
        if (entries.size > 3) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to steadyGroundAt(1f),
                        ),
                    ),
            )
        }
    }
}

// The four columns, in dp, inside a row that is 848dp wide with 24dp of padding at each
// end. They are declared once and used by both the header and every row, which is the
// only way a column stays a column.
//
// Widths are set by the longest thing each column can hold, not by dividing 800 into
// four. "How long" is the widest of them because of its shortest value: a session
// somebody pressed straight through reads "under a minute", which is fourteen characters
// where "11 minutes" is ten. At 178dp that one wrapped to two lines and made its row
// taller than every other row in the list — one uneven rung in a ledger whose whole
// argument is that it lines up.
private val COL_DATE: Dp = 225.dp
private val COL_HOW_LONG: Dp = 214.dp
private val COL_HOW_FELT: Dp = 183.dp

@Composable
private fun ColumnHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 10.dp),
    ) {
        HeaderCell("Date", COL_DATE)
        HeaderCell("How long", COL_HOW_LONG)
        HeaderCell("How it felt", COL_HOW_FELT)
        HeaderCell("Movements", null)
    }
}

@Composable
private fun HeaderCell(label: String, width: Dp?) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = SteadyColors.LinenDim,
        modifier = if (width != null) Modifier.width(width) else Modifier,
    )
}

@Composable
private fun HistoryRow(
    entry: SessionHistoryEntry,
    focused: Boolean,
    showYear: Boolean,
    stacked: Boolean,
) {
    // Focus is a fill in this app, so a focused row flips its whole contents to bark —
    // the marks included. A moss mark on a gold fill is the one combination in the
    // palette that loses to the thing behind it.
    val ink = if (focused) SteadyColors.Bark else SteadyColors.Linen
    val inkDim = if (focused) SteadyColors.Bark.copy(alpha = 0.72f) else SteadyColors.LinenDim
    val markDone = if (focused) SteadyColors.Bark else SteadyColors.Moss
    // The mark for a movement that was not done is the whole point of drawing them: a
    // row of five where two are unfilled is the only way this screen says "she stopped
    // early" without making her read a sentence about it. At 20% it was a smudge, and the
    // partial rows looked like the full ones from across a room.
    val markToDo = if (focused) SteadyColors.Bark.copy(alpha = 0.30f) else SteadyColors.Linen.copy(alpha = 0.34f)

    val dateText = entry.date.format(
        DateTimeFormatter.ofPattern(if (showYear) "d MMM yyyy" else "d MMMM"),
    )
    val detail = entry.detail

    if (detail == null) {
        // An older install, or a history seeded as dates alone. The row still carries the
        // day it happened, and then says plainly that the rest was not kept rather than
        // filling three columns with dashes, which reads as data that failed to arrive.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DateCell(dateText, ink, stacked)
            Text(
                text = "No detail was kept for this one.",
                style = MaterialTheme.typography.bodyMedium,
                color = inkDim,
            )
        }
        return
    }

    val howLong = SessionPhrasing.durationLabel(detail.secondsElapsed)
    val howFelt = SessionPhrasing.feltLabel(detail.feedback)
    val movements = SessionPhrasing.movementsLabel(detail.movementsDone, detail.movementsPlanned)

    if (stacked) {
        // Two lines, because four fields will not cross the safe width at 1.35x. The day
        // and the length lead; the answer and the marks sit under them.
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DateCell(dateText, ink, stacked = true)
                Text(
                    text = howLong,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = howFelt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (detail.feedback == null) inkDim else ink,
                )
                MovementMarks(
                    done = detail.movementsDone,
                    planned = detail.movementsPlanned,
                    label = movements,
                    doneColor = markDone,
                    toDoColor = markToDo,
                    labelColor = inkDim,
                )
            }
        }
        return
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DateCell(dateText, ink, stacked = false)
        Text(
            text = howLong,
            style = MaterialTheme.typography.bodyMedium,
            color = ink,
            modifier = Modifier.width(COL_HOW_LONG),
        )
        Text(
            text = howFelt,
            style = MaterialTheme.typography.bodyMedium,
            color = if (detail.feedback == null) inkDim else ink,
            modifier = Modifier.width(COL_HOW_FELT),
        )
        MovementMarks(
            done = detail.movementsDone,
            planned = detail.movementsPlanned,
            label = movements,
            doneColor = markDone,
            toDoColor = markToDo,
            labelColor = inkDim,
        )
    }
}

@Composable
private fun DateCell(text: String, color: Color, stacked: Boolean) {
    Text(
        text = text,
        // The anchor of the row, and the one field set heavier than the rest. The scale
        // in this app is narrow on purpose (see SteadyTheme), so weight is what separates
        // one thing from another.
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = if (stacked) Modifier else Modifier.width(COL_DATE),
    )
}

/**
 * Five marks and the figures beside them.
 *
 * The marks are upright strokes rather than dots or ticks: a tally is what somebody keeps
 * when they are counting repetitions of something physical, and it is the one place in
 * this app where a quantity is drawn instead of described. They are announced to VoiceView
 * as a single phrase, because five separate unlabelled shapes read aloud one at a time
 * would be worse than silence.
 */
@Composable
private fun MovementMarks(
    done: Int,
    planned: Int,
    label: String,
    doneColor: Color,
    toDoColor: Color,
    labelColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // One phrase for the marks and their figures together. Left alone, VoiceView
        // would announce five unlabelled shapes and then "5 of 5", which is the same
        // fact twice and the first time in a form nobody can act on.
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "$done of $planned movements done"
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            repeat(planned.coerceIn(0, 8)) { i ->
                Box(
                    modifier = Modifier
                        .size(width = 8.dp, height = 28.dp)
                        .background(
                            color = if (i < done) doneColor else toDoColor,
                            shape = RoundedCornerShape(4.dp),
                        ),
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = labelColor,
        )
    }
}
