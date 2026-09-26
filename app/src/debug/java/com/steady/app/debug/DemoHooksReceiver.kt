package com.steady.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.steady.app.data.steadyDataStore
import com.steady.app.domain.SessionFeedback
import com.steady.app.domain.SessionRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Debug builds only. This file is compiled from `src/debug` and is in no release APK.
 *
 * It exists so that a nine-day gap in somebody's exercise history can be demonstrated in
 * a minute, without either of the two dishonest shortcuts:
 *
 * 1. **Moving the device clock.** This machine's emulator is shared with other agents'
 *    work (see `FRICTION.md` row 8), and `adb shell date` would move their clock
 *    too. It would also prove nothing about the app: an app cannot tell a moved clock
 *    from a passing week, so neither can a viewer.
 * 2. **Mocking the screen.** Nothing here writes a sentence, a notice or a notification.
 *    It writes *session dates* and a day offset into the same DataStore the app has
 *    always read, and then the real `CatchUpEvaluator`, the real `AdherenceNoticePolicy`
 *    and the real worker do exactly what they would do in March.
 *
 * Two commands:
 *
 *     adb shell am broadcast -a com.steady.app.DEMO_SEED --user 0 \
 *         -n com.steady.app/com.steady.app.debug.DemoHooksReceiver \
 *         --es dates 2026-09-10,2026-09-12,2026-09-14
 *
 *     adb shell am broadcast -a com.steady.app.DEMO_OFFSET --user 0 \
 *         -n com.steady.app/com.steady.app.debug.DemoHooksReceiver \
 *         --ei days 9
 *
 * The offset is added to `LocalDate.now()` everywhere the app asks what day it is, so
 * "nine days later" is a real nine days as far as every rule in the app is concerned.
 */
class DemoHooksReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_SEED -> seed(app, intent.getStringExtra("dates").orEmpty())
                    ACTION_OFFSET -> offset(app, intent.getIntExtra("days", 0).toLong())
                    ACTION_TEXT_SIZE -> textSize(app, intent.getStringExtra("size").orEmpty())
                    else -> Log.w(TAG, "unknown demo action: ${intent.action}")
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun seed(context: Context, raw: String) {
        val dates = raw.split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (dates.isEmpty()) {
            Log.w(TAG, "seed: no parseable dates in \"$raw\"")
            return
        }
        val start = dates.min()
        context.steadyDataStore.edit { prefs ->
            prefs[PROGRAM_START] = start.toEpochDay()
            prefs[COMPLETED] = dates.map(LocalDate::toString).toSet()
            prefs[SESSION_RECORDS] = dates.map { date -> seededRecord(date).encode() }.toSet()
            // A seeded history is a different history, so everything the app has already
            // said about the old one has to go with it: the markers that stop a notice
            // being raised twice, and the weekly update that was written about weeks
            // which no longer exist. Leaving the sent summary behind meant a freshly
            // seeded app opened Settings claiming it had told somebody's daughter about a
            // week it had just forgotten.
            prefs.remove(NOTIFIED_FOR_SESSION)
            prefs.remove(DISMISSED_ON)
            prefs.remove(LAST_CHECK)
            prefs.remove(LAST_SUMMARY_SENT_AT)
            prefs.remove(LAST_SUMMARY_TEXT)
            prefs.remove(LAST_SUMMARY_SOURCE)
        }
        Log.i(TAG, "seed: programme starts $start, ${dates.size} sessions recorded")
    }

    /**
     * A seeded day needs a seeded session behind it, or the history screen shows a column
     * of dates and "No detail was kept for this one" nineteen times — which is a true
     * statement about seeded data and a useless demo.
     *
     * Derived from the date rather than drawn at random, so the same seed always produces
     * the same history and a screenshot taken twice is the same screenshot. The spread is
     * modest and deliberately boring: mostly five movements out of five in nine to twelve
     * minutes and mostly "just right", with the occasional short day, because that is what
     * the record of somebody sticking to a falls-prevention programme looks like.
     */
    private fun seededRecord(date: LocalDate): SessionRecord {
        val n = (date.toEpochDay() % 9L).toInt()
        val planned = 5
        val done = if (n == 4) 3 else if (n == 7) 4 else planned
        val seconds = 9 * 60 + n * 23 + done * 18
        val feedback = when (n) {
            1, 6 -> SessionFeedback.TOO_EASY
            4 -> SessionFeedback.TOO_MUCH
            else -> SessionFeedback.JUST_RIGHT
        }
        return SessionRecord(date, seconds, done, planned, feedback)
    }

    private suspend fun offset(context: Context, days: Long) {
        context.steadyDataStore.edit { prefs -> prefs[DEMO_DAY_OFFSET] = days }
        Log.i(TAG, "offset: the app now believes today is ${LocalDate.now().plusDays(days)}")
    }

    /** Drives the in-app text-size control from adb, so the layout can be checked at
     * the 28sp accessibility step without six D-pad presses between every screenshot. */
    private suspend fun textSize(context: Context, size: String) {
        val value = size.trim().uppercase()
        if (value !in setOf("NORMAL", "LARGER", "LARGEST")) {
            Log.w(TAG, "textSize: \"$size\" is not one of NORMAL, LARGER, LARGEST")
            return
        }
        context.steadyDataStore.edit { prefs -> prefs[TEXT_SIZE] = value }
        Log.i(TAG, "textSize: $value")
    }

    private companion object {
        const val TAG = "Steady/Demo"
        const val ACTION_SEED = "com.steady.app.DEMO_SEED"
        const val ACTION_OFFSET = "com.steady.app.DEMO_OFFSET"
        const val ACTION_TEXT_SIZE = "com.steady.app.DEMO_TEXT_SIZE"

        val PROGRAM_START = longPreferencesKey("program_start_epoch_day")
        val COMPLETED = stringSetPreferencesKey("completed_session_dates")
        val SESSION_RECORDS = stringSetPreferencesKey("session_records")
        val DEMO_DAY_OFFSET = longPreferencesKey("demo_day_offset")
        val NOTIFIED_FOR_SESSION = longPreferencesKey("notified_for_session_epoch_day")
        val DISMISSED_ON = longPreferencesKey("catch_up_dismissed_epoch_day")
        val LAST_CHECK = longPreferencesKey("last_check_epoch_day")
        val TEXT_SIZE = stringPreferencesKey("text_size")
        val LAST_SUMMARY_SENT_AT = stringPreferencesKey("last_summary_sent_at")
        val LAST_SUMMARY_TEXT = stringPreferencesKey("last_summary_text")
        val LAST_SUMMARY_SOURCE = stringPreferencesKey("last_summary_source")
    }
}
