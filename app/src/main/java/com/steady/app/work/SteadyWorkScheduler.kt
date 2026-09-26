package com.steady.app.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Enqueues the daily check, once, for the life of the install.
 *
 * `KEEP` rather than `UPDATE`: the request has no inputs and no constraints, so
 * re-enqueueing it on every app launch would only reset its period, and a person who
 * opens Steady every evening would push the check past midnight forever and it would
 * never run on the days they did not open it — which are precisely the days it exists
 * for.
 *
 * No constraints are attached. There is nothing to wait for: no network call, no
 * battery worth sparing on a mains-powered television, and no reason to defer arithmetic
 * over a handful of dates.
 *
 * The flex window is six hours against a one-day period, so the system gets a wide slot
 * to batch this into rather than a deadline to hit. Nothing here is time-critical to the
 * hour; the whole point is that it happens on a day when nobody opened the app.
 */
object SteadyWorkScheduler {

    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<AdherenceCheckWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS,
            flexTimeInterval = 6,
            flexTimeIntervalUnit = TimeUnit.HOURS,
        ).addTag(AdherenceCheckWorker.UNIQUE_WORK_NAME).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AdherenceCheckWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
