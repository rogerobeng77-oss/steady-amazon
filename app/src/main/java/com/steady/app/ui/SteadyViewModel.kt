package com.steady.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steady.app.data.BedrockHttpNarrator
import com.steady.app.data.NarratedText
import com.steady.app.data.NarrationService
import com.steady.app.data.SteadyRepository
import com.steady.app.data.SteadyState
import com.steady.app.data.SummaryFacts
import com.steady.app.data.TextSize
import com.steady.app.data.TextSource
import com.steady.app.domain.AdjustmentKind
import com.steady.app.domain.AdherenceNoticePolicy
import com.steady.app.domain.AdherenceTracker
import com.steady.app.domain.CatchUpEvaluator
import com.steady.app.domain.CatchUpNotice
import com.steady.app.domain.CaregiverSummaryBuilder
import com.steady.app.domain.Exercise
import com.steady.app.domain.IntensityAdjuster
import com.steady.app.domain.PausePeriod
import com.steady.app.domain.PauseReason
import com.steady.app.domain.PauseTracker
import com.steady.app.domain.ProgramEngine
import com.steady.app.domain.SessionFeedback
import com.steady.app.domain.SessionHistory
import com.steady.app.domain.SessionHistoryEntry
import com.steady.app.domain.Tier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Everything the UI needs, derived once here so every screen reads the same numbers.
 * Deriving all of it from [SteadyState] on every emission, rather than storing it as
 * separately-mutated fields, is what keeps the tier, the exercise list and the streak
 * from ever disagreeing with the session history they are computed from. */
data class SteadyUiState(
    val loading: Boolean = true,
    val setupComplete: Boolean = false,
    val familyMemberName: String = "",
    val weekNumber: Int = 1,
    val tier: Tier = Tier.CHAIR_SUPPORTED,
    val todaysExercises: List<Exercise> = emptyList(),
    val adjustmentChanged: Boolean = false,
    val adjustmentFallbackText: String? = null,
    val adjustmentKind: AdjustmentKind = AdjustmentKind.UNCHANGED,
    val lastFeedback: SessionFeedback? = null,
    val streakWeeks: Int = 0,
    val sessionsThisWeek: Int = 0,
    val prescribedPerWeek: Int = ProgramEngine.DEFAULT_PRESCRIBED_SESSIONS_PER_WEEK,
    val lastSummarySentAt: String? = null,
    val lastSummaryText: String? = null,
    val lastSummarySource: String? = null,
    val completedSessionDates: List<LocalDate> = emptyList(),
    /** One row per completed day, newest first, each carrying whatever detail was kept
     * about it. Joined here rather than on the screen so History reads one list. */
    val history: List<SessionHistoryEntry> = emptyList(),
    val isPaused: Boolean = false,
    val pauseReason: PauseReason? = null,
    val pausedSinceLabel: String? = null,
    val textSize: TextSize = TextSize.NORMAL,
    /** The gap the app noticed, or null when there is nothing to say. Already filtered
     * by [AdherenceNoticePolicy.showOnHome], so a screen never has to decide whether it
     * is allowed to show this. */
    val catchUp: CatchUpNotice? = null,
    /** The day the scheduled evaluation last ran, for the line in Settings that makes
     * "this app checks on its own" a thing you can read off the device. */
    val lastCheckedOnLabel: String? = null,
    /** Days between the most recent session and today, or null if there has never been
     * one. Handed to the caregiver summary so the weekly update can finally carry it. */
    val daysSinceLastSession: Int? = null,
)

