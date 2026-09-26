package fr.alaedine.aesh.data.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.alaedine.aesh.R

/**
 * Wraps the platform notification APIs needed to show the daily report
 * reminder, keeping [DailyReportReminderWorker] free of notification
 * plumbing (channel creation, permission checks) so it only holds the
 * "should I remind the user?" decision.
 */
class DailyReminderNotifier(private val context: Context) {

    /** Shows the "reports still missing" reminder, creating its channel on demand. */
    fun notifyMissingReports() {
        ensureChannel()

        // API 33+ requires the runtime POST_NOTIFICATIONS permission; if the
        // user hasn't granted it (or declined the app's request), silently
        // skip rather than crash — there is nothing else useful to do from
        // a background worker.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(context.getString(R.string.notification_daily_report_title))
            .setContentText(context.getString(R.string.notification_daily_report_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_daily_report_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_daily_report_description)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "daily_report_reminder"
        const val NOTIFICATION_ID = 1001
    }
}
