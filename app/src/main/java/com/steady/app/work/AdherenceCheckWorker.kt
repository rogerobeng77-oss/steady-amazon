package com.steady.app.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.steady.app.data.SteadyRepository
import com.steady.app.domain.AdherenceNoticePolicy
import com.steady.app.domain.CatchUpEvaluator
import com.steady.app.domain.PausePeriod
import com.steady.app.domain.PauseTracker
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * The thing Steady did not have: something that runs when nobody is looking.
 *
 * Before this, every number in the app came into existence at the moment a person
 * pressed a button on a remote. A week boundary, a broken streak, a tier regression —
 * all of them were computed lazily inside `SteadyViewModel.deriveState` from
 * `LocalDate.now()`, which means an adherence programme could not tell that adherence
 * had stopped, because the only event it could observe was somebody showing up.
 *
 * This worker is deliberately thin. It reads state, calls two pure functions, and writes
 * their answers down. Both of those functions ([CatchUpEvaluator] and
 * [AdherenceNoticePolicy]) live in `domain` and take dates rather than reading a clock,
 * which is what makes nine days of programme behaviour something a unit test can walk
 * through in a millisecond instead of something you find out about on a television next
 * Tuesday.
 *
 * Why `WorkManager` rather than a foreground service: the job is about forty
 * microseconds of date arithmetic, once a day. A foreground service would hold a live
 * process and, on API 28 and up, a permanent notification, to do it — and a permanent
 * notification in this app is itself the nag the whole feature is written to avoid.
 * WorkManager also brings its own `BOOT_COMPLETED` receiver, so persisted periodic work
 * is re-enqueued after a power cycle without this app asking for a boot permission of
 * its own. That is the library's behaviour and not a promise from Amazon, who publish no
 * launch-on-boot API for Fire TV at all — which is why nothing the person sees depends
 * on this having run. Every fact on the Home screen is still recomputed from the session
 * dates at open. A worker that never fires costs them nothing; a worker that fires gives
 * them a note and gives the app a record.
 */
class AdherenceCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = SteadyRepository(applicationContext)
        val state = runCatching { repository.state.first() }.getOrElse { error ->
            // A read that fails is a reason to try again tomorrow, never a reason to
            // guess. Guessing here would mean telling a family member about a gap that
            // may not exist.
            Log.w(TAG, "adherence check could not read state: ${error.javaClass.simpleName}")
            return Result.retry()
        }

        if (!state.setupComplete || state.programStart == null) return Result.success()

        val today = LocalDate.now().plusDays(state.demoDayOffset)
        val pausePeriods: List<PausePeriod> =
            state.closedPausePeriods + listOfNotNull(state.activePause)
        val excludedWeeks =
            PauseTracker.excludedWeekIndices(state.programStart, pausePeriods, today)

        val notice = CatchUpEvaluator.evaluate(
            programStart = state.programStart,
            completedDates = state.completedSessionDates,
            asOfDate = today,
            excludedWeeks = excludedWeeks,
            isPaused = state.activePause != null,
        )

        // Written every run, including the runs that find nothing. "Checked on the 23rd,
        // all fine" and "never checked" are different states and Settings shows which.
        repository.recordAdherenceCheck(today, notice)

        val shouldRaise = AdherenceNoticePolicy.shouldRaiseNotification(
            notice = notice,
            notifiedForSessionEpochDay = state.notifiedForSessionEpochDay,
            dismissedOnEpochDay = state.catchUpDismissedEpochDay,
            todayEpochDay = today.toEpochDay(),
        )
        if (shouldRaise && notice != null) {
            AdherenceNotifier(applicationContext).raise(notice)
            repository.recordNoticeRaised(notice.lastSessionEpochDay)
            Log.i(TAG, "raised one notice for the stretch since day ${notice.lastSessionEpochDay}")
        }

        return Result.success()
    }

    companion object {
        const val TAG = "Steady/AdherenceCheck"
        const val UNIQUE_WORK_NAME = "steady-adherence-check"
    }
}