class SteadyViewModel(
    private val repository: SteadyRepository,
    private val narrationService: NarrationService = NarrationService(BedrockHttpNarrator()),
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    val uiState: StateFlow<SteadyUiState> = repository.state
        .map { state -> deriveState(state) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SteadyUiState())

    /** The adjustment banner starts as the deterministic sentence (always correct,
     * always instant) and is upgraded in place if Bedrock answers before the person
     * moves on. A null value means "nothing to say," not "still loading": the
     * deterministic text already went into [SteadyUiState.adjustmentFallbackText]. */
    private val _adjustmentNarration = MutableStateFlow<NarratedText?>(null)
    val adjustmentNarration: StateFlow<NarratedText?> = _adjustmentNarration.asStateFlow()

    private var narratedFeedback: SessionFeedback? = null

    /**
     * What day it is, for anything that writes rather than reads.
     *
     * [deriveState] gets the offset for free because it is handed the state; the write
     * paths are not, and a session recorded under today's real date while the screen
     * that recorded it believed it was nine days later would put the demo and the
     * programme into two different weeks. In a release build the offset is always zero
     * and this is `LocalDate.now()`.
     */
    private suspend fun currentDay(): LocalDate = today().plusDays(repository.state.first().demoDayOffset)

    private fun deriveState(state: SteadyState): SteadyUiState {
        // The demo offset is zero in every release build (see SteadyState.demoDayOffset),
        // so in production this is exactly today(). Applied in one place, deliberately:
        // every rule below then sees the same day, which is what makes walking the
        // programme forward a real walk rather than a screen with different dates on it.
        val asOf = today().plusDays(state.demoDayOffset)
        val programStart = state.programStart ?: asOf

        val allPausePeriods: List<PausePeriod> = state.closedPausePeriods +
            listOfNotNull(state.activePause)
        val excludedWeeks = PauseTracker.excludedWeekIndices(programStart, allPausePeriods, asOf)

        val tier = ProgramEngine.tierFor(
            programStart,
            state.completedSessionDates,
            asOf,
            excludedWeeks = excludedWeeks,
        )
        val baseExercises = ProgramEngine.sessionFor(
            programStart,
            state.completedSessionDates,
            asOf,
            excludedWeeks = excludedWeeks,
        )

        val justResumedToday = state.lastResumeEpochDay == asOf.toEpochDay()
        val adjustment = when {
            state.activePause != null -> IntensityAdjuster.adjust(baseExercises, null) // unused while paused
            justResumedToday -> IntensityAdjuster.welcomeBackAdjustment(baseExercises)
            else -> IntensityAdjuster.adjust(baseExercises, state.lastFeedback)
        }

        val streak = AdherenceTracker.currentStreakWeeks(
            programStart,
            state.completedSessionDates,
            asOf,
            excludedWeeks = excludedWeeks,
        )
        val sessionsThisWeek = AdherenceTracker.sessionsThisWeek(programStart, state.completedSessionDates, asOf)
        val weekNumber = ((asOf.toEpochDay() - programStart.toEpochDay()) / 7).toInt() + 1
        val daysSinceLastSession =
            AdherenceTracker.daysSinceLastSession(state.completedSessionDates, asOf)

        // The same evaluator the scheduled worker runs, on the same recorded dates. One
        // implementation, so the line on Home and the note in the Notification Center
        // can never disagree about whether there is a gap.
        val notice = CatchUpEvaluator.evaluate(
            programStart = state.programStart,
            completedDates = state.completedSessionDates,
            asOfDate = asOf,
            excludedWeeks = excludedWeeks,
            isPaused = state.activePause != null,
        )
        val visibleNotice = notice.takeIf {
            AdherenceNoticePolicy.showOnHome(
                notice = it,
                dismissedOnEpochDay = state.catchUpDismissedEpochDay,
                todayEpochDay = asOf.toEpochDay(),
            )
        }

        val adjustmentFallbackText = when {
            justResumedToday -> IntensityAdjuster.fallbackWelcomeBackExplanation()
            state.lastFeedback != null -> IntensityAdjuster.fallbackExplanation(state.lastFeedback)
            else -> null
        }

        if (!justResumedToday && adjustment.changed && state.lastFeedback != null && state.lastFeedback != narratedFeedback) {
            refreshAdjustmentNarration(state.lastFeedback, adjustment.kind, adjustment.description)
        }

        return SteadyUiState(
            loading = false,
            setupComplete = state.setupComplete,
            familyMemberName = state.familyMemberName,
            weekNumber = weekNumber.coerceAtLeast(1),
            tier = tier,
            todaysExercises = adjustment.exercises,
            adjustmentChanged = adjustment.changed && state.activePause == null,
            adjustmentFallbackText = adjustmentFallbackText,
            adjustmentKind = if (justResumedToday) AdjustmentKind.WELCOME_BACK else adjustment.kind,
            lastFeedback = state.lastFeedback,
            streakWeeks = streak,
            sessionsThisWeek = sessionsThisWeek,
            lastSummarySentAt = state.lastSummarySentAt,
            lastSummaryText = state.lastSummaryText,
            lastSummarySource = state.lastSummarySource,
            completedSessionDates = state.completedSessionDates,
            history = SessionHistory.rows(state.completedSessionDates, state.sessionRecords),
            isPaused = state.activePause != null,
            pauseReason = state.activePause?.reason,
            textSize = state.textSize,
            catchUp = visibleNotice,
            daysSinceLastSession = daysSinceLastSession,
            lastCheckedOnLabel = state.lastCheckEpochDay?.let {
                LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            },
            pausedSinceLabel = state.activePause?.let {
                LocalDate.ofEpochDay(it.startEpochDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            },
        )
    }

    private fun refreshAdjustmentNarration(
        feedback: SessionFeedback,
        kind: AdjustmentKind,
        description: String,
    ) {
        narratedFeedback = feedback
        viewModelScope.launch {
            _adjustmentNarration.value = narrationService.adjustmentExplanation(kind, description)
        }
    }

    fun completeSetup(familyMemberName: String) {
        viewModelScope.launch {
            repository.completeSetup(familyMemberName, currentDay())
        }
    }

    fun setTextSize(size: TextSize) {
        viewModelScope.launch { repository.setTextSize(size) }
    }

    fun updateFamilyMemberName(name: String) {
        viewModelScope.launch {
            repository.updateFamilyMemberName(name)
        }
    }

    /** Called once, by the session screen, at the moment the last movement is finished.
     * The three numbers are what that screen watched happen; nothing here infers them. */
    fun recordSessionCompleted(secondsElapsed: Int, movementsDone: Int, movementsPlanned: Int) {
        viewModelScope.launch {
            repository.recordSessionCompleted(
                date = currentDay(),
                secondsElapsed = secondsElapsed,
                movementsDone = movementsDone,
                movementsPlanned = movementsPlanned,
            )
        }
    }

    fun recordFeedback(feedback: SessionFeedback) {
        viewModelScope.launch {
            repository.recordFeedback(feedback, currentDay())
        }
    }

    /** "Not today". One press, and the line is gone for the rest of this day. */
    fun dismissCatchUp() {
        viewModelScope.launch { repository.dismissCatchUp(currentDay()) }
    }

    fun pause(reason: PauseReason) {
        viewModelScope.launch {
            repository.pause(reason, currentDay())
        }
    }

    fun resume() {
        viewModelScope.launch {
            repository.resume(currentDay())
            _adjustmentNarration.value = null
            narratedFeedback = null
        }
    }

    fun sendWeeklySummary() {
        viewModelScope.launch {
            val state = uiState.value
            val facts = SummaryFacts(
                familyMemberName = state.familyMemberName,
                weekNumber = state.weekNumber,
                sessionsCompletedThisWeek = state.sessionsThisWeek,
                prescribedPerWeek = state.prescribedPerWeek,
                streakWeeks = state.streakWeeks,
                tier = state.tier,
                daysSinceLastSession = state.daysSinceLastSession,
            )
            val narrated = narrationService.summary(facts) {
                CaregiverSummaryBuilder.build(
                    familyMemberName = state.familyMemberName,
                    weekNumber = state.weekNumber,
                    sessionsCompletedThisWeek = state.sessionsThisWeek,
                    prescribedPerWeek = state.prescribedPerWeek,
                    streakWeeks = state.streakWeeks,
                    tier = state.tier,
                    daysSinceLastSession = state.daysSinceLastSession,
                )
            }
            val timeLabel = currentDay().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            repository.recordSummarySent(timeLabel, narrated.text, narrated.source)
        }
    }
}
