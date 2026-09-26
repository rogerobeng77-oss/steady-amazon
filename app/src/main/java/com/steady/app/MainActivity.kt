package com.steady.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.steady.app.data.SteadyRepository
import com.steady.app.ui.SteadyViewModel
import com.steady.app.ui.components.animationsEnabled
import com.steady.app.ui.components.SteadyButton
import com.steady.app.ui.components.SteadyButtonTone
import com.steady.app.ui.components.SteadyPage
import com.steady.app.ui.screens.HistoryScreen
import com.steady.app.ui.screens.HomeScreen
import com.steady.app.ui.screens.PauseScreen
import com.steady.app.ui.screens.SessionScreen
import com.steady.app.ui.screens.SettingsScreen
import com.steady.app.ui.screens.SetupScreen
import com.steady.app.ui.theme.SteadyColors
import com.steady.app.ui.theme.SteadyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = SteadyRepository(applicationContext)

        setContent {
            // The text-size setting has to be read before the theme is built, since it
            // is the theme. Fire TV has no system text-size control, so this is the only
            // one the person has (see com.steady.app.data.TextSize).
            val bootState by repository.state.collectAsState(initial = null)
            SteadyTheme(textScale = bootState?.textSize?.scale ?: 1f) {
                // `color` explicitly, not `Modifier.background`. Surface paints its own
                // colour over whatever the modifier drew, and its default is
                // colorScheme.surface, which here is BarkRaised. So the app's ground was
                // BarkRaised, every "raised" surface in it was the same value, and every
                // card, list row and outlined button measured 1.00:1 against the screen
                // behind it. That is why the caregiver card was invisible on the device
                // while looking correct in the source.
                Surface(modifier = Modifier.fillMaxSize(), color = SteadyColors.Bark) {
                    val viewModel: SteadyViewModel = viewModel(
                        factory = SteadyViewModelFactory(repository),
                    )
                    SteadyApp(viewModel)
                }
            }
        }
    }
}

