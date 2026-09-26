package com.steady.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.steady.app.ui.theme.SteadyColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Text entry for a remote, which is not text entry for a phone.
 *
 * A plain Material `OutlinedTextField` on Fire TV does three things wrong at once, and
 * all three were live in Steady before this component existed:
 *
 * 1. **It opens the keyboard on focus.** Moving the D-pad onto the field summoned the
 *    on-screen keyboard, which covers the bottom half of a 1080p screen — including, on
 *    the setup screen, the sentence explaining what to type and the Skip button. Apple's
 *    rule is the general one: focusing something must never activate it.
 * 2. **It traps focus.** Once the field had focus, no D-pad direction left it. Seven
 *    presses — down, up, left, right — and the only escape was Back, which exits the app
 *    and discards what was typed. The family member setting the television up for their
 *    parent could not finish setup with the remote at all.
 * 3. **Its focus indicator is a 2dp coloured line**, which is 2.3 arcminutes at three
 *    metres and one channel (colour). A real Fire TV does not draw a line at all: the
 *    focused thing fills. See [steadyFocusScale].
 *
 * So this is the pattern every television app uses instead. The resting state is a
 * focusable row showing the current value, filling with ember and growing exactly as
 * every button does. Select opens the editor and the keyboard together, deliberately.
 * Done or Back closes the editor and puts focus back on the row, where the D-pad works
 * again. The row never keeps focus hostage.
 */
