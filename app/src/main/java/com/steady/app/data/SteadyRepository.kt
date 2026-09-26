package com.steady.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.steady.app.BuildConfig
import com.steady.app.domain.CatchUpNotice
import com.steady.app.domain.CatchUpReason
import com.steady.app.domain.PausePeriod
import com.steady.app.domain.PauseReason
import com.steady.app.domain.SessionFeedback
import com.steady.app.domain.SessionRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Everything Steady remembers lives on this device, in this file, and nowhere else.
 * There is no account, no server, and no sync: the whole point is that the person using
 * the television never has to sign in to anything. */
class SteadyRepository(private val context: Context) {

    private object Keys {
        val programStartEpochDay = longPreferencesKey("program_start_epoch_day")
        val completedSessionDates = stringSetPreferencesKey("completed_session_dates")

        /**
         * What happened during each of those sessions, one encoded [SessionRecord] per
         * entry. Deliberately a *second* key rather than a richer encoding of the first.
         *
         * `completed_session_dates` is what every rule in the app is computed from — the
         * tier, the streak, the weekly count, the catch-up evaluation, the worker. Those
         * rules care about which days a session happened and about nothing else, and
         * rewriting the shape of the value they all read, to carry three facts only one
         * screen needs, would be putting a whole app's behaviour at the mercy of a parser.
         * Detail lives beside the dates and is joined to them on the way to the screen, so
         * a record that is missing or unreadable costs a history row some columns and
         * costs the programme nothing.
         */
        val sessionRecords = stringSetPreferencesKey("session_records")
        val familyMemberName = stringPreferencesKey("family_member_name")
        val lastSummarySentAt = stringPreferencesKey("last_summary_sent_at")
        val lastSummaryText = stringPreferencesKey("last_summary_text")
        val lastSummarySource = stringPreferencesKey("last_summary_source")
        val setupComplete = stringPreferencesKey("setup_complete")
        val lastFeedback = stringPreferencesKey("last_feedback")
        val lastFeedbackEpochDay = longPreferencesKey("last_feedback_epoch_day")
        val activePauseStartEpochDay = longPreferencesKey("active_pause_start_epoch_day")
        val activePauseReason = stringPreferencesKey("active_pause_reason")
        val closedPausePeriods = stringSetPreferencesKey("closed_pause_periods")
        val lastResumeEpochDay = longPreferencesKey("last_resume_epoch_day")
        val textSize = stringPreferencesKey("text_size")

        // --- what the scheduled evaluation found, whether or not anyone was looking ---
        // These five are the whole of the "app notices things" state. Everything the
        // person sees is still recomputed from `completed_session_dates` at open, so a
        // worker that never ran costs them nothing; what these buy is a record that the
        // app made a judgement at a time when the television was on and nobody was in
        // the room, plus the two markers that stop it saying so more than once.
        val lastCheckEpochDay = longPreferencesKey("last_check_epoch_day")
        val lastCheckReason = stringPreferencesKey("last_check_reason")
        val lastCheckDaysSince = longPreferencesKey("last_check_days_since")
        val notifiedForSessionEpochDay = longPreferencesKey("notified_for_session_epoch_day")
        val catchUpDismissedEpochDay = longPreferencesKey("catch_up_dismissed_epoch_day")

        /** Debug builds only; see [SteadyState.demoDayOffset]. Never written in a
         * release build, because the only thing that can write it lives in `src/debug`. */
        val demoDayOffset = longPreferencesKey("demo_day_offset")
    }

    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    val state: Flow<SteadyState> = context.steadyDataStore.data.map { prefs ->
        val startEpochDay = prefs[Keys.programStartEpochDay]
        val programStart = if (startEpochDay != null) LocalDate.ofEpochDay(startEpochDay) else null
        val completed = prefs[Keys.completedSessionDates].orEmpty()
            .mapNotNull { runCatching { LocalDate.parse(it, isoFormatter) }.getOrNull() }
            .sorted()
        val feedback = prefs[Keys.lastFeedback]?.let { raw ->
            runCatching { SessionFeedback.valueOf(raw) }.getOrNull()
        }

        val activePauseStart = prefs[Keys.activePauseStartEpochDay]
        val activePauseReason = prefs[Keys.activePauseReason]?.let { raw ->
            runCatching { PauseReason.valueOf(raw) }.getOrNull()
        }
        val activePause = if (activePauseStart != null && activePauseReason != null) {
            PausePeriod(activePauseStart, endEpochDay = null, reason = activePauseReason)
        } else {
            null
        }
        val closedPauses = prefs[Keys.closedPausePeriods].orEmpty()
            .mapNotNull { encoded -> decodePausePeriod(encoded) }

        SteadyState(
            programStart = programStart,
            completedSessionDates = completed,
            sessionRecords = prefs[Keys.sessionRecords].orEmpty()
                .mapNotNull(SessionRecord::decode)
                .sortedByDescending { it.date },
            familyMemberName = prefs[Keys.familyMemberName].orEmpty(),
            lastSummarySentAt = prefs[Keys.lastSummarySentAt],
            lastSummaryText = prefs[Keys.lastSummaryText],
            lastSummarySource = prefs[Keys.lastSummarySource],
            setupComplete = prefs[Keys.setupComplete] == "true",
            lastFeedback = feedback,
            activePause = activePause,
            closedPausePeriods = closedPauses,
            lastResumeEpochDay = prefs[Keys.lastResumeEpochDay],
            textSize = prefs[Keys.textSize]?.let { raw ->
                runCatching { TextSize.valueOf(raw) }.getOrNull()
            } ?: TextSize.NORMAL,
            lastCheckEpochDay = prefs[Keys.lastCheckEpochDay],
            lastCheckReason = prefs[Keys.lastCheckReason]?.let { raw ->
                runCatching { CatchUpReason.valueOf(raw) }.getOrNull()
            },
            lastCheckDaysSince = prefs[Keys.lastCheckDaysSince]?.toInt(),
            notifiedForSessionEpochDay = prefs[Keys.notifiedForSessionEpochDay],
            catchUpDismissedEpochDay = prefs[Keys.catchUpDismissedEpochDay],
            demoDayOffset = if (BuildConfig.DEBUG) prefs[Keys.demoDayOffset] ?: 0L else 0L,
        )
    }

