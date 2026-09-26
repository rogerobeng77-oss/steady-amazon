package com.steady.app

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.steady.app.work.SteadyWorkScheduler

/**
 * Exists for one line: putting the daily adherence check on the schedule.
 *
 * It runs on process start rather than from `MainActivity`, because the Activity is the
 * one thing that is guaranteed *not* to have happened on the days this feature is about.
 * `ExistingPeriodicWorkPolicy.KEEP` makes it idempotent, so a process that starts ten
 * times still has exactly one check enqueued.
 */
class SteadyApplication : Application(), Configuration.Provider {

    /**
     * WorkManager says nothing at all at its default logging level, which makes "did the
     * daily check run" unanswerable from a terminal. Debug builds get the full trace;
     * release builds stay at ERROR, where they were.
     */
    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()
        SteadyWorkScheduler.ensureScheduled(this)
    }
}