@androidx.compose.foundation.layout.ExperimentalLayoutApi
@androidx.compose.ui.ExperimentalComposeUiApi
@Composable
fun SteadyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
) {
    var editing by remember { mutableStateOf(false) }
    var rowFocused by remember { mutableStateOf(false) }
    val rowFocusRequester = remember { FocusRequester() }
    val editorFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }
    var sawKeyboard by remember(editing) { mutableStateOf(false) }
    var editorHadFocus by remember(editing) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun stopEditing() {
        editing = false
        keyboard?.hide()
        scope.launch {
            delay(16)
            runCatching { rowFocusRequester.requestFocus() }
        }
    }

    if (editing) {
        LaunchedEffect(Unit) {
            delay(32)
            runCatching { editorFocusRequester.requestFocus() }
            keyboard?.show()
        }
    }

    // The editor closes when the keyboard does, however it was closed.
    //
    // This is the belt to BackHandler's braces, and it is the one that actually fires in
    // the common case: while the soft keyboard is up, the system consumes Back to hide
    // it and the app never sees the key at all. Without this, one Back left the editor
    // open, the keyboard gone, and focus on nothing — a live remote and a dead screen,
    // which is the exact failure this component was written to remove.
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible, editing) {
        if (editing && imeVisible) sawKeyboard = true
        if (editing && sawKeyboard && !imeVisible) stopEditing()
    }

    // Back closes the editor rather than the app. This has to be a BackHandler rather
    // than onPreviewKeyEvent on the field: when no soft keyboard is up, Back never
    // reaches the view hierarchy as a key event, it goes straight to the activity — so
    // the previous version of this dropped the typed name and quit to the launcher.
    BackHandler(enabled = editing) { stopEditing() }

    Box(modifier = modifier) {
        if (editing) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(color = SteadyColors.Bark),
                cursorBrush = SolidColor(SteadyColors.Bark),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { stopEditing() }),
                modifier = Modifier
                    .focusRequester(editorFocusRequester)
                    // Back, caught before the soft keyboard gets it. This is the only
                    // hook that works: while the keyboard is up the system consumes Back
                    // to hide it, so neither BackHandler nor onPreviewKeyEvent ever sees
                    // the press, and the editor was left open over a vanished keyboard
                    // with focus on nothing — a live remote and a dead screen.
                    .onPreInterceptKeyBeforeSoftKeyboard { event ->
                        if (event.type == KeyEventType.KeyUp && event.key == Key.Back) {
                            stopEditing()
                            true
                        } else {
                            false
                        }
                    }
                    .onFocusChanged { state ->
                        // A second route out: if anything takes focus off the editor,
                        // the editor stops being the editor.
                        if (state.isFocused) editorHadFocus = true else if (editorHadFocus) stopEditing()
                    }
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .background(SteadyColors.Linen, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            )
        } else {
            val shown = value.ifBlank { placeholder }
            // VoiceView rides focus and speaks whatever lands under it, so the row says
            // what it is and what pressing it does. Without this it reads out only the
            // current value, which for an empty field is the placeholder.
            val spoken = if (value.isBlank()) {
                "$label, empty. Press to type."
            } else {
                "$label, $value. Press to change."
            }
            Box(
                // focusRequester, the focus target (clickable) and the indicator all on
                // one chain. Split across a parent and a child composable, as this was,
                // requestFocus() throws because the parent's chain holds no focus target
                // — which is why the setup screen opened with focus on Continue instead
                // of on the one field it exists to fill in.
                modifier = Modifier
                    .focusRequester(rowFocusRequester)
                    .onGloballyPositioned {
                        if (autoFocus && !hasRequestedInitialFocus) {
                            hasRequestedInitialFocus = true
                            scope.launch {
                                delay(48)
                                runCatching { rowFocusRequester.requestFocus() }
                            }
                        }
                    }
                    .onFocusChanged { rowFocused = it.isFocused }
                    .steadyFocusScale(rowFocused)
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    // Focus fills, per a set of reference photographs of a real Fire TV: gold container and
                    // dark text when focused, a quiet filled trough when not. No edge in
                    // either state — a field that is only an outline is invisible at
                    // three metres, and an outline that brightens is the mock-up tell.
                    .background(
                        color = if (rowFocused) SteadyColors.EmberFocused else SteadyColors.BarkLift,
                        shape = RoundedCornerShape(14.dp),
                    )
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { editing = true }
                    .semantics(mergeDescendants = true) { contentDescription = spoken }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = shown,
                    style = MaterialTheme.typography.titleLarge,
                    color = when {
                        rowFocused -> SteadyColors.Bark
                        value.isBlank() -> SteadyColors.LinenDim
                        else -> SteadyColors.Linen
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * How far the fill grows above and below the row when the remote points at it. See
 * [SteadyFocusableRow] for why it grows rather than scales.
 */
val ROW_FOCUS_GROWTH: Dp = 7.dp

/**
 * A row in a list that the D-pad can reach but that does nothing when pressed.
 *
 * This exists because a scrolling list with no focusable children cannot be scrolled
 * with a remote at all: Compose scrolls a lazy list by moving focus into it, so a list
 * of plain `Surface`s is a screen where the D-pad is dead. It also gives VoiceView
 * something to speak, since static text is only reachable in Review Mode.
 *
 * **Focus grows this row upward and downward, never sideways.** Everything else in the
 * app says it has focus partly by getting bigger, through
 * [steadyFocusScale][com.steady.app.ui.components.steadyFocusScale], and for a button or
 * an artwork tile that is right — they are islands with room around them. A list row is
 * not. It already spans the full safe width, so 1.08 of it is 34dp of new row hanging off
 * each side of the screen, and what hangs off is the content: the focused row, the one the
 * remote is pointing at, was the only row in the list missing the first letter of its
 * date. Scaling a thing that is already as wide as it is allowed to be can only ever push
 * it somewhere it is not allowed to go.
 *
 * So the fill is painted behind the row rather than laid out with it, and on focus it
 * reaches [ROW_FOCUS_GROWTH] past the top and bottom edges into the gap the list leaves
 * between rows. The row's own footprint never changes, so nothing reflows and nothing
 * moves — including the words, which stay exactly where the eye left them. The geometry
 * channel survives; it just points along the axis that has somewhere to go.
 */
@Composable
fun SteadyFocusableRow(
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    /**
     * Receives whether the remote is pointing at this row, so the caller can invert its
     * text the way [SteadyButton][com.steady.app.ui.components.SteadyButton] does.
     *
     * Without it the row filled with ember and kept its light text, which made the
     * focused row the *least* readable thing in a long history: a near-white date on a
     * gold fill, with every unfocused row around it cream on dark brown. Focus is a fill
     * in this app, and a fill means the content flips to Bark.
     */
    content: @Composable (focused: Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val animated = animationsEnabled()
    val growth by animateDpAsState(
        targetValue = if (focused) ROW_FOCUS_GROWTH else 0.dp,
        animationSpec = tween(durationMillis = if (animated) FOCUS_MILLIS else 0),
        label = "steadyRowFocusGrowth",
    )
    val fill = if (focused) SteadyColors.EmberFocused else SteadyColors.BarkLift

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .onGloballyPositioned {
                if (autoFocus && !hasRequestedInitialFocus) {
                    hasRequestedInitialFocus = true
                    scope.launch {
                        delay(48)
                        runCatching { focusRequester.requestFocus() }
                    }
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .fillMaxWidth()
            // Painted, not laid out. `background` would put the fill inside the row's
            // bounds and there would be no way to grow it without growing the row.
            .drawBehind {
                val grow = growth.toPx()
                drawRoundRect(
                    color = fill,
                    topLeft = Offset(0f, -grow),
                    size = Size(size.width, size.height + grow * 2f),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                )
            }
            .clickable(interactionSource = interactionSource, indication = null) {}
            .widthIn(min = 0.dp)
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        content(focused)
    }
}