    /**
     * Writes down what the scheduled evaluation found. Called from
     * [com.steady.app.work.AdherenceCheckWorker] once a day, and from nowhere else.
     *
     * The timestamp is written even when [notice] is null, because "the app checked and
     * everything was fine" is a different and more useful record than "the app has not
     * checked". Settings shows the date, so the claim that this runs is visible on the
     * device rather than only in a README.
     */
    suspend fun recordAdherenceCheck(today: LocalDate, notice: CatchUpNotice?) {
        context.steadyDataStore.edit { prefs ->
            prefs[Keys.lastCheckEpochDay] = today.toEpochDay()
            if (notice == null) {
                prefs.remove(Keys.lastCheckReason)
                prefs.remove(Keys.lastCheckDaysSince)
            } else {
                prefs[Keys.lastCheckReason] = notice.reason.name
                prefs[Keys.lastCheckDaysSince] = notice.daysSinceLastSession.toLong()
            }
        }
    }

    /** Marks the quiet stretch running from [lastSessionEpochDay] as already mentioned.
     * [com.steady.app.domain.AdherenceNoticePolicy] reads this to guarantee one
     * notification per stretch rather than one per day. */
    suspend fun recordNoticeRaised(lastSessionEpochDay: Long) {
        context.steadyDataStore.edit { prefs ->
            prefs[Keys.notifiedForSessionEpochDay] = lastSessionEpochDay
        }
    }

    /** "Not today". Clears the Home line for the rest of this day and no longer. */
    suspend fun dismissCatchUp(today: LocalDate) {
        context.steadyDataStore.edit { prefs ->
            prefs[Keys.catchUpDismissedEpochDay] = today.toEpochDay()
        }
    }

    suspend fun completeSetup(familyMemberName: String, today: LocalDate) {
        context.steadyDataStore.edit { prefs ->
            if (prefs[Keys.programStartEpochDay] == null) {
                prefs[Keys.programStartEpochDay] = today.toEpochDay()
            }
            prefs[Keys.familyMemberName] = familyMemberName.trim()
            prefs[Keys.setupComplete] = "true"
        }
    }

    /** A settings-surface action: the family member's name is guessable wrong at setup
     * (a nickname, a typo, or simply the wrong relative) and there was previously no way
     * to fix it without reinstalling. */
    suspend fun updateFamilyMemberName(name: String) {
        context.steadyDataStore.edit { prefs ->
            prefs[Keys.familyMemberName] = name.trim()
        }
    }

    /**
     * Writes the day down for the programme's rules, and what happened during it for the
     * history screen.
     *
     * Two sessions on the same day collapse into one record, the way they already collapse
     * into one date: the set is keyed on the day. The later one wins, which is the right
     * way round — it is the one the person has just finished and is about to be asked
     * about, so it is the one the feedback belongs to.
     */
    suspend fun recordSessionCompleted(
        date: LocalDate,
        secondsElapsed: Int,
        movementsDone: Int,
        movementsPlanned: Int,
    ) {
        context.steadyDataStore.edit { prefs ->
            val current = prefs[Keys.completedSessionDates].orEmpty().toMutableSet()
            current += date.format(isoFormatter)
            prefs[Keys.completedSessionDates] = current

            val record = SessionRecord(date, secondsElapsed, movementsDone, movementsPlanned, feedback = null)
            prefs[Keys.sessionRecords] = prefs[Keys.sessionRecords]
                .orEmpty()
                .replacingRecordFor(date, record)
        }
    }