@Composable
private fun SteadyApp(viewModel: SteadyViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val adjustmentNarration by viewModel.adjustmentNarration.collectAsState()
    var screen by rememberSaveable { mutableStateOf("home") }
    var startAt by rememberSaveable { mutableIntStateOf(0) }

    // Never a blank screen. On Fire OS focus is the screen reader, so a frame with
    // nothing focusable is a frame where VoiceView says nothing and the remote does
    // nothing — for somebody with low vision that is indistinguishable from a broken
    // television. This state lasts a few milliseconds off a local DataStore read, and it
    // still gets a sentence and a control.
    if (uiState.loading) {
        LoadingScreen()
        return
    }

    if (!uiState.setupComplete) {
        SetupScreen(
            textSize = uiState.textSize,
            onComplete = { name -> viewModel.completeSetup(name) },
        )
        return
    }

    // Back always goes up one level, from every screen but Home (the shared Fire TV craft reference, "back
    // goes up one level, from everywhere"). Home has no handler here, so the system
    // default applies there and exits to the Fire TV launcher: Home is both this app's
    // first screen and its last one, which is the fixed-start-destination rule.
    BackHandler(enabled = screen != "home") { screen = "home" }

    // Was a raw `when (screen)` swap -- an instant cut on every navigation.
    // an internal design review's item 1: 220 ms sits inside
    // the shared Fire TV craft reference's 200-250 ms screen-transition range and next
    // to, not on top of, [FOCUS_MILLIS] (120 ms, see SteadyFocus.kt) and the
    // mock launcher's 180 ms lift. A fade, not a slide: nothing in this app's
    // navigation implies screens live to the left or right of one another.
    val animated = animationsEnabled()
    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            val duration = if (animated) 220 else 0
            fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
        },
        label = "screen",
    ) { current ->
    when (current) {
        "session" -> SessionScreen(
            exercises = uiState.todaysExercises,
            onSessionCompleted = { seconds, done, planned ->
                viewModel.recordSessionCompleted(seconds, done, planned)
            },
            onFeedback = { feedback -> viewModel.recordFeedback(feedback) },
            onReturnHome = { screen = "home" },
            textSize = uiState.textSize,
            // Pressing a tile on Home opens the session at that movement rather than at
            // the start. Skipping forward is not a new capability — "Done, next" was
            // always pressable immediately — this is just the shorter route to it, for
            // somebody who came back to repeat one thing.
            startIndex = startAt,
        )

        "history" -> HistoryScreen(
            entries = uiState.history,
            textSize = uiState.textSize,
            onBack = { screen = "home" },
        )

        "settings" -> SettingsScreen(
            currentFamilyMemberName = uiState.familyMemberName,
            totalSessionsCompleted = uiState.completedSessionDates.size,
            textSize = uiState.textSize,
            lastSummarySentAt = uiState.lastSummarySentAt,
            lastSummaryText = uiState.lastSummaryText,
            lastCheckedOnLabel = uiState.lastCheckedOnLabel,
            onTextSizeChange = { viewModel.setTextSize(it) },
            onSendSummary = { viewModel.sendWeeklySummary() },
            onSave = { name ->
                viewModel.updateFamilyMemberName(name)
                screen = "home"
            },
            onBack = { screen = "home" },
        )

        "pause" -> PauseScreen(
            textSize = uiState.textSize,
            onConfirm = { reason ->
                viewModel.pause(reason)
                screen = "home"
            },
            onCancel = { screen = "home" },
        )

        else -> HomeScreen(
            weekNumber = uiState.weekNumber,
            tier = uiState.tier,
            streakWeeks = uiState.streakWeeks,
            sessionsThisWeek = uiState.sessionsThisWeek,
            prescribedPerWeek = uiState.prescribedPerWeek,
            todaysExercises = uiState.todaysExercises,
            adjustmentChanged = uiState.adjustmentChanged,
            textSize = uiState.textSize,
            adjustmentText = adjustmentNarration?.text ?: uiState.adjustmentFallbackText,
            // The adjustment line's provenance travels with its text rather than being
            // dropped here, so the one place a model touches the exercise screen is
            // never indistinguishable from the rules' own sentence.
            adjustmentSource = adjustmentNarration?.source,
            familyMemberName = uiState.familyMemberName,
            lastSummarySentAt = uiState.lastSummarySentAt,
            lastSummarySource = uiState.lastSummarySource,
            isPaused = uiState.isPaused,
            pauseReason = uiState.pauseReason,
            pausedSinceLabel = uiState.pausedSinceLabel,
            catchUp = uiState.catchUp,
            onDismissCatchUp = { viewModel.dismissCatchUp() },
            onStartSession = {
                startAt = 0
                screen = "session"
            },
            onStartAt = { index ->
                startAt = index
                screen = "session"
            },
            onOpenHistory = { screen = "history" },
            onOpenSettings = { screen = "settings" },
            onPause = { screen = "pause" },
            onResume = { viewModel.resume() },
        )
    }
    }
}

/** The only screen in this app nobody is meant to read. It still says what is happening
 * and still holds a focusable control, so the remote is never dead. */
@Composable
private fun LoadingScreen() {
    SteadyPage {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 58.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Getting today's session ready",
                style = MaterialTheme.typography.headlineLarge,
                color = SteadyColors.Linen,
            )
            Text(
                text = "This only takes a moment.",
                style = MaterialTheme.typography.bodyMedium,
                color = SteadyColors.LinenDim,
                modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
            )
            SteadyButton(
                text = "Waiting…",
                onClick = {},
                autoFocus = true,
                tone = SteadyButtonTone.SECONDARY,
            )
        }
    }
}

private class SteadyViewModelFactory(
    private val repository: SteadyRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SteadyViewModel(repository) as T
    }
}
