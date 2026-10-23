package app.tsunayomi.updates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo

internal object UpdateNotifications {
    private const val PROGRESS_CHANNEL = "library_update"
    private const val NEW_CHAPTERS_CHANNEL = "new_chapters"
    private const val PROGRESS_ID = 7101
    private const val NEW_CHAPTERS_ID = 7102

    /** Extra the launcher activity reads to open the Updates tab. */
    const val EXTRA_OPEN = "app.tsunayomi.open"
    const val OPEN_UPDATES = "updates"

    fun progress(context: Context, done: Int, total: Int): ForegroundInfo {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, PROGRESS_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(context.getString(R.string.updates_progress_title))
            .setContentText(context.getString(R.string.updates_progress, done, total))
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PROGRESS_ID, notification)
        }
    }

    fun newChapters(context: Context, found: List<NewChapters>) {
        if (found.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return // the Updates tab still lists them
        }
        ensureChannels(context)
        val total = found.sumOf { it.chapterNames.size }
        val style = NotificationCompat.InboxStyle()
        NewChaptersText.lines(found).forEach(style::addLine)
        if (found.size > 5) style.setSummaryText(context.getString(R.string.updates_more_titles, found.size - 5))
        val notification = NotificationCompat.Builder(context, NEW_CHAPTERS_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(context.resources.getQuantityString(R.plurals.updates_new_chapters, total, total))
            .setContentText(NewChaptersText.titles(found))
            .setStyle(style)
            .setAutoCancel(true)
            .setContentIntent(openUpdates(context))
            .build()
        NotificationManagerCompat.from(context).notify(NEW_CHAPTERS_ID, notification)
    }

    private fun openUpdates(context: Context): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra(EXTRA_OPEN, OPEN_UPDATES)
        } ?: return null
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(PROGRESS_CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(PROGRESS_CHANNEL, context.getString(R.string.updates_progress_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        if (manager.getNotificationChannel(NEW_CHAPTERS_CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(NEW_CHAPTERS_CHANNEL, context.getString(R.string.updates_channel), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }
}