    /** The answer arrives a screen after the session it belongs to, so it is patched onto
     * the record that was written a moment ago rather than written with it. If there is no
     * record for the day — an install upgraded mid-session, say — the answer is still kept
     * as the *last* feedback, because that is what tomorrow's dose is computed from. */
    suspend fun recordFeedback(feedback: SessionFeedback, date: LocalDate) {
        context.steadyDataStore.edit { prefs ->
            prefs[Keys.lastFeedback] = feedback.name
            prefs[Keys.lastFeedbackEpochDay] = date.toEpochDay()

            val existing = prefs[Keys.sessionRecords].orEmpty()
            val forDay = existing.mapNotNull(SessionRecord::decode).firstOrNull { it.date == date }
            if (forDay != null) {
                prefs[Keys.sessionRecords] = existing.replacingRecordFor(date, forDay.withFeedback(feedback))
            }
        }
    }

    private fun Set<String>.replacingRecordFor(date: LocalDate, record: SessionRecord): Set<String> =
        filterNot { SessionRecord.decode(it)?.date == date }.toSet() + record.encode()

    /** Fire TV ships no system text-size setting, so this is the only one the person
     * has. Persisted here rather than held in memory because somebody who needed larger
     * text yesterday needs it today. */
    suspend fun setTextSize(size: TextSize) {
        context.steadyDataStore.edit { prefs -> prefs[Keys.textSize] = size.name }
    }

    suspend fun recordSummarySent(sentAtLabel: String, text: String, source: TextSource) {
        context.steadyDataStore.edit { prefs ->
            prefs[Keys.lastSummarySentAt] = sentAtLabel
            prefs[Keys.lastSummaryText] = text
            prefs[Keys.lastSummarySource] = source.name
        }
    }

    /** "I am away" / "I am unwell": starts a pause. While a pause is active, no week it
     * touches can regress the tier or break the streak (see [com.steady.app.domain.PauseTracker]). */
    suspend fun pause(reason: PauseReason, today: LocalDate) {
        context.steadyDataStore.edit { prefs ->
            prefs[Keys.activePauseStartEpochDay] = today.toEpochDay()
            prefs[Keys.activePauseReason] = reason.name
        }
    }

    /** Closes the active pause into permanent history (so the weeks it covered stay
     * excluded forever, not just while the pause was active) and marks today as a
     * resume day, so the next session generated is the eased-back-in one rather than
     * resuming at full dose as if nothing happened. */
    suspend fun resume(today: LocalDate) {
        context.steadyDataStore.edit { prefs ->
            val startEpochDay = prefs[Keys.activePauseStartEpochDay]
            val reasonRaw = prefs[Keys.activePauseReason]
            if (startEpochDay != null && reasonRaw != null) {
                val closed = prefs[Keys.closedPausePeriods].orEmpty().toMutableSet()
                closed += encodePausePeriod(PausePeriod(startEpochDay, today.toEpochDay(), PauseReason.valueOf(reasonRaw)))
                prefs[Keys.closedPausePeriods] = closed
            }
            prefs.remove(Keys.activePauseStartEpochDay)
            prefs.remove(Keys.activePauseReason)
            prefs[Keys.lastResumeEpochDay] = today.toEpochDay()
        }
    }

    private fun encodePausePeriod(period: PausePeriod): String =
        "${period.startEpochDay}:${period.endEpochDay}:${period.reason.name}"

    private fun decodePausePeriod(encoded: String): PausePeriod? {
        val parts = encoded.split(":")
        if (parts.size != 3) return null
        val start = parts[0].toLongOrNull() ?: return null
        val end = parts[1].toLongOrNull()
        val reason = runCatching { PauseReason.valueOf(parts[2]) }.getOrNull() ?: return null
        return PausePeriod(start, end, reason)
    }
}

data class SteadyState(
    val programStart: LocalDate?,
    val completedSessionDates: List<LocalDate>,
    /** The detail behind those dates, newest first. Shorter than
     * [completedSessionDates] on any install that was exercising before the app started
     * keeping it, and on a demo history that was seeded as dates alone. */
    val sessionRecords: List<SessionRecord>,
    val familyMemberName: String,
    val lastSummarySentAt: String?,
    val lastSummaryText: String?,
    val lastSummarySource: String?,
    val setupComplete: Boolean,
    val lastFeedback: SessionFeedback?,
    val activePause: PausePeriod?,
    val closedPausePeriods: List<PausePeriod>,
    val lastResumeEpochDay: Long?,
    val textSize: TextSize = TextSize.NORMAL,
    /** The day the scheduled evaluation last ran. Null means it has not run yet. */
    val lastCheckEpochDay: Long? = null,
    /** What it found, or null when it found nothing worth saying. */
    val lastCheckReason: CatchUpReason? = null,
    val lastCheckDaysSince: Int? = null,
    /** The session date of the quiet stretch a notification has already been raised for. */
    val notifiedForSessionEpochDay: Long? = null,
    /** The day "Not today" was last pressed. */
    val catchUpDismissedEpochDay: Long? = null,
    /**
     * Days added to the system date, so a demo can walk this programme forward without
     * touching the device clock — which on a shared emulator is somebody else's clock
     * too. Always zero in a release build: the receiver that sets it is compiled only
     * into `src/debug`, and the read above is additionally gated on `BuildConfig.DEBUG`.
     */
    val demoDayOffset: Long = 0L,
)
