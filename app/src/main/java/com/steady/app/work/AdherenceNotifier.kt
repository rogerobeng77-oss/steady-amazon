package com.steady.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.steady.app.MainActivity
import com.steady.app.R
import com.steady.app.domain.CatchUpNotice

/**
 * The notification, and the reasons it is the quietest one the platform will let us post.
 *
 * What Fire TV actually supports, from
 * `https://developer.amazon.com/docs/fire-tv/notifications.html` (fetched 2026-09-23;
 * the page's own footer says it was last updated 29 October 2020):
 *
 * - Fire TV supports the standard Android Notification API "with some limitations". The
 *   old `com.amazon.device.notification` API has been discontinued since Fire OS 5.
 * - A **heads-up** notification appears over whatever is playing, at the bottom of the
 *   screen, and Amazon requires `PRIORITY_HIGH` to get one.
 * - A **standard** notification does not interrupt the foreground app. It goes into a
 *   Notification Center that lives under Settings, with a bell next to Settings when
 *   something is unread.
 * - Progress and stacked notifications are not supported.
 * - Users switch notifications off per app, device-wide. Amazon: "More granular
 *   notification configurations are not possible." A channel exists because Android 8
 *   requires one, not because a Fire TV owner can see it.
 *
 * So this posts a standard, default-importance, silent notification and nothing else.
 * A heads-up notice is available and is not used: covering the bottom of a film in a
 * living room to tell somebody in their seventies that they have not exercised is the
 * exact failure this feature is supposed to avoid, and it would be the app's loudest
 * possible way of saying it.
 *
 * The honest cost of that choice, said plainly because it is the kind of thing a demo
 * quietly skips: **a standard notification on Fire TV lands in a list under Settings
 * that this app's user will probably never open.** It is worth posting — it costs
 * nothing, it never interrupts, and whoever set the television up may well see the bell
 * — but it is the second channel, not the mechanism. The mechanism is the line on Home
 * the next time the television goes on, which is the only moment somebody is certainly
 * looking at this app.
 */
class AdherenceNotifier(private val context: Context) {

    fun raise(notice: CatchUpNotice) {
        ensureChannel()

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(notice.notificationTitle)
            .setContentText(notice.notificationBody)
            // DEFAULT, not HIGH. HIGH is what makes it a heads-up on Fire TV.
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()

        // A device that has notifications switched off for this app is a device where
        // notify() does nothing. That is a setting, not an error, and there is nothing
        // to recover: the Home line still works and still carries the same fact.
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Session reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "One quiet note when a stretch of days goes by without a session."
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "steady_sessions"

        /** One id, reused. A second quiet stretch replaces the first note rather than
         * stacking beside it — and Fire TV does not support stacked notifications in any
         * case. */
        const val NOTIFICATION_ID = 1001
    }
}
